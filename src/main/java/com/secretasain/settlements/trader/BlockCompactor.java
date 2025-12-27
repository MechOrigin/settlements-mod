package com.secretasain.settlements.trader;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Utility class for compacting items into blocks (9 items = 1 block).
 * Used for fruit/vegetable block acceptance in trades.
 */
public class BlockCompactor {
    /**
     * Attempts to compact 9 items of the same type into a block.
     * @param items List of item stacks to compact
     * @return Block state if 9 items can be compacted, null otherwise
     */
    public static BlockState compactItemsToBlock(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        
        // Check if we have at least 9 items of the same type
        int totalCount = 0;
        Item firstItem = null;
        
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            
            if (firstItem == null) {
                firstItem = stack.getItem();
            } else if (stack.getItem() != firstItem) {
                // Different item type - can't compact
                return null;
            }
            
            totalCount += stack.getCount();
        }
        
        if (firstItem == null || totalCount < 9) {
            return null;
        }
        
        // Find the block that corresponds to this item
        Identifier itemId = Registries.ITEM.getId(firstItem);
        if (itemId == null) {
            return null;
        }
        
        // Check if this item has a corresponding block in the registry
        for (Identifier blockId : FruitVegetableBlockRegistry.getAcceptedBlocks()) {
            Identifier itemEquivalent = FruitVegetableBlockRegistry.getItemEquivalent(blockId);
            if (itemEquivalent != null && itemEquivalent.equals(itemId)) {
                Block block = Registries.BLOCK.get(blockId);
                if (block != null) {
                    return block.getDefaultState();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Expands a block into 9 items.
     * @param blockState The block state
     * @param blockCount Number of blocks
     * @return Item stack with 9 * blockCount items, or null if block is not accepted
     */
    public static ItemStack expandBlockToItems(BlockState blockState, int blockCount) {
        if (blockState == null) {
            return null;
        }
        
        Block block = blockState.getBlock();
        return FruitVegetableBlockRegistry.expandBlockToItems(block, blockCount);
    }
}

