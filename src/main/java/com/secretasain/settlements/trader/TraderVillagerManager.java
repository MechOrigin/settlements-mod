package com.secretasain.settlements.trader;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.VillagerData;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * Manages special trader villagers assigned to trader huts.
 * Handles conversion of villagers to special traders and restoration of original professions.
 */
public class TraderVillagerManager {
    private static final Map<UUID, TraderVillagerData> TRADER_VILLAGERS = new HashMap<>();
    
    /**
     * Converts a villager to a special trader when assigned to a trader hut.
     * @param world The server world
     * @param settlement The settlement
     * @param villagerData The villager data
     * @param building The trader hut building
     * @return true if conversion was successful
     */
    public static boolean convertToSpecialTrader(ServerWorld world, Settlement settlement, 
                                                 VillagerData villagerData, Building building) {
        if (world == null || settlement == null || villagerData == null || building == null) {
            return false;
        }
        
        // Check if building is a trader hut
        if (!isTraderHut(building)) {
            SettlementsMod.LOGGER.warn("Cannot convert villager: building {} is not a trader hut", building.getId());
            return false;
        }
        
        // Get villager entity
        VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
        if (villager == null) {
            SettlementsMod.LOGGER.warn("Cannot convert villager: entity {} not found", villagerData.getEntityId());
            return false;
        }
        
        // Store original profession BEFORE changing it
        net.minecraft.village.VillagerProfession originalProfession = villager.getVillagerData().getProfession();
        if (originalProfession == null) {
            originalProfession = net.minecraft.village.VillagerProfession.NONE;
        }
        
        SettlementsMod.LOGGER.info("Villager {} original profession: {}", villager.getUuid(), 
            originalProfession != null ? net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(originalProfession) : "null");
        
        // Find the composter in the trader hut structure and set it as the villager's workstation
        net.minecraft.util.math.BlockPos composterPos = findComposterInStructure(world, building);
        
        if (composterPos != null) {
            SettlementsMod.LOGGER.info("Found composter in trader hut structure at {}", composterPos);
            
            // Set the composter as the villager's workstation
            // This makes the villager take the farmer profession
            try {
                // Use reflection or direct method call to set workstation
                // In Minecraft 1.20.1, villagers have a setWorkstation method or similar
                // For now, we'll set the profession and the villager should detect the composter
                // The composter being in the structure should be enough for the villager to detect it
                SettlementsMod.LOGGER.info("Composter found at {}, villager should detect it automatically", composterPos);
            } catch (Exception e) {
                SettlementsMod.LOGGER.warn("Could not set composter as workstation: {}", e.getMessage());
            }
        } else {
            SettlementsMod.LOGGER.warn("Could not find composter in trader hut structure at {}", building.getPosition());
        }
        
        // Set profession to farmer - villagers need a profession to trade
        // Even if they already have a profession, we'll set them to farmer for the trader hut
        net.minecraft.village.VillagerProfession farmerProfession = 
            net.minecraft.registry.Registries.VILLAGER_PROFESSION.get(
                new net.minecraft.util.Identifier("minecraft:farmer"));
        
        if (farmerProfession != null) {
            try {
                // Set profession using withProfession
                net.minecraft.village.VillagerData newData = villager.getVillagerData().withProfession(farmerProfession);
                villager.setVillagerData(newData);
                
                // Verify the change
                net.minecraft.village.VillagerProfession checkProfession = villager.getVillagerData().getProfession();
                SettlementsMod.LOGGER.info("Set villager {} to farmer profession (was: {}, now: {})", 
                    villager.getUuid(),
                    originalProfession != null ? net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(originalProfession) : "null",
                    checkProfession != null ? net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(checkProfession) : "null");
                
                // Force restock to initialize trades
                villager.restock();
                
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Failed to set villager profession: {}", e.getMessage());
                e.printStackTrace();
            }
        } else {
            SettlementsMod.LOGGER.error("Farmer profession not found in registry!");
        }
        
        // Create trader villager data (with stored original profession)
        TraderVillagerData traderData = new TraderVillagerData(villager.getUuid(), building.getId());
        traderData.setOriginalProfession(originalProfession);
        traderData.setSpecialTrader(true);
        traderData.setCustomTrades(TraderTradeLoader.getCached());
        
        // Store trader data
        TRADER_VILLAGERS.put(villagerData.getEntityId(), traderData);
        
        // Verify trade config is loaded
        TraderTradeConfig config = TraderTradeLoader.getCached();
        if (config == null) {
            SettlementsMod.LOGGER.error("Trade config not loaded! Cannot convert villager to special trader. " +
                "Make sure the server has started and trader_hut_trades.json exists.");
            // Still mark as special trader, but trades won't work until config is loaded
        } else {
            SettlementsMod.LOGGER.info("Trade config loaded with {} trades", config.getTrades().size());
        }
        
        // Immediately set custom trades for the villager
        // Note: This sets trades directly, but they may be reset on restock
        // The mixin (when working) will handle restock to maintain custom trades
        setCustomTradesForVillager(villager);
        
        // Verify trades were set
        net.minecraft.village.TradeOfferList offers = villager.getOffers();
        if (offers != null) {
            SettlementsMod.LOGGER.info("Villager {} now has {} trade offers", villager.getUuid(), offers.size());
            for (int i = 0; i < Math.min(offers.size(), 3); i++) {
                net.minecraft.village.TradeOffer offer = offers.get(i);
                try {
                    net.minecraft.item.ItemStack inputStack = offer.getAdjustedFirstBuyItem();
                    net.minecraft.item.ItemStack outputStack = offer.getSellItem();
                    SettlementsMod.LOGGER.info("  Trade {}: {} x{} -> {} x{}", i + 1,
                        net.minecraft.registry.Registries.ITEM.getId(inputStack.getItem()),
                        inputStack.getCount(),
                        net.minecraft.registry.Registries.ITEM.getId(outputStack.getItem()),
                        outputStack.getCount());
                } catch (Exception e) {
                    SettlementsMod.LOGGER.warn("  Trade {}: Could not log trade details: {}", i + 1, e.getMessage());
                }
            }
        } else {
            SettlementsMod.LOGGER.warn("Villager {} has no offers after conversion attempt", villager.getUuid());
        }
        
        // Update trader hut data
        TraderHutData hutData = TraderHutData.getOrCreate(building);
        hutData.setAssignedVillagerId(villagerData.getEntityId());
        hutData.saveToBuilding(building);
        
        SettlementsMod.LOGGER.info("Converted villager {} to special trader at building {}", 
            villagerData.getEntityId(), building.getId());
        
        return true;
    }
    
