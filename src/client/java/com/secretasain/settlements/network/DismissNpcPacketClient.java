package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending dismiss NPC packets.
 */
public class DismissNpcPacketClient {
    public static void send(UUID entityId) {
        send(entityId, null);
    }
    
    public static void send(UUID entityId, java.util.UUID barracksId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(entityId);
        buf.writeBoolean(barracksId != null);
        if (barracksId != null) {
            buf.writeUuid(barracksId);
        }
        ClientPlayNetworking.send(DismissNpcPacket.ID, buf);
    }
}

