package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.block.GhostBlockSyncHandler;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.ui.SettlementScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Handles client-side network packets for the settlements mod.
 */
public class ClientNetworkHandler {
    public static void register() {
        SettlementsMod.LOGGER.info("Registering client network handler for settlement screen");
        ClientPlayNetworking.registerGlobalReceiver(
            OpenSettlementScreenPacket.ID,
            (client, handler, buf, responseSender) -> {
                SettlementsMod.LOGGER.info("Received open settlement screen packet");
                try {
                    // Read full settlement data from NBT
                    NbtCompound settlementNbt = buf.readNbt();
                    if (settlementNbt == null) {
                        SettlementsMod.LOGGER.error("Received null settlement NBT data");
                        return;
                    }
                    
                    Settlement settlement = Settlement.fromNbt(settlementNbt);
                    
                    SettlementsMod.LOGGER.info("Opening settlement screen for: {} at {} with {} buildings", 
                        settlement.getName(), settlement.getLecternPos(), settlement.getBuildings().size());
                    
                    // Log building materials for debugging
                    for (com.secretasain.settlements.settlement.Building building : settlement.getBuildings()) {
                        java.util.Map<net.minecraft.util.Identifier, Integer> materials = building.getRequiredMaterials();
                        SettlementsMod.LOGGER.info("Building {} has {} required materials", building.getId(), materials.size());
                        for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> entry : materials.entrySet()) {
                            SettlementsMod.LOGGER.info("  - {}: {}", entry.getKey(), entry.getValue());
                        }
                    }
                    
                    client.execute(() -> {
                        SettlementsMod.LOGGER.info("Setting screen on client thread");
                        
                        // Sync ghost blocks from all buildings in the settlement
                        for (com.secretasain.settlements.settlement.Building building : settlement.getBuildings()) {
                            java.util.List<net.minecraft.util.math.BlockPos> ghostPositions = building.getGhostBlockPositions();
                            if (ghostPositions != null && !ghostPositions.isEmpty()) {
                                GhostBlockSyncHandler.syncGhostBlocksFromPositions(ghostPositions);
                            }
                        }
                        
                        MinecraftClient.getInstance().setScreen(new SettlementScreen(settlement));
                    });
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error handling open settlement screen packet", e);
                }
            }
        );
        
        // Register handler for materials sync packet
        ClientPlayNetworking.registerGlobalReceiver(
            SyncMaterialsPacket.ID,
            (client, handler, buf, responseSender) -> {
                SettlementsMod.LOGGER.info("Received sync materials packet");
                try {
                    UUID settlementId = buf.readUuid();
                    NbtCompound materialsNbt = buf.readNbt();
                    
                    if (materialsNbt == null) {
                        SettlementsMod.LOGGER.error("Received null materials NBT data");
                        return;
                    }
                    
                    client.execute(() -> {
                        // Update materials in the currently open settlement screen
                        if (client.currentScreen instanceof SettlementScreen) {
                            SettlementScreen screen = (SettlementScreen) client.currentScreen;
                            screen.updateMaterials(settlementId, materialsNbt);
                        }
                    });
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error handling sync materials packet", e);
                }
            }
        );
        
        // Register handler for building status sync packet
        ClientPlayNetworking.registerGlobalReceiver(
            SyncBuildingStatusPacket.ID,
            (client, handler, buf, responseSender) -> {
                SettlementsMod.LOGGER.info("Received sync building status packet");
                try {
                    buf.readUuid(); // settlementId - read but not used (verified in screen)
                    UUID buildingId = buf.readUuid();
                    String statusName = buf.readString();
                    NbtCompound settlementNbt = buf.readNbt();
                    
                    if (settlementNbt == null) {
                        SettlementsMod.LOGGER.error("Received null settlement NBT data in status sync");
                        return;
                    }
                    
                    client.execute(() -> {
                        // Update settlement data in the currently open settlement screen
                        if (client.currentScreen instanceof SettlementScreen) {
                            SettlementScreen screen = (SettlementScreen) client.currentScreen;
                            Settlement updatedSettlement = Settlement.fromNbt(settlementNbt);
                            screen.updateSettlementData(updatedSettlement);
                            SettlementsMod.LOGGER.info("Updated building {} status to {} in UI", buildingId, statusName);
                        }
                    });
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error handling sync building status packet", e);
                }
            }
        );
        
