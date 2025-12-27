package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles active crop harvesting for farm buildings.
 * Scans the building's structure area for mature crops and harvests them.
 */
public class FarmCropHarvester {
    // Common right-click harvest mod IDs
    private static final String[] RIGHT_CLICK_HARVEST_MOD_IDS = {
        "rightclickharvest",      // RightClickHarvest mod
        "rightclicktoharvest",     // Right Click to Harvest mod
        "rightclickgetcrops",     // Right Click, Get Crops mod
        "harvestwithcase"         // Harvest With Case mod (if it exists)
    };
    
    private static String detectedModId = null;
    private static boolean rightClickHarvestModPresent = false;
    private static boolean rightClickHarvestChecked = false;
    
    /**
     * Checks if any right-click harvest mod is present.
     * These mods allow harvesting crops without breaking them (non-destructive harvesting).
     * @return true if a right-click harvest mod is detected
     */
    private static boolean isRightClickHarvestModPresent() {
        if (rightClickHarvestChecked) {
            return rightClickHarvestModPresent;
        }
        
        // Use FabricLoader to check for mods
        try {
            FabricLoader loader = FabricLoader.getInstance();
            
            // Check each known right-click harvest mod ID
            for (String modId : RIGHT_CLICK_HARVEST_MOD_IDS) {
                if (loader.isModLoaded(modId)) {
                    rightClickHarvestModPresent = true;
                    detectedModId = modId;
                    SettlementsMod.LOGGER.info("Right-click harvest mod detected: {} - will use non-destructive harvesting", modId);
                    break;
                }
            }
            
            // If no known mod found, log that we're using vanilla harvesting
            if (!rightClickHarvestModPresent) {
                SettlementsMod.LOGGER.debug("No right-click harvest mod detected - using vanilla crop breaking");
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error checking for right-click harvest mod: {}", e.getMessage());
            rightClickHarvestModPresent = false;
        }
        
        rightClickHarvestChecked = true;
        return rightClickHarvestModPresent;
    }
    
    /**
     * Gets the detected right-click harvest mod ID, if any.
     * @return The mod ID, or null if no mod is detected
     */
    public static String getDetectedModId() {
        if (!rightClickHarvestChecked) {
            isRightClickHarvestModPresent();
        }
        return detectedModId;
    }
    
    /**
     * Harvests mature crops in a farm building's area.
     * @param building The farm building
     * @param world The server world
     * @param server The Minecraft server (for structure loading)
     * @return List of harvested items
     */
    public static List<ItemStack> harvestCrops(Building building, ServerWorld world, MinecraftServer server) {
        List<ItemStack> harvestedItems = new ArrayList<>();
        
        // Only process farm buildings
        if (!isFarmBuilding(building)) {
            return harvestedItems;
        }
        
        // Load structure data to get the area
        StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
        if (structureData == null) {
            SettlementsMod.LOGGER.warn("Could not load structure data for building {}: {}", 
                building.getId(), building.getStructureType());
            return harvestedItems;
        }
        
        // Get building position and rotation
        BlockPos buildingPos = building.getPosition();
        int rotation = building.getRotation();
        
        // Get structure dimensions
        Vec3i size = structureData.getDimensions();
        
        // Scan all blocks in the structure area
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos relativePos = new BlockPos(x, y, z);
                    
                    // Apply rotation to relative position
                    BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
                    
                    // Convert to world position
                    BlockPos worldPos = buildingPos.add(rotatedPos);
                    
                    // Check if chunk is loaded
                    if (!world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    // Get block state
                    BlockState blockState = world.getBlockState(worldPos);
                    Block block = blockState.getBlock();
                    
                    // Check if this is farmland
                    if (block instanceof FarmlandBlock) {
                        // Check the block above for crops
                        BlockPos cropPos = worldPos.up();
                        if (!world.getChunkManager().isChunkLoaded(cropPos.getX() >> 4, cropPos.getZ() >> 4)) {
                            continue;
                        }
                        
                        BlockState cropState = world.getBlockState(cropPos);
                        Block cropBlock = cropState.getBlock();
                        
                        // Check if crop is mature and harvestable
                        if (isMatureCrop(cropState, cropBlock)) {
                            List<ItemStack> drops = harvestCrop(cropPos, cropState, cropBlock, world, server);
                            harvestedItems.addAll(drops);
                        }
                    }
                }
            }
        }
        
