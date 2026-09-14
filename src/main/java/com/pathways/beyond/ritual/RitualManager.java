package com.pathways.beyond.ritual;

import com.pathways.beyond.item.OccultItems;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.pathway.SequenceLogic;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModBlocks;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ritual engine.
 *
 * <p>A ritual site is not a GUI: it is a place. The player builds it out of an Occult Ritual
 * Altar, chalk, candles and blood, and then stands in it. This class:
 *
 * <ul>
 *   <li>recognises a valid site (altar + candle count + drawn circle size);</li>
 *   <li>tracks a live session per site (stability, danger, elapsed time, celebrants);</li>
 *   <li>runs the theatrical sequence - symbols glow, candles light, smoke rises, the
 *       ambience changes, and then one clear visual resolution;</li>
 *   <li>applies the failure table when it goes wrong, which is often.</li>
 * </ul>
 */
public final class RitualManager {
    private RitualManager() {}

    /** A live ritual. One per site, per level. */
    public static final class Session {
        public final BlockPos site;
        public final RitualRecipes.RitualRecipe recipe;
        public final UUID owner;
        public final long startTick;
        public float stability;
        public float danger;
        public boolean active = true;
        public int pulseTimer;

        Session(BlockPos site, RitualRecipes.RitualRecipe recipe, UUID owner, long now) {
            this.site = site;
            this.recipe = recipe;
            this.owner = owner;
            this.startTick = now;
            this.stability = recipe.baseStability();
            this.danger = recipe.danger();
        }
    }

    /** What the client screen needs to know about a site. */
    public record Snapshot(boolean active, String recipeId, float stability, float danger,
                           List<String> missing, int x, int y, int z) {}

    private static final Map<String, Session> SESSIONS = new HashMap<>();

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    public static void loadRecipes(MinecraftServer server) {
        // Recipes are code-defined; this hook exists so a future datapack layer can add to
        // them. Logging the count at startup makes a broken recipe table obvious.
        com.pathways.beyond.PathwaysMod.LOGGER.info("[Pathways] {} ritual recipes loaded, {} pathway potions indexed",
                RitualRecipes.all().size(), countPotions());
    }

    private static int countPotions() {
        int count = 0;
        for (var holder : ModItems.ITEMS.getEntries()) {
            if (holder.get() instanceof OccultItems.PathwayPotionItem) {
                count++;
            }
        }
        return count;
    }

