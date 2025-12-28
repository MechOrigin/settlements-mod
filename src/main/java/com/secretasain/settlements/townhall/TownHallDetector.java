package com.secretasain.settlements.townhall;

import com.secretasain.settlements.settlement.Building;
import net.minecraft.util.Identifier;

/**
 * Utility class for detecting town hall buildings.
 */
public class TownHallDetector {
    /**
     * Checks if a building is a town hall.
     * @param building The building to check
     * @return true if the building is a town hall
     */
    public static boolean isTownHall(Building building) {
        if (building == null || building.getStructureType() == null) {
            return false;
        }
        
        String structurePath = building.getStructureType().getPath();
        // Check if structure name contains "town_hall" or "townhall"
        return structurePath.contains("town_hall") || structurePath.contains("townhall");
    }
    
    /**
     * Checks if a structure type identifier represents a town hall.
     * @param structureType The structure type identifier
     * @return true if the structure is a town hall
     */
    public static boolean isTownHall(Identifier structureType) {
        if (structureType == null) {
            return false;
        }
        
        String structurePath = structureType.getPath();
        return structurePath.contains("town_hall") || structurePath.contains("townhall");
    }
}

