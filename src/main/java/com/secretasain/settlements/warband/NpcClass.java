package com.secretasain.settlements.warband;

import net.minecraft.text.Text;

/**
 * NPC class types for warband system.
 */
public enum NpcClass {
    /**
     * Warrior class - melee combat specialist
     */
    WARRIOR(Text.translatable("settlements.warband.class.warrior")),
    
    /**
     * Priest class - support/healing specialist (not implemented yet)
     */
    PRIEST(Text.translatable("settlements.warband.class.priest")),
    
    /**
     * Mage class - ranged magic specialist (not implemented yet)
     */
    MAGE(Text.translatable("settlements.warband.class.mage"));
    
    private final Text displayName;
    
    NpcClass(Text displayName) {
        this.displayName = displayName;
    }
    
    public Text getDisplayName() {
        return displayName;
    }
    
    /**
     * Checks if this class is currently implemented.
     * @return true if implemented, false otherwise
     */
    public boolean isImplemented() {
        return this == WARRIOR; // Only warrior is implemented for now
    }
}

