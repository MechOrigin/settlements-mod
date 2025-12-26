package com.secretasain.settlements.building;

import net.minecraft.util.math.BlockPos;

/**
 * Client-side manager for build mode state.
 * Tracks the active structure and placement position for preview rendering.
 */
public class ClientBuildModeManager {
    private static StructureData activeStructure;
    private static BlockPos placementPos;
    private static int rotation = 0;
    private static boolean isActive = false;
    
    /**
     * Activates build mode on the client.
     * @param structure The structure to preview
     */
    public static void activateBuildMode(StructureData structure) {
        activeStructure = structure;
        rotation = 0;
        isActive = true;
        
        // Set initial placement position at player's feet if available
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            // Set initial position at player's position (will be updated by raycast)
            BlockPos playerPos = client.player.getBlockPos();
            placementPos = playerPos.add(0, 0, 3); // Place 3 blocks in front of player initially
            
            // Show message to player
            client.player.sendMessage(
                net.minecraft.text.Text.literal("Build Mode: " + (structure != null ? structure.getName() : "Unknown")), 
                false
            );
        } else {
            placementPos = null; // Will be set by raycast
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Build mode activated on client. Structure: {}, Initial pos: {}", 
            structure != null ? structure.getName() : "null", placementPos);
    }
    
    /**
     * Deactivates build mode on the client.
     */
    public static void deactivateBuildMode() {
        activeStructure = null;
        placementPos = null;
        rotation = 0;
        isActive = false;
    }
    
    /**
     * Checks if build mode is active.
     * @return true if build mode is active
     */
    public static boolean isActive() {
        return isActive && activeStructure != null;
    }
    
    /**
     * Gets the active structure.
     * @return StructureData, or null if not active
     */
    public static StructureData getActiveStructure() {
        return activeStructure;
    }
    
    /**
     * Gets the current placement position.
     * @return BlockPos, or null if not set
     */
    public static BlockPos getPlacementPos() {
        return placementPos;
    }
    
    /**
     * Sets the placement position.
     * @param pos Placement position
     */
    public static void setPlacementPos(BlockPos pos) {
        placementPos = pos;
    }
    
    /**
     * Gets the current rotation (0, 90, 180, or 270).
     * @return Rotation in degrees
     */
    public static int getRotation() {
        return rotation;
    }
    
    /**
     * Sets the rotation.
     * @param rot Rotation in degrees (will be snapped to 90-degree increments)
     */
    public static void setRotation(int rot) {
        rotation = ((rot % 360) + 360) % 360; // Normalize to 0-359
        rotation = (rotation / 90) * 90; // Snap to 90-degree increments
    }
    
    /**
     * Rotates the structure by 90 degrees clockwise.
     */
    public static void rotateClockwise() {
        setRotation(rotation + 90);
    }
    
    /**
     * Rotates the structure by 90 degrees counter-clockwise.
     */
    public static void rotateCounterClockwise() {
        setRotation(rotation - 90);
    }
}

