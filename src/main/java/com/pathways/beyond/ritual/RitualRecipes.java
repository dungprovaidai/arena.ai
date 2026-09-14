package com.pathways.beyond.ritual;

import com.pathways.beyond.item.OccultItems;
import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ritual definitions.
 *
 * <p>Rituals are code-defined rather than datapack-defined so that each one can carry a
 * unique failure consequence and a unique opening sequence. A ritual is not a crafting
 * recipe with a nicer GUI: it has components that are consumed, a duration during which
 * the player must stay inside the circle, a stability value that decays when something is
 * wrong, and a failure table that hurts.
 */
public final class RitualRecipes {
    private RitualRecipes() {}

    /** One required component: an item and how many the circle consumes. */
    public record Component(Item item, int count, String note) {}

    /** What the ritual produces when it closes cleanly. */
    public enum Outcome {
        /** Brews a pathway potion for the given sequence. */
        BREW_POTION,
        /** Advances the player one sequence (the only legitimate way up the ladder). */
        ADVANCE_SEQUENCE,
        /** Unseals an artifact. */
        UNSEAL_ARTIFACT,
        /** Teaches a ritual / opens the codex. */
        REVEAL_KNOWLEDGE,
        /** Opens a door into the Spirit World. */
        OPEN_SPIRIT_GATE
    }

    /** A complete ritual definition. */
    public record RitualRecipe(String id, String display, Outcome outcome, int targetSequence,
                               int minSequence, int durationTicks, int requiredCandles,
                               int requiredCircleBlocks, float baseStability, float danger,
                               List<Component> components, String lore) {
        public boolean isAvailableTo(PlayerPathway pathway) {
            return pathway.hasPathway() && pathway.sequence() <= minSequence;
        }
    }

    private static final List<RitualRecipe> RECIPES = new ArrayList<>();

    private static void add(RitualRecipe recipe) {
        RECIPES.add(recipe);
    }

    /** Set once the table has been built. */
    private static boolean built;

    /**
     * Builds the book on first use, never while classes are still initialising.
     *
     * <p>Every component in a recipe holds a real registered {@code Item}. Building this table in a
     * static initialiser meant resolving those items before the item registry existed, which fails
     * the class load outright - so the table is deferred to the first caller instead.
     */
    private static void ensureBuilt() {
        if (built) {
            return;
        }
        built = true;
        try {
            build();
        } catch (RuntimeException | LinkageError failure) {
            PathwaysMod.LOGGER.error("[Pathways] ritual table could not be built", failure);
        }
    }

