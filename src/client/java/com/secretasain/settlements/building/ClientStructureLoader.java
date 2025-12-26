package com.secretasain.settlements.building;

import com.secretasain.settlements.SettlementsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side utility for loading NBT structure files from resources.
 */
public class ClientStructureLoader {
    private static final Map<Identifier, StructureData> CACHE = new HashMap<>();
    
    /**
     * Loads a structure from a resource location on the client.
     * Structures are cached after first load.
     * @param resourceLocation Resource location (e.g., "settlements:structures/lvl1_oak_wall.nbt")
     * @return StructureData if loaded successfully, null otherwise
     */
    public static StructureData loadStructure(Identifier resourceLocation) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return null;
        }
        
        // Check cache first
        if (CACHE.containsKey(resourceLocation)) {
            return CACHE.get(resourceLocation);
        }
        
        ResourceManager resourceManager = client.getResourceManager();
        
        try {
            SettlementsMod.LOGGER.info("Loading structure with identifier: {}", resourceLocation);
            
            // For data files, use getAllResources() with the identifier directly
            // Minecraft automatically looks in data/<namespace>/<path>
            // Identifier "settlements:structures/lvl1_oak_wall.nbt" -> looks in "data/settlements/structures/lvl1_oak_wall.nbt"
            List<Resource> resources;
            try {
                resources = resourceManager.getAllResources(resourceLocation);
                SettlementsMod.LOGGER.info("getAllResources() returned {} resources for {}", resources.size(), resourceLocation);
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Exception calling getAllResources(): {}", e.getMessage());
                e.printStackTrace();
                return null;
            }
            
            if (resources.isEmpty()) {
                SettlementsMod.LOGGER.error("No resources found for identifier: {}", resourceLocation);
                // Try alternative: construct data file identifier
                try {
                    String pathStr = resourceLocation.getPath();
                    Identifier dataFileId = new Identifier(resourceLocation.getNamespace(), "data/" + resourceLocation.getNamespace() + "/" + pathStr);
                    SettlementsMod.LOGGER.info("Trying alternative identifier: {}", dataFileId);
                    resources = resourceManager.getAllResources(dataFileId);
                    if (!resources.isEmpty()) {
                        SettlementsMod.LOGGER.info("Found {} resources with alternative identifier", resources.size());
                    }
                } catch (Exception e) {
                    SettlementsMod.LOGGER.debug("Alternative identifier failed: {}", e.getMessage());
                }
            }
            
            if (resources.isEmpty()) {
                SettlementsMod.LOGGER.error("Structure file not found: {}", resourceLocation);
                return null;
            }
            
            // Use the first resource (highest priority)
            Resource resource = resources.get(0);
            SettlementsMod.LOGGER.info("Using resource: {} (found {} total)", resourceLocation, resources.size());
            try (InputStream inputStream = resource.getInputStream()) {
                NbtCompound nbt = NbtIo.readCompressed(inputStream);
                
                // Extract name from resource location
                String name = resourceLocation.getPath();
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf('/') + 1);
                }
                if (name.endsWith(".nbt")) {
                    name = name.substring(0, name.length() - 4);
                }
                
                StructureData structure = new StructureData(nbt, name);
                
                // Validate structure
                StructureData.ValidationResult validation = structure.validate();
                if (!validation.isValid()) {
                    SettlementsMod.LOGGER.error("Structure {} failed validation:", resourceLocation);
                    for (String error : validation.getErrors()) {
                        SettlementsMod.LOGGER.error("  - {}", error);
                    }
                    return null;
                }
                
                // Log warnings if any
                if (!validation.getWarnings().isEmpty()) {
                    SettlementsMod.LOGGER.warn("Structure {} has warnings:", resourceLocation);
                    for (String warning : validation.getWarnings()) {
                        SettlementsMod.LOGGER.warn("  - {}", warning);
                    }
                }
                
                // Cache and return
                CACHE.put(resourceLocation, structure);
                SettlementsMod.LOGGER.info("Loaded structure on client: {} ({} blocks)", resourceLocation, structure.getBlockCount());
                return structure;
            }
        } catch (IOException e) {
            SettlementsMod.LOGGER.error("Failed to read structure file {}: {}", resourceLocation, e.getMessage());
            return null;
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Failed to parse structure file {}: {}", resourceLocation, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Loads a structure using the default namespace and path.
     * @param path Path relative to structures folder (e.g., "lvl1_oak_wall.nbt")
     * @return StructureData if loaded successfully, null otherwise
     */
    public static StructureData loadStructure(String path) {
        Identifier resourceLocation = new Identifier("settlements", "structures/" + path);
        return loadStructure(resourceLocation);
    }
    
    /**
     * Clears the structure cache.
     */
    public static void clearCache() {
        CACHE.clear();
        SettlementsMod.LOGGER.info("Client structure cache cleared");
    }
    
    /**
     * Clears a specific structure from the cache.
     * @param resourceLocation The structure identifier to clear from cache
     */
    public static void clearCache(Identifier resourceLocation) {
        if (CACHE.remove(resourceLocation) != null) {
            SettlementsMod.LOGGER.info("Cleared structure from client cache: {}", resourceLocation);
        }
    }
    
    /**
     * Forces a structure to be reloaded from disk, bypassing the cache.
     * @param resourceLocation Resource location (e.g., "settlements:structures/lvl1_oak_wall.nbt")
     * @return StructureData if loaded successfully, null otherwise
     */
    public static StructureData loadStructureForceReload(Identifier resourceLocation) {
        // Clear from cache first
        clearCache(resourceLocation);
        // Load fresh
        return loadStructure(resourceLocation);
    }
}

