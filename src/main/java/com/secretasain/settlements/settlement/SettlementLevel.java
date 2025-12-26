package com.secretasain.settlements.settlement;

import net.minecraft.text.Text;

/**
 * Represents a settlement level/tier.
 * Each level has requirements that must be met to progress.
 */
public enum SettlementLevel {
    LEVEL_1(1, "Hamlet", 0, 0, 0), // Starting level
    LEVEL_2(2, "Village", 5, 3, 0), // 5 villagers, 3 buildings
    LEVEL_3(3, "Town", 10, 8, 2),   // 10 villagers, 8 buildings, 2 employed
    LEVEL_4(4, "City", 20, 15, 5),  // 20 villagers, 15 buildings, 5 employed
    LEVEL_5(5, "Metropolis", 40, 30, 10); // 40 villagers, 30 buildings, 10 employed
    
    private final int level;
    private final String displayName;
    private final int requiredVillagers;
    private final int requiredBuildings;
    private final int requiredEmployedVillagers;
    
    SettlementLevel(int level, String displayName, int requiredVillagers, int requiredBuildings, int requiredEmployedVillagers) {
        this.level = level;
        this.displayName = displayName;
        this.requiredVillagers = requiredVillagers;
        this.requiredBuildings = requiredBuildings;
        this.requiredEmployedVillagers = requiredEmployedVillagers;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Text getDisplayText() {
        return Text.translatable("settlements.level." + level + ".name", displayName);
    }
    
    public int getRequiredVillagers() {
        return requiredVillagers;
    }
    
    public int getRequiredBuildings() {
        return requiredBuildings;
    }
    
    public int getRequiredEmployedVillagers() {
        return requiredEmployedVillagers;
    }
    
    /**
     * Gets the next level, or null if this is the maximum level.
     */
    public SettlementLevel getNextLevel() {
        if (this == LEVEL_5) {
            return null; // Max level
        }
        return values()[this.ordinal() + 1];
    }
    
    /**
     * Gets a level by its numeric value.
     */
    public static SettlementLevel fromLevel(int level) {
        for (SettlementLevel lvl : values()) {
            if (lvl.level == level) {
                return lvl;
            }
        }
        return LEVEL_1; // Default to level 1
    }
    
    /**
     * Calculates the current level of a settlement based on its stats.
     * Returns the highest level that the settlement qualifies for.
     */
    public static SettlementLevel calculateLevel(Settlement settlement) {
        if (settlement == null) {
            return LEVEL_1;
        }
        
        int villagerCount = settlement.getVillagers().size();
        int buildingCount = (int) settlement.getBuildings().stream()
            .filter(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED)
            .count();
        int employedCount = (int) settlement.getVillagers().stream()
            .filter(VillagerData::isEmployed)
            .count();
        
        // Check from highest to lowest level
        for (int i = values().length - 1; i >= 0; i--) {
            SettlementLevel level = values()[i];
            if (villagerCount >= level.requiredVillagers &&
                buildingCount >= level.requiredBuildings &&
                employedCount >= level.requiredEmployedVillagers) {
                return level;
            }
        }
        
        return LEVEL_1; // Default to level 1
    }
}

