package com.secretasain.settlements.settlement;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for detecting wall buildings (wall stations) in settlements.
 */
public class WallStationDetector {
    
    /**
     * Finds all wall buildings in a settlement.
     * A building is considered a wall if its structure type name contains "wall".
     * @param settlement The settlement to search
     * @param world The server world (for additional validation if needed)
     * @return List of wall building IDs and positions
     */
    public static List<Building> findWallStations(Settlement settlement, ServerWorld world) {
        List<Building> wallStations = new ArrayList<>();
        
        if (settlement == null) {
            return wallStations;
        }
        
        for (Building building : settlement.getBuildings()) {
            // Check if building is completed (only completed buildings can be wall stations)
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Check if structure type name contains "wall" (case-insensitive)
            Identifier structureType = building.getStructureType();
            if (structureType != null) {
                String structureName = structureType.getPath().toLowerCase();
                if (structureName.contains("wall")) {
                    wallStations.add(building);
                }
            }
        }
        
        return wallStations;
    }
    
    /**
     * Checks if a building is a wall station.
     * @param building The building to check
     * @return true if the building is a wall station
     */
    public static boolean isWallStation(Building building) {
        if (building == null) {
            return false;
        }
        
        // Must be completed
        if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
            return false;
        }
        
        // Check structure type name
        Identifier structureType = building.getStructureType();
        if (structureType != null) {
            String structureName = structureType.getPath().toLowerCase();
            return structureName.contains("wall");
        }
        
        return false;
    }
}

