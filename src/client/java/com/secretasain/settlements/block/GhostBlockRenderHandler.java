package com.secretasain.settlements.block;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Handles registration and rendering of ghost blocks in the world.
 */
public class GhostBlockRenderHandler {
    
    /**
     * Registers the ghost block renderer with Fabric's world render events.
     * Should be called during client initialization.
     */
    public static void register() {
        // Register to render after translucent blocks
        // TEMPORARY: Disabled to test block entity renderer instead
        // WorldRenderEvents.AFTER_TRANSLUCENT.register(GhostBlockRenderHandler::renderGhostBlocks);
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockRenderHandler: Registered (currently disabled - using block entity renderer)");
    }
    
    /**
     * Renders all ghost blocks during world rendering.
     * @param context The world render context
     */
    private static void renderGhostBlocks(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        
        GhostBlockManager manager = GhostBlockManager.getInstance();
        
        if (manager.isEmpty()) {
            // DEBUG: Log when we have no blocks (only occasionally)
            if (com.secretasain.settlements.SettlementsMod.LOGGER.isDebugEnabled() && Math.random() < 0.01) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockRenderHandler: No ghost blocks to render");
            }
            return;
        }
        
        // DEBUG: Log ghost block count (only occasionally to avoid spam)
        // Removed the INFO level spam - it's too verbose
        
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        
        // CRITICAL: Render ghost blocks as fully opaque, exactly like real blocks
        // No blend mode, no transparency - they should look exactly like the NBT blocks
        // Depth test is enabled so they render correctly with the world
        // They're still walk-throughable because GhostBlock.getCollisionShape() returns empty
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        // CRITICAL FIX: Depth test is enabled, which should prevent clipping
        // The default depth function (LESS) should work correctly for opaque blocks
        
        // Set default shader to solid (will be overridden per-block if needed)
        // Set shader color to fully opaque (no transparency)
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> 
            net.minecraft.client.render.GameRenderer.getRenderTypeSolidProgram());
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        try {
            // Render all ghost blocks (they will use manageBlendState=false for batch rendering)
            // Blocks are rendered fully opaque, exactly like real blocks
            manager.renderAll(matrices, cameraPos, immediate);
            
            // CRITICAL: Draw the buffers BEFORE resetting shader color
            immediate.draw();
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.error("Error rendering ghost blocks", e);
            e.printStackTrace();
        } finally {
            // Reset shader color after rendering
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}

