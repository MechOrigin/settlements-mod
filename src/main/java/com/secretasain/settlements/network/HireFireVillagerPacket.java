package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.HiringCostCalculator;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementLevelManager;
import com.secretasain.settlements.settlement.SettlementManager;
import com.secretasain.settlements.settlement.VillagerData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Network packet to hire or fire a villager.
 * Sent from client when player clicks hire/fire button.
 */
public class HireFireVillagerPacket {
    public static final Identifier ID = new Identifier("settlements", "hire_fire_villager");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID villagerId = buf.readUuid();
            UUID settlementId = buf.readUuid();
            boolean hire = buf.readBoolean(); // true = hire, false = fire
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    SettlementManager manager = SettlementManager.getInstance(world);
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot {} villager: settlement {} not found", 
                            hire ? "hire" : "fire", settlementId);
                        player.sendMessage(net.minecraft.text.Text.literal("Settlement not found"), false);
                        return;
                    }
                    
                    // Find the villager
                    VillagerData villager = null;
                    for (VillagerData v : settlement.getVillagers()) {
                        if (v.getEntityId().equals(villagerId)) {
                            villager = v;
                            break;
                        }
                    }
                    
                    if (villager == null) {
                        SettlementsMod.LOGGER.warn("Cannot {} villager: villager {} not found in settlement", 
                            hire ? "hire" : "fire", villagerId);
                        player.sendMessage(net.minecraft.text.Text.literal("Villager not found"), false);
                        return;
                    }
                    
                    // Update employment status
                    if (hire) {
                        if (villager.isEmployed()) {
                            player.sendMessage(Text.translatable("settlements.hire.already_employed"), false);
                            return;
                        }
                        
                        // Calculate and check hiring cost
                        int cost = HiringCostCalculator.calculateHiringCost(villager);
                        int emeraldCount = countEmeralds(player);
                        
                        if (emeraldCount < cost) {
                            player.sendMessage(Text.translatable("settlements.hire.insufficient_emeralds", cost, emeraldCount), false);
                            return;
                        }
                        
                        // Deduct emeralds from player inventory
                        if (!deductEmeralds(player, cost)) {
                            player.sendMessage(Text.translatable("settlements.hire.payment_failed"), false);
                            return;
                        }
                        
                        villager.setEmployed(true);
                        SettlementsMod.LOGGER.info("Hired villager {} in settlement {} for {} emeralds", 
                            villagerId, settlementId, cost);
                        player.sendMessage(Text.translatable("settlements.hire.success", villager.getName(), cost), false);
                        
                        // Update settlement level (may have changed with new employed villager)
                        int oldLevel = settlement.getLevel();
                        if (SettlementLevelManager.updateSettlementLevel(settlement)) {
                            int newLevel = settlement.getLevel();
                            if (newLevel > oldLevel) {
                                player.sendMessage(Text.translatable("settlements.level.up", oldLevel, newLevel), false);
                            }
                        }
                    } else {
                        if (!villager.isEmployed()) {
                            player.sendMessage(Text.translatable("settlements.fire.not_employed"), false);
                            return;
                        }
                        villager.setEmployed(false);
                        SettlementsMod.LOGGER.info("Fired villager {} from settlement {}", villagerId, settlementId);
                        player.sendMessage(Text.translatable("settlements.fire.success", villager.getName()), false);
                    }
                    
                    // Mark settlement as dirty to ensure changes are saved
                    manager.markDirty();
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing hire/fire villager packet", e);
                    player.sendMessage(Text.translatable("settlements.error.generic"), false);
                }
            });
        });
    }
    
    /**
     * Counts the number of emeralds in the player's inventory.
     * @param player The player
     * @return The total count of emeralds
     */
    private static int countEmeralds(net.minecraft.server.network.ServerPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.EMERALD) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    /**
     * Deducts the specified number of emeralds from the player's inventory.
     * @param player The player
     * @param amount The number of emeralds to deduct
     * @return true if successful, false otherwise
     */
    private static boolean deductEmeralds(net.minecraft.server.network.ServerPlayerEntity player, int amount) {
        int remaining = amount;
        
        // Deduct from inventory slots
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.EMERALD) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
                
                if (stack.isEmpty()) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
        }
        
        return remaining == 0;
    }
}

