package com.secretasain.settlements.townhall;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureBlock;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.VillagerData;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;

import java.util.UUID;

/**
 * Manages librarian assignment to town hall buildings.
 * Handles conversion of villagers to librarians and restoration of original professions.
 */
public class TownHallLibrarianManager {
    
    /**
     * Assigns a villager as librarian to a town hall building.
     * @param world The server world
     * @param settlement The settlement
     * @param villagerData The villager data
     * @param building The town hall building
     * @return true if assignment was successful
     */
    public static boolean assignLibrarian(ServerWorld world, Settlement settlement,
                                        VillagerData villagerData, Building building) {
        if (world == null || settlement == null || villagerData == null || building == null) {
            return false;
        }
        
        // Check if building is a town hall
        if (!TownHallDetector.isTownHall(building)) {
            SettlementsMod.LOGGER.warn("Cannot assign librarian: building {} is not a town hall", building.getId());
            return false;
        }
        
        // Get villager entity
        VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
        if (villager == null) {
            SettlementsMod.LOGGER.warn("Cannot assign librarian: entity {} not found", villagerData.getEntityId());
            return false;
        }
        
        // Store original profession BEFORE changing it
        VillagerProfession originalProfession = villager.getVillagerData().getProfession();
        if (originalProfession == null) {
            originalProfession = VillagerProfession.NONE;
        }
        
        SettlementsMod.LOGGER.info("Villager {} original profession: {}", villager.getUuid(),
            originalProfession != null ? Registries.VILLAGER_PROFESSION.getId(originalProfession) : "null");
        
        // Find the lectern in the town hall structure
        BlockPos lecternPos = findLecternInStructure(world, building);
        if (lecternPos != null) {
            SettlementsMod.LOGGER.info("Found lectern at {} for town hall at {}", lecternPos, building.getPosition());
        } else {
            SettlementsMod.LOGGER.warn("No lectern found in town hall structure at {}. Villager may not take profession.", building.getPosition());
        }
        
        // Set profession to librarian
        VillagerProfession librarianProfession = Registries.VILLAGER_PROFESSION.get(new Identifier("minecraft:librarian"));
        
        if (librarianProfession != null) {
            try {
                // Set profession using withProfession
                villager.setVillagerData(villager.getVillagerData().withProfession(librarianProfession));
                
                // Verify the change took effect
                VillagerProfession newProfession = villager.getVillagerData().getProfession();
                SettlementsMod.LOGGER.info("Set villager {} to librarian profession (was: {}, now: {})",
                    villager.getUuid(),
                    originalProfession != null ? Registries.VILLAGER_PROFESSION.getId(originalProfession) : "null",
                    newProfession != null ? Registries.VILLAGER_PROFESSION.getId(newProfession) : "null");
                
                // Force a restock to update trades and make profession change visible
                try {
                    villager.restock();
                    SettlementsMod.LOGGER.info("Forced restock for villager {} after profession change", villager.getUuid());
                } catch (Exception e) {
                    SettlementsMod.LOGGER.warn("Failed to restock villager after profession change: {}", e.getMessage());
                }
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Failed to set villager profession: {}", e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            SettlementsMod.LOGGER.error("Librarian profession not found in registry!");
            return false;
        }
        
        // Update town hall data
        TownHallData hallData = TownHallData.getOrCreate(building);
        hallData.setAssignedLibrarianId(villagerData.getEntityId());
        hallData.saveToBuilding(building);
        
        SettlementsMod.LOGGER.info("Assigned villager {} as librarian to town hall {} in settlement {}",
            villagerData.getEntityId(), building.getId(), settlement.getName());
        SettlementsMod.LOGGER.info("Town hall {} is now active - villager spawning and wandering trader enhancement enabled",
            building.getId());
        
        return true;
    }
    
    /**
     * Unassigns the librarian from a town hall building.
     * @param world The server world
     * @param settlement The settlement
     * @param building The town hall building
     * @return true if unassignment was successful
     */
    public static boolean unassignLibrarian(ServerWorld world, Settlement settlement, Building building) {
        if (world == null || settlement == null || building == null) {
            return false;
        }
        
        // Check if building is a town hall
        if (!TownHallDetector.isTownHall(building)) {
            SettlementsMod.LOGGER.warn("Cannot unassign librarian: building {} is not a town hall", building.getId());
            return false;
        }
        
        // Get town hall data
        TownHallData hallData = TownHallData.getOrCreate(building);
        UUID librarianId = hallData.getAssignedLibrarianId();
        
        if (librarianId == null) {
            SettlementsMod.LOGGER.warn("No librarian assigned to town hall {}", building.getId());
            return false;
        }
        
        // Find villager in settlement
        VillagerData villagerData = null;
        for (VillagerData vData : settlement.getVillagers()) {
            if (vData.getEntityId().equals(librarianId)) {
                villagerData = vData;
                break;
            }
        }
        
        if (villagerData == null) {
            SettlementsMod.LOGGER.warn("Librarian villager {} not found in settlement", librarianId);
            // Clear assignment anyway
            hallData.setAssignedLibrarianId(null);
            hallData.saveToBuilding(building);
            return false;
        }
        
        // Get villager entity
        VillagerEntity villager = getVillagerEntity(world, villagerData.getEntityId());
        if (villager != null) {
            // Restore to jobless (NONE profession)
            // We don't store original profession for librarians, so just set to NONE
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));
            SettlementsMod.LOGGER.info("Restored villager {} to jobless from librarian", villagerData.getEntityId());
            
            // Clear trades
            villager.getOffers().clear();
            villager.restock(); // Force restock to clear librarian trades
        }
        
