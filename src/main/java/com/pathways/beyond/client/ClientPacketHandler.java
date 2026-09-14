package com.pathways.beyond.client;

import com.pathways.beyond.network.OccultMessages;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side reception of every occult packet.
 *
 * <p>All of the mod's atmosphere is driven from here: the fog that thickens in the Spirit
 * World, the camera that drifts, the grain that creeps in at low sanity, and the particles
 * that belong to each individual ability. Each visual category is handled separately so that
 * no two effects ever look like the same reused particle burst.
 */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    // ---- cached server state -----------------------------------------------------------
    private static String pathwayId = "";
    private static String pathwayName = "Unbegun";
    private static int sequence = -1;
    private static float digestion;
    private static float sanity = 100.0f;
    private static float corruption;
    private static boolean spiritForm;
    private static int tetherStrain;
    private static String lastFeedback = "";
    private static List<String> unlockedAbilities = new ArrayList<>();
    private static List<String> knownRituals = new ArrayList<>();
    private static boolean beyondUnlocked;

    // ---- ambient state driven by packets ----------------------------------------------
    private static float fogDensity;
    private static float fogTarget;
    private static int fogColour = 0x2A2E33;
    private static float cameraShake;
    private static float textGlitch;
    private static int whisperTicks;
    private static int hallucinationTicks;
    private static final List<Hallucination> HALLUCINATIONS = new ArrayList<>();

    public record Hallucination(int kind, double x, double y, double z, float strength, int ttl) {}

    // ===================================================================================
    // Dispatch
    // ===================================================================================
    public static void handle(CompoundTag tag) {
        String kind = tag.getString(OccultMessages.KIND);
        switch (kind) {
            case OccultMessages.C_PATHWAY_SYNC -> applyPathway(tag);
            case OccultMessages.C_OCCULT_SYNC -> applyOccult(tag);
            case OccultMessages.C_VISUAL -> applyVisual(tag);
            case OccultMessages.C_HALLUCINATION -> applyHallucination(tag);
            case OccultMessages.C_ABILITY_FEEDBACK -> lastFeedback = tag.getString(OccultMessages.F_MESSAGE);
            case OccultMessages.C_SPIRIT_STATE -> {
                spiritForm = tag.getBoolean(OccultMessages.F_SPIRIT);
                tetherStrain = tag.getInt(OccultMessages.F_TETHER);
            }
            case OccultMessages.C_RITUAL_STATE -> ClientScreens.onRitualState(tag);
            case OccultMessages.C_THREAD_SYNC -> ClientThreadRender.accept(tag);
            default -> {
            }
        }
    }

    private static void applyPathway(CompoundTag tag) {
        pathwayId = tag.getString(OccultMessages.F_PATHWAY);
        pathwayName = pathwayId;
        sequence = tag.getInt(OccultMessages.F_SEQUENCE);
        digestion = tag.getFloat(OccultMessages.F_DIGESTION);
        unlockedAbilities = readStrings(tag, OccultMessages.F_UNLOCKED);
        knownRituals = readStrings(tag, OccultMessages.F_RITUALS);
        beyondUnlocked = tag.getBoolean(OccultMessages.F_BEYOND);
    }

    private static List<String> readStrings(CompoundTag tag, String key) {
        List<String> result = new ArrayList<>();
        ListTag list = tag.getList(key, CompoundTag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }

    private static void applyOccult(CompoundTag tag) {
        sanity = tag.getFloat(OccultMessages.F_SANITY);
        corruption = tag.getFloat(OccultMessages.F_CORRUPTION);
        spiritForm = tag.getBoolean(OccultMessages.F_SPIRIT);
    }

    private static void applyVisual(CompoundTag tag) {
        int id = tag.getInt(OccultMessages.F_TYPE);
        double x = tag.getDouble(OccultMessages.F_X);
        double y = tag.getDouble(OccultMessages.F_Y);
        double z = tag.getDouble(OccultMessages.F_Z);
        float strength = tag.getFloat(OccultMessages.F_INTENSITY);
        int duration = tag.getInt(OccultMessages.F_DURATION);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RandomSource random = minecraft.level.random;
        switch (id) {
            case OccultMessages.V_ABILITY_CAST -> {
                for (int i = 0; i < 14; i++) {
                    minecraft.level.addParticle(ModParticles.RuneDust.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.4,
                            (random.nextDouble() - 0.5) * 0.6);
                }
            }
            case OccultMessages.V_MIRACLE -> {
                for (int i = 0; i < 30; i++) {
                    minecraft.level.addParticle(ModParticles.BeyondShard.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 0.8, random.nextDouble() * 0.8,
                            (random.nextDouble() - 0.5) * 0.8);
                }
                minecraft.level.playLocalSound(x, y, z, ModSounds.SequenceAdvance.get(),
                        SoundSource.PLAYERS, strength, 1.4f, false);
            }
            case OccultMessages.V_RITUAL_START, OccultMessages.V_RITUAL_PULSE -> {
                for (int i = 0; i < 40; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double radius = strength * (1.0 + random.nextDouble());
                    minecraft.level.addParticle(ModParticles.RuneDust.get(),
                            x + Math.cos(angle) * radius, y + 0.2, z + Math.sin(angle) * radius,
                            -Math.cos(angle) * 0.1, 0.05, -Math.sin(angle) * 0.1);
                }
                minecraft.level.playLocalSound(x, y, z, ModSounds.RitualPulse.get(),
                        SoundSource.PLAYERS, strength, 1.0f, false);
            }
            case OccultMessages.V_RITUAL_COMPLETE -> {
                for (int i = 0; i < 60; i++) {
                    minecraft.level.addParticle(ModParticles.EmberOccult.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 1.2,
                            (random.nextDouble() - 0.5) * 1.2);
                }
                minecraft.level.playLocalSound(x, y, z, ModSounds.RitualComplete.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
            case OccultMessages.V_RITUAL_FAIL -> {
                for (int i = 0; i < 50; i++) {
                    minecraft.level.addParticle(ModParticles.VeilSmoke.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 1.5, random.nextDouble(), (random.nextDouble() - 0.5) * 1.5);
                }
                cameraShake = Math.max(cameraShake, 8.0f);
                minecraft.level.playLocalSound(x, y, z, ModSounds.RitualFail.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
            case OccultMessages.V_THREAD_BIND -> {
                for (int i = 0; i < 12; i++) {
                    minecraft.level.addParticle(ModParticles.SoulFlow.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4,
                            (random.nextDouble() - 0.5) * 0.4);
                }
                minecraft.level.playLocalSound(x, y, z, ModSounds.ThreadBind.get(),
                        SoundSource.PLAYERS, 0.7f, 1.0f, false);
            }
            case OccultMessages.V_THREAD_SEVER -> {
                for (int i = 0; i < 20; i++) {
                    minecraft.level.addParticle(ModParticles.SoulFlow.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 1.0, (random.nextDouble() - 0.5) * 1.0,
                            (random.nextDouble() - 0.5) * 1.0);
                }
                minecraft.level.playLocalSound(x, y, z, ModSounds.ThreadSever.get(),
                        SoundSource.PLAYERS, 0.9f, 1.2f, false);
            }
            case OccultMessages.V_SOUL_SNAP -> {
                cameraShake = Math.max(cameraShake, 14.0f);
                textGlitch = Math.max(textGlitch, 60.0f);
                for (int i = 0; i < 60; i++) {
                    minecraft.level.addParticle(ModParticles.SpiritMote.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 2.0, (random.nextDouble() - 0.5) * 2.0,
                            (random.nextDouble() - 0.5) * 2.0);
                }
                minecraft.level.playLocalSound(x, y, z, ModSounds.SpiritSnap.get(),
                        SoundSource.PLAYERS, 1.0f, 0.8f, false);
            }
            case OccultMessages.V_SPIRIT_ENTER, OccultMessages.V_SPIRIT_EXIT -> {
                fogTarget = id == OccultMessages.V_SPIRIT_ENTER ? 0.7f : 0.0f;
                for (int i = 0; i < 50; i++) {
                    minecraft.level.addParticle(ModParticles.SpiritMote.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2,
                            (random.nextDouble() - 0.5) * 1.2);
                }
                minecraft.level.playLocalSound(x, y, z,
                        id == OccultMessages.V_SPIRIT_ENTER ? ModSounds.SpiritEnter.get()
                                : ModSounds.SpiritExit.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
            case OccultMessages.V_SEQUENCE_ADVANCE -> {
                cameraShake = Math.max(cameraShake, 20.0f);
                for (int i = 0; i < 90; i++) {
                    minecraft.level.addParticle(ModParticles.BlackWisp.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 1.5, random.nextDouble() * 2.0,
                            (random.nextDouble() - 0.5) * 1.5);
                }
            }
            case OccultMessages.V_BEYOND_NOTICING -> {
                fogTarget = Math.max(fogTarget, 0.5f);
                textGlitch = Math.max(textGlitch, 200.0f);
                for (int i = 0; i < 120; i++) {
                    minecraft.level.addParticle(ModParticles.BeyondShard.get(),
                            x + (random.nextDouble() - 0.5) * 20.0, y + random.nextDouble() * 8.0,
                            z + (random.nextDouble() - 0.5) * 20.0, 0.0, -0.01, 0.0);
                }
            }
            case OccultMessages.V_DEATH_OF_BOSS -> {
                cameraShake = Math.max(cameraShake, 12.0f);
                for (int i = 0; i < 80; i++) {
                    minecraft.level.addParticle(ModParticles.BlackWisp.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 2.0,
                            (random.nextDouble() - 0.5) * 1.2);
                }
            }
            case OccultMessages.V_ARTIFACT_ACTIVATE -> {
                for (int i = 0; i < 24; i++) {
                    minecraft.level.addParticle(ModParticles.RuneDust.get(), x, y, z,
                            (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8,
                            (random.nextDouble() - 0.5) * 0.8);
                }
            }
            default -> {
            }
        }
        if (duration > 0 && fogDensity < 0.01f) {
            // any major visual event briefly tints the fog so the effect reads at range
            fogTarget = Math.max(fogTarget, 0.05f * strength);
        }
    }

    private static void applyHallucination(CompoundTag tag) {
        HALLUCINATIONS.add(new Hallucination(
                tag.getInt(OccultMessages.F_TYPE),
                tag.getDouble(OccultMessages.F_X),
                tag.getDouble(OccultMessages.F_Y),
                tag.getDouble(OccultMessages.F_Z),
                tag.getFloat(OccultMessages.F_INTENSITY),
                tag.getInt(OccultMessages.F_DURATION)));
        int kind = tag.getInt(OccultMessages.F_TYPE);
        if (kind == OccultMessages.H_CAMERA_SHAKE) {
            cameraShake = Math.max(cameraShake, 6.0f);
        } else if (kind == OccultMessages.H_TEXT_GLITCH) {
            textGlitch = Math.max(textGlitch, 80.0f);
        } else if (kind == OccultMessages.H_WHISPER) {
            whisperTicks = Math.max(whisperTicks, 60);
        }
        hallucinationTicks = Math.max(hallucinationTicks, 100);
    }

    // ===================================================================================
    // Client tick: decay ambient values and play the hallucination list
    // ===================================================================================
    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        // fog eases toward its target; the Spirit World forces a floor on it
        boolean inSpiritWorld = minecraft.level.dimension().location().toString()
                .startsWith("pathwaysofthebeyond:spirit_world");
        float floor = inSpiritWorld ? 0.55f : 0.0f;
        float ceiling = Math.max(fogTarget, floor);
        fogDensity += (ceiling - fogDensity) * 0.02f;
        fogTarget *= 0.995f;

        cameraShake *= 0.9f;
        textGlitch *= 0.985f;
        if (whisperTicks > 0) {
            whisperTicks--;
        }
        if (hallucinationTicks > 0) {
            hallucinationTicks--;
        }

        // hallucinations: driven here so they continue to unfold over several seconds
        HALLUCINATIONS.removeIf(hallucination -> hallucination.ttl() <= 0);
        for (int i = 0; i < HALLUCINATIONS.size(); i++) {
            Hallucination hallucination = HALLUCINATIONS.get(i);
            renderHallucination(minecraft, hallucination);
            HALLUCINATIONS.set(i, new Hallucination(hallucination.kind(), hallucination.x(), hallucination.y(),
                    hallucination.z(), hallucination.strength(), hallucination.ttl() - 1));
        }

        // low sanity colour bleed: the world's saturation falls away as sanity drains
        if (sanity < 40.0f && player.tickCount % 4 == 0) {
            RandomSource random = player.getRandom();
            minecraft.level.addParticle(ModParticles.ChromaticSpeck.get(),
                    player.getX() + (random.nextDouble() - 0.5) * 6.0,
                    player.getY() + random.nextDouble() * 2.0,
                    player.getZ() + (random.nextDouble() - 0.5) * 6.0, 0.0, 0.0, 0.0);
        }
        // spirit form: the player's own thread is visible trailing behind them
        if (spiritForm && player.tickCount % 2 == 0) {
            minecraft.level.addParticle(ModParticles.SpiritMote.get(), player.getX(),
                    player.getY() + 1.0, player.getZ(), 0.0, 0.01, 0.0);
        }
    }

    private static void renderHallucination(Minecraft minecraft, Hallucination hallucination) {
        RandomSource random = minecraft.level.random;
        double x = hallucination.x();
        double y = hallucination.y();
        double z = hallucination.z();
        switch (hallucination.kind()) {
            case OccultMessages.H_SILHOUETTE -> {
                // a figure at the edge of vision; it is only ever seen, never reached
                for (int i = 0; i < 6; i++) {
                    minecraft.level.addParticle(ModParticles.ShadowMove.get(),
                            x + (random.nextDouble() - 0.5) * 0.6,
                            y + random.nextDouble() * 1.8,
                            z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.0, 0.0);
                }
            }
            case OccultMessages.H_WHISPER -> {
                if (random.nextFloat() < 0.25f) {
                    minecraft.level.playLocalSound(x, y, z, ModSounds.WhisperNear.get(),
                            SoundSource.AMBIENT, hallucination.strength() * 0.6f, 0.9f + random.nextFloat() * 0.3f,
                            false);
                }
            }
            case OccultMessages.H_PHANTOM_SOUND -> {
                if (random.nextFloat() < 0.12f) {
                    minecraft.level.playLocalSound(x, y, z, ModSounds.Breathing.get(),
                            SoundSource.AMBIENT, hallucination.strength(), 0.7f, false);
                }
            }
            case OccultMessages.H_FAKE_FOOTSTEP -> {
                if (random.nextFloat() < 0.2f) {
                    minecraft.level.playLocalSound(x, y, z, ModSounds.HollowStep.get(),
                            SoundSource.AMBIENT, hallucination.strength() * 0.8f, 1.0f, false);
                }
            }
            case OccultMessages.H_DOOR -> {
                if (random.nextFloat() < 0.15f) {
                    minecraft.level.playLocalSound(x, y, z, ModSounds.Knock.get(),
                            SoundSource.AMBIENT, hallucination.strength(), 0.8f, false);
                }
            }
            case OccultMessages.H_FOG_SHIFT -> {
                fogTarget = Math.max(fogTarget, hallucination.strength() * 0.4f);
            }
            case OccultMessages.H_SHADOW_MOVE -> {
                for (int i = 0; i < 4; i++) {
                    minecraft.level.addParticle(ModParticles.ShadowMove.get(), x, y + 0.1, z,
                            (random.nextDouble() - 0.5) * 0.4, 0.0, (random.nextDouble() - 0.5) * 0.4);
                }
            }
            default -> {
            }
        }
    }

    // ---- accessors for the HUD and renderer -------------------------------------------
    public static float sanity() {
        return sanity;
    }

    public static float corruption() {
        return corruption;
    }

    public static String pathwayName() {
        return pathwayName;
    }

    public static String pathwayId() {
        return pathwayId;
    }

    public static int sequence() {
        return sequence;
    }

    public static float digestion() {
        return digestion;
    }

    /** The ids of every ability the player may currently cast, as sent by the server. */
    public static List<String> unlockedAbilities() {
        return unlockedAbilities;
    }

    public static List<String> knownRituals() {
        return knownRituals;
    }

    public static boolean beyondUnlocked() {
        return beyondUnlocked;
    }

    public static boolean spiritForm() {
        return spiritForm;
    }

    public static int tetherStrain() {
        return tetherStrain;
    }

    public static float fogDensity() {
        return fogDensity;
    }

    public static int fogColour() {
        return fogColour;
    }

    public static float cameraShake() {
        return cameraShake;
    }

    public static float textGlitch() {
        return textGlitch;
    }

    public static int whisperTicks() {
        return whisperTicks;
    }

    public static int hallucinationCount() {
        return HALLUCINATIONS.size();
    }

    /** Called when the server sends a set of currently visible threads. */
    public static List<int[]> lastThreads = new ArrayList<>();

    static void setThreads(ListTag list) {
        List<int[]> threads = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            threads.add(new int[]{entry.getInt("id"), entry.getInt("kind")});
        }
        lastThreads = threads;
    }

    public static Component feedback() {
        return lastFeedback.isEmpty() ? null : Component.literal(lastFeedback);
    }
}
