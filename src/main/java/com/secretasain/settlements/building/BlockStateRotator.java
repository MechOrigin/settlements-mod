package com.secretasain.settlements.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;

/**
 * Utility class for rotating block states when structures are rotated.
 * Handles directional blocks like stairs, slabs, doors, and other blocks with facing properties.
 * Uses Minecraft's vanilla rotation logic to ensure compatibility.
 */
public class BlockStateRotator {
    
    /**
     * Rotates a block state based on the structure rotation.
     * @param state The original block state
     * @param rotation The rotation in degrees (0, 90, 180, 270)
     * @return Rotated block state, or original if rotation not applicable
     */
    public static BlockState rotateBlockState(BlockState state, int rotation) {
        if (rotation == 0) {
            return state; // No rotation needed
        }
        
        Block block = state.getBlock();
        
        // Handle doors - special case, they have two halves and hinge side
        if (block instanceof DoorBlock) {
            return rotateDoor(state, rotation);
        }
        
        // Handle stairs
        if (block instanceof StairsBlock) {
            return rotateStairs(state, rotation);
        }
        
        // Handle slabs (slabs don't have facing, but we check anyway)
        if (block instanceof SlabBlock) {
            // Slabs don't rotate, but keep for completeness
            return state;
        }
        
        // Handle blocks with horizontal facing (beds, etc.)
        if (state.contains(HorizontalFacingBlock.FACING)) {
            return rotateHorizontalFacing(state, rotation);
        }
        
        // Handle other directional blocks
        // Check for any DirectionProperty
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty) {
                DirectionProperty dirProp = (DirectionProperty) property;
                if (state.contains(dirProp)) {
                    Direction originalDir = state.get(dirProp);
                    Direction rotatedDir = rotateDirection(originalDir, rotation);
                    if (rotatedDir != null && dirProp.getValues().contains(rotatedDir)) {
                        return state.with(dirProp, rotatedDir);
                    }
                }
            }
        }
        
        // No rotation applicable, return original
        return state;
    }
    
    /**
     * Rotates a stairs block state.
     * @param state The stairs block state
     * @param rotation The rotation in degrees (90, 180, 270)
     * @return Rotated stairs block state
     */
    private static BlockState rotateStairs(BlockState state, int rotation) {
        if (!state.contains(StairsBlock.FACING) || !state.contains(StairsBlock.HALF) || !state.contains(StairsBlock.SHAPE)) {
            return state;
        }
        
        Direction originalFacing = state.get(StairsBlock.FACING);
        BlockHalf half = state.get(StairsBlock.HALF);
        StairShape shape = state.get(StairsBlock.SHAPE);
        
        // Rotate facing direction
        Direction rotatedFacing = rotateDirection(originalFacing, rotation);
        if (rotatedFacing == null) {
            return state;
        }
        
        // Rotate stair shape
        StairShape rotatedShape = rotateStairShape(shape, rotation);
        
        return state.with(StairsBlock.FACING, rotatedFacing)
                   .with(StairsBlock.HALF, half) // Half doesn't change
                   .with(StairsBlock.SHAPE, rotatedShape);
    }
    
    /**
     * Rotates a door block state.
     * Doors have facing, half (upper/lower), hinge side, and open state.
     * @param state The door block state
     * @param rotation The rotation in degrees (90, 180, 270)
     * @return Rotated door block state
     */
    private static BlockState rotateDoor(BlockState state, int rotation) {
        if (!state.contains(DoorBlock.FACING) || !state.contains(DoorBlock.HALF) || 
            !state.contains(DoorBlock.OPEN)) {
            return state;
        }
        
        Direction originalFacing = state.get(DoorBlock.FACING);
        DoubleBlockHalf half = state.get(DoorBlock.HALF);
        boolean open = state.get(DoorBlock.OPEN);
        
        // Rotate facing direction
        Direction rotatedFacing = rotateDirection(originalFacing, rotation);
        if (rotatedFacing == null || !rotatedFacing.getAxis().isHorizontal()) {
            return state;
        }
        
        // Get hinge if present (may not be available in all versions)
        BlockState rotatedState = state.with(DoorBlock.FACING, rotatedFacing)
                                      .with(DoorBlock.HALF, half) // Half doesn't change
                                      .with(DoorBlock.OPEN, open); // Open state doesn't change
        
        // Rotate hinge side if property exists
        if (state.contains(DoorBlock.HINGE)) {
            // Use reflection or property access to get hinge value
            // For now, let Minecraft handle hinge rotation automatically
            // The hinge will be adjusted when the door is placed
        }
        
        return rotatedState;
    }
    
    /**
     * Rotates a horizontal facing direction.
     * @param state The block state with horizontal facing
     * @param rotation The rotation in degrees (90, 180, 270)
     * @return Rotated block state
     */
    private static BlockState rotateHorizontalFacing(BlockState state, int rotation) {
        if (!state.contains(HorizontalFacingBlock.FACING)) {
            return state;
        }
        
        Direction originalFacing = state.get(HorizontalFacingBlock.FACING);
        Direction rotatedFacing = rotateDirection(originalFacing, rotation);
        
        if (rotatedFacing == null || !rotatedFacing.getAxis().isHorizontal()) {
            return state; // Can't rotate vertical directions
        }
        
        return state.with(HorizontalFacingBlock.FACING, rotatedFacing);
    }
    
    /**
     * Rotates a direction based on rotation angle.
     * @param direction The original direction
     * @param rotation The rotation in degrees (90, 180, 270)
     * @return Rotated direction, or null if rotation not applicable
     */
    private static Direction rotateDirection(Direction direction, int rotation) {
        if (direction.getAxis() == Direction.Axis.Y) {
            return direction; // Vertical directions don't rotate
        }
        
        // Get horizontal rotation steps (90 degrees = 1 step)
        int steps = rotation / 90;
        
        // Rotate clockwise
        Direction[] horizontalDirections = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
        };
        
        // Find current direction index
        int currentIndex = -1;
        for (int i = 0; i < horizontalDirections.length; i++) {
            if (horizontalDirections[i] == direction) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex == -1) {
            return direction; // Not a horizontal direction
        }
        
        // Calculate new index (clockwise rotation)
        int newIndex = (currentIndex + steps) % horizontalDirections.length;
        return horizontalDirections[newIndex];
    }
    
    /**
     * Rotates a stair shape based on rotation angle.
     * @param shape The original stair shape
     * @param rotation The rotation in degrees (90, 180, 270)
     * @return Rotated stair shape
     */
    private static StairShape rotateStairShape(StairShape shape, int rotation) {
        // Stair shapes are: STRAIGHT, INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT
        // Rotation affects LEFT/RIGHT and INNER/OUTER relationships
        
        int steps = rotation / 90;
        
        // Map of shape rotations
        // Each rotation step: LEFT <-> RIGHT, INNER <-> OUTER (depending on facing)
        // For simplicity, we'll rotate LEFT/RIGHT based on rotation steps
        switch (shape) {
            case STRAIGHT:
                return StairShape.STRAIGHT; // Straight doesn't change
            case INNER_LEFT:
                if (steps % 2 == 0) {
                    return StairShape.INNER_LEFT;
                } else {
                    return StairShape.INNER_RIGHT;
                }
            case INNER_RIGHT:
                if (steps % 2 == 0) {
                    return StairShape.INNER_RIGHT;
                } else {
                    return StairShape.INNER_LEFT;
                }
            case OUTER_LEFT:
                if (steps % 2 == 0) {
                    return StairShape.OUTER_LEFT;
                } else {
                    return StairShape.OUTER_RIGHT;
                }
            case OUTER_RIGHT:
                if (steps % 2 == 0) {
                    return StairShape.OUTER_RIGHT;
                } else {
                    return StairShape.OUTER_LEFT;
                }
            default:
                return shape;
        }
    }
}

