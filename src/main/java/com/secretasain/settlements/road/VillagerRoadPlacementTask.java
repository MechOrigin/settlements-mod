package com.secretasain.settlements.road;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Settlement;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Task for a villager to place road blocks using a shovel.
 * Villager will pathfind to each position and use a shovel to convert grass to path.
 */
public class VillagerRoadPlacementTask {
    private final UUID villagerId;
    private final List<BlockPos> pathPositions;
    private int currentIndex;
    private BlockPos currentTarget;
    private int waitTicks;
    private static final int MAX_WAIT_TICKS = 40; // Wait up to 2 seconds at each position
    
    public VillagerRoadPlacementTask(UUID villagerId, Settlement settlement, List<BlockPos> pathPositions) {
        this.villagerId = villagerId;
        this.pathPositions = pathPositions;
        this.currentIndex = 0;
        this.waitTicks = 0;
    }
    
    /**
     * Updates the task for one tick.
     * @param world The server world
     * @return true if task is complete, false if still in progress
     */
    public boolean tick(ServerWorld world) {
        if (pathPositions.isEmpty() || currentIndex >= pathPositions.size()) {
            return true; // Task complete
        }
        
        // Find villager entity
        VillagerEntity villager = findVillager(world);
        if (villager == null) {
            return true; // Villager not found, task complete
        }
        
        // Get current target position
        currentTarget = pathPositions.get(currentIndex);
        
        // Get ground level for target
        int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, currentTarget.getX(), currentTarget.getZ());
        BlockPos targetPos = new BlockPos(currentTarget.getX(), topY, currentTarget.getZ());
        
        // Check if position is already a path block
        BlockState currentState = world.getBlockState(targetPos);
        if (currentState.getBlock() == Blocks.DIRT_PATH) {
            // Already a path, move to next position
            currentIndex++;
            waitTicks = 0;
            return false;
        }
        
        // Don't convert blocks that aren't grass/dirt (stairs, doors, etc.)
        if (currentState.getBlock() != Blocks.GRASS_BLOCK && 
            currentState.getBlock() != Blocks.DIRT &&
            currentState.getBlock() != Blocks.COARSE_DIRT &&
            currentState.getBlock() != Blocks.PODZOL &&
            !currentState.isAir()) {
            // Skip this position - can't convert (might be stairs, door, etc.)
            SettlementsMod.LOGGER.debug("Road placement: Skipping position {} - block {} cannot be converted to path", 
                targetPos, currentState.getBlock());
            currentIndex++;
            waitTicks = 0;
            return false;
        }
        
        // Check distance to target
        double distanceSq = villager.squaredDistanceTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        
        if (distanceSq > 16.0) {
            // Too far, pathfind closer
            villager.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
            waitTicks = 0;
            return false;
        }
        
        // Close enough, wait a bit then use shovel
        waitTicks++;
        if (waitTicks < MAX_WAIT_TICKS) {
            return false; // Still waiting
        }
        
        // Use shovel to convert grass to path
        if (useShovelOnBlock(villager, world, targetPos)) {
            // Successfully converted, move to next position
            currentIndex++;
            waitTicks = 0;
        } else {
            // Failed, try again next tick
            waitTicks = 0;
        }
        
        return false; // Task not complete yet
    }
    
    /**
     * Finds the villager entity by UUID.
     */
    private VillagerEntity findVillager(ServerWorld world) {
        net.minecraft.entity.Entity entity = world.getEntity(villagerId);
        return entity instanceof VillagerEntity ? (VillagerEntity) entity : null;
    }
    
    /**
     * Uses a shovel on a block to convert it to a path block.
     * @param villager The villager
     * @param world The server world
     * @param pos The position to convert
     * @return true if successful
     */
    private boolean useShovelOnBlock(VillagerEntity villager, ServerWorld world, BlockPos pos) {
        BlockState currentState = world.getBlockState(pos);
        
        // Check if block can be converted to path (grass, dirt, etc.)
        if (currentState.getBlock() != Blocks.GRASS_BLOCK && 
            currentState.getBlock() != Blocks.DIRT &&
            currentState.getBlock() != Blocks.COARSE_DIRT &&
            currentState.getBlock() != Blocks.PODZOL &&
            !currentState.isAir()) {
            return false; // Can't convert this block
        }
        
        // Give villager a shovel if they don't have one
        ItemStack shovel = findShovelInInventory(villager);
        if (shovel == null) {
            // Try to get shovel from settlement materials or give one
            shovel = new ItemStack(Items.IRON_SHOVEL);
            villager.getInventory().addStack(shovel);
        }
        
        // Use shovel on the block (simulate right-click)
        // This will convert grass/dirt to path block
        BlockState newState = Blocks.DIRT_PATH.getDefaultState();
        world.setBlockState(pos, newState, 3); // 3 = NOTIFY_NEIGHBORS | BLOCK_UPDATE
        
        // Play shovel use sound
        world.playSound(null, pos, 
            net.minecraft.sound.SoundEvents.ITEM_SHOVEL_FLATTEN,
            net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
        
        // Spawn particles
        world.spawnParticles(
            new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK, currentState),
            pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
            10, 0.2, 0.1, 0.2, 0.1);
        
        return true;
    }
    
    /**
     * Finds a shovel in the villager's inventory.
     */
    private ItemStack findShovelInInventory(VillagerEntity villager) {
        for (int i = 0; i < villager.getInventory().size(); i++) {
            ItemStack stack = villager.getInventory().getStack(i);
            if (stack.getItem() instanceof net.minecraft.item.ShovelItem) {
                return stack;
            }
        }
        return null;
    }
    
    /**
     * Gets the current progress (0.0 to 1.0).
     */
    public double getProgress() {
        if (pathPositions.isEmpty()) {
            return 1.0;
        }
        return (double) currentIndex / pathPositions.size();
    }
    
    /**
     * Checks if the task is complete.
     */
    public boolean isComplete() {
        return currentIndex >= pathPositions.size();
    }
}

