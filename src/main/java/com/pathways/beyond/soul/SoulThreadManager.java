package com.pathways.beyond.soul;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soul Threads.
 *
 * <p>Every living thing is tied to something. From Sequence 5 (Marionettist) the player can
 * <b>see</b> those threads, and from Sequence 4 can <b>pull</b> them: binding a creature makes
 * it a puppet that obeys, and severing a thread takes something permanent out of the target.
 *
 * <p>The threads themselves are rendered by the client from this server-side state, so a
 * thread that exists in the world is a thread every nearby player can see.
 */
public final class SoulThreadManager {
    private SoulThreadManager() {}

    /** A binding: one entity, one owner, plus how much it is resisting. */
    public static final class Binding {
        public final int entityId;
        public final UUID owner;
        public final Level level;
        /** 0.0 = entirely compliant, 1.0 = about to break free. */
        public float resistance;
        public int age;

        public Binding(int entityId, UUID owner, Level level) {
            this.entityId = entityId;
            this.owner = owner;
            this.level = level;
        }
    }

    /** entityId -> binding, per server lifetime. */
    private static final Map<Integer, Binding> BINDINGS = new HashMap<>();
    /** Players who currently see all threads, with the radius they asked for. */
    private static final Map<UUID, Double> THREAD_SIGHT = new HashMap<>();

    // ===================================================================================
    // Binding
    // ===================================================================================
    public static void bind(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        PlayerPathway pathway = ModAttachments.pathway(player);
        if (pathway.sequence() > 5) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.pathwaysofthebeyond.insufficient_sequence"), true);
            return;
        }
        Binding binding = new Binding(target.getId(), player.getUUID(), level);
        // stronger creatures resist: a rabbit is easy, a Warden is not
        binding.resistance = Math.min(0.9f, target.getMaxHealth() / 60.0f);
        BINDINGS.put(target.getId(), binding);
        target.getPersistentData().putUUID("pathways_puppet_owner", player.getUUID());

        level.playSound(null, target.blockPosition(), ModSounds.ThreadBind.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        ModNetwork.sendVisual(  player, com.pathways.beyond.network.OccultMessages.V_THREAD_BIND,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                1.0f, 0x2F4232, 80);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.pathwaysofthebeyond.thread_bound").withStyle(net.minecraft.ChatFormatting.DARK_GREEN), true);
        sendVisibleThreads(player, 48.0);
    }

