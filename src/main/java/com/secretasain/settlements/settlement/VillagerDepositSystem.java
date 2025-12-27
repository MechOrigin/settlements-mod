package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Handles villager deposit trips to the lectern.
 * When villagers accumulate 32 items, they walk to the lectern and deposit items into nearby chests.
 */
public class VillagerDepositSystem {
    private static final int DEPOSIT_CHECK_INTERVAL_TICKS = 20; // Check every 1 second
    private static final int DEPOSIT_THRESHOLD = 32; // Items needed to trigger deposit
    private static final double LECTERN_SEARCH_RADIUS = 8.0; // Search for chests within 8 blocks of lectern
    private static final double DEPOSIT_COMPLETE_DISTANCE = 3.0; // Consider deposit complete when within 3 blocks
    
    /**
     * Registers the deposit system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     */
    private static void tick(ServerWorld world) {
        // Only run periodically
        if (world.getTime() % DEPOSIT_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        for (Settlement settlement : allSettlements) {
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Processes deposit logic for all assigned villagers in a settlement.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        for (VillagerData villagerData : settlement.getVillagers()) {
            // Only process employed and assigned villagers
            if (!villagerData.isEmployed() || !villagerData.isAssigned()) {
                continue;
            }
            
            VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
            if (villager == null) {
                continue;
            }
            
            int totalItems = villagerData.getTotalAccumulatedItems();
            
            // Check if villager should start depositing
            if (!villagerData.isDepositing() && totalItems >= DEPOSIT_THRESHOLD) {
                startDepositTrip(settlement, villagerData, villager, world);
            }
            
            // Handle ongoing deposit trips
            if (villagerData.isDepositing()) {
                handleDepositTrip(settlement, villagerData, villager, world);
            }
        }
    }
    
    /**
     * Starts a deposit trip for a villager.
     */
    private static void startDepositTrip(Settlement settlement, VillagerData villagerData, 
                                        VillagerEntity villager, ServerWorld world) {
        BlockPos lecternPos = settlement.getLecternPos();
        
        // Mark villager as depositing (this disables auto-rally in VillagerPathfindingSystem)
        villagerData.setDepositing(true);
        
        // Pathfind to lectern
        boolean pathStarted = villager.getNavigation().startMovingTo(
            lecternPos.getX() + 0.5,
            lecternPos.getY() + 0.5,
            lecternPos.getZ() + 0.5,
            1.0 // Normal speed
        );
        
        if (pathStarted) {
            SettlementsMod.LOGGER.info("Villager {} started deposit trip to lectern at {} ({} items)", 
                villagerData.getEntityId(), lecternPos, villagerData.getTotalAccumulatedItems());
        }
        
        SettlementManager.getInstance(world).markDirty();
    }
    
    /**
     * Handles an ongoing deposit trip.
     */
    private static void handleDepositTrip(Settlement settlement, VillagerData villagerData,
                                         VillagerEntity villager, ServerWorld world) {
        BlockPos lecternPos = settlement.getLecternPos();
        
        // Check if villager is close enough to lectern to deposit
        double distanceSq = villager.getPos().squaredDistanceTo(
            lecternPos.getX() + 0.5,
            lecternPos.getY() + 0.5,
            lecternPos.getZ() + 0.5
        );
        
        if (distanceSq <= DEPOSIT_COMPLETE_DISTANCE * DEPOSIT_COMPLETE_DISTANCE) {
            // Villager is at lectern, deposit items into nearby chests
            boolean deposited = depositItemsToChests(settlement, villagerData, lecternPos, world);
            
            if (deposited) {
                // Clear accumulated items and stop depositing
                villagerData.clearAccumulatedItems();
                villagerData.setDepositing(false);
                SettlementManager.getInstance(world).markDirty();
                
                SettlementsMod.LOGGER.info("Villager {} completed deposit trip", villagerData.getEntityId());
            } else {
                // No chests found, but clear items anyway (they're "deposited" to settlement storage)
                // This prevents villagers from getting stuck if no chests are placed
                depositItemsToSettlementStorage(settlement, villagerData, world);
                villagerData.clearAccumulatedItems();
                villagerData.setDepositing(false);
                SettlementManager.getInstance(world).markDirty();
                
                SettlementsMod.LOGGER.info("Villager {} deposited items to settlement storage (no chests found)", 
                    villagerData.getEntityId());
            }
        } else {
            // Continue pathfinding to lectern - keep trying until they reach it
            villager.getNavigation().startMovingTo(
                lecternPos.getX() + 0.5,
                lecternPos.getY() + 0.5,
                lecternPos.getZ() + 0.5,
                1.0
            );
        }
    }
    
    /**
     * Deposits accumulated items into nearby chests.
     * @return true if items were deposited, false if no chests found
     */
    private static boolean depositItemsToChests(Settlement settlement, VillagerData villagerData,
                                               BlockPos lecternPos, ServerWorld world) {
        // Find chests near lectern
        List<BlockPos> chestPositions = findChestsNearLectern(lecternPos, world);
        
        if (chestPositions.isEmpty()) {
            return false;
        }
        
        // Try to deposit items into chests
        Map<String, Integer> itemsToDeposit = new HashMap<>(villagerData.getAccumulatedItems());
        
        for (BlockPos chestPos : chestPositions) {
            if (itemsToDeposit.isEmpty()) {
                break; // All items deposited
            }
            
            BlockState chestState = world.getBlockState(chestPos);
            if (chestState.getBlock() instanceof ChestBlock) {
                ChestBlockEntity chestEntity = (ChestBlockEntity) world.getBlockEntity(chestPos);
                if (chestEntity != null) {
                    // Try to add items to chest
                    Iterator<Map.Entry<String, Integer>> iterator = itemsToDeposit.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Integer> entry = iterator.next();
                        String itemId = entry.getKey();
                        int count = entry.getValue();
                        
                        Identifier itemIdentifier = Identifier.tryParse(itemId);
                        if (itemIdentifier == null) {
                            iterator.remove();
                            continue;
                        }
                        
                        Item item = Registries.ITEM.get(itemIdentifier);
                        if (item == null) {
                            iterator.remove();
                            continue;
                        }
                        
                        // Try to add item stack to chest
                        ItemStack stack = new ItemStack(item, count);
                        int remaining = addItemStackToChest(chestEntity, stack);
                        
                        if (remaining < count) {
                            // Some or all items were added
                            if (remaining > 0) {
                                entry.setValue(remaining);
                            } else {
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }
        
        // If any items remain, deposit to settlement storage
        if (!itemsToDeposit.isEmpty()) {
            for (Map.Entry<String, Integer> entry : itemsToDeposit.entrySet()) {
                String itemId = entry.getKey();
                int count = entry.getValue();
                int currentCount = settlement.getMaterials().getOrDefault(itemId, 0);
                settlement.getMaterials().put(itemId, currentCount + count);
            }
        }
        
        return true;
    }
    
    /**
     * Deposits items directly to settlement storage (fallback when no chests found).
     */
    private static void depositItemsToSettlementStorage(Settlement settlement, VillagerData villagerData,
                                                        ServerWorld world) {
        for (Map.Entry<String, Integer> entry : villagerData.getAccumulatedItems().entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue();
            int currentCount = settlement.getMaterials().getOrDefault(itemId, 0);
            settlement.getMaterials().put(itemId, currentCount + count);
        }
    }
    
    /**
     * Finds chests near the lectern position.
     */
    private static List<BlockPos> findChestsNearLectern(BlockPos lecternPos, ServerWorld world) {
        List<BlockPos> chests = new ArrayList<>();
        int radius = (int) Math.ceil(LECTERN_SEARCH_RADIUS);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = lecternPos.add(x, y, z);
                    BlockState state = world.getBlockState(checkPos);
                    
                    if (state.getBlock() instanceof ChestBlock) {
                        double distance = Math.sqrt(lecternPos.getSquaredDistance(checkPos));
                        if (distance <= LECTERN_SEARCH_RADIUS) {
                            chests.add(checkPos);
                        }
                    }
                }
            }
        }
        
        return chests;
    }
    
    /**
     * Adds an ItemStack to a chest, returning the remaining count.
     */
    private static int addItemStackToChest(ChestBlockEntity chest, ItemStack stack) {
        int remaining = stack.getCount();
        
        for (int i = 0; i < chest.size() && remaining > 0; i++) {
            ItemStack slotStack = chest.getStack(i);
            
            if (slotStack.isEmpty()) {
                // Empty slot, place entire stack
                int toPlace = Math.min(remaining, stack.getMaxCount());
                chest.setStack(i, new ItemStack(stack.getItem(), toPlace));
                remaining -= toPlace;
            } else if (ItemStack.canCombine(slotStack, stack)) {
                // Same item, try to combine
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

