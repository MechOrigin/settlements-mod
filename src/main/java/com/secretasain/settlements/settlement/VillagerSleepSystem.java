package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * System that makes assigned villagers return to the settlement and sleep in beds at night.
 * Respects vanilla villager sleep mechanics.
 */
public class VillagerSleepSystem {
    private static final int CHECK_INTERVAL_TICKS = 20; // Check every second
    private static final long NIGHT_START_TICK = 12000; // Dusk (6 PM)
    private static final long DAY_START_TICK = 0; // Dawn (6 AM)
    private static final double ARRIVAL_DISTANCE_SQ = 9.0; // Consider arrived when within 3 blocks of bed
    
    // Track which villagers are currently going to bed
    private static final Map<UUID, BedSleepState> villagerSleepStates = new HashMap<>();
    
    /**
     * Tracks a villager's sleep state.
     */
    private static class BedSleepState {
        BlockPos targetBedPos;
        long startTime;
        boolean isSleeping;
        
        BedSleepState(BlockPos bedPos) {
            this.targetBedPos = bedPos;
            this.startTime = System.currentTimeMillis();
            this.isSleeping = false;
        }
    }
    
    /**
     * Registers the villager sleep system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_INTERVAL_TICKS == 0) {
                tick(world);
            }
        });
        
        SettlementsMod.LOGGER.info("VillagerSleepSystem registered - will check for villager sleep every {} ticks", CHECK_INTERVAL_TICKS);
    }
    
    /**
     * Performs a tick update for the given world.
     */
    private static void tick(ServerWorld world) {
        long timeOfDay = world.getTimeOfDay() % 24000;
        boolean isNighttime = timeOfDay >= NIGHT_START_TICK || timeOfDay < DAY_START_TICK;
        
        if (!isNighttime) {
            // It's daytime - wake up any sleeping villagers and clear sleep states
            wakeUpVillagers(world);
            return;
        }
        
        // It's nighttime - make assigned villagers go to bed
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        for (Settlement settlement : allSettlements) {
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Wakes up all sleeping villagers when it becomes daytime.
     */
    private static void wakeUpVillagers(ServerWorld world) {
        List<UUID> toRemove = new ArrayList<>();
        
        for (Map.Entry<UUID, BedSleepState> entry : villagerSleepStates.entrySet()) {
            UUID villagerId = entry.getKey();
            BedSleepState state = entry.getValue();
            
            VillagerEntity villager = getVillagerEntity(world, villagerId);
            if (villager == null || villager.isRemoved()) {
                toRemove.add(villagerId);
                continue;
            }
            
            if (state.isSleeping) {
                // Wake up the villager
                villager.wakeUp();
                SettlementsMod.LOGGER.debug("Woke up villager {} at dawn", villagerId);
            }
            
            toRemove.add(villagerId);
        }
        
        for (UUID id : toRemove) {
            villagerSleepStates.remove(id);
        }
    }
    
    /**
     * Processes sleep behavior for all assigned villagers in a settlement.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        BlockPos settlementCenter = settlement.getLecternPos();
        if (settlementCenter == null) {
            return;
        }
        
        // Find all beds in the settlement
        List<BlockPos> availableBeds = findBedsInSettlement(world, settlement, settlementCenter);
        
        if (availableBeds.isEmpty()) {
            SettlementsMod.LOGGER.debug("No beds found in settlement {} - villagers cannot sleep", settlement.getName());
            return;
        }
        
        // Process each assigned villager
        for (VillagerData villagerData : settlement.getVillagers()) {
            // Only process employed and assigned villagers
            if (!villagerData.isEmployed() || !villagerData.isAssigned()) {
                continue;
            }
            
            UUID villagerId = villagerData.getEntityId();
            VillagerEntity villager = getVillagerEntity(world, villagerId);
            if (villager == null || villager.isRemoved()) {
                continue;
            }
            
            // Check if villager is already sleeping
            if (villager.isSleeping()) {
                BedSleepState state = villagerSleepStates.get(villagerId);
                if (state != null) {
                    state.isSleeping = true;
                }
                continue;
            }
            
            // Check if villager is already going to bed
            BedSleepState sleepState = villagerSleepStates.get(villagerId);
            if (sleepState != null) {
                // Villager is already going to bed - check if they've arrived
                handleGoingToBed(villager, sleepState, world, settlementCenter);
                continue;
            }
            
            // Find the closest available bed
            BlockPos closestBed = findClosestBed(villager, availableBeds, world);
            if (closestBed == null) {
                continue; // No available bed found
            }
            
            // Start pathfinding to bed
            sleepState = new BedSleepState(closestBed);
            villagerSleepStates.put(villagerId, sleepState);
            
            // Make villager pathfind to bed
            makeVillagerGoToBed(villager, closestBed, world);
            
            SettlementsMod.LOGGER.debug("Villager {} is going to bed at {}", villagerId, closestBed);
        }
    }
    
    /**
     * Handles a villager that is currently going to bed.
     */
    private static void handleGoingToBed(VillagerEntity villager, BedSleepState state, ServerWorld world, BlockPos settlementCenter) {
        BlockPos bedPos = state.targetBedPos;
        
        // Check if villager has arrived at bed
        double distanceSq = villager.getPos().squaredDistanceTo(
            bedPos.getX() + 0.5,
            bedPos.getY() + 0.5,
            bedPos.getZ() + 0.5
        );
        
        if (distanceSq <= ARRIVAL_DISTANCE_SQ) {
            // Villager has arrived - try to sleep
            if (trySleepInBed(villager, bedPos, world)) {
                state.isSleeping = true;
                SettlementsMod.LOGGER.debug("Villager {} is now sleeping in bed at {}", villager.getUuid(), bedPos);
            } else {
                // Bed might be occupied or invalid - clear state and try again next tick
                villagerSleepStates.remove(villager.getUuid());
                SettlementsMod.LOGGER.debug("Villager {} could not sleep in bed at {} - will try again", villager.getUuid(), bedPos);
            }
        } else {
            // Still pathfinding - check if villager is stuck (hasn't moved in 10 seconds)
            long timeSinceStart = System.currentTimeMillis() - state.startTime;
            if (timeSinceStart > 10000) {
                // Villager might be stuck - try to find a different bed or clear state
                BlockPos currentPos = villager.getBlockPos();
                double currentDistanceSq = currentPos.getSquaredDistance(bedPos);
                
                // If still far away after 10 seconds, might be stuck
                if (currentDistanceSq > ARRIVAL_DISTANCE_SQ * 4) {
                    SettlementsMod.LOGGER.debug("Villager {} appears stuck going to bed - clearing state", villager.getUuid());
                    villagerSleepStates.remove(villager.getUuid());
                }
            }
        }
    }
    
    /**
     * Makes a villager pathfind to a bed.
     */
    private static void makeVillagerGoToBed(VillagerEntity villager, BlockPos bedPos, ServerWorld world) {
        // Use villager's navigation to pathfind to bed
        // The bed position should be the block above the bed (where the villager stands)
        BlockPos targetPos = bedPos.up();
        
        // Set villager's navigation target
        villager.getNavigation().startMovingTo(
            targetPos.getX() + 0.5,
            targetPos.getY(),
            targetPos.getZ() + 0.5,
            1.0 // Speed: normal walking speed
        );
        
        SettlementsMod.LOGGER.debug("Villager {} pathfinding to bed at {}", villager.getUuid(), bedPos);
    }
    
    /**
     * Attempts to make a villager sleep in a bed.
     */
    private static boolean trySleepInBed(VillagerEntity villager, BlockPos bedPos, ServerWorld world) {
        BlockState bedState = world.getBlockState(bedPos);
        
        // Check if it's actually a bed
        if (!(bedState.getBlock() instanceof BedBlock)) {
            return false;
        }
        
        // Check if bed is already occupied
        if (bedState.get(BedBlock.OCCUPIED)) {
            return false;
        }
        
        // Check if bed has space above (villagers need 2 blocks of air above bed)
        BlockPos aboveBed = bedPos.up();
        if (!world.getBlockState(aboveBed).isAir() || !world.getBlockState(aboveBed.up()).isAir()) {
            return false;
        }
        
        // Try to make villager sleep
        // Use the bed's sleep position (usually the bed block itself)
        BlockPos sleepPos = bedPos;
        
        // Set villager's position near bed
        villager.refreshPositionAndAngles(
            sleepPos.getX() + 0.5,
            sleepPos.getY() + 0.5,
            sleepPos.getZ() + 0.5,
            villager.getYaw(),
            villager.getPitch()
        );
        
        // Try to make villager sleep by interacting with the bed
        // In Minecraft, villagers sleep by right-clicking the bed
        try {
            if (bedState.getBlock() instanceof BedBlock) {
                // Create a block hit result for the bed interaction
                net.minecraft.util.hit.BlockHitResult hitResult = new net.minecraft.util.hit.BlockHitResult(
                    villager.getPos(),
                    net.minecraft.util.math.Direction.UP,
                    bedPos,
                    false
                );
                
                // Try to use the bed (this will make the villager sleep if successful)
                net.minecraft.util.ActionResult result = bedState.onUse(
                    world,
                    null, // No player - villager is using it
                    net.minecraft.util.Hand.MAIN_HAND,
                    hitResult
                );
                
                if (result == net.minecraft.util.ActionResult.SUCCESS || result == net.minecraft.util.ActionResult.CONSUME) {
                    return true;
                }
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error making villager sleep: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Finds all beds in a settlement.
     */
    private static List<BlockPos> findBedsInSettlement(ServerWorld world, Settlement settlement, BlockPos center) {
        List<BlockPos> beds = new ArrayList<>();
        
        // Scan for beds in completed buildings
        for (Building building : settlement.getBuildings()) {
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Search for beds in the building area
            BlockPos buildingPos = building.getPosition();
            
            // Simple search: check a reasonable area around the building
            // In a real implementation, you might want to load the structure and check actual block positions
            for (int x = -5; x <= 5; x++) {
                for (int y = -2; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        BlockPos checkPos = buildingPos.add(x, y, z);
                        
                        if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                            continue;
                        }
                        
                        BlockState state = world.getBlockState(checkPos);
                        if (state.getBlock() instanceof BedBlock) {
                            // Check if bed is not occupied
                            if (!state.get(BedBlock.OCCUPIED)) {
                                // Check if bed has space above
                                BlockPos above = checkPos.up();
                                if (world.getBlockState(above).isAir() && world.getBlockState(above.up()).isAir()) {
                                    beds.add(checkPos);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Also search general area around settlement center for beds
        int radius = settlement.getRadius();
        for (int x = -radius; x <= radius; x += 4) {
            for (int z = -radius; z <= radius; z += 4) {
                for (int y = -5; y <= 10; y++) {
                    BlockPos checkPos = center.add(x, y, z);
                    
                    if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    BlockState state = world.getBlockState(checkPos);
                    if (state.getBlock() instanceof BedBlock) {
                        if (!state.get(BedBlock.OCCUPIED)) {
                            BlockPos above = checkPos.up();
                            if (world.getBlockState(above).isAir() && world.getBlockState(above.up()).isAir()) {
                                beds.add(checkPos);
                            }
                        }
                    }
                }
            }
        }
        
        return beds;
    }
    
    /**
     * Finds the closest available bed to a villager.
     */
    private static BlockPos findClosestBed(VillagerEntity villager, List<BlockPos> beds, ServerWorld world) {
        if (beds.isEmpty()) {
            return null;
        }
        
        BlockPos villagerPos = villager.getBlockPos();
        BlockPos closestBed = null;
        double closestDistanceSq = Double.MAX_VALUE;
        
        for (BlockPos bedPos : beds) {
            // Check if bed is still available (not occupied)
            if (!world.getChunkManager().isChunkLoaded(bedPos.getX() >> 4, bedPos.getZ() >> 4)) {
                continue;
            }
            
            BlockState bedState = world.getBlockState(bedPos);
            if (!(bedState.getBlock() instanceof BedBlock) || bedState.get(BedBlock.OCCUPIED)) {
                continue;
            }
            
            double distanceSq = villagerPos.getSquaredDistance(bedPos);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closestBed = bedPos;
            }
        }
        
        return closestBed;
    }
    
    /**
     * Gets the VillagerEntity from the world by UUID.
     */
    private static VillagerEntity getVillagerEntity(ServerWorld world, UUID entityId) {
        if (entityId == null) {
            return null;
        }
        try {
            return (VillagerEntity) world.getEntity(entityId);
        } catch (Exception e) {
            return null;
        }
    }
}

