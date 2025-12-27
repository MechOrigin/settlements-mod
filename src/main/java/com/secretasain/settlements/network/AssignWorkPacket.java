package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import com.secretasain.settlements.settlement.VillagerData;
import com.secretasain.settlements.settlement.WorkAssignmentManager;
import com.secretasain.settlements.trader.TraderVillagerManager;
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
                        // Check capacity first to provide better error messages
                        if (!com.secretasain.settlements.settlement.BuildingCapacity.canAcceptMoreVillagers(settlement, buildingId)) {
                            Building building = settlement.getBuildings().stream()
                                .filter(b -> b.getId().equals(buildingId))
                                .findFirst()
                                .orElse(null);
                            if (building != null) {
                                int capacity = com.secretasain.settlements.settlement.BuildingCapacity.getCapacity(building.getStructureType());
                                int assigned = com.secretasain.settlements.settlement.WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, buildingId).size();
                                player.sendMessage(Text.translatable("settlements.work.building_full", capacity, assigned), false);
                            } else {
                                player.sendMessage(Text.translatable("settlements.work.assignment_failed"), false);
                            }
                        } else if (WorkAssignmentManager.assignVillagerToBuilding(settlement, villagerId, buildingId)) {
                            // Check if building is a trader hut and convert villager if needed
                            Building building = settlement.getBuildings().stream()
                                .filter(b -> b.getId().equals(buildingId))
                                .findFirst()
                                .orElse(null);
                            
                            if (building != null) {
                                // Check if this is a trader hut
                                String structurePath = building.getStructureType().getPath();
                                if (structurePath.contains("trader_hut") || structurePath.contains("traderhut")) {
                                    // Find villager data
                                    VillagerData villagerData = settlement.getVillagers().stream()
                                        .filter(v -> v.getEntityId().equals(villagerId))
                                        .findFirst()
                                        .orElse(null);
                                    
                                    if (villagerData != null) {
                                        // Convert to special trader
                                        TraderVillagerManager.convertToSpecialTrader(
                                            player.getServerWorld(), 
                                            settlement, 
                                            villagerData, 
                                            building
                                        );
                                    }
                                }
                                
                                // Send settlement data sync to client so UI updates assignment counts
                                SyncBuildingStatusPacket.sendToPlayer(player, settlement, building);
                            }
                            
                            player.sendMessage(Text.translatable("settlements.work.assigned"), false);
                            manager.markDirty();
                        } else {
                            player.sendMessage(Text.translatable("settlements.work.assignment_failed"), false);
                        }
                    } else {
                        // Unassign villager - find building BEFORE unassigning so we can send sync
                        Building affectedBuilding = null;
                        for (Building b : settlement.getBuildings()) {
                            var assigned = WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, b.getId());
                            if (assigned.stream().anyMatch(v -> v.getEntityId().equals(villagerId))) {
                                affectedBuilding = b;
                                break;
                            }
                        }
                        
                        if (WorkAssignmentManager.unassignVillager(settlement, villagerId)) {
                            // Check if villager was a special trader and restore original profession
                            VillagerData villagerData = settlement.getVillagers().stream()
                                .filter(v -> v.getEntityId().equals(villagerId))
                                .findFirst()
                                .orElse(null);
                            
                            if (villagerData != null && TraderVillagerManager.isSpecialTrader(villagerId)) {
                                // Restore original profession
                                TraderVillagerManager.restoreOriginalProfession(
                                    player.getServerWorld(),
                                    settlement,
                                    villagerData,
                                    affectedBuilding
                                );
                            }
                            
                            player.sendMessage(Text.translatable("settlements.work.unassigned"), false);
                            manager.markDirty();
                            // Send sync to update UI assignment counts
                            if (affectedBuilding != null) {
                                SyncBuildingStatusPacket.sendToPlayer(player, settlement, affectedBuilding);
                            }
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

