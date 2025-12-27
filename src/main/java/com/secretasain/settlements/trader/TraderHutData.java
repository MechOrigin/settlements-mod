package com.secretasain.settlements.trader;

import com.secretasain.settlements.settlement.Building;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.*;

/**
 * Data class for storing trader hut-specific information.
 * Stores attracted wandering traders and assigned special trader villager.
 */
public class TraderHutData {
    private UUID buildingId;
    private List<UUID> attractedTraders; // Wandering trader entity IDs
    private UUID assignedVillagerId; // Special trader villager
    private boolean hasSpecialTrader;
    
    public TraderHutData(UUID buildingId) {
        this.buildingId = buildingId;
        this.attractedTraders = new ArrayList<>();
        this.assignedVillagerId = null;
        this.hasSpecialTrader = false;
    }
    
    /**
     * Gets or creates TraderHutData for a building.
     * @param building The building
     * @return TraderHutData instance
     */
    public static TraderHutData getOrCreate(Building building) {
        // Try to load from building's custom data
        NbtCompound customData = getCustomData(building);
        if (customData != null && customData.contains("traderHutData", 10)) {
            return fromNbt(customData.getCompound("traderHutData"), building.getId());
        }
        
        // Create new instance
        return new TraderHutData(building.getId());
    }
    
    /**
     * Gets custom data from building (stored in Building's NBT).
     * For now, we'll use a simple approach: store in a static map.
     * TODO: Integrate with Building class to store customData field properly.
     */
    private static NbtCompound getCustomData(Building building) {
        // This is a temporary approach - ideally Building would have a customData field
        // For now, we'll use a static map to store data
        return TraderHutDataManager.getData(building.getId());
    }
    
    /**
     * Saves this data to the building's custom data.
     * @param building The building to save to
     */
    public void saveToBuilding(Building building) {
        // Save to static manager for now
        TraderHutDataManager.setData(building.getId(), this.toNbt());
    }
    
    public UUID getBuildingId() {
        return buildingId;
    }
    
    public List<UUID> getAttractedTraders() {
        return Collections.unmodifiableList(attractedTraders);
    }
    
    public void setAttractedTraders(List<UUID> attractedTraders) {
        this.attractedTraders = new ArrayList<>(attractedTraders);
    }
    
    public UUID getAssignedVillagerId() {
        return assignedVillagerId;
    }
    
    public void setAssignedVillagerId(UUID villagerId) {
        this.assignedVillagerId = villagerId;
        this.hasSpecialTrader = (villagerId != null);
    }
    
    public boolean hasSpecialTrader() {
        return hasSpecialTrader;
    }
    
    /**
     * Serializes this data to NBT.
     * @return NBT compound
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("buildingId", buildingId);
        
        // Save attracted traders (store as NbtCompounds with UUID)
        NbtList tradersList = new NbtList();
        for (UUID traderId : attractedTraders) {
            NbtCompound traderNbt = new NbtCompound();
            traderNbt.putUuid("id", traderId);
            tradersList.add(traderNbt);
        }
        nbt.put("attractedTraders", tradersList);
        
        // Save assigned villager
        if (assignedVillagerId != null) {
            nbt.putUuid("assignedVillagerId", assignedVillagerId);
        }
        nbt.putBoolean("hasSpecialTrader", hasSpecialTrader);
        
        return nbt;
    }
    
    /**
     * Creates TraderHutData from NBT.
     * @param nbt NBT compound
     * @param buildingId Building ID (fallback if not in NBT)
     * @return TraderHutData instance
     */
    public static TraderHutData fromNbt(NbtCompound nbt, UUID buildingId) {
        UUID id = nbt.containsUuid("buildingId") ? nbt.getUuid("buildingId") : buildingId;
        TraderHutData data = new TraderHutData(id);
        
        // Load attracted traders
        if (nbt.contains("attractedTraders", 9)) { // 9 = NbtList
            NbtList tradersList = nbt.getList("attractedTraders", 10); // 10 = NbtCompound
            for (int i = 0; i < tradersList.size(); i++) {
                NbtCompound traderNbt = tradersList.getCompound(i);
                if (traderNbt.containsUuid("id")) {
                    UUID traderId = traderNbt.getUuid("id");
                    data.attractedTraders.add(traderId);
                }
            }
        }
        
        // Load assigned villager
        if (nbt.containsUuid("assignedVillagerId")) {
            data.setAssignedVillagerId(nbt.getUuid("assignedVillagerId"));
        }
        
        data.hasSpecialTrader = nbt.contains("hasSpecialTrader") ? nbt.getBoolean("hasSpecialTrader") : false;
        
        return data;
    }
}

