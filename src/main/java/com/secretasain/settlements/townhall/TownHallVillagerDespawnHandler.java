package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * System for tracking and despawning attracted villagers from town halls.
 * Villagers have a 50/50 chance to stay or leave (enderman teleport out).
 * This creates a cycling effect where 2-3 villagers are visible at any time.
 */
public class TownHallVillagerDespawnHandler {
    private static final int CHECK_INTERVAL = 20; // Check every 1 second (20 ticks)
    private static final long VILLAGER_LIFETIME = 1200; // 1 minute at 20 TPS (1200 ticks = 60 seconds)
    private static final double STAY_CHANCE = 0.5; // 50% chance to stay, 50% chance to leave
    
    // Track attracted villagers: Map<VillagerUUID, AttractionData>
    private static final Map<UUID, AttractionData> ATTRACTED_VILLAGERS = new HashMap<>();
    
    /**
     * Data for tracking an attracted villager.
     */
    private static class AttractionData {
        final UUID villagerId;
        final UUID townHallId;
        final long spawnTime;
        final boolean willStay; // Determined at spawn time (50/50 chance)
        
        AttractionData(UUID villagerId, UUID townHallId, long spawnTime) {
            this.villagerId = villagerId;
            this.townHallId = townHallId;
            this.spawnTime = spawnTime;
            this.willStay = new Random().nextDouble() < STAY_CHANCE; // 50/50 chance
        }
    }
    
    /**
     * Registers the despawn handler with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
        SettlementsMod.LOGGER.info("TownHallVillagerDespawnHandler registered - will check every tick when villagers are tracked");
    }
    
    /**
     * Records an attracted villager spawn.
     * @param villager The villager entity
     * @param townHallId The town hall building ID that attracted this villager
     */
    public static void recordAttractedVillager(VillagerEntity villager, UUID townHallId) {
        if (villager != null && townHallId != null) {
            long spawnTime = villager.getWorld().getTime();
            UUID villagerId = villager.getUuid();
            AttractionData data = new AttractionData(villagerId, townHallId, spawnTime);
            ATTRACTED_VILLAGERS.put(villagerId, data);
            
            SettlementsMod.LOGGER.info("Recorded attracted villager {} for town hall {} at tick {} (will {} after {} ticks = {} seconds). Total tracked: {}", 
                villagerId, townHallId, spawnTime, 
                data.willStay ? "stay" : "leave",
                VILLAGER_LIFETIME, VILLAGER_LIFETIME / 20, ATTRACTED_VILLAGERS.size());
        }
    }
    
    /**
     * Removes a villager from tracking (e.g., when it despawns naturally or is manually removed).
     * @param villagerId The villager's UUID
     */
    public static void removeVillager(UUID villagerId) {
        ATTRACTED_VILLAGERS.remove(villagerId);
    }
    
