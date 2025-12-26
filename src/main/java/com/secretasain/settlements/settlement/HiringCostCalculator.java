package com.secretasain.settlements.settlement;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Calculates hiring costs for villagers based on their profession and other factors.
 */
public class HiringCostCalculator {
    
    /**
     * Base hiring cost in emeralds.
     */
    private static final int BASE_HIRING_COST = 5;
    
    /**
     * Cost multiplier for different professions.
     */
    private static int getProfessionCostMultiplier(String profession) {
        if (profession == null || profession.isEmpty()) {
            return 1;
        }
        
        String lowerProfession = profession.toLowerCase();
        
        // Specialized professions cost more
        if (lowerProfession.contains("cartographer") || lowerProfession.contains("librarian")) {
            return 2; // 10 emeralds
        }
        if (lowerProfession.contains("smith") || lowerProfession.contains("weapon") || 
            lowerProfession.contains("tool")) {
            return 2; // 10 emeralds
        }
        if (lowerProfession.contains("cleric") || lowerProfession.contains("priest")) {
            return 2; // 10 emeralds
        }
        if (lowerProfession.contains("fletcher") || lowerProfession.contains("bow")) {
            return 2; // 10 emeralds
        }
        
        // Standard professions
        if (lowerProfession.contains("farmer") || lowerProfession.contains("fisherman")) {
            return 1; // 5 emeralds
        }
        if (lowerProfession.contains("butcher") || lowerProfession.contains("leather")) {
            return 1; // 5 emeralds
        }
        if (lowerProfession.contains("mason") || lowerProfession.contains("stone")) {
            return 1; // 5 emeralds
        }
        
        // Default cost for unknown professions
        return 1; // 5 emeralds
    }
    
    /**
     * Calculates the hiring cost in emeralds for a villager.
     * @param villager The villager data
     * @return The cost in emeralds
     */
    public static int calculateHiringCost(VillagerData villager) {
        if (villager == null) {
            return BASE_HIRING_COST;
        }
        
        int multiplier = getProfessionCostMultiplier(villager.getProfession());
        return BASE_HIRING_COST * multiplier;
    }
    
    /**
     * Gets the emerald item for cost calculations.
     * @return The emerald item
     */
    public static Item getEmeraldItem() {
        return Items.EMERALD;
    }
    
    /**
     * Gets the emerald item identifier.
     * @return The emerald identifier
     */
    public static Identifier getEmeraldIdentifier() {
        return Registries.ITEM.getId(Items.EMERALD);
    }
}

