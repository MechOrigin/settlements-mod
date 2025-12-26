package com.secretasain.settlements.building;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages build mode handlers for all players.
 * Stores one BuildModeHandler per player.
 */
public class BuildModeManager {
    private static final Map<UUID, BuildModeHandler> handlers = new HashMap<>();
    
    /**
     * Gets or creates a BuildModeHandler for the given player.
     * @param player The server player
     * @return The BuildModeHandler for this player
     */
    public static BuildModeHandler getHandler(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return handlers.computeIfAbsent(playerId, uuid -> new BuildModeHandler(uuid));
    }
    
    /**
     * Gets the BuildModeHandler for a player by UUID.
     * @param playerId The player UUID
     * @return The BuildModeHandler, or null if not found
     */
    public static BuildModeHandler getHandler(UUID playerId) {
        return handlers.get(playerId);
    }
    
    /**
     * Removes the BuildModeHandler for a player (e.g., on disconnect).
     * @param playerId The player UUID
     */
    public static void removeHandler(UUID playerId) {
        handlers.remove(playerId);
    }
    
    /**
     * Removes all handlers (e.g., on server shutdown).
     */
    public static void clearAll() {
        handlers.clear();
    }
}

