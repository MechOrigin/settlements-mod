package com.secretasain.settlements.warband;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.network.SyncWarbandNpcsPacket;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event handlers for Warband NPC entities.
 * Handles NPC lifecycle, persistence, and respawning.
 */
public class WarbandNpcEventHandlers {
    // Track worlds that need NPC sync (world -> ticks remaining)
    private static final Map<ServerWorld, Integer> worldsNeedingSync = new HashMap<>();
    
    public static void register() {
        // Handle NPC death/removal
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof WarbandNpcEntity npc) {
                handleNpcRemoved(npc);
            }
        });
        
        // Note: ServerEntityEvents doesn't exist in Fabric API
        // We'll handle entity removal through death events only
        // Entity unload is handled automatically by Minecraft's entity system
        
        // Handle world load - mark world for sync (entities load asynchronously)
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world instanceof ServerWorld) {
                ServerWorld serverWorld = (ServerWorld) world;
                // Mark world for sync - will check every tick for 200 ticks (10 seconds)
                worldsNeedingSync.put(serverWorld, 200);
            }
        });
        
        // Handle world unload - remove from sync tracking
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world instanceof ServerWorld) {
                ServerWorld serverWorld = (ServerWorld) world;
                worldsNeedingSync.remove(serverWorld);
            }
        });
        
        // Tick-based sync check - runs every tick to sync NPCs after world load
        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            if (world instanceof ServerWorld) {
                ServerWorld serverWorld = (ServerWorld) world;
                Integer ticksRemaining = worldsNeedingSync.get(serverWorld);
                if (ticksRemaining != null && ticksRemaining > 0) {
                    worldsNeedingSync.put(serverWorld, ticksRemaining - 1);
                    // Try to sync NPCs - will only sync if entities are loaded
                    syncNpcEntityIds(serverWorld);
                    // Remove from tracking after timeout
                    if (ticksRemaining <= 1) {
                        worldsNeedingSync.remove(serverWorld);
                    }
                }
            }
        });
        
        // Handle player join - sync NPC data
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            server.execute(() -> {
                // Sync NPC data when player joins
                SyncWarbandNpcsPacket.send(player);
            });
        });
    }
    
    /**
     * Handles NPC removal (death or unload).
     */
    private static void handleNpcRemoved(WarbandNpcEntity npc) {
        if (npc.getWorld().isClient) {
            return;
        }
        
        ServerWorld world = (ServerWorld) npc.getWorld();
        UUID entityId = npc.getUuid();
        UUID playerId = npc.getPlayerId();
        
        if (playerId == null) {
            return;
        }
        
        PlayerWarbandData warbandData = PlayerWarbandData.getOrCreate(world);
        
        // Remove NPC from data
        warbandData.removeNpc(playerId, entityId);
        
        // Sync to client
        net.minecraft.entity.player.PlayerEntity playerEntity = world.getPlayerByUuid(playerId);
        if (playerEntity instanceof ServerPlayerEntity player) {
            UUID barracksId = npc.getBarracksBuildingId();
            if (barracksId != null) {
                SyncWarbandNpcsPacket.sendForBarracks(player, barracksId);
            }
        }
        
        SettlementsMod.LOGGER.info("Removed NPC {} from player {}'s warband", entityId, playerId);
    }
    
    /**
     * Syncs entity IDs from existing NPCs to PlayerWarbandData.
     * This ensures NpcData has the correct entity IDs for NPCs that were loaded from world save.
     * Only respawns NPCs if they truly don't exist (not just in unloaded chunks).
     * This method is called repeatedly after world load until sync is complete.
     */
    private static void syncNpcEntityIds(ServerWorld world) {
        PlayerWarbandData warbandData = PlayerWarbandData.getOrCreate(world);
        boolean dataChanged = false;
        
        // First, find all existing WarbandNpcEntity instances and sync their IDs to NpcData
        // This handles NPCs that were loaded from world save (Minecraft's entity persistence)
        java.util.List<WarbandNpcEntity> existingNpcs = new java.util.ArrayList<>();
        for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
            if (entity instanceof WarbandNpcEntity npc) {
                existingNpcs.add(npc);
            }
        }
        
        for (WarbandNpcEntity npc : existingNpcs) {
            UUID playerId = npc.getPlayerId();
            UUID entityId = npc.getUuid();
            
            if (playerId == null) {
                continue;
            }
            
            // Find matching NpcData by checking if entity matches NPC data
            List<NpcData> npcs = warbandData.getPlayerWarband(playerId);
            boolean found = false;
            
            for (NpcData npcData : npcs) {
                // Check if this NPC matches the data (by class, paragon, barracks)
                if (npcData.getNpcClass() == npc.getNpcClass() &&
                    npcData.getParagonLevel() == npc.getParagonLevel() &&
                    npcData.getBarracksBuildingId().equals(npc.getBarracksBuildingId())) {
                    
                    // Update entity ID if it changed (e.g., after world reload)
                    if (!npcData.getEntityId().equals(entityId)) {
                        SettlementsMod.LOGGER.info("Syncing entity ID for NPC: old={}, new={}", 
                            npcData.getEntityId(), entityId);
                        npcData.setEntityId(entityId);
                        dataChanged = true;
                    }
                    found = true;
                    break;
                }
            }
            
            // If NPC exists but not in data, add it (shouldn't happen, but handle it)
            if (!found && npc.getNpcClass() != null && npc.getParagonLevel() != null) {
                SettlementsMod.LOGGER.warn("Found NPC {} not in PlayerWarbandData, adding it", entityId);
                NpcData npcData = new NpcData(
                    entityId,
                    playerId,
                    npc.getNpcClass(),
                    npc.getParagonLevel(),
                    npc.getBarracksBuildingId(),
                    npc.getBlockPos()
                );
                warbandData.addNpc(playerId, npcData);
                dataChanged = true;
            }
        }
        
        // Now check for NPCs in data that don't have matching entities
        // Only respawn if entity truly doesn't exist AND we've waited long enough for entities to load
        Integer ticksRemaining = worldsNeedingSync.get(world);
        boolean shouldRespawn = (ticksRemaining == null || ticksRemaining < 50); // Only respawn after 7.5 seconds
        
        for (UUID playerId : warbandData.getAllPlayerIds()) {
            List<NpcData> npcs = new java.util.ArrayList<>(warbandData.getPlayerWarband(playerId));
            
            for (NpcData npcData : npcs) {
                if (!npcData.isHired()) {
                    continue;
                }
                
                // Check if entity exists (by UUID - this checks all entities, not just loaded chunks)
                Entity existingEntity = world.getEntity(npcData.getEntityId());
                if (existingEntity instanceof WarbandNpcEntity) {
                    // Entity exists - ensure it has correct data
                    WarbandNpcEntity npc = (WarbandNpcEntity) existingEntity;
                    if (npc.getPlayerId() == null || !npc.getPlayerId().equals(npcData.getPlayerId())) {
                        npc.setNpcData(npcData.getPlayerId(), npcData.getNpcClass(), 
                                      npcData.getParagonLevel(), npcData.getBarracksBuildingId());
                    }
                    continue;
                }
                
                // Check if there's another NPC with same class/paragon/barracks (duplicate check)
                boolean duplicateExists = false;
                for (WarbandNpcEntity existingNpc : existingNpcs) {
                    if (existingNpc.getPlayerId() != null && existingNpc.getPlayerId().equals(npcData.getPlayerId()) &&
                        existingNpc.getNpcClass() == npcData.getNpcClass() &&
                        existingNpc.getParagonLevel() == npcData.getParagonLevel() &&
                        existingNpc.getBarracksBuildingId().equals(npcData.getBarracksBuildingId())) {
                        // Found duplicate - update NpcData to use this entity's ID and remove old entity if it exists
                        SettlementsMod.LOGGER.info("Found duplicate NPC, syncing entity ID: {} -> {}", 
                            npcData.getEntityId(), existingNpc.getUuid());
                        
                        // If the old entity ID exists but is different, remove it (it's a duplicate)
                        Entity oldEntity = world.getEntity(npcData.getEntityId());
                        if (oldEntity instanceof WarbandNpcEntity && !oldEntity.getUuid().equals(existingNpc.getUuid())) {
                            SettlementsMod.LOGGER.warn("Removing duplicate NPC entity: {}", npcData.getEntityId());
                            oldEntity.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                        }
                        
                        npcData.setEntityId(existingNpc.getUuid());
                        dataChanged = true;
                        duplicateExists = true;
                        break;
                    }
                }
                
                if (duplicateExists) {
                    continue;
                }
                
                // Only respawn if we've waited long enough for entities to load
                if (!shouldRespawn) {
                    continue; // Skip respawning - entities might still be loading
                }
                
                // Entity truly doesn't exist - respawn it
                // This only happens if NPC was deleted or world save was corrupted
                try {
                    SettlementsMod.LOGGER.info("Respawning missing NPC for player {}: class={}, paragon={}", 
                        npcData.getPlayerId(), npcData.getNpcClass(), npcData.getParagonLevel());
                    
                    WarbandNpcEntity npc = ModEntities.WARBAND_NPC.create(world);
                    if (npc == null) {
                        SettlementsMod.LOGGER.warn("Failed to create WarbandNpcEntity for respawn");
                        continue;
                    }
                    
                    // Set NPC data
                    npc.setNpcData(npcData.getPlayerId(), npcData.getNpcClass(), 
                                  npcData.getParagonLevel(), npcData.getBarracksBuildingId());
                    
                    // Restore aggressive mode and behavior state from NBT (if entity was saved)
                    // Note: These are saved in entity NBT automatically by Minecraft
                    
                    // Find spawn position (use barracks position)
                    BlockPos spawnPos = npcData.getBarracksPosition();
                    if (spawnPos == null) {
                        spawnPos = world.getSpawnPos();
                    }
                    
                    // Find safe spawn position
                    BlockPos safePos = findSafeSpawnPosition(world, spawnPos);
                    if (safePos == null) {
                        safePos = spawnPos;
                    }
                    
                    npc.refreshPositionAndAngles(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 
                        world.random.nextFloat() * 360f, 0f);
                    
                    // Spawn entity
                    world.spawnEntity(npc);
                    npc.equipGear();
                    
                    // Update entity ID in NpcData
                    npcData.setEntityId(npc.getUuid());
                    dataChanged = true;
                    
                    SettlementsMod.LOGGER.info("Respawned NPC {} for player {}", npc.getUuid(), npcData.getPlayerId());
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error respawning NPC for player {}", npcData.getPlayerId(), e);
                }
            }
        }
        
        if (dataChanged) {
            warbandData.markDirty();
        }
    }
    
    /**
     * Finds a safe spawn position near the given position.
     */
    private static BlockPos findSafeSpawnPosition(ServerWorld world, BlockPos centerPos) {
        int[] offsets = {0, 1, 2, 3, 4, 5};
        
        for (int offset : offsets) {
            for (int x = -offset; x <= offset; x++) {
                for (int z = -offset; z <= offset; z++) {
                    if (x == 0 && z == 0 && offset > 0) {
                        continue;
                    }
                    
                    int testX = centerPos.getX() + x;
                    int testZ = centerPos.getZ() + z;
                    int y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, testX, testZ);
                    BlockPos testPos = new BlockPos(testX, y, testZ);
                    
                    if (isSafeSpawnPosition(world, testPos)) {
                        return testPos;
                    }
                }
            }
        }
        
        return centerPos; // Fallback to center position
    }
    
    /**
     * Checks if a position is safe for spawning.
     */
    private static boolean isSafeSpawnPosition(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos.down()).isOpaque()) {
            return false;
        }
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        if (!world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        return true;
    }
}
