package com.secretasain.settlements.building;

/**
 * Enum representing the different states of build mode.
 */
public enum BuildMode {
    /**
     * Build mode is not active.
     */
    INACTIVE,
    
    /**
     * Selecting structure type.
     */
    SELECTION,
    
    /**
     * Positioning structure in world.
     */
    PLACEMENT,
    
    /**
     * Rotating structure.
     */
    ROTATION,
    
    /**
     * Adjusting structure position.
     */
    ADJUSTMENT,
    
    /**
     * Confirming placement.
     */
    CONFIRMATION
}

