package com.pathways.beyond.sanity;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModBlocks;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The hallucination engine.
 *
 * <p>Design rule: <b>no jump scares</b>. Horror here is uncertainty. The director therefore
 * mixes three classes of hallucination:
 *
 * <ol>
 *   <li><b>Real, reverted</b> - a door really opens and closes again, a torch really
 *       disappears for six seconds. These are indistinguishable from real events because
 *       they <i>are</i> real events, just temporary.</li>
 *   <li><b>Real, unexplained</b> - a mob that really spawns and then really vanishes, a
 *       sound placed at a real position behind the player. Nothing about it is rendered
 *       differently, so the player cannot learn a "tell".</li>
 *   <li><b>Rendered only</b> - silhouettes at the edge of vision, item icons that crawl,
 *       one line of text that is not what the item says. Cheap, and deeply unsettling
 *       because the player knows the mod can do the other two as well.</li>
 * </ol>
 *
 * <p>The only universal giveaway is that sanity is draining while it happens, and the
 * player is not told that either.
 */
public final class HallucinationDirector {
    private HallucinationDirector() {}

    /** Minimum ticks between hallucinations at each tier (index = Tier ordinal). */
    private static final int[] BASE_COOLDOWN = {600, 380, 220, 150, 100, 70};

    public static void registerClientPayloads(net.neoforged.bus.api.IEventBus bus) {
        // Client-side hallucination rendering is registered in client/ClientEvents;
        // nothing to register on the mod bus here.
    }

    // ===================================================================================
    // Server tick
    // ===================================================================================
    public static void serverTick(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        if (state.hallucinationCooldown() > 0) {
            return;
        }
        SanitySystem.Tier tier = SanitySystem.tier(state.sanity());
        if (tier == SanitySystem.Tier.STABLE && state.corruption() < 25.0f) {
            // a stable, uncorrupted mind is genuinely safe. This matters: the horror only
            // exists once the player has already paid for it.
            state.setHallucinationCooldown(120);
            return;
        }

        RandomSource random = player.getRandom();
        float chance = switch (tier) {
            case STABLE -> 0.02f;
            case UNEASY -> 0.06f;
            case HALLUCINATING -> 0.16f;
            case DELUSIONAL -> 0.30f;
            case INSANE -> 0.48f;
            case LOST_CONTROL -> 0.65f;
        };
        chance += state.corruption() * 0.0025f;
        if (player.hasEffect(com.pathways.beyond.registry.ModEffects.Hallucinating)) {
            chance += 0.25f;
        }
        if (!player.level().isDay() && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            chance += 0.05f;   // the dark has always been the cheapest special effect
        }
        if (random.nextFloat() > chance) {
            state.setHallucinationCooldown(40);
            return;
        }
        fire(player, pick(player.getRandom(), tier, state));
    }

