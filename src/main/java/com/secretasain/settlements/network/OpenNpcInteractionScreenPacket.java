package com.secretasain.settlements.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-side packet to tell client to open NPC interaction screen.
 */
public class OpenNpcInteractionScreenPacket {
    public static final Identifier ID = new Identifier("settlements", "open_npc_interaction");
    
    public static void send(ServerPlayerEntity player, UUID npcEntityId) {
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeUuid(npcEntityId);
        
        // Also send NPC state data so client UI can display correctly
        net.minecraft.server.world.ServerWorld world = player.getServerWorld();
        net.minecraft.entity.Entity entity = world.getEntity(npcEntityId);
        if (entity instanceof com.secretasain.settlements.warband.WarbandNpcEntity npc) {
            buf.writeBoolean(npc.isAggressive());
            buf.writeString(npc.getBehaviorState() != null ? npc.getBehaviorState().name() : "FOLLOW");
        } else {
            buf.writeBoolean(false);
            buf.writeString("FOLLOW");
        }
        
        ServerPlayNetworking.send(player, ID, buf);
    }
    
    public static void register() {
        // No server-side handler needed - this is server-to-client only
    }
}

