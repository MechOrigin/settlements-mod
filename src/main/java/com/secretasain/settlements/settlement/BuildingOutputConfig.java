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
    private static boolean loadAttempted = false; // Track if we've attempted to load (even if failed)
    
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
        // Don't reload if already loaded (prevents spam in render loops)
        if (loaded && !OUTPUT_CONFIGS.isEmpty()) {
            SettlementsMod.LOGGER.debug("Building output config already loaded with {} types, skipping", OUTPUT_CONFIGS.size());
            return;
        }
        
        // Clear previous state (allow retry if previous attempt failed)
        OUTPUT_CONFIGS.clear();
        loaded = false;
        loadAttempted = true;
        
        SettlementsMod.LOGGER.info("Loading building output config...");
        
        try {
            // Identifier for data files: namespace:path (path is relative to data/namespace/)
            // So "settlements:building_outputs.json" resolves to "data/settlements/building_outputs.json"
            Identifier configId = new Identifier("settlements", "building_outputs.json");
            
            SettlementsMod.LOGGER.info("Attempting to load building output config from: {}", configId);
            
            // Try getAllResources first (works better for data files)
            java.util.List<net.minecraft.resource.Resource> resources = new java.util.ArrayList<>();
            try {
                resources = resourceManager.getAllResources(configId);
                SettlementsMod.LOGGER.info("getAllResources({}) returned {} resources", configId, resources.size());
            } catch (Exception e) {
                SettlementsMod.LOGGER.warn("getAllResources({}) failed: {}", configId, e.getMessage());
                e.printStackTrace();
            }
            
            // Fallback to getResource if getAllResources didn't work
            if (resources.isEmpty()) {
                try {
                    var resource = resourceManager.getResource(configId);
                    if (resource.isPresent()) {
                        resources = java.util.Collections.singletonList(resource.get());
                        SettlementsMod.LOGGER.info("getResource({}) found resource", configId);
                    } else {
                        SettlementsMod.LOGGER.warn("getResource({}) returned empty Optional", configId);
                    }
                } catch (Exception e) {
                    SettlementsMod.LOGGER.warn("getResource({}) failed: {}", configId, e.getMessage());
                    e.printStackTrace();
                }
            }
            
            if (resources.isEmpty()) {
                SettlementsMod.LOGGER.error("Building output config file not found! Tried: {}", configId);
                SettlementsMod.LOGGER.error("Expected location: src/main/resources/data/settlements/building_outputs.json");
                SettlementsMod.LOGGER.error("This is a server-side issue - check if file exists and is in the correct location");
                return;
            }
            
            // Use first resource found
            SettlementsMod.LOGGER.info("Reading building output config from resource: {}", resources.get(0).getResourcePackName());
            try (InputStream stream = resources.get(0).getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream)) {
                
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) {
                    SettlementsMod.LOGGER.error("Failed to parse JSON - root is null");
                    return;
                }
                
                JsonObject buildingOutputs = root.getAsJsonObject("building_outputs");
                
                if (buildingOutputs == null) {
                    SettlementsMod.LOGGER.error("No 'building_outputs' object in config JSON. Root keys: {}", root.keySet());
                    return;
                }
                
                SettlementsMod.LOGGER.info("Found 'building_outputs' object with {} building types", buildingOutputs.size());
                
                for (Map.Entry<String, JsonElement> entry : buildingOutputs.entrySet()) {
                    String buildingType = entry.getKey();
                    JsonElement value = entry.getValue();
                    
                    if (!value.isJsonObject()) {
                        SettlementsMod.LOGGER.warn("Building type '{}' value is not a JSON object, skipping", buildingType);
                        continue;
                    }
                    
                    JsonObject buildingConfig = value.getAsJsonObject();
                    
                    List<OutputEntry> outputs = new ArrayList<>();
                    if (buildingConfig.has("outputs")) {
                        JsonElement outputsElement = buildingConfig.get("outputs");
                        if (!outputsElement.isJsonArray()) {
                            SettlementsMod.LOGGER.warn("Building type '{}' outputs is not an array, skipping", buildingType);
                            continue;
                        }
                        
                        for (JsonElement outputElement : outputsElement.getAsJsonArray()) {
                            if (!outputElement.isJsonObject()) {
                                SettlementsMod.LOGGER.warn("Output entry is not a JSON object, skipping");
                                continue;
                            }
                            
                            JsonObject outputObj = outputElement.getAsJsonObject();
                            
                            if (!outputObj.has("item") || !outputObj.has("weight") || 
                                !outputObj.has("min_count") || !outputObj.has("max_count")) {
                                SettlementsMod.LOGGER.warn("Output entry missing required fields, skipping");
                                continue;
                            }
                            
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
                                SettlementsMod.LOGGER.warn("Item not found in registry: {}", itemId);
                                continue;
                            }
                            
                            outputs.add(new OutputEntry(item, weight, minCount, maxCount));
                            SettlementsMod.LOGGER.debug("Added output entry: {} (weight: {}, count: {}-{})", 
                                itemId, weight, minCount, maxCount);
                        }
                    }
                    
                    OUTPUT_CONFIGS.put(buildingType, outputs);
                    SettlementsMod.LOGGER.info("Loaded {} output entries for building type: {}", outputs.size(), buildingType);
                }
                
                loaded = true;
                SettlementsMod.LOGGER.info("Building output config loaded successfully with {} building types: {}", 
                    OUTPUT_CONFIGS.size(), OUTPUT_CONFIGS.keySet());
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Failed to load building output config", e);
            e.printStackTrace();
            loaded = false;
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
    
    /**
     * Gets the number of loaded building type configs (for debugging).
     */
    public static int getLoadedConfigCount() {
        return OUTPUT_CONFIGS.size();
    }
}

