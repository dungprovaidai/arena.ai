package com.pathways.beyond.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.pathway.SequenceLogic;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModComponents;
import com.pathways.beyond.registry.ModEffects;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Every occult item, in one place.
 *
 * <p>Design notes that matter for gameplay feel:
 * <ul>
 *   <li>pathway potions are drunk, not used instantly, and the drinking animation is long
 *       enough that drinking one during a fight is a decision;</li>
 *   <li>artifacts are <b>sealed</b> until unsealed at a ritual altar, so finding one is the
 *       start of a quest rather than a reward;</li>
 *   <li>every tooltip states the price, in plain language, because the mod's whole thesis is
 *       that the price is part of the power.</li>
 * </ul>
 */
public final class OccultItems {
    private OccultItems() {}

    // ===================================================================================
    // Pathway potion
    // ===================================================================================
    public static class PathwayPotionItem extends Item {
        private final String pathwayId;
        private final int sequence;
        private final int colour;
        private final String glow;

        public PathwayPotionItem(Properties properties, String pathwayId, int sequence, int colour, String glow) {
            super(properties);
            this.pathwayId = pathwayId;
            this.sequence = sequence;
            this.colour = colour;
            this.glow = glow;
        }

        public String pathwayId() {
            return pathwayId;
        }

        public int sequence() {
            return sequence;
        }

        public int colour() {
            return colour;
        }

