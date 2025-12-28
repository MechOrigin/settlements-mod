package com.secretasain.settlements.ender;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import com.secretasain.settlements.settlement.VillagerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * System for handling ender pearl teleportation for villagers when ender upgrade is active.
 * Villagers can teleport to their assigned buildings or chests using ender pearls.
 */
public class VillagerEnderTeleportSystem {
    private static final double TELEPORT_DISTANCE_THRESHOLD = 16.0; // Only teleport if more than 16 blocks away
    private static final double TELEPORT_DISTANCE_THRESHOLD_SQ = TELEPORT_DISTANCE_THRESHOLD * TELEPORT_DISTANCE_THRESHOLD;
    private static final int CHECK_INTERVAL = 100; // Check every 5 seconds (100 ticks)
    
    /**
     * Registers the ender teleport system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_INTERVAL == 0) {
                tick(world);
            }
        });
    }
    
    /**
     * Performs a tick update for the given world.
     */
    private static void tick(ServerWorld world) {
        SettlementManager manager = SettlementManager.getInstance(world);
        
        for (Settlement settlement : manager.getAllSettlements()) {
            EnderUpgrade upgrade = settlement.getEnderUpgrade();
            if (upgrade == null || !upgrade.isActive()) {
                continue; // No ender upgrade active
            }
            
            // Check if cooldown has passed
            if (!upgrade.canUseEnderPearl(world.getTime())) {
                continue; // Still on cooldown
            }
            
            // Process villagers in this settlement
            for (VillagerData villagerData : settlement.getVillagers()) {
                if (!villagerData.isEmployed() || !villagerData.isAssigned()) {
                    continue;
                }
                
                UUID buildingId = villagerData.getAssignedBuildingId();
                if (buildingId == null) {
                    continue;
                }
                
                // Find the building
                Building building = settlement.getBuildings().stream()
                    .filter(b -> b.getId().equals(buildingId))
                    .findFirst()
                    .orElse(null);
                
                if (building == null || building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                    continue;
                }
                
                // Get the villager entity
                VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
                if (villager == null) {
                    continue;
                }
                
                // Check if villager is far from building
                BlockPos buildingPos = building.getPosition();
                double distanceSq = villager.getPos().squaredDistanceTo(
                    buildingPos.getX() + 0.5,
                    buildingPos.getY() + 0.5,
                    buildingPos.getZ() + 0.5
                );
                
                // Only teleport if far away and pathfinding might be difficult
                if (distanceSq > TELEPORT_DISTANCE_THRESHOLD_SQ) {
                    // Check if villager is stuck or pathfinding is failing
                    if (shouldTeleport(villager, buildingPos, world)) {
                        BlockPos targetPos = findSafeTeleportPosition(buildingPos, world);
                        if (targetPos != null) {
                            teleportVillager(world, villager, targetPos, upgrade);
                            upgrade.recordTeleport(world.getTime());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Checks if a villager should teleport (e.g., stuck or pathfinding failing).
     */
    private static boolean shouldTeleport(VillagerEntity villager, BlockPos targetPos, ServerWorld world) {
        // Check if villager's navigation is stuck (not making progress)
        var navigation = villager.getNavigation();
        if (navigation.isIdle() || !navigation.isFollowingPath()) {
            // Villager is not pathfinding - might be stuck
            return true;
        }
        
        // Check if pathfinding target is still far away after some time
        // (This is a simple check - could be enhanced)
        return true; // For now, allow teleportation if far enough away
    }
    
    /**
     * Finds a safe position near the building for teleportation.
     */
    private static BlockPos findSafeTeleportPosition(BlockPos buildingPos, ServerWorld world) {
        // Try positions around the building
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos testPos = buildingPos.add(x, 0, z);
                int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, testPos.getX(), testPos.getZ());
                BlockPos groundPos = new BlockPos(testPos.getX(), topY, testPos.getZ());
                
                // Check if position is safe (air at ground level, solid block below, air above)
                if (world.getBlockState(groundPos).isAir() && 
                    !world.getBlockState(groundPos.down()).isAir() &&
                    world.getBlockState(groundPos.up()).isAir()) {
                    return groundPos;
                }
            }
        }
        
        // Fallback: use building position
        return buildingPos;
    }
    
    /**
     * Teleports a villager to a position with visual/audio feedback.
     */
    private static void teleportVillager(ServerWorld world, VillagerEntity villager, BlockPos targetPos, EnderUpgrade upgrade) {
        Vec3d oldPos = villager.getPos();
        
        // Teleport the villager
        villager.teleport(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        
        // Visual feedback: ender particles at old and new positions
        world.spawnParticles(
            ParticleTypes.PORTAL,
            oldPos.x, oldPos.y + 1.0, oldPos.z,
            30, 0.5, 0.5, 0.5, 0.1
        );
        
        world.spawnParticles(
            ParticleTypes.PORTAL,
            targetPos.getX() + 0.5, targetPos.getY() + 1.5, targetPos.getZ() + 0.5,
            30, 0.5, 0.5, 0.5, 0.1
        );
        
        // Audio feedback: ender pearl sound
        world.playSound(null, targetPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 
            SoundCategory.NEUTRAL, 0.5f, 1.0f);
        
        SettlementsMod.LOGGER.debug("Villager {} teleported using ender upgrade", villager.getUuid());
    }
    
    /**
     * Gets the VillagerEntity from the world by UUID.
     */
    private static VillagerEntity getVillagerEntity(ServerWorld world, UUID entityId) {
        try {
            return (VillagerEntity) world.getEntity(entityId);
        } catch (Exception e) {
            return null;
        }
    }
}

