package com.secretasain.settlements.block;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ghost blocks for rendering. Tracks which blocks should be rendered as ghost blocks
 * and handles rendering them during world rendering.
 */
public class GhostBlockManager {
    private static final GhostBlockManager INSTANCE = new GhostBlockManager();
    
    /**
     * Data class for a ghost block entry.
     */
    public static class GhostBlockEntry {
        private final BlockPos pos;
        private final BlockState state;
        private final float alpha;
        private final boolean useWorldLight;
        private final float redTint;
        private final float greenTint;
        private final float blueTint;
        
        public GhostBlockEntry(BlockPos pos, BlockState state) {
            // Default to fully opaque (1.0f) so blocks render exactly like the NBT shows
            this(pos, state, 1.0f, false, 1.0f, 1.0f, 1.0f);
        }
        
        public GhostBlockEntry(BlockPos pos, BlockState state, float alpha) {
            this(pos, state, alpha, false, 1.0f, 1.0f, 1.0f);
        }
        
        public GhostBlockEntry(BlockPos pos, BlockState state, float alpha, boolean useWorldLight) {
            this(pos, state, alpha, useWorldLight, 1.0f, 1.0f, 1.0f);
        }
        
        public GhostBlockEntry(BlockPos pos, BlockState state, float alpha, 
                              float redTint, float greenTint, float blueTint) {
            this(pos, state, alpha, false, redTint, greenTint, blueTint);
        }
        
        public GhostBlockEntry(BlockPos pos, BlockState state, float alpha, boolean useWorldLight,
                              float redTint, float greenTint, float blueTint) {
            this.pos = pos;
            this.state = state;
            this.alpha = Math.max(0.0f, Math.min(1.0f, alpha)); // Clamp to 0-1
            this.useWorldLight = useWorldLight;
            this.redTint = Math.max(0.0f, Math.min(1.0f, redTint));
            this.greenTint = Math.max(0.0f, Math.min(1.0f, greenTint));
            this.blueTint = Math.max(0.0f, Math.min(1.0f, blueTint));
        }
        
        public BlockPos getPos() { return pos; }
        public BlockState getState() { return state; }
        public float getAlpha() { return alpha; }
        public boolean shouldUseWorldLight() { return useWorldLight; }
        public float getRedTint() { return redTint; }
        public float getGreenTint() { return greenTint; }
        public float getBlueTint() { return blueTint; }
    }
    
    // Thread-safe map for ghost blocks
    private final Map<BlockPos, GhostBlockEntry> ghostBlocks = new ConcurrentHashMap<>();
    
    private GhostBlockManager() {
    }
    
    /**
     * Gets the singleton instance of GhostBlockManager.
     * @return The GhostBlockManager instance
     */
    public static GhostBlockManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Adds a ghost block to be rendered.
     * @param pos The position of the ghost block
     * @param state The block state to render
     */
    public void addGhostBlock(BlockPos pos, BlockState state) {
        ghostBlocks.put(pos, new GhostBlockEntry(pos, state));
    }
    
    /**
     * Adds a ghost block with custom alpha.
     * @param pos The position of the ghost block
     * @param state The block state to render
     * @param alpha The transparency level (0.0 to 1.0)
     */
    public void addGhostBlock(BlockPos pos, BlockState state, float alpha) {
        ghostBlocks.put(pos, new GhostBlockEntry(pos, state, alpha));
    }
    
