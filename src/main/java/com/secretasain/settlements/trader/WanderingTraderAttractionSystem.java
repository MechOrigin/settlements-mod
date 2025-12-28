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
    
    // Track which traders have visited which huts (trader UUID -> set of hut UUIDs)
    private static final Map<UUID, Set<UUID>> traderVisitedHuts = new HashMap<>();
    
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
        private int cleanupCounter = 0;
        
        /**
         * Performs a tick update.
         * @param world The server world
         */
        void tick(ServerWorld world) {
            tickCounter++;
            cleanupCounter++;
            
            // Only scan every SCAN_INTERVAL ticks to avoid performance issues
            if (tickCounter < SCAN_INTERVAL) {
                return;
            }
            
            tickCounter = 0;
            
            // Clean up visited huts data for removed traders (every 10 scans = ~50 seconds)
            if (cleanupCounter >= SCAN_INTERVAL * 10) {
                cleanupCounter = 0;
                cleanupVisitedHuts(world);
            }
            
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
     * Makes traders pathfind to the nearest trading hut they haven't visited yet.
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
            
            // Make each trader pathfind to the hut if they haven't visited it yet
            for (WanderingTraderEntity trader : traders) {
                UUID traderId = trader.getUuid();
                attractedTraderIds.add(traderId);
                
                // Check if trader has already visited this hut
                if (!hasTraderVisitedHut(trader, traderHut.getId())) {
                    // Make trader pathfind to the hut
                    attractTraderToHut(world, trader, traderHut);
                }
            }
            
            hutData.setAttractedTraders(attractedTraderIds);
            hutData.saveToBuilding(traderHut);
        }
    }
    
    /**
     * Checks if a trader has already visited a specific trading hut.
     * @param trader The wandering trader entity
     * @param hutId The trading hut building ID
     * @return true if trader has visited this hut
     */
    private static boolean hasTraderVisitedHut(WanderingTraderEntity trader, UUID hutId) {
        UUID traderId = trader.getUuid();
        Set<UUID> visitedHuts = traderVisitedHuts.get(traderId);
        return visitedHuts != null && visitedHuts.contains(hutId);
    }
    
    /**
     * Marks a trading hut as visited by a trader.
     * @param trader The wandering trader entity
     * @param hutId The trading hut building ID
     */
    private static void markHutAsVisited(WanderingTraderEntity trader, UUID hutId) {
        UUID traderId = trader.getUuid();
        traderVisitedHuts.computeIfAbsent(traderId, k -> new HashSet<>()).add(hutId);
    }
    
    /**
     * Cleans up visited huts data for traders that no longer exist.
     * Should be called periodically to prevent memory leaks.
     * @param world The server world
     */
    private static void cleanupVisitedHuts(ServerWorld world) {
        traderVisitedHuts.entrySet().removeIf(entry -> {
            UUID traderId = entry.getKey();
            net.minecraft.entity.Entity entity = world.getEntity(traderId);
            return entity == null || entity.isRemoved() || !entity.isAlive();
        });
    }
    
    /**
     * Makes a wandering trader pathfind to a trading hut.
     * @param world The server world
     * @param trader The wandering trader entity
     * @param traderHut The trading hut building
     */
    private static void attractTraderToHut(ServerWorld world, WanderingTraderEntity trader, Building traderHut) {
        BlockPos hutPos = traderHut.getPosition();
        BlockPos traderPos = trader.getBlockPos();
        
        // Calculate distance to hut
        double distanceSq = traderPos.getSquaredDistance(hutPos);
        double distance = Math.sqrt(distanceSq);
        
        // Only attract if within attraction radius and not too close (within 3 blocks = arrived)
        if (distance > ATTRACTION_RADIUS) {
            return; // Too far away
        }
        
        if (distance <= 3.0) {
            // Trader has arrived at the hut - mark as visited
            markHutAsVisited(trader, traderHut.getId());
            SettlementsMod.LOGGER.debug("Wandering trader {} arrived at trading hut {}", 
                trader.getUuid(), traderHut.getId());
            return;
        }
        
        // Find a safe position near the hut (not inside the building)
        BlockPos targetPos = findSafePositionNearHut(world, hutPos);
        if (targetPos == null) {
            targetPos = hutPos; // Fallback to hut position
        }
        
        // Make trader pathfind to the target position
        net.minecraft.entity.ai.pathing.EntityNavigation navigation = trader.getNavigation();
        net.minecraft.entity.ai.pathing.Path path = navigation.findPathTo(targetPos, 0);
        
        if (path != null && !path.isFinished()) {
            navigation.startMovingAlong(path, 0.5); // Walk speed
            SettlementsMod.LOGGER.debug("Wandering trader {} pathfinding to trading hut {} at {}", 
                trader.getUuid(), traderHut.getId(), targetPos);
        } else {
            // Pathfinding failed, try direct movement
            trader.getMoveControl().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                0.5
            );
        }
    }
    
    /**
     * Finds a safe position near a trading hut for traders to pathfind to.
     * Prefers positions outside the building structure.
     * @param world The server world
     * @param hutPos The trading hut position
     * @return Safe position near the hut, or null if none found
     */
    private static BlockPos findSafePositionNearHut(ServerWorld world, BlockPos hutPos) {
        // Try positions in a circle around the hut (3-5 blocks away)
        for (int radius = 3; radius <= 5; radius++) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                int x = (int) (radius * Math.cos(radians));
                int z = (int) (radius * Math.sin(radians));
                
                BlockPos testPos = hutPos.add(x, 0, z);
                BlockPos groundPos = findGroundLevel(world, testPos);
                
                if (groundPos != null && isSafePosition(world, groundPos)) {
                    return groundPos;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Finds ground level at a given X/Z position.
     * @param world The server world
     * @param pos The position (Y will be adjusted)
     * @return Ground position or null if not found
     */
    private static BlockPos findGroundLevel(ServerWorld world, BlockPos pos) {
        int startY = Math.min(pos.getY() + 5, world.getTopY() - 1);
        int minY = Math.max(world.getBottomY(), startY - 20);
        
        // Search downward first
        for (int y = startY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (world.getBlockState(testPos.down()).isOpaque() &&
                world.getBlockState(testPos).isAir() &&
                world.getBlockState(testPos.up()).isAir()) {
                return testPos;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a position is safe for a trader to stand.
     * @param world The server world
     * @param pos The position to check
     * @return true if position is safe
     */
    private static boolean isSafePosition(ServerWorld world, BlockPos pos) {
        // Must have solid ground
        if (!world.getBlockState(pos.down()).isOpaque()) {
            return false;
        }
        
        // Must have air at position and above
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        
        // Avoid water/lava
        if (!world.getBlockState(pos).getFluidState().isEmpty() ||
            !world.getBlockState(pos.up()).getFluidState().isEmpty()) {
            return false;
        }
        
        return true;
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

