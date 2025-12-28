package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * System for enhancing wandering trader spawn rates based on active town halls.
 * Calculates spawn multipliers and attempts to spawn traders near town halls.
 */
public class WanderingTraderSpawnEnhancer {
    
    /**
     * Calculates the wandering trader spawn multiplier for a world based on active town halls.
     * Only returns a multiplier if there is at least one trading hut built.
     * @param world The server world
     * @return Spawn multiplier (1.0 = no change, 2.0 = double chance, etc.)
     */
    public static double calculateSpawnMultiplier(ServerWorld world) {
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> settlements = manager.getAllSettlements();
        
        // Check if there is at least one trading hut built
        if (!hasTradingHut(settlements)) {
            SettlementsMod.LOGGER.debug("No trading huts found - wandering trader spawn multiplier is 1.0 (no enhancement)");
            return 1.0; // No trading huts, no multiplier
        }
        
        int activeTownHalls = 0;
        
        // Count town halls with assigned librarians
        for (Settlement settlement : settlements) {
            for (Building building : settlement.getBuildings()) {
                if (TownHallDetector.isTownHall(building)) {
                    TownHallData hallData = TownHallData.getOrCreate(building);
                    if (hallData.hasLibrarian()) {
                        activeTownHalls++;
                        SettlementsMod.LOGGER.debug("Found active town hall {} in settlement {} with librarian {}",
                            building.getId(), settlement.getName(), hallData.getAssignedLibrarianId());
                    }
                }
            }
        }
        
        if (activeTownHalls == 0) {
            SettlementsMod.LOGGER.debug("No active town halls found for wandering trader spawn enhancement");
            return 1.0; // No active town halls, no multiplier
        }
        
        // Apply multiplier: base chance × (1 + 0.5 × townHallCount)
        // Cap at 3x (3 town halls = max multiplier)
        double multiplier = 1.0 + (0.5 * Math.min(activeTownHalls, 4)); // Max 4 town halls = 3x multiplier
        double finalMultiplier = Math.min(multiplier, 3.0); // Cap at 3x
        SettlementsMod.LOGGER.debug("Calculated wandering trader spawn multiplier: {} (from {} active town halls)", 
            finalMultiplier, activeTownHalls);
        return finalMultiplier;
    }
    
    /**
     * Attempts to spawn a wandering trader near a town hall if possible.
     * Only spawns if there is at least one trading hut built.
     * @param world The server world
     * @param spawnPos The position where vanilla would spawn the trader
     * @return WanderingTraderEntity if spawned, null otherwise
     */
    public static WanderingTraderEntity trySpawnNearTownHall(ServerWorld world, BlockPos spawnPos) {
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> settlements = manager.getAllSettlements();
        
        // Check if there is at least one trading hut built
        if (!hasTradingHut(settlements)) {
            SettlementsMod.LOGGER.debug("No trading huts found - wandering traders will not spawn");
            return null; // No trading huts, don't spawn traders
        }
        
        // Find town halls with assigned librarians
        List<Building> activeTownHalls = new ArrayList<>();
        for (Settlement settlement : settlements) {
            for (Building building : settlement.getBuildings()) {
                if (TownHallDetector.isTownHall(building)) {
                    TownHallData hallData = TownHallData.getOrCreate(building);
                    if (hallData.hasLibrarian()) {
                        activeTownHalls.add(building);
                    }
                }
            }
        }
        
        if (activeTownHalls.isEmpty()) {
            return null; // No active town halls, use vanilla spawning
        }
        
        // Try to spawn near a random active town hall
        Collections.shuffle(activeTownHalls, new java.util.Random(world.getRandom().nextLong()));
        
        for (Building townHall : activeTownHalls) {
            BlockPos townHallPos = townHall.getPosition();
            
            // Calculate spawn position near town hall (within 32 blocks)
            BlockPos nearPos = findSpawnPositionNearTownHall(world, townHallPos);
            
            if (nearPos != null) {
                try {
                    WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world);
                    if (trader == null) {
                        continue;
                    }
                    
                    trader.refreshPositionAndAngles(
                        nearPos.getX() + 0.5,
                        nearPos.getY(),
                        nearPos.getZ() + 0.5,
                        world.getRandom().nextFloat() * 360.0f,
                        0.0f
                    );
                    
                    world.spawnEntity(trader);
                    
                    // Record trader spawn for despawn tracking
                    WanderingTraderDespawnHandler.recordTraderSpawn(trader);
                    
                    SettlementsMod.LOGGER.info("Spawned wandering trader {} near town hall {} at {}",
                        trader.getUuid(), townHall.getId(), nearPos);
                    
                    return trader;
                } catch (Exception e) {
                    SettlementsMod.LOGGER.warn("Failed to spawn wandering trader near town hall {}: {}",
                        townHall.getId(), e.getMessage());
                }
            }
        }
        
