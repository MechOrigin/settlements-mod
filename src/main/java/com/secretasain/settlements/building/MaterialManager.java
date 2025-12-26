package com.secretasain.settlements.building;

import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages material calculations and consumption for buildings.
 * Handles conversion between blocks and items, and tracks material requirements.
 */
public class MaterialManager {
    
    /**
     * Calculates required materials from a structure.
     * Converts BlockState counts to Item/Identifier counts.
     * @param structure The structure data
     * @return Map of material Identifier to required count
     */
    public static Map<Identifier, Integer> calculateMaterials(StructureData structure) {
        Map<Identifier, Integer> materials = new HashMap<>();
        Map<BlockState, Integer> blockCounts = structure.getMaterialRequirements();
        
        for (Map.Entry<BlockState, Integer> entry : blockCounts.entrySet()) {
            BlockState state = entry.getKey();
            int count = entry.getValue();
            
            // Skip air blocks
            if (state.isAir()) {
                continue;
            }
            
            // Convert BlockState to Item
            Block block = state.getBlock();
            Item item = block.asItem();
            
            // If block doesn't have an item (like barrier blocks), skip it
            if (item == null) {
                continue;
            }
            
            // Get item identifier
            Identifier itemId = Registries.ITEM.getId(item);
            if (itemId != null) {
                // Group by item type (all variants of the same item together)
                materials.put(itemId, materials.getOrDefault(itemId, 0) + count);
            }
        }
        
        return materials;
    }
    
    /**
     * Gets available materials in a settlement.
     * @param settlement The settlement
     * @return Map of material Identifier to available count
     */
    public static Map<Identifier, Integer> getAvailableMaterials(Settlement settlement) {
        Map<Identifier, Integer> available = new HashMap<>();
        Map<String, Integer> settlementMaterials = settlement.getMaterials();
        
        // Convert String keys to Identifier
        for (Map.Entry<String, Integer> entry : settlementMaterials.entrySet()) {
            Identifier materialId = Identifier.tryParse(entry.getKey());
            if (materialId != null) {
                available.put(materialId, entry.getValue());
            }
        }
        
        return available;
    }
    
