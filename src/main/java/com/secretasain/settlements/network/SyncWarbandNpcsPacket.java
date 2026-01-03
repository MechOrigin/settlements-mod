package com.secretasain.settlements.network;

import com.secretasain.settlements.warband.NpcData;
import com.secretasain.settlements.warband.PlayerWarbandData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Server-side packet to sync warband NPCs to client.
 */
public class SyncWarbandNpcsPacket {
    public static final Identifier ID = new Identifier("settlements", "sync_warband_npcs");
    
    /**
     * Sends all NPCs for a player to the client.
     */
    public static void send(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        PlayerWarbandData warbandData = PlayerWarbandData.getOrCreate(world);
        List<NpcData> npcs = warbandData.getPlayerWarband(player.getUuid());
        
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        NbtList npcList = new NbtList();
        for (NpcData npc : npcs) {
            npcList.add(npc.toNbt());
        }
        NbtCompound rootNbt = new NbtCompound();
        rootNbt.put("npcs", npcList);
        buf.writeNbt(rootNbt);
        
        ServerPlayNetworking.send(player, ID, buf);
    }
    
    /**
     * Sends NPCs for a specific barracks to the client.
     */
    public static void sendForBarracks(ServerPlayerEntity player, UUID barracksId) {
        ServerWorld world = player.getServerWorld();
        PlayerWarbandData warbandData = PlayerWarbandData.getOrCreate(world);
        List<NpcData> allNpcs = warbandData.getPlayerWarband(player.getUuid());
        
        // Filter NPCs for this barracks
        List<NpcData> barracksNpcs = allNpcs.stream()
            .filter(npc -> npc.getBarracksBuildingId().equals(barracksId) && npc.isHired())
            .collect(java.util.stream.Collectors.toList());
        
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        NbtList npcList = new NbtList();
        for (NpcData npc : barracksNpcs) {
            npcList.add(npc.toNbt());
        }
        NbtCompound rootNbt = new NbtCompound();
        rootNbt.put("npcs", npcList);
        buf.writeNbt(rootNbt);
        
        ServerPlayNetworking.send(player, ID, buf);
    }
    
    public static void register() {
        // No server-side handler needed - this is server-to-client only
    }
}

