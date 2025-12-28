package com.secretasain.settlements.road;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Calculates path placement routes using A* pathfinding algorithm.
 * Matches vanilla village path placement quality.
 */
public class PathPlacementCalculator {
    
    /**
     * Calculates a path from start to end position.
     * Uses A* pathfinding to find the shortest valid route.
     * @param start Starting position
     * @param end Ending position
     * @param world The server world
     * @return List of BlockPos positions for path placement, or empty list if no path found
     */
    public static List<BlockPos> calculatePath(BlockPos start, BlockPos end, ServerWorld world) {
        List<BlockPos> path = new ArrayList<>();
        
        if (start == null || end == null || world == null) {
            return path;
        }
        
        // Use A* pathfinding
        return aStarPathfinding(start, end, world);
    }
    
    /**
     * A* pathfinding algorithm implementation.
     * Finds shortest path avoiding obstacles (water, lava, walls).
     */
    private static List<BlockPos> aStarPathfinding(BlockPos start, BlockPos end, ServerWorld world) {
        // Priority queue for open set (positions to explore)
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(PathNode::getF));
        // Set of explored positions
        Set<BlockPos> closedSet = new HashSet<>();
        // Map of position to node (for path reconstruction)
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        
        // Create start node
        PathNode startNode = new PathNode(start, null, 0, heuristic(start, end));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        while (!openSet.isEmpty()) {
            // Get node with lowest f score
            PathNode current = openSet.poll();
            
            if (current.pos.equals(end)) {
                // Reached goal, reconstruct path
                return reconstructPath(current);
            }
            
            closedSet.add(current.pos);
            
            // Explore neighbors (4 cardinal directions)
            BlockPos[] neighbors = {
                current.pos.north(),
                current.pos.south(),
                current.pos.east(),
                current.pos.west()
            };
            
            for (BlockPos neighbor : neighbors) {
                if (closedSet.contains(neighbor)) {
                    continue; // Already explored
                }
                
                // Check if position is valid for path placement
                if (!isValidPathPosition(neighbor, world)) {
                    continue; // Invalid position (water, lava, etc.)
                }
                
                // Calculate g score (distance from start)
                double g = current.g + 1.0;
                
                // Check if we've seen this position before
                PathNode neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    // New position
                    double h = heuristic(neighbor, end);
                    neighborNode = new PathNode(neighbor, current, g, h);
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (g < neighborNode.g) {
                    // Found better path to this position
                    neighborNode.g = g;
                    neighborNode.parent = current;
                    neighborNode.f = g + neighborNode.h;
                    // Re-add to priority queue (will be reordered)
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }
        
        // No path found
        return new ArrayList<>();
    }
    
    /**
     * Reconstructs the path from goal to start.
     */
    private static List<BlockPos> reconstructPath(PathNode goal) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = goal;
        
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        
        // Reverse to get path from start to end
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Heuristic function (Manhattan distance).
     */
    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }
    
    /**
     * Checks if a position is valid for path placement.
     * @param pos Position to check
     * @param world The server world
     * @return true if position is valid for path
     */
    private static boolean isValidPathPosition(BlockPos pos, ServerWorld world) {
        // Check if position is loaded (use chunk manager)
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        
        // Get ground level at this position
        int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos groundPos = new BlockPos(pos.getX(), topY, pos.getZ());
        
        BlockState groundState = world.getBlockState(groundPos);
        BlockState belowState = world.getBlockState(groundPos.down());
        
        // Check if ground is suitable for path (air, grass, dirt, etc.)
        if (!groundState.isAir() && !groundState.isReplaceable()) {
            return false; // Blocked by solid block
        }
        
        // Check if block below is solid (needed for path placement)
        if (belowState.isAir() || !belowState.isSolidBlock(world, groundPos.down())) {
            return false; // No solid ground below
        }
        
        // Check for water or lava
        if (groundState.getBlock() == Blocks.WATER || groundState.getBlock() == Blocks.LAVA ||
            belowState.getBlock() == Blocks.WATER || belowState.getBlock() == Blocks.LAVA) {
            return false; // Water or lava
        }
        
        return true;
    }
    
    /**
     * Node for A* pathfinding.
     */
    private static class PathNode {
        final BlockPos pos;
        PathNode parent;
        double g; // Cost from start
        double h; // Heuristic (estimated cost to goal)
        double f; // Total cost (g + h)
        
        PathNode(BlockPos pos, PathNode parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }
        
        double getF() {
            return f;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathNode pathNode = (PathNode) o;
            return Objects.equals(pos, pathNode.pos);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(pos);
        }
    }
}

