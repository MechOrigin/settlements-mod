package com.secretasain.settlements.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * A transparent, non-solid block used to show building placement previews.
 * Players can walk through these blocks, but they are visible to indicate
 * where a building will be constructed.
 */
public class GhostBlock extends Block implements BlockEntityProvider {
    public GhostBlock(Settings settings) {
        super(settings);
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return ModBlocks.GHOST_BLOCK_ENTITY.instantiate(pos, state);
    }
    
    /**
     * Makes the block non-solid (air-like collision).
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }
    
    /**
     * Makes the block have full cube outline shape.
     */
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
    }
    
    /**
     * Makes the block transparent (not opaque).
     */
    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
    
    /**
     * Makes the block not block light.
     */
    @Override
    public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        return 0;
    }
    
    /**
     * Makes the block invisible (we use block entity renderer for the actual block preview).
     */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }
}

