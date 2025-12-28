package com.secretasain.settlements.townhall;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager for storing town hall data temporarily.
 * Similar to TraderHutDataManager, this provides temporary storage until
 * Building class is enhanced to support custom data fields directly.
 */
public class TownHallDataManager {
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
     * Sets stored data for a building.
     * @param buildingId Building UUID
     * @param data NBT compound to store
     */
    public static void setData(UUID buildingId, NbtCompound data) {
        DATA_STORAGE.put(buildingId, data);
    }
    
    /**
     * Removes stored data for a building.
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

