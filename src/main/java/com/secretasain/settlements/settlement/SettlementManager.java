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
     * 
     * CRITICAL: This method copies Settlement objects from persistent data to in-memory map.
     * After this, modifications to settlements should be done on the in-memory objects,
     * and saveData() will sync them back to persistent data.
     */
    private void loadData() {
        if (dataLoaded) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("SettlementManager.loadData(): Data already loaded! This should not happen.");
            return;
        }
        
        persistentData = SettlementData.getOrCreate(world);
        
        // Load settlements from persistent data
        settlements.clear();
        lecternToSettlement.clear();
        
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("SettlementManager.loadData(): Loading {} settlements from persistent data", 
        //     persistentData.getSettlements().size());
        
        for (Map.Entry<UUID, Settlement> entry : persistentData.getSettlements().entrySet()) {
            settlements.put(entry.getKey(), entry.getValue());
            // com.secretasain.settlements.SettlementsMod.LOGGER.info("  - Loaded settlement {} (lectern at {}): {} buildings", 
            //     entry.getKey(), entry.getValue().getLecternPos(), entry.getValue().getBuildings().size());
        }
        
        for (Map.Entry<BlockPos, UUID> entry : persistentData.getLecternToSettlement().entrySet()) {
            lecternToSettlement.put(entry.getKey(), entry.getValue());
        }
        
        dataLoaded = true;
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("SettlementManager.loadData(): Loaded {} settlements, {} lectern mappings", 
        //     settlements.size(), lecternToSettlement.size());
    }
    
    /**
     * Saves settlement data to persistent storage.
     * Should be called after any modifications to settlements.
     * 
     * CRITICAL: This method copies settlements from in-memory map to persistent data.
     * The in-memory settlements are the source of truth - they contain the latest data.
     * 
     * IMPORTANT: We put the SAME Settlement object references into persistent data,
     * so modifications to in-memory settlements are reflected in persistent data.
     */
    private void saveData() {
        if (!dataLoaded) {
            loadData();
        }
        
        // DEBUG: Log building counts before saving (commented out - excessive logging)
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("SettlementManager.saveData(): Saving {} settlements", settlements.size());
        // for (java.util.Map.Entry<UUID, Settlement> entry : settlements.entrySet()) {
        //     com.secretasain.settlements.SettlementsMod.LOGGER.info("  - Settlement {} (lectern at {}): {} buildings", 
        //         entry.getKey(), entry.getValue().getLecternPos(), entry.getValue().getBuildings().size());
        //     for (com.secretasain.settlements.settlement.Building building : entry.getValue().getBuildings()) {
        //         com.secretasain.settlements.SettlementsMod.LOGGER.info("    - Building {}: {} at {} (status: {})", 
        //             building.getId(), building.getStructureType(), building.getPosition(), building.getStatus());
        //     }
        // }
        
        // Sync data to persistent storage
        // CRITICAL: Clear persistent data first, then copy from in-memory map
        // This ensures persistent data has the same Settlement objects as in-memory
        // NOTE: We're putting the SAME object references, so both maps point to the same objects
        persistentData.getSettlements().clear();
        persistentData.getLecternToSettlement().clear();
        
        persistentData.getSettlements().putAll(settlements);
        persistentData.getLecternToSettlement().putAll(lecternToSettlement);
        
        persistentData.markDirty();
        
        // DEBUG: Verify buildings are in persistent data (should be same objects) (commented out - excessive logging)
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("SettlementManager.saveData(): Persistent data now has {} settlements", 
        //     persistentData.getSettlements().size());
        // for (java.util.Map.Entry<UUID, Settlement> entry : persistentData.getSettlements().entrySet()) {
        //     com.secretasain.settlements.SettlementsMod.LOGGER.info("  - Settlement {} (lectern at {}): {} buildings", 
        //         entry.getKey(), entry.getValue().getLecternPos(), entry.getValue().getBuildings().size());
        // }
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
     * 
     * CRITICAL: This method returns the Settlement object from the in-memory map.
     * This is the same object that gets modified when buildings are added, so it should
     * always have the latest data including newly added buildings.
     */
    public Settlement getSettlementByLectern(BlockPos lecternPos) {
        if (!dataLoaded) {
            loadData();
        }
        
        UUID id = lecternToSettlement.get(lecternPos);
        if (id == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("SettlementManager.getSettlementByLectern(): No settlement found for lectern at {}", lecternPos);
            return null;
        }
        
        Settlement settlement = settlements.get(id);
        if (settlement == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("SettlementManager.getSettlementByLectern(): Settlement ID {} found in mapping but not in settlements map!", id);
            return null;
        }
        
        // DEBUG: Log building count when retrieving settlement (commented out - excessive logging)
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("SettlementManager.getSettlementByLectern(): Retrieved settlement {} (lectern at {}) with {} buildings", 
        //     settlement.getId(), lecternPos, settlement.getBuildings().size());
        // for (com.secretasain.settlements.settlement.Building building : settlement.getBuildings()) {
        //     com.secretasain.settlements.SettlementsMod.LOGGER.info("  - Building {}: {} at {} (status: {})", 
        //         building.getId(), building.getStructureType(), building.getPosition(), building.getStatus());
        // }
        
        // CRITICAL: Verify this is the same object that's in persistent data (should be after saveData)
        if (persistentData != null && persistentData.getSettlements().containsKey(id)) {
            Settlement persistentSettlement = persistentData.getSettlements().get(id);
            if (persistentSettlement != settlement) {
                com.secretasain.settlements.SettlementsMod.LOGGER.error("SettlementManager.getSettlementByLectern(): WARNING! Settlement object mismatch! In-memory and persistent data have different Settlement objects for ID {}", id);
                com.secretasain.settlements.SettlementsMod.LOGGER.error("  - In-memory: {} buildings, Persistent: {} buildings", 
                    settlement.getBuildings().size(), persistentSettlement.getBuildings().size());
            }
        }
        
        return settlement;
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

