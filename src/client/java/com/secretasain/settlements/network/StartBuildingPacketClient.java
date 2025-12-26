package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending start building packets.
 */
public class StartBuildingPacketClient {
    /**
     * Sends a start building request from the client.
     * @param buildingId The UUID of the building to start
     * @param settlementId The UUID of the settlement containing the building
     */
    public static void send(UUID buildingId, UUID settlementId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(buildingId);
        buf.writeUuid(settlementId);
        
        ClientPlayNetworking.send(StartBuildingPacket.ID, buf);
    }
}

