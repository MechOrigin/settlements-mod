package com.secretasain.settlements.building;

/**
 * Enum representing the status of a building in the construction process.
 */
public enum BuildingStatus {
    /**
     * Location is marked/reserved, waiting for materials.
     */
    RESERVED,
    
    /**
     * Materials are being consumed and blocks are being placed.
     */
    IN_PROGRESS,
    
    /**
     * Building is fully constructed.
     */
    COMPLETED,
    
    /**
     * Player cancelled the construction.
     */
    CANCELLED;
    
    /**
     * Checks if a status transition is valid.
     * @param from Current status
     * @param to Target status
     * @return true if transition is valid
     */
    public static boolean isValidTransition(BuildingStatus from, BuildingStatus to) {
        if (from == to) {
            return true; // No change is always valid
        }
        
        // Can't transition from COMPLETED to anything else
        if (from == COMPLETED) {
            return false;
        }
        
        // Can't transition from CANCELLED to anything else
        if (from == CANCELLED) {
            return false;
        }
        
        // Valid transitions:
        // RESERVED -> IN_PROGRESS (materials provided)
        // RESERVED -> CANCELLED (player cancels)
        // IN_PROGRESS -> COMPLETED (construction finished)
        // IN_PROGRESS -> CANCELLED (player cancels)
        
        if (from == RESERVED) {
            return to == IN_PROGRESS || to == CANCELLED;
        }
        
        if (from == IN_PROGRESS) {
            return to == COMPLETED || to == CANCELLED;
        }
        
        return false;
    }
}

