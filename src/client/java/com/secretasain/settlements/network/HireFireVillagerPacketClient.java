package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending hire/fire villager packets.
 */
public class HireFireVillagerPacketClient {
    /**
     * Sends a packet to hire or fire a villager.
     * @param villagerId The villager's entity UUID
     * @param settlementId The settlement UUID
     * @param hire true to hire, false to fire
     */
    public static void send(UUID villagerId, UUID settlementId, boolean hire) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villagerId);
        buf.writeUuid(settlementId);
        buf.writeBoolean(hire);
        ClientPlayNetworking.send(HireFireVillagerPacket.ID, buf);
    }
}

