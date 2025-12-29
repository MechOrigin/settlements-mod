package com.secretasain.settlements.farm;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import com.secretasain.settlements.settlement.VillagerData;
import com.secretasain.settlements.settlement.WorkAssignmentManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * System for second farm villager to use composters to convert seeds to bone meal.
 * The second villager assigned to a farm will:
 * 1. Take seeds from lectern chests (leaving at least a stack of each type)
 * 2. Use seeds at composter to convert to bone meal
 * 3. Return bone meal to chests
 * 4. Update farm output data to reflect bone meal production
 */
public class FarmComposterSystem {
    private static final int CHECK_INTERVAL_TICKS = 20; // Check every 1 second (20 ticks) - more frequent for pathfinding
    private static final int SEED_RESERVE_COUNT = 64; // Always leave at least 64 seeds of each type
    private static final double CHEST_SEARCH_RADIUS = 8.0; // Search for chests within 8 blocks of lectern
    private static final double ARRIVAL_DISTANCE = 3.0; // Consider arrived when within 3 blocks
    private static final double ARRIVAL_DISTANCE_SQ = ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    private static final int MIN_CHEST_STAY_TICKS = 100; // Minimum 5 seconds (100 ticks) at chest before continuing
    
    // State tracking for composter tasks
    private static final Map<UUID, ComposterTaskState> TASK_STATES = new HashMap<>();
    
    /**
     * State for a villager's composter task.
     */
    private static class ComposterTaskState {
        UUID buildingId;
        BlockPos targetChestPos;
        BlockPos composterPos;
        ComposterTaskPhase phase;
        Map<Item, Integer> seedsCollected;
        int boneMealProduced;
        long chestArrivalTime; // World time when villager arrived at chest (0 if not at chest)
        
        ComposterTaskState(UUID buildingId) {
            this.buildingId = buildingId;
            this.phase = ComposterTaskPhase.IDLE;
            this.seedsCollected = new HashMap<>();
            this.boneMealProduced = 0;
            this.chestArrivalTime = 0;
        }
    }
    
    /**
     * Phases of the composter task.
     */
    private enum ComposterTaskPhase {
        IDLE,                    // Not doing anything
        GOING_TO_CHEST,          // Pathfinding to chest to get seeds
        AT_CHEST_GETTING_SEEDS,  // At chest, getting seeds
        GOING_TO_COMPOSTER,      // Pathfinding to composter
        AT_COMPOSTER,            // At composter, using seeds
        GOING_TO_CHEST_DEPOSIT,  // Pathfinding back to chest to deposit bone meal
        AT_CHEST_DEPOSITING      // At chest, depositing bone meal
    }
    
    /**
     * Checks if an item can be composted (works for both vanilla and modded items).
     * @param item The item to check
     * @return true if the item can be composted
     */
    private static boolean isCompostable(Item item) {
        float chance = ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(item);
        return chance > 0.0f;
    }
    
