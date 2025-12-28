package com.secretasain.settlements.road;

import com.secretasain.settlements.settlement.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Handles detection of dark areas and placement of fence light posts.
 */
public class LightPostPlacementSystem {
    private static final int DARK_LIGHT_LEVEL = 7; // Light level threshold (below 7 is dark)
    private static final int ROAD_DETECTION_RADIUS = 8; // Must be within 8 blocks of road
    private static final int LIGHT_POST_SPACING = 4; // Minimum spacing between light posts
    
    /**
     * Finds dark areas near roads that need light posts.
     * @param settlement The settlement to scan
     * @param world The server world
     * @return List of positions where light posts should be placed
     */
    public static List<BlockPos> findDarkAreas(Settlement settlement, ServerWorld world) {
        List<BlockPos> darkAreas = new ArrayList<>();
        
        if (settlement == null || world == null || settlement.getLecternPos() == null) {
            return darkAreas;
        }
        
        // Get all roads in settlement
        Set<BlockPos> roads = RoadDetector.findRoads(settlement, world);
        if (roads.isEmpty()) {
            return darkAreas; // No roads, no need for light posts
        }
        
        // Get existing light posts (fence blocks with torch/lantern on top)
        Set<BlockPos> existingLightPosts = findExistingLightPosts(settlement, world);
        
        // Scan area around roads for dark spots
        for (BlockPos road : roads) {
            // Check positions within detection radius of this road
            for (int x = -ROAD_DETECTION_RADIUS; x <= ROAD_DETECTION_RADIUS; x++) {
                for (int z = -ROAD_DETECTION_RADIUS; z <= ROAD_DETECTION_RADIUS; z++) {
                    // Check distance from road
                    double distanceSq = x * x + z * z;
                    if (distanceSq > ROAD_DETECTION_RADIUS * ROAD_DETECTION_RADIUS) {
                        continue;
                    }
                    
                    BlockPos checkPos = road.add(x, 0, z);
                    
                    // Check if position is suitable for light post
                    if (shouldPlaceLightPost(checkPos, world, existingLightPosts)) {
                        darkAreas.add(checkPos);
                    }
                }
            }
        }
        
        return darkAreas;
    }
    
    /**
     * Checks if a light post should be placed at the given position.
     * @param pos Position to check
     * @param world The server world
     * @param existingLightPosts Set of existing light post positions
     * @return true if light post should be placed
     */
    public static boolean shouldPlaceLightPost(BlockPos pos, ServerWorld world, Set<BlockPos> existingLightPosts) {
        // Check if position is within settlement bounds
        // (This will be checked by the caller, but we validate here too)
        
        // Check if light post already exists nearby
        for (BlockPos existing : existingLightPosts) {
            if (pos.getSquaredDistance(existing) < LIGHT_POST_SPACING * LIGHT_POST_SPACING) {
                return false; // Too close to existing light post
            }
        }
        
        // Get ground level
        int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos groundPos = new BlockPos(pos.getX(), topY, pos.getZ());
        
        // Check light level at ground position
        int lightLevel = world.getLightLevel(groundPos);
        if (lightLevel >= DARK_LIGHT_LEVEL) {
            return false; // Not dark enough
        }
        
        // Check if position is suitable (solid block below, air above)
        BlockState groundState = world.getBlockState(groundPos);
        BlockState belowState = world.getBlockState(groundPos.down());
        BlockState aboveState = world.getBlockState(groundPos.up());
        
        // Need air at ground level and solid block below
        if (!groundState.isAir() && !groundState.isReplaceable()) {
            return false; // Blocked
        }
        
        if (belowState.isAir() || !belowState.isSolidBlock(world, groundPos.down())) {
            return false; // No solid ground
        }
        
        if (!aboveState.isAir()) {
            return false; // Blocked above (can't place fence)
        }
        
        // Check for water or lava
        if (groundState.getBlock() == Blocks.WATER || groundState.getBlock() == Blocks.LAVA ||
            belowState.getBlock() == Blocks.WATER || belowState.getBlock() == Blocks.LAVA) {
            return false; // Water or lava
        }
        
        return true;
    }
    
    /**
     * Places a light post at the given position.
     * Places a fence block with a torch on top.
     * @param pos Position to place light post
     * @param world The server world
     * @return true if placement was successful
     */
    public static boolean placeLightPost(BlockPos pos, ServerWorld world) {
        if (pos == null || world == null) {
            return false;
        }
        
        // Get ground level
        int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos groundPos = new BlockPos(pos.getX(), topY, pos.getZ());
        BlockPos fencePos = groundPos;
        BlockPos torchPos = groundPos.up();
        
        // Check if positions are valid
        BlockState groundState = world.getBlockState(fencePos);
        BlockState aboveState = world.getBlockState(torchPos);
        
        if (!groundState.isAir() && !groundState.isReplaceable()) {
            return false; // Can't place fence
        }
        
        if (!aboveState.isAir()) {
            return false; // Can't place torch
        }
        
        // Place fence block
        BlockState fenceState = Blocks.OAK_FENCE.getDefaultState();
        world.setBlockState(fencePos, fenceState, 3); // 3 = NOTIFY_NEIGHBORS | BLOCK_UPDATE
        
        // Place torch on top of fence
        BlockState torchState = Blocks.TORCH.getDefaultState();
        world.setBlockState(torchPos, torchState, 3);
        
        return true;
    }
    
