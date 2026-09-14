package com.pathways.beyond.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Deferred block-state changes.
 *
 * <p>Hallucinations and reality warps both need the same primitive: "change this block, then
 * change it back as if nothing happened". Doing that with a scheduler rather than a per-tick
 * scan keeps the cost proportional to the number of pending changes - and there are never
 * many, because the whole point is that the player is not supposed to notice.
 */
public final class DelayedWorldActions {
    private DelayedWorldActions() {}

    private record Action(BlockPos pos, BlockState state, long executeAt) {}

    private static final Map<String, List<Action>> PENDING = new HashMap<>();

    private static String key(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** Restore a single block after {@code delayTicks}. */
    public static void schedule(ServerLevel level, BlockPos pos, BlockPos ignored, Runnable runnable, int delayTicks) {
        List<Action> list = PENDING.computeIfAbsent(key(level), k -> new ArrayList<>());
        // the runnable form is kept for callers that need side effects (door opening pairs)
        list.add(new Action(pos, null, level.getGameTime() + delayTicks) {
        });
        RUNNABLES.computeIfAbsent(key(level), k -> new ArrayList<>())
                .add(new RunnableEntry(runnable, level.getGameTime() + delayTicks));
    }

    private record RunnableEntry(Runnable runnable, long executeAt) {}

    private static final Map<String, List<RunnableEntry>> RUNNABLES = new HashMap<>();

    /** Restore a batch of blocks (reality warp, failed ritual distortion) after a delay. */
    public static void scheduleBatch(ServerLevel level, List<BlockPos> positions,
                                     List<BlockState> states, int delayTicks) {
        List<RunnableEntry> list = RUNNABLES.computeIfAbsent(key(level), k -> new ArrayList<>());
        List<BlockPos> copyPositions = new ArrayList<>(positions);
        List<BlockState> copyStates = new ArrayList<>(states);
        long at = level.getGameTime() + delayTicks;
        list.add(new RunnableEntry(() -> {
            for (int i = 0; i < copyPositions.size(); i++) {
                level.setBlock(copyPositions.get(i), copyStates.get(i), Block.UPDATE_ALL);
            }
        }, at));
    }

    public static void tick(ServerLevel level) {
        String k = key(level);
        List<RunnableEntry> runnables = RUNNABLES.get(k);
        if (runnables != null && !runnables.isEmpty()) {
            long now = level.getGameTime();
            Iterator<RunnableEntry> iterator = runnables.iterator();
            while (iterator.hasNext()) {
                RunnableEntry entry = iterator.next();
                if (entry.executeAt() <= now) {
                    entry.runnable().run();
                    iterator.remove();
                }
            }
        }
        List<Action> actions = PENDING.get(k);
        if (actions != null && !actions.isEmpty()) {
            long now = level.getGameTime();
            Iterator<Action> iterator = actions.iterator();
            while (iterator.hasNext()) {
                Action action = iterator.next();
                if (action.executeAt() <= now) {
                    if (action.state() != null) {
                        level.setBlock(action.pos(), action.state(), Block.UPDATE_ALL);
                    }
                    iterator.remove();
                }
            }
        }
    }

    /** Drops finished-state bookkeeping so a long-running server does not accumulate maps. */
    public static void cleanup(ServerLevel level) {
        String k = key(level);
        List<RunnableEntry> runnables = RUNNABLES.get(k);
        if (runnables != null && runnables.isEmpty()) {
            RUNNABLES.remove(k);
        }
        List<Action> actions = PENDING.get(k);
        if (actions != null && actions.isEmpty()) {
            PENDING.remove(k);
        }
    }

    /** Number of pending changes - used by the debug command to prove nothing is leaking. */
    public static int pendingCount(ServerLevel level) {
        String k = key(level);
        int total = 0;
        if (PENDING.containsKey(k)) {
            total += PENDING.get(k).size();
        }
        if (RUNNABLES.containsKey(k)) {
            total += RUNNABLES.get(k).size();
        }
        return total;
    }

    public static void clear(ServerLevel level) {
        PENDING.remove(key(level));
        RUNNABLES.remove(key(level));
    }
}
