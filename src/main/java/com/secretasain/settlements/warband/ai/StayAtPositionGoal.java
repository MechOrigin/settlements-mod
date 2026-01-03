package com.secretasain.settlements.warband.ai;

import com.secretasain.settlements.warband.NpcBehaviorState;
import com.secretasain.settlements.warband.WarbandNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * AI goal for NPCs to stay at a specific position.
 */
public class StayAtPositionGoal extends Goal {
    private final WarbandNpcEntity npc;
    private final float maxDistance = 5.0f;
    
    public StayAtPositionGoal(WarbandNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        return npc.getBehaviorState() == NpcBehaviorState.STAY && npc.getStayPosition() != null;
    }
    
    @Override
    public boolean shouldContinue() {
        return npc.getBehaviorState() == NpcBehaviorState.STAY && npc.getStayPosition() != null;
    }
    
    @Override
    public void tick() {
        BlockPos stayPos = npc.getStayPosition();
        if (stayPos == null) {
            return;
        }
        
        Vec3d targetPos = Vec3d.ofBottomCenter(stayPos);
        double distance = npc.squaredDistanceTo(targetPos);
        
        // If too far from stay position, move back
        if (distance > (maxDistance * maxDistance)) {
            npc.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
        } else {
            // Stop moving if close enough
            npc.getNavigation().stop();
        }
    }
}

