package com.pathways.beyond;

import com.pathways.beyond.command.PathwaysCommand;
import com.pathways.beyond.entity.EntityEvents;
import com.pathways.beyond.event.WorldEventManager;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.pathway.SequenceLogic;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModBlocks;
import com.pathways.beyond.registry.ModChunkGenerators;
import com.pathways.beyond.registry.ModComponents;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.registry.ModTabs;
import com.pathways.beyond.ritual.RitualManager;
import com.pathways.beyond.sanity.HallucinationDirector;
import com.pathways.beyond.sanity.SanitySystem;
import com.pathways.beyond.soul.SoulThreadManager;
import com.pathways.beyond.spirit.SoulProjection;
import com.pathways.beyond.spirit.SpiritWorld;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Pathways of the Beyond.
 *
 * <p>A supernatural progression expansion for Minecraft: Pathways and Sequences replace
 * vanilla enchanting as the power ladder, pathway potions replace XP as the currency,
 * and every step upward costs Sanity and buys Corruption. The design rules are:
 *
 * <ul>
 *   <li>power is never free - every ability drains Sanity and/or adds Corruption;</li>
 *   <li>the deeper you go, the less human the player looks, sounds and moves;</li>
 *   <li>horror comes from uncertainty, not from jump scares;</li>
 *   <li>Sequence 0 is not an ending. It is the moment The Beyond notices you.</li>
 * </ul>
 */
@Mod(PathwaysMod.MOD_ID)
public class PathwaysMod {
    public static final String MOD_ID = "pathwaysofthebeyond";

    /** Mod logger: used for startup diagnostics and the one-off "recipe table loaded" line. */
    public static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public PathwaysMod(IEventBus modBus, ModContainer container) {
        // ---- registries (generated from tools/content.py) ----
        ModSounds.register(modBus);
        ModEffects.register(modBus);
        ModParticles.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModTabs.register(modBus);
        ModComponents.register(modBus);
        ModAttachments.register(modBus);
        ModChunkGenerators.register(modBus);

        // ---- mod bus ----
        modBus.addListener(EntityEvents::registerAttributes);
        modBus.addListener(ModNetwork::register);
        // ---- client only ----
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modBus.addListener(com.pathways.beyond.client.ClientEvents::register);
        }

        // ---- game bus ----
        NeoForge.EVENT_BUS.addListener(PathwaysMod::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PathwaysMod::onLevelTick);
        NeoForge.EVENT_BUS.addListener(PathwaysMod::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(PathwaysMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(PathwaysMod::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(PathwaysMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(PathwaysCommand::register);
        NeoForge.EVENT_BUS.addListener(com.pathways.beyond.worldgen.StructurePlacer::onChunkLoad);
        NeoForge.EVENT_BUS.register(com.pathways.beyond.event.PathwayEvents.class);
    }

    // ===================================================================================
    // Server tick: the whole supernatural simulation lives here, in a fixed order.
    // ===================================================================================
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (player.tickCount % 5 == 0) {
            // 1. sanity + corruption drift (abilities, artifacts, environment, tier effects)
            SanitySystem.serverTick(serverPlayer);
            // 2. hallucination direction - picks and fires ambiguous events
            HallucinationDirector.serverTick(serverPlayer);
            // 3. pathway digestion from pathway-appropriate behaviour
            SequenceLogic.digestionTick(serverPlayer);
            // 4. soul projection: tether strain, distance, body possession checks
            SoulProjection.serverTick(serverPlayer);
        }
        if (player.tickCount % 20 == 0) {
            SequenceLogic.passiveTick(serverPlayer);
            com.pathways.beyond.artifact.ArtifactEvents.serverTick(serverPlayer);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            com.pathways.beyond.item.PathwayInfusion.tick(level);
            com.pathways.beyond.event.DelayedWorldActions.cleanup(level);
        }
        if (level.getGameTime() % 10 == 0) {
            RitualManager.serverTick(level);
            SoulThreadManager.serverTick(level);
        }
        if (level.getGameTime() % 100 == 0) {
            WorldEventManager.tick(level);
        }
        if (level.getGameTime() % 200 == 0) {
            SpiritWorld.tick(level);
        }
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        ModAttachments.copyOnRespawn(event.getOriginal(), event.getEntity());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModAttachments.syncAll(player);
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        SoulThreadManager.onDeath(event.getEntity());
        SanitySystem.onDeath(event.getEntity());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        RitualManager.loadRecipes(event.getServer());
        WorldEventManager.loadRules(event.getServer());
    }
}
