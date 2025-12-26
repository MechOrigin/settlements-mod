package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Network packet to sync ghost block entity data to clients.
 * Sends the represented block state for a ghost block entity so the client can render it correctly.
 */
public class SyncGhostBlockEntityPacket {
    public static final Identifier ID = new Identifier("settlements", "sync_ghost_block_entity");
    
    /**
     * Sends ghost block entity data to a specific player.
     * @param player The player to send the data to
     * @param pos The position of the ghost block
     * @param representedBlock The block state that the ghost block represents
     */
    public static void send(ServerPlayerEntity player, BlockPos pos, BlockState representedBlock) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        // Write position
        buf.writeBlockPos(pos);
        
        // Write represented block ID
        if (representedBlock != null) {
            buf.writeString(net.minecraft.registry.Registries.BLOCK.getId(representedBlock.getBlock()).toString());
        } else {
            buf.writeString("minecraft:air");
        }
        
        ServerPlayNetworking.send(player, ID, buf);
        SettlementsMod.LOGGER.info("Sent ghost block entity sync packet to {} for position {} with block {}", 
            player.getName().getString(), pos, representedBlock != null ? net.minecraft.registry.Registries.BLOCK.getId(representedBlock.getBlock()) : "null");
    }
    
    /**
     * Sends ghost block entity data to all players in a world.
     * @param world The world containing the ghost block
     * @param pos The position of the ghost block
     * @param representedBlock The block state that the ghost block represents
     */
    public static void sendToAll(World world, BlockPos pos, BlockState representedBlock) {
        if (world instanceof net.minecraft.server.world.ServerWorld) {
            net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) world;
            for (net.minecraft.server.network.ServerPlayerEntity player : serverWorld.getPlayers()) {
                send(player, pos, representedBlock);
            }
        }
    }
}

