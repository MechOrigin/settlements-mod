package com.secretasain.settlements.ui;

import com.secretasain.settlements.network.ActivateBuildModePacketClient;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.VillagerData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main UI screen for settlement management.
 * Opens when a player right-clicks on a lectern block.
 */
public class SettlementScreen extends Screen {
    private Settlement settlement;
    private TabType activeTab = TabType.OVERVIEW;
    
    // Tab buttons
    private ButtonWidget overviewTabButton;
    private ButtonWidget buildingsTabButton;
    private ButtonWidget villagersTabButton;
    private ButtonWidget settingsTabButton;
    
    // Villager list widget
    private VillagerListWidget villagerListWidget;
    
    // Structure list widget (for selecting structures to build)
    private StructureListWidget structureListWidget;
    private ButtonWidget buildStructureButton;
    
    // Building list widget (for viewing existing buildings)
    private BuildingListWidget buildingListWidget;
    
    // Building selection widget (for work assignment)
    private BuildingSelectionWidget buildingSelectionWidget;
    private boolean showingBuildingSelection = false;
    
    // Building action buttons
    private ButtonWidget cancelBuildingButton;
    private ButtonWidget removeBuildingButton;
    private ButtonWidget startBuildingButton;
    private ButtonWidget checkMaterialsButton;
    private ButtonWidget unloadInventoryButton;
    private ButtonWidget refreshVillagersButton;
    
    // Material display widget
    private MaterialListWidget materialListWidget;
    private com.secretasain.settlements.settlement.Building lastSelectedBuilding; // Track selection to avoid recreating widget every frame
    
    // Configurable material list widget position and size
    // Default to a larger, more visible size
    private int materialListX = -1; // -1 means use default
    private int materialListY = -1; // -1 means use default
    private int materialListWidth = 200; // Default width - larger for better visibility
    private int materialListHeight = 180; // Default height - larger for better visibility

    public SettlementScreen(Settlement settlement) {
        super(Text.translatable("settlements.ui.title"));
        this.settlement = settlement;
    }

    @Override
    protected void init() {
        super.init();
        
        // Screen dimensions: Larger size for better visibility
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Create tab buttons - better spacing for larger screen
        int tabButtonWidth = 90;
        int tabButtonHeight = 20;
        int tabY = y + 5;
        int tabSpacing = 5; // Space between buttons
        
        this.overviewTabButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.tab.overview"),
            button -> switchTab(TabType.OVERVIEW)
        ).dimensions(x + 5, tabY, tabButtonWidth, tabButtonHeight).build();
        