    // ===================================================================================
    // The recipe book. Ordered by how badly they can go wrong.
    // ===================================================================================
    private static void build() {
        add(new RitualRecipe(
                "circle_of_seeing", "Circle of Seeing", Outcome.REVEAL_KNOWLEDGE, 9, 9, 240, 2, 4,
                90.0f, 0.15f,
                List.of(new Component(ModItems.OccultChalk.get(), 2, "to draw the circle"),
                        new Component(ModItems.PurifiedSalt.get(), 2, "to keep the drawn line true"),
                        new Component(ModItems.VeilDust.get(), 1, "so the veil knows you are knocking")),
                "The first ritual anyone is taught, because it is the hardest to die from. "
                        + "It shows the celebrant what is already standing in the room."));

        add(new RitualRecipe(
                "potion_of_the_seer", "Brewing: Seer", Outcome.BREW_POTION, 9, 9, 360, 3, 6,
                80.0f, 0.30f,
                List.of(new Component(ModItems.SpiritFlower.get(), 3, "the seeing flower"),
                        new Component(ModItems.PurifiedSalt.get(), 2, "the ground must be clean"),
                        new Component(ModItems.VeilDust.get(), 2, "so the mixture remembers the Veil"),
                        new Component(ModItems.SoulFragment.get(), 1, "a piece of continuity")),
                "Every pathway begins with a bottle. This one is drunk, not administered, and "
                        + "the difference matters to what comes after."));

        add(new RitualRecipe(
                "unseal_the_eye", "Unsealing: The Eye of Solomon", Outcome.UNSEAL_ARTIFACT, 9, 8,
                420, 4, 6, 70.0f, 0.45f,
                List.of(new Component(ModItems.AbyssalEye.get(), 1, "an eye with no lids"),
                        new Component(ModItems.BlackBloodVial.get(), 1, "what the seal drinks"),
                        new Component(ModItems.OccultChalk.get(), 4, "a circle with corners"),
                        new Component(ModItems.NightshadeAshes.get(), 2, "burnt offering")),
                "The reliquary was sealed for a reason that the seal itself declines to explain."));

        add(new RitualRecipe(
                "potion_of_the_clown", "Brewing: Clown", Outcome.BREW_POTION, 8, 8, 420, 3, 6,
                75.0f, 0.40f,
                List.of(new Component(ModItems.GraveEarth.get(), 2, "ground that has been danced on"),
                        new Component(ModItems.OccultChalk.get(), 2, "the line you will cross"),
                        new Component(ModItems.BlackBloodVial.get(), 1, "the joke told to physics"),
                        new Component(ModItems.MoonlitFungus.get(), 2, "for the unnatural movement")),
                "The Clown sequence is not comedy. It is the body learning to be wrong on purpose."));

        add(new RitualRecipe(
                "unseal_the_book", "Unsealing: The Black Book", Outcome.UNSEAL_ARTIFACT, 8, 7,
                480, 4, 8, 65.0f, 0.55f,
                List.of(new Component(ModItems.AncientMemory.get(), 1, "a year, folded small"),
                        new Component(ModItems.WhisperingBone.get(), 2, "so it has something to say"),
                        new Component(ModItems.NightshadeAshes.get(), 3, "ink that was never ink"),
                        new Component(ModItems.FacelessSkin.get(), 1, "for the cover")),
                "Reading it is not the dangerous part. Finishing a page is."));

        add(new RitualRecipe(
                "potion_of_the_magician", "Brewing: Magician", Outcome.BREW_POTION, 7, 7, 480, 4, 8,
                70.0f, 0.50f,
                List.of(new Component(ModItems.StarlessCrystal.get(), 1, "a crystal with nothing inside"),
                        new Component(ModItems.VeilDust.get(), 3, "misdirection, ground fine"),
                        new Component(ModItems.OccultChalk.get(), 3, "the shape of a lie"),
                        new Component(ModItems.SoulFragment.get(), 2, "a doubled thread")),
                "Misdirection is a craft. The potion simply removes the audience's veto."));

        add(new RitualRecipe(
                "veil_gate", "Opening the Veil Gate", Outcome.OPEN_SPIRIT_GATE, 7, 5, 600, 6, 8,
                55.0f, 0.75f,
                List.of(new Component(ModItems.StarlessCrystal.get(), 2, "so the gate has an edge"),
                        new Component(ModItems.BlackBloodVial.get(), 2, "the door drinks first"),
                        new Component(ModItems.SoulFragment.get(), 3, "three threads, tied together"),
                        new Component(ModItems.NightshadeAshes.get(), 2, "to make the room forget")),
                "A door into the Spirit World does not open. It is opened, and it stays ajar."));

        add(new RitualRecipe(
                "unseal_the_bell", "Unsealing: The Whispering Bell", Outcome.UNSEAL_ARTIFACT, 5, 5,
                540, 5, 8, 60.0f, 0.65f,
                List.of(new Component(ModItems.WhisperingBone.get(), 3, "a clapper, of a kind"),
                        new Component(ModItems.CorruptedHeart.get(), 1, "so it wants to be rung"),
                        new Component(ModItems.BlackBloodVial.get(), 2, "for the crack"),
                        new Component(ModItems.GraveEarth.get(), 4, "from nine different graves")),
                "The clapper was removed by someone careful. This ritual asks it back."));

        add(new RitualRecipe(
                "potion_of_the_faceless", "Brewing: Faceless", Outcome.BREW_POTION, 6, 6, 540, 4, 8,
                65.0f, 0.60f,
                List.of(new Component(ModItems.FacelessSkin.get(), 2, "blank as a page"),
                        new Component(ModItems.AbyssalEye.get(), 1, "so you can still see yourself"),
                        new Component(ModItems.OccultChalk.get(), 4, "a circle with no name on it"),
                        new Component(ModItems.PurifiedSalt.get(), 3, "keep the wearer inside")),
                "The mask is not worn to hide. It is worn because the face underneath got tired."));

        add(new RitualRecipe(
                "potion_of_the_marionettist", "Brewing: Marionettist", Outcome.BREW_POTION, 5, 5,
                600, 5, 8, 60.0f, 0.70f,
                List.of(new Component(ModItems.SoulFragment.get(), 4, "four threads, still warm"),
                        new Component(ModItems.SilverNeedle.get(), 1, "the needle that ties them"),
                        new Component(ModItems.GraveEarth.get(), 3, "for the strings' anchor"),
                        new Component(ModItems.NightshadeAshes.get(), 3, "so the puppets sleep")),
                "The Marionettist does not pull strings. The Marionettist is the reason the "
                        + "strings were already there."));

        add(new RitualRecipe(
                "potion_of_the_scholar", "Brewing: Scholar of Yore", Outcome.BREW_POTION, 3, 3, 720,
                6, 10, 50.0f, 0.80f,
                List.of(new Component(ModItems.AncientMemory.get(), 3, "three years, arranged"),
                        new Component(ModItems.StarlessCrystal.get(), 2, "for the ink"),
                        new Component(ModItems.WhisperingBone.get(), 3, "so you can hear your own notes"),
                        new Component(ModItems.SoulFragment.get(), 3, "to keep the reader present")),
                "Places remember. This teaches you to interrupt them politely."));

        add(new RitualRecipe(
                "potion_of_the_fool", "Brewing: The Fool", Outcome.BREW_POTION, 0, 1, 1200, 8, 12,
                30.0f, 1.00f,
                List.of(new Component(ModItems.AncientMemory.get(), 4, "everything you were"),
                        new Component(ModItems.SoulFragment.get(), 6, "and everything you will be"),
                        new Component(ModItems.AbyssalEye.get(), 3, "the eye that must look away"),
                        new Component(ModItems.StarlessCrystal.get(), 4, "a space for the space between"),
                        new Component(ModItems.BlackBloodVial.get(), 4, "the price, in advance")),
                "The last potion. Drinking it is the last decision the player makes as a person; "
                        + "everything afterwards is a negotiation with The Beyond."));
    }

