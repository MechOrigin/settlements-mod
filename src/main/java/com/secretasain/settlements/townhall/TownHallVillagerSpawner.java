package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.util.*;

/**
 * System for spawning villagers from town hall buildings.
 * Spawns villagers periodically when a librarian is assigned.
 */
public class TownHallVillagerSpawner {
    private static final int CHECK_INTERVAL = 100; // Check every 5 seconds (100 ticks) - kept at 100 for frequent checks
    private static final int STATUS_LOG_INTERVAL = 1200; // Log status every 60 seconds (1200 ticks)
    
    private final Map<ServerWorld, WorldSpawnData> worldData = new HashMap<>();
    
    /**
     * Registers the villager spawner system with Fabric's server tick events.
     */
    public static void register() {
        TownHallVillagerSpawner system = new TownHallVillagerSpawner();
        
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            system.tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private void tick(ServerWorld world) {
        WorldSpawnData data = worldData.computeIfAbsent(world, w -> new WorldSpawnData());
        data.tick(world);
    }
    
    /**
     * Per-world spawning data and state.
     */
    private static class WorldSpawnData {
        private int tickCounter = 0;
        private int statusLogCounter = 0;
        
        /**
         * Performs a tick update.
         * @param world The server world
         */
        void tick(ServerWorld world) {
            tickCounter++;
            statusLogCounter++;
            
            // Log status periodically
            if (statusLogCounter >= STATUS_LOG_INTERVAL) {
                statusLogCounter = 0;
                logTownHallStatus(world);
            }
            
            // Only check every CHECK_INTERVAL ticks to avoid performance issues
            if (tickCounter < CHECK_INTERVAL) {
                return;
            }
            
            tickCounter = 0;
            
            // Get all settlements in this world
            SettlementManager manager = SettlementManager.getInstance(world);
            Collection<Settlement> settlements = manager.getAllSettlements();
            
            // Check each settlement for town halls
            for (Settlement settlement : settlements) {
                checkTownHalls(world, settlement);
            }
        }
        
        /**
         * Logs the status of all town halls for debugging.
         * @param world The server world
         */
        private void logTownHallStatus(ServerWorld world) {
            SettlementManager manager = SettlementManager.getInstance(world);
            Collection<Settlement> settlements = manager.getAllSettlements();
            
            int totalTownHalls = 0;
            int activeTownHalls = 0;
            
            for (Settlement settlement : settlements) {
                for (Building building : settlement.getBuildings()) {
                    if (TownHallDetector.isTownHall(building)) {
                        totalTownHalls++;
                        TownHallData hallData = TownHallData.getOrCreate(building);
                        if (hallData.hasLibrarian()) {
                            activeTownHalls++;
                            long currentTime = world.getTime();
                            long timeSinceLastSpawn = currentTime - hallData.getLastVillagerSpawnTime();
                            int livingSpawnedVillagers = countLivingSpawnedVillagers(world, hallData);
                            
                            SettlementsMod.LOGGER.info("Town Hall Status - Building: {}, Settlement: {}, Librarian: {}, " +
                                "Spawned: {}/{}, Living Spawned Villagers: {}, Last Spawn: {} ticks ago, Can Spawn: {}",
                                building.getId(), settlement.getName(),
                                hallData.getAssignedLibrarianId() != null ? "Yes" : "No",
                                hallData.getCurrentSpawnedVillagers(), hallData.getVillagerSpawnCap(),
                                livingSpawnedVillagers,
                                timeSinceLastSpawn,
                                hallData.canSpawnVillager(currentTime) && livingSpawnedVillagers < hallData.getVillagerSpawnCap());
                        }
                    }
                }
            }
            
            if (totalTownHalls > 0) {
                SettlementsMod.LOGGER.info("Town Hall Summary: {} total town halls, {} active (with librarians), {} inactive",
                    totalTownHalls, activeTownHalls, totalTownHalls - activeTownHalls);
            }
        }
        
        /**
         * Checks town halls in a settlement for villager spawning.
         * @param world The server world
         * @param settlement The settlement to check
         */
        private void checkTownHalls(ServerWorld world, Settlement settlement) {
            long currentTime = world.getTime();
            
            // Find all town hall buildings in the settlement
            for (Building building : settlement.getBuildings()) {
                if (!TownHallDetector.isTownHall(building)) {
                    continue;
                }
                
                // Get town hall data
                TownHallData hallData = TownHallData.getOrCreate(building);
                
                SettlementsMod.LOGGER.debug("Checking town hall {}: hasLibrarian={}, spawnCap={}/{}, lastSpawn={}, currentTime={}, canSpawn={}",
                    building.getId(),
                    hallData.hasLibrarian(),
                    hallData.getCurrentSpawnedVillagers(),
                    hallData.getVillagerSpawnCap(),
                    hallData.getLastVillagerSpawnTime(),
                    currentTime,
                    hallData.canSpawnVillager(currentTime));
                
                // Check if town hall has assigned librarian
                if (!hallData.hasLibrarian()) {
                    SettlementsMod.LOGGER.debug("Town hall {} has no librarian assigned, skipping spawn check", building.getId());
                    continue; // No librarian, can't spawn
                }
                
                // Check if spawn cap has been reached by counting only villagers spawned by this town hall
                // Clean up dead/despawned villagers from the tracking list first
                cleanupDeadVillagers(world, hallData);
                
                // Check if we've spawned the cap amount
                int livingSpawnedVillagers = countLivingSpawnedVillagers(world, hallData);
                if (livingSpawnedVillagers >= hallData.getVillagerSpawnCap()) {
                    SettlementsMod.LOGGER.debug("Town hall {} has reached spawn cap ({} living spawned villagers, cap: {}), skipping spawn check",
                        building.getId(), livingSpawnedVillagers, hallData.getVillagerSpawnCap());
                    continue; // Cap reached, can't spawn more
                }
                
                // Check if enough time has passed since last spawn
                if (!hallData.canSpawnVillager(currentTime)) {
                    long timeSinceLastSpawn = currentTime - hallData.getLastVillagerSpawnTime();
                    long timeNeeded = hallData.getVillagerSpawnInterval();
                    SettlementsMod.LOGGER.debug("Town hall {} cannot spawn yet: {} ticks since last spawn, need {} ticks",
                        building.getId(), timeSinceLastSpawn, timeNeeded);
                    continue; // Not enough time has passed
                }
                
                SettlementsMod.LOGGER.info("Town hall {} attempting to spawn villager (librarian assigned, cap not reached, cooldown passed)",
                    building.getId());
                
                // Attempt to spawn a villager
                VillagerEntity spawnedVillager = spawnVillager(world, settlement, building, hallData);
                if (spawnedVillager != null) {
                    // Update spawn time and track the spawned villager
                    hallData.setLastVillagerSpawnTime(currentTime);
                    hallData.addSpawnedVillager(spawnedVillager.getUuid());
                    hallData.saveToBuilding(building);
                    
                    SettlementsMod.LOGGER.info("Successfully spawned villager from town hall {} in settlement {} (spawned: {}/{})",
                        building.getId(), settlement.getName(),
                        hallData.getCurrentSpawnedVillagers(), hallData.getVillagerSpawnCap());
                } else {
                    SettlementsMod.LOGGER.warn("Failed to spawn villager from town hall {} - spawn position or entity creation failed",
                        building.getId());
                }
            }
        }
        
        /**
         * Attempts to spawn a villager near a town hall building.
         * @param world The server world
         * @param settlement The settlement
         * @param building The town hall building
         * @param hallData The town hall data
         * @return VillagerEntity if spawned successfully, null otherwise
         */
        private VillagerEntity spawnVillager(ServerWorld world, Settlement settlement,
                                     Building building, TownHallData hallData) {
            // Calculate spawn position near town hall
            BlockPos spawnPos = calculateSpawnPosition(world, building);
            if (spawnPos == null) {
                SettlementsMod.LOGGER.warn("Could not find valid spawn position for town hall {}", building.getId());
                return null;
            }
            
            // Check if position is safe (not in walls, has air above)
            if (!isSafeSpawnPosition(world, spawnPos)) {
                SettlementsMod.LOGGER.warn("Spawn position {} is not safe for town hall {} - checking block states", spawnPos, building.getId());
                // Log why it's not safe for debugging
                if (!world.getBlockState(spawnPos.down()).isOpaque()) {
                    SettlementsMod.LOGGER.warn("  - Ground below is not opaque: {}", world.getBlockState(spawnPos.down()).getBlock());
                }
                if (!world.getBlockState(spawnPos).isAir()) {
                    SettlementsMod.LOGGER.warn("  - Position is not air: {}", world.getBlockState(spawnPos).getBlock());
                }
                if (!world.getBlockState(spawnPos.up()).isAir()) {
                    SettlementsMod.LOGGER.warn("  - Position above is not air: {}", world.getBlockState(spawnPos.up()).getBlock());
                }
                return null;
            }
            
            // Create and spawn villager
            try {
                VillagerEntity villager = EntityType.VILLAGER.create(world);
                if (villager == null) {
                    SettlementsMod.LOGGER.error("Failed to create villager entity");
                    return null;
                }
                
                // Set villager properties
                villager.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    0.0f,
                    0.0f
                );
                
                // Set villager to adult (not baby)
                villager.setBreedingAge(0);
                
                // Set profession to NONE (jobless villager)
                VillagerType villagerType = Registries.VILLAGER_TYPE.getRandom(world.getRandom())
                    .map(ref -> ref.value())
                    .orElse(VillagerType.PLAINS);
                VillagerData villagerData = new VillagerData(
                    villagerType,
                    VillagerProfession.NONE,
                    1 // Level 1
                );
                villager.setVillagerData(villagerData);
                
                // Spawn the villager
                world.spawnEntity(villager);
                
                // The villager will be picked up by VillagerScanningSystem on next scan
                SettlementsMod.LOGGER.info("Spawned villager {} at {} for town hall {}",
                    villager.getUuid(), spawnPos, building.getId());
                
                return villager;
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Failed to spawn villager for town hall {}: {}", building.getId(), e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        
        /**
         * Calculates a spawn position near the town hall building.
         * @param world The server world
         * @param building The town hall building
         * @return Spawn position or null if none found
         */
        private BlockPos calculateSpawnPosition(ServerWorld world, Building building) {
            BlockPos buildingPos = building.getPosition();
            
            // Load structure data to get dimensions
            StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), world.getServer());
            Vec3i dimensions;
            if (structureData == null) {
                SettlementsMod.LOGGER.warn("Could not load structure data for town hall {}, using default dimensions", building.getId());
                dimensions = new Vec3i(10, 10, 10); // Default dimensions
            } else {
                dimensions = structureData.getDimensions();
            }
            
            // Try multiple search strategies
            // Strategy 1: Search in expanding circles around building
            int maxRadius = Math.max(dimensions.getX(), dimensions.getZ()) + 8;
            for (int radius = 3; radius <= maxRadius; radius++) {
                for (int angle = 0; angle < 360; angle += 15) { // Check every 15 degrees
                    double radians = Math.toRadians(angle);
                    int x = (int) (radius * Math.cos(radians));
                    int z = (int) (radius * Math.sin(radians));
                    
                    BlockPos testPos = buildingPos.add(x, 0, z);
                    BlockPos groundPos = findGroundLevel(world, testPos);
                    if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                        SettlementsMod.LOGGER.debug("Found spawn position at {} (radius: {}, angle: {})", groundPos, radius, angle);
                        return groundPos;
                    }
                }
            }
            
            // Strategy 2: Try cardinal directions
            for (int offset = 3; offset <= 10; offset++) {
                for (int[] dir : new int[][]{{offset, 0}, {-offset, 0}, {0, offset}, {0, -offset}}) {
                    BlockPos testPos = buildingPos.add(dir[0], 0, dir[1]);
                    BlockPos groundPos = findGroundLevel(world, testPos);
                    if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                        SettlementsMod.LOGGER.debug("Found spawn position at {} (cardinal direction, offset: {})", groundPos, offset);
                        return groundPos;
                    }
                }
            }
            
