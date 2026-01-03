package com.secretasain.settlements.debug;

import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/**
 * Debug renderer for visualizing building structure bounds and detected farmland areas.
 * Shows outlines for all buildings in the world when enabled (F10).
 * Helps debug rotation and detection issues.
 */
public class BuildingDebugOutlineRenderer {
    private static boolean enabled = false;
    
    // Colors
    private static final int STRUCTURE_OUTLINE_COLOR = 0x00FF00; // Green
    private static final int FARMLAND_DETECTED_COLOR = 0x00FFFF; // Cyan
    private static final int FARMLAND_MISSING_COLOR = 0xFF0000; // Red
    
    /**
     * Toggles debug outline rendering.
     */
    public static void toggle() {
        enabled = !enabled;
    }
    
    /**
     * Checks if debug rendering is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Renders debug outlines for all buildings in the world.
     */
    public static void render(WorldRenderContext context) {
        if (!enabled) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingDebugOutlineRenderer: World or player is null");
            return;
        }
        
        // Get server from world (works in single-player)
        if (!(client.world instanceof net.minecraft.client.world.ClientWorld)) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingDebugOutlineRenderer: World is not ClientWorld");
            return;
        }
        net.minecraft.server.MinecraftServer server = client.world.getServer();
        if (server == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingDebugOutlineRenderer: Server is null");
            return;
        }
        
        // Get SettlementManager from the overworld's persistent state
        net.minecraft.server.world.ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingDebugOutlineRenderer: Overworld is null");
            return;
        }
        
        SettlementManager settlementManager = SettlementManager.getInstance(overworld);
        if (settlementManager == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingDebugOutlineRenderer: SettlementManager is null");
            return;
        }
        
        java.util.Collection<Settlement> settlements = settlementManager.getAllSettlements();
        if (settlements == null || settlements.isEmpty()) {
            // Log occasionally to avoid spam
            if (Math.random() < 0.01) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingDebugOutlineRenderer: No settlements found");
            }
            return;
        }
        
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        
        // TEST: Render a simple box at player position to verify rendering works
        // This should always be visible if rendering is working
        BlockPos playerPos = client.player.getBlockPos();
        Box testBox = new Box(playerPos.add(0, 3, 0), playerPos.add(1, 4, 1));
        Tessellator testTessellator = Tessellator.getInstance();
        BufferBuilder testBuffer = testTessellator.getBuffer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f testMatrix = matrices.peek().getPositionMatrix();
        testBuffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        // Render test box in bright yellow (0xFFFF00)
        renderBoxOutline(testBuffer, testMatrix, testBox, 255, 255, 0, 255);
        BufferRenderer.drawWithGlobalProgram(testBuffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        
        int buildingCount = 0;
        int structureDataFailures = 0;
        // Render outlines for all buildings in all settlements
        for (Settlement settlement : settlements) {
            if (settlement == null || settlement.getBuildings() == null) {
                continue;
            }
            for (Building building : settlement.getBuildings()) {
                if (building == null) {
                    continue;
                }
                
                // Load structure data for this building
                StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
                if (structureData == null) {
                    structureDataFailures++;
                    continue;
                }
                
                // Render structure outline (green)
                renderStructureOutline(matrices, building, structureData, STRUCTURE_OUTLINE_COLOR);
                
                // Render detected farmland areas (cyan for detected, red for missing)
                renderFarmlandDetection(matrices, building, structureData, client);
                
                buildingCount++;
            }
        }
        
        matrices.pop();
        
        // Log occasionally to avoid spam (only every 100 frames or so)
        if (Math.random() < 0.01) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("BuildingDebugOutlineRenderer: Rendered outlines for {} buildings ({} settlements, {} structure load failures)", 
                buildingCount, settlements.size(), structureDataFailures);
        }
    }
    
    /**
     * Renders the structure outline (all blocks in the structure).
     */
    private static void renderStructureOutline(MatrixStack matrices, Building building, 
                                              StructureData structureData, int color) {
        BlockPos buildingPos = building.getPosition();
        int rotation = building.getRotation();
        Vec3i size = structureData.getDimensions();
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int alpha = 255;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        
        int blockCount = 0;
        // Render outline for each block in the structure
        for (var structureBlock : structureData.getBlocks()) {
            BlockPos relativePos = structureBlock.getRelativePos();
            BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
            BlockPos worldPos = buildingPos.add(rotatedPos);
            
            // Draw box outline for this block
            Box blockBox = new Box(worldPos, worldPos.add(1, 1, 1));
            renderBoxOutline(buffer, matrix, blockBox, r, g, b, alpha);
            blockCount++;
        }
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    
    /**
     * Renders farmland detection visualization.
     * Shows which farmland blocks are detected (cyan) vs missing (red).
     */
    private static void renderFarmlandDetection(MatrixStack matrices, Building building,
                                               StructureData structureData, MinecraftClient client) {
        if (client.world == null) {
            return;
        }
        
        BlockPos buildingPos = building.getPosition();
        int rotation = building.getRotation();
        Vec3i size = structureData.getDimensions();
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        
        int farmlandCount = 0;
        // Scan structure blocks to find farmland
        for (var structureBlock : structureData.getBlocks()) {
            BlockState blockState = structureBlock.getBlockState();
            
            // Check if this block should be farmland (farmland or dirt/grass in structure)
            boolean shouldBeFarmland = blockState.getBlock() instanceof FarmlandBlock ||
                blockState.getBlock() == Blocks.DIRT ||
                blockState.getBlock() == Blocks.GRASS_BLOCK ||
                blockState.getBlock() == Blocks.COARSE_DIRT ||
                blockState.getBlock() == Blocks.PODZOL;
            
            if (shouldBeFarmland) {
                BlockPos relativePos = structureBlock.getRelativePos();
                BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
                BlockPos worldPos = buildingPos.add(rotatedPos);
                
                // Check if chunk is loaded
                if (!client.world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                    continue;
                }
                
                // Check if farmland is actually there
                BlockState actualState = client.world.getBlockState(worldPos);
                boolean isFarmland = actualState.getBlock() instanceof FarmlandBlock;
                
                // Render outline: cyan if detected, red if missing
                int color = isFarmland ? FARMLAND_DETECTED_COLOR : FARMLAND_MISSING_COLOR;
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int alpha = 255;
                
                // Draw box outline slightly above the block to distinguish from structure outline
                Box farmlandBox = new Box(
                    worldPos.getX(), worldPos.getY() + 0.1, worldPos.getZ(),
                    worldPos.getX() + 1, worldPos.getY() + 1.1, worldPos.getZ() + 1
                );
                renderBoxOutline(buffer, matrix, farmlandBox, r, g, b, alpha);
                farmlandCount++;
            }
        }
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    
    /**
     * Renders a box outline.
     */
    private static void renderBoxOutline(BufferBuilder buffer, Matrix4f matrix, Box box, 
                                        int r, int g, int b, int a) {
        // Draw all 12 edges of the box
        // Bottom face
        renderLine(buffer, matrix, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderLine(buffer, matrix, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderLine(buffer, matrix, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderLine(buffer, matrix, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        
        // Top face
        renderLine(buffer, matrix, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderLine(buffer, matrix, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderLine(buffer, matrix, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderLine(buffer, matrix, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        
        // Vertical edges
        renderLine(buffer, matrix, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        renderLine(buffer, matrix, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderLine(buffer, matrix, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderLine(buffer, matrix, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
    }
    
    /**
     * Renders a line.
     * Note: DEBUG_LINES mode draws lines, but they may be thin. For better visibility,
     * we render each edge twice with slight offset to make them thicker.
     */
    private static void renderLine(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1,
                                  double x2, double y2, double z2, int r, int g, int b, int a) {
        // Render the line
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
        
        // Render again with slight offset to make it thicker (if coordinates differ)
        float offset = 0.001f;
        if (Math.abs(x1 - x2) > 0.01) {
            buffer.vertex(matrix, (float)(x1 + offset), (float)y1, (float)z1).color(r, g, b, a).next();
            buffer.vertex(matrix, (float)(x2 + offset), (float)y2, (float)z2).color(r, g, b, a).next();
        } else if (Math.abs(z1 - z2) > 0.01) {
            buffer.vertex(matrix, (float)x1, (float)y1, (float)(z1 + offset)).color(r, g, b, a).next();
            buffer.vertex(matrix, (float)x2, (float)y2, (float)(z2 + offset)).color(r, g, b, a).next();
        } else if (Math.abs(y1 - y2) > 0.01) {
            buffer.vertex(matrix, (float)x1, (float)(y1 + offset), (float)z1).color(r, g, b, a).next();
            buffer.vertex(matrix, (float)x2, (float)(y2 + offset), (float)z2).color(r, g, b, a).next();
        }
    }
    
    /**
     * Rotates a position based on building rotation.
     * Uses the same formula as FarmMaintenanceSystem.rotatePosition to ensure consistency.
     */
    private static BlockPos rotatePosition(BlockPos relativePos, Vec3i size, int rotation) {
        int x = relativePos.getX();
        int y = relativePos.getY();
        int z = relativePos.getZ();
        
        switch (rotation) {
            case 90:
                return new BlockPos(z, y, size.getX() - x - 1);
            case 180:
                return new BlockPos(size.getX() - x - 1, y, size.getZ() - z - 1);
            case 270:
                return new BlockPos(size.getZ() - z - 1, y, x);
            case 0:
            default:
                return relativePos;
        }
    }
}
