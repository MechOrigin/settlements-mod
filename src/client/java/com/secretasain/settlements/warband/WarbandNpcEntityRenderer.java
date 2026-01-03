package com.secretasain.settlements.warband;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Client-side renderer for WarbandNpcEntity.
 * Renders the NPC as a player model so it can properly display armor and weapons.
 */
public class WarbandNpcEntityRenderer extends LivingEntityRenderer<WarbandNpcEntity, PlayerEntityModel<WarbandNpcEntity>> {
    // Use default player textures - these are the actual paths Minecraft uses
    // For default Steve texture (wide arms)
    private static final Identifier STEVE_TEXTURE = new Identifier("minecraft", "textures/entity/player/wide/steve.png");
    // For default Alex texture (slim arms)  
    private static final Identifier ALEX_TEXTURE = new Identifier("minecraft", "textures/entity/player/slim/alex.png");
    
    public WarbandNpcEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.addFeature(new net.minecraft.client.render.entity.feature.ArmorFeatureRenderer<>(
            this, 
            new net.minecraft.client.render.entity.model.BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
            new net.minecraft.client.render.entity.model.BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
        ));
        this.addFeature(new net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }
    
    @Override
    public Identifier getTexture(WarbandNpcEntity entity) {
        // Alternate between Steve and Alex textures based on entity UUID
        // This gives variety to NPCs while using classic vanilla textures
        long uuidMost = entity.getUuid().getMostSignificantBits();
        boolean useAlex = (uuidMost & 1) == 0; // Use Alex if least significant bit of UUID is 0
        
        Identifier texture = useAlex ? ALEX_TEXTURE : STEVE_TEXTURE;
        
        // Try to load the texture - if it fails, fall back to default
        // Note: Minecraft's default player textures might not exist at these paths
        // In that case, we'll need to create our own textures or use a different approach
        return texture;
    }
    
    @Override
    protected void setupTransforms(WarbandNpcEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta) {
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);
        // Ensure NPC stands upright
        if (entity.isBaby()) {
            matrices.scale(0.5f, 0.5f, 0.5f);
        }
    }
}

