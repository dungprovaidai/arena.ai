package com.pathways.beyond.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visible Soul Threads.
 *
 * <p>A thread is drawn as a chain of glowing fibre segments that sag under gravity and lag
 * behind the entity's motion, so it reads as a physical object rather than a beam. Threads
 * rattle when the bound creature resists: the segments jitter laterally, which is the visual
 * cue that the binding is about to snap.
 *
 * <p>Rendering is deliberately additive-light only (no solid geometry) so that threads never
 * occlude the thing they are attached to.
 */
public final class ClientThreadRender {
    private ClientThreadRender() {}

    /** id -> client-side thread state */
    private static final Map<Integer, ThreadState> THREADS = new HashMap<>();

    private static final class ThreadState {
        final int entityId;
        int kind;
        float sag;
        float rattle;
        final List<Vec3> points = new ArrayList<>();

        ThreadState(int entityId, int kind) {
            this.entityId = entityId;
            this.kind = kind;
            this.sag = 0.35f;
        }
    }

    public static void accept(CompoundTag tag) {
        ListTag list = tag.getList("threads", CompoundTag.TAG_COMPOUND);
        if (list.isEmpty()) {
            THREADS.clear();
            return;
        }
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int id = entry.getInt("id");
            int kind = entry.getInt("kind");
            seen.add(id);
            ThreadState state = THREADS.get(id);
            if (state == null) {
                state = new ThreadState(id, kind);
                THREADS.put(id, state);
            }
            state.kind = kind;
            state.rattle = entry.contains("rattle") ? entry.getFloat("rattle") : 0.0f;
        }
        THREADS.keySet().removeIf(id -> !seen.contains(id));
    }

    public static boolean hasThread(int entityId) {
        return THREADS.containsKey(entityId);
    }

    public static float rattle(int entityId) {
        ThreadState state = THREADS.get(entityId);
        return state == null ? 0.0f : state.rattle;
    }

    public static List<Vec3> pointsFor(int entityId) {
        ThreadState state = THREADS.get(entityId);
        return state == null ? List.of() : state.points;
    }

    /** Called every client tick from the renderer to update thread geometry. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            THREADS.clear();
            return;
        }
        Vec3 playerPos = minecraft.player == null ? Vec3.ZERO : minecraft.player.position();
        THREADS.values().removeIf(state -> {
            Entity entity = level.getEntity(state.entityId);
            if (!(entity instanceof LivingEntity living)) {
                return true;
            }
            updatePoints(state, living, playerPos);
            return false;
        });
    }

    private static void updatePoints(ThreadState state, LivingEntity entity, Vec3 playerPos) {
        // the thread runs from a point above the player's hand to a point above the bound entity
        Vec3 start = playerPos.add(0.0, 1.4, 0.0);
        Vec3 end = entity.position().add(0.0, entity.getBbHeight() * 0.9, 0.0);
        int segments = 12;
        state.points.clear();
        long time = entity.level().getGameTime();
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 base = start.lerp(end, t);
            // sag: the thread hangs in the middle, because it is a physical fibre
            double sag = Math.sin(t * Math.PI) * state.sag;
            double wobble = Math.sin(time * 0.35 + i * 1.7 + entity.getId()) * 0.03;
            double rattle = state.rattle * Math.sin(time * 1.9 + i * 3.1) * 0.12;
            state.points.add(base.add(wobble, -sag + rattle, wobble * 0.5));
        }
        // the sag springs back when the entity stops pulling
        state.sag += (0.35f - state.sag) * 0.05f;
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
            state.sag = Math.min(0.55f, state.sag + 0.01f);
        }
        state.rattle = Math.max(0.0f, state.rattle * 0.95f);
    }

    public static int threadColour(int entityId) {
        ThreadState state = THREADS.get(entityId);
        int kind = state == null ? 0 : state.kind;
        // colour per thread kind: bound (green), commanded (gold), resisting (crimson)
        return switch (kind) {
            case 1 -> 0xD4B265;
            case 2 -> 0x8C1A24;
            default -> 0x6FA98A;
        };
    }
}
