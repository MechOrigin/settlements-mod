package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * System that periodically attempts to spawn wandering traders near active town halls.
 * This is a more reliable approach than relying solely on mixins.
 */
public class WanderingTraderSpawnSystem {
    private static final int SPAWN_CHECK_INTERVAL = 600; // Check every 30 seconds (30 seconds)
    private static final double BASE_SPAWN_CHANCE = 0.15; // 25% chance per check when town hall is active
    private static final int MIN_SPAWN_INTERVAL = 2400; // Minimum 2 minutes between spawns per town hall
    
    private static final Map<UUID, Long> lastSpawnTimeByTownHall = new HashMap<>();
    
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getServer().getTicks() % SPAWN_CHECK_INTERVAL == 0) {
                checkAndSpawnTraders(world);
            }
        });
        
        SettlementsMod.LOGGER.info("WanderingTraderSpawnSystem registered - will check for trader spawns every {} ticks ({} seconds)", 
            SPAWN_CHECK_INTERVAL, SPAWN_CHECK_INTERVAL / 20);
    }
    
    private static void checkAndSpawnTraders(ServerWorld world) {
        if (world.getPlayers().isEmpty()) {
            return; // No players, don't spawn
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> settlements = manager.getAllSettlements();
        
        // Check if there is at least one trading hut built
        if (!hasTradingHut(settlements)) {
            SettlementsMod.LOGGER.debug("No trading huts found - wandering traders will not spawn");
            return; // No trading huts, don't spawn traders
        }
        
        long currentTime = world.getTime();
        List<Building> activeTownHalls = new ArrayList<>();
        
        // Find all active town halls
        for (Settlement settlement : settlements) {
            for (Building building : settlement.getBuildings()) {
                if (TownHallDetector.isTownHall(building)) {
                    TownHallData hallData = TownHallData.getOrCreate(building);
                    if (hallData.hasLibrarian()) {
                        // Check if enough time has passed since last spawn
                        UUID buildingId = building.getId();
                        Long lastSpawn = lastSpawnTimeByTownHall.get(buildingId);
                        if (lastSpawn == null || (currentTime - lastSpawn) >= MIN_SPAWN_INTERVAL) {
                            activeTownHalls.add(building);
                        }
                    }
                }
            }
        }
        
        if (activeTownHalls.isEmpty()) {
            SettlementsMod.LOGGER.debug("No active town halls ready for wandering trader spawn (all on cooldown or no librarians)");
            return; // No active town halls ready to spawn
        }
        
        SettlementsMod.LOGGER.info("Checking {} active town halls for wandering trader spawn (chance: {}%)", 
            activeTownHalls.size(), (int)(BASE_SPAWN_CHANCE * 100));
        
        // Try to spawn near each active town hall
        for (Building townHall : activeTownHalls) {
            // Check spawn chance
            if (world.getRandom().nextDouble() < BASE_SPAWN_CHANCE) {
                BlockPos spawnPos = findSpawnPositionNearTownHall(world, townHall.getPosition());
                if (spawnPos != null) {
                    try {
                        WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world);
                        if (trader == null) {
                            continue;
                        }
                        
                        trader.refreshPositionAndAngles(
                            spawnPos.getX() + 0.5,
                            spawnPos.getY(),
                            spawnPos.getZ() + 0.5,
                            world.getRandom().nextFloat() * 360.0f,
                            0.0f
                        );
                        
                        if (world.spawnEntity(trader)) {
                            // Record spawn time
                            lastSpawnTimeByTownHall.put(townHall.getId(), currentTime);
                            
                            // Record for despawn tracking
                            WanderingTraderDespawnHandler.recordTraderSpawn(trader);
                            
                            SettlementsMod.LOGGER.info("Spawned wandering trader {} near town hall {} at {} (tick {})",
                                trader.getUuid(), townHall.getId(), spawnPos, currentTime);
                            break; // Only spawn one per check
                        }
                    } catch (Exception e) {
                        SettlementsMod.LOGGER.error("Failed to spawn wandering trader near town hall {}: {}",
                            townHall.getId(), e.getMessage(), e);
                    }
                } else {
                    SettlementsMod.LOGGER.debug("Could not find valid spawn position near town hall {}", townHall.getId());
                }
            }
        }
    }
    
    /**
     * Finds a safe spawn position near a town hall.
     */
    private static BlockPos findSpawnPositionNearTownHall(ServerWorld world, BlockPos townHallPos) {
        int searchRadius = 48; // Within 48 blocks
        int attempts = 30;
        
        // Try random positions first
        for (int i = 0; i < attempts; i++) {
            int offsetX = world.getRandom().nextInt(searchRadius * 2) - searchRadius;
            int offsetZ = world.getRandom().nextInt(searchRadius * 2) - searchRadius;
            
            BlockPos testPos = townHallPos.add(offsetX, 0, offsetZ);
            BlockPos groundPos = findGroundLevel(world, testPos);
            
            if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                return groundPos;
            }
        }
        
        // Fallback: systematic search in circles
        for (int radius = 5; radius <= searchRadius; radius += 5) {
            for (int angle = 0; angle < 360; angle += 30) {
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
        
        // Must have air at spawn position and above
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        
        // Check for existing traders nearby (within 32 blocks)
        Box searchBox = new Box(pos).expand(32.0);
        if (!world.getEntitiesByType(EntityType.WANDERING_TRADER, searchBox, e -> true).isEmpty()) {
            return false; // Trader already nearby
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

