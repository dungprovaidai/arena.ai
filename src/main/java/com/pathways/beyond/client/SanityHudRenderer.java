package com.pathways.beyond.client;

import com.pathways.beyond.PathwaysMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Sanity and Corruption HUD.
 *
 * <p>Presented as a Victorian instrument rather than a health bar: a tall glass gauge with an
 * engraved scale for Sanity, a shorter one for Corruption divided into its six stages, and a
 * small brass plate for the player's pathway and Sequence. The gauges only appear once the
 * player has begun a pathway, so early-game screens stay clean.
 *
 * <p>At low sanity the HUD itself degrades: the needle jitters, the glass cracks with fine
 * lines, and the stage name becomes unreliable (occasionally displaying the wrong tier), which
 * is information the player has to be careful about trusting.
 */
public final class SanityHudRenderer implements LayeredDraw.Layer {
    public static final SanityHudRenderer INSTANCE = new SanityHudRenderer();

    private static final ResourceLocation GAUGE =
            PathwaysMod.id("textures/gui/sanity_gauge.png");
    private static final ResourceLocation CORRUPTION =
            PathwaysMod.id("textures/gui/corruption_gauge.png");

    private SanityHudRenderer() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        if (ClientPacketHandler.pathwayId().isEmpty()) {
            return;   // no pathway yet: the instrument is not installed
        }
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        boolean leftHanded = minecraft.options.mainHand().get()
                == net.minecraft.world.entity.HumanoidArm.LEFT;
        int baseX = leftHanded ? 8 : screenWidth - 44;
        int baseY = screenHeight / 2 - 60;

        float sanity = ClientPacketHandler.sanity();
        float corruption = ClientPacketHandler.corruption();
        int jitter = (int) (Math.max(0.0f, 40.0f - sanity) / 8.0f
                * Math.sin(player.tickCount * 0.7));

        // ---- sanity gauge -------------------------------------------------------------
        graphics.blit(GAUGE, baseX, baseY, 0, 0, 20, 96, 64, 128);
        int fill = (int) Math.max(0, Math.min(96, sanity / 100.0f * 96.0f));
        graphics.blit(GAUGE, baseX, baseY + (96 - fill), 20, 96 - fill, 20, fill, 64, 128);
        int needleY = baseY + 96 - fill + jitter;
        graphics.blit(GAUGE, baseX + 10, needleY, 40, 0, 14, 4, 64, 128);

        // ---- corruption gauge ---------------------------------------------------------
        int corruptionY = baseY + 104;
        graphics.blit(CORRUPTION, baseX, corruptionY, 0, 0, 20, 56, 64, 64);
        int corruptionFill = (int) Math.max(0, Math.min(56, corruption / 100.0f * 56.0f));
        graphics.blit(CORRUPTION, baseX, corruptionY + (56 - corruptionFill), 20, 56 - corruptionFill,
                20, corruptionFill, 64, 64);

        // ---- brass plate: pathway, sequence, digestion --------------------------------
        String sequence = ClientPacketHandler.sequence() < 0
                ? "Unbegun" : "Sequence " + ClientPacketHandler.sequence();
        String plate = ClientPacketHandler.pathwayName().toUpperCase() + "  " + sequence;
        int plateWidth = minecraft.font.width(plate) + 8;
        int plateX = baseX + 22;
        graphics.fill(plateX, baseY + 2, plateX + plateWidth, baseY + 16, 0xC0100C0A);
        graphics.drawString(minecraft.font, plate, plateX + 4, baseY + 5, 0xD4B265, false);

        String digestion = "digestion " + (int) ClientPacketHandler.digestion() + "%";
        graphics.drawString(minecraft.font, digestion, plateX + 4, baseY + 18, 0x9A8E77, false);

        // ---- tether readout while projecting ------------------------------------------
        if (ClientPacketHandler.spiritForm()) {
            int strain = ClientPacketHandler.tetherStrain();
            String tether = "tether " + strain + "%";
            int colour = strain > 75 ? 0xB03A3A : strain > 40 ? 0xD4B265 : 0xA8DCE2;
            graphics.drawString(minecraft.font, tether, plateX + 4, baseY + 30, colour, false);
            if (strain > 90 && player.tickCount % 20 < 10) {
                graphics.drawString(minecraft.font, "TETHER NEARLY BROKEN", plateX + 4, baseY + 42,
                        0xB03A3A, true);
            }
        }

        // ---- low sanity distortion ----------------------------------------------------
        if (sanity < 25.0f) {
            int alpha = (int) ((25.0f - sanity) / 25.0f * 90.0f);
            graphics.fill(0, 0, screenWidth, screenHeight, (alpha << 24));
        }
        if (ClientPacketHandler.textGlitch() > 1.0f && player.tickCount % 8 < 3) {
            Component wrong = Component.literal(wrongTierName(sanity, corruption));
            graphics.drawString(minecraft.font, wrong, plateX + 4, baseY + 52, 0x8C1A24, true);
        }
    }

    /** The tier names the HUD will admit to. At low sanity it starts lying. */
    private static String wrongTierName(float sanity, float corruption) {
        if (corruption > 65.0f) {
            return "you are fine";
        }
        if (sanity < 20.0f) {
            return "three of us agree";
        }
        return "the needle is wrong";
    }
}
