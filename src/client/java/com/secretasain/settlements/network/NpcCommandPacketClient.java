package com.secretasain.settlements.network;

import com.secretasain.settlements.warband.NpcBehaviorState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Client-side helper for sending NPC command packets.
 */
public class NpcCommandPacketClient {
    public static void send(UUID entityId, NpcBehaviorState behaviorState, boolean aggressive) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(entityId);
        buf.writeEnumConstant(behaviorState);
        buf.writeBoolean(aggressive);
        ClientPlayNetworking.send(NpcCommandPacket.ID, buf);
    }
}

