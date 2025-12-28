package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import net.minecraft.block.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles active log harvesting for lumberyard buildings.
 * Scans the area around the lumberyard building for log blocks and harvests them.
 * Supports all log types including modded logs.
 */
public class LumberjackLogHarvester {
    private static final int HARVEST_RADIUS = 32; // Search radius around the building (in blocks)
    private static final int MAX_LOGS_PER_HARVEST = 16; // Maximum logs to harvest per cycle
    private static final int LEAF_CHECK_RADIUS = 8; // Check for leaves within 8 blocks (tree detection) - increased for spruce trees
    private static final int TREE_HEIGHT_CHECK = 16; // Check up to 16 blocks above for connected logs (tree trunk) - increased for tall spruce trees
    private static final int TREE_TRUNK_CHECK_DOWN = 6; // Check down to find tree base (natural trees have trunks going to ground)
    private static final int MIN_LEAVES_COUNT = 5; // Minimum number of leaves blocks nearby to be considered a tree (STRICT)
    private static final int MIN_CONNECTED_LOGS = 3; // Minimum connected logs in trunk pattern (STRICT)
    private static final int MAX_TREE_SIZE = 64; // Maximum tree size for harvesting (prevents harvesting huge structures)
    
    /**
     * Harvests logs in the area around a lumberyard building.
     * @param building The lumberyard building
     * @param world The server world
     * @param server The Minecraft server (for structure loading)
     * @return List of harvested log items
     */
    public static List<ItemStack> harvestLogs(Building building, ServerWorld world, MinecraftServer server) {
        List<ItemStack> harvestedItems = new ArrayList<>();
        
        // Only process lumberyard buildings
        if (!isLumberyardBuilding(building)) {
            return harvestedItems;
        }
        
        // Load structure data to get the building center
        StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
        if (structureData == null) {
            SettlementsMod.LOGGER.warn("Could not load structure data for building {}: {}", 
                building.getId(), building.getStructureType());
            return harvestedItems;
        }
        
        // Get building position
        BlockPos buildingPos = building.getPosition();
        
        // Get structure dimensions to find center
        Vec3i size = structureData.getDimensions();
        BlockPos centerPos = buildingPos.add(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
        
        // Scan for logs in a radius around the building center
        // Track which trees we've already started harvesting to avoid harvesting the same tree multiple times
        java.util.Set<BlockPos> processedTreeStarts = new java.util.HashSet<>();
        int treesHarvested = 0;
        
        for (int x = -HARVEST_RADIUS; x <= HARVEST_RADIUS && treesHarvested < MAX_LOGS_PER_HARVEST; x++) {
            for (int y = -HARVEST_RADIUS; y <= HARVEST_RADIUS && treesHarvested < MAX_LOGS_PER_HARVEST; y++) {
                for (int z = -HARVEST_RADIUS; z <= HARVEST_RADIUS && treesHarvested < MAX_LOGS_PER_HARVEST; z++) {
                    BlockPos checkPos = centerPos.add(x, y, z);
                    
                    // Check if chunk is loaded
                    if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    // Get block state
                    BlockState blockState = world.getBlockState(checkPos);
                    Block block = blockState.getBlock();
                    
                    // Check if this is a log block
                    if (isLogBlock(block, blockState)) {
                        // Check if we've already started harvesting a tree from a nearby log
                        // (to avoid harvesting the same tree multiple times from different starting points)
                        boolean alreadyProcessed = false;
                        for (BlockPos processedPos : processedTreeStarts) {
                            if (checkPos.getSquaredDistance(processedPos) <= MAX_TREE_SIZE * MAX_TREE_SIZE) {
                                alreadyProcessed = true;
                                break;
                            }
                        }
                        
                        if (alreadyProcessed) {
                            continue; // Skip - this log is part of a tree we're already harvesting
                        }
                        
                        // Check if this log is part of a natural tree (not a building)
                        if (isNaturalTree(building, checkPos, blockState, block, world, server)) {
                            // Harvest the entire tree (all connected logs)
                            List<ItemStack> treeDrops = harvestTree(checkPos, blockState, block, building, world, server);
                            harvestedItems.addAll(treeDrops);
                            
                            if (!treeDrops.isEmpty()) {
                                treesHarvested++;
                                processedTreeStarts.add(checkPos); // Mark this tree as processed
                                
                                // Record tree harvest location for second villager to collect items
                                LumberyardItemCollectorSystem.recordTreeHarvest(
                                    building.getId(), 
                                    checkPos, 
                                    world.getTime()
                                );
                                
                                SettlementsMod.LOGGER.info("Harvested entire tree starting at {} ({} items)", 
                                    checkPos, treeDrops.size());
                            }
                        } else {
                            SettlementsMod.LOGGER.debug("Skipping log {} at {} - not a natural tree", 
                                block, checkPos);
                        }
                    }
                }
            }
        }
        
        if (!harvestedItems.isEmpty()) {
            SettlementsMod.LOGGER.info("Lumberjack harvested {} items from {} trees around building {}", 
                harvestedItems.size(), treesHarvested, building.getId());
        }
        
        return harvestedItems;
    }
    
    /**
     * Checks if a building is a lumberyard building.
     */
    private static boolean isLumberyardBuilding(Building building) {
        String structureName = building.getStructureType().getPath().toLowerCase();
        return structureName.contains("lumber") || structureName.contains("lumberyard") || 
               structureName.contains("lumber_jack") || structureName.contains("lumberjack");
    }
    
    /**
     * Checks if a block is a log block (supports vanilla and modded logs).
     * @param block The block to check
     * @param blockState The block state
     * @return true if the block is a log
     */
    private static boolean isLogBlock(Block block, BlockState blockState) {
        // Check for mangrove roots explicitly (they don't extend PillarBlock)
        if (block == Blocks.MANGROVE_ROOTS || block == Blocks.MUDDY_MANGROVE_ROOTS) {
            return true;
        }
        
        // Check for vanilla log blocks - logs extend PillarBlock and have AXIS property
        if (block instanceof PillarBlock) {
            // PillarBlock is used for logs, stripped logs, and some other blocks
            // Check if it has the AXIS property (logs have this)
            if (blockState.contains(Properties.AXIS)) {
                // Additional check: logs typically have "log", "wood", "stem", or "hyphae" in their name
                String blockName = block.getTranslationKey().toLowerCase();
                if (blockName.contains("log") || blockName.contains("wood") || 
                    blockName.contains("stem") || blockName.contains("hyphae")) {
                    // Make sure it's not something like "logbook" or other non-log blocks
                    if (!blockName.contains("door") && !blockName.contains("trapdoor") && 
                        !blockName.contains("fence") && !blockName.contains("plank") &&
                        !blockName.contains("slab") && !blockName.contains("stair") &&
                        !blockName.contains("button") && !blockName.contains("pressure_plate") &&
                        !blockName.contains("sign") && !blockName.contains("boat") &&
                        !blockName.contains("chest") && !blockName.contains("barrel") &&
                        !blockName.contains("bale") && !blockName.contains("purpur_pillar")) {
                        return true;
                    }
                }
            }
        }
        
        // Check block registry name for modded logs that might not extend PillarBlock
        // Many modded logs use "log" or "wood" in their name
        // Also check for "root" in name for modded root blocks
        String blockName = block.getTranslationKey().toLowerCase();
        if (blockName.contains("log") || blockName.contains("wood") || 
            blockName.contains("stem") || blockName.contains("hyphae") ||
            blockName.contains("root")) {
            // Additional safety check: make sure it's not something like "logbook" or "wooden_door"
            if (!blockName.contains("door") && !blockName.contains("trapdoor") && 
                !blockName.contains("fence") && !blockName.contains("plank") &&
                !blockName.contains("slab") && !blockName.contains("stair") &&
                !blockName.contains("button") && !blockName.contains("pressure_plate") &&
                !blockName.contains("sign") && !blockName.contains("boat") &&
                !blockName.contains("chest") && !blockName.contains("barrel") &&
                !blockName.contains("bale") && !blockName.contains("purpur_pillar")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a log block is part of a natural tree (not a building structure or manually placed).
     * @param currentBuilding The lumberyard building (to exclude from building checks)
     * @param logPos The position of the log
     * @param logState The block state of the log
     * @param logBlock The log block
     * @param world The server world
     * @param server The Minecraft server
     * @return true if the log is part of a natural tree
     */
    private static boolean isNaturalTree(Building currentBuilding, BlockPos logPos, BlockState logState, 
                                        Block logBlock, ServerWorld world, MinecraftServer server) {
        // First, check if this log is within any building's bounds (exclude it)
        SettlementManager manager = SettlementManager.getInstance(world);
        for (Settlement settlement : manager.getAllSettlements()) {
            for (Building building : settlement.getBuildings()) {
                // Skip the current lumberyard building itself
                if (building.getId().equals(currentBuilding.getId())) {
                    continue;
                }
                
                // Check if log is within this building's structure bounds
                if (isLogWithinBuilding(logPos, building, world, server)) {
                    return false; // This log is part of a building structure
                }
            }
        }
        
        // Check if this log is part of a natural tree by looking for:
        // 1. Multiple leaves nearby (MIN_LEAVES_COUNT or more)
        // 2. Tree trunk pattern (logs connected vertically going down to natural terrain)
        // 3. Logs connected in a tree-like structure (not just isolated logs)
        
        // Count leaves nearby - STRICT: Only obvious trees with clear leaf presence
        // Check both nearby (within LEAF_CHECK_RADIUS) and close (within 3 blocks for spruce trees)
        int leavesCount = 0;
        int closeLeavesCount = 0; // Leaves within 3 blocks - accounts for spruce trees with leaves further from trunk
        int leavesAboveCount = 0; // Leaves above the log - spruce trees have leaves at the top
        
        for (int x = -LEAF_CHECK_RADIUS; x <= LEAF_CHECK_RADIUS; x++) {
            for (int y = -LEAF_CHECK_RADIUS; y <= LEAF_CHECK_RADIUS; y++) {
                for (int z = -LEAF_CHECK_RADIUS; z <= LEAF_CHECK_RADIUS; z++) {
                    BlockPos checkPos = logPos.add(x, y, z);
                    
                    if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    BlockState checkState = world.getBlockState(checkPos);
                    Block checkBlock = checkState.getBlock();
                    
                    // Check if this is a leaves block
                    if (checkBlock instanceof LeavesBlock) {
                        leavesCount++;
                        
                        // Count leaves that are close (within 3 blocks) - accounts for spruce trees
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance <= 3.0) {
                            closeLeavesCount++;
                        }
                        
                        // Count leaves above the log (spruce trees have leaves at the top)
                        if (y > 0) {
                            leavesAboveCount++;
                        }
                    }
                }
            }
        }
        
        // Check for tree type to adjust leaf requirements
        // Acacia trees have sparse leaves, mangrove trees have different leaf patterns
        boolean isAcaciaTree = logBlock.getName().getString().toLowerCase().contains("acacia");
        boolean isMangroveTree = logBlock.getName().getString().toLowerCase().contains("mangrove");
        boolean isJungleTree = logBlock.getName().getString().toLowerCase().contains("jungle");
        boolean isSpruceTree = logBlock.getName().getString().toLowerCase().contains("spruce");
        
        // STRICT REQUIREMENT: Must have leaves present - no exceptions
        // For spruce trees: leaves might be further from trunk, but should have leaves above
        // For acacia trees: allow slightly fewer leaves (they have sparse canopies)
        // For mangrove trees: allow slightly fewer leaves (different structure)
        int requiredCloseLeaves = (isAcaciaTree || isMangroveTree) ? 2 : 3;
        int requiredLeavesAbove = (isAcaciaTree || isMangroveTree) ? 3 : 4;
        int requiredTotalLeaves = (isAcaciaTree || isMangroveTree) ? 8 : 10;
        int requiredTotalLeavesWithClose = (isAcaciaTree || isMangroveTree) ? 5 : 6;
        int absoluteMinLeaves = (isAcaciaTree || isMangroveTree) ? 2 : 3;
        
        boolean hasCloseLeaves = closeLeavesCount >= requiredCloseLeaves;
        boolean hasLeavesAbove = leavesAboveCount >= requiredLeavesAbove;
        boolean hasEnoughTotalLeaves = leavesCount >= requiredTotalLeaves;
        
        // Must have either close leaves OR enough total leaves with leaves above
        // This ensures we only harvest trees with clear leaf presence
        if (!hasCloseLeaves && !(hasEnoughTotalLeaves && hasLeavesAbove)) {
            SettlementsMod.LOGGER.debug("Log at {} is not a natural tree - insufficient leaves (close: {}, total: {}, above: {})", 
                logPos, closeLeavesCount, leavesCount, leavesAboveCount);
            return false;
        }
        
        // Additional check: If we have close leaves, make sure we have enough total leaves too
        // This prevents harvesting logs that just happen to have a few leaves nearby
        if (hasCloseLeaves && leavesCount < requiredTotalLeavesWithClose) {
            SettlementsMod.LOGGER.debug("Log at {} is not a natural tree - has close leaves but insufficient total leaves (close: {}, total: {})", 
                logPos, closeLeavesCount, leavesCount);
            return false;
        }
        
        // Final check: Must have at least some leaves - absolute minimum
        if (leavesCount < absoluteMinLeaves) {
            SettlementsMod.LOGGER.debug("Log at {} is not a natural tree - absolute minimum leaves not met (total: {})", 
                logPos, leavesCount);
            return false;
        }
        
        // Check for tree trunk pattern - logs connected vertically going down to natural terrain
        // Natural trees have a vertical trunk going down to the ground
        int connectedLogsDown = 0;
        BlockPos groundCheckPos = logPos;
        boolean foundNaturalTerrain = false;
        
        for (int y = 0; y <= TREE_TRUNK_CHECK_DOWN; y++) {
            BlockPos checkPos = logPos.down(y);
            
            if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                break;
            }
            
            BlockState checkState = world.getBlockState(checkPos);
            Block checkBlock = checkState.getBlock();
            
            if (isLogBlock(checkBlock, checkState)) {
                connectedLogsDown++;
                groundCheckPos = checkPos;
            } else {
                // If we hit a non-log block, check if it's natural terrain
                // Natural trees have logs going down to grass, dirt, podzol, etc.
                if (y > 0) {
                    // We found the base of the trunk - check if it's on natural terrain
                    BlockPos terrainPos = groundCheckPos.down();
                    if (world.getChunkManager().isChunkLoaded(terrainPos.getX() >> 4, terrainPos.getZ() >> 4)) {
                        BlockState terrainState = world.getBlockState(terrainPos);
                        Block terrainBlock = terrainState.getBlock();
                        
                        // Check if it's natural terrain (grass, dirt, podzol, coarse dirt, mycelium, etc.)
                        // Use Blocks constants to check for natural terrain types
                        // Mangrove trees can grow on mud and mangrove roots
                        boolean isNaturalTerrain = terrainBlock == Blocks.GRASS_BLOCK ||
                                                  terrainBlock == Blocks.DIRT ||
                                                  terrainBlock == Blocks.PODZOL ||
                                                  terrainBlock == Blocks.COARSE_DIRT ||
                                                  terrainBlock == Blocks.MYCELIUM ||
                                                  terrainBlock == Blocks.SAND ||
                                                  terrainBlock == Blocks.GRAVEL ||
                                                  terrainBlock == Blocks.STONE ||
                                                  terrainBlock == Blocks.COBBLESTONE ||
                                                  terrainBlock == Blocks.MOSSY_COBBLESTONE ||
                                                  terrainBlock == Blocks.MUD ||
                                                  terrainBlock == Blocks.MUDDY_MANGROVE_ROOTS;
                        
                        if (isNaturalTerrain) {
                            foundNaturalTerrain = true;
                        } else {
                            // Log is on non-natural terrain (like placed blocks) - not a natural tree
                            SettlementsMod.LOGGER.debug("Log at {} is on non-natural terrain {} - not a natural tree", 
                                logPos, terrainBlock);
                            return false;
                        }
                    }
                } else {
                    // y == 0: The log itself is directly on a non-log block - check if it's natural terrain
                    BlockState terrainState = world.getBlockState(checkPos);
                    Block terrainBlock = terrainState.getBlock();
                    
                    boolean isNaturalTerrain = terrainBlock == Blocks.GRASS_BLOCK ||
                                              terrainBlock == Blocks.DIRT ||
                                              terrainBlock == Blocks.PODZOL ||
                                              terrainBlock == Blocks.COARSE_DIRT ||
                                              terrainBlock == Blocks.MYCELIUM ||
                                              terrainBlock == Blocks.SAND ||
                                              terrainBlock == Blocks.GRAVEL ||
                                              terrainBlock == Blocks.STONE ||
                                              terrainBlock == Blocks.COBBLESTONE ||
                                              terrainBlock == Blocks.MOSSY_COBBLESTONE ||
                                              terrainBlock == Blocks.MUD ||
                                              terrainBlock == Blocks.MUDDY_MANGROVE_ROOTS;
                    
                    if (isNaturalTerrain) {
                        foundNaturalTerrain = true;
                    } else {
                        // Log is directly on non-natural terrain - not a natural tree
                        SettlementsMod.LOGGER.debug("Log at {} is directly on non-natural terrain {} - not a natural tree", 
                            logPos, terrainBlock);
                        return false;
                    }
                }
                break;
            }
        }
        
        // If we didn't find natural terrain below, it might be a manually placed log
        // Natural trees should have their trunk going down to natural terrain
        if (connectedLogsDown > 0 && !foundNaturalTerrain) {
            SettlementsMod.LOGGER.debug("Log at {} has connected logs below but not on natural terrain - likely manually placed", 
                logPos);
            return false;
        }
        
        // Also check if log is directly on natural terrain (no logs below)
        if (connectedLogsDown == 0 && !foundNaturalTerrain) {
            // This shouldn't happen if the above logic worked, but double-check
            BlockPos terrainPos = logPos.down();
            if (world.getChunkManager().isChunkLoaded(terrainPos.getX() >> 4, terrainPos.getZ() >> 4)) {
                BlockState terrainState = world.getBlockState(terrainPos);
                Block terrainBlock = terrainState.getBlock();
                
                boolean isNaturalTerrain = terrainBlock == Blocks.GRASS_BLOCK ||
                                          terrainBlock == Blocks.DIRT ||
                                          terrainBlock == Blocks.PODZOL ||
                                          terrainBlock == Blocks.COARSE_DIRT ||
                                          terrainBlock == Blocks.MYCELIUM ||
                                          terrainBlock == Blocks.SAND ||
                                          terrainBlock == Blocks.GRAVEL ||
                                          terrainBlock == Blocks.STONE ||
                                          terrainBlock == Blocks.COBBLESTONE ||
                                          terrainBlock == Blocks.MOSSY_COBBLESTONE ||
                                          terrainBlock == Blocks.MUD ||
                                          terrainBlock == Blocks.MUDDY_MANGROVE_ROOTS;
                
                if (isNaturalTerrain) {
                    foundNaturalTerrain = true;
                }
            }
        }
        
        // Check for connected logs above (tree trunk pattern)
        // Natural trees have a mostly vertical trunk
        int connectedLogsAbove = 0;
        for (int y = 1; y <= TREE_HEIGHT_CHECK; y++) {
            BlockPos abovePos = logPos.up(y);
            
            if (!world.getChunkManager().isChunkLoaded(abovePos.getX() >> 4, abovePos.getZ() >> 4)) {
                break;
            }
            
            BlockState aboveState = world.getBlockState(abovePos);
            Block aboveBlock = aboveState.getBlock();
            
            if (isLogBlock(aboveBlock, aboveState)) {
                connectedLogsAbove++;
            } else {
                break; // Stop if we hit a non-log block
            }
        }
        
        // Check for horizontal logs (manually placed logs are often placed horizontally)
        // Natural trees have mostly vertical trunks with minimal horizontal logs
        // For 2x2 jungle trees, also check diagonal connections
        int horizontalLogs = 0;
        BlockPos[] horizontalDirs = {
            logPos.north(), logPos.south(), logPos.east(), logPos.west(),
            // Also check diagonals for 2x2 jungle trees
            logPos.north().east(), logPos.north().west(),
            logPos.south().east(), logPos.south().west()
        };
        for (BlockPos hPos : horizontalDirs) {
            if (world.getChunkManager().isChunkLoaded(hPos.getX() >> 4, hPos.getZ() >> 4)) {
                BlockState hState = world.getBlockState(hPos);
                Block hBlock = hState.getBlock();
                if (isLogBlock(hBlock, hState)) {
                    horizontalLogs++;
                }
            }
        }
        
        // Total connected logs (above + current + below)
        int totalConnectedLogs = connectedLogsAbove + 1 + connectedLogsDown;
        
        // Natural trees should have:
        // 1. Sufficient leaves nearby (MIN_LEAVES_COUNT or more)
        // 2. Multiple connected logs in trunk pattern (MIN_CONNECTED_LOGS or more)
        // 3. Mostly vertical trunk (more vertical logs than horizontal)
        // 4. Trunk going down to natural terrain (foundNaturalTerrain)
        boolean hasEnoughLeaves = leavesCount >= MIN_LEAVES_COUNT;
        boolean hasTrunkPattern = totalConnectedLogs >= MIN_CONNECTED_LOGS;
        boolean isMostlyVertical = (connectedLogsAbove + connectedLogsDown) >= horizontalLogs;
        
        // Check for tree type to determine if horizontal logs are acceptable
        // Acacia trees naturally have many horizontal branches, so be more lenient
        // Jungle 2x2 trees also have horizontal connections
        // (isAcaciaTree and isJungleTree already declared above)
        
        // For acacia, jungle, and spruce trees (2x2 spruce), allow more horizontal logs if we have enough leaves
        // For other trees, be stricter about horizontal logs
        int maxHorizontalLogs = (isAcaciaTree || isJungleTree || isSpruceTree) ? 6 : 2;
        boolean hasTooManyHorizontalLogs = horizontalLogs > maxHorizontalLogs && 
                                          horizontalLogs >= (connectedLogsAbove + connectedLogsDown);
        
        // CRITICAL: If there are horizontal logs (especially 2+), require STRONG leaf evidence
        // Manually placed logs are often placed horizontally next to each other
        // Natural trees with horizontal branches still have leaves very close to those branches
        // For 2x2 jungle trees: they have diagonal connections, so horizontalLogs might be 2-3
        // For 2x2 jungle trees: leaves are often higher up, not at the base
        if (horizontalLogs >= 2) {
            // Require leaves to be very close (within 2 blocks) if there are horizontal connections
            // This prevents harvesting manually placed logs that happen to be near leaves from other trees
            int veryCloseLeaves = 0;
            int leavesAboveHorizontal = 0; // Leaves above horizontal connections (for 2x2 jungle trees)
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos checkPos = logPos.add(x, y, z);
                        if (world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                            BlockState checkState = world.getBlockState(checkPos);
                            Block checkBlock = checkState.getBlock();
                            if (checkBlock instanceof LeavesBlock) {
                                veryCloseLeaves++;
                                // For 2x2 jungle and spruce trees, also count leaves above the horizontal connections
                                if ((isJungleTree || isSpruceTree) && y > 0) {
                                    leavesAboveHorizontal++;
                                }
                            }
                        }
                    }
                }
            }
            
            // For 2x2 jungle and spruce trees: be more lenient - they have leaves higher up, not at the base
            // 2x2 trees have 2-3 horizontal/diagonal connections (the other logs in the 2x2 pattern)
            // They also have leaves much higher up (the existing leavesAboveCount already checks this)
            boolean isLikely2x2Tree = (isJungleTree || isSpruceTree) && horizontalLogs >= 2 && horizontalLogs <= 3;
            
            if (isLikely2x2Tree) {
                // For 2x2 trees: require leaves above (use leavesAboveCount which checks up to 8 blocks)
                // OR very close leaves at the base
                // This allows 2x2 trees which have leaves high up, not at the base
                // leavesAboveCount is already calculated above and checks a larger radius (LEAF_CHECK_RADIUS = 8)
                if (leavesAboveCount >= 3 || veryCloseLeaves >= 3 || leavesAboveHorizontal >= 2) {
                    String treeType = isJungleTree ? "jungle" : "spruce";
                    SettlementsMod.LOGGER.debug("Log at {} is likely a 2x2 {} tree ({} horizontal logs, {} leaves above total, {} very close, {} above horizontal) - allowing", 
                        logPos, treeType, horizontalLogs, leavesAboveCount, veryCloseLeaves, leavesAboveHorizontal);
                } else {
                    // Not enough leaves for a 2x2 tree
                    SettlementsMod.LOGGER.debug("Log at {} has {} horizontal logs but insufficient leaves ({} above total, {} very close, {} above horizontal) - likely manually placed", 
                        logPos, horizontalLogs, leavesAboveCount, veryCloseLeaves, leavesAboveHorizontal);
                    return false;
                }
            } else {
                // For other trees, require very close leaves
                int requiredVeryCloseLeaves = (isAcaciaTree || isJungleTree || isSpruceTree) ? 3 : 4;
                if (veryCloseLeaves < requiredVeryCloseLeaves) {
                    SettlementsMod.LOGGER.debug("Log at {} has {} horizontal logs but only {} very close leaves (required: {}) - likely manually placed", 
                        logPos, horizontalLogs, veryCloseLeaves, requiredVeryCloseLeaves);
                    return false;
                }
            }
        }
        
