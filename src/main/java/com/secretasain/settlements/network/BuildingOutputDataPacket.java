package com.secretasain.settlements.network;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.BuildingOutputConfig;
import com.secretasain.settlements.settlement.CropStatistics;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.*;

/**
 * Network packet to request building output data from server.
 * Server loads config and structure data, then sends it to client.
 */
public class BuildingOutputDataPacket {
    public static final Identifier ID = new Identifier("settlements", "building_output_data");
    
    /**
     * Registers the server-side packet handler.
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            UUID buildingId = buf.readUuid();
            UUID settlementId = buf.readUuid();
            
            server.execute(() -> {
                try {
                    ServerWorld world = player.getServerWorld();
                    SettlementManager manager = SettlementManager.getInstance(world);
                    Settlement settlement = manager.getSettlement(settlementId);
                    
                    if (settlement == null) {
                        SettlementsMod.LOGGER.warn("Cannot get building output data: settlement {} not found", settlementId);
                        sendResponse(player, buildingId, null, -1);
                        return;
                    }
                    
                    Building building = null;
                    for (Building b : settlement.getBuildings()) {
                        if (b.getId().equals(buildingId)) {
                            building = b;
                            break;
                        }
                    }
                    
                    if (building == null) {
                        SettlementsMod.LOGGER.warn("Cannot get building output data: building {} not found", buildingId);
                        sendResponse(player, buildingId, null, -1, null);
                        return;
                    }
                    
                    // CRITICAL: Only get outputs for COMPLETED buildings
                    if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                        SettlementsMod.LOGGER.debug("Cannot get building output data: building {} is not COMPLETED (status: {})", 
                            buildingId, building.getStatus());
                        sendResponse(player, buildingId, null, -1, null);
                        return;
                    }
                    
                    // Check if building has assigned villagers - no villagers = no outputs
                    List<com.secretasain.settlements.settlement.VillagerData> assignedVillagers = 
                        com.secretasain.settlements.settlement.WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, buildingId);
                    
                    if (assignedVillagers.isEmpty()) {
                        SettlementsMod.LOGGER.info("Building {} has no assigned villagers, sending empty response", buildingId);
                        sendResponse(player, buildingId, null, -1, null);
                        return;
                    }

                    SettlementsMod.LOGGER.info("Building {} has {} assigned villagers, processing outputs", 
                        buildingId, assignedVillagers.size());
                    
                    // Determine building type - extract filename from path
                    String structurePath = building.getStructureType().getPath().toLowerCase();
                    String structureName = getStructureName(building.getStructureType());
                    
                    String buildingType = determineBuildingType(structureName);
                    
                    SettlementsMod.LOGGER.info("Building output request: structurePath={}, structureName={}, buildingType={}", 
                        structurePath, structureName, buildingType);
                    
                    if ("farm".equals(buildingType)) {
                        // For farm buildings, scan crops and calculate statistics
                        List<CropStatistics> cropStats = scanCropsAndCalculateStatistics(building, world);
                        int farmlandCount = countFarmlandInStructure(building, world);
                        int boneMealProduced = com.secretasain.settlements.farm.FarmComposterSystem.getBoneMealProduction(buildingId);
                        SettlementsMod.LOGGER.info("Farm building: farmlandCount={}, cropTypes={}, boneMeal={}, sending response", 
                            farmlandCount, cropStats.size(), boneMealProduced);
                        sendResponse(player, buildingId, null, farmlandCount, cropStats, boneMealProduced);
                    } else if (buildingType != null) {
                        // For other buildings, get outputs from config
                        // Ensure config is loaded - always try to load if not loaded
                        if (!BuildingOutputConfig.isLoaded()) {
                            SettlementsMod.LOGGER.info("Building output config not loaded, loading now...");
                            BuildingOutputConfig.load(world.getServer().getResourceManager());
                            SettlementsMod.LOGGER.info("Building output config loaded: {} (configs: {})", 
                                BuildingOutputConfig.isLoaded(), BuildingOutputConfig.getLoadedConfigCount());
                        }
                        List<BuildingOutputConfig.OutputEntry> outputs = BuildingOutputConfig.getOutputs(buildingType);
                        SettlementsMod.LOGGER.info("Building type '{}': found {} output entries, sending response", buildingType, outputs.size());
                        if (outputs.isEmpty()) {
                            SettlementsMod.LOGGER.warn("No outputs found for building type '{}' - check building_outputs.json", buildingType);
                        }
                        sendResponse(player, buildingId, outputs, -1);
                    } else {
                        // Unknown building type
                        SettlementsMod.LOGGER.info("Unknown building type for structure: {}, sending empty response", structureName);
                        sendResponse(player, buildingId, null, -1, null);
                    }
                    
                } catch (Exception e) {
                    SettlementsMod.LOGGER.error("Error getting building output data", e);
                    sendResponse(player, buildingId, null, -1, null);
                }
            });
        });
    }
    
    /**
     * Sends building output data to client.
     */
    private static void sendResponse(ServerPlayerEntity player, UUID buildingId, 
                                     List<BuildingOutputConfig.OutputEntry> outputs, int farmlandCount) {
        sendResponse(player, buildingId, outputs, farmlandCount, null);
    }
    
