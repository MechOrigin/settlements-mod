package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side helper for sending placement confirmation packets.
 */
public class ConfirmPlacementPacketClient {
    /**
     * Sends a placement confirmation packet to the server.
     * @param placementPos The position where the structure should be placed
     * @param rotation The rotation in degrees (0, 90, 180, 270)
     */
    public static void send(BlockPos placementPos, int rotation) {
        com.secretasain.settlements.SettlementsMod.LOGGER.info("ConfirmPlacementPacketClient: Sending placement packet - position: {}, rotation: {}", placementPos, rotation);
        var buf = PacketByteBufs.create();
        buf.writeLong(placementPos.asLong());
        buf.writeInt(rotation);
        ClientPlayNetworking.send(ConfirmPlacementPacket.ID, buf);
        com.secretasain.settlements.SettlementsMod.LOGGER.info("ConfirmPlacementPacketClient: Packet sent successfully");
    }
}