        // If there are too many horizontal logs relative to vertical, it's likely manually placed
        // Exception: acacia, jungle, and spruce (2x2) trees naturally have more horizontal branches
        if (hasTooManyHorizontalLogs && !(isAcaciaTree || isJungleTree || isSpruceTree)) {
            SettlementsMod.LOGGER.debug("Log at {} has too many horizontal connections ({} horizontal vs {} vertical) - likely manually placed", 
                logPos, horizontalLogs, connectedLogsAbove + connectedLogsDown);
            return false;
        }
        
        // STRICT REQUIREMENT: Must have leaves nearby - no exceptions
        // If there are no leaves, it's definitely not a natural tree (could be manually placed or dead tree)
        if (leavesCount < MIN_LEAVES_COUNT) {
            SettlementsMod.LOGGER.debug("Log at {} is not a natural tree - insufficient leaves ({} < {})", 
                logPos, leavesCount, MIN_LEAVES_COUNT);
            return false;
        }
        
        // Consider it a natural tree if all conditions are met
        boolean isNatural = hasEnoughLeaves && hasTrunkPattern && isMostlyVertical && foundNaturalTerrain;
        
        if (!isNatural) {
            SettlementsMod.LOGGER.debug("Log at {} is not a natural tree (leaves: {}/{}, connected logs: {}/{}, vertical: {}, terrain: {})", 
                logPos, leavesCount, MIN_LEAVES_COUNT, totalConnectedLogs, MIN_CONNECTED_LOGS, isMostlyVertical, foundNaturalTerrain);
        } else {
            SettlementsMod.LOGGER.debug("Log at {} is a natural tree (leaves: {}, connected logs: {}, vertical: {}, terrain: {})", 
                logPos, leavesCount, totalConnectedLogs, isMostlyVertical, foundNaturalTerrain);
        }
        
