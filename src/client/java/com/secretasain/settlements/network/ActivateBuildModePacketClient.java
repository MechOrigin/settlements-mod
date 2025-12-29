package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

/**
 * Client-side helper for sending build mode activation packets.
 */
public class ActivateBuildModePacketClient {
    /**
     * Sends a request to activate build mode from the client.
     * Also activates build mode locally for immediate preview rendering.
     * @param structureIdentifier The identifier of the structure to build (e.g., "settlements:structures/lvl1_oak_wall.nbt")
     * @param settlementId The UUID of the settlement whose screen was open when build mode was activated
     */
    public static void send(String structureIdentifier, java.util.UUID settlementId) {
        // Send packet to server - server will load structure and send it back
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(structureIdentifier);
        buf.writeUuid(settlementId);
        ClientPlayNetworking.send(ActivateBuildModePacket.ID, buf);
        
        SettlementsMod.LOGGER.info("Sent build mode activation request to server for structure: {} in settlement: {}", 
            structureIdentifier, settlementId);
    }
}

