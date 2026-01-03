package com.secretasain.settlements.building;

import net.minecraft.text.Text;

/**
 * Categories for organizing structures in the UI.
 */
public enum StructureCategory {
    /**
     * Defensive structures: walls, gates, towers
     */
    DEFENSIVE(Text.translatable("settlements.structure_category.defensive")),
    
    /**
     * Residential structures: houses, apartments
     */
    RESIDENTIAL(Text.translatable("settlements.structure_category.residential")),
    
    /**
     * Commercial structures: shops, markets, trading posts
     */
    COMMERCIAL(Text.translatable("settlements.structure_category.commercial")),
    
    /**
     * Industrial structures: workshops, farms, smithies
     */
    INDUSTRIAL(Text.translatable("settlements.structure_category.industrial")),
    
    /**
     * Decorative structures: gardens, fountains, monuments
     */
    DECORATIVE(Text.translatable("settlements.structure_category.decorative")),
    
    /**
     * Capital structures: town halls, administrative buildings
     */
    CAPITAL(Text.translatable("settlements.structure_category.capital")),
    
    /**
     * Miscellaneous or uncategorized structures
     */
    MISC(Text.translatable("settlements.structure_category.misc"));
    
    private final Text displayName;
    
    StructureCategory(Text displayName) {
        this.displayName = displayName;
    }
    
    public Text getDisplayName() {
        return displayName;
    }
    
    /**
     * Determines the category of a structure based on its name.
     * @param structureName The structure name/identifier
     * @return The inferred category
     */
    public static StructureCategory fromStructureName(String structureName) {
        if (structureName == null) {
            return MISC;
        }
        
        String lower = structureName.toLowerCase();
        
        // Defensive structures
        if (lower.contains("wall") || lower.contains("gate") || lower.contains("tower") || 
            lower.contains("fort") || lower.contains("barrier") || lower.contains("fence")) {
            return DEFENSIVE;
        }
        
        // Residential structures
        if (lower.contains("house") || lower.contains("home") || lower.contains("cottage") ||
            lower.contains("apartment") || lower.contains("residence") || lower.contains("dwelling")) {
            return RESIDENTIAL;
        }
        
        // Commercial structures
        if (lower.contains("shop") || lower.contains("market") || lower.contains("store") ||
            lower.contains("trading") || lower.contains("merchant") || lower.contains("stall") ||
            lower.contains("cartographer") || lower.contains("librarian") || lower.contains("cleric")) {
            return COMMERCIAL;
        }
        
        // Industrial structures
        if (lower.contains("workshop") || lower.contains("smith") || lower.contains("forge") ||
            lower.contains("farm") || lower.contains("mill") || lower.contains("factory") ||
            lower.contains("mine") || lower.contains("quarry") || lower.contains("smithing")) {
            return INDUSTRIAL;
        }
        
        // Capital structures
        if (lower.contains("town_hall") || lower.contains("townhall") || lower.contains("capital") ||
            lower.contains("city_hall") || lower.contains("cityhall") || lower.contains("administrative")) {
            return CAPITAL;
        }
        
        // Decorative structures
        if (lower.contains("garden") || lower.contains("fountain") || lower.contains("statue") ||
            lower.contains("monument") || lower.contains("park") || lower.contains("plaza")) {
            return DECORATIVE;
        }
        
        return MISC;
    }
}

