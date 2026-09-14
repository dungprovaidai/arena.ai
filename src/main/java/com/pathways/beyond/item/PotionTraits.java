package com.pathways.beyond.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How much of a potion the body has processed, and whether it rejected it.
 *
 * <p>Digestion is the real gate on Sequence advancement: a potion can be drunk long before the
 * body accepts it, and acting against your pathway makes the body reject what it was given.
 */
public record PotionTraits(float digested, boolean rejected) {
    public static final Codec<PotionTraits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("digested").forGetter(PotionTraits::digested),
            Codec.BOOL.optionalFieldOf("rejected", false).forGetter(PotionTraits::rejected)
    ).apply(instance, PotionTraits::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PotionTraits> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, PotionTraits::digested,
                    ByteBufCodecs.BOOL, PotionTraits::rejected,
                    PotionTraits::new);
}
