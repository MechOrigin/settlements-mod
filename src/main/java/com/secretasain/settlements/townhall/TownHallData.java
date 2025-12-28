package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Data class for storing town hall-specific information.
 * Stores librarian assignment, villager spawn data, and wandering trader multipliers.
 */
public class TownHallData {
    private UUID buildingId;
    private UUID assignedLibrarianId;
    private int villagerSpawnCap;
    private int currentSpawnedVillagers;
    private long lastVillagerSpawnTime;
    private int villagerSpawnInterval;
    private double wanderingTraderMultiplier;
    private List<BlockPos> workstationPositions;
    private List<UUID> spawnedVillagerIds; // Track UUIDs of villagers spawned by this town hall
    
    public TownHallData(UUID buildingId) {
        this.buildingId = buildingId;
        this.assignedLibrarianId = null;
        this.villagerSpawnCap = 5; // Default spawn cap
        this.currentSpawnedVillagers = 0;
        this.lastVillagerSpawnTime = 0;
        this.villagerSpawnInterval = 1200; // Default: 1 minute at 20 TPS (reduced for faster testing)
        this.wanderingTraderMultiplier = 1.5; // Default multiplier
        this.workstationPositions = new ArrayList<>();
        this.spawnedVillagerIds = new ArrayList<>();
    }
    
    /**
     * Gets or creates TownHallData for a building.
     * @param building The building
     * @return TownHallData instance
     */
    public static TownHallData getOrCreate(Building building) {
        // Try to load from building's custom data (preferred - persists across restarts)
        NbtCompound customData = building.getCustomData();
        if (customData != null && customData.contains("townHallData", 10)) {
            TownHallData data = fromNbt(customData.getCompound("townHallData"), building.getId());
            SettlementsMod.LOGGER.debug("Loaded TownHallData for building {} from Building.customData: hasLibrarian={}, librarianId={}", 
                building.getId(), data.hasLibrarian(), data.getAssignedLibrarianId());
            return data;
        }
        
        // Fallback: try static manager (for backwards compatibility during session)
        NbtCompound managerData = TownHallDataManager.getData(building.getId());
        if (managerData != null && managerData.contains("townHallData", 10)) {
            TownHallData data = fromNbt(managerData.getCompound("townHallData"), building.getId());
            SettlementsMod.LOGGER.debug("Loaded TownHallData for building {} from static manager: hasLibrarian={}, librarianId={}", 
                building.getId(), data.hasLibrarian(), data.getAssignedLibrarianId());
            // Migrate to Building.customData
            data.saveToBuilding(building);
            return data;
        }
        
        // Create new instance
        TownHallData data = new TownHallData(building.getId());
        // Save it immediately so it's stored
        data.saveToBuilding(building);
        SettlementsMod.LOGGER.debug("Created new TownHallData for building {} (no existing data)", building.getId());
        return data;
    }
    
    /**
     * Saves this data to the building's custom data.
     * @param building The building to save to
     */
    public void saveToBuilding(Building building) {
        // Save to Building's customData (persists across restarts)
        NbtCompound customData = building.getCustomData();
        customData.put("townHallData", this.toNbt());
        building.setCustomData(customData);
        
        // Also save to static manager for backwards compatibility
        NbtCompound wrapper = new NbtCompound();
        wrapper.put("townHallData", this.toNbt());
        TownHallDataManager.setData(building.getId(), wrapper);
        
        SettlementsMod.LOGGER.debug("Saved TownHallData for building {}: hasLibrarian={}, librarianId={}", 
            building.getId(), hasLibrarian(), getAssignedLibrarianId());
    }
    
    // Getters and Setters
    public UUID getBuildingId() {
        return buildingId;
    }
    
    public UUID getAssignedLibrarianId() {
        return assignedLibrarianId;
    }
    
    public void setAssignedLibrarianId(UUID librarianId) {
        SettlementsMod.LOGGER.debug("Setting assignedLibrarianId for building {}: {} (was: {})", 
            buildingId, librarianId, this.assignedLibrarianId);
        this.assignedLibrarianId = librarianId;
    }
    
    public boolean hasLibrarian() {
        return assignedLibrarianId != null;
    }
    
    public int getVillagerSpawnCap() {
        return villagerSpawnCap;
    }
    
    public void setVillagerSpawnCap(int cap) {
        this.villagerSpawnCap = cap;
    }
    
    public int getCurrentSpawnedVillagers() {
        return currentSpawnedVillagers;
    }
    
    public void setCurrentSpawnedVillagers(int count) {
        this.currentSpawnedVillagers = count;
    }
    
    public void incrementSpawnedVillagers() {
        this.currentSpawnedVillagers++;
    }
    
    public long getLastVillagerSpawnTime() {
        return lastVillagerSpawnTime;
    }
    
    public void setLastVillagerSpawnTime(long time) {
        this.lastVillagerSpawnTime = time;
    }
    
    public int getVillagerSpawnInterval() {
        return villagerSpawnInterval;
    }
    
    public void setVillagerSpawnInterval(int interval) {
        this.villagerSpawnInterval = interval;
    }
    
    public double getWanderingTraderMultiplier() {
        return wanderingTraderMultiplier;
    }
    
    public void setWanderingTraderMultiplier(double multiplier) {
        this.wanderingTraderMultiplier = multiplier;
    }
    
    public List<BlockPos> getWorkstationPositions() {
        return Collections.unmodifiableList(workstationPositions);
    }
    
    public void setWorkstationPositions(List<BlockPos> positions) {
        this.workstationPositions = new ArrayList<>(positions);
    }
    
