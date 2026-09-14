package com.pathways.beyond.entity;

import com.pathways.beyond.registry.ModParticles;
import com.pathways.beyond.registry.ModSounds;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;
import com.pathways.beyond.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static com.pathways.beyond.entity.EntityEvents.humanoidAttributes;

/**
 * The uncanny things that live in the mod.
 *
 * <p>Every creature here follows the same art direction: an asymmetrical humanoid where one
 * side of the body does not match the other, the head is slightly the wrong size, and the
 * animation set contains one motion that a person cannot make. Several are not hostile at all
 * - the Watcher's entire function is to be seen.
 *
 * <p>The shared AI is intentionally minimal. These creatures should not behave like skeletons:
 * they stand, they watch, they step closer while the player is not looking, and only commit to
 * violence when the player is already compromised.
 */
public final class OccultEntities {
    private OccultEntities() {}

    // ===================================================================================
    // Shared state
    // ===================================================================================
    protected static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(UncannyEntity.class, EntityDataSerializers.INT);

    /** How hostile an uncanny humanoid is on a scale from "watch" to "kill". */
    public enum Presence { WATCHING, APPROACHING, HUNTING, FLEEING }

    // ===================================================================================
    // Base: the uncanny humanoid
    // ===================================================================================
    public abstract static class UncannyEntity extends Monster {
        /** Convenience: entity logic here only ever runs on the logical server. */
        protected ServerLevel serverLevel() {
            return (ServerLevel) level();
        }

        private float asymmetry = 0.5f;

