package com.secretasain.settlements.trader;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;

/**
 * Helper class for creating trade offers from trade configurations.
 * Handles fruit/vegetable block conversion and trade offer creation.
 */
public class TradeOfferHelper {
    
    /**
     * Creates a TradeOffer from a TradeOfferConfig.
     * Handles fruit/vegetable block conversion (9 items = 1 block).
     * @param config The trade offer configuration
     * @return TradeOffer or null if creation failed
     */
    public static TradeOffer createTradeOffer(TradeOfferConfig config) {
        if (config == null) {
            return null;
        }
        
        try {
            // Get input item - try as item first, then check if it's a block
            Item inputItem = null;
            Identifier inputId = config.getInputItem();
            
            // First, try to get it as an item directly (most common case)
            inputItem = Registries.ITEM.get(inputId);
            
            // If not found as item, try to get it as a block and convert to item
            if (inputItem == null || inputItem == net.minecraft.item.Items.AIR) {
                net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(inputId);
                if (block != null) {
                    inputItem = block.asItem();
                    
                    // Special case: if block.asItem() returns null, try getting the item by the same identifier
                    // Some blocks have items with the same identifier
                    if (inputItem == null || inputItem == net.minecraft.item.Items.AIR) {
                        // Try one more time with the item registry (maybe it exists but wasn't found before)
                        inputItem = Registries.ITEM.get(inputId);
                        
                        // If still null, the block doesn't have an item form
                        if (inputItem == null || inputItem == net.minecraft.item.Items.AIR) {
                            com.secretasain.settlements.SettlementsMod.LOGGER.warn(
                                "Block {} does not have an item form, cannot create trade offer. " +
                                "Tried: item registry, block.asItem(). Block exists: {}", inputId, block != null);
                            return null;
                        }
                    }
                    
                    if (inputItem != null && inputItem != net.minecraft.item.Items.AIR) {
                        com.secretasain.settlements.SettlementsMod.LOGGER.debug(
                            "Found input {} as block, converted to item: {}", inputId, 
                            net.minecraft.registry.Registries.ITEM.getId(inputItem));
                    }
                }
            }
            
            if (inputItem == null || inputItem == net.minecraft.item.Items.AIR) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn(
                    "Trade offer input item not found: {} (tried as item and block)", inputId);
                return null;
            }
            
            Identifier inputItemId = net.minecraft.registry.Registries.ITEM.getId(inputItem);
            com.secretasain.settlements.SettlementsMod.LOGGER.info(
                "Creating trade offer: {} x{} -> {} x{} (input ID: {})", 
                inputItemId, config.getInputCount(),
                config.getOutputItem(), config.getOutputCount(), inputId);
            
            // Get output item
            Item outputItem = Registries.ITEM.get(config.getOutputItem());
            if (outputItem == null) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("Trade offer output item not found: {}", config.getOutputItem());
                return null;
            }
            
            // Create input stack
            // For fruit/vegetable blocks, config.inputCount is the number of blocks
            // The trade offer will show the block, and we'll accept blocks in trades
            ItemStack inputStack = new ItemStack(inputItem, config.getInputCount());
            
