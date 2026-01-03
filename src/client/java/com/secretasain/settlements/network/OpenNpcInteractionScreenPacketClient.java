package com.secretasain.settlements.network;

import com.secretasain.settlements.client.ui.NpcInteractionScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client-side handler for opening NPC interaction screen.
 */
public class OpenNpcInteractionScreenPacketClient {
    public static final Identifier ID = new Identifier("settlements", "open_npc_interaction");
    
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (client, handler, buf, responseSender) -> {
            UUID npcEntityId = buf.readUuid();
            boolean aggressive = buf.readBoolean();
            String behaviorStateStr = buf.readString();
            
            client.execute(() -> {
                if (client.world == null || client.player == null) {
                    return;
                }
                
                // Find entity by UUID - iterate through entities
                Entity entity = null;
                for (Entity e : client.world.getEntities()) {
                    if (e.getUuid().equals(npcEntityId)) {
                        entity = e;
                        break;
                    }
                }
                
                if (entity instanceof com.secretasain.settlements.warband.WarbandNpcEntity) {
                    com.secretasain.settlements.warband.WarbandNpcEntity npc = 
                        (com.secretasain.settlements.warband.WarbandNpcEntity) entity;
                    // Verify NPC is still valid and not removed
                    if (npc != null && !npc.isRemoved() && npc.isAlive()) {
                        // Parse behavior state from server data
                        com.secretasain.settlements.warband.NpcBehaviorState state = com.secretasain.settlements.warband.NpcBehaviorState.FOLLOW;
                        try {
                            state = com.secretasain.settlements.warband.NpcBehaviorState.valueOf(behaviorStateStr);
                        } catch (IllegalArgumentException e) {
                            // Invalid state, use default
                        }
                        
                        // Open screen with server-provided state (accurate) instead of reading from client entity (may be stale)
                        client.setScreen(new com.secretasain.settlements.client.ui.NpcInteractionScreen(npc, aggressive, state));
                    }
                }
            });
        });
    }
}

