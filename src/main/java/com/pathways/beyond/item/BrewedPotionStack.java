package com.pathways.beyond.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The pathway + sequence + colour a brewed potion carries.
 *
 * <p>Stored as a data component so a potion keeps its identity when traded, stored or dropped:
 * a bottle of Sequence 4 is the same bottle in anyone's hands, and the wrong pathway still
 * rejects it.
 */
public record BrewedPotionStack(String pathway, int sequence, int colour, String glow) {
    public static final Codec<BrewedPotionStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("pathway").forGetter(BrewedPotionStack::pathway),
            Codec.INT.fieldOf("sequence").forGetter(BrewedPotionStack::sequence),
            Codec.INT.fieldOf("colour").forGetter(BrewedPotionStack::colour),
            Codec.STRING.optionalFieldOf("glow", "spirit").forGetter(BrewedPotionStack::glow)
    ).apply(instance, BrewedPotionStack::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BrewedPotionStack> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BrewedPotionStack::pathway,
                    ByteBufCodecs.VAR_INT, BrewedPotionStack::sequence,
                    ByteBufCodecs.INT, BrewedPotionStack::colour,
                    ByteBufCodecs.STRING_UTF8, BrewedPotionStack::glow,
                    BrewedPotionStack::new);
}
