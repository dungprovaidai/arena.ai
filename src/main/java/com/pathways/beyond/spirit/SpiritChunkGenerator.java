package com.pathways.beyond.spirit;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pathways.beyond.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The Spirit World's terrain generator.
 *
 * <p>Deliberately simple and fully deterministic: a value-noise height field of desaturated
 * stone, drowned in fog, with broken pillars standing in it. No ores, no caves worth
 * exploring, no reason to be here except the architecture and the things standing in it.
 *
 * <p>Writing blocks directly (rather than composing vanilla noise settings) keeps the
 * generator self-contained and cheap: a Spirit World chunk costs a fraction of an Overworld
 * chunk, which matters because the dimension is entered for short visits.
 */
public class SpiritChunkGenerator extends ChunkGenerator {
    public static final MapCodec<SpiritChunkGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            Codec.LONG.fieldOf("seed").stable().forGetter(generator -> generator.seed)
    ).apply(instance, instance.stable(SpiritChunkGenerator::new)));

    public static final Codec<SpiritChunkGenerator> CODEC = MAP_CODEC.codec();

    private static final int BASE_HEIGHT = 72;
    private final long seed;

    public SpiritChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MAP_CODEC;
    }

    // ===================================================================================
    // Deterministic value noise - no vanilla noise routers, no settings JSON
    // ===================================================================================
    private static float hash(int x, int z, long seed) {
        long h = seed;
        h = h * 6364136223846793005L + x * 1442695040888963407L;
        h = h * 6364136223846793005L + z * 1442695040888963407L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return ((h >>> 11) / (float) (1L << 53));
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private float valueNoise(double x, double z, double scale) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        float tx = smooth((float) (fx - x0));
        float tz = smooth((float) (fz - z0));
        float a = hash(x0, z0, seed);
        float b = hash(x0 + 1, z0, seed);
        float c = hash(x0, z0 + 1, seed);
        float d = hash(x0 + 1, z0 + 1, seed);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private int heightAt(int x, int z) {
        float h = valueNoise(x, z, 96.0) * 0.6f
                + valueNoise(x, z, 32.0) * 0.3f
                + valueNoise(x, z, 12.0) * 0.1f;
        return BASE_HEIGHT + (int) ((h - 0.5f) * 26.0f);
    }

    // ===================================================================================
    // Generation
    // ===================================================================================
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        BlockState stone = ModBlocks.DesecratedStone.get().defaultBlockState();
        BlockState deep = ModBlocks.VoidStone.get().defaultBlockState();
        BlockState surface = Blocks.GRAVEL.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinBuildHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                int height = heightAt(worldX, worldZ);
                for (int y = minY; y < height; y++) {
                    pos.set(x, y, z);
                    if (y > height - 3) {
                        chunk.setBlockState(pos, surface, false);
                    } else if (y > height - 12) {
                        chunk.setBlockState(pos, stone, false);
                    } else {
                        chunk.setBlockState(pos, deep, false);
                    }
                }
                // impossible architecture: occasional broken pillars that go nowhere
                if (hash(worldX / 7, worldZ / 7, seed + 991) > 0.965f && height > BASE_HEIGHT - 8) {
                    int pillarHeight = 6 + (int) (hash(worldX, worldZ, seed + 7) * 18);
                    for (int y = 0; y < pillarHeight; y++) {
                        pos.set(x, height + y, z);
                        if (chunk.getBlockState(pos).isAir()) {
                            chunk.setBlockState(pos, y % 5 == 4 ? ModBlocks.GothicBricks.get().defaultBlockState()
                                    : stone, false);
                        }
                    }
                    // some pillars carry a chalk circle at the top, which should not be there
                    if (hash(worldX, worldZ, seed + 13) > 0.7f) {
                        pos.set(x, height + pillarHeight, z);
                        chunk.setBlockState(pos, ModBlocks.ChalkCircle.get().defaultBlockState(), false);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random,
                             ChunkAccess chunk) {
        // surface is written during fillFromNoise: this dimension has no biome surface rules
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // no caves in the Spirit World; the architecture is the only interior worth entering
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // nothing spawns here naturally: the mobs in this dimension are placed by hand
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 62;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.max(getMinY(), heightAt(x, z));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int height = heightAt(x, z);
        BlockState[] states = new BlockState[Math.max(1, height - level.getMinBuildHeight())];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = y > height - 3 ? Blocks.GRAVEL.defaultBlockState()
                    : y > height - 12 ? ModBlocks.DesecratedStone.get().defaultBlockState()
                    : ModBlocks.VoidStone.get().defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Pathways Spirit World generator: value-noise wastes, seed " + seed);
        info.add("Surface height here: " + heightAt(pos.getX(), pos.getZ()));
    }

    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk,
                                     StructureManager structureManager) {
        // biome decoration (our ruin features) is applied by the vanilla feature pipeline
        super.applyBiomeDecoration(level, chunk, structureManager);
    }

    @Override
    public Holder<Biome> getBiomeSourceHolder() {
        return null;
    }
}
