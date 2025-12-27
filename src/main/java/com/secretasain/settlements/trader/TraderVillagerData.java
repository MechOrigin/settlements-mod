package com.secretasain.settlements.trader;

import com.secretasain.settlements.SettlementsMod;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

import java.util.UUID;

/**
 * Data class for storing special trader villager information.
 * Tracks which villager is assigned to a trader hut and their custom trade configuration.
 */
public class TraderVillagerData {
    private UUID villagerId;
    private UUID buildingId;
    private VillagerProfession originalProfession;
    private TraderTradeConfig customTrades;
    private boolean isSpecialTrader;
    
    public TraderVillagerData(UUID villagerId, UUID buildingId) {
        this.villagerId = villagerId;
        this.buildingId = buildingId;
        this.originalProfession = null;
        this.customTrades = null;
        this.isSpecialTrader = false;
    }
    
    public UUID getVillagerId() {
        return villagerId;
    }
    
    public UUID getBuildingId() {
        return buildingId;
    }
    
    public VillagerProfession getOriginalProfession() {
        return originalProfession;
    }
    
    public void setOriginalProfession(VillagerProfession profession) {
        this.originalProfession = profession;
    }
    
    public TraderTradeConfig getCustomTrades() {
        return customTrades;
    }
    
    public void setCustomTrades(TraderTradeConfig trades) {
        this.customTrades = trades;
    }
    
    public boolean isSpecialTrader() {
        return isSpecialTrader;
    }
    
    public void setSpecialTrader(boolean specialTrader) {
        this.isSpecialTrader = specialTrader;
    }
    
    /**
     * Creates TraderVillagerData from a villager entity.
     * Stores the villager's current profession as the original profession.
     * @param villager The villager entity
     * @param buildingId The trader hut building ID
     * @return TraderVillagerData instance
     */
    public static TraderVillagerData fromVillager(VillagerEntity villager, UUID buildingId) {
        TraderVillagerData data = new TraderVillagerData(villager.getUuid(), buildingId);
        // Note: Original profession should be stored BEFORE changing it in TraderVillagerManager
        // This method is called after profession change, so we need to get it from the stored value
        // For now, we'll store the current profession, but it should already be farmer
        net.minecraft.village.VillagerProfession currentProfession = villager.getVillagerData().getProfession();
        data.setOriginalProfession(currentProfession);
        data.setSpecialTrader(true);
        
        // Load custom trades from config
        TraderTradeConfig trades = TraderTradeLoader.getCached();
        if (trades == null) {
            SettlementsMod.LOGGER.warn("Custom trades not loaded for trader villager {}", villager.getUuid());
        } else {
            data.setCustomTrades(trades);
        }
        
        return data;
    }
    
    /**
     * Serializes this data to NBT.
     * @return NBT compound
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("villagerId", villagerId);
        nbt.putUuid("buildingId", buildingId);
        
        if (originalProfession != null) {
            Identifier professionId = Registries.VILLAGER_PROFESSION.getId(originalProfession);
            if (professionId != null) {
                nbt.putString("originalProfession", professionId.toString());
            }
        }
        
        nbt.putBoolean("isSpecialTrader", isSpecialTrader);
        
        return nbt;
    }
    
    /**
     * Creates TraderVillagerData from NBT.
     * @param nbt NBT compound
     * @return TraderVillagerData instance
     */
    public static TraderVillagerData fromNbt(NbtCompound nbt) {
        UUID villagerId = nbt.getUuid("villagerId");
        UUID buildingId = nbt.getUuid("buildingId");
        
        TraderVillagerData data = new TraderVillagerData(villagerId, buildingId);
        
        if (nbt.contains("originalProfession", 8)) { // 8 = String
            String professionStr = nbt.getString("originalProfession");
            Identifier professionId = Identifier.tryParse(professionStr);
            if (professionId != null) {
                VillagerProfession profession = Registries.VILLAGER_PROFESSION.get(professionId);
                if (profession != null) {
                    data.setOriginalProfession(profession);
                }
            }
        }
        
        data.setSpecialTrader(nbt.contains("isSpecialTrader") ? nbt.getBoolean("isSpecialTrader") : false);
        
        // Load custom trades (they're cached globally, so just get from cache)
        data.setCustomTrades(TraderTradeLoader.getCached());
        
        return data;
    }
}