        public String glow() {
            return glow;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 48;   // deliberately slow: this is not a combat item
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.consume(stack);
            }
            PlayerPathway pathway = ModAttachments.pathway(serverPlayer);
            // the potion must match the pathway the player is already on, unless it is their first
            if (pathway.hasPathway() && !pathway.pathwayId().equals(pathwayId)) {
                serverPlayer.displayClientMessage(Component.literal(
                                "This is not your pathway. Drinking it would be a different kind of mistake.")
                        .withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (pathway.hasPathway() && pathway.digestion() < 100.0f && pathway.sequence() <= sequence) {
                // already digesting, or this potion would not move them forward
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.pathwaysofthebeyond.digesting_hold"), true);
                return InteractionResultHolder.fail(stack);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!(entity instanceof ServerPlayer player)) {
                return super.finishUsingItem(stack, level, entity);
            }
            ServerLevel serverLevel = player.serverLevel();
            SequenceLogic.onPotionDrunk(player, pathwayId, sequence);

            // drinking animation aftermath: the world warps for a few seconds
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            player.addEffect(new MobEffectInstance(ModEffects.Clarity, 100, 0, false, false));
            if (sequence <= 5) {
                player.addEffect(new MobEffectInstance(ModEffects.Hallucinating, 400, 0, false, false));
            }
            serverLevel.playSound(null, player.blockPosition(), ModSounds.PotionDrink.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            serverLevel.playSound(null, player.blockPosition(), ModSounds.PotionFinish.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
            serverLevel.sendParticles(ModParticles.SpiritMote.get(), player.getX(), player.getY() + 1.4,
                    player.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
            ModNetwork.sendVisual(player, com.pathways.beyond.network.OccultMessages.V_ABILITY_CAST,
                    player.getX(), player.getY() + 1.2, player.getZ(), 1.0f, colour, 60);
            player.displayClientMessage(Component.translatable("message.pathwaysofthebeyond.potion_consumed")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);

            stack.consume(1, player);
            return stack;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            tooltip.add(Component.literal("Pathway of " + pathwayId).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.literal("Sequence " + sequence).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Drink, then digest it by behaving like the pathway. "
                    + "The next Sequence is attempted in a ritual circle, not from a bottle.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Price: sanity, and a permanent mark on your Corruption stage.")
                    .withStyle(ChatFormatting.RED));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return sequence <= 3;
        }
    }

    // ===================================================================================
    // Plain ingredient with lore
    // ===================================================================================
    public static class SimpleLoreItem extends Item {
        private final int tier;

        public SimpleLoreItem(Properties properties, int tier) {
            super(properties);
            this.tier = tier;
        }

        public int tier() {
            return tier;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            String key = stack.getItem().getDescriptionId() + ".lore";
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            tooltip.add(Component.literal("Rarity: " + "I".repeat(Math.max(1, tier)))
                    .withStyle(tier >= 4 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    // ===================================================================================
    // Codex
    // ===================================================================================
    public static class CodexItem extends Item {
        public CodexItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                serverLevelTick(serverPlayer);
            }
            // the screen itself is opened client-side; the server only records the study beat
            if (level.isClientSide()) {
                com.pathways.beyond.client.ClientScreens.openPathwayScreen();
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        private void serverLevelTick(ServerPlayer player) {
            PlayerPathway pathway = ModAttachments.pathway(player);
            pathway.addStudy();
            pathway.addDigestion(1.2f);
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.CodexPage.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
            ModNetwork.sendPathwaySync(player);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            tooltip.add(Component.translatable("item.pathwaysofthebeyond.pathway_codex.desc")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click: open the Pathway screen. "
                    + "Studying fills digestion faster than anything else.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ===================================================================================
    // Sealed artifacts
    // ===================================================================================
    public static class EyeOfSolomonItem extends Item {
        public EyeOfSolomonItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                if (!SealedArtifactStack.isUnsealed(stack)) {
                    player.displayClientMessage(Component.literal(
                                    "The reliquary is sealed. Unseal it at an Occult Ritual Altar.")
                            .withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.fail(stack);
                }
                com.pathways.beyond.artifact.ArtifactEvents.eyeReveal(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            addArtifactText(stack, tooltip, "item.pathwaysofthebeyond.eye_of_solomon");
        }
    }

    public static class BlackBookItem extends Item {
        public BlackBookItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                com.pathways.beyond.artifact.ArtifactEvents.bookRead(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            addArtifactText(stack, tooltip, "item.pathwaysofthebeyond.black_book");
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }

    public static class WhisperingBellItem extends Item {
        public WhisperingBellItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                if (!SealedArtifactStack.isUnsealed(stack)) {
                    player.displayClientMessage(Component.literal(
                            "The bell has no clapper. Unsealing restores it, which is a choice.")
                            .withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.fail(stack);
                }
                com.pathways.beyond.artifact.ArtifactEvents.bellRing(serverPlayer);
                player.getCooldowns().addCooldown(this, 600);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            addArtifactText(stack, tooltip, "item.pathwaysofthebeyond.whispering_bell");
        }
    }

    public static class BrassKeyItem extends Item {
        public BrassKeyItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                com.pathways.beyond.artifact.ArtifactEvents.keyOpen(serverPlayer);
                player.getCooldowns().addCooldown(this, 300);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            addArtifactText(stack, tooltip, "item.pathwaysofthebeyond.brass_key");
        }
    }

    private static void addArtifactText(ItemStack stack, List<Component> tooltip, String key) {
        tooltip.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Passive: ").append(Component.translatable(key + ".passive"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Drawback: ").append(Component.translatable(key + ".drawback"))
                .withStyle(ChatFormatting.RED));
        if (!SealedArtifactStack.isUnsealed(stack)) {
            tooltip.add(Component.literal("SEALED - requires a ritual to unseal")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
    }

    // ===================================================================================
    // Tools
    // ===================================================================================
    public static class ThreadShearsItem extends Item {
        public ThreadShearsItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                var target = nearestThreadTarget(serverPlayer);
                if (target == null) {
                    player.displayClientMessage(Component.literal("No Soul Thread within reach.")
                            .withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.fail(stack);
                }
                com.pathways.beyond.soul.SoulThreadManager.sever(serverPlayer, target);
                stack.hurtAndBreak(1, serverPlayer, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        private LivingEntity nearestThreadTarget(ServerPlayer player) {
            return player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(6.0),
                            e -> e != player && com.pathways.beyond.soul.SoulThreadManager.isBound(e.getId()))
                    .stream().findFirst().orElse(null);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            tooltip.add(Component.literal("Cuts a bound Soul Thread. The puppet remembers being cut.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static class RitualDaggerItem extends Item {
        public RitualDaggerItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                int charges = stack.getOrDefault(ModComponents.BLOOD_CHARGES.get(), 0);
                if (charges >= 3) {
                    player.displayClientMessage(Component.literal(
                            "The blade is full. Pour it into a blood basin.").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.fail(stack);
                }
                stack.set(ModComponents.BLOOD_CHARGES.get(), charges + 1);
                if (player instanceof net.minecraft.server.level.ServerPlayer bloodOwner) {
                    bloodOwner.hurt(player.damageSources().magic(), 1.5f);
                    bloodOwner.level().playSound(null, player.blockPosition(), ModSounds.DaggerCut.get(),
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                    bloodOwner.serverLevel().sendParticles(ModParticles.BloodDrop.get(), player.getX(),
                            player.getY() + 1.2, player.getZ(), 8, 0.2, 0.2, 0.2, 0.03);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            int charges = stack.getOrDefault(ModComponents.BLOOD_CHARGES.get(), 0);
            tooltip.add(Component.literal("Blood charges: " + charges + "/3").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.literal("Open a palm for the circle. Blood is a component, not a metaphor.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static class OccultCompassItem extends Item {
        public OccultCompassItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                ServerLevel serverLevel = serverPlayer.serverLevel();
                BlockPos altar = com.pathways.beyond.ritual.RitualManager.nearestAltar(serverLevel,
                        serverPlayer, 96);
                if (altar != null) {
                    String direction = direction(serverPlayer.blockPosition(), altar);
                    int distance = (int) Math.sqrt(altar.distSqr(serverPlayer.blockPosition()));
                    player.displayClientMessage(Component.literal(
                                    "A ritual site " + direction + ", " + distance + " blocks.")
                            .withStyle(ChatFormatting.GOLD), true);
                    serverLevel.playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                            SoundSource.PLAYERS, 0.4f, 1.6f);
                } else {
                    player.displayClientMessage(Component.literal(
                                    "Nothing within 96 blocks. The needle turns, slowly, anyway.")
                            .withStyle(ChatFormatting.DARK_GRAY), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        private String direction(BlockPos from, BlockPos to) {
            int dx = to.getX() - from.getX();
            int dz = to.getZ() - from.getZ();
            String ns = dz > 0 ? "south" : "north";
            String ew = dx > 0 ? "east" : "west";
            if (Math.abs(dx) > Math.abs(dz) * 2) {
                return ew;
            }
            if (Math.abs(dz) > Math.abs(dx) * 2) {
                return ns;
            }
            return ns + "-" + ew;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            tooltip.add(Component.translatable("item.pathwaysofthebeyond.occult_compass.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static class WardCharmItem extends Item {
        public WardCharmItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                OccultState state = ModAttachments.occult(serverPlayer);
                state.setWardCharges(1);
                serverPlayer.displayClientMessage(Component.literal(
                                "The charm is bound to you. It will take one thing on your behalf.")
                        .withStyle(ChatFormatting.GREEN), true);
                serverPlayer.serverLevel().playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                        SoundSource.PLAYERS, 0.7f, 1.3f);
                stack.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            tooltip.add(Component.translatable("item.pathwaysofthebeyond.ward_charm.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static class SpiritTonicItem extends Item {
        public SpiritTonicItem(Properties properties) {
            super(properties);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 32;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (entity instanceof ServerPlayer player) {
                OccultState state = ModAttachments.occult(player);
                state.addSanity(28.0f);
                state.addCorruption(1.2f);
                player.addEffect(new MobEffectInstance(ModEffects.Clarity, 600, 0, false, false));
                player.serverLevel().playSound(null, player.blockPosition(), ModSounds.PotionDrink.get(),
                        SoundSource.PLAYERS, 0.9f, 1.3f);
                player.displayClientMessage(Component.literal(
                                "The shaking stops. The damage to your Corruption is permanent, of course.")
                        .withStyle(ChatFormatting.AQUA), true);
                ModNetwork.sendOccultSync(player);
                stack.consume(1, player);
            }
            return stack;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            tooltip.add(Component.translatable("item.pathwaysofthebeyond.spirit_tonic.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ===================================================================================
    // Data carriers
    // ===================================================================================
    /** Called from the block code when an artifact is placed on an altar to be unsealed. */
    public static void unsealAt(ServerPlayer player, ItemStack artifact) {
        SealedArtifactStack.setSealed(artifact, false);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.ArtifactActivate.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        SanitySystem.addCorruption(player, 1.0f);
    }
}