    public static List<RitualRecipe> all() {
        ensureBuilt();
        return List.copyOf(RECIPES);
    }

    public static RitualRecipe byId(String id) {
        ensureBuilt();
        for (RitualRecipe recipe : RECIPES) {
            if (recipe.id().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<RitualRecipe> forPlayer(PlayerPathway pathway) {
        ensureBuilt();
        return RECIPES.stream().filter(r -> r.isAvailableTo(pathway)).toList();
    }

    // ===================================================================================
    // Potion lookup: pathway + sequence -> the potion item, resolved from the registry
    // so adding a potion in tools/content.py automatically wires it into rituals.
    // ===================================================================================
    private static Map<String, Item> potionIndex;

    public static Item potionFor(String pathwayId, int sequence) {
        if (potionIndex == null) {
            potionIndex = new LinkedHashMap<>();
            for (var holder : ModItems.ITEMS.getEntries()) {
                Item item = holder.get();
                if (item instanceof OccultItems.PathwayPotionItem potion) {
                    potionIndex.put(potion.pathwayId().toLowerCase() + ":" + potion.sequence(), item);
                }
            }
        }
        return potionIndex.get(pathwayId.toLowerCase() + ":" + sequence);
    }

    public static ItemStack potionStack(String pathwayId, int sequence) {
        Item item = potionFor(pathwayId, sequence);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
