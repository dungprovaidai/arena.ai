package com.pathways.beyond.pathway;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModEntities;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;
import com.pathways.beyond.soul.SoulThreadManager;
import com.pathways.beyond.spirit.SoulProjection;

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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The ability library.
 *
 * <p>Every ability obeys the same contract:
 * <ul>
 *   <li>it costs Sanity (always) and usually Corruption (usually small);</li>
 *   <li>it has a cooldown stored on the player, so it survives a relog;</li>
 *   <li>it produces its own VFX signature - no two abilities share a particle recipe;</li>
 *   <li>it is validated server-side; the client only ever asks.</li>
 * </ul>
 *
 * <p>Ability ids are defined in {@link PathwayData} (generated), so the Pathway screen,
 * the network layer and this file cannot drift apart.
 */
public final class Abilities {
    private Abilities() {}

    public record Ability(String id, String display, int sequence, String pathway,
                          float sanityCost, float corruptionCost, int cooldownTicks,
                          BiConsumer<ServerPlayer, Vec3> effect) {}

    private static final Map<String, Ability> REGISTRY = new LinkedHashMap<>();

    public static Map<String, Ability> all() {
        return REGISTRY;
    }

    public static Ability get(String id) {
        return REGISTRY.get(id);
    }

    public static List<Ability> forPathwayAndSequence(String pathway, int sequence) {
        return REGISTRY.values().stream()
                .filter(a -> a.pathway().equals(pathway) && a.sequence() == sequence)
                .toList();
    }

    // ===================================================================================
    // Client request -> server execution
    // ===================================================================================
    public static void request(ServerPlayer player, String abilityId) {
        Ability ability = REGISTRY.get(abilityId);
        if (ability == null) {
            return;
        }
        PlayerPathway pathway = ModAttachments.pathway(player);
        OccultState state = ModAttachments.occult(player);

        if (!pathway.hasPathway() || !pathway.pathwayId().equals(ability.pathway())) {
            reject(player, "message.pathwaysofthebeyond.insufficient_sequence");
            return;
        }
        // abilities unlock as the sequence number decreases
        if (pathway.sequence() > ability.sequence()) {
            reject(player, "message.pathwaysofthebeyond.insufficient_sequence");
            return;
        }
        if (pathway.cooldown(abilityId) > 0) {
            ModNetwork.sendAbilityFeedback(player, "cooldown", pathway.cooldown(abilityId));
            return;
        }
        // Corruption makes power cheaper and Sanity more fragile: the standing bargain
        float sanityCost = ability.sanityCost()
                / SanitySystem.corruptionPowerMultiplier(state.corruption());
        if (!SanitySystem.spendSanity(player, sanityCost)) {
            reject(player, "message.pathwaysofthebeyond.lost_control");
            return;
        }
        if (ability.corruptionCost() > 0.0f) {
            SanitySystem.addCorruption(player, ability.corruptionCost());
        }
        pathway.setCooldown(abilityId, ability.cooldownTicks());

        Vec3 target = player.pick(32.0, 0.0f, false).getLocation();
        ability.effect().accept(player, target);

        ModNetwork.sendVisual(player, OccultMessages.V_ABILITY_CAST,
                player.getX(), player.getY() + 1.0, player.getZ(), 0.8f,
                pathway.definition().colour(), 30);
        ModNetwork.sendOccultSync(player);
        ModNetwork.sendPathwaySync(player);
    }

