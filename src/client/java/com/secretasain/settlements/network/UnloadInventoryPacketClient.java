package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending unload inventory packets to the server.
 */
public class UnloadInventoryPacketClient {
    /**
     * Sends an unload inventory packet to the server.
     * @param buildingId The ID of the building to unload inventory from, or null to unload from settlement storage
     * @param settlementId The ID of the settlement containing the building
     */
    public static void send(UUID buildingId, UUID settlementId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(buildingId != null);
        if (buildingId != null) {
            buf.writeUuid(buildingId);
        }
        buf.writeUuid(settlementId);
        
        ClientPlayNetworking.send(UnloadInventoryPacket.ID, buf);
    }
}

