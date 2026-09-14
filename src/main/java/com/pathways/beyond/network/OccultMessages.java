package com.pathways.beyond.network;

import net.minecraft.nbt.CompoundTag;

/**
 * Message schema shared by both sides.
 *
 * <p>Rather than a payload class per message type, the mod sends one clientbound and one
 * serverbound envelope carrying a {@link CompoundTag} and a {@code kind} discriminator.
 * This keeps the wire format in one place, makes the protocol easy to log/verify, and
 * removes a whole class of "field order drifted between reader and writer" bugs.
 *
 * <p>All field keys are defined here as constants - never as literals at the call sites.
 */
public final class OccultMessages {
    private OccultMessages() {}

    // ---- envelope keys ----
    public static final String KIND = "kind";
    public static final String TAG = "payload";

    // ---- clientbound kinds ----
    public static final String C_PATHWAY_SYNC = "pathway_sync";
    public static final String C_OCCULT_SYNC = "occult_sync";
    public static final String C_VISUAL = "visual";
    public static final String C_HALLUCINATION = "hallucination";
    public static final String C_RITUAL_STATE = "ritual_state";
    public static final String C_THREAD_SYNC = "thread_sync";
    public static final String C_SPIRIT_STATE = "spirit_state";
    public static final String C_ABILITY_FEEDBACK = "ability_feedback";

    // ---- serverbound kinds ----
    public static final String S_USE_ABILITY = "use_ability";
    public static final String S_RITUAL_ACTION = "ritual_action";
    public static final String S_SOUL_PROJECT = "soul_project";
    public static final String S_THREAD_ACTION = "thread_action";
    public static final String S_ARTIFACT_ACTION = "artifact_action";
    public static final String S_OPEN_SCREEN = "open_screen";

    // ---- shared field keys ----
    public static final String F_PATHWAY = "pathway";
    public static final String F_SEQUENCE = "sequence";
    public static final String F_DIGESTION = "digestion";
    public static final String F_UNLOCKED = "unlocked";
    public static final String F_RITUALS = "rituals";
    public static final String F_BEYOND = "beyond";
    public static final String F_SANITY = "sanity";
    public static final String F_CORRUPTION = "corruption";
    public static final String F_SPIRIT = "spirit";
    public static final String F_TETHER = "tether";
    public static final String F_THREADS = "threads";
    public static final String F_ABILITY = "ability";
    public static final String F_ACTION = "action";
    public static final String F_X = "x";
    public static final String F_Y = "y";
    public static final String F_Z = "z";
    public static final String F_ENTITY = "entity";
    public static final String F_TYPE = "type";
    public static final String F_INTENSITY = "intensity";
    public static final String F_DURATION = "duration";
    public static final String F_COLOUR = "colour";
    public static final String F_STABILITY = "stability";
    public static final String F_DANGER = "danger";
    public static final String F_RECIPE = "recipe";
    public static final String F_MISSING = "missing";
    public static final String F_ACTIVE = "active";
    public static final String F_MESSAGE = "message";
    public static final String F_STAGE = "stage";
    public static final String F_TIER = "tier";
    public static final String F_SEQUENCE_NAME = "sequence_name";
    public static final String F_OLD_SEQUENCE = "old_sequence";

    // ---- visual event ids (client VFX switch) ----
    public static final int V_RITUAL_START = 0;
    public static final int V_RITUAL_PULSE = 1;
    public static final int V_RITUAL_COMPLETE = 2;
    public static final int V_RITUAL_FAIL = 3;
    public static final int V_SEQUENCE_ADVANCE = 4;
    public static final int V_CORRUPTION_PULSE = 5;
    public static final int V_SPIRIT_ENTER = 6;
    public static final int V_SPIRIT_EXIT = 7;
    public static final int V_SOUL_SNAP = 8;
    public static final int V_ABILITY_CAST = 9;
    public static final int V_THREAD_BIND = 10;
    public static final int V_THREAD_SEVER = 11;
    public static final int V_ARTIFACT_ACTIVATE = 12;
    public static final int V_BEYOND_NOTICING = 13;
    public static final int V_MIRACLE = 14;
    public static final int V_DEATH_OF_BOSS = 15;

    // ---- hallucination kinds (client renderer switch, and server-side real events) ----
    public static final int H_FAKE_MOB = 0;
    public static final int H_FAKE_PLAYER = 1;
    public static final int H_FAKE_FOOTSTEP = 2;
    public static final int H_WHISPER = 3;
    public static final int H_DOOR = 4;
    public static final int H_FAKE_TORCH = 5;
    public static final int H_FAKE_BLOCK = 6;
    public static final int H_FAKE_CHEST = 7;
    public static final int H_SHADOW_MOVE = 8;
    public static final int H_DISTANT_ENTITY = 9;
    public static final int H_SILHOUETTE = 10;
    public static final int H_ITEM_DISTORTION = 11;
    public static final int H_TEXT_GLITCH = 12;
    public static final int H_CAMERA_SHAKE = 13;
    public static final int H_FOG_SHIFT = 14;
    public static final int H_LIGHT_FLICKER = 15;
    public static final int H_PHANTOM_SOUND = 16;
    public static final int H_FAKE_BREAK = 17;
    public static final int H_FALSE_ALARM = 18;   // an entirely real event that never happened twice

    public static CompoundTag tag() {
        return new CompoundTag();
    }

    public static CompoundTag withKind(String kind) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KIND, kind);
        return tag;
    }
}
