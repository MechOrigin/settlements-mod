package com.secretasain.settlements.road;

import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for detecting doors in building structures.
 */
public class BuildingDoorDetector {
    
    /**
     * Finds all door positions in a building structure.
     * Accounts for structure rotation when calculating world positions.
     * @param building The building to scan
     * @param server The Minecraft server (for structure loading)
     * @return List of door positions in world coordinates
     */
    public static List<BlockPos> findDoors(Building building, MinecraftServer server) {
        List<BlockPos> doorPositions = new ArrayList<>();
        
        if (building == null || server == null) {
            return doorPositions;
        }
        
        // Load structure data
        StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
        if (structureData == null) {
            return doorPositions;
        }
        
        // Get building position and rotation
        BlockPos buildingPos = building.getPosition();
        int rotation = building.getRotation();
        
        // Scan all blocks in structure for doors
        for (com.secretasain.settlements.building.StructureBlock structureBlock : structureData.getBlocks()) {
            BlockState blockState = structureBlock.getBlockState();
            Block block = blockState.getBlock();
            
            // Check if block is a door
            if (block instanceof DoorBlock) {
                // Get relative position from structure
                BlockPos relativePos = structureBlock.getRelativePos();
                
                // Apply rotation to relative position
                BlockPos rotatedPos = rotatePosition(relativePos, rotation, structureData.getDimensions());
                
                // Calculate world position
                BlockPos worldPos = buildingPos.add(rotatedPos);
                
                doorPositions.add(worldPos);
            }
        }
        
        return doorPositions;
    }
    
    /**
     * Rotates a relative position based on building rotation.
     * @param relativePos Relative position in structure
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @param dimensions Structure dimensions
     * @return Rotated relative position
     */
    private static BlockPos rotatePosition(BlockPos relativePos, int rotation, Vec3i dimensions) {
        int x = relativePos.getX();
        int y = relativePos.getY();
        int z = relativePos.getZ();
        
        // Normalize rotation to 0-270
        rotation = ((rotation % 360) + 360) % 360;
        rotation = (rotation / 90) * 90; // Snap to 90-degree increments
        
        int width = dimensions.getX();
        int depth = dimensions.getZ();
        
        switch (rotation) {
            case 90:
                // Rotate 90 degrees clockwise: (x, z) -> (depth - 1 - z, x)
                return new BlockPos(depth - 1 - z, y, x);
            case 180:
                // Rotate 180 degrees: (x, z) -> (width - 1 - x, depth - 1 - z)
                return new BlockPos(width - 1 - x, y, depth - 1 - z);
            case 270:
                // Rotate 270 degrees clockwise: (x, z) -> (z, width - 1 - x)
                return new BlockPos(z, y, width - 1 - x);
            case 0:
            default:
                // No rotation
                return relativePos;
        }
    }
}

