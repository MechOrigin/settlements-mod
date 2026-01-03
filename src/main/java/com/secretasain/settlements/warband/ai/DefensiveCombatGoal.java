package com.secretasain.settlements.warband.ai;

import com.secretasain.settlements.warband.WarbandNpcEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * AI goal for NPCs to attack hostile mobs (only in aggressive mode or when owner is attacked).
 */
public class DefensiveCombatGoal extends ActiveTargetGoal<HostileEntity> {
    private final WarbandNpcEntity npc;
    
    public DefensiveCombatGoal(WarbandNpcEntity npc) {
        super(npc, HostileEntity.class, true);
        this.npc = npc;
    }
    
    @Override
    public boolean canStart() {
        // Only attack if aggressive mode is enabled
        if (!npc.isAggressive()) {
            return false;
        }
        
        // Find owner
        if (npc.getWorld().isClient) {
            return false;
        }
        
        UUID playerId = npc.getPlayerId();
        if (playerId == null) {
            return false;
        }
        
        PlayerEntity owner = npc.getWorld().getPlayerByUuid(playerId);
        if (owner == null) {
            return false;
        }
        
        // Only attack mobs within 24 blocks of owner (larger range for better protection)
        double distanceSq = npc.squaredDistanceTo(owner);
        boolean inRange = distanceSq < (24.0 * 24.0);
        
        if (!inRange) {
            return false;
        }
        
        // Now check if there are hostile mobs to target
        return super.canStart();
    }
    
    @Override
    public boolean shouldContinue() {
        // Stop targeting if aggressive mode is disabled
        if (!npc.isAggressive()) {
            return false;
        }
        
        return super.shouldContinue();
    }
    
    @Override
    protected double getFollowRange() {
        return 24.0; // Increased range for better protection
    }
}

