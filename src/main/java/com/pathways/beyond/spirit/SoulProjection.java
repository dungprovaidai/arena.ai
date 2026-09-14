package com.pathways.beyond.spirit;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModBlocks;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Soul Projection.
 *
 * <p>The player leaves their body behind and moves as a spirit: translucent, glowing at the
 * edges, floating, with a Soul Thread running back to the abandoned body. The body still
 * exists in the Overworld and is <b>not</b> protected - a Veil Tenant that finds it will
 * start using it.
 *
 * <p>The tether has a finite tolerance. Past it the strain rises, the screen distorts, the
 * player's own heartbeat becomes audible, and finally the connection parts: the spirit is
 * flung back, and whatever was standing over the body is now wearing it.
 */
public final class SoulProjection {
    private SoulProjection() {}

    /** Beyond this distance the tether starts to strain. */
    public static final double TETHER_SOFT_LIMIT = 48.0;
    /** Past this, the connection fails outright. */
    public static final double TETHER_HARD_LIMIT = 160.0;

    // ===================================================================================
    // Entering and leaving
    // ===================================================================================
    public static void request(ServerPlayer player, boolean project) {
        OccultState state = ModAttachments.occult(player);
        PlayerPathway pathway = ModAttachments.pathway(player);
        if (project) {
            if (state.spiritForm()) {
                return;
            }
            // Sequence 1 and above may project at will; lower sequences need the veil gate
            boolean allowed = pathway.hasPathway() && pathway.sequence() <= 1;
            if (!allowed && !player.getPersistentData().getBoolean("pathways_veil_gate_open")) {
                player.displayClientMessage(Component.translatable(
                        "message.pathwaysofthebeyond.insufficient_sequence"), true);
                return;
            }
            enter(player);
        } else if (state.spiritForm()) {
            exit(player, false);
        }
    }

    public static void enter(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        ServerLevel level = player.serverLevel();
        state.setSpiritForm(true);
        state.setBodyPos(java.util.Optional.of(player.blockPosition()));
        state.setTetherStrain(0.0f);

        // the body stays behind as an actual entity, so it can be found and used
        var shell = ModEntities.PlayerBodyShell.get().create(level);
        if (shell != null) {
            shell.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            shell.setOwner(player.getUUID());
            shell.setOwnerName(player.getGameProfile().getName());
            level.addFreshEntity(shell);
        }

        player.addEffect(new MobEffectInstance(ModEffects.SpiritForm, 100000, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100000, 0, false, false));
        player.setInvisible(true);
        level.playSound(null, player.blockPosition(), ModSounds.SpiritEnter.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ModParticles.SpiritMote.get(), player.getX(), player.getY() + 1.0,
                player.getZ(), 40, 0.6, 1.0, 0.6, 0.03);
        ModNetwork.sendVisual(player, OccultMessages.V_SPIRIT_ENTER,
                player.getX(), player.getY() + 1.0, player.getZ(), 1.0f, 0xA8DCE2, 100);
        player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.soul_exit")
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
        ModNetwork.sendSpiritState(player, true, 0);
    }

