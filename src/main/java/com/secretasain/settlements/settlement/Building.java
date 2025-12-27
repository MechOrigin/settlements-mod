package com.secretasain.settlements.settlement;

import com.secretasain.settlements.building.BuildingStatus;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Represents a building in a settlement.
 * Tracks construction progress, materials, and status.
 */
public class Building {
    private UUID id;
    private BlockPos position;
    private Identifier structureType; // ResourceLocation for structure type
    private BuildingStatus status;
    private Map<Identifier, Integer> requiredMaterials; // Map of item/material ID to count
    private Map<Identifier, Integer> providedMaterials; // Map of item/material ID to count provided
    private float progress; // 0.0 to 1.0
    private List<BlockPos> barrierPositions; // Positions where barrier blocks are placed
    private List<BlockPos> ghostBlockPositions; // Positions where ghost blocks are placed
    private int rotation; // Rotation in degrees (0, 90, 180, 270)
    
    /**
     * Creates a new building with the given parameters.
     * @param id Unique identifier for this building
     * @param position Position where the building is placed (corner/center)
     * @param structureType Identifier of the structure type
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     */
    public Building(UUID id, BlockPos position, Identifier structureType, int rotation) {
        this.id = id;
        this.position = position;
        this.structureType = structureType;
        this.rotation = ((rotation % 360) + 360) % 360; // Normalize
        this.rotation = (this.rotation / 90) * 90; // Snap to 90-degree increments
        this.status = BuildingStatus.RESERVED;
        this.requiredMaterials = new HashMap<>();
        this.providedMaterials = new HashMap<>();
        this.progress = 0.0f;
        this.barrierPositions = new ArrayList<>();
        this.ghostBlockPositions = new ArrayList<>();
    }
    
    /**
     * Creates a building from NBT data.
     * @param nbt NBT compound containing building data
     * @return New Building instance
     */
    public static Building fromNbt(NbtCompound nbt) {
        UUID id = nbt.getUuid("id");
        BlockPos pos = BlockPos.fromLong(nbt.getLong("position"));
        Identifier structureType = new Identifier(nbt.getString("structureType"));
        int rotation = nbt.contains("rotation") ? nbt.getInt("rotation") : 0;
        
        Building building = new Building(id, pos, structureType, rotation);
        
        // Load status
        if (nbt.contains("status", 8)) { // 8 = String
            try {
                building.status = BuildingStatus.valueOf(nbt.getString("status"));
            } catch (IllegalArgumentException e) {
                building.status = BuildingStatus.RESERVED; // Default to RESERVED if invalid
            }
        }
        
        // Load required materials
        if (nbt.contains("requiredMaterials", 10)) { // 10 = NbtCompound
            NbtCompound requiredNbt = nbt.getCompound("requiredMaterials");
            for (String key : requiredNbt.getKeys()) {
                Identifier materialId = Identifier.tryParse(key);
                if (materialId != null) {
                    // Skip air blocks - they're not required materials
                    if (!"minecraft:air".equals(materialId.toString())) {
                        building.requiredMaterials.put(materialId, requiredNbt.getInt(key));
                    }
                }
            }
        }
        
        // Load provided materials
        if (nbt.contains("providedMaterials", 10)) {
            NbtCompound providedNbt = nbt.getCompound("providedMaterials");
            for (String key : providedNbt.getKeys()) {
                Identifier materialId = Identifier.tryParse(key);
                if (materialId != null) {
                    building.providedMaterials.put(materialId, providedNbt.getInt(key));
                }
            }
        }
        
        // Load progress
        building.progress = nbt.contains("progress", 5) ? nbt.getFloat("progress") : 0.0f; // 5 = Float
        
        // Load barrier positions
        if (nbt.contains("barrierPositions", 9)) { // 9 = NbtList
            NbtList barrierList = nbt.getList("barrierPositions", 10); // 10 = NbtCompound
            for (int i = 0; i < barrierList.size(); i++) {
                NbtCompound posNbt = barrierList.getCompound(i);
                if (posNbt.contains("x", 3) && posNbt.contains("y", 3) && posNbt.contains("z", 3)) {
                    int x = posNbt.getInt("x");
                    int y = posNbt.getInt("y");
                    int z = posNbt.getInt("z");
                    building.barrierPositions.add(new BlockPos(x, y, z));
                }
            }
        }
        
        // Load ghost block positions
        building.ghostBlockPositions = new ArrayList<>();
        if (nbt.contains("ghostBlockPositions", 9)) { // 9 = NbtList
            NbtList ghostList = nbt.getList("ghostBlockPositions", 10); // 10 = NbtCompound
            for (int i = 0; i < ghostList.size(); i++) {
                NbtCompound posNbt = ghostList.getCompound(i);
                if (posNbt.contains("x", 3) && posNbt.contains("y", 3) && posNbt.contains("z", 3)) {
                    int x = posNbt.getInt("x");
                    int y = posNbt.getInt("y");
                    int z = posNbt.getInt("z");
                    building.ghostBlockPositions.add(new BlockPos(x, y, z));
                }
            }
        }
        
        return building;
    }
    
