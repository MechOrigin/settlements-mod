package com.secretasain.settlements.building;

import com.secretasain.settlements.SettlementsMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for loading NBT structure files from resources.
 */
public class StructureLoader {
    private static final Map<Identifier, StructureData> CACHE = new HashMap<>();
    
    /**
     * Loads a structure from a resource location.
     * Structures are cached after first load.
     * @param resourceLocation Resource location (e.g., "settlements:structures/wall_basic.nbt")
     * @param server Minecraft server instance (for resource manager access)
     * @return StructureData if loaded successfully, null otherwise
     */
    public static StructureData loadStructure(Identifier resourceLocation, MinecraftServer server) {
        // Check cache first
        if (CACHE.containsKey(resourceLocation)) {
            return CACHE.get(resourceLocation);
        }
        
        ResourceManager resourceManager = server.getResourceManager();
        
        try {
            SettlementsMod.LOGGER.info("Loading structure with identifier: {}", resourceLocation);
            
            // For data files, use getAllResources() with the identifier directly
            // Minecraft automatically looks in data/<namespace>/<path>
            // Identifier "settlements:structures/lvl1_oak_wall.nbt" -> looks in "data/settlements/structures/lvl1_oak_wall.nbt"
            List<Resource> resources = new java.util.ArrayList<>();
            try {
                resources = resourceManager.getAllResources(resourceLocation);
                SettlementsMod.LOGGER.info("getAllResources() returned {} resources for {}", resources.size(), resourceLocation);
            } catch (Exception e) {
                SettlementsMod.LOGGER.error("Exception calling getAllResources() for {}: {}", resourceLocation, e.getMessage());
                e.printStackTrace();
                
                // Try alternative: use findResources() which might work better for data files
                try {
                    java.util.Map<net.minecraft.util.Identifier, Resource> foundResources = 
                        resourceManager.findResources(resourceLocation.getPath(), path -> path.getPath().endsWith(".nbt"));
                    SettlementsMod.LOGGER.info("findResources() found {} resources", foundResources.size());
                    if (!foundResources.isEmpty()) {
                        resources = new java.util.ArrayList<>(foundResources.values());
                    }
                } catch (Exception e2) {
                    SettlementsMod.LOGGER.error("findResources() also failed: {}", e2.getMessage());
                }
            }
            
            if (resources.isEmpty()) {
                SettlementsMod.LOGGER.error("No resources found for identifier: {}", resourceLocation);
                // Log available structure files for debugging
                try {
                    java.util.Map<net.minecraft.util.Identifier, Resource> allStructures = 
                        resourceManager.findResources("structures", path -> path.getPath().endsWith(".nbt"));
                    SettlementsMod.LOGGER.info("Available structure files: {}", allStructures.keySet());
                } catch (Exception e) {
                    SettlementsMod.LOGGER.debug("Could not list available structures: {}", e.getMessage());
                }
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
                SettlementsMod.LOGGER.info("Loaded structure: {} ({} blocks)", resourceLocation, structure.getBlockCount());
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
     * @param path Path relative to structures folder (e.g., "wall_basic.nbt")
     * @param server Minecraft server instance
     * @return StructureData if loaded successfully, null otherwise
     */
    public static StructureData loadStructure(String path, MinecraftServer server) {
        Identifier resourceLocation = new Identifier("settlements", "structures/" + path);
        return loadStructure(resourceLocation, server);
    }
    
    /**
     * Clears the structure cache.
     * Useful for development/reloading.
     */
    public static void clearCache() {
        CACHE.clear();
        SettlementsMod.LOGGER.info("Structure cache cleared");
    }
    
    /**
     * Clears a specific structure from the cache.
     * @param resourceLocation The structure identifier to clear from cache
     */
    public static void clearCache(Identifier resourceLocation) {
        if (CACHE.remove(resourceLocation) != null) {
            SettlementsMod.LOGGER.info("Cleared structure from cache: {}", resourceLocation);
        }
    }
    
    /**
     * Forces a structure to be reloaded from disk, bypassing the cache.
     * @param resourceLocation Resource location (e.g., "settlements:structures/wall_basic.nbt")
     * @param server Minecraft server instance (for resource manager access)
     * @return StructureData if loaded successfully, null otherwise
     */
    public static StructureData loadStructureForceReload(Identifier resourceLocation, MinecraftServer server) {
        // Clear from cache first
        clearCache(resourceLocation);
        // Load fresh
        return loadStructure(resourceLocation, server);
    }
    
    /**
     * Gets a cached structure without loading.
     * @param resourceLocation Resource location
     * @return Cached StructureData, or null if not cached
     */
    public static StructureData getCached(Identifier resourceLocation) {
        return CACHE.get(resourceLocation);
    }
    
    /**
     * Discovers all available structure files from resources.
     * This method searches for all .nbt files in the settlements:structures/ namespace.
     * Clears the cache before discovery to ensure new/modified files are detected.
     * @param server Minecraft server instance
     * @return List of structure identifiers (e.g., "settlements:structures/lvl1_oak_wall.nbt")
     */
    public static List<Identifier> discoverStructures(MinecraftServer server) {
        // Clear cache to ensure new/modified files are picked up
        clearCache();
        
        List<Identifier> structures = new java.util.ArrayList<>();
        ResourceManager resourceManager = server.getResourceManager();
        
        try {
            // findResources() searches relative to resource root (includes both assets/ and data/)
            // Path "settlements/structures" will search in both:
            // - assets/settlements/structures/ (if any)
            // - data/settlements/structures/ (what we want)
            Map<Identifier, Resource> structureResources = resourceManager.findResources(
                "settlements/structures",
                path -> path.getPath().endsWith(".nbt")
            );
            
            SettlementsMod.LOGGER.info("Server-side discovery found {} structure resources", structureResources.size());
            
            for (Map.Entry<Identifier, Resource> entry : structureResources.entrySet()) {
                Identifier id = entry.getKey();
                // Filter to only include structures from the "settlements" namespace
                // and ensure they're in the structures/ path
                if ("settlements".equals(id.getNamespace()) && id.getPath().startsWith("structures/")) {
                    structures.add(id);
                    SettlementsMod.LOGGER.info("Discovered structure: {}", id);
                }
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Error discovering structures on server", e);
        }
        
        SettlementsMod.LOGGER.info("Discovered {} structures total", structures.size());
        return structures;
    }
    
    /**
     * Gets a list of structure filenames (without path/namespace) from discovered structures.
     * @param server Minecraft server instance
     * @return List of structure filenames (e.g., "lvl1_oak_wall.nbt")
     */
    public static List<String> discoverStructureNames(MinecraftServer server) {
        List<Identifier> structures = discoverStructures(server);
        List<String> names = new java.util.ArrayList<>();
        
        for (Identifier id : structures) {
            String path = id.getPath();
            // Extract filename from path (e.g., "structures/lvl1_oak_wall.nbt" -> "lvl1_oak_wall.nbt")
            if (path.contains("/")) {
                String filename = path.substring(path.lastIndexOf('/') + 1);
                names.add(filename);
            } else {
                names.add(path);
            }
        }
        
        return names;
    }
}

