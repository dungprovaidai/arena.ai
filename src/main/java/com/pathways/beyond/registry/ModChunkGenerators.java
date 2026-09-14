package com.pathways.beyond.registry;

import com.mojang.serialization.Codec;
import com.pathways.beyond.spirit.SpiritChunkGenerator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.pathways.beyond.PathwaysMod.MOD_ID;

/** The Spirit World's terrain generator. Referenced by data/pathwaysofthebeyond/dimension/*.json. */
public final class ModChunkGenerators {
    private ModChunkGenerators() {}

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

    public static final DeferredHolder<Codec<? extends ChunkGenerator>, Codec<? extends ChunkGenerator>>
            SPIRIT_WASTES = CHUNK_GENERATORS.register("spirit_wastes", () -> SpiritChunkGenerator.CODEC);

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
    }
}