            // Verify input stack is valid (not air)
            if (inputStack.isEmpty() || inputStack.getItem() == net.minecraft.item.Items.AIR) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn(
                    "Cannot create trade offer: input item {} resulted in air stack", inputId);
                return null;
            }
            
            // Create output stack
            ItemStack outputStack = new ItemStack(outputItem, config.getOutputCount());
            
            // Verify output stack is valid (not air)
            if (outputStack.isEmpty() || outputStack.getItem() == net.minecraft.item.Items.AIR) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn(
                    "Cannot create trade offer: output item {} resulted in air stack", config.getOutputItem());
                return null;
            }
            
            // Create trade offer
            // Parameters: input, output, maxUses, experienceReward, priceMultiplier
            // maxUses: number of times trade can be used
            // experienceReward: experience points gained (typically 1-5)
            // priceMultiplier: price adjustment factor (0.05f = 5% increase per use)
            return new TradeOffer(inputStack, outputStack, config.getMaxUses(), 1, 0.05f);
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.error("Failed to create trade offer from config: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Checks if an item stack matches a trade offer input, handling block-to-item conversion.
     * For fruit/vegetable blocks, 1 block = 9 items.
     * @param offerInput The trade offer's input stack
     * @param playerInput The player's input stack
     * @return true if the player's input matches the trade offer input
     */
    public static boolean matchesTradeInput(ItemStack offerInput, ItemStack playerInput) {
        if (offerInput == null || playerInput == null || offerInput.isEmpty() || playerInput.isEmpty()) {
            return false;
        }
        
        // Check if items match
        if (offerInput.getItem() != playerInput.getItem()) {
            // Check if player input is a fruit/vegetable block that converts to the offer input
            Identifier playerItemId = Registries.ITEM.getId(playerInput.getItem());
            if (playerItemId != null) {
                // Check if the item itself is a registered block (block items have the same ID as blocks)
                if (FruitVegetableBlockRegistry.isFruitVegetableBlock(playerItemId)) {
                    Identifier convertedItemId = FruitVegetableBlockRegistry.getItemEquivalent(playerItemId);
                    Identifier offerItemId = Registries.ITEM.getId(offerInput.getItem());
                    if (convertedItemId != null && convertedItemId.equals(offerItemId)) {
                        // Player gave a block, check if quantity matches (1 block = 9 items)
                        int requiredItems = offerInput.getCount();
                        int playerBlocks = playerInput.getCount();
                        int playerItems = playerBlocks * 9;
                        com.secretasain.settlements.SettlementsMod.LOGGER.debug(
                            "Block match: {} blocks = {} items, need {} items", 
                            playerBlocks, playerItems, requiredItems);
                        return playerItems >= requiredItems;
                    }
                }
                
                // Also check if the item corresponds to a block in the block registry
                // (some mods have block items with the same identifier as the block)
                net.minecraft.block.Block playerBlock = net.minecraft.registry.Registries.BLOCK.get(playerItemId);
                if (playerBlock != null && playerBlock != net.minecraft.block.Blocks.AIR) {
                    if (FruitVegetableBlockRegistry.isFruitVegetableBlock(playerBlock)) {
                        Identifier convertedItemId = FruitVegetableBlockRegistry.getItemEquivalent(playerBlock);
                        Identifier offerItemId = Registries.ITEM.getId(offerInput.getItem());
                        if (convertedItemId != null && convertedItemId.equals(offerItemId)) {
                            // Player gave a block item, check if quantity matches (1 block = 9 items)
                            int requiredItems = offerInput.getCount();
                            int playerBlocks = playerInput.getCount();
                            int playerItems = playerBlocks * 9;
                            com.secretasain.settlements.SettlementsMod.LOGGER.debug(
                                "Block item match: {} blocks = {} items, need {} items", 
                                playerBlocks, playerItems, requiredItems);
                            return playerItems >= requiredItems;
                        }
                    }
                }
            }
            return false;
        }
        
        // Items match, check quantity
        return playerInput.getCount() >= offerInput.getCount();
    }
    
    /**
     * Converts player input to match trade offer input, handling block-to-item conversion.
     * @param offerInput The trade offer's input stack
     * @param playerInput The player's input stack
     * @return Converted item stack that matches the offer, or null if conversion not possible
     */
    public static ItemStack convertPlayerInput(ItemStack offerInput, ItemStack playerInput) {
        if (offerInput == null || playerInput == null || offerInput.isEmpty() || playerInput.isEmpty()) {
            return null;
        }
        
        // If items already match, return as-is
        if (offerInput.getItem() == playerInput.getItem()) {
            return playerInput.copy();
        }
        
        // Check if player input is a fruit/vegetable block
        Identifier playerItemId = Registries.ITEM.getId(playerInput.getItem());
        if (playerItemId != null && FruitVegetableBlockRegistry.isFruitVegetableBlock(playerItemId)) {
            Identifier convertedItemId = FruitVegetableBlockRegistry.getItemEquivalent(playerItemId);
            Identifier offerItemId = Registries.ITEM.getId(offerInput.getItem());
            
            if (convertedItemId != null && convertedItemId.equals(offerItemId)) {
                // Convert blocks to items: 1 block = 9 items
                int playerBlocks = playerInput.getCount();
                int playerItems = playerBlocks * 9;
                int requiredItems = offerInput.getCount();
                
                if (playerItems >= requiredItems) {
                    // Create item stack with converted items
                    Item item = Registries.ITEM.get(convertedItemId);
                    if (item != null) {
                        return new ItemStack(item, Math.min(playerItems, requiredItems));
                    }
                }
            }
        }
        
        return null;
    }
}

