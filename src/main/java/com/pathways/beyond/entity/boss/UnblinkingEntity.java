package com.pathways.beyond.entity.boss;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Unblinking - the endgame boss, and the one that fights the player's abilities rather
 * than their health bar.
 *
 * <p>Its eyes never close, so it cannot be fooled by illusions: decoys are seen through, but
 * only after a delay. It cannot be escaped by teleporting, because it watches where you go.
 * Its three phases are:
 *
 * <ol>
 *   <li><b>REGARD</b> - it looks at you. Sanity drains continuously. It does not attack yet,
 *       and it is genuinely possible to walk away from this phase.</li>
 *   <li><b>RECOGNITION</b> - it has decided you are interesting. It speaks, it follows, and it
 *       starts closing doors behind you.</li>
 *   <li><b>The LOOKING</b> - full aggression. It attacks through walls at reduced damage, and
 *       the arena floor becomes a lattice of eyes.</li>
 * </ol>
 *
 * <p>It cannot be killed with sanity intact: the intended end is not victory but being noticed,
 * and the loot it drops is knowledge, not equipment.
 */
public class UnblinkingEntity extends Monster {
    public enum Phase { REGARD, RECOGNITION, LOOKING, CLOSED }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    private Phase phase = Phase.REGARD;
    private int phaseTicks;
    private int blinkAvoidance;
    private int gazeCooldown;
    private int possessionCooldown;

