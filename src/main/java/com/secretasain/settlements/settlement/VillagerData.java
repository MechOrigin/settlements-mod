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

    public VillagerData(UUID entityId, BlockPos lastKnownPos, String profession, boolean isEmployed, String name) {
        this.entityId = entityId;
        this.lastKnownPos = lastKnownPos;
        this.profession = profession;
        this.isEmployed = isEmployed;
        this.name = name;
        this.lastSeen = System.currentTimeMillis();
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
        
        VillagerData data = new VillagerData(entityId, lastKnownPos, profession, isEmployed, name);
        data.lastSeen = lastSeen;
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

