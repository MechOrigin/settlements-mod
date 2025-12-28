package com.secretasain.settlements.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.*;

/**
 * Represents a loaded structure with all its blocks and metadata.
 */
public class StructureData {
    private final List<StructureBlock> blocks;
    private final Vec3i size;
    private final Map<BlockState, Integer> materialCount;
    private final List<BlockPos> buildOrder;
    private final String name;
    
    /**
     * Creates a StructureData from an NBT compound (Minecraft structure format).
     * @param nbt NBT compound containing structure data
     * @param name Name/identifier of the structure
     */
    public StructureData(NbtCompound nbt, String name) {
        this.name = name;
        this.blocks = new ArrayList<>();
        this.materialCount = new HashMap<>();
        this.buildOrder = new ArrayList<>();
        
        // Read size
        if (nbt.contains("size", 9)) { // 9 = NbtList
            NbtList sizeList = nbt.getList("size", 3); // 3 = Int
            int sizeX = sizeList.getInt(0);
            int sizeY = sizeList.getInt(1);
            int sizeZ = sizeList.getInt(2);
            this.size = new Vec3i(sizeX, sizeY, sizeZ);
        } else {
            throw new IllegalArgumentException("Structure NBT missing 'size' tag");
        }
        
        // Read palette
        Map<Integer, BlockState> palette = new HashMap<>();
        if (nbt.contains("palette", 9)) {
            NbtList paletteList = nbt.getList("palette", 10); // 10 = NbtCompound
            for (int i = 0; i < paletteList.size(); i++) {
                NbtCompound paletteEntry = paletteList.getCompound(i);
                if (paletteEntry.contains("Name", 8)) { // 8 = String
                    String blockName = paletteEntry.getString("Name");
                    Identifier blockId = Identifier.tryParse(blockName);
                    if (blockId != null) {
                        net.minecraft.block.Block block = Registries.BLOCK.get(blockId);
                        BlockState state = block.getDefaultState();
                        
                        // Parse properties from NBT
                        if (paletteEntry.contains("Properties", 10)) {
                            NbtCompound properties = paletteEntry.getCompound("Properties");
                            state = parseBlockStateWithProperties(block, state, properties);
                        }
                        
                        palette.put(i, state);
                    }
                }
            }
        }
        
        // Read blocks
        if (nbt.contains("blocks", 9)) {
            NbtList blocksList = nbt.getList("blocks", 10);
            for (int i = 0; i < blocksList.size(); i++) {
                NbtCompound blockNbt = blocksList.getCompound(i);
                
                // Get position
                if (blockNbt.contains("pos", 9)) {
                    NbtList posList = blockNbt.getList("pos", 3);
                    int x = posList.getInt(0);
                    int y = posList.getInt(1);
                    int z = posList.getInt(2);
                    BlockPos relativePos = new BlockPos(x, y, z);
                    
                    // Get palette index
                    int paletteIndex = blockNbt.contains("state", 3) ? blockNbt.getInt("state") : 0;
                    BlockState state = palette.getOrDefault(paletteIndex, net.minecraft.block.Blocks.AIR.getDefaultState());
                    
                    // Get block entity data (if present)
                    NbtCompound blockEntityData = blockNbt.contains("nbt", 10) ? blockNbt.getCompound("nbt") : null;
                    
                    StructureBlock structureBlock = new StructureBlock(relativePos, state, blockEntityData);
                    blocks.add(structureBlock);
                    buildOrder.add(relativePos);
                    
                    // Count materials (skip air blocks - they're not required materials)
                    if (!state.isAir()) {
                        materialCount.put(state, materialCount.getOrDefault(state, 0) + 1);
                    }
                }
            }
        }
        
        // Sort build order by Y coordinate (lowest first), then X, then Z
        buildOrder.sort((a, b) -> {
            if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
            if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
            return Integer.compare(a.getZ(), b.getZ());
        });
    }
    
    /**
     * Parses block state properties from NBT and applies them to the block state.
     * @param block The block
     * @param defaultState The default state
     * @param propertiesNbt NBT compound containing property key-value pairs
     * @return BlockState with properties applied
     */
    private static BlockState parseBlockStateWithProperties(Block block, BlockState defaultState, NbtCompound propertiesNbt) {
        BlockState state = defaultState;
        
        // Iterate through all properties in the NBT
        for (String key : propertiesNbt.getKeys()) {
            String value = propertiesNbt.getString(key);
            
            // Find the property by name
            Property<?> property = null;
            for (Property<?> prop : state.getProperties()) {
                if (prop.getName().equals(key)) {
                    property = prop;
                    break;
                }
            }
            
            if (property != null) {
                // Parse the property value
                state = parsePropertyValue(state, property, value);
            }
        }
        
        return state;
    }
    
    /**
     * Parses a property value and applies it to the block state.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState parsePropertyValue(BlockState state, Property<T> property, String value) {
        Optional<T> parsedValue = property.parse(value);
        if (parsedValue.isPresent()) {
            return state.with(property, parsedValue.get());
        }
        return state;
    }
    
    /**
     * Gets all blocks in this structure.
     * @return List of StructureBlocks
     */
    public List<StructureBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }
    
    /**
     * Gets the dimensions of this structure.
     * @return Vec3i representing width, height, depth
     */
    public Vec3i getDimensions() {
        return size;
    }
    
    /**
     * Gets the size in blocks (width * height * depth).
     * @return Total block count
     */
    public int getBlockCount() {
        return blocks.size();
    }
    
    /**
     * Gets the material requirements for this structure.
     * @return Map of BlockState to count
     */
    public Map<BlockState, Integer> getMaterialRequirements() {
        return Collections.unmodifiableMap(materialCount);
    }
    
    /**
     * Gets the build order (sorted by Y coordinate).
     * @return List of relative BlockPos in build order
     */
    public List<BlockPos> getBuildOrder() {
        return Collections.unmodifiableList(buildOrder);
    }
    
    /**
     * Gets a block at a specific relative position.
     * @param relativePos Relative position within structure
     * @return StructureBlock at that position, or null if not found
     */
    public StructureBlock getBlockAt(BlockPos relativePos) {
        for (StructureBlock block : blocks) {
            if (block.getRelativePos().equals(relativePos)) {
                return block;
            }
        }
        return null;
    }
    
    /**
     * Gets the name/identifier of this structure.
     * @return Structure name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Validates that this structure is valid.
     * @return Validation result with error messages
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Check dimensions are reasonable
        if (size.getX() > 64 || size.getY() > 64 || size.getZ() > 64) {
            warnings.add("Structure dimensions exceed 64 blocks (max recommended)");
        }
        
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            errors.add("Structure has invalid dimensions");
        }
        
        // Check all block positions are within bounds
        for (StructureBlock block : blocks) {
            BlockPos pos = block.getRelativePos();
            if (pos.getX() < 0 || pos.getX() >= size.getX() ||
                pos.getY() < 0 || pos.getY() >= size.getY() ||
                pos.getZ() < 0 || pos.getZ() >= size.getZ()) {
                errors.add("Block at " + pos + " is outside structure bounds");
            }
        }
        
        // Check block count matches expected
        int expectedBlocks = size.getX() * size.getY() * size.getZ();
        if (blocks.size() > expectedBlocks) {
            warnings.add("Structure has more blocks than expected for its size");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Result of structure validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
    }
}