    /**
     * Checks if enough time has passed since last spawn to spawn again.
     * @param currentTime Current world tick time
     * @return true if enough time has passed
     */
    public boolean canSpawnVillager(long currentTime) {
        if (lastVillagerSpawnTime == 0) {
            return true; // Never spawned, can spawn immediately
        }
        return (currentTime - lastVillagerSpawnTime) >= villagerSpawnInterval;
    }
    
    /**
     * Checks if spawn cap has been reached.
     * @return true if cap reached
     */
    public boolean isSpawnCapReached() {
        return currentSpawnedVillagers >= villagerSpawnCap;
    }
    
    /**
     * Adds a spawned villager UUID to the tracking list.
     * @param villagerId The UUID of the spawned villager
     */
    public void addSpawnedVillager(UUID villagerId) {
        if (!spawnedVillagerIds.contains(villagerId)) {
            spawnedVillagerIds.add(villagerId);
            currentSpawnedVillagers = spawnedVillagerIds.size();
        }
    }
    
    /**
     * Removes a spawned villager UUID from the tracking list (when they die/despawn).
     * @param villagerId The UUID of the villager to remove
     */
    public void removeSpawnedVillager(UUID villagerId) {
        if (spawnedVillagerIds.remove(villagerId)) {
            currentSpawnedVillagers = spawnedVillagerIds.size();
        }
    }
    
    /**
     * Gets the list of spawned villager UUIDs.
     * @return List of villager UUIDs
     */
    public List<UUID> getSpawnedVillagerIds() {
        return Collections.unmodifiableList(spawnedVillagerIds);
    }
    
    /**
     * Serializes this data to NBT.
     * @return NBT compound
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("buildingId", buildingId);
        
        if (assignedLibrarianId != null) {
            nbt.putUuid("assignedLibrarianId", assignedLibrarianId);
        }
        
        nbt.putInt("villagerSpawnCap", villagerSpawnCap);
        nbt.putInt("currentSpawnedVillagers", currentSpawnedVillagers);
        nbt.putLong("lastVillagerSpawnTime", lastVillagerSpawnTime);
        nbt.putInt("villagerSpawnInterval", villagerSpawnInterval);
        nbt.putDouble("wanderingTraderMultiplier", wanderingTraderMultiplier);
        
        // Save spawned villager UUIDs
        NbtList villagerIdList = new NbtList();
        for (UUID villagerId : spawnedVillagerIds) {
            NbtCompound idNbt = new NbtCompound();
            idNbt.putUuid("id", villagerId);
            villagerIdList.add(idNbt);
        }
        nbt.put("spawnedVillagerIds", villagerIdList);
        
        // Save workstation positions
        NbtList workstationList = new NbtList();
        for (BlockPos pos : workstationPositions) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putLong("pos", pos.asLong());
            workstationList.add(posNbt);
        }
        nbt.put("workstationPositions", workstationList);
        
        return nbt;
    }
    
    /**
     * Creates TownHallData from NBT.
     * @param nbt NBT compound
     * @param buildingId Building ID (fallback if not in NBT)
     * @return TownHallData instance
     */
    public static TownHallData fromNbt(NbtCompound nbt, UUID buildingId) {
        UUID id = nbt.containsUuid("buildingId") ? nbt.getUuid("buildingId") : buildingId;
        TownHallData data = new TownHallData(id);
        
        if (nbt.containsUuid("assignedLibrarianId")) {
            data.setAssignedLibrarianId(nbt.getUuid("assignedLibrarianId"));
        }
        
        data.setVillagerSpawnCap(nbt.contains("villagerSpawnCap") ? nbt.getInt("villagerSpawnCap") : 5);
        data.setCurrentSpawnedVillagers(nbt.contains("currentSpawnedVillagers") ? nbt.getInt("currentSpawnedVillagers") : 0);
        data.setLastVillagerSpawnTime(nbt.contains("lastVillagerSpawnTime") ? nbt.getLong("lastVillagerSpawnTime") : 0);
        data.setVillagerSpawnInterval(nbt.contains("villagerSpawnInterval") ? nbt.getInt("villagerSpawnInterval") : 6000);
        data.setWanderingTraderMultiplier(nbt.contains("wanderingTraderMultiplier") ? nbt.getDouble("wanderingTraderMultiplier") : 1.5);
        
        // Load spawned villager UUIDs
        if (nbt.contains("spawnedVillagerIds", 9)) { // 9 = NbtList
            NbtList villagerIdList = nbt.getList("spawnedVillagerIds", 10); // 10 = NbtCompound
            List<UUID> villagerIds = new ArrayList<>();
            for (int i = 0; i < villagerIdList.size(); i++) {
                NbtCompound idNbt = villagerIdList.getCompound(i);
                if (idNbt.containsUuid("id")) {
                    villagerIds.add(idNbt.getUuid("id"));
                }
            }
            data.spawnedVillagerIds = new ArrayList<>(villagerIds);
            data.currentSpawnedVillagers = villagerIds.size(); // Sync counter with list size
        }
        
        // Load workstation positions
        if (nbt.contains("workstationPositions", 9)) { // 9 = NbtList
            NbtList workstationList = nbt.getList("workstationPositions", 10); // 10 = NbtCompound
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < workstationList.size(); i++) {
                NbtCompound posNbt = workstationList.getCompound(i);
                if (posNbt.contains("pos", 4)) { // 4 = Long
                    positions.add(BlockPos.fromLong(posNbt.getLong("pos")));
                }
            }
            data.setWorkstationPositions(positions);
        }
        
        return data;
    }
}

