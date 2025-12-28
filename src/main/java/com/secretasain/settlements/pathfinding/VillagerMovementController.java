package com.secretasain.settlements.pathfinding;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Controls villager movement to prevent teleportation and fast movement
 * unless an ender upgrade is active.
 */
public class VillagerMovementController {
    private static final double MAX_WALKING_SPEED = 0.25; // Normal walking speed (blocks per tick)
    
    /**
     * Checks if a settlement has an active ender upgrade.
     * @param world The server world
     * @param settlement The settlement
     * @return true if ender upgrade is active
     */
    public static boolean hasEnderUpgrade(ServerWorld world, Settlement settlement) {
        if (settlement == null) {
            return false;
        }
        
        com.secretasain.settlements.ender.EnderUpgrade upgrade = settlement.getEnderUpgrade();
        return upgrade != null && upgrade.isActive();
    }
    
    /**
     * Checks if a villager can teleport (only if ender upgrade is active).
     * @param world The server world
     * @param villager The villager entity
     * @return true if teleportation is allowed
     */
    public static boolean canTeleport(ServerWorld world, VillagerEntity villager) {
        SettlementManager manager = SettlementManager.getInstance(world);
        Settlement settlement = manager.findSettlementAt(villager.getBlockPos());
        
        if (settlement == null) {
            return false; // No settlement, no teleportation
        }
        
        return hasEnderUpgrade(world, settlement);
    }
    
    /**
     * Limits villager movement speed to normal walking speed.
     * The speed is controlled by the speed parameter in Navigation.startMovingTo(),
     * which is already handled in VillagerPathfindingSystem.
     * @param villager The villager entity
     */
    public static void limitMovementSpeed(VillagerEntity villager) {
        // Speed limiting is handled in VillagerPathfindingSystem by using speed = 1.0
        // This method is kept for future enhancements if needed
    }
    
    /**
     * Safely teleports a villager to a position (only if allowed).
     * @param world The server world
     * @param villager The villager entity
     * @param pos The target position
     * @return true if teleportation was successful
     */
    public static boolean safeTeleport(ServerWorld world, VillagerEntity villager, BlockPos pos) {
        if (!canTeleport(world, villager)) {
            SettlementsMod.LOGGER.debug("Teleportation denied for villager {} - no ender upgrade", villager.getUuid());
            return false;
        }
        
        // Teleportation is allowed - perform it
        villager.teleport(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return true;
    }
}

