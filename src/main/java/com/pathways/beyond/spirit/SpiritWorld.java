package com.pathways.beyond.spirit;

import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Spirit World.
 *
 * <p>A supernatural layer of the Overworld: desaturated, drowned in fog, full of impossible
 * architecture and distant silhouettes. It is deliberately <b>not</b> a Nether reskin - it has
 * no hostiles of its own, no ore, and nothing to farm. It is a place you go to look at things,
 * and everything in it is looking back.
 */
public final class SpiritWorld {
    private SpiritWorld() {}

    public static final ResourceKey<Level> SPIRIT_WORLD =
            ResourceKey.create(Registries.DIMENSION, PathwaysMod.id("spirit_world"));

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(SPIRIT_WORLD);
    }

    public static void registerPortalEvents(net.neoforged.bus.api.IEventBus bus) {
        // Travel is ritual-driven rather than portal-driven; there is no portal block to
        // register. Kept as an explicit no-op so the intent is documented in code.
    }

    public static void registerDimensionEvents(net.neoforged.bus.api.IEventBus bus) {
        // Dimension type and generator are data-driven; see data/pathwaysofthebeyond/dimension.
    }

    // ===================================================================================
    // Travelling
    // ===================================================================================
    /** Opens a gate at a ritual site and walks the celebrant through it. */
    public static void openGate(ServerLevel level, BlockPos altar, ServerPlayer celebrant) {
        level.playSound(null, altar, ModSounds.SpiritEnter.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
        level.sendParticles(ModParticles.VeilSmoke.get(), altar.getX() + 0.5, altar.getY() + 1.0,
                altar.getZ() + 0.5, 60, 1.0, 1.4, 1.0, 0.02);
        if (celebrant != null) {
            travel(celebrant, !inSpiritWorld(level));
        }
    }

    /** The single entry point for crossing over. Safe, and always lands on solid ground. */
    public static boolean travel(ServerPlayer player, boolean toSpirit) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel target = toSpirit ? level(server) : server.overworld();
        if (target == null) {
            return false;
        }
        if (player.level().dimension().equals(target.dimension())) {
            return false;
        }
        BlockPos from = player.blockPosition();
        // force the destination chunk in so the player never falls through the world
        target.getChunkAt(from);
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ()) + 1;
        BlockPos destination = new BlockPos(from.getX(), Math.max(target.getMinBuildHeight() + 2, y), from.getZ());

        ServerLevel source = player.serverLevel();
        source.playSound(null, player.blockPosition(), ModSounds.SpiritEnter.get(),
                SoundSource.PLAYERS, 1.0f, toSpirit ? 0.9f : 1.2f);
        player.teleportTo(target, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setPortalCooldown();
        player.displayClientMessage(toSpirit
                ? Component.translatable("message.pathwaysofthebeyond.spirit_enter")
                .withStyle(net.minecraft.ChatFormatting.AQUA)
                : Component.translatable("message.pathwaysofthebeyond.spirit_exit")
                .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        ModNetwork.sendVisual(player, toSpirit ? OccultMessages.V_SPIRIT_ENTER : OccultMessages.V_SPIRIT_EXIT,
                destination.getX(), destination.getY() + 1.0, destination.getZ(), 1.0f, 0xA8DCE2, 100);
        return true;
    }

    // ===================================================================================
    // Ambient life of the dimension
    // ===================================================================================
    public static void tick(ServerLevel level) {
        if (!level.dimension().equals(SPIRIT_WORLD)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            // drifting motes: the cheap, constant reminder that the air here is not air
            level.sendParticles(ModParticles.SpiritMote.get(), player.getX(), player.getY() + 1.4,
                    player.getZ(), 6, 12.0, 4.0, 12.0, 0.008);
            // fog particles hug the ground so the world reads as voluminous without shaders
            level.sendParticles(ModParticles.VeilSmoke.get(), player.getX(), player.getY() - 0.4,
                    player.getZ(), 4, 14.0, 0.6, 14.0, 0.002);
            if (player.tickCount % 400 == 0) {
                level.playSound(null, pos, ModSounds.SpiritAmbient.get(), SoundSource.AMBIENT, 0.6f, 1.0f);
            }
            // distant silhouettes: real entities, far away, that never approach
            if (level.getRandom().nextFloat() < 0.06f) {
                spawnSilhouette(level, player);
            }
            // the Spirit World is expensive to be in
            if (player.tickCount % 200 == 0) {
                com.pathways.beyond.registry.ModAttachments.occult(player).addSanity(-1.2f);
            }
            ModNetwork.sendSpiritState(player, true, (int) com.pathways.beyond.registry.ModAttachments
                    .occult(player).tetherStrain());
        }
    }

    private static void spawnSilhouette(ServerLevel level, ServerPlayer player) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        double distance = 40.0 + level.getRandom().nextDouble() * 30.0;
        BlockPos pos = BlockPos.containing(player.getX() + Math.cos(angle) * distance,
                player.getY(), player.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        Mob silhouette = (level.getRandom().nextBoolean()
                ? ModEntities.Watcher.get() : ModEntities.Hollow.get()).create(level);
        if (silhouette != null) {
            silhouette.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
            silhouette.getPersistentData().putBoolean("pathways_silhouette", true);
            // a silhouette never closes the distance: it is content to be seen
            silhouette.setNoAi(true);
            level.addFreshEntity(silhouette);
        }
    }

    /** Silhouettes are decorative; they vanish when the player gets close, and drop nothing. */
    public static void tickSilhouettes(ServerLevel level) {
        if (!level.dimension().equals(SPIRIT_WORLD)) {
            return;
        }
        for (Entity entity : level.getEntities().getAll()) {
            if (!entity.getPersistentData().getBoolean("pathways_silhouette")) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(entity) < 12.0 * 12.0) {
                    level.playSound(null, entity.blockPosition(), ModSounds.WatcherVanish.get(),
                            SoundSource.AMBIENT, 0.6f, 1.1f);
                    level.sendParticles(ModParticles.VeilSmoke.get(), entity.getX(),
                            entity.getY() + 1.0, entity.getZ(), 20, 0.5, 0.8, 0.5, 0.02);
                    entity.discard();
                    break;
                }
            }
        }
    }

    /** Fog and colour grading values the client uses when this dimension is active. */
    public static int fogColour() {
        return 0x2A2E33;
    }

    public static float fogDensity() {
        return 0.055f;
    }
}
