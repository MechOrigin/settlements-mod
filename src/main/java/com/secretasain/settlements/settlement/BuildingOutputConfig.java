package com.secretasain.settlements.settlement;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.secretasain.settlements.SettlementsMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Loads and manages building output configurations from JSON.
 */
public class BuildingOutputConfig {
    private static final Map<String, List<OutputEntry>> OUTPUT_CONFIGS = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static boolean loaded = false;
    
    /**
     * Represents a single output entry from the config.
     */
    public static class OutputEntry {
        public final Item item;
        public final int weight;
        public final int minCount;
        public final int maxCount;
        
        public OutputEntry(Item item, int weight, int minCount, int maxCount) {
            this.item = item;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }
    
    /**
     * Loads the building output configuration from JSON.
     * Should be called during resource reload or mod initialization.
     */
    public static void load(ResourceManager resourceManager) {
        OUTPUT_CONFIGS.clear();
        
        try {
            Identifier configId = new Identifier("settlements", "building_outputs.json");
            var resource = resourceManager.getResource(configId);
            if (resource.isEmpty()) {
                SettlementsMod.LOGGER.warn("Building output config not found: {}", configId);
                return;
            }
            
            try (InputStream stream = resource.get().getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream)) {
                
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                JsonObject buildingOutputs = root.getAsJsonObject("building_outputs");
                
                if (buildingOutputs == null) {
                    SettlementsMod.LOGGER.warn("No 'building_outputs' object in config");
                    return;
                }
                
                for (Map.Entry<String, JsonElement> entry : buildingOutputs.entrySet()) {
                    String buildingType = entry.getKey();
                    JsonObject buildingConfig = entry.getValue().getAsJsonObject();
                    
                    List<OutputEntry> outputs = new ArrayList<>();
                    if (buildingConfig.has("outputs")) {
                        for (JsonElement outputElement : buildingConfig.getAsJsonArray("outputs")) {
                            JsonObject outputObj = outputElement.getAsJsonObject();
                            
                            String itemId = outputObj.get("item").getAsString();
                            int weight = outputObj.get("weight").getAsInt();
                            int minCount = outputObj.get("min_count").getAsInt();
                            int maxCount = outputObj.get("max_count").getAsInt();
                            
                            Identifier itemIdentifier = Identifier.tryParse(itemId);
                            if (itemIdentifier == null) {
                                SettlementsMod.LOGGER.warn("Invalid item identifier: {}", itemId);
                                continue;
                            }
                            
                            Item item = Registries.ITEM.get(itemIdentifier);
                            if (item == null) {
                                SettlementsMod.LOGGER.warn("Item not found: {}", itemId);
                                continue;
                            }
                            
                            outputs.add(new OutputEntry(item, weight, minCount, maxCount));
                        }
                    }
                    
                    OUTPUT_CONFIGS.put(buildingType, outputs);
                    SettlementsMod.LOGGER.info("Loaded {} output entries for building type: {}", outputs.size(), buildingType);
                }
                
                loaded = true;
                SettlementsMod.LOGGER.info("Building output config loaded successfully");
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Failed to load building output config", e);
        }
    }
    
    /**
     * Gets output entries for a building type.
     * @param buildingType The building type (e.g., "wall", "smithing", "farm")
     * @return List of output entries, or empty list if not found
     */
    public static List<OutputEntry> getOutputs(String buildingType) {
        return OUTPUT_CONFIGS.getOrDefault(buildingType, Collections.emptyList());
    }
    
    /**
     * Generates outputs for a building type based on weighted random selection.
     * @param buildingType The building type
     * @return List of ItemStacks generated
     */
    public static List<ItemStack> generateOutputs(String buildingType, Random random) {
        List<OutputEntry> entries = getOutputs(buildingType);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ItemStack> outputs = new ArrayList<>();
        
        // Calculate total weight
        int totalWeight = entries.stream().mapToInt(e -> e.weight).sum();
        if (totalWeight == 0) {
            return Collections.emptyList();
        }
        
        // Generate one output per execution (can be modified to generate multiple)
        int roll = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (OutputEntry entry : entries) {
            currentWeight += entry.weight;
            if (roll < currentWeight) {
                int count = entry.minCount + random.nextInt(entry.maxCount - entry.minCount + 1);
                outputs.add(new ItemStack(entry.item, count));
                break;
            }
        }
        
        return outputs;
    }
    
    /**
     * Checks if the config has been loaded.
     */
    public static boolean isLoaded() {
        return loaded;
    }
}

