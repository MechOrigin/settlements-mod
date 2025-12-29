package com.secretasain.settlements.network;

import com.secretasain.settlements.settlement.Settlement;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Network packet to open the settlement screen on the client.
 * Sends full settlement data including buildings, villagers, and materials.
 */
public class OpenSettlementScreenPacket {
    public static final Identifier ID = new Identifier("settlements", "open_screen");

    public static void send(ServerPlayerEntity player, Settlement settlement) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        // DEBUG: Log building count before serialization
        com.secretasain.settlements.SettlementsMod.LOGGER.info("OpenSettlementScreenPacket: Settlement {} has {} buildings before serialization", 
            settlement.getId(), settlement.getBuildings().size());
        for (com.secretasain.settlements.settlement.Building building : settlement.getBuildings()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("  - Building {}: {} at {}", 
                building.getId(), building.getStructureType(), building.getPosition());
        }
        
        // Send full settlement data as NBT
        NbtCompound settlementNbt = settlement.toNbt();
        
        // DEBUG: Verify buildings are in NBT
        if (settlementNbt.contains("buildings", 9)) { // 9 = NbtList
            net.minecraft.nbt.NbtList buildingList = settlementNbt.getList("buildings", 10); // 10 = NbtCompound
            com.secretasain.settlements.SettlementsMod.LOGGER.info("OpenSettlementScreenPacket: NBT contains {} buildings", buildingList.size());
        } else {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("OpenSettlementScreenPacket: NBT does NOT contain buildings list!");
        }
        
        buf.writeNbt(settlementNbt);
        
        ServerPlayNetworking.send(player, ID, buf);
    }
}

