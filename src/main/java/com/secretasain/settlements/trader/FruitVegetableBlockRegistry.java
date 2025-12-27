package com.secretasain.settlements.trader;

import com.secretasain.settlements.SettlementsMod;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Registry for fruit and vegetable blocks that can be accepted by trader huts.
 * Supports both vanilla and modded blocks.
 * Handles block-to-item conversion (9 items = 1 block).
 */
public class FruitVegetableBlockRegistry {
    private static final Set<Identifier> ACCEPTED_BLOCKS = new HashSet<>();
    private static final Map<Identifier, Identifier> BLOCK_TO_ITEM_MAP = new HashMap<>();
    
    static {
        // Register vanilla fruit/vegetable blocks
        registerBlock(Blocks.MELON, "minecraft:melon");
        registerBlock(Blocks.PUMPKIN, "minecraft:pumpkin");
        registerBlock(Blocks.CARVED_PUMPKIN, "minecraft:pumpkin"); // Carved pumpkin -> pumpkin item
        registerBlock(Blocks.JACK_O_LANTERN, "minecraft:pumpkin"); // Jack o'lantern -> pumpkin item
        registerBlock(Blocks.HAY_BLOCK, "minecraft:wheat"); // Hay bale -> wheat (9 wheat = 1 hay bale)
        
        // Note: Modded blocks are registered in registerModdedBlocks() which is called on server start
        // This ensures blocks are only registered if the mods are actually loaded
    }
    
    /**
     * Registers a block as an accepted fruit/vegetable block.
     * @param block The block to register
     * @param itemId The item identifier that this block converts to (9 items per block)
     */
    public static void registerBlock(Block block, String itemId) {
        Identifier blockId = Registries.BLOCK.getId(block);
        if (blockId != null) {
            ACCEPTED_BLOCKS.add(blockId);
            Identifier itemIdentifier = Identifier.tryParse(itemId);
            if (itemIdentifier != null) {
                BLOCK_TO_ITEM_MAP.put(blockId, itemIdentifier);
            }
        }
    }
    
    /**
     * Registers a block by identifier as an accepted fruit/vegetable block.
     * Only registers if the block actually exists in the registry (mod is loaded).
     * @param blockId The block identifier (e.g., "farmersdelight:apple_crate")
     * @param itemId The item identifier that this block converts to (e.g., "farmersdelight:apple")
     * @return true if the block was registered, false if it doesn't exist
     */
    public static boolean registerBlock(String blockId, String itemId) {
        Identifier blockIdentifier = Identifier.tryParse(blockId);
        Identifier itemIdentifier = Identifier.tryParse(itemId);
        
        if (blockIdentifier != null && itemIdentifier != null) {
            // Check if the block actually exists in the registry (mod is loaded)
            Block block = Registries.BLOCK.get(blockIdentifier);
            if (block != null && block != Blocks.AIR) {
                // Check if the item exists too
                Item item = Registries.ITEM.get(itemIdentifier);
                if (item != null && item != Items.AIR) {
                    ACCEPTED_BLOCKS.add(blockIdentifier);
                    BLOCK_TO_ITEM_MAP.put(blockIdentifier, itemIdentifier);
                    SettlementsMod.LOGGER.debug("Registered farming block: {} -> {}", blockId, itemId);
                    return true;
                } else {
                    SettlementsMod.LOGGER.debug("Skipping block {}: item {} not found in registry", blockId, itemId);
                }
            } else {
                SettlementsMod.LOGGER.debug("Skipping block {}: not found in registry (mod may not be loaded)", blockId);
            }
        }
        return false;
    }
    