    public static void sever(ServerPlayer player, LivingEntity target) {
        Binding binding = BINDINGS.remove(target.getId());
        target.getPersistentData().remove("pathways_puppet_owner");
        ServerLevel level = player.serverLevel();
        level.playSound(null, target.blockPosition(), ModSounds.ThreadSever.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetwork.sendVisual(player, com.pathways.beyond.network.OccultMessages.V_THREAD_SEVER,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1.0f, 0x8C1A24, 60);
        // severing is not free for either party
        float damage = (float) (target.getMaxHealth() * 0.25 + 2.0);
        target.hurt(level.damageSources().magic(), damage);
        ModAttachments.occult(player).addSanity(-1.5f);
        SanitySystem.addCorruption(player, 0.5f);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.pathwaysofthebeyond.thread_severed").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
        if (binding != null) {
            com.pathways.beyond.event.DelayedWorldActions.schedule(level, target.blockPosition(),
                    target.blockPosition(), () -> {
                        if (target.isAlive()) {
                            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, false));
                        }
                    }, 1);
        }
    }

    /** Puppet Command: everything bound to this player attacks the given point. */
    public static void commandPuppets(ServerPlayer player, Vec3 target) {
        List<LivingEntity> puppets = ownedPuppets(player);
        if (puppets.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "No threads are tied to your fingers.").withStyle(net.minecraft.chat.ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        for (LivingEntity puppet : puppets) {
            if (puppet instanceof Mob mob) {
                LivingEntity enemy = level.getEntitiesOfClass(LivingEntity.class,
                                new AABB(target, target).inflate(16.0),
                                e -> e != player && e != puppet && !(e instanceof Player)
                                        && !BINDINGS.containsKey(e.getId()))
                        .stream()
                        .min(Comparator.comparingDouble(e -> e.distanceToSqr(target)))
                        .orElse(null);
                mob.setTarget(enemy);
                if (enemy == null) {
                    mob.getNavigation().moveTo(target.x, target.y, target.z, 1.2);
                }
            }
            level.sendParticles(ModParticles.SoulFlow.get(), puppet.getX(),
                    puppet.getY() + puppet.getBbHeight() * 0.6, puppet.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        }
        level.playSound(null, player.blockPosition(), ModSounds.MarionetteJoint.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    public static List<LivingEntity> ownedPuppets(ServerPlayer player) {
        List<LivingEntity> puppets = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        for (Map.Entry<Integer, Binding> entry : BINDINGS.entrySet()) {
            if (!entry.getValue().owner.equals(player.getUUID())) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                puppets.add(living);
            }
        }
        return puppets;
    }

    // ===================================================================================
    // Thread sight (Seer sequence and above can see what everything is tied to)
    // ===================================================================================
    public static void setThreadSight(ServerPlayer player, boolean enabled, double radius) {
        if (enabled) {
            THREAD_SIGHT.put(player.getUUID(), radius);
        } else {
            THREAD_SIGHT.remove(player.getUUID());
        }
    }

    public static boolean hasThreadSight(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        return THREAD_SIGHT.containsKey(player.getUUID())
                || (pathway.hasPathway() && pathway.sequence() <= 9 && pathway.sequence() >= 0
                && player.getPersistentData().getBoolean("pathways_thread_sight"));
    }

    public static void sendVisibleThreads(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        List<int[]> threads = new ArrayList<>();
        // kind 0 = ambient life thread, 1 = bound puppet, 2 = the player's own tether
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(radius))) {
            if (entity instanceof LivingEntity living && living.isAlive() && living != player) {
                threads.add(new int[]{living.getId(), BINDINGS.containsKey(living.getId()) ? 1 : 0});
                if (threads.size() > 96) {
                    break;   // the network layer is not a firehose
                }
            }
        }
        ModNetwork.sendThreadSync(player, threads);
        player.getPersistentData().putBoolean("pathways_thread_sight", true);
    }

    // ===================================================================================
    // Server tick: puppets behave like puppets
    // ===================================================================================
    public static void serverTick(ServerLevel level) {
        if (BINDINGS.isEmpty()) {
            return;
        }
        List<Integer> dead = new ArrayList<>();
        for (Map.Entry<Integer, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            if (!binding.level.dimension().equals(level.dimension())) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                dead.add(entry.getKey());
                continue;
            }
            binding.age++;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(binding.owner);
            if (owner == null) {
                dead.add(entry.getKey());
                continue;
            }

            // --- the string: distance strains it, distance alone breaks it ------------------
            double distance = living.distanceTo(owner);
            if (distance > 48.0) {
                binding.resistance += 0.02f;
            }
            if (binding.resistance > 1.0f) {
                level.playSound(null, living.blockPosition(), ModSounds.ThreadSever.get(),
                        SoundSource.PLAYERS, 0.8f, 1.2f);
                living.getPersistentData().remove("pathways_puppet_owner");
                dead.add(entry.getKey());
                owner.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "A thread snapped on its own.").withStyle(net.minecraft.chat.ChatFormatting.DARK_RED), true);
                continue;
            }

            // --- puppet movement: a body that is being moved, not moving --------------------
            if (living instanceof Mob mob) {
                if (mob.getTarget() == null && mob.tickCount % 40 == 0) {
                    double angle = (living.tickCount * 7 % 360) * Math.PI / 180.0;
                    double radius = 7.0;
                    BlockPos destination = owner.blockPosition().offset(
                            (int) (Math.cos(angle) * radius), 0, (int) (Math.sin(angle) * radius));
                    mob.getNavigation().moveTo(destination.getX(), destination.getY(), destination.getZ(), 1.0);
                }
                // a puppet does not flee, does not panic, and does not decide anything
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
            }
            // threads shed motes as they pull
            if (living.tickCount % 10 == 0) {
                level.sendParticles(ModParticles.SoulFlow.get(), living.getX(),
                        living.getY() + living.getBbHeight() * 0.65, living.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
            }
            // every puppet is a small drain on the puppeteer's mind
            if (binding.age % 100 == 0) {
                com.pathways.beyond.sanity.SanitySystem.spendSanity(owner, 0.4f);
            }
        }
        for (Integer id : dead) {
            BINDINGS.remove(id);
        }
    }

    public static void onDeath(LivingEntity entity) {
        BINDINGS.remove(entity.getId());
    }

    /** Network entry point for client-requested thread actions. */
    public static void handleAction(ServerPlayer player, String action, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        // range and sequence are re-checked here: the client is never trusted
        if (living.distanceToSqr(player) > 32.0 * 32.0) {
            return;
        }
        switch (action) {
            case "bind" -> bind(player, living);
            case "sever" -> sever(player, living);
            case "command" -> commandPuppets(player, living.position());
            case "inspect" -> inspect(player, living);
            default -> {
            }
        }
    }

    /** Right-clicking a creature with thread sight shows what it is tied to. */
    private static void inspect(ServerPlayer player, LivingEntity living) {
        CompoundTag tag = living.getPersistentData();
        String state = BINDINGS.containsKey(living.getId())
                ? "bound to " + (player.getUUID().equals(BINDINGS.get(living.getId()).owner) ? "you" : "someone else")
                : "unbound";
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                living.getType().getDescription().getString() + ": " + state
                        + " (" + String.format("%.0f", living.getHealth()) + "/"
                        + String.format("%.0f", living.getMaxHealth()) + ")")
                .withStyle(net.minecraft.chat.ChatFormatting.DARK_AQUA), true);
        if (tag.contains("pathways_puppet_owner")) {
            player.serverLevel().sendParticles(ModParticles.SoulFlow.get(), living.getX(),
                    living.getY() + living.getHeight() * 0.6, living.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Every binding that involves a given entity, for rendering on the client. */
    public static boolean isBound(int entityId) {
        return BINDINGS.containsKey(entityId);
    }

    public static Map<Integer, Binding> bindings() {
        return java.util.Collections.unmodifiableMap(BINDINGS);
    }
}
