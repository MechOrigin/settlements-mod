package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Network packet to unload internal inventory (providedMaterials) to nearby chests.
 * Sent from client when player clicks "Unload Inventory" button.
 */
public class UnloadInventoryPacket {
    public static final Identifier ID = new Identifier("settlements", "unload_inventory");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            // Read building ID (can be null to indicate settlement storage)
            boolean hasBuilding = buf.readBoolean();
            UUID buildingId = hasBuilding ? buf.readUuid() : null;
            UUID settlementId = buf.readUuid();
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    SettlementManager manager = SettlementManager.getInstance(world);
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot unload inventory: settlement {} not found", settlementId);
                        player.sendMessage(net.minecraft.text.Text.literal("Settlement not found"), false);
                        return;
                    }
                    
                    if (buildingId != null) {
                        // Unload from building's providedMaterials
                        Building building = null;
                        for (Building b : settlement.getBuildings()) {
                            if (b.getId().equals(buildingId)) {
                                building = b;
                                break;
                            }
                        }
                        
                        if (building == null) {
                            SettlementsMod.LOGGER.warn("Cannot unload inventory: building {} not found in settlement {}", buildingId, settlementId);
                            player.sendMessage(net.minecraft.text.Text.literal("Building not found"), false);
                            return;
                        }
                        
                        // Check if building has any materials to unload
                        java.util.Map<net.minecraft.util.Identifier, Integer> provided = building.getProvidedMaterials();
                        if (provided.isEmpty()) {
                            SettlementsMod.LOGGER.debug("Building {} has no materials to unload", buildingId);
                            player.sendMessage(net.minecraft.text.Text.literal("No materials in internal inventory"), false);
                            // Still sync to ensure UI is up to date
                            com.secretasain.settlements.network.SyncBuildingStatusPacket.sendToPlayer(player, settlement, building);
                            return;
                        }
                        
                        // Log what we're unloading for verification (helps debug duplication issues)
                        SettlementsMod.LOGGER.info("Unloading materials from building {}: {}", buildingId, provided);
                        for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> entry : provided.entrySet()) {
                            SettlementsMod.LOGGER.info("  - {}: {} items", entry.getKey(), entry.getValue());
                        }
                        
                        // Unload materials to chests
                        int materialCount = provided.size();
                        int totalItems = provided.values().stream().mapToInt(Integer::intValue).sum();
                        
                        SettlementsMod.LOGGER.info("Unloading {} material types ({} total items) from building {} to chests", 
                            materialCount, totalItems, buildingId);
                        
                        // Return materials to chests (this will clear providedMaterials)
                        com.secretasain.settlements.building.MaterialManager.returnMaterialsToChests(building, settlement, world);
                        
                        // Mark settlement as dirty to save changes
                        manager.markDirty();
                        
                        SettlementsMod.LOGGER.info("Successfully unloaded {} material types from building {} to chests", 
                            materialCount, buildingId);
                        player.sendMessage(net.minecraft.text.Text.literal(
                            String.format("Unloaded %d material types (%d items) from building to chest", materialCount, totalItems)), false);
                        
                        // CRITICAL: Sync building status to client so UI updates
                        // This ensures the client sees that providedMaterials is now empty
                        com.secretasain.settlements.network.SyncBuildingStatusPacket.sendToPlayer(player, settlement, building);
                        
                        // Also sync materials to update UI
                        com.secretasain.settlements.network.SyncMaterialsPacket.send(player, settlement);
                    } else {
                        // Unload from settlement storage directly
                        java.util.Map<String, Integer> settlementMaterials = settlement.getMaterials();
                        if (settlementMaterials.isEmpty()) {
                            SettlementsMod.LOGGER.debug("Settlement {} has no materials to unload", settlementId);
                            player.sendMessage(net.minecraft.text.Text.literal("No materials in settlement storage"), false);
                            return;
                        }
                        
                        // Convert settlement materials to Identifier map
                        java.util.Map<net.minecraft.util.Identifier, Integer> materialsToUnload = new java.util.HashMap<>();
                        for (java.util.Map.Entry<String, Integer> entry : settlementMaterials.entrySet()) {
                            net.minecraft.util.Identifier materialId = net.minecraft.util.Identifier.tryParse(entry.getKey());
                            if (materialId != null) {
                                materialsToUnload.put(materialId, entry.getValue());
                            }
                        }
                        
                        if (materialsToUnload.isEmpty()) {
                            SettlementsMod.LOGGER.warn("Settlement {} has materials but none could be parsed as identifiers", settlementId);
                            player.sendMessage(net.minecraft.text.Text.literal("Error: Could not parse materials"), false);
                            return;
                        }
                        
                        // Log what we're unloading
                        SettlementsMod.LOGGER.info("Unloading materials from settlement storage: {} material types", materialsToUnload.size());
                        for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> entry : materialsToUnload.entrySet()) {
                            SettlementsMod.LOGGER.info("  - {}: {} items", entry.getKey(), entry.getValue());
                        }
                        
                        int materialCount = materialsToUnload.size();
                        int totalItems = materialsToUnload.values().stream().mapToInt(Integer::intValue).sum();
                        
                        // Unload materials from settlement storage to chests
                        com.secretasain.settlements.building.MaterialManager.unloadSettlementStorageToChests(settlement, world, materialsToUnload);
                        
                        // Mark settlement as dirty to save changes
                        manager.markDirty();
                        
                        SettlementsMod.LOGGER.info("Successfully unloaded {} material types ({} items) from settlement storage to chests", 
                            materialCount, totalItems);
                        player.sendMessage(net.minecraft.text.Text.literal(
                            String.format("Unloaded %d material types (%d items) from settlement storage to chest", materialCount, totalItems)), false);
                        
                        // CRITICAL: Sync materials to client so UI updates in real-time
                        com.secretasain.settlements.network.SyncMaterialsPacket.send(player, settlement);
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error unloading inventory", e);
                    player.sendMessage(net.minecraft.text.Text.literal("Error unloading inventory"), false);
                }
            });
        });
    }
}