    /**
     * Sends building output data to client (with crop statistics for farm buildings).
     */
    private static void sendResponse(ServerPlayerEntity player, UUID buildingId, 
                                     List<BuildingOutputConfig.OutputEntry> outputs, int farmlandCount,
                                     List<CropStatistics> cropStats) {
        sendResponse(player, buildingId, outputs, farmlandCount, cropStats, 0);
    }
    
    /**
     * Sends building output data to client (with crop statistics and bone meal for farm buildings).
     */
    private static void sendResponse(ServerPlayerEntity player, UUID buildingId, 
                                     List<BuildingOutputConfig.OutputEntry> outputs, int farmlandCount,
                                     List<CropStatistics> cropStats, int boneMealProduced) {
        var buf = PacketByteBufs.create();
        buf.writeUuid(buildingId);
        
        if (farmlandCount >= 0) {
            // Farm building - send farmland count, crop statistics, and bone meal
            buf.writeBoolean(true); // isFarm
            buf.writeInt(farmlandCount);
            buf.writeInt(boneMealProduced); // Bone meal produced by second villager
            
            // Send crop statistics
            if (cropStats != null && !cropStats.isEmpty()) {
                buf.writeInt(cropStats.size());
                for (CropStatistics stats : cropStats) {
                    buf.writeString(stats.cropType);
                    buf.writeString(stats.cropItemId.toString());
                    buf.writeInt(stats.totalCount);
                    buf.writeInt(stats.matureCount);
                    buf.writeInt(stats.immatureCount);
                    buf.writeInt(stats.averageAge);
                    buf.writeInt(stats.maxAge);
                    buf.writeLong(stats.estimatedTicksUntilHarvest);
                    buf.writeDouble(stats.avgDropsPerCrop);
                    
                    // Write age distribution
                    buf.writeInt(stats.ageDistribution.size());
                    for (Map.Entry<Integer, Integer> entry : stats.ageDistribution.entrySet()) {
                        buf.writeInt(entry.getKey());
                        buf.writeInt(entry.getValue());
                    }
                }
                SettlementsMod.LOGGER.debug("Sending farm building data: buildingId={}, farmlandCount={}, cropTypes={}, boneMeal={}", 
                    buildingId, farmlandCount, cropStats.size(), boneMealProduced);
            } else {
                buf.writeInt(0);
                SettlementsMod.LOGGER.debug("Sending farm building data: buildingId={}, farmlandCount={}, no crops, boneMeal={}", 
                    buildingId, farmlandCount, boneMealProduced);
            }
        } else if (outputs != null && !outputs.isEmpty()) {
            // Regular building - send output entries
            buf.writeBoolean(false); // isFarm
            buf.writeInt(outputs.size());
            for (BuildingOutputConfig.OutputEntry entry : outputs) {
                String itemId = Registries.ITEM.getId(entry.item).toString();
                buf.writeString(itemId);
                buf.writeInt(entry.weight);
                buf.writeInt(entry.minCount);
                buf.writeInt(entry.maxCount);
                SettlementsMod.LOGGER.debug("Sending output entry: item={}, weight={}, count={}-{}", 
                    itemId, entry.weight, entry.minCount, entry.maxCount);
            }
            SettlementsMod.LOGGER.debug("Sending {} output entries for building {}", outputs.size(), buildingId);
        } else {
            // No outputs
            buf.writeBoolean(false); // isFarm
            buf.writeInt(0);
            SettlementsMod.LOGGER.debug("Sending empty output data for building {}", buildingId);
        }
        
        ServerPlayNetworking.send(player, ID, buf);
    }
    
