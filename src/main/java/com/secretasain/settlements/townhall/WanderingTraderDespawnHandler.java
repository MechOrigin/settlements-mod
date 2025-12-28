package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * System for tracking and despawning wandering traders.
 * Tracks trader lifetime and despawns them after a timeout period.
 */
public class WanderingTraderDespawnHandler {
    private static final int CHECK_INTERVAL = 20; // Check every 1 second (20 ticks) - more frequent for better despawn
    private static final long TRADER_LIFETIME = 3000; // 10 minutes at 20 TPS (12000 ticks = 600 seconds)
    
    // Track traders and their spawn time
    private static final Map<UUID, Long> TRADER_SPAWN_TIMES = new HashMap<>();
    
    /**
     * Registers the despawn handler with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
        SettlementsMod.LOGGER.info("WanderingTraderDespawnHandler registered - will check every tick when traders are tracked");
    }
    
    /**
     * Records a wandering trader spawn.
     * @param trader The wandering trader entity
     */
    public static void recordTraderSpawn(WanderingTraderEntity trader) {
        if (trader != null) {
            long spawnTime = trader.getWorld().getTime();
            UUID traderId = trader.getUuid();
            TRADER_SPAWN_TIMES.put(traderId, spawnTime);
            SettlementsMod.LOGGER.info("Recorded wandering trader spawn: {} at tick {} (will despawn after {} ticks = {} minutes). Total tracked: {}", 
                traderId, spawnTime, TRADER_LIFETIME, TRADER_LIFETIME / 1200, TRADER_SPAWN_TIMES.size());
        }
    }
    
