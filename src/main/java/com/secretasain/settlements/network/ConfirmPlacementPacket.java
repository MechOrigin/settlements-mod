package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.BuildModeHandler;
import com.secretasain.settlements.building.BuildModeManager;
import com.secretasain.settlements.building.BuildingStatus;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Network packet to confirm structure placement and create a building reservation.
 * Sent from client when player presses ENTER in build mode.
 */
public class ConfirmPlacementPacket {
    public static final Identifier ID = new Identifier("settlements", "confirm_placement");
    public static final Identifier DEACTIVATE_BUILD_MODE_PACKET_ID = new Identifier("settlements", "deactivate_build_mode");
    
    /**
     * Registers the server-side packet handler.
     * Call this from SettlementsMod.onInitialize().
     */
    public static void register() {
        SettlementsMod.LOGGER.info("Registering ConfirmPlacementPacket handler");
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, networkHandler, buf, responseSender) -> {
            SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Received packet from player {}", player.getName().getString());
            try {
                // Read placement position and rotation from packet
                BlockPos placementPos = BlockPos.fromLong(buf.readLong());
                int rotation = buf.readInt();
                SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Read placement position {} and rotation {}", placementPos, rotation);
                
                server.execute(() -> {
                    try {
                        SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Executing on server thread for player {} at position {}", 
                            player.getName().getString(), placementPos);
                    
                        // Get build mode handler
                        BuildModeHandler handler = BuildModeManager.getHandler(player);
                        if (!handler.isActive()) {
                            SettlementsMod.LOGGER.warn("Player {} tried to confirm placement but build mode is not active", 
                                player.getName().getString());
                            return;
                        }
                        
                        // Get structure data
                        if (handler.getSelectedStructure() == null) {
                            SettlementsMod.LOGGER.warn("Player {} tried to confirm placement but no structure is selected", 
                                player.getName().getString());
                            return;
                        }
                        
                        // CRITICAL: Get settlement from build mode handler (the settlement whose screen was open)
                        // This ensures buildings are added to the correct settlement, not just any settlement containing the position
                        ServerWorld world = player.getServerWorld();
                        SettlementManager manager = SettlementManager.getInstance(world);
                        Settlement settlement = null;
                        
                        UUID settlementId = handler.getSettlementId();
                        if (settlementId != null) {
                            // Use the settlement ID from build mode handler (the settlement whose screen was open)
                            settlement = manager.getSettlement(settlementId);
                            if (settlement == null) {
                                SettlementsMod.LOGGER.warn("ConfirmPlacementPacket: Settlement {} from build mode handler not found! Falling back to findSettlementAt", settlementId);
                            } else {
                                SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Using settlement {} (lectern at {}) from build mode handler for building at {}", 
                                    settlement.getId(), settlement.getLecternPos(), placementPos);
                            }
                        }
                        
                        // Fallback: Find settlement at placement position if handler doesn't have settlement ID
                        if (settlement == null) {
                            settlement = manager.findSettlementAt(placementPos);
                            if (settlement == null) {
                                SettlementsMod.LOGGER.warn("Cannot place building: position {} is not within any settlement", placementPos);
                                player.sendMessage(net.minecraft.text.Text.literal("Cannot place building: must be within a settlement"), false);
                                return;
                            }
                            SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Found settlement {} (lectern at {}) for building at {} using findSettlementAt fallback", 
                                settlement.getId(), settlement.getLecternPos(), placementPos);
                        }
                        
                        // Verify structure is within settlement bounds
                        if (!settlement.isWithinBounds(placementPos)) {
                            SettlementsMod.LOGGER.warn("Cannot place building: position {} is outside settlement bounds", placementPos);
                            player.sendMessage(net.minecraft.text.Text.literal("Cannot place building: outside settlement bounds"), false);
                            return;
                        }
                        
                        // Enhanced validation: Check all structure blocks are within settlement bounds
                        com.secretasain.settlements.building.StructureData structureData = handler.getSelectedStructure();
                        String validationError = validatePlacement(structureData, placementPos, rotation, settlement, world);
                        if (validationError != null) {
                            SettlementsMod.LOGGER.warn("Cannot place building: {}", validationError);
                            player.sendMessage(net.minecraft.text.Text.literal("Cannot place building: " + validationError), false);
                            return;
                        }
                        
                        // Create building
                        UUID buildingId = UUID.randomUUID();
                        // Structure identifier should be the full path: "settlements:structures/name.nbt"
                        // The structure name might be just "lvl1_oak_wall" or "lvl1_oak_wall.nbt"
                        String structureName = handler.getSelectedStructure().getName();
                        if (!structureName.contains("/")) {
                            // Add the structures/ path if not present
                            structureName = "structures/" + structureName;
                        }
                        if (!structureName.endsWith(".nbt")) {
                            structureName = structureName + ".nbt";
                        }
                        Identifier structureType = new Identifier("settlements", structureName);
                        Building building = new Building(buildingId, placementPos, structureType, rotation);
                        
                        SettlementsMod.LOGGER.info("Creating building with structure identifier: {}", structureType);
                        
                        // Calculate and set required materials from structure
                        java.util.Map<net.minecraft.util.Identifier, Integer> requiredMaterials = 
                            com.secretasain.settlements.building.MaterialManager.calculateMaterials(structureData);
                        building.setRequiredMaterials(requiredMaterials);
                        
                        SettlementsMod.LOGGER.info("Building requires {} different material types", requiredMaterials.size());
                        
                        // Add building to settlement
                        settlement.getBuildings().add(building);
                        
                        // DEBUG: Log building addition
                        SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Added building {} to settlement {} (total buildings: {})", 
                            building.getId(), settlement.getId(), settlement.getBuildings().size());
                        
                        // CRITICAL: Mark settlement as dirty so building is persisted
                        SettlementManager.getInstance(world).markDirty();
                        
                        // Place barrier blocks
                        placeBarriers(building, handler.getSelectedStructure(), world, placementPos, rotation);
                        
                        // Place ghost blocks to show structure preview
                        placeGhostBlocks(building, handler.getSelectedStructure(), world, placementPos, rotation);
                        
                        // CRITICAL: Send settlement update to client so building appears in UI
                        // This ensures the building list is updated when the screen is open
                        com.secretasain.settlements.network.SyncBuildingStatusPacket.sendToPlayer(player, settlement, building);
                        
                        // Deactivate build mode on server
                        handler.deactivateBuildMode();
                        
                        // Send deactivation packet to client
                        net.minecraft.network.PacketByteBuf deactivateBuf = PacketByteBufs.create();
                        ServerPlayNetworking.send(player, DEACTIVATE_BUILD_MODE_PACKET_ID, deactivateBuf);
                        
                        SettlementsMod.LOGGER.info("Building reservation created for player {} at position {}", 
                            player.getName().getString(), placementPos);
                        player.sendMessage(net.minecraft.text.Text.literal("Building reserved! Provide materials to start construction."), false);
                        
                    } catch (Exception e) {
                        SettlementsMod.LOGGER.error("Error confirming placement", e);
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Error reading confirm placement packet data", e);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Places barrier blocks around the building area.
     * @param building The building to place barriers for
     * @param structure The structure data
     * @param world The world
     * @param placementPos The placement position
     * @param rotation The rotation in degrees
     */
    private static void placeBarriers(Building building, com.secretasain.settlements.building.StructureData structure, 
                                     ServerWorld world, BlockPos placementPos, int rotation) {
        // Calculate bounding box
        net.minecraft.util.math.Vec3i size = structure.getDimensions();
        
        // Place barriers at corners and edges
        // For now, place barriers at the 8 corners of the bounding box
        BlockPos minPos = placementPos;
        BlockPos maxPos = placementPos.add(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        
        // Apply rotation to corners
        // For simplicity, we'll place barriers at the original corners
        // TODO: Apply rotation transformation to barrier positions
        
        java.util.List<BlockPos> barrierPositions = new java.util.ArrayList<>();
        
        // Place barriers at the 8 corners
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos corner = new BlockPos(
                        minPos.getX() + x * (maxPos.getX() - minPos.getX()),
                        minPos.getY() + y * (maxPos.getY() - minPos.getY()),
                        minPos.getZ() + z * (maxPos.getZ() - minPos.getZ())
                    );
                    
                    // Only place barrier if chunk is loaded
                    if (world.getChunkManager().isChunkLoaded(corner.getX() >> 4, corner.getZ() >> 4)) {
                        if (world.getBlockState(corner).isAir()) {
                            world.setBlockState(corner, Blocks.BARRIER.getDefaultState());
                            barrierPositions.add(corner);
                        }
                    }
                }
            }
        }
        
