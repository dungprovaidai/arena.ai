package com.pathways.beyond.sanity;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Sanity and Corruption.
 *
 * <p>Neither value is a simple timer. Sanity moves because of what the player *does*
 * (abilities, artifacts, rituals, looking at things that should not be looked at) and
 * what the world does to them (night, low light, proximity to supernatural entities).
 * Corruption only ever goes up, and it turns the player into something else one stage at
 * a time: subtle, then disturbing, then monstrous.
 */
public final class SanitySystem {
    private SanitySystem() {}

    public static final float MAX = 100.0f;

    public static float clamp(float value) {
        return Math.max(0.0f, Math.min(MAX, value));
    }

    // ===================================================================================
    // Tiers
    // ===================================================================================
    public enum Tier {
        STABLE("stable", 80, 100),
        UNEASY("uneasy", 60, 80),
        HALLUCINATING("hallucinating", 40, 60),
        DELUSIONAL("delusional", 20, 40),
        INSANE("insane", 1, 20),
        LOST_CONTROL("lost_control", 0, 0);

        public final String id;
        public final int min;
        public final int max;

        Tier(String id, int min, int max) {
            this.id = id;
            this.min = min;
            this.max = max;
        }

        public Component display() {
            return Component.translatable("sanity.pathwaysofthebeyond." + id);
        }
    }

    public static Tier tier(float sanity) {
        if (sanity <= 0.0f) {
            return Tier.LOST_CONTROL;
        }
        if (sanity < 20.0f) {
            return Tier.INSANE;
        }
        if (sanity < 40.0f) {
            return Tier.DELUSIONAL;
        }
        if (sanity < 60.0f) {
            return Tier.HALLUCINATING;
        }
        if (sanity < 80.0f) {
            return Tier.UNEASY;
        }
        return Tier.STABLE;
    }

    public enum CorruptionStage {
        CLEAN("clean", 0),
        STAINED("stained", 10),
        MARKED("marked", 25),
        VEINED("veined", 45),
        TAINTED("tainted", 65),
        MONSTROUS("monstrous", 85);

        public final String id;
        public final int threshold;

        CorruptionStage(String id, int threshold) {
            this.id = id;
            this.threshold = threshold;
        }

        public Component display() {
            return Component.translatable("corruption.pathwaysofthebeyond." + id);
        }
    }

    public static CorruptionStage stage(float corruption) {
        CorruptionStage found = CorruptionStage.CLEAN;
        for (CorruptionStage stage : CorruptionStage.values()) {
            if (corruption >= stage.threshold) {
                found = stage;
            }
        }
        return found;
    }

    // ===================================================================================
    // Costs - every supernatural power has a price
    // ===================================================================================
    /** Spend sanity for an ability. Returns false if the player is too far gone to try. */
    public static boolean spendSanity(ServerPlayer player, float amount) {
        OccultState state = ModAttachments.occult(player);
        if (state.sanity() <= 1.0f) {
            return false;
        }
        state.addSanity(-amount);
        // using power while already unstable pushes you further: the price compounds
        Tier tier = tier(state.sanity());
        if (tier.ordinal() >= Tier.HALLUCINATING.ordinal()) {
            state.addCorruption(amount * 0.05f);
        }
        ModNetwork.sendOccultSync(player);
        return true;
    }

    /** Operator helper: force the corruption value (used by /pathways corruption). */
    public static void setCorruption(ServerPlayer player, float value) {
        OccultState state = ModAttachments.occult(player);
        state.setCorruption(clamp(value));
        ModNetwork.sendOccultSync(player);
    }

