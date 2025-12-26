package com.secretasain.settlements.settlement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Represents a settlement managed through a lectern block.
 * Stores settlement data including position, radius, name, and associated entities.
 */
public class Settlement {
    private UUID id;
    private BlockPos lecternPos;
    private int radius;
    private String name;
    private List<VillagerData> villagers;
    private List<Building> buildings;
    private Map<String, Integer> materials; // Using String for ResourceLocation key for now
    private int level; // Settlement level (1-5)

    /**
     * Creates a new settlement with the given parameters.
     * @param id Unique identifier for this settlement
     * @param lecternPos Position of the lectern block managing this settlement
     * @param radius Radius of the settlement in blocks
     * @param name Display name of the settlement
     */
    public Settlement(UUID id, BlockPos lecternPos, int radius, String name) {
        this.id = id;
        this.lecternPos = lecternPos;
        this.radius = radius;
        this.name = name;
        this.villagers = new ArrayList<>();
        this.buildings = new ArrayList<>();
        this.materials = new HashMap<>();
        this.level = 1; // Start at level 1
    }

    /**
     * Creates a settlement from NBT data.
     * @param nbt NBT compound containing settlement data
     * @return New Settlement instance
     */
    public static Settlement fromNbt(NbtCompound nbt) {
        UUID id = nbt.getUuid("id");
        BlockPos pos = BlockPos.fromLong(nbt.getLong("lecternPos"));
        int radius = nbt.getInt("radius");
        String name = nbt.getString("name");
        
        Settlement settlement = new Settlement(id, pos, radius, name);
        
        // Load villagers
        if (nbt.contains("villagers", 9)) { // 9 = NbtList type
            NbtList villagerList = nbt.getList("villagers", 10); // 10 = NbtCompound type
            for (int i = 0; i < villagerList.size(); i++) {
                NbtCompound villagerNbt = villagerList.getCompound(i);
                VillagerData villager = VillagerData.fromNbt(villagerNbt);
                settlement.villagers.add(villager);
            }
        }
        
        // Load buildings
        if (nbt.contains("buildings", 9)) {
            NbtList buildingList = nbt.getList("buildings", 10);
            for (int i = 0; i < buildingList.size(); i++) {
                NbtCompound buildingNbt = buildingList.getCompound(i);
                Building building = Building.fromNbt(buildingNbt);
                settlement.buildings.add(building);
            }
        }
        
        // Load materials
        if (nbt.contains("materials", 10)) {
            NbtCompound materialsNbt = nbt.getCompound("materials");
            for (String key : materialsNbt.getKeys()) {
                settlement.materials.put(key, materialsNbt.getInt(key));
            }
        }
        
        // Load level (default to 1 if not present for backwards compatibility)
        settlement.level = nbt.contains("level", 3) ? nbt.getInt("level") : 1;
        
        return settlement;
    }

    /**
     * Serializes this settlement to NBT.
     * @return NBT compound containing settlement data
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("id", id);
        nbt.putLong("lecternPos", lecternPos.asLong());
        nbt.putInt("radius", radius);
        nbt.putString("name", name);
        
        // Save villagers
        NbtList villagerList = new NbtList();
        for (VillagerData villager : villagers) {
            villagerList.add(villager.toNbt());
        }
        nbt.put("villagers", villagerList);
        
        // Save buildings
        NbtList buildingList = new NbtList();
        for (Building building : buildings) {
            buildingList.add(building.toNbt());
        }
        nbt.put("buildings", buildingList);
        
        // Save materials
        NbtCompound materialsNbt = new NbtCompound();
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            materialsNbt.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("materials", materialsNbt);
        
        // Save level
        nbt.putInt("level", level);
        
        return nbt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BlockPos getLecternPos() {
        return lecternPos;
    }

    public void setLecternPos(BlockPos lecternPos) {
        this.lecternPos = lecternPos;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(1, radius); // Ensure radius is at least 1
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "Unnamed Settlement";
    }

    public List<VillagerData> getVillagers() {
        return villagers;
    }

    public List<Building> getBuildings() {
        return buildings;
    }

    public Map<String, Integer> getMaterials() {
        return materials;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(5, level)); // Clamp to 1-5
    }
    
    /**
     * Updates the settlement level based on current stats.
     * Returns true if the level changed.
     */
    public boolean updateLevel() {
        SettlementLevel calculatedLevel = SettlementLevel.calculateLevel(this);
        int oldLevel = this.level;
        this.level = calculatedLevel.getLevel();
        return oldLevel != this.level;
    }
    
    /**
     * Gets the SettlementLevel enum for this settlement's level.
     */
    public SettlementLevel getSettlementLevel() {
        return SettlementLevel.fromLevel(this.level);
    }
    
    /**
     * Gets the next level this settlement can reach.
     * Returns null if already at max level.
     */
    public SettlementLevel getNextLevel() {
        return getSettlementLevel().getNextLevel();
    }
    
    /**
     * Checks if the settlement can level up.
     */
    public boolean canLevelUp() {
        SettlementLevel nextLevel = getNextLevel();
        if (nextLevel == null) {
            return false; // Already at max level
        }
        
        int villagerCount = villagers.size();
        int buildingCount = (int) buildings.stream()
            .filter(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED)
            .count();
        int employedCount = (int) villagers.stream()
            .filter(VillagerData::isEmployed)
            .count();
        
        return villagerCount >= nextLevel.getRequiredVillagers() &&
               buildingCount >= nextLevel.getRequiredBuildings() &&
               employedCount >= nextLevel.getRequiredEmployedVillagers();
    }

    /**
     * Checks if a given position is within this settlement's bounds.
     * @param pos Position to check
     * @return true if position is within settlement radius
     */
    public boolean isWithinBounds(BlockPos pos) {
        if (lecternPos == null || pos == null) {
            return false;
        }
        double distanceSq = lecternPos.getSquaredDistance(pos);
        return distanceSq <= (radius * radius);
    }
    
    /**
     * Validates that this settlement has valid data.
     * @return true if the settlement is valid, false otherwise
     */
    public boolean isValid() {
        return lecternPos != null && radius > 0 && name != null && !name.isEmpty();
    }
    
    /**
     * Validates that a position is valid for a settlement.
     * @param pos Position to validate
     * @return true if position is valid (not null)
     */
    public static boolean isValidPosition(BlockPos pos) {
        return pos != null;
    }
    
    /**
     * Validates that a radius is valid for a settlement.
     * @param radius Radius to validate
     * @return true if radius is positive
     */
    public static boolean isValidRadius(int radius) {
        return radius > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settlement that = (Settlement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