    /**
     * Checks if a villager is an attracted villager for a specific town hall.
     * @param villagerId The villager's UUID
     * @param townHallId The town hall building ID
     * @return true if the villager is tracked as attracted to this town hall
     */
    public static boolean isAttractedVillager(UUID villagerId, UUID townHallId) {
        AttractionData data = ATTRACTED_VILLAGERS.get(villagerId);
        return data != null && data.townHallId.equals(townHallId);
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
        boolean hasTrackedVillagers = !ATTRACTED_VILLAGERS.isEmpty();
        
        // If we have tracked villagers, check every tick to ensure prompt despawning
        // Otherwise, only check every CHECK_INTERVAL ticks to reduce performance impact
        if (!hasTrackedVillagers && currentTime % CHECK_INTERVAL != 0) {
            return;
        }
        
        // Check each tracked villager
        List<UUID> villagersToDespawn = new ArrayList<>();
        List<UUID> villagersToRemove = new ArrayList<>();
        
        for (Map.Entry<UUID, AttractionData> entry : ATTRACTED_VILLAGERS.entrySet()) {
            UUID villagerId = entry.getKey();
            AttractionData data = entry.getValue();
            
            // Try to find the villager in the current world
            net.minecraft.entity.Entity entity = world.getEntity(villagerId);
            
            if (entity instanceof VillagerEntity) {
                // Villager exists in this world - check lifetime
                long elapsed = currentTime - data.spawnTime;
                long remaining = VILLAGER_LIFETIME - elapsed;
                
                if (elapsed >= VILLAGER_LIFETIME) {
                    // Lifetime expired - check if villager should stay or leave
                    if (data.willStay) {
                        // Villager stays - remove from tracking but don't despawn
                        // They become a regular villager in the settlement
                        villagersToRemove.add(villagerId);
                        SettlementsMod.LOGGER.info("Attracted villager {} decided to stay in settlement (removing from tracking)", villagerId);
                    } else {
                        // Villager leaves - despawn with enderman teleport
                        villagersToDespawn.add(villagerId);
                        SettlementsMod.LOGGER.info("Attracted villager {} decided to leave (despawning with enderman teleport)", villagerId);
                    }
                } else if (remaining <= 100 && currentTime % 20 == 0) { // Log when less than 5 seconds remaining
                    SettlementsMod.LOGGER.debug("Attracted villager {} will {} in {} ticks ({} seconds) - elapsed: {} ticks", 
                        villagerId, data.willStay ? "stay" : "leave", remaining, remaining / 20, elapsed);
                }
            } else {
                // Villager not in this world - check if it exists in any world
                if (currentTime % 100 == 0) { // Every 5 seconds
                    boolean existsAnywhere = false;
                    for (net.minecraft.server.world.ServerWorld serverWorld : world.getServer().getWorlds()) {
                        if (serverWorld.getEntity(villagerId) instanceof VillagerEntity) {
                            existsAnywhere = true;
                            break;
                        }
                    }
                    if (!existsAnywhere) {
                        // Villager doesn't exist in any world, remove from tracking
                        villagersToRemove.add(villagerId);
                        SettlementsMod.LOGGER.debug("Attracted villager {} no longer exists in any world, removing from tracking", villagerId);
                    }
                }
            }
        }
        
        // Remove villagers that don't exist or decided to stay
        for (UUID villagerId : villagersToRemove) {
            ATTRACTED_VILLAGERS.remove(villagerId);
        }
        
        // Despawn villagers that decided to leave
        for (UUID villagerId : villagersToDespawn) {
            despawnVillager(world, villagerId);
        }
        
        if (!villagersToDespawn.isEmpty()) {
            SettlementsMod.LOGGER.info("Despawning {} attracted villager(s) after lifetime expired (they decided to leave)", villagersToDespawn.size());
        }
    }
    
    /**
     * Despawns an attracted villager with enderman-style teleport effect.
     * @param world The server world
     * @param villagerId The villager's UUID
     */
    private static void despawnVillager(ServerWorld world, UUID villagerId) {
        try {
            net.minecraft.entity.Entity entity = world.getEntity(villagerId);
            if (entity instanceof VillagerEntity) {
                VillagerEntity villager = (VillagerEntity) entity;
                
                // Remove from tracking
                ATTRACTED_VILLAGERS.remove(villagerId);
                
                // Spawn enderman teleport particles at villager location
                double x = villager.getX();
                double y = villager.getY() + villager.getHeight() / 2.0;
                double z = villager.getZ();
                
                // Spawn portal particles (enderman teleport effect)
                for (int i = 0; i < 32; i++) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                        x + (world.getRandom().nextDouble() - 0.5) * 2.0,
                        y + (world.getRandom().nextDouble() - 0.5) * 2.0,
                        z + (world.getRandom().nextDouble() - 0.5) * 2.0,
                        1, 0.0, 0.0, 0.0, 0.0);
                }
                
                // Play enderman teleport sound
                world.playSound(null, villager.getBlockPos(), 
                    net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, 
                    net.minecraft.sound.SoundCategory.NEUTRAL, 1.0f, 1.0f);
                
                // Despawn the villager
                villager.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                
                SettlementsMod.LOGGER.info("Despawned attracted villager {} after lifetime expired (enderman warp - they decided to leave)", villagerId);
            } else {
                // Entity not found or already despawned, just remove from tracking
                ATTRACTED_VILLAGERS.remove(villagerId);
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error despawning attracted villager {}: {}", villagerId, e.getMessage());
            // Remove from tracking anyway
            ATTRACTED_VILLAGERS.remove(villagerId);
        }
    }
    
    /**
     * Gets the remaining lifetime of an attracted villager in ticks.
     * @param villagerId The villager's UUID
     * @param currentTime Current world time
     * @return Remaining lifetime in ticks, or 0 if not tracked or expired
     */
    public static long getRemainingLifetime(UUID villagerId, long currentTime) {
        AttractionData data = ATTRACTED_VILLAGERS.get(villagerId);
        if (data == null) {
            return 0;
        }
        
        long elapsed = currentTime - data.spawnTime;
        long remaining = VILLAGER_LIFETIME - elapsed;
        return Math.max(0, remaining);
    }
    
    /**
     * Gets whether an attracted villager will stay or leave.
     * @param villagerId The villager's UUID
     * @return true if villager will stay, false if they will leave, or null if not tracked
     */
    public static Boolean willVillagerStay(UUID villagerId) {
        AttractionData data = ATTRACTED_VILLAGERS.get(villagerId);
        return data != null ? data.willStay : null;
    }
}