            // Strategy 3: Try diagonal directions
            for (int offset = 3; offset <= 10; offset++) {
                for (int[] dir : new int[][]{{offset, offset}, {-offset, offset}, {offset, -offset}, {-offset, -offset}}) {
                    BlockPos testPos = buildingPos.add(dir[0], 0, dir[1]);
                    BlockPos groundPos = findGroundLevel(world, testPos);
                    if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                        SettlementsMod.LOGGER.debug("Found spawn position at {} (diagonal, offset: {})", groundPos, offset);
                        return groundPos;
                    }
                }
            }
            
            // Strategy 4: Try positions at building Y level and search up/down
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    if (Math.abs(x) < 2 && Math.abs(z) < 2) continue; // Skip too close
                    BlockPos testPos = buildingPos.add(x, 0, z);
                    BlockPos groundPos = findGroundLevel(world, testPos);
                    if (groundPos != null && isSafeSpawnPosition(world, groundPos)) {
                        SettlementsMod.LOGGER.debug("Found spawn position at {} (grid search)", groundPos);
                        return groundPos;
                    }
                }
            }
            
            SettlementsMod.LOGGER.warn("Could not find any safe spawn position for town hall {} after exhaustive search", building.getId());
            return null;
        }
        
        /**
         * Finds the ground level at a given X/Z position.
         * Prioritizes searching downward to avoid spawning on top of buildings.
         * @param world The server world
         * @param pos The position (Y will be adjusted)
         * @return Ground position or null if not found
         */
        private BlockPos findGroundLevel(ServerWorld world, BlockPos pos) {
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
         * Checks if a position is safe for spawning a villager.
         * Ensures ground is a safe surface block (dirt, grass, path, etc.) and not on top of buildings.
         * @param world The server world
         * @param pos The position to check
         * @return true if position is safe
         */
        private boolean isSafeSpawnPosition(ServerWorld world, BlockPos pos) {
            net.minecraft.block.BlockState groundState = world.getBlockState(pos.down());
            
            // Check if position has solid ground
            if (!groundState.isOpaque()) {
                return false;
            }
            
            // Check if ground is a safe surface block (dirt, grass, path, etc.)
            // Avoid spawning on stone, wood planks, or other building materials
            net.minecraft.block.Block groundBlock = groundState.getBlock();
            net.minecraft.registry.Registries.BLOCK.getId(groundBlock);
            
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
            if (!world.getEntitiesByType(EntityType.VILLAGER, entityBox, e -> true).isEmpty()) {
                return false; // Another villager is here
            }
            
            // Avoid spawning in water or lava
            if (!world.getBlockState(pos).getFluidState().isEmpty() ||
                !world.getBlockState(pos.up()).getFluidState().isEmpty()) {
                return false;
            }
            
            return true;
        }
        
        /**
         * Counts the number of living villagers that were spawned by the town hall.
         * @param world The server world
         * @param hallData The town hall data
         * @return Number of living spawned villagers
         */
        private int countLivingSpawnedVillagers(ServerWorld world, TownHallData hallData) {
            int count = 0;
            List<UUID> spawnedIds = new ArrayList<>(hallData.getSpawnedVillagerIds());
            
            for (UUID villagerId : spawnedIds) {
                try {
                    net.minecraft.entity.Entity entity = world.getEntity(villagerId);
                    if (entity instanceof VillagerEntity villager && !villager.isRemoved() && villager.isAlive()) {
                        count++;
                    } else {
                        // Villager is dead/despawned, remove from tracking
                        hallData.removeSpawnedVillager(villagerId);
                    }
                } catch (Exception e) {
                    // Entity not found or error, remove from tracking
                    hallData.removeSpawnedVillager(villagerId);
                }
            }
            
            return count;
        }
        
        /**
         * Cleans up dead/despawned villagers from the tracking list.
         * @param world The server world
         * @param hallData The town hall data
         */
        private void cleanupDeadVillagers(ServerWorld world, TownHallData hallData) {
            List<UUID> spawnedIds = new ArrayList<>(hallData.getSpawnedVillagerIds());
            
            for (UUID villagerId : spawnedIds) {
                try {
                    net.minecraft.entity.Entity entity = world.getEntity(villagerId);
                    if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                        // Villager is dead/despawned, remove from tracking
                        hallData.removeSpawnedVillager(villagerId);
                    }
                } catch (Exception e) {
                    // Entity not found or error, remove from tracking
                    hallData.removeSpawnedVillager(villagerId);
                }
            }
        }
    }
}

