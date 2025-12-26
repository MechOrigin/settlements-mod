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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
            
            // Add to queue
            queue.addBlock(worldPos, structureBlock.getBlockState(), structureBlock.getBlockEntityData());
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
         * Performs a tick update.
         */
        public void tick(ServerWorld world) {
            tickCounter++;
            
            // Only process every N ticks
            if (tickCounter < TICK_DELAY) {
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
                int blocksPlaced = 0;
                while (!queue.isEmpty() && blocksPlaced < BLOCKS_PER_TICK) {
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
            
            // Check if chunk is loaded
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                SettlementsMod.LOGGER.debug("Cannot place block at {}: chunk not loaded", pos);
                return false;
            }
            
            // Check if position is valid (air or same block)
            BlockState existingState = world.getBlockState(pos);
            // Allow placement if:
            // 1. Air
            // 2. Same block (already placed)
            // For other blocks, we'll try to place and let Minecraft handle validation
            // (replaceable blocks like tall grass will be replaced automatically)
            if (!existingState.isAir() && !existingState.equals(state)) {
                // Check if it's a solid block that would prevent placement
                // For now, allow placement - Minecraft's setBlockState will handle validation
                // If it fails, the block just won't be placed (which is fine)
            }
            
            try {
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
            
            // Remove barrier blocks
            for (BlockPos barrierPos : building.getBarrierPositions()) {
                if (world.getBlockState(barrierPos).isOf(Blocks.BARRIER)) {
                    world.setBlockState(barrierPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_NEIGHBORS);
                }
            }
            // Clear barrier positions using setBarrierPositions with empty list (can't clear unmodifiable list directly)
            building.setBarrierPositions(new java.util.ArrayList<>());
            
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
    }
}

