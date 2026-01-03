package com.secretasain.settlements.building;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementLevelManager;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles sequential block placement for buildings in progress.
 * Places blocks at a controlled rate to avoid lag.
 */
public class BlockPlacementScheduler {
    private static final int BLOCKS_PER_TICK = 1; // Place 1 block per processing cycle
    private static final int TICK_DELAY = 5; // Place blocks every 5 ticks (1 block per 0.25 seconds = 4 blocks/second)
    
    // Singleton instance
    private static BlockPlacementScheduler instance;
    
    private final Map<ServerWorld, WorldPlacementData> worldData = new HashMap<>();
    
    /**
     * Registers the block placement scheduler with Fabric's server tick events.
     */
    public static void register() {
        instance = new BlockPlacementScheduler();
        
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            instance.tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private void tick(ServerWorld world) {
        WorldPlacementData data = worldData.computeIfAbsent(world, w -> new WorldPlacementData());
        data.tick(world);
    }
    
    /**
     * Initializes the placement queue for a building.
     * @param building The building to initialize
     * @param structureData The structure data to place
     * @param world The server world
     */
    public static void initializeQueue(Building building, StructureData structureData, ServerWorld world) {
        WorldPlacementData data = getWorldData(world);
        BlockPlacementQueue queue = new BlockPlacementQueue();
        
        BlockPos basePos = building.getPosition();
        int rotation = building.getRotation();
        
        // Get sorted build order (already sorted by Y in StructureData)
        for (net.minecraft.util.math.BlockPos relativePos : structureData.getBuildOrder()) {
            StructureBlock structureBlock = structureData.getBlockAt(relativePos);
            if (structureBlock == null) {
                continue;
            }
            
            // Apply rotation to relative position
            BlockPos rotatedPos = applyRotation(relativePos, rotation, structureData.getDimensions());
            
            // Calculate absolute world position
            BlockPos worldPos = basePos.add(rotatedPos);
            
            // Rotate block state if needed (for directional blocks like stairs, slabs, etc.)
            BlockState rotatedState = BlockStateRotator.rotateBlockState(
                structureBlock.getBlockState(), 
                rotation
            );
            
            // CRITICAL: Skip barrier blocks - they cause invisible collision blocks
            // Barrier blocks should never be placed from NBT structures
            if (rotatedState.getBlock() == Blocks.BARRIER) {
                continue; // Skip this block entirely
            }
            
            // Add to queue with rotated block state
            queue.addBlock(worldPos, rotatedState, structureBlock.getBlockEntityData());
        }
        
        data.addBuildingQueue(building.getId(), queue);
        SettlementsMod.LOGGER.info("Initialized placement queue for building {} with {} blocks", 
            building.getId(), queue.getTotalBlocks());
    }
    
    /**
     * Applies rotation to a relative position.
     * Rotates around the origin (0, 0, 0) - same formula used in ConfirmPlacementPacket and BuildModePreviewRenderer.
     * @param pos The relative position
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @param dimensions Structure dimensions (unused, kept for compatibility)
     * @return Rotated position
     */
    private static BlockPos applyRotation(BlockPos pos, int rotation, net.minecraft.util.math.Vec3i dimensions) {
        int x = pos.getX();
        int z = pos.getZ();
        
        switch (rotation) {
            case 90:
                // Rotate 90 degrees clockwise around origin: (x, y, z) -> (-z, y, x)
                return new BlockPos(-z, pos.getY(), x);
            case 180:
                // Rotate 180 degrees around origin: (x, y, z) -> (-x, y, -z)
                return new BlockPos(-x, pos.getY(), -z);
            case 270:
                // Rotate 270 degrees clockwise around origin: (x, y, z) -> (z, y, -x)
                return new BlockPos(z, pos.getY(), -x);
            case 0:
            default:
                return pos;
        }
    }
    
    /**
     * Gets the world data for a server world.
     */
    private static WorldPlacementData getWorldData(ServerWorld world) {
        if (instance == null) {
            SettlementsMod.LOGGER.error("BlockPlacementScheduler not initialized! Call register() first.");
            return new WorldPlacementData(); // Return a temporary instance to avoid NPE
        }
        return instance.worldData.computeIfAbsent(world, w -> new WorldPlacementData());
    }
    
    /**
     * Stops block placement for a building (e.g., when cancelled).
     * Removes the building's queue from the scheduler.
     * @param buildingId The building ID to stop
     * @param world The server world
     */
    public static void stopBuilding(UUID buildingId, ServerWorld world) {
        WorldPlacementData data = getWorldData(world);
        data.removeBuildingQueue(buildingId);
        SettlementsMod.LOGGER.info("Stopped block placement for building {}", buildingId);
    }
    
    /**
     * Per-world placement data and state.
     */
    private static class WorldPlacementData {
        private final Map<UUID, BlockPlacementQueue> buildingQueues = new HashMap<>();
        private int tickCounter = 0;
        
        /**
         * Adds a building queue to be processed.
         */
        public void addBuildingQueue(UUID buildingId, BlockPlacementQueue queue) {
            buildingQueues.put(buildingId, queue);
        }
        
        /**
         * Removes a building queue (when building is complete or cancelled).
         */
        public void removeBuildingQueue(UUID buildingId) {
            buildingQueues.remove(buildingId);
        }
        
        /**
         * Checks if any player in creative mode is nearby any building under construction.
         * @param world The server world
         * @return true if a creative mode player is within 64 blocks of any building
         */
        private boolean isCreativeModePlayerNearby(ServerWorld world) {
            // Check all players in the world
            for (ServerPlayerEntity player : world.getPlayers()) {
                // Check if player is in creative mode
                if (player.interactionManager.getGameMode() == GameMode.CREATIVE || 
                    player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
                    // Check if player is near any building under construction
                    BlockPos playerPos = player.getBlockPos();
                    
                    // Check all buildings in all settlements
                    SettlementManager manager = SettlementManager.getInstance(world);
                    for (Settlement settlement : manager.getAllSettlements()) {
                        for (Building building : settlement.getBuildings()) {
                            // Only check buildings that are in progress
                            if (building.getStatus() == com.secretasain.settlements.building.BuildingStatus.IN_PROGRESS) {
                                BlockPos buildingPos = building.getPosition();
                                // Check if player is within 64 blocks of the building
                                double distanceSq = playerPos.getSquaredDistance(buildingPos);
                                if (distanceSq <= 64 * 64) { // 64 blocks radius
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }
        
        /**
         * Performs a tick update.
         */
        public void tick(ServerWorld world) {
            tickCounter++;
            
            // Check if any player in creative mode is nearby any building
            // If so, speed up construction by 100% (half the tick delay)
            boolean creativeModeActive = isCreativeModePlayerNearby(world);
            int effectiveTickDelay = creativeModeActive ? TICK_DELAY / 2 : TICK_DELAY;
            
            // Only process every N ticks
            if (tickCounter < effectiveTickDelay) {
                return;
            }
            tickCounter = 0;
            
            SettlementManager manager = SettlementManager.getInstance(world);
            
            // Process each building queue
            for (Map.Entry<UUID, BlockPlacementQueue> entry : new HashMap<>(buildingQueues).entrySet()) {
                UUID buildingId = entry.getKey();
                BlockPlacementQueue queue = entry.getValue();
                
                // Find the building
                Building building = null;
                Settlement settlement = null;
                for (Settlement s : manager.getAllSettlements()) {
                    for (Building b : s.getBuildings()) {
                        if (b.getId().equals(buildingId)) {
                            building = b;
                            settlement = s;
                            break;
                        }
                    }
                    if (building != null) break;
                }
                
                if (building == null || settlement == null) {
                    // Building no longer exists, remove queue
                    buildingQueues.remove(buildingId);
                    continue;
                }
                
                // Check if building is still IN_PROGRESS
                if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.IN_PROGRESS) {
                    // Building is no longer in progress, remove queue
                    buildingQueues.remove(buildingId);
                    continue;
                }
                
                // Place blocks
                // In creative mode, place 2 blocks per cycle instead of 1 (100% speed increase)
                int effectiveBlocksPerTick = creativeModeActive ? BLOCKS_PER_TICK * 2 : BLOCKS_PER_TICK;
                
                int blocksPlaced = 0;
                while (!queue.isEmpty() && blocksPlaced < effectiveBlocksPerTick) {
                    QueuedBlock queuedBlock = queue.getNextBlock();
                    if (queuedBlock != null) {
                        if (placeBlock(queuedBlock, world, building)) {
                            blocksPlaced++;
                        }
                    }
                }
                
                // Update building progress
                building.setProgress(queue.getProgress());
                manager.markDirty();
                
                // Check if queue is empty (building complete)
                if (queue.isEmpty()) {
                    completeBuilding(building, settlement, world, manager);
                    buildingQueues.remove(buildingId);
                }
            }
        }
        
        /**
         * Places a single block in the world.
         */
        private boolean placeBlock(QueuedBlock queuedBlock, ServerWorld world, Building building) {
            BlockPos pos = queuedBlock.getWorldPos();
            BlockState state = queuedBlock.getBlockState();
            
            // Skip air blocks
            if (state.isAir()) {
                return true;
            }
            
            // CRITICAL: Skip barrier blocks - they cause invisible collision blocks
            // This is a safety check in case barrier blocks somehow made it into the queue
            if (state.getBlock() == Blocks.BARRIER) {
                SettlementsMod.LOGGER.warn("Attempted to place barrier block at {} for building {} - skipping", pos, building.getId());
                return true; // Return true to continue processing other blocks
            }
            
            // Check if chunk is loaded
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                SettlementsMod.LOGGER.debug("Cannot place block at {}: chunk not loaded", pos);
                return false;
            }
            
            // Check if position is valid (air or same block)
            BlockState existingState = world.getBlockState(pos);
            
            // CRITICAL: Remove ghost block if one exists at this position
            // This ensures ghost blocks are removed immediately when actual blocks replace them
            if (existingState.isOf(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK)) {
                // Remove ghost block entity if it exists
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) {
                    world.removeBlockEntity(pos);
                }
                // The ghost block will be replaced by the actual block below
            }
            
            // Allow placement if:
            // 1. Air
            // 2. Same block (already placed)
            // 3. Ghost block (will be replaced)
            // For other blocks, we'll try to place and let Minecraft handle validation
            // (replaceable blocks like tall grass will be replaced automatically)
            if (!existingState.isAir() && !existingState.equals(state) && !existingState.isOf(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK)) {
                // Check if it's a solid block that would prevent placement
                // For now, allow placement - Minecraft's setBlockState will handle validation
                // If it fails, the block just won't be placed (which is fine)
            }
            
            try {
                // Special handling for doors - they need both halves placed together
                if (state.getBlock() instanceof DoorBlock) {
                    return placeDoorBlock(world, pos, state);
                }
                
                // Place the block with proper flags for client synchronization
                // NOTIFY_NEIGHBORS: Notify neighboring blocks of the change
                // NOTIFY_LISTENERS: Notify block update listeners (including clients)
                // This ensures blocks are visible on the client
                // Note: Removed FORCE_STATE flag as it prevents client synchronization
                int flags = Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS;
                boolean changed = world.setBlockState(pos, state, flags);
                
                if (!changed) {
                    SettlementsMod.LOGGER.debug("Block state did not change at {}", pos);
                    return false;
                }
                
                // Set block entity data if present
                if (queuedBlock.hasBlockEntityData()) {
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity != null) {
                        NbtCompound nbt = queuedBlock.getBlockEntityData().copy();
                        nbt.putInt("x", pos.getX());
                        nbt.putInt("y", pos.getY());
                        nbt.putInt("z", pos.getZ());
                        blockEntity.readNbt(nbt);
                        blockEntity.markDirty();
                    }
                }
                
                // Visual feedback
                addVisualFeedback(world, pos, state);
                
                return true;
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Error placing block at {}", pos, e);
                return false;
            }
        }
        
        /**
         * Places a door block, ensuring both halves are placed together.
         * Doors require both upper and lower halves to be placed in the same operation.
         * @param world The server world
         * @param pos The position to place the door
         * @param state The door block state
         * @return true if placed successfully
         */
        private boolean placeDoorBlock(ServerWorld world, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof DoorBlock)) {
                return false;
            }
            
            DoubleBlockHalf half = state.get(DoorBlock.HALF);
            
            int flags = Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS;
            
            if (half == DoubleBlockHalf.LOWER) {
                // Place lower half
                boolean lowerPlaced = world.setBlockState(pos, state, flags);
                if (!lowerPlaced) {
                    return false;
                }
                
                // Immediately place upper half
                BlockPos upperPos = pos.up();
                BlockState upperState = state.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                boolean upperPlaced = world.setBlockState(upperPos, upperState, flags);
                
                if (!upperPlaced) {
                    // If upper half failed, remove lower half to prevent broken door
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
                    SettlementsMod.LOGGER.warn("Failed to place door upper half at {}, removed lower half", upperPos);
                    return false;
                }
                
                // Visual feedback for both halves
                addVisualFeedback(world, pos, state);
                addVisualFeedback(world, upperPos, upperState);
                
                return true;
            } else {
                // Upper half - check if lower half is already placed
                BlockPos lowerPos = pos.down();
                BlockState lowerState = world.getBlockState(lowerPos);
                
                if (lowerState.getBlock() instanceof DoorBlock && 
                    lowerState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    // Lower half is already placed, just place upper half
                    boolean placed = world.setBlockState(pos, state, flags);
                    if (placed) {
                        addVisualFeedback(world, pos, state);
                    }
                    return placed;
                } else {
                    // Lower half not placed yet - skip this block, it will be placed when we process the lower half
                    SettlementsMod.LOGGER.debug("Skipping door upper half at {} - lower half not placed yet", pos);
                    return true; // Return true so we don't retry, but don't actually place
                }
            }
        }
        
        /**
         * Adds visual feedback for block placement (particles and sounds).
         */
        private void addVisualFeedback(ServerWorld world, BlockPos pos, BlockState state) {
            // Spawn block break particles
            world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                10, 0.2, 0.2, 0.2, 0.05
            );
            
            // Play block placement sound
            world.playSound(null, pos, state.getSoundGroup().getPlaceSound(), 
                SoundCategory.BLOCKS, 0.5f, 1.0f);
        }
        
        /**
         * Completes a building construction.
         */
        private void completeBuilding(Building building, Settlement settlement, ServerWorld world, 
                                     SettlementManager manager) {
            // Update building status to COMPLETED
            building.updateStatus(com.secretasain.settlements.building.BuildingStatus.COMPLETED);
            building.setProgress(1.0f);
            
            // CRITICAL: Materials were already consumed from settlement storage when building started
            // providedMaterials tracks what was consumed - we'll use it for the book, then clear it
            Map<Identifier, Integer> provided = building.getProvidedMaterials();
            if (!provided.isEmpty()) {
                SettlementsMod.LOGGER.info("Building {} completed - materials were consumed: {}", building.getId(), provided);
            }
            
            // CRITICAL: Remove ALL barrier blocks - both tracked and any that might be in the structure
            // First, remove barrier blocks from tracked positions
            int barrierCount = 0;
            for (BlockPos barrierPos : building.getBarrierPositions()) {
                if (world.getChunkManager().isChunkLoaded(barrierPos.getX() >> 4, barrierPos.getZ() >> 4)) {
                    if (world.getBlockState(barrierPos).isOf(Blocks.BARRIER)) {
                        world.setBlockState(barrierPos, Blocks.AIR.getDefaultState(), 
                            Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
                        barrierCount++;
                    }
                }
            }
            // Clear barrier positions using setBarrierPositions with empty list (can't clear unmodifiable list directly)
            building.setBarrierPositions(new java.util.ArrayList<>());
            
            // CRITICAL: Remove any remaining ghost blocks and barrier blocks
            // This comprehensive scan will catch barrier blocks that weren't tracked or are in the structure
            removeGhostBlocks(building, world);
            
            // CRITICAL: Also perform an aggressive barrier block scan of the entire building area
            removeAllBarrierBlocks(building, world);
            
            if (barrierCount > 0) {
                SettlementsMod.LOGGER.info("Removed {} tracked barrier blocks after building {} completion", barrierCount, building.getId());
            }
            
            // Create written book receipt/ledger in chest next to lectern (before clearing providedMaterials)
            createCompletionBook(building, settlement, world);
            
            // Clear providedMaterials after creating book (materials were already consumed from settlement storage)
            building.clearProvidedMaterials();
            
            // Update settlement level (may have changed with new completed building)
            int oldLevel = settlement.getLevel();
            if (SettlementLevelManager.updateSettlementLevel(settlement)) {
                int newLevel = settlement.getLevel();
                SettlementsMod.LOGGER.info("Settlement {} leveled up from {} to {} after building completion", 
                    settlement.getName(), oldLevel, newLevel);
            }
            
            manager.markDirty();
            SettlementsMod.LOGGER.info("Building {} construction completed", building.getId());
            
            // Send packet to client to refresh UI (remove building from list)
            // TODO: Create RefreshSettlementPacket if needed, or client will refresh on next open
        }
        
        /**
         * Creates a written book in the chest next to the lectern with building completion details.
         */
        private void createCompletionBook(Building building, Settlement settlement, ServerWorld world) {
            BlockPos lecternPos = settlement.getLecternPos();
            if (lecternPos == null) {
                SettlementsMod.LOGGER.warn("Cannot create completion book: lectern position is null");
                return;
            }
            
            // Find adjacent chests
            java.util.List<net.minecraft.block.entity.ChestBlockEntity> chests = new java.util.ArrayList<>();
            for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
                BlockPos chestPos = lecternPos.offset(direction);
                net.minecraft.block.BlockState blockState = world.getBlockState(chestPos);
                net.minecraft.block.Block block = blockState.getBlock();
                
                if (block instanceof net.minecraft.block.ChestBlock) {
                    net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(chestPos);
                    if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity) {
                        chests.add((net.minecraft.block.entity.ChestBlockEntity) blockEntity);
                    }
                }
            }
            
            if (chests.isEmpty()) {
                SettlementsMod.LOGGER.debug("No adjacent chests found for completion book");
                return;
            }
            
            // Create written book
            net.minecraft.item.ItemStack book = new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);
            net.minecraft.nbt.NbtCompound bookNbt = book.getOrCreateNbt();
            
