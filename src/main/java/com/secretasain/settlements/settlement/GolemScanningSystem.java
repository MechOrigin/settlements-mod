package com.secretasain.settlements.settlement;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * Handles periodic scanning of iron golems for all settlements.
 * Scans settlements in rotation to spread load across ticks.
 */
public class GolemScanningSystem {
    private static final int SCAN_INTERVAL_TICKS = 100; // Scan every 5 seconds (100 ticks)
    private static final long GOLEM_TIMEOUT_MS = 60000; // Remove golems not seen for 60 seconds
    
    private final Map<ServerWorld, WorldScanData> worldData = new HashMap<>();
    
    /**
     * Registers the scanning system with Fabric's server tick events.
     */
    public static void register() {
        GolemScanningSystem system = new GolemScanningSystem();
        
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
            
            // Clean up old golems periodically (every 5 seconds)
            if (tickCounter % SCAN_INTERVAL_TICKS == 0) {
                cleanupOldGolems(allSettlements);
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
         * Scans a specific settlement for golems.
         */
        private void scanSettlement(Settlement settlement, ServerWorld world, SettlementManager manager) {
            List<GolemData> foundGolems = GolemTracker.scanForGolems(settlement, world);
            
            // Update settlement's golem list
            Map<UUID, GolemData> existingGolems = new HashMap<>();
            for (GolemData golem : settlement.getGolems()) {
                existingGolems.put(golem.getEntityId(), golem);
            }
            
            // Update or add found golems
            for (GolemData found : foundGolems) {
                UUID entityId = found.getEntityId();
                if (existingGolems.containsKey(entityId)) {
                    // Update existing golem (position, last seen, preserve assignment)
                    GolemData existing = existingGolems.get(entityId);
                    existing.setLastKnownPos(found.getLastKnownPos());
                    // Assignment is already preserved in existing golem, no need to update
                } else {
                    // Add new golem
                    settlement.getGolems().add(found);
                }
            }
            
            // Mark settlement as dirty to trigger save
            manager.markDirty();
        }
        
        /**
         * Removes golems that haven't been seen for too long.
         */
        private void cleanupOldGolems(Collection<Settlement> settlements) {
            long currentTime = System.currentTimeMillis();
            
            for (Settlement settlement : settlements) {
                List<GolemData> golems = settlement.getGolems();
                golems.removeIf(golem -> {
                    long timeSinceSeen = currentTime - golem.getLastSeen();
                    return timeSinceSeen > GOLEM_TIMEOUT_MS;
                });
            }
        }
    }
}

