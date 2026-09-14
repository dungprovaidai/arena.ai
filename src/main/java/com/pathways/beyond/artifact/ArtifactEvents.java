package com.pathways.beyond.artifact;

import com.pathways.beyond.item.OccultItems;
import com.pathways.beyond.item.SealedArtifactStack;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sealed artifact behaviour.
 *
 * <p>An artifact is never a stat stick. Each one has a passive effect, an active ability and a
 * drawback, and the drawback is always about attention: using power makes the world aware of
 * you. The three here are the mod's flagship items.
 */
public final class ArtifactEvents {
    private ArtifactEvents() {}

    /** Called once per second per player: passives, drawbacks, and the Eye's slow bleed. */
    public static void serverTick(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        // ---- THE EYE OF SOLOMON: reveals, at the cost of the mind ------------------------
        if (main.is(ModItems.EyeOfSolomon.get()) || off.is(ModItems.EyeOfSolomon.get())) {
            if (!SealedArtifactStack.isUnsealed(main.is(ModItems.EyeOfSolomon.get()) ? main : off)) {
                player.displayClientMessage(Component.literal(
                        "The reliquary is still sealed. It needs unsealing at a ritual altar.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            } else {
                // passive: a wide, faint aura of supernatural things is revealed continuously
                AABB area = player.getBoundingBox().inflate(24.0);
                ServerLevel level = player.serverLevel();
                for (Entity entity : level.getEntities(player, area)) {
                    if (entity instanceof LivingEntity living
                            && SanitySystem.supernaturalWeight(living) > 0.0f
                            && player.tickCount % 60 == 0) {
                        level.sendParticles(ModParticles.RuneDust.get(), living.getX(),
                                living.getY() + living.getBbHeight() * 0.8, living.getZ(),
                                2, 0.2, 0.2, 0.2, 0.0);
                    }
                }
                // drawback: the eye does not close when you put it away
                state.addSanity(-0.12f);
                state.addCorruption(0.05f);
                if (player.getRandom().nextFloat() < 0.02f) {
                    level.playSound(null, player.blockPosition(), ModSounds.WhisperNear.get(),
                            SoundSource.PLAYERS, 0.4f, 1.4f);
                }
            }
        }

        // ---- THE BLACK BOOK: forbidden knowledge, paid for in certainty ------------------
        if (main.is(ModItems.BlackBook.get()) || off.is(ModItems.BlackBook.get())) {
            state.addSanity(-0.05f);
            if (player.getRandom().nextFloat() < 0.03f
                    && !player.hasEffect(com.pathways.beyond.registry.ModEffects.Clarity)) {
                player.displayClientMessage(Component.literal(
                        "You have read this page before. It says something different now.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
                com.pathways.beyond.sanity.HallucinationDirector.force(player,
                        OccultMessages.H_TEXT_GLITCH);
            }
        }

        // ---- THE WHISPERING BELL: a passive hum that never quite stops -------------------
        if (main.is(ModItems.WhisperingBell.get()) || off.is(ModItems.WhisperingBell.get())) {
            if (player.tickCount % 400 == 0) {
                player.serverLevel().playSound(null, player.blockPosition(), ModSounds.WhisperFar.get(),
                        SoundSource.PLAYERS, 0.25f, 1.1f);
            }
        }

        // ---- THE BRASS KEY: warm to the touch, and always slightly in the way ------------
        if (main.is(ModItems.BrassKey.get())) {
            if (player.tickCount % 300 == 0) {
                player.displayClientMessage(Component.literal("The key is warm again.")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
        }
    }

    /** Network entry point for artifact activation. */
    public static void handleAction(ServerPlayer player, String action, int type) {
        switch (action) {
            case "eye_reveal" -> eyeReveal(player);
            case "book_read" -> bookRead(player);
            case "bell_ring" -> bellRing(player);
            case "key_open" -> keyOpen(player);
            default -> {
            }
        }
    }

    // ===================================================================================
    // The Eye of Solomon
    // ===================================================================================
    /** Reveals supernatural entities and hidden resources in a wide radius. */
    public static void eyeReveal(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(32.0);
        int revealed = 0;
        for (Entity entity : level.getEntities(player, area)) {
            if (entity instanceof LivingEntity living && SanitySystem.supernaturalWeight(living) > 0.0f) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 300, 0, false, false));
                revealed++;
            }
        }
        // and hidden resources: ore veins, chests, and blocks that should not be there
        BlockPos centre = player.blockPosition();
        int hidden = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-16, -10, -16), centre.offset(16, 10, 16))) {
            var blockState = level.getBlockState(pos);
            if (blockState.is(com.pathways.beyond.registry.ModBlocks.VoidStone.get())
                    || blockState.is(Blocks.CHEST) || blockState.is(Blocks.SPAWNER)
                    || blockState.is(com.pathways.beyond.registry.ModBlocks.MemoryShardBlock.get())) {
                level.sendParticles(ModParticles.RuneDust.get(), pos.getX() + 0.5, pos.getY() + 0.5,
                        pos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
                hidden++;
                if (hidden > 160) {
                    break;
                }
            }
        }
        level.playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                SoundSource.PLAYERS, 0.9f, 1.2f);
        ModNetwork.sendVisual(player, OccultMessages.V_ARTIFACT_ACTIVATE,
                player.getX(), player.getY() + 1.6, player.getZ(), 1.0f, 0x63A2B8, 80);
        player.displayClientMessage(Component.literal(revealed + " presence(s), " + hidden + " hidden thing(s).")
                .withStyle(net.minecraft.ChatFormatting.AQUA), true);

        // drawback: every use widens the pupil, and the drain is permanent until you rest
        state.addSanity(-6.0f);
        state.addCorruption(0.6f);
        if (player.getRandom().nextFloat() < 0.25f) {
            com.pathways.beyond.sanity.HallucinationDirector.force(player, OccultMessages.H_SILHOUETTE);
        }
    }

