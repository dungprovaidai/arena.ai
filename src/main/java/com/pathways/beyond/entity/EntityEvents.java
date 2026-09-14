package com.pathways.beyond.entity;

import com.pathways.beyond.entity.boss.ChoirmasterEntity;
import com.pathways.beyond.entity.boss.UnblinkingEntity;
import com.pathways.beyond.registry.ModEntities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Attribute registration.
 *
 * <p>Every creature here is deliberately fragile for its threat level: the horror is meant to
 * come from not knowing whether something is real, not from a health bar. Bosses are the
 * exception, and their difficulty is expressed in phases rather than in hit points.
 */
public final class EntityEvents {
    private EntityEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.Watcher.get(), OccultEntities.WatcherEntity.createAttributes().build());
        event.put(ModEntities.Hollow.get(), OccultEntities.HollowEntity.createAttributes().build());
        event.put(ModEntities.WhisperingHusk.get(), OccultEntities.WhisperingHuskEntity.createAttributes().build());
        event.put(ModEntities.VeilTenant.get(), OccultEntities.VeilTenantEntity.createAttributes().build());
        event.put(ModEntities.SpiritWisp.get(), OccultEntities.SpiritWispEntity.createAttributes().build());
        event.put(ModEntities.Marionette.get(), OccultEntities.MarionetteEntity.createAttributes().build());
        event.put(ModEntities.BizarroClone.get(), OccultEntities.BizarroCloneEntity.createAttributes().build());
        event.put(ModEntities.HistoricalEcho.get(), OccultEntities.HistoricalEchoEntity.createAttributes().build());
        event.put(ModEntities.PlayerBodyShell.get(), OccultEntities.PlayerBodyShell.createAttributes().build());
        event.put(ModEntities.ChoirmasterOfTheVeil.get(), ChoirmasterEntity.createAttributes().build());
        event.put(ModEntities.TheUnblinking.get(), UnblinkingEntity.createAttributes().build());
    }

    /** Shared attribute template for "a person-shaped thing that should not be here". */
    static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder humanoidAttributes(
            double health, double damage, double speed, double followRange) {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, followRange)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
    }

    static void noop(IEventBus bus) {
        // keeps the import of IEventBus meaningful for future listeners on this class
    }
}
