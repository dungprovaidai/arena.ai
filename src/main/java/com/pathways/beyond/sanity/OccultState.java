package com.pathways.beyond.sanity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Volatile supernatural state for one player: Sanity, Corruption and the soul-tether.
 *
 * <p>Sanity is the short-term mental resource (spent by abilities, restored by rest and
 * ritual). Corruption is the long-term transformation track (never really goes down).
 * They are independent on purpose: a player can be sane but deeply corrupted, or
 * hallucinating while physically untouched.
 */
public class OccultState {
    private float sanity = 100.0f;
    private float corruption = 0.0f;
    /** True while the player's soul is projected out of their body. */
    private boolean spiritForm;
    /** Strain on the tether, 0..100. At 100 the connection snaps. */
    private float tetherStrain;
    /** Where the abandoned body is standing, if any. */
    private Optional<BlockPos> bodyPos = Optional.empty();
    private int boundThreadCount;
    /** Ticks until the next hallucination roll can fire. */
    private int hallucinationCooldown;
    /** Ward Charm charges left; each absorbs one possession attempt / sanity plunge. */
    private int wardCharges;
    /** Set by Miracle Invoker's Debt of Miracles: survives one lethal blow. */
    private boolean miracleDebt;
    /** Disguise name for Faceless, empty when not disguised. */
    private String disguise = "";
    /** Sequence-0 flag: The Beyond has started reacting to this player. */
    private boolean noticedByTheBeyond;
    /** Last remembered state, used by Scholar of Yore's Rewind Moment. */
    private double rememberX;
    private double rememberY;
    private double rememberZ;
    private float rememberHealth;
    private int rememberAge;