    /** Warn a player who has just logged in while still in the worst sanity tier. */
    public static void alertIfLostControl(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (state.sanity() <= 0.0f) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "You are not in control. Something else has been using your hands.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
        }
    }

    public static void addCorruption(ServerPlayer player, float amount) {
        OccultState state = ModAttachments.occult(player);
        CorruptionStage before = stage(state.corruption());
        state.addCorruption(amount);
        CorruptionStage after = stage(state.corruption());
        if (after != before) {
            onStageChange(player, after);
        }
        ModNetwork.sendOccultSync(player);
    }

    /** Called when corruption crosses a stage boundary: the body changes, audibly. */
    private static void onStageChange(ServerPlayer player, CorruptionStage stage) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), ModSounds.CorruptionPulse.get(), SoundSource.PLAYERS,
                0.9f, 0.7f - stage.ordinal() * 0.06f);
        ModNetwork.sendVisual(player, com.pathways.beyond.network.OccultMessages.V_CORRUPTION_PULSE,
                player.getX(), player.getY() + 1.0, player.getZ(), 1.0f + stage.ordinal(),  0x5A0F17, 60);
        player.displayClientMessage(Component.translatable("corruption.pathwaysofthebeyond." + stage.id)
                .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
    }

    // ===================================================================================
    // Server tick
    // ===================================================================================
    public static void serverTick(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        PlayerPathway pathway = ModAttachments.pathway(player);
        pathway.tickCooldowns();
        state.tickHallucinationCooldown();
        state.ageMemory();

        float drain = 0.0f;

        // --- environment -----------------------------------------------------------------
        ServerLevel level = player.serverLevel();
        long time = level.getDayTime() % 24000L;
        boolean night = time > 13000L && time < 23000L;
        int blockLight = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
        boolean dark = blockLight < 4;
        boolean spiritForm = state.spiritForm();

        if (spiritForm) {
            drain += 0.05f;                      // being out of your body is inherently costly
        }
        if (night && dark) {
            drain += 0.02f;                      // the dark is not empty
        }
        if (player.isInWaterOrRain() && night) {
            drain += 0.01f;
        }

        // --- proximity to the supernatural ----------------------------------------------
        AABB area = player.getBoundingBox().inflate(24.0);
        List<Entity> nearby = level.getEntities(player, area);
        for (Entity entity : nearby) {
            float weight = supernaturalWeight(entity);
            if (weight <= 0.0f) {
                continue;
            }
            // looking directly at something supernatural costs more than being near it
            boolean looking = isLookingAt(player, entity);
            drain += (looking ? weight * 0.09f : weight * 0.02f);
        }

        // --- corruption pressure ---------------------------------------------------------
        float corruption = state.corruption();
        if (corruption > 50.0f) {
            drain += (corruption - 50.0f) * 0.0025f;
        }

        // --- recovery --------------------------------------------------------------------
        float recovery = 0.0f;
        if (tier(state.sanity()).ordinal() <= Tier.UNEASY.ordinal() && !spiritForm) {
            // daylight, shelter and candlelight settle the mind
            if (!night && level.canSeeSky(player.blockPosition())) {
                recovery += 0.05f;
            }
            if (blockLight >= 8) {
                recovery += 0.03f;
            }
            if (player.getMainHandItem().is(com.pathways.beyond.registry.ModItems.EyeOfSolomon.get())) {
                recovery -= 0.04f;                // the Eye keeps working even when stowed
            }
        }
        if (player.hasEffect(ModEffects.Clarity)) {
            recovery += 0.06f;
        }
        if (player.isSleeping()) {
            recovery += 0.4f;
        }
        if (player.hasEffect(ModEffects.Clarity())) {
            recovery += 0.05f;
        }

        state.addSanity(recovery - drain);

        // --- tier consequences -----------------------------------------------------------
        applyTierEffects(player, state);

        // --- sanity feedback pings -------------------------------------------------------
        Tier tier = tier(state.sanity());
        if (tier == Tier.LOST_CONTROL && state.tetherStrain() < 1.0f) {
            loseControl(player);
        }
        if (player.tickCount % 40 == 0) {
            ModNetwork.sendOccultSync(player);
        }
    }

    /**
     * How much a given entity disturbs the mind. Bosses and spirits cost the most;
     * ordinary animals do not register at all (until you can see their Soul Threads).
     */
    public static float supernaturalWeight(Entity entity) {
        if (entity.getType() == ModEntities.Watcher.get()) {
            return 1.4f;
        }
        if (entity.getType() == ModEntities.Hollow.get()) {
            return 1.1f;
        }
        if (entity.getType() == ModEntities.WhisperingHusk.get()) {
            return 1.0f;
        }
        if (entity.getType() == ModEntities.VeilTenant.get()) {
            return 1.6f;
        }
        if (entity.getType() == ModEntities.ChoirmasterOfTheVeil.get()
                || entity.getType() == ModEntities.TheUnblinking.get()) {
            return 2.5f;
        }
        if (entity.getType() == ModEntities.SpiritWisp.get()) {
            return 0.15f;
        }
        if (entity instanceof LivingEntity living && living.hasEffect(ModEffects.CorruptionSurge)) {
            return 0.6f;
        }
        return 0.0f;
    }

    private static boolean isLookingAt(Player player, Entity entity) {
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f).normalize();
        net.minecraft.world.phys.Vec3 to = entity.position().add(0, entity.getBbHeight() * 0.6, 0)
                .subtract(player.getEyePosition()).normalize();
        return look.dot(to) > 0.86;
    }

    /** Applies the visible/behavioural consequences of the current Sanity tier. */
    private static void applyTierEffects(ServerPlayer player, OccultState state) {
        Tier tier = tier(state.sanity());
        switch (tier) {
            case UNEASY -> {
                // nothing mechanical yet: this tier exists so players notice the pressure
            }
            case HALLUCINATING -> {
                if (player.tickCount % 200 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, false));
                }
            }
            case DELUSIONAL -> {
                if (player.tickCount % 300 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false));
                }
                if (player.tickCount % 600 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0, false, false));
                }
            }
            case INSANE -> {
                if (player.tickCount % 200 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 220, 1, false, false));
                }
                if (player.tickCount % 400 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false));
                }
            }
            case LOST_CONTROL -> {
                player.addEffect(new MobEffectInstance(ModEffects.CosmicDread, 100, 0, false, false));
            }
            default -> {
            }
        }
        // corruption always bites, at every stage, in small ways
        CorruptionStage stage = stage(state.corruption());
        if (stage.ordinal() >= CorruptionStage.VEINED.ordinal() && player.tickCount % 100 == 0) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ModParticles.BlackWisp.get(), player.getX(), player.getY() + 1.0,
                    player.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
        }
        if (stage == CorruptionStage.MONSTROUS && player.tickCount % 200 == 0) {
            // monsters recognise their own
            AABB area = player.getBoundingBox().inflate(16.0);
            for (Entity entity : player.serverLevel().getEntities(player, area)) {
                if (entity instanceof Mob mob && mob.getTarget() == player
                        && mob.getType() != ModEntities.TheUnblinking.get()) {
                    mob.setTarget(null);
                }
            }
        }
    }

    // ===================================================================================
    // Lost Control - Sanity 0. This is not a game over screen; it is worse than that.
    // ===================================================================================
    private static void loseControl(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        OccultState state = ModAttachments.occult(player);
        level.playSound(null, player.blockPosition(), ModSounds.WhisperNear.get(), SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), ModSounds.Heartbeat.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        ModNetwork.sendVisual(player, com.pathways.beyond.network.OccultMessages.V_SOUL_SNAP,
                player.getX(), player.getY(), player.getZ(), 1.0f, 0x2B070D, 200);
        player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.lost_control")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);

        // A body left unattended attracts tenants. This is the intended way to lose control:
        // something else begins using your hands, and it wants to keep them.
        if (state.consumeWard()) {
            player.displayClientMessage(Component.literal("The ward charm cracks."), true);
            state.addSanity(25.0f);
            return;
        }
        var tenant = ModEntities.VeilTenant.get().create(level);
        if (tenant != null) {
            tenant.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
            tenant.setTarget(player);
            level.addFreshEntity(tenant);
        }
        state.addSanity(12.0f);
    }

    // ===================================================================================
    // Combat and death hooks
    // ===================================================================================
    /** Called when the player kills something: killing for no reason is contrary to some pathways. */
    public static void onKill(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        if (!pathway.hasPathway()) {
            return;
        }
        // The Fool is the pathway of perception, not slaughter
        if ("fool".equals(pathway.pathwayId())) {
            pathway.addContradiction();
            addCorruption(player, 0.4f);
            ModAttachments.occult(player).addSanity(-0.6f);
        }
    }

    public static void onDeath(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            OccultState state = ModAttachments.occult(player);
            player.setData(ModAttachments.OCCULT.get(), state.onDeath());
            ModNetwork.sendOccultSync(player);
        }
    }

    /** Ward charm behaviour: absorb one sanity plunge, then shatter. */
    public static boolean tryAbsorb(ServerPlayer player, float amount) {
        OccultState state = ModAttachments.occult(player);
        if (amount < 0 && state.wardCharges() > 0) {
            state.consumeWard();
            player.serverLevel().playSound(null, player.blockPosition(),
                    ModSounds.ArtifactCurse.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            return true;
        }
        return false;
    }

    /** Miracle Invoker's escape clause: survives one lethal blow, then the debt is paid. */
    public static boolean consumeMiracleDebt(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (state.miracleDebt()) {
            state.setMiracleDebt(false);
            state.addSanity(-10.0f);
            addCorruption(player, 2.0f);
            player.setHealth(4.0f);
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.RitualComplete.get(),
                    SoundSource.PLAYERS, 1.0f, 1.2f);
            ModNetwork.sendVisual(player, com.pathways.beyond.network.OccultMessages.V_MIRACLE,
                    player.getX(), player.getY() + 1.0, player.getZ(), 1.0f, 0xEBD08A, 100);
            return true;
        }
        return false;
    }

    /** Corruption gates: higher stages unlock power at the cost of being recognised. */
    public static float corruptionPowerMultiplier(float corruption) {
        return 1.0f + corruption * 0.006f;
    }

    public static boolean canAfford(ServerPlayer player, float sanityCost) {
        return ModAttachments.occult(player).sanity() > sanityCost * 0.6f;
    }

    /** Utility for the many abilities that need "get a point safely in front of the player". */
    public static BlockPos safeLanding(ServerLevel level, ServerPlayer player, double distance) {
        net.minecraft.world.phys.Vec3 start = player.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = player.getViewVector(1.0f).normalize().scale(distance);
        net.minecraft.world.phys.Vec3 end = start.add(dir);
        BlockPos target = BlockPos.containing(end);
        for (int i = 0; i < 24; i++) {
            BlockPos candidate = target.below(i / 2);
            if (!level.getBlockState(candidate).canOcclude() && !level.getBlockState(candidate.above()).canOcclude()) {
                return candidate;
            }
            if (!level.getBlockState(candidate.above(2)).canOcclude()) {
                return candidate.above();
            }
        }
        return player.blockPosition();
    }
}