    /**
     * Registers the composter system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_INTERVAL_TICKS == 0) {
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
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Processes composter tasks for farm buildings with second villagers.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        for (Building building : settlement.getBuildings()) {
            if (!isFarmBuilding(building)) {
                continue;
            }
            
            // CRITICAL: Only process COMPLETED buildings
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue; // Skip non-completed buildings
            }
            
            // Get all villagers assigned to this farm
            List<VillagerData> assignedVillagers = WorkAssignmentManager.getVillagersAssignedToBuilding(
                settlement, building.getId()
            );
            
            // Check if there's a second villager (index 1)
            if (assignedVillagers.size() < 2) {
                continue; // Need at least 2 villagers
            }
            
            // Get the first and second villagers
            VillagerData firstVillagerData = assignedVillagers.get(0);
            VillagerData secondVillagerData = assignedVillagers.get(1);
            VillagerEntity villager = getVillagerEntity(world, secondVillagerData.getEntityId());
            
            if (villager == null) {
                continue;
            }
            
            // Skip if second villager is depositing (let deposit system handle it)
            if (secondVillagerData.isDepositing()) {
                continue;
            }
            
            // Check if first villager is depositing - if so, trigger second villager to go to chest
            if (firstVillagerData.isDepositing()) {
                // First villager is going to chest - second villager should follow
                ComposterTaskState state = TASK_STATES.computeIfAbsent(secondVillagerData.getEntityId(), 
                    id -> new ComposterTaskState(building.getId()));
                
                // Validate building still matches
                if (!state.buildingId.equals(building.getId())) {
                    TASK_STATES.remove(secondVillagerData.getEntityId());
                    state = new ComposterTaskState(building.getId());
                    TASK_STATES.put(secondVillagerData.getEntityId(), state);
                }
                
                // Only trigger if second villager is idle (not already doing a task)
                if (state.phase == ComposterTaskPhase.IDLE) {
                    // Find composter in farm structure (needed for later steps)
                    BlockPos composterPos = findComposterInFarm(building, world);
                    if (composterPos == null) {
                        SettlementsMod.LOGGER.debug("No composter found in farm {} - cannot start composter task", building.getId());
                        // Don't continue - still process the task in case state was already set
                    } else {
                        state.composterPos = composterPos;
                        
                        // Find chests near lectern (same location first villager is going to)
                        BlockPos lecternPos = settlement.getLecternPos();
                        List<BlockPos> chestPositions = findChestsNearLectern(lecternPos, world);
                        if (!chestPositions.isEmpty()) {
                            state.targetChestPos = chestPositions.get(0);
                            state.phase = ComposterTaskPhase.GOING_TO_CHEST;
                            
                            // Pathfind to chest
                            boolean pathStarted = villager.getNavigation().startMovingTo(
                                state.targetChestPos.getX() + 0.5,
                                state.targetChestPos.getY() + 0.5,
                                state.targetChestPos.getZ() + 0.5,
                                1.0 // Normal speed
                            );
                            
                            if (pathStarted) {
                                SettlementsMod.LOGGER.info("Second farm villager {} following first villager to chest at {}", 
                                    secondVillagerData.getEntityId(), state.targetChestPos);
                            } else {
                                SettlementsMod.LOGGER.warn("Second farm villager {} failed to start pathfinding to chest", 
                                    secondVillagerData.getEntityId());
                            }
                        } else {
                            SettlementsMod.LOGGER.debug("No chests found near lectern for second villager to follow first");
                        }
                    }
                } else {
                    SettlementsMod.LOGGER.debug("Second villager {} already in phase {}, not triggering follow", 
                        secondVillagerData.getEntityId(), state.phase);
                }
                // Continue processing the task (will handle chest interaction when arrived)
            }
            
            // Process composter task (handles pathfinding and state management)
            processComposterTask(settlement, building, secondVillagerData, villager, world);
        }
    }
    
    /**
     * Processes a composter task for the second farm villager.
     * Uses state machine to handle pathfinding and task execution.
     */
    private static void processComposterTask(Settlement settlement, Building building, 
                                            VillagerData villagerData, VillagerEntity villager,
                                            ServerWorld world) {
        UUID villagerId = villagerData.getEntityId();
        ComposterTaskState state = TASK_STATES.computeIfAbsent(villagerId, 
            id -> new ComposterTaskState(building.getId()));
        
        // Validate building still matches (in case villager was reassigned)
        if (!state.buildingId.equals(building.getId())) {
            // Building changed - reset task
            TASK_STATES.remove(villagerId);
            state = new ComposterTaskState(building.getId());
            TASK_STATES.put(villagerId, state);
        }
        
        // Initialize task if needed
        if (state.phase == ComposterTaskPhase.IDLE) {
            // Step 1: Find composter in farm structure
            BlockPos composterPos = findComposterInFarm(building, world);
            if (composterPos == null) {
                SettlementsMod.LOGGER.debug("No composter found in farm {}", building.getId());
                return; // No composter found
            }
            state.composterPos = composterPos;
            
            // Step 2: Find chests near lectern
            BlockPos lecternPos = settlement.getLecternPos();
            List<BlockPos> chestPositions = findChestsNearLectern(lecternPos, world);
            if (chestPositions.isEmpty()) {
                SettlementsMod.LOGGER.debug("No chests found near lectern for composter task");
                return; // No chests found
            }
            state.targetChestPos = chestPositions.get(0); // Use first chest
            
            // Check if there are seeds available
            Map<Item, Integer> availableSeeds = checkSeedsInChests(chestPositions, world);
            if (availableSeeds.isEmpty()) {
                SettlementsMod.LOGGER.debug("No seeds available in chests for composter task");
                return; // No seeds available
            }
            
            // Start task - pathfind to chest
            state.phase = ComposterTaskPhase.GOING_TO_CHEST;
            boolean pathStarted = villager.getNavigation().startMovingTo(
                state.targetChestPos.getX() + 0.5,
                state.targetChestPos.getY() + 0.5,
                state.targetChestPos.getZ() + 0.5,
                1.0 // Normal speed
            );
            
            if (pathStarted) {
                SettlementsMod.LOGGER.info("Second farm villager {} started composter task - going to chest at {}", 
                    villagerId, state.targetChestPos);
            }
            return;
        }
        
        // Handle ongoing task based on phase
        switch (state.phase) {
            case IDLE:
                // Should not happen - IDLE state is handled at start of method
                break;
            case GOING_TO_CHEST:
                handleGoingToChest(villager, state, world);
                break;
            case AT_CHEST_GETTING_SEEDS:
                handleAtChestGettingSeeds(settlement, building, villagerData, villager, state, world);
                break;
            case GOING_TO_COMPOSTER:
                handleGoingToComposter(villager, state, world);
                break;
            case AT_COMPOSTER:
                handleAtComposter(building, villagerData, villager, state, world);
                break;
            case GOING_TO_CHEST_DEPOSIT:
                handleGoingToChestDeposit(villager, state, world);
                break;
            case AT_CHEST_DEPOSITING:
                handleAtChestDepositing(settlement, building, villagerData, villager, state, world);
                break;
        }
    }
    
