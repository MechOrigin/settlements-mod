package com.secretasain.settlements.network;

import com.secretasain.settlements.warband.NpcClass;
import com.secretasain.settlements.warband.ParagonLevel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending hire NPC packets.
 */
public class HireNpcPacketClient {
    /**
     * Sends a packet to hire an NPC.
     * @param barracksId The barracks building UUID
     * @param settlementId The settlement UUID
     * @param npcClass The NPC class to hire
     * @param paragonLevel The paragon level
     */
    public static void send(UUID barracksId, UUID settlementId, NpcClass npcClass, ParagonLevel paragonLevel) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(barracksId);
        buf.writeUuid(settlementId);
        buf.writeString(npcClass.name()); // Write enum as string
        buf.writeString(paragonLevel.name()); // Write enum as string
        ClientPlayNetworking.send(HireNpcPacket.ID, buf);
    }
}