            // Set book title
            String buildingName = building.getStructureType().getPath().replace("_", " ").replace(".nbt", "");
            bookNbt.putString("title", "Building Ledger");
            bookNbt.putString("author", settlement.getName());
            
            // Create pages
            net.minecraft.nbt.NbtList pages = new net.minecraft.nbt.NbtList();
            
            // Page 1: What was built
            // CRITICAL FIX: Ensure page text doesn't exceed 256 characters
            String page1 = "Building Completed:\n" + buildingName + "\n\nLocation:\n" + 
                          building.getPosition().getX() + ", " + 
                          building.getPosition().getY() + ", " + 
                          building.getPosition().getZ();
            // Truncate if too long (shouldn't happen, but safety check)
            if (page1.length() > 256) {
                page1 = page1.substring(0, 253) + "...";
            }
            pages.add(net.minecraft.nbt.NbtString.of(page1));
            
            // Page 2: Materials used
            // CRITICAL FIX: Minecraft book pages have a 256 character limit
            // We need to split materials across multiple pages if needed
            Map<Identifier, Integer> provided = building.getProvidedMaterials();
            
            // CRITICAL FIX: Log what we're reading for debugging
            SettlementsMod.LOGGER.info("Creating completion book - providedMaterials size: {}, requiredMaterials size: {}", 
                provided.size(), building.getRequiredMaterials().size());
            
