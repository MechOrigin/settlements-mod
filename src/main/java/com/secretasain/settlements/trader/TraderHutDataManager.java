package com.secretasain.settlements.trader;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary manager for storing TraderHutData.
 * TODO: Integrate with Building class to store customData field properly.
 */
public class TraderHutDataManager {
    private static final Map<UUID, NbtCompound> DATA_STORAGE = new HashMap<>();
    
    /**
     * Gets stored data for a building.
     * @param buildingId Building UUID
     * @return NBT compound or null if not found
     */
    public static NbtCompound getData(UUID buildingId) {
        return DATA_STORAGE.get(buildingId);
    }
    
    /**
     * Sets data for a building.
     * @param buildingId Building UUID
     * @param data NBT compound
     */
    public static void setData(UUID buildingId, NbtCompound data) {
        DATA_STORAGE.put(buildingId, data);
    }
    
    /**
     * Removes data for a building.
     * @param buildingId Building UUID
     */
    public static void removeData(UUID buildingId) {
        DATA_STORAGE.remove(buildingId);
    }
    
    /**
     * Clears all stored data.
     */
    public static void clearAll() {
        DATA_STORAGE.clear();
    }
}

