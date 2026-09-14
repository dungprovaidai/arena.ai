package com.pathways.beyond.pathway;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModEffects;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Digestion and Sequence advancement.
 *
 * <p>Nothing here uses vanilla XP. A pathway potion is drunk, and then it must be
 * <i>digested</i>: the player has to behave like the pathway they are climbing. Performing
 * pathway-appropriate acts adds digestion; acting against the pathway removes it, adds
 * Corruption and invites hallucinations. Only at 100% digestion can the next Sequence be
 * attempted, and the attempt itself is a ritual.
 */
public final class SequenceLogic {
    private SequenceLogic() {}

    /** How much digestion is worth one "beat" of appropriate behaviour. */
    private static final float DIGESTION_PER_BEAT = 0.35f;
    /** Digestion per tick of a completed ritual anchor nearby. */
    private static final float RITUAL_DIGESTION = 22.0f;

    // ===================================================================================
    // Digestion
    // ===================================================================================
    public static void digestionTick(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        if (!pathway.hasPathway()) {
            return;
        }
        OccultState state = ModAttachments.occult(player);
        float gain = 0.0f;
        float loss = 0.0f;

        // --- observation: looking at living things, especially the uncanny ---------------
        LivingEntity looked = watchedLiving(player);
        if (looked != null) {
            pathway.addObservation();
            gain += DIGESTION_PER_BEAT;
            // observing something supernatural teaches far more (and costs sanity)
            float weight = SanitySystem.supernaturalWeight(looked);
            if (weight > 1.0f) {
                gain += DIGESTION_PER_BEAT * weight;
                state.addSanity(-0.15f * weight);
                state.addCorruption(0.02f * weight);
            }
        }

        // --- exploration: new ground is new information -----------------------------------
        if (player.tickCount % 40 == 0) {
            if (!player.level().getChunkSource().hasChunk(player.chunkPosition().x, player.chunkPosition().z)) {
                // chunk not loaded in the client cache: treat as new terrain
                pathway.addExploration();
            }
            if (player.getPersistentData().getLong("pathways_last_chunk") != player.chunkPosition().toLong()) {
                player.getPersistentData().putLong("pathways_last_chunk", player.chunkPosition().toLong());
                pathway.addExploration();
                gain += DIGESTION_PER_BEAT * 0.6f;
            }
        }

        // --- study: holding the Codex or reading ritual knowledge -------------------------
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean studying = main.is(ModItems.PathwayCodex.get()) || off.is(ModItems.PathwayCodex.get())
                || main.is(ModItems.BlackBook.get()) || off.is(ModItems.BlackBook.get());
        if (studying) {
            pathway.addStudy();
            gain += DIGESTION_PER_BEAT * 1.4f;
        }
        if (player.hasEffect(ModEffects.DigestionQuickened)) {
            gain *= 1.5f;
        }

        // --- ritual proximity: standing inside your own ritual teaches the body fast -------
        if (com.pathways.beyond.ritual.RitualManager.isPlayerInActiveRitual(player)) {
            gain += RITUAL_DIGESTION / 200.0f;
        }

        // --- contradictions: needless slaughter and forbidden artifact overuse ------------
        if ("fool".equals(pathway.pathwayId())) {
            if (player.getAttackStrengthScale(0.5f) > 0.9f && player.swinging
                    && player.getLastHurtMob() != null
                    && player.getLastHurtMob().tickCount - player.getPersistentData().getInt("pathways_last_kill") < 40) {
                loss += DIGESTION_PER_BEAT * 2.0f;
            }
        }
        if (pathway.contradictions() > 6) {
            loss += DIGESTION_PER_BEAT * 1.5f;
            pathway.decayContradiction();
            state.addCorruption(0.35f);
            ModNetwork.sendHallucination(player, OccultMessages.H_WHISPER,
                    player.getX(), player.getY(), player.getZ(), 0.6f, 60, "contrary");
        }

        pathway.addDigestion(gain - loss);

        // --- tell the player when digestion is complete ----------------------------------
        if (pathway.digestion() >= 100.0f && player.tickCount % 100 == 0) {
            player.displayClientMessage(Component.translatable("gui.pathwaysofthebeyond.ready")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }

    private static LivingEntity watchedLiving(ServerPlayer player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();
        AABB area = player.getBoundingBox().inflate(28.0);
        List<Entity> entities = player.serverLevel().getEntities(player, area);
        return entities.stream()
                .filter(e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof Player))
                .map(e -> (LivingEntity) e)
                .filter(e -> {
                    Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eyes).normalize();
                    return look.dot(to) > 0.94;
                })
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    // ===================================================================================
    // Advancement
    // ===================================================================================
    /** Requirements a player must satisfy before a sequence can be attempted. */
    public static boolean meetsRequirements(ServerPlayer player, int targetSequence) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        PathwayData.SequenceDef def = pathway.definition().bySequence(targetSequence);
        if (def == null) {
            return false;
        }
        if (!pathway.isUnlocked(def.abilities().isEmpty() ? "none" : def.abilities().get(0))) {
            return false;
        }
        return true;
    }

