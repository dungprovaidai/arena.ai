package com.pathways.beyond.network;

import com.pathways.beyond.pathway.Abilities;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.ritual.RitualManager;
import com.pathways.beyond.sanity.HallucinationDirector;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;
import com.pathways.beyond.soul.SoulThreadManager;
import com.pathways.beyond.spirit.SoulProjection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

import static com.pathways.beyond.PathwaysMod.id;

/**
 * Networking: two envelopes, one discriminator, all handlers in one place.
 *
 * <p>The client is never trusted: every serverbound message re-validates the request
 * against the player's actual sequence, Sanity and position before doing anything.
 */
public final class ModNetwork {
    private ModNetwork() {}

    // ===================================================================================
    // Envelopes
    // ===================================================================================
    public record ToClient(CompoundTag payload) implements CustomPacketPayload {
        public static final Type<ToClient> TYPE = new Type<>(id("occult_to_client"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToClient> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, ToClient::payload, ToClient::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ToServer(CompoundTag payload) implements CustomPacketPayload {
        public static final Type<ToServer> TYPE = new Type<>(id("occult_to_server"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToServer> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, ToServer::payload, ToServer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===================================================================================
    // Registration
    // ===================================================================================
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(ToClient.TYPE, ToClient.CODEC, ModNetwork::onClientbound);
        registrar.playToServer(ToServer.TYPE, ToServer.CODEC, ModNetwork::onServerbound);
    }

    private static void onClientbound(ToClient msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.pathways.beyond.client.ClientPacketHandler.handle(msg.payload()));
    }

    private static void onServerbound(ToServer msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                handleServerbound(player, msg.payload());
            }
        });
    }

    private static void handleServerbound(ServerPlayer player, CompoundTag tag) {
        String kind = tag.getString(OccultMessages.KIND);
        switch (kind) {
            case OccultMessages.S_USE_ABILITY ->
                    Abilities.request(player, tag.getString(OccultMessages.F_ABILITY));
            case OccultMessages.S_RITUAL_ACTION -> RitualManager.handle(player,
                    tag.getString(OccultMessages.F_ACTION),
                    tag.getInt(OccultMessages.F_X),
                    tag.getInt(OccultMessages.F_Y),
                    tag.getInt(OccultMessages.F_Z),
                    tag.getString(OccultMessages.F_RECIPE));
            case OccultMessages.S_SOUL_PROJECT ->
                    SoulProjection.request(player, tag.getBoolean("project"));
            case OccultMessages.S_THREAD_ACTION -> SoulThreadManager.handleAction(player,
                    tag.getString(OccultMessages.F_ACTION),
                    tag.getInt(OccultMessages.F_ENTITY));
            case OccultMessages.S_ARTIFACT_ACTION ->
                    com.pathways.beyond.artifact.ArtifactEvents.handleAction(player,
                            tag.getString(OccultMessages.F_ACTION),
                            tag.getInt(OccultMessages.F_TYPE));
            case OccultMessages.S_OPEN_SCREEN -> {
                // screen opening is purely a client concern; we only log it for the codex ledger
                ModNetwork.sendAbilityFeedback(player, "codex_opened", 0);
            }
            default -> {
                // unknown kind: ignore rather than crash a server on a malformed packet
            }
        }
    }

