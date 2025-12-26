package com.secretasain.settlements.building;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Handles rendering hooks for build mode preview.
 */
public class BuildModeRenderer {
    
    /**
     * Registers render hooks for build mode.
     */
    public static void register() {
        // Register world render hook for preview blocks
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BuildModeRenderer::renderPreview);
        
        // Register tick handler for position tracking
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(BuildModeRenderer::updatePlacementPosition);
    }
    
    /**
     * Renders the structure preview.
     */
    private static void renderPreview(WorldRenderContext context) {
        if (!ClientBuildModeManager.isActive()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        
        Camera camera = context.camera();
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = camera.getPos();
        
        BuildModePreviewRenderer.render(client, matrices, cameraPos);
    }
    
    /**
     * Updates the placement position based on player crosshair/raycast.
     */
    private static void updatePlacementPosition(MinecraftClient client) {
        if (!ClientBuildModeManager.isActive()) {
            return;
        }
        
        if (client.player == null || client.world == null) {
            return;
        }
        
        // Perform raycast to find target block
        HitResult hitResult = client.player.raycast(20.0, 0.0f, true);
        
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockPos hitPos = blockHit.getBlockPos();
            
            // Place structure adjacent to the hit block (on the face that was hit)
            BlockPos placementPos = hitPos.offset(blockHit.getSide());
            
            // Only update if position changed (to avoid constant updates)
            BlockPos currentPos = ClientBuildModeManager.getPlacementPos();
            if (currentPos == null || !currentPos.equals(placementPos)) {
                ClientBuildModeManager.setPlacementPos(placementPos);
            }
        }
    }
}

