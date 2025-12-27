package com.secretasain.settlements.block;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

/**
 * Renders ghost blocks with transparency, showing the actual block texture they represent.
 * Now uses the GhostBlockRendererUtility for consistent rendering.
 * 
 * Note: Ghost blocks are also automatically synced to GhostBlockManager via GhostBlockSyncHandler,
 * which handles rendering through the general-purpose system. This renderer is kept for
 * backwards compatibility and as a fallback.
 */
public class GhostBlockRenderer implements BlockEntityRenderer<GhostBlockEntity> {
    // Use fully opaque (1.0f) so blocks render exactly like the NBT shows
    private static final float ALPHA = 1.0f;
    
    public GhostBlockRenderer(BlockEntityRendererFactory.Context context) {
    }
    
    @Override
    public void render(GhostBlockEntity entity, float tickDelta, MatrixStack matrices,
                      VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState representedBlock = entity.getRepresentedBlock();
        
        // Debug logging to check if block entity has data
        if (representedBlock == null || representedBlock.isAir()) {
            // com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockRenderer: Block entity at {} has no represented block or is air (representedBlock: {})", 
            //     entity.getPos(), representedBlock);
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            // com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockRenderer: Client or world is null at {}", entity.getPos());
            return;
        }
        
        BlockPos pos = entity.getPos();
        
        // TEMPORARY: Always render via block entity renderer to test if it works
        // The GhostBlockManager approach might not be working correctly
        // TODO: Re-enable manager check once we verify block entity renderer works
        // if (GhostBlockManager.getInstance().hasGhostBlock(pos)) {
        //     // Block is being rendered by GhostBlockManager, skip this renderer
        //     return;
        // }
        
        // Get proper light level from world
        int blockLight = client.world.getLightLevel(pos);
        int skyLight = client.world.getLightLevel(pos.up());
        int combinedLight = LightmapTextureManager.pack(blockLight, skyLight);
        
        // DEBUG: Log that we're rendering
        // if (com.secretasain.settlements.SettlementsMod.LOGGER.isInfoEnabled() && Math.random() < 0.1) {
        //     com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockRenderer: Rendering ghost block at {} with represented block {}", 
        //         pos, net.minecraft.registry.Registries.BLOCK.getId(representedBlock.getBlock()));
        // }
        
        // Use the GhostBlockRendererUtility for consistent rendering
        // CRITICAL: In BlockEntityRenderer, matrices are ALREADY at the block position (0,0,0 relative to block)
        // The utility will handle centering the block at (0.5, 0.5, 0.5) when manageBlendState=true
        // manageBlendState=true because this is a single block entity renderer
        boolean success = GhostBlockRendererUtility.renderGhostBlock(
            pos,
            representedBlock,
            matrices,
            vertexConsumers,
            ALPHA,
            combinedLight,
            true
        );
        
        // if (!success) {
        //     com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockRenderer: Failed to render ghost block at {}", pos);
        // }
    }
}

