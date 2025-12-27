package com.secretasain.settlements.trader;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * System for attracting wandering traders to trader hut buildings.
 * Scans for wandering traders within 64 blocks of trader huts.
 */
public class WanderingTraderAttractionSystem {
    private static final int ATTRACTION_RADIUS = 64; // Blocks
    private static final int SCAN_INTERVAL = 100; // Ticks (5 seconds at 20 TPS)
    
    private final Map<ServerWorld, WorldScanData> worldData = new HashMap<>();
    
    /**
     * Registers the wandering trader attraction system with Fabric's server tick events.
     */
    public static void register() {
        WanderingTraderAttractionSystem system = new WanderingTraderAttractionSystem();
        
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
        
        /**
         * Performs a tick update.
         * @param world The server world
         */
        void tick(ServerWorld world) {
            tickCounter++;
            
            // Only scan every SCAN_INTERVAL ticks to avoid performance issues
            if (tickCounter < SCAN_INTERVAL) {
                return;
            }
            
            tickCounter = 0;
            
            // Get all settlements in this world
            SettlementManager manager = SettlementManager.getInstance(world);
            Collection<Settlement> settlements = manager.getAllSettlements();
            
            // Scan each settlement for trader huts
            for (Settlement settlement : settlements) {
                WanderingTraderAttractionSystem.scanForWanderingTraders(world, settlement);
            }
        }
    }
    
    /**
     * Scans for wandering traders near trader huts in a settlement.
     * @param world The server world
     * @param settlement The settlement to scan
     */
    private static void scanForWanderingTraders(ServerWorld world, Settlement settlement) {
        // Find all trader hut buildings in the settlement
        List<Building> traderHuts = new ArrayList<>();
        for (Building building : settlement.getBuildings()) {
            if (isTraderHut(building)) {
                traderHuts.add(building);
            }
        }
        
        if (traderHuts.isEmpty()) {
            return; // No trader huts in this settlement
        }
        
        // Scan for wandering traders near each trader hut
        for (Building traderHut : traderHuts) {
            scanNearTraderHut(world, traderHut);
        }
    }
    
    /**
     * Scans for wandering traders within attraction radius of a trader hut.
     * @param world The server world
     * @param traderHut The trader hut building
     */
    private static void scanNearTraderHut(ServerWorld world, Building traderHut) {
        BlockPos hutPos = traderHut.getPosition();
        
        // Create bounding box for attraction radius
        Box boundingBox = new Box(
            hutPos.getX() - ATTRACTION_RADIUS,
            hutPos.getY() - ATTRACTION_RADIUS,
            hutPos.getZ() - ATTRACTION_RADIUS,
            hutPos.getX() + ATTRACTION_RADIUS,
            hutPos.getY() + ATTRACTION_RADIUS,
            hutPos.getZ() + ATTRACTION_RADIUS
        );
        
        // Find all wandering traders in the bounding box
        List<WanderingTraderEntity> traders = world.getEntitiesByType(
            EntityType.WANDERING_TRADER,
            boundingBox,
            trader -> {
                // Calculate distance from trader hut
                BlockPos traderPos = trader.getBlockPos();
                double distanceSq = traderPos.getSquaredDistance(hutPos);
                return distanceSq <= (ATTRACTION_RADIUS * ATTRACTION_RADIUS);
            }
        );
        
        if (!traders.isEmpty()) {
            SettlementsMod.LOGGER.debug("Found {} wandering trader(s) near trader hut at {}", 
                traders.size(), hutPos);
            
            // Store attracted traders in building data (via TraderHutData)
            TraderHutData hutData = TraderHutData.getOrCreate(traderHut);
            List<UUID> attractedTraderIds = new ArrayList<>();
            for (WanderingTraderEntity trader : traders) {
                attractedTraderIds.add(trader.getUuid());
            }
            hutData.setAttractedTraders(attractedTraderIds);
            
            // Optional: Move traders toward hut (could be enhanced later)
            // For now, we just detect and track them
        }
    }
    
    /**
     * Checks if a building is a trader hut.
     * @param building The building to check
     * @return true if the building is a trader hut
     */
    private static boolean isTraderHut(Building building) {
        if (building == null || building.getStructureType() == null) {
            return false;
        }
        
        String structurePath = building.getStructureType().getPath();
        // Check if structure name contains "trader_hut" or "traderhut"
        return structurePath.contains("trader_hut") || structurePath.contains("traderhut");
    }
}

