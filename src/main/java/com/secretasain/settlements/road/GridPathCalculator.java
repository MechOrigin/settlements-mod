package com.secretasain.settlements.road;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Calculates grid-based paths for road placement.
 * Creates straight-line paths (like a grid) from existing roads to building doors.
 */
public class GridPathCalculator {
    
    /**
     * Calculates a grid-based path from start to end position.
     * Creates a path that goes straight in one direction, then turns and goes straight in the other.
     * @param start Starting position (door)
     * @param end Ending position (existing road)
     * @param world The server world
     * @return List of BlockPos positions for path placement, or empty list if no path found
     */
    public static List<BlockPos> calculateGridPath(BlockPos start, BlockPos end, ServerWorld world) {
        List<BlockPos> path = new ArrayList<>();
        
        if (start == null || end == null || world == null) {
            return path;
        }
        
        // Get ground levels for start and end
        int startY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, start.getX(), start.getZ());
        int endY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, end.getX(), end.getZ());
        
        // Calculate grid path: go straight in X direction first, then Z direction (or vice versa)
        // Choose direction based on which is longer
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        
        // First, go in the direction with the larger difference
        if (Math.abs(dx) >= Math.abs(dz)) {
            // Go in X direction first
            int stepX = dx > 0 ? 1 : -1;
            for (int x = start.getX(); x != end.getX(); x += stepX) {
                // Check ground level at each position
                int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, start.getZ());
                BlockPos pos = new BlockPos(x, groundY, start.getZ());
                if (isValidPathPosition(pos, world)) {
                    path.add(pos);
                } else {
                    // If position is invalid, path might be blocked - return empty path
                    // But allow some tolerance for height differences
                    if (Math.abs(groundY - startY) > 3) {
                        return new ArrayList<>(); // Too much height difference
                    }
                }
            }
            // Then go in Z direction
            int stepZ = dz > 0 ? 1 : -1;
            for (int z = start.getZ(); z != end.getZ(); z += stepZ) {
                int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, end.getX(), z);
                BlockPos pos = new BlockPos(end.getX(), groundY, z);
                if (isValidPathPosition(pos, world)) {
                    path.add(pos);
                } else {
                    if (Math.abs(groundY - endY) > 3) {
                        return new ArrayList<>(); // Too much height difference
                    }
                }
            }
        } else {
            // Go in Z direction first
            int stepZ = dz > 0 ? 1 : -1;
            for (int z = start.getZ(); z != end.getZ(); z += stepZ) {
                int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, start.getX(), z);
                BlockPos pos = new BlockPos(start.getX(), groundY, z);
                if (isValidPathPosition(pos, world)) {
                    path.add(pos);
                } else {
                    if (Math.abs(groundY - startY) > 3) {
                        return new ArrayList<>(); // Too much height difference
                    }
                }
            }
            // Then go in X direction
            int stepX = dx > 0 ? 1 : -1;
            for (int x = start.getX(); x != end.getX(); x += stepX) {
                int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, end.getZ());
                BlockPos pos = new BlockPos(x, groundY, end.getZ());
                if (isValidPathPosition(pos, world)) {
                    path.add(pos);
                } else {
                    if (Math.abs(groundY - endY) > 3) {
                        return new ArrayList<>(); // Too much height difference
                    }
                }
            }
        }
        
        // Add the end position
        BlockPos endPos = new BlockPos(end.getX(), endY, end.getZ());
        if (isValidPathPosition(endPos, world) && !path.contains(endPos)) {
            path.add(endPos);
        }
        
        // Only return path if we have at least a few positions (avoid single-block paths)
        if (path.size() < 2) {
            return new ArrayList<>();
        }
        
        return path;
    }
    
    /**
     * Checks if a position is valid for path placement.
     * @param pos Position to check (X, Y, Z) - should be at ground level
     * @param world The server world
     * @return true if position is valid for path
     */
    private static boolean isValidPathPosition(BlockPos pos, ServerWorld world) {
        // Check if position is loaded
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        
        BlockState state = world.getBlockState(pos);
        BlockState belowState = world.getBlockState(pos.down());
        
        // Check for water or lava first
        if (state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.LAVA ||
            belowState.getBlock() == Blocks.WATER || belowState.getBlock() == Blocks.LAVA) {
            return false; // Water or lava
        }
        
        // Block below must be solid
        if (belowState.isAir() || !belowState.isSolidBlock(world, pos.down())) {
            return false; // No solid ground
        }
        
        // Position must be grass, dirt, or air (can be converted to path with shovel)
        // Allow existing path blocks too (so we can connect to them)
        if (state.getBlock() == Blocks.DIRT_PATH) {
            return true; // Already a path, valid for connection
        }
        
        if (state.getBlock() == Blocks.GRASS_BLOCK || 
            state.getBlock() == Blocks.DIRT ||
            state.getBlock() == Blocks.COARSE_DIRT ||
            state.getBlock() == Blocks.PODZOL ||
            state.isAir()) {
            return true; // Can be converted to path
        }
        
        // Don't allow if blocked by solid non-replaceable blocks (stairs, doors, etc.)
        if (!state.isAir() && !state.isReplaceable()) {
            return false; // Blocked by solid block
        }
        
        return false;
    }
}

