package com.secretasain.settlements.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for GhostBlock that stores which block this ghost block represents.
 */
public class GhostBlockEntity extends BlockEntity {
    private BlockState representedBlock = null;
    
    public GhostBlockEntity(BlockPos pos, BlockState state) {
        super(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK_ENTITY, pos, state);
    }
    
    public BlockState getRepresentedBlock() {
        return representedBlock != null ? representedBlock : net.minecraft.block.Blocks.AIR.getDefaultState();
    }
    
    public void setRepresentedBlock(BlockState blockState) {
        Identifier blockId = blockState != null ? Registries.BLOCK.getId(blockState.getBlock()) : null;
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.setRepresentedBlock: Setting represented block {} at {} (isClient={}, was null={})", 
        //     blockId, this.pos, this.world != null && this.world.isClient, this.representedBlock == null);
        
        this.representedBlock = blockState;
        this.markDirty();
        
        // CRITICAL: If this is on the server, sync to clients
        // On client, we don't need to sync back to server - just mark dirty for local rendering
        if (this.world != null && !this.world.isClient) {
            // Mark the block entity as needing sync
            // This will trigger a sync to all nearby players
            this.markDirty();
            // Also mark the chunk for update to ensure clients receive the data
            if (this.world instanceof net.minecraft.server.world.ServerWorld) {
                net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) this.world;
                serverWorld.getChunkManager().markForUpdate(this.pos);
                // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.setRepresentedBlock: Marked chunk for update at {}", this.pos);
            }
        }
        // On client, just mark dirty - the rendering system will pick it up
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        
        // DEBUG: Log all NBT keys to see what we're receiving
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.readNbt: Reading NBT at {} (isClient={}), NBT keys: {}", 
        //     this.pos, this.world != null && this.world.isClient, nbt.getKeys());
        
        // CRITICAL: Check for both "RepresentedBlock" (capital R) and "representedBlock" (lowercase r) for compatibility
        String blockIdString = null;
        if (nbt.contains("RepresentedBlock", 8)) { // 8 = String
            blockIdString = nbt.getString("RepresentedBlock");
            // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.readNbt: Found 'RepresentedBlock' key with value '{}' at {}", 
            //     blockIdString, this.pos);
        } else if (nbt.contains("representedBlock", 10)) { // 10 = Compound (old format)
            // Handle old format where it was stored as a compound with "Name" key
            NbtCompound representedBlockNbt = nbt.getCompound("representedBlock");
            if (representedBlockNbt.contains("Name", 8)) {
                blockIdString = representedBlockNbt.getString("Name");
                // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.readNbt: Found old format 'representedBlock' compound with Name '{}' at {}", 
                //     blockIdString, this.pos);
            }
        }
        
