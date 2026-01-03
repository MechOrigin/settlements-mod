package com.secretasain.settlements.warband.ai;

import com.secretasain.settlements.warband.NpcBehaviorState;
import com.secretasain.settlements.warband.WarbandNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * AI goal for NPCs to follow their owner (player).
 */
public class FollowOwnerGoal extends Goal {
    private final WarbandNpcEntity npc;
    private PlayerEntity owner;
    private int updateCountdownTicks;
    private final float minDistance = 3.0f;
    
    public FollowOwnerGoal(WarbandNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        // Only follow if behavior state is FOLLOW
        if (npc.getBehaviorState() != NpcBehaviorState.FOLLOW) {
            return false;
        }
        
        // Find owner player
        if (npc.getWorld().isClient) {
            return false;
        }
        
        PlayerEntity player = npc.getWorld().getPlayerByUuid(npc.getPlayerId());
        if (player == null || player.isSpectator() || player.isInvisible()) {
            return false;
        }
        
        // Don't follow if too close
        if (npc.squaredDistanceTo(player) < (minDistance * minDistance)) {
            return false;
        }
        
        this.owner = player;
        return true;
    }
    
    @Override
    public boolean shouldContinue() {
        if (npc.getBehaviorState() != NpcBehaviorState.FOLLOW) {
            return false;
        }
        
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        
        // Stop following if too far away (64 blocks)
        if (npc.squaredDistanceTo(owner) > (64.0 * 64.0)) {
            return false;
        }
        
        // Continue if not too close
        return npc.squaredDistanceTo(owner) >= (minDistance * minDistance);
    }
    
    @Override
    public void start() {
        this.updateCountdownTicks = 0;
    }
    
    @Override
    public void stop() {
        this.owner = null;
        npc.getNavigation().stop();
    }
    
    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        
        npc.getLookControl().lookAt(owner, 10.0f, 30.0f);
        
        if (--this.updateCountdownTicks <= 0) {
            this.updateCountdownTicks = 10;
            
            if (!npc.getNavigation().isFollowingPath()) {
                Vec3d targetPos = owner.getPos();
                npc.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
            }
        }
    }
}

