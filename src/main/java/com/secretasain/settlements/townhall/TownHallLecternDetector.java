package com.secretasain.settlements.townhall;

import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;

/**
 * Utility class for detecting if a lectern is inside a town hall building.
 */
public class TownHallLecternDetector {
    
    /**
     * Checks if a lectern position is inside any town hall building in a settlement.
     * @param settlement The settlement to check
     * @param lecternPos The lectern position
     * @param server The Minecraft server (for loading structure data)
     * @return The town hall building if found, null otherwise
     */
    public static Building findTownHallForLectern(Settlement settlement, BlockPos lecternPos, MinecraftServer server) {
        if (settlement == null || lecternPos == null || server == null) {
            return null;
        }
        
        // Check all buildings in the settlement
        for (Building building : settlement.getBuildings()) {
            if (!TownHallDetector.isTownHall(building)) {
                continue; // Skip non-town-hall buildings
            }
            
            // Check if lectern is within the building's structure bounds
            if (isLecternInBuilding(lecternPos, building, server)) {
                return building;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a lectern position is within a building's structure bounds.
     * @param lecternPos The lectern position
     * @param building The building to check
     * @param server The Minecraft server
     * @return true if lectern is within building bounds
     */
    private static boolean isLecternInBuilding(BlockPos lecternPos, Building building, MinecraftServer server) {
        if (building == null || lecternPos == null) {
            return false;
        }
        
        // Load structure data to get dimensions
        StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), server);
        if (structureData == null) {
            return false;
        }
        
        // Get building base position and dimensions
        BlockPos buildingBasePos = building.getPosition();
        Vec3i dimensions = structureData.getDimensions();
        
        // Calculate bounding box for the building
        // Account for rotation if needed (simplified: assume rotation doesn't change bounding box much)
        Box buildingBox = new Box(
            buildingBasePos.getX(),
            buildingBasePos.getY(),
            buildingBasePos.getZ(),
            buildingBasePos.getX() + dimensions.getX(),
            buildingBasePos.getY() + dimensions.getY(),
            buildingBasePos.getZ() + dimensions.getZ()
        );
        
        // Check if lectern is within the bounding box
        return buildingBox.contains(lecternPos.getX() + 0.5, lecternPos.getY() + 0.5, lecternPos.getZ() + 0.5);
    }
    
    /**
     * Gets the town hall building associated with a lectern position.
     * @param settlement The settlement
     * @param lecternPos The lectern position
     * @param server The Minecraft server
     * @return Town hall building or null if not found
     */
    public static Building getTownHallForLectern(Settlement settlement, BlockPos lecternPos, MinecraftServer server) {
        return findTownHallForLectern(settlement, lecternPos, server);
    }
}

