package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.warband.NpcBehaviorState;
import com.secretasain.settlements.warband.WarbandNpcEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-side handler for NPC commands (follow, stay, aggressive mode).
 */
public class NpcCommandPacket {
    public static final Identifier ID = new Identifier("settlements", "npc_command");
    
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID entityId = buf.readUuid();
            NpcBehaviorState behaviorState = buf.readEnumConstant(NpcBehaviorState.class);
            boolean aggressive = buf.readBoolean();
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    
                    // Find the entity
                    Entity entity = world.getEntity(entityId);
                    if (entity == null || !(entity instanceof WarbandNpcEntity)) {
                        SettlementsMod.LOGGER.warn("Failed to command NPC: Entity {} not found or invalid", entityId);
                        return;
                    }
                    
                    WarbandNpcEntity npc = (WarbandNpcEntity) entity;
                    
                    // Verify player owns this NPC
                    if (!npc.getPlayerId().equals(player.getUuid())) {
                        SettlementsMod.LOGGER.warn("Player {} tried to command NPC {} owned by {}", 
                            player.getName().getString(), entityId, npc.getPlayerId());
                        return;
                    }
                    
                    // Apply behavior state
                    npc.setBehaviorState(behaviorState);
                    if (behaviorState == NpcBehaviorState.STAY) {
                        npc.setStayPosition(npc.getBlockPos());
                    }
                    
                    // Apply aggressive mode
                    npc.setAggressive(aggressive);
                    
                    // Update NpcData to persist aggressive mode
                    com.secretasain.settlements.warband.PlayerWarbandData warbandData = 
                        com.secretasain.settlements.warband.PlayerWarbandData.getOrCreate(world);
                    com.secretasain.settlements.warband.NpcData npcData = warbandData.getNpcByEntityId(entityId);
                    if (npcData != null) {
                        // NpcData doesn't store aggressive mode directly, but entity NBT does
                        // The entity will save its state to NBT automatically
                        warbandData.markDirty(); // Ensure changes are saved
                    }
                    
                    // Sync NPCs to client so UI updates
                    UUID barracksId = npc.getBarracksBuildingId();
                    if (barracksId != null) {
                        com.secretasain.settlements.network.SyncWarbandNpcsPacket.sendForBarracks(player, barracksId);
                    }
                    
                    // If player has the interaction screen open, send updated state
                    // This ensures UI reflects the change immediately
                    com.secretasain.settlements.network.OpenNpcInteractionScreenPacket.send(player, entityId);
                    
                    SettlementsMod.LOGGER.info("Player {} commanded NPC {}: state={}, aggressive={}", 
                        player.getName().getString(), entityId, behaviorState, aggressive);
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing NpcCommandPacket for player {}", player.getName().getString(), e);
                }
            });
        });
    }
}