        return null; // Could not spawn near town hall, use vanilla position
    }
    
    /**
     * Finds a safe spawn position near a town hall.
     * @param world The server world
     * @param townHallPos The town hall position
     * @return Safe spawn position or null if none found
     */
    private static BlockPos findSpawnPositionNearTownHall(ServerWorld world, BlockPos townHallPos) {
        // Try positions within 32 blocks of town hall
        int searchRadius = 32;
        int attempts = 20; // Try up to 20 random positions
        
        for (int i = 0; i < attempts; i++) {
            int offsetX = world.getRandom().nextInt(searchRadius * 2) - searchRadius;
            int offsetZ = world.getRandom().nextInt(searchRadius * 2) - searchRadius;
            
            BlockPos testPos = townHallPos.add(offsetX, 0, offsetZ);
            
            // Find ground level
            BlockPos groundPos = findGroundLevel(world, testPos);
            if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                return groundPos;
            }
        }
        
        // Fallback: try positions in a circle around town hall
        for (int radius = 5; radius <= searchRadius; radius += 5) {
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                int x = (int) (radius * Math.cos(radians));
                int z = (int) (radius * Math.sin(radians));
                
                BlockPos testPos = townHallPos.add(x, 0, z);
                BlockPos groundPos = findGroundLevel(world, testPos);
                if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                    return groundPos;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Finds the ground level at a given X/Z position.
     * Prioritizes searching downward to avoid spawning on top of buildings.
     * @param world The server world
     * @param pos The position (Y will be adjusted)
     * @return Ground position or null if not found
     */
    private static BlockPos findGroundLevel(ServerWorld world, BlockPos pos) {
        // Start from building Y level + 2 (slightly above building) and search down
        int startY = Math.min(pos.getY() + 2, world.getTopY() - 1);
        int minY = world.getBottomY();
        
        // ALWAYS search downward first to avoid spawning on top of buildings
        for (int y = startY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockPos groundPos = testPos.down();
            
            // Check if this is a valid ground position
            // Ground must be solid, spawn position and above must be air
            if (world.getBlockState(groundPos).isOpaque() &&
                world.getBlockState(testPos).isAir() &&
                world.getBlockState(testPos.up()).isAir()) {
                return testPos;
            }
        }
        
        // If no ground found below, try searching up (only as last resort)
        // This handles cases where building is below ground level
        for (int y = startY + 1; y <= world.getTopY() - 1; y++) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockPos groundPos = testPos.down();
            
            if (world.getBlockState(groundPos).isOpaque() &&
                world.getBlockState(testPos).isAir() &&
                world.getBlockState(testPos.up()).isAir()) {
                return testPos;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if position is safe for spawning a wandering trader.
     * Ensures ground is a safe surface block (dirt, grass, path, etc.) and not on top of buildings.
     * @param world The server world
     * @param pos The position to check
     * @return true if position is safe
     */
    private static boolean isSafeSpawnPosition(ServerWorld world, BlockPos pos) {
        net.minecraft.block.BlockState groundState = world.getBlockState(pos.down());
        
        // Check if position has solid ground
        if (!groundState.isOpaque()) {
            return false;
        }
        
        // Check if ground is a safe surface block (dirt, grass, path, etc.)
        // Avoid spawning on stone, wood planks, or other building materials
        net.minecraft.block.Block groundBlock = groundState.getBlock();
        
        // Safe ground blocks: dirt variants, grass, path, sand, gravel, etc.
        // Unsafe: stone, wood planks, bricks, concrete, etc. (building materials)
        boolean isSafeGround = groundBlock == net.minecraft.block.Blocks.DIRT ||
            groundBlock == net.minecraft.block.Blocks.GRASS_BLOCK ||
            groundBlock == net.minecraft.block.Blocks.PODZOL ||
            groundBlock == net.minecraft.block.Blocks.COARSE_DIRT ||
            groundBlock == net.minecraft.block.Blocks.DIRT_PATH ||
            groundBlock == net.minecraft.block.Blocks.FARMLAND ||
            groundBlock == net.minecraft.block.Blocks.SAND ||
            groundBlock == net.minecraft.block.Blocks.RED_SAND ||
            groundBlock == net.minecraft.block.Blocks.GRAVEL ||
            groundBlock == net.minecraft.block.Blocks.CLAY ||
            groundBlock == net.minecraft.block.Blocks.MYCELIUM ||
            groundBlock == net.minecraft.block.Blocks.SNOW_BLOCK ||
            groundBlock == net.minecraft.block.Blocks.SOUL_SAND ||
            groundBlock == net.minecraft.block.Blocks.SOUL_SOIL;
        
        if (!isSafeGround) {
            return false; // Not a safe ground surface
        }
        
        // Check if position and space above are air
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        
        // Check if there are no entities blocking the position
        Box entityBox = new Box(pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        if (!world.getEntitiesByType(EntityType.WANDERING_TRADER, entityBox, e -> true).isEmpty()) {
            return false; // Another trader is here
        }
        
        // Avoid spawning in water/lava
        if (!world.getBlockState(pos).getFluidState().isEmpty() ||
            !world.getBlockState(pos.up()).getFluidState().isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if there is at least one trading hut built in any settlement.
     * @param settlements All settlements in the world
     * @return true if at least one trading hut exists
     */
    private static boolean hasTradingHut(Collection<Settlement> settlements) {
        for (Settlement settlement : settlements) {
            for (Building building : settlement.getBuildings()) {
                if (isTraderHut(building)) {
                    return true; // Found at least one trading hut
                }
            }
        }
        return false; // No trading huts found
    }
    
    /**
     * Checks if a building is a trader hut.
     * @param building The building to check
     * @return true if the building is a trader hut
     */
    private static boolean isTraderHut(Building building) {
        if (building == null || building.getStructureType() == null) {
            return false;
        }
        
        String structurePath = building.getStructureType().getPath();
        // Check if structure name contains "trader_hut" or "traderhut"
        return structurePath.contains("trader_hut") || structurePath.contains("traderhut");
    }
}

