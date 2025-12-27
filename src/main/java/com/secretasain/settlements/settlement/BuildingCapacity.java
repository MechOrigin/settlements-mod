package com.secretasain.settlements.settlement;

import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Manages building capacity - how many villagers can be assigned to each building type.
 */
public class BuildingCapacity {
    
    /**
     * Gets the maximum number of villagers that can be assigned to a building.
     * @param structureType The structure type identifier
     * @return Maximum capacity (default: 1)
     */
    public static int getCapacity(Identifier structureType) {
        if (structureType == null) {
            return 1; // Default capacity
        }
        
        String structureName = structureType.getPath();
        if (structureName.contains("/")) {
            structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
        }
        if (structureName.endsWith(".nbt")) {
            structureName = structureName.substring(0, structureName.length() - 4);
        }
        structureName = structureName.toLowerCase();
        
        // Determine capacity based on structure name
        if (structureName.contains("wall") || structureName.contains("fence") || structureName.contains("gate")) {
            return 1; // Walls, fences, gates: 1 villager
        } else if (structureName.contains("smithing") || structureName.contains("smith")) {
            return 3; // Smithing buildings: 3 villagers
        } else if (structureName.contains("farm") || structureName.contains("farmland")) {
            return 2; // Farms: 2 villagers
        } else if (structureName.contains("cartographer") || structureName.contains("cartography")) {
            return 2; // Cartographer buildings: 2 villagers
        } else if (structureName.contains("house")) {
            return 1; // Houses: 1 villager (housing assignment)
        } else {
            return 1; // Default: 1 villager
        }
    }
    
    /**
     * Checks if a building can accept more villagers.
     * @param settlement The settlement
     * @param buildingId The building's UUID
     * @return true if building has capacity for more villagers
     */
    public static boolean canAcceptMoreVillagers(Settlement settlement, UUID buildingId) {
        if (settlement == null || buildingId == null) {
            return false;
        }
        
        Building building = settlement.getBuildings().stream()
            .filter(b -> b.getId().equals(buildingId))
            .findFirst()
            .orElse(null);
        
        if (building == null) {
            return false;
        }
        
        int capacity = getCapacity(building.getStructureType());
        int assignedCount = WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, buildingId).size();
        
        return assignedCount < capacity;
    }
    
    /**
     * Gets the number of available slots in a building.
     * @param settlement The settlement
     * @param buildingId The building's UUID
     * @return Number of available slots (0 if full or invalid)
     */
    public static int getAvailableSlots(Settlement settlement, UUID buildingId) {
        if (settlement == null || buildingId == null) {
            return 0;
        }
        
        Building building = settlement.getBuildings().stream()
            .filter(b -> b.getId().equals(buildingId))
            .findFirst()
            .orElse(null);
        
        if (building == null) {
            return 0;
        }
        
        int capacity = getCapacity(building.getStructureType());
        int assignedCount = WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, buildingId).size();
        
        return Math.max(0, capacity - assignedCount);
    }
}

