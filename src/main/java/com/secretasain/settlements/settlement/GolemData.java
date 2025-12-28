package com.secretasain.settlements.settlement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Data class representing an iron golem associated with a settlement.
 */
public class GolemData {
    private UUID entityId;
    private BlockPos lastKnownPos;
    private long lastSeen;
    private UUID assignedWallStationId; // Wall building this golem is assigned to (null if unassigned)
    private String name;

    public GolemData(UUID entityId, BlockPos lastKnownPos, String name) {
        this.entityId = entityId;
        this.lastKnownPos = lastKnownPos;
        this.name = name;
        this.lastSeen = System.currentTimeMillis();
        this.assignedWallStationId = null;
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

    public long getLastSeen() {
        return lastSeen;
    }

    public UUID getAssignedWallStationId() {
        return assignedWallStationId;
    }

    public void setAssignedWallStationId(UUID wallStationId) {
        this.assignedWallStationId = wallStationId;
    }

    public boolean isAssigned() {
        return assignedWallStationId != null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Serializes this golem data to NBT.
     * @return NBT compound containing golem data
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("entityId", entityId);
        if (lastKnownPos != null) {
            nbt.putLong("lastKnownPos", lastKnownPos.asLong());
        }
        nbt.putString("name", name != null ? name : "");
        nbt.putLong("lastSeen", lastSeen);
        if (assignedWallStationId != null) {
            nbt.putUuid("assignedWallStationId", assignedWallStationId);
        }
        return nbt;
    }

    /**
     * Creates a GolemData instance from NBT data.
     * @param nbt NBT compound containing golem data
     * @return New GolemData instance
     */
    public static GolemData fromNbt(NbtCompound nbt) {
        UUID entityId = nbt.getUuid("entityId");
        BlockPos lastKnownPos = nbt.contains("lastKnownPos") ? BlockPos.fromLong(nbt.getLong("lastKnownPos")) : null;
        String name = nbt.getString("name");
        long lastSeen = nbt.contains("lastSeen") ? nbt.getLong("lastSeen") : System.currentTimeMillis();
        UUID assignedWallStationId = nbt.contains("assignedWallStationId") ? nbt.getUuid("assignedWallStationId") : null;

        GolemData data = new GolemData(entityId, lastKnownPos, name);
        data.lastSeen = lastSeen;
        data.assignedWallStationId = assignedWallStationId;
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GolemData golemData = (GolemData) o;
        return Objects.equals(entityId, golemData.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }
}

