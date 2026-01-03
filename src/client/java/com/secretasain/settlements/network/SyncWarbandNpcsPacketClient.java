package com.secretasain.settlements.network;

import com.secretasain.settlements.warband.NpcData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side handler for syncing warband NPCs from server.
 */
public class SyncWarbandNpcsPacketClient {
    public static final Identifier ID = new Identifier("settlements", "sync_warband_npcs");
    
    private static List<NpcData> cachedNpcs = new ArrayList<>();
    
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (client, handler, buf, responseSender) -> {
            NbtCompound nbt = buf.readNbt();
            if (nbt == null) {
                return;
            }
            
            NbtList npcList = nbt.getList("npcs", 10); // 10 = NBT_COMPOUND
            List<NpcData> npcs = new ArrayList<>();
            
            for (int i = 0; i < npcList.size(); i++) {
                NbtCompound npcNbt = npcList.getCompound(i);
                npcs.add(NpcData.fromNbt(npcNbt));
            }
            
            client.execute(() -> {
                cachedNpcs = npcs;
                // Update UI if SettlementScreen is open
                if (client.currentScreen instanceof com.secretasain.settlements.ui.SettlementScreen) {
                    com.secretasain.settlements.ui.SettlementScreen screen = 
                        (com.secretasain.settlements.ui.SettlementScreen) client.currentScreen;
                    screen.refreshHiredNpcList();
                }
            });
        });
    }
    
    /**
     * Gets the cached NPC list.
     */
    public static List<NpcData> getCachedNpcs() {
        return new ArrayList<>(cachedNpcs);
    }
    
    /**
     * Gets NPCs for a specific barracks.
     */
    public static List<NpcData> getNpcsForBarracks(java.util.UUID barracksId) {
        return cachedNpcs.stream()
            .filter(npc -> npc.getBarracksBuildingId().equals(barracksId) && npc.isHired())
            .collect(java.util.stream.Collectors.toList());
    }
}

