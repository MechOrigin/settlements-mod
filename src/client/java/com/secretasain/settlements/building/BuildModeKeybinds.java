package com.secretasain.settlements.building;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Manages keybinds for build mode controls.
 */
public class BuildModeKeybinds {
    // NOTE: We no longer use KeyBinding registration to avoid conflicts with shift key
    // Instead, we use direct GLFW key checks only when build mode is active
    // This prevents any interference with Minecraft's keybinding system
    
    // Track key states for direct GLFW checks (to avoid KeyBinding registration conflicts)
    private static boolean rWasPressedLastTick = false;
    private static boolean enterWasPressedLastTick = false;
    private static boolean spaceWasPressedLastTick = false;
    private static boolean xWasPressedLastTick = false;
    private static boolean upWasPressedLastTick = false;
    private static boolean downWasPressedLastTick = false;
    private static boolean leftWasPressedLastTick = false;
    private static boolean rightWasPressedLastTick = false;
    private static boolean escWasPressedLastTick = false;
    
    /**
     * Registers all build mode keybinds.
     * NOTE: Using direct GLFW key checks instead of KeyBinding registration
     * to avoid interfering with Minecraft's default keybindings (especially shift/sneak).
     */
    public static void register() {
        // NOTE: We're NOT registering KeyBindings to avoid conflicts with shift key
        // Instead, we'll use direct GLFW key checks only when build mode is active
        // This prevents any interference with Minecraft's keybinding system
        
        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(BuildModeKeybinds::onClientTick);
        
        SettlementsMod.LOGGER.info("Registered build mode key handlers (using direct GLFW checks)");
    }
    
    /**
     * Handles key presses each tick using direct GLFW checks.
     * IMPORTANT: This handler must do absolutely nothing when build mode is inactive
     * to avoid interfering with normal game input (especially shift key).
     * Using direct GLFW checks instead of KeyBinding to prevent conflicts.
     */
    private static void onClientTick(MinecraftClient client) {
        // CRITICAL: Only handle keys when build mode is active
        // Return immediately and do NOT check any keys or key states when inactive
        // This prevents any interference with normal game controls (especially shift key)
        if (!ClientBuildModeManager.isActive()) {
            // Reset all key state tracking when build mode is inactive
            rWasPressedLastTick = false;
            enterWasPressedLastTick = false;
            spaceWasPressedLastTick = false;
            xWasPressedLastTick = false;
            upWasPressedLastTick = false;
            downWasPressedLastTick = false;
            leftWasPressedLastTick = false;
            rightWasPressedLastTick = false;
            escWasPressedLastTick = false;
            return;
        }
        
        long windowHandle = client.getWindow().getHandle();
        
        // Handle rotation (R key - always clockwise) using direct GLFW check
        boolean rPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_R);
        if (rPressed && !rWasPressedLastTick) {
            ClientBuildModeManager.rotateClockwise();
            SettlementsMod.LOGGER.debug("Rotated structure clockwise");
        }
        rWasPressedLastTick = rPressed;
        
        // Handle movement keys - check them even if currentPos is null initially
        // This ensures keys work immediately when build mode is activated
        BlockPos currentPos = ClientBuildModeManager.getPlacementPos();
        int moveAmount = 1; // Always use 1-block movement
        
        // If position is null, initialize it at player position
        if (currentPos == null && client.player != null) {
            currentPos = client.player.getBlockPos();
            ClientBuildModeManager.setPlacementPos(currentPos);
        }
        
        // Now handle movement keys - currentPos should not be null at this point
        if (currentPos != null) {
            // Space - Move up
            boolean spacePressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_SPACE);
            if (spacePressed && !spaceWasPressedLastTick) {
                ClientBuildModeManager.setPlacementPos(currentPos.up(moveAmount));
            }
            spaceWasPressedLastTick = spacePressed;
            
            // X - Move down
            boolean xPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_X);
            if (xPressed && !xWasPressedLastTick) {
                ClientBuildModeManager.setPlacementPos(currentPos.down(moveAmount));
            }
            xWasPressedLastTick = xPressed;
            
            // Arrow keys - Move relative to player facing direction
            if (client.player != null) {
                net.minecraft.util.math.Direction facing = client.player.getHorizontalFacing();
                
                // Up arrow - Move forward
                boolean upPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_UP);
                if (upPressed && !upWasPressedLastTick) {
                    ClientBuildModeManager.setPlacementPos(currentPos.offset(facing, moveAmount));
                }
                upWasPressedLastTick = upPressed;
                
                // Down arrow - Move backward
                boolean downPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_DOWN);
                if (downPressed && !downWasPressedLastTick) {
                    ClientBuildModeManager.setPlacementPos(currentPos.offset(facing.getOpposite(), moveAmount));
                }
                downWasPressedLastTick = downPressed;
                
                // Left arrow - Move left
                boolean leftPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT);
                if (leftPressed && !leftWasPressedLastTick) {
                    ClientBuildModeManager.setPlacementPos(currentPos.offset(facing.rotateYCounterclockwise(), moveAmount));
                }
                leftWasPressedLastTick = leftPressed;
                
                // Right arrow - Move right
                boolean rightPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT);
                if (rightPressed && !rightWasPressedLastTick) {
                    ClientBuildModeManager.setPlacementPos(currentPos.offset(facing.rotateYClockwise(), moveAmount));
                }
                rightWasPressedLastTick = rightPressed;
            }
        }
        
        // Handle Enter key to confirm placement (only when no screen is open)
        boolean enterPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_ENTER);
        if (enterPressed && !enterWasPressedLastTick && client.currentScreen == null) {
            BlockPos placementPos = ClientBuildModeManager.getPlacementPos();
            StructureData structure = ClientBuildModeManager.getActiveStructure();
            if (placementPos != null && structure != null) {
                int rotation = ClientBuildModeManager.getRotation();
                com.secretasain.settlements.network.ConfirmPlacementPacketClient.send(placementPos, rotation);
                SettlementsMod.LOGGER.info("Sent placement confirmation for position {}", placementPos);
                // Don't deactivate here - wait for server confirmation
            }
        }
        enterWasPressedLastTick = enterPressed;
        
        // Handle Escape key to exit build mode (only when no screen is open)
        boolean escPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_ESCAPE);
        if (escPressed && !escWasPressedLastTick && client.currentScreen == null) {
            ClientBuildModeManager.deactivateBuildMode();
            SettlementsMod.LOGGER.info("Exited build mode");
        }
        escWasPressedLastTick = escPressed;
    }
}

