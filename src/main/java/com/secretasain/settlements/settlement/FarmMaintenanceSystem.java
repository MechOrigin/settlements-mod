package com.secretasain.settlements.settlement;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.building.StructureData;
import com.secretasain.settlements.building.StructureLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.*;

/**
 * System for farm villagers to maintain farmland and plant seeds.
 * The first assigned villager (index 0) repairs broken farmland and plants seeds.
 */
public class FarmMaintenanceSystem {
    private static final int CHECK_INTERVAL_TICKS = 100; // Check every 5 seconds
    private static final int FIX_INTERVAL_TICKS = 200; // Fix one block every 10 seconds (to avoid lag)
    
    // Track maintenance state per building
    private static final Map<UUID, MaintenanceState> maintenanceStates = new HashMap<>();
    
    /**
     * Tracks maintenance state for a farm building.
     */
    private static class MaintenanceState {
        List<BlockPos> brokenFarmlandPositions; // Positions where farmland needs to be fixed
        String selectedCropType; // Crop type to plant (based on nearby crops)
        long lastFixTime; // Last time a block was fixed
        boolean isPlanting; // Whether villager is currently planting
        
        MaintenanceState() {
            this.brokenFarmlandPositions = new ArrayList<>();
            this.lastFixTime = 0;
            this.isPlanting = false;
        }
    }
    
