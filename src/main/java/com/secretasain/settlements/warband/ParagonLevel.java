package com.secretasain.settlements.warband;

import net.minecraft.text.Text;

/**
 * Paragon levels for NPCs, determining gear and power level.
 */
public enum ParagonLevel {
    /**
     * Paragon I - Entry level (stone/copper gear)
     */
    I(1, Text.translatable("settlements.warband.paragon.i")),
    
    /**
     * Paragon II - Intermediate level (iron gear)
     */
    II(10, Text.translatable("settlements.warband.paragon.ii")),
    
    /**
     * Paragon III - Advanced level (diamond gear)
     */
    III(20, Text.translatable("settlements.warband.paragon.iii")),
    
    /**
     * Paragon IV - Master level (netherite gear)
     */
    IV(30, Text.translatable("settlements.warband.paragon.iv"));
    
    private final int requiredPlayerLevel;
    private final Text displayName;
    
    ParagonLevel(int requiredPlayerLevel, Text displayName) {
        this.requiredPlayerLevel = requiredPlayerLevel;
        this.displayName = displayName;
    }
    
    /**
     * Gets the minimum player level required to hire NPCs at this paragon level.
     * @return Required player level
     */
    public int getRequiredPlayerLevel() {
        return requiredPlayerLevel;
    }
    
    public Text getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the next paragon level, or null if this is the highest level.
     * @return Next level, or null
     */
    public ParagonLevel getNext() {
        return switch (this) {
            case I -> II;
            case II -> III;
            case III -> IV;
            case IV -> null;
        };
    }
    
    /**
     * Gets the previous paragon level, or null if this is the lowest level.
     * @return Previous level, or null
     */
    public ParagonLevel getPrevious() {
        return switch (this) {
            case I -> null;
            case II -> I;
            case III -> II;
            case IV -> III;
        };
    }
}

