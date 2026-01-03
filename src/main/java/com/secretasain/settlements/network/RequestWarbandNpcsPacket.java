package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-side handler for client requests for warband NPCs.
 */
public class RequestWarbandNpcsPacket {
    public static final Identifier ID = new Identifier("settlements", "request_warband_npcs");
    
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID barracksId = buf.readUuid();
            
            server.execute(() -> {
                // Send NPCs for this barracks to client
                SyncWarbandNpcsPacket.sendForBarracks(player, barracksId);
            });
        });
    }
}