    /**
     * Extracts the base structure name from an Identifier.
     * Removes path prefixes and .nbt extension.
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
     * Determines building type from structure name.
     * Checks for keywords in the structure name (e.g., "lvl1_oak_farm" -> "farm").
     */
    private static String determineBuildingType(String structureName) {
        String lowerName = structureName.toLowerCase();
        
        // Check for farm first (before wall, since "farmland" contains "farm")
        if (lowerName.contains("farm") || lowerName.contains("farmland")) {
            return "farm";
        }
        // Check for wall/fence/gate (defensive structures)
        if (lowerName.contains("wall") || lowerName.contains("fence") || lowerName.contains("gate")) {
            return "wall";
        }
        // Check for smithing
        if (lowerName.contains("smithing") || lowerName.contains("smith")) {
            return "smithing";
        }
        // Check for cartographer
        if (lowerName.contains("cartographer") || lowerName.contains("cartography")) {
            return "cartographer";
        }
        
        return null;
    }
    
    /**
     * Counts farmland blocks in a building's structure.
     */
    private static int countFarmlandInStructure(Building building, ServerWorld world) {
        try {
            StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), world.getServer());
            if (structureData == null) {
                return 0;
            }
            
            int count = 0;
            for (var block : structureData.getBlocks()) {
                net.minecraft.block.BlockState state = block.getBlockState();
                // Check if it's farmland by checking the block registry ID
                net.minecraft.util.Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
                if (blockId != null && (blockId.getPath().contains("farmland") || 
                    blockId.toString().equals("minecraft:farmland"))) {
                    count++;
                }
            }
            
            return count;
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Failed to count farmland in structure: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Scans crops in a farm building's area and calculates statistics.
     * Similar to FarmCropHarvester but without harvesting - just scanning.
     */
    private static List<CropStatistics> scanCropsAndCalculateStatistics(Building building, ServerWorld world) {
        List<CropStatistics> cropStatsList = new ArrayList<>();
        
        try {
            // Load structure data to get the area
            StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), world.getServer());
            if (structureData == null) {
                SettlementsMod.LOGGER.warn("Could not load structure data for crop scanning: {}", building.getStructureType());
                return cropStatsList;
            }
            
            // Get building position and rotation
            BlockPos buildingPos = building.getPosition();
            int rotation = building.getRotation();
            Vec3i size = structureData.getDimensions();
            
            // Map to collect crop data: cropType -> (age -> count)
            Map<String, Map<Integer, Integer>> cropData = new HashMap<>();
            Map<String, Identifier> cropItemIds = new HashMap<>();
            Map<String, Integer> cropMaxAges = new HashMap<>();
            
            // Scan all blocks in the structure area
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        BlockPos relativePos = new BlockPos(x, y, z);
                        
                        // Apply rotation to relative position
                        BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
                        
                        // Convert to world position
                        BlockPos worldPos = buildingPos.add(rotatedPos);
                        
                        // Check if chunk is loaded
                        if (!world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                            continue;
                        }
                        
                        // Get block state
                        BlockState blockState = world.getBlockState(worldPos);
                        Block block = blockState.getBlock();
                        
