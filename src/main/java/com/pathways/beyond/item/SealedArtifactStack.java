package com.pathways.beyond.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.pathways.beyond.registry.ModComponents;

/**
 * Whether an artifact is still sealed, and how many times it has been used.
 *
 * <p>A sealed artifact is a quest, not a reward: it must be unsealed at an altar, and the use
 * counter is kept because the drawbacks escalate the longer you keep using one.
 */
public record SealedArtifactStack(boolean sealed, int uses) {
    public static final Codec<SealedArtifactStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("sealed", true).forGetter(SealedArtifactStack::sealed),
            Codec.INT.optionalFieldOf("uses", 0).forGetter(SealedArtifactStack::uses)
    ).apply(instance, SealedArtifactStack::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SealedArtifactStack> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SealedArtifactStack::sealed,
                    ByteBufCodecs.VAR_INT, SealedArtifactStack::uses,
                    SealedArtifactStack::new);

    public static boolean isUnsealed(ItemStack stack) {
        SealedArtifactStack state = stack.get(ModComponents.ARTIFACT_STATE.get());
        return state == null || !state.sealed();
    }

    public static void setSealed(ItemStack stack, boolean sealed) {
        SealedArtifactStack state = stack.get(ModComponents.ARTIFACT_STATE.get());
        int uses = state == null ? 0 : state.uses();
        stack.set(ModComponents.ARTIFACT_STATE.get(), new SealedArtifactStack(sealed, uses));
    }

    public static void addUse(ItemStack stack) {
        SealedArtifactStack state = stack.get(ModComponents.ARTIFACT_STATE.get());
        int uses = (state == null ? 0 : state.uses()) + 1;
        boolean sealed = state != null && state.sealed();
        stack.set(ModComponents.ARTIFACT_STATE.get(), new SealedArtifactStack(sealed, uses));
    }
}
