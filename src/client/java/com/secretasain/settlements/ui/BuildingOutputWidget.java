package com.secretasain.settlements.ui;

import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.BuildingOutputConfig;
import com.secretasain.settlements.settlement.CropStatistics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Widget for displaying building outputs and production statistics.
 * Shows item outputs, items per minute, and detailed tooltips.
 * For farm buildings, shows crop statistics and harvest estimates.
 */
public class BuildingOutputWidget extends AlwaysSelectedEntryListWidget<BuildingOutputWidget.OutputEntry> {
    // Static tooltip lock to prevent multiple tooltips from rendering simultaneously
    private static boolean tooltipRendering = false;
    private static BuildingOutputWidget currentTooltipWidget = null;
    
    /**
     * Clears the tooltip rendering state. Should be called when widget is removed or tab is switched.
     */
    public static void clearTooltipState() {
        tooltipRendering = false;
        currentTooltipWidget = null;
    }
    
    private final Building building;
    private final String buildingType;
    private List<BuildingOutputConfig.OutputEntry> outputEntries;
    private int serverFarmlandCount = -1;
    private List<CropStatistics> serverCropStats = null;
    private boolean hasServerData = false;
    
    // Task execution interval: 200 ticks = 10 seconds (20 ticks per second)
    private static final int TASK_INTERVAL_TICKS = 200;
    private static final double TASK_INTERVAL_SECONDS = TASK_INTERVAL_TICKS / 20.0;
    private static final double TASKS_PER_MINUTE = 60.0 / TASK_INTERVAL_SECONDS;
    
    public BuildingOutputWidget(MinecraftClient client, int width, int height, int top, int bottom, 
                               int itemHeight, Building building, String buildingType) {
        super(client, width, height, top, bottom, itemHeight);
        this.building = building;
        this.buildingType = buildingType;
        
        // For farm buildings, we'll show crop statistics instead of JSON outputs
        // For other buildings, load output entries from config
        if ("farm".equals(buildingType)) {
            this.outputEntries = null; // Farms use active harvesting, not JSON config
        } else if ("unknown".equals(buildingType)) {
            this.outputEntries = null; // Unknown building type, will show "No outputs configured"
        } else {
            // Load output entries from config (may return null or empty list)
            this.outputEntries = BuildingOutputConfig.getOutputs(buildingType);
            // If building type was detected but no outputs found, that's fine - will show "No outputs configured"
        }
        
        this.updateEntries();
    }
    
    /**
     * Updates widget with data received from server.
     */
    public void updateWithServerData(List<BuildingOutputConfig.OutputEntry> outputs, int farmlandCount) {
        updateWithServerData(outputs, farmlandCount, null);
    }
    
