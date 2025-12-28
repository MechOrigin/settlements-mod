package com.secretasain.settlements.road;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.VillagerData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Main system for coordinating road placement by unassigned villagers.
 * Handles finding buildings without road connections and assigning road placement tasks.
 */
public class RoadPlacementSystem {
    private static final int MAX_ROAD_WORKERS = 2; // Maximum villagers working on roads at once
    private static final int MAX_PATH_DISTANCE = 64; // Maximum path distance from building
    
    /**
     * Checks if creative mode is enabled for the settlement.
     * Creative mode is enabled if any player in the world is in creative mode.
     * @param world The server world
     * @return true if creative mode is enabled
     */
    private static boolean isCreativeModeEnabled(ServerWorld world) {
        if (world == null) {
            return false;
        }
        
        // Check if any player in the world is in creative mode
        for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
            if (player.isCreative()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Processes road placement for a settlement.
     * Finds buildings without road connections and assigns unassigned villagers to place paths.
     * @param settlement The settlement to process
     * @param world The server world
     * @param server The Minecraft server
     */
    public static void processRoadPlacement(Settlement settlement, ServerWorld world, MinecraftServer server) {
        if (settlement == null || world == null || server == null) {
            SettlementsMod.LOGGER.debug("Road placement: Invalid parameters");
            return;
        }
        
        // Get all roads in settlement
        Set<BlockPos> roads = RoadDetector.findRoads(settlement, world);
        // TODO: Road placement is buggy - commented out logging
        // SettlementsMod.LOGGER.info("Road placement: Found {} existing road blocks", roads.size());
        
        // Find buildings without road connections
        List<Building> buildingsNeedingRoads = findBuildingsNeedingRoads(settlement, world, server, roads);
        if (buildingsNeedingRoads.isEmpty()) {
            // TODO: Road placement is buggy - commented out logging
        // SettlementsMod.LOGGER.info("Road placement: All buildings already have road connections");
            return; // All buildings have roads
        }
        
        // TODO: Road placement is buggy - commented out logging
        // SettlementsMod.LOGGER.info("Road placement: Found {} buildings needing roads", buildingsNeedingRoads.size());
        
        // Get unassigned villagers to assign road placement tasks
        // For road work, we allow any unassigned villagers (not just employed ones)
        List<VillagerData> unassignedVillagers = new ArrayList<>();
        List<VillagerData> allVillagers = settlement.getVillagers();
        
        // TODO: Road placement is buggy - commented out logging
        // SettlementsMod.LOGGER.info("Road placement: Checking {} total villagers in settlement", allVillagers.size());
        
        for (VillagerData villager : allVillagers) {
            boolean isEmployed = villager.isEmployed();
            boolean isAssigned = villager.isAssigned();
            
            // TODO: Road placement is buggy - commented out logging
            // SettlementsMod.LOGGER.debug("Road placement: Villager {} - employed: {}, assigned: {}", 
            //     villager.getEntityId(), isEmployed, isAssigned);
            
            if (!isAssigned) {
                unassignedVillagers.add(villager);
            }
        }
        
        if (unassignedVillagers.isEmpty()) {
            // TODO: Road placement is buggy - commented out logging
            // SettlementsMod.LOGGER.info("Road placement: No unassigned villagers available for road placement (all {} villagers are assigned)", 
            //     allVillagers.size());
            return; // Need villagers to place roads
        }
        
        // TODO: Road placement is buggy - commented out logging
        // SettlementsMod.LOGGER.info("Road placement: Found {} unassigned villagers for road placement (out of {} total)", 
        //     unassignedVillagers.size(), allVillagers.size());
        
        // Assign road placement tasks to villagers
        int tasksAssigned = 0;
        int villagerIndex = 0;
        
        for (Building building : buildingsNeedingRoads) {
            if (villagerIndex >= unassignedVillagers.size() || tasksAssigned >= MAX_ROAD_WORKERS) {
                break; // No more villagers available or max workers reached
            }
            
            // Skip if this villager already has a task
            VillagerData villager = unassignedVillagers.get(villagerIndex);
            if (RoadPlacementTaskManager.hasTask(villager.getEntityId())) {
                villagerIndex++;
                continue;
            }
            
            // Find doors in building
            List<BlockPos> doors = BuildingDoorDetector.findDoors(building, server);
            if (doors.isEmpty()) {
                // TODO: Road placement is buggy - commented out logging
                // SettlementsMod.LOGGER.info("Road placement: Building {} has no doors", building.getId());
                continue; // No doors found
            }
            
            // Find nearest road to first door
            BlockPos door = doors.get(0);
            BlockPos nearestRoad = RoadDetector.findNearestRoad(door, roads, MAX_PATH_DISTANCE * MAX_PATH_DISTANCE);
            
            if (nearestRoad == null) {
                // No road nearby - skip this building for now
                // We need at least one existing road to connect to
                // TODO: Road placement is buggy - commented out logging
                // SettlementsMod.LOGGER.info("Road placement: No nearby roads found for building {}, skipping (need existing road to connect to)", building.getId());
                villagerIndex++;
                continue;
            }
            
            // Calculate grid-based path from door to road
            List<BlockPos> path = GridPathCalculator.calculateGridPath(door, nearestRoad, world);
            if (path.isEmpty()) {
                // TODO: Road placement is buggy - commented out logging
                // SettlementsMod.LOGGER.info("Road placement: No valid grid path found from door {} to road {}", door, nearestRoad);
                villagerIndex++;
                continue; // No valid path found
            }
            
                // TODO: Road placement is buggy - commented out logging
                // SettlementsMod.LOGGER.info("Road placement: Calculated grid path of {} blocks from door {} to road {} for villager {}", 
                //     path.size(), door, nearestRoad, villager.getEntityId());
            
            // Assign task to villager
            RoadPlacementTaskManager.addTask(villager.getEntityId(), settlement, path);
            tasksAssigned++;
            villagerIndex++;
        }
        
        // TODO: Road placement is buggy - commented out logging
        // if (tasksAssigned > 0) {
        //     SettlementsMod.LOGGER.info("Road placement: Assigned {} road placement tasks to villagers", tasksAssigned);
        // }
    }
    
    /**
     * Finds buildings that need road connections.
     * @param settlement The settlement
     * @param world The server world
     * @param server The Minecraft server
     * @param roads Set of existing road positions
     * @return List of buildings needing roads
     */
    private static List<Building> findBuildingsNeedingRoads(Settlement settlement, ServerWorld world, 
                                                           MinecraftServer server, Set<BlockPos> roads) {
        List<Building> buildingsNeedingRoads = new ArrayList<>();
        
        for (Building building : settlement.getBuildings()) {
            // Only process completed buildings
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Find doors in building
            List<BlockPos> doors = BuildingDoorDetector.findDoors(building, server);
            if (doors.isEmpty()) {
                continue; // No doors, skip
            }
            
            // Check if any door is connected to a road
            // A door is considered "connected" if there's a road within 8 blocks AND a valid path can be calculated
            boolean hasRoadConnection = false;
            for (BlockPos door : doors) {
                BlockPos nearestRoad = RoadDetector.findNearestRoad(door, roads, 8.0 * 8.0); // Within 8 blocks
                if (nearestRoad != null) {
                    // Check if we can actually create a grid path to the road (not just that it exists nearby)
                    List<BlockPos> path = GridPathCalculator.calculateGridPath(door, nearestRoad, world);
                    if (!path.isEmpty() && path.size() <= 32) { // Path exists and is reasonable length (within 32 blocks)
                        hasRoadConnection = true;
                        // TODO: Road placement is buggy - commented out logging
                        // SettlementsMod.LOGGER.debug("Building {} has road connection via door {} (grid path length: {})", 
                        //     building.getId(), door, path.size());
                        break;
                    } else {
                        // TODO: Road placement is buggy - commented out logging
                        // SettlementsMod.LOGGER.debug("Building {} door {} has nearby road at {} but no valid grid path (path length: {})", 
                        //     building.getId(), door, nearestRoad, path != null ? path.size() : 0);
                    }
                }
            }
            
            if (!hasRoadConnection) {
                // TODO: Road placement is buggy - commented out logging
                // SettlementsMod.LOGGER.info("Building {} needs road connection (doors: {}, roads found: {})", 
                //     building.getId(), doors.size(), roads.size());
                buildingsNeedingRoads.add(building);
            }
        }
        
        return buildingsNeedingRoads;
    }
    
    
}

