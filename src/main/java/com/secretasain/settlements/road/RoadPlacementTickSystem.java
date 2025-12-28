package com.secretasain.settlements.road;

import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.Collection;

/**
 * Tick system for processing road placement tasks.
 * Runs periodically to assign road placement to unassigned villagers.
 */
public class RoadPlacementTickSystem {
    private static final int PLACEMENT_INTERVAL_TICKS = 200; // Process every 10 seconds
    
    /**
     * Registers the road placement tick system.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private static void tick(ServerWorld world) {
        // Only run periodically to avoid performance issues
        if (world.getTime() % PLACEMENT_INTERVAL_TICKS != 0) {
            return;
        }
        
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        // TODO: Road placement is buggy and needs more work - commented out for now
        // Update all active road placement tasks
        // RoadPlacementTaskManager.tick(world);
        
        // Process each settlement
        for (Settlement settlement : allSettlements) {
            // TODO: Road placement is buggy and needs more work - commented out for now
            // Process road placement (assigns new tasks to villagers)
            // RoadPlacementSystem.processRoadPlacement(settlement, world, server);
            
            // Process light post placement (less frequently - every 2 cycles = 20 seconds)
            if (world.getTime() % (PLACEMENT_INTERVAL_TICKS * 2) == 0) {
                LightPostPlacementSystem.processLightPostPlacement(settlement, world);
            }
        }
    }
}

