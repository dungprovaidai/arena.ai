package com.pathways.beyond.item;

import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.block.OccultBlocks;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

/**
 * Pathway Infusion: the mod's replacement for vanilla potion brewing.
 *
 * <p>Instead of mapping potion to potion (which cannot produce custom items), infusion works on
 * a brewing stand directly: put a water bottle in a bottle slot, the catalyst for a Sequence in
 * the ingredient slot, and fuel below, and light a ritual candle within four blocks. The stand
 * then converts the water into the pathway potion for that Sequence.
 *
 * <p>Three design consequences, all intentional:
 * <ul>
 *   <li>brewing is a <i>place</i> - you need a candle and a stand, so it happens in a laboratory
 *       rather than in a pocket;</li>
 *   <li>the catalyst is a rare ingredient, so potion economy is gated on exploration;</li>
 *   <li>no experience is consumed anywhere, because Sequence advancement is not bought.</li>
 * </ul>
 */
public final class PathwayInfusion {
    private PathwayInfusion() {}

    /** Catalyst item id -> the Sequence whose potion it produces. */
    private record Catalyst(String ingredient, int sequence) {}

    private static final List<Catalyst> CATALYSTS = List.of(
            new Catalyst("spirit_flower", 9),
            new Catalyst("whispering_bone", 8),
            new Catalyst("starless_crystal", 7),
            new Catalyst("faceless_skin", 6),
            new Catalyst("soul_fragment", 5),
            new Catalyst("abyssal_eye", 4),
            new Catalyst("ancient_memory", 3),
            new Catalyst("corrupted_heart", 2),
            new Catalyst("black_blood_vial", 1),
            new Catalyst("veil_dust", 0));

    private static final int SLOT_FUEL = 4;
    private static final int SLOT_INGREDIENT = 3;

    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    tryInfuse(level, chunk);
                }
            }
        }
    }

    private static void tryInfuse(ServerLevel level, LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof BrewingStandBlockEntity stand)) {
                continue;
            }
            Container container = stand;
            ItemStack ingredient = container.getItem(SLOT_INGREDIENT);
            if (ingredient.isEmpty()) {
                continue;
            }
            int sequence = sequenceFor(ingredient);
            if (sequence < 0) {
                continue;
            }
            BlockPos pos = stand.getBlockPos();
            // brewing by candlelight: the stand must stand in a ritual working
            if (OccultBlocks.litCandles(level, pos, 4) < 1) {
                continue;
            }
            ItemStack fuel = container.getItem(SLOT_FUEL);
            if (fuel.isEmpty()) {
                continue;
            }
            Item potion = potionFor(sequence);
            if (potion == null) {
                continue;
            }
            boolean brewed = false;
            for (int slot = 0; slot < 3; slot++) {
                ItemStack bottle = container.getItem(slot);
                if (!isWaterBottle(bottle)) {
                    continue;
                }
                ItemStack result = new ItemStack(potion);
                container.setItem(slot, result);
                brewed = true;
            }
            if (!brewed) {
                continue;
            }
            // consume one catalyst and one fuel
            ingredient.shrink(1);
            if (ingredient.isEmpty()) {
                container.setItem(SLOT_INGREDIENT, ItemStack.EMPTY);
            }
            fuel.shrink(1);
            if (fuel.isEmpty()) {
                container.setItem(SLOT_FUEL, ItemStack.EMPTY);
            }
            container.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            level.playSound(null, pos, ModSounds.PotionBrew.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ModParticles.SpiritMote.get(), pos.getX() + 0.5, pos.getY() + 0.9,
                    pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
            for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(pos.getCenter()) < 64.0)) {
                nearby.displayClientMessage(Component.literal(
                        "The liquid takes on the colour of Sequence " + sequence + "."), true);
            }
        }
    }

    private static boolean isWaterBottle(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.is(Potions.WATER);
    }

    private static int sequenceFor(ItemStack ingredient) {
        var item = ingredient.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals(PathwaysMod.MOD_ID)) {
            return -1;
        }
        for (Catalyst catalyst : CATALYSTS) {
            if (catalyst.ingredient().equals(id.getPath())) {
                return catalyst.sequence();
            }
        }
        return -1;
    }

    /** Finds the registered pathway potion for a Sequence without assuming holder names. */
    private static Item potionFor(int sequence) {
        for (var holder : ModItems.ITEMS.getEntries()) {
            Item item = holder.get();
            if (item instanceof OccultItems.PathwayPotionItem potion && potion.sequence() == sequence) {
                return item;
            }
        }
        return null;
    }
}
