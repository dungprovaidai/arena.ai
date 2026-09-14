package com.pathways.beyond.block;

import com.pathways.beyond.item.OccultItems;
import com.pathways.beyond.registry.ModBlocks;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every block in the mod.
 *
 * <p>Interactions are the interesting part: this mod has almost no GUI-with-slots blocks. The
 * altar is used by <i>standing in the circle</i>, the archive by <i>reading</i>, the basin by
 * <i>pouring blood into it</i>. That is what makes ritual feel like a place rather than a menu.
 */
public final class OccultBlocks {
    private OccultBlocks() {}

    // ===================================================================================
    // Factory used by the generated registry
    // ===================================================================================
    public static final class SimpleBlockFactory {
        private SimpleBlockFactory() {}

        /** Creates the right block implementation for an id, driven by tools/content.py. */
        public static Block create(String id, float hardness, float resistance, int light,
                                   String soundId, boolean needsTool) {
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(hardness, resistance)
                    .sound(resolveSound(soundId));
            if (light > 0) {
                properties = properties.lightLevel(state -> light);
            }
            if (light > 0) {
                properties = properties.emissiveRendering((state, level, pos) -> true);
            }
            return switch (id) {
                case "ritual_altar" -> new RitualAltarBlock(properties);
                case "ritual_candle" -> new RitualCandleBlock(properties);
                case "chalk_circle" -> new ChalkCircleBlock(properties);
                case "occult_table" -> new OccultTableBlock(properties);
                case "blood_basin" -> new BloodBasinBlock(properties);
                case "spirit_lantern" -> new SpiritLanternBlock(properties);
                case "occult_archive" -> new OccultArchiveBlock(properties);
                case "memory_shard_block" -> new MemoryShardBlock(properties);
                case "spirit_flower" -> new SpiritFlowerBlock(properties);
                case "moonlit_fungus" -> new MoonlitFungusBlock(properties);
                case "veil_glass" -> new VeilGlassBlock(properties);
                case "iron_grate" -> new IronGrateBlock(properties);
                // building families: stairs / slabs / walls / plain cubes
                case "gothic_brick_stairs", "desecrated_stone_stairs", "dark_plank_stairs",
                     "slate_tile_stairs" -> new StairBlock(Blocks.STONE.defaultBlockState(), properties);
                case "gothic_brick_slab", "desecrated_stone_slab", "dark_plank_slab",
                     "slate_tile_slab" -> new SlabBlock(properties);
                case "gothic_brick_wall" -> new WallBlock(properties);
                default -> new Block(properties);
            };
        }

        private static SoundType resolveSound(String id) {
            return switch (id) {
                case "minecraft:wood" -> SoundType.WOOD;
                case "minecraft:metal" -> SoundType.METAL;
                case "minecraft:glass" -> SoundType.GLASS;
                case "minecraft:candle" -> SoundType.CANDLE;
                case "minecraft:lantern" -> SoundType.LANTERN;
                case "minecraft:grass" -> SoundType.GRASS;
                default -> SoundType.STONE;
            };
        }
    }