        return isNatural;
    }
    
    /**
     * Checks if a log position is within a building's structure bounds.
     * @param logPos The position of the log
     * @param building The building to check
     * @param world The server world
     * @param server The Minecraft server
     * @return true if the log is within the building's bounds
     */
    private static boolean isLogWithinBuilding(BlockPos logPos, Building building, ServerWorld world, 
                                              MinecraftServer server) {
        // Load the building's structure data
        StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
        if (structureData == null) {
            return false;
        }
        
        BlockPos buildingPos = building.getPosition();
        Vec3i size = structureData.getDimensions();
        int rotation = building.getRotation();
        
        // Calculate building bounds (accounting for rotation)
        // For simplicity, we'll use a bounding box approach
        int minX = buildingPos.getX();
        int maxX = buildingPos.getX() + size.getX();
        int minY = buildingPos.getY();
        int maxY = buildingPos.getY() + size.getY();
        int minZ = buildingPos.getZ();
        int maxZ = buildingPos.getZ() + size.getZ();
        
        // Handle rotation (simplified - assumes 0, 90, 180, 270 degree rotations)
        if (rotation == 90 || rotation == 270) {
            // Swap X and Z dimensions
            int tempMin = minX;
            int tempMax = maxX;
            minX = minZ;
            maxX = maxZ;
            minZ = tempMin;
            maxZ = tempMax;
        }
        
        // Check if log is within building bounds
        boolean withinBounds = logPos.getX() >= minX && logPos.getX() < maxX &&
                              logPos.getY() >= minY && logPos.getY() < maxY &&
                              logPos.getZ() >= minZ && logPos.getZ() < maxZ;
        
        if (withinBounds) {
            SettlementsMod.LOGGER.debug("Log at {} is within building {} bounds ({}, {}, {}) to ({}, {}, {})", 
                logPos, building.getId(), minX, minY, minZ, maxX, maxY, maxZ);
        }
        
        return withinBounds;
    }
    
    /**
     * Harvests an entire tree starting from the given log position.
     * Finds all connected logs and harvests them all, including branches.
     * @param startPos The starting position of a log in the tree
     * @param startState The block state of the starting log
     * @param startBlock The block type of the starting log
     * @param building The lumberyard building (for building exclusion checks)
     * @param world The server world
     * @param server The Minecraft server
     * @return List of all dropped items from the tree
     */
    private static List<ItemStack> harvestTree(BlockPos startPos, BlockState startState, Block startBlock,
                                              Building building, ServerWorld world, MinecraftServer server) {
        List<ItemStack> allDrops = new ArrayList<>();
        java.util.Set<BlockPos> visitedLogs = new java.util.HashSet<>(); // Track visited positions to avoid infinite loops
        java.util.Queue<BlockPos> logsToHarvest = new java.util.LinkedList<>();
        
        // Start with the initial log
        logsToHarvest.add(startPos);
        
        // Find all connected logs in the tree using breadth-first search
        // This ensures we find all logs including branches, not just the main trunk
        while (!logsToHarvest.isEmpty()) {
            BlockPos currentPos = logsToHarvest.poll();
            
            // Skip if already processed
            if (visitedLogs.contains(currentPos)) {
                continue;
            }
            
            // Check if chunk is loaded
            if (!world.getChunkManager().isChunkLoaded(currentPos.getX() >> 4, currentPos.getZ() >> 4)) {
                continue;
            }
            
            BlockState currentState = world.getBlockState(currentPos);
            Block currentBlock = currentState.getBlock();
            
            // Check if this is still a log block (might have been harvested already)
            if (!isLogBlock(currentBlock, currentState)) {
                continue;
            }
            
            // Check if this log is part of a building (don't harvest building logs)
            if (isLogWithinAnyBuilding(currentPos, building, world, server)) {
                continue;
            }
            
            // Check distance from start - prevent harvesting logs that are too far (unrelated trees)
            double distanceSq = startPos.getSquaredDistance(currentPos);
            if (distanceSq > MAX_TREE_SIZE * MAX_TREE_SIZE) {
                SettlementsMod.LOGGER.debug("Skipping log at {} - too far from tree start ({} blocks)", 
                    currentPos, Math.sqrt(distanceSq));
                continue;
            }
            
            // Mark as visited before harvesting to avoid processing twice
            visitedLogs.add(currentPos);
            
            // Harvest this log
            List<ItemStack> drops = harvestLog(currentPos, currentState, currentBlock, world);
            allDrops.addAll(drops);
            
            // Find connected logs by checking ALL 26 neighbors in a 3x3x3 cube
            // This ensures we find all touching logs including diagonals at different Y levels
            // This is critical for acacia trees (many horizontal branches), jungle 2x2 trees, and mangrove trees
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        // Skip the center block (currentPos itself)
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        
                        BlockPos neighborPos = currentPos.add(dx, dy, dz);
                        
                        // Skip if already processed
                        if (visitedLogs.contains(neighborPos)) {
                            continue;
                        }
                        
                        // Check if chunk is loaded
                        if (!world.getChunkManager().isChunkLoaded(neighborPos.getX() >> 4, neighborPos.getZ() >> 4)) {
                            continue;
                        }
                        
                        BlockState neighborState = world.getBlockState(neighborPos);
                        Block neighborBlock = neighborState.getBlock();
                        
                        // Check if neighbor is a log
                        if (isLogBlock(neighborBlock, neighborState)) {
                            // Check distance from start to prevent harvesting unrelated logs
                            double neighborDistanceSq = startPos.getSquaredDistance(neighborPos);
                            if (neighborDistanceSq <= MAX_TREE_SIZE * MAX_TREE_SIZE) {
                                logsToHarvest.add(neighborPos);
                            }
                        }
                        
                        // Also check for leaves above - if there are leaves above, there might be more logs above them
                        // This helps with acacia trees and jungle trees that have leaves between branches
                        if (dy > 0 && neighborBlock instanceof LeavesBlock) {
                            // Check a few blocks above the leaves for more logs
                            for (int checkY = 1; checkY <= 3; checkY++) {
                                BlockPos aboveLeafPos = neighborPos.up(checkY);
                                
                                // Skip if already processed
                                if (visitedLogs.contains(aboveLeafPos)) {
                                    continue;
                                }
                                
                                // Check if chunk is loaded
                                if (!world.getChunkManager().isChunkLoaded(aboveLeafPos.getX() >> 4, aboveLeafPos.getZ() >> 4)) {
                                    break;
                                }
                                
                                // Check distance from start
                                double aboveDistanceSq = startPos.getSquaredDistance(aboveLeafPos);
                                if (aboveDistanceSq > MAX_TREE_SIZE * MAX_TREE_SIZE) {
                                    break;
                                }
                                
                                BlockState aboveState = world.getBlockState(aboveLeafPos);
                                Block aboveBlock = aboveState.getBlock();
                                
                                // If we find a log above the leaves, add it to the queue
                                if (isLogBlock(aboveBlock, aboveState)) {
                                    logsToHarvest.add(aboveLeafPos);
                                    break; // Found a log, stop checking further up
                                }
                                
                                // If we hit a non-leaf, non-log block, stop checking
                                if (!(aboveBlock instanceof LeavesBlock)) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        SettlementsMod.LOGGER.info("Harvested entire tree starting at {}: {} logs, {} total items", 
            startPos, visitedLogs.size(), allDrops.size());
        
        return allDrops;
    }
    
    /**
     * Counts the number of logs in a tree starting from the given position.
     * Used for logging purposes.
     */
    private static int countLogsInTree(BlockPos startPos, Block startBlock, ServerWorld world) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.Queue<BlockPos> toVisit = new java.util.LinkedList<>();
        
        toVisit.add(startPos);
        visited.add(startPos);
        
        while (!toVisit.isEmpty()) {
            BlockPos currentPos = toVisit.poll();
            
            if (!world.getChunkManager().isChunkLoaded(currentPos.getX() >> 4, currentPos.getZ() >> 4)) {
                continue;
            }
            
            BlockState currentState = world.getBlockState(currentPos);
            Block currentBlock = currentState.getBlock();
            
            if (!isLogBlock(currentBlock, currentState)) {
                continue;
            }
            
            // Check neighbors
            BlockPos[] directions = {
                currentPos.up(), currentPos.down(),
                currentPos.north(), currentPos.south(),
                currentPos.east(), currentPos.west()
            };
            
            for (BlockPos neighborPos : directions) {
                if (visited.contains(neighborPos)) {
                    continue;
                }
                
                if (!world.getChunkManager().isChunkLoaded(neighborPos.getX() >> 4, neighborPos.getZ() >> 4)) {
                    continue;
                }
                
                BlockState neighborState = world.getBlockState(neighborPos);
                Block neighborBlock = neighborState.getBlock();
                
                if (isLogBlock(neighborBlock, neighborState)) {
                    if (startPos.getSquaredDistance(neighborPos) <= MAX_TREE_SIZE * MAX_TREE_SIZE) {
                        toVisit.add(neighborPos);
                        visited.add(neighborPos);
                    }
                }
            }
        }
        
        return visited.size();
    }
    
    /**
     * Checks if a log is within any building's bounds (excluding the lumberyard).
     */
    private static boolean isLogWithinAnyBuilding(BlockPos logPos, Building currentBuilding, 
                                                 ServerWorld world, MinecraftServer server) {
        SettlementManager manager = SettlementManager.getInstance(world);
        for (Settlement settlement : manager.getAllSettlements()) {
            for (Building building : settlement.getBuildings()) {
                // Skip the current lumberyard building itself
                if (building.getId().equals(currentBuilding.getId())) {
                    continue;
                }
                
                if (isLogWithinBuilding(logPos, building, world, server)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Harvests a log at the given position.
     * Breaks the log and collects the drops.
     * @param logPos The position of the log
     * @param logState The block state of the log
     * @param logBlock The log block
     * @param world The server world
     * @return List of dropped items
     */
    private static List<ItemStack> harvestLog(BlockPos logPos, BlockState logState, Block logBlock, 
                                              ServerWorld world) {
        // Get what would drop from breaking the log
        List<ItemStack> droppedStacks = Block.getDroppedStacks(logState, world, logPos, null);
        
        // Break the log block (false = don't drop items automatically)
        // Items will be returned and go through the normal accumulation/deposit system
        world.breakBlock(logPos, false);
        
        SettlementsMod.LOGGER.debug("Harvested log {} at {} (drops: {})", 
            logBlock, logPos, droppedStacks.size());
        
        return droppedStacks;
    }
}