    /**
     * Checks if a settlement can afford the required materials.
     * @param settlement The settlement
     * @param requiredMaterials Map of material Identifier to required count
     * @return true if all required materials are available
     */
    public static boolean canAfford(Settlement settlement, Map<Identifier, Integer> requiredMaterials) {
        Map<Identifier, Integer> available = getAvailableMaterials(settlement);
        
        for (Map.Entry<Identifier, Integer> entry : requiredMaterials.entrySet()) {
            Identifier materialId = entry.getKey();
            int required = entry.getValue();
            int availableCount = available.getOrDefault(materialId, 0);
            
            if (availableCount < required) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Consumes materials from a settlement's storage.
     * @param settlement The settlement
     * @param materials Map of material Identifier to count to consume
     * @return true if all materials were successfully consumed, false if insufficient materials
     */
    public static boolean consumeMaterials(Settlement settlement, Map<Identifier, Integer> materials) {
        // First check if we can afford it
        if (!canAfford(settlement, materials)) {
            return false;
        }
        
        // Consume materials
        Map<String, Integer> settlementMaterials = settlement.getMaterials();
        for (Map.Entry<Identifier, Integer> entry : materials.entrySet()) {
            Identifier materialId = entry.getKey();
            int amount = entry.getValue();
            String materialKey = materialId.toString();
            
            int current = settlementMaterials.getOrDefault(materialKey, 0);
            int newAmount = current - amount;
            
            if (newAmount <= 0) {
                settlementMaterials.remove(materialKey);
            } else {
                settlementMaterials.put(materialKey, newAmount);
            }
        }
        
        return true;
    }
    
    /**
     * Adds materials to a settlement's storage.
     * @param settlement The settlement
     * @param materials Map of material Identifier to count to add
     */
    public static void addMaterials(Settlement settlement, Map<Identifier, Integer> materials) {
        Map<String, Integer> settlementMaterials = settlement.getMaterials();
        
        for (Map.Entry<Identifier, Integer> entry : materials.entrySet()) {
            Identifier materialId = entry.getKey();
            int amount = entry.getValue();
            String materialKey = materialId.toString();
            
            int current = settlementMaterials.getOrDefault(materialKey, 0);
            settlementMaterials.put(materialKey, current + amount);
        }
    }
    
    /**
     * Consumes materials for a specific building and updates the building's provided materials.
     * @param building The building
     * @param settlement The settlement
     * @return true if materials were successfully consumed
     */
    public static boolean consumeMaterialsForBuilding(Building building, Settlement settlement) {
        Map<Identifier, Integer> required = building.getRequiredMaterials();
        
        if (required.isEmpty()) {
            // No materials required, consider it successful
            return true;
        }
        
        // Check if we can afford it
        if (!canAfford(settlement, required)) {
            return false;
        }
        
        // CRITICAL: Clear providedMaterials first to prevent duplication
        // If building was started before and cancelled, materials might still be in providedMaterials
        building.clearProvidedMaterials();
        
        // Consume materials from settlement
        if (!consumeMaterials(settlement, required)) {
            return false;
        }
        
        // Set building's provided materials (use set, not add, to prevent duplication)
        for (Map.Entry<Identifier, Integer> entry : required.entrySet()) {
            building.setProvidedMaterial(entry.getKey(), entry.getValue());
        }
        
        return true;
    }
    
    /**
     * Returns materials from a building back to settlement storage.
     * Used when a building is cancelled.
     * Returns the exact same amount that was consumed when the building started.
     * @param building The building to return materials from
     * @param settlement The settlement to return materials to
     */
    public static void returnMaterials(Building building, Settlement settlement) {
        Map<Identifier, Integer> provided = building.getProvidedMaterials();
        
        if (provided.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("No materials to return for building {} (no materials were provided)", building.getId());
            return;
        }
        
        // Log what we're returning
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Returning materials for building {}: {}", building.getId(), provided);
        
        // Return materials to settlement (exact same amount that was consumed)
        addMaterials(settlement, provided);
        
        // Clear provided materials from building since they've been returned
        // Note: We don't clear required materials as they're still needed for reference
        building.clearProvidedMaterials();
    }
    
    /**
     * Returns materials from a building back to adjacent chests.
     * Deposits items into chests adjacent to the lectern, similar to how materials are extracted.
     * Returns the exact same amount that was consumed when the building started.
     * @param building The building to return materials from
     * @param settlement The settlement containing the lectern
     * @param world The server world
     */
    public static void returnMaterialsToChests(Building building, Settlement settlement, ServerWorld world) {
        Map<Identifier, Integer> provided = building.getProvidedMaterials();
        
        if (provided.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("No materials to return for building {} (no materials were provided - building may not have started yet)", building.getId());
            return;
        }
        
        // Log what we're returning
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Returning materials to chests for building {}: {} material types, total items: {}", 
            building.getId(), provided.size(), provided.values().stream().mapToInt(Integer::intValue).sum());
        
        BlockPos lecternPos = settlement.getLecternPos();
        if (lecternPos == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot return materials to chests: lectern position is null");
            // Fallback to settlement storage
            returnMaterials(building, settlement);
            return;
        }
        
        // Find adjacent chests
        java.util.List<ChestBlockEntity> chests = new java.util.ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos chestPos = lecternPos.offset(direction);
            BlockState blockState = world.getBlockState(chestPos);
            Block block = blockState.getBlock();
            
            if (block instanceof ChestBlock) {
                BlockEntity blockEntity = world.getBlockEntity(chestPos);
                if (blockEntity instanceof ChestBlockEntity) {
                    chests.add((ChestBlockEntity) blockEntity);
                }
            }
        }
        
        if (chests.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("No adjacent chests found for returning materials, falling back to settlement storage");
            // Fallback to settlement storage if no chests found
            returnMaterials(building, settlement);
            return;
        }
        
        // Distribute materials across available chests
        int totalDeposited = 0;
        Map<Identifier, Integer> remainderForStorage = new HashMap<>();
        
        for (Map.Entry<Identifier, Integer> entry : provided.entrySet()) {
            Identifier itemId = entry.getKey();
            int amount = entry.getValue();
            
            Item item = Registries.ITEM.get(itemId);
            if (item == null) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot return material {}: item not found in registry", itemId);
                // Add to storage as fallback
                remainderForStorage.put(itemId, amount);
                continue;
            }
            
            // Try to deposit this material into chests
            // Go through all chests and try to deposit items
            int remaining = amount;
            boolean madeProgress = true;
            
            while (remaining > 0 && madeProgress) {
                madeProgress = false;
                
                // Try each chest
                for (ChestBlockEntity chest : chests) {
                    if (remaining <= 0) break;
                    
                    // Try to find a slot with the same item or an empty slot
                    for (int slot = 0; slot < chest.size() && remaining > 0; slot++) {
                        ItemStack stack = chest.getStack(slot);
                        
                        if (stack.isEmpty()) {
                            // Empty slot - place as much as possible (max stack size)
                            int toPlace = Math.min(remaining, item.getMaxCount());
                            chest.setStack(slot, new ItemStack(item, toPlace));
                            remaining -= toPlace;
                            totalDeposited += toPlace;
                            madeProgress = true;
                            chest.markDirty();
                        } else if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
                            // Same item with space - add to existing stack
                            int toAdd = Math.min(remaining, stack.getMaxCount() - stack.getCount());
                            stack.increment(toAdd);
                            remaining -= toAdd;
                            totalDeposited += toAdd;
                            madeProgress = true;
                            chest.markDirty();
                        }
                    }
                }
            }
            