    /**
     * Registers the farm maintenance system with Fabric's server tick events.
     */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_INTERVAL_TICKS == 0) {
                tick(world);
            }
        });
        
        SettlementsMod.LOGGER.info("FarmMaintenanceSystem registered - will check for farmland maintenance every {} ticks", CHECK_INTERVAL_TICKS);
    }
    
    /**
     * Performs a tick update for the given world.
     */
    private static void tick(ServerWorld world) {
        // Check if it's work hours (daytime: 1000-12000 ticks, roughly 6 AM to 6 PM)
        long timeOfDay = world.getTimeOfDay() % 24000;
        boolean isWorkHours = timeOfDay >= 1000 && timeOfDay < 12000;
        
        if (!isWorkHours) {
            return; // Villagers don't work at night
        }
        
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        for (Settlement settlement : allSettlements) {
            processSettlement(settlement, world);
        }
    }
    
    /**
     * Processes farm maintenance for all farm buildings in a settlement.
     */
    private static void processSettlement(Settlement settlement, ServerWorld world) {
        for (Building building : settlement.getBuildings()) {
            if (building.getStatus() != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
                continue;
            }
            
            // Check if this is a farm building
            if (!isFarmBuilding(building)) {
                continue;
            }
            
            // Get all villagers assigned to this farm
            List<VillagerData> assignedVillagers = WorkAssignmentManager.getVillagersAssignedToBuilding(
                settlement, building.getId()
            );
            
            // Only the first villager (index 0) should do maintenance
            if (assignedVillagers.isEmpty()) {
                SettlementsMod.LOGGER.info("Farm building {} has no assigned villagers - skipping maintenance", building.getId());
                continue;
            }
            
            VillagerData firstVillagerData = assignedVillagers.get(0);
            VillagerEntity villager = getVillagerEntity(world, firstVillagerData.getEntityId());
            if (villager == null || villager.isRemoved()) {
                continue;
            }
            
            // Check if villager is close enough to building to work
            BlockPos buildingPos = building.getPosition();
            double distanceSq = villager.getPos().squaredDistanceTo(
                buildingPos.getX() + 0.5,
                buildingPos.getY() + 0.5,
                buildingPos.getZ() + 0.5
            );
            
            double distance = Math.sqrt(distanceSq);
            if (distance > 16.0) { // Within 16 blocks
                SettlementsMod.LOGGER.info("Villager {} is too far from farm building {} (distance: {} blocks)", 
                    firstVillagerData.getEntityId(), building.getId(), String.format("%.2f", distance));
                continue; // Villager is too far from building
            }
            
            // SettlementsMod.LOGGER.info("Processing farm maintenance for building {} with villager {} (distance: {} blocks)", 
            //     building.getId(), firstVillagerData.getEntityId(), String.format("%.2f", distance));
            
            // Process maintenance for this farm
            processFarmMaintenance(building, villager, world);
        }
    }
    
    /**
     * Processes farmland repair and seed planting for a farm building.
     */
    private static void processFarmMaintenance(Building building, VillagerEntity villager, ServerWorld world) {
        UUID buildingId = building.getId();
        MaintenanceState state = maintenanceStates.computeIfAbsent(buildingId, k -> new MaintenanceState());
        
        long currentTime = world.getTime();
        
        // Step 1: Scan for broken farmland and fix it
        // Always scan if list is empty or enough time has passed since last fix
        if (state.brokenFarmlandPositions.isEmpty() || 
            (currentTime - state.lastFixTime) >= FIX_INTERVAL_TICKS) {
            // Scan for broken farmland
            scanForBrokenFarmland(building, world, state);
            
            // SettlementsMod.LOGGER.info("Farm maintenance scan for building {} found {} broken farmland blocks", 
            //     buildingId, state.brokenFarmlandPositions.size());
            
            // Fix one broken farmland block
            if (!state.brokenFarmlandPositions.isEmpty()) {
                fixBrokenFarmland(building, world, state, villager);
                state.lastFixTime = currentTime;
            }
        }
        
        // Step 2: After fixing farmland, plant seeds
        // Only plant if we're not currently fixing farmland and there's empty farmland
        if (state.brokenFarmlandPositions.isEmpty() && !state.isPlanting) {
            Settlement settlement = findSettlementByBuilding(building.getId(), world);
            if (settlement != null) {
                plantSeeds(building, world, state, villager, settlement);
            }
        }
    }
    
    /**
     * Scans the farm structure for broken farmland (dirt/grass where farmland should be).
     */
    private static void scanForBrokenFarmland(Building building, ServerWorld world, MaintenanceState state) {
        state.brokenFarmlandPositions.clear();
        
        try {
            // Load structure data to get the area
            StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), world.getServer());
            if (structureData == null) {
                SettlementsMod.LOGGER.warn("Could not load structure data for building {} (type: {})", 
                    building.getId(), building.getStructureType());
                return;
            }
            
            BlockPos buildingPos = building.getPosition();
            int rotation = building.getRotation();
            Vec3i size = structureData.getDimensions();
            
            int farmlandInStructure = 0;
            int brokenFarmlandFound = 0;
            int totalBlocksInStructure = 0;
            Map<String, Integer> blockTypeCounts = new HashMap<>();
            
            // Iterate through actual blocks in the structure (not all possible positions)
            // This is more efficient and ensures we only check positions that have blocks
            for (com.secretasain.settlements.building.StructureBlock structureBlock : structureData.getBlocks()) {
                totalBlocksInStructure++;
                BlockState structureBlockState = structureBlock.getBlockState();
                Block block = structureBlockState.getBlock();
                String blockName = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
                blockTypeCounts.put(blockName, blockTypeCounts.getOrDefault(blockName, 0) + 1);
                
                // Check if the structure has farmland at this position, OR dirt/grass that should be farmland
                // (Some structure files may have dirt/grass blocks that should be converted to farmland)
                boolean shouldBeFarmland = structureBlockState.getBlock() instanceof FarmlandBlock ||
                    structureBlockState.getBlock() == Blocks.DIRT ||
                    structureBlockState.getBlock() == Blocks.GRASS_BLOCK ||
                    structureBlockState.getBlock() == Blocks.COARSE_DIRT ||
                    structureBlockState.getBlock() == Blocks.PODZOL;
                
                if (shouldBeFarmland) {
                    if (structureBlockState.getBlock() instanceof FarmlandBlock) {
                        farmlandInStructure++;
                    }
                    
                    // Get relative position from structure block
                    BlockPos relativePos = structureBlock.getRelativePos();
                    
                    // Apply rotation to relative position
                    BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
                    
                    // Convert to world position
                    BlockPos worldPos = buildingPos.add(rotatedPos);
                    
                    // Check if chunk is loaded
                    if (!world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    // This position should be farmland - check if it's broken (dirt/grass instead of farmland)
                    BlockState currentState = world.getBlockState(worldPos);
                    Block currentBlock = currentState.getBlock();
                    
                    // Check if it's dirt or grass (broken farmland) - but NOT if it's already farmland
                    if (!(currentBlock instanceof FarmlandBlock) &&
                        (currentBlock == Blocks.DIRT || 
                         currentBlock == Blocks.GRASS_BLOCK || 
                         currentBlock == Blocks.COARSE_DIRT ||
                         currentBlock == Blocks.PODZOL)) {
                        state.brokenFarmlandPositions.add(worldPos);
                        brokenFarmlandFound++;
                        
                        SettlementsMod.LOGGER.info("Found broken farmland at {} (should be farmland, is {})", 
                            worldPos, currentBlock);
                    }
                }
            }
            
            // SettlementsMod.LOGGER.info("Farm scan complete: {} farmland blocks in structure, {} broken blocks found (total blocks in structure: {})", 
            //     farmlandInStructure, brokenFarmlandFound, totalBlocksInStructure);
            
            // Log top block types for debugging
            if (farmlandInStructure == 0 && totalBlocksInStructure > 0) {
                SettlementsMod.LOGGER.warn("No farmland blocks found in structure! Top block types:");
                blockTypeCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> SettlementsMod.LOGGER.warn("  - {}: {}", entry.getKey(), entry.getValue()));
            }
                
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error scanning for broken farmland: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Fixes one broken farmland block by replacing it with farmland.
     */
    private static void fixBrokenFarmland(Building building, ServerWorld world, MaintenanceState state, VillagerEntity villager) {
        if (state.brokenFarmlandPositions.isEmpty()) {
            return;
        }
        
        // Get the first broken farmland position
        BlockPos brokenPos = state.brokenFarmlandPositions.get(0);
        
        // Check if chunk is loaded
        if (!world.getChunkManager().isChunkLoaded(brokenPos.getX() >> 4, brokenPos.getZ() >> 4)) {
            return;
        }
        
        // Check if it's still broken
        BlockState currentState = world.getBlockState(brokenPos);
        Block currentBlock = currentState.getBlock();
        
        if (currentBlock == Blocks.DIRT || 
            currentBlock == Blocks.GRASS_BLOCK || 
            currentBlock == Blocks.COARSE_DIRT ||
            currentBlock == Blocks.PODZOL) {
            
            // Replace with farmland
            BlockState farmlandState = Blocks.FARMLAND.getDefaultState();
            world.setBlockState(brokenPos, farmlandState, Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
            
            // Remove from list
            state.brokenFarmlandPositions.remove(0);
            
            SettlementsMod.LOGGER.info("Villager {} fixed broken farmland at {} for building {}", 
                villager.getUuid(), brokenPos, building.getId());
        } else {
            // Already fixed or changed - remove from list
            state.brokenFarmlandPositions.remove(0);
        }
    }
    
    /**
     * Plants seeds in empty farmland after checking nearby crops.
     */
    private static void plantSeeds(Building building, ServerWorld world, MaintenanceState state, 
                                  VillagerEntity villager, Settlement settlement) {
        try {
            // Load structure data to get the area
            StructureData structureData = StructureLoader.loadStructure(building.getStructureType(), world.getServer());
            if (structureData == null) {
                return;
            }
            
            BlockPos buildingPos = building.getPosition();
            int rotation = building.getRotation();
            Vec3i size = structureData.getDimensions();
            
            // First, scan nearby crops to determine what to plant
            if (state.selectedCropType == null) {
                state.selectedCropType = determineCropTypeToPlant(building, world, structureData, buildingPos, rotation, size);
            }
            
            if (state.selectedCropType == null) {
                return; // No crop type determined yet
            }
            
            // Find empty farmland that needs seeds
            BlockPos emptyFarmlandPos = findEmptyFarmland(building, world, structureData, buildingPos, rotation, size);
            if (emptyFarmlandPos == null) {
                return; // No empty farmland found
            }
            
            // Check if we have seeds in settlement storage or villager inventory
            Item seedItem = getSeedItemForCropType(state.selectedCropType);
            if (seedItem == null) {
                return; // Unknown crop type
            }
            
            // Check if we have seeds available
            if (!hasSeedsAvailable(settlement, seedItem, world)) {
                return; // No seeds available
            }
            
            // Plant the seed
            BlockPos cropPos = emptyFarmlandPos.up();
            Block cropBlock = getCropBlockForType(state.selectedCropType);
            if (cropBlock == null) {
                return;
            }
            
            // Check if position is air (can plant)
            if (!world.getBlockState(cropPos).isAir()) {
                return; // Position not empty
            }
            
            // Place the crop block with age 0
            BlockState cropState = cropBlock.getDefaultState();
            if (cropState.contains(Properties.AGE_7)) {
                cropState = cropState.with(Properties.AGE_7, 0);
            } else if (cropState.contains(Properties.AGE_3)) {
                cropState = cropState.with(Properties.AGE_3, 0);
            }
            
            world.setBlockState(cropPos, cropState, Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
            
            // Consume one seed from settlement storage
            consumeSeed(settlement, seedItem, world);
            
            SettlementsMod.LOGGER.info("Villager {} planted {} at {} for building {}", 
                villager.getUuid(), state.selectedCropType, cropPos, building.getId());
            
            // Reset crop type after planting (will re-scan next time)
            state.selectedCropType = null;
            
        } catch (Exception e) {
            SettlementsMod.LOGGER.warn("Error planting seeds: {}", e.getMessage());
        }
    }
    
    /**
     * Determines what crop type to plant based on nearby crops.
     */
    private static String determineCropTypeToPlant(Building building, ServerWorld world, 
                                                   StructureData structureData, BlockPos buildingPos, 
                                                   int rotation, Vec3i size) {
        Map<String, Integer> cropCounts = new HashMap<>();
        
        // Scan all farmland positions in the structure
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos relativePos = new BlockPos(x, y, z);
                    
                    // Apply rotation
                    BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
                    BlockPos worldPos = buildingPos.add(rotatedPos);
                    
                    if (!world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    // Check if this is farmland
                    BlockState blockState = world.getBlockState(worldPos);
                    if (blockState.getBlock() instanceof FarmlandBlock) {
                        // Check the block above for crops
                        BlockPos cropPos = worldPos.up();
                        if (!world.getChunkManager().isChunkLoaded(cropPos.getX() >> 4, cropPos.getZ() >> 4)) {
                            continue;
                        }
                        
                        BlockState cropState = world.getBlockState(cropPos);
                        Block cropBlock = cropState.getBlock();
                        
                        // Identify crop type
                        String cropType = identifyCropType(cropBlock);
                        if (cropType != null) {
                            cropCounts.put(cropType, cropCounts.getOrDefault(cropType, 0) + 1);
                        }
                    }
                }
            }
        }
        
        // Pick the most common crop type
        if (cropCounts.isEmpty()) {
            return null; // No crops found
        }
        
        String mostCommonCrop = cropCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        SettlementsMod.LOGGER.debug("Determined crop type to plant: {} (count: {})", 
            mostCommonCrop, cropCounts.get(mostCommonCrop));
        
        return mostCommonCrop;
    }
    
    /**
     * Identifies the crop type from a block.
     */
    private static String identifyCropType(Block cropBlock) {
        if (cropBlock == Blocks.WHEAT) {
            return "wheat";
        } else if (cropBlock == Blocks.CARROTS) {
            return "carrots";
        } else if (cropBlock == Blocks.POTATOES) {
            return "potatoes";
        } else if (cropBlock == Blocks.BEETROOTS) {
            return "beetroot";
        } else if (cropBlock == Blocks.NETHER_WART) {
            return "nether_wart";
        }
        return null;
    }
    
    /**
     * Finds an empty farmland position that needs seeds.
     */
    private static BlockPos findEmptyFarmland(Building building, ServerWorld world, 
                                             StructureData structureData, BlockPos buildingPos, 
                                             int rotation, Vec3i size) {
        // Scan for empty farmland (farmland with air above)
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos relativePos = new BlockPos(x, y, z);
                    
                    // Apply rotation
                    BlockPos rotatedPos = rotatePosition(relativePos, size, rotation);
                    BlockPos worldPos = buildingPos.add(rotatedPos);
                    
                    if (!world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                        continue;
                    }
                    
                    // Check if this should be farmland (from structure)
                    com.secretasain.settlements.building.StructureBlock structureBlock = structureData.getBlockAt(relativePos);
                    if (structureBlock == null) {
                        continue;
                    }
                    
                    if (structureBlock.getBlockState().getBlock() instanceof FarmlandBlock) {
                        // Check if it's actually farmland
                        BlockState currentState = world.getBlockState(worldPos);
                        if (currentState.getBlock() instanceof FarmlandBlock) {
                            // Check if the block above is air (needs planting)
                            BlockPos cropPos = worldPos.up();
                            if (!world.getChunkManager().isChunkLoaded(cropPos.getX() >> 4, cropPos.getZ() >> 4)) {
                                continue;
                            }
                            
                            BlockState cropState = world.getBlockState(cropPos);
                            if (cropState.isAir()) {
                                return worldPos; // Found empty farmland
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Gets the seed item for a crop type.
     */
    private static Item getSeedItemForCropType(String cropType) {
        switch (cropType.toLowerCase()) {
            case "wheat":
                return Items.WHEAT_SEEDS;
            case "carrots":
                return Items.CARROT;
            case "potatoes":
                return Items.POTATO;
            case "beetroot":
                return Items.BEETROOT_SEEDS;
            case "nether_wart":
                return Items.NETHER_WART;
            default:
                return null;
        }
    }
    
    /**
     * Gets the crop block for a crop type.
     */
    private static Block getCropBlockForType(String cropType) {
        switch (cropType.toLowerCase()) {
            case "wheat":
                return Blocks.WHEAT;
            case "carrots":
                return Blocks.CARROTS;
            case "potatoes":
                return Blocks.POTATOES;
            case "beetroot":
                return Blocks.BEETROOTS;
            case "nether_wart":
                return Blocks.NETHER_WART;
            default:
                return null;
        }
    }
    
    /**
     * Checks if seeds are available in settlement storage or chests.
     */
    private static boolean hasSeedsAvailable(Settlement settlement, Item seedItem, ServerWorld world) {
        // Check settlement storage
        Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(seedItem);
        if (itemId != null) {
            String itemKey = itemId.toString();
            int count = settlement.getMaterials().getOrDefault(itemKey, 0);
            if (count > 0) {
                return true;
            }
        }
        
        // Check chests near lectern
        BlockPos lecternPos = settlement.getLecternPos();
        if (lecternPos == null) {
            return false;
        }
        
        // Search for chests adjacent to lectern
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
            BlockPos chestPos = lecternPos.offset(direction);
            if (!world.getChunkManager().isChunkLoaded(chestPos.getX() >> 4, chestPos.getZ() >> 4)) {
                continue;
            }
            
            BlockState blockState = world.getBlockState(chestPos);
            if (blockState.getBlock() instanceof net.minecraft.block.ChestBlock) {
                net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(chestPos);
                if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity) {
                    net.minecraft.block.entity.ChestBlockEntity chest = (net.minecraft.block.entity.ChestBlockEntity) blockEntity;
                    
                    // Check if chest has the seed item
                    for (int i = 0; i < chest.size(); i++) {
                        ItemStack stack = chest.getStack(i);
                        if (!stack.isEmpty() && stack.getItem() == seedItem) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Consumes one seed from settlement storage or chests.
     */
    private static void consumeSeed(Settlement settlement, Item seedItem, ServerWorld world) {
        Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(seedItem);
        if (itemId == null) {
            return;
        }
        
        // Try settlement storage first
        String itemKey = itemId.toString();
        Map<String, Integer> materials = settlement.getMaterials();
        int count = materials.getOrDefault(itemKey, 0);
        if (count > 0) {
            materials.put(itemKey, count - 1);
            if (materials.get(itemKey) <= 0) {
                materials.remove(itemKey);
            }
            SettlementManager.getInstance(world).markDirty();
            return;
        }
        
        // Try chests near lectern
        BlockPos lecternPos = settlement.getLecternPos();
        if (lecternPos == null) {
            return;
        }
        
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
            BlockPos chestPos = lecternPos.offset(direction);
            if (!world.getChunkManager().isChunkLoaded(chestPos.getX() >> 4, chestPos.getZ() >> 4)) {
                continue;
            }
            
            BlockState blockState = world.getBlockState(chestPos);
            if (blockState.getBlock() instanceof net.minecraft.block.ChestBlock) {
                net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(chestPos);
                if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity) {
                    net.minecraft.block.entity.ChestBlockEntity chest = (net.minecraft.block.entity.ChestBlockEntity) blockEntity;
                    
                    // Find and remove one seed
                    for (int i = 0; i < chest.size(); i++) {
                        ItemStack stack = chest.getStack(i);
                        if (!stack.isEmpty() && stack.getItem() == seedItem) {
                            stack.decrement(1);
                            chest.markDirty();
                            return;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Rotates a position based on building rotation.
     */
    /**
     * Applies rotation to a relative position.
     * Uses the same formula as BlockPlacementScheduler.applyRotation to ensure consistency.
     * Rotates around the origin (0, 0, 0) - same formula used for placing blocks.
     * @param relativePos The relative position
     * @param size Structure dimensions (unused, kept for compatibility)
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @return Rotated position
     */
    private static BlockPos rotatePosition(BlockPos relativePos, Vec3i size, int rotation) {
        int x = relativePos.getX();
        int z = relativePos.getZ();
        
        switch (rotation) {
            case 90:
                // Rotate 90 degrees clockwise around origin: (x, y, z) -> (-z, y, x)
                return new BlockPos(-z, relativePos.getY(), x);
            case 180:
                // Rotate 180 degrees around origin: (x, y, z) -> (-x, y, -z)
                return new BlockPos(-x, relativePos.getY(), -z);
            case 270:
                // Rotate 270 degrees clockwise around origin: (x, y, z) -> (z, y, -x)
                return new BlockPos(z, relativePos.getY(), -x);
            case 0:
            default:
                return relativePos;
        }
    }
    
    /**
     * Checks if a building is a farm building.
     */
    private static boolean isFarmBuilding(Building building) {
        String structureName = building.getStructureType().getPath().toLowerCase();
        return structureName.contains("farm");
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
    
    /**
     * Finds the settlement that contains the given building.
     */
    private static Settlement findSettlementByBuilding(UUID buildingId, ServerWorld world) {
        SettlementManager manager = SettlementManager.getInstance(world);
        Collection<Settlement> allSettlements = manager.getAllSettlements();
        
        for (Settlement settlement : allSettlements) {
            for (Building building : settlement.getBuildings()) {
                if (building.getId().equals(buildingId)) {
                    return settlement;
                }
            }
        }
        
        return null;
    }
}

