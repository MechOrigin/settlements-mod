package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.GolemAssignmentManager;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Network packet to assign or unassign a golem to/from a wall station.
 * Sent from client when player assigns/unassigns golems in the UI.
 */
public class AssignGolemPacket {
    public static final Identifier ID = new Identifier("settlements", "assign_golem");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID settlementId = buf.readUuid();
            UUID golemId = buf.readUuid();
            UUID buildingId = buf.readUuid(); // null UUID means unassign
            boolean assign = buf.readBoolean(); // true = assign, false = unassign
            
            server.execute(() -> {
                try {
                    SettlementManager manager = SettlementManager.getInstance(player.getServerWorld());
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot assign golem: settlement {} not found", settlementId);
                        player.sendMessage(Text.translatable("settlements.golem.settlement_not_found"), false);
                        return;
                    }
                    
                    if (assign) {
                        // Assign golem to wall station
                        if (GolemAssignmentManager.assignGolemToWallStation(settlement, golemId, buildingId)) {
                            player.sendMessage(Text.translatable("settlements.golem.assigned"), false);
                            manager.markDirty();
                        } else {
                            player.sendMessage(Text.translatable("settlements.golem.assignment_failed"), false);
                        }
                    } else {
                        // Unassign golem
                        if (GolemAssignmentManager.unassignGolem(settlement, golemId)) {
                            player.sendMessage(Text.translatable("settlements.golem.unassigned"), false);
                            manager.markDirty();
                        } else {
                            player.sendMessage(Text.translatable("settlements.golem.unassignment_failed"), false);
                        }
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing assign golem packet", e);
                    player.sendMessage(Text.translatable("settlements.error.generic"), false);
                }
            });
        });
    }
}