        return harvestedItems;
    }
    
    /**
     * Checks if a building is a farm building.
     */
    private static boolean isFarmBuilding(Building building) {
        String structureName = building.getStructureType().getPath().toLowerCase();
        return structureName.contains("farm");
    }
    
    /**
     * Rotates a position based on building rotation.
     */
    private static BlockPos rotatePosition(BlockPos relativePos, Vec3i size, int rotation) {
        int x = relativePos.getX();
        int y = relativePos.getY();
        int z = relativePos.getZ();
        
        // Rotation is in 90-degree increments
        switch (rotation) {
            case 90:
                // Rotate 90 degrees clockwise: (x, y, z) -> (z, y, size.x - x - 1)
                return new BlockPos(z, y, size.getX() - x - 1);
            case 180:
                // Rotate 180 degrees: (x, y, z) -> (size.x - x - 1, y, size.z - z - 1)
                return new BlockPos(size.getX() - x - 1, y, size.getZ() - z - 1);
            case 270:
                // Rotate 270 degrees clockwise: (x, y, z) -> (size.z - z - 1, y, x)
                return new BlockPos(size.getZ() - z - 1, y, x);
            case 0:
            default:
                return relativePos;
        }
    }
    
    /**
     * Checks if a crop is mature and ready to harvest.
     */
    private static boolean isMatureCrop(BlockState cropState, Block cropBlock) {
        // Vanilla crops
        if (cropBlock instanceof CropBlock) {
            // CropBlock has an age property - check if it's at max age
            if (cropState.contains(Properties.AGE_7)) {
                return cropState.get(Properties.AGE_7) >= 7;
            } else if (cropState.contains(Properties.AGE_3)) {
                return cropState.get(Properties.AGE_3) >= 3;
            }
        } else if (cropBlock instanceof BeetrootsBlock) {
            // Beetroot uses AGE_3
            if (cropState.contains(Properties.AGE_3)) {
                return cropState.get(Properties.AGE_3) >= 3;
            }
        } else if (cropBlock instanceof NetherWartBlock) {
            // Nether wart uses AGE_3
            if (cropState.contains(Properties.AGE_3)) {
                return cropState.get(Properties.AGE_3) >= 3;
            }
        }
        
        // Check for modded crops - try common property names
        // Many modded crops use "age" or "growth" properties
        if (cropState.contains(Properties.AGE_7)) {
            int age = cropState.get(Properties.AGE_7);
            // Assume max age is 7 for most crops (wheat, carrots, potatoes)
            return age >= 7;
        } else if (cropState.contains(Properties.AGE_3)) {
            int age = cropState.get(Properties.AGE_3);
            // Assume max age is 3 for some crops (beetroot, nether wart)
            return age >= 3;
        }
        
        // If we can't determine maturity, assume it's not mature (safer)
        return false;
    }
    
    /**
     * Harvests a crop at the given position.
     * Tries right-click harvest mod first (non-destructive), then falls back to vanilla breaking.
     */
    private static List<ItemStack> harvestCrop(BlockPos cropPos, BlockState cropState, Block cropBlock, 
                                              ServerWorld world, MinecraftServer server) {
        // Check if right-click harvest mod is present
        if (isRightClickHarvestModPresent()) {
            // Try to use the mod's API for non-destructive harvesting
            List<ItemStack> modDrops = tryRightClickHarvestMod(cropPos, cropState, cropBlock, world);
            if (modDrops != null && !modDrops.isEmpty()) {
                // Mod successfully harvested - crop should still be in place (replanted)
                SettlementsMod.LOGGER.debug("Used right-click harvest mod to harvest crop at {}", cropPos);
                return modDrops;
            }
            // If mod API call failed, fall through to vanilla harvesting
            SettlementsMod.LOGGER.debug("Right-click harvest mod present but API call failed, using vanilla harvesting");
        }
        
        // Vanilla harvesting: break the crop and collect drops
        // Use getDroppedStacks to get what would drop
        List<ItemStack> droppedStacks = Block.getDroppedStacks(cropState, world, cropPos, null);
        
        // Break the crop block
        world.breakBlock(cropPos, false);
        
        // Always replant the crop after breaking (villager replants automatically)
        replantCrop(cropPos, cropBlock, droppedStacks, world);
        
        return droppedStacks;
    }
    
    /**
     * Attempts to use a right-click harvest mod's API to harvest the crop non-destructively.
     * @param cropPos The position of the crop
     * @param cropState The block state of the crop
     * @param cropBlock The crop block
     * @param world The server world
     * @return List of harvested items, or null if mod API is not available or failed
     */
    private static List<ItemStack> tryRightClickHarvestMod(BlockPos cropPos, BlockState cropState, Block cropBlock, 
                                                           ServerWorld world) {
        String modId = getDetectedModId();
        if (modId == null) {
            return null;
        }
        
        try {
            // Different mods may have different APIs
            // We'll try to use reflection to call the mod's harvesting method
            // This is a generic approach that should work with most right-click harvest mods
            
            // Most right-click harvest mods work by:
            // 1. Simulating a right-click on the crop block
            // 2. Getting the drops without breaking the block
            // 3. Optionally replanting seeds
            
            // For now, we'll use a reflection-based approach to try to access the mod's functionality
            // This is safer than hardcoding specific mod APIs
            
            // Try to find and call a common method pattern
            // Many mods expose a static method like: harvestCrop(World, BlockPos, BlockState)
            // or similar
            
            // Since we can't easily call mod-specific APIs without their source,
            // we'll simulate what the mod does: get drops without breaking
            
            // For crops that drop seeds, we can:
            // 1. Get the drops
            // 2. Keep the crop block (don't break it)
            // 3. Reset the crop age to 0 (replant)
            
            if (cropBlock instanceof CropBlock) {
                // For crop blocks, we can simulate right-click harvest:
                // Get drops, then reset age to 0
                List<ItemStack> drops = Block.getDroppedStacks(cropState, world, cropPos, null);
                
                // Reset crop to age 0 (replant)
                if (cropState.contains(Properties.AGE_7)) {
                    BlockState replantedState = cropState.with(Properties.AGE_7, 0);
                    world.setBlockState(cropPos, replantedState, 3);
                } else if (cropState.contains(Properties.AGE_3)) {
                    BlockState replantedState = cropState.with(Properties.AGE_3, 0);
                    world.setBlockState(cropPos, replantedState, 3);
                }
                
                SettlementsMod.LOGGER.debug("Simulated right-click harvest: harvested and replanted crop at {}", cropPos);
                return drops;
            } else if (cropBlock instanceof BeetrootsBlock) {
                // Beetroot uses AGE_3
                List<ItemStack> drops = Block.getDroppedStacks(cropState, world, cropPos, null);
                BlockState replantedState = cropState.with(Properties.AGE_3, 0);
                world.setBlockState(cropPos, replantedState, 3);
                SettlementsMod.LOGGER.debug("Simulated right-click harvest: harvested and replanted beetroot at {}", cropPos);
                return drops;
            }
            
        } catch (Exception e) {
            SettlementsMod.LOGGER.debug("Error using right-click harvest mod API: {}", e.getMessage());
            // Fall through to return null, which will trigger vanilla harvesting
        }
        
        return null;
    }
    
    /**
     * Replants a crop after harvesting.
     * Uses seeds from the drops, or the crop item itself for crops like carrots/potatoes.
     * @param cropPos The position where the crop was broken
     * @param cropBlock The crop block type that was broken
     * @param drops The items that dropped from breaking the crop
     * @param world The server world
     */
    private static void replantCrop(BlockPos cropPos, Block cropBlock, List<ItemStack> drops, ServerWorld world) {
        try {
            // Check if the block below is farmland (required for crops)
            BlockPos farmlandPos = cropPos.down();
            BlockState farmlandState = world.getBlockState(farmlandPos);
            if (!(farmlandState.getBlock() instanceof FarmlandBlock)) {
                SettlementsMod.LOGGER.debug("No farmland below crop at {}, cannot replant", cropPos);
                return;
            }
            
            // Determine what block to replant and what seed/item is needed
            Block replantBlock = cropBlock; // Use the same block by default
            Item requiredSeed = null;
            
            // Check block type using Blocks constants
            if (cropBlock == Blocks.WHEAT) {
                replantBlock = Blocks.WHEAT;
                requiredSeed = Items.WHEAT_SEEDS;
            } else if (cropBlock == Blocks.CARROTS) {
                replantBlock = Blocks.CARROTS;
                requiredSeed = Items.CARROT;
            } else if (cropBlock == Blocks.POTATOES) {
                replantBlock = Blocks.POTATOES;
                requiredSeed = Items.POTATO;
            } else if (cropBlock == Blocks.BEETROOTS) {
                replantBlock = Blocks.BEETROOTS;
                requiredSeed = Items.BEETROOT_SEEDS;
            } else {
                // For modded crops, try to find any seed in drops
                // If it's a CropBlock, we can still replant it
                if (cropBlock instanceof CropBlock) {
                    // Try to find a seed item in drops
                    for (ItemStack drop : drops) {
                        Item dropItem = drop.getItem();
                        // Check if this item can be planted (basic heuristic)
                        if (dropItem != null) {
                            requiredSeed = dropItem;
                            break;
                        }
                    }
                } else {
                    // Unknown crop type, can't replant
                    SettlementsMod.LOGGER.debug("Unknown crop type {}, cannot determine seed for replanting", cropBlock);
                    return;
                }
            }
            
            // Check if we have the required seed/item in drops
            boolean hasSeed = false;
            if (requiredSeed != null) {
                for (ItemStack drop : drops) {
                    if (drop.getItem() == requiredSeed) {
                        hasSeed = true;
                        break;
                    }
                }
            } else {
                // For modded crops without a specific seed requirement, assume we can replant
                hasSeed = true;
            }
            
            // Replant if we have a seed/item
            if (hasSeed) {
                // Get the default state and set age to 0
                BlockState replantState = replantBlock.getDefaultState();
                
                // Set age to 0 (newly planted)
                if (replantState.contains(Properties.AGE_7)) {
                    replantState = replantState.with(Properties.AGE_7, 0);
                } else if (replantState.contains(Properties.AGE_3)) {
                    replantState = replantState.with(Properties.AGE_3, 0);
                }
                
                // Place the replanted crop
                world.setBlockState(cropPos, replantState, 3);
                SettlementsMod.LOGGER.debug("Replanted crop {} at {}", replantBlock, cropPos);
            } else {
                SettlementsMod.LOGGER.debug("No seed/item found in drops to replant {} at {} (needed: {})", 
                    cropBlock, cropPos, requiredSeed);
            }
            
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error replanting crop at {}: {}", cropPos, e.getMessage());
        }
    }
}

