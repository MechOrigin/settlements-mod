package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending golem assignment packets.
 */
public class AssignGolemPacketClient {
    /**
     * Sends a golem assignment request from the client.
     * @param settlementId The UUID of the settlement
     * @param golemId The UUID of the golem
     * @param buildingId The UUID of the wall station (null UUID to unassign)
     * @param assign True to assign, false to unassign
     */
    public static void send(UUID settlementId, UUID golemId, UUID buildingId, boolean assign) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(settlementId);
        buf.writeUuid(golemId);
        buf.writeUuid(buildingId != null ? buildingId : new UUID(0, 0)); // Use null UUID for unassign
        buf.writeBoolean(assign);
        
        ClientPlayNetworking.send(AssignGolemPacket.ID, buf);
    }
}