    // ===================================================================================
    // Ritual altar: the centre of the mod's crafting
    // ===================================================================================
    public static class RitualAltarBlock extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public RitualAltarBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide()) {
                // opening the ritual screen is a client action; the server is asked for state
                com.pathways.beyond.client.ClientScreens.openRitualScreen(pos);
                com.pathways.beyond.network.ModNetwork.requestRitualAction("query",
                        pos.getX(), pos.getY(), pos.getZ(), "");
                return ItemInteractionResult.SUCCESS;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                // placing a sealed artifact on the altar offers it for unsealing
                if (stack.getItem() instanceof OccultItems.EyeOfSolomonItem
                        || stack.getItem() instanceof OccultItems.BlackBookItem
                        || stack.getItem() instanceof OccultItems.WhisperingBellItem) {
                    OccultItems.unsealAt(serverPlayer, stack);
                    return ItemInteractionResult.SUCCESS;
                }
                com.pathways.beyond.ritual.RitualManager.sendSnapshot(serverPlayer, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            // subtle idle VFX so an altar never looks like furniture
            if (random.nextFloat() < 0.35f) {
                level.addParticle(ModParticles.EmberOccult.get(),
                        pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.15,
                        pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
            }
            if (random.nextFloat() < 0.08f) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                        ModSounds.RitualPulse.get(), SoundSource.BLOCKS, 0.25f, 1.6f, false);
            }
        }

        @Override
        public boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        @Override
        public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            // the altar slowly stains the ground beneath it
            if (random.nextFloat() < 0.02f) {
                BlockPos below = pos.below();
                if (level.getBlockState(below).is(Blocks.STONE) || level.getBlockState(below).is(Blocks.DEEPSLATE)) {
                    level.setBlock(below, ModBlocks.DesecratedStone.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    // ===================================================================================
    // Ritual candle: light that matters
    // ===================================================================================
    public static class RitualCandleBlock extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public RitualCandleBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)
                    || stack.is(net.minecraft.world.item.Items.FIRE_CHARGE)) {
                if (!level.isClientSide()) {
                    level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                    level.playSound(null, pos, ModSounds.RitualPulse.get(), SoundSource.BLOCKS, 0.5f, 1.8f);
                }
                return ItemInteractionResult.SUCCESS;
            }
            if (state.getValue(LIT) && stack.isEmpty()) {
                if (!level.isClientSide()) {
                    level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) {
                return;
            }
            if (random.nextFloat() < 0.4f) {
                level.addParticle(ModParticles.EmberOccult.get(), pos.getX() + 0.5, pos.getY() + 0.95,
                        pos.getZ() + 0.5, 0.0, 0.01, 0.0);
            }
            if (random.nextFloat() < 0.15f) {
                level.addParticle(ModParticles.VeilSmoke.get(), pos.getX() + 0.5, pos.getY() + 1.0,
                        pos.getZ() + 0.5, 0.0, 0.02, 0.0);
            }
        }
    }

    // ===================================================================================
    // Chalk circle: a flat decal that makes a place a ritual site
    // ===================================================================================
    public static class ChalkCircleBlock extends Block {
        private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

        public ChalkCircleBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isSolidRender(level, pos.below());
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            // erasing the circle is a deliberate act, and it is heard
            if (!level.isClientSide() && !state.is(newState.getBlock())) {
                level.playSound(null, pos, ModSounds.ChalkDraw.get(), SoundSource.BLOCKS, 0.6f, 0.8f);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // ===================================================================================
    // Occult table: the practical workbench (no GUI; right-click crafts the potion if ready)
    // ===================================================================================
    public static class OccultTableBlock extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public OccultTableBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide()) {
                com.pathways.beyond.client.ClientScreens.openCodexScreen();
                return ItemInteractionResult.SUCCESS;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "The table holds notes, not ingredients. Brewing is done in a circle."), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
    }

    // ===================================================================================
    // Blood basin: stores blood charges, powers rituals
    // ===================================================================================
    public static class BloodBasinBlock extends Block {
        public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);

        public BloodBasinBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LEVEL);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            int charges = stack.getOrDefault(com.pathways.beyond.registry.ModComponents.BLOOD_CHARGES.get(), 0);
            if (charges > 0) {
                if (!level.isClientSide()) {
                    int current = state.getValue(LEVEL);
                    if (current < 3) {
                        level.setBlock(pos, state.setValue(LEVEL, current + 1), Block.UPDATE_ALL);
                    }
                    stack.remove(com.pathways.beyond.registry.ModComponents.BLOOD_CHARGES.get());
                    level.playSound(null, pos, ModSounds.DaggerCut.get(), SoundSource.BLOCKS, 0.7f, 1.2f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ModParticles.BloodDrop.get(), pos.getX() + 0.5, pos.getY() + 0.8,
                                pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.02);
                    }
                }
                return ItemInteractionResult.SUCCESS;
            }
            if (stack.isEmpty() && state.getValue(LEVEL) > 0 && player.isShiftKeyDown()) {
                if (!level.isClientSide()) {
                    level.setBlock(pos, state.setValue(LEVEL, 0), Block.UPDATE_ALL);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "You empty the basin. Something in the room disapproves."), true);
                    SanitySystem.addCorruption((net.minecraft.server.level.ServerPlayer) player, 0.2f);
                }
                return ItemInteractionResult.SUCCESS;
            }
            if (state.getValue(LEVEL) > 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Blood level: " + state.getValue(LEVEL) + "/3"), true);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(LEVEL) > 0 && random.nextFloat() < 0.1f) {
                level.addParticle(ModParticles.BloodDrop.get(), pos.getX() + 0.5, pos.getY() + 0.7,
                        pos.getZ() + 0.5, 0.0, 0.0, 0.0);
            }
        }
    }

    // ===================================================================================
    // Spirit lantern: cold light, and it flickers when something is near
    // ===================================================================================
    public static class SpiritLanternBlock extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public SpiritLanternBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) {
                return;
            }
            if (random.nextFloat() < 0.3f) {
                level.addParticle(ModParticles.SpiritMote.get(), pos.getX() + 0.5,
                        pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
            }
            // the lantern dims where something supernatural stands close to it
            boolean disturbance = !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(6.0),
                    e -> SanitySystem.supernaturalWeight(e) > 0.0f).isEmpty();
            if (disturbance && random.nextFloat() < 0.4f) {
                level.addParticle(ModParticles.BlackWisp.get(), pos.getX() + 0.5, pos.getY() + 0.6,
                        pos.getZ() + 0.5, 0.0, 0.0, 0.0);
            }
        }

        @Override
        public boolean isRandomlyTicking(BlockState state) {
            return true;
        }
    }

    // ===================================================================================
    // Occult archive: portable forbidden knowledge
    // ===================================================================================
    public static class OccultArchiveBlock extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public OccultArchiveBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.pathways.beyond.artifact.ArtifactEvents.bookRead(serverPlayer);
                com.pathways.beyond.sanity.SanitySystem.addCorruption(serverPlayer, 0.4f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextFloat() < 0.05f) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        ModSounds.CodexPage.get(), SoundSource.BLOCKS, 0.2f, 1.4f, false);
            }
        }
    }

    // ===================================================================================
    // Memory shard block: frozen remembered light
    // ===================================================================================
    public static class MemoryShardBlock extends Block {
        public MemoryShardBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.pathways.beyond.event.MemoryEchoRuntime.replay(serverPlayer.serverLevel(), pos, serverPlayer);
                com.pathways.beyond.sanity.SanitySystem.spendSanity(serverPlayer, 3.0f);
            }
            return ItemInteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextFloat() < 0.25f) {
                level.addParticle(ModParticles.SpiritMote.get(), pos.getX() + random.nextDouble(),
                        pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
            }
        }
    }

    // ===================================================================================
    // Plants
    // ===================================================================================
    public static class SpiritFlowerBlock extends net.minecraft.world.level.block.BushBlock {
        public SpiritFlowerBlock(Properties properties) {
            super(properties);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextFloat() < 0.12f) {
                level.addParticle(ModParticles.SpiritMote.get(), pos.getX() + 0.5, pos.getY() + 0.6,
                        pos.getZ() + 0.5, 0.0, 0.01, 0.0);
            }
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BushBlock> codec() {
            return simpleCodec(SpiritFlowerBlock::new);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                    || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SOUL_SAND)
                    || state.is(ModBlocks.DesecratedStone.get()) || state.is(Blocks.GRAVEL);
        }
    }

    public static class MoonlitFungusBlock extends net.minecraft.world.level.block.BushBlock {
        public MoonlitFungusBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BushBlock> codec() {
            return simpleCodec(MoonlitFungusBlock::new);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.DIRT)
                    || state.is(Blocks.GRAVEL) || state.is(ModBlocks.DesecratedStone.get())
                    || state.is(ModBlocks.VoidStone.get());
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextFloat() < 0.08f) {
                level.addParticle(ModParticles.SpiritMote.get(), pos.getX() + 0.5, pos.getY() + 0.8,
                        pos.getZ() + 0.5, 0.0, 0.0, 0.0);
            }
        }

        /** Moonlit Fungus only spreads when the moon is out. */
        @Override
        public boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        @Override
        public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (level.isDay() || random.nextFloat() > 0.15f) {
                return;
            }
            BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - 1, random.nextInt(3) - 1);
            if (level.getBlockState(target).isAir() && mayPlaceOn(level.getBlockState(target.below()),
                    level, target.below()) && level.getMaxLocalRawBrightness(target) < 12) {
                level.setBlock(target, defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // ===================================================================================
    // Glass and grate
    // ===================================================================================
    public static class VeilGlassBlock extends net.minecraft.world.level.block.TransparentBlock {
        public VeilGlassBlock(Properties properties) {
            super(properties);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            // looking through veil glass is mildly unpleasant, close up
            if (random.nextFloat() < 0.03f) {
                level.addParticle(ModParticles.ChromaticSpeck.get(), pos.getX() + random.nextDouble(),
                        pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
            }
        }
    }

    public static class IronGrateBlock extends Block {
        private static final VoxelShape SHAPE = Shapes.or(
                Block.box(0, 12, 0, 16, 16, 16),
                Block.box(0, 0, 0, 2, 16, 16),
                Block.box(14, 0, 0, 16, 16, 16));

        public IronGrateBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return SHAPE;
        }
    }

    /** Convenience used by several blocks: count lit candles in a radius. */
    public static int litCandles(LevelReader level, BlockPos centre, int radius) {
        int lit = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CandleBlock && state.hasProperty(CandleBlock.LIT)
                    && state.getValue(CandleBlock.LIT)) {
                lit++;
            }
        }
        return lit;
    }

    /** Convenience used by several blocks: count chalk circle blocks in a radius. */
    public static int chalkBlocks(LevelReader level, BlockPos centre, int radius) {
        int chalk = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.ChalkCircle.get())) {
                chalk++;
            }
        }
        return chalk;
    }
}
