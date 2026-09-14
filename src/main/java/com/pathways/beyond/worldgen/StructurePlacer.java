package com.pathways.beyond.worldgen;

import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Structure placement.
 *
 * <p>Supernatural structures are placed deterministically on chunk load rather than through a
 * jigsaw/feature datapack. Reasons:
 *
 * <ul>
 *   <li>the templates ship as NBT and stay editable in any structure tool;</li>
 *   <li>placement can be biome-aware without a biome tag JSON per structure;</li>
 *   <li>a placed-set is persisted, so a structure can never generate twice;</li>
 *   <li>the same code path can drop "impossible structures" during The Noticing.</li>
 * </ul>
 */
public final class StructurePlacer {
    private StructurePlacer() {}

    /** One structure definition: template id, spacing in chunks, biome rule, where to stand it. */
    public record Rule(String id, int spacing, int separation, BiomeRule biome, Placement placement) {}

    public enum BiomeRule { ANY, FOREST, TAIGA, SWAMP, MOUNTAIN, UNDERGROUND, SPIRIT_ONLY }

    public enum Placement { SURFACE, UNDERGROUND, SPIRIT }

    public static final List<Rule> RULES = List.of(
            new Rule("occult_mansion", 34, 8, BiomeRule.FOREST, Placement.SURFACE),
            new Rule("ruined_chapel", 30, 7, BiomeRule.ANY, Placement.SURFACE),
            new Rule("forbidden_library", 36, 9, BiomeRule.TAIGA, Placement.SURFACE),
            new Rule("witch_laboratory", 32, 8, BiomeRule.SWAMP, Placement.SURFACE),
            new Rule("sealed_shrine", 40, 10, BiomeRule.MOUNTAIN, Placement.SURFACE),
            new Rule("ritual_chamber", 22, 5, BiomeRule.UNDERGROUND, Placement.UNDERGROUND),
            new Rule("catacombs", 26, 6, BiomeRule.UNDERGROUND, Placement.UNDERGROUND),
            new Rule("spirit_ruins", 18, 4, BiomeRule.SPIRIT_ONLY, Placement.SPIRIT)
    );

    // ===================================================================================
    // Bookkeeping: which chunks have already generated a structure
    // ===================================================================================
    public static final class PlacedData extends SavedData {
        public static final String NAME = "pathwaysofthebeyond_structures";
        private final Set<Long> placed = new HashSet<>();

        public static PlacedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            PlacedData data = new PlacedData();
            for (long key : tag.getLongArray("placed")) {
                data.placed.add(key);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            long[] array = new long[placed.size()];
            int i = 0;
            for (long key : placed) {
                array[i++] = key;
            }
            tag.putLongArray("placed", array);
            return tag;
        }

        public boolean claim(long chunkKey) {
            if (placed.contains(chunkKey)) {
                return false;
            }
            placed.add(chunkKey);
            setDirty();
            return true;
        }

        public static PlacedData get(ServerLevel level) {
            DimensionDataStorage storage = level.getDataStorage();
            return storage.computeIfAbsent(new Factory<>(PlacedData::new, PlacedData::load, null), NAME);
        }
    }

    // ===================================================================================
    // Chunk-load placement
    // ===================================================================================
    public static void registerLoadListener(net.neoforged.bus.api.IEventBus bus) {
        // The chunk-load hook lives on the game bus; we subscribe in the mod constructor path.
    }

    /** Called from ChunkEvent.Load (registered in the main class). */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var chunk = event.getChunk();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        PlacedData data = PlacedData.get(level);
        RandomSource random = RandomSource.create(level.getSeed() ^ (chunkX * 341873128712L + chunkZ * 132897987541L));

