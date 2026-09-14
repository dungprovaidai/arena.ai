package com.pathways.beyond.event;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory Echoes.
 *
 * <p>A place remembers. Scholar of Yore can read that memory, and the mod replays it: spectral
 * actors, ghost blocks, and the sounds of an event that has already happened, in the position
 * where it happened. The echo is not a threat - it is a piece of environmental storytelling
 * that the player can walk through.
 *
 * <p>Echoes are chosen from a small table of archetypes (a murder, an argument, a ritual that
 * failed, an evacuation) and instantiated with whatever materials are actually present, so
 * an echo in a chapel is about a choir and an echo in a mine is about a collapse.
 */
public final class MemoryEchoRuntime {
    private MemoryEchoRuntime() {}

    /** Plays a full echo at a position, seen only by the player who read it (plus nearby players). */
    public static void replay(ServerLevel level, BlockPos pos, ServerPlayer reader) {
        String archetype = chooseArchetype(level, pos);
        double radius = 24.0;
        for (ServerPlayer witness : level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(pos)) < radius * radius)) {
            ModNetwork.sendVisual(witness, OccultMessages.V_SPIRIT_ENTER,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.8f, 0xA8DCE2, 140);
        }
        switch (archetype) {
            case "murder" -> echoMurder(level, pos);
            case "ritual_failure" -> echoRitualFailure(level, pos);
            case "evacuation" -> echoEvacuation(level, pos);
            case "chant" -> echoChant(level, pos);
            default -> echoArrival(level, pos);
        }
        reader.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "The place remembers: " + archetype.replace('_', ' ') + ".")
                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA), true);
    }

    /** Reads whatever has actually happened nearby and picks the most plausible memory. */
    private static String chooseArchetype(ServerLevel level, BlockPos pos) {
        int water = 0;
        int stone = 0;
        int wood = 0;
        for (BlockPos scan : BlockPos.betweenClosed(pos.offset(-6, -4, -6), pos.offset(6, 4, 6))) {
            BlockState state = level.getBlockState(scan);
            if (state.is(Blocks.WATER)) {
                water++;
            } else if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
                stone++;
            } else if (state.is(net.minecraft.tags.BlockTags.PLANKS)) {
                wood++;
            }
        }
        if (stone > 60 && wood < 10) {
            return "murder";
        }
        if (wood > 40) {
            return "chant";
        }
        if (water > 30) {
            return "evacuation";
        }
        return level.getRandom().nextBoolean() ? "ritual_failure" : "arrival";
    }

    // ---- archetypes -----------------------------------------------------------------
    private static void echoMurder(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.DaggerCut.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
        level.playSound(null, pos.above(), ModSounds.Breathing.get(), SoundSource.AMBIENT, 0.7f, 0.8f);
        spawnEcho(level, pos, EntityType.VILLAGER, true);
        ghostBlocks(level, pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 6, 60);
    }

    private static void echoRitualFailure(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.RitualStart.get(), SoundSource.AMBIENT, 0.7f, 1.1f);
        level.playSound(null, pos, ModSounds.RitualFail.get(), SoundSource.AMBIENT, 0.9f, 1.0f);
        level.sendParticles(ModParticles.EmberOccult.get(), pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 40, 2.0, 1.0, 2.0, 0.02);
        spawnEcho(level, pos, EntityType.WITCH, false);
        spawnEcho(level, pos, EntityType.VILLAGER, false);
    }

    private static void echoEvacuation(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 5; i++) {
            level.playSound(null, pos, ModSounds.HollowStep.get(), SoundSource.AMBIENT,
                    0.5f, 0.9f + i * 0.05f);
            Vec3 offset = pos.getCenter().add((i % 3 - 1) * 2.0, 0, (i / 3) * 2.0);
            level.sendParticles(ModParticles.SpiritMote.get(), offset.x, offset.y + 0.2, offset.z,
                    4, 0.2, 0.1, 0.2, 0.01);
        }
        spawnEcho(level, pos, EntityType.VILLAGER, false);
    }

    private static void echoChant(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.ChoirmasterChant.get(), SoundSource.AMBIENT, 0.8f, 1.0f);
        spawnEcho(level, pos, EntityType.VILLAGER, false);
        ModNetwork.sendVisualToAll(level, OccultMessages.V_SPIRIT_ENTER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.5f, 0xEBD08A, 160);
    }

    private static void echoArrival(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.SpiritEnter.get(), SoundSource.AMBIENT, 0.7f, 0.9f);
        spawnEcho(level, pos, EntityType.VILLAGER, true);
    }

    // ---- helpers ---------------------------------------------------------------------
    private static void spawnEcho(ServerLevel level, BlockPos pos, EntityType<?> type, boolean hostileEcho) {
        var echo = ModEntities.HistoricalEcho.get().create(level);
        if (echo == null) {
            return;
        }
        echo.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                level.getRandom().nextFloat() * 360.0f, 0.0f);
        echo.setEchoType(type);
        echo.setAggressive(hostileEcho);
        level.addFreshEntity(echo);
    }

    /** Ghost blocks: real blocks that fade in and are removed again. They are never obtainable. */
    private static void ghostBlocks(ServerLevel level, BlockPos pos, BlockState state, int count, int life) {
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos at = pos.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, state, Block.UPDATE_CLIENTS);
                placed.add(at);
            }
        }
        DelayedWorldActions.scheduleBatch(level, placed,
                placed.stream().map(p -> Blocks.AIR.defaultBlockState()).toList(), life);
    }

    /** Replays the memory of every structure within range (Memory Echo ability). */
    public static void replayNearby(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(radius);
        List<LivingEntity> corpses = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity instanceof Mob mob && mob.getHealth() < mob.getMaxHealth());
        BlockPos centre = player.blockPosition();
        replay(level, centre, player);
        for (LivingEntity corpse : corpses) {
            // the echo of a wound: brief, and in the right place
            level.sendParticles(ModParticles.BloodDrop.get(), corpse.getX(),
                    corpse.getY() + 0.8, corpse.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
    }
}