    /**
     * Checks if a block is an accepted fruit/vegetable block.
     * @param blockId The block identifier
     * @return true if the block is accepted
     */
    public static boolean isFruitVegetableBlock(Identifier blockId) {
        if (blockId == null) {
            return false;
        }
        // Check if it's registered as a block
        if (ACCEPTED_BLOCKS.contains(blockId)) {
            return true;
        }
        // Also check if it exists as a block in the registry and is registered
        Block block = Registries.BLOCK.get(blockId);
        if (block != null && block != Blocks.AIR) {
            Identifier blockRegistryId = Registries.BLOCK.getId(block);
            return blockRegistryId != null && ACCEPTED_BLOCKS.contains(blockRegistryId);
        }
        return false;
    }
    
    /**
     * Checks if a block is an accepted fruit/vegetable block.
     * @param block The block
     * @return true if the block is accepted
     */
    public static boolean isFruitVegetableBlock(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        Identifier blockId = Registries.BLOCK.getId(block);
        return blockId != null && ACCEPTED_BLOCKS.contains(blockId);
    }
    
    /**
     * Checks if an item (which might be a block item) is an accepted fruit/vegetable block.
     * @param item The item
     * @return true if the item corresponds to an accepted block
     */
    public static boolean isFruitVegetableBlock(Item item) {
        if (item == null || item == Items.AIR) {
            return false;
        }
        Identifier itemId = Registries.ITEM.getId(item);
        if (itemId == null) {
            return false;
        }
        // Check if the item ID is registered as a block
        if (ACCEPTED_BLOCKS.contains(itemId)) {
            return true;
        }
        // Check if the item corresponds to a block (block items have same ID as blocks)
        Block block = Registries.BLOCK.get(itemId);
        if (block != null && block != Blocks.AIR) {
            return ACCEPTED_BLOCKS.contains(itemId);
        }
        return false;
    }
    
    /**
     * Gets the item identifier that a block converts to.
     * @param blockId The block identifier
     * @return Item identifier, or null if not found
     */
    public static Identifier getItemEquivalent(Identifier blockId) {
        return BLOCK_TO_ITEM_MAP.get(blockId);
    }
    
    /**
     * Gets the item identifier that a block converts to.
     * @param block The block
     * @return Item identifier, or null if not found
     */
    public static Identifier getItemEquivalent(Block block) {
        Identifier blockId = Registries.BLOCK.getId(block);
        return blockId != null ? BLOCK_TO_ITEM_MAP.get(blockId) : null;
    }
    
    /**
     * Converts a block to 9 items.
     * @param block The block
     * @param blockCount Number of blocks
     * @return Item stack with 9 * blockCount items, or null if block is not accepted
     */
    public static ItemStack expandBlockToItems(Block block, int blockCount) {
        Identifier itemId = getItemEquivalent(block);
        if (itemId == null) {
            return null;
        }
        
        Item item = Registries.ITEM.get(itemId);
        if (item == null) {
            return null;
        }
        
        int itemCount = 9 * blockCount;
        return new ItemStack(item, itemCount);
    }
    
    /**
     * Converts a block to 9 items.
     * @param blockId The block identifier
     * @param blockCount Number of blocks
     * @return Item stack with 9 * blockCount items, or null if block is not accepted
     */
    public static ItemStack expandBlockToItems(Identifier blockId, int blockCount) {
        Identifier itemId = getItemEquivalent(blockId);
        if (itemId == null) {
            return null;
        }
        
        Item item = Registries.ITEM.get(itemId);
        if (item == null) {
            return null;
        }
        
        int itemCount = 9 * blockCount;
        return new ItemStack(item, itemCount);
    }
    
    /**
     * Gets all registered block identifiers.
     * @return Set of accepted block identifiers
     */
    public static Set<Identifier> getAcceptedBlocks() {
        return Collections.unmodifiableSet(ACCEPTED_BLOCKS);
    }
    