    // ===================================================================================
    // Site recognition
    // ===================================================================================
    /** Counts the circle and candle components around an altar. */
    public static Site findSite(ServerLevel level, BlockPos altar) {
        int chalk = 0;
        int candles = 0;
        int litCandles = 0;
        int basins = 0;
        for (BlockPos pos : BlockPos.betweenClosed(altar.offset(-6, -3, -6), altar.offset(6, 3, 6))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.ChalkCircle.get())) {
                chalk++;
            } else if (state.is(ModBlocks.RitualCandle.get())
                    || state.getBlock() instanceof CandleBlock) {
                candles++;
                if (state.hasProperty(CandleBlock.LIT) && state.getValue(CandleBlock.LIT)) {
                    litCandles++;
                }
            } else if (state.is(ModBlocks.BloodBasin.get())) {
                basins++;
            }
        }
        return new Site(altar.immutable(), chalk, candles, litCandles, basins);
    }

    /** The physical parts of a ritual site, as found in the world. */
    public record Site(BlockPos altar, int chalkBlocks, int candles, int litCandles, int basins) {
        public boolean suits(RitualRecipes.RitualRecipe recipe) {
            return chalkBlocks >= recipe.requiredCircleBlocks()
                    && litCandles >= recipe.requiredCandles()
                    && basins >= 1;
        }
    }

    /** Finds the nearest valid altar to a player, if any. */
    public static BlockPos nearestAltar(ServerLevel level, ServerPlayer player, int radius) {
        BlockPos centre = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -8, -radius),
                centre.offset(radius, 8, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.RitualAltar.get())) {
                double dist = pos.distSqr(centre);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    // ===================================================================================
    // Session control
    // ===================================================================================
    public static void handle(ServerPlayer player, String action, int x, int y, int z, String recipeId) {
        ServerLevel level = player.serverLevel();
        BlockPos altar = new BlockPos(x, y, z);
        switch (action) {
            case "query" -> sendSnapshot(player, altar);
            case "begin" -> begin(player, altar, recipeId);
            case "abort" -> abort(level, altar, player, true);
            case "offer" -> offer(player, altar);
            default -> {
            }
        }
    }

    public static void sendSnapshot(ServerPlayer player, BlockPos altar) {
        ServerLevel level = player.serverLevel();
        Session session = SESSIONS.get(key(level, altar));
        if (session != null) {
            ModNetwork.sendRitualState(player, new Snapshot(true, session.recipe.id(), session.stability,
                    session.danger, List.of(), altar.getX(), altar.getY(), altar.getZ()));
            return;
        }
        Site site = findSite(level, altar);
        PlayerPathway pathway = ModAttachments.pathway(player);
        List<String> missing = new ArrayList<>();
        List<RitualRecipes.RitualRecipe> available = RitualRecipes.forPlayer(pathway);
        if (available.isEmpty()) {
            missing.add("no ritual available at your sequence");
        }
        for (RitualRecipes.RitualRecipe recipe : available) {
            if (!site.suits(recipe)) {
                missing.add(recipe.id() + ": needs " + recipe.requiredCandles() + " lit candles and "
                        + recipe.requiredCircleBlocks() + " chalk circle blocks");
            }
            for (RitualRecipes.Component component : recipe.components()) {
                if (countItem(player, component.item()) < component.count()) {
                    missing.add(component.note() + " (" + new ItemStack(component.item()).getHoverName().getString()
                            + " x" + component.count() + ")");
                }
            }
        }
        ModNetwork.sendRitualState(player, new Snapshot(false, available.isEmpty() ? "" : available.get(0).id(),
                0.0f, 0.0f, missing, altar.getX(), altar.getY(), altar.getZ()));
    }

    private static int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consume(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        int remaining = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.containerMenu.broadcastChanges();
    }

    public static void begin(ServerPlayer player, BlockPos altar, String recipeId) {
        ServerLevel level = player.serverLevel();
        PlayerPathway pathway = ModAttachments.pathway(player);
        RitualRecipes.RitualRecipe recipe = RitualRecipes.byId(recipeId);
        if (recipe == null || !recipe.isAvailableTo(pathway)) {
            player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.insufficient_sequence")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        Site site = findSite(level, altar);
        if (!site.suits(recipe)) {
            player.displayClientMessage(Component.literal("The circle is incomplete: it needs "
                    + recipe.requiredCandles() + " lit candles, " + recipe.requiredCircleBlocks()
                    + " chalk blocks and a blood basin.").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        for (RitualRecipes.Component component : recipe.components()) {
            if (countItem(player, component.item()) < component.count()) {
                player.displayClientMessage(Component.literal("Missing: " + component.note()
                        + " (" + new ItemStack(component.item()).getHoverName().getString()
                        + " x" + component.count() + ")").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
        }
        for (RitualRecipes.Component component : recipe.components()) {
            consume(player, component.item(), component.count());
        }
        SESSIONS.put(key(level, altar), new Session(altar, recipe, player.getUUID(), level.getGameTime()));
        level.playSound(null, altar, ModSounds.RitualStart.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(altar)) < 40 * 40)) {
            nearby.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.ritual_started")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
        }
        ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_START,
                altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5, 1.0f, 0x9C7C3A, 300);
        sendSnapshot(player, altar);
    }

    public static void abort(ServerLevel level, BlockPos altar, ServerPlayer by, boolean punish) {
        Session session = SESSIONS.remove(key(level, altar));
        if (session == null) {
            return;
        }
        level.playSound(null, altar, ModSounds.RitualFail.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_FAIL,
                altar.getX() + 0.5, altar.getY() + 1.5, altar.getZ() + 0.5, 1.0f, 0x5A0F17, 120);
        if (punish) {
            applyFailure(level, altar, session, by);
        }
    }

    /** Standing on the altar itself is an offering: it steadies the circle. */
    private static void offer(ServerPlayer player, BlockPos altar) {
        ServerLevel level = player.serverLevel();
        Session session = SESSIONS.get(key(level, altar));
        if (session == null) {
            return;
        }
        float cost = player.getHealth() * 0.15f;
        player.hurt(level.damageSources().magic(), cost);
        session.stability = Math.min(100.0f, session.stability + 12.0f);
        level.playSound(null, altar, ModSounds.DaggerCut.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        level.sendParticles(ModParticles.BloodDrop.get(), altar.getX() + 0.5, altar.getY() + 1.2,
                altar.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.03);
    }

    /** Deep Ritual (Attendant of Mysteries): push an active ritual to completion. */
    public static boolean deepRitual(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Map.Entry<String, Session> entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            if (session.owner.equals(player.getUUID())
                    && player.distanceToSqr(Vec3.atCenterOf(session.site)) < 100.0) {
                session.stability = Math.min(100.0f, session.stability + 45.0f);
                level.playSound(null, session.site, ModSounds.RitualPulse.get(),
                        SoundSource.BLOCKS, 1.0f, 1.2f);
                return true;
            }
        }
        return false;
    }

    public static boolean isPlayerInActiveRitual(ServerPlayer player) {
        for (Session session : SESSIONS.values()) {
            if (session.owner.equals(player.getUUID())
                    && player.distanceToSqr(Vec3.atCenterOf(session.site)) < 36.0) {
                return true;
            }
        }
        return false;
    }

    // ===================================================================================
    // Server tick: the theatrical sequence itself
    // ===================================================================================
    public static void serverTick(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        List<String> finished = new ArrayList<>();
        for (Map.Entry<String, Session> entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            if (!entry.getKey().startsWith(level.dimension().location().toString())) {
                continue;
            }
            BlockPos altar = session.site;
            long elapsed = level.getGameTime() - session.startTick;
            float progress = Math.min(1.0f, elapsed / (float) session.recipe.durationTicks());
            List<ServerPlayer> participants = level.getPlayers(p ->
                    p.distanceToSqr(Vec3.atCenterOf(altar)) < 64.0);

            // --- the circle only holds while someone is inside it -------------------------
            if (participants.isEmpty()) {
                session.stability -= 1.2f;
            } else {
                boolean celebrantPresent = participants.stream().anyMatch(p -> p.getUUID().equals(session.owner));
                if (!celebrantPresent) {
                    session.stability -= 0.9f;
                }
                // a ward charm or clarity helps; open combat does not
                for (ServerPlayer p : participants) {
                    if (p.hurtTime > 0) {
                        session.stability -= 0.6f;
                    }
                    if (p.hasEffect(ModEffects.Clarity)) {
                        session.stability += 0.25f;
                    }
                }
                session.stability -= session.recipe.danger() * 0.35f;
                session.stability += 0.5f * participants.size() * 0.25f;
            }

            // --- stage 1..4: symbols, candles, smoke, ambience ----------------------------
            session.pulseTimer++;
            if (session.pulseTimer % 20 == 0) {
                level.playSound(null, altar, ModSounds.RitualLoop.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            }
            if (session.pulseTimer % 30 == 0) {
                level.playSound(null, altar, ModSounds.RitualPulse.get(), SoundSource.BLOCKS, 0.5f,
                        0.9f + progress * 0.4f);
                for (int i = 0; i < 24; i++) {
                    double angle = i / 24.0 * Math.PI * 2.0;
                    double radius = 3.0 + progress * 3.0;
                    level.sendParticles(ModParticles.RuneDust.get(),
                            altar.getX() + 0.5 + Math.cos(angle) * radius, altar.getY() + 1.1,
                            altar.getZ() + 0.5 + Math.sin(angle) * radius, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
            if (session.pulseTimer % 10 == 0) {
                level.sendParticles(ModParticles.EmberOccult.get(), altar.getX() + 0.5,
                        altar.getY() + 1.3, altar.getZ() + 0.5, 4, 0.9, 0.4, 0.9, 0.01);
                level.sendParticles(ModParticles.VeilSmoke.get(), altar.getX() + 0.5,
                        altar.getY() + 0.6, altar.getZ() + 0.5, 3, 2.0, 0.2, 2.0, 0.01);
            }

            // --- stage 5: entities start noticing -----------------------------------------
            if (progress > 0.65f && level.getRandom().nextFloat() < 0.06f * session.recipe.danger()) {
                Entity intruder = (level.getRandom().nextBoolean()
                        ? ModEntities.Hollow.get() : ModEntities.WhisperingHusk.get()).create(level);
                if (intruder != null) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                    intruder.moveTo(altar.getX() + Math.cos(angle) * 8.0, altar.getY() + 1.0,
                            altar.getZ() + Math.sin(angle) * 8.0, level.getRandom().nextFloat() * 360.0f, 0.0f);
                    level.addFreshEntity(intruder);
                }
            }

            // --- resolution -----------------------------------------------------------------
            ServerPlayer celebrant = level.getServer().getPlayerList().getPlayer(session.owner);
            if (progress >= 1.0f) {
                finished.add(entry.getKey());
                if (session.stability <= 0.0f) {
                    abort(level, altar, celebrant, true);
                    continue;
                }
                complete(level, altar, session, celebrant);
            } else if (session.stability <= 0.0f) {
                finished.add(entry.getKey());
                abort(level, altar, celebrant, true);
            }
        }
        for (String done : finished) {
            SESSIONS.remove(done);
        }
    }

    private static void complete(ServerLevel level, BlockPos altar, Session session, ServerPlayer celebrant) {
        RitualRecipes.RitualRecipe recipe = session.recipe;
        level.playSound(null, altar, ModSounds.RitualComplete.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ModParticles.SpiritMote.get(), altar.getX() + 0.5, altar.getY() + 2.0,
                altar.getZ() + 0.5, 80, 1.2, 1.6, 1.2, 0.06);
        ModNetwork.sendVisualToAll(level, OccultMessages.V_RITUAL_COMPLETE,
                altar.getX() + 0.5, altar.getY() + 1.5, altar.getZ() + 0.5, 1.0f, 0xEBD08A, 160);

        if (celebrant == null) {
            return;
        }
        SequenceLogic.onRitualComplete(celebrant, recipe.id());
        celebrant.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.ritual_complete")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);

        switch (recipe.outcome()) {
            case BREW_POTION -> {
                ItemStack potion = RitualRecipes.potionStack(ModAttachments.pathway(celebrant).pathwayId(),
                        recipe.targetSequence());
                if (!potion.isEmpty()) {
                    if (!celebrant.getInventory().add(potion)) {
                        celebrant.drop(potion, false);
                    }
                    level.playSound(null, altar, ModSounds.PotionFinish.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
                }
            }
            case ADVANCE_SEQUENCE -> {
                SequenceLogic.advance(celebrant, recipe.targetSequence(), true);
            }
            case UNSEAL_ARTIFACT -> {
                ItemStack artifact = switch (recipe.id()) {
                    case "unseal_the_eye" -> new ItemStack(ModItems.EyeOfSolomon.get());
                    case "unseal_the_book" -> new ItemStack(ModItems.BlackBook.get());
                    case "unseal_the_bell" -> new ItemStack(ModItems.WhisperingBell.get());
                    default -> new ItemStack(ModItems.BrassKey.get());
                };
                OccultItems.SealedArtifactStack.setSealed(artifact, false);
                if (!celebrant.getInventory().add(artifact)) {
                    celebrant.drop(artifact, false);
                }
                level.playSound(null, altar, ModSounds.ArtifactActivate.get(),
                        SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            case REVEAL_KNOWLEDGE -> {
                PlayerPathway pathway = ModAttachments.pathway(celebrant);
                pathway.learnRitual(recipe.id());
                OccultState state = ModAttachments.occult(celebrant);
                state.addSanity(-4.0f);
                celebrant.addEffect(new MobEffectInstance(ModEffects.Clarity, 1200, 0, false, false));
                ModNetwork.sendPathwaySync(celebrant);
            }
            case OPEN_SPIRIT_GATE -> {
                com.pathways.beyond.spirit.SpiritWorld.openGate(level, altar, celebrant);
            }
            default -> {
            }
        }
        ModNetwork.sendRitualState(celebrant, new Snapshot(false, recipe.id(), 100.0f, 0.0f,
                List.of(), altar.getX(), altar.getY(), altar.getZ()));
    }

    // ===================================================================================
    // Failure: the part players remember
    // ===================================================================================
    private static void applyFailure(ServerLevel level, BlockPos altar, Session session, ServerPlayer celebrant) {
        float danger = session.recipe.danger();
        AABB area = new AABB(altar).inflate(8.0);
        List<ServerPlayer> victims = level.getEntitiesOfClass(ServerPlayer.class, area);

        // 1. sanity loss, always
        for (ServerPlayer victim : victims) {
            OccultState state = ModAttachments.occult(victim);
            state.addSanity(-(8.0f + danger * 14.0f));
            SanitySystem.addCorruption(victim, 1.5f + danger * 3.0f);
            victim.addEffect(new MobEffectInstance(ModEffects.Hallucinating, (int) (400 * danger) + 200, 0));
            victim.addEffect(new MobEffectInstance(ModEffects.CurseOfTheVeil, (int) (600 * danger) + 200, 0));
            victim.hurt(level.damageSources().magic(), 4.0f * danger);
            ModNetwork.sendHallucination(victim, com.pathways.beyond.network.OccultMessages.H_FOG_SHIFT,
                    victim.getX(), victim.getY(), victim.getZ(), 1.0f, 300, "ritual_fail");
        }

        // 2. a hostile entity, spawned where the player is standing
        if (danger > 0.3f) {
            Mob spawn = (danger > 0.7f ? ModEntities.VeilTenant.get() : ModEntities.WhisperingHusk.get())
                    .create(level);
            if (spawn != null) {
                spawn.moveTo(altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5, 0.0f, 0.0f);
                level.addFreshEntity(spawn);
            }
        }

        // 3. an explosion that does not break blocks - only the player's certainty
        if (danger > 0.5f) {
            level.explode(null, altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5,
                    2.0f, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        }

        // 4. temporary world distortion: blocks around the altar briefly become wrong
        List<BlockPos> changed = new ArrayList<>();
        List<BlockState> originals = new ArrayList<>();
        for (int i = 0; i < (int) (20 * danger) + 6; i++) {
            BlockPos pos = altar.offset(level.getRandom().nextInt(11) - 5,
                    level.getRandom().nextInt(5) - 1, level.getRandom().nextInt(11) - 5);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            changed.add(pos.immutable());
            originals.add(state);
            level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
        com.pathways.beyond.event.DelayedWorldActions.scheduleBatch(level, changed, originals,
                200 + (int) (danger * 200));

        // 5. the candles go out, which somehow feels worse than the explosion
        for (BlockPos pos : BlockPos.betweenClosed(altar.offset(-6, -3, -6), altar.offset(6, 3, 6))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CandleBlock && state.getValue(CandleBlock.LIT)) {
                level.setBlock(pos, state.setValue(CandleBlock.LIT, false), Block.UPDATE_ALL);
            }
        }
        level.playSound(null, altar, ModSounds.RitualFail.get(), SoundSource.BLOCKS, 1.0f, 0.85f);
    }

    /** Sites ticked for all levels; called from the level tick hook. */
    public static void tickAll(ServerLevel level) {
        serverTick(level);
    }

    /** Drops every session belonging to a level - used when a level unloads. */
    public static void clearLevel(ServerLevel level) {
        SESSIONS.entrySet().removeIf(e -> e.getKey().startsWith(level.dimension().location().toString()));
    }

    /** Read-only view for the debug command. */
    public static Map<String, Session> sessions() {
        return java.util.Collections.unmodifiableMap(SESSIONS);
    }

    /** Serialised session state, so a server restart does not silently eat a live ritual. */
    public static CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Session> entry : SESSIONS.entrySet()) {
            CompoundTag sessionTag = new CompoundTag();
            sessionTag.putLong("start", entry.getValue().startTick);
            sessionTag.putFloat("stability", entry.getValue().stability);
            sessionTag.putString("recipe", entry.getValue().recipe.id());
            sessionTag.putLong("site", entry.getValue().site.asLong());
            sessionTag.putUUID("owner", entry.getValue().owner);
            tag.put(entry.getKey(), sessionTag);
        }
        return tag;
    }
}
