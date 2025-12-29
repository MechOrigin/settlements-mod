package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
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
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.util.*;

/**
 * System that periodically attempts to spawn villagers near town halls WITHOUT librarian requirement.
 * Uses the same 15% chance system as wandering traders.
 * Villagers spawned this way will be tracked and may despawn with enderman teleport effect.
 */
public class TownHallVillagerAttractionSystem {
    private static final int SPAWN_CHECK_INTERVAL = 60; // Check every 3 seconds (60 ticks = 3 seconds)
    private static final double BASE_SPAWN_CHANCE = 0.15; // 15% chance per check (same as wandering traders)
    private static final int MIN_SPAWN_INTERVAL = 2400; // Minimum 2 minutes between spawns per town hall
    private static final int MAX_ATTRACTED_VILLAGERS = 5; // Maximum number of attracted villagers per town hall
    
    private static final Map<UUID, Long> lastSpawnTimeByTownHall = new HashMap<>();
    
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getServer().getTicks() % SPAWN_CHECK_INTERVAL == 0) {
                checkAndSpawnVillagers(world);
            }
        });
        
        SettlementsMod.LOGGER.info("TownHallVillagerAttractionSystem registered - will check for villager spawns every {} ticks ({} seconds)", 
            SPAWN_CHECK_INTERVAL, SPAWN_CHECK_INTERVAL / 20);
    }
    
    private static void checkAndSpawnVillagers(ServerWorld world) {
        if (world.getPlayers().isEmpty()) {
            return; // No players, don't spawn
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> settlements = manager.getAllSettlements();
        
        long currentTime = world.getTime();
        List<Building> activeTownHalls = new ArrayList<>();
        
        // Find all COMPLETED town halls WITHOUT librarian requirement
        for (Settlement settlement : settlements) {
            for (Building building : settlement.getBuildings()) {
                if (TownHallDetector.isTownHall(building)) {
                    // CRITICAL: Only attract villagers to COMPLETED town halls
                    // Do not attract during RESERVED, IN_PROGRESS, or any other status
                    if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                        continue; // Skip non-completed town halls
                    }
                    
                    TownHallData hallData = TownHallData.getOrCreate(building);
                    
                    // Check if town hall has NO librarian (this is the key difference from TownHallVillagerSpawner)
                    // We want to attract villagers to town halls that don't have librarians yet
                    if (!hallData.hasLibrarian()) {
                        // Count currently attracted villagers (not spawned by librarian system)
                        int attractedVillagerCount = countAttractedVillagers(world, building, hallData);
                        
                        // Check if we're under the max (5 villagers max, but aim for 2-3 visible)
                        if (attractedVillagerCount < MAX_ATTRACTED_VILLAGERS) {
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
        }
        
        if (activeTownHalls.isEmpty()) {
            return; // No active town halls ready to spawn
        }
        
        SettlementsMod.LOGGER.debug("Checking {} town halls (without librarians) for villager attraction (chance: {}%, max: {})", 
            activeTownHalls.size(), (int)(BASE_SPAWN_CHANCE * 100), MAX_ATTRACTED_VILLAGERS);
        
        // Try to spawn near each active town hall
        for (Building townHall : activeTownHalls) {
            // Check spawn chance (15% same as wandering traders)
            if (world.getRandom().nextDouble() < BASE_SPAWN_CHANCE) {
                BlockPos spawnPos = findSpawnPositionNearTownHall(world, townHall.getPosition());
                if (spawnPos != null) {
                    try {
                        VillagerEntity villager = EntityType.VILLAGER.create(world);
                        if (villager == null) {
                            continue;
                        }
                        
                        villager.refreshPositionAndAngles(
                            spawnPos.getX() + 0.5,
                            spawnPos.getY(),
                            spawnPos.getZ() + 0.5,
                            world.getRandom().nextFloat() * 360.0f,
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
                        
                        if (world.spawnEntity(villager)) {
                            // Record spawn time
                            lastSpawnTimeByTownHall.put(townHall.getId(), currentTime);
                            
                            // Record for despawn tracking (50/50 chance to stay or leave)
                            TownHallVillagerDespawnHandler.recordAttractedVillager(villager, townHall.getId());
                            
                            SettlementsMod.LOGGER.info("Spawned attracted villager {} near town hall {} at {} (tick {})",
                                villager.getUuid(), townHall.getId(), spawnPos, currentTime);
                            break; // Only spawn one per check
                        }
                    } catch (Exception e) {
                        SettlementsMod.LOGGER.error("Failed to spawn attracted villager near town hall {}: {}",
                            townHall.getId(), e.getMessage(), e);
                    }
                } else {
                    SettlementsMod.LOGGER.debug("Could not find valid spawn position near town hall {}", townHall.getId());
                }
            }
        }
    }
    
    /**
     * Counts the number of villagers currently attracted to this town hall.
     * Only counts villagers tracked by the attraction system (not librarian-spawned villagers).
     */
    private static int countAttractedVillagers(ServerWorld world, Building townHall, TownHallData hallData) {
        // Count villagers within a reasonable radius of the town hall that were attracted by this system
        BlockPos townHallPos = townHall.getPosition();
        Box searchBox = new Box(townHallPos).expand(64.0); // 64 block radius
        
        List<VillagerEntity> nearbyVillagers = world.getEntitiesByType(
            EntityType.VILLAGER,
            searchBox,
            villager -> {
                // Check if villager is within reasonable distance
                double distanceSq = villager.getBlockPos().getSquaredDistance(townHallPos);
                return distanceSq <= 64 * 64; // Within 64 blocks
            }
        );
        
        // Count only villagers tracked by the despawn handler (attracted villagers)
        int count = 0;
        for (VillagerEntity villager : nearbyVillagers) {
            if (TownHallVillagerDespawnHandler.isAttractedVillager(villager.getUuid(), townHall.getId())) {
                count++;
            }
        }
        
        return count;
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
     */
    private static BlockPos findGroundLevel(ServerWorld world, BlockPos pos) {
        int startY = Math.min(pos.getY() + 2, world.getTopY() - 1);
        int minY = world.getBottomY();
        
        // ALWAYS search downward first to avoid spawning on top of buildings
        for (int y = startY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockPos groundPos = testPos.down();
            
            if (world.getBlockState(groundPos).isOpaque() &&
                world.getBlockState(testPos).isAir() &&
                world.getBlockState(testPos.up()).isAir()) {
                return testPos;
            }
        }
        
        // If no ground found below, try searching up (only as last resort)
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
     * Checks if position is safe for spawning a villager.
     */
    private static boolean isSafeSpawnPosition(ServerWorld world, BlockPos pos) {
        net.minecraft.block.BlockState groundState = world.getBlockState(pos.down());
        
        // Check if position has solid ground
        if (!groundState.isOpaque()) {
            return false;
        }
        
        // Check if ground is a safe surface block (dirt, grass, path, etc.)
        net.minecraft.block.Block groundBlock = groundState.getBlock();
        
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
            return false;
        }
        
        // Must have air at spawn position and above
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        
        // Check for existing villagers nearby (within 16 blocks)
        Box searchBox = new Box(pos).expand(16.0);
        if (!world.getEntitiesByType(EntityType.VILLAGER, searchBox, e -> true).isEmpty()) {
            return false; // Villager already nearby
        }
        
        // Avoid spawning in water/lava
        if (!world.getBlockState(pos).getFluidState().isEmpty() ||
            !world.getBlockState(pos.up()).getFluidState().isEmpty()) {
            return false;
        }
        
        return true;
    }
}