                        // Check if this is farmland
                        if (block instanceof FarmlandBlock) {
                            // Check the block above for crops
                            BlockPos cropPos = worldPos.up();
                            if (!world.getChunkManager().isChunkLoaded(cropPos.getX() >> 4, cropPos.getZ() >> 4)) {
                                continue;
                            }
                            
                            BlockState cropState = world.getBlockState(cropPos);
                            Block cropBlock = cropState.getBlock();
                            
                            // Get crop type and age
                            CropInfo cropInfo = getCropInfo(cropState, cropBlock);
                            if (cropInfo != null) {
                                String cropType = cropInfo.type;
                                int age = cropInfo.age;
                                int maxAge = cropInfo.maxAge;
                                
                                // Initialize maps if needed
                                cropData.putIfAbsent(cropType, new HashMap<>());
                                cropItemIds.putIfAbsent(cropType, cropInfo.itemId);
                                cropMaxAges.putIfAbsent(cropType, maxAge);
                                
                                // Count this crop
                                Map<Integer, Integer> ageMap = cropData.get(cropType);
                                ageMap.put(age, ageMap.getOrDefault(age, 0) + 1);
                            }
                        }
                    }
                }
            }
            
            // Calculate statistics for each crop type
            for (Map.Entry<String, Map<Integer, Integer>> entry : cropData.entrySet()) {
                String cropType = entry.getKey();
                Map<Integer, Integer> ageDistribution = entry.getValue();
                Identifier cropItemId = cropItemIds.get(cropType);
                int maxAge = cropMaxAges.get(cropType);
                
                // Calculate totals
                int totalCount = ageDistribution.values().stream().mapToInt(Integer::intValue).sum();
                int matureCount = ageDistribution.getOrDefault(maxAge, 0);
                int immatureCount = totalCount - matureCount;
                
                // Calculate average age
                int totalAge = 0;
                for (Map.Entry<Integer, Integer> ageEntry : ageDistribution.entrySet()) {
                    totalAge += ageEntry.getKey() * ageEntry.getValue();
                }
                int averageAge = totalCount > 0 ? totalAge / totalCount : 0;
                
                // Estimate ticks until harvest for immature crops
                // Average growth time per stage: ~4286 ticks (25 minutes / 7 stages for AGE_7 crops)
                // For AGE_3 crops: ~10000 ticks per stage (25 minutes / 3 stages)
                long avgTicksPerStage = maxAge == 7 ? 4286L : 10000L;
                long estimatedTicksUntilHarvest = 0;
                if (immatureCount > 0) {
                    // Calculate average ticks remaining for all immature crops
                    long totalTicksRemaining = 0;
                    for (Map.Entry<Integer, Integer> ageEntry : ageDistribution.entrySet()) {
                        int age = ageEntry.getKey();
                        int count = ageEntry.getValue();
                        if (age < maxAge) {
                            int stagesRemaining = maxAge - age;
                            totalTicksRemaining += (long) stagesRemaining * avgTicksPerStage * count;
                        }
                    }
                    estimatedTicksUntilHarvest = totalTicksRemaining / immatureCount;
                }
                
                // Average drops per crop (typically 1-3, average 2)
                double avgDropsPerCrop = getAverageDropsPerCrop(cropType);
                
                CropStatistics stats = new CropStatistics(
                    cropType, cropItemId, totalCount, matureCount, immatureCount,
                    ageDistribution, averageAge, maxAge, estimatedTicksUntilHarvest, avgDropsPerCrop
                );
                cropStatsList.add(stats);
            }
            
            SettlementsMod.LOGGER.info("Scanned crops: found {} crop types with {} total crops", 
                cropStatsList.size(), cropStatsList.stream().mapToInt(s -> s.totalCount).sum());
            
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Error scanning crops for building {}: {}", building.getId(), e.getMessage(), e);
        }
        
        return cropStatsList;
    }
    
    /**
     * Helper class to hold crop information.
     */
    private static class CropInfo {
        final String type;
        final int age;
        final int maxAge;
        final Identifier itemId;
        
        CropInfo(String type, int age, int maxAge, Identifier itemId) {
            this.type = type;
            this.age = age;
            this.maxAge = maxAge;
            this.itemId = itemId;
        }
    }
    
    /**
     * Gets crop information from a block state.
     * Uses block registry ID to identify crop type (works for vanilla and modded crops).
     */
    private static CropInfo getCropInfo(BlockState cropState, Block cropBlock) {
        // Check if it's a crop block
        if (!(cropBlock instanceof CropBlock) && !(cropBlock instanceof BeetrootsBlock) && 
            !(cropBlock instanceof NetherWartBlock)) {
            return null;
        }
        
        Identifier blockId = Registries.BLOCK.getId(cropBlock);
        String blockPath = blockId.getPath();
        String cropType;
        int maxAge;
        Identifier itemId;
        
        // Identify vanilla crops by block ID
        if (blockPath.equals("wheat")) {
            cropType = "wheat";
            maxAge = 7;
            itemId = new Identifier("minecraft:wheat");
            int age = cropState.contains(Properties.AGE_7) ? cropState.get(Properties.AGE_7) : 0;
            return new CropInfo(cropType, age, maxAge, itemId);
        } else if (blockPath.equals("carrots")) {
            cropType = "carrots";
            maxAge = 7;
            itemId = new Identifier("minecraft:carrot");
            int age = cropState.contains(Properties.AGE_7) ? cropState.get(Properties.AGE_7) : 0;
            return new CropInfo(cropType, age, maxAge, itemId);
        } else if (blockPath.equals("potatoes")) {
            cropType = "potatoes";
            maxAge = 7;
            itemId = new Identifier("minecraft:potato");
            int age = cropState.contains(Properties.AGE_7) ? cropState.get(Properties.AGE_7) : 0;
            return new CropInfo(cropType, age, maxAge, itemId);
        } else if (blockPath.equals("beetroots")) {
            cropType = "beetroot";
            maxAge = 3;
            itemId = new Identifier("minecraft:beetroot");
            int age = cropState.contains(Properties.AGE_3) ? cropState.get(Properties.AGE_3) : 0;
            return new CropInfo(cropType, age, maxAge, itemId);
        } else if (blockPath.equals("nether_wart")) {
            cropType = "nether_wart";
            maxAge = 3;
            itemId = new Identifier("minecraft:nether_wart");
            int age = cropState.contains(Properties.AGE_3) ? cropState.get(Properties.AGE_3) : 0;
            return new CropInfo(cropType, age, maxAge, itemId);
        }
        
        // Generic crop detection for modded crops
        if (cropState.contains(Properties.AGE_7)) {
            int age = cropState.get(Properties.AGE_7);
            cropType = blockPath.replace("_crop", "").replace("_block", "");
            maxAge = 7;
            // Try to infer item ID from block ID
            itemId = new Identifier(blockId.getNamespace(), cropType);
            return new CropInfo(cropType, age, maxAge, itemId);
        } else if (cropState.contains(Properties.AGE_3)) {
            int age = cropState.get(Properties.AGE_3);
            cropType = blockPath.replace("_crop", "").replace("_block", "");
            maxAge = 3;
            itemId = new Identifier(blockId.getNamespace(), cropType);
            return new CropInfo(cropType, age, maxAge, itemId);
        }
        
        return null;
    }
    
    /**
     * Gets average drops per crop harvest.
     */
    private static double getAverageDropsPerCrop(String cropType) {
        // Most crops drop 1-3 items on average, with some variation
        // Wheat: 1-3 (avg 2), Carrots: 1-4 (avg 2.5), Potatoes: 1-4 (avg 2.5), Beetroot: 1-3 (avg 2)
        switch (cropType.toLowerCase()) {
            case "wheat":
                return 2.0;
            case "carrots":
            case "potatoes":
                return 2.5;
            case "beetroot":
                return 2.0;
            case "nether_wart":
                return 2.0; // Nether wart drops 2-4, average 3, but we'll use 2 for consistency
            default:
                return 2.0; // Default average
        }
    }
    
    /**
     * Rotates a position based on building rotation.
     */
    private static BlockPos rotatePosition(BlockPos relativePos, Vec3i size, int rotation) {
        int x = relativePos.getX();
        int y = relativePos.getY();
        int z = relativePos.getZ();
        
        // Rotation is in 90-degree increments
        switch (rotation) {
            case 90:
                // Rotate 90 degrees clockwise: (x, y, z) -> (z, y, size.x - x - 1)
                return new BlockPos(z, y, size.getX() - x - 1);
            case 180:
                // Rotate 180 degrees: (x, y, z) -> (size.x - x - 1, y, size.z - z - 1)
                return new BlockPos(size.getX() - x - 1, y, size.getZ() - z - 1);
            case 270:
                // Rotate 270 degrees clockwise: (x, y, z) -> (size.z - z - 1, y, x)
                return new BlockPos(size.getZ() - z - 1, y, x);
            case 0:
            default:
                return relativePos;
        }
    }
}

