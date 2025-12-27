package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.MaterialManager;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Network packet to check adjacent chests for materials and add them to settlement storage.
 * Sent from client when player clicks "Check for Materials" button.
 */
public class CheckMaterialsPacket {
    public static final Identifier ID = new Identifier("settlements", "check_materials");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            // Read building ID (can be null to extract all materials)
            boolean hasBuilding = buf.readBoolean();
            UUID buildingId = hasBuilding ? buf.readUuid() : null;
            UUID settlementId = buf.readUuid();
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    SettlementManager manager = SettlementManager.getInstance(world);
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot check materials: settlement {} not found", settlementId);
                        player.sendMessage(net.minecraft.text.Text.literal("Settlement not found"), false);
                        return;
                    }
                    
                    BlockPos lecternPos = settlement.getLecternPos();
                    if (lecternPos == null) {
                        SettlementsMod.LOGGER.warn("Cannot check materials: lectern position is null");
                        player.sendMessage(net.minecraft.text.Text.literal("Invalid lectern position"), false);
                        return;
                    }
                    
                    // Get required materials if building is specified
                    Map<Identifier, Integer> requiredMaterials = null;
                    if (buildingId != null) {
                        com.secretasain.settlements.settlement.Building building = null;
                        for (com.secretasain.settlements.settlement.Building b : settlement.getBuildings()) {
                            if (b.getId().equals(buildingId)) {
                                building = b;
                                break;
                            }
                        }
                        
                        if (building == null) {
                            SettlementsMod.LOGGER.warn("Cannot check materials: building {} not found", buildingId);
                            player.sendMessage(net.minecraft.text.Text.literal("Building not found"), false);
                            return;
                        }
                        
                        requiredMaterials = building.getRequiredMaterials();
                        
                        SettlementsMod.LOGGER.info("Building {} found - status: {}, requiredMaterials size: {}", 
                            buildingId, building.getStatus(), requiredMaterials.size());
                        
                        if (requiredMaterials.isEmpty()) {
                            SettlementsMod.LOGGER.warn("Building {} has empty required materials map!", buildingId);
                            player.sendMessage(net.minecraft.text.Text.literal("Selected building has no material requirements. Materials may not have been calculated when building was created."), false);
                            return;
                        }
                        
                        // Log all required materials for debugging
                        SettlementsMod.LOGGER.info("Checking materials for building {}: {} required material types", 
                            buildingId, requiredMaterials.size());
                        for (Map.Entry<Identifier, Integer> entry : requiredMaterials.entrySet()) {
                            SettlementsMod.LOGGER.info("  Required: {} x{}", entry.getKey(), entry.getValue());
                        }
                    }
                    
                    // Search for chests within a radius around the lectern (similar to VillagerDepositSystem)
                    // This allows chests to be placed nearby but not necessarily adjacent
                    double CHEST_SEARCH_RADIUS = 8.0; // Search within 8 blocks
                    Map<Identifier, Integer> foundMaterials = new HashMap<>();
                    int chestsChecked = 0;
                    List<BlockPos> checkedPositions = new ArrayList<>();
                    
                    // Search in a radius around the lectern
                    int radius = (int) Math.ceil(CHEST_SEARCH_RADIUS);
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                if (dx == 0 && dy == 0 && dz == 0) {
                                    continue; // Skip lectern position itself
                                }
                                
                                BlockPos chestPos = lecternPos.add(dx, dy, dz);
                                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                
                                if (distance <= CHEST_SEARCH_RADIUS && !checkedPositions.contains(chestPos)) {
                                    checkedPositions.add(chestPos);
                                    
                                    BlockState blockState = world.getBlockState(chestPos);
                                    Block block = blockState.getBlock();
                                    
                                    // Check if it's a chest block
                                    if (block instanceof ChestBlock) {
                                        BlockEntity blockEntity = world.getBlockEntity(chestPos);
                                        if (blockEntity instanceof ChestBlockEntity) {
                                            ChestBlockEntity chestEntity = (ChestBlockEntity) blockEntity;
                                            
                                            chestsChecked++;
                                            SettlementsMod.LOGGER.info("Found chest at {} (distance: {:.2f} blocks)", chestPos, distance);
                                            
                                            // Extract items from chest - only required materials if building is specified
                                            for (int i = 0; i < chestEntity.size(); i++) {
                                    ItemStack stack = chestEntity.getStack(i);
                                    if (!stack.isEmpty()) {
                                        Item item = stack.getItem();
                                        Identifier itemId = Registries.ITEM.getId(item);
                                        
                                        SettlementsMod.LOGGER.debug("Chest slot {}: {} x{}", i, itemId, stack.getCount());
                                        
                                        if (itemId != null) {
                                            // If building is specified, only extract required materials
                                            if (requiredMaterials != null && !requiredMaterials.isEmpty()) {
                                                Integer required = requiredMaterials.get(itemId);
                                                if (required == null) {
                                                    // Not a required material, skip it
                                                    // NOTE: This means items not in the required list won't be extracted
                                                    // If you want to extract ALL materials regardless, set requiredMaterials to null
                                                    SettlementsMod.LOGGER.debug("Skipping item {} - not in required materials list (required materials: {})", 
                                                        itemId, requiredMaterials.keySet());
                                                    continue;
                                                }
                                                
                                                // CRITICAL FIX: Calculate how much we still need
                                                // Account for:
                                                // 1. What's already in settlement storage
                                                // 2. What we've already taken from chests in this operation (foundMaterials)
                                                int alreadyInStorage = settlement.getMaterials().getOrDefault(itemId.toString(), 0);
                                                int alreadyTaken = foundMaterials.getOrDefault(itemId, 0);
                                                int totalHave = alreadyInStorage + alreadyTaken;
                                                int stillNeeded = Math.max(0, required - totalHave);
                                                
                                                SettlementsMod.LOGGER.debug("Item {}: required={}, inStorage={}, alreadyTaken={}, totalHave={}, stillNeeded={}, stackCount={}", 
                                                    itemId, required, alreadyInStorage, alreadyTaken, totalHave, stillNeeded, stack.getCount());
                                                
                                                if (stillNeeded <= 0) {
                                                    // Already have enough (including what we've taken), skip this item
                                                    SettlementsMod.LOGGER.debug("Skipping item {} - already have enough ({} >= {})", 
                                                        itemId, totalHave, required);
                                                    continue;
                                                }
                                                
                                                // Only take exactly what we need (no more, no less)
                                                int toTake = Math.min(stack.getCount(), stillNeeded);
                                                
                                                if (toTake > 0) {
                                                    // Update foundMaterials with what we're taking
                                                    foundMaterials.put(itemId, foundMaterials.getOrDefault(itemId, 0) + toTake);
                                                    
                                                    SettlementsMod.LOGGER.info("Taking {} of {} from chest (needed {}, had {} in storage, {} already taken)", 
                                                        toTake, itemId, stillNeeded, alreadyInStorage, alreadyTaken);
                                                    
                                                    // Remove only the amount we took from chest
                                                    if (toTake >= stack.getCount()) {
                                                        chestEntity.setStack(i, ItemStack.EMPTY);
                                                    } else {
                                                        // Create a new stack with the remaining items
                                                        ItemStack newStack = stack.copy();
                                                        newStack.setCount(stack.getCount() - toTake);
                                                        chestEntity.setStack(i, newStack);
                                                    }
                                                }
                                            } else {
                                                // No building specified - extract all materials (old behavior for compatibility)
                                                SettlementsMod.LOGGER.debug("No building specified - extracting all materials");
                                                int count = stack.getCount();
                                                foundMaterials.put(itemId, foundMaterials.getOrDefault(itemId, 0) + count);
                                                
                                                // Remove item from chest
                                                chestEntity.setStack(i, ItemStack.EMPTY);
                                            }
                                        }
                                    }
                                            }
                                            
                                            // Mark chest inventory as changed
                                            chestEntity.markDirty();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (chestsChecked == 0) {
                        SettlementsMod.LOGGER.warn("No chests found within {} blocks of lectern at {}", CHEST_SEARCH_RADIUS, lecternPos);
                        player.sendMessage(net.minecraft.text.Text.literal(String.format("No chests found within %.0f blocks of lectern", CHEST_SEARCH_RADIUS)), false);
                        return;
                    }
                    
                    SettlementsMod.LOGGER.info("Checked {} chest(s), found {} different material types", chestsChecked, foundMaterials.size());
                    
                    // Add found materials to settlement storage
                    if (!foundMaterials.isEmpty()) {
                        // Log what we're adding
                        for (Map.Entry<Identifier, Integer> entry : foundMaterials.entrySet()) {
                            SettlementsMod.LOGGER.info("Adding to settlement storage: {} x{}", entry.getKey(), entry.getValue());
                        }
                        
                        MaterialManager.addMaterials(settlement, foundMaterials);
                        manager.markDirty();
                        
                        // Send updated materials to client
                        SyncMaterialsPacket.send(player, settlement);
                        
                        int totalItems = foundMaterials.values().stream().mapToInt(Integer::intValue).sum();
                        SettlementsMod.LOGGER.info("Player {} checked materials: found {} items from {} chest(s)", 
                            player.getName().getString(), totalItems, chestsChecked);
                        player.sendMessage(net.minecraft.text.Text.literal(
                            String.format("Found %d items from %d chest(s)", totalItems, chestsChecked)), false);
                    } else {
                        if (requiredMaterials != null && !requiredMaterials.isEmpty()) {
                            SettlementsMod.LOGGER.warn("No required materials found in chests. Required: {}, but found nothing.", requiredMaterials.keySet());
                            player.sendMessage(net.minecraft.text.Text.literal(
                                String.format("No required materials found in adjacent chests. Required: %s", 
                                    requiredMaterials.keySet().toString())), false);
                        } else {
                            SettlementsMod.LOGGER.info("No items found in adjacent chests");
                            player.sendMessage(net.minecraft.text.Text.literal("No items found in adjacent chests"), false);
                        }
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error checking materials", e);
                    player.sendMessage(net.minecraft.text.Text.literal("Error checking materials"), false);
                }
            });
        });
    }
}

