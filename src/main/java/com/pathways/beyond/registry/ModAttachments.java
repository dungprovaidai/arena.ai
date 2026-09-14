package com.pathways.beyond.registry;

import com.mojang.serialization.Codec;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.sanity.OccultState;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import static com.pathways.beyond.PathwaysMod.MOD_ID;

/**
 * Data attachments: the player's occult state.
 *
 * <p>Two separate attachments on purpose:
 * <ul>
 *   <li>{@link PlayerPathway} - permanent progression (pathway, sequence, digestion, unlocks)</li>
 *   <li>{@link OccultState} - volatile state (sanity, corruption, soul projection, tethers)</li>
 * </ul>
 * Keeping them apart means a sanity collapse can never corrupt progression data.
 */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerPathway>> PATHWAY =
            ATTACHMENTS.register("pathway", () -> AttachmentType.builder(PlayerPathway::new)
                    .serialize(PlayerPathway.CODEC)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<OccultState>> OCCULT =
            ATTACHMENTS.register("occult_state", () -> AttachmentType.builder(OccultState::new)
                    .serialize(OccultState.CODEC)
                    .build());

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    // ---- accessors ------------------------------------------------------------------
    public static PlayerPathway pathway(ServerPlayer player) {
        return player.getData(PATHWAY.get());
    }

    public static OccultState occult(ServerPlayer player) {
        return player.getData(OCCULT.get());
    }

    /** Full re-sync: called on login, respawn and whenever a screen opens. */
    public static void syncAll(ServerPlayer player) {
        ModNetwork.sendPathwaySync(player);
        ModNetwork.sendOccultSync(player);
    }

    public static void copyOnRespawn(ServerPlayer original, net.minecraft.world.entity.player.Player replacement) {
        if (!(replacement instanceof ServerPlayer target)) {
            return;
        }
        PlayerPathway old = original.getData(PATHWAY.get());
        target.setData(PATHWAY.get(), old.copy());
        OccultState oldState = original.getData(OCCULT.get());
        target.setData(OCCULT.get(), oldState.onRespawn());
    }

    /** Codec used by the command layer for export/import debugging. */
    public static final Codec<PlayerPathway> DEBUG_PATHWAY_CODEC = PlayerPathway.CODEC;
}
