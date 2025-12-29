package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3i;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * System for second lumberyard villager to collect items near harvested trees.
 * The second villager assigned to a lumberyard will:
 * 1. Collect items near recently harvested trees (saplings, sticks, apples, etc.)
 * 2. Return items to chests
 * 
 * Note: Composting is handled by the farmer's composter system (FarmComposterSystem),
 * which takes compostable items from chests and converts them to bone meal.
 */
public class LumberyardItemCollectorSystem {
    private static final int CHECK_INTERVAL_TICKS = 10; // Check every 0.5 seconds (10 ticks) - faster for quicker pickup
    private static final double ITEM_SEARCH_RADIUS = 16.0; // Search for items within 16 blocks of harvested trees
    private static final double CHEST_SEARCH_RADIUS = 8.0; // Search for chests within 8 blocks of lectern
    private static final double ARRIVAL_DISTANCE = 3.0; // Consider arrived when within 3 blocks
    private static final double ARRIVAL_DISTANCE_SQ = ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    private static final int MIN_CHEST_STAY_TICKS = 100; // Minimum 5 seconds (100 ticks) at chest before continuing
    private static final long TREE_HARVEST_TIMEOUT = 6000; // Items from trees harvested more than 5 minutes ago are ignored (6000 ticks = 5 minutes)
    private static final int SAPLINGS_BEFORE_PLANT = 3; // Plant 1 sapling after collecting 3 saplings (2 go to chest)
    private static final int PLANTING_SEARCH_RADIUS = 8; // Search for planting location within 8 blocks of collection location
    private static final double ITEM_PICKUP_RADIUS = 6.0; // Pick up items within 6 blocks (6x6 area)
    private static final double ITEM_PICKUP_RADIUS_SQ = ITEM_PICKUP_RADIUS * ITEM_PICKUP_RADIUS;
    private static final double SAPLING_CHAIN_RADIUS = 3.0; // Look for nearby saplings in 3x3 area for chaining
    private static final double SAPLING_CHAIN_RADIUS_SQ = SAPLING_CHAIN_RADIUS * SAPLING_CHAIN_RADIUS;
    private static final double ITEM_COLLECTION_SPEED = 2.5; // Fast speed for collecting items (2.5x normal speed)
    private static final double SAPLING_SPACING_RADIUS = 4.0; // Don't plant saplings within 4 blocks of existing saplings
    private static final double SAPLING_SPACING_RADIUS_SQ = SAPLING_SPACING_RADIUS * SAPLING_SPACING_RADIUS;
    private static final int MAX_SAPLINGS_IN_AREA = 10; // Stop planting after 10 saplings are in the area
    private static final int DEPOSIT_THRESHOLD = 32; // Items needed to trigger deposit (same as farmer villager)
    
    // Track harvested tree positions per building
    private static final Map<UUID, List<TreeHarvestLocation>> HARVESTED_TREES = new HashMap<>();
    
    // State tracking for collection tasks
    private static final Map<UUID, CollectionTaskState> TASK_STATES = new HashMap<>();
    
    /**
     * Represents a location where a tree was harvested.
     */
    private static class TreeHarvestLocation {
        BlockPos position;
        long harvestTime; // World time when tree was harvested
        
        TreeHarvestLocation(BlockPos position, long harvestTime) {
            this.position = position;
            this.harvestTime = harvestTime;
        }
    }
    
    /**
     * State for a villager's collection task.
     */
    private static class CollectionTaskState {
        UUID buildingId;
        BlockPos targetItemPos;
        CollectionTaskPhase phase;
        List<ItemStack> collectedItems;
        int saplingCount; // Track number of saplings collected
        BlockPos lastSaplingPickupPos; // Position where last sapling was picked up
        
        CollectionTaskState(UUID buildingId) {
            this.buildingId = buildingId;
            this.phase = CollectionTaskPhase.IDLE;
            this.collectedItems = new ArrayList<>();
            this.saplingCount = 0;
            this.lastSaplingPickupPos = null;
        }
    }
    
    /**
     * Phases of the collection task.
     */
    private enum CollectionTaskPhase {
        IDLE,                    // Not doing anything
        GOING_TO_ITEM,           // Pathfinding to item
        COLLECTING_ITEM         // Collecting item (at item location)
    }
    
