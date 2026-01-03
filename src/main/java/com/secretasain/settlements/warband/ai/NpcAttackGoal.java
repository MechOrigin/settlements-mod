package com.secretasain.settlements.warband.ai;

import com.secretasain.settlements.warband.WarbandNpcEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;

/**
 * AI goal for NPCs to attack hostile mobs.
 */
public class NpcAttackGoal extends MeleeAttackGoal {
    private final WarbandNpcEntity npc;
    
    public NpcAttackGoal(WarbandNpcEntity npc, double speed, boolean pauseWhenMobIdle) {
        super(npc, speed, pauseWhenMobIdle);
        this.npc = npc;
    }
    
    @Override
    public boolean canStart() {
        // Only attack if aggressive mode is enabled
        if (!npc.isAggressive()) {
            return false;
        }
        
        return super.canStart();
    }
    
    @Override
    public boolean shouldContinue() {
        if (!npc.isAggressive()) {
            return false;
        }
        
        return super.shouldContinue();
    }
    
    @Override
    protected void attack(LivingEntity target, double squaredDistance) {
        double reachDistance = this.getSquaredMaxAttackDistance(target);
        if (squaredDistance <= reachDistance && this.getCooldown() <= 0) {
            this.resetCooldown();
            this.mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            this.mob.tryAttack(target);
        }
    }
    
    @Override
    protected double getSquaredMaxAttackDistance(LivingEntity entity) {
        return this.mob.getWidth() * 2.0f * this.mob.getWidth() * 2.0f + entity.getWidth();
    }
}

