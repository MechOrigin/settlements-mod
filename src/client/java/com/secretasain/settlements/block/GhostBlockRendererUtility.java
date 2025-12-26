package com.secretasain.settlements.block;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

/**
 * General-purpose utility for rendering any block as a ghost block.
 * Can be used for structure previews, block placement previews, or any other visualization needs.
 * By default, blocks are rendered fully opaque (alpha 1.0f) to match the NBT structure exactly.
 */
public class GhostBlockRendererUtility {
    // Default to fully opaque (1.0f) so blocks render exactly like the NBT shows
    private static final float DEFAULT_ALPHA = 1.0f;
    
    /**
     * Renders a single block as a ghost block at the specified position.
     * Uses the actual block model with transparency applied.
     * 
     * @param pos The world position to render the block at
     * @param state The block state to render
     * @param matrices The matrix stack for rendering
     * @param vertexConsumers The vertex consumer provider
     * @param alpha The transparency level (0.0 to 1.0, where 0.0 is fully transparent)
     * @param lightLevel The light level to use (use LightmapTextureManager.MAX_LIGHT_COORDINATE for full brightness)
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderGhostBlock(BlockPos pos, BlockState state, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, float alpha, int lightLevel) {
        return renderGhostBlock(pos, state, matrices, vertexConsumers, alpha, lightLevel, true);
    }
    
    /**
     * Renders a single block as a ghost block at the specified position.
     * Uses the actual block model with transparency applied.
     * 
     * @param pos The world position to render the block at
     * @param state The block state to render
     * @param matrices The matrix stack for rendering
     * @param vertexConsumers The vertex consumer provider
     * @param alpha The transparency level (0.0 to 1.0, where 0.0 is fully transparent)
     * @param lightLevel The light level to use
     * @param manageBlendState If true, manages blend state (for single block rendering). If false, assumes blend state is already set (for batch rendering).
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderGhostBlock(BlockPos pos, BlockState state, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, float alpha, int lightLevel, 
                                          boolean manageBlendState) {
        if (state == null || state.isAir()) {
            return false;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getBlockRenderManager() == null) {
            return false;
        }
        
        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        
        // CRITICAL: Render ghost blocks as fully opaque, exactly like real blocks
        // No blend mode, no transparency - they should look exactly like the NBT blocks
        // Depth test is enabled so they render correctly with the world
        if (manageBlendState) {
            // For single block rendering, ensure depth test is enabled
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            // CRITICAL FIX: Depth test is already enabled, which should prevent clipping
            // The default depth function (LESS) should work correctly
        }
        
        // CRITICAL: Get the render layer first to determine which shader to use
        net.minecraft.client.render.RenderLayer renderLayer = net.minecraft.client.render.RenderLayers.getMovingBlockLayer(state);
        
        // CRITICAL: Set the shader program based on the render layer
        // The render layer determines whether we need solid, cutout, or translucent shader
        if (manageBlendState) {
            // For single block rendering, set shader based on render layer
            if (renderLayer == net.minecraft.client.render.RenderLayer.getSolid()) {
                RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeSolidProgram());
            } else if (renderLayer == net.minecraft.client.render.RenderLayer.getCutout()) {
                RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeCutoutProgram());
            } else if (renderLayer == net.minecraft.client.render.RenderLayer.getCutoutMipped()) {
                RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeCutoutMippedProgram());
            } else {
                // Default to solid for other layers
                RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeSolidProgram());
            }
        }
        
        // Always set shader color to fully opaque (no transparency)
        // Ghost blocks should render exactly like the NBT blocks
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        try {
            matrices.push();
            
            // CRITICAL: When called from BlockEntityRenderer, matrices are ALREADY at the block position (0,0,0 relative to block)
            // When called from world render event, we need to translate to the block position
            // renderBlockAsEntity automatically centers the block at (0.5, 0.5, 0.5) relative to current translation
            // So we just need to ensure we're at the right position
            if (manageBlendState) {
                // Called from BlockEntityRenderer - matrices are already at block position (0,0,0 relative to block)
                // renderBlockAsEntity will center it automatically, so we don't need to translate
                // Just leave matrices at current position (already at block)
            } else {
                // Called from world render event - need to translate to block position first
                // renderBlockAsEntity will center it automatically
                matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            }
            
            // Use the render layer directly - blocks will render fully opaque
            // Ghost blocks should render exactly like the NBT blocks
            VertexConsumerProvider solidProvider = (requestedRenderType) -> {
                // Use the moving block layer for the block state
                return vertexConsumers.getBuffer(renderLayer);
            };
            
            // DEBUG: Log rendering attempt
            if (com.secretasain.settlements.SettlementsMod.LOGGER.isDebugEnabled() && Math.random() < 0.01) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockRendererUtility: Rendering block {} at {} with layer {} (manageBlendState={})", 
                    net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()), pos, renderLayer, manageBlendState);
            }
            
            // Render the block model using BlockRenderManager
            // This renders the actual block texture/model exactly like a real block
            // CRITICAL: renderBlockAsEntity expects the block centered at (0.5, 0.5, 0.5) relative to translation
            // We've already translated to center, so renderBlockAsEntity will render correctly
            blockRenderer.renderBlockAsEntity(
                state,
                matrices,
                solidProvider,
                lightLevel,
                OverlayTexture.DEFAULT_UV
            );
            
            matrices.pop();
            
            return true;
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.error("Failed to render ghost block at {}: {}", pos, e.getMessage(), e);
            return false;
        } finally {
            // Reset shader color (always) - but only if we're managing blend state
            // If not managing blend state, keep the shader color set for batch rendering
            if (manageBlendState) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }
    
    /**
     * Renders a single block as a ghost block with default alpha (1.0, fully opaque).
     * 
     * @param pos The world position to render the block at
     * @param state The block state to render
     * @param matrices The matrix stack for rendering
     * @param vertexConsumers The vertex consumer provider
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderGhostBlock(BlockPos pos, BlockState state, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers) {
        return renderGhostBlock(pos, state, matrices, vertexConsumers, DEFAULT_ALPHA, 
                               LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }
    
    /**
     * Renders a single block as a ghost block with custom alpha and world light level.
     * Automatically calculates light level from the world at the block position.
     * 
     * @param pos The world position to render the block at
     * @param state The block state to render
     * @param matrices The matrix stack for rendering
     * @param vertexConsumers The vertex consumer provider
     * @param alpha The transparency level (0.0 to 1.0)
     * @param useWorldLight If true, uses world light level; if false, uses max light
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderGhostBlock(BlockPos pos, BlockState state, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, float alpha, boolean useWorldLight) {
        int lightLevel = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        
        if (useWorldLight) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                int blockLight = client.world.getLightLevel(pos);
                int skyLight = client.world.getLightLevel(pos.up());
                lightLevel = LightmapTextureManager.pack(blockLight, skyLight);
            }
        }
        
        return renderGhostBlock(pos, state, matrices, vertexConsumers, alpha, lightLevel);
    }
    
    /**
     * Renders a single block as a ghost block with color tinting.
     * Useful for showing valid/invalid placement states.
     * 
     * @param pos The world position to render the block at
     * @param state The block state to render
     * @param matrices The matrix stack for rendering
     * @param vertexConsumers The vertex consumer provider
     * @param alpha The transparency level (0.0 to 1.0)
     * @param redTint Red tint multiplier (0.0 to 1.0)
     * @param greenTint Green tint multiplier (0.0 to 1.0)
     * @param blueTint Blue tint multiplier (0.0 to 1.0)
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderGhostBlockTinted(BlockPos pos, BlockState state, MatrixStack matrices,
                                                 VertexConsumerProvider vertexConsumers, float alpha,
                                                 float redTint, float greenTint, float blueTint) {
        return renderGhostBlockTinted(pos, state, matrices, vertexConsumers, alpha, redTint, greenTint, blueTint, true);
    }
    
    /**
     * Renders a single block as a ghost block with color tinting.
     * Useful for showing valid/invalid placement states.
     * 
     * @param pos The world position to render the block at
     * @param state The block state to render
     * @param matrices The matrix stack for rendering
     * @param vertexConsumers The vertex consumer provider
     * @param alpha The transparency level (0.0 to 1.0)
     * @param redTint Red tint multiplier (0.0 to 1.0)
     * @param greenTint Green tint multiplier (0.0 to 1.0)
     * @param blueTint Blue tint multiplier (0.0 to 1.0)
     * @param manageBlendState If true, manages blend state (for single block rendering). If false, assumes blend state is already set (for batch rendering).
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderGhostBlockTinted(BlockPos pos, BlockState state, MatrixStack matrices,
                                                 VertexConsumerProvider vertexConsumers, float alpha,
                                                 float redTint, float greenTint, float blueTint, boolean manageBlendState) {
        if (state == null || state.isAir()) {
            return false;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getBlockRenderManager() == null) {
            return false;
        }
        
        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        
        // CRITICAL: Render ghost blocks as fully opaque, exactly like real blocks
        // No blend mode, no transparency - they should look exactly like the NBT blocks
        // Depth test is enabled so they render correctly with the world
        if (manageBlendState) {
            // For single block rendering, ensure depth test is enabled
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            // Don't set shader program - let the render layer determine it
            // This ensures blocks render correctly based on their type
        }
        
        // Set shader color with tinting but fully opaque (alpha = 1.0)
        // Ghost blocks should render exactly like the NBT blocks
        RenderSystem.setShaderColor(redTint, greenTint, blueTint, 1.0f);
        
        try {
            matrices.push();
            // renderBlockAsEntity automatically centers the block at (0.5, 0.5, 0.5) relative to the translation
            // So we just translate to the block position
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            
            // CRITICAL: Use getMovingBlockLayer() which is the standard way to render blocks as entities
            net.minecraft.client.render.RenderLayer renderLayer = net.minecraft.client.render.RenderLayers.getMovingBlockLayer(state);
            
            // Use the render layer directly with tinting but no alpha modification
            // Ghost blocks should render exactly like the NBT blocks (fully opaque)
            VertexConsumerProvider tintProvider = (requestedRenderType) -> {
                // Use the moving block layer for the block state
                VertexConsumer buffer = vertexConsumers.getBuffer(renderLayer);
                // Apply color tinting but keep fully opaque
                return new TintVertexConsumer(buffer, redTint, greenTint, blueTint);
            };
            
            blockRenderer.renderBlockAsEntity(
                state,
                matrices,
                tintProvider,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV
            );
            
            matrices.pop();
            
            return true;
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Failed to render tinted ghost block at {}: {}", pos, e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Reset shader color if we're managing blend state
            if (manageBlendState) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }
    
    /**
     * Vertex consumer wrapper that applies alpha to all vertices.
     */
    private static class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;
        
