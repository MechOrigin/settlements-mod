package com.secretasain.settlements.settlement;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Manages all settlements in a world.
 * Provides methods to create, retrieve, and manage settlements.
 */
public class SettlementManager {
    private static final Map<ServerWorld, SettlementManager> INSTANCES = new HashMap<>();
    
    private final ServerWorld world;
    private final Map<UUID, Settlement> settlements;
    private final Map<BlockPos, UUID> lecternToSettlement;
    private SettlementData persistentData;
    private boolean dataLoaded;

    private SettlementManager(ServerWorld world) {
        this.world = world;
        this.settlements = new HashMap<>();
        this.lecternToSettlement = new HashMap<>();
        this.dataLoaded = false;
    }
    
    /**
     * Loads settlement data from persistent storage.
     * Should be called once when the manager is first accessed.
     */
    private void loadData() {
        if (dataLoaded) {
            return;
        }
        
        persistentData = SettlementData.getOrCreate(world);
        
        // Load settlements from persistent data
        settlements.clear();
        lecternToSettlement.clear();
        
        for (Map.Entry<UUID, Settlement> entry : persistentData.getSettlements().entrySet()) {
            settlements.put(entry.getKey(), entry.getValue());
        }
        
        for (Map.Entry<BlockPos, UUID> entry : persistentData.getLecternToSettlement().entrySet()) {
            lecternToSettlement.put(entry.getKey(), entry.getValue());
        }
        
        dataLoaded = true;
    }
    
    /**
     * Saves settlement data to persistent storage.
     * Should be called after any modifications to settlements.
     */
    private void saveData() {
        if (!dataLoaded) {
            loadData();
        }
        
        // Sync data to persistent storage
        persistentData.getSettlements().clear();
        persistentData.getLecternToSettlement().clear();
        
        persistentData.getSettlements().putAll(settlements);
        persistentData.getLecternToSettlement().putAll(lecternToSettlement);
        
        persistentData.markDirty();
    }

    /**
     * Gets the SettlementManager instance for the given world.
     * @param world The server world
     * @return The SettlementManager for this world
     */
    public static SettlementManager getInstance(ServerWorld world) {
        SettlementManager manager = INSTANCES.computeIfAbsent(world, SettlementManager::new);
        if (!manager.dataLoaded) {
            manager.loadData();
        }
        return manager;
    }

    /**
     * Creates a new settlement at the given lectern position.
     * @param lecternPos Position of the lectern block
     * @param name Name of the settlement
     * @param radius Radius of the settlement in blocks
     * @return The newly created settlement
     */
    public Settlement createSettlement(BlockPos lecternPos, String name, int radius) {
        if (!dataLoaded) {
            loadData();
        }
        
        UUID id = UUID.randomUUID();
        Settlement settlement = new Settlement(id, lecternPos, radius, name != null ? name : "Settlement");
        
        settlements.put(id, settlement);
        lecternToSettlement.put(lecternPos, id);
        
        saveData();
        
        return settlement;
    }

    /**
     * Gets a settlement by its UUID.
     * @param id The settlement UUID
     * @return The settlement, or null if not found
     */
    public Settlement getSettlement(UUID id) {
        return settlements.get(id);
    }

    /**
     * Gets a settlement by the lectern position.
     * @param lecternPos Position of the lectern block
     * @return The settlement, or null if not found
     */
    public Settlement getSettlementByLectern(BlockPos lecternPos) {
        UUID id = lecternToSettlement.get(lecternPos);
        if (id == null) {
            return null;
        }
        return settlements.get(id);
    }

    /**
     * Removes a settlement.
     * @param id The settlement UUID
     * @return true if the settlement was removed, false if it didn't exist
     */
    public boolean removeSettlement(UUID id) {
        if (!dataLoaded) {
            loadData();
        }
        
        Settlement settlement = settlements.remove(id);
        if (settlement != null) {
            lecternToSettlement.remove(settlement.getLecternPos());
            saveData();
            return true;
        }
        return false;
    }
    
    /**
     * Marks settlement data as dirty, triggering a save.
     * Call this after modifying settlement data directly.
     */
    public void markDirty() {
        if (dataLoaded) {
            saveData();
        }
    }

    /**
     * Gets all settlements in this world.
     * @return Collection of all settlements
     */
    public Collection<Settlement> getAllSettlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    /**
     * Finds the settlement that contains the given position.
     * @param pos The position to check
     * @return The settlement containing this position, or null if none found
     */
    public Settlement findSettlementAt(BlockPos pos) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.isWithinBounds(pos)) {
                return settlement;
            }
        }
        return null;
    }
    
    /**
     * Triggers an immediate villager scan for a specific settlement.
     * Useful for manual refresh from UI.
     * @param settlement The settlement to scan
     */
    public void scanVillagersFor(Settlement settlement) {
        if (settlement == null || world == null) {
            return;
        }
        
        List<VillagerData> foundVillagers = VillagerTracker.scanForVillagers(settlement, world);
        
        // Update settlement's villager list
        Map<UUID, VillagerData> existingVillagers = new HashMap<>();
        for (VillagerData villager : settlement.getVillagers()) {
            existingVillagers.put(villager.getEntityId(), villager);
        }
        
        // Update or add found villagers
        for (VillagerData found : foundVillagers) {
            UUID entityId = found.getEntityId();
            if (existingVillagers.containsKey(entityId)) {
                // Update existing villager (position, last seen)
                VillagerData existing = existingVillagers.get(entityId);
                existing.setLastKnownPos(found.getLastKnownPos());
            } else {
                // Add new villager
                settlement.getVillagers().add(found);
            }
        }
        
        // Mark as dirty to trigger save
        markDirty();
    }
}

