package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import com.secretasain.settlements.warband.NpcClass;
import com.secretasain.settlements.warband.ParagonLevel;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Network packet to hire an NPC.
 * Sent from client when player clicks hire button.
 */
public class HireNpcPacket {
    public static final Identifier ID = new Identifier("settlements", "hire_npc");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID barracksId = buf.readUuid();
            UUID settlementId = buf.readUuid();
            String npcClassStr = buf.readString();
            String paragonLevelStr = buf.readString();
            
            server.execute(() -> {
                try {
                    // Parse enums
                    NpcClass npcClass;
                    ParagonLevel paragonLevel;
                    try {
                        npcClass = NpcClass.valueOf(npcClassStr);
                        paragonLevel = ParagonLevel.valueOf(paragonLevelStr);
                    } catch (IllegalArgumentException e) {
                        SettlementsMod.LOGGER.warn("Invalid NPC class or paragon level: {} / {}", npcClassStr, paragonLevelStr);
                        player.sendMessage(Text.literal("Invalid NPC class or paragon level"), false);
                        return;
                    }
                    
                    ServerWorld world = player.getServerWorld();
                    SettlementManager manager = SettlementManager.getInstance(world);
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot hire NPC: settlement {} not found", settlementId);
                        player.sendMessage(Text.literal("Settlement not found"), false);
                        return;
                    }
                    
                    // Find the barracks building
                    com.secretasain.settlements.settlement.Building barracks = null;
                    for (com.secretasain.settlements.settlement.Building building : settlement.getBuildings()) {
                        if (building.getId().equals(barracksId)) {
                            String structureName = building.getStructureType().getPath().toLowerCase();
                            if (structureName.contains("barracks") && 
                                building.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                                barracks = building;
                                break;
                            }
                        }
                    }
                    
                    if (barracks == null) {
                        SettlementsMod.LOGGER.warn("Cannot hire NPC: barracks {} not found or not completed", barracksId);
                        player.sendMessage(Text.literal("Barracks not found or not completed"), false);
                        return;
                    }
                    
                    // Check player level requirement
                    int playerLevel = player.experienceLevel;
                    int requiredLevel = paragonLevel.getRequiredPlayerLevel();
                    if (playerLevel < requiredLevel) {
                        player.sendMessage(Text.translatable("settlements.warband.hire.level_requirement", 
                            requiredLevel, playerLevel), false);
                        return;
                    }
                    
                    // Calculate hiring cost
                    int cost = calculateHireCost(paragonLevel);
                    int emeraldCount = countEmeralds(player);
                    
                    if (emeraldCount < cost) {
                        player.sendMessage(Text.translatable("settlements.hire.insufficient_emeralds", cost, emeraldCount), false);
                        return;
                    }
                    
                    // TODO: Check if NPC already hired for this class at this barracks
                    // For now, we'll allow multiple hires
                    
                    // Deduct emeralds from player inventory
                    if (!deductEmeralds(player, cost)) {
                        player.sendMessage(Text.translatable("settlements.hire.payment_failed"), false);
                        return;
                    }
                    
                    // Check if player already has this NPC class at this barracks
                    com.secretasain.settlements.warband.PlayerWarbandData warbandData = 
                        com.secretasain.settlements.warband.PlayerWarbandData.getOrCreate(world);
                    if (warbandData.hasNpcAtBarracks(player.getUuid(), barracksId, npcClass)) {
                        player.sendMessage(Text.translatable("settlements.warband.hire.already_hired", 
                            npcClass.getDisplayName().getString()), false);
                        return;
                    }
                    
                    // Find safe spawn position near barracks
                    net.minecraft.util.math.BlockPos spawnPos = findSafeSpawnPosition(world, barracks.getPosition());
                    if (spawnPos == null) {
                        player.sendMessage(Text.translatable("settlements.warband.hire.no_spawn_location"), false);
                        SettlementsMod.LOGGER.warn("Could not find safe spawn position for NPC near barracks {}", barracksId);
                        return;
                    }
                    
                    // Create and spawn NPC entity
                    com.secretasain.settlements.warband.WarbandNpcEntity npc = 
                        com.secretasain.settlements.warband.ModEntities.WARBAND_NPC.create(world);
                    if (npc == null) {
                        player.sendMessage(Text.translatable("settlements.error.generic"), false);
                        SettlementsMod.LOGGER.error("Failed to create WarbandNpcEntity");
                        return;
                    }
                    
                    // Set NPC data (this also equips gear)
                    npc.setNpcData(player.getUuid(), npcClass, paragonLevel, barracksId);
                    npc.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 
                        world.random.nextFloat() * 360f, 0f);
                    
                    // Spawn entity in world
                    world.spawnEntity(npc);
                    
                    // Re-equip gear after spawning (in case it was cleared during spawn)
                    npc.equipGear();
                    
                    // Store NPC data
                    com.secretasain.settlements.warband.NpcData npcData = new com.secretasain.settlements.warband.NpcData(
                        npc.getUuid(),
                        player.getUuid(),
                        npcClass,
                        paragonLevel,
                        barracksId,
                        barracks.getPosition()
                    );
                    warbandData.addNpc(player.getUuid(), npcData);
                    
                    // Sync NPCs to client
                    com.secretasain.settlements.network.SyncWarbandNpcsPacket.sendForBarracks(player, barracksId);
                    
                    SettlementsMod.LOGGER.info("Hired NPC: class={}, paragon={}, barracks={}, cost={}, entityId={}", 
                        npcClass, paragonLevel, barracksId, cost, npc.getUuid());
                    player.sendMessage(Text.translatable("settlements.warband.hire.success", 
                        npcClass.getDisplayName().getString(), paragonLevel.getDisplayName().getString(), cost), false);
                    
                    // Mark settlement as dirty to ensure changes are saved
                    manager.markDirty();
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error processing hire NPC packet", e);
                    player.sendMessage(Text.translatable("settlements.error.generic"), false);
                }
            });
        });
    }
    
    /**
     * Calculates the hiring cost based on paragon level.
     */
    private static int calculateHireCost(ParagonLevel level) {
        return switch (level) {
            case I -> 10;
            case II -> 25;
            case III -> 50;
            case IV -> 100;
        };
    }
    
    /**
     * Counts the number of emeralds in the player's inventory.
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
     */
    private static boolean deductEmeralds(net.minecraft.server.network.ServerPlayerEntity player, int amount) {
        int remaining = amount;
        
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
    
    /**
     * Finds a safe spawn position near the barracks building.
     * Tries positions in a spiral pattern around the barracks.
     */
    private static net.minecraft.util.math.BlockPos findSafeSpawnPosition(ServerWorld world, net.minecraft.util.math.BlockPos barracksPos) {
        // Try positions in a spiral pattern, starting close to barracks
        int[] offsets = {1, 2, 3, 4, 5}; // Try distances 1-5 blocks away
        int[] directions = {0, 1, 2, 3, 4, 5, 6, 7}; // 8 directions around barracks
        
        for (int offset : offsets) {
            for (int dir : directions) {
                // Calculate position in spiral pattern
                int x = barracksPos.getX();
                int z = barracksPos.getZ();
                
                switch (dir) {
                    case 0: x += offset; break; // East
                    case 1: x += offset; z += offset; break; // Southeast
                    case 2: z += offset; break; // South
                    case 3: x -= offset; z += offset; break; // Southwest
                    case 4: x -= offset; break; // West
                    case 5: x -= offset; z -= offset; break; // Northwest
                    case 6: z -= offset; break; // North
                    case 7: x += offset; z -= offset; break; // Northeast
                }
                
                // Find ground level at this X/Z position
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                net.minecraft.util.math.BlockPos testPos = new net.minecraft.util.math.BlockPos(x, y, z);
                
                // Check if position is safe
                if (isSafeSpawnPosition(world, testPos)) {
                    return testPos;
                }
            }
        }
        
        // If no safe position found, try directly above barracks
        net.minecraft.util.math.BlockPos abovePos = barracksPos.up(2);
        if (isSafeSpawnPosition(world, abovePos)) {
            return abovePos;
        }
        
        return null;
    }
    
    /**
     * Checks if a position is safe for spawning an NPC.
     */
    private static boolean isSafeSpawnPosition(ServerWorld world, net.minecraft.util.math.BlockPos pos) {
        // Check if ground is solid
        if (!world.getBlockState(pos.down()).isOpaque()) {
            return false;
        }
        
        // Check if position and position above are air
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        if (!world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        
        // Check if there are no entities blocking
        net.minecraft.util.math.Box boundingBox = new net.minecraft.util.math.Box(pos);
        if (!world.getEntitiesByClass(net.minecraft.entity.Entity.class, boundingBox, e -> true).isEmpty()) {
            return false;
        }
        
        return true;
    }
}