    /**
     * Handles villager pathfinding to chest.
     */
    private static void handleGoingToChest(VillagerEntity villager, ComposterTaskState state, ServerWorld world) {
        double distanceSq = villager.getPos().squaredDistanceTo(
            state.targetChestPos.getX() + 0.5,
            state.targetChestPos.getY() + 0.5,
            state.targetChestPos.getZ() + 0.5
        );
        
        if (distanceSq <= ARRIVAL_DISTANCE_SQ) {
            // Arrived at chest - record arrival time if not already recorded
            if (state.chestArrivalTime == 0) {
                state.chestArrivalTime = world.getTime();
                SettlementsMod.LOGGER.info("Villager {} arrived at chest at {} (distance: {})", 
                    villager.getUuid(), state.targetChestPos, String.format("%.2f", Math.sqrt(distanceSq)));
            }
            // Transition to getting seeds phase (will check stay duration in handleAtChestGettingSeeds)
            state.phase = ComposterTaskPhase.AT_CHEST_GETTING_SEEDS;
        } else {
            // Continue pathfinding - check if navigation is still active
            if (!villager.getNavigation().isFollowingPath()) {
                // Path was interrupted or completed - restart
                boolean pathStarted = villager.getNavigation().startMovingTo(
                    state.targetChestPos.getX() + 0.5,
                    state.targetChestPos.getY() + 0.5,
                    state.targetChestPos.getZ() + 0.5,
                    1.0
                );
                if (!pathStarted) {
                    SettlementsMod.LOGGER.warn("Villager {} failed to restart pathfinding to chest at {} (distance: {})", 
                        villager.getUuid(), state.targetChestPos, String.format("%.2f", Math.sqrt(distanceSq)));
                }
            }
        }
    }
    
    /**
     * Handles villager at chest getting seeds.
     */
    private static void handleAtChestGettingSeeds(Settlement settlement, Building building,
                                                  VillagerData villagerData, VillagerEntity villager,
                                                  ComposterTaskState state, ServerWorld world) {
        long currentTime = world.getTime();
        long arrivalTime = state.chestArrivalTime;
        
        // Check if villager has been at chest long enough (minimum 5 seconds)
        if (arrivalTime == 0) {
            // Just arrived - record arrival time
            state.chestArrivalTime = currentTime;
            SettlementsMod.LOGGER.info("Villager {} just arrived at chest, waiting...", villager.getUuid());
            return; // Wait for next tick
        }
        
        long timeAtChest = currentTime - arrivalTime;
        if (timeAtChest < MIN_CHEST_STAY_TICKS) {
            // Still need to wait - villager must stay at chest
            SettlementsMod.LOGGER.debug("Villager {} at chest, waiting... ({}/{})", 
                villager.getUuid(), timeAtChest, MIN_CHEST_STAY_TICKS);
            return; // Continue waiting
        }
        
        // Villager has been at chest long enough - check for seeds
        BlockPos lecternPos = settlement.getLecternPos();
        List<BlockPos> chestPositions = findChestsNearLectern(lecternPos, world);
        
        SettlementsMod.LOGGER.info("Villager {} at chest getting seeds - checking {} chests (been here {} ticks)", 
            villager.getUuid(), chestPositions.size(), timeAtChest);
        
        // First check what seeds are available (for logging)
        Map<Item, Integer> availableSeeds = checkSeedsInChests(chestPositions, world);
        SettlementsMod.LOGGER.info("Villager {} checking for seeds - found {} types available: {}", 
            villager.getUuid(), availableSeeds.size(), availableSeeds);
        
        // Take seeds from chests
        Map<Item, Integer> seedsToCompost = takeSeedsFromChests(chestPositions, world);
        if (seedsToCompost.isEmpty()) {
            // No seeds available - reset task
            SettlementsMod.LOGGER.info("No seeds available in chests for villager {} (checked {} chests, available: {}), resetting composter task", 
                villager.getUuid(), chestPositions.size(), availableSeeds);
            TASK_STATES.remove(villagerData.getEntityId());
            return;
        }
        
        SettlementsMod.LOGGER.info("Villager {} collected {} types of seeds from chests (total {} items): {}", 
            villager.getUuid(), seedsToCompost.size(), 
            seedsToCompost.values().stream().mapToInt(Integer::intValue).sum(),
            seedsToCompost);
        
        state.seedsCollected = seedsToCompost;
        state.phase = ComposterTaskPhase.GOING_TO_COMPOSTER;
        state.chestArrivalTime = 0; // Reset for next chest visit
        
        // Pathfind to composter - MUST walk (speed 1.0)
        boolean pathStarted = villager.getNavigation().startMovingTo(
            state.composterPos.getX() + 0.5,
            state.composterPos.getY() + 0.5,
            state.composterPos.getZ() + 0.5,
            1.0 // Normal walking speed - NO teleporting, NO running, NO flying
        );
        
        if (pathStarted) {
            SettlementsMod.LOGGER.info("Villager {} started pathfinding to composter at {} - walking", 
                villager.getUuid(), state.composterPos);
        } else {
            SettlementsMod.LOGGER.warn("Villager {} failed to start pathfinding to composter at {}", 
                villager.getUuid(), state.composterPos);
        }
        
        if (pathStarted) {
            SettlementsMod.LOGGER.debug("Villager {} got seeds, going to composter at {}", 
                villager.getUuid(), state.composterPos);
        }
    }
    
