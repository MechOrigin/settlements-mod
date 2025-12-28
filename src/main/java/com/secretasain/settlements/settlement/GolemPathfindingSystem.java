package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.UUID;

/**
 * Handles pathfinding for assigned golems to their wall stations.
 * Makes golems stay within a small radius of their assigned wall station persistently.
 */
public class GolemPathfindingSystem {
    private static final int PATHFINDING_INTERVAL_TICKS = 40; // Update pathfinding every 2 seconds (40 ticks)
    private static final double WALL_STATION_RADIUS = 16.0; // Golems must stay within 16 blocks of their wall station
    private static final double WALL_STATION_RADIUS_SQ = WALL_STATION_RADIUS * WALL_STATION_RADIUS; // Squared distance for comparison
    
    /**
     * Registers the pathfinding system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private static void tick(ServerWorld world) {
        // Only run periodically to avoid performance issues
        if (world.getTime() % PATHFINDING_INTERVAL_TICKS != 0) {
            return;
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        // Process each settlement
        for (Settlement settlement : allSettlements) {
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Processes pathfinding for all assigned golems in a settlement.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        for (GolemData golemData : settlement.getGolems()) {
            // Only process assigned golems
            if (!golemData.isAssigned()) {
                continue;
            }
            
            UUID buildingId = golemData.getAssignedWallStationId();
            if (buildingId == null) {
                continue;
            }
            
            // Find the building
            Building building = settlement.getBuildings().stream()
                .filter(b -> b.getId().equals(buildingId))
                .findFirst()
                .orElse(null);
            
            if (building == null) {
                // Building was removed, unassign the golem
                SettlementsMod.LOGGER.warn("Wall station {} not found for assigned golem {}, unassigning", 
                    buildingId, golemData.getEntityId());
                golemData.setAssignedWallStationId(null);
                continue;
            }
            
            // Only pathfind to completed buildings
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Verify it's actually a wall station
            if (!WallStationDetector.isWallStation(building)) {
                SettlementsMod.LOGGER.warn("Building {} is not a wall station for assigned golem {}, unassigning", 
                    buildingId, golemData.getEntityId());
                golemData.setAssignedWallStationId(null);
                continue;
            }
            
            // Get the actual golem entity
            IronGolemEntity golem = getGolemEntity(world, golemData.getEntityId());
            if (golem == null) {
                continue; // Golem not loaded or doesn't exist
            }
            
            // Calculate target position (building center)
            BlockPos buildingPos = building.getPosition();
            BlockPos targetPos = findTargetPosition(buildingPos, world);
            
            // Calculate distance from golem to building center
            double distanceSq = golem.getPos().squaredDistanceTo(
                targetPos.getX() + 0.5, 
                targetPos.getY() + 0.5, 
                targetPos.getZ() + 0.5
            );
            
            // Always enforce radius - if golem is outside the work radius, pathfind back
            if (distanceSq > WALL_STATION_RADIUS_SQ) {
                // Golem is outside work radius, pathfind back to wall station
                boolean pathStarted = golem.getNavigation().startMovingTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    1.0 // Speed modifier (normal speed)
                );
                
                if (pathStarted) {
                    SettlementsMod.LOGGER.debug("Golem {} outside wall station radius ({} blocks), pathfinding back to wall station at {}", 
                        golemData.getEntityId(), Math.sqrt(distanceSq), targetPos);
                }
            }
        }
    }
    
    /**
     * Gets the IronGolemEntity from the world by UUID.
     */
    private static IronGolemEntity getGolemEntity(ServerWorld world, UUID entityId) {
        try {
            return (IronGolemEntity) world.getEntity(entityId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Finds a good target position near the wall station.
     * Returns the building position (center) for now.
     * Could be enhanced to find entrance or safe position.
     */
    private static BlockPos findTargetPosition(BlockPos buildingPos, ServerWorld world) {
        // For now, just use the building position
        // Could be enhanced to:
        // - Find a door/entrance
        // - Find a safe position (not in blocks, not in water)
        // - Find a position with air above it
        return buildingPos;
    }
}