    public static final Codec<OccultState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("sanity", 100.0f).forGetter(OccultState::sanity),
            Codec.FLOAT.optionalFieldOf("corruption", 0.0f).forGetter(OccultState::corruption),
            Codec.BOOL.optionalFieldOf("spirit_form", false).forGetter(OccultState::spiritForm),
            Codec.FLOAT.optionalFieldOf("tether", 0.0f).forGetter(OccultState::tetherStrain),
            BlockPos.CODEC.optionalFieldOf("body").forGetter(OccultState::bodyPos),
            Codec.INT.optionalFieldOf("threads", 0).forGetter(OccultState::boundThreadCount),
            Codec.INT.optionalFieldOf("ward", 0).forGetter(OccultState::wardCharges),
            Codec.BOOL.optionalFieldOf("miracle_debt", false).forGetter(OccultState::miracleDebt),
            Codec.STRING.optionalFieldOf("disguise", "").forGetter(OccultState::disguise),
            Codec.BOOL.optionalFieldOf("noticed", false).forGetter(OccultState::noticedByTheBeyond),
            Codec.DOUBLE.optionalFieldOf("rx", 0.0).forGetter(OccultState::rememberX),
            Codec.DOUBLE.optionalFieldOf("ry", 0.0).forGetter(OccultState::rememberY),
            Codec.DOUBLE.optionalFieldOf("rz", 0.0).forGetter(OccultState::rememberZ),
            Codec.FLOAT.optionalFieldOf("rh", 20.0f).forGetter(OccultState::rememberHealth),
            Codec.INT.optionalFieldOf("rage", 0).forGetter(OccultState::rememberAge)
    ).apply(instance, (sanity, corruption, spirit, tether, body, threads, ward, debt, disguise,
                       noticed, rx, ry, rz, rh, rage) -> {
        OccultState state = new OccultState();
        state.sanity = sanity;
        state.corruption = corruption;
        state.spiritForm = spirit;
        state.tetherStrain = tether;
        state.bodyPos = body;
        state.boundThreadCount = threads;
        state.wardCharges = ward;
        state.miracleDebt = debt;
        state.disguise = disguise;
        state.noticedByTheBeyond = noticed;
        state.rememberX = rx;
        state.rememberY = ry;
        state.rememberZ = rz;
        state.rememberHealth = rh;
        state.rememberAge = rage;
        return state;
    }));

    public float sanity() {
        return sanity;
    }

    public void setSanity(float value) {
        this.sanity = SanitySystem.clamp(value);
    }

    public void addSanity(float delta) {
        setSanity(this.sanity + delta);
    }

    public float corruption() {
        return corruption;
    }

    public void setCorruption(float value) {
        this.corruption = Math.max(0.0f, Math.min(100.0f, value));
    }

    public void addCorruption(float delta) {
        setCorruption(this.corruption + delta);
    }

    public boolean spiritForm() {
        return spiritForm;
    }

    public void setSpiritForm(boolean value) {
        this.spiritForm = value;
    }

    public float tetherStrain() {
        return tetherStrain;
    }

    public void setTetherStrain(float value) {
        this.tetherStrain = Math.max(0.0f, Math.min(100.0f, value));
    }

    public Optional<BlockPos> bodyPos() {
        return bodyPos;
    }

    public void setBodyPos(Optional<BlockPos> pos) {
        this.bodyPos = pos;
    }

    public int boundThreadCount() {
        return boundThreadCount;
    }

    public void setBoundThreadCount(int value) {
        this.boundThreadCount = Math.max(0, value);
    }

    public int hallucinationCooldown() {
        return hallucinationCooldown;
    }

    public void setHallucinationCooldown(int ticks) {
        this.hallucinationCooldown = Math.max(0, ticks);
    }

    public void tickHallucinationCooldown() {
        if (hallucinationCooldown > 0) {
            hallucinationCooldown--;
        }
    }

    public int wardCharges() {
        return wardCharges;
    }

    public void setWardCharges(int value) {
        this.wardCharges = Math.max(0, value);
    }

    public boolean consumeWard() {
        if (wardCharges > 0) {
            wardCharges--;
            return true;
        }
        return false;
    }

    public boolean miracleDebt() {
        return miracleDebt;
    }

    public void setMiracleDebt(boolean value) {
        this.miracleDebt = value;
    }

    public String disguise() {
        return disguise;
    }

    public void setDisguise(String value) {
        this.disguise = value == null ? "" : value;
    }

    public boolean noticedByTheBeyond() {
        return noticedByTheBeyond;
    }

    public void setNoticedByTheBeyond(boolean value) {
        this.noticedByTheBeyond = value;
    }

    public double rememberX() {
        return rememberX;
    }

    public double rememberY() {
        return rememberY;
    }

    public double rememberZ() {
        return rememberZ;
    }

    public float rememberHealth() {
        return rememberHealth;
    }

    public int rememberAge() {
        return rememberAge;
    }

    public void remember(double x, double y, double z, float health, int age) {
        this.rememberX = x;
        this.rememberY = y;
        this.rememberZ = z;
        this.rememberHealth = health;
        this.rememberAge = age;
    }

    public void ageMemory() {
        rememberAge++;
    }

    /** On death: Sanity takes a lasting hit, Corruption creeps up, the tether snaps. */
    public OccultState onDeath() {
        OccultState next = onRespawn();
        next.sanity = SanitySystem.clamp(this.sanity - 12.0f);
        next.corruption = Math.min(100.0f, this.corruption + 3.0f);
        return next;
    }

    /** On respawn: the body is restored, the soul is back home, sanity keeps its damage. */
    public OccultState onRespawn() {
        OccultState next = new OccultState();
        next.sanity = this.sanity;
        next.corruption = this.corruption;
        next.wardCharges = this.wardCharges;
        next.miracleDebt = this.miracleDebt;
        next.disguise = this.disguise;
        next.noticedByTheBeyond = this.noticedByTheBeyond;
        next.spiritForm = false;
        next.tetherStrain = 0.0f;
        next.bodyPos = Optional.empty();
        next.boundThreadCount = 0;
        return next;
    }
}
