package com.secretasain.settlements.settlement;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Handles villager-related events (death, despawn, etc.)
 */
public class VillagerEventHandlers {
    
    /**
     * Registers all villager event handlers.
     */
    public static void register() {
        // Handle villager death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof VillagerEntity) {
                VillagerEntity villager = (VillagerEntity) entity;
                if (entity.getWorld() instanceof ServerWorld) {
                    ServerWorld serverWorld = (ServerWorld) entity.getWorld();
                    handleVillagerRemoved(villager, serverWorld);
                }
            }
        });
        
        // Handle entity removal (despawn, chunk unload, etc.)
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof VillagerEntity) {
                VillagerEntity villager = (VillagerEntity) entity;
                if (world instanceof ServerWorld) {
                    ServerWorld serverWorld = (ServerWorld) world;
                    // Only handle if entity is actually removed (not just unloaded temporarily)
                    if (entity.isRemoved()) {
                        handleVillagerRemoved(villager, serverWorld);
                    }
                }
            }
        });
    }
    
    /**
     * Handles when a villager is removed (death, despawn, etc.)
     * @param villager The villager entity that was removed
     * @param world The server world
     */
    private static void handleVillagerRemoved(VillagerEntity villager, ServerWorld world) {
        UUID villagerId = villager.getUuid();
        SettlementManager manager = SettlementManager.getInstance(world);
        
        // Find and remove villager from all settlements
        boolean removed = false;
        for (Settlement settlement : manager.getAllSettlements()) {
            if (settlement.getVillagers().removeIf(villagerData -> 
                villagerData.getEntityId().equals(villagerId)
            )) {
                removed = true;
                // Update settlement level (may have changed with villager removal)
                SettlementLevelManager.updateSettlementLevel(settlement);
            }
        }
        
        // Mark as dirty to save changes if a villager was removed
        if (removed) {
            manager.markDirty();
        }
    }
}

