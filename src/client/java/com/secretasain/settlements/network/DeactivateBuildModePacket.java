package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.ClientBuildModeManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

/**
 * Client-side packet handler for receiving build mode deactivation from server.
 */
public class DeactivateBuildModePacket {
    public static final Identifier ID = com.secretasain.settlements.network.ConfirmPlacementPacket.DEACTIVATE_BUILD_MODE_PACKET_ID;
    
    /**
     * Registers the client-side packet handler.
     * Call this from SettlementsModClient.onInitializeClient().
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                SettlementsMod.LOGGER.info("Received build mode deactivation from server");
                ClientBuildModeManager.deactivateBuildMode();
            });
        });
    }
}