        for (Rule rule : RULES) {
            long key = ((long) rule.id().hashCode() << 42) ^ ((long) chunkX << 21) ^ chunkZ;
            if (Math.floorMod(chunkX, rule.spacing()) != 0 || Math.floorMod(chunkZ, rule.spacing()) != 0) {
                continue;
            }
            if (random.nextInt(rule.separation()) != 0) {
                continue;
            }
            if (!matchesBiome(level, chunk.getPos().getMiddleBlockPosition(96), rule)) {
                continue;
            }
            if (!data.claim(key)) {
                continue;
            }
            place(level, rule.id(), chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), random);
        }
    }

    private static boolean matchesBiome(ServerLevel level, BlockPos pos, Rule rule) {
        var biome = level.getBiome(pos);
        return switch (rule.biome()) {
            case ANY -> !biome.is(BiomeTags.IS_OCEAN);
            case FOREST -> biome.is(BiomeTags.IS_FOREST);
            case TAIGA -> biome.is(BiomeTags.IS_TAIGA);
            case SWAMP -> biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)
                    || biome.unwrapKey().map(k -> k.location().getPath().contains("swamp")).orElse(false);
            case MOUNTAIN -> biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL);
            case UNDERGROUND, SPIRIT_ONLY -> true;
        };
    }

    // ===================================================================================
    // Placement
    // ===================================================================================
    public static boolean place(ServerLevel level, String structureId, int x, int z, RandomSource random) {
        Rule rule = RULES.stream().filter(r -> r.id().equals(structureId)).findFirst().orElse(null);
        StructureTemplate template = load(level, structureId);
        int y;
        if (rule != null && rule.placement() == Placement.UNDERGROUND) {
            y = 24 + random.nextInt(14);
        } else if (rule != null && rule.placement() == Placement.SPIRIT) {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        } else {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        }
        if (template == null) {
            // No authored template yet: build the procedural version instead of skipping. The
            // world must always contain the mod's places, and the NBT templates are an upgrade
            // to these layouts, not a requirement for them.
            BlockPos procedural = new BlockPos(x, y, z);
            boolean built = ProceduralStructures.build(level, structureId, procedural, random);
            if (!built) {
                PathwaysMod.LOGGER.warn("[Pathways] no structure builder for: {}", structureId);
            }
            return built;
        }
        BlockPos origin = new BlockPos(x - template.getSize().getX() / 2, y, z - template.getSize().getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setFinalizeEntities(true)
                .setKeepLiquid(false);
        template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_ALL);
        clearAbove(level, origin, template.getSize().getX(), template.getSize().getZ(),
                Math.max(4, template.getSize().getY() / 3));
        return true;
    }

    private static void clearAbove(ServerLevel level, BlockPos origin, int sizeX, int sizeZ, int height) {
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                for (int dy = 1; dy <= height; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0
                            && !state.is(Blocks.BEDROCK)) {
                        return;   // something solid above: leave the structure buried
                    }
                }
            }
        }
        for (int dx = -1; dx <= sizeX; dx++) {
            for (int dz = -1; dz <= sizeZ; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static StructureTemplate load(ServerLevel level, String id) {
        ResourceLocation location = PathwaysMod.id(id);
        Optional<StructureTemplate> template = level.getStructureManager().get(location);
        return template.orElse(null);
    }

    /** The Noticing: impossible structures that were never in the structure list. */
    public static void placeImpossible(ServerLevel level, BlockPos at, Random random) {
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        // a monolith of void stone, bricked in gothic courses, with no door and no purpose
        int height = 8 + random.nextInt(12);
        int width = 3 + random.nextInt(3);
        for (int y = 0; y < height; y++) {
            for (int x = -width; x <= width; x++) {
                for (int z = -width; z <= width; z++) {
                    boolean shell = Math.abs(x) == width || Math.abs(z) == width || y == 0 || y == height - 1;
                    if (!shell) {
                        continue;
                    }
                    BlockState state = (y % 6 == 5)
                            ? ModBlocks.GothicBricks.get().defaultBlockState()
                            : ModBlocks.VoidStone.get().defaultBlockState();
                    level.setBlock(ground.offset(x, y, z), state, Block.UPDATE_ALL);
                }
            }
        }
        // and a shard of memory set into the ground next to it, which is the only light here
        level.setBlock(ground.offset(width + 1, 0, width + 1),
                ModBlocks.MemoryShardBlock.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Debug helper: force a structure at a position, ignoring spacing rules. */
    public static boolean forceAt(ServerLevel level, String structureId, BlockPos pos, RandomSource random) {
        return place(level, structureId, pos.getX(), pos.getZ(), random);
    }
}
