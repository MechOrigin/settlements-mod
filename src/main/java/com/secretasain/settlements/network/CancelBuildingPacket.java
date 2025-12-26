package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementLevelManager;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

/**
 * Network packet to cancel or remove a building.
 * Sent from client when player clicks cancel/remove button.
 */
public class CancelBuildingPacket {
    public static final Identifier ID = new Identifier("settlements", "cancel_building");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID buildingId = buf.readUuid();
            UUID settlementId = buf.readUuid();
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    SettlementManager manager = SettlementManager.getInstance(world);
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot cancel building: settlement {} not found", settlementId);
                        player.sendMessage(net.minecraft.text.Text.literal("Settlement not found"), false);
                        return;
                    }
                    
                    // Find the building
                    Building building = null;
                    for (Building b : settlement.getBuildings()) {
                        if (b.getId().equals(buildingId)) {
                            building = b;
                            break;
                        }
                    }
                    
                    if (building == null) {
                        SettlementsMod.LOGGER.warn("Cannot cancel building: building {} not found in settlement {}", buildingId, settlementId);
                        player.sendMessage(net.minecraft.text.Text.literal("Building not found"), false);
                        return;
                    }
                    
                    // Cancel the building
                    boolean materialsReturned = cancelBuilding(building, settlement, world);
                    
                    // Update settlement level (may have changed if completed building was removed)
                    int oldLevel = settlement.getLevel();
                    if (SettlementLevelManager.updateSettlementLevel(settlement)) {
                        int newLevel = settlement.getLevel();
                        if (newLevel < oldLevel) {
                            SettlementsMod.LOGGER.info("Settlement {} leveled down from {} to {} after building removal", 
                                settlement.getName(), oldLevel, newLevel);
                            player.sendMessage(net.minecraft.text.Text.translatable("settlements.level.down", oldLevel, newLevel), false);
                        }
                    }
                    
                    // Mark settlement as dirty to save changes
                    manager.markDirty();
                    
                    SettlementsMod.LOGGER.info("Building {} cancelled/removed by player {}", buildingId, player.getName().getString());
                    if (materialsReturned) {
                        player.sendMessage(net.minecraft.text.Text.literal("Building cancelled - materials returned to chest"), false);
                    } else {
                        player.sendMessage(net.minecraft.text.Text.literal("Building cancelled/removed"), false);
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error cancelling building", e);
                    player.sendMessage(net.minecraft.text.Text.literal("Error cancelling building"), false);
                }
            });
        });
    }
    
    /**
     * Cancels or removes a building.
     * Removes barrier blocks and removes the building from the settlement.
     * @param building The building to cancel
     * @param settlement The settlement containing the building
     * @param world The server world
     * @return true if materials were returned to chests, false otherwise
     */
    private static boolean cancelBuilding(Building building, Settlement settlement, ServerWorld world) {
        // Stop block placement if building is in progress
        if (building.getStatus() == com.secretasain.settlements.building.BuildingStatus.IN_PROGRESS) {
            com.secretasain.settlements.building.BlockPlacementScheduler.stopBuilding(building.getId(), world);
        }
        
        // Remove barrier blocks
        for (BlockPos barrierPos : building.getBarrierPositions()) {
            ChunkPos chunkPos = new ChunkPos(barrierPos);
            if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                if (world.getBlockState(barrierPos).isOf(Blocks.BARRIER)) {
                    world.setBlockState(barrierPos, Blocks.AIR.getDefaultState());
                }
            }
        }
        
        // CRITICAL: Always return materials if any were provided (consumed)
        // This returns the exact same amount that was consumed when building started
        // Deposit materials back into adjacent chests immediately
        // Even if building is RESERVED (not started), providedMaterials might be empty, which is fine
        boolean materialsReturned = false;
        if (!building.getProvidedMaterials().isEmpty()) {
            int materialCount = building.getProvidedMaterials().size();
            SettlementsMod.LOGGER.info("Cancelling building {} - returning {} material types to chests", 
                building.getId(), materialCount);
            com.secretasain.settlements.building.MaterialManager.returnMaterialsToChests(building, settlement, world);
            SettlementsMod.LOGGER.info("Successfully returned {} material types to chests for cancelled building {}", 
                materialCount, building.getId());
            materialsReturned = true;
        } else {
            SettlementsMod.LOGGER.debug("Building {} has no provided materials to return (status: {}) - building may not have started yet", 
                building.getId(), building.getStatus());
        }
        
        // Remove ghost blocks
        removeGhostBlocks(building, world);
        
        // Update building status to CANCELLED if not already COMPLETED
        if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
            building.updateStatus(com.secretasain.settlements.building.BuildingStatus.CANCELLED);
        }
        
        // Remove building from settlement
        settlement.getBuildings().remove(building);
        
        return materialsReturned;
    }
    
    /**
     * Removes ghost blocks for a building.
     */
    private static void removeGhostBlocks(Building building, ServerWorld world) {
        for (BlockPos ghostPos : building.getGhostBlockPositions()) {
            ChunkPos chunkPos = new ChunkPos(ghostPos);
            if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                if (world.getBlockState(ghostPos).isOf(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK)) {
                    world.setBlockState(ghostPos, Blocks.AIR.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_NEIGHBORS | net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
        }
        building.clearGhostBlockPositions();
        SettlementsMod.LOGGER.info("Removed ghost blocks for cancelled building {}", building.getId());
    }
}