    public UnblinkingEntity(EntityType<? extends UnblinkingEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
        this.fireImmune();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95);
    }

    public Phase phase() {
        return phase;
    }

    @Override
    protected void registerGoals() {
        // it has no goals: it does not path, it does not wander, it simply goes where you went
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    // ===================================================================================
    // Tick
    // ===================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            if (tickCount % 3 == 0) {
                level().addParticle(ModParticles.ChromaticSpeck.get(),
                        getX() + (getRandom().nextDouble() - 0.5) * 5.0,
                        getY() + 2.0 + getRandom().nextDouble() * 3.0,
                        getZ() + (getRandom().nextDouble() - 0.5) * 5.0, 0.0, 0.0, 0.0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        phaseTicks++;

        switch (phase) {
            case REGARD -> regardTick(level);
            case RECOGNITION -> recognitionTick(level);
            case LOOKING -> lookingTick(level);
            case CLOSED -> closedTick(level);
        }
        if (gazeCooldown > 0) {
            gazeCooldown--;
        }
        if (possessionCooldown > 0) {
            possessionCooldown--;
        }
    }

    /** Phase one: the world gets quieter, and the player loses sanity just by being seen. */
    private void regardTick(ServerLevel level) {
        this.setInvulnerable(true);
        if (phaseTicks % 40 == 0) {
            for (ServerPlayer player : watched(level, 64.0)) {
                SanitySystem.spendSanity(player, 1.6f);
                SanitySystem.addCorruption(player, 0.15f);
                if (player.getRandom().nextFloat() < 0.3f) {
                    ModNetwork.sendHallucination(player, OccultMessages.H_CAMERA_SHAKE,
                            player.getX(), player.getY(), player.getZ(), 0.5f, 30, "regard");
                }
            }
        }
        if (phaseTicks % 120 == 0) {
            level.playSound(null, blockPosition(), ModSounds.UnblinkingGaze.get(),
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        // phase one ends when the player commits: it must be attacked, or approached closely
        if (phaseTicks > 200 && !findNearbyPlayers(level, 96.0).isEmpty()) {
            for (ServerPlayer player : findNearbyPlayers(level, 96.0)) {
                if (player.distanceTo(this) < 24.0) {
                    enterPhase(Phase.RECOGNITION, level);
                    break;
                }
            }
        }
        if (getHealth() < getMaxHealth() * 0.85f) {
            enterPhase(Phase.RECOGNITION, level);
        }
    }

    /** Phase two: it speaks, it follows, and it starts shutting the world behind the player. */
    private void recognitionTick(ServerLevel level) {
        this.setInvulnerable(false);
        ServerPlayer target = nearestPlayer(level, 128.0);
        if (target == null) {
            return;
        }
        // it moves like a rumour: nearest path, no regard for terrain
        if (phaseTicks % 20 == 0) {
            Vec3 direction = target.position().subtract(position()).normalize();
            this.setDeltaMovement(direction.scale(0.42));
            this.hurtMarked = true;
            level.sendParticles(ModParticles.ChromaticSpeck.get(), getX(), getY() + 2.0, getZ(),
                    8, 0.6, 0.8, 0.6, 0.01);
        }
        if (phaseTicks % 100 == 0) {
            say(level, "I have been looking at you for longer than you have been alive.");
        }
        if (phaseTicks % 200 == 0 && possessionCooldown <= 0) {
            // it does not attack the body: it attempts one possession and leaves
            attemptGaze(level, target);
            possessionCooldown = 400;
        }
        if (getHealth() < getMaxHealth() * 0.45f) {
            enterPhase(Phase.LOOKING, level);
        }
    }

    /** Phase three: full aggression, including through walls, and the floor becomes eyes. */
    private void lookingTick(ServerLevel level) {
        this.setInvulnerable(false);
        ServerPlayer target = nearestPlayer(level, 160.0);
        if (target == null) {
            return;
        }
        if (phaseTicks % 10 == 0) {
            Vec3 direction = target.position().subtract(position()).normalize();
            this.setDeltaMovement(direction.scale(0.5));
            this.hurtMarked = true;
        }
        if (phaseTicks % 60 == 0) {
            // the floor lattice: every tenth block around the target sprouts an eye for 3 seconds
            BlockPos centre = target.blockPosition();
            for (int i = 0; i < 16; i++) {
                BlockPos at = centre.offset(getRandom().nextInt(24) - 12, 0, getRandom().nextInt(24) - 12);
                BlockPos ground = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                ParticleTypes.BLOCK, com.pathways.beyond.registry.ModBlocks.DesecratedStone.get()
                                .defaultBlockState()),
                        ground.getX() + 0.5, ground.getY() + 0.1, ground.getZ() + 0.5,
                        2, 0.2, 0.0, 0.2, 0.0);
            }
            for (ServerPlayer player : watched(level, 64.0)) {
                SanitySystem.spendSanity(player, 3.0f);
            }
        }
        if (phaseTicks % 40 == 0 && gazeCooldown <= 0) {
            // the gaze: line-of-sight damage that ignores walls at half strength
            double damage = hasLineOfSight(target) ? 10.0 : 5.0;
            target.hurt(damageSources().magic(), (float) damage);
            SanitySystem.spendSanity(target, 4.0f);
            ModNetwork.sendVisual(target, OccultMessages.V_ABILITY_CAST,
                    getX(), getY() + 3.0, getZ(), 1.0f, 0xE8F1F2, 40);
            level.playSound(null, blockPosition(), ModSounds.UnblinkingGaze.get(),
                    SoundSource.HOSTILE, 1.0f, 0.9f);
            gazeCooldown = 30;
        }
    }

    private void closedTick(ServerLevel level) {
        this.setInvulnerable(true);
        if (phaseTicks == 1) {
            bossEvent.removeAllPlayers();
            say(level, "Good. Now you know what I am looking at.");
            level.playSound(null, blockPosition(), ModSounds.BeyondArrival.get(),
                    SoundSource.HOSTILE, 1.2f, 0.8f);
            com.pathways.beyond.event.WorldEventManager.beginTheNoticing(level,
                    nearestPlayer(level, 128.0));
        }
        if (phaseTicks % 10 == 0) {
            level.sendParticles(ModParticles.BeyondShard.get(), getX(),
                    getY() + 3.0, getZ(), 30, 1.5, 2.0, 1.5, 0.04);
        }
        if (phaseTicks >= 120) {
            dropLoot(level);
            ModNetwork.sendVisualToAll(level, OccultMessages.V_DEATH_OF_BOSS,
                    getX(), getY() + 3.0, getZ(), 2.0f, 0xE8F1F2, 200);
            this.discard();
        }
    }

    // ===================================================================================
    // Abilities
    // ===================================================================================
    /** The gaze: blocks possession, strips wards, and teaches the player what wards are for. */
    private void attemptGaze(ServerLevel level, ServerPlayer target) {
        level.playSound(null, target.blockPosition(), ModSounds.UnblinkingGaze.get(),
                SoundSource.HOSTILE, 1.2f, 0.8f);
        ModNetwork.sendVisual(target, OccultMessages.V_BEYOND_NOTICING,
                target.getX(), target.getY() + 2.0, target.getZ(), 1.0f, 0xE8F1F2, 80);
        if (target.hasEffect(ModEffects.Clarity) || target.hasEffect(ModEffects.Clarity)) {
            target.displayClientMessage(Component.literal(
                            "Your ward holds. It looks at the ward instead, for a while.")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), false);
            target.removeEffect(ModEffects.Clarity);
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.UnblinkingGaze, 600, 0, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.CosmicDread, 600, 0, false, false));
        SanitySystem.spendSanity(target, 12.0f);
        SanitySystem.addCorruption(target, 3.0f);
        say(level, "I am not going to kill you. I am going to keep looking.");
    }

    /** Called by the mod when a player uses Bizarro decoys near the boss. */
    public boolean seesThroughDecoys() {
        return phase == Phase.LOOKING;
    }

    // ===================================================================================
    // Lifecycle
    // ===================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase == Phase.REGARD) {
            // attacking during Regard is what starts the fight - and it is allowed
            if (source.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(
                        "It blinks. Once. Then it stops doing that.").withStyle(
                        net.minecraft.ChatFormatting.DARK_PURPLE), false);
            }
            enterPhase(Phase.RECOGNITION, (ServerLevel) level());
        }
        if (phase == Phase.CLOSED) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof ServerPlayer player) {
            SanitySystem.spendSanity(player, 6.0f);
            SanitySystem.addCorruption(player, 1.0f);
            player.addEffect(new MobEffectInstance(ModEffects.Hallucinating, 400, 1, false, false));
            ModNetwork.sendHallucination(player, OccultMessages.H_SILHOUETTE,
                    player.getX(), player.getY(), player.getZ(), 1.0f, 200, "unblinking");
        }
        return hit;
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (getHealth() <= 0.0f && phase != Phase.CLOSED) {
            this.phase = Phase.CLOSED;
            this.phaseTicks = 0;
            this.setHealth(1.0f);
        }
    }

    private void enterPhase(Phase next, ServerLevel level) {
        this.phase = next;
        this.phaseTicks = 0;
        switch (next) {
            case RECOGNITION -> {
                say(level, "There. You looked back.");
                level.playSound(null, blockPosition(), ModSounds.UnblinkingGaze.get(),
                        SoundSource.HOSTILE, 1.4f, 0.7f);
                ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_START,
                        getX(), getY() + 3.0, getZ(), 1.5f, 0xE8F1F2, 100);
            }
            case LOOKING -> {
                say(level, "I have seen everything you have ever done. Shall I start?");
                level.playSound(null, blockPosition(), ModSounds.UnblinkingDeath.get(),
                        SoundSource.HOSTILE, 1.5f, 0.8f);
                ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_PULSE,
                        getX(), getY() + 3.0, getZ(), 2.0f, 0x8C1A24, 60);
            }
            default -> {
            }
        }
        for (ServerPlayer player : watched(level, 96.0)) {
            player.displayClientMessage(Component.literal("THE UNBLINKING: " + next.name())
                    .withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD), true);
        }
    }

    private void dropLoot(ServerLevel level) {
        spawnAtLocation(ModItems.AbyssalEye.get().getDefaultInstance());
        spawnAtLocation(ModItems.StarlessCrystal.get().getDefaultInstance());
        spawnAtLocation(ModItems.AncientMemory.get().getDefaultInstance());
        spawnAtLocation(ModItems.EyeOfSolomon.get().getDefaultInstance());
        for (int i = 0; i < 4; i++) {
            spawnAtLocation(ModItems.SoulFragment.get().getDefaultInstance());
        }
        // and in its place, one Whispering Husk that will not look at anyone
        var husk = ModEntities.WhisperingHusk.get().create(level);
        if (husk != null) {
            husk.moveTo(getX(), getY(), getZ(), 0.0f, 0.0f);
            husk.setCustomName(Component.literal("Something that used to watch"));
            level.addFreshEntity(husk);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // nothing that is merely physical worries it; only ritual damage and player weapons land
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("pathways_phase", phase.name());
        tag.putInt("pathways_phase_ticks", phaseTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            phase = Phase.valueOf(tag.getString("pathways_phase"));
        } catch (IllegalArgumentException ignored) {
            phase = Phase.REGARD;
        }
        phaseTicks = tag.getInt("pathways_phase_ticks");
    }

    private List<ServerPlayer> watched(ServerLevel level, double radius) {
        return level.getPlayers(p -> p.distanceToSqr(this) < radius * radius);
    }

    private List<ServerPlayer> findNearbyPlayers(ServerLevel level, double radius) {
        return watched(level, radius);
    }

    private ServerPlayer nearestPlayer(ServerLevel level, double radius) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : watched(level, radius)) {
            double distance = player.distanceToSqr(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void say(ServerLevel level, String message) {
        for (ServerPlayer player : watched(level, 96.0)) {
            player.displayClientMessage(Component.literal(message).withStyle(
                    net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.ITALIC), false);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.UnblinkingGaze.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.UnblinkingHurt.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.UnblinkingDeath.get();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPreventingPlayerRest(ServerPlayer player) {
        return true;
    }

    static LivingEntity self(UnblinkingEntity boss) {
        return boss;
    }
}
