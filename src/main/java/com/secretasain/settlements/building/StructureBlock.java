package com.secretasain.settlements.building;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a single block in a structure.
 * Stores the relative position, block state, and optional block entity data.
 */
public class StructureBlock {
    private final BlockPos relativePos;
    private final BlockState blockState;
    private final NbtCompound blockEntityData;
    
    public StructureBlock(BlockPos relativePos, BlockState blockState, NbtCompound blockEntityData) {
        this.relativePos = relativePos;
        this.blockState = blockState;
        this.blockEntityData = blockEntityData != null ? blockEntityData : new NbtCompound();
    }
    
    public StructureBlock(BlockPos relativePos, BlockState blockState) {
        this(relativePos, blockState, null);
    }
    
    /**
     * Gets the relative position of this block within the structure.
     * @return Relative BlockPos
     */
    public BlockPos getRelativePos() {
        return relativePos;
    }
    
    /**
     * Gets the block state for this block.
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

