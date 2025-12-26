package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Network packet to sync building status changes to clients.
 * Sent from server when building status changes (e.g., RESERVED -> IN_PROGRESS).
 */
public class SyncBuildingStatusPacket {
    public static final Identifier ID = new Identifier("settlements", "sync_building_status");
    
    /**
     * Registers the client-side packet handler.
     */
    public static void register() {
        // Client-side handler will be registered in ClientNetworkHandler
    }
    
    /**
     * Sends building status update to all players viewing the settlement.
     * @param settlement The settlement containing the building
     * @param building The building with updated status
     * @param world The server world
     */
    public static void send(Settlement settlement, Building building, ServerWorld world) {
        // Send to all players in the world (or just nearby players)
        // For now, send to all players - can be optimized later
        for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
            // Check if player is near the settlement (within 128 blocks)
            BlockPos lecternPos = settlement.getLecternPos();
            if (lecternPos != null && player.getBlockPos().getSquaredDistance(lecternPos) < 128 * 128) {
                sendToPlayer(player, settlement, building);
            }
        }
    }
    
    /**
     * Sends building status update to a specific player.
     * @param player The player to send to
     * @param settlement The settlement containing the building
     * @param building The building with updated status
     */
    public static void sendToPlayer(net.minecraft.server.network.ServerPlayerEntity player, 
                                   Settlement settlement, Building building) {
        net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(settlement.getId());
        buf.writeUuid(building.getId());
        buf.writeString(building.getStatus().name());
        buf.writeNbt(settlement.toNbt()); // Send full settlement data for UI update
        
        ServerPlayNetworking.send(player, ID, buf);
        SettlementsMod.LOGGER.debug("Sent building status update for building {} (status: {}) to player {}", 
            building.getId(), building.getStatus(), player.getName().getString());
    }
}

