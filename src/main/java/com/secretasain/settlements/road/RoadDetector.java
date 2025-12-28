package com.secretasain.settlements.road;

import com.secretasain.settlements.settlement.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Utility class for detecting existing roads (path blocks) in settlements.
 */
public class RoadDetector {
    
    /**
     * Finds all path blocks within a settlement's radius.
     * @param settlement The settlement to scan
     * @param world The server world
     * @return Set of BlockPos positions containing path blocks
     */
    public static Set<BlockPos> findRoads(Settlement settlement, ServerWorld world) {
        Set<BlockPos> roadPositions = new HashSet<>();
        
        if (settlement == null || world == null || settlement.getLecternPos() == null) {
            return roadPositions;
        }
        
        BlockPos center = settlement.getLecternPos();
        int radius = settlement.getRadius();
        
        // Scan area for path blocks
        // We'll scan in chunks to avoid performance issues
        int scanStep = 4; // Scan every 4 blocks to reduce load
        for (int x = center.getX() - radius; x <= center.getX() + radius; x += scanStep) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += scanStep) {
                // Check distance
                double distanceSq = center.getSquaredDistance(x, center.getY(), z);
                if (distanceSq > radius * radius) {
                    continue;
                }
                
                // Get top Y at this position for accurate ground level
                int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos groundPos = new BlockPos(x, topY, z);
                
                BlockState state = world.getBlockState(groundPos);
                Block block = state.getBlock();
                
                // Check if block is a path block
                if (block == Blocks.DIRT_PATH) {
                    roadPositions.add(groundPos);
                }
            }
        }
        
        return roadPositions;
    }
    
    /**
     * Finds the nearest road to a given position.
     * @param pos The position to search from
     * @param roads Set of road positions
     * @param maxDistance Maximum distance to search (squared)
     * @return Nearest road position, or null if none found within maxDistance
     */
    public static BlockPos findNearestRoad(BlockPos pos, Set<BlockPos> roads, double maxDistanceSq) {
        if (roads == null || roads.isEmpty()) {
            return null;
        }
        
        BlockPos nearest = null;
        double nearestDistanceSq = maxDistanceSq;
        
        for (BlockPos roadPos : roads) {
            double distanceSq = pos.getSquaredDistance(roadPos);
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = roadPos;
            }
        }
        
        return nearest;
    }
    
    /**
     * Builds a graph of connected road segments.
     * Roads are considered connected if they are adjacent (within 1 block).
     * @param roads Set of road positions
     * @return Map of road position to list of connected road positions
     */
    public static Map<BlockPos, List<BlockPos>> buildRoadNetwork(Set<BlockPos> roads) {
        Map<BlockPos, List<BlockPos>> network = new HashMap<>();
        
        for (BlockPos road : roads) {
            List<BlockPos> connected = new ArrayList<>();
            
            // Check all 4 cardinal directions
            BlockPos[] neighbors = {
                road.north(),
                road.south(),
                road.east(),
                road.west()
            };
            
            for (BlockPos neighbor : neighbors) {
                if (roads.contains(neighbor)) {
                    connected.add(neighbor);
                }
            }
            
            network.put(road, connected);
        }
        
        return network;
    }
}

