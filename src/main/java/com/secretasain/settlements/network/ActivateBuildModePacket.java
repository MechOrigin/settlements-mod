package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.BuildModeHandler;
import com.secretasain.settlements.building.BuildModeManager;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Network packet to activate build mode on the server.
 * Sent from client when player clicks "Build Structure" button.
 * 
 * Note: The client-side send method is in ActivateBuildModePacketClient.
 */
public class ActivateBuildModePacket {
    public static final Identifier ID = new Identifier("settlements", "activate_build_mode");
    public static final Identifier STRUCTURE_DATA_PACKET_ID = new Identifier("settlements", "structure_data");
    
    /**
     * Registers the server-side packet handler.
     * Call this from SettlementsMod.onInitialize().
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, networkHandler, buf, responseSender) -> {
            String structureIdentifier = buf.readString();
            UUID settlementId = buf.readUuid(); // Read settlement ID
            
            server.execute(() -> {
                try {
                    SettlementsMod.LOGGER.info("Activating build mode for player {} with structure {} for settlement {}", 
                        player.getName().getString(), structureIdentifier, settlementId);
                    
                    // Load the structure
                    Identifier structureId = Identifier.tryParse(structureIdentifier);
                    if (structureId == null) {
                        SettlementsMod.LOGGER.error("Invalid structure identifier: {}", structureIdentifier);
                        return;
                    }
                    
                    StructureData structureData = StructureLoader.loadStructure(structureId, server);
                    if (structureData == null) {
                        SettlementsMod.LOGGER.error("Failed to load structure: {}", structureIdentifier);
                        return;
                    }
                    
                    // Get or create build mode handler for this player
                    BuildModeHandler buildModeHandler = BuildModeManager.getHandler(player);
                    
                    // Activate build mode on server with settlement ID
                    buildModeHandler.activateBuildMode(structureData, settlementId);
                    
                    // Send structure data to client
                    // Load the NBT file again to send to client
                    ResourceManager resourceManager = server.getResourceManager();
                    List<Resource> resources = resourceManager.getAllResources(structureId);
                    if (!resources.isEmpty()) {
                        try (InputStream inputStream = resources.get(0).getInputStream()) {
                            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.readCompressed(inputStream);
                            
                            // Send NBT to client
                            net.minecraft.network.PacketByteBuf responseBuf = PacketByteBufs.create();
                            responseBuf.writeNbt(nbt);
                            responseBuf.writeString(structureData.getName());
                            ServerPlayNetworking.send(player, STRUCTURE_DATA_PACKET_ID, responseBuf);
                            
                            SettlementsMod.LOGGER.info("Sent structure data to client for player {}", player.getName().getString());
                        } catch (Exception e) {
                            SettlementsMod.LOGGER.error("Failed to send structure data to client", e);
                        }
                    }
                    
                    SettlementsMod.LOGGER.info("Build mode activated for player {}", player.getName().getString());
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error activating build mode", e);
                }
            });
        });
    }
}