    /** Choose which hallucination to run, weighted by tier: subtle first, hostile later. */
    private static int pick(RandomSource random, SanitySystem.Tier tier, OccultState state) {
        List<Integer> pool = new ArrayList<>();
        // class 3 - rendered only, available at every tier above stable
        pool.add(OccultMessages.H_FOG_SHIFT);
        pool.add(OccultMessages.H_SILHOUETTE);
        pool.add(OccultMessages.H_ITEM_DISTORTION);
        pool.add(OccultMessages.H_LIGHT_FLICKER);
        pool.add(OccultMessages.H_PHANTOM_SOUND);
        pool.add(OccultMessages.H_WHISPER);
        if (tier.ordinal() >= SanitySystem.Tier.HALLUCINATING.ordinal()) {
            pool.add(OccultMessages.H_FAKE_FOOTSTEP);
            pool.add(OccultMessages.H_SHADOW_MOVE);
            pool.add(OccultMessages.H_FAKE_BREAK);
            pool.add(OccultMessages.H_TEXT_GLITCH);
            pool.add(OccultMessages.H_FAKE_TORCH);
            pool.add(OccultMessages.H_DISTANT_ENTITY);
        }
        if (tier.ordinal() >= SanitySystem.Tier.DELUSIONAL.ordinal()) {
            pool.add(OccultMessages.H_DOOR);
            pool.add(OccultMessages.H_FAKE_MOB);
            pool.add(OccultMessages.H_FAKE_CHEST);
            pool.add(OccultMessages.H_CAMERA_SHAKE);
            pool.add(OccultMessages.H_FAKE_PLAYER);
            pool.add(OccultMessages.H_FALSE_ALARM);
        }
        if (tier == SanitySystem.Tier.INSANE || tier == SanitySystem.Tier.LOST_CONTROL) {
            pool.add(OccultMessages.H_FAKE_MOB);
            pool.add(OccultMessages.H_FALSE_ALARM);
            pool.add(OccultMessages.H_DISTANT_ENTITY);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** Force a specific hallucination (commands, testing, artifact side-effects). */
    public static void force(ServerPlayer player, int kind) {
        fire(player, kind);
    }

    private static void fire(ServerPlayer player, int kind) {
        OccultState state = ModAttachments.occult(player);
        SanitySystem.Tier tier = SanitySystem.tier(state.sanity());
        state.setHallucinationCooldown(BASE_COOLDOWN[Math.min(BASE_COOLDOWN.length - 1, tier.ordinal())]
                + player.getRandom().nextInt(160));
        ServerLevel level = player.serverLevel();
        float intensity = 0.4f + tier.ordinal() * 0.12f + state.corruption() * 0.004f;

        switch (kind) {
            case OccultMessages.H_WHISPER -> {
                Vec3 at = offsetAround(player, 3.0 + player.getRandom().nextDouble() * 6.0);
                SoundEvent sound = player.getRandom().nextBoolean()
                        ? ModSounds.WhisperNear.get() : ModSounds.WhisperFar.get();
                // placed at a real position: the player can turn to face it, and it stops.
                level.playSound(null, at.x, at.y, at.z, sound, SoundSource.AMBIENT,
                        0.75f, 0.9f + player.getRandom().nextFloat() * 0.2f);
                send(player, OccultMessages.H_WHISPER, at, intensity, 60, "");
            }
            case OccultMessages.H_FAKE_FOOTSTEP -> {
                for (int i = 0; i < 4 + tier.ordinal(); i++) {
                    Vec3 at = offsetAround(player, 2.5 + i * 0.6);
                    level.playSound(null, at.x, at.y, at.z, ModSounds.HollowStep.get(),
                            SoundSource.AMBIENT, 0.5f, 0.92f + i * 0.02f);
                }
                send(player, OccultMessages.H_FAKE_FOOTSTEP, player.position(), intensity, 80, "behind");
            }
            case OccultMessages.H_PHANTOM_SOUND -> {
                Vec3 at = offsetAround(player, 4.0);
                SoundEvent[] pool = {ModSounds.Knock.get(), ModSounds.Breathing.get(),
                        ModSounds.Heartbeat.get(), ModSounds.Rumble.get(), ModSounds.Static.get()};
                level.playSound(null, at.x, at.y, at.z, pool[player.getRandom().nextInt(pool.length)],
                        SoundSource.AMBIENT, 0.6f, 0.85f + player.getRandom().nextFloat() * 0.3f);
                send(player, OccultMessages.H_PHANTOM_SOUND, at, intensity, 60, "");
            }
            case OccultMessages.H_DOOR -> {
                BlockPos door = findNearby(level, player.blockPosition(), 12,
                        st -> st.getBlock() instanceof DoorBlock);
                if (door != null) {
                    realDoor(level, door, player);
                } else {
                    send(player, OccultMessages.H_DOOR, offsetAround(player, 6.0), intensity, 100, "fake");
                }
            }
            case OccultMessages.H_FAKE_TORCH -> {
                BlockPos torch = findNearby(level, player.blockPosition(), 14,
                        st -> st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH)
                                || st.is(Blocks.SOUL_TORCH) || st.is(Blocks.SOUL_WALL_TORCH)
                                || st.is(Blocks.LANTERN));
                if (torch != null) {
                    realTorch(level, torch, player);
                } else {
                    send(player, OccultMessages.H_LIGHT_FLICKER, player.position(), intensity, 100, "");
                }
            }
            case OccultMessages.H_FAKE_BREAK -> {
                BlockPos target = player.blockPosition().offset(
                        player.getRandom().nextInt(9) - 4, -1, player.getRandom().nextInt(9) - 4);
                BlockState stateAt = level.getBlockState(target);
                if (!stateAt.isAir()) {
                    // the sound of a block breaking, with the block still there afterwards
                    level.playSound(null, target, stateAt.getSoundType().getBreakSound(),
                            SoundSource.BLOCKS, 0.8f, 1.0f);
                    send(player, OccultMessages.H_FAKE_BREAK, Vec3.atCenterOf(target), intensity, 40, "");
                }
            }
            case OccultMessages.H_DISTANT_ENTITY -> {
                // a real entity, spawned far away, that leaves on its own - or does not
                EntityType<?> type = player.getRandom().nextFloat() < 0.5f
                        ? ModEntities.Watcher.get() : ModEntities.Hollow.get();
                Vec3 at = offsetAround(player, 28.0 + player.getRandom().nextDouble() * 14.0);
                BlockPos pos = BlockPos.containing(at);
                for (int i = 0; i < 12 && level.getBlockState(pos).isAir(); i++) {
                    pos = pos.below();
                }
                Entity spawned = type.create(level);
                if (spawned != null) {
                    spawned.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                            player.getRandom().nextFloat() * 360.0f, 0.0f);
                    spawned.getPersistentData().putInt("pathways_hallucination_life", 200 + player.getRandom().nextInt(400));
                    level.addFreshEntity(spawned);
                }
                send(player, OccultMessages.H_DISTANT_ENTITY, Vec3.atCenterOf(pos), intensity, 120, "");
            }
            case OccultMessages.H_FAKE_MOB -> {
                Monster mob = (player.getRandom().nextFloat() < 0.6f
                        ? ModEntities.WhisperingHusk.get() : ModEntities.Hollow.get()).create(level);
                if (mob != null) {
                    Vec3 at = offsetAround(player, 6.0 + player.getRandom().nextDouble() * 6.0);
                    mob.moveTo(at.x, player.getY(), at.z, player.getRandom().nextFloat() * 360.0f, 0.0f);
                    // tagged so its death is silent and its loot is nothing: it was never real
                    mob.getPersistentData().putBoolean("pathways_hallucination", true);
                    level.addFreshEntity(mob);
                }
            }
            case OccultMessages.H_FAKE_PLAYER -> {
                send(player, OccultMessages.H_FAKE_PLAYER, offsetAround(player, 12.0), intensity, 100,
                        player.getGameProfile().getName());
            }
            case OccultMessages.H_FAKE_CHEST -> {
                send(player, OccultMessages.H_FAKE_CHEST, offsetAround(player, 5.0), intensity, 140, "");
            }
            case OccultMessages.H_SHADOW_MOVE -> {
                send(player, OccultMessages.H_SHADOW_MOVE, offsetAround(player, 3.0), intensity, 70, "");
                level.sendParticles(ModParticles.BlackWisp.get(), player.getX(), player.getY() + 0.2,
                        player.getZ(), 6, 1.4, 0.3, 1.4, 0.02);
            }
            case OccultMessages.H_SILHOUETTE -> send(player, OccultMessages.H_SILHOUETTE,
                    offsetAround(player, 20.0), intensity, 90, "");
            case OccultMessages.H_ITEM_DISTORTION -> send(player, OccultMessages.H_ITEM_DISTORTION,
                    player.position(), intensity, 160, "");
            case OccultMessages.H_TEXT_GLITCH -> send(player, OccultMessages.H_TEXT_GLITCH,
                    player.position(), intensity, 120, "");
            case OccultMessages.H_CAMERA_SHAKE -> send(player, OccultMessages.H_CAMERA_SHAKE,
                    player.position(), intensity, 40, "");
            case OccultMessages.H_FOG_SHIFT -> send(player, OccultMessages.H_FOG_SHIFT,
                    player.position(), intensity, 200, "");
            case OccultMessages.H_LIGHT_FLICKER -> send(player, OccultMessages.H_LIGHT_FLICKER,
                    player.position(), intensity, 60, "");
            case OccultMessages.H_FALSE_ALARM -> falseAlarm(player, intensity);
            default -> {
                // unknown kind: never crash over a hallucination
            }
        }
    }