        protected UncannyEntity(EntityType<? extends UncannyEntity> type, Level level) {
            super(type, level);
            this.asymmetry = level.random.nextFloat();
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
            super.defineSynchedData(builder);
            builder.define(DATA_STATE, Presence.WATCHING.ordinal());
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            // the signature behaviour: stand still and watch, then close the distance when unobserved
            this.goalSelector.addGoal(1, new WatchingApproachGoal(this, 1.0));
            this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.55));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0f));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new CompromisedTargetGoal(this));
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        public float asymmetry() {
            return asymmetry;
        }

        public Presence presence() {
            Presence[] values = Presence.values();
            int index = this.entityData.get(DATA_STATE);
            return values[Math.max(0, Math.min(values.length - 1, index))];
        }

        protected void setPresence(Presence presence) {
            this.entityData.set(DATA_STATE, presence.ordinal());
        }

        /** Every uncanny creature emits its own particle signature, so they are never confused. */
        protected abstract void ambientVfx(ServerLevel level);

        @Override
        public void aiStep() {
            super.aiStep();
            if (!this.level().isClientSide() && this.tickCount % 40 == 0) {
                ambientVfx((ServerLevel) this.level());
            }
            // the wrongness is audible: the sound follows the creature rather than its steps
            if (!this.level().isClientSide() && this.tickCount % 200 == 0 && this.getRandom().nextFloat() < 0.3f) {
                this.level().playSound(null, this.blockPosition(), idleSound(),
                        SoundSource.HOSTILE, 0.35f, 0.85f + asymmetry * 0.3f);
            }
        }

        protected SoundEvent idleSound() {
            return ModSounds.HollowIdle.get();
        }

        @Override
        public boolean canAttack(LivingEntity target) {
            if (target instanceof Player player && isProtected(player)) {
                return false;
            }
            return super.canAttack(target);
        }

        /** Players who are deep in a ritual are not attacked: the circle is a truce. */
        private static boolean isProtected(Player player) {
            return player instanceof ServerPlayer serverPlayer
                    && com.pathways.beyond.ritual.RitualManager.isPlayerInActiveRitual(serverPlayer);
        }

        @Override
        public boolean removeWhenFarAway(double distance) {
            return false;
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putFloat("pathways_asymmetry", asymmetry);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            if (tag.contains("pathways_asymmetry")) {
                asymmetry = tag.getFloat("pathways_asymmetry");
            }
        }
    }

    /** Advances only while the player cannot see the creature, and freezes when observed. */
    public static class WatchingApproachGoal extends Goal {
        private final UncannyEntity entity;
        private final double speed;
        private Player target;

        public WatchingApproachGoal(UncannyEntity entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            target = entity.level().getNearestPlayer(entity, 48.0);
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distance = entity.distanceTo(target);
            if (distance > 40.0) {
                entity.setPresence(Presence.WATCHING);
                return false;
            }
            return entity.hasLineOfSight(target) || distance < 8.0;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && entity.distanceTo(target) < 48.0;
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            boolean observed = target.hasLineOfSight(entity);
            double distance = entity.distanceTo(target);
            if (observed && distance < 24.0) {
                // freeze. Do not move. Do not blink. Let them look.
                entity.setPresence(Presence.WATCHING);
                entity.getNavigation().stop();
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.4));
                return;
            }
            entity.setPresence(distance < 6.0 ? Presence.HUNTING : Presence.APPROACHING);
            entity.getNavigation().moveTo(target, speed);
        }

        @Override
        public void stop() {
            entity.getNavigation().stop();
            entity.setPresence(Presence.WATCHING);
        }
    }

    /**
     * Targets players whose sanity is low enough that they cannot tell this thing from a person,
     * and only those players. Someone fully Stable simply gets watched.
     */
    public static class CompromisedTargetGoal extends Goal {
        private final UncannyEntity entity;
        private Player target;

        public CompromisedTargetGoal(UncannyEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            target = entity.level().getNearestPlayer(entity, 24.0);
            if (target == null || !(target instanceof ServerPlayer serverPlayer)) {
                return false;
            }
            OccultState state = ModAttachments.occult(serverPlayer);
            return state.sanity() < 45.0f && entity.getSensing().hasLineOfSight(target);
        }

        @Override
        public void start() {
            entity.setTarget(target);
            entity.setPresence(Presence.HUNTING);
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && entity.getTarget() == target;
        }
    }

    // ===================================================================================
    // Watcher: it only watches
    // ===================================================================================
    public static class WatcherEntity extends UncannyEntity {
        public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
            super(type, level);
            this.xpReward = 0;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(12.0, 0.0, 0.0, 48.0);
        }

        @Override
        protected void registerGoals() {
            // no goals at all: the Watcher does not move, does not fight, does not flee
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        protected void doPush(Entity entity) {
            // it does not acknowledge being walked into
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.getEntity() instanceof ServerPlayer player) {
                SanitySystem.addCorruption(player, 1.5f);
                SanitySystem.spendSanity(player, 4.0f);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "You hit it. It was a person. It is still a person. It is not there.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
                this.discard();
                this.level().playSound(null, this.blockPosition(), ModSounds.WatcherVanish.get(),
                        SoundSource.HOSTILE, 1.0f, 1.0f);
                return true;
            }
            return false;
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return true;
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.ChromaticSpeck.get(), this.getX(),
                    this.getY() + this.getBbHeight() * 0.9, this.getZ(), 1, 0.1, 0.05, 0.1, 0.0);
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.WatcherStare.get();
        }

        @Override
        public void aiStep() {
            super.aiStep();
            // staring directly at the player for a long time is itself the attack
            if (!level().isClientSide() && tickCount % 100 == 0 && tickCount > 0) {
                Player nearest = level().getNearestPlayer(this, 24.0);
                if (nearest instanceof ServerPlayer serverPlayer && hasLineOfSight(serverPlayer)) {
                    SanitySystem.spendSanity(serverPlayer, 0.8f);
                }
            }
        }

        @Override
        public boolean shouldRenderAtSqrDistance(double distance) {
            return distance < 128.0 * 128.0;
        }

        @Override
        public boolean fireImmune() {
            return true;
        }
    }

    // ===================================================================================
    // Hollow: a person with the person taken out
    // ===================================================================================
    public static class HollowEntity extends UncannyEntity {
        public HollowEntity(EntityType<? extends HollowEntity> type, Level level) {
            super(type, level);
            this.xpReward = 3;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(16.0, 3.0, 0.245, 24.0);
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.HollowIdle.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.VeilSmoke.get(), this.getX(),
                    this.getY() + this.getBbHeight() * 0.8, this.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return ModSounds.HollowIdle.get();
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return ModSounds.HollowStep.get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return ModSounds.HollowStep.get();
        }

        /** A Hollow does not drop anything, because there is nothing in it. */
        @Override
        protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
            if (getRandom().nextFloat() < 0.15f) {
                spawnAtLocation(com.pathways.beyond.registry.ModItems.GraveEarth.get().getDefaultInstance());
            }
        }
    }

    // ===================================================================================
    // Whispering Husk: speaks with a mouth that is not opened
    // ===================================================================================
    public static class WhisperingHuskEntity extends UncannyEntity {
        public WhisperingHuskEntity(EntityType<? extends WhisperingHuskEntity> type, Level level) {
            super(type, level);
            this.xpReward = 5;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(20.0, 4.5, 0.27, 32.0);
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, true));
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.HuskWhisper.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.BlackWisp.get(), this.getX(),
                    this.getY() + this.getBbHeight() * 0.5, this.getZ(), 2, 0.25, 0.25, 0.25, 0.0);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof ServerPlayer player) {
                SanitySystem.spendSanity(player, 2.0f);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "It says your name, correctly, in your own voice.").withStyle(
                        net.minecraft.ChatFormatting.DARK_PURPLE), true);
                com.pathways.beyond.sanity.HallucinationDirector.force(player,
                        com.pathways.beyond.network.OccultMessages.H_WHISPER);
            }
            return hit;
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return ModSounds.HuskWhisper.get();
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return ModSounds.HuskAttack.get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return ModSounds.HuskWhisper.get();
        }
    }

    // ===================================================================================
    // Veil Tenant: lives in the space behind a doorway
    // ===================================================================================
    public static class VeilTenantEntity extends UncannyEntity {
        private boolean hunting;

        public VeilTenantEntity(EntityType<? extends VeilTenantEntity> type, Level level) {
            super(type, level);
            this.xpReward = 8;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(30.0, 6.0, 0.3, 40.0);
        }

        public void setHunting(boolean hunting) {
            this.hunting = hunting;
            this.setPresence(hunting ? Presence.HUNTING : Presence.WATCHING);
        }

        public boolean isHunting() {
            return hunting;
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.Breathing.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.VeilSmoke.get(), this.getX(),
                    this.getY() + this.getBbHeight() * 0.7, this.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof ServerPlayer player) {
                // the tenant tries to take the body, not just the health
                if (!player.hasEffect(com.pathways.beyond.registry.ModEffects.Clarity)) {
                    OccultState state = ModAttachments.occult(player);
                    state.addCorruption(1.0f);
                    SanitySystem.spendSanity(player, 5.0f);
                    player.level().playSound(null, player.blockPosition(), ModSounds.TenantPossess.get(),
                            SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            }
            return hit;
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return ModSounds.Breathing.get();
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return ModSounds.TenantPossess.get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return ModSounds.TenantPossess.get();
        }

        /** A tenant that is dying leaves the doorway it was living in marked. */
        @Override
        protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
            if (getRandom().nextFloat() < 0.4f) {
                spawnAtLocation(com.pathways.beyond.registry.ModItems.AbyssalEye.get().getDefaultInstance());
            }
            if (getRandom().nextFloat() < 0.2f) {
                spawnAtLocation(com.pathways.beyond.registry.ModItems.FacelessSkin.get().getDefaultInstance());
            }
        }
    }

    // ===================================================================================
    // Spirit Wisp: a nothing that is looking for something to be
    // ===================================================================================
    public static class SpiritWispEntity extends UncannyEntity {
        public SpiritWispEntity(EntityType<? extends SpiritWispEntity> type, Level level) {
            super(type, level);
            this.xpReward = 1;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(4.0, 0.0, 0.12, 16.0);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.4));
            this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.SpiritAmbient.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.SpiritMote.get(), this.getX(),
                    this.getY() + 0.8, this.getZ(), 2, 0.2, 0.3, 0.2, 0.005);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            // wisps drift upward slightly and never touch the ground
            if (!level().isClientSide() && tickCount % 20 == 0) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.01, 0.0));
            }
        }
    }

    // ===================================================================================
    // Marionette: the mod's signature "that used to be a mob" creature
    // ===================================================================================
    public static class MarionetteEntity extends UncannyEntity {
        private UUID owner;
        private int threadId = -1;

        public MarionetteEntity(EntityType<? extends MarionetteEntity> type, Level level) {
            super(type, level);
            this.xpReward = 2;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(18.0, 3.5, 0.26, 32.0);
        }

        public void setOwner(UUID owner) {
            this.owner = owner;
        }

        public UUID owner() {
            return owner;
        }

        public void setThreadId(int threadId) {
            this.threadId = threadId;
        }

        public int threadId() {
            return threadId;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 0.9, true));
            this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.MarionetteJoint.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            // the threads are always visible if you are looking for them
            level.sendParticles(ModParticles.SoulFlow.get(), this.getX(),
                    this.getY() + this.getBbHeight(), this.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit) {
                level().playSound(null, blockPosition(), ModSounds.MarionetteJoint.get(),
                        SoundSource.HOSTILE, 0.8f, 0.7f);
            }
            return hit;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            // puppets move in half-steps: a jerk, then a correction, never a smooth walk
            if (!level().isClientSide() && tickCount % 30 < 4) {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x * 1.6, motion.y, motion.z * 1.6);
            }
            if (!level().isClientSide() && owner != null && tickCount % 600 == 0
                    && level() instanceof ServerLevel serverLevel) {
                ServerPlayer master = serverLevel.getServer().getPlayerList().getPlayer(owner);
                if (master == null) {
                    // with the puppeteer gone the threads go slack, and then so does the puppet
                    this.hurt(this.damageSources().magic(), 4.0f);
                }
            }
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            if (owner != null) {
                tag.putUUID("pathways_owner", owner);
            }
            tag.putInt("pathways_thread", threadId);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            if (tag.hasUUID("pathways_owner")) {
                owner = tag.getUUID("pathways_owner");
            }
            threadId = tag.getInt("pathways_thread");
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return ModSounds.MarionetteJoint.get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return ModSounds.ThreadSever.get();
        }
    }

    // ===================================================================================
    // Bizarro Clone: a copy of the player that plays back their behaviour
    // ===================================================================================
    public static class BizarroCloneEntity extends UncannyEntity {
        private UUID owner;
        private boolean decoy;

        public BizarroCloneEntity(EntityType<? extends BizarroCloneEntity> type, Level level) {
            super(type, level);
            this.xpReward = 0;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(20.0, 4.0, 0.3, 24.0);
        }

        public void setOwner(Player owner) {
            this.owner = owner.getUUID();
        }

        public UUID owner() {
            return owner;
        }

        public void setDecoy(boolean decoy) {
            this.decoy = decoy;
        }

        public boolean isDecoy() {
            return decoy;
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.Breathing.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.ChromaticSpeck.get(), this.getX(),
                    this.getY() + this.getBbHeight() * 0.9, this.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (decoy) {
                // dispelling the decoy reveals what it was made of
                if (!level().isClientSide()) {
                    serverLevel().sendParticles(ModParticles.ChromaticSpeck.get(),
                            getX(), getY() + 1.0, getZ(), 40, 0.5, 0.8, 0.5, 0.05);
                    level().playSound(null, blockPosition(), ModSounds.FacelessShift.get(),
                            SoundSource.HOSTILE, 0.8f, 1.2f);
                    discard();
                }
                return true;
            }
            return super.hurt(source, amount);
        }

        @Override
        public boolean removeWhenFarAway(double distance) {
            return decoy && distance > 64.0;
        }

        @Override
        protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
            // killing your own copy is one of the few reliable sources of a soul fragment
            spawnAtLocation(com.pathways.beyond.registry.ModItems.SoulFragment.get().getDefaultInstance());
        }
    }

    // ===================================================================================
    // Historical Echo: a replay of something that already happened
    // ===================================================================================
    public static class HistoricalEchoEntity extends UncannyEntity {
        private EntityType<?> echoType;
        private boolean aggressive;

        public HistoricalEchoEntity(EntityType<? extends HistoricalEchoEntity> type, Level level) {
            super(type, level);
            this.xpReward = 0;
            this.setInvulnerable(true);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return humanoidAttributes(10.0, 2.0, 0.25, 16.0);
        }

        public void setEchoType(EntityType<?> echoType) {
            this.echoType = echoType;
        }

        public EntityType<?> echoType() {
            return echoType;
        }

        public void setAggressive(boolean aggressive) {
            this.aggressive = aggressive;
            this.setPresence(aggressive ? Presence.HUNTING : Presence.WATCHING);
        }

        public boolean isAggressiveEcho() {
            return aggressive;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            if (aggressive) {
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            } else {
                this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5));
                this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            }
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return true;
        }

        @Override
        protected SoundEvent idleSound() {
            return ModSounds.MemoryEcho.get();
        }

        @Override
        protected void ambientVfx(ServerLevel level) {
            level.sendParticles(ModParticles.SpiritMote.get(), this.getX(),
                    this.getY() + this.getBbHeight() * 0.5, this.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            // echoes do not age; they run out of remembering
            if (!level().isClientSide() && tickCount > 600) {
                discard();
            }
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof ServerPlayer player) {
                SanitySystem.spendSanity(player, 1.0f);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "It is not here. It has not been here for a very long time.").withStyle(
                        net.minecraft.ChatFormatting.DARK_GRAY), true);
            }
            return hit;
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putBoolean("pathways_aggressive", aggressive);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            aggressive = tag.getBoolean("pathways_aggressive");
        }
    }

    // ===================================================================================
    // Player body shell: what is left behind during Soul Projection
    // ===================================================================================
    public static class PlayerBodyShellEntity extends net.minecraft.world.entity.PathfinderMob {
        private UUID owner;
        private String ownerName = "";

        public PlayerBodyShellEntity(EntityType<? extends PlayerBodyShellEntity> type, Level level) {
            super(type, level);
            this.setInvulnerable(false);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Mob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 20.0)
                    .add(Attributes.MOVEMENT_SPEED, 0.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
        }

        public void setOwner(ServerPlayer player) {
            this.owner = player.getUUID();
            this.setOwnerName(player.getGameProfile().getName());
        }

        public void setOwnerName(String name) {
            this.ownerName = name;
            this.setCustomName(net.minecraft.network.chat.Component.literal(
                    name + "'s body"));
            this.setCustomNameVisible(true);
        }

        public String ownerName() {
            return ownerName;
        }

        public UUID owner() {
            return owner;
        }

        @Override
        protected void registerGoals() {
            // the body breathes and nothing else
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (!level().isClientSide() && tickCount % 20 == 0) {
                serverLevel().sendParticles(ModParticles.SpiritMote.get(), getX(), getY() + 1.0, getZ(),
                        1, 0.2, 0.3, 0.2, 0.0);
            }
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            // harming the body while the soul is away is the worst thing you can do to yourself
            if (level() instanceof ServerLevel serverLevel && owner != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(owner);
                if (player != null) {
                    SanitySystem.spendSanity(player, amount * 2.0f);
                    SanitySystem.addCorruption(player, amount * 0.5f);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                    "You feel your body being touched, from a long way away.")
                            .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
                    level().playSound(null, blockPosition(), ModSounds.Heartbeat.get(),
                            SoundSource.HOSTILE, 1.0f, 0.7f);
                }
            }
            return super.hurt(source, amount);
        }

        @Override
        protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
            if (level().getServer() != null && owner != null) {
                ServerPlayer player = level().getServer().getPlayerList().getPlayer(owner);
                if (player != null) {
                    com.pathways.beyond.spirit.SoulProjection.onBodyLost(player);
                }
            }
        }

        @Override
        public boolean removeWhenFarAway(double distance) {
            return false;
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            if (owner != null) {
                tag.putUUID("pathways_owner", owner);
            }
            tag.putString("pathways_owner_name", ownerName);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            if (tag.hasUUID("pathways_owner")) {
                owner = tag.getUUID("pathways_owner");
            }
            ownerName = tag.getString("pathways_owner_name");
        }
    }

    /** Used by the spirit world to place decorative, temporary silhouettes. */
    public static BlockPos surfaceBelow(Level level, BlockPos origin) {
        for (int y = origin.getY(); y > level.getMinBuildHeight(); y--) {
            BlockPos candidate = new BlockPos(origin.getX(), y, origin.getZ());
            if (!level.getBlockState(candidate).isAir()) {
                return candidate.above();
            }
        }
        return origin;
    }
}
