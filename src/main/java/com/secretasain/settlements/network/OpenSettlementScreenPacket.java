package com.secretasain.settlements.network;

import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Network packet to open the settlement screen on the client.
 * Sends full settlement data including buildings, villagers, and materials.
 */
public class OpenSettlementScreenPacket {
    public static final Identifier ID = new Identifier("settlements", "open_screen");

    public static void send(ServerPlayerEntity player, Settlement settlement) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        // Send full settlement data as NBT
        NbtCompound settlementNbt = settlement.toNbt();
        buf.writeNbt(settlementNbt);
        
        ServerPlayNetworking.send(player, ID, buf);
    }
}

