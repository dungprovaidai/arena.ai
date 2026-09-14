package com.pathways.beyond.worldgen;

import com.pathways.beyond.registry.ModBlocks;
import com.pathways.beyond.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural structure generation.
 *
 * <p>Every supernatural structure ships as a data-driven NBT template <i>and</i> as a procedural
 * builder here. The NBT is what artists edit; the procedural version is what guarantees the world
 * actually contains the mod's places even before a single template has been authored, and it is
 * what the "impossible structures" of The Noticing are drawn from.
 *
 * <p>The design rules these builders follow:
 * <ul>
 *   <li>no random block spam: every structure is a walled, roofed, purposeful space;</li>
 *   <li>one hidden space or one trap per structure, so exploring pays;</li>
 *   <li>every structure tells a small story through props (a ritual abandoned mid-circle, a
 *       library with one book removed, a chapel whose pews face the wrong way).</li>
 * </ul>
 */
public final class ProceduralStructures {
    private ProceduralStructures() {}

    public static boolean build(ServerLevel level, String id, BlockPos origin, RandomSource random) {
        return switch (id) {
            case "occult_mansion" -> mansion(level, origin, random);
            case "ruined_chapel" -> chapel(level, origin, random);
            case "forbidden_library" -> library(level, origin, random);
            case "witch_laboratory" -> laboratory(level, origin, random);
            case "sealed_shrine" -> shrine(level, origin, random);
            case "ritual_chamber" -> ritualChamber(level, origin, random);
            case "catacombs" -> catacombs(level, origin, random);
            case "spirit_ruins" -> spiritRuins(level, origin, random);
            default -> false;
        };
    }

    // ===================================================================================
    // Helpers
    // ===================================================================================
    private static BlockState bricks() {
        return ModBlocks.GothicBricks.get().defaultBlockState();
    }

    private static BlockState darkPlanks() {
        return ModBlocks.DarkPlanks.get().defaultBlockState();
    }

    private static BlockState slate() {
        return ModBlocks.SlateTiles.get().defaultBlockState();
    }

    private static BlockState desecrated() {
        return ModBlocks.DesecratedStone.get().defaultBlockState();
    }

