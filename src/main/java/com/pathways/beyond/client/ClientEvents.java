package com.pathways.beyond.client;

import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.spirit.SpiritWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-side wiring: HUD, atmosphere, and the small render touches that make the mod's
 * lighting feel different from vanilla without any shader pack installed.
 *
 * <p>Three things are done here and nowhere else:
 * <ul>
 *   <li>fog colour and density, driven by the ambient state the server sends (Spirit World,
 *       sanity, world events, hallucinations);</li>
 *   <li>camera shake at low sanity and on ritual failure, applied as a subtle positional
 *       wobble rather than a hard shake, so it stays readable;</li>
 *   <li>the underwater-style darkening at high Corruption: the player's own vision narrows.</li>
 * </ul>
 */
public final class ClientEvents {
    private ClientEvents() {}

    private static float shakePhase;

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientEvents::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderFog);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(PathwaysMod.id("sanity_hud"), SanityHudRenderer.INSTANCE);
    }

    private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientPacketHandler.clientTick();
        ClientThreadRender.tick();
    }

    /** Fog colour: cold grey in the Spirit World, stained crimson as corruption rises. */
    private static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        boolean spiritWorld = minecraft.level.dimension().location().toString()
                .startsWith("pathwaysofthebeyond:spirit_world");
        if (spiritWorld) {
            int colour = SpiritWorld.fogColour();
            event.setRed(((colour >> 16) & 0xFF) / 255.0f);
            event.setGreen(((colour >> 8) & 0xFF) / 255.0f);
            event.setBlue((colour & 0xFF) / 255.0f);
            return;
        }
        float corruption = ClientPacketHandler.corruption();
        if (corruption > 25.0f) {
            // corruption tints the air: crimson at the edges, never a full screen wash
            float amount = Mth.clamp((corruption - 25.0f) / 75.0f, 0.0f, 0.35f);
            event.setRed(event.getRed() * (1.0f - amount) + 0.34f * amount);
            event.setGreen(event.getGreen() * (1.0f - amount) + 0.06f * amount);
            event.setBlue(event.getBlue() * (1.0f - amount) + 0.10f * amount);
        }
        // at the very bottom of the sanity scale the world loses saturation entirely
        float sanity = ClientPacketHandler.sanity();
        if (sanity < 30.0f) {
            float amount = (30.0f - sanity) / 30.0f * 0.5f;
            float grey = (event.getRed() + event.getGreen() + event.getBlue()) / 3.0f;
            event.setRed(Mth.lerp(amount, event.getRed(), grey));
            event.setGreen(Mth.lerp(amount, event.getGreen(), grey));
            event.setBlue(Mth.lerp(amount, event.getBlue(), grey));
        }
    }

    /** Fog density: volumetric-looking in the Spirit World, thicker at Corruption and Dread. */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float packetFog = ClientPacketHandler.fogDensity();
        boolean spiritWorld = minecraft.level.dimension().location().toString()
                .startsWith("pathwaysofthebeyond:spirit_world");
        if (!spiritWorld && packetFog < 0.02f) {
            return;
        }
        float density = spiritWorld ? SpiritWorld.fogDensity() : 0.02f + packetFog * 0.15f;
        float far = Mth.clamp(event.getFarPlaneDistance() * (1.0f - density), 16.0f, 512.0f);
        float near = Math.min(event.getNearPlaneDistance(), far * 0.35f);
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
        // and the air itself carries motes, which is what sells "this is not a place"
        LocalPlayer player = minecraft.player;
        if (player != null && player.tickCount % 2 == 0) {
            RandomSource random = player.getRandom();
            for (int i = 0; i < 2; i++) {
                minecraft.level.addParticle(ModParticles.SpiritMote.get(),
                        player.getX() + (random.nextDouble() - 0.5) * 10.0,
                        player.getY() + random.nextDouble() * 3.0,
                        player.getZ() + (random.nextDouble() - 0.5) * 10.0, 0.0, 0.004, 0.0);
            }
        }
    }

    /** Applied by the camera setup mixin-free path: called from the fog handler for shake phase. */
    public static float shakeOffset() {
        shakePhase += 0.7f;
        float shake = ClientPacketHandler.cameraShake();
        if (shake < 0.01f) {
            return 0.0f;
        }
        return Mth.sin(shakePhase) * shake * 0.0025f;
    }
}