            // If providedMaterials is empty, use requiredMaterials as fallback
            // This can happen if materials were cleared before book creation
            if (provided.isEmpty()) {
                SettlementsMod.LOGGER.warn("providedMaterials is empty, using requiredMaterials as fallback");
                provided = building.getRequiredMaterials();
            }
            
            if (provided.isEmpty()) {
                String page2 = "Materials Used:\n\nNo materials recorded.\n\nCompleted successfully.";
                pages.add(net.minecraft.nbt.NbtString.of(page2));
            } else {
                // Build materials list, splitting across pages if needed (256 char limit per page)
                java.util.List<String> materialLines = new java.util.ArrayList<>();
                for (Map.Entry<Identifier, Integer> entry : provided.entrySet()) {
                    net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(entry.getKey());
                    String itemName = item != null ? item.getName().getString() : entry.getKey().getPath();
                    materialLines.add(itemName + " x" + entry.getValue());
                }
                
                // Create pages with materials, respecting 256 character limit per page
                // CRITICAL FIX: Be conservative with character limits to avoid invalid book tags
                StringBuilder currentPage = new StringBuilder("Materials Used:\n\n");
                String footer = "\n\nCompleted successfully.";
                int maxPageLength = 250; // Conservative limit (256 - some buffer)
                
                for (String line : materialLines) {
                    // Check if adding this line would exceed limit
                    String testLine = currentPage.toString() + line + "\n" + footer;
                    if (testLine.length() > maxPageLength) {
                        // Finish current page (without this line)
                        if (currentPage.length() > "Materials Used:\n\n".length()) {
                            currentPage.append(footer);
                            String pageText = currentPage.toString();
                            // Final safety check
                            if (pageText.length() <= 256) {
                                pages.add(net.minecraft.nbt.NbtString.of(pageText));
                            } else {
                                // Truncate if somehow still too long
                                pageText = pageText.substring(0, Math.min(256, pageText.length()));
                                pages.add(net.minecraft.nbt.NbtString.of(pageText));
                            }
                        }
                        // Start new page with this line
                        currentPage = new StringBuilder("Materials (cont):\n\n" + line + "\n");
                    } else {
                        currentPage.append(line).append("\n");
                    }
                }
                // Add final page
                if (currentPage.length() > 0) {
                    currentPage.append(footer);
                    String pageText = currentPage.toString();
                    // Final safety check - ensure page doesn't exceed 256 characters
                    if (pageText.length() > 256) {
                        // Truncate to fit
                        pageText = pageText.substring(0, 253) + "...";
                    }
                    pages.add(net.minecraft.nbt.NbtString.of(pageText));
                }
            }
            
