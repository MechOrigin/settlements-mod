package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;

import java.util.*;

/**
 * Manages work assignments for villagers in settlements.
 * Handles assigning villagers to buildings/workstations.
 */
public class WorkAssignmentManager {
    
    /**
     * Assigns a villager to a building.
     * @param settlement The settlement
     * @param villagerId The villager's entity UUID
     * @param buildingId The building's UUID
     * @return true if assignment was successful, false otherwise
     */
    public static boolean assignVillagerToBuilding(Settlement settlement, UUID villagerId, UUID buildingId) {
        if (settlement == null) {
            SettlementsMod.LOGGER.warn("Cannot assign villager: settlement is null");
            return false;
        }
        
        // Find the villager
        VillagerData villager = settlement.getVillagers().stream()
            .filter(v -> v.getEntityId().equals(villagerId))
            .findFirst()
            .orElse(null);
        
        if (villager == null) {
            SettlementsMod.LOGGER.warn("Cannot assign villager: villager {} not found", villagerId);
            return false;
        }
        
        // Check if villager is employed
        if (!villager.isEmployed()) {
            SettlementsMod.LOGGER.warn("Cannot assign villager: villager {} is not employed", villagerId);
            return false;
        }
        
        // Find the building
        Building building = settlement.getBuildings().stream()
            .filter(b -> b.getId().equals(buildingId))
            .findFirst()
            .orElse(null);
        
        if (building == null) {
            SettlementsMod.LOGGER.warn("Cannot assign villager: building {} not found", buildingId);
            return false;
        }
        
        // Check if building is completed
        if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
            SettlementsMod.LOGGER.warn("Cannot assign villager: building {} is not completed", buildingId);
            return false;
        }
        
        // Unassign villager from previous building if assigned
        if (villager.isAssigned()) {
            unassignVillager(settlement, villagerId);
        }
        
        // Assign to new building
        villager.setAssignedBuildingId(buildingId);
        SettlementsMod.LOGGER.info("Assigned villager {} to building {}", villagerId, buildingId);
        return true;
    }
    
    /**
     * Unassigns a villager from their current building.
     * @param settlement The settlement
     * @param villagerId The villager's entity UUID
     * @return true if unassignment was successful, false otherwise
     */
    public static boolean unassignVillager(Settlement settlement, UUID villagerId) {
        if (settlement == null) {
            return false;
        }
        
        VillagerData villager = settlement.getVillagers().stream()
            .filter(v -> v.getEntityId().equals(villagerId))
            .findFirst()
            .orElse(null);
        
        if (villager == null || !villager.isAssigned()) {
            return false;
        }
        
        UUID oldBuildingId = villager.getAssignedBuildingId();
        villager.setAssignedBuildingId(null);
        SettlementsMod.LOGGER.info("Unassigned villager {} from building {}", villagerId, oldBuildingId);
        return true;
    }
    
    /**
     * Gets all villagers assigned to a specific building.
     * @param settlement The settlement
     * @param buildingId The building's UUID
     * @return List of villagers assigned to the building
     */
    public static List<VillagerData> getVillagersAssignedToBuilding(Settlement settlement, UUID buildingId) {
        if (settlement == null) {
            return Collections.emptyList();
        }
        
        List<VillagerData> assigned = new ArrayList<>();
        for (VillagerData villager : settlement.getVillagers()) {
            if (villager.isAssigned() && villager.getAssignedBuildingId().equals(buildingId)) {
                assigned.add(villager);
            }
        }
        return assigned;
    }
    
    /**
     * Gets all available buildings that can accept workers.
     * Only returns COMPLETED buildings.
     * @param settlement The settlement
     * @return List of available buildings
     */
    public static List<Building> getAvailableBuildings(Settlement settlement) {
        if (settlement == null) {
            return Collections.emptyList();
        }
        
        List<Building> available = new ArrayList<>();
        for (Building building : settlement.getBuildings()) {
            if (building.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                available.add(building);
            }
        }
        return available;
    }
    
    /**
     * Gets all unassigned employed villagers.
     * @param settlement The settlement
     * @return List of unassigned employed villagers
     */
    public static List<VillagerData> getUnassignedEmployedVillagers(Settlement settlement) {
        if (settlement == null) {
            return Collections.emptyList();
        }
        
        List<VillagerData> unassigned = new ArrayList<>();
        for (VillagerData villager : settlement.getVillagers()) {
            if (villager.isEmployed() && !villager.isAssigned()) {
                unassigned.add(villager);
            }
        }
        return unassigned;
    }
}

