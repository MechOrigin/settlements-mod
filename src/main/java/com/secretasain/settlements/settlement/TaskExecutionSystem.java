package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Handles automated task execution for assigned villagers.
 * Villagers perform work at their assigned buildings and generate outputs.
 */
public class TaskExecutionSystem {
    private static final int TASK_INTERVAL_TICKS = 200; // Execute tasks every 2.5 seconds (50 ticks)
    private static final double WORK_DISTANCE_SQ = 16.0 * 16.0; // Villager must be within 16 blocks to work
    
    /**
     * Registers the task execution system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tick(world);
        });
    }
    
    /**
     * Performs a tick update for the given world.
     * @param world The server world to update
     */
    private static void tick(ServerWorld world) {
        // Only run periodically to avoid performance issues
        if (world.getTime() % TASK_INTERVAL_TICKS != 0) {
            return;
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        // Process each settlement
        for (Settlement settlement : allSettlements) {
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Processes task execution for all assigned villagers in a settlement.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        // Check if it's work hours (daytime: 1000-12000 ticks, roughly 6 AM to 6 PM)
        long timeOfDay = world.getTimeOfDay() % 24000;
        boolean isWorkHours = timeOfDay >= 1000 && timeOfDay < 12000;
        
        if (!isWorkHours) {
            return; // Villagers don't work at night
        }
        
        for (VillagerData villagerData : settlement.getVillagers()) {
            // Only process employed and assigned villagers
            if (!villagerData.isEmployed() || !villagerData.isAssigned()) {
                continue;
            }
            
            UUID buildingId = villagerData.getAssignedBuildingId();
            if (buildingId == null) {
                continue;
            }
            
            // Find the building
            Building building = settlement.getBuildings().stream()
                .filter(b -> b.getId().equals(buildingId))
                .findFirst()
                .orElse(null);
            
            if (building == null || building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Get the actual villager entity
            VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
            if (villager == null) {
                continue; // Villager not loaded or doesn't exist
            }
            
            // Check if villager is close enough to building to work
            BlockPos buildingPos = building.getPosition();
            double distanceSq = villager.getPos().squaredDistanceTo(
                buildingPos.getX() + 0.5,
                buildingPos.getY() + 0.5,
                buildingPos.getZ() + 0.5
            );
            
            if (distanceSq > WORK_DISTANCE_SQ) {
                continue; // Villager is too far from building to work
            }
            
            // Skip if villager is currently depositing (let deposit system handle it)
            if (villagerData.isDepositing()) {
                continue;
            }
            
            // Execute task based on building type
            executeTask(settlement, villagerData, building, villager, world);
        }
    }
    
    /**
     * Executes a work task for a villager at their assigned building.
     */
    private static void executeTask(Settlement settlement, VillagerData villagerData, 
                                   Building building, VillagerEntity villager, ServerWorld world) {
        Identifier structureType = building.getStructureType();
        String structureName = getStructureName(structureType);
        
        // Determine building type for config lookup
        String buildingType = determineBuildingType(structureName);
        if (buildingType == null) {
            return; // No task for this building type
        }
        
        // Generate outputs using JSON config
        Random random = new Random();
        List<ItemStack> outputs = BuildingOutputConfig.generateOutputs(buildingType, random);
        
        if (outputs.isEmpty()) {
            return; // No outputs generated
        }
        
        // Add outputs to villager's accumulated items (not directly to storage)
        for (ItemStack output : outputs) {
            Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(output.getItem());
            if (itemId != null) {
                String itemKey = itemId.toString();
                villagerData.addAccumulatedItem(itemKey, output.getCount());
            }
        }
        
        // Mark settlement as dirty to save accumulated items
        SettlementManager.getInstance(world).markDirty();
        
        SettlementsMod.LOGGER.debug("Villager {} accumulated {} items at {} building (total: {})", 
            villagerData.getEntityId(), outputs.size(), structureName, villagerData.getTotalAccumulatedItems());
    }
    
    /**
     * Gets the structure name from the identifier.
     */
    private static String getStructureName(Identifier structureType) {
        String path = structureType.getPath();
        if (path.contains("/")) {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        if (path.endsWith(".nbt")) {
            path = path.substring(0, path.length() - 4);
        }
        return path.toLowerCase();
    }
    
    /**
     * Determines the building type for config lookup.
     */
    private static String determineBuildingType(String structureName) {
        if (structureName.contains("wall") || structureName.contains("fence") || structureName.contains("gate")) {
            return "wall";
        } else if (structureName.contains("smithing") || structureName.contains("smith")) {
            return "smithing";
        } else if (structureName.contains("farm") || structureName.contains("farmland")) {
            return "farm";
        } else if (structureName.contains("cartographer") || structureName.contains("cartography")) {
            return "cartographer";
        } else if (structureName.contains("house")) {
            return null; // Houses don't produce outputs (housing assignment only)
        }
        
        return null; // Unknown building type
    }
    
    
    /**
     * Gets the VillagerEntity from the world by UUID.
     */
    private static VillagerEntity getVillagerEntity(ServerWorld world, UUID entityId) {
        try {
            return (VillagerEntity) world.getEntity(entityId);
        } catch (Exception e) {
            return null;
        }
    }
    
}

