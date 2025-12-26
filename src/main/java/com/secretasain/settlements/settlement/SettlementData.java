package com.secretasain.settlements.settlement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent state handler for settlement data.
 * Manages saving and loading settlement data from world save files.
 */
public class SettlementData extends PersistentState {
    private static final String DATA_KEY = "settlements";
    private static final int DATA_VERSION = 1;
    
    private final Map<UUID, Settlement> settlements;
    private final Map<BlockPos, UUID> lecternToSettlement;
    
    public SettlementData() {
        this.settlements = new HashMap<>();
        this.lecternToSettlement = new HashMap<>();
    }
    
    /**
     * Gets or creates the SettlementData for the given world.
     * @param world The server world
     * @return The SettlementData instance
     */
    public static SettlementData getOrCreate(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(
            SettlementData::fromNbt,
            SettlementData::new,
            DATA_KEY
        );
    }
    
    /**
     * Creates a SettlementData instance from NBT.
     * @param nbt NBT compound containing settlement data
     * @return New SettlementData instance
     */
    public static SettlementData fromNbt(NbtCompound nbt) {
        SettlementData data = new SettlementData();
        
        int version = nbt.contains("version") ? nbt.getInt("version") : 0;
        
        // Load settlements
        if (nbt.contains("settlements", 9)) { // 9 = NbtList type
            NbtList settlementList = nbt.getList("settlements", 10); // 10 = NbtCompound type
            for (int i = 0; i < settlementList.size(); i++) {
                NbtCompound settlementNbt = settlementList.getCompound(i);
                Settlement settlement = Settlement.fromNbt(settlementNbt);
                data.settlements.put(settlement.getId(), settlement);
                data.lecternToSettlement.put(settlement.getLecternPos(), settlement.getId());
            }
        }
        
        // Handle version migration if needed
        if (version < DATA_VERSION) {
            // Future: Add migration logic here if data format changes
        }
        
        return data;
    }
    
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("version", DATA_VERSION);
        
        // Save settlements
        NbtList settlementList = new NbtList();
        for (Settlement settlement : settlements.values()) {
            settlementList.add(settlement.toNbt());
        }
        nbt.put("settlements", settlementList);
        
        return nbt;
    }
    
    /**
     * Gets all settlements.
     * @return Map of settlement UUIDs to settlements
     */
    public Map<UUID, Settlement> getSettlements() {
        return settlements;
    }
    
    /**
     * Gets the lectern to settlement mapping.
     * @return Map of lectern positions to settlement UUIDs
     */
    public Map<BlockPos, UUID> getLecternToSettlement() {
        return lecternToSettlement;
    }
}

