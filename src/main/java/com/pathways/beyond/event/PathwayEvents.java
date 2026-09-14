package com.pathways.beyond.event;

import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.registry.ModItems;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.sanity.HallucinationDirector;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;
import com.pathways.beyond.spirit.SpiritWorld;
import com.pathways.beyond.soul.SoulThreadManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Game events: where the mod's economy and consequences live.
 *
 * <p>Two rules are enforced here rather than inside entity classes, because they have to apply
 * to <i>everything</i>:
 *
 * <ol>
 *   <li>hallucinated creatures drop nothing and give no experience. A fight that never happened
 *       must never pay out, or the optimal strategy would be to farm your own psychosis;</li>
 *   <li>killing something real advances digestion and costs sanity, which is the whole
 *       progression loop of the Fool pathway.</li>
 * </ol>
 */
public final class PathwayEvents {
    private PathwayEvents() {}

    // ===================================================================================
    // Loot
    // ===================================================================================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (HallucinationDirector.isHallucination(entity)) {
            event.getDrops().forEach(ItemEntity::discard);
            event.getDrops().clear();
            return;
        }
        if (entity.getPersistentData().getBoolean("pathways_silhouette")) {
            event.getDrops().clear();
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            // the ritual dagger collects blood from what it kills
            if (player.getMainHandItem().is(ModItems.RitualDagger.get())
                    && player.getRandom().nextFloat() < 0.35f) {
                ItemStack drop = new ItemStack(ModItems.BlackBloodVial.get());
                event.getDrops().add(new ItemEntity(player.level(),
                        entity.getX(), entity.getY() + 0.5, entity.getZ(), drop));
            }
            // creatures killed while the player is compromised sometimes leave a memory behind
            if (SanitySystem.supernaturalWeight(entity) > 0.5f
                    && player.getRandom().nextFloat() < 0.25f) {
                ItemStack memory = new ItemStack(ModItems.AncientMemory.get());
                event.getDrops().add(new ItemEntity(player.level(),
                        entity.getX(), entity.getY() + 0.5, entity.getZ(), memory));
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (HallucinationDirector.isHallucination(event.getEntity())) {
            event.setDroppedExperience(0);
        }
    }

    // ===================================================================================
    // Damage
    // ===================================================================================
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        OccultState state = ModAttachments.occult(player);
        float amount = event.getAmount();
        if (state.corruption() > 45.0f) {
            event.setAmount(amount * 0.9f);       // the flesh is tougher than it used to be
        }
        if (state.sanity() < 20.0f) {
            event.setAmount(event.getAmount() * 1.15f);
        }
        if (state.spiritForm()) {
            SanitySystem.spendSanity(player, amount * 1.5f);
        }
    }

    // ===================================================================================
    // Player lifecycle
    // ===================================================================================
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        com.pathways.beyond.pathway.PlayerPathway pathway = ModAttachments.pathway(player);
        if (player.tickCount % 100 == 0) {
            pathway.tickCooldowns();
        }
        // ritual presence is itself a source of digestion: being there counts
        if (com.pathways.beyond.ritual.RitualManager.isPlayerInActiveRitual(player)) {
            pathway.addDigestion(0.05f);
        }
        if (player.tickCount % 200 == 0 && ModAttachments.occult(player).sanity() < 30.0f) {
            attractAttention(player);
        }
    }

    private static void attractAttention(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int alreadyWatching = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0),
                mob -> SanitySystem.supernaturalWeight(mob) > 1.0f).size();
        if (alreadyWatching > 3 || player.getRandom().nextFloat() > 0.25f) {
            return;
        }
        Mob watcher = com.pathways.beyond.registry.ModEntities.Watcher.get().create(level);
        if (watcher == null) {
            return;
        }
        watcher.moveTo(player.getX() + 14, player.getY(), player.getZ() + 10, 0.0f, 0.0f);
        watcher.getPersistentData().putInt("pathways_event_life", 1200);
        level.addFreshEntity(watcher);
        ModNetwork.sendVisual(player, com.pathways.beyond.network.OccultMessages.V_SPIRIT_ENTER,
                watcher.getX(), watcher.getY() + 1.0, watcher.getZ(), 0.5f, 0x13273F, 40);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer dead)
                || !(event.getEntity() instanceof ServerPlayer reborn)) {
            return;
        }
        ModAttachments.copyOnRespawn(dead, reborn);
        OccultState state = ModAttachments.occult(reborn);
        state.setSanity(Math.min(100.0f, state.sanity() + 20.0f));
        state.setSpiritForm(false);
        ModNetwork.sendOccultSync(reborn);
        ModNetwork.sendPathwaySync(reborn);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendPathwaySync(player);
            ModNetwork.sendOccultSync(player);
            SoulThreadManager.sendVisibleThreads(player, 64.0);
            SanitySystem.alertIfLostControl(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendPathwaySync(player);
            ModNetwork.sendOccultSync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpiritWorld.tick(player.serverLevel());
            ModNetwork.sendOccultSync(player);
        }
    }

    // ===================================================================================
    // Level and server lifecycle
    // ===================================================================================
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // the mod's own tick order (sanity -> hallucination -> digestion -> projection) lives in
        // PathwaysMod; this listener only drives the world-side schedulers
        DelayedWorldActions.tick(level);
    }
}