        // Register handler for ghost block entity sync packet
        ClientPlayNetworking.registerGlobalReceiver(
            com.secretasain.settlements.network.SyncGhostBlockEntityPacket.ID,
            (client, handler, buf, responseSender) -> {
                SettlementsMod.LOGGER.info("Received ghost block entity sync packet");
                try {
                    // Read position
                    net.minecraft.util.math.BlockPos pos = buf.readBlockPos();
                    
                    // Read represented block ID
                    String blockIdString = buf.readString();
                    net.minecraft.util.Identifier blockId = net.minecraft.util.Identifier.tryParse(blockIdString);
                    
                    SettlementsMod.LOGGER.info("Ghost block sync packet: position {}, block ID {}", pos, blockIdString);
                    
                    if (blockId == null) {
                        SettlementsMod.LOGGER.warn("Received invalid block ID '{}' for ghost block at {}", blockIdString, pos);
                        return;
                    }
                    
                    // Get block state from registry
                    net.minecraft.block.BlockState representedBlock = net.minecraft.registry.Registries.BLOCK.get(blockId).getDefaultState();
                    
                    // CRITICAL: Execute on client thread to ensure world access
                    client.execute(() -> {
                        // Update the block entity on the client
                        if (client.world != null) {
                            SettlementsMod.LOGGER.info("ClientNetworkHandler: Processing sync packet for ghost block at {} with represented block {}", 
                                pos, blockIdString);
                            
                            // Check if block entity exists before updating
                            net.minecraft.block.entity.BlockEntity be = client.world.getBlockEntity(pos);
                            if (be instanceof com.secretasain.settlements.block.GhostBlockEntity) {
                                net.minecraft.block.BlockState currentBlock = ((com.secretasain.settlements.block.GhostBlockEntity) be).getRepresentedBlock();
                                SettlementsMod.LOGGER.info("ClientNetworkHandler: Block entity exists, current represented block: {}", 
                                    currentBlock != null ? net.minecraft.registry.Registries.BLOCK.getId(currentBlock.getBlock()) : "null");
                            } else {
                                SettlementsMod.LOGGER.warn("ClientNetworkHandler: Block entity at {} is not a GhostBlockEntity (type: {})", 
                                    pos, be != null ? be.getClass().getName() : "null");
                            }
                            
                            // CRITICAL: Retry mechanism - block entity might not exist yet when packet arrives
                            // Try immediately first
                            updateGhostBlockEntity(client, pos, representedBlock, blockId, 0);
                        } else {
                            SettlementsMod.LOGGER.warn("Received ghost block sync packet but client world is null");
                        }
                    });
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error handling ghost block entity sync packet", e);
                    e.printStackTrace();
                }
            }
        );
        
        SettlementsMod.LOGGER.info("Client network handler registered successfully");
    }
    
    /**
     * Updates a ghost block entity with the represented block.
     * Retries if the block entity doesn't exist yet (race condition).
     */
    private static void updateGhostBlockEntity(MinecraftClient client, net.minecraft.util.math.BlockPos pos, 
                                             net.minecraft.block.BlockState representedBlock, 
                                             net.minecraft.util.Identifier blockId, int retryCount) {
        if (client.world == null) {
            return;
        }
        
        net.minecraft.block.entity.BlockEntity be = client.world.getBlockEntity(pos);
        if (be instanceof com.secretasain.settlements.block.GhostBlockEntity) {
            com.secretasain.settlements.block.GhostBlockEntity ghostEntity = (com.secretasain.settlements.block.GhostBlockEntity) be;
            ghostEntity.setRepresentedBlock(representedBlock);
            // Also sync to GhostBlockManager for rendering
            com.secretasain.settlements.block.GhostBlockSyncHandler.syncGhostBlockEntity(ghostEntity, true);
            SettlementsMod.LOGGER.debug("Updated ghost block entity at {} with represented block {}", 
                pos, blockId);
        } else {
            // Block entity doesn't exist yet - store as pending sync for when it loads
            // Also retry in case it loads very quickly
            if (retryCount < 3) { // Retry up to 3 times (3 ticks = ~0.15 seconds)
                SettlementsMod.LOGGER.debug("Ghost block entity not found at {} (retry {}/3), storing as pending and will retry...", pos, retryCount + 1);
                // Store as pending sync - will be applied when block entity loads
                com.secretasain.settlements.block.GhostBlockSyncHandler.storePendingSync(pos, representedBlock);
                client.execute(() -> {
                    // Wait one tick and retry
                    client.execute(() -> {
                        updateGhostBlockEntity(client, pos, representedBlock, blockId, retryCount + 1);
                    });
                });
            } else {
                // After retries, store as pending sync and wait for block entity to load
                com.secretasain.settlements.block.GhostBlockSyncHandler.storePendingSync(pos, representedBlock);
                SettlementsMod.LOGGER.debug("Stored pending sync for {} at {} - will apply when block entity loads", blockId, pos);
            }
        }
    }
}

