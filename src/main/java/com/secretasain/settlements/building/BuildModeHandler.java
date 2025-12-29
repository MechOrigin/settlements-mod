package com.secretasain.settlements.building;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Handles build mode state for a player.
 * Manages structure selection, placement position, and rotation.
 */
public class BuildModeHandler {
    private BuildMode currentState;
    private StructureData selectedStructure;
    private BlockPos placementPos;
    private int rotation; // 0, 90, 180, 270 degrees
    private final UUID playerId;
    private UUID settlementId; // Settlement ID for the settlement whose screen was open when build mode was activated
    
    public BuildModeHandler(UUID playerId) {
        this.playerId = playerId;
        this.currentState = BuildMode.INACTIVE;
        this.rotation = 0;
        this.settlementId = null;
    }
    
    /**
     * Activates build mode with a selected structure.
     * @param structure The structure to place
     * @param settlementId The settlement ID for the settlement whose screen was open when build mode was activated
     */
    public void activateBuildMode(StructureData structure, UUID settlementId) {
        this.selectedStructure = structure;
        this.currentState = BuildMode.PLACEMENT;
        this.rotation = 0;
        this.settlementId = settlementId;
    }
    
    /**
     * Gets the settlement ID for this build mode session.
     * @return Settlement ID, or null if not set
     */
    public UUID getSettlementId() {
        return settlementId;
    }
    
    /**
     * Deactivates build mode.
     */
    public void deactivateBuildMode() {
        this.currentState = BuildMode.INACTIVE;
        this.selectedStructure = null;
        this.placementPos = null;
        this.rotation = 0;
        this.settlementId = null;
    }
    
    /**
     * Checks if build mode is active.
     * @return true if build mode is active
     */
    public boolean isActive() {
        return currentState != BuildMode.INACTIVE;
    }
    
    /**
     * Gets the current build mode state.
     * @return Current BuildMode
     */
    public BuildMode getCurrentState() {
        return currentState;
    }
    
    /**
     * Sets the current build mode state.
     * @param state New state
     */
    public void setCurrentState(BuildMode state) {
        this.currentState = state;
    }
    
    /**
     * Gets the selected structure.
     * @return Selected StructureData, or null if none
     */
    public StructureData getSelectedStructure() {
        return selectedStructure;
    }
    
    /**
     * Gets the placement position.
     * @return Placement BlockPos, or null if not set
     */
    public BlockPos getPlacementPos() {
        return placementPos;
    }
    
    /**
     * Sets the placement position.
     * @param pos Placement position
     */
    public void setPlacementPos(BlockPos pos) {
        this.placementPos = pos;
    }
    
    /**
     * Gets the current rotation (0, 90, 180, or 270).
     * @return Rotation in degrees
     */
    public int getRotation() {
        return rotation;
    }
    
    /**
     * Sets the rotation.
     * @param rotation Rotation in degrees (0, 90, 180, or 270)
     */
    public void setRotation(int rotation) {
        this.rotation = ((rotation % 360) + 360) % 360; // Normalize to 0-359
        // Snap to 90-degree increments
        this.rotation = (this.rotation / 90) * 90;
    }
    
    /**
     * Rotates the structure by 90 degrees clockwise.
     */
    public void rotateClockwise() {
        setRotation(rotation + 90);
    }
    
    /**
     * Rotates the structure by 90 degrees counter-clockwise.
     */
    public void rotateCounterClockwise() {
        setRotation(rotation - 90);
    }
    
    /**
     * Gets the player ID this handler belongs to.
     * @return Player UUID
     */
    public UUID getPlayerId() {
        return playerId;
    }
}

