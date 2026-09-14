package com.pathways.beyond.event;

import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Supernatural world events.
 *
 * <p>Events are rare and memorable on purpose: a server might see one every few in-game days,
 * and each one changes what the next hour of play feels like. They are announced in-world (sky,
 * sound, behaviour) rather than in chat, except for one ambiguous line.
 */
public final class WorldEventManager {
    private WorldEventManager() {}

    public enum Event {
        RED_MOON("red_moon", 1.0f, 8),
        WHISPERING_NIGHT("whispering_night", 0.7f, 6),
        VEIL_THINS("veil_thins", 0.6f, 5),
        EMPTY_VILLAGE("empty_village", 0.3f, 4),
        THE_WATCHER("the_watcher", 0.5f, 4),
        MEMORY_ECHO("memory_echo", 0.8f, 3),
        THE_NOTICING("the_noticing", 0.0f, 0);

        public final String id;
        public final float weight;
        public final int durationMinutes;

        Event(String id, float weight, int durationMinutes) {
            this.id = id;
            this.weight = weight;
            this.durationMinutes = durationMinutes;
        }
    }

    private static final Map<String, Long> ACTIVE = new HashMap<>();
    private static final Map<String, Integer> COOLDOWNS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static boolean theNoticingBegun;

    public static void loadRules(MinecraftServer server) {
        PathwaysMod.LOGGER.info("[Pathways] world event rules loaded ({} events registered)", Event.values().length);
    }

    // ===================================================================================
    // Tick: one roll every five seconds per level
    // ===================================================================================
    public static void tick(ServerLevel level) {
        // expire finished events
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : ACTIVE.entrySet()) {
            if (entry.getKey().startsWith(level.dimension().location().toString())
                    && level.getGameTime() > entry.getValue()) {
                expired.add(entry.getKey());
            }
        }
        expired.forEach(ACTIVE::remove);

