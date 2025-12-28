package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.BlockPlacementScheduler;
import com.secretasain.settlements.building.BuildingStatus;
import com.secretasain.settlements.building.MaterialManager;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Network packet to start building construction.
 * Consumes materials and transitions building status to IN_PROGRESS.
 * Sent from client when player clicks "Start Building" button.
 */
public class StartBuildingPacket {
    public static final Identifier ID = new Identifier("settlements", "start_building");
    
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
                        SettlementsMod.LOGGER.warn("Cannot start building: settlement {} not found", settlementId);
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
                        SettlementsMod.LOGGER.warn("Cannot start building: building {} not found in settlement {}", buildingId, settlementId);
                        player.sendMessage(net.minecraft.text.Text.literal("Building not found"), false);
                        return;
                    }
                    
                    // Check building status
                    if (building.getStatus() != BuildingStatus.RESERVED) {
                        SettlementsMod.LOGGER.warn("Cannot start building: building {} is not in RESERVED status (current: {})", 
                            buildingId, building.getStatus());
                        if (building.getStatus() == BuildingStatus.IN_PROGRESS) {
                            player.sendMessage(net.minecraft.text.Text.literal("Building is already in progress"), false);
                        } else {
                            player.sendMessage(net.minecraft.text.Text.literal("Building is not ready to start (status: " + building.getStatus() + ")"), false);
                        }
                        return;
                    }
                    
                    // Check if we can afford the materials (skip check in creative mode)
                    boolean creativeMode = player.isCreative();
                    if (!creativeMode && !MaterialManager.canAfford(settlement, building.getRequiredMaterials())) {
                        SettlementsMod.LOGGER.warn("Cannot start building: insufficient materials for building {}", buildingId);
                        player.sendMessage(net.minecraft.text.Text.literal("Insufficient materials! Check required materials."), false);
                        return;
                    }
                    
                    // Consume materials (pass world for creative mode check)
                    if (!MaterialManager.consumeMaterialsForBuilding(building, settlement, world)) {
                        SettlementsMod.LOGGER.warn("Failed to consume materials for building {}", buildingId);
                        player.sendMessage(net.minecraft.text.Text.literal("Failed to consume materials"), false);
                        return;
                    }
                    
                    // Remove ghost blocks before starting construction
                    removeGhostBlocks(building, world);
                    
                    // Update building status to IN_PROGRESS
                    if (!building.updateStatus(BuildingStatus.IN_PROGRESS)) {
                        SettlementsMod.LOGGER.warn("Failed to update building status to IN_PROGRESS for building {}", buildingId);
                        player.sendMessage(net.minecraft.text.Text.literal("Failed to start building"), false);
                        return;
                    }
                    
                    // Mark settlement as dirty to save changes
                    manager.markDirty();
                    
                    // Initialize block placement queue
                    try {
                        // Load structure data
                        Identifier structureId = building.getStructureType();
                        StructureData structureData = StructureLoader.loadStructure(structureId, world.getServer());
                        
                        if (structureData == null) {
                            SettlementsMod.LOGGER.error("Failed to load structure data for building {}: structure {} not found", 
                                buildingId, structureId);
                            player.sendMessage(net.minecraft.text.Text.literal(
                                "Failed to load structure data. Materials returned. You can try again."), false);
                            // Return materials since we can't build
                            // returnMaterialsToChests already clears providedMaterials, so no need to clear again
                            MaterialManager.returnMaterialsToChests(building, settlement, world);
                            building.updateStatus(BuildingStatus.RESERVED);
                            manager.markDirty();
                            return;
                        }
                        
                        // Initialize placement queue
                        BlockPlacementScheduler.initializeQueue(building, structureData, world);
                        
                        SettlementsMod.LOGGER.info("Building {} started construction by player {} with {} blocks", 
                            buildingId, player.getName().getString(), structureData.getBlockCount());
                        player.sendMessage(net.minecraft.text.Text.literal("Building construction started!"), false);
                        
                        // Sync building status update to client
                        SyncBuildingStatusPacket.send(settlement, building, world);
                    } catch (Exception e) {
                        SettlementsMod.LOGGER.error("Error initializing block placement queue for building {}", buildingId, e);
                        player.sendMessage(net.minecraft.text.Text.literal(
                            "Error starting construction: " + e.getMessage() + ". Materials returned. You can try again."), false);
                        // Return materials since we can't build
                        // returnMaterialsToChests already clears providedMaterials, so no need to clear again
                        MaterialManager.returnMaterialsToChests(building, settlement, world);
                        building.updateStatus(BuildingStatus.RESERVED);
                        manager.markDirty();
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error starting building", e);
                    player.sendMessage(net.minecraft.text.Text.literal("Error starting building"), false);
                }
            });
        });
    }
    
    /**
     * Removes ghost blocks for a building.
     */
    private static void removeGhostBlocks(Building building, ServerWorld world) {
        int count = building.getGhostBlockPositions().size();
        for (net.minecraft.util.math.BlockPos ghostPos : building.getGhostBlockPositions()) {
            net.minecraft.util.math.ChunkPos chunkPos = new net.minecraft.util.math.ChunkPos(ghostPos);
            if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                if (world.getBlockState(ghostPos).isOf(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK)) {
                    world.setBlockState(ghostPos, net.minecraft.block.Blocks.AIR.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_NEIGHBORS | net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
        }
        building.clearGhostBlockPositions();
        SettlementsMod.LOGGER.info("Removed {} ghost blocks for building {}", count, building.getId());
    }
}

