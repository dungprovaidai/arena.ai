package com.pathways.beyond.entity.boss;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The Choirmaster of the Veil - the mod's accessible boss.
 *
 * <p>Fight design: the Choirmaster is invulnerable while it is singing. Its choir (three
 * Whispering Husks) must be silenced first, and they only appear during the Antiphon phase.
 * Phase transitions are performed with a visible gesture and a musical telegraph, so the player
 * can learn the fight rather than memorise it.
 *
 * <p>Weaker players can win this fight with charcoal and patience. Everything the boss does is
 * telegraphed by sound two seconds in advance, because the intended failure mode is "I got
 * greedy", not "I could not see what was happening".
 */
public class ChoirmasterEntity extends Monster {
    public enum Phase { INTRO, FIRST_ANTIPHON, SILENCE, SECOND_ANTIPHON, FINALE, DYING }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private Phase phase = Phase.INTRO;
    private int phaseTicks;
    private int attackCooldown;
    private int telegraphTicks;
    private boolean summonedChoir;

    public ChoirmasterEntity(EntityType<? extends ChoirmasterEntity> type, Level level) {
        super(type, level);
        this.xpReward = 220;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ConductGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 0.95, true));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 40.0f));
    }

    public Phase phase() {
        return phase;
    }

    // ===================================================================================
    // Tick
    // ===================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            clientAmbience();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        phaseTicks++;

        switch (phase) {
            case INTRO -> introTick(level);
            case FIRST_ANTIPHON -> antiphonTick(level, 0);
            case SILENCE -> silenceTick(level);
            case SECOND_ANTIPHON -> antiphonTick(level, 1);
            case FINALE -> finaleTick(level);
            case DYING -> dyingTick(level);
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (telegraphTicks > 0) {
            telegraphTicks--;
        }
        // the arena breathes: candles gutter in time with the choir
        if (tickCount % 10 == 0) {
            level.sendParticles(ModParticles.EmberOccult.get(), getX(), getY() + 2.4, getZ(),
                    6, 2.5, 1.0, 2.5, 0.01);
        }
    }

    private void introTick(ServerLevel level) {
        // two seconds of standing up, during which it cannot be hurt
        this.setInvulnerable(true);
        if (phaseTicks % 8 == 0) {
            level.sendParticles(ModParticles.VeilSmoke.get(), getX(), getY() + 1.5, getZ(),
                    20, 0.8, 1.2, 0.8, 0.02);
        }
        if (phaseTicks == 40) {
            level.playSound(null, blockPosition(), ModSounds.ChoirmasterChant.get(),
                    SoundSource.HOSTILE, 1.4f, 0.7f);
        }
        if (phaseTicks >= 80) {
            this.setInvulnerable(false);
            enterPhase(Phase.FIRST_ANTIPHON, level);
        }
    }

    private void antiphonTick(ServerLevel level, int round) {
        this.setInvulnerable(true);   // cannot be touched while singing
        if (phaseTicks % 100 == 0) {
            level.playSound(null, blockPosition(), ModSounds.ChoirmasterChant.get(),
                    SoundSource.HOSTILE, 1.2f, 0.65f + round * 0.05f);
            ModNetwork.sendVisualToAll(level, OccultMessages.V_ABILITY_CAST,
                    getX(), getY() + 2.0, getZ(), 1.0f, 0x6B4A8C, 100);
        }
        // the hymn itself: everyone in the hall loses sanity while it lasts
        if (phaseTicks % 40 == 0) {
            for (ServerPlayer player : nearbyPlayers(level, 32.0)) {
                SanitySystem.spendSanity(player, 1.2f + round * 0.6f);
                if (player.getRandom().nextFloat() < 0.2f) {
                    ModNetwork.sendHallucination(player, OccultMessages.H_WHISPER,
                            player.getX(), player.getY() + 1.0, player.getZ(), 0.7f, 60, "choir");
                }
            }
        }
        if (!summonedChoir) {
            summonedChoir = true;
            int husks = round == 0 ? 3 : 4;
            for (int i = 0; i < husks; i++) {
                double angle = (Math.PI * 2 / husks) * i;
                var husk = ModEntities.WhisperingHusk.get().create(level);
                if (husk != null) {
                    husk.moveTo(getX() + Math.cos(angle) * 6.0, getY(), getZ() + Math.sin(angle) * 6.0,
                            (float) Math.toDegrees(angle), 0.0f);
                    level.addFreshEntity(husk);
                    level.sendParticles(ModParticles.VeilSmoke.get(), husk.getX(), husk.getY() + 1.0,
                            husk.getZ(), 25, 0.4, 0.8, 0.4, 0.02);
                }
            }
            level.playSound(null, blockPosition(), ModSounds.HuskWhisper.get(),
                    SoundSource.HOSTILE, 1.0f, 0.8f);
        }
        // once the husks are dead the hymn loses its voices and the boss is exposed
        List<com.pathways.beyond.entity.OccultEntities.WhisperingHuskEntity> husks =
                level.getEntitiesOfClass(com.pathways.beyond.entity.OccultEntities.WhisperingHuskEntity.class,
                        getBoundingBox().inflate(40.0));
        if (husks.isEmpty() && phaseTicks > 120) {
            enterPhase(round == 0 ? Phase.SILENCE : Phase.FINALE, level);
        }
    }

    private void silenceTick(ServerLevel level) {
        this.setInvulnerable(false);
        if (phaseTicks == 1) {
            level.playSound(null, blockPosition(), ModSounds.RitualFail.get(),
                    SoundSource.HOSTILE, 1.0f, 0.8f);
            say("The hymn stops. It is still standing there. This is the part where you move.");
        }
        if (phaseTicks % 60 == 0 && getHealth() > getMaxHealth() * 0.5f) {
            // in the silence it walks, slowly and directly, and screams once per interval
            Player nearest = level.getNearestPlayer(this, 48.0);
            if (nearest != null) {
                getNavigation().moveTo(nearest, 1.0);
                if (attackCooldown <= 0) {
                    telegraph(level, nearest);
                }
            }
        }
        if (phaseTicks >= 200) {
            enterPhase(Phase.SECOND_ANTIPHON, level);
        }
    }

    private void finaleTick(ServerLevel level) {
        this.setInvulnerable(false);
        if (phaseTicks == 1) {
            level.playSound(null, blockPosition(), ModSounds.ChoirmasterScream.get(),
                    SoundSource.HOSTILE, 1.5f, 0.6f);
            ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_PULSE,
                    getX(), getY() + 1.5, getZ(), 1.5f, 0x8C1A24, 60);
            say("It opens the book it has been carrying and the words come out on their own.");
        }
        // the last phase: fast, and every hit it lands costs sanity as well as health
        if (phaseTicks % 20 == 0) {
            Player nearest = level.getNearestPlayer(this, 48.0);
            if (nearest != null) {
                getNavigation().moveTo(nearest, 1.25);
                if (attackCooldown <= 0 && distanceTo(nearest) < 4.0) {
                    telegraph(level, nearest);
                }
            }
        }
        if (phaseTicks % 50 == 0) {
            // it reads aloud from the Black Book; spectral text crawls across the floor
            for (int i = 0; i < 12; i++) {
                BlockPos at = blockPosition().offset(getRandom().nextInt(20) - 10, 0,
                        getRandom().nextInt(20) - 10);
                level.sendParticles(ModParticles.ChromaticSpeck.get(), at.getX() + 0.5,
                        level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).getY() + 0.2,
                        at.getZ() + 0.5, 1, 0.1, 0.0, 0.1, 0.0);
            }
            for (ServerPlayer player : nearbyPlayers(level, 32.0)) {
                SanitySystem.spendSanity(player, 2.0f);
            }
        }
    }

    private void dyingTick(ServerLevel level) {
        this.setInvulnerable(true);
        getNavigation().stop();
        if (phaseTicks == 1) {
            bossEvent.removeAllPlayers();
            level.playSound(null, blockPosition(), ModSounds.ChoirmasterScream.get(),
                    SoundSource.HOSTILE, 1.0f, 0.5f);
            say("It finishes the sentence it started a hundred years ago. It sounds relieved.");
        }
        if (phaseTicks % 8 == 0) {
            level.sendParticles(ModParticles.BlackWisp.get(), getX(), getY() + 1.6, getZ(),
                    25, 0.6, 1.0, 0.6, 0.03);
        }
        if (phaseTicks >= 100) {
            dropLoot(level);
            level.sendParticles(ModParticles.BeyondShard.get(), getX(), getY() + 1.5, getZ(),
                    40, 1.0, 1.0, 1.0, 0.05);
            ModNetwork.sendVisualToAll(level, OccultMessages.V_DEATH_OF_BOSS,
                    getX(), getY() + 1.5, getZ(), 1.0f, 0x6B4A8C, 120);
            this.discard();
        }
    }

    private void enterPhase(Phase next, ServerLevel level) {
        this.phase = next;
        this.phaseTicks = 0;
        this.summonedChoir = next == Phase.FINALE;
        level.playSound(null, blockPosition(), ModSounds.SequenceAdvance.get(),
                SoundSource.HOSTILE, 1.0f, 0.8f);
        ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_START,
                getX(), getY() + 2.0, getZ(), 1.0f, 0xD4B265, 80);
    }

    /** Two seconds of visible, audible warning before a strike lands. */
    private void telegraph(ServerLevel level, Player target) {
        attackCooldown = 60;
        telegraphTicks = 40;
        level.playSound(null, blockPosition(), ModSounds.RitualPulse.get(),
                SoundSource.HOSTILE, 1.0f, 1.6f);
        Vec3 direction = target.position().subtract(position()).normalize();
        // the strike lands along a line, marked out on the floor first
        for (int i = 1; i <= 6; i++) {
            BlockPos at = BlockPos.containing(getX() + direction.x * i, getY(), getZ() + direction.z * i);
            level.sendParticles(ModParticles.EmberOccult.get(), at.getX() + 0.5, at.getY() + 0.2,
                    at.getZ() + 0.5, 3, 0.1, 0.0, 0.1, 0.0);
        }
        if (telegraphTicks <= 0) {
            // resolves next tick in ConductGoal
        }
    }

    /** Applies the telegraphed strike. Called from ConductGoal when the timer expires. */
    void resolveStrike(ServerLevel level) {
        Vec3 direction = getViewVector(1.0f);
        for (int i = 1; i <= 7; i++) {
            Vec3 point = position().add(direction.scale(i));
            for (ServerPlayer player : nearbyPlayers(level, 48.0)) {
                if (player.position().distanceTo(point) < 1.6) {
                    player.hurt(damageSources().mobAttack(this), 8.0f);
                    SanitySystem.spendSanity(player, 3.0f);
                    player.push(direction.x * 1.2, 0.35, direction.z * 1.2);
                }
            }
            level.sendParticles(ModParticles.EmberOccult.get(), point.x, point.y + 0.3, point.z,
                    6, 0.2, 0.3, 0.2, 0.05);
        }
        level.playSound(null, blockPosition(), ModSounds.ChoirmasterScream.get(),
                SoundSource.HOSTILE, 0.8f, 1.3f);
    }

    private void dropLoot(ServerLevel level) {
        spawnAtLocation(ModItems.BlackBook.get().getDefaultInstance());
        spawnAtLocation(ModItems.WhisperingBone.get().getDefaultInstance());
        spawnAtLocation(ModItems.AncientMemory.get().getDefaultInstance());
        for (int i = 0; i < 3; i++) {
            spawnAtLocation(ModItems.SoulFragment.get().getDefaultInstance());
        }
        // the cathedral's own seal: the key the Choirmaster wore around its neck
        spawnAtLocation(ModItems.BrassKey.get().getDefaultInstance());
    }

    private void clientAmbience() {
        if (tickCount % 4 == 0) {
            level().addParticle(ModParticles.BlackWisp.get(),
                    getX() + (getRandom().nextDouble() - 0.5) * 1.4, getY() + 1.2 + getRandom().nextDouble(),
                    getZ() + (getRandom().nextDouble() - 0.5) * 1.4, 0.0, 0.01, 0.0);
        }
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel level, double radius) {
        return level.getPlayers(p -> p.distanceToSqr(this) < radius * radius);
    }

    private void say(String message) {
        for (ServerPlayer player : nearbyPlayers((ServerLevel) level(), 40.0)) {
            player.displayClientMessage(Component.literal(message)
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), false);
        }
    }

    // ===================================================================================
    // Lifecycle
    // ===================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase == Phase.INTRO || phase == Phase.DYING) {
            return false;
        }
        if (this.isInvulnerable() && source.getEntity() instanceof Player) {
            // hitting the singer while it sings: it does not stop
            if (source.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(
                        "The hymn does not stop for you. Silence the choir first.").withStyle(
                        net.minecraft.ChatFormatting.GRAY), true);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof ServerPlayer player) {
            SanitySystem.spendSanity(player, 4.0f);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.pathways.beyond.registry.ModEffects.Hallucinating, 200, 0, false, false));
        }
        return hit;
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (this.getHealth() <= 0.0f && phase != Phase.DYING) {
            // death is a sequence, not a state change
            this.phase = Phase.DYING;
            this.phaseTicks = 0;
            this.setHealth(1.0f);
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
    public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
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
            phase = Phase.INTRO;
        }
        phaseTicks = tag.getInt("pathways_phase_ticks");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.ChoirmasterChant.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ChoirmasterScream.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ChoirmasterScream.get();
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity vehicle) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;   // the boss is bound to its arena
    }

    /** Conducts: manages the telegraphed strike and keeps facing the target. */
    static class ConductGoal extends Goal {
        private final ChoirmasterEntity boss;

        ConductGoal(ChoirmasterEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return boss.phase != Phase.INTRO && boss.phase != Phase.DYING;
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null) {
                return;
            }
            boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (boss.telegraphTicks == 1) {
                boss.resolveStrike((ServerLevel) boss.level());
            }
        }
    }
}