        building.setBarrierPositions(barrierPositions);
    }
    
    /**
     * Places ghost blocks to show the structure preview in the world.
     * @param building The building to place ghost blocks for
     * @param structure The structure data
     * @param world The world
     * @param placementPos The placement position
     * @param rotation The rotation in degrees
     */
    private static void placeGhostBlocks(Building building, com.secretasain.settlements.building.StructureData structure, 
                                        ServerWorld world, BlockPos placementPos, int rotation) {
        java.util.List<BlockPos> ghostPositions = new java.util.ArrayList<>();
        
        // Apply rotation transformation
        for (com.secretasain.settlements.building.StructureBlock structureBlock : structure.getBlocks()) {
            BlockPos relativePos = structureBlock.getRelativePos();
            BlockPos worldPos = applyRotation(relativePos, rotation).add(placementPos);
            
            // Skip air blocks
            if (structureBlock.getBlockState().isAir()) {
                continue;
            }
            
            // Only place ghost block if chunk is loaded
            if (world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                BlockState existingState = world.getBlockState(worldPos);
                // Only place if position is air or replaceable
                if (existingState.isAir() || existingState.isReplaceable()) {
                    // Rotate block state if needed (for directional blocks like stairs, slabs, etc.)
                    BlockState representedState = com.secretasain.settlements.building.BlockStateRotator.rotateBlockState(
                        structureBlock.getBlockState(), 
                        rotation
                    );
                    
                    // Place ghost block
                    // CRITICAL: Use flags to ensure block is placed and persisted
                    // NOTIFY_NEIGHBORS: Notify neighboring blocks of the change
                    // NOTIFY_LISTENERS: Notify block update listeners
                    // SKIP_DROPS: Skip block drops (we don't want drops for ghost blocks)
                    int flags = net.minecraft.block.Block.NOTIFY_NEIGHBORS 
                        | net.minecraft.block.Block.NOTIFY_LISTENERS
                        | net.minecraft.block.Block.SKIP_DROPS;
                    SettlementsMod.LOGGER.info("ConfirmPlacementPacket: About to place ghost block at {} with represented block {}", 
                        worldPos, net.minecraft.registry.Registries.BLOCK.getId(representedState.getBlock()));
                    
                    boolean placed = world.setBlockState(worldPos, com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK.getDefaultState(), flags);
                    
                    // Verify block was actually placed
                    net.minecraft.block.BlockState actualState = world.getBlockState(worldPos);
                    if (!placed || actualState.getBlock() != com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK) {
                        SettlementsMod.LOGGER.error("ConfirmPlacementPacket: Failed to place ghost block at {}! placed={}, actual block={}", 
                            worldPos, placed, net.minecraft.registry.Registries.BLOCK.getId(actualState.getBlock()));
                        continue; // Skip this block
                    }
                    
                    SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Successfully placed ghost block at {}", worldPos);
                    
                    // CRITICAL: Get block entity - it should be created automatically by BlockEntityProvider
                    // The block entity is created synchronously when setBlockState is called
                    net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(worldPos);
                    net.minecraft.util.Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(representedState.getBlock());
                    
                    SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Block entity at {} is: {} (type: {}), setting represented block {}", 
                        worldPos, be != null ? "exists" : "null", be != null ? be.getClass().getName() : "N/A", blockId);
                    
                    if (be instanceof com.secretasain.settlements.block.GhostBlockEntity) {
                        com.secretasain.settlements.block.GhostBlockEntity ghostEntity = (com.secretasain.settlements.block.GhostBlockEntity) be;
                        
                        // DEBUG: Check current state before setting
                        net.minecraft.block.BlockState currentRepresented = ghostEntity.getRepresentedBlock();
                        SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Ghost block entity at {} currently has represented block: {}", 
                            worldPos, currentRepresented != null ? net.minecraft.registry.Registries.BLOCK.getId(currentRepresented.getBlock()) : "null");
                        
                        // CRITICAL: Set represented block directly - this will write it to NBT via writeNbt()
                        // The block entity's writeNbt() method will save it with the correct key "RepresentedBlock"
                        ghostEntity.setRepresentedBlock(representedState);
                        ghostEntity.markDirty();
                        
                        // Verify it was set
                        net.minecraft.block.BlockState verifyRepresented = ghostEntity.getRepresentedBlock();
                        SettlementsMod.LOGGER.info("ConfirmPlacementPacket: After setRepresentedBlock, ghost block entity at {} now has represented block: {}", 
                            worldPos, verifyRepresented != null ? net.minecraft.registry.Registries.BLOCK.getId(verifyRepresented.getBlock()) : "null");
                        
                        // CRITICAL: Mark chunk for update IMMEDIATELY to ensure NBT is synced to client
                        // This ensures toInitialChunkDataNbt() is called with the represented block set
                        if (world instanceof net.minecraft.server.world.ServerWorld) {
                            net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) world;
                            // Mark chunk for update immediately - this syncs the block entity NBT to clients
                            serverWorld.getChunkManager().markForUpdate(worldPos);
                            SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Marked chunk for update at {} to sync represented block {}", worldPos, blockId);
                            
                            // Also send sync packet as backup (delay by 1 tick to ensure block entity exists on client)
                            serverWorld.getServer().execute(() -> {
                                // Send custom sync packet
                                com.secretasain.settlements.network.SyncGhostBlockEntityPacket.sendToAll(serverWorld, worldPos, representedState);
                                SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Sent sync packet for represented block {} at {}", blockId, worldPos);
                            });
                        }
                    } else {
                        // Block entity not created - this shouldn't happen, but handle it
                        SettlementsMod.LOGGER.error("ConfirmPlacementPacket: Block entity not found at {} after placement! Block entity: {}", 
                            worldPos, be != null ? be.getClass().getName() : "null");
                        // Try to create it manually
                        if (world instanceof net.minecraft.server.world.ServerWorld) {
                            net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) world;
                            com.secretasain.settlements.block.GhostBlockEntity newEntity = com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK_ENTITY.instantiate(worldPos, 
                                com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK.getDefaultState());
                            // CRITICAL: Set represented block directly - this will write it to NBT via writeNbt()
                            newEntity.setRepresentedBlock(representedState);
                            newEntity.markDirty();
                            serverWorld.addBlockEntity(newEntity);
                            
                            // CRITICAL: Mark chunk for update IMMEDIATELY to ensure NBT is synced to client
                            serverWorld.getChunkManager().markForUpdate(worldPos);
                            
                            // Also send sync packet as backup (delay by 1 tick to ensure block entity exists on client)
                            serverWorld.getServer().execute(() -> {
                                com.secretasain.settlements.network.SyncGhostBlockEntityPacket.sendToAll(serverWorld, worldPos, representedState);
                                SettlementsMod.LOGGER.info("ConfirmPlacementPacket: Manually created block entity with represented block {} at {}", blockId, worldPos);
                            });
                        }
                    }
                    
                    ghostPositions.add(worldPos);
                }
            }
        }
        
        building.setGhostBlockPositions(ghostPositions);
        SettlementsMod.LOGGER.info("Placed {} ghost blocks for building {}", ghostPositions.size(), building.getId());
    }
    
    /**
     * Validates that a structure can be placed at the given position.
     * @param structure The structure data
     * @param placementPos The placement position
     * @param rotation The rotation in degrees
     * @param settlement The settlement
     * @param world The world
     * @return Error message if validation fails, null if valid
     */
    private static String validatePlacement(com.secretasain.settlements.building.StructureData structure, 
                                           BlockPos placementPos, int rotation, 
                                           Settlement settlement, ServerWorld world) {
        // Check all structure blocks are within settlement bounds
        for (com.secretasain.settlements.building.StructureBlock block : structure.getBlocks()) {
            BlockPos relativePos = block.getRelativePos();
            BlockPos worldPos = applyRotation(relativePos, rotation).add(placementPos);
            
            // Check if block is within settlement bounds
            if (!settlement.isWithinBounds(worldPos)) {
                return "Structure extends outside settlement bounds";
            }
            
            // Check if chunk is loaded
            if (!world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                return "Structure extends into unloaded chunks";
            }
            
            // Check if block space is available (air or replaceable)
            BlockState existing = world.getBlockState(worldPos);
            if (!existing.isAir() && !existing.isReplaceable()) {
                // Allow barrier blocks and ghost blocks (they'll be replaced)
                if (existing.getBlock() != Blocks.BARRIER && 
                    existing.getBlock() != com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK) {
                    return "Structure overlaps with existing blocks";
                }
            }
        }
        
        // Check for overlap with existing buildings
        for (Building existingBuilding : settlement.getBuildings()) {
            // Skip cancelled buildings
            if (existingBuilding.getStatus() == BuildingStatus.CANCELLED) {
                continue;
            }
            
            // Calculate bounding boxes for both buildings
            net.minecraft.util.math.Box newBounds = calculateStructureBounds(structure, placementPos, rotation);
            net.minecraft.util.math.Box existingBounds = calculateBuildingBounds(existingBuilding, world);
            
            // Check if bounding boxes overlap
            if (newBounds.intersects(existingBounds)) {
                // More detailed check: check if any blocks overlap
                if (buildingsOverlap(structure, placementPos, rotation, existingBuilding, world)) {
                    return "Structure overlaps with existing building";
                }
            }
        }
        
        return null; // Validation passed
    }
    
    /**
     * Calculates the bounding box for a structure at a given position and rotation.
     */
    private static net.minecraft.util.math.Box calculateStructureBounds(
            com.secretasain.settlements.building.StructureData structure, 
            BlockPos placementPos, int rotation) {
        BlockPos min = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        BlockPos max = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        
        for (com.secretasain.settlements.building.StructureBlock block : structure.getBlocks()) {
            BlockPos relativePos = block.getRelativePos();
            BlockPos worldPos = applyRotation(relativePos, rotation).add(placementPos);
            
            min = new BlockPos(
                Math.min(min.getX(), worldPos.getX()),
                Math.min(min.getY(), worldPos.getY()),
                Math.min(min.getZ(), worldPos.getZ())
            );
            max = new BlockPos(
                Math.max(max.getX(), worldPos.getX()),
                Math.max(max.getY(), worldPos.getY()),
                Math.max(max.getZ(), worldPos.getZ())
            );
        }
        
        return new net.minecraft.util.math.Box(min, max.add(1, 1, 1));
    }
    
    /**
     * Calculates the bounding box for an existing building.
     */
    private static net.minecraft.util.math.Box calculateBuildingBounds(Building building, ServerWorld world) {
        // Try to get structure data for the building
        com.secretasain.settlements.building.StructureData structureData = 
            com.secretasain.settlements.building.StructureLoader.loadStructure(building.getStructureType(), world.getServer());
        
        if (structureData != null) {
            return calculateStructureBounds(structureData, building.getPosition(), building.getRotation());
        } else {
            // Fallback: use a small bounding box around the building position
            BlockPos pos = building.getPosition();
            return new net.minecraft.util.math.Box(pos, pos.add(1, 1, 1));
        }
    }
    
    /**
     * Checks if two buildings overlap by comparing their block positions.
     */
    private static boolean buildingsOverlap(
            com.secretasain.settlements.building.StructureData newStructure, 
            BlockPos newPos, int newRotation,
            Building existingBuilding, ServerWorld world) {
        // Get structure data for existing building
        com.secretasain.settlements.building.StructureData existingStructure = 
            com.secretasain.settlements.building.StructureLoader.loadStructure(existingBuilding.getStructureType(), world.getServer());
        
        if (existingStructure == null) {
            // Can't check overlap without structure data, assume no overlap
            return false;
        }
        
        // Create sets of world positions for both buildings
        java.util.Set<BlockPos> newPositions = new java.util.HashSet<>();
        for (com.secretasain.settlements.building.StructureBlock block : newStructure.getBlocks()) {
            BlockPos relativePos = block.getRelativePos();
            BlockPos worldPos = applyRotation(relativePos, newRotation).add(newPos);
            newPositions.add(worldPos);
        }
        
        java.util.Set<BlockPos> existingPositions = new java.util.HashSet<>();
        for (com.secretasain.settlements.building.StructureBlock block : existingStructure.getBlocks()) {
            BlockPos relativePos = block.getRelativePos();
            BlockPos worldPos = applyRotation(relativePos, existingBuilding.getRotation()).add(existingBuilding.getPosition());
            existingPositions.add(worldPos);
        }
        
        // Check for any overlapping positions
        newPositions.retainAll(existingPositions);
        return !newPositions.isEmpty();
    }
    
    /**
     * Applies rotation to a relative position.
     */
    private static BlockPos applyRotation(BlockPos relativePos, int rotation) {
        int x = relativePos.getX();
        int z = relativePos.getZ();
        
        switch (rotation) {
            case 90:
                return new BlockPos(-z, relativePos.getY(), x);
            case 180:
                return new BlockPos(-x, relativePos.getY(), -z);
            case 270:
                return new BlockPos(z, relativePos.getY(), -x);
            default: // 0
                return relativePos;
        }
    }
}