    /**
     * Removes a trader from tracking (e.g., when it despawns naturally).
     * @param traderId The trader's UUID
     */
    public static void removeTrader(UUID traderId) {
        TRADER_SPAWN_TIMES.remove(traderId);
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private static void tick(ServerWorld world) {
        if (world == null) {
            return;
        }
        
        long currentTime = world.getTime();
        boolean hasTrackedTraders = !TRADER_SPAWN_TIMES.isEmpty();
        
        // If we have tracked traders, check every tick to ensure prompt despawning
        // Otherwise, only check every CHECK_INTERVAL ticks to reduce performance impact
        if (!hasTrackedTraders && currentTime % CHECK_INTERVAL != 0) {
            return;
        }
        
        // First, scan for any wandering traders in the world that aren't tracked yet
        // This catches traders spawned by vanilla or other mods
        net.minecraft.util.math.Box worldBounds = new net.minecraft.util.math.Box(
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
        );
        java.util.List<WanderingTraderEntity> allTraders = world.getEntitiesByType(
            net.minecraft.entity.EntityType.WANDERING_TRADER, 
            worldBounds, 
            trader -> true
        );
        
        // Always log when we check (for debugging) - but reduce frequency to avoid spam
        // If we have tracked traders, log more frequently
        if (TRADER_SPAWN_TIMES.size() > 0 || allTraders.size() > 0) {
            if (currentTime % 20 == 0) { // Every second when we have traders
                SettlementsMod.LOGGER.info("Wandering trader despawn check: {} traders in world, {} tracked (world time: {})", 
                    allTraders.size(), TRADER_SPAWN_TIMES.size(), currentTime);
            }
        }
        
        // Track any traders we haven't seen before
        for (WanderingTraderEntity trader : allTraders) {
            UUID traderId = trader.getUuid();
            if (!TRADER_SPAWN_TIMES.containsKey(traderId)) {
                // New trader found - for untracked traders, set spawn time so they despawn very soon
                // Set spawn time to almost the full lifetime ago, so they expire in just a few seconds
                long estimatedSpawnTime = currentTime - TRADER_LIFETIME + 40; // Will despawn in ~2 seconds (40 ticks)
                TRADER_SPAWN_TIMES.put(traderId, estimatedSpawnTime);
                SettlementsMod.LOGGER.info("Found untracked wandering trader {}, will despawn in ~2 seconds", traderId);
            }
        }
        
        // Check ALL tracked traders - find them in the current world and check lifetime
        // This ensures we check traders even if they're not in allTraders (e.g., if getEntitiesByType missed them)
        List<UUID> tradersToDespawn = new ArrayList<>();
        List<UUID> tradersToRemove = new ArrayList<>();
        
        // Check each tracked trader
        for (Map.Entry<UUID, Long> entry : TRADER_SPAWN_TIMES.entrySet()) {
            UUID traderId = entry.getKey();
            long spawnTime = entry.getValue();
            
            // Try to find the trader in the current world
            net.minecraft.entity.Entity entity = world.getEntity(traderId);
            
            if (entity instanceof WanderingTraderEntity) {
                // Trader exists in this world - check lifetime
                long elapsed = currentTime - spawnTime;
                long remaining = TRADER_LIFETIME - elapsed;
                
                if (elapsed >= TRADER_LIFETIME) {
                    // Lifetime expired - mark for despawn
                    tradersToDespawn.add(traderId);
                    SettlementsMod.LOGGER.info("Trader {} has been alive for {} ticks (lifetime: {} ticks), marking for despawn", 
                        traderId, elapsed, TRADER_LIFETIME);
                } else if (remaining <= 100 && currentTime % 20 == 0) { // Log when less than 5 seconds remaining
                    SettlementsMod.LOGGER.info("Trader {} will despawn in {} ticks ({} seconds) - elapsed: {} ticks", 
                        traderId, remaining, remaining / 20, elapsed);
                }
            } else {
                // Trader not in this world - check if it exists in any world
                // Only do expensive check occasionally
                if (currentTime % 100 == 0) { // Every 5 seconds
                    boolean existsAnywhere = false;
                    for (net.minecraft.server.world.ServerWorld serverWorld : world.getServer().getWorlds()) {
                        if (serverWorld.getEntity(traderId) instanceof WanderingTraderEntity) {
                            existsAnywhere = true;
                            break;
                        }
                    }
                    if (!existsAnywhere) {
                        // Trader doesn't exist in any world, remove from tracking
                        tradersToRemove.add(traderId);
                        SettlementsMod.LOGGER.debug("Trader {} no longer exists in any world, removing from tracking", traderId);
                    }
                }
            }
        }
        
        // Remove traders that don't exist anywhere
        for (UUID traderId : tradersToRemove) {
            TRADER_SPAWN_TIMES.remove(traderId);
        }
        
        // Despawn expired traders
        for (UUID traderId : tradersToDespawn) {
            despawnTrader(world, traderId);
        }
        
        if (!tradersToDespawn.isEmpty()) {
            SettlementsMod.LOGGER.info("Despawning {} wandering trader(s) after lifetime expired", tradersToDespawn.size());
        }
    }
    
    /**
     * Despawns a wandering trader with enderman-style teleport effect.
     * @param world The server world
     * @param traderId The trader's UUID
     */
    private static void despawnTrader(ServerWorld world, UUID traderId) {
        try {
            net.minecraft.entity.Entity entity = world.getEntity(traderId);
            if (entity instanceof WanderingTraderEntity) {
                WanderingTraderEntity trader = (WanderingTraderEntity) entity;
                
                // Remove from tracking
                TRADER_SPAWN_TIMES.remove(traderId);
                
                // Spawn enderman teleport particles at trader location
                double x = trader.getX();
                double y = trader.getY() + trader.getHeight() / 2.0;
                double z = trader.getZ();
                
                // Spawn portal particles (enderman teleport effect)
                for (int i = 0; i < 32; i++) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                        x + (world.getRandom().nextDouble() - 0.5) * 2.0,
                        y + (world.getRandom().nextDouble() - 0.5) * 2.0,
                        z + (world.getRandom().nextDouble() - 0.5) * 2.0,
                        1, 0.0, 0.0, 0.0, 0.0);
                }
                
                // Play enderman teleport sound
                world.playSound(null, trader.getBlockPos(), 
                    net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, 
                    net.minecraft.sound.SoundCategory.NEUTRAL, 1.0f, 1.0f);
                
                // Despawn the trader
                trader.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                
                SettlementsMod.LOGGER.info("Despawned wandering trader {} after lifetime expired (enderman warp)", traderId);
            } else {
                // Entity not found or already despawned, just remove from tracking
                TRADER_SPAWN_TIMES.remove(traderId);
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error despawning wandering trader {}: {}", traderId, e.getMessage());
            // Remove from tracking anyway
            TRADER_SPAWN_TIMES.remove(traderId);
        }
    }
    
    /**
     * Gets the remaining lifetime of a trader in ticks.
     * @param traderId The trader's UUID
     * @param currentTime Current world time
     * @return Remaining lifetime in ticks, or 0 if not tracked or expired
     */
    public static long getRemainingLifetime(UUID traderId, long currentTime) {
        Long spawnTime = TRADER_SPAWN_TIMES.get(traderId);
        if (spawnTime == null) {
            return 0;
        }
        
        long elapsed = currentTime - spawnTime;
        long remaining = TRADER_LIFETIME - elapsed;
        return Math.max(0, remaining);
    }
}

