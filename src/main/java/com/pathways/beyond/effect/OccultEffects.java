package com.pathways.beyond.effect;

import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mob effect behaviour.
 *
 * <p>Effects are created from one factory so that adding an effect in tools/content.py is
 * enough to get a working, self-documenting effect with its own colour. The colours are
 * taken straight from the mod palette so the HUD icons and the particles always match.
 */
public final class OccultEffects {
    private OccultEffects() {}

    public static MobEffect create(String id) {
        return switch (id) {
            case "sanity_bleed" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x4B3068) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 40 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        OccultState state = ModAttachments.occult(player);
                        state.addSanity(-(0.35f + amplifier * 0.25f));
                    }
                    return true;
                }
            };
            case "hallucinating" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x7B5AA0);
            case "spirit_form" -> new OccultEffect(id, MobEffectCategory.NEUTRAL, 0xA8DCE2) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 10 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        com.pathways.beyond.spirit.SoulProjection.applySpiritMovement(player);
                    }
                    return true;
                }
            };
            case "corruption_surge" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x8C1A24) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 60 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        SanitySystem.addCorruption(player, 0.5f + amplifier * 0.4f);
                    }
                    return true;
                }
            };
            case "thread_bond" -> new OccultEffect(id, MobEffectCategory.NEUTRAL, 0x2F4232);
            case "unblinking_gaze" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x13273F) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 20 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        ModAttachments.occult(player).addSanity(-(0.5f + amplifier * 0.5f));
                        SanitySystem.addCorruption(player, 0.1f);
                        ServerLevel level = player.serverLevel();
                        level.sendParticles(ModParticles.ChromaticSpeck.get(), player.getX(),
                                player.getY() + 1.5, player.getZ(), 4, 0.4, 0.4, 0.4, 0.01);
                        // being watched by something with no eyelids means you cannot hide
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                    }
                    return true;
                }
            };
            case "curse_of_the_veil" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x5A0F17) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 100 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, amplifier));
                        if (player.getRandom().nextFloat() < 0.4f) {
                            com.pathways.beyond.sanity.HallucinationDirector.force(player,
                                    com.pathways.beyond.network.OccultMessages.H_SHADOW_MOVE);
                        }
                    }
                    return true;
                }
            };
            case "clarity" -> new OccultEffect(id, MobEffectCategory.BENEFICIAL, 0xDFF6F7);
            case "digestion_quickened" -> new OccultEffect(id, MobEffectCategory.BENEFICIAL, 0xD4B265);
            case "cosmic_dread" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x2A1B3C) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 200 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                        "IT KNOWS WHERE YOU ARE.")
                                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
                        SanitySystem.addCorruption(player, 1.0f);
                    }
                    return true;
                }
            };
            // "lost control" is applied by the sanity system at Sanity 0 and is implemented
            // by the possession path rather than as a passive effect tick
            case "lost_control" -> new OccultEffect(id, MobEffectCategory.HARMFUL, 0x2B070D) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return duration % 40 == 0;
                }

                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity instanceof ServerPlayer player) {
                        // the hands are not yours: the body lurches in a direction you did not pick
                        double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                        player.push(Math.cos(angle) * 0.35, 0.15, Math.sin(angle) * 0.35);
                        player.hurtMarked = true;
                    }
                    return true;
                }
            };
            default -> new OccultEffect(id, MobEffectCategory.NEUTRAL, 0x6B6570);
        };
    }

    /** Base class: one place for the shared behaviour (no instant effect, no heart particles). */
    private static class OccultEffect extends MobEffect {
        protected OccultEffect(String id, MobEffectCategory category, int colour) {
            super(category, colour);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return false;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            return true;
        }
    }
}