    // ===================================================================================
    // Send helpers - the only place packets are constructed
    // ===================================================================================
    public static void sendPathwaySync(ServerPlayer player) {
        PlayerPathway data = ModAttachments.pathway(player);
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_PATHWAY_SYNC);
        tag.putString(OccultMessages.F_PATHWAY, data.pathwayId());
        tag.putInt(OccultMessages.F_SEQUENCE, data.sequence());
        tag.putFloat(OccultMessages.F_DIGESTION, data.digestion());
        tag.put(OccultMessages.F_UNLOCKED, stringList(data.unlockedAbilities()));
        tag.put(OccultMessages.F_RITUALS, stringList(data.knownRituals()));
        tag.putBoolean(OccultMessages.F_BEYOND, data.beyondUnlocked());
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    public static void sendOccultSync(ServerPlayer player) {
        OccultState state = ModAttachments.occult(player);
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_OCCULT_SYNC);
        tag.putFloat(OccultMessages.F_SANITY, state.sanity());
        tag.putFloat(OccultMessages.F_CORRUPTION, state.corruption());
        tag.putBoolean(OccultMessages.F_SPIRIT, state.spiritForm());
        tag.putFloat(OccultMessages.F_TETHER, state.tetherStrain());
        tag.putInt(OccultMessages.F_THREADS, state.boundThreadCount());
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    /** Fire a client-side visual event (VFX + animation + local sound) at a position. */
    public static void sendVisual(ServerPlayer player, int visual, double x, double y, double z,
                                  float intensity, int colour, int duration) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_VISUAL);
        tag.putInt(OccultMessages.F_TYPE, visual);
        tag.putDouble(OccultMessages.F_X, x);
        tag.putDouble(OccultMessages.F_Y, y);
        tag.putDouble(OccultMessages.F_Z, z);
        tag.putFloat(OccultMessages.F_INTENSITY, intensity);
        tag.putInt(OccultMessages.F_COLOUR, colour);
        tag.putInt(OccultMessages.F_DURATION, duration);
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    public static void sendVisualToAll(net.minecraft.server.level.ServerLevel level, int visual,
                                       double x, double y, double z, float intensity, int colour,
                                       int duration) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_VISUAL);
        tag.putInt(OccultMessages.F_TYPE, visual);
        tag.putDouble(OccultMessages.F_X, x);
        tag.putDouble(OccultMessages.F_Y, y);
        tag.putDouble(OccultMessages.F_Z, z);
        tag.putFloat(OccultMessages.F_INTENSITY, intensity);
        tag.putInt(OccultMessages.F_COLOUR, colour);
        tag.putInt(OccultMessages.F_DURATION, duration);
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, 96.0, new ToClient(tag));
    }

    /** Hallucination event: the client renders/fakes it, the server decides everything. */
    public static void sendHallucination(ServerPlayer player, int kind, double x, double y, double z,
                                         float intensity, int duration, String data) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_HALLUCINATION);
        tag.putInt(OccultMessages.F_TYPE, kind);
        tag.putDouble(OccultMessages.F_X, x);
        tag.putDouble(OccultMessages.F_Y, y);
        tag.putDouble(OccultMessages.F_Z, z);
        tag.putFloat(OccultMessages.F_INTENSITY, intensity);
        tag.putInt(OccultMessages.F_DURATION, duration);
        tag.putString("data", data == null ? "" : data);
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    public static void sendRitualState(ServerPlayer player, RitualManager.Snapshot snapshot) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_RITUAL_STATE);
        tag.putBoolean(OccultMessages.F_ACTIVE, snapshot.active());
        tag.putString(OccultMessages.F_RECIPE, snapshot.recipeId());
        tag.putFloat(OccultMessages.F_STABILITY, snapshot.stability());
        tag.putFloat(OccultMessages.F_DANGER, snapshot.danger());
        tag.put(OccultMessages.F_MISSING, stringList(snapshot.missing()));
        tag.putInt(OccultMessages.F_X, snapshot.x());
        tag.putInt(OccultMessages.F_Y, snapshot.y());
        tag.putInt(OccultMessages.F_Z, snapshot.z());
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    public static void sendThreadSync(ServerPlayer player, List<int[]> threads) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_THREAD_SYNC);
        int[] ids = new int[threads.size()];
        int[] kinds = new int[threads.size()];
        for (int i = 0; i < threads.size(); i++) {
            ids[i] = threads.get(i)[0];
            kinds[i] = threads.get(i)[1];
        }
        tag.putIntArray("ids", ids);
        tag.putIntArray("kinds", kinds);
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    public static void sendAbilityFeedback(ServerPlayer player, String messageKey, int cooldownTicks) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_ABILITY_FEEDBACK);
        tag.putString(OccultMessages.F_MESSAGE, messageKey);
        tag.putInt("cooldown", cooldownTicks);
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    public static void sendSpiritState(ServerPlayer player, boolean inSpiritForm, int tetherPercent) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.C_SPIRIT_STATE);
        tag.putBoolean(OccultMessages.F_SPIRIT, inSpiritForm);
        tag.putInt(OccultMessages.F_TETHER, tetherPercent);
        PacketDistributor.sendToPlayer(player, new ToClient(tag));
    }

    // ===================================================================================
    // Client -> server request builders (used by screens, keybinds and the HUD)
    // ===================================================================================
    public static void requestAbility(String abilityId) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.S_USE_ABILITY);
        tag.putString(OccultMessages.F_ABILITY, abilityId);
        PacketDistributor.sendToServer(new ToServer(tag));
    }

    public static void requestRitualAction(String action, int x, int y, int z, String recipe) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.S_RITUAL_ACTION);
        tag.putString(OccultMessages.F_ACTION, action);
        tag.putInt(OccultMessages.F_X, x);
        tag.putInt(OccultMessages.F_Y, y);
        tag.putInt(OccultMessages.F_Z, z);
        tag.putString(OccultMessages.F_RECIPE, recipe == null ? "" : recipe);
        PacketDistributor.sendToServer(new ToServer(tag));
    }

    public static void requestSoulProject(boolean project) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.S_SOUL_PROJECT);
        tag.putBoolean("project", project);
        PacketDistributor.sendToServer(new ToServer(tag));
    }

    public static void requestThreadAction(String action, int entityId) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.S_THREAD_ACTION);
        tag.putString(OccultMessages.F_ACTION, action);
        tag.putInt(OccultMessages.F_ENTITY, entityId);
        PacketDistributor.sendToServer(new ToServer(tag));
    }

    public static void requestArtifactAction(String action, int type) {
        CompoundTag tag = OccultMessages.withKind(OccultMessages.S_ARTIFACT_ACTION);
        tag.putString(OccultMessages.F_ACTION, action);
        tag.putInt(OccultMessages.F_TYPE, type);
        PacketDistributor.sendToServer(new ToServer(tag));
    }

    private static net.minecraft.nbt.ListTag stringList(List<String> values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String value : values) {
            list.add(net.minecraft.nbt.StringTag.valueOf(value));
        }
        return list;
    }

    /** Sanity helper used by the client handler for local prediction. */
    public static float clampSanity(float value) {
        return SanitySystem.clamp(value);
    }

    /** Server tick used by hallucination timing (exposed for the director). */
    public static void pingHallucination(ServerPlayer player, int kind) {
        HallucinationDirector.force(player, kind);
    }
}