    private static void reject(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key)
                .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        ModNetwork.sendAbilityFeedback(player, key, 0);
    }

    // ===================================================================================
    // Registration
    // ===================================================================================
    private static void add(String id, String display, int sequence, float sanity, float corruption,
                            int cooldown, BiConsumer<ServerPlayer, Vec3> effect) {
        REGISTRY.put(id, new Ability(id, display, sequence, "fool", sanity, corruption, cooldown, effect));
    }

    static {
        // ---- Sequence 9: Seer - the pathway's promise: you will see more -----------------
        add("supernatural_sight", "Supernatural Sight", 9, 4.0f, 0.2f, 200, (player, target) -> {
            ServerLevel level = player.serverLevel();
            AABB area = player.getBoundingBox().inflate(32.0);
            int revealed = 0;
            for (Entity entity : level.getEntities(player, area)) {
                if (entity instanceof LivingEntity living
                        && SanitySystem.supernaturalWeight(living) > 0.0f) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                    revealed++;
                }
            }
            // the Eye's signature: a thin brass ring that expands and fades
            level.sendParticles(ModParticles.RuneDust.get(), player.getX(), player.getY() + 1.2, player.getZ(),
                    28, 1.6, 0.3, 1.6, 0.02);
            level.playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                    SoundSource.PLAYERS, 0.7f, 1.35f);
            if (revealed > 0) {
                player.displayClientMessage(Component.literal(revealed + " presence(s) marked.")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            }
        });

        add("ore_sense", "Ore Sense", 9, 3.0f, 0.1f, 160, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos origin = player.blockPosition();
            int found = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -12, -12), origin.offset(12, 12, 12))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(net.minecraft.tags.BlockTags.IRON_ORES) || state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
                        || state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)
                        || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)
                        || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)
                        || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
                        || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                        || state.is(ModBlocksVoidStone())) {
                    level.sendParticles(ModParticles.RuneDust.get(), pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.005);
                    found++;
                    if (found > 120) {
                        break;   // never spam particles: hard cap
                    }
                }
            }
            level.playSound(null, player.blockPosition(), ModSounds.ChalkDraw.get(), SoundSource.PLAYERS, 0.5f, 1.6f);
        });

        add("danger_premonition", "Danger Premonition", 9, 3.0f, 0.0f, 240, (player, target) -> {
            ServerLevel level = player.serverLevel();
            List<Monster> threats = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(24.0));
            if (threats.isEmpty()) {
                player.displayClientMessage(Component.literal("Nothing is moving towards you. Yet.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            } else {
                Vec3 average = Vec3.ZERO;
                for (Monster mob : threats) {
                    average = average.add(mob.position());
                }
                average = average.scale(1.0 / threats.size());
                Vec3 delta = average.subtract(player.position());
                String dir = directionWord(delta);
                player.displayClientMessage(Component.literal(threats.size() + " hostile(s), " + dir)
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, false));
            level.playSound(null, player.blockPosition(), ModSounds.Heartbeat.get(), SoundSource.PLAYERS, 0.5f, 1.2f);
        });

        add("perception", "Perception", 9, 2.0f, 0.05f, 400, (player, target) -> {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0, false, false));
            player.serverLevel().sendParticles(ModParticles.SpiritMote.get(), player.getX(),
                    player.getY() + 2.2, player.getZ(), 12, 0.4, 0.2, 0.4, 0.01);
        });

        // ---- Sequence 8: Clown - the body becomes a joke told to physics ------------------
        add("unnatural_agility", "Unnatural Agility", 8, 3.0f, 0.2f, 300, (player, target) -> {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 300, 2, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
            player.serverLevel().sendParticles(ModParticles.ShadowMove.get(), player.getX(),
                    player.getY(), player.getZ(), 14, 0.5, 0.2, 0.5, 0.03);
        });

        add("fold_step", "Fold Step", 8, 4.0f, 0.4f, 120, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos from = player.blockPosition();
            BlockPos to = SanitySystem.safeLanding(level, player, 6.0);
            foldTeleport(player, level, from, to, 0.6f);
        });

        add("dodge_instinct", "Dodge Instinct", 8, 3.0f, 0.3f, 160, (player, target) -> {
            // the dodge happens before the blow exists: brief, total evasion
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false));
            player.invulnerableTime = 30;
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(), ModSounds.MarionetteJoint.get(),
                    SoundSource.PLAYERS, 0.6f, 1.5f);
            level.sendParticles(ModParticles.ChromaticSpeck.get(), player.getX(), player.getY() + 1.0,
                    player.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        });

        add("combat_prediction", "Combat Prediction", 8, 4.0f, 0.3f, 200, (player, target) -> {
            ServerLevel level = player.serverLevel();
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(16.0), e -> e != player && e instanceof Monster);
            for (LivingEntity living : nearby) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                // predicted: the target's attack cooldown is exposed to the player
                if (living instanceof Mob mob && mob.getTarget() != player) {
                    mob.setTarget(player);   // and it is provoked in exactly the way it would be
                }
            }
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0, false, false));
            level.playSound(null, player.blockPosition(), ModSounds.WatcherStare.get(),
                    SoundSource.PLAYERS, 0.6f, 1.2f);
        });

        // ---- Sequence 7: Magician - misdirection made literal -----------------------------
        add("stage_illusion", "Stage Illusion", 7, 5.0f, 0.4f, 220, (player, target) -> {
            ServerLevel level = player.serverLevel();
            var decoy = ModEntities.BizarroClone.get().create(level);
            if (decoy != null) {
                decoy.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
                decoy.setOwner(player);
                decoy.setDecoy(true);
                level.addFreshEntity(decoy);
            }
            level.sendParticles(ModParticles.VeilSmoke.get(), player.getX(), player.getY() + 0.2,
                    player.getZ(), 20, 0.8, 0.2, 0.8, 0.02);
            level.playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                    SoundSource.PLAYERS, 0.7f, 1.5f);
        });

        add("blink", "Blink", 7, 5.0f, 0.5f, 140, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos to = SanitySystem.safeLanding(level, player, 14.0);
            foldTeleport(player, level, player.blockPosition(), to, 1.0f);
        });

        add("decoy_double", "Decoy Double", 7, 6.0f, 0.6f, 320, (player, target) -> {
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < 2; i++) {
                var doubleEntity = ModEntities.BizarroClone.get().create(level);
                if (doubleEntity != null) {
                    doubleEntity.moveTo(player.getX() + (i == 0 ? 1.5 : -1.5), player.getY(), player.getZ(),
                            player.getYRot() + 180.0f, 0.0f);
                    doubleEntity.setOwner(player);
                    doubleEntity.setDecoy(true);
                    level.addFreshEntity(doubleEntity);
                }
            }
            level.playSound(null, player.blockPosition(), ModSounds.MarionetteJoint.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        });

        add("perception_rewrite", "Perception Rewrite", 7, 6.0f, 0.8f, 300, (player, target) -> {
            ServerLevel level = player.serverLevel();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0))) {
                mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1, false, false));
            }
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
            level.sendParticles(ModParticles.ChromaticSpeck.get(), player.getX(), player.getY() + 1.0,
                    player.getZ(), 24, 1.0, 0.8, 1.0, 0.02);
        });

        // ---- Sequence 6: Faceless ---------------------------------------------------------
        add("wear_face", "Wear a Face", 6, 6.0f, 0.8f, 600, (player, target) -> {
            ServerLevel level = player.serverLevel();
            List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(12.0), e -> e != player && !(e instanceof Player));
            OccultState state = ModAttachments.occult(player);
            if (candidates.isEmpty()) {
                state.setDisguise("");
                player.displayClientMessage(Component.literal("Nothing humanoid nearby to borrow from.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            } else {
                LivingEntity source = candidates.stream()
                        .min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElseThrow();
                state.setDisguise(source.getType().getDescription().getString());
                player.displayClientMessage(Component.literal("You are wearing a "
                        + state.disguise() + "'s face.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0))) {
                    if (mob.getType() == source.getType()) {
                        mob.setTarget(null);
                    }
                }
            }
            level.playSound(null, player.blockPosition(), ModSounds.FacelessShift.get(),
                    SoundSource.PLAYERS, 0.7f, 1.0f);
            level.sendParticles(ModParticles.BlackWisp.get(), player.getX(), player.getY() + 1.4,
                    player.getZ(), 18, 0.5, 0.5, 0.5, 0.01);
        });

        add("borrowed_mien", "Borrowed Mien", 6, 5.0f, 0.6f, 400, (player, target) -> {
            ServerLevel level = player.serverLevel();
            // mobs of the same kind as your current face simply do not see you
            OccultState state = ModAttachments.occult(player);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0))) {
                if (mob.getTarget() == player
                        && (state.disguise().isEmpty()
                        || mob.getType().getDescription().getString().equals(state.disguise()))) {
                    mob.setTarget(null);
                }
            }
            player.addEffect(new MobEffectInstance(ModEffects.Clarity, 200, 0, false, false));
            level.sendParticles(ModParticles.VeilSmoke.get(), player.getX(), player.getY(),
                    player.getZ(), 16, 0.6, 0.2, 0.6, 0.01);
        });

        add("feature_shift", "Feature Shift", 6, 5.0f, 0.5f, 300, (player, target) -> {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, false, false));
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.FacelessShift.get(),
                    SoundSource.PLAYERS, 0.5f, 1.3f);
        });

        add("mask_of_nobody", "Mask of Nobody", 6, 8.0f, 1.2f, 900, (player, target) -> {
            ServerLevel level = player.serverLevel();
            // for thirty seconds, nothing can hold a target: not you, not anything
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(28.0))) {
                mob.setTarget(null);
            }
            player.addEffect(new MobEffectInstance(ModEffects.Clarity, 600, 0, false, false));
            level.sendParticles(ModParticles.BlackWisp.get(), player.getX(), player.getY() + 1.0,
                    player.getZ(), 40, 1.2, 1.0, 1.2, 0.02);
            level.playSound(null, player.blockPosition(), ModSounds.WatcherVanish.get(),
                    SoundSource.PLAYERS, 0.6f, 0.9f);
        });

        // ---- Sequence 5: Marionettist - the thread becomes a leash ------------------------
        add("soul_thread_view", "Soul Thread View", 5, 2.0f, 0.1f, 200, (player, target) -> {
            OccultState state = ModAttachments.occult(player);
            boolean nowOn = !player.getPersistentData().getBoolean("pathways_thread_sight");
            player.getPersistentData().putBoolean("pathways_thread_sight", nowOn);
            SoulThreadManager.sendVisibleThreads(player, 48.0);
            player.displayClientMessage(Component.literal(nowOn
                            ? "The threads are visible. Every living thing is tied to something."
                            : "The threads fade.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA), true);
            if (nowOn) {
                state.addSanity(-2.0f);
            }
        });

        add("bind_puppet", "Bind Puppet", 5, 7.0f, 1.0f, 200, (player, target) -> {
            LivingEntity victim = nearestLiving(player, 16.0);
            if (victim == null) {
                player.displayClientMessage(Component.literal("Nothing to bind.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                return;
            }
            SoulThreadManager.bind(player, victim);
        });

        add("puppet_command", "Puppet Command", 5, 4.0f, 0.6f, 120, (player, target) -> {
            SoulThreadManager.commandPuppets(player, target);
        });

        add("sever_thread", "Sever Thread", 5, 6.0f, 0.8f, 160, (player, target) -> {
            LivingEntity victim = nearestLiving(player, 20.0);
            if (victim != null) {
                SoulThreadManager.sever(player, victim);
            }
        });

        // ---- Sequence 4: Bizarro Sorcerer --------------------------------------------------
        add("bizarro_copy", "Bizarro Copy", 4, 8.0f, 1.4f, 400, (player, target) -> {
            ServerLevel level = player.serverLevel();
            var clone = ModEntities.BizarroClone.get().create(level);
            if (clone != null) {
                clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
                clone.setOwner(player);
                clone.setDecoy(false);
                level.addFreshEntity(clone);
            }
            level.sendParticles(ModParticles.BeyondShard.get(), player.getX(), player.getY() + 1.2,
                    player.getZ(), 20, 0.8, 0.8, 0.8, 0.02);
            level.playSound(null, player.blockPosition(), ModSounds.RitualPulse.get(),
                    SoundSource.PLAYERS, 0.8f, 1.3f);
        });

        add("reality_warp", "Reality Warp", 4, 10.0f, 2.0f, 500, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos centre = player.blockPosition();
            List<BlockPos> changed = new ArrayList<>();
            List<BlockState> originals = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                BlockPos pos = centre.offset(player.getRandom().nextInt(9) - 4,
                        player.getRandom().nextInt(5) - 2, player.getRandom().nextInt(9) - 4);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
                    continue;
                }
                BlockState replacement = state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                        ? com.pathways.beyond.registry.ModBlocks.DesecratedStone.get().defaultBlockState()
                        : state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                        ? com.pathways.beyond.registry.ModBlocks.VoidStone.get().defaultBlockState()
                        : null;
                if (replacement == null) {
                    continue;
                }
                changed.add(pos.immutable());
                originals.add(state);
                level.setBlock(pos, replacement, Block.UPDATE_ALL);
            }
            com.pathways.beyond.event.DelayedWorldActions.scheduleBatch(level, changed, originals, 100);
            level.playSound(null, centre, ModSounds.RitualFail.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
            ModNetwork.sendVisual(player, OccultMessages.V_MIRACLE, player.getX(), player.getY(),
                    player.getZ(), 1.0f, 0x7B5AA0, 100);
        });

        add("twisted_mirage", "Twisted Mirage", 4, 8.0f, 1.2f, 380, (player, target) -> {
            ServerLevel level = player.serverLevel();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(18.0))) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false));
            }
            level.sendParticles(ModParticles.BeyondShard.get(), player.getX(), player.getY() + 1.5,
                    player.getZ(), 18, 2.0, 1.0, 2.0, 0.01);
        });

        add("argument_of_copies", "Argument of Copies", 4, 10.0f, 2.0f, 600, (player, target) -> {
            ServerLevel level = player.serverLevel();
            List<Entity> copies = level.getEntities(player, player.getBoundingBox().inflate(32.0),
                    e -> e.getType() == ModEntities.BizarroClone.get());
            float damage = 4.0f + copies.size() * 2.0f;
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(8.0), e -> e != player && e instanceof Monster)) {
                living.hurt(level.damageSources().magic(), damage);
                living.push(0, 0.4, 0);
            }
            level.playSound(null, player.blockPosition(), ModSounds.RitualPulse.get(),
                    SoundSource.PLAYERS, 0.9f, 0.8f);
        });

        // ---- Sequence 3: Scholar of Yore ---------------------------------------------------
        add("read_the_stone", "Read the Stone", 3, 5.0f, 0.5f, 300, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos pos = BlockPos.containing(target);
            // the place remembers: an echo of what happened here, rendered and heard
            level.playSound(null, pos, ModSounds.MemoryEcho.get(), SoundSource.AMBIENT, 0.8f, 1.0f);
            level.sendParticles(ModParticles.SpiritMote.get(), pos.getX() + 0.5, pos.getY() + 1.0,
                    pos.getZ() + 0.5, 30, 1.5, 1.0, 1.5, 0.02);
            ModNetwork.sendVisual(player, OccultMessages.V_SPIRIT_ENTER, pos.getX(), pos.getY(), pos.getZ(),
                    0.7f, 0xA8DCE2, 120);
            com.pathways.beyond.event.MemoryEchoRuntime.replay(level, pos, player);
        });

        add("memory_echo_runtime", "Memory Echo", 3, 8.0f, 1.0f, 500, (player, target) -> {
            com.pathways.beyond.event.MemoryEchoRuntime.replayNearby(player, 32.0);
        });

        add("echo_summon", "Echo Summon", 3, 9.0f, 1.5f, 600, (player, target) -> {
            ServerLevel level = player.serverLevel();
            LivingEntity source = nearestLiving(player, 24.0);
            var echo = ModEntities.HistoricalEcho.get().create(level);
            if (echo != null) {
                echo.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
                echo.setOwner(player.getUUID());
                if (source != null) {
                    echo.setEchoType(source.getType());
                }
                level.addFreshEntity(echo);
            }
            level.playSound(null, player.blockPosition(), ModSounds.SpiritEnter.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        });

        add("rewind_moment", "Rewind Moment", 3, 12.0f, 2.0f, 1200, (player, target) -> {
            OccultState state = ModAttachments.occult(player);
            if (state.rememberAge() < 60) {
                player.displayClientMessage(Component.literal("There is nothing to rewind to yet.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                return;
            }
            ServerLevel level = player.serverLevel();
            level.sendParticles(ModParticles.RuneDust.get(), player.getX(), player.getY() + 1.0,
                    player.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
            player.teleportTo(level, state.rememberX(), state.rememberY(), state.rememberZ(),
                    player.getYRot(), player.getXRot());
            player.setHealth(Math.max(2.0f, state.rememberHealth()));
            level.playSound(null, player.blockPosition(), ModSounds.SpiritExit.get(),
                    SoundSource.PLAYERS, 0.9f, 0.8f);
        });

        // ---- Sequence 2: Miracle Invoker ----------------------------------------------------
        add("small_miracle", "Small Miracle", 2, 10.0f, 2.5f, 800, (player, target) -> {
            ServerLevel level = player.serverLevel();
            player.heal(8.0f);
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1, false, false));
            level.playSound(null, player.blockPosition(), ModSounds.SequenceAdvance.get(),
                    SoundSource.PLAYERS, 0.7f, 1.4f);
            ModNetwork.sendVisual(player, OccultMessages.V_MIRACLE, player.getX(), player.getY() + 1.0,
                    player.getZ(), 1.0f, 0xEBD08A, 80);
        });

        add("wish_lantern", "Wish Lantern", 2, 6.0f, 1.5f, 400, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition().above(2);
            BlockState previous = level.getBlockState(pos);
            if (previous.isAir()) {
                level.setBlock(pos, com.pathways.beyond.registry.ModBlocks.SpiritLantern.get()
                        .defaultBlockState(), Block.UPDATE_ALL);
                com.pathways.beyond.event.DelayedWorldActions.schedule(level, pos, pos,
                        () -> level.setBlock(pos, previous, Block.UPDATE_ALL), 2400);
                player.displayClientMessage(Component.literal("You asked for light. Something answered.")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
        });

        add("rainstorm_of_quiet", "Rainstorm of Quiet", 2, 8.0f, 2.0f, 900, (player, target) -> {
            ServerLevel level = player.serverLevel();
            // an area where nothing can make a sound: the opposite of a scream
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0))) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 2, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1, false, false));
                mob.setSilent(true);
            }
            level.sendParticles(ModParticles.VeilSmoke.get(), player.getX(), player.getY() + 0.3,
                    player.getZ(), 50, 6.0, 0.4, 6.0, 0.01);
            level.playSound(null, player.blockPosition(), ModSounds.RitualComplete.get(),
                    SoundSource.AMBIENT, 0.5f, 0.7f);
        });

        add("debt_of_miracles", "Debt of Miracles", 2, 20.0f, 4.0f, 2400, (player, target) -> {
            OccultState state = ModAttachments.occult(player);
            state.setMiracleDebt(true);
            player.displayClientMessage(Component.literal("A favour has been granted in advance. "
                            + "The ledger is aware of you now.").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            SanitySystem.addCorruption(player, 6.0f);
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.BellAnswer.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        });

        // ---- Sequence 1: Attendant of Mysteries --------------------------------------------
        add("forbidden_ward", "Forbidden Ward", 1, 8.0f, 1.0f, 600, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos centre = player.blockPosition();
            for (int i = 0; i < 36; i++) {
                double angle = i / 36.0 * Math.PI * 2.0;
                level.sendParticles(ModParticles.RuneDust.get(),
                        centre.getX() + 0.5 + Math.cos(angle) * 6.0, centre.getY() + 0.3,
                        centre.getZ() + 0.5 + Math.sin(angle) * 6.0, 2, 0.05, 0.05, 0.05, 0.0);
            }
            for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(6.0))) {
                if (SanitySystem.supernaturalWeight(entity) > 0.0f) {
                    Vec3 push = entity.position().subtract(player.position()).normalize().scale(1.6);
                    entity.push(push.x, 0.3, push.z);
                    entity.hurt(level.damageSources().magic(), 4.0f);
                }
            }
            level.playSound(null, centre, ModSounds.RitualPulse.get(), SoundSource.PLAYERS, 0.9f, 0.9f);
        });

        add("deep_ritual", "Deep Ritual", 1, 15.0f, 3.0f, 1800, (player, target) -> {
            com.pathways.beyond.ritual.RitualManager.deepRitual(player);
        });

        add("astral_walk", "Astral Walk", 1, 10.0f, 1.0f, 400, (player, target) -> {
            SoulProjection.request(player, !ModAttachments.occult(player).spiritForm());
        });

        add("authority_to_forbid", "Authority to Forbid", 1, 12.0f, 2.0f, 1200, (player, target) -> {
            ServerLevel level = player.serverLevel();
            // "may not enter" - enforced on everything that was listening
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0))) {
                if (SanitySystem.supernaturalWeight(mob) > 0.0f) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(2.2);
                    mob.push(away.x, 0.2, away.z);
                    mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 2, false, false));
                }
            }
            level.playSound(null, player.blockPosition(), ModSounds.AuthorityForbid.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            ModNetwork.sendVisual(player, OccultMessages.V_RITUAL_PULSE, player.getX(), player.getY(),
                    player.getZ(), 1.0f, 0xB49BD0, 60);
        });

        // ---- Sequence 0: The Fool -----------------------------------------------------------
        add("between_two_truths", "Between Two Truths", 0, 12.0f, 1.0f, 600, (player, target) -> {
            // for five seconds the player is not in either version of events
            OccultState state = ModAttachments.occult(player);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
            player.noPhysics = true;
            player.getPersistentData().putInt("pathways_untouchable", 100);
            player.serverLevel().sendParticles(ModParticles.BeyondShard.get(), player.getX(),
                    player.getY() + 1.0, player.getZ(), 30, 0.6, 0.8, 0.6, 0.01);
            state.addCorruption(1.0f);
        });

        add("unreality_walk", "Unreality Walk", 0, 18.0f, 2.0f, 900, (player, target) -> {
            player.noPhysics = true;
            player.getPersistentData().putInt("pathways_untouchable", 200);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.BeyondCall.get(),
                    SoundSource.PLAYERS, 0.7f, 1.2f);
            ModNetwork.sendVisual(player, OccultMessages.V_BEYOND_NOTICING, player.getX(), player.getY(),
                    player.getZ(), 0.6f, 0xB49BD0, 120);
        });

        add("the_space_between", "The Space Between", 0, 20.0f, 3.0f, 400, (player, target) -> {
            ServerLevel level = player.serverLevel();
            BlockPos from = player.blockPosition();
            // 40 blocks, but not in a straight line: the shortest path is not the real one
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            BlockPos to = BlockPos.containing(player.getX() + Math.cos(angle) * 40.0,
                    player.getY() + player.getRandom().nextInt(9) - 4,
                    player.getZ() + Math.sin(angle) * 40.0);
            while (!level.getBlockState(to).isAir() && to.getY() < level.getMaxBuildHeight() - 2) {
                to = to.above();
            }
            foldTeleport(player, level, from, to, 1.6f);
            ModNetwork.sendVisual(player, OccultMessages.V_ABILITY_CAST, to.getX(), to.getY(), to.getZ(),
                    1.4f, 0xB49BD0, 40);
        });

        add("the_noticing_dawn", "The Noticing Dawn", 0, 40.0f, 8.0f, 3600, (player, target) -> {
            ServerLevel level = player.serverLevel();
            // The player does not cast this. It happens near them, and they are aware of it.
            level.playSound(null, player.blockPosition(), ModSounds.BeyondArrival.get(),
                    SoundSource.AMBIENT, 1.0f, 1.0f);
            level.sendParticles(ModParticles.BeyondShard.get(), player.getX(), player.getY() + 6.0,
                    player.getZ(), 200, 24.0, 8.0, 24.0, 0.02);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(48.0), e -> e != player)) {
                if (living instanceof Monster monster) {
                    monster.setTarget(null);
                    monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 2, false, false));
                } else {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
                }
            }
            ModNetwork.sendVisual(player, OccultMessages.V_BEYOND_NOTICING, player.getX(), player.getY(),
                    player.getZ(), 1.0f, 0xF6F3F8, 300);
            player.displayClientMessage(Component.literal("THE BEYOND IS LOOKING AT YOU.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
            com.pathways.beyond.event.WorldEventManager.beginTheNoticing(level, player);
        });
    }

    // ===================================================================================
    // Shared helpers
    // ===================================================================================
    private static void foldTeleport(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to,
                                     float intensity) {
        level.sendParticles(ModParticles.ShadowMove.get(), from.getX() + 0.5, from.getY() + 1.0,
                from.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.04);
        player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), player.getXRot());
        level.sendParticles(ModParticles.ShadowMove.get(), to.getX() + 0.5, to.getY() + 1.0,
                to.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.04);
        level.playSound(null, to, ModSounds.ThreadSever.get(), SoundSource.PLAYERS, 0.6f * intensity, 0.9f);
    }

    private static LivingEntity nearestLiving(ServerPlayer player, double radius) {
        return player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        e -> e != player && e.isAlive())
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    private static String directionWord(Vec3 delta) {
        double angle = Math.toDegrees(Math.atan2(-delta.z, delta.x));
        if (angle < 0) {
            angle += 360;
        }
        String[] names = {"east", "north-east", "north", "north-west", "west", "south-west",
                "south", "south-east"};
        int index = (int) Math.round(angle / 45.0) % 8;
        return names[index];
    }

    private static net.minecraft.world.level.block.Block ModBlocksVoidStone() {
        return com.pathways.beyond.registry.ModBlocks.VoidStone.get();
    }

    /** Cleans up temporary no-physics states; called from the player tick. */
    public static void tickTemporaryStates(ServerPlayer player) {
        int untouchable = player.getPersistentData().getInt("pathways_untouchable");
        if (untouchable > 0) {
            untouchable--;
            player.getPersistentData().putInt("pathways_untouchable", untouchable);
            if (untouchable == 0) {
                player.noPhysics = false;
            }
        } else if (player.noPhysics && !player.isSpectator()) {
            player.noPhysics = false;
        }
    }

    /** Used by the Marionettist tree to remember which puppets answer to a player. */
    public static void registerPuppetCommands(ServerPlayer player) {
        // Handled by SoulThreadManager; this exists so ability code has one entry point.
        SoulThreadManager.commandPuppets(player, player.position());
    }

    /** Convenience for the codex UI: the ability ids a player currently has. */
    public static List<String> unlockedFor(ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        return pathway.unlockedAbilities().stream()
                .filter(REGISTRY::containsKey)
                .toList();
    }

    /** Items a player must hold to be granted an ability (used for flavor checks). */
    public static boolean requiresFocusItem(ServerPlayer player, String abilityId) {
        Ability ability = REGISTRY.get(abilityId);
        if (ability == null) {
            return false;
        }
        ItemStack main = player.getMainHandItem();
        return switch (abilityId) {
            case "supernatural_sight", "ore_sense" ->
                    main.is(ModItems.EyeOfSolomon.get()) || main.is(ModItems.PathwayCodex.get());
            case "read_the_stone", "memory_echo_runtime" -> main.is(ModItems.PathwayCodex.get());
            default -> false;
        };
    }
}
