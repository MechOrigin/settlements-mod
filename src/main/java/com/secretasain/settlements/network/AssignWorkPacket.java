package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import com.secretasain.settlements.settlement.WorkAssignmentManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Network packet to assign or unassign a villager to/from a building.
 * Sent from client when player assigns/unassigns work in the UI.
 */
public class AssignWorkPacket {
    public static final Identifier ID = new Identifier("settlements", "assign_work");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID settlementId = buf.readUuid();
            UUID villagerId = buf.readUuid();
            UUID buildingId = buf.readUuid(); // null UUID means unassign
            boolean assign = buf.readBoolean(); // true = assign, false = unassign
            
            server.execute(() -> {
                try {
                    SettlementManager manager = SettlementManager.getInstance(player.getServerWorld());
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot assign work: settlement {} not found", settlementId);
                        player.sendMessage(Text.translatable("settlements.work.settlement_not_found"), false);
                        return;
                    }
                    
                    if (assign) {
                        // Assign villager to building
                        if (WorkAssignmentManager.assignVillagerToBuilding(settlement, villagerId, buildingId)) {
                            player.sendMessage(Text.translatable("settlements.work.assigned"), false);
                            manager.markDirty();
                        } else {
                            player.sendMessage(Text.translatable("settlements.work.assignment_failed"), false);
                        }
                    } else {
                        // Unassign villager
                        if (WorkAssignmentManager.unassignVillager(settlement, villagerId)) {
                            player.sendMessage(Text.translatable("settlements.work.unassigned"), false);
                            manager.markDirty();
                        } else {
                            player.sendMessage(Text.translatable("settlements.work.unassignment_failed"), false);
                        }
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing assign work packet", e);
                    player.sendMessage(Text.translatable("settlements.error.generic"), false);
                }
            });
        });
    }
}