        if (level.dimension() != Level.OVERWORLD || level.players().isEmpty()) {
            return;
        }
        // events are rare: roughly one per two in-game days per level
        if (RANDOM.nextFloat() > 0.012f) {
            return;
        }
        if (!level.isNight() && RANDOM.nextBoolean()) {
            return;
        }
        Event event = pick(level);
        if (event != null) {
            start(level, event);
        }
    }

    private static Event pick(ServerLevel level) {
        List<Event> candidates = new ArrayList<>();
        for (Event event : Event.values()) {
            if (event == Event.THE_NOTICING) {
                continue;   // only ever triggered by a player reaching Sequence 0
            }
            if (onCooldown(level, event)) {
                continue;
            }
            candidates.add(event);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        float total = 0.0f;
        for (Event event : candidates) {
            total += event.weight;
        }
        float roll = RANDOM.nextFloat() * total;
        for (Event event : candidates) {
            roll -= event.weight;
            if (roll <= 0.0f) {
                return event;
            }
        }
        return candidates.get(0);
    }

    private static boolean onCooldown(ServerLevel level, Event event) {
        String key = level.dimension().location() + ":" + event.id;
        Integer remaining = COOLDOWNS.get(key);
        return remaining != null && remaining > 0;
    }

    // ===================================================================================
    // Events
    // ===================================================================================
    public static void start(ServerLevel level, Event event) {
        String key = level.dimension().location() + ":" + event.id;
        ACTIVE.put(level.dimension().location() + "@" + event.id,
                level.getGameTime() + event.durationMinutes * 1200L);
        COOLDOWNS.put(key, event.durationMinutes * 1200 * 6);
        PathsActivated:
        for (ServerPlayer player : level.players()) {
            switch (event) {
                case RED_MOON -> {
                    level.playSound(null, player.blockPosition(), ModSounds.RedMoon.get(),
                            SoundSource.AMBIENT, 1.0f, 1.0f);
                    ModNetwork.sendVisual(player, OccultMessages.V_BEYOND_NOTICING,
                            player.getX(), player.getY() + 20.0, player.getZ(), 1.0f, 0x8C1A24, 400);
                    player.displayClientMessage(Component.translatable(
                            "message.pathwaysofthebeyond.event_red_moon"), false);
                    // the moon reddens; every supernatural thing gets bolder
                    for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0))) {
                        if (com.pathways.beyond.sanity.SanitySystem.supernaturalWeight(mob) > 0.0f) {
                            mob.setTarget(player);
                        }
                    }
                }
                case WHISPERING_NIGHT -> {
                    player.displayClientMessage(Component.translatable(
                            "message.pathwaysofthebeyond.event_whispering_night"), false);
                    for (int i = 0; i < 3; i++) {
                        ModNetwork.sendHallucination(player, OccultMessages.H_WHISPER,
                                player.getX() + RANDOM.nextInt(20) - 10, player.getY(),
                                player.getZ() + RANDOM.nextInt(20) - 10, 0.8f, 200, "event");
                    }
                    level.playSound(null, player.blockPosition(), ModSounds.WhisperingNight.get(),
                            SoundSource.AMBIENT, 0.8f, 1.0f);
                }
                case VEIL_THINS -> {
                    player.displayClientMessage(Component.translatable(
                            "message.pathwaysofthebeyond.event_veil_thins"), false);
                    level.playSound(null, player.blockPosition(), ModSounds.VeilThins.get(),
                            SoundSource.AMBIENT, 0.9f, 1.0f);
                    for (int i = 0; i < 3; i++) {
                        Mob spirit = (RANDOM.nextBoolean() ? ModEntities.Hollow.get()
                                : ModEntities.SpiritWisp.get()).create(level);
                        if (spirit != null) {
                            BlockPos at = player.blockPosition().offset(RANDOM.nextInt(40) - 20, 0,
                                    RANDOM.nextInt(40) - 20);
                            spirit.moveTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                                    RANDOM.nextFloat() * 360.0f, 0.0f);
                            spirit.getPersistentData().putInt("pathways_event_life", 2400);
                            level.addFreshEntity(spirit);
                        }
                    }
                }
                case EMPTY_VILLAGE -> emptyVillage(level, player);
                case THE_WATCHER -> {
                    player.displayClientMessage(Component.translatable(
                            "message.pathwaysofthebeyond.event_the_watcher"), false);
                    Mob watcher = ModEntities.Watcher.get().create(level);
                    if (watcher != null) {
                        BlockPos at = player.blockPosition().offset(RANDOM.nextInt(60) - 30, 0,
                                RANDOM.nextInt(60) - 30);
                        watcher.moveTo(at.getX() + 0.5, player.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
                        watcher.getPersistentData().putInt("pathways_event_life", 6000);
                        level.addFreshEntity(watcher);
                        level.playSound(null, at, ModSounds.WatcherStare.get(),
                                SoundSource.AMBIENT, 0.7f, 1.0f);
                    }
                }
                case MEMORY_ECHO -> {
                    player.displayClientMessage(Component.translatable(
                            "message.pathwaysofthebeyond.event_memory_echo"), true);
                    MemoryEchoRuntime.replay(level, player.blockPosition(), player);
                }
                case THE_NOTICING -> beginTheNoticing(level, player);
                default -> {
                }
            }
            break PathsActivated;
        }
    }

    /** The Empty Village: villagers removed, doors left open, one thing left behind. */
    private static void emptyVillage(ServerLevel level, ServerPlayer player) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(96.0));
        if (villagers.size() < 3) {
            return;
        }
        for (Villager villager : villagers) {
            Vec3 at = villager.position();
            level.sendParticles(ModParticles.VeilSmoke.get(), at.x, at.y + 1.0, at.z, 30, 0.5, 0.8, 0.5, 0.02);
            villager.discard();
        }
        // one door per building is left open; the meals are still warm
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-32, -6, -32),
                player.blockPosition().offset(32, 6, 32))) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                level.setBlock(pos, state.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
                break;
            }
        }
        // and something is left behind, wearing a villager's shape
        Mob leftover = ModEntities.Hollow.get().create(level);
        if (leftover != null) {
            leftover.moveTo(player.getX() + 8, player.getY(), player.getZ() + 8, 0.0f, 0.0f);
            leftover.getPersistentData().putInt("pathways_event_life", 3600);
            level.addFreshEntity(leftover);
        }
        Animal animal = EntityType.COW.create(level);
        if (animal != null) {
            animal.moveTo(player.getX() - 6, player.getY(), player.getZ() - 4, 0.0f, 0.0f);
            level.addFreshEntity(animal);
        }
        level.playSound(null, player.blockPosition(), ModSounds.HuskWhisper.get(),
                SoundSource.AMBIENT, 0.6f, 0.8f);
        player.displayClientMessage(Component.translatable(
                "message.pathwaysofthebeyond.event_empty_village"), false);
    }

    // ===================================================================================
    // The Beyond: the endgame state
    // ===================================================================================
    /**
     * Begins The Noticing: the world starts reacting to the player permanently. Reality
     * distortions, impossible structures, strange stars and entities that were not there
     * before. There is no boss to beat here: the mod's final content is the world noticing,
     * and the player's job is to work out where the Pathways came from.
     */
    public static void beginTheNoticing(ServerLevel level, ServerPlayer player) {
        if (theNoticingBegun && !ModAttachments.occult(player).noticedByTheBeyond()) {
            // already running for someone else: this player simply notices it too
            ModAttachments.occult(player).setNoticedByTheBeyond(true);
            return;
        }
        theNoticingBegun = true;
        ModAttachments.occult(player).setNoticedByTheBeyond(true);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "The sky is not the colour it was this morning.").withStyle(
                net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), false);
        // impossible structures begin forming around the player, slowly, over hours
        for (int i = 0; i < 6; i++) {
            BlockPos at = player.blockPosition().offset(RANDOM.nextInt(80) - 40, 0, RANDOM.nextInt(80) - 40);
            com.pathways.beyond.worldgen.StructurePlacer.placeImpossible(level, at, RANDOM);
        }
        level.playSound(null, player.blockPosition(), ModSounds.BeyondArrival.get(),
                SoundSource.AMBIENT, 1.0f, 1.0f);
    }

    public static boolean isActive(ServerLevel level, Event event) {
        return ACTIVE.containsKey(level.dimension().location() + "@" + event.id);
    }

    /** Red Moon utility used by rendering and mob behaviour. */
    public static boolean isRedMoon(ServerLevel level) {
        return isActive(level, Event.RED_MOON);
    }

    public static boolean theNoticingHasBegun() {
        return theNoticingBegun;
    }

    /** Called once per second from the level tick to age cooldowns. */
    public static void ageCooldowns() {
        COOLDOWNS.replaceAll((key, value) -> Math.max(0, value - 1));
    }

    public static Map<String, Long> active() {
        return Map.copyOf(ACTIVE);
    }
}
