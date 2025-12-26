package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;

/**
 * Manages settlement level updates and checks.
 * Should be called periodically or when settlement stats change.
 */
public class SettlementLevelManager {
    
    /**
     * Updates the level for a settlement and checks if it can level up.
     * @param settlement The settlement to update
     * @return true if the level changed, false otherwise
     */
    public static boolean updateSettlementLevel(Settlement settlement) {
        if (settlement == null) {
            return false;
        }
        
        int oldLevel = settlement.getLevel();
        boolean levelChanged = settlement.updateLevel();
        
        if (levelChanged) {
            int newLevel = settlement.getLevel();
            SettlementsMod.LOGGER.info("Settlement {} leveled up from level {} to level {}", 
                settlement.getName(), oldLevel, newLevel);
            
            // TODO: Trigger level-up event/notifications
            // TODO: Apply level-up rewards/unlocks
        }
        
        return levelChanged;
    }
    
    /**
     * Checks if a settlement can level up without actually updating the level.
     * @param settlement The settlement to check
     * @return true if the settlement can level up
     */
    public static boolean canLevelUp(Settlement settlement) {
        if (settlement == null) {
            return false;
        }
        return settlement.canLevelUp();
    }
}