        this.buildingsTabButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.tab.buildings"),
            button -> switchTab(TabType.BUILDINGS)
        ).dimensions(x + 5 + tabButtonWidth + tabSpacing, tabY, tabButtonWidth, tabButtonHeight).build();
        
        this.villagersTabButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.tab.villagers"),
            button -> switchTab(TabType.VILLAGERS)
        ).dimensions(x + 5 + (tabButtonWidth + tabSpacing) * 2, tabY, tabButtonWidth, tabButtonHeight).build();
        
        this.settingsTabButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.tab.settings"),
            button -> switchTab(TabType.SETTINGS)
        ).dimensions(x + 5 + (tabButtonWidth + tabSpacing) * 3, tabY, tabButtonWidth, tabButtonHeight).build();
        
        this.addDrawableChild(overviewTabButton);
        this.addDrawableChild(buildingsTabButton);
        this.addDrawableChild(villagersTabButton);
        this.addDrawableChild(settingsTabButton);
        
        // Create villager list widget (initially hidden)
        // Position it below the info section - same calculation as in render()
        int infoY = y + 28;
        int lineHeight = 14;
        int tabContentY = infoY + lineHeight * 4 + 8;
        int listY = tabContentY + 5; // Start below tab content title
        int listWidth = screenWidth - 10;
        int listHeight = (y + screenHeight - 5) - listY; // Fill remaining space
        this.villagerListWidget = new VillagerListWidget(
            this.client,
            listWidth,
            listHeight,
            listY,
            y + screenHeight - 5,
            50, // Increased item height to prevent overlapping text
            settlement.getVillagers()
        );
        // Set hire/fire callbacks
        villagerListWidget.setOnHireCallback(villager -> {
            com.secretasain.settlements.network.HireFireVillagerPacketClient.send(
                villager.getEntityId(), 
                settlement.getId(), 
                true
            );
            // Refresh the list after a short delay to get updated data
            this.client.execute(() -> {
                villagerListWidget.updateEntries();
            });
        });
        villagerListWidget.setOnFireCallback(villager -> {
            com.secretasain.settlements.network.HireFireVillagerPacketClient.send(
                villager.getEntityId(), 
                settlement.getId(), 
                false
            );
            // Refresh the list after a short delay to get updated data
            this.client.execute(() -> {
                villagerListWidget.updateEntries();
            });
        });
        villagerListWidget.setOnAssignWorkCallback((villager, buildingId) -> {
            if (villager.isAssigned() && buildingId != null) {
                // Unassign from current building
                com.secretasain.settlements.network.AssignWorkPacketClient.send(
                    settlement.getId(),
                    villager.getEntityId(),
                    buildingId, // Current building ID
                    false
                );
                // Refresh the list after a short delay to get updated data
                this.client.execute(() -> {
                    villagerListWidget.updateEntries();
                });
            } else if (!villager.isAssigned() && buildingId == null) {
                // Show building selection dialog
                showBuildingSelectionDialog(villager);
            }
        });
        this.addDrawableChild(villagerListWidget);
        
        // Structure list widget will be created dynamically when Buildings tab is active
        // Don't create it here to avoid any rendering issues
        this.structureListWidget = null;
        
        // Building list widget will be created dynamically when Buildings tab is active
        this.buildingListWidget = null;
        
        // Create "Build Structure" button - will be positioned when sidebar is created
        // Position it off-screen initially
        this.buildStructureButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.buildings.build_button"),
            button -> {
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Build Structure button clicked! Button handler called.");
                onBuildStructureClicked();
            }
        ).dimensions(-1000, -1000, 100, 20).build(); // Off-screen initially
        this.addDrawableChild(buildStructureButton);
        
        // Initially hide build button - it will be shown when Buildings tab is active
        if (buildStructureButton != null) {
            buildStructureButton.visible = false;
            buildStructureButton.active = false; // Also disable it initially
        }
        
        // Create cancel/remove building buttons - positioned off-screen initially
        this.cancelBuildingButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.buildings.cancel"),
            button -> onCancelBuildingClicked()
        ).dimensions(-1000, -1000, 80, 20).build();
        this.addDrawableChild(cancelBuildingButton);
        this.cancelBuildingButton.visible = false;
        this.cancelBuildingButton.active = false;
        
        this.removeBuildingButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.buildings.remove"),
            button -> onRemoveBuildingClicked()
        ).dimensions(-1000, -1000, 80, 20).build();
        this.addDrawableChild(removeBuildingButton);
        this.removeBuildingButton.visible = false;
        this.removeBuildingButton.active = false;
        
        // Create start building button
        this.startBuildingButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.buildings.start"),
            button -> onStartBuildingClicked()
        ).dimensions(-1000, -1000, 100, 20).build();
        this.addDrawableChild(startBuildingButton);
        this.startBuildingButton.visible = false;
        this.startBuildingButton.active = false;
        
        // Create check materials button
        this.checkMaterialsButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.buildings.check_materials"),
            button -> onCheckMaterialsClicked()
        ).dimensions(-1000, -1000, 140, 20).build();
        this.addDrawableChild(checkMaterialsButton);
        this.checkMaterialsButton.visible = false;
        this.checkMaterialsButton.active = false;
        
        // Create unload inventory button - positioned off-screen initially
        this.unloadInventoryButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.buildings.unload_inventory"),
            button -> onUnloadInventoryClicked()
        ).dimensions(-1000, -1000, 120, 20).build();
        this.addDrawableChild(unloadInventoryButton);
        this.unloadInventoryButton.visible = false;
        this.unloadInventoryButton.active = false;
        
        // Create refresh villagers button
        this.refreshVillagersButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.villagers.refresh"),
            button -> {
                // Trigger villager scan by requesting settlement data refresh
                // The server will scan and update villager data
                if (settlement != null) {
                    // Request settlement data sync (this will trigger a scan)
                    // For now, just refresh the UI list - actual scan happens server-side periodically
                    villagerListWidget.updateEntries();
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(
                            Text.translatable("settlements.ui.villagers.refresh"), 
                            false
                        );
                    }
                }
            }
        ).dimensions(-1000, -1000, 100, 20).build();
        this.addDrawableChild(refreshVillagersButton);
        this.refreshVillagersButton.visible = false;
        this.refreshVillagersButton.active = false;
        
        // Material list widget will be created dynamically when Buildings tab is active
        this.materialListWidget = null;
        
        // Ensure structure widget is null and not in children list
        // Widget will only be created when Buildings tab is clicked
        if (structureListWidget != null) {
            if (this.children().contains(structureListWidget)) {
                this.remove(structureListWidget);
            }
            structureListWidget = null; // Ensure it's null
        }
        
        updateTabButtons();
    }
    
    // Cache discovered structure resources
    private Map<String, Identifier> structureResourceIds = new HashMap<>();
    
    /**
     * Discovers available structure files from resources.
     * Clears the structure cache to ensure new/modified files are detected.
     * Note: Client-side discovery may not work reliably for data files.
     * For production, consider using server-side discovery via network packets.
     */
    private void discoverStructures() {
        // Clear structure cache to ensure new/modified files are picked up
        com.secretasain.settlements.building.ClientStructureLoader.clearCache();
        
        List<String> structures = new ArrayList<>();
        structureResourceIds.clear(); // Clear previous cache
        
        try {
            ResourceManager resourceManager = this.client.getResourceManager();
            
            // Try multiple search patterns to find structure files
            java.util.Set<Identifier> allFoundIds = new java.util.HashSet<>();
            
            // Pattern 1: Search in "settlements/structures" path
            try {
                Map<Identifier, Resource> structureResources1 = resourceManager.findResources(
                    "settlements/structures",
                    path -> path.getPath().endsWith(".nbt")
                );
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Pattern 1 (settlements/structures): found {} resources", structureResources1.size());
                allFoundIds.addAll(structureResources1.keySet());
            } catch (Exception e) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Pattern 1 failed: {}", e.getMessage());
            }
            
            // Pattern 2: Search in "data/settlements/structures" path
            try {
                Map<Identifier, Resource> structureResources2 = resourceManager.findResources(
                    "data/settlements/structures",
                    path -> path.getPath().endsWith(".nbt")
                );
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Pattern 2 (data/settlements/structures): found {} resources", structureResources2.size());
                allFoundIds.addAll(structureResources2.keySet());
            } catch (Exception e) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Pattern 2 failed: {}", e.getMessage());
            }
            
            // Pattern 3: Search for any .nbt files in structures folder
            try {
                Map<Identifier, Resource> structureResources3 = resourceManager.findResources(
                    "structures",
                    path -> path.getPath().endsWith(".nbt") && path.getPath().contains("settlements")
                );
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Pattern 3 (structures with settlements): found {} resources", structureResources3.size());
                allFoundIds.addAll(structureResources3.keySet());
            } catch (Exception e) {
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Pattern 3 failed: {}", e.getMessage());
            }
            
            // Pattern 4: Try direct identifier lookup for known structures
            String[] knownStructures = {
                "lvl1_oak_wall.nbt", "lvl2_oak_wall.nbt", "lvl3_oak_wall.nbt",
                "lvl1_oak_cartographer.nbt", "lvl1_oak_farm.nbt", "lvl1_oak_fence.nbt",
                "lvl1_oak_gate.nbt", "lvl1_oak_smithing.nbt",
                "lvl1_oak_house.nbt", "lvl2_oak_house.nbt", "lvl3_oak_house.nbt"
            };
            for (String known : knownStructures) {
                try {
                    Identifier directId = new Identifier("settlements", "structures/" + known);
                    List<Resource> directResources = resourceManager.getAllResources(directId);
                    if (!directResources.isEmpty()) {
                        com.secretasain.settlements.SettlementsMod.LOGGER.info("Pattern 4 (direct lookup): found {}", directId);
                        allFoundIds.add(directId);
                    } else {
                        com.secretasain.settlements.SettlementsMod.LOGGER.debug("Pattern 4 (direct lookup): {} not found via getAllResources", directId);
                    }
                } catch (Exception e) {
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("Pattern 4 (direct lookup): exception for {}: {}", known, e.getMessage());
                }
            }
            
            // Log all found identifiers for debugging
            if (allFoundIds.isEmpty()) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("No structures found with any pattern! This might indicate:");
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("  1. Resources need to be rebuilt (run 'gradlew build')");
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("  2. Client ResourceManager doesn't have access to data files");
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("  3. Files are not in the correct location");
            } else {
                com.secretasain.settlements.SettlementsMod.LOGGER.info("All found identifiers: {}", allFoundIds);
            }
            
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Client-side discovery found {} total unique structure resources", allFoundIds.size());
            
            // Process all found identifiers
            for (Identifier id : allFoundIds) {
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Processing structure identifier: {} (namespace: {}, path: {})", 
                    id, id.getNamespace(), id.getPath());
                
                // Accept structures from "settlements" namespace or any structure in structures/ path
                boolean isValid = false;
                String path = id.getPath();
                
                if ("settlements".equals(id.getNamespace()) && path.startsWith("structures/")) {
                    isValid = true;
                } else if (path.contains("settlements/structures/") || path.contains("data/settlements/structures/")) {
                    isValid = true;
                }
                
                if (isValid) {
                    // Extract just the filename
                    String filename;
                    if (path.contains("/")) {
                        filename = path.substring(path.lastIndexOf('/') + 1);
                    } else {
                        filename = path;
                    }
                    
                    // Only add if it ends with .nbt
                    if (filename.endsWith(".nbt")) {
                        structures.add(filename);
                        // Cache the identifier for this filename
                        structureResourceIds.put(filename, id);
                        com.secretasain.settlements.SettlementsMod.LOGGER.info("Added structure: {} -> {}", filename, id);
                    }
                }
            }
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.error("Error discovering structures on client", e);
            e.printStackTrace();
        }
        
        // If no structures found, add known ones as fallback
        // This ensures structures are available even if discovery fails
        if (structures.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("No structures discovered via ResourceManager, using fallback list");
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("This is normal if client-side discovery doesn't work for data files");
            structures.add("lvl1_oak_wall.nbt");
            structures.add("lvl2_oak_wall.nbt");
            structures.add("lvl3_oak_wall.nbt");
            
            // Create identifiers for fallback structures
            for (String fallback : structures) {
                Identifier fallbackId = new Identifier("settlements", "structures/" + fallback);
                structureResourceIds.put(fallback, fallbackId);
            }
        } else {
            // Even if we found some, add known ones that might be missing
            // This ensures all structures are available
            String[] allKnown = {"lvl1_oak_wall.nbt", "lvl2_oak_wall.nbt", "lvl3_oak_wall.nbt"};
            for (String known : allKnown) {
                if (!structures.contains(known)) {
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("Adding missing known structure to list: {}", known);
                    structures.add(known);
                    Identifier knownId = new Identifier("settlements", "structures/" + known);
                    structureResourceIds.put(known, knownId);
                }
            }
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Discovered {} structures: {}", structures.size(), structures);
        
        if (structureListWidget != null) {
            structureListWidget.setStructures(structures);
            
            // Auto-select first structure after setting the list
            if (!structureListWidget.children().isEmpty()) {
                structureListWidget.setSelected(structureListWidget.children().get(0));
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Auto-selected structure after discovery: {}", 
                    structureListWidget.getSelectedStructure());
            }
        }
    }
    
    /**
     * Called when the "Build Structure" button is clicked.
     */
    private void onBuildStructureClicked() {
        if (structureListWidget == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Build Structure button clicked but structureListWidget is null");
            return;
        }
        
        String selectedStructure = structureListWidget.getSelectedStructure();
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Build Structure button clicked. Selected structure: {}", selectedStructure);
        
        if (selectedStructure == null) {
            // Show message to player
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.literal("Please select a structure first!"), 
                    false
                );
            }
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("No structure selected when Build Structure button was clicked");
            return;
        }
        
        // Use cached resource identifier if available, otherwise construct one
        Identifier resourceId = structureResourceIds.get(selectedStructure);
        String structureIdentifier;
        
        if (resourceId != null) {
            // Use the cached identifier from discovery
            structureIdentifier = resourceId.toString();
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Using cached resource identifier: {} for structure: {}", structureIdentifier, selectedStructure);
        } else {
            // Fallback: construct identifier (shouldn't happen if discovery worked)
            if (selectedStructure.endsWith(".nbt")) {
                structureIdentifier = "settlements:structures/" + selectedStructure;
            } else {
                structureIdentifier = "settlements:structures/" + selectedStructure + ".nbt";
            }
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("No cached identifier found for {}, using constructed: {}", selectedStructure, structureIdentifier);
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Sending build mode activation for structure: {}", structureIdentifier);
        
        // Send network packet to server to activate build mode
        ActivateBuildModePacketClient.send(structureIdentifier);
        
        // Close the screen
        this.client.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // CRITICAL: Check button FIRST before other widgets
        // This ensures button clicks are handled even if widget is on top
        if (buildStructureButton != null && 
            buildStructureButton.visible && 
            buildStructureButton.active &&
            buildStructureButton.isMouseOver(mouseX, mouseY)) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Mouse clicked on Build Structure button at ({}, {}), button bounds: ({}, {}) to ({}, {})", 
                mouseX, mouseY, 
                buildStructureButton.getX(), buildStructureButton.getY(),
                buildStructureButton.getX() + buildStructureButton.getWidth(), 
                buildStructureButton.getY() + buildStructureButton.getHeight());
            // Manually trigger button click
            if (buildStructureButton.mouseClicked(mouseX, mouseY, button)) {
                return true; // Button handled the click
            }
        }
        // Then check other widgets
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't call renderBackground() - we'll draw our own custom background
        // This prevents the default Minecraft dirt texture background from showing
        
        // Screen dimensions
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Draw background rectangle (will be replaced with texture later)
        context.fill(x, y, x + screenWidth, y + screenHeight, 0xC0101010);
        context.drawBorder(x, y, screenWidth, screenHeight, 0xFF404040);
        
        // Draw settlement information - better spacing
        int infoY = y + 28; // Start below tab buttons
        int lineHeight = 14; // Proper line spacing
        
        context.drawText(
            this.textRenderer, 
            Text.translatable("settlements.ui.info.settlement", settlement.getName()), 
            x + 10, 
            infoY, 
            0xFFFFFF, 
            false
        );
        context.drawText(
            this.textRenderer, 
            Text.translatable("settlements.ui.info.villagers", settlement.getVillagers().size()), 
            x + 10, 
            infoY + lineHeight, 
            0xFFFFFF, 
            false
        );
        context.drawText(
            this.textRenderer, 
            Text.translatable("settlements.ui.info.buildings", settlement.getBuildings().size()), 
            x + 10, 
            infoY + lineHeight * 2, 
            0xFFFFFF, 
            false
        );
        context.drawText(
            this.textRenderer, 
            Text.translatable("settlements.ui.info.radius", settlement.getRadius()), 
            x + 10, 
            infoY + lineHeight * 3, 
            0xFFFFFF, 
            false
        );
        
        // Draw tab content based on active tab - start below info section
        drawTabContent(context, x, infoY + lineHeight * 4 + 8);
        
        // Draw building selection dialog background if shown (BEFORE super.render so widget appears on top)
        if (showingBuildingSelection && buildingSelectionWidget != null) {
            renderBuildingSelectionDialogBackground(context);
        }
        
        // CRITICAL: Remove widget from children BEFORE super.render() if it shouldn't be visible
        // This prevents Screen from rendering it even if it's in the children list
        boolean widgetShouldBeVisible = (activeTab == TabType.BUILDINGS) && 
                                       (structureListWidget != null) && 
                                       (structureListWidget.isVisible());
        
        // CRITICAL: Remove widget from children if NOT on Buildings tab
        // This prevents the empty box from appearing on the left
        if (activeTab != TabType.BUILDINGS) {
            // Remove ALL StructureListWidget instances from children IMMEDIATELY
            this.children().removeIf(child -> {
                if (child instanceof StructureListWidget) {
                    StructureListWidget widget = (StructureListWidget) child;
                    widget.setForceDisable(true);
                    widget.setVisible(false);
                    return true; // Remove it
                }
                return false;
            });
            
            // Destroy widget reference completely
            if (structureListWidget != null) {
                structureListWidget.setForceDisable(true);
                structureListWidget.setVisible(false);
                structureListWidget = null;
            }
        } else if (!widgetShouldBeVisible) {
            // Also remove if widget exists but shouldn't be visible
            this.children().removeIf(child -> child instanceof StructureListWidget);
            if (structureListWidget != null) {
                structureListWidget.setForceDisable(true);
                structureListWidget.setVisible(false);
                structureListWidget = null;
            }
        }
        
        // Render structure sidebar background FIRST (before widget)
        // ONLY render if Buildings tab is active AND widget exists AND is in children AND is visible
        if (activeTab == TabType.BUILDINGS && 
            structureListWidget != null && 
            structureListWidget.isVisible() &&
            this.children().contains(structureListWidget)) {
            renderStructureSidebar(context, x, y);
        }
        
        // Call super.render() - this will render all children INCLUDING the widget ON TOP of sidebar/dialog
        super.render(context, mouseX, mouseY, delta);
        
        // Draw building selection dialog text if shown (AFTER super.render so text appears on top)
        if (showingBuildingSelection && buildingSelectionWidget != null) {
            renderBuildingSelectionDialogText(context);
        }
        
        // Explicitly render material list widget if it exists and is in children
        if (activeTab == TabType.BUILDINGS && materialListWidget != null && this.children().contains(materialListWidget)) {
            materialListWidget.render(context, mouseX, mouseY, delta);
        }
        
        // Re-add widget to children AFTER render if it should be visible (for next frame)
        // BUT ONLY if we're actually on Buildings tab
        if (activeTab == TabType.BUILDINGS && 
            widgetShouldBeVisible && 
            structureListWidget != null && 
            !this.children().contains(structureListWidget)) {
            this.addDrawableChild(structureListWidget);
        }
    }

    private void drawTabContent(DrawContext context, int x, int y) {
        int lineHeight = 14;
        
        switch (activeTab) {
            case OVERVIEW:
                // Overview tab - display settlement information
                int overviewY = y;
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.overview.title"), 
                    x + 10, 
                    overviewY, 
                    0xFFFFFF, 
                    false
                );
                overviewY += lineHeight + 5;
                
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.overview.welcome", settlement.getName()), 
                    x + 10, 
                    overviewY, 
                    0xCCCCCC, 
                    false
                );
                overviewY += lineHeight + 5;
                
                // Display settlement level
                com.secretasain.settlements.settlement.SettlementLevel level = settlement.getSettlementLevel();
                context.drawText(
                    this.textRenderer,
                    Text.translatable("settlements.ui.level", level.getDisplayText()),
                    x + 10,
                    overviewY,
                    0xFFFF00,
                    false
                );
                overviewY += lineHeight;
                
                // Display level progress/requirements if not max level
                com.secretasain.settlements.settlement.SettlementLevel nextLevel = settlement.getNextLevel();
                if (nextLevel != null) {
                    int villagerCount = settlement.getVillagers().size();
                    int buildingCount = (int) settlement.getBuildings().stream()
                        .filter(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED)
                        .count();
                    int employedCount = (int) settlement.getVillagers().stream()
                        .filter(com.secretasain.settlements.settlement.VillagerData::isEmployed)
                        .count();
                    
                    int reqVillagers = nextLevel.getRequiredVillagers();
                    int reqBuildings = nextLevel.getRequiredBuildings();
                    int reqEmployed = nextLevel.getRequiredEmployedVillagers();
                    
                    int villagerColor = villagerCount >= reqVillagers ? 0x00FF00 : 0xCCCCCC;
                    int buildingColor = buildingCount >= reqBuildings ? 0x00FF00 : 0xCCCCCC;
                    int employedColor = employedCount >= reqEmployed ? 0x00FF00 : 0xCCCCCC;
                    
                    context.drawText(
                        this.textRenderer,
                        Text.translatable("settlements.ui.level.villagers", villagerCount, reqVillagers),
                        x + 15,
                        overviewY,
                        villagerColor,
                        false
                    );
                    overviewY += lineHeight - 2;
                    
                    context.drawText(
                        this.textRenderer,
                        Text.translatable("settlements.ui.level.buildings", buildingCount, reqBuildings),
                        x + 15,
                        overviewY,
                        buildingColor,
                        false
                    );
                    overviewY += lineHeight - 2;
                    
                    context.drawText(
                        this.textRenderer,
                        Text.translatable("settlements.ui.level.employed", employedCount, reqEmployed),
                        x + 15,
                        overviewY,
                        employedColor,
                        false
                    );
                    overviewY += lineHeight;
                } else {
                    // Max level reached
                    context.drawText(
                        this.textRenderer,
                        Text.translatable("settlements.ui.level.max"),
                        x + 10,
                        overviewY,
                        0x00FF00,
                        false
                    );
                    overviewY += lineHeight;
                }
                
                // Display settlement information
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.info.settlement", settlement.getName()), 
                    x + 10, 
                    overviewY, 
                    0xCCCCCC, 
                    false
                );
                overviewY += lineHeight;
                
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.info.villagers", settlement.getVillagers().size()), 
                    x + 10, 
                    overviewY, 
                    0xCCCCCC, 
                    false
                );
                overviewY += lineHeight;
                
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.info.buildings", settlement.getBuildings().size()), 
                    x + 10, 
                    overviewY, 
                    0xCCCCCC, 
                    false
                );
                overviewY += lineHeight;
                
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.info.radius", settlement.getRadius()), 
                    x + 10, 
                    overviewY, 
                    0xCCCCCC, 
                    false
                );
                overviewY += lineHeight + 5;
                
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.overview.description"), 
                    x + 10, 
                    overviewY, 
                    0xAAAAAA, 
                    false
                );
                break;
            case BUILDINGS:
                // Buildings tab - structure list sidebar is shown separately
                
                // Draw title in main content area
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.buildings.title"), 
                    x + 10, 
                    y, 
                    0xFFFFFF, 
                    false
                );
                
                // Show building list if there are buildings
                if (buildingListWidget != null && this.children().contains(buildingListWidget)) {
                    buildingListWidget.updateEntries(); // Refresh list
                    updateBuildingActionButtons(); // Update buttons based on selection
                    
                    // CRITICAL FIX: Don't recreate material list widget in render loop
                    // The selection callback handles this to prevent duplicates
                    // Only update entries if widget exists (materials might have changed)
                    if (materialListWidget != null && this.children().contains(materialListWidget)) {
                        materialListWidget.updateEntries();
                    }
                    
                    if (settlement.getBuildings().isEmpty()) {
                        // Show empty message
                        context.drawText(
                            this.textRenderer, 
                            Text.translatable("settlements.ui.buildings.none"), 
                            x + 10, 
                            y + lineHeight, 
                            0xCCCCCC, 
                            false
                        );
                        context.drawText(
                            this.textRenderer, 
                            Text.translatable("settlements.ui.buildings.select_structure"), 
                            x + 10, 
                            y + lineHeight * 2, 
                            0xAAAAAA, 
                            false
                        );
                    }
                } else {
                    // Show instructions if building list not yet created
                    context.drawText(
                        this.textRenderer, 
                        Text.translatable("settlements.ui.buildings.select_structure"), 
                        x + 10, 
                        y + lineHeight, 
                        0xCCCCCC, 
                        false
                    );
                }
                break;
            case VILLAGERS:
                // Draw title
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.villagers.title"), 
                    x + 10, 
                    y, 
                    0xFFFFFF, 
                    false
                );
                // Show villager list widget
                villagerListWidget.updateEntries(); // Refresh list
                if (settlement.getVillagers().isEmpty()) {
                    // Show empty message below title
                    context.drawText(
                        this.textRenderer, 
                        Text.translatable("settlements.ui.villagers.none"), 
                        x + 10, 
                        y + lineHeight, 
                        0xCCCCCC, 
                        false
                    );
                    context.drawText(
                        this.textRenderer, 
                        Text.translatable("settlements.ui.villagers.tracking", settlement.getRadius()), 
                        x + 10, 
                        y + lineHeight * 2, 
                        0x888888, 
                        false
                    );
                    context.drawText(
                        this.textRenderer, 
                        Text.translatable("settlements.ui.villagers.tracking_desc"), 
                        x + 10, 
                        y + lineHeight * 3, 
                        0x888888, 
                        false
                    );
                }
                break;
            case SETTINGS:
                // Settings tab - widgets remain visible but not actively used
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.settings.title"), 
                    x + 10, 
                    y, 
                    0xFFFFFF, 
                    false
                );
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.ui.settings.coming_soon"), 
                    x + 10, 
                    y + lineHeight, 
                    0xCCCCCC, 
                    false
                );
                break;
        }
    }

    private void switchTab(TabType tab) {
        this.activeTab = tab;
        updateTabButtons();
        
        // Show/hide structure list sidebar based on active tab
        boolean showBuildings = (tab == TabType.BUILDINGS);
        
        if (showBuildings) {
            // Create and add structure selection widget when Buildings tab is active
            if (structureListWidget == null) {
                createStructureListWidget();
            }
            if (structureListWidget != null) {
                structureListWidget.setForceDisable(false); // Enable rendering
                structureListWidget.setVisible(true);
                structureListWidget.setAllowRendering(true);
                // Only add to children if not already there
                if (!this.children().contains(structureListWidget)) {
                    this.addDrawableChild(structureListWidget);
                }
            }
            
            // Create and add building list widget when Buildings tab is active
            if (buildingListWidget == null) {
                createBuildingListWidget();
            }
            if (buildingListWidget != null) {
                // Only add to children if not already there
                if (!this.children().contains(buildingListWidget)) {
                    this.addDrawableChild(buildingListWidget);
                }
            }
            
            // Show build button - IMPORTANT: Add button AFTER widgets so it renders on top
            if (buildStructureButton != null) {
                buildStructureButton.visible = true;
                buildStructureButton.active = true; // Ensure button is active/clickable
                // Remove and re-add button to ensure it's at the end of children list (renders on top)
                if (this.children().contains(buildStructureButton)) {
                    this.remove(buildStructureButton);
                }
                this.addDrawableChild(buildStructureButton); // Add at end so it renders on top
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Build Structure button made visible and active. Position: ({}, {})", 
                    buildStructureButton.getX(), buildStructureButton.getY());
            }
        } else if (tab == TabType.VILLAGERS) {
            // Position and show villager list widget when Villagers tab is active
            if (villagerListWidget != null) {
                int screenWidth = 400;
                int screenHeight = 280;
                int x = (this.width - screenWidth) / 2;
                int listX = x + 10; // Position from left edge
                
                // Set the X position (required for rendering - prevents early return in render())
                villagerListWidget.setLeftPos(listX);
                
                // Ensure widget is in children list
                if (!this.children().contains(villagerListWidget)) {
                    this.addDrawableChild(villagerListWidget);
                }
            }
            
            // Position refresh button
            if (refreshVillagersButton != null) {
                int screenWidth = 400;
                int screenHeight = 280;
                int x = (this.width - screenWidth) / 2;
                int y = (this.height - screenHeight) / 2;
                int buttonY = y + 30;
                int buttonX = x + screenWidth - 110; // Right side
                refreshVillagersButton.setX(buttonX);
                refreshVillagersButton.setY(buttonY);
                refreshVillagersButton.visible = true;
                refreshVillagersButton.active = true;
                if (!this.children().contains(refreshVillagersButton)) {
                    this.addDrawableChild(refreshVillagersButton);
                }
            }
            
            // Position check materials button first (unload button needs its position)
            positionCheckMaterialsButton();
            if (checkMaterialsButton != null) {
                checkMaterialsButton.visible = true;
                checkMaterialsButton.active = true;
            }
            
            // Position and show cancel/remove buttons (this also positions unload button)
            positionBuildingActionButtons();
            
            // Make unload button always visible on Buildings tab (like check materials button)
            if (unloadInventoryButton != null) {
                unloadInventoryButton.visible = true;
                unloadInventoryButton.active = true;
            }
            
            updateBuildingActionButtons();
            
            // Create and show material list widget if building is selected
            createMaterialListWidget();
        } else if (tab == TabType.VILLAGERS) {
            // Show refresh button when Villagers tab is active
            if (refreshVillagersButton != null) {
                int screenWidth = 400;
                int screenHeight = 280;
                int x = (this.width - screenWidth) / 2;
                int y = (this.height - screenHeight) / 2;
                int buttonY = y + 30;
                int buttonX = x + screenWidth - 110; // Right side
                refreshVillagersButton.setX(buttonX);
                refreshVillagersButton.setY(buttonY);
                refreshVillagersButton.visible = true;
                refreshVillagersButton.active = true;
            }
        } else {
            // Completely remove and destroy widgets when not on Buildings tab
            // CRITICAL: Do this FIRST before any other operations
            // Remove from children IMMEDIATELY
            this.children().removeIf(child -> child instanceof StructureListWidget);
            this.children().removeIf(child -> child instanceof BuildingListWidget);
            
            // Destroy widgets completely
            if (structureListWidget != null) {
                structureListWidget.setVisible(false);
                structureListWidget = null; // Destroy the widget completely
            }
            if (buildingListWidget != null) {
                buildingListWidget = null; // Destroy the widget completely
            }
            
            // Hide build button when not on Buildings tab
            if (buildStructureButton != null) {
                buildStructureButton.visible = false;
                // Move off-screen to prevent any rendering issues
                buildStructureButton.setX(-1000);
                buildStructureButton.setY(-1000);
            }
            
            // Hide cancel/remove/start buttons when not on Buildings tab
            if (cancelBuildingButton != null) {
                cancelBuildingButton.visible = false;
                cancelBuildingButton.active = false;
                cancelBuildingButton.setX(-1000);
                cancelBuildingButton.setY(-1000);
            }
            
            // Widget is already created in init() with correct position
            // Just ensure it's in children list when Villagers tab is active
            if (villagerListWidget != null && tab == TabType.VILLAGERS) {
                if (!this.children().contains(villagerListWidget)) {
                    this.addDrawableChild(villagerListWidget);
                }
            }
            
            if (refreshVillagersButton != null && tab != TabType.VILLAGERS) {
                // Hide refresh villagers button when not on Villagers tab
                refreshVillagersButton.visible = false;
                refreshVillagersButton.active = false;
                refreshVillagersButton.setX(-1000);
                refreshVillagersButton.setY(-1000);
            }
            if (removeBuildingButton != null) {
                removeBuildingButton.visible = false;
                removeBuildingButton.active = false;
                removeBuildingButton.setX(-1000);
                removeBuildingButton.setY(-1000);
            }
            if (startBuildingButton != null) {
                startBuildingButton.visible = false;
                startBuildingButton.active = false;
                startBuildingButton.setX(-1000);
                startBuildingButton.setY(-1000);
            }
            if (checkMaterialsButton != null) {
                checkMaterialsButton.visible = false;
                checkMaterialsButton.active = false;
                checkMaterialsButton.setX(-1000);
                checkMaterialsButton.setY(-1000);
            }
            if (unloadInventoryButton != null) {
                unloadInventoryButton.visible = false;
                unloadInventoryButton.active = false;
                unloadInventoryButton.setX(-1000);
                unloadInventoryButton.setY(-1000);
            }
            
            // Remove material list widget when not on Buildings tab
            this.children().removeIf(child -> child instanceof MaterialListWidget);
            if (materialListWidget != null) {
                materialListWidget = null;
            }
        }
    }
    
    /**
     * Creates the structure list widget. Called when Buildings tab is activated.
     * ONLY creates widget when Buildings tab is active.
     */
    private void createStructureListWidget() {
        // ONLY create widget if we're actually on Buildings tab
        if (activeTab != TabType.BUILDINGS) {
            return; // Don't create widget if not on Buildings tab
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        int sidebarWidth = 180;
        int sidebarX = x - sidebarWidth - 15; // 15px gap from main window
        int sidebarY = y; // Align exactly with main window top
        int sidebarHeight = screenHeight; // Match main window height exactly
        int sidebarTitleHeight = 20;
        int sidebarPadding = 5; // Padding inside sidebar
        int sidebarButtonHeight = 25; // Height for button at bottom
        int sidebarButtonPadding = 5; // Padding above button
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding; // Start below title bar with padding
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarButtonHeight - sidebarPadding - sidebarButtonPadding; // Fill remaining space (account for button and padding)
        
        // Calculate centered width for both widget and button
        int contentWidth = sidebarWidth - sidebarPadding * 2; // Width with padding on both sides
        
        this.structureListWidget = new StructureListWidget(
            this.client,
            contentWidth, // Width - same as button for perfect alignment
            sidebarListHeight, // Height of list area
            sidebarListY, // Top of list area
            sidebarListY + sidebarListHeight, // Bottom of list area (exact calculation)
            22 // Item height
        );
        // Position widget - adjust slightly to the right to fix left misalignment
        // The widget appears to be offset to the left, so we add a small adjustment
        int widgetXOffset = 22; // Adjust this value if needed (user adjusted to 22px)
        this.structureListWidget.setLeftPos(sidebarX + sidebarPadding + widgetXOffset); // Add offset to align properly
        this.structureListWidget.setVisible(true);
        this.structureListWidget.setForceDisable(false);
        
        // Position build button at bottom of sidebar - CENTERED with structure list above it
        // Button uses the SAME offset as widget to ensure perfect alignment
        if (buildStructureButton != null) {
            // Use the SAME offset as the widget for perfect alignment
            int buildStructureButtonXOffset = 0; // Always keep at 0 for perfect alignment
            // Both widget and button use widgetXOffset for alignment
            int buttonX = sidebarX + sidebarPadding + buildStructureButtonXOffset; // Same X position and offset as structure list widget
            int buttonWidth = contentWidth; // Same width as structure list widget for perfect alignment
            int buttonY = sidebarY + sidebarHeight - sidebarButtonHeight - sidebarPadding; // Position from top with padding
            buildStructureButton.setX(buttonX);
            buildStructureButton.setY(buttonY);
            buildStructureButton.setWidth(buttonWidth);
            buildStructureButton.active = true; // Ensure button is active
            int buttonBottom = buttonY + buildStructureButton.getHeight();
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Build Structure button positioned at ({}, {}), size: {}x{}, active: {}, bottom: {}", 
                buttonX, buttonY, buttonWidth, buildStructureButton.getHeight(), buildStructureButton.active, buttonBottom);
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Widget top: {}, Widget calculated bottom: {}, Button Y: {}", 
                sidebarListY, sidebarListY + sidebarListHeight, buttonY);
        }
        
        // Set structures if they've been discovered
        if (structureListWidget != null) {
            discoverStructures();
            
            // Auto-select first structure if available
            if (!structureListWidget.children().isEmpty()) {
                structureListWidget.setSelected(structureListWidget.children().get(0));
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Auto-selected first structure: {}", 
                    structureListWidget.getSelectedStructure());
            }
        }
    }

    private void updateTabButtons() {
        // Update button appearance based on active tab
        // For now, we'll just enable/disable them
        // TODO: Add visual styling for active/inactive tabs
    }

    @Override
    public void renderBackground(DrawContext context) {
        // Override to prevent default Minecraft background (dirt texture) from rendering
        // We draw our own custom background in render() method instead
        // Do NOT call super.renderBackground() - this prevents the dirt texture from showing
    }
    
    @Override
    public boolean shouldPause() {
        return false; // Don't pause the game when UI is open
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Close building selection dialog on ESC
        if (showingBuildingSelection && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeBuildingSelectionDialog();
            return true;
        }
        
        // Handle other key presses
        // Handle ESC to close the screen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.client != null) {
                this.client.setScreen(null);
            }
            return true;
        }
        
        // CRITICAL FIX: Don't intercept movement keys at all
        // Let Minecraft handle them normally - this prevents keys from being broken globally
        // Only handle keys that are specific to this UI screen
        
        // For all other keys (widget navigation, etc.), call super to handle them
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Renders the structure list sidebar.
     * Only called when Buildings tab is active.
     */
    private void renderStructureSidebar(DrawContext context, int mainX, int mainY) {
        int screenHeight = 280;
        
        int sidebarWidth = 180;
        int sidebarX = mainX - sidebarWidth - 15; // 15px gap from main window
        int sidebarY = mainY; // Align exactly with main window top
        int sidebarHeight = screenHeight; // Match main window height exactly
        
        // Draw sidebar background using SAME style as main window (0xC0101010)
        // This creates the outer border/background for the sidebar
        context.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xC0101010);
        context.drawBorder(sidebarX, sidebarY, sidebarWidth, sidebarHeight, 0xFF404040);
        
        // Draw title bar - aligned with main window's tab area
        int titleHeight = 20;
        context.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + titleHeight, 0xC0101010);
        context.drawBorder(sidebarX, sidebarY, sidebarWidth, titleHeight, 0xFF404040);
        
        // Draw title text - centered vertically in title bar
        context.drawText(
            this.textRenderer,
            Text.translatable("settlements.ui.buildings.structures"),
            sidebarX + 8,
            sidebarY + 6,
            0xFFFFFF,
            false
        );
        
        // List area background is handled by the widget itself, no need to draw here
    }

    /**
     * Creates the building list widget. Called when Buildings tab is activated.
     */
    private void createBuildingListWidget() {
        if (activeTab != TabType.BUILDINGS) {
            return; // Don't create widget if not on Buildings tab
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Position building list in main content area (at the bottom)
        int listX = x - 70; // Left side with padding
        int listHeight = 100; // Fixed height for the widget
        int bottomOffset = -110; // Space from bottom edge (for buttons)
        int listY = (y + screenHeight) - listHeight - bottomOffset; // Position from bottom
        int listWidth = screenWidth - 20; // Leave padding on sides
        
        this.buildingListWidget = new BuildingListWidget(
            this.client,
            listWidth,
            listHeight,
            listY,
            listY + listHeight,
            24, // Item height
            settlement.getBuildings()
        );
        // Set X position for the widget
        this.buildingListWidget.setLeftPos(listX);
        
        // Set delete callback
        this.buildingListWidget.setOnDeleteCallback(building -> {
            // Remove building from client-side settlement immediately (optimistic update)
            settlement.getBuildings().remove(building);
            
            // Send cancel packet to delete the building on server
            com.secretasain.settlements.network.CancelBuildingPacketClient.send(building.getId(), settlement.getId());
            
            // Refresh the building list
            if (buildingListWidget != null) {
                buildingListWidget.updateEntries();
            }
            
            // Update material list widget (will be removed if no building selected)
            createMaterialListWidget();
            
            // Update action buttons (will hide if no building selected)
            updateBuildingActionButtons();
        });
        
        // Set start callback
        this.buildingListWidget.setOnStartCallback(building -> {
            // Send start building packet to server
            com.secretasain.settlements.network.StartBuildingPacketClient.send(building.getId(), settlement.getId());
            
            // Refresh the building list to update status
            if (buildingListWidget != null) {
                buildingListWidget.updateEntries();
            }
            
            // Update material list widget
            createMaterialListWidget();
            
            // Update action buttons
            updateBuildingActionButtons();
        });
        
        // Set selection changed callback to update material list when building is selected
        // CRITICAL FIX: Defer widget creation to avoid ConcurrentModificationException
        // We can't modify the children list while mouseClicked is iterating over it
        this.buildingListWidget.setOnSelectionChangedCallback(building -> {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Building selection changed callback fired: {}", building != null ? building.getId() : "null");
            if (building != null) {
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Selected building has {} required materials", building.getRequiredMaterials().size());
                if (!building.getRequiredMaterials().isEmpty()) {
                    for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> entry : building.getRequiredMaterials().entrySet()) {
                        com.secretasain.settlements.SettlementsMod.LOGGER.info("  Required: {} x{}", entry.getKey(), entry.getValue());
                    }
                } else {
                    com.secretasain.settlements.SettlementsMod.LOGGER.warn("Selected building has EMPTY required materials map!");
                }
            }
            lastSelectedBuilding = building;
            
            // CRITICAL FIX: Defer widget creation to next tick to avoid ConcurrentModificationException
            // We can't modify children list while mouseClicked is iterating over it
            if (this.client != null) {
                this.client.execute(() -> {
                    createMaterialListWidget();
                    updateBuildingActionButtons();
                });
            } else {
                // Fallback if client is null (shouldn't happen, but be safe)
                createMaterialListWidget();
                updateBuildingActionButtons();
            }
        });
        
        // Set available materials for the building list widget
        this.buildingListWidget.setAvailableMaterials(settlement.getMaterials());
        
        // Auto-select first building if available (so materials are shown by default)
        // CRITICAL FIX: Manually trigger callback after auto-selection since it doesn't fire on programmatic selection
        if (!this.buildingListWidget.children().isEmpty()) {
            this.buildingListWidget.setSelected(this.buildingListWidget.children().get(0));
            com.secretasain.settlements.settlement.Building firstBuilding = this.buildingListWidget.getSelectedBuilding();
            if (firstBuilding != null) {
                lastSelectedBuilding = firstBuilding;
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Auto-selected first building: {} - triggering material list creation", firstBuilding.getId());
                // Manually trigger the callback since programmatic selection doesn't fire it
                this.buildingListWidget.triggerSelectionChanged(firstBuilding);
            }
        }
    }
    
    /**
     * Positions the cancel/remove/start building buttons.
     */
    private void positionBuildingActionButtons() {
        if (activeTab != TabType.BUILDINGS) {
            return;
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Position buttons at bottom of main content area
        int buttonY = y + screenHeight - 25;
        int buttonSpacing = 5;
        int buttonWidth = 80;
        
        // Start building button on left (for RESERVED buildings)
        if (startBuildingButton != null) {
            startBuildingButton.setX(x + 10);
            startBuildingButton.setY(buttonY);
            startBuildingButton.setWidth(100); // Slightly wider for "Start Building"
        }
        
        // Cancel button next to start button
        if (cancelBuildingButton != null) {
            cancelBuildingButton.setX(x + 10 + 100 + buttonSpacing);
            cancelBuildingButton.setY(buttonY);
            cancelBuildingButton.setWidth(buttonWidth);
        }
        
        // Remove button next to cancel button
        if (removeBuildingButton != null) {
            removeBuildingButton.setX(x + 10 + 100 + buttonWidth + buttonSpacing * 2);
            removeBuildingButton.setY(buttonY);
            removeBuildingButton.setWidth(buttonWidth);
        }
        
        // Unload inventory button - positioned directly under check materials button
        if (unloadInventoryButton != null) {
            // Use the exact same calculation as positionCheckMaterialsButton
            // Check materials button is positioned at: x + screenWidth - 150, y + 28 + 14
            // Position unload button directly below it (25 pixels down = 20 button height + 5 spacing)
            int unloadButtonX = x + screenWidth - 150; // Same X as check materials button
            int unloadButtonY = y + 28 + 14 + 25; // Check materials Y (y + 28 + 14) + 25 pixels below
            
            unloadInventoryButton.setX(unloadButtonX);
            unloadInventoryButton.setY(unloadButtonY);
            unloadInventoryButton.setWidth(140); // Match check materials button width
            
            // DEBUG: Log button position to verify it's being set
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("Unload inventory button positioned at ({}, {})", unloadButtonX, unloadButtonY);
        }
    }
    
    /**
     * Updates the cancel/remove buttons based on selected building.
     */
    private void updateBuildingActionButtons() {
        if (activeTab != TabType.BUILDINGS) {
            return;
        }
        
        com.secretasain.settlements.settlement.Building selectedBuilding = null;
        if (buildingListWidget != null) {
            selectedBuilding = buildingListWidget.getSelectedBuilding();
        }
        
        if (selectedBuilding != null) {
            com.secretasain.settlements.building.BuildingStatus status = selectedBuilding.getStatus();
            
            // Show start building button for RESERVED buildings (if materials are available)
            if (startBuildingButton != null) {
                boolean canStart = (status == com.secretasain.settlements.building.BuildingStatus.RESERVED);
                // TODO: Check if materials are available (will need to sync settlement materials to client)
                startBuildingButton.visible = canStart;
                startBuildingButton.active = canStart;
            }
            
            // Show cancel button for RESERVED or IN_PROGRESS buildings
            if (cancelBuildingButton != null) {
                cancelBuildingButton.visible = (status == com.secretasain.settlements.building.BuildingStatus.RESERVED || 
                                               status == com.secretasain.settlements.building.BuildingStatus.IN_PROGRESS);
                cancelBuildingButton.active = cancelBuildingButton.visible;
            }
            
            // Show remove button for COMPLETED buildings
            if (removeBuildingButton != null) {
                removeBuildingButton.visible = (status == com.secretasain.settlements.building.BuildingStatus.COMPLETED);
                removeBuildingButton.active = removeBuildingButton.visible;
            }
            
            // Unload inventory button is always visible on Buildings tab (like check materials button)
            // Visibility is set in switchTab, don't override it here
            // Button will show message if clicked without materials
        } else {
            // Hide buttons if no building is selected
            if (startBuildingButton != null) {
                startBuildingButton.visible = false;
                startBuildingButton.active = false;
            }
            if (cancelBuildingButton != null) {
                cancelBuildingButton.visible = false;
                cancelBuildingButton.active = false;
            }
            if (removeBuildingButton != null) {
                removeBuildingButton.visible = false;
                removeBuildingButton.active = false;
            }
            // Keep unload inventory button visible even when no building selected (like check materials button)
            // It will show a message if clicked without a building selected
            // Visibility is already set in switchTab when Buildings tab is active
        }
    }
    
    /**
     * Called when the cancel building button is clicked.
     */
    private void onCancelBuildingClicked() {
        if (buildingListWidget == null) {
            return;
        }
        
        com.secretasain.settlements.settlement.Building selectedBuilding = buildingListWidget.getSelectedBuilding();
        if (selectedBuilding == null) {
            return;
        }
        
        com.secretasain.settlements.building.BuildingStatus status = selectedBuilding.getStatus();
        if (status != com.secretasain.settlements.building.BuildingStatus.RESERVED && 
            status != com.secretasain.settlements.building.BuildingStatus.IN_PROGRESS) {
            return; // Can only cancel RESERVED or IN_PROGRESS buildings
        }
        
        // Send cancel packet to server
        com.secretasain.settlements.network.CancelBuildingPacketClient.send(selectedBuilding.getId(), settlement.getId());
        
        // Update UI
        updateBuildingActionButtons();
    }
    
    /**
     * Called when the remove building button is clicked.
     */
    private void onRemoveBuildingClicked() {
        if (buildingListWidget == null) {
            return;
        }
        
        com.secretasain.settlements.settlement.Building selectedBuilding = buildingListWidget.getSelectedBuilding();
        if (selectedBuilding == null) {
            return;
        }
        
        com.secretasain.settlements.building.BuildingStatus status = selectedBuilding.getStatus();
        if (status != com.secretasain.settlements.building.BuildingStatus.COMPLETED) {
            return; // Can only remove COMPLETED buildings
        }
        
        // Send cancel packet to server (same packet handles removal)
        com.secretasain.settlements.network.CancelBuildingPacketClient.send(selectedBuilding.getId(), settlement.getId());
        
        // Update UI
        updateBuildingActionButtons();
    }
    
    /**
     * Called when the unload inventory button is clicked.
     * Can unload from either a selected building's providedMaterials or from settlement storage directly.
     */
    private void onUnloadInventoryClicked() {
        // First try to unload from selected building's providedMaterials
        com.secretasain.settlements.settlement.Building selectedBuilding = null;
        if (buildingListWidget != null) {
            selectedBuilding = buildingListWidget.getSelectedBuilding();
        }
        
        if (selectedBuilding != null) {
            // Check if building has materials to unload
            java.util.Map<net.minecraft.util.Identifier, Integer> provided = selectedBuilding.getProvidedMaterials();
            if (!provided.isEmpty()) {
                // Log what we're trying to unload
                int totalItems = provided.values().stream().mapToInt(Integer::intValue).sum();
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Unload button clicked for building {}: {} material types, {} total items", 
                    selectedBuilding.getId(), provided.size(), totalItems);
                
                // Send packet to server to unload materials from building
                try {
                    com.secretasain.settlements.network.UnloadInventoryPacketClient.send(selectedBuilding.getId(), settlement.getId());
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("Successfully sent unload inventory packet for building {}", selectedBuilding.getId());
                    
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(
                            net.minecraft.text.Text.literal(String.format("Unloading %d material types (%d items) from building to chest...", 
                                provided.size(), totalItems)), 
                            false
                        );
                    }
                } catch (Exception e) {
                    com.secretasain.settlements.SettlementsMod.LOGGER.error("Error sending unload inventory packet", e);
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(
                            net.minecraft.text.Text.literal("Error sending unload request: " + e.getMessage()), 
                            false
                        );
                    }
                }
                
                // Update button state (will be updated when server responds)
                updateBuildingActionButtons();
                return;
            }
        }
        
        // If no building selected or building has no materials, unload from settlement storage
        java.util.Map<String, Integer> settlementMaterials = settlement.getMaterials();
        if (settlementMaterials.isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Unload button clicked but settlement has no materials in storage");
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.literal("No materials in settlement storage to unload"), 
                    false
                );
            }
            return;
        }
        
        // Convert settlement materials to Identifier map for logging
        int totalItems = settlementMaterials.values().stream().mapToInt(Integer::intValue).sum();
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Unload button clicked for settlement storage: {} material types, {} total items", 
            settlementMaterials.size(), totalItems);
        
        // Send packet to server to unload materials from settlement storage (buildingId = null)
        try {
            com.secretasain.settlements.network.UnloadInventoryPacketClient.send(null, settlement.getId());
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Successfully sent unload inventory packet for settlement storage");
            
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.literal(String.format("Unloading %d material types (%d items) from settlement storage to chest...", 
                        settlementMaterials.size(), totalItems)), 
                    false
                );
            }
        } catch (Exception e) {
            com.secretasain.settlements.SettlementsMod.LOGGER.error("Error sending unload inventory packet", e);
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.literal("Error sending unload request: " + e.getMessage()), 
                    false
                );
            }
        }
        
        // Update button state (will be updated when server responds)
        updateBuildingActionButtons();
    }
    
    /**
     * Called when the start building button is clicked.
     */
    private void onStartBuildingClicked() {
        if (buildingListWidget == null) {
            return;
        }
        
        com.secretasain.settlements.settlement.Building selectedBuilding = buildingListWidget.getSelectedBuilding();
        if (selectedBuilding == null) {
            return;
        }
        
        com.secretasain.settlements.building.BuildingStatus status = selectedBuilding.getStatus();
        if (status != com.secretasain.settlements.building.BuildingStatus.RESERVED) {
            return; // Can only start RESERVED buildings
        }
        
        // Send start building packet to server
        com.secretasain.settlements.network.StartBuildingPacketClient.send(selectedBuilding.getId(), settlement.getId());
        
        // Update UI
        updateBuildingActionButtons();
    }
    
    /**
     * Called when the check materials button is clicked.
     */
    private void onCheckMaterialsClicked() {
        // Get selected building to only extract required materials
        // CRITICAL: Use lastSelectedBuilding as PRIMARY source since it's updated reliably
        // The widget's selection state may be lost when the widget is recreated or updated
        com.secretasain.settlements.settlement.Building selectedBuilding = lastSelectedBuilding;
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials button clicked - lastSelectedBuilding: {}", 
            selectedBuilding != null ? selectedBuilding.getId().toString() : "null");
        
        // Fallback: Try getting from buildingListWidget if lastSelectedBuilding is null
        if (selectedBuilding == null && buildingListWidget != null) {
            selectedBuilding = buildingListWidget.getSelectedBuilding();
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials - fallback to buildingListWidget.getSelectedBuilding(): {}", 
                selectedBuilding != null ? selectedBuilding.getId().toString() : "null");
            
            // If that didn't work, try getting from getSelectedOrNull
            if (selectedBuilding == null) {
                com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry selectedEntry = buildingListWidget.getSelectedOrNull();
                if (selectedEntry != null) {
                    selectedBuilding = selectedEntry.getBuilding();
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials - fallback to getSelectedOrNull(): {}", 
                        selectedBuilding != null ? selectedBuilding.getId().toString() : "null");
                }
            }
        }
        
        // Debug: Log all buildings in the list and their selection status
        if (buildingListWidget != null && !buildingListWidget.children().isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Building list has {} entries", buildingListWidget.children().size());
            com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry currentlySelected = buildingListWidget.getSelectedOrNull();
            for (int i = 0; i < buildingListWidget.children().size(); i++) {
                com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry entry = buildingListWidget.children().get(i);
                com.secretasain.settlements.settlement.Building b = entry.getBuilding();
                boolean isSelected = currentlySelected == entry;
                boolean isLastSelected = lastSelectedBuilding != null && lastSelectedBuilding.getId().equals(b.getId());
                com.secretasain.settlements.SettlementsMod.LOGGER.info("  Entry {}: building {} (status: {}), widgetSelected: {}, lastSelected: {}", 
                    i, b.getId(), b.getStatus(), isSelected, isLastSelected);
            }
        }
        
        // CRITICAL FIX: Require a building to be selected to prevent taking all items
        if (selectedBuilding == null) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.literal("Please select a building first to check materials"), 
                    false
                );
            }
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Check materials clicked but no building selected (tried lastSelectedBuilding, getSelectedBuilding, and getSelectedOrNull)");
            return;
        }
        
        // Send check materials packet to server
        // Only extract required materials for the selected building
        // The server will send back updated materials via SyncMaterialsPacket
        com.secretasain.settlements.network.CheckMaterialsPacketClient.send(selectedBuilding.getId(), settlement.getId());
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials clicked for building {} (ID: {}), sending packet to server", 
            selectedBuilding.getStructureType(), selectedBuilding.getId());
    }
    
    /**
     * Updates the settlement's materials from server sync.
     * Called when SyncMaterialsPacket is received.
     * @param settlementId The settlement ID (for verification)
     * @param materialsNbt NBT containing the updated materials map
     */
    /**
     * Updates the settlement data when received from server (e.g., building status changes).
     * @param updatedSettlement The updated settlement data
     */
    public void updateSettlementData(Settlement updatedSettlement) {
        // Update the settlement reference
        this.settlement = updatedSettlement;
        
        // Recreate building list widget to reflect changes
        if (activeTab == TabType.BUILDINGS && buildingListWidget != null) {
            createBuildingListWidget();
        }
        
        // Update material list widget if it exists
        createMaterialListWidget();
        
        // Update action buttons (this will hide unload button if materials are now empty)
        updateBuildingActionButtons();
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Updated settlement data in UI - buildings: {}, materials: {}", 
            updatedSettlement.getBuildings().size(), updatedSettlement.getMaterials().size());
    }
    
    /**
     * Shows a building selection dialog for assigning a villager to work.
     */
    private void showBuildingSelectionDialog(VillagerData villager) {
        // Get available buildings (completed only)
        List<com.secretasain.settlements.settlement.Building> availableBuildings = 
            settlement.getBuildings().stream()
                .filter(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED)
                .collect(java.util.stream.Collectors.toList());
        
        if (availableBuildings.isEmpty()) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.translatable("settlements.work.no_buildings_available"),
                    false
                );
            }
            return;
        }
        
        // Create building selection widget
        int screenWidth = this.width;
        int screenHeight = this.height;
        int dialogWidth = 220;
        int listHeight = Math.min(120, availableBuildings.size() * 26);
        int dialogHeight = 50 + listHeight + 20; // title + list + footer
        int dialogX = (screenWidth - dialogWidth) / 2;
        int dialogY = (screenHeight - dialogHeight) / 2;
        int listTop = dialogY + 30;
        int listBottom = dialogY + dialogHeight - 20;
        
        buildingSelectionWidget = new BuildingSelectionWidget(
            this.client,
            dialogWidth - 20,
            listHeight,
            listTop,
            listBottom,
            26,
            availableBuildings,
            villager
        );
        buildingSelectionWidget.setLeftPos(dialogX + 10);
        
        buildingSelectionWidget.setOnBuildingSelected((v, buildingId) -> {
            // Assign villager to selected building
            com.secretasain.settlements.network.AssignWorkPacketClient.send(
                settlement.getId(),
                v.getEntityId(),
                buildingId,
                true
            );
            // Close dialog
            showingBuildingSelection = false;
            if (buildingSelectionWidget != null) {
                this.remove(buildingSelectionWidget);
                buildingSelectionWidget = null;
            }
            // Refresh villager list
            this.client.execute(() -> {
                villagerListWidget.updateEntries();
            });
        });
        
        this.addDrawableChild(buildingSelectionWidget);
        showingBuildingSelection = true;
    }
    
    /**
     * Closes the building selection dialog.
     */
    private void closeBuildingSelectionDialog() {
        showingBuildingSelection = false;
        if (buildingSelectionWidget != null) {
            this.remove(buildingSelectionWidget);
            buildingSelectionWidget = null;
        }
    }
    
    /**
     * Renders the building selection dialog background overlay.
     */
    private void renderBuildingSelectionDialogBackground(DrawContext context) {
        int screenWidth = this.width;
        int screenHeight = this.height;
        int dialogWidth = 220;
        int listHeight = buildingSelectionWidget != null && !buildingSelectionWidget.children().isEmpty() ? 
            Math.min(120, buildingSelectionWidget.children().size() * 26) : 60;
        int dialogHeight = 50 + listHeight + 20; // title + list + footer
        int dialogX = (screenWidth - dialogWidth) / 2;
        int dialogY = (screenHeight - dialogHeight) / 2;
        
        // Draw semi-transparent background overlay
        context.fill(0, 0, screenWidth, screenHeight, 0x80000000);
        
        // Draw dialog background
        context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF202020);
        context.drawBorder(dialogX, dialogY, dialogWidth, dialogHeight, 0xFF404040);
    }
    
    /**
     * Renders the building selection dialog text (title and footer).
     */
    private void renderBuildingSelectionDialogText(DrawContext context) {
        int screenWidth = this.width;
        int screenHeight = this.height;
        int dialogWidth = 220;
        int listHeight = buildingSelectionWidget != null && !buildingSelectionWidget.children().isEmpty() ? 
            Math.min(120, buildingSelectionWidget.children().size() * 26) : 60;
        int dialogHeight = 50 + listHeight + 20; // title + list + footer
        int dialogX = (screenWidth - dialogWidth) / 2;
        int dialogY = (screenHeight - dialogHeight) / 2;
        
        // Draw title
        if (buildingSelectionWidget != null && buildingSelectionWidget.getVillager() != null) {
            VillagerData villager = buildingSelectionWidget.getVillager();
            String villagerName = villager.getName() != null && !villager.getName().isEmpty() 
                ? villager.getName() 
                : Text.translatable("settlements.ui.villagers.unnamed").getString();
            context.drawText(
                this.textRenderer,
                Text.translatable("settlements.ui.work.select_building", villagerName),
                dialogX + 10,
                dialogY + 10,
                0xFFFFFF,
                false
            );
        }
        
        // Draw cancel hint
        context.drawText(
            this.textRenderer,
            Text.translatable("settlements.ui.work.press_esc"),
            dialogX + 10,
            dialogY + dialogHeight - 16,
            0xCCCCCC,
            false
        );
    }
    
    public void updateMaterials(UUID settlementId, net.minecraft.nbt.NbtCompound materialsNbt) {
        // Verify this is the correct settlement
        if (!settlement.getId().equals(settlementId)) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Received materials update for different settlement: {} vs {}", settlementId, settlement.getId());
            return;
        }
        
        // Update materials map
        settlement.getMaterials().clear();
        for (String key : materialsNbt.getKeys()) {
            settlement.getMaterials().put(key, materialsNbt.getInt(key));
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Updated materials for settlement {}: {} material types", settlementId, settlement.getMaterials().size());
        
        // Update building list widget with new materials (so it can recalculate availability)
        if (buildingListWidget != null) {
            buildingListWidget.setAvailableMaterials(settlement.getMaterials());
        }
        
        // Refresh material list widget to show updated available materials
        if (materialListWidget != null) {
            // Update the widget's available materials
            materialListWidget.updateAvailableMaterials(settlement.getMaterials());
            materialListWidget.updateEntries();
        } else if (activeTab == TabType.BUILDINGS) {
            // Recreate widget if it doesn't exist
            createMaterialListWidget();
        }
    }
    
    /**
     * Positions the check materials button.
     */
    private void positionCheckMaterialsButton() {
        if (activeTab != TabType.BUILDINGS) {
            return;
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Position button in the top-right area of the Buildings tab
        int buttonX = x + screenWidth - 150; // Right side with padding
        int buttonY = y + 28 + 14; // Below the title
        
        if (checkMaterialsButton != null) {
            checkMaterialsButton.setX(buttonX);
            checkMaterialsButton.setY(buttonY);
            checkMaterialsButton.setWidth(140);
        }
    }
    
    /**
     * Creates the material list widget to display required materials for selected building.
     */
    private void createMaterialListWidget() {
        if (activeTab != TabType.BUILDINGS) {
            return;
        }
        
        // Remove existing widget if any
        this.children().removeIf(child -> child instanceof MaterialListWidget);
        materialListWidget = null;
        
        // Get selected building
        com.secretasain.settlements.settlement.Building selectedBuilding = null;
        if (buildingListWidget != null) {
            selectedBuilding = buildingListWidget.getSelectedBuilding();
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Position material list - use configured values or defaults
        // Default position: Right side of screen, below check materials button
        int defaultX = x + screenWidth + 30; // Right side with more space
        int defaultY = y + 50; // Below title and check materials button
        int defaultWidth = 200; // Wider for better visibility
        int defaultHeight = 180; // Taller to show more materials
        
        int materialListX = this.materialListX >= 0 ? this.materialListX : defaultX;
        int materialListY = this.materialListY >= 0 ? this.materialListY : defaultY;
        int materialListWidth = this.materialListWidth > 0 ? this.materialListWidth : defaultWidth;
        int materialListHeight = this.materialListHeight > 0 ? this.materialListHeight : defaultHeight;
        
        // Get required materials - ONLY from selected building (don't sum up all buildings)
        // CRITICAL FIX: Only show materials for selected building to prevent incrementing
        java.util.Map<net.minecraft.util.Identifier, Integer> requiredMaterials;
        if (selectedBuilding != null) {
            // Show materials for selected building only
            requiredMaterials = new java.util.HashMap<>(selectedBuilding.getRequiredMaterials()); // Create a copy to avoid unmodifiable map issues
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Selected building {} has {} required materials", 
                selectedBuilding.getId(), requiredMaterials.size());
            if (!requiredMaterials.isEmpty()) {
                for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> entry : requiredMaterials.entrySet()) {
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("  - {}: {}", entry.getKey(), entry.getValue());
                }
            } else {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("Selected building {} has empty required materials map!", selectedBuilding.getId());
            }
        } else {
            // No building selected - show empty materials list
            // CRITICAL FIX: Don't sum up all buildings - this causes incrementing issue
            requiredMaterials = new java.util.HashMap<>();
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("No building selected - showing empty materials list");
        }
        
        // Debug logging
        if (selectedBuilding != null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Creating material list widget for building {} with {} required materials", 
                selectedBuilding.getId(), requiredMaterials != null ? requiredMaterials.size() : 0);
        } else {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Creating material list widget for all buildings with {} total required materials", 
                requiredMaterials != null ? requiredMaterials.size() : 0);
        }
        if (requiredMaterials != null && !requiredMaterials.isEmpty()) {
            for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> entry : requiredMaterials.entrySet()) {
                com.secretasain.settlements.SettlementsMod.LOGGER.info("  Material: {} x{}", entry.getKey(), entry.getValue());
            }
        }
        
        // Always create the widget, even if empty (will show "No materials required" message)
        java.util.Map<net.minecraft.util.Identifier, Integer> materialsToShow = requiredMaterials != null ? requiredMaterials : new java.util.HashMap<>();
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Creating MaterialListWidget with {} materials", materialsToShow.size());
        
        this.materialListWidget = new MaterialListWidget(
            this.client,
            materialListWidth,
            materialListHeight,
            materialListY,
            materialListY + materialListHeight,
            16, // Item height
            materialsToShow,
            settlement.getMaterials() // Available materials (convert String keys to Identifier)
        );
        // Set X position for the widget
        this.materialListWidget.setLeftPos(materialListX);
        this.addDrawableChild(materialListWidget);
        
        // Force update entries to ensure materials are displayed
        this.materialListWidget.updateEntries();
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Material list widget created at position ({}, {}), size {}x{}, entries: {}", 
            materialListX, materialListY, materialListWidth, materialListHeight, 
            this.materialListWidget.children().size());
    }
    
    /**
     * Sets the position of the material list widget.
     * @param x X position (screen coordinates), or -1 to use default
     * @param y Y position (screen coordinates), or -1 to use default
     */
    public void setMaterialListPosition(int x, int y) {
        this.materialListX = x;
        this.materialListY = y;
        // Recreate widget if it exists
        if (activeTab == TabType.BUILDINGS) {
            createMaterialListWidget();
        }
    }
    
    /**
     * Sets the size of the material list widget.
     * @param width Width in pixels, or -1 to use default
     * @param height Height in pixels, or -1 to use default
     */
    public void setMaterialListSize(int width, int height) {
        this.materialListWidth = width;
        this.materialListHeight = height;
        // Recreate widget if it exists
        if (activeTab == TabType.BUILDINGS) {
            createMaterialListWidget();
        }
    }
    
    /**
     * Gets the current material list widget position.
     * @return Array with [x, y] coordinates, or null if using defaults
     */
    public int[] getMaterialListPosition() {
        if (materialListX >= 0 && materialListY >= 0) {
            return new int[]{materialListX, materialListY};
        }
        return null;
    }
    
    /**
     * Gets the current material list widget size.
     * @return Array with [width, height] dimensions, or null if using defaults
     */
    public int[] getMaterialListSize() {
        if (materialListWidth > 0 && materialListHeight > 0) {
            return new int[]{materialListWidth, materialListHeight};
        }
        return null;
    }
    
    private enum TabType {
        OVERVIEW,
        BUILDINGS,
        VILLAGERS,
        SETTINGS
    }
}

