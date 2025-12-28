package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;

import java.util.*;

/**
 * Manages golem assignments to wall stations in settlements.
 * Handles assigning golems to wall buildings.
 */
public class GolemAssignmentManager {
    
    /**
     * Assigns a golem to a wall station.
     * @param settlement The settlement
     * @param golemId The golem's entity UUID
     * @param buildingId The wall building's UUID
     * @return true if assignment was successful, false otherwise
     */
    public static boolean assignGolemToWallStation(Settlement settlement, UUID golemId, UUID buildingId) {
        if (settlement == null) {
            SettlementsMod.LOGGER.warn("Cannot assign golem: settlement is null");
            return false;
        }
        
        // Find the golem
        GolemData golem = settlement.getGolems().stream()
            .filter(g -> g.getEntityId().equals(golemId))
            .findFirst()
            .orElse(null);
        
        if (golem == null) {
            SettlementsMod.LOGGER.warn("Cannot assign golem: golem {} not found", golemId);
            return false;
        }
        
        // Find the building
        Building building = settlement.getBuildings().stream()
            .filter(b -> b.getId().equals(buildingId))
            .findFirst()
            .orElse(null);
        
        if (building == null) {
            SettlementsMod.LOGGER.warn("Cannot assign golem: building {} not found", buildingId);
            return false;
        }
        
        // Check if building is a wall station
        if (!WallStationDetector.isWallStation(building)) {
            SettlementsMod.LOGGER.warn("Cannot assign golem: building {} is not a wall station", buildingId);
            return false;
        }
        
        // Check if building is completed
        if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
            SettlementsMod.LOGGER.warn("Cannot assign golem: building {} is not completed", buildingId);
            return false;
        }
        
        // Unassign golem from previous wall station if assigned
        if (golem.isAssigned()) {
            unassignGolem(settlement, golemId);
        }
        
        // Assign to new wall station
        golem.setAssignedWallStationId(buildingId);
        SettlementsMod.LOGGER.info("Assigned golem {} to wall station {}", golemId, buildingId);
        return true;
    }
    
    /**
     * Unassigns a golem from their current wall station.
     * @param settlement The settlement
     * @param golemId The golem's entity UUID
     * @return true if unassignment was successful, false otherwise
     */
    public static boolean unassignGolem(Settlement settlement, UUID golemId) {
        if (settlement == null) {
            return false;
        }
        
        GolemData golem = settlement.getGolems().stream()
            .filter(g -> g.getEntityId().equals(golemId))
            .findFirst()
            .orElse(null);
        
        if (golem == null || !golem.isAssigned()) {
            return false;
        }
        
        UUID oldBuildingId = golem.getAssignedWallStationId();
        golem.setAssignedWallStationId(null);
        SettlementsMod.LOGGER.info("Unassigned golem {} from wall station {}", golemId, oldBuildingId);
        return true;
    }
    
    /**
     * Gets all golems assigned to a specific wall station.
     * @param settlement The settlement
     * @param buildingId The wall building's UUID
     * @return List of golems assigned to the wall station
     */
    public static List<GolemData> getGolemsAssignedToWallStation(Settlement settlement, UUID buildingId) {
        if (settlement == null) {
            return Collections.emptyList();
        }
        
        List<GolemData> assigned = new ArrayList<>();
        for (GolemData golem : settlement.getGolems()) {
            if (golem.isAssigned() && golem.getAssignedWallStationId().equals(buildingId)) {
                assigned.add(golem);
            }
        }
        return assigned;
    }
    
    /**
     * Gets all available wall stations that can accept golems.
     * Only returns COMPLETED wall buildings.
     * @param settlement The settlement
     * @return List of available wall stations
     */
    public static List<Building> getAvailableWallStations(Settlement settlement) {
        if (settlement == null) {
            return Collections.emptyList();
        }
        
        return WallStationDetector.findWallStations(settlement, null);
    }
    
    /**
     * Gets all unassigned golems.
     * @param settlement The settlement
     * @return List of unassigned golems
     */
    public static List<GolemData> getUnassignedGolems(Settlement settlement) {
        if (settlement == null) {
            return Collections.emptyList();
        }
        
        List<GolemData> unassigned = new ArrayList<>();
        for (GolemData golem : settlement.getGolems()) {
            if (!golem.isAssigned()) {
                unassigned.add(golem);
            }
        }
        return unassigned;
    }
}

