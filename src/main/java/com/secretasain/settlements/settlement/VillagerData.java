package com.secretasain.settlements.settlement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Data class representing a villager associated with a settlement.
 */
public class VillagerData {
    private UUID entityId;
    private BlockPos lastKnownPos;
    private String profession; // Will be replaced with VillagerProfession later
    private boolean isEmployed;
    private String name;
    private long lastSeen;
    private UUID assignedBuildingId; // Building this villager is assigned to work at (null if unassigned)
    private java.util.Map<String, Integer> accumulatedItems; // Items accumulated for deposit (item ID -> count)
    private boolean isDepositing; // Whether villager is currently on a deposit trip

    public VillagerData(UUID entityId, BlockPos lastKnownPos, String profession, boolean isEmployed, String name) {
        this.entityId = entityId;
        this.lastKnownPos = lastKnownPos;
        this.profession = profession;
        this.isEmployed = isEmployed;
        this.name = name;
        this.lastSeen = System.currentTimeMillis();
        this.assignedBuildingId = null;
        this.accumulatedItems = new java.util.HashMap<>();
        this.isDepositing = false;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    public void setLastKnownPos(BlockPos lastKnownPos) {
        this.lastKnownPos = lastKnownPos;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public boolean isEmployed() {
        return isEmployed;
    }

    public void setEmployed(boolean employed) {
        isEmployed = employed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastSeen() {
        return lastSeen;
    }
    
    public UUID getAssignedBuildingId() {
        return assignedBuildingId;
    }
    
    public void setAssignedBuildingId(UUID buildingId) {
        this.assignedBuildingId = buildingId;
    }
    
    public boolean isAssigned() {
        return assignedBuildingId != null;
    }
    
    public java.util.Map<String, Integer> getAccumulatedItems() {
        return accumulatedItems;
    }
    
    public void addAccumulatedItem(String itemId, int count) {
        accumulatedItems.put(itemId, accumulatedItems.getOrDefault(itemId, 0) + count);
    }
    
    public int getTotalAccumulatedItems() {
        return accumulatedItems.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    public void clearAccumulatedItems() {
        accumulatedItems.clear();
    }
    
    public boolean isDepositing() {
        return isDepositing;
    }
    
    public void setDepositing(boolean depositing) {
        isDepositing = depositing;
    }

    /**
     * Serializes this villager data to NBT.
     * @return NBT compound containing villager data
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("entityId", entityId);
        if (lastKnownPos != null) {
            nbt.putLong("lastKnownPos", lastKnownPos.asLong());
        }
        nbt.putString("profession", profession != null ? profession : "");
        nbt.putBoolean("isEmployed", isEmployed);
        nbt.putString("name", name != null ? name : "");
        nbt.putLong("lastSeen", lastSeen);
        if (assignedBuildingId != null) {
            nbt.putUuid("assignedBuildingId", assignedBuildingId);
        }
        
        // Save accumulated items
        net.minecraft.nbt.NbtCompound itemsNbt = new net.minecraft.nbt.NbtCompound();
        for (java.util.Map.Entry<String, Integer> entry : accumulatedItems.entrySet()) {
            itemsNbt.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("accumulatedItems", itemsNbt);
        nbt.putBoolean("isDepositing", isDepositing);
        
        return nbt;
    }

    /**
     * Creates a VillagerData instance from NBT data.
     * @param nbt NBT compound containing villager data
     * @return New VillagerData instance
     */
    public static VillagerData fromNbt(NbtCompound nbt) {
        UUID entityId = nbt.getUuid("entityId");
        BlockPos lastKnownPos = nbt.contains("lastKnownPos") ? BlockPos.fromLong(nbt.getLong("lastKnownPos")) : null;
        String profession = nbt.getString("profession");
        boolean isEmployed = nbt.getBoolean("isEmployed");
        String name = nbt.getString("name");
        long lastSeen = nbt.contains("lastSeen") ? nbt.getLong("lastSeen") : System.currentTimeMillis();
        UUID assignedBuildingId = nbt.contains("assignedBuildingId") ? nbt.getUuid("assignedBuildingId") : null;
        
        VillagerData data = new VillagerData(entityId, lastKnownPos, profession, isEmployed, name);
        data.lastSeen = lastSeen;
        data.assignedBuildingId = assignedBuildingId;
        
        // Load accumulated items
        if (nbt.contains("accumulatedItems", 10)) {
            net.minecraft.nbt.NbtCompound itemsNbt = nbt.getCompound("accumulatedItems");
            for (String key : itemsNbt.getKeys()) {
                data.accumulatedItems.put(key, itemsNbt.getInt(key));
            }
        }
        data.isDepositing = nbt.contains("isDepositing") ? nbt.getBoolean("isDepositing") : false;
        
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VillagerData that = (VillagerData) o;
        return Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }
}