        public AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }
        
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return delegate.vertex(x, y, z);
        }
        
        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            // Apply alpha multiplier to the vertex color
            // Formula: newAlpha = incomingAlpha * ourAlpha
            // With alpha 1.0f (fully opaque), this preserves the original alpha value
            // With alpha 0.5f, this would make blocks semi-transparent (255 * 0.5 = 127)
            int newAlpha = Math.max(1, Math.min(255, Math.round(alpha * this.alpha)));
            // Use max(1, ...) to ensure alpha is never 0 (which would make it invisible)
            // DEBUG: Log if we're actually being called (only in debug mode to avoid spam)
            if (com.secretasain.settlements.SettlementsMod.LOGGER.isDebugEnabled()) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("AlphaVertexConsumer.color() called: incoming alpha={}, our alpha={}, new alpha={}", 
                    alpha, this.alpha, newAlpha);
            }
            return delegate.color(red, green, blue, newAlpha);
        }
        
        @Override
        public VertexConsumer texture(float u, float v) {
            return delegate.texture(u, v);
        }
        
        @Override
        public VertexConsumer overlay(int u, int v) {
            return delegate.overlay(u, v);
        }
        
        @Override
        public VertexConsumer light(int u, int v) {
            return delegate.light(u, v);
        }
        
        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return delegate.normal(x, y, z);
        }
        
        @Override
        public void next() {
            delegate.next();
        }
        
        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            // Apply alpha multiplier to fixed color
            // CRITICAL: Ensure alpha is never 0 (invisible) - use max(1, ...)
            int newAlpha = Math.max(1, Math.min(255, Math.round(alpha * this.alpha)));
            delegate.fixedColor(red, green, blue, newAlpha);
        }
        
        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }
    }
    
    /**
     * Vertex consumer wrapper that applies alpha and color tinting to all vertices.
     */
    private static class AlphaTintVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;
        private final float redTint;
        private final float greenTint;
        private final float blueTint;
        
        public AlphaTintVertexConsumer(VertexConsumer delegate, float alpha, 
                                      float redTint, float greenTint, float blueTint) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.redTint = redTint;
            this.greenTint = greenTint;
            this.blueTint = blueTint;
        }
        
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return delegate.vertex(x, y, z);
        }
        
        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            // Apply color tinting and alpha multiplier
            int newRed = Math.max(0, Math.min(255, Math.round(red * redTint)));
            int newGreen = Math.max(0, Math.min(255, Math.round(green * greenTint)));
            int newBlue = Math.max(0, Math.min(255, Math.round(blue * blueTint)));
            // CRITICAL: Ensure alpha is never 0 (invisible) - use max(1, ...)
            int newAlpha = Math.max(1, Math.min(255, Math.round(alpha * this.alpha)));
            return delegate.color(newRed, newGreen, newBlue, newAlpha);
        }
        
        @Override
        public VertexConsumer texture(float u, float v) {
            return delegate.texture(u, v);
        }
        
        @Override
        public VertexConsumer overlay(int u, int v) {
            return delegate.overlay(u, v);
        }
        
        @Override
        public VertexConsumer light(int u, int v) {
            return delegate.light(u, v);
        }
        
        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return delegate.normal(x, y, z);
        }
        
        @Override
        public void next() {
            delegate.next();
        }
        
        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            // Apply color tinting and alpha multiplier to fixed color
            int newRed = Math.max(0, Math.min(255, Math.round(red * redTint)));
            int newGreen = Math.max(0, Math.min(255, Math.round(green * greenTint)));
            int newBlue = Math.max(0, Math.min(255, Math.round(blue * blueTint)));
            // CRITICAL: Ensure alpha is never 0 (invisible) - use max(1, ...)
            int newAlpha = Math.max(1, Math.min(255, Math.round(alpha * this.alpha)));
            delegate.fixedColor(newRed, newGreen, newBlue, newAlpha);
        }
        
        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }
    }
    
    /**
     * Vertex consumer wrapper that applies color tinting but keeps blocks fully opaque.
     * Used for rendering ghost blocks exactly like real blocks with optional color tinting.
     */
    private static class TintVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float redTint;
        private final float greenTint;
        private final float blueTint;
        
        public TintVertexConsumer(VertexConsumer delegate, 
                                  float redTint, float greenTint, float blueTint) {
            this.delegate = delegate;
            this.redTint = redTint;
            this.greenTint = greenTint;
            this.blueTint = blueTint;
        }
        
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return delegate.vertex(x, y, z);
        }
        
        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            // Apply color tinting but keep alpha fully opaque (255)
            int newRed = Math.max(0, Math.min(255, Math.round(red * redTint)));
            int newGreen = Math.max(0, Math.min(255, Math.round(green * greenTint)));
            int newBlue = Math.max(0, Math.min(255, Math.round(blue * blueTint)));
            // Keep alpha at 255 (fully opaque) - ghost blocks should render exactly like real blocks
            return delegate.color(newRed, newGreen, newBlue, 255);
        }
        
        @Override
        public VertexConsumer texture(float u, float v) {
            return delegate.texture(u, v);
        }
        
        @Override
        public VertexConsumer overlay(int u, int v) {
            return delegate.overlay(u, v);
        }
        
        @Override
        public VertexConsumer light(int u, int v) {
            return delegate.light(u, v);
        }
        
        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return delegate.normal(x, y, z);
        }
        
        @Override
        public void next() {
            delegate.next();
        }
        
        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            // Apply color tinting but keep alpha fully opaque (255)
            int newRed = Math.max(0, Math.min(255, Math.round(red * redTint)));
            int newGreen = Math.max(0, Math.min(255, Math.round(green * greenTint)));
            int newBlue = Math.max(0, Math.min(255, Math.round(blue * blueTint)));
            // Keep alpha at 255 (fully opaque)
            delegate.fixedColor(newRed, newGreen, newBlue, 255);
        }
        
        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }
    }
}

