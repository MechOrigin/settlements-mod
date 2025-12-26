package com.secretasain.settlements.building;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages a queue of blocks to be placed for a building.
 * Blocks are placed in order (FIFO - first in, first out).
 */
public class BlockPlacementQueue {
    private final Deque<QueuedBlock> queue;
    private int totalBlocks;
    private int placedBlocks;
    
    public BlockPlacementQueue() {
        this.queue = new ArrayDeque<>();
        this.totalBlocks = 0;
        this.placedBlocks = 0;
    }
    
    /**
     * Adds a block to the placement queue.
     * @param worldPos The absolute world position where the block should be placed
     * @param blockState The block state to place
     * @param blockEntityData Optional block entity data (can be null)
     */
    public void addBlock(BlockPos worldPos, BlockState blockState, NbtCompound blockEntityData) {
        queue.offer(new QueuedBlock(worldPos, blockState, blockEntityData));
        totalBlocks++;
    }
    
    /**
     * Gets and removes the next block from the queue.
     * @return The next QueuedBlock, or null if queue is empty
     */
    public QueuedBlock getNextBlock() {
        QueuedBlock block = queue.poll();
        if (block != null) {
            placedBlocks++;
        }
        return block;
    }
    
    /**
     * Checks if the queue is empty.
     * @return true if no blocks remain in the queue
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Gets the number of blocks remaining in the queue.
     * @return Number of blocks in queue
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * Gets the total number of blocks that were in the queue (including placed ones).
     * @return Total block count
     */
    public int getTotalBlocks() {
        return totalBlocks;
    }
    
    /**
     * Gets the number of blocks that have been placed.
     * @return Number of placed blocks
     */
    public int getPlacedBlocks() {
        return placedBlocks;
    }
    
    /**
     * Gets the current progress as a percentage (0.0 to 1.0).
     * @return Progress percentage
     */
    public float getProgress() {
        if (totalBlocks == 0) {
            return 1.0f;
        }
        return (float) placedBlocks / (float) totalBlocks;
    }
    
    /**
     * Clears all blocks from the queue.
     */
    public void clear() {
        queue.clear();
        totalBlocks = 0;
        placedBlocks = 0;
    }
}

