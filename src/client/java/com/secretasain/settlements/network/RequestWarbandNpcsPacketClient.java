package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client-side packet to request warband NPCs from server.
 */
public class RequestWarbandNpcsPacketClient {
    public static final Identifier ID = new Identifier("settlements", "request_warband_npcs");
    
    public static void send(UUID barracksId) {
        var buf = PacketByteBufs.create();
        buf.writeUuid(barracksId);
        ClientPlayNetworking.send(ID, buf);
    }
}

