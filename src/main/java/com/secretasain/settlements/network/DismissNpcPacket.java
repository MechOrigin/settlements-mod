package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.warband.PlayerWarbandData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-side handler for dismissing NPCs.
 */
public class DismissNpcPacket {
    public static final Identifier ID = new Identifier("settlements", "dismiss_npc");
    
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID entityId = buf.readUuid();
            final UUID packetBarracksId;
            if (buf.readBoolean()) {
                packetBarracksId = buf.readUuid();
            } else {
                packetBarracksId = null;
            }
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    
                    // Check if NPC exists in PlayerWarbandData first
                    PlayerWarbandData warbandData = PlayerWarbandData.getOrCreate(world);
                    if (!warbandData.hasNpc(player.getUuid(), entityId)) {
                        player.sendMessage(Text.translatable("settlements.warband.dismiss.not_found"), false);
                        SettlementsMod.LOGGER.warn("Failed to dismiss NPC: NPC {} not found in player {}'s warband", 
                            entityId, player.getName().getString());
                        return;
                    }
                    
                    // Get NPC data to find barracks ID
                    java.util.List<com.secretasain.settlements.warband.NpcData> playerNpcs = 
                        warbandData.getPlayerWarband(player.getUuid());
                    com.secretasain.settlements.warband.NpcData npcData = null;
                    UUID barracksId = packetBarracksId; // Use packet barracks ID if provided
                    for (com.secretasain.settlements.warband.NpcData data : playerNpcs) {
                        if (data.getEntityId().equals(entityId)) {
                            npcData = data;
                            // Use data barracks ID if packet didn't provide one
                            if (barracksId == null) {
                                barracksId = data.getBarracksBuildingId();
                            }
                            break;
                        }
                    }
                    
                    if (npcData == null) {
                        player.sendMessage(Text.translatable("settlements.warband.dismiss.not_found"), false);
                        return;
                    }
                    
                    // Find the entity (might be dead or already removed)
                    Entity entity = world.getEntity(entityId);
                    com.secretasain.settlements.warband.WarbandNpcEntity npc = null;
                    
                    if (entity instanceof com.secretasain.settlements.warband.WarbandNpcEntity) {
                        npc = (com.secretasain.settlements.warband.WarbandNpcEntity) entity;
                        
                        // Verify player owns this NPC
                        if (!npc.getPlayerId().equals(player.getUuid())) {
                            player.sendMessage(Text.translatable("settlements.warband.dismiss.not_owner"), false);
                            SettlementsMod.LOGGER.warn("Player {} tried to dismiss NPC {} owned by {}", 
                                player.getName().getString(), entityId, npc.getPlayerId());
                            return;
                        }
                        
                        // Remove entity from world if it exists
                        npc.remove(Entity.RemovalReason.DISCARDED);
                    } else if (entity != null) {
                        // Entity exists but is not a WarbandNpcEntity
                        player.sendMessage(Text.translatable("settlements.warband.dismiss.invalid_entity"), false);
                        SettlementsMod.LOGGER.warn("Failed to dismiss NPC: Entity {} is not a WarbandNpcEntity", entityId);
                        return;
                    }
                    // If entity is null, it's already dead/removed - we'll just remove from data
                    
                    // Remove from PlayerWarbandData
                    warbandData.removeNpc(player.getUuid(), entityId);
                    
                    // Sync NPCs to client
                    UUID finalBarracksId = barracksId != null ? barracksId : (npc != null ? npc.getBarracksBuildingId() : null);
                    if (finalBarracksId != null) {
                        com.secretasain.settlements.network.SyncWarbandNpcsPacket.sendForBarracks(player, finalBarracksId);
                    }
                    
                    // Get NPC class from data (entity might be null if dead)
                    com.secretasain.settlements.warband.NpcClass npcClass = npc != null ? 
                        npc.getNpcClass() : npcData.getNpcClass();
                    
                    player.sendMessage(Text.translatable("settlements.warband.dismiss.success", 
                        npcClass.getDisplayName().getString()), false);
                    SettlementsMod.LOGGER.info("Player {} dismissed NPC {} (class: {})", 
                        player.getName().getString(), entityId, npcClass);
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing DismissNpcPacket for player {}", player.getName().getString(), e);
                    player.sendMessage(Text.translatable("settlements.error.generic"), false);
                }
            });
        });
    }
}

