package com.secretasain.settlements.network;

import com.secretasain.settlements.settlement.Settlement;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Network packet to sync settlement materials from server to client.
 * Sent after materials are updated (e.g., after checking chests).
 */
public class SyncMaterialsPacket {
    public static final Identifier ID = new Identifier("settlements", "sync_materials");

    /**
     * Sends updated materials to the client.
     * @param player The player to send to
     * @param settlement The settlement with updated materials
     */
    public static void send(ServerPlayerEntity player, Settlement settlement) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        // Send settlement ID and materials map
        buf.writeUuid(settlement.getId());
        
        // Send materials as NBT
        NbtCompound materialsNbt = new NbtCompound();
        for (java.util.Map.Entry<String, Integer> entry : settlement.getMaterials().entrySet()) {
            materialsNbt.putInt(entry.getKey(), entry.getValue());
        }
        buf.writeNbt(materialsNbt);
        
        ServerPlayNetworking.send(player, ID, buf);
    }
}