    /**
     * The cruellest option: something entirely, verifiably real happens, and nothing about
     * it is hostile. A villager wanders past. A door opens. An animal screams.
     */
    private static void falseAlarm(ServerPlayer player, float intensity) {
        ServerLevel level = player.serverLevel();
        float roll = player.getRandom().nextFloat();
        Vec3 at = offsetAround(player, 8.0 + player.getRandom().nextDouble() * 8.0);
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 14 && level.getBlockState(pos).isAir(); i++) {
            pos = pos.below();
        }
        if (roll < 0.4f) {
            Animal animal = EntityType.COW.create(level);
            if (animal != null) {
                animal.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        player.getRandom().nextFloat() * 360.0f, 0.0f);
                animal.getPersistentData().putInt("pathways_hallucination_life", 200);
                level.addFreshEntity(animal);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COW_AMBIENT,
                        SoundSource.NEUTRAL, 0.7f, 0.75f);   // a wrong pitch
            }
        } else if (roll < 0.7f) {
            BlockPos door = findNearby(level, player.blockPosition(), 20,
                    st -> st.getBlock() instanceof DoorBlock);
            if (door != null && level.getBlockState(door).getValue(DoorBlock.OPEN)) {
                realDoor(level, door, player);
            }
        } else {
            level.playSound(null, pos, ModSounds.HuskWhisper.get(), SoundSource.AMBIENT, 0.6f, 0.9f);
        }
        send(player, OccultMessages.H_FALSE_ALARM, Vec3.atCenterOf(pos), intensity, 120, "");
    }

    // ===================================================================================
    // Real, temporary world changes
    // ===================================================================================
    private static void realDoor(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return;
        }
        boolean open = state.getValue(DoorBlock.OPEN);
        BlockPos otherHalf = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.above();
        level.setBlock(pos, state.setValue(DoorBlock.OPEN, !open), Block.UPDATE_ALL);
        BlockState otherState = level.getBlockState(otherHalf);
        if (otherState.getBlock() instanceof DoorBlock) {
            level.setBlock(otherHalf, otherState.setValue(DoorBlock.OPEN, !open), Block.UPDATE_ALL);
        }
        // it closes itself again, shortly after the player has decided it was nothing
        int delay = 60 + player.getRandom().nextInt(120);
        com.pathways.beyond.event.DelayedWorldActions.schedule(level, pos, otherHalf,
                () -> {
                    BlockState now = level.getBlockState(pos);
                    if (now.getBlock() instanceof DoorBlock) {
                        level.setBlock(pos, now.setValue(DoorBlock.OPEN, open), Block.UPDATE_ALL);
                    }
                    BlockState nowOther = level.getBlockState(otherHalf);
                    if (nowOther.getBlock() instanceof DoorBlock) {
                        level.setBlock(otherHalf, nowOther.setValue(DoorBlock.OPEN, open), Block.UPDATE_ALL);
                    }
                }, delay);
        ModNetwork.sendHallucination(player, OccultMessages.H_DOOR, pos.getX(), pos.getY(), pos.getZ(),
                0.6f, delay, "real");
    }

    private static void realTorch(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 0.5f, 1.0f);
        com.pathways.beyond.event.DelayedWorldActions.schedule(level, pos, pos,
                () -> level.setBlock(pos, state, Block.UPDATE_ALL), 80 + player.getRandom().nextInt(120));
        ModNetwork.sendHallucination(player, OccultMessages.H_FAKE_TORCH, pos.getX(), pos.getY(), pos.getZ(),
                0.8f, 100, "real");
    }

    // ===================================================================================
    // Helpers
    // ===================================================================================
    private static void send(ServerPlayer player, int kind, Vec3 at, float intensity, int duration, String data) {
        ModNetwork.sendHallucination(player, kind, at.x, at.y, at.z, intensity, duration, data);
    }

    private static Vec3 offsetAround(ServerPlayer player, double distance) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;
        return new Vec3(player.getX() + dx, player.getY() + player.getRandom().nextDouble() * 1.5 - 0.5,
                player.getZ() + dz);
    }

    private static BlockPos findNearby(ServerLevel level, BlockPos centre, int radius,
                                       java.util.function.Predicate<BlockState> filter) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -6, -radius),
                centre.offset(radius, 6, radius))) {
            if (filter.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.get(level.getRandom().nextInt(found.size()));
    }

    /** Cleanup pass: hallucinated entities expire quietly, leaving no trace. */
    public static void tickHallucinationEntities(ServerLevel level) {
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getPersistentData().contains("pathways_hallucination_life")) {
                int life = entity.getPersistentData().getInt("pathways_hallucination_life") - 1;
                if (life <= 0) {
                    if (entity instanceof LivingEntity) {
                        level.playSound(null, entity.blockPosition(), ModSounds.WatcherVanish.get(),
                                SoundSource.AMBIENT, 0.5f, 1.1f);
                    }
                    entity.discard();
                } else {
                    entity.getPersistentData().putInt("pathways_hallucination_life", life);
                }
            }
        }
    }

    /** Whether an entity is a hallucination - used to suppress loot, XP and death messages. */
    public static boolean isHallucination(Entity entity) {
        return entity.getPersistentData().getBoolean("pathways_hallucination");
    }

    /** Corruption bleed: at high corruption the player leaks black wisps even when calm. */
    public static void corruptionAmbience(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        SanitySystem.CorruptionStage stage = SanitySystem.stage(state.corruption());
        if (stage.ordinal() < SanitySystem.CorruptionStage.VEINED.ordinal()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int count = stage == SanitySystem.CorruptionStage.MONSTROUS ? 6 : 3;
        level.sendParticles(ModParticles.BlackWisp.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                count, 0.5, 0.8, 0.5, 0.01);
        if (player.getRandom().nextFloat() < 0.05f) {
            level.playSound(null, player.blockPosition(), ModSounds.CorruptionPulse.get(),
                    SoundSource.PLAYERS, 0.4f, 0.8f);
        }
    }
}