            // Page 3: Signature (only if we have at least one page)
            if (pages.size() > 0) {
                String signature = "Signed by:\n" + settlement.getName() + "\n\n" + 
                                  "Construction completed on:\n" + 
                                  java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                // Truncate if too long
                if (signature.length() > 256) {
                    signature = signature.substring(0, 253) + "...";
                }
                pages.add(net.minecraft.nbt.NbtString.of(signature));
            }
            
            // CRITICAL FIX: Ensure we have at least one page before creating book
            if (pages.isEmpty()) {
                SettlementsMod.LOGGER.warn("Cannot create book - no pages generated");
                return;
            }
            
            bookNbt.put("pages", pages);
            bookNbt.putInt("generation", 0); // Original book
            bookNbt.putBoolean("resolved", true); // Mark as resolved (signed)
            
            // Try to place book in first available chest slot
            for (net.minecraft.block.entity.ChestBlockEntity chest : chests) {
                for (int slot = 0; slot < chest.size(); slot++) {
                    if (chest.getStack(slot).isEmpty()) {
                        chest.setStack(slot, book);
                        chest.markDirty();
                        SettlementsMod.LOGGER.info("Created completion book in chest at {}", chest.getPos());
                        return;
                    }
                }
            }
            
            SettlementsMod.LOGGER.warn("Could not place completion book - all chest slots full");
        }
        
        /**
         * Removes ghost blocks and barrier blocks for a building after construction completes.
         * This ensures any ghost blocks or barrier blocks that weren't replaced by actual blocks are cleaned up.
         * 
         * This method performs a comprehensive scan of the entire building area to find and remove
         * all ghost blocks and barrier blocks, not just those in the tracked list. This catches corner blocks
         * and any that might have been missed during construction.
         */
        private void removeGhostBlocks(Building building, ServerWorld world) {
            int countFromTracked = 0;
            int countFromScan = 0;
            int barrierBlocksRemoved = 0;
            
            // First, remove ghost blocks from tracked positions
            for (net.minecraft.util.math.BlockPos ghostPos : building.getGhostBlockPositions()) {
                // Only remove if chunk is loaded
                if (world.getChunkManager().isChunkLoaded(ghostPos.getX() >> 4, ghostPos.getZ() >> 4)) {
                    net.minecraft.block.BlockState currentState = world.getBlockState(ghostPos);
                    // Check if it's still a ghost block (might have been replaced by actual block during construction)
                    if (currentState.isOf(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK)) {
                        // Remove block entity if it exists
                        BlockEntity blockEntity = world.getBlockEntity(ghostPos);
                        if (blockEntity != null) {
                            world.removeBlockEntity(ghostPos);
                        }
                        world.setBlockState(ghostPos, Blocks.AIR.getDefaultState(),
                            Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
                        countFromTracked++;
                    }
                }
            }
            building.clearGhostBlockPositions();
            
            // Second, scan the entire building area to find any remaining ghost blocks and barrier blocks
            // This catches corner blocks and any that weren't properly tracked
            // CRITICAL: Use the same approach as block placement - scan all actual block positions from structure
            try {
                // Load structure data to get all block positions
                StructureData structureData = com.secretasain.settlements.building.StructureLoader.loadStructure(
                    building.getStructureType(), world.getServer());
                
                if (structureData != null) {
                    BlockPos basePos = building.getPosition();
                    int rotation = building.getRotation();
                    net.minecraft.util.math.Vec3i dimensions = structureData.getDimensions();
                    
                    // Scan all actual block positions from the structure (same as when placing)
                    // This ensures we cover the exact same area where blocks were placed
                    java.util.Set<BlockPos> scannedPositions = new java.util.HashSet<>();
                    
                    for (com.secretasain.settlements.building.StructureBlock structureBlock : structureData.getBlocks()) {
                        net.minecraft.util.math.BlockPos relativePos = structureBlock.getRelativePos();
                        
                        // Apply rotation to relative position (same as block placement)
                        BlockPos rotatedPos = applyRotation(relativePos, rotation, dimensions);
                        
                        // Calculate absolute world position
                        BlockPos worldPos = basePos.add(rotatedPos);
                        scannedPositions.add(worldPos);
                        
                        // Also scan adjacent positions (1 block padding) to catch corner blocks
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dy == 0 && dz == 0) continue; // Skip center (already added)
                                    BlockPos adjacentPos = worldPos.add(dx, dy, dz);
                                    scannedPositions.add(adjacentPos);
                                }
                            }
                        }
                    }
                    
                    // Now scan all collected positions
                    for (BlockPos scanPos : scannedPositions) {
                        // Only check if chunk is loaded
                        if (world.getChunkManager().isChunkLoaded(scanPos.getX() >> 4, scanPos.getZ() >> 4)) {
                            net.minecraft.block.BlockState currentState = world.getBlockState(scanPos);
                            
                            // Remove ghost blocks
                            if (currentState.isOf(com.secretasain.settlements.block.ModBlocks.GHOST_BLOCK)) {
                                // Since we're scanning the building's area, any ghost block found here
                                // is likely from this building. Remove it to ensure complete cleanup.
                                BlockEntity blockEntity = world.getBlockEntity(scanPos);
                                
                                // Remove block entity if it exists
                                if (blockEntity != null) {
                                    world.removeBlockEntity(scanPos);
                                }
                                world.setBlockState(scanPos, Blocks.AIR.getDefaultState(),
                                    Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
                                countFromScan++;
                            }
                            
                            // CRITICAL: Also remove barrier blocks - they cause invisible collision blocks
                            // Barrier blocks should never be in completed buildings
                            if (currentState.getBlock() == Blocks.BARRIER) {
                                BlockEntity blockEntity = world.getBlockEntity(scanPos);
                                if (blockEntity != null) {
                                    world.removeBlockEntity(scanPos);
                                }
                                world.setBlockState(scanPos, Blocks.AIR.getDefaultState(),
                                    Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
                                barrierBlocksRemoved++;
                                SettlementsMod.LOGGER.info("Removed barrier block at {} for building {}", scanPos, building.getId());
                            }
                        }
                    }
                    
                    // Fallback: Also scan a bounding box area to catch any barrier blocks outside structure bounds
                    // This is a safety measure in case barrier blocks were placed outside the structure
                    int maxDim = Math.max(Math.max(dimensions.getX(), dimensions.getY()), dimensions.getZ());
                    int padding = 2; // 2-block padding to catch any barrier blocks outside structure
                    
                    for (int x = -padding; x < maxDim + padding; x++) {
                        for (int y = -padding; y < dimensions.getY() + padding; y++) {
                            for (int z = -padding; z < maxDim + padding; z++) {
                                BlockPos scanPos = basePos.add(x, y, z);
                                
                                // Skip if we already scanned this position
                                if (scannedPositions.contains(scanPos)) {
                                    continue;
                                }
                                
                                // Only check if chunk is loaded
                                if (world.getChunkManager().isChunkLoaded(scanPos.getX() >> 4, scanPos.getZ() >> 4)) {
                                    net.minecraft.block.BlockState currentState = world.getBlockState(scanPos);
                                    
                                    // Only remove barrier blocks in the fallback scan (ghost blocks should be in structure bounds)
                                    if (currentState.getBlock() == Blocks.BARRIER) {
                                        BlockEntity blockEntity = world.getBlockEntity(scanPos);
                                        if (blockEntity != null) {
                                            world.removeBlockEntity(scanPos);
                                        }
                                        world.setBlockState(scanPos, Blocks.AIR.getDefaultState(),
                                            Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
                                        barrierBlocksRemoved++;
                                        SettlementsMod.LOGGER.info("Removed barrier block at {} (outside structure bounds) for building {}", scanPos, building.getId());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                SettlementsMod.LOGGER.warn("Error scanning building area for ghost blocks and barrier blocks: {}", e.getMessage(), e);
                // Continue - we've already removed tracked ghost blocks
            }
            
            int totalCount = countFromTracked + countFromScan;
            if (totalCount > 0 || barrierBlocksRemoved > 0) {
                SettlementsMod.LOGGER.info("Removed {} ghost blocks and {} barrier blocks after building {} completion ({} ghost blocks from tracked positions, {} ghost blocks from area scan)", 
                    totalCount, barrierBlocksRemoved, building.getId(), countFromTracked, countFromScan);
            }
        }
        
        /**
         * Aggressively removes ALL barrier blocks in the building area.
         * This is a comprehensive scan that catches barrier blocks from any source.
         */
        private void removeAllBarrierBlocks(Building building, ServerWorld world) {
            int barrierBlocksRemoved = 0;
            
            try {
                // Load structure data to get dimensions
                StructureData structureData = com.secretasain.settlements.building.StructureLoader.loadStructure(
                    building.getStructureType(), world.getServer());
                
                if (structureData != null) {
                    net.minecraft.util.math.Vec3i dimensions = structureData.getDimensions();
                    BlockPos basePos = building.getPosition();
                    int rotation = building.getRotation();
                    
                    // Calculate rotated dimensions (swap X and Z for 90/270 degree rotations)
                    int width, depth;
                    if (rotation == 90 || rotation == 270) {
                        width = dimensions.getZ();
                        depth = dimensions.getX();
                    } else {
                        width = dimensions.getX();
                        depth = dimensions.getZ();
                    }
                    int height = dimensions.getY();
                    
                    // AGGRESSIVE SCAN: Scan entire building area with generous padding
                    // This ensures we catch barrier blocks that might be outside the structure bounds
                    int padding = 5; // 5-block padding to catch any barrier blocks
                    for (int x = -padding; x < width + padding; x++) {
                        for (int y = -padding; y < height + padding; y++) {
                            for (int z = -padding; z < depth + padding; z++) {
                                BlockPos scanPos = basePos.add(x, y, z);
                                
                                // Only check if chunk is loaded
                                if (world.getChunkManager().isChunkLoaded(scanPos.getX() >> 4, scanPos.getZ() >> 4)) {
                                    net.minecraft.block.BlockState currentState = world.getBlockState(scanPos);
                                    
                                    // Remove ANY barrier block found in the area
                                    if (currentState.getBlock() == Blocks.BARRIER) {
                                        BlockEntity blockEntity = world.getBlockEntity(scanPos);
                                        if (blockEntity != null) {
                                            world.removeBlockEntity(scanPos);
                                        }
                                        world.setBlockState(scanPos, Blocks.AIR.getDefaultState(),
                                            Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
                                        barrierBlocksRemoved++;
                                        SettlementsMod.LOGGER.info("Removed barrier block at {} for building {} (aggressive scan)", scanPos, building.getId());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                SettlementsMod.LOGGER.warn("Error performing aggressive barrier block scan: {}", e.getMessage(), e);
            }
            
            if (barrierBlocksRemoved > 0) {
                SettlementsMod.LOGGER.info("Removed {} barrier blocks from aggressive scan after building {} completion", 
                    barrierBlocksRemoved, building.getId());
            }
        }
    }
}

