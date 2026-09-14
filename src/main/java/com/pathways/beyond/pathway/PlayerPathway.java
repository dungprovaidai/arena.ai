package com.pathways.beyond.pathway;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Permanent occult progression for one player.
 *
 * <p>Sequence numbering follows the lore: <b>9 is the weakest and 0 the strongest</b>.
 * A player who has never taken a pathway potion has {@code sequence == -1} and
 * {@link #pathwayId()} returns "none".
 */
public class PlayerPathway {
    public static final int NO_PATHWAY = -1;

    private String pathwayId = "none";
    private int sequence = NO_PATHWAY;
    /** 0..100. A potion must be fully digested before the next sequence can be attempted. */
    private float digestion;
    private final Set<String> unlockedAbilities = new LinkedHashSet<>();
    private final Set<String> knownRituals = new LinkedHashSet<>();
    private boolean beyondUnlocked;
    /** Beats of pathway-appropriate behaviour, tracked for the digestion HUD. */
    private int observations;
    private int explorations;
    private int studies;
    private int contradictions;
    /** Cooldowns are stored by ability id so they survive a relog. */
    private final CompoundTag cooldowns = new CompoundTag();

    public static final Codec<PlayerPathway> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("pathway", "none").forGetter(PlayerPathway::pathwayId),
            Codec.INT.optionalFieldOf("sequence", NO_PATHWAY).forGetter(PlayerPathway::sequence),
            Codec.FLOAT.optionalFieldOf("digestion", 0.0f).forGetter(PlayerPathway::digestion),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of())
                    .forGetter(p -> new ArrayList<>(p.unlockedAbilities)),
            Codec.STRING.listOf().optionalFieldOf("rituals", List.of())
                    .forGetter(p -> new ArrayList<>(p.knownRituals)),
            Codec.BOOL.optionalFieldOf("beyond", false).forGetter(PlayerPathway::beyondUnlocked),
            Codec.INT.optionalFieldOf("observations", 0).forGetter(p -> p.observations),
            Codec.INT.optionalFieldOf("explorations", 0).forGetter(p -> p.explorations),
            Codec.INT.optionalFieldOf("studies", 0).forGetter(p -> p.studies),
            Codec.INT.optionalFieldOf("contradictions", 0).forGetter(p -> p.contradictions),
            CompoundTag.CODEC.optionalFieldOf("cooldowns", new CompoundTag()).forGetter(p -> p.cooldowns)
    ).apply(instance, (pathway, seq, digest, unlocked, rituals, beyond, obs, expl, stud, contra, cds) -> {
        PlayerPathway data = new PlayerPathway();
        data.pathwayId = pathway;
        data.sequence = seq;
        data.digestion = digest;
        data.unlockedAbilities.addAll(unlocked);
        data.knownRituals.addAll(rituals);
        data.beyondUnlocked = beyond;
        data.observations = obs;
        data.explorations = expl;
        data.studies = stud;
        data.contradictions = contra;
        data.cooldowns.merge(cds);
        return data;
    }));

    // ---- accessors ------------------------------------------------------------------
    public String pathwayId() {
        return pathwayId;
    }

    public PathwayData.PathwayDef definition() {
        return PathwayData.byId(pathwayId);
    }

    public boolean hasPathway() {
        return sequence >= 0 && !"none".equals(pathwayId);
    }

    public int sequence() {
        return sequence;
    }

    public void setSequence(int value) {
        this.sequence = Math.max(PlayerPathway.NO_PATHWAY, Math.min(9, value));
    }

    public float digestion() {
        return digestion;
    }

    public void setDigestion(float value) {
        this.digestion = Math.max(0.0f, Math.min(100.0f, value));
    }

    public void addDigestion(float delta) {
        setDigestion(this.digestion + delta);
    }

    public boolean beyondUnlocked() {
        return beyondUnlocked;
    }

    public void setBeyondUnlocked(boolean value) {
        this.beyondUnlocked = value;
    }

    public Set<String> unlockedAbilities() {
        return unlockedAbilities;
    }

    public Set<String> knownRituals() {
        return knownRituals;
    }

    public CompoundTag cooldowns() {
        return cooldowns;
    }

    public int observations() {
        return observations;
    }

    public int explorations() {
        return explorations;
    }

    public int studies() {
        return studies;
    }

    public int contradictions() {
        return contradictions;
    }

    public void addObservation() {
        observations++;
    }

    public void addExploration() {
        explorations++;
    }

    public void addStudy() {
        studies++;
    }

    public void addContradiction() {
        contradictions++;
    }

    public void decayContradiction() {
        contradictions = Math.max(0, contradictions - 1);
    }

    // ---- mutation -------------------------------------------------------------------
    /** Starts a pathway at Sequence 9. Only meaningful for a player with no pathway yet. */
    public void begin(String pathway, int startingSequence) {
        this.pathwayId = pathway;
        this.sequence = startingSequence;
        this.digestion = 0.0f;
        this.unlockedAbilities.clear();
        PathwayData.PathwayDef def = definition();
        PathwayData.SequenceDef seqDef = def.bySequence(startingSequence);
        if (seqDef != null) {
            unlockedAbilities.addAll(seqDef.abilities());
        }
    }

    public void unlockSequence(int newSequence) {
        this.sequence = newSequence;
        PathwayData.SequenceDef seqDef = definition().bySequence(newSequence);
        if (seqDef != null) {
            unlockedAbilities.addAll(seqDef.abilities());
        }
        this.digestion = 0.0f;
    }

    public boolean isUnlocked(String abilityId) {
        return unlockedAbilities.contains(abilityId);
    }

    public boolean knowsRitual(String ritualId) {
        return knownRituals.contains(ritualId);
    }

    public void learnRitual(String ritualId) {
        knownRituals.add(ritualId);
    }

    /** Remaining cooldown in ticks for an ability. */
    public int cooldown(String abilityId) {
        return cooldowns.contains(abilityId) ? cooldowns.getInt(abilityId) : 0;
    }

    public void setCooldown(String abilityId, int ticks) {
        cooldowns.putInt(abilityId, ticks);
    }

    public void tickCooldowns() {
        List<String> done = new ArrayList<>();
        for (String key : new ArrayList<>(cooldowns.getAllKeys())) {
            int left = cooldowns.getInt(key) - 1;
            if (left <= 0) {
                done.add(key);
            } else {
                cooldowns.putInt(key, left);
            }
        }
        for (String key : done) {
            cooldowns.remove(key);
        }
    }

    public PlayerPathway copy() {
        PlayerPathway copy = new PlayerPathway();
        copy.pathwayId = pathwayId;
        copy.sequence = sequence;
        copy.digestion = digestion;
        copy.unlockedAbilities.addAll(unlockedAbilities);
        copy.knownRituals.addAll(knownRituals);
        copy.beyondUnlocked = beyondUnlocked;
        copy.observations = observations;
        copy.explorations = explorations;
        copy.studies = studies;
        copy.contradictions = contradictions;
        copy.cooldowns.merge(cooldowns);
        return copy;
    }

    /** Sequence name for chat/HUD, or a neutral string when there is no pathway yet. */
    public String sequenceName() {
        if (!hasPathway()) {
            return "Uninitiated";
        }
        PathwayData.SequenceDef def = definition().bySequence(sequence);
        return def == null ? "Unknown" : def.name();
    }

    public ResourceLocation abilityIcon(String abilityId) {
        return ResourceLocation.fromNamespaceAndPath("pathwaysofthebeyond", "textures/gui/emblem_"
                + definition().emblem() + ".png");
    }
}