    /**
     * Adds a ghost block with custom alpha and world light usage.
     * @param pos The position of the ghost block
     * @param state The block state to render
     * @param alpha The transparency level (0.0 to 1.0)
     * @param useWorldLight If true, uses world light level; if false, uses max light
     */
    public void addGhostBlock(BlockPos pos, BlockState state, float alpha, boolean useWorldLight) {
        ghostBlocks.put(pos, new GhostBlockEntry(pos, state, alpha, useWorldLight));
        com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockManager: Added ghost block at {} with state {} (total: {})", 
            pos, state != null ? net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()) : "null", ghostBlocks.size());
    }
    
    /**
     * Adds a ghost block with color tinting (useful for valid/invalid placement).
     * @param pos The position of the ghost block
     * @param state The block state to render
     * @param alpha The transparency level (0.0 to 1.0)
     * @param redTint Red tint multiplier (0.0 to 1.0)
     * @param greenTint Green tint multiplier (0.0 to 1.0)
     * @param blueTint Blue tint multiplier (0.0 to 1.0)
     */
    public void addGhostBlockTinted(BlockPos pos, BlockState state, float alpha,
                                    float redTint, float greenTint, float blueTint) {
        ghostBlocks.put(pos, new GhostBlockEntry(pos, state, alpha, redTint, greenTint, blueTint));
    }
    
    /**
     * Adds a ghost block entry directly.
     * @param entry The ghost block entry
     */
    public void addGhostBlock(GhostBlockEntry entry) {
        ghostBlocks.put(entry.getPos(), entry);
    }
    
    /**
     * Removes a ghost block from rendering.
     * @param pos The position of the ghost block to remove
     */
    public void removeGhostBlock(BlockPos pos) {
        ghostBlocks.remove(pos);
    }
    
    /**
     * Removes all ghost blocks.
     */
    public void clearAll() {
        ghostBlocks.clear();
    }
    
    /**
     * Removes all ghost blocks in a collection of positions.
     * @param positions The positions to remove
     */
    public void removeGhostBlocks(Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            ghostBlocks.remove(pos);
        }
    }
    
    /**
     * Checks if a position has a ghost block.
     * @param pos The position to check
     * @return true if there is a ghost block at this position
     */
    public boolean hasGhostBlock(BlockPos pos) {
        return ghostBlocks.containsKey(pos);
    }
    
    /**
     * Gets the ghost block entry at a position.
     * @param pos The position to check
     * @return The ghost block entry, or null if none exists
     */
    public GhostBlockEntry getGhostBlock(BlockPos pos) {
        return ghostBlocks.get(pos);
    }
    
    /**
     * Gets all ghost block positions.
     * @return A set of all positions with ghost blocks
     */
    public Set<BlockPos> getAllPositions() {
        return new HashSet<>(ghostBlocks.keySet());
    }
    
    /**
     * Gets the number of ghost blocks.
     * @return The count of ghost blocks
     */
    public int size() {
        return ghostBlocks.size();
    }
    
    /**
     * Checks if there are any ghost blocks.
     * @return true if there are no ghost blocks
     */
    public boolean isEmpty() {
        return ghostBlocks.isEmpty();
    }
    
    /**
     * Renders all ghost blocks. Should be called from a world render event.
     * @param matrices The matrix stack for rendering
     * @param cameraPos The camera position (for culling)
     * @param vertexConsumers The vertex consumer provider
     */
    public void renderAll(MatrixStack matrices, Vec3d cameraPos, VertexConsumerProvider vertexConsumers) {
        if (ghostBlocks.isEmpty()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        
        int renderedCount = 0;
        int skippedCount = 0;
        
        // Render each ghost block
        for (GhostBlockEntry entry : ghostBlocks.values()) {
            BlockPos pos = entry.getPos();
            BlockState state = entry.getState();
            
            // Skip air blocks
            if (state == null || state.isAir()) {
                skippedCount++;
                continue;
            }
            
            // Optional: Frustum culling (skip blocks too far from camera)
            // This is a simple distance check - could be enhanced with proper frustum culling
            double distanceSq = pos.getSquaredDistance(cameraPos.x, cameraPos.y, cameraPos.z);
            if (distanceSq > 64 * 64) { // Skip blocks more than 64 blocks away
                skippedCount++;
                continue;
            }
            
            // Render with tinting if colors are not white
            // Use manageBlendState=false for batch rendering (blend state is managed by GhostBlockRenderHandler)
            boolean success = false;
            if (entry.getRedTint() != 1.0f || entry.getGreenTint() != 1.0f || entry.getBlueTint() != 1.0f) {
                success = GhostBlockRendererUtility.renderGhostBlockTinted(
                    pos, state, matrices, vertexConsumers,
                    entry.getAlpha(),
                    entry.getRedTint(), entry.getGreenTint(), entry.getBlueTint(),
                    false // Batch rendering - don't manage blend state
                );
            } else {
                // Render with or without world light
                int lightLevel = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                if (entry.shouldUseWorldLight()) {
                    int blockLight = client.world.getLightLevel(pos);
                    int skyLight = client.world.getLightLevel(pos.up());
                    lightLevel = LightmapTextureManager.pack(blockLight, skyLight);
                }
                // CRITICAL: Set shader color BEFORE rendering each block
                // This ensures each block's alpha is applied correctly in batch rendering
                // The vertex consumer also applies alpha per-vertex for double transparency
                // CRITICAL: For batch rendering, we need to set the shader program per block based on render layer
                net.minecraft.client.render.RenderLayer renderLayer = net.minecraft.client.render.RenderLayers.getMovingBlockLayer(state);
                if (renderLayer == net.minecraft.client.render.RenderLayer.getSolid()) {
                    com.mojang.blaze3d.systems.RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeSolidProgram());
                } else if (renderLayer == net.minecraft.client.render.RenderLayer.getCutout()) {
                    com.mojang.blaze3d.systems.RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeCutoutProgram());
                } else if (renderLayer == net.minecraft.client.render.RenderLayer.getCutoutMipped()) {
                    com.mojang.blaze3d.systems.RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeCutoutMippedProgram());
                } else {
                    com.mojang.blaze3d.systems.RenderSystem.setShader(() -> net.minecraft.client.render.GameRenderer.getRenderTypeSolidProgram());
                }
                // Set shader color to fully opaque
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                
                success = GhostBlockRendererUtility.renderGhostBlock(
                    pos, state, matrices, vertexConsumers,
                    entry.getAlpha(), lightLevel,
                    false // Batch rendering - don't manage blend state
                );
            }
            
            if (success) {
                renderedCount++;
            } else {
                skippedCount++;
                // DEBUG: Log failed renders (only occasionally to avoid spam)
                if (com.secretasain.settlements.SettlementsMod.LOGGER.isDebugEnabled() && Math.random() < 0.1) {
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockManager: Failed to render ghost block at {} with state {}", 
                        pos, net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()));
                }
            }
        }
        
        // CRITICAL: Log rendering stats - this is important to see if blocks are actually rendering
        if (renderedCount > 0 || skippedCount > 0) {
            // Log every time we try to render, but only if there's something to report
            if (renderedCount == 0 && !ghostBlocks.isEmpty()) {
                // WARN if we have blocks but none rendered (potential issue)
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockManager: Have {} ghost blocks but rendered 0! Skipped: {} (total: {})", 
                    ghostBlocks.size(), skippedCount, ghostBlocks.size());
            } else if (com.secretasain.settlements.SettlementsMod.LOGGER.isDebugEnabled() && Math.random() < 0.1) {
                // Only log success occasionally to avoid spam
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockManager: Rendered {} blocks, skipped {} blocks (total: {})", 
                    renderedCount, skippedCount, ghostBlocks.size());
            }
        }
    }
}