        // Clear assignment
        hallData.setAssignedLibrarianId(null);
        hallData.saveToBuilding(building);
        
        SettlementsMod.LOGGER.info("Unassigned librarian from town hall {}", building.getId());
        
        return true;
    }
    
    /**
     * Finds the position of a lectern block within the given building's structure.
     * @param world The server world
     * @param building The building to search within
     * @return The world position of the lectern, or null if not found
     */
    private static BlockPos findLecternInStructure(ServerWorld world, Building building) {
        Identifier structureId = building.getStructureType();
        StructureData structureData = StructureLoader.loadStructure(structureId, world.getServer());
        
        if (structureData == null) {
            SettlementsMod.LOGGER.warn("Could not load structure data for building {}", structureId);
            return null;
        }
        
        BlockPos buildingBasePos = building.getPosition();
        int rotation = building.getRotation();
        net.minecraft.util.math.Vec3i dimensions = structureData.getDimensions();
        
        for (StructureBlock structureBlock : structureData.getBlocks()) {
            if (structureBlock.getBlockState().isOf(Blocks.LECTERN)) {
                BlockPos relativePos = structureBlock.getRelativePos();
                // Apply rotation to get the correct relative position in the world
                BlockPos rotatedPos = applyRotation(relativePos, rotation, dimensions);
                BlockPos worldPos = buildingBasePos.add(rotatedPos);
                SettlementsMod.LOGGER.debug("Found lectern at relative pos {} (rotated {}) -> world pos {}", relativePos, rotatedPos, worldPos);
                return worldPos;
            }
        }
        return null;
    }
    
    /**
     * Applies rotation to a relative BlockPos within a structure.
     * @param pos The relative BlockPos
     * @param rotation The rotation in degrees (0, 90, 180, 270)
     * @param dimensions The dimensions of the structure (width, height, depth)
     * @return The rotated relative BlockPos
     */
    private static BlockPos applyRotation(BlockPos pos, int rotation, net.minecraft.util.math.Vec3i dimensions) {
        int x = pos.getX();
        int z = pos.getZ();
        
        switch (rotation) {
            case 90:
                // Rotate 90 degrees clockwise: (x, z) -> (sizeZ - 1 - z, x)
                return new BlockPos(dimensions.getZ() - 1 - z, pos.getY(), x);
            case 180:
                // Rotate 180 degrees: (x, z) -> (sizeX - 1 - x, sizeZ - 1 - z)
                return new BlockPos(dimensions.getX() - 1 - x, pos.getY(), dimensions.getZ() - 1 - z);
            case 270:
                // Rotate 270 degrees clockwise: (x, z) -> (z, sizeX - 1 - x)
                return new BlockPos(z, pos.getY(), dimensions.getX() - 1 - x);
            case 0:
            default:
                return pos;
        }
    }
    
    /**
     * Gets a villager entity from the world.
     * @param world The server world
     * @param villagerId The villager's entity UUID
     * @return VillagerEntity or null if not found
     */
    private static VillagerEntity getVillagerEntity(ServerWorld world, UUID villagerId) {
        if (world == null || villagerId == null) {
            return null;
        }
        
        try {
            net.minecraft.entity.Entity entity = world.getEntity(villagerId);
            if (entity instanceof VillagerEntity) {
                return (VillagerEntity) entity;
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error getting villager entity {}: {}", villagerId, e.getMessage());
        }
        
        return null;
    }
}