    /**
     * Serializes this building to NBT.
     * @return NBT compound containing building data
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("id", id);
        nbt.putLong("position", position.asLong());
        nbt.putString("structureType", structureType.toString());
        nbt.putInt("rotation", rotation);
        nbt.putString("status", status.name());
        nbt.putFloat("progress", progress);
        
        // Save required materials
        NbtCompound requiredNbt = new NbtCompound();
        for (Map.Entry<Identifier, Integer> entry : requiredMaterials.entrySet()) {
            requiredNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("requiredMaterials", requiredNbt);
        
        // Save provided materials
        NbtCompound providedNbt = new NbtCompound();
        for (Map.Entry<Identifier, Integer> entry : providedMaterials.entrySet()) {
            providedNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("providedMaterials", providedNbt);
        
        // Save barrier positions as NbtCompounds
        NbtList barrierList = new NbtList();
        for (BlockPos barrierPos : barrierPositions) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", barrierPos.getX());
            posNbt.putInt("y", barrierPos.getY());
            posNbt.putInt("z", barrierPos.getZ());
            barrierList.add(posNbt);
        }
        nbt.put("barrierPositions", barrierList);
        
        // Save ghost block positions as NbtCompounds
        NbtList ghostList = new NbtList();
        for (BlockPos ghostPos : ghostBlockPositions) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", ghostPos.getX());
            posNbt.putInt("y", ghostPos.getY());
            posNbt.putInt("z", ghostPos.getZ());
            ghostList.add(posNbt);
        }
        nbt.put("ghostBlockPositions", ghostList);
        
        return nbt;
    }
    
    /**
     * Gets the progress as a percentage (0-100).
     * @return Progress percentage
     */
    public int getProgressPercentage() {
        return Math.round(progress * 100.0f);
    }
    
    /**
     * Checks if all required materials have been provided.
     * @return true if all materials are available
     */
    public boolean hasAllMaterials() {
        for (Map.Entry<Identifier, Integer> entry : requiredMaterials.entrySet()) {
            int required = entry.getValue();
            int provided = providedMaterials.getOrDefault(entry.getKey(), 0);
            if (provided < required) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Updates the building status if the transition is valid.
     * @param newStatus New status
     * @return true if status was updated, false if transition is invalid
     */
    public boolean updateStatus(BuildingStatus newStatus) {
        if (BuildingStatus.isValidTransition(this.status, newStatus)) {
            this.status = newStatus;
            return true;
        }
        return false;
    }
    
    /**
     * Adds provided materials.
     * WARNING: This method ADDS to existing materials. Use with caution.
     * For setting materials (not adding), clear providedMaterials first.
     * @param materialId Material identifier
     * @param amount Amount to add
     */
    public void addProvidedMaterial(Identifier materialId, int amount) {
        providedMaterials.put(materialId, providedMaterials.getOrDefault(materialId, 0) + amount);
    }
    
    /**
     * Sets provided materials (replaces existing).
     * @param materialId Material identifier
     * @param amount Amount to set
     */
    public void setProvidedMaterial(Identifier materialId, int amount) {
        providedMaterials.put(materialId, amount);
    }
    
    /**
     * Clears all provided materials.
     * Used when materials are returned to settlement storage.
     */
    public void clearProvidedMaterials() {
        providedMaterials.clear();
    }
    
    /**
     * Sets the required materials for this building.
     * @param materials Map of material identifiers to required counts
     */
    public void setRequiredMaterials(Map<Identifier, Integer> materials) {
        this.requiredMaterials.clear();
        this.requiredMaterials.putAll(materials);
    }
    
    /**
     * Updates the construction progress.
     * @param progress Progress value (0.0 to 1.0)
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress)); // Clamp to 0.0-1.0
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public BlockPos getPosition() {
        return position;
    }
    
    public void setPosition(BlockPos position) {
        this.position = position;
    }
    
    public Identifier getStructureType() {
        return structureType;
    }
    
    public BuildingStatus getStatus() {
        return status;
    }
    
    public Map<Identifier, Integer> getRequiredMaterials() {
        // Filter out air blocks - they're not required materials
        Map<Identifier, Integer> filtered = new HashMap<>();
        for (Map.Entry<Identifier, Integer> entry : requiredMaterials.entrySet()) {
            Identifier materialId = entry.getKey();
            // Skip air blocks
            if (!"minecraft:air".equals(materialId.toString())) {
                filtered.put(materialId, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(filtered);
    }
    
    public Map<Identifier, Integer> getProvidedMaterials() {
        return Collections.unmodifiableMap(providedMaterials);
    }
    
    public float getProgress() {
        return progress;
    }
    
    public List<BlockPos> getBarrierPositions() {
        return Collections.unmodifiableList(barrierPositions);
    }
    
    public void setBarrierPositions(List<BlockPos> positions) {
        this.barrierPositions.clear();
        this.barrierPositions.addAll(positions);
    }
    
    public List<BlockPos> getGhostBlockPositions() {
        return Collections.unmodifiableList(ghostBlockPositions);
    }
    
    public void setGhostBlockPositions(List<BlockPos> positions) {
        this.ghostBlockPositions.clear();
        this.ghostBlockPositions.addAll(positions);
    }
    
    public void addGhostBlockPosition(BlockPos pos) {
        if (!ghostBlockPositions.contains(pos)) {
            ghostBlockPositions.add(pos);
        }
    }
    
    public void clearGhostBlockPositions() {
        this.ghostBlockPositions.clear();
    }
    
    public void addBarrierPosition(BlockPos pos) {
        if (!barrierPositions.contains(pos)) {
            barrierPositions.add(pos);
        }
    }
    
    public void removeBarrierPosition(BlockPos pos) {
        barrierPositions.remove(pos);
    }
    
    public int getRotation() {
        return rotation;
    }
    
    public void setRotation(int rotation) {
        this.rotation = ((rotation % 360) + 360) % 360; // Normalize
        this.rotation = (this.rotation / 90) * 90; // Snap to 90-degree increments
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Building building = (Building) o;
        return Objects.equals(id, building.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
