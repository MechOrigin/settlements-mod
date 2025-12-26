package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.ClientBuildModeManager;
import com.secretasain.settlements.building.StructureData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/**
 * Client-side packet handler for receiving structure data from the server.
 */
public class StructureDataPacket {
    public static final Identifier ID = com.secretasain.settlements.network.ActivateBuildModePacket.STRUCTURE_DATA_PACKET_ID;
    
    /**
     * Registers the client-side packet handler.
     * Call this from SettlementsModClient.onInitializeClient().
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (client, handler, buf, responseSender) -> {
            NbtCompound nbt = buf.readNbt();
            String structureName = buf.readString();
            
            client.execute(() -> {
                try {
                    SettlementsMod.LOGGER.info("Received structure data from server: {} ({} bytes)", 
                        structureName, nbt != null ? nbt.getSize() : 0);
                    
                    if (nbt == null) {
                        SettlementsMod.LOGGER.error("Received null NBT for structure: {}", structureName);
                        return;
                    }
                    
                    // Create StructureData from NBT
                    StructureData structure = new StructureData(nbt, structureName);
                    
                    // Activate build mode on client
                    ClientBuildModeManager.activateBuildMode(structure);
                    
                    SettlementsMod.LOGGER.info("Activated build mode on client for structure: {} ({} blocks)", 
                        structureName, structure.getBlockCount());
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing structure data from server", e);
                    if (client.player != null) {
                        client.player.sendMessage(
                            net.minecraft.text.Text.literal("Failed to load structure: " + structureName), 
                            false
                        );
                    }
                }
            });
        });
    }
}