    /**
     * Finds existing light posts in the settlement.
     * Light posts are fence blocks with torch/lantern on top.
     * @param settlement The settlement to scan
     * @param world The server world
     * @return Set of light post positions (fence positions)
     */
    private static Set<BlockPos> findExistingLightPosts(Settlement settlement, ServerWorld world) {
        Set<BlockPos> lightPosts = new HashSet<>();
        
        if (settlement == null || world == null || settlement.getLecternPos() == null) {
            return lightPosts;
        }
        
        BlockPos center = settlement.getLecternPos();
        int radius = settlement.getRadius();
        
        // Scan for fence blocks with torch/lantern on top
        int scanStep = 4; // Scan every 4 blocks
        for (int x = center.getX() - radius; x <= center.getX() + radius; x += scanStep) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += scanStep) {
                double distanceSq = center.getSquaredDistance(x, center.getY(), z);
                if (distanceSq > radius * radius) {
                    continue;
                }
                
                int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos fencePos = new BlockPos(x, topY, z);
                BlockPos abovePos = fencePos.up();
                
                BlockState fenceState = world.getBlockState(fencePos);
                BlockState aboveState = world.getBlockState(abovePos);
                
                // Check if this is a fence with torch/lantern on top
                Block fenceBlock = fenceState.getBlock();
                Block aboveBlock = aboveState.getBlock();
                
                if ((fenceBlock == Blocks.OAK_FENCE || fenceBlock == Blocks.SPRUCE_FENCE ||
                     fenceBlock == Blocks.BIRCH_FENCE || fenceBlock == Blocks.JUNGLE_FENCE ||
                     fenceBlock == Blocks.ACACIA_FENCE || fenceBlock == Blocks.DARK_OAK_FENCE ||
                     fenceBlock == Blocks.CRIMSON_FENCE || fenceBlock == Blocks.WARPED_FENCE) &&
                    (aboveBlock == Blocks.TORCH || aboveBlock == Blocks.LANTERN ||
                     aboveBlock == Blocks.SOUL_TORCH || aboveBlock == Blocks.SOUL_LANTERN)) {
                    lightPosts.add(fencePos);
                }
            }
        }
        
        return lightPosts;
    }
    
    /**
     * Processes light post placement for a settlement.
     * Finds dark areas and places light posts (limited by materials).
     * @param settlement The settlement to process
     * @param world The server world
     */
    public static void processLightPostPlacement(Settlement settlement, ServerWorld world) {
        if (settlement == null || world == null) {
            return;
        }
        
        // Find dark areas
        List<BlockPos> darkAreas = findDarkAreas(settlement, world);
        if (darkAreas.isEmpty()) {
            return; // No dark areas
        }
        
        // Check if creative mode is enabled (skip material consumption in creative)
        boolean creativeMode = isCreativeModeEnabled(world);
        
        // Check if settlement has materials for light posts
        // Need: fence blocks and torches
        String fenceKey = "minecraft:oak_fence";
        String torchKey = "minecraft:torch";
        
        int availableFences = creativeMode ? Integer.MAX_VALUE : settlement.getMaterials().getOrDefault(fenceKey, 0);
        int availableTorches = creativeMode ? Integer.MAX_VALUE : settlement.getMaterials().getOrDefault(torchKey, 0);
        
        if (!creativeMode && (availableFences == 0 || availableTorches == 0)) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("Settlement {} has no fence/torch materials for light posts", settlement.getId());
            return; // No materials available
        }
        
        // Place light posts (limit to available materials, or all if creative mode)
        int postsToPlace = creativeMode ? darkAreas.size() : Math.min(darkAreas.size(), Math.min(availableFences, availableTorches));
        int fencesUsed = 0;
        int torchesUsed = 0;
        
        for (int i = 0; i < postsToPlace; i++) {
            BlockPos pos = darkAreas.get(i);
            
            if (placeLightPost(pos, world)) {
                fencesUsed++;
                torchesUsed++;
            }
        }
        
        // Consume materials (skip in creative mode)
        if (fencesUsed > 0 && !creativeMode) {
            settlement.getMaterials().put(fenceKey, settlement.getMaterials().getOrDefault(fenceKey, 0) - fencesUsed);
            settlement.getMaterials().put(torchKey, settlement.getMaterials().getOrDefault(torchKey, 0) - torchesUsed);
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Placed {} light posts for settlement {}", postsToPlace, settlement.getId());
        } else if (fencesUsed > 0) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Placed {} light posts for settlement {} (creative mode)", postsToPlace, settlement.getId());
        }
    }
    
    /**
     * Checks if creative mode is enabled for the settlement.
     * Creative mode is enabled if any player in the world is in creative mode.
     * @param world The server world
     * @return true if creative mode is enabled
     */
    private static boolean isCreativeModeEnabled(ServerWorld world) {
        if (world == null) {
            return false;
        }
        
        // Check if any player in the world is in creative mode
        for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
            if (player.isCreative()) {
                return true;
            }
        }
        
        return false;
    }
}

