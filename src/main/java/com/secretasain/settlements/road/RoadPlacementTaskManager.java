package com.secretasain.settlements.road;

import com.secretasain.settlements.settlement.Settlement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Manages road placement tasks for villagers.
 * Tracks active tasks and updates them each tick.
 */
public class RoadPlacementTaskManager {
    private static final Map<UUID, VillagerRoadPlacementTask> activeTasks = new HashMap<>();
    
    /**
     * Adds a new road placement task for a villager.
     * @param villagerId The villager's UUID
     * @param settlement The settlement
     * @param pathPositions List of positions to place paths
     */
    public static void addTask(UUID villagerId, Settlement settlement, List<BlockPos> pathPositions) {
        if (pathPositions == null || pathPositions.isEmpty()) {
            return;
        }
        
        VillagerRoadPlacementTask task = new VillagerRoadPlacementTask(villagerId, settlement, pathPositions);
        activeTasks.put(villagerId, task);
    }
    
    /**
     * Updates all active road placement tasks.
     * @param world The server world
     */
    public static void tick(ServerWorld world) {
        List<UUID> completedTasks = new ArrayList<>();
        
        for (Map.Entry<UUID, VillagerRoadPlacementTask> entry : activeTasks.entrySet()) {
            UUID villagerId = entry.getKey();
            VillagerRoadPlacementTask task = entry.getValue();
            
            if (task.tick(world)) {
                completedTasks.add(villagerId);
            }
        }
        
        // Remove completed tasks
        for (UUID villagerId : completedTasks) {
            activeTasks.remove(villagerId);
        }
    }
    
    /**
     * Checks if a villager has an active road placement task.
     */
    public static boolean hasTask(UUID villagerId) {
        return activeTasks.containsKey(villagerId);
    }
    
    /**
     * Removes a task for a villager.
     */
    public static void removeTask(UUID villagerId) {
        activeTasks.remove(villagerId);
    }
    
    /**
     * Gets the number of active tasks.
     */
    public static int getActiveTaskCount() {
        return activeTasks.size();
    }
}

