package com.secretasain.settlements.warband;

import net.minecraft.util.math.BlockPos;

/**
 * Represents the behavior state of an NPC.
 */
public enum NpcBehaviorState {
    FOLLOW,      // NPC follows the player
    STAY,        // NPC stays at a specific position
    RETURN       // NPC returns to barracks
}

