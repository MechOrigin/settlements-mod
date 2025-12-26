package com.secretasain.settlements.settlement;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * Handles periodic scanning of villagers for all settlements.
 * Scans settlements in rotation to spread load across ticks.
 */
public class VillagerScanningSystem {
    private static final int SCAN_INTERVAL_TICKS = 100; // Scan every 5 seconds (100 ticks)
    private static final long VILLAGER_TIMEOUT_MS = 60000; // Remove villagers not seen for 60 seconds
    
    private final Map<ServerWorld, WorldScanData> worldData = new HashMap<>();
    
    /**
     * Registers the scanning system with Fabric's server tick events.
     */
    public static void register() {
        VillagerScanningSystem system = new VillagerScanningSystem();
        
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            system.tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private void tick(ServerWorld world) {
        WorldScanData data = worldData.computeIfAbsent(world, w -> new WorldScanData());
        data.tick(world);
    }
    
    /**
     * Per-world scanning data and state.
     */
    private static class WorldScanData {
        private int tickCounter = 0;
        private int currentSettlementIndex = 0;
        private List<UUID> settlementIds = new ArrayList<>();
        private long lastFullScan = 0;
        
        /**
         * Performs a tick update.
         * @param world The server world
         */
        void tick(ServerWorld world) {
            tickCounter++;
            
            SettlementManager manager = SettlementManager.getInstance(world);
            Collection<Settlement> allSettlements = manager.getAllSettlements();
            
            // Update settlement list if it changed
            if (tickCounter % SCAN_INTERVAL_TICKS == 0) {
                updateSettlementList(allSettlements);
            }
            
            // Scan one settlement per tick (rotation)
            if (!settlementIds.isEmpty() && tickCounter % 10 == 0) { // Scan one every 10 ticks
                scanNextSettlement(world, manager);
            }
            
            // Clean up old villagers periodically (every 5 seconds)
            if (tickCounter % SCAN_INTERVAL_TICKS == 0) {
                cleanupOldVillagers(allSettlements);
            }
        }
        
        /**
         * Updates the list of settlement IDs to scan.
         */
        private void updateSettlementList(Collection<Settlement> settlements) {
            settlementIds.clear();
            for (Settlement settlement : settlements) {
                settlementIds.add(settlement.getId());
            }
            // Reset index if list changed
            if (currentSettlementIndex >= settlementIds.size()) {
                currentSettlementIndex = 0;
            }
        }
        
        /**
         * Scans the next settlement in rotation.
         */
        private void scanNextSettlement(ServerWorld world, SettlementManager manager) {
            if (settlementIds.isEmpty()) {
                return;
            }
            
            // Get next settlement in rotation
            UUID settlementId = settlementIds.get(currentSettlementIndex);
            Settlement settlement = manager.getSettlement(settlementId);
            
            if (settlement != null) {
                scanSettlement(settlement, world, manager);
            }
            
            // Move to next settlement
            currentSettlementIndex = (currentSettlementIndex + 1) % settlementIds.size();
        }
        
        /**
         * Scans a specific settlement for villagers.
         */
        private void scanSettlement(Settlement settlement, ServerWorld world, SettlementManager manager) {
            List<VillagerData> foundVillagers = VillagerTracker.scanForVillagers(settlement, world);
            
            // Update settlement's villager list
            Map<UUID, VillagerData> existingVillagers = new HashMap<>();
            for (VillagerData villager : settlement.getVillagers()) {
                existingVillagers.put(villager.getEntityId(), villager);
            }
            
            // Update or add found villagers
            for (VillagerData found : foundVillagers) {
                UUID entityId = found.getEntityId();
                if (existingVillagers.containsKey(entityId)) {
                    // Update existing villager (position, last seen)
                    VillagerData existing = existingVillagers.get(entityId);
                    existing.setLastKnownPos(found.getLastKnownPos());
                    // lastSeen is updated automatically in setLastKnownPos
                } else {
                    // Add new villager
                    settlement.getVillagers().add(found);
                    // Update settlement level (may have changed with new villager)
                    SettlementLevelManager.updateSettlementLevel(settlement);
                }
            }
            
            // Mark settlement as dirty to trigger save
            manager.markDirty();
        }
        
        /**
         * Removes villagers that haven't been seen for too long.
         */
        private void cleanupOldVillagers(Collection<Settlement> settlements) {
            long currentTime = System.currentTimeMillis();
            
            for (Settlement settlement : settlements) {
                List<VillagerData> villagers = settlement.getVillagers();
                int oldSize = villagers.size();
                villagers.removeIf(villager -> {
                    long timeSinceSeen = currentTime - villager.getLastSeen();
                    return timeSinceSeen > VILLAGER_TIMEOUT_MS;
                });
                // Update settlement level if villagers were removed
                if (villagers.size() < oldSize) {
                    SettlementLevelManager.updateSettlementLevel(settlement);
                }
            }
        }
    }
}

