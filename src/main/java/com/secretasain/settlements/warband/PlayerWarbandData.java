package com.secretasain.settlements.warband;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

/**
 * Persistent state for storing player warband data (hired NPCs).
 * Stored per-world, keyed by player UUID.
 */
public class PlayerWarbandData extends PersistentState {
    private final Map<UUID, List<NpcData>> playerWarbands = new HashMap<>();
    
    /**
     * Gets all player IDs that have warbands.
     */
    public java.util.Set<UUID> getAllPlayerIds() {
        return new java.util.HashSet<>(playerWarbands.keySet());
    }
    
    public PlayerWarbandData() {
        super();
    }
    
    /**
     * Gets or creates the PlayerWarbandData for a world.
     */
    public static PlayerWarbandData getOrCreate(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(
            PlayerWarbandData::fromNbt,
            PlayerWarbandData::new,
            "settlements_warband_data"
        );
    }
    
    /**
     * Adds an NPC to a player's warband.
     */
    public void addNpc(UUID playerId, NpcData npcData) {
        playerWarbands.computeIfAbsent(playerId, k -> new ArrayList<>()).add(npcData);
        markDirty();
    }
    
    /**
     * Removes an NPC from a player's warband.
     */
    public void removeNpc(UUID playerId, UUID entityId) {
        List<NpcData> warband = playerWarbands.get(playerId);
        if (warband != null) {
            warband.removeIf(npc -> npc.getEntityId().equals(entityId));
            if (warband.isEmpty()) {
                playerWarbands.remove(playerId);
            }
            markDirty();
        }
    }
    
    /**
     * Gets all NPCs for a player.
     */
    public List<NpcData> getPlayerWarband(UUID playerId) {
        return playerWarbands.getOrDefault(playerId, new ArrayList<>());
    }
    
    /**
     * Gets an NPC by entity ID.
     */
    public NpcData getNpcByEntityId(UUID entityId) {
        for (List<NpcData> warband : playerWarbands.values()) {
            for (NpcData npc : warband) {
                if (npc.getEntityId().equals(entityId)) {
                    return npc;
                }
            }
        }
        return null;
    }
    
    /**
     * Checks if a player has already hired an NPC of a specific class at a barracks.
     */
    public boolean hasNpcAtBarracks(UUID playerId, UUID barracksId, NpcClass npcClass) {
        List<NpcData> warband = playerWarbands.get(playerId);
        if (warband == null) {
            return false;
        }
        return warband.stream()
            .anyMatch(npc -> npc.getBarracksBuildingId().equals(barracksId) && 
                           npc.getNpcClass() == npcClass && 
                           npc.isHired());
    }
    
    /**
     * Checks if a player has a specific NPC by entity ID.
     */
    public boolean hasNpc(UUID playerId, UUID entityId) {
        List<NpcData> warband = playerWarbands.get(playerId);
        if (warband == null) {
            return false;
        }
        return warband.stream()
            .anyMatch(npc -> npc.getEntityId().equals(entityId));
    }
    
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound warbandsNbt = new NbtCompound();
        for (Map.Entry<UUID, List<NpcData>> entry : playerWarbands.entrySet()) {
            NbtList npcList = new NbtList();
            for (NpcData npc : entry.getValue()) {
                npcList.add(npc.toNbt());
            }
            warbandsNbt.put(entry.getKey().toString(), npcList);
        }
        nbt.put("warbands", warbandsNbt);
        return nbt;
    }
    
    public static PlayerWarbandData fromNbt(NbtCompound nbt) {
        PlayerWarbandData data = new PlayerWarbandData();
        if (nbt.contains("warbands")) {
            NbtCompound warbandsNbt = nbt.getCompound("warbands");
            for (String playerIdStr : warbandsNbt.getKeys()) {
                UUID playerId = UUID.fromString(playerIdStr);
                NbtList npcList = warbandsNbt.getList(playerIdStr, 10); // 10 = NBT_COMPOUND
                List<NpcData> warband = new ArrayList<>();
                for (int i = 0; i < npcList.size(); i++) {
                    warband.add(NpcData.fromNbt(npcList.getCompound(i)));
                }
                data.playerWarbands.put(playerId, warband);
            }
        }
        return data;
    }
}