    /**
     * Updates widget with data received from server (with crop statistics).
     */
    public void updateWithServerData(List<BuildingOutputConfig.OutputEntry> outputs, int farmlandCount, 
                                     List<CropStatistics> cropStats) {
        com.secretasain.settlements.SettlementsMod.LOGGER.info("BuildingOutputWidget.updateWithServerData: buildingType={}, outputs={}, farmlandCount={}, cropStats={}, hasServerData={}", 
            buildingType, outputs != null ? outputs.size() : "null", farmlandCount, 
            cropStats != null ? cropStats.size() : "null", hasServerData);
        
        // If both outputs and farmlandCount are null/empty, building has no villagers
        boolean isEmpty = (outputs == null || outputs.isEmpty()) && farmlandCount < 0 && 
                         (cropStats == null || cropStats.isEmpty());
        
        this.outputEntries = outputs;
        this.serverFarmlandCount = farmlandCount;
        this.serverCropStats = cropStats;
        this.hasServerData = true;
        
        // If empty response (no villagers), set a flag to show "No villagers assigned" message
        if (isEmpty) {
            this.outputEntries = null; // Clear outputs to trigger "No villagers" message
            com.secretasain.settlements.SettlementsMod.LOGGER.info("BuildingOutputWidget: Empty response received - building has no assigned villagers");
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("BuildingOutputWidget.updateWithServerData: After update - hasServerData={}, serverFarmlandCount={}, cropStats={}", 
            this.hasServerData, this.serverFarmlandCount, this.serverCropStats != null ? this.serverCropStats.size() : "null");
        this.updateEntries();
    }
    
    /**
     * Gets the building associated with this widget.
     */
    public Building getBuilding() {
        return building;
    }
    
    /**
     * Updates the list entries from the output data.
     */
    public void updateEntries() {
        this.clearEntries();
        
        // Reset farmland count cache when updating (in case building changed)
        farmlandCount = -1;
        
        // For farm buildings, show crop statistics
        if ("farm".equals(buildingType)) {
            updateFarmEntries();
            return;
        }
        
        // For other buildings, show JSON config outputs
        // Use server data if available, otherwise show placeholder
        if (hasServerData) {
            // We have server data - use it
            if (outputEntries == null || outputEntries.isEmpty()) {
                // Check if this is because building has no villagers (empty response from server)
                // vs. building type has no outputs configured
                if (serverFarmlandCount < 0 && outputEntries == null) {
                    // Empty response from server = no villagers assigned
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("BuildingOutputWidget: Building has no assigned villagers");
                    this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "No villagers assigned"));
                    return;
                }
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingOutputWidget: No outputs configured for building type '{}'", buildingType);
                this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "No outputs configured"));
                return;
            }
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingOutputWidget: Processing {} output entries for building type '{}'", 
                outputEntries.size(), buildingType);
        } else {
            // No server data yet - show loading or placeholder
            if (outputEntries == null || outputEntries.isEmpty()) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingOutputWidget: Waiting for server data for building type '{}'", buildingType);
                this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "Loading..."));
                return;
            }
        }
        
        // Calculate total weight for probability calculations
        int totalWeight = outputEntries.stream().mapToInt(e -> e.weight).sum();
        
        for (BuildingOutputConfig.OutputEntry entry : outputEntries) {
            // Calculate expected average count per drop
            double avgCount = (entry.minCount + entry.maxCount) / 2.0;
            
            // Calculate probability (weight / total weight)
            double probability = totalWeight > 0 ? (double) entry.weight / totalWeight : 0.0;
            
            // Calculate expected items per task execution
            double expectedPerTask = avgCount * probability;
            
            // Calculate items per minute
            double itemsPerMinute = expectedPerTask * TASKS_PER_MINUTE;
            
            // Create detailed display message with statistics
            String displayMessage = String.format("%.1f%% chance, %.2f/task, %.1f/min", 
                probability * 100.0, expectedPerTask, itemsPerMinute);
            
            this.addEntry(new OutputEntry(
                entry.item,
                entry.weight,
                entry.minCount,
                entry.maxCount,
                itemsPerMinute,
                displayMessage,
                null, // Not a crop entry
                totalWeight,
                probability
            ));
        }
    }
    
    /**
     * Updates entries for farm buildings with crop statistics.
     */
    private void updateFarmEntries() {
        // Check if building has no villagers
        if (hasServerData && outputEntries == null && serverFarmlandCount < 0 && 
            (serverCropStats == null || serverCropStats.isEmpty())) {
            this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "No villagers assigned"));
            com.secretasain.settlements.SettlementsMod.LOGGER.info("updateFarmEntries: Building has no assigned villagers");
            return;
        }
        
        // Use server data if available
        int farmlandCount = -1;
        if (hasServerData && serverFarmlandCount >= 0) {
            farmlandCount = serverFarmlandCount;
            com.secretasain.settlements.SettlementsMod.LOGGER.info("updateFarmEntries: Using server data, farmlandCount={}, cropStats={}", 
                farmlandCount, serverCropStats != null ? serverCropStats.size() : "null");
        } else {
            farmlandCount = countFarmlandInStructure();
            com.secretasain.settlements.SettlementsMod.LOGGER.info("updateFarmEntries: Using client data, farmlandCount={}", farmlandCount);
        }
        
        if (farmlandCount == -1) {
            // No data available - show loading message
            if (!hasServerData) {
                this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "Loading farm data..."));
                com.secretasain.settlements.SettlementsMod.LOGGER.info("updateFarmEntries: No server data yet, showing loading message");
            } else {
                this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "No farmland detected"));
                com.secretasain.settlements.SettlementsMod.LOGGER.info("updateFarmEntries: Server data received but farmlandCount=-1");
            }
            return;
        }
        
        // Show farmland count
        this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
            String.format("Farmland plots: %d", farmlandCount)));
        
        // Show crop statistics if available from server
        if (hasServerData && serverCropStats != null && !serverCropStats.isEmpty()) {
            // Calculate total items per minute across all crops
            double totalItemsPerMinute = 0.0;
            int totalMatureCrops = 0;
            int totalImmatureCrops = 0;
            
            for (CropStatistics stats : serverCropStats) {
                // Calculate items per minute for this crop type
                // Expected items per harvest cycle = matureCount * avgDropsPerCrop
                // Harvest cycle time = estimatedTicksUntilHarvest (for immature) or 0 (for mature)
                // Items per minute = (expectedItemsPerHarvest / harvestCycleTimeInMinutes) * 60
                double expectedItemsPerHarvest = stats.expectedItemsPerHarvest;
                
                // If there are mature crops, they can be harvested immediately
                // If there are immature crops, calculate based on growth time
                double itemsPerMinuteForCrop = 0.0;
                if (stats.matureCount > 0) {
                    // Mature crops can be harvested every task cycle (10 seconds = 6 per minute)
                    itemsPerMinuteForCrop += stats.matureCount * stats.avgDropsPerCrop * TASKS_PER_MINUTE;
                }
                if (stats.immatureCount > 0 && stats.estimatedTicksUntilHarvest > 0) {
                    // Immature crops will be ready in estimatedTicksUntilHarvest
                    // After that, they can be harvested every task cycle
                    double harvestCycleMinutes = stats.estimatedTicksUntilHarvest / 1200.0; // 1200 ticks = 1 minute
                    if (harvestCycleMinutes > 0) {
                        // Once mature, they'll produce items at the same rate as mature crops
                        double futureItemsPerMinute = stats.immatureCount * stats.avgDropsPerCrop * TASKS_PER_MINUTE;
                        // Average over the growth period (simplified calculation)
                        itemsPerMinuteForCrop += futureItemsPerMinute * 0.5; // Rough estimate
                    }
                }
                
                totalItemsPerMinute += itemsPerMinuteForCrop;
                totalMatureCrops += stats.matureCount;
                totalImmatureCrops += stats.immatureCount;
                
                // Display crop type entry with statistics
                String cropDisplayName = capitalizeFirst(stats.cropType);
                String maturityStatus = String.format("%d mature, %d immature", stats.matureCount, stats.immatureCount);
                
                // Create entry with crop item icon if available
                net.minecraft.item.Item cropItem = net.minecraft.registry.Registries.ITEM.get(stats.cropItemId);
                this.addEntry(new OutputEntry(
                    cropItem != null ? cropItem : null,
                    0, 0, 0,
                    itemsPerMinuteForCrop,
                    String.format("%s: %s", cropDisplayName, maturityStatus),
                    stats, // Pass crop statistics for tooltip
                    0,
                    0.0
                ));
                
                // Add detailed breakdown entry with crop stats for visual indicators
                if (stats.totalCount > 0) {
                    double maturityPercent = stats.getMaturityPercentage() * 100.0;
                    String ageDist = stats.getAgeDistributionString();
                    this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
                        String.format("  %d total (%.0f%% mature)", stats.totalCount, maturityPercent),
                        stats, // Pass stats for progress bar
                        0,
                        0.0
                    ));
                    
                    if (stats.immatureCount > 0 && stats.estimatedTicksUntilHarvest > 0) {
                        double minutesUntilHarvest = stats.getEstimatedMinutesUntilHarvest();
                        this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
                            String.format("  ~%.1f min until harvest", minutesUntilHarvest),
                            stats, // Pass stats for progress bar
                            0,
                            0.0
                        ));
                    }
                }
            }
            
            // Show summary
            if (totalMatureCrops > 0 || totalImmatureCrops > 0) {
                this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, "---"));
                this.addEntry(new OutputEntry(null, 0, 0, 0, totalItemsPerMinute, 
                    String.format("Total: ~%.1f items/min", totalItemsPerMinute)));
                this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
                    String.format("(%d mature, %d immature)", totalMatureCrops, totalImmatureCrops)));
            }
        } else if (farmlandCount > 0) {
            // No crop statistics available, show theoretical capacity
            double avgDropsPerHarvest = 2.0; // Average 2 items per crop harvest
            double maxItemsPerCycle = farmlandCount * avgDropsPerHarvest;
            double maxItemsPerMinute = maxItemsPerCycle * TASKS_PER_MINUTE;
            
            this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
                String.format("Max capacity: ~%.1f/min", maxItemsPerMinute)));
            this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
                "(when all crops mature)"));
        }
        
        // Show note about active harvesting
        this.addEntry(new OutputEntry(null, 0, 0, 0, 0.0, 
            "Active harvesting: checks every 10s"));
    }
    
    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // Handle identifiers like "minecraft:wheat" or "wheat_seeds"
        String processed = str;
        if (processed.contains(":")) {
            // Extract the part after the colon
            processed = processed.substring(processed.indexOf(":") + 1);
        }
        // Replace underscores with spaces and capitalize each word
        String[] parts = processed.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append(" ");
            if (!parts[i].isEmpty()) {
                result.append(parts[i].substring(0, 1).toUpperCase());
                if (parts[i].length() > 1) {
                    result.append(parts[i].substring(1));
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Counts farmland blocks in the building's structure.
     * @return Number of farmland blocks found
     */
    private int farmlandCount = -1; // -1 means not calculated yet
    
    private int countFarmlandInStructure() {
        if (farmlandCount >= 0) {
            return farmlandCount; // Cache result
        }
        
        farmlandCount = 0; // Initialize to 0 before calculation
        
        try {
            // Load structure NBT on client
            // NOTE: Client ResourceManager may not have access to data files (per cursor rules)
            // This is a limitation - we can't reliably load structure NBT on client
            // For now, we'll try but gracefully handle failure
            net.minecraft.util.Identifier structureId = building.getStructureType();
            net.minecraft.resource.ResourceManager resourceManager = client.getResourceManager();
            
            if (resourceManager != null) {
                // Try multiple resource paths - structure ID might be in different formats
                java.util.List<net.minecraft.resource.Resource> resources = new java.util.ArrayList<>();
                
                // Try direct identifier first
                try {
                    resources = resourceManager.getAllResources(structureId);
                } catch (Exception e) {
                    // Expected on client - data files may not be accessible
                }
                
                // If no resources found, try alternative path formats
                if (resources.isEmpty()) {
                    String path = structureId.getPath();
                    // Try with "structures/" prefix if not present
                    if (!path.startsWith("structures/")) {
                        net.minecraft.util.Identifier altId1 = new net.minecraft.util.Identifier(
                            structureId.getNamespace(), "structures/" + path);
                        try {
                            resources = resourceManager.getAllResources(altId1);
                        } catch (Exception e2) {
                            // Expected on client
                        }
                    }
                    
                    // Try data/settlements/structures/ path format
                    if (resources.isEmpty()) {
                        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                        net.minecraft.util.Identifier altId2 = new net.minecraft.util.Identifier(
                            "settlements", "structures/" + fileName);
                        try {
                            resources = resourceManager.getAllResources(altId2);
                        } catch (Exception e3) {
                            // Expected on client - data files not accessible
                        }
                    }
                }
                
                if (!resources.isEmpty()) {
                    try (java.io.InputStream inputStream = resources.get(0).getInputStream()) {
                        net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.readCompressed(inputStream);
                        
                        // Parse structure to count farmland
                        farmlandCount = countFarmlandInNBT(nbt);
                    }
                } else {
                    // Client can't load data files - this is expected per cursor rules
                    // We'll show a placeholder message instead
                    farmlandCount = -1; // Use -1 to indicate "could not load"
                    // Don't log warning - this is expected behavior on client
                }
            }
        } catch (Exception e) {
            // Client can't load data files - expected behavior
            farmlandCount = -1; // Use -1 to indicate "could not load"
            // Don't log warning - this is expected behavior on client
        }
        
        return farmlandCount;
    }
    
    /**
     * Counts farmland blocks in a structure NBT.
     */
    private int countFarmlandInNBT(net.minecraft.nbt.NbtCompound nbt) {
        int count = 0;
        
        try {
            // Read palette
            if (nbt.contains("palette", 9)) {
                net.minecraft.nbt.NbtList paletteList = nbt.getList("palette", 10);
                java.util.Set<Integer> farmlandIndices = new java.util.HashSet<>();
                
                for (int i = 0; i < paletteList.size(); i++) {
                    net.minecraft.nbt.NbtCompound paletteEntry = paletteList.getCompound(i);
                    if (paletteEntry.contains("Name", 8)) {
                        String blockName = paletteEntry.getString("Name");
                        // Check if it's farmland (case-insensitive, check for farmland in name)
                        String blockNameLower = blockName.toLowerCase();
                        if (blockNameLower.contains("farmland") || 
                            blockNameLower.equals("minecraft:farmland") ||
                            blockNameLower.endsWith(":farmland")) {
                            farmlandIndices.add(i);
                            com.secretasain.settlements.SettlementsMod.LOGGER.debug("Found farmland block in palette: {} (index: {})", blockName, i);
                        }
                    }
                }
                
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Found {} farmland indices in palette", farmlandIndices.size());
                
                // Count blocks with farmland indices
                if (nbt.contains("blocks", 9)) {
                    net.minecraft.nbt.NbtList blocksList = nbt.getList("blocks", 10);
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("Checking {} blocks for farmland", blocksList.size());
                    for (int i = 0; i < blocksList.size(); i++) {
                        net.minecraft.nbt.NbtCompound blockNbt = blocksList.getCompound(i);
                        if (blockNbt.contains("state", 3)) {
                            int stateIndex = blockNbt.getInt("state");
                            if (farmlandIndices.contains(stateIndex)) {
                                count++;
                            }
                        }
                    }
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("Counted {} farmland blocks", count);
                }
            }
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Error counting farmland in NBT: {}", e.getMessage());
        }
        
        return count;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Get client instance once at the start of the method
        MinecraftClient client = MinecraftClient.getInstance();
        
        // CRITICAL: Clear tooltip state if this widget is the current tooltip widget but shouldn't be rendering
        // This prevents tooltips from persisting when switching tabs or removing widgets
        if (currentTooltipWidget == this) {
            // Check if widget is still valid - if not, clear tooltip state immediately
            if (client.currentScreen == null || 
                !(client.currentScreen instanceof SettlementScreen) ||
                this.building == null) {
                clearTooltipState();
                return;
            }
            
            // Check if we're on the Villagers tab - if not, clear tooltip state
            SettlementScreen screen = (SettlementScreen) client.currentScreen;
            if (screen.getActiveTab() != SettlementScreen.TabType.VILLAGERS) {
                clearTooltipState();
                return;
            }
        }
        
        // CRITICAL: Don't render if building is null
        if (this.building == null) {
            // Clear tooltip state if widget shouldn't be rendering
            if (currentTooltipWidget == this) {
                clearTooltipState();
            }
            return;
        }
        
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Draw background
        context.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xFF101010);
        context.drawBorder(x - 5, y - 5, width + 10, height + 10, 0xFF404040);
        
        // Draw title (positioned above the widget with proper spacing)
        String titleText = "Building Outputs";
        if ("farm".equals(buildingType)) {
            titleText = "Farm Statistics";
        } else if (hasServerData && outputEntries != null && !outputEntries.isEmpty()) {
            titleText += " (" + outputEntries.size() + ")";
        }
        int titleWidth = this.client.textRenderer.getWidth(titleText);
        int titleY = y - 15; // Position title above widget with spacing
        context.drawText(
            this.client.textRenderer,
            Text.literal(titleText),
            x + (width - titleWidth) / 2, // Center the title
            titleY,
            0xFFFFFF,
            false
        );
        
        // Show message if no outputs (but only if not a farm - farms show their own entries)
        if (!"farm".equals(buildingType) && (outputEntries == null || outputEntries.isEmpty())) {
            String noOutputsText = "No outputs configured";
            int textWidth = this.client.textRenderer.getWidth(noOutputsText);
            context.drawText(
                this.client.textRenderer,
                Text.literal(noOutputsText),
                x + (width - textWidth) / 2,
                y + 10,
                0xAAAAAA,
                false
            );
            // Still render entries if they exist (for farm buildings)
            if (this.children().isEmpty()) {
                return;
            }
        }
        
        // Render entries
        int scrollAmount = (int)this.getScrollAmount();
        int startIndex = Math.max(0, scrollAmount / this.itemHeight);
        int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
        
        // Track which entry is hovered (only one at a time)
        OutputEntry hoveredEntry = null;
        int hoveredEntryY = 0;
        
        // First pass: render all entries and find the hovered one
        for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
            OutputEntry entry = this.children().get(i);
            int entryY = y + (i * this.itemHeight) - scrollAmount;
            
            if (entryY + this.itemHeight >= y && entryY <= y + height) {
                boolean isSelected = this.getSelectedOrNull() == entry;
                // More precise hover detection - check exact bounds
                boolean isHovered = mouseX >= x && mouseX < x + width && 
                                   mouseY >= entryY && mouseY < entryY + this.itemHeight;
                
                entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
                
                // Track the first (topmost) hovered entry
                if (isHovered && hoveredEntry == null) {
                    hoveredEntry = entry;
                    hoveredEntryY = entryY;
                }
            }
        }
        
        // Second pass: render tooltip for the hovered entry only (if any)
        // CRITICAL: Use static lock to prevent multiple tooltips from rendering simultaneously
        // Also check that this widget is still valid (building not null) and we're on the right tab
        // Check if we're on the Villagers tab before rendering tooltips
        boolean shouldRenderTooltip = false;
        if (client.currentScreen instanceof SettlementScreen) {
            SettlementScreen screen = (SettlementScreen) client.currentScreen;
            shouldRenderTooltip = (screen.getActiveTab() == SettlementScreen.TabType.VILLAGERS);
        }
        
        if (hoveredEntry != null && !tooltipRendering && building != null && shouldRenderTooltip) {
            // Lock tooltip rendering to this widget
            tooltipRendering = true;
            currentTooltipWidget = this;
            
            try {
                // Get screen-relative mouse coordinates
                // mouseX and mouseY are already relative to the screen in Minecraft's render system
                int tooltipX = mouseX + 12; // Offset to right of cursor
                int tooltipY = mouseY - 12; // Offset above cursor
                
                // Get screen dimensions for boundary checking
                int screenWidth = client.getWindow().getScaledWidth();
                int screenHeight = client.getWindow().getScaledHeight();
                
                // Ensure tooltip doesn't go off screen - adjust position if needed
                int estimatedTooltipWidth = 220; // Approximate tooltip width
                int estimatedTooltipHeight = 150; // Approximate tooltip height
                
                if (tooltipX + estimatedTooltipWidth > screenWidth) {
                    tooltipX = mouseX - estimatedTooltipWidth - 12; // Show to left of cursor instead
                }
                if (tooltipX < 0) {
                    tooltipX = 12; // Keep it on screen
                }
                
                if (tooltipY + estimatedTooltipHeight > screenHeight) {
                    tooltipY = screenHeight - estimatedTooltipHeight - 12; // Move up if too low
                }
                if (tooltipY < 0) {
                    tooltipY = 12; // Keep it on screen
                }
                
                // Render tooltip with adjusted position
                // Pass 'this' reference so the entry can verify it belongs to the correct widget
                hoveredEntry.renderTooltip(context, tooltipX, tooltipY, screenWidth, screenHeight, this);
            } finally {
                // Always unlock, even if rendering fails
                tooltipRendering = false;
                currentTooltipWidget = null;
            }
        } else if (tooltipRendering && currentTooltipWidget == this && building == null) {
            // Widget was invalidated during rendering - clear tooltip state immediately
            clearTooltipState();
        }
        
        // Render scrollbar if needed
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = x + width - 6;
            int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
            int scrollbarY = y + (int)((scrollAmount / (float)maxScroll) * (height - scrollbarHeight));
            context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
    }
    
    @Override
    protected void renderBackground(DrawContext context) {
        // Override to prevent default background rendering
    }
    
    /**
     * A single entry in the output list.
     */
    public static class OutputEntry extends AlwaysSelectedEntryListWidget.Entry<OutputEntry> {
        private final net.minecraft.item.Item item;
        private final int weight;
        private final int minCount;
        private final int maxCount;
        private final double itemsPerMinute;
        private final String customMessage;
        private final CropStatistics cropStats; // For farm crop entries
        private final int totalWeight; // For probability calculation
        private final double probability; // Calculated probability
        
        public OutputEntry(net.minecraft.item.Item item, int weight, int minCount, int maxCount, 
                          double itemsPerMinute, String customMessage) {
            this(item, weight, minCount, maxCount, itemsPerMinute, customMessage, null, 0, 0.0);
        }
        
        public OutputEntry(net.minecraft.item.Item item, int weight, int minCount, int maxCount, 
                          double itemsPerMinute, String customMessage, CropStatistics cropStats,
                          int totalWeight, double probability) {
            this.item = item;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.itemsPerMinute = itemsPerMinute;
            this.customMessage = customMessage;
            this.cropStats = cropStats;
            this.totalWeight = totalWeight;
            this.probability = probability;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, 
                          int mouseX, int mouseY, boolean hovered, float tickDelta) {
            MinecraftClient client = MinecraftClient.getInstance();
            
            // Determine background color based on crop maturity (if crop entry)
            int backgroundColor = 0;
            if (cropStats != null) {
                double maturityPercent = cropStats.getMaturityPercentage();
                if (maturityPercent >= 0.7) {
                    // Mostly mature - green tint
                    backgroundColor = hovered ? 0x44AAFF44 : 0x22AAFF44;
                } else if (maturityPercent >= 0.3) {
                    // Partially mature - yellow tint
                    backgroundColor = hovered ? 0x44FFFF44 : 0x22FFFF44;
                } else {
                    // Mostly immature - gray tint
                    backgroundColor = hovered ? 0x44AAAAAA : 0x22AAAAAA;
                }
            } else if (hovered) {
                // Default hover background
                backgroundColor = 0x33FFFFFF;
            }
            
            // Draw background
            if (backgroundColor != 0) {
                context.fill(x, y, x + entryWidth, y + entryHeight, backgroundColor);
            }
            
            // Handle custom message
            if (customMessage != null) {
                // Determine text color based on crop maturity
                int textColor = 0xAAAAAA;
                if (cropStats != null) {
                    double maturityPercent = cropStats.getMaturityPercentage();
                    if (maturityPercent >= 0.7) {
                        textColor = 0xAAFFAA; // Light green
                    } else if (maturityPercent >= 0.3) {
                        textColor = 0xFFFFAA; // Light yellow
                    } else {
                        textColor = 0xAAAAAA; // Gray
                    }
                }
                
                context.drawText(
                    client.textRenderer,
                    Text.literal(customMessage),
                    x + 5,
                    y + 3,
                    textColor,
                    false
                );
                
                // Draw progress bar for crop entries
                if (cropStats != null) {
                    drawCropProgressBar(context, x, y, entryWidth, entryHeight);
                }
                return;
            }
            
            // Get item name
            String itemName = item != null ? item.getName().getString() : "Unknown";
            
            // Draw item icon (small, 16x16)
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                context.drawItem(stack, x + 5, y + 1);
            }
            
            // Draw item name and count range
            String countText = minCount == maxCount 
                ? String.format("%d", minCount)
                : String.format("%d-%d", minCount, maxCount);
            String itemText = String.format("%s (%s)", itemName, countText);
            context.drawText(
                client.textRenderer,
                Text.literal(itemText),
                x + 25, // After icon
                y + 3,
                0xFFFFFF,
                false
            );
            
            // Draw items per minute
            String rateText = String.format("%.2f/min", itemsPerMinute);
            int rateTextWidth = client.textRenderer.getWidth(rateText);
            context.drawText(
                client.textRenderer,
                Text.literal(rateText),
                x + entryWidth - rateTextWidth - 5,
                y + 3,
                0x00FF00, // Green color for rate
                false
            );
            
            // Draw weight/probability on second line
            String weightText = String.format("Weight: %d", weight);
            context.drawText(
                client.textRenderer,
                Text.literal(weightText),
                x + 25,
                y + 13,
                0xCCCCCC,
                false
            );
        }
        
        /**
         * Renders a tooltip when the entry is hovered.
         * CRITICAL: This method should only be called when tooltipRendering is true and currentTooltipWidget matches.
         */
        public void renderTooltip(DrawContext context, int mouseX, int mouseY, int screenWidth, int screenHeight, BuildingOutputWidget parentWidget) {
            // Double-check that we're the widget that should be rendering
            // Only render if we're the current tooltip widget and the parent matches
            if (!tooltipRendering || currentTooltipWidget == null || currentTooltipWidget != parentWidget) {
                return; // Another widget is rendering or no tooltip should be shown, skip
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            java.util.List<net.minecraft.text.Text> tooltipLines = new java.util.ArrayList<>();
            
            // Build tooltip based on entry type
            if (cropStats != null) {
                // Crop statistics tooltip - with better formatting and colors
                tooltipLines.add(Text.literal("§6§l" + capitalizeFirst(cropStats.cropType) + " Statistics"));
                tooltipLines.add(Text.literal("§8" + cropStats.cropItemId.toString()));
                tooltipLines.add(Text.empty());
                
                tooltipLines.add(Text.literal("§bTotal Crops: §e" + cropStats.totalCount));
                tooltipLines.add(Text.literal("§aMature: §2" + cropStats.matureCount + " §7| §eImmature: §6" + cropStats.immatureCount));
                tooltipLines.add(Text.literal("§bAverage Age: §e" + cropStats.averageAge + "§7/§e" + cropStats.maxAge));
                tooltipLines.add(Text.empty());
                
                // Age distribution
                if (!cropStats.ageDistribution.isEmpty()) {
                    tooltipLines.add(Text.literal("§7Age Distribution:"));
                    for (java.util.Map.Entry<Integer, Integer> ageEntry : cropStats.ageDistribution.entrySet()) {
                        int age = ageEntry.getKey();
                        int count = ageEntry.getValue();
                        String ageColor = age >= cropStats.maxAge ? "§a" : (age >= cropStats.maxAge * 0.7 ? "§e" : "§7");
                        tooltipLines.add(Text.literal("  " + ageColor + "Age " + age + ": §f" + count));
                    }
                    tooltipLines.add(Text.empty());
                }
                
                // Maturity and harvest info
                double maturityPercent = cropStats.getMaturityPercentage() * 100.0;
                String maturityColor = maturityPercent >= 70 ? "§a" : (maturityPercent >= 30 ? "§e" : "§7");
                tooltipLines.add(Text.literal("§bMaturity: " + maturityColor + String.format("%.1f", maturityPercent) + "%"));
                
                if (cropStats.immatureCount > 0 && cropStats.estimatedTicksUntilHarvest > 0) {
                    double minutesUntilHarvest = cropStats.getEstimatedMinutesUntilHarvest();
                    tooltipLines.add(Text.literal("§bEst. Harvest Time: §e" + String.format("%.1f", minutesUntilHarvest) + " min"));
                } else if (cropStats.matureCount > 0) {
                    tooltipLines.add(Text.literal("§a§lReady to harvest now!"));
                }
                
                tooltipLines.add(Text.empty());
                tooltipLines.add(Text.literal("§bAvg Drops/Crop: §e" + String.format("%.1f", cropStats.avgDropsPerCrop)));
                tooltipLines.add(Text.literal("§bExpected/Harvest: §e" + String.format("%.1f", cropStats.expectedItemsPerHarvest)));
                tooltipLines.add(Text.literal("§bItems/Minute: §a" + String.format("%.1f", itemsPerMinute)));
                
            } else if (item != null && weight > 0) {
                // Regular output item tooltip - with better formatting and colors
                tooltipLines.add(Text.literal("§6§l" + item.getName().getString()));
                tooltipLines.add(Text.literal("§8" + net.minecraft.registry.Registries.ITEM.getId(item).toString()));
                tooltipLines.add(Text.empty());
                
                // Drop rate information
                if (totalWeight > 0 && probability > 0) {
                    tooltipLines.add(Text.literal("§bDrop Weight: §e" + weight));
                    String probColor = probability >= 0.3 ? "§a" : (probability >= 0.1 ? "§e" : "§7");
                    tooltipLines.add(Text.literal("§bProbability: " + probColor + String.format("%.1f", probability * 100.0) + "%"));
                    tooltipLines.add(Text.literal("§7(Weight / Total: " + weight + "/" + totalWeight + ")"));
                    tooltipLines.add(Text.empty());
                }
                
                // Count information
                if (minCount == maxCount) {
                    tooltipLines.add(Text.literal("§bDrops: §e" + minCount + " item" + (minCount != 1 ? "s" : "")));
                } else {
                    tooltipLines.add(Text.literal("§bDrops: §e" + minCount + "-" + maxCount + " items"));
                    double avgCount = (minCount + maxCount) / 2.0;
                    tooltipLines.add(Text.literal("§7Average: §e" + String.format("%.1f", avgCount) + " items"));
                }
                tooltipLines.add(Text.empty());
                
                // Calculation breakdown
                if (totalWeight > 0 && probability > 0) {
                    double avgCount = (minCount + maxCount) / 2.0;
                    double expectedPerTask = avgCount * probability;
                    tooltipLines.add(Text.literal("§7Calculation Breakdown:"));
                    tooltipLines.add(Text.literal("§7  Avg Count: §f" + String.format("%.1f", avgCount)));
                    tooltipLines.add(Text.literal("§7  × Probability: §f" + String.format("%.1f%%", probability * 100.0)));
                    tooltipLines.add(Text.literal("§7  = Expected/Task: §e" + String.format("%.2f", expectedPerTask)));
                    tooltipLines.add(Text.literal("§7  × Tasks/Min: §f6.0"));
                    tooltipLines.add(Text.literal("§7  = Items/Min: §a" + String.format("%.1f", itemsPerMinute)));
                }
            } else if (customMessage != null) {
                // Custom message tooltip (for info entries)
                tooltipLines.add(Text.literal("§7" + customMessage));
            }
            
            // Render tooltip
            if (!tooltipLines.isEmpty()) {
                context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
            }
        }
        
        /**
         * Draws a progress bar showing crop maturity progress.
         */
        private void drawCropProgressBar(DrawContext context, int x, int y, int entryWidth, int entryHeight) {
            if (cropStats == null) return;
            
            double maturityPercent = cropStats.getMaturityPercentage();
            int barWidth = entryWidth - 10;
            int barHeight = 2;
            int barX = x + 5;
            int barY = y + entryHeight - 4;
            
            // Draw background bar (gray)
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
            
            // Draw progress bar (color based on maturity)
            int filledWidth = (int)(barWidth * maturityPercent);
            int progressColor;
            if (maturityPercent >= 0.7) {
                progressColor = 0xFF00FF00; // Green
            } else if (maturityPercent >= 0.3) {
                progressColor = 0xFFFFFF00; // Yellow
            } else {
                progressColor = 0xFFAAAAAA; // Gray
            }
            
            if (filledWidth > 0) {
                context.fill(barX, barY, barX + filledWidth, barY + barHeight, progressColor);
            }
        }
        
        /**
         * Capitalizes the first letter of a string.
         */
        private String capitalizeFirst(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        
        @Override
        public Text getNarration() {
            if (customMessage != null) {
                return Text.literal(customMessage);
            }
            return Text.literal(item != null ? item.getName().getString() : "Unknown item");
        }
        
        public net.minecraft.item.Item getItem() {
            return item;
        }
        
        public int getWeight() {
            return weight;
        }
        
        public double getItemsPerMinute() {
            return itemsPerMinute;
        }
    }
}