    /**
     * Registers the item collector system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_INTERVAL_TICKS == 0) {
                tick(world);
            }
        });
    }
    
    /**
     * Records a tree harvest location for item collection.
     * Called by LumberjackLogHarvester when a tree is harvested.
     */
    public static void recordTreeHarvest(UUID buildingId, BlockPos treePosition, long worldTime) {
        HARVESTED_TREES.computeIfAbsent(buildingId, k -> new ArrayList<>()).add(
            new TreeHarvestLocation(treePosition, worldTime)
        );
        
        // Clean up old harvest locations (older than timeout)
        List<TreeHarvestLocation> locations = HARVESTED_TREES.get(buildingId);
        if (locations != null) {
            locations.removeIf(loc -> (worldTime - loc.harvestTime) > TREE_HARVEST_TIMEOUT);
        }
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
     * Processes collection tasks for lumberyard buildings with second villagers.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        for (Building building : settlement.getBuildings()) {
            if (!isLumberyardBuilding(building)) {
                continue;
            }
            
            // CRITICAL: Only process COMPLETED buildings
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue; // Skip non-completed buildings
            }
            
            // Get all villagers assigned to this lumberyard
            List<VillagerData> assignedVillagers = WorkAssignmentManager.getVillagersAssignedToBuilding(
                settlement, building.getId()
            );
            
            // Check if there's a second villager (index 1)
            if (assignedVillagers.size() < 2) {
                continue; // Need at least 2 villagers
            }
            
            // Get the second villager
            VillagerData secondVillagerData = assignedVillagers.get(1);
            VillagerEntity villager = getVillagerEntity(world, secondVillagerData.getEntityId());
            
            if (villager == null) {
                continue;
            }
            
            // Skip if second villager is depositing (let deposit system handle it)
            if (secondVillagerData.isDepositing()) {
                continue;
            }
            
            // Check if villager is close enough to building to work (within 32 blocks)
            BlockPos buildingPos = building.getPosition();
            double distanceSq = villager.getPos().squaredDistanceTo(
                buildingPos.getX() + 0.5,
                buildingPos.getY() + 0.5,
                buildingPos.getZ() + 0.5
            );
            
            if (distanceSq > 32.0 * 32.0) {
                continue; // Villager is too far from building to work
            }
            
            // Process collection task
            processCollectionTask(settlement, building, secondVillagerData, villager, world);
        }
    }
    
    /**
     * Processes a collection task for the second lumberyard villager.
     */
    private static void processCollectionTask(Settlement settlement, Building building,
                                            VillagerData villagerData, VillagerEntity villager,
                                            ServerWorld world) {
        UUID villagerId = villagerData.getEntityId();
        CollectionTaskState state = TASK_STATES.computeIfAbsent(villagerId,
            id -> new CollectionTaskState(building.getId()));
        
        // Validate building still matches
        if (!state.buildingId.equals(building.getId())) {
            // Building changed - preserve collected items and update building ID
            List<ItemStack> preservedItems = new ArrayList<>(state.collectedItems);
            int preservedSaplingCount = state.saplingCount;
            BlockPos preservedPickupPos = state.lastSaplingPickupPos;
            
            TASK_STATES.remove(villagerId);
            state = new CollectionTaskState(building.getId());
            state.collectedItems = preservedItems;
            state.saplingCount = preservedSaplingCount;
            state.lastSaplingPickupPos = preservedPickupPos;
            TASK_STATES.put(villagerId, state);
        }
        
        // Initialize task if needed OR if current target item is gone
        boolean shouldSearchForNewItem = false;
        if (state.phase == CollectionTaskPhase.IDLE) {
            shouldSearchForNewItem = true;
        } else if (state.phase == CollectionTaskPhase.GOING_TO_ITEM) {
            // Check if target item still exists
            ItemEntity targetItem = findItemAtPosition(state.targetItemPos, world);
            if (targetItem == null || targetItem.isRemoved()) {
                // Item is gone - search for a new one
                shouldSearchForNewItem = true;
                state.phase = CollectionTaskPhase.IDLE;
            }
        }
        
        if (shouldSearchForNewItem) {
            
            // Find items near harvested trees OR around the building area OR near villager
            ItemEntity nearbyItem = null;
            
            // First, try to find items very close to villager (within pickup radius)
            nearbyItem = findItemNearVillager(villager, world, ITEM_PICKUP_RADIUS);
            if (nearbyItem != null) {
                SettlementsMod.LOGGER.debug("Found item very close to villager: {} at {}", 
                    nearbyItem.getStack().getItem(), nearbyItem.getBlockPos());
            }
            
            // If no items very close, try near harvested trees
            if (nearbyItem == null) {
                List<TreeHarvestLocation> harvestLocations = HARVESTED_TREES.getOrDefault(building.getId(), new ArrayList<>());
                if (!harvestLocations.isEmpty()) {
                    nearbyItem = findItemNearHarvestedTrees(harvestLocations, world);
                    if (nearbyItem != null) {
                        SettlementsMod.LOGGER.debug("Found item near harvested tree: {} at {}", 
                            nearbyItem.getStack().getItem(), nearbyItem.getBlockPos());
                    }
                }
            }
            
            // If no items near harvested trees, search around the building area
            if (nearbyItem == null) {
                nearbyItem = findItemsAroundBuilding(building, world);
                if (nearbyItem != null) {
                    SettlementsMod.LOGGER.debug("Found item around building: {} at {}", 
                        nearbyItem.getStack().getItem(), nearbyItem.getBlockPos());
                }
            }
            
            // If still no items, try searching in a larger radius around villager
            if (nearbyItem == null) {
                nearbyItem = findItemNearVillager(villager, world, ITEM_SEARCH_RADIUS);
                if (nearbyItem != null) {
                    SettlementsMod.LOGGER.debug("Found item near villager (larger radius): {} at {}", 
                        nearbyItem.getStack().getItem(), nearbyItem.getBlockPos());
                }
            }
            
            if (nearbyItem == null) {
                // No items found - only log occasionally to avoid spam
                if (world.getTime() % 200 == 0) { // Log every 10 seconds
                    List<TreeHarvestLocation> harvestLocations = HARVESTED_TREES.getOrDefault(building.getId(), new ArrayList<>());
                    if (harvestLocations.isEmpty()) {
                        SettlementsMod.LOGGER.debug("Second lumberyard villager {} - no trees harvested recently, no items to collect", villagerId);
                    } else {
                        SettlementsMod.LOGGER.debug("Second lumberyard villager {} - {} harvest locations, but no items found nearby", 
                            villagerId, harvestLocations.size());
                    }
                }
                return; // No items found
            }
            
            state.targetItemPos = nearbyItem.getBlockPos();
            state.phase = CollectionTaskPhase.GOING_TO_ITEM;
            
            // Pathfind to item at high speed
            boolean pathStarted = villager.getNavigation().startMovingTo(
                nearbyItem.getX(),
                nearbyItem.getY(),
                nearbyItem.getZ(),
                ITEM_COLLECTION_SPEED // Fast speed for quick pickup
            );
            
            if (pathStarted) {
                SettlementsMod.LOGGER.info("Second lumberyard villager {} started collecting item {} at {}", 
                    villagerId, nearbyItem.getStack().getItem(), state.targetItemPos);
            } else {
                SettlementsMod.LOGGER.warn("Second lumberyard villager {} failed to start pathfinding to item at {}", 
                    villagerId, state.targetItemPos);
            }
            return;
        }
        
        // Check if threshold is reached (same as farmer villager)
        int totalAccumulated = villagerData.getTotalAccumulatedItems();
        if (totalAccumulated >= DEPOSIT_THRESHOLD && !villagerData.isDepositing()) {
            // Threshold reached - let VillagerDepositSystem handle deposit
            villagerData.setDepositing(true);
            SettlementManager.getInstance(world).markDirty();
        }
        
        // ALWAYS check if villager is within pickup radius of any item (opportunistic collection)
        // This allows the villager to pick up items even when going to chest/composter
        ItemEntity veryCloseItem = findItemNearVillager(villager, world, ITEM_PICKUP_RADIUS);
        if (veryCloseItem != null) {
            // Item is within pickup radius - collect it immediately (interrupt current task if needed)
            state.targetItemPos = veryCloseItem.getBlockPos();
            state.phase = CollectionTaskPhase.COLLECTING_ITEM;
            SettlementsMod.LOGGER.debug("Found item within pickup radius of villager {}, collecting immediately (interrupting phase: {})", 
                villagerId, state.phase);
        }
        
        // Periodically try to plant saplings and search for items (every 2 seconds)
        // This ensures the villager continuously looks for items and plants saplings
        if (world.getTime() % 40 == 0) {
            // Always try to plant saplings if we have enough
            tryPlantSaplingsFromInventory(villager, state, world);
            
            // Only search for items if idle
            if (state.phase == CollectionTaskPhase.IDLE) {
                // Search for nearby items
                ItemEntity nearbyItem = findItemNearVillager(villager, world, ITEM_SEARCH_RADIUS);
                if (nearbyItem == null) {
                    List<TreeHarvestLocation> harvestLocations = HARVESTED_TREES.getOrDefault(building.getId(), new ArrayList<>());
                    if (!harvestLocations.isEmpty()) {
                        nearbyItem = findItemNearHarvestedTrees(harvestLocations, world);
                    }
                }
                if (nearbyItem == null) {
                    nearbyItem = findItemsAroundBuilding(building, world);
                }
                
                if (nearbyItem != null && state.phase != CollectionTaskPhase.COLLECTING_ITEM) {
                    // Found an item - start collecting it at high speed
                    state.targetItemPos = nearbyItem.getBlockPos();
                    state.phase = CollectionTaskPhase.GOING_TO_ITEM;
                    villager.getNavigation().startMovingTo(
                        nearbyItem.getX(),
                        nearbyItem.getY(),
                        nearbyItem.getZ(),
                        ITEM_COLLECTION_SPEED // Fast speed for quick pickup
                    );
                    SettlementsMod.LOGGER.info("Second lumberyard villager {} found item {} during periodic search, starting collection", 
                        villagerId, nearbyItem.getStack().getItem());
                }
            }
        }
        
        // Always try to plant saplings if we have enough (regardless of phase)
        // This ensures saplings are planted even when villager is moving or collecting
        tryPlantSaplingsFromInventory(villager, state, world);
        
        // Handle ongoing task based on phase
        switch (state.phase) {
            case IDLE:
                // Continue collecting - threshold check happens after each collection
                break;
            case GOING_TO_ITEM:
                handleGoingToItem(villager, state, world);
                break;
            case COLLECTING_ITEM:
                handleCollectingItem(settlement, building, villagerData, villager, state, world);
                break;
        }
    }
    
    /**
     * Finds an item entity near harvested trees.
     */
    private static ItemEntity findItemNearHarvestedTrees(List<TreeHarvestLocation> harvestLocations, ServerWorld world) {
        long currentTime = world.getTime();
        
        for (TreeHarvestLocation location : harvestLocations) {
            // Skip old harvest locations
            if ((currentTime - location.harvestTime) > TREE_HARVEST_TIMEOUT) {
                continue;
            }
            
            // Search for items in a box around the harvest location
            Box searchBox = new Box(
                location.position.getX() - ITEM_SEARCH_RADIUS,
                location.position.getY() - ITEM_SEARCH_RADIUS,
                location.position.getZ() - ITEM_SEARCH_RADIUS,
                location.position.getX() + ITEM_SEARCH_RADIUS,
                location.position.getY() + ITEM_SEARCH_RADIUS,
                location.position.getZ() + ITEM_SEARCH_RADIUS
            );
            
            List<ItemEntity> items = world.getEntitiesByType(
                net.minecraft.entity.EntityType.ITEM,
                searchBox,
                entity -> entity instanceof ItemEntity && !entity.isRemoved() && !entity.getStack().isEmpty()
            );
            
            if (!items.isEmpty()) {
                // Filter out log items (those should go through normal deposit system)
                // Only collect saplings, sticks, apples, and other tree drops
                items.removeIf(item -> {
                    Item itemType = item.getStack().getItem();
                    String itemName = itemType.getTranslationKey().toLowerCase();
                    return itemName.contains("log") || itemName.contains("wood") || itemName.contains("plank");
                });
                
                if (!items.isEmpty()) {
                    // Return the closest item
                    ItemEntity closest = items.get(0);
                    double closestDist = location.position.getSquaredDistance(closest.getBlockPos());
                    for (ItemEntity item : items) {
                        double dist = location.position.getSquaredDistance(item.getBlockPos());
                        if (dist < closestDist) {
                            closest = item;
                            closestDist = dist;
                        }
                    }
                    return closest;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Finds items around the building area (fallback if no items near harvested trees).
     */
    private static ItemEntity findItemsAroundBuilding(Building building, ServerWorld world) {
        BlockPos buildingPos = building.getPosition();
        
        // Search in a larger area around the building
        Box searchBox = new Box(
            buildingPos.getX() - ITEM_SEARCH_RADIUS * 2,
            buildingPos.getY() - ITEM_SEARCH_RADIUS,
            buildingPos.getZ() - ITEM_SEARCH_RADIUS * 2,
            buildingPos.getX() + ITEM_SEARCH_RADIUS * 2,
            buildingPos.getY() + ITEM_SEARCH_RADIUS,
            buildingPos.getZ() + ITEM_SEARCH_RADIUS * 2
        );
        
        List<ItemEntity> items = world.getEntitiesByType(
            net.minecraft.entity.EntityType.ITEM,
            searchBox,
            entity -> entity instanceof ItemEntity && !entity.isRemoved() && !entity.getStack().isEmpty()
        );
        
        if (!items.isEmpty()) {
            // Filter out log items - only collect tree drops (saplings, sticks, apples, etc.)
            items.removeIf(item -> {
                Item itemType = item.getStack().getItem();
                String itemName = itemType.getTranslationKey().toLowerCase();
                return itemName.contains("log") || itemName.contains("wood") || itemName.contains("plank");
            });
            
            if (!items.isEmpty()) {
                // Return the closest item to building
                ItemEntity closest = items.get(0);
                double closestDist = buildingPos.getSquaredDistance(closest.getBlockPos());
                for (ItemEntity item : items) {
                    double dist = buildingPos.getSquaredDistance(item.getBlockPos());
                    if (dist < closestDist) {
                        closest = item;
                        closestDist = dist;
                    }
                }
                return closest;
            }
        }
        
        return null;
    }
    
    /**
     * Handles villager pathfinding to item.
     */
    private static void handleGoingToItem(VillagerEntity villager, CollectionTaskState state, ServerWorld world) {
        // Check if item still exists
        ItemEntity item = findItemAtPosition(state.targetItemPos, world);
        if (item == null || item.isRemoved()) {
            // Item no longer exists - just search for a new one instead of resetting
            // Don't lose collected items!
            state.phase = CollectionTaskPhase.IDLE;
            return;
        }
        
        double distanceSq = villager.getPos().squaredDistanceTo(item.getX(), item.getY(), item.getZ());
        
        // Use pickup radius - villagers can pick up items when within range
        if (distanceSq <= ITEM_PICKUP_RADIUS_SQ) {
            // Close enough to collect - transition to collecting phase
            state.phase = CollectionTaskPhase.COLLECTING_ITEM;
        } else {
            // Continue pathfinding
            if (!villager.getNavigation().isFollowingPath()) {
                villager.getNavigation().startMovingTo(item.getX(), item.getY(), item.getZ(), 1.0);
            }
        }
    }
    
    /**
     * Handles villager collecting item.
     * When villager is close enough, directly collect the item.
     */
    private static void handleCollectingItem(Settlement settlement, Building building,
                                           VillagerData villagerData, VillagerEntity villager,
                                           CollectionTaskState state, ServerWorld world) {
        // Find item near villager (not just at target position, in case it moved)
        ItemEntity item = findItemNearVillager(villager, world, ITEM_PICKUP_RADIUS);
        if (item == null || item.isRemoved()) {
            // Try to find item at original target position
            item = findItemAtPosition(state.targetItemPos, world);
            if (item == null || item.isRemoved()) {
                // Item no longer exists - continue searching
                SettlementsMod.LOGGER.debug("Item no longer exists, continuing search for villager {}", 
                    villagerData.getEntityId());
                state.phase = CollectionTaskPhase.IDLE;
                return;
            }
        }
        
        // Check if villager is close enough to collect (within pickup radius)
        double distanceSq = villager.getPos().squaredDistanceTo(item.getX(), item.getY(), item.getZ());
        if (distanceSq > ITEM_PICKUP_RADIUS_SQ) {
            // Not close enough yet - continue pathfinding at high speed
            if (!villager.getNavigation().isFollowingPath()) {
                villager.getNavigation().startMovingTo(item.getX(), item.getY(), item.getZ(), ITEM_COLLECTION_SPEED);
            }
            return;
        }
        
        // Collect the item
        ItemStack itemStack = item.getStack();
        if (itemStack.isEmpty()) {
            // Item stack is empty - just search for a new item, don't lose collected items
            state.phase = CollectionTaskPhase.IDLE;
            return;
        }
        
        // Track where this item was collected (for chaining sapling collection)
        BlockPos collectionLocation = item.getBlockPos();
        
        Item itemType = itemStack.getItem();
        Identifier itemId = Registries.ITEM.getId(itemType);
        if (itemId == null) {
            state.phase = CollectionTaskPhase.IDLE;
            return;
        }
        String itemKey = itemId.toString();
        
        // Add ALL items to villager's accumulated items (same system as farmer villager)
        // Deposit everything to chests - no composting
        int totalCount = itemStack.getCount();
        villagerData.addAccumulatedItem(itemKey, totalCount);
        SettlementManager.getInstance(world).markDirty();
        
        // Also keep a copy in collectedItems for sapling planting logic and tracking
        ItemStack chestStack = itemStack.copy();
        state.collectedItems.add(chestStack);
        
        // Check if item is a sapling and handle planting logic
        boolean isSapling = isSapling(itemType);
        if (isSapling) {
            // Record villager's current position when picking up sapling
            BlockPos villagerPos = villager.getBlockPos();
            state.lastSaplingPickupPos = villagerPos;
            
            state.saplingCount += itemStack.getCount();
            SettlementsMod.LOGGER.info("Second lumberyard villager {} collected {} saplings (total: {}/{}) at {}", 
                villagerData.getEntityId(), itemStack.getCount(), state.saplingCount, SAPLINGS_BEFORE_PLANT, villagerPos);
            
            // Every 3rd sapling, plant at the position where the sapling was picked up
            while (state.saplingCount >= SAPLINGS_BEFORE_PLANT) {
                state.saplingCount -= SAPLINGS_BEFORE_PLANT; // Subtract the count used for planting
                
                // Get the position where the sapling was picked up
                BlockPos plantPos = state.lastSaplingPickupPos;
                if (plantPos == null) {
                    // Fallback: use current position
                    plantPos = villager.getBlockPos();
                }
                
                // Check if there's already a sapling within spacing radius
                if (hasSaplingNearby(plantPos, world)) {
                    SettlementsMod.LOGGER.debug("Second lumberyard villager {} cannot plant sapling at {} - sapling already exists within {} blocks", 
                        villagerData.getEntityId(), plantPos, SAPLING_SPACING_RADIUS);
                    // Keep the sapling count (don't subtract) since we couldn't plant
                    state.saplingCount += SAPLINGS_BEFORE_PLANT;
                    break;
                }
                
                // Check if there are too many saplings in the area
                int saplingCount = countSaplingsInArea(plantPos, world);
                if (saplingCount >= MAX_SAPLINGS_IN_AREA) {
                    SettlementsMod.LOGGER.debug("Second lumberyard villager {} cannot plant sapling at {} - too many saplings in area ({} >= {})", 
                        villagerData.getEntityId(), plantPos, saplingCount, MAX_SAPLINGS_IN_AREA);
                    // Keep the sapling count (don't subtract) since we couldn't plant
                    state.saplingCount += SAPLINGS_BEFORE_PLANT;
                    break;
                }
                
                // Try to plant sapling at the pickup position
                ItemStack saplingToPlant = itemStack.copy();
                saplingToPlant.setCount(1); // Only plant one sapling
                
                if (tryPlantSaplingAtPosition(plantPos, saplingToPlant, world)) {
                    SettlementsMod.LOGGER.info("Second lumberyard villager {} planted sapling at position {}", 
                        villagerData.getEntityId(), plantPos);
                    
                    // Remove one sapling from accumulated items since we planted it (don't deposit it)
                    Identifier saplingItemId = Registries.ITEM.getId(itemType);
                    if (saplingItemId != null) {
                        String saplingKey = saplingItemId.toString();
                        int currentCount = villagerData.getAccumulatedItems().getOrDefault(saplingKey, 0);
                        if (currentCount > 0) {
                            villagerData.getAccumulatedItems().put(saplingKey, currentCount - 1);
                            if (currentCount - 1 == 0) {
                                villagerData.getAccumulatedItems().remove(saplingKey);
                            }
                            SettlementManager.getInstance(world).markDirty();
                        }
                    }
                    
                    // Remove one sapling from collected items since we planted it
                    for (ItemStack collected : state.collectedItems) {
                        if (collected.getItem() == itemType && collected.getCount() > 0) {
                            collected.decrement(1);
                            if (collected.isEmpty()) {
                                state.collectedItems.remove(collected);
                            }
                            break;
                        }
                    }
                } else {
                    SettlementsMod.LOGGER.debug("Second lumberyard villager {} could not plant sapling at position {} (invalid ground or no air above)", 
                        villagerData.getEntityId(), plantPos);
                    // Keep the sapling count (don't subtract) since we couldn't plant
                    state.saplingCount += SAPLINGS_BEFORE_PLANT;
                    break;
                }
            }
            
            // CHAINING: If we haven't collected enough saplings yet, look for nearby saplings in 3x3 area
            if (state.saplingCount < SAPLINGS_BEFORE_PLANT) {
                ItemEntity nearbySapling = findNearbySapling(villager, collectionLocation, world);
                if (nearbySapling != null) {
                    // Found a nearby sapling - chain to it immediately at high speed
                    state.targetItemPos = nearbySapling.getBlockPos();
                    state.phase = CollectionTaskPhase.GOING_TO_ITEM;
                    villager.getNavigation().startMovingTo(
                        nearbySapling.getX(),
                        nearbySapling.getY(),
                        nearbySapling.getZ(),
                        ITEM_COLLECTION_SPEED // Fast speed for chaining
                    );
                    SettlementsMod.LOGGER.debug("Second lumberyard villager {} chaining to nearby sapling at {} (collected: {}/{})", 
                        villagerData.getEntityId(), nearbySapling.getBlockPos(), state.saplingCount, SAPLINGS_BEFORE_PLANT);
                    // Don't remove the item entity yet - we'll collect it when we get there
                    return; // Exit early to continue chaining
                }
            }
        }
        
        // Remove item entity (villager has "collected" it)
        item.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        
        SettlementsMod.LOGGER.info("Second lumberyard villager {} collected {} {} (distance: {})", 
            villagerData.getEntityId(), itemStack.getCount(), itemType, String.format("%.2f", Math.sqrt(distanceSq)));
        
        // Check if threshold is reached (same as farmer villager)
        int totalAccumulated = villagerData.getTotalAccumulatedItems();
        if (totalAccumulated >= DEPOSIT_THRESHOLD) {
            // Threshold reached - let VillagerDepositSystem handle deposit
            // Mark as depositing so we don't interfere
            villagerData.setDepositing(true);
            SettlementManager.getInstance(world).markDirty();
            state.phase = CollectionTaskPhase.IDLE;
        } else {
            // Threshold not reached - continue collecting
            state.phase = CollectionTaskPhase.IDLE;
        }
    }
    
    /**
     * Finds an item entity near the villager.
     */
    private static ItemEntity findItemNearVillager(VillagerEntity villager, ServerWorld world, double radius) {
        Box searchBox = new Box(
            villager.getX() - radius,
            villager.getY() - radius,
            villager.getZ() - radius,
            villager.getX() + radius,
            villager.getY() + radius,
            villager.getZ() + radius
        );
        
        List<ItemEntity> items = world.getEntitiesByType(
            net.minecraft.entity.EntityType.ITEM,
            searchBox,
            entity -> entity instanceof ItemEntity && !entity.isRemoved() && !entity.getStack().isEmpty()
        );
        
        if (!items.isEmpty()) {
            // Filter out log items
            items.removeIf(item -> {
                Item itemType = item.getStack().getItem();
                String itemName = itemType.getTranslationKey().toLowerCase();
                return itemName.contains("log") || itemName.contains("wood") || itemName.contains("plank");
            });
            
            if (!items.isEmpty()) {
                // Return the closest item
                ItemEntity closest = items.get(0);
                double closestDist = villager.getPos().squaredDistanceTo(closest.getX(), closest.getY(), closest.getZ());
                for (ItemEntity item : items) {
                    double dist = villager.getPos().squaredDistanceTo(item.getX(), item.getY(), item.getZ());
                    if (dist < closestDist) {
                        closest = item;
                        closestDist = dist;
                    }
                }
                return closest;
            }
        }
        
        return null;
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
     * Checks if a lumberyard villager is actively working (collecting items).
     * This is used by VillagerPathfindingSystem to avoid interrupting work.
     */
    public static boolean isVillagerActivelyWorking(UUID villagerId) {
        CollectionTaskState state = TASK_STATES.get(villagerId);
        if (state == null) {
            return false;
        }
        
        // Villager is actively working if they're not idle
        return state.phase != CollectionTaskPhase.IDLE;
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
     * Deposits an item stack to chests.
     */
    private static void depositItemToChests(List<BlockPos> chestPositions, ItemStack stack, ServerWorld world) {
        for (BlockPos chestPos : chestPositions) {
            if (stack.isEmpty()) {
                break;
            }
            
            net.minecraft.block.entity.ChestBlockEntity chest = 
                (net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(chestPos);
            
            if (chest == null) {
                continue;
            }
            
            int remaining = addItemStackToChest(chest, stack);
            stack.setCount(remaining);
            chest.markDirty();
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
    
    /**
     * Finds an item entity at a specific position.
     */
    private static ItemEntity findItemAtPosition(BlockPos pos, ServerWorld world) {
        Box searchBox = new Box(pos).expand(1.0);
        List<ItemEntity> items = world.getEntitiesByType(
            net.minecraft.entity.EntityType.ITEM,
            searchBox,
            entity -> entity instanceof ItemEntity && !entity.isRemoved()
        );
        
        if (!items.isEmpty()) {
            // Find closest item to the position
            ItemEntity closest = items.get(0);
            double closestDist = pos.getSquaredDistance(closest.getBlockPos());
            for (ItemEntity item : items) {
                double dist = pos.getSquaredDistance(item.getBlockPos());
                if (dist < closestDist) {
                    closest = item;
                    closestDist = dist;
                }
            }
            return closest;
        }
        
        return null;
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
     * Finds a nearby sapling in a 3x3 area around the given location for chaining.
     * Returns the closest sapling item entity, or null if none found.
     */
    private static ItemEntity findNearbySapling(VillagerEntity villager, BlockPos centerPos, ServerWorld world) {
        // Search in a 3x3 area around the collection location
        Box searchBox = new Box(
            centerPos.getX() - SAPLING_CHAIN_RADIUS,
            centerPos.getY() - SAPLING_CHAIN_RADIUS,
            centerPos.getZ() - SAPLING_CHAIN_RADIUS,
            centerPos.getX() + SAPLING_CHAIN_RADIUS,
            centerPos.getY() + SAPLING_CHAIN_RADIUS,
            centerPos.getZ() + SAPLING_CHAIN_RADIUS
        );
        
        List<ItemEntity> items = world.getEntitiesByType(
            net.minecraft.entity.EntityType.ITEM,
            searchBox,
            entity -> {
                if (!(entity instanceof ItemEntity) || entity.isRemoved()) {
                    return false;
                }
                ItemEntity itemEntity = (ItemEntity) entity;
                ItemStack stack = itemEntity.getStack();
                if (stack.isEmpty()) {
                    return false;
                }
                // Only look for saplings
                return isSapling(stack.getItem());
            }
        );
        
        if (items.isEmpty()) {
            return null;
        }
        
        // Return the closest sapling to the villager
        ItemEntity closest = items.get(0);
        double closestDist = villager.getPos().squaredDistanceTo(closest.getX(), closest.getY(), closest.getZ());
        for (ItemEntity item : items) {
            double dist = villager.getPos().squaredDistanceTo(item.getX(), item.getY(), item.getZ());
            if (dist < closestDist) {
                closest = item;
                closestDist = dist;
            }
        }
        
        return closest;
    }
    
    /**
     * Checks if an item is a sapling.
     */
    private static boolean isSapling(Item item) {
        Identifier itemId = Registries.ITEM.getId(item);
        String path = itemId.getPath();
        return path.contains("sapling");
    }
    
    /**
     * Tries to plant a sapling at a specific position (where villager is standing).
     * Returns true if successful, false otherwise.
     */
    private static boolean tryPlantSaplingAtPosition(BlockPos pos, ItemStack saplingStack, ServerWorld world) {
        // Check if chunk is loaded
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        
        // Check if the block below is suitable ground
        BlockPos groundPos = pos.down();
        BlockState groundState = world.getBlockState(groundPos);
        boolean isSuitableGround = groundState.isOf(Blocks.GRASS_BLOCK) ||
                                  groundState.isOf(Blocks.DIRT) ||
                                  groundState.isOf(Blocks.COARSE_DIRT) ||
                                  groundState.isOf(Blocks.PODZOL) ||
                                  groundState.isOf(Blocks.MYCELIUM) ||
                                  groundState.isOf(Blocks.MUD) ||
                                  groundState.isOf(Blocks.MUDDY_MANGROVE_ROOTS) ||
                                  groundState.isOf(Blocks.FARMLAND) ||
                                  groundState.isOf(Blocks.ROOTED_DIRT);
        
        if (!isSuitableGround) {
            return false;
        }
        
        // Check if there's air at the position (space for sapling)
        BlockState aboveState = world.getBlockState(pos);
        if (!aboveState.isAir()) {
            return false;
        }
        
        // Plant the sapling
        return placeSapling(saplingStack, pos, world, pos);
    }
    
    /**
     * Counts the number of sapling blocks within the spacing radius of the given location.
     */
    private static int countSaplingsInArea(BlockPos centerPos, ServerWorld world) {
        int count = 0;
        int radius = (int) Math.ceil(SAPLING_SPACING_RADIUS);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = centerPos.add(x, y, z);
                    double distanceSq = centerPos.getSquaredDistance(checkPos);
                    if (distanceSq > SAPLING_SPACING_RADIUS_SQ) {
                        continue; // Outside radius
                    }
                    
                    // Check if chunk is loaded
                    if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    BlockState blockState = world.getBlockState(checkPos);
                    if (blockState.getBlock() instanceof SaplingBlock) {
                        count++;
                    }
                }
            }
        }
        
        return count;
    }
    
    /**
     * Checks if there's a sapling block within the spacing radius of the given position.
     */
    private static boolean hasSaplingNearby(BlockPos pos, ServerWorld world) {
        int radius = (int) Math.ceil(SAPLING_SPACING_RADIUS);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    double distanceSq = pos.getSquaredDistance(checkPos);
                    if (distanceSq > SAPLING_SPACING_RADIUS_SQ) {
                        continue; // Outside radius
                    }
                    
                    // Don't check the position itself
                    if (distanceSq == 0) {
                        continue;
                    }
                    
                    // Check if chunk is loaded
                    if (!world.getChunkManager().isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    BlockState blockState = world.getBlockState(checkPos);
                    if (blockState.getBlock() instanceof SaplingBlock) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    
    /**
     * Places a sapling at the given position.
     */
    private static boolean placeSapling(ItemStack saplingStack, BlockPos plantPos, ServerWorld world, BlockPos nearPos) {
        // Get the sapling block from the item
        Item saplingItem = saplingStack.getItem();
        net.minecraft.block.Block saplingBlock = net.minecraft.block.Block.getBlockFromItem(saplingItem);
        
        if (saplingBlock == null || !(saplingBlock instanceof SaplingBlock)) {
            SettlementsMod.LOGGER.warn("Could not get sapling block from item {}", saplingItem);
            return false;
        }
        
        // Place the sapling
        BlockState saplingState = saplingBlock.getDefaultState();
        world.setBlockState(plantPos, saplingState);
        
        SettlementsMod.LOGGER.info("Planted sapling at {} (near collection location at {})", plantPos, nearPos);
        return true;
    }
    
    /**
     * Tries to plant saplings from the villager's collected items inventory.
     * Plants every 3rd sapling collected (including those already in inventory).
     */
    private static void tryPlantSaplingsFromInventory(VillagerEntity villager, CollectionTaskState state, ServerWorld world) {
        // Count total saplings (inventory + counter)
        int totalSaplings = state.saplingCount;
        for (ItemStack stack : state.collectedItems) {
            if (isSapling(stack.getItem())) {
                totalSaplings += stack.getCount();
            }
        }
        
        // Need at least 3 saplings to plant
        if (totalSaplings < SAPLINGS_BEFORE_PLANT) {
            return;
        }
        
        // Find a sapling stack to use
        ItemStack saplingToPlant = null;
        for (ItemStack stack : state.collectedItems) {
            if (isSapling(stack.getItem()) && stack.getCount() > 0) {
                saplingToPlant = stack;
                break;
            }
        }
        
        if (saplingToPlant == null) {
            return; // No saplings in inventory
        }
        
        // Get the position where the sapling was picked up (or current position)
        BlockPos plantPos = state.lastSaplingPickupPos;
        if (plantPos == null) {
            plantPos = villager.getBlockPos();
        }
        
        // Check if there's already a sapling within spacing radius
        if (hasSaplingNearby(plantPos, world)) {
            return; // Can't plant here
        }
        
        // Check if there are too many saplings in the area
        int saplingCount = countSaplingsInArea(plantPos, world);
        if (saplingCount >= MAX_SAPLINGS_IN_AREA) {
            return; // Too many saplings already
        }
        
        // Try to plant one sapling at the pickup position
        if (tryPlantSaplingAtPosition(plantPos, saplingToPlant, world)) {
            SettlementsMod.LOGGER.info("Second lumberyard villager {} planted sapling from inventory at position {}", 
                villager.getUuid(), plantPos);
            
            // Remove one sapling from accumulated items since we planted it (don't deposit it)
            Item saplingItem = saplingToPlant.getItem();
            Identifier saplingItemId = Registries.ITEM.getId(saplingItem);
            if (saplingItemId != null) {
                String saplingKey = saplingItemId.toString();
                SettlementManager manager = SettlementManager.getInstance(world);
                VillagerData villagerData = manager.getAllSettlements().stream()
                    .flatMap(s -> s.getVillagers().stream())
                    .filter(v -> v.getEntityId().equals(villager.getUuid()))
                    .findFirst()
                    .orElse(null);
                if (villagerData != null) {
                    int currentCount = villagerData.getAccumulatedItems().getOrDefault(saplingKey, 0);
                    if (currentCount > 0) {
                        villagerData.getAccumulatedItems().put(saplingKey, currentCount - 1);
                        if (currentCount - 1 == 0) {
                            villagerData.getAccumulatedItems().remove(saplingKey);
                        }
                        manager.markDirty();
                    }
                }
            }
            
            // Remove one sapling from collected items
            saplingToPlant.decrement(1);
            if (saplingToPlant.isEmpty()) {
                state.collectedItems.remove(saplingToPlant);
            }
            // Update counter: if we had 3+ total, we planted one, so reduce counter by 3
            // But keep any remainder (e.g., if we had 5, we plant 1, counter becomes 2)
            if (state.saplingCount >= SAPLINGS_BEFORE_PLANT) {
                state.saplingCount -= SAPLINGS_BEFORE_PLANT;
            } else {
                // Counter was less than 3, so we used inventory saplings
                // Reset counter since we planted
                state.saplingCount = 0;
            }
        }
    }
    
}