    private static BlockState plaster() {
        return ModBlocks.WeatheredPlaster.get().defaultBlockState();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    /** Hollow box: walls, floor and roof, with a doorway punched in the south wall. */
    private static void box(ServerLevel level, BlockPos origin, int width, int depth, int height,
                            BlockState wall, BlockState floor, BlockState roof, RandomSource random) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                set(level, origin.offset(x, -1, z), floor);
                set(level, origin.offset(x, height, z), roof);
                for (int y = 0; y < height; y++) {
                    boolean edge = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                    if (edge) {
                        boolean window = y > 0 && y < height - 1
                                && (x % 4 == 2 && (z == 0 || z == depth - 1)
                                || z % 4 == 2 && (x == 0 || x == width - 1));
                        set(level, origin.offset(x, y, z), window
                                ? ModBlocks.VeilGlass.get().defaultBlockState() : wall);
                    } else {
                        set(level, origin.offset(x, y, z), air());
                    }
                }
            }
        }
        // doorway
        int doorX = width / 2;
        for (int y = 0; y < 3; y++) {
            set(level, origin.offset(doorX, y, 0), air());
            set(level, origin.offset(doorX, y, 1), air());
        }
    }

    /** Chest with real contents: the mod never places a chest that pays nothing. */
    private static void chest(ServerLevel level, BlockPos pos, RandomSource random, List<ItemStack> loot) {
        set(level, pos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < loot.size() && i < 27; i++) {
                chest.setItem(i, loot.get(i));
            }
            chest.setChanged();
        }
    }

    private static void candle(ServerLevel level, BlockPos pos) {
        set(level, pos, ModBlocks.RitualCandle.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
    }

    /** A ritual abandoned mid-working: the circle is drawn, the candles are out, the blood is dry. */
    private static void abandonedCircle(ServerLevel level, BlockPos centre, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (Math.abs(distance - radius) < 0.6 || Math.abs(distance - radius * 0.55) < 0.5) {
                    BlockPos at = centre.offset(x, 0, z);
                    if (level.getBlockState(at).isAir()) {
                        set(level, at, ModBlocks.ChalkCircle.get().defaultBlockState());
                    }
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8.0;
            BlockPos at = centre.offset((int) Math.round(Math.cos(angle) * radius), 0,
                    (int) Math.round(Math.sin(angle) * radius));
            if (i % 3 != 0) {
                candle(level, at);   // three of the eight went out
            } else {
                set(level, at, ModBlocks.RitualCandle.get().defaultBlockState());
            }
        }
    }

    // ===================================================================================
    // Structures
    // ===================================================================================
    /** Abandoned Occult Mansion: two floors, a collapsed east wing, a sealed attic, a cellar. */
    private static boolean mansion(ServerLevel level, BlockPos origin, RandomSource random) {
        int width = 19;
        int depth = 13;
        int height = 7;
        box(level, origin, width, depth, height, bricks(), darkPlanks(), slate(), random);
        // floor between storeys, with a stairwell gap
        int stairX = width / 2 + 2;
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                if (Math.abs(x - stairX) < 2 && z < 4) {
                    continue;
                }
                set(level, origin.offset(x, 3, z), darkPlanks());
            }
        }
        // parlour: table, chairs, a ritual laid out and never finished
        set(level, origin.offset(4, 1, 8), ModBlocks.OccultTable.get().defaultBlockState());
        abandonedCircle(level, origin.offset(4, 1, 8), 3);
        // east wing: collapsed - rubble and a hole in the roof
        for (int x = width - 5; x < width - 1; x++) {
            for (int z = 2; z < depth - 2; z++) {
                if (random.nextFloat() < 0.6f) {
                    set(level, origin.offset(x, 3, z), desecrated());
                }
                if (random.nextFloat() < 0.35f) {
                    set(level, origin.offset(x, height, z), air());
                }
            }
        }
        // library corner upstairs, with one book missing from the shelf
        for (int z = 1; z < 5; z++) {
            set(level, origin.offset(2, 4, z), ModBlocks.OccultArchive.get().defaultBlockState());
        }
        chest(level, origin.offset(3, 4, 2), random, List.of(
                new ItemStack(ModItems.BlackBook.get()),
                new ItemStack(ModItems.OccultChalk.get(), 6),
                new ItemStack(ModItems.GraveEarth.get(), 3)));
        // sealed attic: bricked shut, and there is something behind the brick
        set(level, origin.offset(width - 3, 5, depth - 3), ModBlocks.VoidStone.get().defaultBlockState());
        chest(level, origin.offset(width - 4, 5, depth - 4), random, List.of(
                new ItemStack(ModItems.WhisperingBell.get()),
                new ItemStack(ModItems.SilverNeedle.get()),
                new ItemStack(ModItems.StarlessCrystal.get(), 2)));
        // cellar hatch (a trapdoor that opens onto a small dark room with the mansion's ledger)
        BlockPos cellar = origin.offset(3, -4, 3);
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 3; z++) {
                for (int y = -1; y <= 3; y++) {
                    BlockPos at = cellar.offset(x, y, z);
                    boolean edge = x == -1 || z == -1 || x == 3 || z == 3 || y == -1 || y == 3;
                    set(level, at, edge ? desecrated() : air());
                }
            }
        }
        chest(level, cellar.offset(1, 0, 1), random, List.of(
                new ItemStack(ModItems.AncientMemory.get()),
                new ItemStack(ModItems.BrassKey.get()),
                new ItemStack(Items.TORCH, 8)));
        set(level, origin.offset(3, 0, 3), Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        set(level, origin.offset(3, 1, 3), Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        return true;
    }

    /** Ruined Chapel: pews facing the wrong way, a bell tower that is mostly gone. */
    private static boolean chapel(ServerLevel level, BlockPos origin, RandomSource random) {
        int width = 13;
        int depth = 17;
        int height = 6;
        box(level, origin, width, depth, height, ModBlocks.DesecratedStone.get().defaultBlockState(),
                ModBlocks.SlateTiles.get().defaultBlockState(), ModBlocks.SlateTiles.get().defaultBlockState(), random);
        // ruin the roof and the north wall
        for (int x = 0; x < width; x++) {
            for (int z = depth - 6; z < depth; z++) {
                if (random.nextFloat() < 0.5f) {
                    set(level, origin.offset(x, height, z), air());
                }
                if (random.nextFloat() < 0.3f) {
                    set(level, origin.offset(x, random.nextInt(height), z), air());
                }
            }
        }
        // pews, all facing away from the altar
        for (int z = 4; z < depth - 3; z += 2) {
            for (int x = 2; x < width - 2; x++) {
                set(level, origin.offset(x, 0, z), darkPlanks());
                if (x == 2 || x == width - 3) {
                    set(level, origin.offset(x, 1, z), darkPlanks());
                }
            }
        }
        // the altar, and the thing the congregation was actually facing
        set(level, origin.offset(width / 2, 0, 2), ModBlocks.RitualAltar.get().defaultBlockState());
        abandonedCircle(level, origin.offset(width / 2, 1, 2), 4);
        set(level, origin.offset(width / 2, 3, depth - 2), ModBlocks.MemoryShardBlock.get().defaultBlockState());
        chest(level, origin.offset(2, 1, depth - 3), random, List.of(
                new ItemStack(ModItems.PurifiedSalt.get(), 4),
                new ItemStack(ModItems.WhisperingBone.get()),
                new ItemStack(ModItems.SpiritTonic.get())));
        return true;
    }

    /** Forbidden Library: shelves of sealed knowledge and one deliberately empty shelf. */
    private static boolean library(ServerLevel level, BlockPos origin, RandomSource random) {
        int width = 17;
        int depth = 13;
        int height = 6;
        box(level, origin, width, depth, height, ModBlocks.WeatheredPlaster.get().defaultBlockState(),
                darkPlanks(), darkPlanks(), random);
        for (int x = 2; x < width - 2; x += 3) {
            for (int z = 2; z < depth - 2; z++) {
                set(level, origin.offset(x, 1, z), ModBlocks.OccultArchive.get().defaultBlockState());
                set(level, origin.offset(x, 2, z), ModBlocks.OccultArchive.get().defaultBlockState());
            }
        }
        // reading desks
        set(level, origin.offset(width / 2, 1, depth / 2), ModBlocks.OccultTable.get().defaultBlockState());
        set(level, origin.offset(width / 2 - 2, 1, depth / 2), ModBlocks.OccultTable.get().defaultBlockState());
        // the empty shelf: a hidden room behind it
        BlockPos hidden = origin.offset(width - 2, 0, depth - 2).offset(-4, 0, -4);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0 || y == 2;
                    set(level, hidden.offset(x, y, z), edge ? bricks() : air());
                }
            }
        }
        set(level, hidden.offset(0, 1, 0), ModBlocks.MemoryShardBlock.get().defaultBlockState());
        chest(level, hidden.offset(1, 1, 1), random, List.of(
                new ItemStack(ModItems.AncientMemory.get(), 2),
                new ItemStack(ModItems.EyeOfSolomon.get()),
                new ItemStack(ModItems.OccultCompass.get())));
        chest(level, origin.offset(width / 2 + 3, 1, depth - 3), random, List.of(
                new ItemStack(ModItems.FacelessSkin.get()),
                new ItemStack(ModItems.NightshadeAshes.get(), 3),
                new ItemStack(ModItems.PathwayCodex.get())));
        return true;
    }

    /** Witch Laboratory: workbenches, jars, and a basin that is still wet. */
    private static boolean laboratory(ServerLevel level, BlockPos origin, RandomSource random) {
        int width = 11;
        int depth = 9;
        int height = 5;
        box(level, origin, width, depth, height, ModBlocks.DesecratedStone.get().defaultBlockState(),
                ModBlocks.SlateTiles.get().defaultBlockState(), darkPlanks(), random);
        set(level, origin.offset(2, 1, 2), ModBlocks.OccultTable.get().defaultBlockState());
        set(level, origin.offset(width - 3, 1, 2), ModBlocks.OccultTable.get().defaultBlockState());
        set(level, origin.offset(width / 2, 0, depth - 3), ModBlocks.BloodBasin.get().defaultBlockState());
        set(level, origin.offset(2, 1, depth - 3), Blocks.BREWING_STAND.defaultBlockState());
        candle(level, origin.offset(3, 0, depth - 4));
        candle(level, origin.offset(width - 4, 0, depth - 4));
        // a small greenhouse corner: the fungus only grows where the moon reaches
        set(level, origin.offset(width - 2, height, 2), air());
        for (int i = 0; i < 5; i++) {
            BlockPos at = origin.offset(1 + random.nextInt(width - 2), 0, 1 + random.nextInt(depth - 2));
            if (level.getBlockState(at).isAir()) {
                set(level, at, ModBlocks.MoonlitFungus.get().defaultBlockState());
            }
        }
        chest(level, origin.offset(width - 2, 1, depth - 2), random, List.of(
                new ItemStack(ModItems.RitualDagger.get()),
                new ItemStack(ModItems.BlackBloodVial.get(), 2),
                new ItemStack(ModItems.SpiritFlower.get(), 3),
                new ItemStack(ModItems.MoonlitFungus.get(), 2)));
        return true;
    }

    /** Sealed Shrine: a mountain plinth, eight candles, and a relic left waiting. */
    private static boolean shrine(ServerLevel level, BlockPos origin, RandomSource random) {
        int size = 7;
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > size) {
                    continue;
                }
                set(level, origin.offset(x, -1, z), desecrated());
                for (int y = 0; y < 3; y++) {
                    if (distance > size - 0.9 && distance > 2.0) {
                        set(level, origin.offset(x, y, z), ModBlocks.VoidStone.get().defaultBlockState());
                    }
                }
            }
        }
        set(level, origin.offset(0, 0, 0), ModBlocks.RitualAltar.get().defaultBlockState());
        set(level, origin.offset(0, 3, 0), ModBlocks.SpiritLantern.get().defaultBlockState());
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8.0;
            BlockPos at = origin.offset((int) Math.round(Math.cos(angle) * 4), 0,
                    (int) Math.round(Math.sin(angle) * 4));
            candle(level, at);
        }
        chest(level, origin.offset(0, 1, 3), random, List.of(
                new ItemStack(ModItems.StarlessCrystal.get(), 2),
                new ItemStack(ModItems.AbyssalEye.get()),
                new ItemStack(ModItems.WardCharm.get(), 2)));
        return true;
    }

    /** Underground Ritual Chamber: a round room with a working circle and a way down. */
    private static boolean ritualChamber(ServerLevel level, BlockPos origin, RandomSource random) {
        int radius = 8;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                for (int y = -1; y <= 4; y++) {
                    BlockPos at = origin.offset(x, y, z);
                    if (distance > radius) {
                        continue;
                    }
                    if (distance > radius - 1.2) {
                        set(level, at, bricks());
                    } else if (y == -1) {
                        set(level, at, slate());
                    } else if (y == 4 && distance < radius - 1.2) {
                        set(level, at, bricks());
                    } else {
                        set(level, at, air());
                    }
                }
            }
        }
        set(level, origin.offset(0, 0, 0), ModBlocks.RitualAltar.get().defaultBlockState());
        abandonedCircle(level, origin.offset(0, 0, 0), 5);
        set(level, origin.offset(4, 0, 4), ModBlocks.BloodBasin.get().defaultBlockState());
        set(level, origin.offset(-4, 0, 4), ModBlocks.RitualCandle.get().defaultBlockState());
        set(level, origin.offset(-4, 0, -4), ModBlocks.RitualPedestal.get().defaultBlockState());
        chest(level, origin.offset(6, 0, 0), random, List.of(
                new ItemStack(ModItems.OccultChalk.get(), 8),
                new ItemStack(ModItems.CorruptedHeart.get()),
                new ItemStack(ModItems.SoulFragment.get(), 2)));
        // a shaft up to the surface, so the chamber is discoverable rather than a trap
        for (int y = 4; y < 40; y++) {
            set(level, origin.offset(0, y, 0), air());
        }
        return true;
    }

    /** Catacombs: a corridor of alcoves, one of which is a trap. */
    private static boolean catacombs(ServerLevel level, BlockPos origin, RandomSource random) {
        int length = 32;
        for (int z = 0; z < length; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = 0; y <= 3; y++) {
                    boolean wall = Math.abs(x) == 2 || y == 3;
                    set(level, origin.offset(x, y - 1, z), wall
                            ? ModBlocks.DesecratedStone.get().defaultBlockState() : air());
                    if (y == 0) {
                        set(level, origin.offset(x, -1, z), ModBlocks.SlateTiles.get().defaultBlockState());
                    }
                }
            }
            // alcoves every 6 blocks, some with a chest, one with a collapsing floor
            if (z % 6 == 3 && z > 3) {
                boolean left = random.nextBoolean();
                int side = left ? -3 : 3;
                for (int x = 0; x <= 1; x++) {
                    for (int y = 0; y <= 2; y++) {
                        set(level, origin.offset(side + (left ? x : -x), y, z), air());
                    }
                }
                set(level, origin.offset(side + (left ? 1 : -1), -1, z),
                        left ? ModBlocks.DesecratedStone.get().defaultBlockState()
                                : ModBlocks.GothicBricks.get().defaultBlockState());
                if (random.nextFloat() < 0.5f) {
                    chest(level, origin.offset(side + (left ? 1 : -1), 0, z), random, List.of(
                            new ItemStack(ModItems.WhisperingBone.get()),
                            new ItemStack(ModItems.NightshadeAshes.get(), 2)));
                } else {
                    // the trap: the alcove floor is chalk, and chalk is not a floor
                    set(level, origin.offset(side + (left ? 1 : -1), 0, z),
                            ModBlocks.ChalkCircle.get().defaultBlockState());
                    for (int y = 1; y <= 6; y++) {
                        set(level, origin.offset(side + (left ? 1 : -1), -y, z), air());
                    }
                }
            }
        }
        chest(level, origin.offset(0, 0, length - 2), random, List.of(
                new ItemStack(ModItems.GraveEarth.get(), 6),
                new ItemStack(ModItems.AncientMemory.get()),
                new ItemStack(ModItems.SilverNeedle.get())));
        return true;
    }

    /** Spirit World ruins: broken pillars and circles drawn by nothing. */
    private static boolean spiritRuins(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int i = 0; i < 7; i++) {
            int x = random.nextInt(17) - 8;
            int z = random.nextInt(17) - 8;
            int height = 3 + random.nextInt(6);
            for (int y = 0; y < height; y++) {
                set(level, origin.offset(x, y, z), y % 3 == 2
                        ? ModBlocks.GothicBricks.get().defaultBlockState()
                        : ModBlocks.VoidStone.get().defaultBlockState());
            }
        }
        abandonedCircle(level, origin, 6);
        set(level, origin.offset(0, 0, 0), ModBlocks.RitualPedestal.get().defaultBlockState());
        set(level, origin.offset(0, 1, 0), ModBlocks.MemoryShardBlock.get().defaultBlockState());
        return true;
    }

    /** Utility used by both worldgen and The Noticing. */
    public static List<BlockPos> ring(BlockPos centre, int radius) {
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2 * i / 32.0;
            result.add(centre.offset((int) Math.round(Math.cos(angle) * radius), 0,
                    (int) Math.round(Math.sin(angle) * radius)));
        }
        return result;
    }
}
