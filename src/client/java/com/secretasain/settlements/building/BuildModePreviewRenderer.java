package com.secretasain.settlements.building;

import com.secretasain.settlements.block.GhostBlockRendererUtility;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4f;

/**
 * Renders ghost blocks for structure preview in build mode.
 */
public class BuildModePreviewRenderer {
    private static final float ALPHA = 0.5f;
    private static final int VALID_COLOR = 0x00FF00; // Green
    private static final int INVALID_COLOR = 0xFF0000; // Red
    
    /**
     * Renders the structure preview at the given position.
     * @param client Minecraft client instance
     * @param matrices Matrix stack for rendering
     * @param camera Camera position
     */
    public static void render(MinecraftClient client, MatrixStack matrices, Vec3d camera) {
        if (!ClientBuildModeManager.isActive()) {
            return;
        }
        
        StructureData structure = ClientBuildModeManager.getActiveStructure();
        BlockPos placementPos = ClientBuildModeManager.getPlacementPos();
        
        if (structure == null || placementPos == null) {
            return;
        }
        
        int rotation = ClientBuildModeManager.getRotation();
        boolean isValid = canPlaceStructure(client, structure, placementPos, rotation);
        
        // Set up rendering
        matrices.push();
        
        // Move to world coordinates (subtract camera position to get relative coordinates)
        matrices.translate(-camera.x, -camera.y, -camera.z);
        
        // Get vertex consumer provider for batching
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        
        // Set up blend state once for all blocks (keep it active throughout)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        
        try {
            // Render each block
            // Each block will set its own shader color (via GhostBlockRendererUtility)
            // but we manage blend state globally for batch rendering
            for (StructureBlock block : structure.getBlocks()) {
                BlockPos relativePos = block.getRelativePos();
                BlockPos worldPos = applyRotation(relativePos, rotation).add(placementPos);
                
                // Rotate block state if needed (for directional blocks like stairs, slabs, etc.)
                BlockState blockState = com.secretasain.settlements.building.BlockStateRotator.rotateBlockState(
                    block.getBlockState(), 
                    rotation
                );
                
                // Skip air blocks
                if (blockState.isAir()) {
                    continue;
                }
                
                // Render ghost block (don't reset blend state between blocks)
                // GhostBlockRendererUtility will set shader color per-block
                renderGhostBlock(client, matrices, immediate, worldPos, blockState, isValid);
            }
            
            // Draw all batched blocks at once
            immediate.draw();
        } finally {
            // Reset blend state after all blocks are rendered
            // CRITICAL: Reset shader color AFTER drawing to ensure transparency was applied
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
        
        // Draw outline for structure bounds
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        renderStructureBounds(client, matrices, buffer, structure, placementPos, rotation, isValid);
        
        matrices.pop();
    }
    
    /**
     * Renders a single ghost block using the actual block model with transparency.
     * Now uses GhostBlockRendererUtility for consistent rendering.
     * Note: Blend state should be set up before calling this, and immediate.draw() should be called after all blocks.
     */
    private static void renderGhostBlock(MinecraftClient client, MatrixStack matrices, 
                                         VertexConsumerProvider.Immediate immediate,
                                         BlockPos pos, BlockState state, boolean isValid) {
        if (client.world == null) {
            return;
        }
        
        // Skip air blocks
        if (state.isAir()) {
            return;
        }
        
        try {
            // Use GhostBlockRendererUtility for consistent rendering
            // Pass manageBlendState=false since we're managing blend state globally for batch rendering
            // The shader color is already set globally above, so each block will use that alpha
            if (!isValid) {
                // Red tint for invalid placement
                // Note: We still need to set shader color per-block for tinting, but alpha is global
                GhostBlockRendererUtility.renderGhostBlockTinted(
                    pos, state, matrices, immediate,
                    ALPHA, 1.0f, 0.7f, 0.7f, false
                );
            } else {
                // Normal color for valid placement
                // Shader color is already set globally, so this will use that alpha
                GhostBlockRendererUtility.renderGhostBlock(
                    pos, state, matrices, immediate,
                    ALPHA, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE, false
                );
            }
        } catch (Exception e) {
            // Log error but don't break rendering
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Failed to render block model for preview at {}: {}", pos, e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Renders the structure bounds outline.
     */
    private static void renderStructureBounds(MinecraftClient client, MatrixStack matrices, BufferBuilder buffer,
                                             StructureData structure, BlockPos placementPos, int rotation, boolean isValid) {
        // Calculate bounds
        BlockPos min = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        BlockPos max = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        
        for (StructureBlock block : structure.getBlocks()) {
            BlockPos rotated = applyRotation(block.getRelativePos(), rotation);
            BlockPos world = rotated.add(placementPos);
            
            min = new BlockPos(
                Math.min(min.getX(), world.getX()),
                Math.min(min.getY(), world.getY()),
                Math.min(min.getZ(), world.getZ())
            );
            max = new BlockPos(
                Math.max(max.getX(), world.getX()),
                Math.max(max.getY(), world.getY()),
                Math.max(max.getZ(), world.getZ())
            );
        }
        
        Box bounds = new Box(min, max.add(1, 1, 1));
        
        int color = isValid ? VALID_COLOR : INVALID_COLOR;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int alpha = 255;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        
        // Draw outline edges
        renderLine(buffer, matrix, bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.minY, bounds.minZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.minX, bounds.minY, bounds.minZ, bounds.minX, bounds.maxY, bounds.minZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.minX, bounds.minY, bounds.minZ, bounds.minX, bounds.minY, bounds.maxZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.maxX, bounds.maxY, bounds.maxZ, bounds.minX, bounds.maxY, bounds.maxZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.maxX, bounds.maxY, bounds.maxZ, bounds.maxX, bounds.minY, bounds.maxZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.maxX, bounds.maxY, bounds.maxZ, bounds.maxX, bounds.maxY, bounds.minZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.maxX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.minZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.maxX, bounds.minY, bounds.minZ, bounds.maxX, bounds.minY, bounds.maxZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.minX, bounds.maxY, bounds.minZ, bounds.minX, bounds.maxY, bounds.maxZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.minX, bounds.maxY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.minZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.minX, bounds.minY, bounds.maxZ, bounds.maxX, bounds.minY, bounds.maxZ, r, g, b, alpha);
        renderLine(buffer, matrix, bounds.minX, bounds.minY, bounds.maxZ, bounds.minX, bounds.maxY, bounds.maxZ, r, g, b, alpha);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    
    /**
     * Renders a line.
     */
    private static void renderLine(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1,
                                   double x2, double y2, double z2, int r, int g, int b, int a) {
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
    }
    
    /**
     * Applies rotation to a relative position.
     */
    private static BlockPos applyRotation(BlockPos relativePos, int rotation) {
        int x = relativePos.getX();
        int z = relativePos.getZ();
        
        switch (rotation) {
            case 90:
                return new BlockPos(-z, relativePos.getY(), x);
            case 180:
                return new BlockPos(-x, relativePos.getY(), -z);
            case 270:
                return new BlockPos(z, relativePos.getY(), -x);
            default: // 0
                return relativePos;
        }
    }
    
    /**
     * Checks if the structure can be placed at the given position.
     */
    private static boolean canPlaceStructure(MinecraftClient client, StructureData structure, BlockPos placementPos, int rotation) {
        if (client.world == null) {
            return false;
        }
        
        // Simple validation: check if blocks are in loaded chunks and not solid
        for (StructureBlock block : structure.getBlocks()) {
            BlockPos relativePos = block.getRelativePos();
            BlockPos worldPos = applyRotation(relativePos, rotation).add(placementPos);
            
            // Check if chunk is loaded
            ChunkPos chunkPos = new ChunkPos(worldPos);
            if (!client.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                return false;
            }
            
            BlockState existing = client.world.getBlockState(worldPos);
            if (!existing.isAir() && !existing.isReplaceable()) {
                return false;
            }
        }
        
        return true;
    }
}

