package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.UUID;

/**
 * Handles pathfinding for assigned villagers to their work buildings.
 * Makes villagers stay within a small radius of their assigned building persistently.
 */
public class VillagerPathfindingSystem {
    private static final int PATHFINDING_INTERVAL_TICKS = 20; // Update pathfinding every 1 second (20 ticks)
    private static final double WORK_RADIUS = 8.0; // Villagers must stay within 8 blocks of their building
    private static final double WORK_RADIUS_SQ = WORK_RADIUS * WORK_RADIUS; // Squared distance for comparison
    private static final double LUMBERYARD_WORK_RADIUS = 32.0; // Lumberyard villagers can work within 32 blocks (for tree harvesting)
    private static final double LUMBERYARD_WORK_RADIUS_SQ = LUMBERYARD_WORK_RADIUS * LUMBERYARD_WORK_RADIUS;
    
    /**
     * Registers the pathfinding system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private static void tick(ServerWorld world) {
        // Only run periodically to avoid performance issues
        if (world.getTime() % PATHFINDING_INTERVAL_TICKS != 0) {
            return;
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        // Process each settlement
        for (Settlement settlement : allSettlements) {
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Processes pathfinding for all assigned villagers in a settlement.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        for (VillagerData villagerData : settlement.getVillagers()) {
            // Only process employed and assigned villagers
            if (!villagerData.isEmployed() || !villagerData.isAssigned()) {
                continue;
            }
            
            // Skip pathfinding if villager is depositing (let deposit system handle movement)
            if (villagerData.isDepositing()) {
                continue;
            }
            
            // Skip pathfinding if villager is doing composter task (let composter system handle movement)
            if (com.secretasain.settlements.farm.FarmComposterSystem.isVillagerDoingComposterTask(villagerData.getEntityId())) {
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
            
            if (building == null) {
                // Building was removed, unassign the villager
                SettlementsMod.LOGGER.warn("Building {} not found for assigned villager {}, unassigning", 
                    buildingId, villagerData.getEntityId());
                villagerData.setAssignedBuildingId(null);
                continue;
            }
            
            // Only pathfind to completed buildings
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Check if this is a lumberyard building
            boolean isLumberyard = isLumberyardBuilding(building);
            
            // Skip pathfinding if lumberyard villager is actively working (harvesting trees or collecting items)
            if (isLumberyard) {
                if (isLumberyardVillagerActivelyWorking(villagerData.getEntityId())) {
                    continue; // Let them work without interruption
                }
            }
            
            // Get the actual villager entity
            VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
            if (villager == null) {
                continue; // Villager not loaded or doesn't exist
            }
            
            // Calculate target position (building center)
            BlockPos buildingPos = building.getPosition();
            BlockPos targetPos = findTargetPosition(buildingPos, world);
            
            // Calculate distance from villager to building center
            double distanceSq = villager.getPos().squaredDistanceTo(
                targetPos.getX() + 0.5, 
                targetPos.getY() + 0.5, 
                targetPos.getZ() + 0.5
            );
            
            // Use larger radius for lumberyard buildings
            double workRadiusSq = isLumberyard ? LUMBERYARD_WORK_RADIUS_SQ : WORK_RADIUS_SQ;
            
            // Always enforce radius - if villager is outside the work radius, pathfind back
            if (distanceSq > workRadiusSq) {
                // Villager is outside work radius, pathfind back to building
                boolean pathStarted = villager.getNavigation().startMovingTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    1.0 // Speed modifier (normal speed)
                );
                
                if (pathStarted) {
                    SettlementsMod.LOGGER.debug("Villager {} outside work radius ({} blocks), pathfinding back to building at {}", 
                        villagerData.getEntityId(), Math.sqrt(distanceSq), targetPos);
                } else {
                    // If pathfinding failed, try to teleport them closer (last resort)
                    // This prevents villagers from getting stuck
                    // BUT: Only allow teleportation if ender upgrade is active
                    double maxRadiusSq = isLumberyard ? LUMBERYARD_WORK_RADIUS_SQ * 4 : WORK_RADIUS_SQ * 4;
                    if (distanceSq > maxRadiusSq) { // Only if very far away (more than 2x radius)
                        BlockPos safePos = findSafePositionNearBuilding(buildingPos, world);
                        if (safePos != null) {
                            // Use safe teleport which checks for ender upgrade
                            boolean teleported = com.secretasain.settlements.pathfinding.VillagerMovementController.safeTeleport(
                                world, villager, safePos
                            );
                            if (teleported) {
                                SettlementsMod.LOGGER.info("Teleported villager {} back to building area (was {} blocks away)", 
                                    villagerData.getEntityId(), Math.sqrt(distanceSq));
                            } else {
                                SettlementsMod.LOGGER.debug("Cannot teleport villager {} - no ender upgrade active", 
                                    villagerData.getEntityId());
                            }
                        }
                    }
                }
            }
        }
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
    
    /**
     * Finds a good target position near the building.
     * Returns the building position (center) for now.
     * Could be enhanced to find entrance or safe position.
     */
    private static BlockPos findTargetPosition(BlockPos buildingPos, ServerWorld world) {
        // For now, just use the building position
        // Could be enhanced to:
        // - Find a door/entrance
        // - Find a safe position (not in blocks, not in water)
        // - Find a position with air above it
        return buildingPos;
    }
    
    /**
     * Finds a safe position near the building for teleporting villagers.
     * Looks for a position with air above it and solid ground below.
     */
    private static BlockPos findSafePositionNearBuilding(BlockPos buildingPos, ServerWorld world) {
        // Try positions around the building in a small radius
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos testPos = buildingPos.add(x, 0, z);
                int topY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, testPos.getX(), testPos.getZ());
                BlockPos groundPos = new BlockPos(testPos.getX(), topY, testPos.getZ());
                
                // Check if position is safe (air at ground level, solid block below)
                if (world.getBlockState(groundPos).isAir() && 
                    !world.getBlockState(groundPos.down()).isAir()) {
                    return groundPos;
                }
            }
        }
        
        // Fallback: just use building position
        return buildingPos;
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
     * Checks if a lumberyard villager is actively working (harvesting trees or collecting items).
     */
    private static boolean isLumberyardVillagerActivelyWorking(UUID villagerId) {
        // Check if villager is collecting items (second lumberyard villager)
        if (com.secretasain.settlements.settlement.LumberyardItemCollectorSystem.isVillagerActivelyWorking(villagerId)) {
            return true;
        }
        
        // Check if villager is harvesting logs (first lumberyard villager)
        // Note: LumberjackLogHarvester doesn't have a state tracking system,
        // but we can check if the villager has a navigation path to a log location
        // For now, we'll rely on the item collector check and the increased radius
        
        return false;
    }
}