    /**
     * Handles villager pathfinding to composter.
     */
    private static void handleGoingToComposter(VillagerEntity villager, ComposterTaskState state, ServerWorld world) {
        double distanceSq = villager.getPos().squaredDistanceTo(
            state.composterPos.getX() + 0.5,
            state.composterPos.getY() + 0.5,
            state.composterPos.getZ() + 0.5
        );
        
        if (distanceSq <= ARRIVAL_DISTANCE_SQ) {
            // Arrived at composter
            state.phase = ComposterTaskPhase.AT_COMPOSTER;
            SettlementsMod.LOGGER.debug("Villager {} arrived at composter", villager.getUuid());
        } else {
            // Continue pathfinding
            villager.getNavigation().startMovingTo(
                state.composterPos.getX() + 0.5,
                state.composterPos.getY() + 0.5,
                state.composterPos.getZ() + 0.5,
                1.0
            );
        }
    }
    
    /**
     * Handles villager at composter using seeds.
     */
    private static void handleAtComposter(Building building, VillagerData villagerData,
                                         VillagerEntity villager, ComposterTaskState state,
                                         ServerWorld world) {
        // Use seeds at composter to create bone meal
        int boneMealProduced = useComposter(state.composterPos, state.seedsCollected, world);
        
        if (boneMealProduced > 0) {
            state.boneMealProduced = boneMealProduced;
            state.phase = ComposterTaskPhase.GOING_TO_CHEST_DEPOSIT;
            
            // Pathfind back to chest
            boolean pathStarted = villager.getNavigation().startMovingTo(
                state.targetChestPos.getX() + 0.5,
                state.targetChestPos.getY() + 0.5,
                state.targetChestPos.getZ() + 0.5,
                1.0
            );
            
            if (pathStarted) {
                SettlementsMod.LOGGER.debug("Villager {} produced {} bone meal, going back to chest", 
                    villager.getUuid(), boneMealProduced);
            }
        } else {
            // No bone meal produced - reset task
            SettlementsMod.LOGGER.debug("No bone meal produced, resetting composter task");
            TASK_STATES.remove(villagerData.getEntityId());
        }
    }
    
    /**
     * Handles villager pathfinding back to chest to deposit.
     */
    private static void handleGoingToChestDeposit(VillagerEntity villager, ComposterTaskState state, ServerWorld world) {
        double distanceSq = villager.getPos().squaredDistanceTo(
            state.targetChestPos.getX() + 0.5,
            state.targetChestPos.getY() + 0.5,
            state.targetChestPos.getZ() + 0.5
        );
        
        if (distanceSq <= ARRIVAL_DISTANCE_SQ) {
            // Arrived at chest - record arrival time if not already recorded
            if (state.chestArrivalTime == 0) {
                state.chestArrivalTime = world.getTime();
                SettlementsMod.LOGGER.info("Villager {} arrived at chest to deposit bone meal (distance: {})", 
                    villager.getUuid(), String.format("%.2f", Math.sqrt(distanceSq)));
            }
            // Transition to depositing phase (will check stay duration in handleAtChestDepositing)
            state.phase = ComposterTaskPhase.AT_CHEST_DEPOSITING;
        } else {
            // Continue pathfinding - MUST walk (speed 1.0)
            if (!villager.getNavigation().isFollowingPath()) {
                boolean pathStarted = villager.getNavigation().startMovingTo(
                    state.targetChestPos.getX() + 0.5,
                    state.targetChestPos.getY() + 0.5,
                    state.targetChestPos.getZ() + 0.5,
                    1.0 // Normal walking speed - NO teleporting, NO running, NO flying
                );
                if (!pathStarted) {
                    SettlementsMod.LOGGER.warn("Villager {} failed to restart pathfinding to chest for deposit", 
                        villager.getUuid());
                }
            }
        }
    }
    