            // If we couldn't deposit all items, add remainder to settlement storage
            if (remaining > 0) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("Could not deposit all {} items ({} remaining), adding to settlement storage", 
                    itemId, remaining);
                remainderForStorage.put(itemId, remaining);
            }
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Deposited {} items into {} chest(s) for building {}", 
            totalDeposited, chests.size(), building.getId());
        
        // CRITICAL FIX: Only add remaining materials that couldn't fit in chests to settlement storage
        // DO NOT add all materials back - this was causing duplication!
        // Materials were already consumed from settlement storage when building started,
        // so we should only add back what couldn't fit in chests
        if (!remainderForStorage.isEmpty()) {
            addMaterials(settlement, remainderForStorage);
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("Added {} remaining materials to settlement storage", remainderForStorage.size());
        }
        
        // Clear provided materials from building since they've been returned
        building.clearProvidedMaterials();
    }
    
    /**
     * Unloads materials from settlement storage directly to adjacent chests.
     * This allows players to retrieve excess materials that were deposited but not used.
     * @param settlement The settlement containing the materials
     * @param world The server world
     * @param materialsToUnload Map of material Identifier to count to unload (all materials if null)
     */
    public static void unloadSettlementStorageToChests(Settlement settlement, ServerWorld world, 
                                                       Map<Identifier, Integer> materialsToUnload) {
        BlockPos lecternPos = settlement.getLecternPos();
        if (lecternPos == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot unload settlement storage: lectern position is null");
            return;
        }
        
        // Find adjacent chests
        java.util.List<ChestBlockEntity> chests = new java.util.ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos chestPos = lecternPos.offset(direction);
            BlockState blockState = world.getBlockState(chestPos);
            Block block = blockState.getBlock();
            
            if (block instanceof ChestBlock) {
                BlockEntity blockEntity = world.getBlockEntity(chestPos);
                if (blockEntity instanceof ChestBlockEntity) {
                    chests.add((ChestBlockEntity) blockEntity);
                }
            }
        }
        
        if (chests.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("No adjacent chests found for unloading settlement storage");
            return;
        }
        
        // If materialsToUnload is null, unload all materials from settlement storage
        Map<Identifier, Integer> materials = materialsToUnload;
        if (materials == null) {
            materials = getAvailableMaterials(settlement);
        }
        
        if (materials.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("No materials to unload from settlement storage");
            return;
        }
        
        // Distribute materials across available chests
        int totalDeposited = 0;
        Map<String, Integer> settlementMaterials = settlement.getMaterials();
        
        for (Map.Entry<Identifier, Integer> entry : materials.entrySet()) {
            Identifier itemId = entry.getKey();
            int amountToUnload = entry.getValue();
            
            // Get the actual amount available in settlement storage
            String materialKey = itemId.toString();
            int available = settlementMaterials.getOrDefault(materialKey, 0);
            int amount = Math.min(amountToUnload, available); // Don't unload more than available
            
            if (amount <= 0) {
                continue; // Skip if no materials available
            }
            
            Item item = Registries.ITEM.get(itemId);
            if (item == null) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot unload material {}: item not found in registry", itemId);
                continue;
            }
            
            // Try to deposit this material into chests
            int remaining = amount;
            boolean madeProgress = true;
            
            while (remaining > 0 && madeProgress) {
                madeProgress = false;
                
                // Try each chest
                for (ChestBlockEntity chest : chests) {
                    if (remaining <= 0) break;
                    
                    // Try to find a slot with the same item or an empty slot
                    for (int slot = 0; slot < chest.size() && remaining > 0; slot++) {
                        ItemStack stack = chest.getStack(slot);
                        
                        if (stack.isEmpty()) {
                            // Empty slot - place as much as possible (max stack size)
                            int toPlace = Math.min(remaining, item.getMaxCount());
                            chest.setStack(slot, new ItemStack(item, toPlace));
                            remaining -= toPlace;
                            totalDeposited += toPlace;
                            madeProgress = true;
                            chest.markDirty();
                        } else if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
                            // Same item with space - add to existing stack
                            int toAdd = Math.min(remaining, stack.getMaxCount() - stack.getCount());
                            stack.increment(toAdd);
                            remaining -= toAdd;
                            totalDeposited += toAdd;
                            madeProgress = true;
                            chest.markDirty();
                        }
                    }
                }
            }
            
            // Remove deposited amount from settlement storage
            int deposited = amount - remaining;
            if (deposited > 0) {
                int current = settlementMaterials.getOrDefault(materialKey, 0);
                int newAmount = current - deposited;
                
                if (newAmount <= 0) {
                    settlementMaterials.remove(materialKey);
                } else {
                    settlementMaterials.put(materialKey, newAmount);
                }
            }
            
            // If we couldn't deposit all items, keep remainder in settlement storage
            if (remaining > 0) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Could not deposit all {} items ({} remaining), keeping in settlement storage", 
                    itemId, remaining);
            }
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Deposited {} items from settlement storage into {} chest(s)", 
            totalDeposited, chests.size());
    }
}

