package com.secretasain.settlements.building;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a single block queued for placement.
 * Contains the absolute world position, block state, and optional block entity data.
 */
public class QueuedBlock {
    private final BlockPos worldPos;
    private final BlockState blockState;
    private final NbtCompound blockEntityData;
    
    public QueuedBlock(BlockPos worldPos, BlockState blockState, NbtCompound blockEntityData) {
        this.worldPos = worldPos;
        this.blockState = blockState;
        this.blockEntityData = blockEntityData != null ? blockEntityData : new NbtCompound();
    }
    
    public QueuedBlock(BlockPos worldPos, BlockState blockState) {
        this(worldPos, blockState, null);
    }
    
    /**
     * Gets the world position where this block should be placed.
     * @return Absolute BlockPos in the world
     */
    public BlockPos getWorldPos() {
        return worldPos;
    }
    
    /**
     * Gets the block state to place.
     * @return BlockState
     */
    public BlockState getBlockState() {
        return blockState;
    }
    
    /**
     * Gets the block entity data (if any).
     * @return NbtCompound containing block entity data, or empty compound if none
     */
    public NbtCompound getBlockEntityData() {
        return blockEntityData;
    }
    
    /**
     * Checks if this block has block entity data.
     * @return true if block entity data exists
     */
    public boolean hasBlockEntityData() {
        return blockEntityData != null && !blockEntityData.isEmpty();
    }
}