    /**
     * Handles villager at chest depositing bone meal.
     */
    private static void handleAtChestDepositing(Settlement settlement, Building building,
                                               VillagerData villagerData, VillagerEntity villager,
                                               ComposterTaskState state, ServerWorld world) {
        long currentTime = world.getTime();
        long arrivalTime = state.chestArrivalTime;
        
        // Check if villager has been at chest long enough (minimum 5 seconds)
        if (arrivalTime == 0) {
            // Just arrived - record arrival time
            state.chestArrivalTime = currentTime;
            SettlementsMod.LOGGER.info("Villager {} just arrived at chest to deposit, waiting...", villager.getUuid());
            return; // Wait for next tick
        }
        
        long timeAtChest = currentTime - arrivalTime;
        if (timeAtChest < MIN_CHEST_STAY_TICKS) {
            // Still need to wait - villager must stay at chest
            SettlementsMod.LOGGER.debug("Villager {} at chest depositing, waiting... ({}/{})", 
                villager.getUuid(), timeAtChest, MIN_CHEST_STAY_TICKS);
            return; // Continue waiting
        }
        
        // Villager has been at chest long enough - deposit bone meal
        BlockPos lecternPos = settlement.getLecternPos();
        List<BlockPos> chestPositions = findChestsNearLectern(lecternPos, world);
        
        SettlementsMod.LOGGER.info("Villager {} depositing bone meal at chest (been here {} ticks)", 
            villager.getUuid(), timeAtChest);
        
        // Return bone meal to chests
        returnBoneMealToChests(chestPositions, state.boneMealProduced, world);
        
        // Update farm output data
        updateFarmOutputData(building, state.boneMealProduced);
        
        SettlementsMod.LOGGER.info("Second farm villager {} completed composter task - produced {} bone meal after {} ticks at chest", 
            villagerData.getEntityId(), state.boneMealProduced, timeAtChest);
        
        // Reset task state
        TASK_STATES.remove(villagerData.getEntityId());
    }
    
    /**
     * Checks if seeds are available in chests (without taking them).
     */
    private static Map<Item, Integer> checkSeedsInChests(List<BlockPos> chestPositions, ServerWorld world) {
        Map<Item, Integer> availableSeeds = new HashMap<>();
        
        for (BlockPos chestPos : chestPositions) {
            net.minecraft.block.entity.ChestBlockEntity chest = 
                (net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(chestPos);
            
            if (chest == null) {
                continue;
            }
            
            // Scan chest for compostable seeds
            for (int i = 0; i < chest.size(); i++) {
                net.minecraft.item.ItemStack stack = chest.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }
                
                Item item = stack.getItem();
                if (isCompostable(item)) {
                    // Calculate how much is available (leaving at least SEED_RESERVE_COUNT)
                    int currentCount = stack.getCount();
                    int available = Math.max(0, currentCount - SEED_RESERVE_COUNT);
                    
                    if (available > 0) {
                        availableSeeds.put(item, availableSeeds.getOrDefault(item, 0) + available);
                    }
                }
            }
        }
        
        return availableSeeds;
    }
    
    /**
     * Checks if a building is a farm building.
     */
    private static boolean isFarmBuilding(Building building) {
        String structureName = building.getStructureType().getPath().toLowerCase();
        return structureName.contains("farm");
    }
    
