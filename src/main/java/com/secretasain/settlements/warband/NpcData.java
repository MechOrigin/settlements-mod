package com.secretasain.settlements.warband;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Data class representing a hired NPC in a player's warband.
 */
public class NpcData {
    private UUID entityId; // UUID of the NPC entity in the world
    private UUID playerId; // UUID of the player who hired this NPC
    private NpcClass npcClass;
    private ParagonLevel paragonLevel;
    private UUID barracksBuildingId; // Building ID of the barracks this NPC belongs to
    private BlockPos barracksPosition; // Position of the barracks
    private boolean isHired; // Whether NPC is currently hired/active
    private long hiredTime; // Timestamp when NPC was hired
    
    public NpcData(UUID entityId, UUID playerId, NpcClass npcClass, ParagonLevel paragonLevel, 
                   UUID barracksBuildingId, BlockPos barracksPosition) {
        this.entityId = entityId;
        this.playerId = playerId;
        this.npcClass = npcClass;
        this.paragonLevel = paragonLevel;
        this.barracksBuildingId = barracksBuildingId;
        this.barracksPosition = barracksPosition;
        this.isHired = true;
        this.hiredTime = System.currentTimeMillis();
    }
    
    public UUID getEntityId() {
        return entityId;
    }
    
    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public NpcClass getNpcClass() {
        return npcClass;
    }
    
    public ParagonLevel getParagonLevel() {
        return paragonLevel;
    }
    
    public UUID getBarracksBuildingId() {
        return barracksBuildingId;
    }
    
    public BlockPos getBarracksPosition() {
        return barracksPosition;
    }
    
    public boolean isHired() {
        return isHired;
    }
    
    public void setHired(boolean hired) {
        this.isHired = hired;
    }
    
    public long getHiredTime() {
        return hiredTime;
    }
    
    /**
     * Writes NPC data to NBT.
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("entityId", entityId);
        nbt.putUuid("playerId", playerId);
        nbt.putString("npcClass", npcClass.name());
        nbt.putString("paragonLevel", paragonLevel.name());
        nbt.putUuid("barracksBuildingId", barracksBuildingId);
        nbt.putLong("barracksPosX", barracksPosition.getX());
        nbt.putLong("barracksPosY", barracksPosition.getY());
        nbt.putLong("barracksPosZ", barracksPosition.getZ());
        nbt.putBoolean("isHired", isHired);
        nbt.putLong("hiredTime", hiredTime);
        return nbt;
    }
    
    /**
     * Reads NPC data from NBT.
     */
    public static NpcData fromNbt(NbtCompound nbt) {
        UUID entityId = nbt.getUuid("entityId");
        UUID playerId = nbt.getUuid("playerId");
        NpcClass npcClass = NpcClass.valueOf(nbt.getString("npcClass"));
        ParagonLevel paragonLevel = ParagonLevel.valueOf(nbt.getString("paragonLevel"));
        UUID barracksBuildingId = nbt.getUuid("barracksBuildingId");
        BlockPos barracksPosition = new BlockPos(
            (int) nbt.getLong("barracksPosX"),
            (int) nbt.getLong("barracksPosY"),
            (int) nbt.getLong("barracksPosZ")
        );
        
        NpcData data = new NpcData(entityId, playerId, npcClass, paragonLevel, barracksBuildingId, barracksPosition);
        data.setHired(nbt.getBoolean("isHired"));
        data.hiredTime = nbt.getLong("hiredTime");
        return data;
    }
}