    // ===================================================================================
    // The Black Book
    // ===================================================================================
    /** Opens the Codex of Forbidden Knowledge: recipes, ritual knowledge and Sequence insight. */
    public static void bookRead(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        PlayerPathway pathway = ModAttachments.pathway(player);
        ServerLevel level = player.serverLevel();

        // whatever the player is missing, the Book knows it and is willing to say so
        var available = com.pathways.beyond.ritual.RitualRecipes.forPlayer(pathway);
        if (!available.isEmpty()) {
            var recipe = available.get(player.getRandom().nextInt(available.size()));
            pathway.learnRitual(recipe.id());
            player.displayClientMessage(Component.literal("Recorded: " + recipe.display()
                    + " - " + recipe.components().size() + " components.").withStyle(
                    net.minecraft.ChatFormatting.DARK_PURPLE), false);
        }
        level.playSound(null, player.blockPosition(), ModSounds.CodexOpen.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetwork.sendPathwaySync(player);

        // drawback: reading costs sanity, and roughly one page in four is watching you read it
        state.addSanity(-4.0f);
        state.addCorruption(0.35f);
        if (player.getRandom().nextFloat() < 0.25f) {
            com.pathways.beyond.sanity.HallucinationDirector.force(player, OccultMessages.H_FAKE_PLAYER);
        }
        if (player.getRandom().nextFloat() < 0.10f) {
            // the one page that is never blank twice
            Mob watcher = ModEntities.Watcher.get().create(level);
            if (watcher != null) {
                watcher.moveTo(player.getX() + 6, player.getY(), player.getZ() + 6, 0.0f, 0.0f);
                watcher.getPersistentData().putInt("pathways_event_life", 600);
                level.addFreshEntity(watcher);
            }
            player.displayClientMessage(Component.literal(
                            "Something is reading over your shoulder. It has been for a while.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
        }
    }

    // ===================================================================================
    // The Whispering Bell
    // ===================================================================================
    /** Ringing the bell: spirits appear, hidden things reveal, and something specific listens. */
    public static void bellRing(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        OccultState state = ModAttachments.occult(player);
        level.playSound(null, player.blockPosition(), ModSounds.BellRing.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetwork.sendVisual(player, OccultMessages.V_ARTIFACT_ACTIVATE,
                player.getX(), player.getY() + 1.4, player.getZ(), 1.0f, 0xD4B265, 100);

        // 1. spirit entities appear
        for (int i = 0; i < 4; i++) {
            Mob spirit = (player.getRandom().nextBoolean() ? ModEntities.SpiritWisp.get()
                    : ModEntities.Hollow.get()).create(level);
            if (spirit != null) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                spirit.moveTo(player.getX() + Math.cos(angle) * 12.0, player.getY() + 1.0,
                        player.getZ() + Math.sin(angle) * 12.0, 0.0f, 0.0f);
                spirit.getPersistentData().putInt("pathways_event_life", 1200);
                level.addFreshEntity(spirit);
            }
        }
        // 2. hidden entities reveal themselves
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(48.0))) {
            if (entity instanceof LivingEntity living && SanitySystem.supernaturalWeight(living) > 0.0f) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 400, 0, false, false));
            }
        }
        // 3. nearby supernatural activity spikes: every ritual in the area accelerates
        com.pathways.beyond.ritual.RitualManager.deepRitual(player);

        // drawback: something specific hears the bell. It is already on its way.
        state.addSanity(-5.0f);
        state.addCorruption(0.8f);
        level.playSound(null, player.blockPosition(), ModSounds.BellAnswer.get(),
                SoundSource.AMBIENT, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.bell_answered")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);

        // the answer arrives as an Unblinking, at a distance, and it does not attack immediately
        if (player.getRandom().nextFloat() < 0.35f) {
            var unblinking = ModEntities.TheUnblinking.get().create(level);
            if (unblinking != null) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * 64.0, player.getY(),
                        player.getZ() + Math.sin(angle) * 64.0);
                unblinking.moveTo(at.getX() + 0.5, player.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
                level.addFreshEntity(unblinking);
            }
        }
    }

    // ===================================================================================
    // The Brass Key
    // ===================================================================================
    /** Opens a short blink through space - a Door that insists it was always there. */
    public static void keyOpen(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(32.0, 0.0f, false);
        Vec3 destination = hit.getLocation();
        if (hit.getType() != HitResult.Type.BLOCK) {
            destination = player.position().add(player.getViewVector(1.0f).scale(16.0));
        }
        BlockPos target = BlockPos.containing(destination);
        while (!level.getBlockState(target).isAir() && target.getY() < level.getMaxBuildHeight() - 3) {
            target = target.above();
        }
        level.playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                SoundSource.PLAYERS, 0.9f, 1.4f);
        level.sendParticles(ModParticles.VeilSmoke.get(), player.getX(), player.getY() + 1.0,
                player.getZ(), 30, 0.6, 0.8, 0.6, 0.02);
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        level.playSound(null, target, ModSounds.ThreadSever.get(), SoundSource.PLAYERS, 0.7f, 1.3f);

        // drawback: each use costs a small amount of maximum sanity until you sleep
        state.addSanity(-3.0f);
        state.addCorruption(0.25f);
        if (player.getRandom().nextFloat() < 0.15f) {
            BlockPos behind = target.relative(player.getDirection().getOpposite());
            if (level.getBlockState(behind).isAir()) {
                level.setBlock(behind, com.pathways.beyond.registry.ModBlocks.ChalkCircle.get()
                        .defaultBlockState(), Block.UPDATE_ALL);
                com.pathways.beyond.event.DelayedWorldActions.schedule(level, behind, behind,
                        () -> level.setBlock(behind, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL), 40);
                player.displayClientMessage(Component.literal("A door closed behind you. It was not there before.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
        }
    }
}