    /**
     * Restores a special trader villager to their original profession.
     * @param world The server world
     * @param settlement The settlement
     * @param villagerData The villager data
     * @param building The trader hut building
     * @return true if restoration was successful
     */
    public static boolean restoreOriginalProfession(ServerWorld world, Settlement settlement,
                                                     VillagerData villagerData, Building building) {
        if (world == null || settlement == null || villagerData == null) {
            return false;
        }
        
        TraderVillagerData traderData = TRADER_VILLAGERS.get(villagerData.getEntityId());
        if (traderData == null) {
            // Not a special trader, nothing to restore
            return true;
        }
        
        // Get villager entity
        VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
        if (villager != null) {
            // Restore original profession if it was stored
            net.minecraft.village.VillagerProfession originalProfession = traderData.getOriginalProfession();
            if (originalProfession != null) {
                // Restore to original profession (or NONE if they were jobless)
                villager.setVillagerData(villager.getVillagerData().withProfession(originalProfession));
                SettlementsMod.LOGGER.info("Restored villager {} to original profession: {}", 
                    villagerData.getEntityId(), 
                    net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(originalProfession));
            } else {
                // If no original profession stored, set to NONE (jobless)
                villager.setVillagerData(villager.getVillagerData().withProfession(net.minecraft.village.VillagerProfession.NONE));
                SettlementsMod.LOGGER.info("Restored villager {} to jobless (no original profession stored)", 
                    villagerData.getEntityId());
            }
        }
        
        // Remove trader data
        TRADER_VILLAGERS.remove(villagerData.getEntityId());
        
        // Update trader hut data
        if (building != null && isTraderHut(building)) {
            TraderHutData hutData = TraderHutData.getOrCreate(building);
            hutData.setAssignedVillagerId(null);
            hutData.saveToBuilding(building);
        }
        
        return true;
    }
    
