package com.pathways.beyond.registry;

import com.mojang.serialization.Codec;
import com.pathways.beyond.item.BrewedPotionStack;
import com.pathways.beyond.item.PotionTraits;
import com.pathways.beyond.item.SealedArtifactStack;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

import static com.pathways.beyond.PathwaysMod.MOD_ID;

/**
 * Data components (1.21 replacement for item NBT).
 *
 * <p>Potions carry their pathway + sequence + digestion potency; artifacts carry their
 * seal state and how many times they have been used. Both are visible to the tooltip and
 * survive being dropped, traded or put in a chest - a forbidden artifact stays forbidden.
 */
public final class ModComponents {
    private ModComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    /** Pathway + sequence + digestion potency of a pathway potion. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BrewedPotionStack>> POTION_TRAITS =
            COMPONENTS.register("potion_traits", () -> DataComponentType.<BrewedPotionStack>builder()
                    .persistent(BrewedPotionStack.CODEC)
                    .networkSynchronized(BrewedPotionStack.STREAM_CODEC)
                    .build());

    /** How far along a potion's digestion is (0..100), stored on the potion as potency memory. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PotionTraits>> POTION_MEMORY =
            COMPONENTS.register("potion_memory", () -> DataComponentType.<PotionTraits>builder()
                    .persistent(PotionTraits.CODEC)
                    .networkSynchronized(PotionTraits.STREAM_CODEC)
                    .build());

    /** Artifact seal state: sealed / unsealed / partially unsealed, plus use count. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SealedArtifactStack>> ARTIFACT_STATE =
            COMPONENTS.register("artifact_state", () -> DataComponentType.<SealedArtifactStack>builder()
                    .persistent(SealedArtifactStack.CODEC)
                    .networkSynchronized(SealedArtifactStack.STREAM_CODEC)
                    .build());

    /** Ritual components recorded on a ritual dagger (blood charges). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BLOOD_CHARGES =
            COMPONENTS.register("blood_charges", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Soul Threads currently attached to a spool. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> BOUND_THREADS =
            COMPONENTS.register("bound_threads", () -> DataComponentType.<List<String>>builder()
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                    .build());

    /** Knowledge recorded by the Codex: ritual ids learned. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> KNOWN_RITUALS =
            COMPONENTS.register("known_rituals", () -> DataComponentType.<List<String>>builder()
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                    .build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
