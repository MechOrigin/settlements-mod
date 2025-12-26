package com.secretasain.settlements.settlement;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles scanning and tracking of villagers within settlements.
 */
public class VillagerTracker {
    
    /**
     * Scans for villagers within a settlement's radius.
     * @param settlement The settlement to scan for
     * @param world The server world
     * @return List of VillagerData for villagers found within the settlement radius
     */
    public static List<VillagerData> scanForVillagers(Settlement settlement, ServerWorld world) {
        List<VillagerData> foundVillagers = new ArrayList<>();
        
        if (settlement == null || world == null || settlement.getLecternPos() == null) {
            return foundVillagers;
        }
        
        BlockPos center = settlement.getLecternPos();
        int radius = settlement.getRadius();
        
        // Create bounding box for efficient spatial query
        // Expand box by radius in all directions
        Box boundingBox = new Box(
            center.getX() - radius, center.getY() - radius, center.getZ() - radius,
            center.getX() + radius, center.getY() + radius, center.getZ() + radius
        );
        
        // Get all villagers within the bounding box
        List<VillagerEntity> villagers = world.getEntitiesByType(
            EntityType.VILLAGER,
            boundingBox,
            villager -> {
                // Additional filter: check if villager is within actual radius (not just bounding box)
                if (villager == null || villager.isRemoved()) {
                    return false;
                }
                BlockPos villagerPos = villager.getBlockPos();
                double distanceSq = center.getSquaredDistance(villagerPos);
                return distanceSq <= (radius * radius);
            }
        );
        
        // Convert VillagerEntity to VillagerData
        for (VillagerEntity villager : villagers) {
            UUID entityId = villager.getUuid();
            BlockPos lastKnownPos = villager.getBlockPos();
            
            // Get profession (convert to string for now, can be improved later)
            VillagerProfession profession = villager.getVillagerData().getProfession();
            String professionName = "none";
            if (profession != null) {
                Identifier professionId = Registries.VILLAGER_PROFESSION.getId(profession);
                professionName = professionId != null ? professionId.toString() : "none";
            }
            
            // Get villager name (custom name or generate one)
            String name = villager.hasCustomName() 
                ? villager.getCustomName().getString() 
                : generateVillagerName(villager);
            
            // Check if already employed (for now, default to false)
            boolean isEmployed = false; // TODO: Check employment status from settlement
            
            VillagerData data = new VillagerData(
                entityId,
                lastKnownPos,
                professionName,
                isEmployed,
                name
            );
            
            foundVillagers.add(data);
        }
        
        return foundVillagers;
    }
    
    /**
     * Generates a name for a villager if it doesn't have a custom name.
     * @param villager The villager entity
     * @return Generated name
     */
    private static String generateVillagerName(VillagerEntity villager) {
        // Simple name generation based on profession
        VillagerProfession profession = villager.getVillagerData().getProfession();
        String professionName = "villager";
        if (profession != null) {
            Identifier professionId = Registries.VILLAGER_PROFESSION.getId(profession);
            if (professionId != null) {
                professionName = professionId.getPath();
            }
        }
        
        // Capitalize first letter
        professionName = professionName.substring(0, 1).toUpperCase() + professionName.substring(1);
        
        // Add a simple identifier (last 4 chars of UUID)
        String uuidStr = villager.getUuid().toString();
        String id = uuidStr.substring(uuidStr.length() - 4);
        
        return professionName + " #" + id;
    }
}