        if (blockIdString != null) {
            Identifier id = Identifier.tryParse(blockIdString);
            if (id != null) {
                representedBlock = Registries.BLOCK.get(id).getDefaultState();
                // DEBUG: Log when we read the represented block from NBT
                // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.readNbt: Successfully read represented block {} from NBT at {} (isClient={})", 
                //     blockIdString, this.pos, this.world != null && this.world.isClient);
            } else {
                // com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockEntity.readNbt: Failed to parse block ID '{}' from NBT at {}", 
                //     blockIdString, this.pos);
            }
        } else {
            // DEBUG: Warn if NBT doesn't contain represented block
            // com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockEntity.readNbt: NBT does not contain 'RepresentedBlock' or 'representedBlock' at {} (isClient={}). NBT keys: {}", 
            //     this.pos, this.world != null && this.world.isClient, nbt.getKeys());
            // CRITICAL: Try to fix existing ghost blocks by looking them up in building data
            if (this.world != null && !this.world.isClient) {
                // Schedule a fix attempt for the next tick (after world is fully loaded)
                if (this.world instanceof net.minecraft.server.world.ServerWorld) {
                    net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) this.world;
                    serverWorld.getServer().execute(() -> {
                        tryToFixRepresentedBlock(serverWorld);
                    });
                }
            }
        }
    }
    
    /**
     * Attempts to fix the represented block by looking it up in building data.
     * This is a recovery mechanism for ghost blocks that were placed before the fix.
     */
    private void tryToFixRepresentedBlock(net.minecraft.server.world.ServerWorld world) {
        try {
            com.secretasain.settlements.settlement.SettlementManager manager = com.secretasain.settlements.settlement.SettlementManager.getInstance(world);
            com.secretasain.settlements.settlement.Settlement settlement = manager.findSettlementAt(this.pos);
            if (settlement == null) {
                return;
            }
            
            // Find the building that contains this ghost block position
            for (com.secretasain.settlements.settlement.Building building : settlement.getBuildings()) {
                if (building.getGhostBlockPositions().contains(this.pos)) {
                    // Found the building! Now load the structure and find the block
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity: Found building for ghost block at {}, attempting to fix represented block", this.pos);
                    
                    // Load structure data
                    com.secretasain.settlements.building.StructureData structureData = com.secretasain.settlements.building.StructureLoader.loadStructure(
                        building.getStructureType(), world.getServer());
                    if (structureData == null) {
                        com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockEntity: Could not load structure {} for building", building.getStructureType());
                        return;
                    }
                    
                    // Calculate relative position within structure
                    net.minecraft.util.math.BlockPos buildingPos = building.getPosition();
                    net.minecraft.util.math.BlockPos rotatedRelativePos = this.pos.subtract(buildingPos);
                    
                    // Reverse the rotation to get the original relative position
                    int rotation = building.getRotation();
                    net.minecraft.util.math.BlockPos relativePos = reverseRotation(rotatedRelativePos, rotation);
                    
                    // Find the block in the structure
                    for (com.secretasain.settlements.building.StructureBlock structureBlock : structureData.getBlocks()) {
                        if (structureBlock.getRelativePos().equals(relativePos)) {
                            BlockState representedState = structureBlock.getBlockState();
                            if (!representedState.isAir()) {
                                this.setRepresentedBlock(representedState);
                                com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity: Fixed represented block {} for ghost block at {}", 
                                    net.minecraft.registry.Registries.BLOCK.getId(representedState.getBlock()), this.pos);
                                // Send sync packet
                                com.secretasain.settlements.network.SyncGhostBlockEntityPacket.sendToAll(world, this.pos, representedState);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.error("GhostBlockEntity: Error trying to fix represented block at {}", this.pos, e);
        }
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (representedBlock != null) {
            Identifier blockId = Registries.BLOCK.getId(representedBlock.getBlock());
            nbt.putString("RepresentedBlock", blockId.toString());
            // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.writeNbt: Writing represented block {} to NBT at {} (isClient={})", 
            //     blockId, this.pos, this.world != null && this.world.isClient);
        } else {
            // com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockEntity.writeNbt: Writing NBT but represented block is null at {} (isClient={})", 
            //     this.pos, this.world != null && this.world.isClient);
        }
    }
    
    /**
     * CRITICAL: Override toInitialChunkDataNbt to ensure NBT is synced to client when chunk loads.
     * This is called when the chunk is sent to the client, so the client will receive the represented block data.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = super.toInitialChunkDataNbt();
        if (representedBlock != null) {
            Identifier blockId = Registries.BLOCK.getId(representedBlock.getBlock());
            nbt.putString("RepresentedBlock", blockId.toString());
            // com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockEntity.toInitialChunkDataNbt: Writing represented block {} to initial chunk data at {} (isClient={})", 
            //     blockId, this.pos, this.world != null && this.world.isClient);
        } else {
            // com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockEntity.toInitialChunkDataNbt: Writing initial chunk data but represented block is null at {} (isClient={}). This means client will receive null represented block!", 
            //     this.pos, this.world != null && this.world.isClient);
        }
        return nbt;
    }
    
    /**
     * Reverses rotation to get the original relative position.
     * This is the inverse of the rotation applied in ConfirmPlacementPacket.
     */
    private BlockPos reverseRotation(BlockPos rotatedPos, int rotation) {
        int x = rotatedPos.getX();
        int z = rotatedPos.getZ();
        
        // Reverse the rotation (opposite of what applyRotation does)
        switch (rotation) {
            case 90:
                // Reverse of (x, z) -> (-z, x) is (x, z) -> (z, -x)
                return new BlockPos(z, rotatedPos.getY(), -x);
            case 180:
                // Reverse of (x, z) -> (-x, -z) is (x, z) -> (-x, -z) (same)
                return new BlockPos(-x, rotatedPos.getY(), -z);
            case 270:
                // Reverse of (x, z) -> (z, -x) is (x, z) -> (-z, x)
                return new BlockPos(-z, rotatedPos.getY(), x);
            default: // 0
                return rotatedPos;
        }
    }
}