    /**
     * Finds a composter block in the farm structure.
     */
    private static BlockPos findComposterInFarm(Building building, ServerWorld world) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return null;
        }
        
        StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
        if (structureData == null) {
            return null;
        }
        
        BlockPos buildingPos = building.getPosition();
        int rotation = building.getRotation();
        
        // Search for composter in structure
        for (com.secretasain.settlements.building.StructureBlock structureBlock : structureData.getBlocks()) {
            if (structureBlock.getBlockState().isOf(Blocks.COMPOSTER)) {
                BlockPos relativePos = structureBlock.getRelativePos();
                // Apply rotation
                BlockPos rotatedPos = applyRotation(relativePos, rotation, structureData.getDimensions());
                BlockPos worldPos = buildingPos.add(rotatedPos);
                return worldPos;
            }
        }
        
        return null;
    }
    
    /**
     * Applies rotation to a relative position.
     */
    private static BlockPos applyRotation(BlockPos pos, int rotation, net.minecraft.util.math.Vec3i dimensions) {
        int x = pos.getX();
        int z = pos.getZ();
        
        switch (rotation) {
            case 90:
                return new BlockPos(-z, pos.getY(), x);
            case 180:
                return new BlockPos(-x, pos.getY(), -z);
            case 270:
                return new BlockPos(z, pos.getY(), -x);
            default:
                return pos;
        }
    }
    
    /**
     * Finds chests near the lectern position.
     */
    private static List<BlockPos> findChestsNearLectern(BlockPos lecternPos, ServerWorld world) {
        List<BlockPos> chests = new ArrayList<>();
        int radius = (int) Math.ceil(CHEST_SEARCH_RADIUS);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = lecternPos.add(x, y, z);
                    BlockState state = world.getBlockState(checkPos);
                    
                    if (state.getBlock() instanceof net.minecraft.block.ChestBlock) {
                        double distance = Math.sqrt(lecternPos.getSquaredDistance(checkPos));
                        if (distance <= CHEST_SEARCH_RADIUS) {
                            chests.add(checkPos);
                        }
                    }
                }
            }
        }
        
        return chests;
    }
    
    /**
     * Takes seeds from chests, leaving at least a stack of each type.
     * @return Map of seed items to counts taken
     */
    private static Map<Item, Integer> takeSeedsFromChests(List<BlockPos> chestPositions, ServerWorld world) {
        Map<Item, Integer> seedsTaken = new HashMap<>();
        
        SettlementsMod.LOGGER.info("Taking seeds from {} chests", chestPositions.size());
        
        // FIRST PASS: Count total seeds of each type across ALL chests
        Map<Item, Integer> totalSeedsByType = new HashMap<>();
        for (BlockPos chestPos : chestPositions) {
            net.minecraft.block.entity.ChestBlockEntity chest = 
                (net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(chestPos);
            
            if (chest == null) {
                continue;
            }
            
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }
                
                Item item = stack.getItem();
                if (isCompostable(item)) {
                    totalSeedsByType.put(item, totalSeedsByType.getOrDefault(item, 0) + stack.getCount());
                }
            }
        }
        
        SettlementsMod.LOGGER.info("Total seeds found across all chests: {}", totalSeedsByType);
        
        // Calculate how much of each type we can take (reserve 64 TOTAL per type, not per slot)
        Map<Item, Integer> seedsToTakeByType = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : totalSeedsByType.entrySet()) {
            Item item = entry.getKey();
            int totalCount = entry.getValue();
            int toTake = Math.max(0, totalCount - SEED_RESERVE_COUNT);
            if (toTake > 0) {
                seedsToTakeByType.put(item, toTake);
                SettlementsMod.LOGGER.info("Can take {} {} (total: {}, reserving {})", toTake, item, totalCount, SEED_RESERVE_COUNT);
            } else {
                SettlementsMod.LOGGER.info("Not taking {} - only {} total (need to reserve {})", item, totalCount, SEED_RESERVE_COUNT);
            }
        }
        
        if (seedsToTakeByType.isEmpty()) {
            SettlementsMod.LOGGER.info("No seeds to take after reserving {} per type", SEED_RESERVE_COUNT);
            return seedsTaken;
        }
        
        // SECOND PASS: Actually take seeds from chests, respecting the total we calculated
        Map<Item, Integer> takenSoFar = new HashMap<>();
        
        for (BlockPos chestPos : chestPositions) {
            net.minecraft.block.entity.ChestBlockEntity chest = 
                (net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(chestPos);
            
            if (chest == null) {
                SettlementsMod.LOGGER.debug("Chest at {} is null", chestPos);
                continue;
            }
            
            SettlementsMod.LOGGER.info("Scanning chest at {} (size: {})", chestPos, chest.size());
            
            // Scan chest for compostable seeds
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }
                
                Item item = stack.getItem();
                
                if (isCompostable(item)) {
                    int alreadyTaken = takenSoFar.getOrDefault(item, 0);
                    int targetToTake = seedsToTakeByType.getOrDefault(item, 0);
                    int stillNeed = targetToTake - alreadyTaken;
                    
                    if (stillNeed <= 0) {
                        // Already taken enough of this type
                        continue;
                    }
                    
                    int currentCount = stack.getCount();
                    int toTakeFromThisSlot = Math.min(stillNeed, currentCount);
                    
                    SettlementsMod.LOGGER.info("Taking {} {} from chest at {} slot {} (slot has {}, need {} more of this type)", 
                        toTakeFromThisSlot, item, chestPos, i, currentCount, stillNeed);
                    
                    if (toTakeFromThisSlot > 0) {
                        // Create new stack with reduced count
                        int remainingCount = currentCount - toTakeFromThisSlot;
                        ItemStack newStack = remainingCount > 0 ? new ItemStack(item, remainingCount) : ItemStack.EMPTY;
                        
                        // FORCE UPDATE: Set the stack directly in the chest
                        chest.setStack(i, newStack);
                        
                        // Mark chest as dirty to save changes
                        chest.markDirty();
                        
                        // Track what we took
                        takenSoFar.put(item, alreadyTaken + toTakeFromThisSlot);
                        seedsTaken.put(item, seedsTaken.getOrDefault(item, 0) + toTakeFromThisSlot);
                        
                        SettlementsMod.LOGGER.info("REMOVED {} {} from chest at {} slot {} (left {} in chest, total taken of this type: {})", 
                            toTakeFromThisSlot, item, chestPos, i, remainingCount, takenSoFar.get(item));
                    }
                }
            }
        }
        
        int totalItems = seedsTaken.values().stream().mapToInt(Integer::intValue).sum();
        SettlementsMod.LOGGER.info("Total seeds taken: {} types, {} items - {}", 
            seedsTaken.size(), totalItems, seedsTaken);
        
        return seedsTaken;
    }
    
    /**
     * Uses seeds at a composter to produce bone meal.
     * Simulates composter behavior by manually updating block states.
     * @param composterPos Position of the composter
     * @param seeds Map of seed items to counts
     * @param world The server world
     * @return Amount of bone meal produced
     */
    private static int useComposter(BlockPos composterPos, Map<Item, Integer> seeds, ServerWorld world) {
        BlockState composterState = world.getBlockState(composterPos);
        if (!(composterState.getBlock() instanceof ComposterBlock)) {
            SettlementsMod.LOGGER.warn("Composter at {} is not a ComposterBlock", composterPos);
            return 0;
        }
        
        int totalBoneMeal = 0;
        int currentLevel = composterState.get(ComposterBlock.LEVEL);
        
        SettlementsMod.LOGGER.info("Using composter at {} (current level: {}) with {} seed types", 
            composterPos, currentLevel, seeds.size());
        
        // Process each seed type
        for (Map.Entry<Item, Integer> entry : seeds.entrySet()) {
            Item seedItem = entry.getKey();
            int seedCount = entry.getValue();
            
            SettlementsMod.LOGGER.info("Processing {} {} at composter", seedCount, seedItem);
            
            // Get composter chance for this item
            float chance = ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(seedItem);
            if (chance <= 0.0f) {
                SettlementsMod.LOGGER.warn("Item {} cannot be composted (chance: {})", seedItem, chance);
                continue; // Item cannot be composted
            }
            
            SettlementsMod.LOGGER.info("Item {} has compost chance: {}", seedItem, chance);
            
            // Add items to composter and collect bone meal
            for (int i = 0; i < seedCount; i++) {
                // Each item has a chance to increase the level
                if (world.getRandom().nextFloat() < chance) {
                    currentLevel++;
                    SettlementsMod.LOGGER.debug("Added {} to composter, level now: {}", seedItem, currentLevel);
                    
                    // Check if composter is full (level 7) and ready to produce bone meal
                    if (currentLevel >= 7) {
                        // Composter is full - extract bone meal
                        currentLevel = 0;
                        totalBoneMeal += 1; // One bone meal per full composter
                        SettlementsMod.LOGGER.info("Composter full! Extracted 1 bone meal (total: {})", totalBoneMeal);
                    }
                } else {
                    SettlementsMod.LOGGER.debug("{} did not increase composter level (chance failed)", seedItem);
                }
            }
        }
        
        // Update composter block state with final level
        if (currentLevel != composterState.get(ComposterBlock.LEVEL)) {
            BlockState newState = composterState.with(ComposterBlock.LEVEL, currentLevel);
            world.setBlockState(composterPos, newState);
            SettlementsMod.LOGGER.info("Updated composter level from {} to {}", 
                composterState.get(ComposterBlock.LEVEL), currentLevel);
        }
        
        SettlementsMod.LOGGER.info("Composter processing complete - produced {} bone meal (final level: {})", 
            totalBoneMeal, currentLevel);
        return totalBoneMeal;
    }
    
    
    /**
     * Returns bone meal to chests.
     */
    private static void returnBoneMealToChests(List<BlockPos> chestPositions, int boneMealCount, ServerWorld world) {
        if (boneMealCount <= 0) {
            SettlementsMod.LOGGER.warn("Attempted to return {} bone meal to chests (invalid count)", boneMealCount);
            return;
        }
        
        ItemStack boneMealStack = new ItemStack(Items.BONE_MEAL, boneMealCount);
        int initialCount = boneMealCount;
        
        SettlementsMod.LOGGER.info("Returning {} bone meal to {} chests", boneMealCount, chestPositions.size());
        
        for (BlockPos chestPos : chestPositions) {
            if (boneMealStack.isEmpty()) {
                break;
            }
            
            net.minecraft.block.entity.ChestBlockEntity chest = 
                (net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(chestPos);
            
            if (chest == null) {
                SettlementsMod.LOGGER.debug("Chest at {} is null, skipping", chestPos);
                continue;
            }
            
            int beforeCount = boneMealStack.getCount();
            // Try to add bone meal to chest
            int remaining = addItemStackToChest(chest, boneMealStack);
            int deposited = beforeCount - remaining;
            boneMealStack.setCount(remaining);
            chest.markDirty();
            
            if (deposited > 0) {
                SettlementsMod.LOGGER.info("Deposited {} bone meal to chest at {} ({} remaining)", 
                    deposited, chestPos, remaining);
            }
        }
        
        int depositedTotal = initialCount - boneMealStack.getCount();
        if (depositedTotal < initialCount) {
            SettlementsMod.LOGGER.warn("Could not deposit all bone meal! Deposited {}/{} ({} lost - chests may be full)", 
                depositedTotal, initialCount, boneMealStack.getCount());
        } else {
            SettlementsMod.LOGGER.info("Successfully deposited all {} bone meal to chests", depositedTotal);
        }
    }
    
    /**
     * Adds an ItemStack to a chest, returning the remaining count.
     */
    private static int addItemStackToChest(net.minecraft.block.entity.ChestBlockEntity chest, ItemStack stack) {
        int remaining = stack.getCount();
        
        for (int i = 0; i < chest.size() && remaining > 0; i++) {
            ItemStack slotStack = chest.getStack(i);
            
            if (slotStack.isEmpty()) {
                int toPlace = Math.min(remaining, stack.getMaxCount());
                chest.setStack(i, new ItemStack(stack.getItem(), toPlace));
                remaining -= toPlace;
            } else if (ItemStack.canCombine(slotStack, stack)) {
                int space = slotStack.getMaxCount() - slotStack.getCount();
                if (space > 0) {
                    int toAdd = Math.min(remaining, space);
                    slotStack.increment(toAdd);
                    chest.setStack(i, slotStack);
                    remaining -= toAdd;
                }
            }
        }
        
        return remaining;
    }
    
    // Track bone meal production per building
    private static final Map<UUID, Integer> BONE_MEAL_PRODUCTION = new HashMap<>();
    
    /**
     * Updates farm output data to include bone meal production.
     */
    private static void updateFarmOutputData(Building building, int boneMealProduced) {
        if (boneMealProduced > 0) {
            int current = BONE_MEAL_PRODUCTION.getOrDefault(building.getId(), 0);
            BONE_MEAL_PRODUCTION.put(building.getId(), current + boneMealProduced);
            SettlementsMod.LOGGER.debug("Farm {} produced {} bone meal (total: {})", 
                building.getId(), boneMealProduced, current + boneMealProduced);
        }
    }
    
    /**
     * Gets the total bone meal produced by a farm building.
     * @param buildingId The building UUID
     * @return Total bone meal produced
     */
    public static int getBoneMealProduction(UUID buildingId) {
        return BONE_MEAL_PRODUCTION.getOrDefault(buildingId, 0);
    }
    
    /**
     * Resets bone meal production for a building (e.g., when building is removed).
     */
    public static void resetBoneMealProduction(UUID buildingId) {
        BONE_MEAL_PRODUCTION.remove(buildingId);
    }
    
    /**
     * Checks if a villager is currently doing a composter task.
     * @param villagerId The villager UUID
     * @return true if villager is doing a composter task
     */
    public static boolean isVillagerDoingComposterTask(UUID villagerId) {
        ComposterTaskState state = TASK_STATES.get(villagerId);
        return state != null && state.phase != ComposterTaskPhase.IDLE;
    }
    
    /**
     * Cleans up task state for a villager (e.g., when unassigned).
     */
    public static void cleanupVillagerTask(UUID villagerId) {
        TASK_STATES.remove(villagerId);
    }
    
    /**
     * Gets the VillagerEntity from the world by UUID.
     */
    private static VillagerEntity getVillagerEntity(ServerWorld world, java.util.UUID entityId) {
        try {
            return (VillagerEntity) world.getEntity(entityId);
        } catch (Exception e) {
            return null;
        }
    }
}

