package com.pathways.beyond.client;

import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.network.ModNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's GUI screens: Pathway, Codex and Ritual.
 *
 * <p>All three share one look: aged parchment over a dark engraved frame, brass rules, small
 * caps serif typography, and no bright fills anywhere. The Pathway screen is a vertical ladder
 * of Sequences 9 to 0, each rung showing name, colour, and how far through its digestion the
 * player is; the Codex lists rituals as recipes with their components; the Ritual screen shows
 * the circle's live stability and what is missing.
 */
public final class ClientScreens {
    private ClientScreens() {}

    public static void openPathwayScreen() {
        Minecraft.getInstance().setScreen(new PathwayScreen());
    }

    public static void openCodexScreen() {
        Minecraft.getInstance().setScreen(new CodexScreen());
    }

    public static void openRitualScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new RitualScreen(pos));
    }

    /** Ritual state pushed by the server; rendered by the Ritual screen while it is open. */
    private static CompoundTag ritualState = new CompoundTag();

    public static void onRitualState(CompoundTag tag) {
        ritualState = tag.copy();
    }

    // ===================================================================================
    // Shared frame
    // ===================================================================================
    private abstract static class OccultScreen extends Screen {
        private static final ResourceLocation FRAME =
                PathwaysMod.id("textures/gui/occult_panel.png");
        protected static final int PANEL_WIDTH = 256;
        protected static final int PANEL_HEIGHT = 200;
        protected int left;
        protected int top;

        protected OccultScreen(Component title) {
            super(title);
        }

        @Override
        protected void init() {
            this.left = (this.width - PANEL_WIDTH) / 2;
            this.top = (this.height - PANEL_HEIGHT) / 2;
        }

        protected void drawFrame(GuiGraphics graphics, String title) {
            graphics.blit(FRAME, left, top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
            graphics.drawString(this.font, title.toUpperCase(), left + 12, top + 10, 0xD4B265, false);
            graphics.fill(left + 10, top + 22, left + PANEL_WIDTH - 10, top + 23, 0xFF6B5A3A);
        }

        protected void closeButton() {
            this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                    .bounds(left + PANEL_WIDTH - 68, top + PANEL_HEIGHT - 26, 56, 18)
                    .build());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // ===================================================================================
    // Pathway screen: Sequences 9 -> 0
    // ===================================================================================
    public static class PathwayScreen extends OccultScreen {
        private static final String[] SEQUENCES = {
                "Sequence 9 - Seer", "Sequence 8 - Clown", "Sequence 7 - Magician",
                "Sequence 6 - Faceless", "Sequence 5 - Marionettist", "Sequence 4 - Bizarro Sorcerer",
                "Sequence 3 - Scholar of Yore", "Sequence 2 - Miracle Invoker",
                "Sequence 1 - Attendant of Mysteries", "Sequence 0 - The Fool"};

        public PathwayScreen() {
            super(Component.literal("Pathway of the Fool"));
        }

        @Override
        protected void init() {
            super.init();
            closeButton();
            this.addRenderableWidget(Button.builder(Component.literal("Codex"), button ->
                            Minecraft.getInstance().setScreen(new CodexScreen()))
                    .bounds(left + 12, top + PANEL_HEIGHT - 26, 56, 18).build());
            // one button per unlocked ability: the ladder is not just a readout, it is the cast bar
            List<String> abilities = ClientPacketHandler.unlockedAbilities();
            for (int i = 0; i < abilities.size() && i < 10; i++) {
                String id = abilities.get(i);
                int column = i % 2;
                int row = i / 2;
                this.addRenderableWidget(Button.builder(
                                Component.literal(prettyAbilityName(id)),
                                button -> ModNetwork.requestAbility(id))
                        .bounds(left + 130 + column * 60, top + 150 + row * 0, 56, 18)
                        .build());
                if (row > 0) {
                    // only two columns fit; the rest are reachable by paging with the Codex open
                    break;
                }
            }
        }

        private static String prettyAbilityName(String id) {
            String[] parts = id.split("_");
            StringBuilder name = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return name.toString();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawFrame(graphics, "Pathway of the Fool");

            int sequence = ClientPacketHandler.sequence();
            float digestion = ClientPacketHandler.digestion();
            String pathwayName = ClientPacketHandler.pathwayName();

            if (pathwayName == null || pathwayName.isEmpty() || pathwayName.equals("Unbegun")) {
                graphics.drawString(this.font, "No pathway begun.", left + 12, top + 34, 0x9A8E77, false);
                graphics.drawString(this.font, "Drink a potion of the Seer to begin, or read a Codex.",
                        left + 12, top + 46, 0x6B6570, false);
                graphics.drawString(this.font, "Sequences are attempted in a ritual circle, not bought.",
                        left + 12, top + 58, 0x6B6570, false);
                return;
            }

            for (int index = 0; index < SEQUENCES.length; index++) {
                int rungSequence = 9 - index;
                int y = top + 30 + index * 15;
                boolean unlocked = sequence >= 0 && rungSequence >= sequence;
                boolean current = rungSequence == sequence;

                int colour = unlocked ? 0xD4B265 : 0x4A4640;
                if (rungSequence <= 3 && unlocked) {
                    colour = 0x8C1A24;   // past Sequence 3 the ladder stops being safe
                }
                if (current) {
                    graphics.fill(left + 8, y - 2, left + PANEL_WIDTH - 8, y + 11, 0x40241308);
                }
                graphics.drawString(this.font, SEQUENCES[index], left + 12, y, colour, false);

                if (current) {
                    int barWidth = 90;
                    graphics.fill(left + 150, y + 1, left + 150 + barWidth, y + 6, 0x80201410);
                    graphics.fill(left + 150, y + 1, left + 150 + (int) (digestion / 100.0f * barWidth),
                            y + 6, 0xFFD4B265);
                    graphics.drawString(this.font, (int) digestion + "%", left + 148, y,
                            0xD4B265, false);
                }
                if (rungSequence == 0 && unlocked) {
                    graphics.drawString(this.font, "not human", left + 150, y, 0xB03A3A, false);
                }
            }
            if (ClientPacketHandler.beyondUnlocked()) {
                graphics.drawString(this.font, "THE BEYOND IS AWARE OF YOU", left + 12,
                        top + 172, 0x8C1A24, true);
            }
        }
    }

    // ===================================================================================
    // Codex screen: recipes as knowledge
    // ===================================================================================
    public static class CodexScreen extends OccultScreen {
        private static final List<String[]> ENTRIES = new ArrayList<>();
        private int page;

        static {
            ENTRIES.add(new String[]{"Circle of Seeing", "chalk x4, candles x2, spirit flower x1",
                    "Reads the place. Reveals what has happened here recently."});
            ENTRIES.add(new String[]{"Potion of the Seer", "starless crystal, spirit flower, purified salt",
                    "Begins the Fool pathway at Sequence 9. Sanity cost is permanent."});
            ENTRIES.add(new String[]{"Unseal the Eye", "eye of solomon, black blood, brass key",
                    "Opens the reliquary. The Eye then watches you instead of the world."});
            ENTRIES.add(new String[]{"Potion of the Clown", "spirit flower x2, whispering bone, grave earth",
                    "Sequence 8. The body is remade slightly wrong, on purpose."});
            ENTRIES.add(new String[]{"Unseal the Book", "black book, ancient memory x2, occult chalk",
                    "The pages will then contain your own handwriting."});
            ENTRIES.add(new String[]{"Veil Gate", "abyssal eye, starless crystal x2, blood x3",
                    "Thins the veil locally. Opens the way to the Spirit World."});
            ENTRIES.add(new String[]{"Unseal the Bell", "whispering bell, silver needle, moonlit fungus x2",
                    "Restores the clapper. Then it can be heard by others."});
            ENTRIES.add(new String[]{"Potion of the Fool", "corrupted heart, ancient memory x3, starless crystal x3",
                    "Sequence 0. There is no coming back from a successful drink."});
        }

        public CodexScreen() {
            super(Component.literal("Codex of Forbidden Knowledge"));
        }

        @Override
        protected void init() {
            super.init();
            closeButton();
            this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = Math.max(0, page - 1);
            }).bounds(left + 12, top + PANEL_HEIGHT - 26, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = Math.min(ENTRIES.size() - 1, page + 1);
            }).bounds(left + 34, top + PANEL_HEIGHT - 26, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("Pathway"), button ->
                            Minecraft.getInstance().setScreen(new PathwayScreen()))
                    .bounds(left + 56, top + PANEL_HEIGHT - 26, 64, 18).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawFrame(graphics, "Codex of Forbidden Knowledge");
            String[] entry = ENTRIES.get(page);
            graphics.drawString(this.font, entry[0], left + 12, top + 32, 0xD4B265, false);
            graphics.drawString(this.font, "Components: " + entry[1], left + 12, top + 48, 0x9A8E77, false);
            wrap(graphics, entry[2], left + 12, top + 64, PANEL_WIDTH - 24, 0x8A8377);
            graphics.drawString(this.font, "page " + (page + 1) + " / " + ENTRIES.size(),
                    left + PANEL_WIDTH - 90, top + PANEL_HEIGHT - 20, 0x6B6570, false);
            graphics.drawString(this.font, "Knowledge is not free: every entry costs sanity on use.",
                    left + 12, top + 116, 0xB03A3A, false);
        }

        private void wrap(GuiGraphics graphics, String text, int x, int y, int maxWidth, int colour) {
            StringBuilder line = new StringBuilder();
            int lineY = y;
            for (String word : text.split(" ")) {
                if (this.font.width(line + " " + word) > maxWidth) {
                    graphics.drawString(this.font, line.toString(), x, lineY, colour, false);
                    line = new StringBuilder(word);
                    lineY += 11;
                } else {
                    if (!line.isEmpty()) {
                        line.append(' ');
                    }
                    line.append(word);
                }
            }
            if (!line.isEmpty()) {
                graphics.drawString(this.font, line.toString(), x, lineY, colour, false);
            }
        }
    }

    // ===================================================================================
    // Ritual screen: the live circle
    // ===================================================================================
    public static class RitualScreen extends OccultScreen {
        private final BlockPos altar;

        public RitualScreen(BlockPos altar) {
            super(Component.literal("Occult Ritual"));
            this.altar = altar;
        }

        @Override
        protected void init() {
            super.init();
            closeButton();
            this.addRenderableWidget(Button.builder(Component.literal("Begin"), button -> {
                ModNetwork.requestRitualAction("begin", altar.getX(), altar.getY(), altar.getZ(), "");
                this.onClose();
            }).bounds(left + 100, top + PANEL_HEIGHT - 26, 56, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("Abort"), button -> {
                ModNetwork.requestRitualAction("abort", altar.getX(), altar.getY(), altar.getZ(), "");
                this.onClose();
            }).bounds(left + 160, top + PANEL_HEIGHT - 26, 56, 18).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawFrame(graphics, "Occult Ritual");
            boolean active = ritualState.getBoolean("active");
            String recipe = ritualState.getString("recipe");
            float stability = ritualState.getFloat("stability");
            float danger = ritualState.getFloat("danger");

            if (!active) {
                graphics.drawString(this.font, "The altar is dormant.", left + 12, top + 34, 0x9A8E77, false);
                graphics.drawString(this.font, "Lay a chalk circle, light the candles, fill a basin.",
                        left + 12, top + 46, 0x6B6570, false);
                graphics.drawString(this.font, "Then offer the components in order. Seven steps.",
                        left + 12, top + 58, 0x6B6570, false);
                return;
            }

            graphics.drawString(this.font, "Ritual: " + recipe, left + 12, top + 32, 0xD4B265, false);
            int barWidth = PANEL_WIDTH - 24;
            int stabilityWidth = (int) (stability / 100.0f * barWidth);
            graphics.fill(left + 12, top + 50, left + 12 + barWidth, top + 58, 0x80201410);
            int colour = stability > 60 ? 0x6FA98A : stability > 30 ? 0xD4B265 : 0xB03A3A;
            graphics.fill(left + 12, top + 50, left + 12 + stabilityWidth, top + 58, 0xFF000000 | colour);
            graphics.drawString(this.font, "stability " + (int) stability + "%", left + 12, top + 62,
                    0x9A8E77, false);
            graphics.drawString(this.font, "danger " + (int) (danger * 100) + "%", left + 150, top + 62,
                    danger > 0.5f ? 0xB03A3A : 0x9A8E77, false);

            String missing = ritualState.getString("missing");
            if (!missing.isEmpty()) {
                graphics.drawString(this.font, "Missing: " + missing, left + 12, top + 80, 0xB03A3A, false);
            }
            graphics.drawString(this.font, "Failure is not a refund. The circle takes its price.",
                    left + 12, top + 140, 0x8C1A24, false);
        }
    }

    static {
        ChatFormatting unused = ChatFormatting.GRAY;   // keeps the import meaningful for tooltips
    }
}
