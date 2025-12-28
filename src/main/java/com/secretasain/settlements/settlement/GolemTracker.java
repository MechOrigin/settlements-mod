package com.secretasain.settlements.settlement;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles scanning and tracking of iron golems within settlements.
 */
public class GolemTracker {
    
    /**
     * Scans for iron golems within a settlement's radius.
     * @param settlement The settlement to scan for
     * @param world The server world
     * @return List of GolemData for golems found within the settlement radius
     */
    public static List<GolemData> scanForGolems(Settlement settlement, ServerWorld world) {
        List<GolemData> foundGolems = new ArrayList<>();
        
        if (settlement == null || world == null || settlement.getLecternPos() == null) {
            return foundGolems;
        }
        
        BlockPos center = settlement.getLecternPos();
        int radius = settlement.getRadius();
        
        // Create bounding box for efficient spatial query
        // Expand box by radius in all directions
        Box boundingBox = new Box(
            center.getX() - radius, center.getY() - radius, center.getZ() - radius,
            center.getX() + radius, center.getY() + radius, center.getZ() + radius
        );
        
        // Get all iron golems within the bounding box
        List<IronGolemEntity> golems = world.getEntitiesByType(
            EntityType.IRON_GOLEM,
            boundingBox,
            golem -> {
                // Additional filter: check if golem is within actual radius (not just bounding box)
                if (golem == null || golem.isRemoved()) {
                    return false;
                }
                BlockPos golemPos = golem.getBlockPos();
                double distanceSq = center.getSquaredDistance(golemPos);
                return distanceSq <= (radius * radius);
            }
        );
        
        // Convert IronGolemEntity to GolemData
        for (IronGolemEntity golem : golems) {
            UUID entityId = golem.getUuid();
            BlockPos lastKnownPos = golem.getBlockPos();
            
            // Get golem name (custom name or generate one)
            String name = golem.hasCustomName() 
                ? golem.getCustomName().getString() 
                : generateGolemName(golem);
            
            GolemData data = new GolemData(
                entityId,
                lastKnownPos,
                name
            );
            
            foundGolems.add(data);
        }
        
        return foundGolems;
    }
    
    /**
     * Generates a name for a golem if it doesn't have a custom name.
     * @param golem The golem entity
     * @return Generated name
     */
    private static String generateGolemName(IronGolemEntity golem) {
        // Simple name generation - use last 4 chars of UUID
        String uuidStr = golem.getUuid().toString();
        String id = uuidStr.substring(uuidStr.length() - 4);
        return "Golem #" + id;
    }
}

