package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending material check packets.
 */
public class CheckMaterialsPacketClient {
    /**
     * Sends a check materials request from the client.
     * @param buildingId The ID of the building to check materials for (null to check all materials)
     * @param settlementId The UUID of the settlement
     */
    public static void send(UUID buildingId, UUID settlementId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(buildingId != null);
        if (buildingId != null) {
            buf.writeUuid(buildingId);
        }
        buf.writeUuid(settlementId);
        
        ClientPlayNetworking.send(CheckMaterialsPacket.ID, buf);
    }
}

