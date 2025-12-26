package com.secretasain.settlements.block;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles syncing ghost blocks from block entities to the GhostBlockManager.
 * Automatically registers ghost blocks when block entities are loaded and unregisters them when removed.
 */
public class GhostBlockSyncHandler {
    // Flags for world load syncing
    public static boolean hasSyncedThisWorld = false;
    public static int syncTicks = 0;
    
    // Pending sync queue: stores represented blocks from sync packets that arrived before block entities loaded
    private static final Map<BlockPos, BlockState> pendingSyncs = new ConcurrentHashMap<>();
    
    /**
     * Registers event handlers for syncing ghost blocks.
     * Should be called during client initialization.
     */
    public static void register() {
        // Register handler for when block entities are loaded
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof GhostBlockEntity) {
                GhostBlockEntity ghostEntity = (GhostBlockEntity) blockEntity;
                com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockSyncHandler: Block entity loaded at {}", ghostEntity.getPos());
                
                // CRITICAL: Don't sync immediately - readNbt() hasn't been called yet!
                // The BLOCK_ENTITY_LOAD event fires BEFORE readNbt() is called, so getRepresentedBlock() will return air
                // Instead, schedule sync with retry logic to ensure readNbt() has been called
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null) {
                    // Schedule sync with retry - will retry if represented block is still air
                    scheduleSyncWithRetry(client, ghostEntity, 0);
                }
            }
        });
        
        // Register handler for when block entities are unloaded
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof GhostBlockEntity) {
                syncGhostBlockEntity((GhostBlockEntity) blockEntity, false);
            }
        });
    }
    
    /**
     * Stores a pending represented block from a sync packet that arrived before the block entity loaded.
     * This will be applied when the block entity loads.
     */
    public static void storePendingSync(BlockPos pos, BlockState representedBlock) {
        if (representedBlock != null && !representedBlock.isAir()) {
            pendingSyncs.put(pos, representedBlock);
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockSyncHandler: Stored pending sync for {} at {}", 
                net.minecraft.registry.Registries.BLOCK.getId(representedBlock.getBlock()), pos);
        }
    }
    
    /**
     * Schedules a sync with retry logic. Retries if the represented block is still air.
     * This handles the case where readNbt() hasn't been called yet or hasn't read the RepresentedBlock yet.
     * 
     * CRITICAL: Always checks for pending syncs from sync packets, as they represent authoritative server state
     * and should override any values read from NBT (which might be stale if toInitialChunkDataNbt() was called before setRepresentedBlock()).
     */
    private static void scheduleSyncWithRetry(net.minecraft.client.MinecraftClient client, GhostBlockEntity entity, int retryCount) {
        if (client == null || entity == null || entity.getWorld() == null) {
            return;
        }
        
        BlockPos pos = entity.getPos();
        
        // Check if block entity still exists
        if (entity.getWorld().getBlockEntity(pos) != entity) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockSyncHandler: Block entity no longer exists at {}, stopping retry", pos);
            return;
        }
        
        // CRITICAL: Always check for pending syncs - they represent authoritative server state from sync packets
        // and should override NBT values (which might be stale/empty if toInitialChunkDataNbt() was called before setRepresentedBlock())
        // We check on EVERY retry, not just the first one, because pending syncs might arrive during retries
        BlockState pendingBlock = pendingSyncs.remove(pos);
        net.minecraft.block.BlockState representedBlock;
        if (pendingBlock != null) {
            // Pending sync exists - apply it immediately (this is authoritative server state)
            entity.setRepresentedBlock(pendingBlock);
            representedBlock = pendingBlock;
            com.secretasain.settlements.SettlementsMod.LOGGER.info("GhostBlockSyncHandler: Applied pending sync for {} at {} (retry {})", 
                net.minecraft.registry.Registries.BLOCK.getId(pendingBlock.getBlock()), pos, retryCount);
        } else {
            // No pending sync - use what's currently in the block entity (from readNbt() or previous setRepresentedBlock)
            representedBlock = entity.getRepresentedBlock();
        }
        
        boolean isAir = representedBlock == null || representedBlock.isAir();
        
        if (isAir && retryCount < 10) {
            // Represented block is still air - retry after a delay
            // This handles the case where readNbt() hasn't been called yet or hasn't read the RepresentedBlock yet
            // Retry up to 10 times (10 ticks = ~0.5 seconds) to give readNbt() time to complete
            // Also, pending syncs might arrive during retries, so we check again on the next retry
            client.execute(() -> {
                scheduleSyncWithRetry(client, entity, retryCount + 1);
            });
        } else {
            // Either represented block is available, or we've exhausted retries
            if (isAir) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockSyncHandler: Represented block still air after {} retries at {} - block may show as air until sync packet arrives", 
                    retryCount, pos);
            } else {
                // Only log if retry count > 0 (otherwise it's the first attempt, which is normal)
                if (retryCount > 0) {
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockSyncHandler: Syncing ghost block at {} after readNbt completion (retry {})", 
                        pos, retryCount);
                }
            }
            syncGhostBlockEntity(entity, true);
        }
    }
    
    /**
     * Syncs a ghost block entity with the GhostBlockManager.
     * @param entity The ghost block entity
     * @param add If true, adds to manager; if false, removes from manager
     */
    public static void syncGhostBlockEntity(GhostBlockEntity entity, boolean add) {
        if (entity == null || entity.getWorld() == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockSyncHandler: Entity or world is null");
            return;
        }
        
        BlockPos pos = entity.getPos();
        net.minecraft.block.BlockState representedBlock = entity.getRepresentedBlock();
        
        if (representedBlock == null || representedBlock.isAir()) {
            // If removing, still remove it even if it's air
            if (!add) {
                GhostBlockManager.getInstance().removeGhostBlock(pos);
            } else {
                // DEBUG: Warn if we're trying to add a ghost block with no represented block
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("GhostBlockSyncHandler: Cannot add ghost block at {} - represented block is null or air", pos);
            }
            return;
        }
        
        GhostBlockManager manager = GhostBlockManager.getInstance();
        String blockIdString = net.minecraft.registry.Registries.BLOCK.getId(representedBlock.getBlock()).toString();
        
        if (add) {
            // Check if block is already synced with the same state to avoid duplicate syncing
            if (manager.hasGhostBlock(pos)) {
                GhostBlockManager.GhostBlockEntry existing = manager.getGhostBlock(pos);
                if (existing != null && existing.getState().equals(representedBlock)) {
                    // Already synced with correct state, skip
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockSyncHandler: Ghost block at {} already synced with correct state {}, skipping", 
                        pos, blockIdString);
                    return;
                }
            }
            
            // Add ghost block with world light for realistic rendering
            // Use alpha 1.0f (fully opaque) so blocks render exactly like the NBT shows
            manager.addGhostBlock(pos, representedBlock, 1.0f, true);
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("GhostBlockSyncHandler: Added ghost block at {} with represented block {}", 
                pos, blockIdString);
        } else {
            // Remove ghost block
            manager.removeGhostBlock(pos);
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("Removed ghost block at {} from manager", pos);
        }
    }
    
    /**
     * Manually syncs all ghost blocks in the world to the manager.
     * Useful when loading a world or when ghost blocks are placed.
     */
    public static void syncAllGhostBlocks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        
        // Clear existing ghost blocks first
        GhostBlockManager.getInstance().clearAll();
        
        // Find all ghost block entities by iterating through loaded chunks
        // We'll check block entities at known positions or use world iteration
        int syncedCount = 0;
        
        // Iterate through a reasonable area around the player
        if (client.player != null) {
            BlockPos playerPos = client.player.getBlockPos();
            int radius = 64; // Check 64 blocks in each direction
            
            for (int x = -radius; x <= radius; x += 16) {
                for (int z = -radius; z <= radius; z += 16) {
                    BlockPos checkPos = playerPos.add(x, 0, z);
                    int chunkX = checkPos.getX() >> 4;
                    int chunkZ = checkPos.getZ() >> 4;
                    
                    if (client.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                        // Check each block in this chunk area
                        for (int bx = 0; bx < 16; bx++) {
                            for (int bz = 0; bz < 16; bz++) {
                                for (int by = -64; by <= 320; by++) {
                                    BlockPos pos = new BlockPos(
                                        (chunkX << 4) + bx,
                                        by,
                                        (chunkZ << 4) + bz
                                    );
                                    
                                    BlockEntity blockEntity = client.world.getBlockEntity(pos);
                                    if (blockEntity instanceof GhostBlockEntity) {
                                        syncGhostBlockEntity((GhostBlockEntity) blockEntity, true);
                                        syncedCount++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (syncedCount > 0) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Synced {} ghost blocks to manager", syncedCount);
        }
    }
    
    /**
     * Syncs ghost blocks from a building's ghost block positions.
     * This is useful when a settlement is loaded and we need to ensure ghost blocks are synced.
     * @param ghostBlockPositions List of positions where ghost blocks should be
     */
    public static void syncGhostBlocksFromPositions(java.util.List<BlockPos> ghostBlockPositions) {
        if (ghostBlockPositions == null || ghostBlockPositions.isEmpty()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        
        int syncedCount = 0;
        for (BlockPos pos : ghostBlockPositions) {
            // Check if chunk is loaded
            if (client.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                BlockEntity blockEntity = client.world.getBlockEntity(pos);
                if (blockEntity instanceof GhostBlockEntity) {
                    syncGhostBlockEntity((GhostBlockEntity) blockEntity, true);
                    syncedCount++;
                }
            }
        }
        
        if (syncedCount > 0) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("Synced {} ghost blocks from building positions", syncedCount);
        }
    }
}