    /**
     * Advance one sequence. This is deliberately the most theatrical thing in the mod:
     * it is the moment the player stops being exactly human.
     */
    public static boolean advance(ServerPlayer player, int newSequence, boolean viaRitual) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        if (!pathway.hasPathway()) {
            return false;
        }
        OccultState state = ModAttachments.occult(player);
        if (!viaRitual && pathway.digestion() < 100.0f) {
            return false;
        }
        int previous = pathway.sequence();
        if (newSequence >= previous) {
            return false;   // sequences only move downward (towards 0)
        }
        ServerLevel level = player.serverLevel();
        String previousName = pathway.sequenceName();
        pathway.unlockSequence(newSequence);
        pathway.setDigestion(0.0f);

        // cost: the higher you climb, the more it takes out of you
        int steps = Math.max(1, previous - newSequence);
        state.addSanity(-(6.0f + steps * 2.0f));
        state.addCorruption(2.5f + steps * 1.2f);

        // ---- transformation VFX/SFX -----------------------------------------------------
        level.playSound(null, player.blockPosition(), ModSounds.SequenceAdvance.get(), SoundSource.PLAYERS,
                1.0f, 1.0f);
        level.sendParticles(ModParticles.RuneDust.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                60, 0.9, 1.2, 0.9, 0.08);
        level.sendParticles(ModParticles.SpiritMote.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.6, 1.0, 0.6, 0.04);
        level.sendParticles(ModParticles.BlackWisp.get(), player.getX(), player.getY() + 0.4, player.getZ(),
                24, 0.5, 0.4, 0.5, 0.02);
        ModNetwork.sendVisual(player, OccultMessages.V_SEQUENCE_ADVANCE,
                player.getX(), player.getY() + 1.0, player.getZ(), 1.0f,
                pathway.definition().colour(), 140);
        // brief, total loss of the senses: the body is busy
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.Clarity, 200, 0, false, false));

        String seqName = pathway.sequenceName();
        var message = Component.translatable("message.pathwaysofthebeyond.advanced",
                String.valueOf(newSequence), seqName);
        player.displayClientMessage(message.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);

        // reaching Sequence 0 opens The Beyond
        if (newSequence == 0) {
            unlockBeyond(player);
        }
        ModNetwork.sendPathwaySync(player);
        ModNetwork.sendOccultSync(player);
        return true;
    }

    /** Sequence 0. The world starts reacting; the mod's endgame begins. */
    public static void unlockBeyond(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        OccultState state = ModAttachments.occult(player);
        if (pathway.beyondUnlocked()) {
            return;
        }
        pathway.setBeyondUnlocked(true);
        state.setNoticedByTheBeyond(true);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), ModSounds.BeyondArrival.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), ModSounds.BeyondCall.get(), SoundSource.PLAYERS, 0.9f, 0.8f);
        ModNetwork.sendVisual(player, OccultMessages.V_BEYOND_NOTICING,
                player.getX(), player.getY() + 2.0, player.getZ(), 1.0f, 0xB49BD0, 400);
        level.sendParticles(ModParticles.BeyondShard.get(), player.getX(), player.getY() + 3.0, player.getZ(),
                80, 6.0, 4.0, 6.0, 0.05);
        player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.beyond_unlocked")
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.OBFUSCATED),
                false);
        com.pathways.beyond.event.WorldEventManager.beginTheNoticing(level, player);
    }

    // ===================================================================================
    // Passive effects of being on a pathway
    // ===================================================================================
    public static void passiveTick(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        if (!pathway.hasPathway()) {
            return;
        }
        int sequence = pathway.sequence();
        OccultState state = ModAttachments.occult(player);
        SanitySystem.Tier tier = SanitySystem.tier(state.sanity());

        switch (pathway.pathwayId()) {
            case "fool" -> {
                // Seer and below: perception keeps expanding. This is the pathway's promise
                // and its cost - you see more, so there is more to be afraid of.
                if (sequence <= 9 && sequence >= 7 && player.tickCount % 100 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, false, false));
                }
                if (sequence <= 5 && player.tickCount % 100 == 0) {
                    player.addEffect(new MobEffectInstance(ModEffects.Clarity, 200, 0, false, false));
                }
                if (sequence <= 3) {
                    // high-sequence Fool: the body is only a suggestion
                    if (player.fallDistance > 6.0f && player.tickCount % 10 == 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
                    }
                    if (player.tickCount % 200 == 0) {
                        player.addEffect(new MobEffectInstance(ModEffects.ThreadBond, 200, 0, false, false));
                    }
                }
                if (sequence == 0) {
                    passiveSequenceZero(player);
                }
            }
            default -> {
                // other pathways are registered but not yet implemented
            }
        }
        // corruption lends power and takes clarity - the standing bargain of the mod
        if (state.corruption() >= 45.0f && player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                    120, state.corruption() >= 85.0f ? 1 : 0, false, false));
            player.addEffect(new MobEffectInstance(ModEffects.SanityBleed, 120, 0, false, false));
        }
        if (tier.ordinal() >= SanitySystem.Tier.INSANE.ordinal() && player.tickCount % 60 == 0) {
            levelParticles(player);
        }
    }

    private static void levelParticles(ServerPlayer player) {
        player.serverLevel().sendParticles(ModParticles.ChromaticSpeck.get(),
                player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.6, 0.6, 0.6, 0.01);
    }

    /** Sequence 0: the player is a supernatural entity now, and the world says so. */
    private static void passiveSequenceZero(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (player.tickCount % 200 == 0) {
            // mobs that would hunt a human do not know where to look
            AABB area = player.getBoundingBox().inflate(24.0);
            for (Entity entity : player.serverLevel().getEntities(player, area)) {
                if (entity instanceof Monster monster && monster.getTarget() == player
                        && player.getRandom().nextFloat() < 0.5f) {
                    monster.setTarget(null);
                }
            }
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 220, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 220, 1, false, false));
        }
        if (player.tickCount % 600 == 0) {
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.BeyondCall.get(),
                    SoundSource.AMBIENT, 0.4f, 1.6f);
            ModNetwork.sendHallucination(player, OccultMessages.H_FALSE_ALARM,
                    player.getX(), player.getY(), player.getZ(), 0.4f, 200, "beyond");
        }
    }

    // ===================================================================================
    // Potion consumption entry point (called by PathwayPotionItem)
    // ===================================================================================
    public static void onPotionDrunk(ServerPlayer player, String pathwayId, int sequence) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        OccultState state = ModAttachments.occult(player);
        if (!pathway.hasPathway()) {
            pathway.begin(pathwayId, sequence);
            state.addSanity(-8.0f);
            state.addCorruption(1.0f);
        } else if (pathway.digestion() >= 100.0f && sequence < pathway.sequence()) {
            // the potion itself does not advance the sequence: it opens the door.
            // advancement happens in the ritual, which is where the risk lives.
            player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.potion_consumed")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
            state.addCorruption(1.5f);
        } else {
            // drinking while digesting: the body rejects it, hard
            state.addSanity(-5.0f);
            state.addCorruption(3.0f);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
            player.addEffect(new MobEffectInstance(ModEffects.Hallucinating, 400, 0));
            ModNetwork.sendHallucination(player, OccultMessages.H_WISPS,
                    player.getX(), player.getY(), player.getZ(), 1.0f, 120, "reject");
        }
        ModNetwork.sendPathwaySync(player);
        ModNetwork.sendOccultSync(player);
    }

    /** Rituals the player is allowed to attempt at their current sequence. */
    public static List<String> availableRituals(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        return com.pathways.beyond.ritual.RitualRecipes.forPlayer(pathway).stream()
                .map(com.pathways.beyond.ritual.RitualRecipes.RitualRecipe::id)
                .toList();
    }

    /** Called by the ritual manager on success. */
    public static void onRitualComplete(ServerPlayer player, String ritualId) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        pathway.learnRitual(ritualId);
        pathway.addDigestion(RITUAL_DIGESTION * 0.5f);
        ModNetwork.sendPathwaySync(player);
    }

    /** Whether a mob can be bound by the Marionettist sequence or above. */
    public static boolean canBind(LivingEntity entity) {
        return entity instanceof Mob || entity instanceof Animal;
    }

    /** Convenience: the block the player is looking at within reach. */
    public static BlockPos lookedAtBlock(ServerPlayer player, double reach) {
        var hit = player.pick(reach, 0.0f, false);
        return BlockPos.containing(hit.getLocation());
    }
}