    /**
     * Gets the trader villager data for a villager.
     * @param villagerId The villager's entity UUID
     * @return TraderVillagerData or null if not a special trader
     */
    public static TraderVillagerData getTraderData(UUID villagerId) {
        return TRADER_VILLAGERS.get(villagerId);
    }
    
    /**
     * Checks if a villager is a special trader.
     * @param villagerId The villager's entity UUID
     * @return true if the villager is a special trader
     */
    public static boolean isSpecialTrader(UUID villagerId) {
        return TRADER_VILLAGERS.containsKey(villagerId);
    }
    
    /**
     * Sets custom trades for a special trader villager.
     * @param villager The villager entity
     */
    private static void setCustomTradesForVillager(VillagerEntity villager) {
        if (villager == null) {
            SettlementsMod.LOGGER.warn("Cannot set custom trades: villager is null");
            return;
        }
        
        // Get custom trade config
        TraderTradeConfig config = TraderTradeLoader.getCached();
        if (config == null || config.getTrades().isEmpty()) {
            SettlementsMod.LOGGER.warn("No custom trades loaded for special trader {} (config is null or empty)", villager.getUuid());
            return;
        }
        
        SettlementsMod.LOGGER.info("Setting custom trades for villager {} ({} trades in config)", 
            villager.getUuid(), config.getTrades().size());
        
        // Get or create offers list
        net.minecraft.village.TradeOfferList offers = villager.getOffers();
        if (offers == null) {
            SettlementsMod.LOGGER.warn("Villager {} has no offers list yet, trying to force restock", villager.getUuid());
            // Try to force restock to initialize offers
            try {
                villager.restock();
                offers = villager.getOffers();
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Failed to restock villager {}: {}", villager.getUuid(), e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (offers == null) {
            SettlementsMod.LOGGER.error("Cannot set custom trades: villager {} still has no offers after restock", villager.getUuid());
            return;
        }
        
        // Clear existing offers and add custom ones
        int oldSize = offers.size();
        offers.clear();
        
        int addedCount = 0;
        for (com.secretasain.settlements.trader.TradeOfferConfig tradeConfig : config.getTrades()) {
            SettlementsMod.LOGGER.info("Creating trade offer: {} x{} -> {} x{}", 
                tradeConfig.getInputItem(), tradeConfig.getInputCount(),
                tradeConfig.getOutputItem(), tradeConfig.getOutputCount());
            net.minecraft.village.TradeOffer offer = com.secretasain.settlements.trader.TradeOfferHelper.createTradeOffer(tradeConfig);
            if (offer != null) {
                offers.add(offer);
                addedCount++;
                SettlementsMod.LOGGER.info("Successfully added trade offer: {} x{} -> {} x{}", 
                    tradeConfig.getInputItem(), tradeConfig.getInputCount(),
                    tradeConfig.getOutputItem(), tradeConfig.getOutputCount());
            } else {
                SettlementsMod.LOGGER.error("Failed to create trade offer from config: input={}, output={}", 
                    tradeConfig.getInputItem(), tradeConfig.getOutputItem());
            }
        }
        
        SettlementsMod.LOGGER.info("Set {} custom trades for special trader {} (cleared {} old trades)", 
            addedCount, villager.getUuid(), oldSize);
    }
    
    /**
     * Gets a villager entity from the world.
     * @param world The server world
     * @param villagerId The villager's entity UUID
     * @return VillagerEntity or null if not found
     */
    private static VillagerEntity getVillagerEntity(ServerWorld world, UUID villagerId) {
        if (world == null || villagerId == null) {
            return null;
        }
        
        try {
            net.minecraft.entity.Entity entity = world.getEntity(villagerId);
            if (entity instanceof VillagerEntity) {
                return (VillagerEntity) entity;
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error getting villager entity {}: {}", villagerId, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Finds the composter in a trader hut structure.
     * @param world The server world
     * @param building The trader hut building
     * @return BlockPos of the composter, or null if not found
     */
    private static net.minecraft.util.math.BlockPos findComposterInStructure(ServerWorld world, Building building) {
        try {
            // Load the structure
            com.secretasain.settlements.building.StructureData structureData = 
                com.secretasain.settlements.building.StructureLoader.loadStructure(
                    building.getStructureType(), 
                    world.getServer()
                );
            
            if (structureData == null) {
                SettlementsMod.LOGGER.warn("Could not load structure data for trader hut");
                return null;
            }
            
            // Find composter in structure blocks
            for (com.secretasain.settlements.building.StructureBlock block : structureData.getBlocks()) {
                if (block.getBlockState().isOf(net.minecraft.block.Blocks.COMPOSTER)) {
                    // Found composter! Apply rotation and calculate world position
                    net.minecraft.util.math.BlockPos relativePos = block.getRelativePos();
                    net.minecraft.util.math.BlockPos rotatedPos = applyRotation(relativePos, building.getRotation(), structureData.getDimensions());
                    net.minecraft.util.math.BlockPos worldPos = building.getPosition().add(rotatedPos);
                    
                    SettlementsMod.LOGGER.info("Found composter at relative pos {} -> world pos {}", relativePos, worldPos);
                    return worldPos;
                }
            }
            
            SettlementsMod.LOGGER.warn("No composter found in trader hut structure");
            return null;
            
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Error finding composter in structure: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Applies rotation to a block position.
     * @param pos Relative position
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @param dimensions Structure dimensions
     * @return Rotated position
     */
    private static net.minecraft.util.math.BlockPos applyRotation(net.minecraft.util.math.BlockPos pos, int rotation, net.minecraft.util.math.Vec3i dimensions) {
        int x = pos.getX();
        int z = pos.getZ();
        
        switch (rotation) {
            case 90:
                return new net.minecraft.util.math.BlockPos(-z, pos.getY(), x);
            case 180:
                return new net.minecraft.util.math.BlockPos(-x, pos.getY(), -z);
            case 270:
                return new net.minecraft.util.math.BlockPos(z, pos.getY(), -x);
            case 0:
            default:
                return pos;
        }
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
        return structurePath.contains("trader_hut") || structurePath.contains("traderhut");
    }
    
    /**
     * Saves all trader villager data to NBT.
     * @return NBT compound containing all trader data
     */
    public static NbtCompound saveAll() {
        NbtCompound nbt = new NbtCompound();
        net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
        
        for (TraderVillagerData data : TRADER_VILLAGERS.values()) {
            list.add(data.toNbt());
        }
        
        nbt.put("traderVillagers", list);
        return nbt;
    }
    
    /**
     * Loads all trader villager data from NBT.
     * @param nbt NBT compound containing trader data
     */
    public static void loadAll(NbtCompound nbt) {
        TRADER_VILLAGERS.clear();
        
        if (nbt.contains("traderVillagers", 9)) { // 9 = NbtList
            net.minecraft.nbt.NbtList list = nbt.getList("traderVillagers", 10); // 10 = NbtCompound
            for (int i = 0; i < list.size(); i++) {
                NbtCompound dataNbt = list.getCompound(i);
                TraderVillagerData data = TraderVillagerData.fromNbt(dataNbt);
                if (data != null) {
                    TRADER_VILLAGERS.put(data.getVillagerId(), data);
                }
            }
        }
    }
}