    public static void exit(ServerPlayer player, boolean snapped) {
        OccultState state = ModAttachments.occult(player);
        ServerLevel level = player.serverLevel();
        state.setSpiritForm(false);

        // find our abandoned body; if it is gone, we simply return to where we stand
        var shell = findShell(level, player);
        if (shell != null) {
            player.teleportTo(level, shell.getX(), shell.getY(), shell.getZ(), player.getYRot(), player.getXRot());
            shell.discard();
        } else if (snapped) {
            // the body was taken: the spirit is pushed out of the Spirit World instead
            player.hurt(level.damageSources().magic(), 6.0f);
            state.addSanity(-18.0f);
        }
        state.setBodyPos(java.util.Optional.empty());
        state.setTetherStrain(0.0f);
        player.removeEffect(ModEffects.SpiritForm);
        player.setInvisible(false);
        level.playSound(null, player.blockPosition(), ModSounds.SpiritExit.get(),
                SoundSource.PLAYERS, snapped ? 1.0f : 0.8f, snapped ? 0.7f : 1.0f);
        ModNetwork.sendVisual(player, snapped ? OccultMessages.V_SOUL_SNAP : OccultMessages.V_SPIRIT_EXIT,
                player.getX(), player.getY() + 1.0, player.getZ(), 1.0f, 0xA8DCE2, 80);
        if (!snapped) {
            player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.soul_return")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
        ModNetwork.sendSpiritState(player, false, 0);
        ModNetwork.sendOccultSync(player);
    }

    private static com.pathways.beyond.entity.OccultEntities.PlayerBodyShellEntity findShell(ServerLevel level,
                                                                                       ServerPlayer player) {
        return level.getEntitiesOfClass(com.pathways.beyond.entity.OccultEntities.PlayerBodyShellEntity.class,
                        player.getBoundingBox().inflate(TETHER_HARD_LIMIT + 32.0),
                        shell -> shell.getOwner() != null && shell.getOwner().equals(player.getUUID()))
                .stream()
                .findFirst()
                .orElse(null);
    }

    // ===================================================================================
    // Server tick: tether physics and consequences
    // ===================================================================================
    public static void serverTick(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (!state.spiritForm()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        var shell = findShell(level, player);
        if (shell == null) {
            // the body is gone: the projection fails immediately and painfully
            exit(player, true);
            return;
        }

        double distance = player.distanceTo(shell);
        float strain = (float) Math.max(0.0, (distance - TETHER_SOFT_LIMIT)
                / (TETHER_HARD_LIMIT - TETHER_SOFT_LIMIT) * 100.0);
        state.setTetherStrain(strain);

        // --- the tether is a physical thing: it can be felt --------------------------------
        if (strain > 0.0f) {
            if (player.tickCount % 40 == 0) {
                level.playSound(null, player.blockPosition(),
                        strain > 60.0f ? ModSounds.SpiritStrain.get() : ModSounds.ThreadTension.get(),
                        SoundSource.PLAYERS, Math.min(1.0f, strain / 100.0f + 0.2f), 1.0f);
            }
            if (player.tickCount % 20 == 0) {
                level.playSound(null, player.blockPosition(), ModSounds.Heartbeat.get(),
                        SoundSource.PLAYERS, Math.min(0.9f, strain / 120.0f), 1.2f);
            }
            // motes stream along the thread from the body to the spirit
            Vec3 delta = player.position().subtract(shell.position());
            int steps = 8;
            for (int i = 0; i < steps; i++) {
                Vec3 at = shell.position().add(delta.scale(i / (double) steps));
                level.sendParticles(ModParticles.SoulFlow.get(), at.x, at.y + 1.0, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            // audible and visual distress, both routed through the hallucination channel
            if (strain > 65.0f && player.tickCount % 60 == 0) {
                ModNetwork.sendHallucination(player, OccultMessages.H_CAMERA_SHAKE,
                        player.getX(), player.getY(), player.getZ(), strain / 100.0f, 40, "tether");
                ModNetwork.sendHallucination(player, OccultMessages.H_WHISPER,
                        player.getX(), player.getY(), player.getZ(), strain / 100.0f, 40, "tether");
            }
        }
        ModNetwork.sendSpiritState(player, true, (int) strain);

        // --- possession attempt: something in the fog wants your body ----------------------
        if (player.tickCount % 100 == 0 && strain > 25.0f && level.getRandom().nextFloat() < 0.15f) {
            attemptPossession(level, player, shell, strain);
        }

        // --- the hard limit: the tether parts ---------------------------------------------
        if (distance > TETHER_HARD_LIMIT) {
            level.playSound(null, player.blockPosition(), ModSounds.SpiritSnap.get(),
                    SoundSource.PLAYERS, 1.0f, 0.9f);
            player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.soul_snap")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
            // The body may now be occupied. This is the real cost of projecting too far.
            var tenant = ModEntities.VeilTenant.get().create(level);
            if (tenant != null) {
                tenant.moveTo(shell.getX(), shell.getY(), shell.getZ(), shell.getYRot(), 0.0f);
                tenant.setTarget(player);
                level.addFreshEntity(tenant);
            }
            exit(player, true);
        }
    }

    /**
     * A Veil Tenant finds the abandoned body and starts trying it on. The player gets a
     * warning they cannot decode: their own voice, from behind them, saying nothing.
     */
    private static void attemptPossession(ServerLevel level, ServerPlayer player, Entity shell, float strain) {
        OccultState state = ModAttachments.occult(player);
        Vec3 fromBody = shell.position();
        level.sendParticles(ModParticles.BlackWisp.get(), fromBody.x, fromBody.y + 1.0, fromBody.z,
                10, 0.4, 0.6, 0.4, 0.01);
        if (state.consumeWard()) {
            level.playSound(null, fromBody.x, fromBody.y, fromBody.z, ModSounds.ArtifactCurse.get(),
                    SoundSource.PLAYERS, 0.8f, 1.5f);
            player.displayClientMessage(Component.literal("Something tried your body on. The ward refused it.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return;
        }
        if (level.getRandom().nextFloat() < 0.35f + strain * 0.004f) {
            var tenant = ModEntities.VeilTenant.get().create(level);
            if (tenant != null) {
                tenant.moveTo(fromBody.x, fromBody.y, fromBody.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
                tenant.setTarget(player);
                tenant.setHunting(true);
                level.addFreshEntity(tenant);
                level.playSound(null, fromBody.x, fromBody.y, fromBody.z, ModSounds.TenantPossess.get(),
                        SoundSource.PLAYERS, 0.9f, 1.0f);
                ModNetwork.sendHallucination(player, OccultMessages.H_SILHOUETTE,
                        fromBody.x, fromBody.y, fromBody.z, 1.0f, 200, "tenant");
            }
        }
    }

    /** Spirit movement: floating, and no fall damage. Applied in the player tick. */
    public static void applySpiritMovement(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (!state.spiritForm()) {
            return;
        }
        if (player.isShiftKeyDown()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, -0.08, 0));
        } else if (player.jumping) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.08, 0));
        }
        if (player.fallDistance > 2.0f) {
            player.fallDistance = 0.0f;
        }
        player.setAirSupply(player.getMaxAirSupply());
        if (player.tickCount % 4 == 0) {
            player.serverLevel().sendParticles(ModParticles.SpiritMote.get(),
                    player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.2, 0.3, 0.2, 0.005);
        }
        // the body holding still: the spirit's own soul burns a little to stay out
        if (player.tickCount % 40 == 0) {
            SanitySystem.spendSanity(player, 0.35f);
        }
    }

    /** Called when the player's body is destroyed while projecting. */
    public static void onBodyLost(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (!state.spiritForm()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        state.addSanity(-30.0f);
        SanitySystem.addCorruption(player, 5.0f);
        exit(player, true);
        // being pulled back to nothing is not survivable in the ordinary sense
        player.hurt(level.damageSources().magic(), 12.0f);
        player.addEffect(new MobEffectInstance(ModEffects.Hallucinating, 1200, 1));
        List<ServerPlayer> witnesses = level.getPlayers(p -> p.distanceToSqr(player) < 64 * 64);
        for (ServerPlayer witness : witnesses) {
            ModNetwork.sendVisual(witness, OccultMessages.V_SOUL_SNAP,
                    player.getX(), player.getY() + 1.0, player.getZ(), 1.0f, 0x2B070D, 120);
        }
    }

    /** True when the player's physical body is in the Overworld and they are elsewhere. */
    public static boolean bodyIsElsewhere(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        return state.spiritForm() && state.bodyPos().isPresent();
    }

    /** Used by the Spirit World gate: opens the pocket that allows projection at Sequence > 1. */
    public static void grantGatePass(ServerPlayer player, int ticks) {
        player.getPersistentData().putInt("pathways_veil_gate_pass", ticks);
    }

    public static void tickGatePass(ServerPlayer player) {
        int pass = player.getPersistentData().getInt("pathways_veil_gate_pass");
        if (pass > 0) {
            player.getPersistentData().putInt("pathways_veil_gate_pass", pass - 1);
            if (pass - 1 == 0) {
                player.getPersistentData().remove("pathways_veil_gate_pass");
            }
        }
    }

    /** Altar-based spirit gate: standing on a lit ritual altar lets anyone project briefly. */
    public static boolean altarAllowsProjection(ServerPlayer player) {
        BlockPos below = player.blockPosition().below();
        if (player.level().getBlockState(below).is(ModBlocks.RitualAltar.get())) {
            return true;
        }
        return player.getPersistentData().getBoolean("pathways_veil_gate_open");
    }

    /** Opens a lasting veil gate at a site (used by the OPEN_SPIRIT_GATE ritual outcome). */
    public static void markGatePass(ServerPlayer player) {
        player.getPersistentData().putBoolean("pathways_veil_gate_open", true);
    }

    public static boolean inSpiritWorld(Level level) {
        return level.dimension().location().equals(com.pathways.beyond.PathwaysMod.id("spirit_world"));
    }

    /** Distance from the abandoned body, or -1 when not projecting. */
    public static double tetherDistance(ServerPlayer player) {
        var shell = findShell(player.serverLevel(), player);
        return shell == null ? -1.0 : player.distanceTo(shell);
    }
}