    /**
     * Registers blocks dynamically at runtime.
     * Useful for registering blocks from mods that load after this mod.
     * This method can be called during mod initialization or when mods are detected.
     * Only registers blocks that actually exist (mods are loaded).
     */
    public static void registerModdedBlocks() {
        int registeredCount = 0;
        
        // Try to register FarmersDelight blocks if the mod is loaded
        // These will only register if the blocks actually exist in the registry
        
        // FarmersDelight crates
        if (registerBlock("farmersdelight:carrot_crate", "minecraft:carrot")) registeredCount++;
        if (registerBlock("farmersdelight:potato_crate", "minecraft:potato")) registeredCount++;
        if (registerBlock("farmersdelight:beetroot_crate", "minecraft:beetroot")) registeredCount++;
        if (registerBlock("farmersdelight:cabbage_crate", "farmersdelight:cabbage")) registeredCount++;
        if (registerBlock("farmersdelight:tomato_crate", "farmersdelight:tomato")) registeredCount++;
        if (registerBlock("farmersdelight:onion_crate", "farmersdelight:onion")) registeredCount++;
        if (registerBlock("farmersdelight:apple_crate", "minecraft:apple")) registeredCount++;
        if (registerBlock("farmersdelight:strawberry_crate", "farmersdelight:strawberry")) registeredCount++;
        
        // FarmersDelight bales and bags
        if (registerBlock("farmersdelight:rice_bag", "farmersdelight:rice")) registeredCount++;
        if (registerBlock("farmersdelight:rice_bale", "farmersdelight:rice")) registeredCount++;
        if (registerBlock("farmersdelight:straw_bale", "farmersdelight:straw")) registeredCount++;
        
        // Farm and Charm mod blocks
        if (registerBlock("farm_and_charm:lettuce_bag", "farm_and_charm:lettuce")) registeredCount++;
        if (registerBlock("farm_and_charm:strawberry_bag", "farm_and_charm:strawberry")) registeredCount++;
        if (registerBlock("farm_and_charm:tomato_bag", "farm_and_charm:tomato")) registeredCount++;
        if (registerBlock("farm_and_charm:carrot_bag", "minecraft:carrot")) registeredCount++;
        if (registerBlock("farm_and_charm:potato_bag", "minecraft:potato")) registeredCount++;
        if (registerBlock("farm_and_charm:onion_bag", "farm_and_charm:onion")) registeredCount++;
        if (registerBlock("farm_and_charm:beetroot_bag", "minecraft:beetroot")) registeredCount++;
        if (registerBlock("farm_and_charm:corn_bag", "farm_and_charm:corn")) registeredCount++;
        if (registerBlock("farm_and_charm:flour_bag", "farm_and_charm:flour")) registeredCount++;
        if (registerBlock("farm_and_charm:oat_bale", "farm_and_charm:oat")) registeredCount++;
        if (registerBlock("farm_and_charm:barley_bale", "farm_and_charm:barley")) registeredCount++;
        
        // Vinery mod blocks
        if (registerBlock("vinery:white_grape_bag", "vinery:white_grape")) registeredCount++;
        if (registerBlock("vinery:red_grape_bag", "vinery:red_grape")) registeredCount++;
        
        // Herbal Brews mod blocks
        if (registerBlock("herbalbrews:cherry_bag", "herbalbrews:cherry")) registeredCount++;
        if (registerBlock("herbalbrews:apple_bag", "herbalbrews:apple")) registeredCount++;
        
        // Vanilla hay bale (if not already registered)
        if (!ACCEPTED_BLOCKS.contains(Registries.BLOCK.getId(Blocks.HAY_BLOCK))) {
            registerBlock(Blocks.HAY_BLOCK, "minecraft:wheat");
            registeredCount++;
        }
        
        SettlementsMod.LOGGER.info("Registered {} modded farming blocks for trading (total: {})", 
            registeredCount, ACCEPTED_BLOCKS.size());
        
        // Log all registered blocks for debugging
        if (SettlementsMod.LOGGER.isDebugEnabled()) {
            for (Identifier blockId : ACCEPTED_BLOCKS) {
                Identifier itemId = BLOCK_TO_ITEM_MAP.get(blockId);
                SettlementsMod.LOGGER.debug("  - {} -> {}", blockId, itemId);
            }
        }
    }
}

