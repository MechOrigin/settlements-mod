package com.secretasain.settlements.ui;

import com.secretasain.settlements.network.ActivateBuildModePacketClient;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.VillagerData;
import com.secretasain.settlements.settlement.GolemData;
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
    TabType activeTab = TabType.OVERVIEW;
    
    /**
     * Gets the currently active tab.
     * @return The active tab type
     */
    public TabType getActiveTab() {
        return activeTab;
    }
    
    // Tab buttons
    private ButtonWidget overviewTabButton;
    private ButtonWidget buildingsTabButton;
    private ButtonWidget villagersTabButton;
    private ButtonWidget warbandTabButton;
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
    // removeBuildingButton removed - delete functionality is now in BuildingListWidget (red X button)
    private ButtonWidget startBuildingButton;
    private ButtonWidget checkMaterialsButton;
    private ButtonWidget unloadInventoryButton;
    private ButtonWidget refreshVillagersButton;
    
    // Material display widget
    private MaterialListWidget materialListWidget;
    private com.secretasain.settlements.settlement.Building lastSelectedBuilding; // Track selection to avoid recreating widget every frame
    
    // Building output display widget (for Villagers tab)
    private BuildingOutputWidget buildingOutputWidget;
    private com.secretasain.settlements.settlement.Building lastSelectedBuildingForOutput; // Track selection to avoid recreating widget every frame
    // Track all BuildingOutputWidget instances explicitly to ensure proper cleanup
    private final java.util.Set<BuildingOutputWidget> allBuildingOutputWidgets = new java.util.HashSet<>();
    
    // Configurable material list widget position and size
    // Default to a larger, more visible size
    private int materialListX = -1; // -1 means use default
    private int materialListY = -1; // -1 means use default
    private int materialListWidth = 200; // Default width - larger for better visibility
    private int materialListHeight = 180; // Default height - larger for better visibility
    
    // Warband tab widgets
    private BuildingListWidget warbandBarracksListWidget; // List of barracks buildings
    private com.secretasain.settlements.settlement.Building selectedBarracks; // Currently selected barracks
    private WarbandPanelWidget warriorPanel;
    private WarbandPanelWidget priestPanel;
    private WarbandPanelWidget magePanel;
    private HiredNpcListWidget hiredNpcListWidget; // List of hired NPCs
    
    /**
     * Refreshes the hired NPC list widget with latest data from server.
     */
    public void refreshHiredNpcList() {
        if (activeTab == TabType.WARBAND && selectedBarracks != null) {
            createHiredNpcListWidget();
        }
    }

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
        
        // Create tab buttons - reduced width to fit 5 tabs (70*5 + 4*5 = 370px, fits in 400px)
        int tabButtonWidth = 70;
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
        
        this.warbandTabButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.tab.warband"),
            button -> switchTab(TabType.WARBAND)
        ).dimensions(x + 5 + (tabButtonWidth + tabSpacing) * 3, tabY, tabButtonWidth, tabButtonHeight).build();
        
        this.settingsTabButton = ButtonWidget.builder(
            Text.translatable("settlements.ui.tab.settings"),
            button -> switchTab(TabType.SETTINGS)
        ).dimensions(x + 5 + (tabButtonWidth + tabSpacing) * 4, tabY, tabButtonWidth, tabButtonHeight).build();
        
        this.addDrawableChild(overviewTabButton);
        this.addDrawableChild(buildingsTabButton);
        this.addDrawableChild(villagersTabButton);
        this.addDrawableChild(warbandTabButton);
        this.addDrawableChild(settingsTabButton);
        
        // Create villager list widget (initially hidden)
        // Position it at the bottom - same as building list widget
        // Will be repositioned in switchTab() when Villagers tab is active
        int listX = -1000; // Off-screen initially
        int listHeight = 100; // Fixed height (same as building list)
        int listY = -1000; // Off-screen initially
        int listWidth = 380; // Width (will be updated in switchTab)
        this.villagerListWidget = new VillagerListWidget(
            this.client,
            listWidth,
            listHeight,
            listY,
            listY + listHeight,
            65, // Increased item height to accommodate assignment location info
            settlement.getVillagers(),
            settlement.getGolems(),
            settlement
        );
        villagerListWidget.setLeftPos(listX); // Set initial position off-screen
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
        villagerListWidget.setOnAssignGolemCallback((golem, buildingId) -> {
            UUID specialUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
            if (buildingId != null && buildingId.equals(specialUuid)) {
                showWallStationSelectionDialog(golem);
            } else if (golem.isAssigned() && buildingId != null) {
                // Unassign golem
                com.secretasain.settlements.network.AssignGolemPacketClient.send(
                    settlement.getId(),
                    golem.getEntityId(),
                    buildingId,
                    false
                );
                this.client.execute(() -> {
                    villagerListWidget.updateEntries();
                });
            } else if (!golem.isAssigned() && buildingId == null) {
                // Show wall station selection dialog
                showWallStationSelectionDialog(golem);
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
        
        // Remove button removed - delete functionality is now in BuildingListWidget (red X button)
        
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
        
        // Define known structures array at method scope so it's accessible for fallback
        // All NBT files found in src/main/resources/data/settlements/structures/
        String[] knownStructures = {
            // Defensive structures
            "lvl1_oak_fence.nbt",
            "lvl1_oak_gate.nbt",
            "lvl1_oak_wall.nbt",
            "lvl2_oak_wall.nbt",
            "lvl3_oak_wall.nbt",
            // Residential structures
            "lvl1_oak_house.nbt",
            "lvl2_oak_house.nbt",
            "lvl3_oak_house.nbt",
            // Commercial structures
            "lvl1_oak_cartographer.nbt",
            "lvl1_trader_hut.nbt",
            // Industrial structures
            "lvl1_oak_farm.nbt",
            "lvl1_oak_smithing.nbt",
            "lvl1_lumber_yard.nbt",
            // Warband structures
            "lvl1_barracks.nbt",
            // Misc structures
            "lvl1_town_hall.nbt"
        };
        
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
            
            // Pattern 4: Try direct identifier lookup for known structures (using knownStructures defined at method scope)
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
            // Use the same knownStructures array that was used for Pattern 4 discovery
            for (String fallback : knownStructures) {
                structures.add(fallback);
            }
            
            // Create identifiers for fallback structures
            for (String fallback : structures) {
                Identifier fallbackId = new Identifier("settlements", "structures/" + fallback);
                structureResourceIds.put(fallback, fallbackId);
            }
        } else {
            // Even if we found some, add known ones that might be missing
            // This ensures all structures are available
            // Use the same knownStructures array that was used for Pattern 4 discovery
            for (String known : knownStructures) {
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
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Sending build mode activation for structure: {} in settlement: {}", 
            structureIdentifier, settlement.getId());
        
        // Send network packet to server to activate build mode
        // CRITICAL: Include settlement ID so buildings are added to the correct settlement
        ActivateBuildModePacketClient.send(structureIdentifier, settlement.getId());
        
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
        
        // CRITICAL: Make a defensive copy of children to avoid ConcurrentModificationException
        // Some widgets may modify the children list during click handling
        java.util.List<net.minecraft.client.gui.Element> childrenCopy = new java.util.ArrayList<>(this.children());
        
        // Iterate over the copy instead of the original list
        for (net.minecraft.client.gui.Element element : childrenCopy) {
            // Only process if element is still in the actual children list (might have been removed)
            if (this.children().contains(element) && element.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        return false;
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
        
        // Render debug title if enabled
        UIDebugRenderer.renderWidgetTitle(context, "SettlementScreen", x, y, screenWidth);
        
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
        
        // Render building assignment sidebar background FIRST (before widget)
        // ONLY render if Villagers tab is active AND widget exists AND is NOT showing dialog
        if (activeTab == TabType.VILLAGERS && 
            buildingSelectionWidget != null && 
            !showingBuildingSelection &&
            this.children().contains(buildingSelectionWidget)) {
            renderBuildingAssignmentSidebar(context, x, y);
        }
        
        // DEBUG: Check button states before rendering
        if (activeTab == TabType.BUILDINGS) {
            if (checkMaterialsButton != null) {
                boolean inChildren = this.children().contains(checkMaterialsButton);
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Before render: checkMaterialsButton visible={}, active={}, inChildren={}, pos=({}, {})", 
                    checkMaterialsButton.visible, checkMaterialsButton.active, inChildren, checkMaterialsButton.getX(), checkMaterialsButton.getY());
            }
            if (unloadInventoryButton != null) {
                boolean inChildren = this.children().contains(unloadInventoryButton);
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Before render: unloadInventoryButton visible={}, active={}, inChildren={}, pos=({}, {})", 
                    unloadInventoryButton.visible, unloadInventoryButton.active, inChildren, unloadInventoryButton.getX(), unloadInventoryButton.getY());
            }
        }
        
        // CRITICAL: Remove BuildingOutputWidget instances that don't belong to the current selection
        // This must happen BEFORE super.render() to prevent rendering stale widgets
        if (activeTab == TabType.VILLAGERS) {
            // Get the currently selected building
            com.secretasain.settlements.settlement.Building currentBuilding = null;
            if (buildingSelectionWidget != null) {
                currentBuilding = buildingSelectionWidget.getSelectedBuilding();
            }
            
            // Remove ALL BuildingOutputWidget instances that don't match the current building
            // Use our tracking set for efficient lookup
            java.util.List<BuildingOutputWidget> widgetsToRemove = new java.util.ArrayList<>();
            for (BuildingOutputWidget widget : allBuildingOutputWidgets) {
                // Remove if it doesn't match current building OR if current building is null
                if (currentBuilding == null || widget.getBuilding() == null || 
                    !widget.getBuilding().getId().equals(currentBuilding.getId())) {
                    widgetsToRemove.add(widget);
                }
            }
            
            // Also check children() for any widgets not in our tracking set
            for (net.minecraft.client.gui.Element child : this.children()) {
                if (child instanceof BuildingOutputWidget) {
                    BuildingOutputWidget widget = (BuildingOutputWidget) child;
                    if (!allBuildingOutputWidgets.contains(widget)) {
                        // Widget not in tracking set - add it and check if it should be removed
                        allBuildingOutputWidgets.add(widget);
                        if (currentBuilding == null || widget.getBuilding() == null || 
                            !widget.getBuilding().getId().equals(currentBuilding.getId())) {
                            if (!widgetsToRemove.contains(widget)) {
                                widgetsToRemove.add(widget);
                            }
                        }
                    }
                }
            }
            
            // Remove mismatched widgets
            for (BuildingOutputWidget widget : widgetsToRemove) {
                try {
                    this.remove(widget);
                    this.children().remove(widget);
                    allBuildingOutputWidgets.remove(widget);
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("Removed BuildingOutputWidget for wrong building: {}", 
                        widget.getBuilding() != null ? widget.getBuilding().getId() : "null");
                } catch (Exception e) {
                    // Already removed, but still remove from tracking
                    allBuildingOutputWidgets.remove(widget);
                    this.children().remove(widget);
                }
            }
            
            // Also ensure only the correct widget is in the reference
            if (buildingOutputWidget != null) {
                if (currentBuilding == null || buildingOutputWidget.getBuilding() == null ||
                    !buildingOutputWidget.getBuilding().getId().equals(currentBuilding.getId())) {
                    buildingOutputWidget = null;
                }
            }
        } else {
            // Not on Villagers tab - remove ALL BuildingOutputWidget instances
            this.children().removeIf(child -> child instanceof BuildingOutputWidget);
            buildingOutputWidget = null;
        }
        
        // Call super.render() - this will render all children INCLUDING the widget ON TOP of sidebar/dialog
        super.render(context, mouseX, mouseY, delta);
        
        // Draw building selection dialog text if shown (AFTER super.render so text appears on top)
        if (showingBuildingSelection && buildingSelectionWidget != null) {
            renderBuildingSelectionDialogText(context);
        }
        
        // Explicitly render material list widget if it exists and is in children
        if (activeTab == TabType.BUILDINGS) {
            if (materialListWidget != null) {
                if (this.children().contains(materialListWidget)) {
                    materialListWidget.render(context, mouseX, mouseY, delta);
                } else {
                    // Widget exists but not in children - re-add it
                    com.secretasain.settlements.SettlementsMod.LOGGER.warn("MaterialListWidget exists but not in children - re-adding");
                    this.addDrawableChild(materialListWidget);
                    materialListWidget.render(context, mouseX, mouseY, delta);
                }
            } else {
                // Widget is null - try to create it if a building is selected
                if (buildingListWidget != null) {
                    com.secretasain.settlements.settlement.Building selected = buildingListWidget.getSelectedBuilding();
                    if (selected != null) {
                        com.secretasain.settlements.SettlementsMod.LOGGER.info("MaterialListWidget is null but building is selected - recreating");
                        createMaterialListWidget();
                        if (materialListWidget != null) {
                            materialListWidget.render(context, mouseX, mouseY, delta);
                        }
                    }
                }
            }
        }
        
        // Explicitly render building output widget if it exists and is in children
        // Also ensure only one widget exists (clean up any duplicates)
        if (activeTab == TabType.VILLAGERS) {
            // Get the currently selected building
            com.secretasain.settlements.settlement.Building currentBuilding = null;
            if (buildingSelectionWidget != null) {
                currentBuilding = buildingSelectionWidget.getSelectedBuilding();
            }
            
            // Remove any duplicate or wrong widgets first
            java.util.List<BuildingOutputWidget> widgets = new java.util.ArrayList<>();
            BuildingOutputWidget correctWidget = null;
            for (var child : this.children()) {
                if (child instanceof BuildingOutputWidget) {
                    BuildingOutputWidget widget = (BuildingOutputWidget) child;
                    widgets.add(widget);
                    // Find the widget that matches the current building
                    if (currentBuilding != null && widget.getBuilding() != null &&
                        widget.getBuilding().getId().equals(currentBuilding.getId())) {
                        correctWidget = widget;
                    }
                }
            }
            
            // Remove all widgets that don't match the current building
            for (BuildingOutputWidget widget : widgets) {
                if (widget != correctWidget) {
                    try {
                        // Aggressive removal - use multiple methods
                        this.remove(widget);
                        this.children().remove(widget);
                        com.secretasain.settlements.SettlementsMod.LOGGER.info("Removed duplicate/wrong BuildingOutputWidget for building: {}", 
                            widget.getBuilding() != null ? widget.getBuilding().getId() : "null");
                    } catch (Exception e) {
                        // Try to remove from children even if remove() fails
                        this.children().remove(widget);
                    }
                }
            }
            
            // Final cleanup pass - remove any remaining BuildingOutputWidget instances that don't match
            java.util.List<net.minecraft.client.gui.Element> finalCleanup = new java.util.ArrayList<>(this.children());
            for (net.minecraft.client.gui.Element child : finalCleanup) {
                if (child instanceof BuildingOutputWidget) {
                    BuildingOutputWidget widget = (BuildingOutputWidget) child;
                    if (widget != correctWidget) {
                        try {
                            this.remove(widget);
                            this.children().remove(widget);
                            com.secretasain.settlements.SettlementsMod.LOGGER.info("Final cleanup in render: Removed BuildingOutputWidget for building: {}", 
                                widget.getBuilding() != null ? widget.getBuilding().getId() : "null");
                        } catch (Exception e) {
                            this.children().remove(widget);
                        }
                    }
                }
            }
            
            // Update reference to the correct widget
            if (correctWidget != null) {
                buildingOutputWidget = correctWidget;
            } else if (currentBuilding == null) {
                // No building selected, clear reference
                buildingOutputWidget = null;
            }
            
            if (buildingOutputWidget != null && this.children().contains(buildingOutputWidget)) {
                buildingOutputWidget.render(context, mouseX, mouseY, delta);
            }
        }
        
        // Render warband panels if warband tab is active
        if (activeTab == TabType.WARBAND) {
            if (warriorPanel != null) {
                warriorPanel.render(context, mouseX, mouseY, delta);
            }
            if (priestPanel != null) {
                priestPanel.render(context, mouseX, mouseY, delta);
            }
            if (magePanel != null) {
                magePanel.render(context, mouseX, mouseY, delta);
            }
        }
        
        // Explicitly render building list widget if it exists and is in children (for debugging)
        if (activeTab == TabType.BUILDINGS && buildingListWidget != null) {
            if (this.children().contains(buildingListWidget)) {
                // Widget is in children, parent's render should handle it, but log for debugging
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("BuildingListWidget is in children and should be rendered by parent");
            } else {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("BuildingListWidget exists but NOT in children - re-adding");
                this.addDrawableChild(buildingListWidget);
            }
        }
        
        // CRITICAL: Explicitly ensure buttons are visible and in children list during render
        // This ensures they're rendered even if something removed them
        // Also re-position them in case screen wasn't initialized when they were first positioned
        if (activeTab == TabType.BUILDINGS && this.width > 0 && this.height > 0) {
            // Re-position buttons to ensure they're in the correct location
            positionCheckMaterialsButton();
            positionBuildingActionButtons(); // This also positions unload button
            
            if (checkMaterialsButton != null) {
                checkMaterialsButton.visible = true;
                checkMaterialsButton.active = true;
                if (!this.children().contains(checkMaterialsButton)) {
                    this.addDrawableChild(checkMaterialsButton);
                    com.secretasain.settlements.SettlementsMod.LOGGER.warn("Check materials button was missing from children during render - re-added!");
                }
            }
            if (unloadInventoryButton != null) {
                unloadInventoryButton.visible = true;
                unloadInventoryButton.active = true;
                if (!this.children().contains(unloadInventoryButton)) {
                    this.addDrawableChild(unloadInventoryButton);
                    com.secretasain.settlements.SettlementsMod.LOGGER.warn("Unload inventory button was missing from children during render - re-added!");
                }
            }
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
                // NOTE: Removed per-frame updateEntries() calls - widgets now refresh only when data changes
                // This improves performance and prevents unnecessary updates
                if (buildingListWidget != null && this.children().contains(buildingListWidget)) {
                    updateBuildingActionButtons(); // Update buttons based on selection
                    
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
            case WARBAND:
                // Draw title
                context.drawText(
                    this.textRenderer, 
                    Text.translatable("settlements.warband.title"), 
                    x + 10, 
                    y, 
                    0xFFFFFF, 
                    false
                );
                
                // Show message if no barracks buildings
                if (warbandBarracksListWidget == null || settlement.getBuildings().stream()
                    .noneMatch(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED &&
                                   b.getStructureType().getPath().toLowerCase().contains("barracks"))) {
                    context.drawText(
                        this.textRenderer, 
                        Text.translatable("settlements.warband.no_barracks"), 
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
                // NOTE: Removed per-frame updateEntries() calls - widgets now refresh only when data changes
                // Refresh building assignment sidebar to update assignment counts
                if (buildingSelectionWidget != null && !showingBuildingSelection) {
                    // Only refresh if needed (not every frame)
                    // buildingSelectionWidget.updateEntries(); // Removed per-frame refresh
                }
                
                // Update building output widget if building selection changed
                // Widget creation is handled by selection callbacks, not in render loop
                // Only check if widget needs to be removed (if no building selected)
                if (buildingSelectionWidget != null) {
                    com.secretasain.settlements.settlement.Building selected = buildingSelectionWidget.getSelectedBuilding();
                    if (selected == null && buildingOutputWidget != null) {
                        // No building selected but widget exists - remove it
                        this.children().removeIf(child -> child instanceof BuildingOutputWidget);
                        buildingOutputWidget = null;
                    }
                }
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
        
        // Handle Overview and Settings tabs - close all widgets and show only main UI
        if (tab == TabType.OVERVIEW || tab == TabType.SETTINGS) {
            // Close other tab widgets when switching to Overview or Settings
            closeBuildingWidgets();
            closeVillagerWidgets();
            closeWarbandWidgets();
            
            // Overview and Settings tabs display information directly from settlement object
            // The settlement object is kept up-to-date through various network packets,
            // so the displayed information will reflect the current state
            // No additional refresh needed - data is read fresh on each render
            return;
        }
        
        // Handle Warband tab
        if (tab == TabType.WARBAND) {
            // Close other tab widgets when switching to warband tab
            closeBuildingWidgets();
            closeVillagerWidgets();
            
            // Ensure buildingListWidget is removed (it shouldn't be visible on warband tab)
            if (buildingListWidget != null) {
                try {
                    this.remove(buildingListWidget);
                } catch (Exception e) {
                    // Ignore if already removed
                }
                this.children().removeIf(child -> child == buildingListWidget);
                buildingListWidget = null;
            }
            
            // Create and show warband tab content
            createWarbandTabContent();
            
            // Request NPC sync when opening warband tab
            if (selectedBarracks != null && this.client.player != null && this.client.getNetworkHandler() != null) {
                com.secretasain.settlements.network.RequestWarbandNpcsPacketClient.send(selectedBarracks.getId());
            }
            return;
        }
        
        // When switching tabs, refresh widgets to ensure they show current data
        refreshCurrentTabWidgets();
        
        // Show/hide structure list sidebar based on active tab
        boolean showBuildings = (tab == TabType.BUILDINGS);
        
        if (showBuildings) {
            // Close other tab widgets when switching to buildings tab
            closeVillagerWidgets();
            closeWarbandWidgets();
            
            // Create and add structure selection widget when Buildings tab is active
            createStructureListWidget();
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
            // Always recreate the widget to ensure it has the latest building data
            if (buildingListWidget != null) {
                // Remove old widget if it exists
                this.remove(buildingListWidget);
                buildingListWidget = null;
            }
            createBuildingListWidget();
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
            // Close other tab widgets when switching to villagers tab
            closeBuildingWidgets();
            closeWarbandWidgets();
            
            // Create and show building assignment widget as a permanent sidebar (similar to structure list widget)
            createBuildingAssignmentSidebar();
            
            // Position and show villager list widget when Villagers tab is active
            // Use same position and dimensions as building list widget (bottom of screen)
            if (villagerListWidget != null) {
                int screenWidth = 400;
                int screenHeight = 280;
                int x = (this.width - screenWidth) / 2;
                int y = (this.height - screenHeight) / 2;
                
                // Position villager list in main content area (at the bottom) - same as building list
                int listX = x - 70; // Left side with padding (same as building list)
                int listHeight = 100; // Fixed height for the widget (same as building list)
                int bottomOffset = -110; // Space from bottom edge (for buttons) (same as building list)
                int listY = (y + screenHeight) - listHeight - bottomOffset; // Position from bottom (same as building list)
                int listWidth = screenWidth - 20; // Leave padding on sides (same as building list)
                
                // Recreate widget with correct dimensions to match building list
                // Remove old widget
                this.remove(villagerListWidget);
                
                // Create new widget with correct dimensions
                villagerListWidget = new VillagerListWidget(
                    this.client,
                    listWidth,
                    listHeight,
                    listY,
                    listY + listHeight,
                    65, // Increased item height to accommodate assignment location info
                    settlement.getVillagers(),
                    settlement.getGolems(),
                    settlement
                );
                
                // Restore callbacks (same as in init())
                villagerListWidget.setOnHireCallback(villager -> {
                    // Optimistically update local settlement data immediately for instant UI feedback
                    villager.setEmployed(true);
                    
                    // Update UI immediately
                    villagerListWidget.updateEntries();
                    
                    // Send packet to server
                    com.secretasain.settlements.network.HireFireVillagerPacketClient.send(
                        villager.getEntityId(), 
                        settlement.getId(), 
                        true
                    );
                });
                villagerListWidget.setOnFireCallback(villager -> {
                    // Optimistically update local settlement data immediately for instant UI feedback
                    villager.setEmployed(false);
                    villager.setAssignedBuildingId(null); // Unassign when fired
                    
                    // Update UI immediately
                    villagerListWidget.updateEntries();
                    
                    // Send packet to server
                    com.secretasain.settlements.network.HireFireVillagerPacketClient.send(
                        villager.getEntityId(), 
                        settlement.getId(), 
                        false
                    );
                });
                villagerListWidget.setOnAssignWorkCallback((villager, buildingId) -> {
                    if (villager.isAssigned() && buildingId != null) {
                        // Unassign from current building
                        // Optimistically update local settlement data immediately for instant UI feedback
                        villager.setAssignedBuildingId(null);
                        
                        // Update UI immediately
                        villagerListWidget.updateEntries();
                        
                        // Send packet to server
                        com.secretasain.settlements.network.AssignWorkPacketClient.send(
                            settlement.getId(),
                            villager.getEntityId(),
                            buildingId, // Current building ID
                            false
                        );
                    } else if (!villager.isAssigned() && buildingId == null) {
                        // Show building selection dialog
                        showBuildingSelectionDialog(villager);
                    }
                });
                
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
            // CRITICAL: Position button AFTER ensuring screen is initialized
            if (checkMaterialsButton != null) {
                positionCheckMaterialsButton();
                // Set visible and active AFTER positioning
                checkMaterialsButton.visible = true;
                checkMaterialsButton.active = true;
                // Ensure button is in children list and moved to end (renders on top)
                if (this.children().contains(checkMaterialsButton)) {
                    this.remove(checkMaterialsButton);
                }
                this.addDrawableChild(checkMaterialsButton);
                // Re-position after adding to ensure position is set correctly
                positionCheckMaterialsButton();
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials button added to children, visible: {}, active: {}, pos: ({}, {})", 
                    checkMaterialsButton.visible, checkMaterialsButton.active, checkMaterialsButton.getX(), checkMaterialsButton.getY());
            }
            
            // Position and show cancel/remove buttons (this also positions unload button)
            positionBuildingActionButtons();
            
            // Make unload button always visible on Buildings tab (like check materials button)
            if (unloadInventoryButton != null) {
                unloadInventoryButton.visible = true;
                unloadInventoryButton.active = true;
                // Ensure button is in children list and moved to end (renders on top)
                if (this.children().contains(unloadInventoryButton)) {
                    this.remove(unloadInventoryButton);
                }
                this.addDrawableChild(unloadInventoryButton);
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Unload inventory button added to children, visible: {}, active: {}, pos: ({}, {})", 
                    unloadInventoryButton.visible, unloadInventoryButton.active, unloadInventoryButton.getX(), unloadInventoryButton.getY());
            }
            
            updateBuildingActionButtons();
            
            // CRITICAL: Ensure buttons remain visible after updateBuildingActionButtons
            // (updateBuildingActionButtons doesn't touch these buttons, but be safe)
            if (checkMaterialsButton != null) {
                checkMaterialsButton.visible = true;
                checkMaterialsButton.active = true;
            }
            if (unloadInventoryButton != null) {
                unloadInventoryButton.visible = true;
                unloadInventoryButton.active = true;
            }
            
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
            
            // Widget creation is handled by selection callbacks, not in render loop
            // Only ensure widget is removed if no building is selected
            if (buildingSelectionWidget != null) {
                com.secretasain.settlements.settlement.Building selected = buildingSelectionWidget.getSelectedBuilding();
                if (selected == null && buildingOutputWidget != null) {
                    // No building selected but widget exists - remove it
                    this.children().removeIf(child -> child instanceof BuildingOutputWidget);
                    buildingOutputWidget = null;
                }
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
            // removeBuildingButton removed - delete functionality is now in BuildingListWidget
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
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding; // Start below title bar with padding
        // Use same height calculation as BuildingSelectionWidget (fills remaining space with padding)
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarPadding * 2; // Fill remaining space with padding
        
        // Calculate centered width for both widget and button
        int contentWidth = sidebarWidth - sidebarPadding * 2; // Width with padding on both sides
        
        this.structureListWidget = new StructureListWidget(
            this.client,
            contentWidth, // Width - same as button for perfect alignment
            sidebarListHeight, // Height of list area - same as BuildingSelectionWidget
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
        
        // Position build button at bottom of sidebar - moved lower with margin to prevent clipping
        // Button uses the SAME offset as widget to ensure perfect alignment
        if (buildStructureButton != null) {
            int sidebarButtonHeight = 25; // Height for button at bottom
            int sidebarButtonMargin = 10; // Margin from bottom to prevent UI clipping
            int buttonDownOffset = 50; // Additional offset to move button down
            int buildStructureButtonXOffset = 0; // Always keep at 0 for perfect alignment
            // Both widget and button use widgetXOffset for alignment
            int buttonX = sidebarX + sidebarPadding + buildStructureButtonXOffset; // Same X position and offset as structure list widget
            int buttonWidth = contentWidth; // Same width as structure list widget for perfect alignment
            // Position button lower with margin from bottom, moved down by additional offset
            int buttonY = sidebarY + sidebarHeight - sidebarButtonHeight - sidebarButtonMargin + buttonDownOffset; // Position from bottom with margin, moved down 50px
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
     * Renders the building assignment sidebar.
     * Only called when Villagers tab is active.
     */
    private void renderBuildingAssignmentSidebar(DrawContext context, int mainX, int mainY) {
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
            Text.translatable("settlements.ui.villagers.buildings"), // You may need to add this translation key
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
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("createBuildingListWidget called but not on BUILDINGS tab (activeTab: {})", activeTab);
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
            36, // Item height - increased to fit building name and status text
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
            
            // Refresh material list widget with updated settlement materials
            if (materialListWidget != null && this.children().contains(materialListWidget)) {
                materialListWidget.updateAvailableMaterials(settlement.getMaterials());
                materialListWidget.updateEntries();
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
            lastSelectedBuilding = building;
            
            // Create material widget when building is selected
            if (activeTab == TabType.BUILDINGS) {
                if (this.client != null) {
                    // Defer to next tick to avoid ConcurrentModificationException
                    this.client.execute(() -> {
                        if (activeTab == TabType.BUILDINGS) {
                            createMaterialListWidget();
                            updateBuildingActionButtons();
                        }
                    });
                } else {
                    createMaterialListWidget();
                    updateBuildingActionButtons();
                }
            }
        });
        
        // Set available materials for the building list widget
        this.buildingListWidget.setAvailableMaterials(settlement.getMaterials());
        
        // Ensure entries are updated after widget is fully set up
        this.buildingListWidget.updateEntries();
        
        // Auto-select first building if available (so materials are shown by default)
        if (!this.buildingListWidget.children().isEmpty()) {
            // Find first BuildingEntry (skip group entries)
            for (var entryObj : this.buildingListWidget.children()) {
                if (entryObj instanceof com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry) {
                    @SuppressWarnings("unchecked")
                    var entry = (net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget.Entry<?>) entryObj;
                    this.buildingListWidget.setSelected(entry);
                    com.secretasain.settlements.settlement.Building firstBuilding = this.buildingListWidget.getSelectedBuilding();
                    if (firstBuilding != null) {
                        lastSelectedBuilding = firstBuilding;
                        // Manually trigger the callback since programmatic selection doesn't fire it
                        this.buildingListWidget.triggerSelectionChanged(firstBuilding);
                    }
                    break;
                }
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
        
        // Remove button removed - delete functionality is now in BuildingListWidget (red X button)
        
        // Unload inventory button - positioned directly under check materials button
        if (unloadInventoryButton != null) {
            // Use the exact same calculation as positionCheckMaterialsButton
            // Check materials button is positioned at: x + screenWidth - 150, y + 28 + 14
            // Position unload button directly below it (25 pixels down = 20 button height + 5 spacing)
            int unloadButtonX = x + screenWidth - 150; // Same X as check materials button
            int unloadButtonY = y + 28 + 14 + 25; // Check materials Y (y + 28 + 14) + 25 pixels below
            
            // Validate button position is on screen
            if (unloadButtonX < 0 || unloadButtonY < 0 || unloadButtonX > this.width || unloadButtonY > this.height) {
                com.secretasain.settlements.SettlementsMod.LOGGER.warn("Unload inventory button position ({}, {}) is outside screen bounds ({}x{})", 
                    unloadButtonX, unloadButtonY, this.width, this.height);
            }
            
            unloadInventoryButton.setX(unloadButtonX);
            unloadInventoryButton.setY(unloadButtonY);
            unloadInventoryButton.setWidth(140); // Match check materials button width
            // Ensure button is visible (safety check)
            unloadInventoryButton.visible = true;
            unloadInventoryButton.active = true;
            
            // DEBUG: Log button position to verify it's being set
            // com.secretasain.settlements.SettlementsMod.LOGGER.info("Unload inventory button positioned at ({}, {}), width: {}, visible: {}, active: {}, screen: {}x{}", 
            //     unloadButtonX, unloadButtonY, 140, unloadInventoryButton.visible, unloadInventoryButton.active, this.width, this.height);
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
            
            // Remove button removed - delete functionality is now in BuildingListWidget (red X button)
            
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
            // removeBuildingButton removed - delete functionality is now in BuildingListWidget
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
    
    // onRemoveBuildingClicked() removed - delete functionality is now in BuildingListWidget (red X button)
    
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
                var selectedEntry = buildingListWidget.getSelectedOrNull();
                if (selectedEntry instanceof com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry) {
                    com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry buildingEntry = 
                        (com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry) selectedEntry;
                    selectedBuilding = buildingEntry.getBuilding();
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials - fallback to getSelectedOrNull(): {}", 
                        selectedBuilding != null ? selectedBuilding.getId().toString() : "null");
                }
            }
        }
        
        // Debug: Log all buildings in the list and their selection status
        if (buildingListWidget != null && !buildingListWidget.children().isEmpty()) {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Building list has {} entries", buildingListWidget.children().size());
            var currentlySelected = buildingListWidget.getSelectedOrNull();
            com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry selectedBuildingEntry = null;
            if (currentlySelected instanceof com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry) {
                selectedBuildingEntry = (com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry) currentlySelected;
            }
            for (int i = 0; i < buildingListWidget.children().size(); i++) {
                var entryObj = buildingListWidget.children().get(i);
                if (!(entryObj instanceof com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry)) {
                    continue; // Skip group entries in debug logging
                }
                com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry entry = 
                    (com.secretasain.settlements.ui.BuildingListWidget.BuildingEntry) entryObj;
                com.secretasain.settlements.settlement.Building b = entry.getBuilding();
                boolean isSelected = selectedBuildingEntry == entry;
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
        
        // CRITICAL: Always recreate building list widget if it exists to reflect changes
        // This ensures newly placed buildings appear even if we're on a different tab
        if (buildingListWidget != null) {
            // Remove old widget
            this.remove(buildingListWidget);
            buildingListWidget = null;
        }
        
        // Recreate with updated settlement data if we're on Buildings tab
        if (activeTab == TabType.BUILDINGS) {
            createBuildingListWidget();
            // Ensure widget is added to children
            if (buildingListWidget != null && !this.children().contains(buildingListWidget)) {
                this.addDrawableChild(buildingListWidget);
            }
        }
        
        // Refresh villager list widget if on Villagers tab
        if (activeTab == TabType.VILLAGERS && villagerListWidget != null) {
            villagerListWidget.updateEntries();
        }
        
        // Refresh building selection widget if it exists
        if (buildingSelectionWidget != null && !showingBuildingSelection) {
            buildingSelectionWidget.updateEntries();
        }
        
        // Refresh material list widget if it exists (materials might have changed)
        if (materialListWidget != null && this.children().contains(materialListWidget)) {
            materialListWidget.updateAvailableMaterials(settlement.getMaterials());
            materialListWidget.updateEntries();
        }
        
        // Update material list widget if it exists
        createMaterialListWidget();
        
        // Update action buttons (this will hide unload button if materials are now empty)
        updateBuildingActionButtons();
        
        // Update widgets based on active tab to show real-time changes
        if (activeTab == TabType.VILLAGERS) {
            // Update building assignment sidebar (to refresh assignment counts)
            if (buildingSelectionWidget != null && !showingBuildingSelection) {
                buildingSelectionWidget.updateEntries();
            }
            // Widget creation is handled by selection callbacks, not here
            // The selection callback will create the widget when a building is selected
            // Update villager list widget
            if (villagerListWidget != null) {
                villagerListWidget.updateEntries();
            }
        } else if (activeTab == TabType.BUILDINGS) {
            // Update building list widget (to refresh assignment counts, status, etc.)
            if (buildingListWidget != null) {
                buildingListWidget.updateEntries();
            }
            // Update material list widget if building is selected
            if (materialListWidget != null && this.children().contains(materialListWidget)) {
                materialListWidget.updateAvailableMaterials(settlement.getMaterials());
                materialListWidget.updateEntries();
            }
        } else {
            // Update villager list widget for other tabs if it exists
            if (villagerListWidget != null) {
                villagerListWidget.updateEntries();
            }
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Updated settlement data in UI - buildings: {}, materials: {}", 
            updatedSettlement.getBuildings().size(), updatedSettlement.getMaterials().size());
    }
    
    /**
     * Creates and shows the building assignment sidebar that's always visible on Villagers tab.
     * Similar to the structure list widget on Buildings tab.
     */
    private void createBuildingAssignmentSidebar() {
        if (activeTab != TabType.VILLAGERS) {
            return; // Only create when on Villagers tab
        }
        
        // Get available buildings (completed only)
        List<com.secretasain.settlements.settlement.Building> availableBuildings = 
            settlement.getBuildings().stream()
                .filter(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED)
                .collect(java.util.stream.Collectors.toList());
        
        // Calculate sidebar position (same as structure sidebar on Buildings tab)
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        int sidebarWidth = 180;
        int sidebarX = x - sidebarWidth - 15; // Same position as structure sidebar
        int sidebarY = y; // Align exactly with main window top
        int sidebarHeight = screenHeight; // Match main window height exactly
        
        int sidebarTitleHeight = 20;
        int sidebarPadding = 5;
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding; // Start below title bar
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarPadding * 2; // Fill remaining space with padding
        
        // Calculate content width (same as structure sidebar)
        int contentWidth = sidebarWidth - sidebarPadding * 2;
        
        // Remove old widget if it exists (but don't close dialog-style widget if it's showing)
        if (buildingSelectionWidget != null && !showingBuildingSelection) {
            // Only remove if it's the permanent sidebar version, not the dialog version
            this.remove(buildingSelectionWidget);
            buildingSelectionWidget = null;
        }
        
        // Create building selection widget with same dimensions as structure sidebar
        // Pass null for villager since this is just for display, not assignment
        buildingSelectionWidget = new BuildingSelectionWidget(
            this.client,
            contentWidth,
            sidebarListHeight,
            sidebarListY,
            sidebarListY + sidebarListHeight,
            26,
            availableBuildings,
            null, // No specific villager - this is just for viewing assignments
            settlement
        );
        // Use same X offset as structure list widget for perfect alignment
        int widgetXOffset = 22; // Same offset as structure list widget
        buildingSelectionWidget.setLeftPos(sidebarX + sidebarPadding + widgetXOffset);
        
        // Set callback to handle building selection (for assigning villagers)
        buildingSelectionWidget.setOnBuildingSelected((villager, buildingId) -> {
            // If a villager was provided, assign them to the selected building
            if (villager != null && buildingId != null) {
                com.secretasain.settlements.network.AssignWorkPacketClient.send(
                    settlement.getId(),
                    villager.getEntityId(),
                    buildingId,
                    true
                );
                // Close dialog if it was open
                showingBuildingSelection = false;
                // Refresh villager list
                if (villagerListWidget != null) {
                    this.client.execute(() -> {
                        villagerListWidget.updateEntries();
                    });
                }
            }
        });
        
        // Set callback for selection changes (to show output widget)
        buildingSelectionWidget.setOnSelectionChanged((building) -> {
            // Update the output widget when a building is selected
            if (activeTab == TabType.VILLAGERS) {
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Building selection changed: {}", 
                    building != null ? building.getId() : "null");
                
                // CRITICAL: Remove ALL existing widgets FIRST using our tracking set
                // This ensures old widgets are always removed when switching buildings
                removeAllBuildingOutputWidgets();
                
                // NOW check if we should skip recreation (after cleanup, this should rarely be true)
                if (building != null) {
                    // Check if a widget for this building still exists (shouldn't happen after cleanup, but check anyway)
                    for (net.minecraft.client.gui.Element child : this.children()) {
                        if (child instanceof BuildingOutputWidget) {
                            BuildingOutputWidget widget = (BuildingOutputWidget) child;
                            if (widget.getBuilding() != null && 
                                widget.getBuilding().getId().equals(building.getId())) {
                                // Widget for this building still exists (shouldn't happen, but handle it)
                                buildingOutputWidget = widget;
                                com.secretasain.settlements.SettlementsMod.LOGGER.info("Widget for building {} still exists after cleanup, reusing it", building.getId());
                                return;
                            }
                        }
                    }
                }
                
                // Clear pending data for old building (only if switching to different building)
                if (building == null || buildingOutputWidget == null || 
                    buildingOutputWidget.getBuilding() == null ||
                    !buildingOutputWidget.getBuilding().getId().equals(building.getId())) {
                    // Only clear if we're switching to a different building
                    // Keep pending data if it's for the newly selected building
                    UUID newBuildingId = building != null ? building.getId() : null;
                    if (newBuildingId != null) {
                        // Keep pending data for the new building, clear others
                        PendingOutputData keepData = pendingOutputData.get(newBuildingId);
                        pendingOutputData.clear();
                        if (keepData != null) {
                            pendingOutputData.put(newBuildingId, keepData);
                        }
                    } else {
                        pendingOutputData.clear();
                    }
                }
                
                lastSelectedBuildingForOutput = building;
                
                // Always create widget when building is selected (even if null, will show "No outputs")
                if (building != null) {
                    createBuildingOutputWidget();
                } else {
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("No building selected, widget removed");
                }
            }
        });
        
        // Add widget to children if not already there
        if (!this.children().contains(buildingSelectionWidget)) {
            this.addDrawableChild(buildingSelectionWidget);
        }
        
        // Update entries to refresh assignment counts
        buildingSelectionWidget.updateEntries();
        
        // CRITICAL: Remove ALL existing BuildingOutputWidget instances before auto-selecting
        // This prevents widgets from previous tab switches or selections from persisting
        removeAllBuildingOutputWidgets();
        
        // Auto-select first building if available (so output widget is shown by default)
        // This must happen AFTER updateEntries() so the children list is populated
        if (!buildingSelectionWidget.children().isEmpty()) {
            var firstEntry = buildingSelectionWidget.children().get(0);
            if (firstEntry instanceof BuildingSelectionWidget.BuildingEntry) {
                BuildingSelectionWidget.BuildingEntry buildingEntry = (BuildingSelectionWidget.BuildingEntry) firstEntry;
                buildingSelectionWidget.setSelected(buildingEntry);
                // Trigger the selection callback to create the widget
                if (buildingSelectionWidget.onSelectionChanged != null) {
                    com.secretasain.settlements.settlement.Building firstBuilding = buildingEntry.getBuilding();
                    if (firstBuilding != null) {
                        com.secretasain.settlements.SettlementsMod.LOGGER.info("Auto-selecting first building: {} ({})", 
                            firstBuilding.getId(), firstBuilding.getStructureType());
                        buildingSelectionWidget.onSelectionChanged.accept(firstBuilding);
                    }
                }
            }
        } else {
            com.secretasain.settlements.SettlementsMod.LOGGER.info("No buildings available for selection in BuildingSelectionWidget");
        }
    }
    
    /**
     * Shows a building selection dialog for assigning a villager to work.
     */
    private void showBuildingSelectionDialog(VillagerData villager) {
        // Remove permanent sidebar widget first (if it exists and not already showing dialog)
        if (buildingSelectionWidget != null && !showingBuildingSelection) {
            this.remove(buildingSelectionWidget);
            buildingSelectionWidget = null;
        }
        
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
            // Recreate permanent sidebar if no buildings available
            if (activeTab == TabType.VILLAGERS) {
                createBuildingAssignmentSidebar();
            }
            return;
        }
        
        // Position building selection dialog in the same location as the structure sidebar
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        int sidebarWidth = 180;
        int sidebarX = x - sidebarWidth - 15; // Same position as structure sidebar
        int sidebarY = y; // Align exactly with main window top
        int sidebarHeight = screenHeight; // Match main window height exactly
        
        int sidebarTitleHeight = 20;
        int sidebarPadding = 5;
        int sidebarButtonHeight = 25;
        int sidebarButtonPadding = 5;
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding; // Start below title bar
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarButtonHeight - sidebarPadding - sidebarButtonPadding; // Fill remaining space
        
        // Calculate content width (same as structure sidebar)
        int contentWidth = sidebarWidth - sidebarPadding * 2;
        
        // Create building selection widget with same dimensions as structure sidebar
        buildingSelectionWidget = new BuildingSelectionWidget(
            this.client,
            contentWidth,
            sidebarListHeight,
            sidebarListY,
            sidebarListY + sidebarListHeight,
            26,
            availableBuildings,
            villager,
            settlement
        );
        // Use same X offset as structure list widget for perfect alignment
        int widgetXOffset = 22; // Same offset as structure list widget
        buildingSelectionWidget.setLeftPos(sidebarX + sidebarPadding + widgetXOffset);
        
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
            // Recreate permanent sidebar after closing dialog
            if (activeTab == TabType.VILLAGERS) {
                createBuildingAssignmentSidebar();
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
     * Shows a wall station selection dialog for assigning a golem to a wall station.
     */
    private void showWallStationSelectionDialog(GolemData golem) {
        // Get available wall stations (completed wall buildings only)
        List<com.secretasain.settlements.settlement.Building> availableWallStations = 
            com.secretasain.settlements.settlement.WallStationDetector.findWallStations(settlement, null);
        
        if (availableWallStations.isEmpty()) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    net.minecraft.text.Text.translatable("settlements.golem.no_wall_stations_available"),
                    false
                );
            }
            return;
        }
        
        // Position building selection dialog in the same location as the structure sidebar
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        int sidebarWidth = 180;
        int sidebarX = x - sidebarWidth - 15;
        int sidebarY = y;
        int sidebarHeight = screenHeight;
        
        int sidebarTitleHeight = 20;
        int sidebarPadding = 5;
        int sidebarButtonHeight = 25;
        int sidebarButtonPadding = 5;
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding;
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarButtonHeight - sidebarPadding - sidebarButtonPadding;
        
        int contentWidth = sidebarWidth - sidebarPadding * 2;
        
        // Create building selection widget (reuse BuildingSelectionWidget for wall stations)
        buildingSelectionWidget = new BuildingSelectionWidget(
            this.client,
            contentWidth,
            sidebarListHeight,
            sidebarListY,
            sidebarListY + sidebarListHeight,
            26,
            availableWallStations,
            null, // No villager for golem assignment
            settlement
        );
        
        int widgetXOffset = 22;
        buildingSelectionWidget.setLeftPos(sidebarX + sidebarPadding + widgetXOffset);
        
        buildingSelectionWidget.setOnBuildingSelected((v, buildingId) -> {
            // Assign golem to selected wall station
            com.secretasain.settlements.network.AssignGolemPacketClient.send(
                settlement.getId(),
                golem.getEntityId(),
                buildingId,
                true
            );
            // Close dialog
            showingBuildingSelection = false;
            if (buildingSelectionWidget != null) {
                this.remove(buildingSelectionWidget);
                buildingSelectionWidget = null;
            }
            // Recreate permanent sidebar after closing dialog
            if (activeTab == TabType.VILLAGERS) {
                createBuildingAssignmentSidebar();
            }
            // Refresh villager list (which now includes golems)
            this.client.execute(() -> {
                if (villagerListWidget != null) {
                    villagerListWidget.updateEntries();
                }
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
            buildingListWidget.updateEntries(); // Refresh to show updated material availability
        }
        
        // Refresh material list widget to show updated available materials
        if (materialListWidget != null && this.children().contains(materialListWidget)) {
            materialListWidget.updateAvailableMaterials(settlement.getMaterials());
            materialListWidget.updateEntries();
        }
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
        
        if (checkMaterialsButton == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot position check materials button - button is null!");
            return;
        }
        
        // Check if screen is initialized
        if (this.width <= 0 || this.height <= 0) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot position check materials button - screen not initialized (width={}, height={})", 
                this.width, this.height);
            return;
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Position button in the top-right area of the Buildings tab
        int buttonX = x + screenWidth - 150; // Right side with padding
        int buttonY = y + 28 + 14; // Below the title
        
        // Validate button position is on screen
        if (buttonX < 0 || buttonY < 0 || buttonX > this.width || buttonY > this.height) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Check materials button position ({}, {}) is outside screen bounds ({}x{})", 
                buttonX, buttonY, this.width, this.height);
        }
        
        checkMaterialsButton.setX(buttonX);
        checkMaterialsButton.setY(buttonY);
        checkMaterialsButton.setWidth(140);
        // Ensure button is visible (safety check)
        checkMaterialsButton.visible = true;
        checkMaterialsButton.active = true;
        // com.secretasain.settlements.SettlementsMod.LOGGER.info("Check materials button positioned at ({}, {}), width: {}, visible: {}, active: {}, screen: {}x{}", 
        //     buttonX, buttonY, 140, checkMaterialsButton.visible, checkMaterialsButton.active, this.width, this.height);
        
        // Verify position was actually set
        // if (checkMaterialsButton.getX() != buttonX || checkMaterialsButton.getY() != buttonY) {
        //     com.secretasain.settlements.SettlementsMod.LOGGER.error("Check materials button position was NOT set correctly! Expected ({}, {}), got ({}, {})", 
        //         buttonX, buttonY, checkMaterialsButton.getX(), checkMaterialsButton.getY());
        // }
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
        java.util.Map<net.minecraft.util.Identifier, Integer> requiredMaterials;
        if (selectedBuilding != null) {
            requiredMaterials = new java.util.HashMap<>(selectedBuilding.getRequiredMaterials());
        } else {
            requiredMaterials = new java.util.HashMap<>();
        }
        
        // Always create the widget, even if empty (will show "No materials required" message)
        java.util.Map<net.minecraft.util.Identifier, Integer> materialsToShow = requiredMaterials;
        
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
    
    /**
     * Creates and displays the building output widget when a building is selected in Villagers tab.
     * Similar to MaterialListWidget but shows building outputs instead of required materials.
     */
    private void createBuildingOutputWidget() {
        if (activeTab != TabType.VILLAGERS) {
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("createBuildingOutputWidget called but not in Villagers tab (activeTab={})", activeTab);
            return;
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("createBuildingOutputWidget called for Villagers tab");
        
        // Get selected building from BuildingSelectionWidget
        com.secretasain.settlements.settlement.Building selectedBuilding = null;
        if (buildingSelectionWidget != null) {
            selectedBuilding = buildingSelectionWidget.getSelectedBuilding();
        }
        
        if (selectedBuilding == null) {
            // No building selected, remove widget if it exists
            com.secretasain.settlements.SettlementsMod.LOGGER.info("createBuildingOutputWidget: No building selected, removing widget");
            this.children().removeIf(child -> child instanceof BuildingOutputWidget);
            buildingOutputWidget = null;
            lastSelectedBuildingForOutput = null;
            return;
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("createBuildingOutputWidget: Building selected: {} ({})", 
            selectedBuilding.getId(), selectedBuilding.getStructureType());
        
        // CRITICAL: Always remove ALL existing widgets first to prevent overlapping
        // This ensures we don't show data from the wrong building
        removeAllBuildingOutputWidgets();
        
        // Note: lastSelectedBuildingForOutput is tracked but not used for widget creation
        // Widget is created based on current selection from BuildingSelectionWidget
        
        // Determine building type from structure name
        String structureName = getStructureName(selectedBuilding.getStructureType());
        String buildingType = determineBuildingType(structureName);
        
        // Always create widget, even if buildingType is null (will show "No outputs configured")
        // This fixes the bug where empty buildings don't show the widget
        if (buildingType == null) {
            buildingType = "unknown"; // Use placeholder so widget is created
        }
        
        // Position widget same as MaterialListWidget (right side of screen)
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Use same position and size as MaterialListWidget
        int defaultX = x + screenWidth + 30; // Right side
        int defaultY = y + 50; // Below title
        int defaultWidth = 200; // Same width
        int defaultHeight = 180; // Same height
        
        // Create the output widget
        // Remove all existing widgets first using our tracking set
        removeAllBuildingOutputWidgets();
        
        buildingOutputWidget = new BuildingOutputWidget(
            this.client,
            defaultWidth,
            defaultHeight,
            defaultY,
            defaultY + defaultHeight,
            20, // Item height (slightly larger for more info)
            selectedBuilding,
            buildingType
        );
        
        // Set X position
        buildingOutputWidget.setLeftPos(defaultX);
        this.addDrawableChild(buildingOutputWidget);
        
        // Check if we have pending data for this building
        UUID buildingId = selectedBuilding.getId();
        PendingOutputData pending = pendingOutputData.get(buildingId);
        if (pending != null) {
            // Apply pending data immediately
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Found pending data for building {}, applying to widget: outputs={}, farmlandCount={}, cropStats={}", 
                buildingId, pending.outputs != null ? pending.outputs.size() : "null", pending.farmlandCount,
                pending.cropStats != null ? pending.cropStats.size() : "null");
                buildingOutputWidget.updateWithServerData(pending.outputs, pending.farmlandCount, pending.cropStats, pending.boneMealProduced);
            pendingOutputData.remove(buildingId);
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Applied pending output data to newly created widget for building {}", buildingId);
        } else {
            // No pending data - request from server
            // Only request if we don't already have data (avoid duplicate requests)
            com.secretasain.settlements.SettlementsMod.LOGGER.info("No pending data, requesting from server for building {}", buildingId);
            requestBuildingOutputData(selectedBuilding);
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Created BuildingOutputWidget for building {} (type: {}), pending data: {}", 
            selectedBuilding.getId(), buildingType, pending != null ? "yes" : "no");
    }
    
    /**
     * Requests building output data from the server.
     */
    private void requestBuildingOutputData(com.secretasain.settlements.settlement.Building building) {
        if (building == null || settlement == null) {
            com.secretasain.settlements.SettlementsMod.LOGGER.warn("Cannot request building output data: building={}, settlement={}", 
                building, settlement);
            return;
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Requesting building output data: buildingId={}, structureType={}", 
            building.getId(), building.getStructureType());
        
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeUuid(building.getId());
        buf.writeUuid(settlement.getId());
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            com.secretasain.settlements.network.BuildingOutputDataPacket.ID, buf);
    }
    
    /**
     * Pending building output data cache - stores responses that arrive before widget is ready.
     */
    private java.util.Map<UUID, PendingOutputData> pendingOutputData = new java.util.HashMap<>();
    
    /**
     * Stores pending output data for a building.
     */
    private static class PendingOutputData {
        final java.util.List<com.secretasain.settlements.settlement.BuildingOutputConfig.OutputEntry> outputs;
        final int farmlandCount;
        final java.util.List<com.secretasain.settlements.settlement.CropStatistics> cropStats;
        final int boneMealProduced;
        
        PendingOutputData(java.util.List<com.secretasain.settlements.settlement.BuildingOutputConfig.OutputEntry> outputs, 
                         int farmlandCount,
                         java.util.List<com.secretasain.settlements.settlement.CropStatistics> cropStats,
                         int boneMealProduced) {
            this.outputs = outputs;
            this.farmlandCount = farmlandCount;
            this.cropStats = cropStats;
            this.boneMealProduced = boneMealProduced;
        }
    }
    
    /**
     * Updates building output widget with data received from server.
     * If widget doesn't exist yet, stores data as pending to apply when widget is created.
     */
    public void updateBuildingOutputData(UUID buildingId, 
                                        java.util.List<com.secretasain.settlements.settlement.BuildingOutputConfig.OutputEntry> outputs,
                                        int farmlandCount) {
        updateBuildingOutputData(buildingId, outputs, farmlandCount, null, 0);
    }
    
    /**
     * Updates building output widget with data received from server (with crop statistics and bone meal).
     */
    public void updateBuildingOutputData(UUID buildingId, 
                                        java.util.List<com.secretasain.settlements.settlement.BuildingOutputConfig.OutputEntry> outputs,
                                        int farmlandCount,
                                        java.util.List<com.secretasain.settlements.settlement.CropStatistics> cropStats,
                                        int boneMealProduced) {
        com.secretasain.settlements.SettlementsMod.LOGGER.info("updateBuildingOutputData called: buildingId={}, widget={}, widgetBuilding={}, outputs={}, farmlandCount={}", 
            buildingId, 
            buildingOutputWidget != null ? "exists" : "null",
            buildingOutputWidget != null && buildingOutputWidget.getBuilding() != null ? buildingOutputWidget.getBuilding().getId() : "null",
            outputs != null ? outputs.size() : "null",
            farmlandCount);
        
        if (buildingOutputWidget != null) {
            if (buildingOutputWidget.getBuilding() != null && 
                buildingOutputWidget.getBuilding().getId().equals(buildingId)) {
                // Widget exists and matches - apply data immediately
                buildingOutputWidget.updateWithServerData(outputs, farmlandCount, cropStats, boneMealProduced);
                // Clear pending data for this building
                pendingOutputData.remove(buildingId);
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Applied output data to existing widget for building {}", buildingId);
                return;
            } else {
                // Widget exists but for different building - store as pending
                pendingOutputData.put(buildingId, new PendingOutputData(outputs, farmlandCount, cropStats, boneMealProduced));
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Widget exists for different building {}, stored as pending for {}", 
                    buildingOutputWidget.getBuilding() != null ? buildingOutputWidget.getBuilding().getId() : "null", buildingId);
            }
        } else {
            // Widget doesn't exist yet - store as pending
            // This is normal - data can arrive before widget is created
            pendingOutputData.put(buildingId, new PendingOutputData(outputs, farmlandCount, cropStats, boneMealProduced));
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Widget not ready yet, stored as pending: buildingId={} (will apply when widget is created)", buildingId);
            
            // Try to create widget if we're in Villagers tab and have a selected building
            if (activeTab == TabType.VILLAGERS && buildingSelectionWidget != null) {
                com.secretasain.settlements.settlement.Building selected = buildingSelectionWidget.getSelectedBuilding();
                if (selected != null && selected.getId().equals(buildingId)) {
                    // This is the selected building - create widget now
                    com.secretasain.settlements.SettlementsMod.LOGGER.debug("Creating widget for selected building {} since data arrived", buildingId);
                    createBuildingOutputWidget();
                }
            }
        }
    }
    
    /**
     * Refreshes all widgets for the currently active tab.
     * Called when switching tabs or when data changes.
     */
    private void refreshCurrentTabWidgets() {
        switch (activeTab) {
            case BUILDINGS:
                // Refresh building list widget
                if (buildingListWidget != null) {
                    buildingListWidget.setAvailableMaterials(settlement.getMaterials());
                    buildingListWidget.updateEntries();
                }
                // Refresh material list widget
                if (materialListWidget != null && this.children().contains(materialListWidget)) {
                    materialListWidget.updateAvailableMaterials(settlement.getMaterials());
                    materialListWidget.updateEntries();
                }
                // Update action buttons
                updateBuildingActionButtons();
                break;
            case WARBAND:
                // Refresh warband barracks list widget
                if (warbandBarracksListWidget != null) {
                    warbandBarracksListWidget.updateEntries();
                }
                break;
            case VILLAGERS:
                // Refresh villager list widget
                if (villagerListWidget != null) {
                    villagerListWidget.updateEntries();
                }
                // Refresh building selection widget
                if (buildingSelectionWidget != null && !showingBuildingSelection) {
                    buildingSelectionWidget.updateEntries();
                }
                // Building output widget refreshes automatically when data arrives
                break;
            case OVERVIEW:
            case SETTINGS:
                // No widgets to refresh for these tabs
                break;
        }
    }
    
    /**
     * Gets the structure name from the identifier (helper method).
     */
    private String getStructureName(net.minecraft.util.Identifier structureType) {
        String path = structureType.getPath();
        if (path.contains("/")) {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        if (path.endsWith(".nbt")) {
            path = path.substring(0, path.length() - 4);
        }
        return path.toLowerCase();
    }
    
    /**
     * Determines the building type for config lookup (helper method).
     */
    private String determineBuildingType(String structureName) {
        if (structureName.contains("wall") || structureName.contains("fence") || structureName.contains("gate")) {
            return "wall";
        } else if (structureName.contains("smithing") || structureName.contains("smith")) {
            return "smithing";
        } else if (structureName.contains("farm") || structureName.contains("farmland")) {
            return "farm";
        } else if (structureName.contains("cartographer") || structureName.contains("cartography")) {
            return "cartographer";
        }
        return null;
    }
    
    /**
     * Closes all building-related widgets when switching away from Buildings tab.
     */
    private void closeBuildingWidgets() {
        // Remove building widgets from children
        this.children().removeIf(child -> child instanceof StructureListWidget);
        this.children().removeIf(child -> child instanceof BuildingListWidget);
        this.children().removeIf(child -> child instanceof MaterialListWidget);
        
        // Disable and hide structure list widget
        if (structureListWidget != null) {
            structureListWidget.setForceDisable(true);
            structureListWidget.setVisible(false);
            structureListWidget.setAllowRendering(false);
            structureListWidget = null;
        }
        
        // Destroy building list widget - ensure it's removed from children first
        if (buildingListWidget != null) {
            try {
                this.remove(buildingListWidget);
            } catch (Exception e) {
                // Ignore if already removed
            }
            buildingListWidget = null;
        }
        
        // Hide and move building-related buttons off-screen
        if (buildStructureButton != null) {
            buildStructureButton.visible = false;
            buildStructureButton.setX(-1000);
            buildStructureButton.setY(-1000);
        }
        if (cancelBuildingButton != null) {
            cancelBuildingButton.visible = false;
            cancelBuildingButton.active = false;
            cancelBuildingButton.setX(-1000);
            cancelBuildingButton.setY(-1000);
        }
        // removeBuildingButton removed - delete functionality is now in BuildingListWidget (red X button)
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
        
        // Destroy material list widget
        if (materialListWidget != null) {
            this.remove(materialListWidget);
            materialListWidget = null;
        }
    }
    
    /**
     * Removes ALL BuildingOutputWidget instances from the screen and clears tracking.
     * This method uses our explicit tracking set to ensure all widgets are found and removed.
     */
    private void removeAllBuildingOutputWidgets() {
        int removedCount = 0;
        
        // Create a copy of the set to avoid concurrent modification
        java.util.Set<BuildingOutputWidget> widgetsToRemove = new java.util.HashSet<>(allBuildingOutputWidgets);
        
        // Also check the reference
        if (buildingOutputWidget != null && !widgetsToRemove.contains(buildingOutputWidget)) {
            widgetsToRemove.add(buildingOutputWidget);
        }
        
        // Remove all widgets using multiple methods
        for (BuildingOutputWidget widget : widgetsToRemove) {
            try {
                // Method 1: Use Screen.remove() - removes from children and drawables
                this.remove(widget);
                
                // Method 2: Explicitly remove from children list
                this.children().remove(widget);
                
                // Method 3: Remove from our tracking set
                allBuildingOutputWidgets.remove(widget);
                
                removedCount++;
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Removed BuildingOutputWidget for building: {}", 
                    widget.getBuilding() != null ? widget.getBuilding().getId() : "null");
            } catch (Exception e) {
                // Widget might already be removed, but still remove from tracking
                allBuildingOutputWidgets.remove(widget);
                this.children().remove(widget);
                removedCount++;
                com.secretasain.settlements.SettlementsMod.LOGGER.debug("Exception removing widget (likely already removed): {}", e.getMessage());
            }
        }
        
        // Final cleanup pass - check children() for any remaining widgets
        java.util.List<net.minecraft.client.gui.Element> childrenCopy = new java.util.ArrayList<>(this.children());
        for (net.minecraft.client.gui.Element child : childrenCopy) {
            if (child instanceof BuildingOutputWidget) {
                BuildingOutputWidget widget = (BuildingOutputWidget) child;
                try {
                    this.remove(widget);
                    this.children().remove(widget);
                    allBuildingOutputWidgets.remove(widget);
                    removedCount++;
                    com.secretasain.settlements.SettlementsMod.LOGGER.info("Removed additional BuildingOutputWidget from children list");
                } catch (Exception e) {
                    this.children().remove(widget);
                    allBuildingOutputWidgets.remove(widget);
                    removedCount++;
                }
            }
        }
        
        // Force clear the widget reference
        buildingOutputWidget = null;
        
        // CRITICAL: Clear tooltip state to prevent tooltips from persisting after widget removal
        BuildingOutputWidget.clearTooltipState();
        
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Removed all BuildingOutputWidget instances. Total removed: {}", removedCount);
    }
    
    /**
     * Closes all villager-related widgets when switching away from Villagers tab.
     */
    private void closeVillagerWidgets() {
        // Remove building output widgets when switching away from Villagers tab
        removeAllBuildingOutputWidgets();
        
        // Remove villager widgets from children
        this.children().removeIf(child -> child instanceof VillagerListWidget);
        
        // Remove building assignment sidebar (always remove when switching tabs, dialog will be recreated if needed)
        if (buildingSelectionWidget != null) {
            try {
                this.remove(buildingSelectionWidget);
            } catch (Exception e) {
                // Ignore if already removed
            }
            this.children().removeIf(child -> child == buildingSelectionWidget);
            buildingSelectionWidget = null;
        }
        showingBuildingSelection = false; // Reset dialog state
        
        // Remove building output widget
        removeAllBuildingOutputWidgets();
        lastSelectedBuildingForOutput = null;
        // Clear pending data when switching away from Villagers tab
        pendingOutputData.clear();
        // CRITICAL: Clear tooltip state when switching away from Villagers tab
        BuildingOutputWidget.clearTooltipState();
        
        // Hide and move villager-related buttons off-screen
        if (refreshVillagersButton != null) {
            refreshVillagersButton.visible = false;
            refreshVillagersButton.active = false;
            refreshVillagersButton.setX(-1000);
            refreshVillagersButton.setY(-1000);
        }
        
        // Move villager list widget off-screen
        if (villagerListWidget != null) {
            villagerListWidget.setLeftPos(-1000);
            // Widget dimensions are set in constructor, so we just move it off-screen
        }
    }
    
    /**
     * Closes all warband-related widgets when switching away from Warband tab.
     */
    private void closeWarbandWidgets() {
        // Remove warband barracks list widget
        if (warbandBarracksListWidget != null) {
            try {
                this.remove(warbandBarracksListWidget);
            } catch (Exception e) {
                // Ignore if already removed
            }
            this.children().removeIf(child -> child == warbandBarracksListWidget);
            warbandBarracksListWidget = null;
        }
        
        // Remove hired NPC list widget
        if (hiredNpcListWidget != null) {
            try {
                this.remove(hiredNpcListWidget);
            } catch (Exception e) {
                // Ignore if already removed
            }
            this.children().removeIf(child -> child == hiredNpcListWidget);
            hiredNpcListWidget = null;
        }
        
        // Remove all buttons that belong to warband panels
        // Panels store references to their buttons, so we need to remove them explicitly
        if (warriorPanel != null) {
            removePanelButtons(warriorPanel);
            warriorPanel = null;
        }
        if (priestPanel != null) {
            removePanelButtons(priestPanel);
            priestPanel = null;
        }
        if (magePanel != null) {
            removePanelButtons(magePanel);
            magePanel = null;
        }
        
        selectedBarracks = null;
    }
    
    /**
     * Helper method to remove buttons from a warband panel.
     */
    private void removePanelButtons(WarbandPanelWidget panel) {
        // Remove buttons using the panel's button references
        for (ButtonWidget button : panel.getButtons()) {
            if (button != null) {
                try {
                    this.remove(button);
                } catch (Exception e) {
                    // Ignore if already removed
                }
                this.children().removeIf(child -> child == button);
            }
        }
    }
    
    /**
     * Creates the warband tab content - shows barracks buildings and warband management panels.
     */
    private void createWarbandTabContent() {
        if (activeTab != TabType.WARBAND) {
            return;
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Get all completed barracks buildings
        List<com.secretasain.settlements.settlement.Building> barracksBuildings = settlement.getBuildings().stream()
            .filter(b -> b.getStatus() == com.secretasain.settlements.building.BuildingStatus.COMPLETED)
            .filter(b -> {
                String structureName = b.getStructureType().getPath().toLowerCase();
                return structureName.contains("barracks");
            })
            .collect(java.util.stream.Collectors.toList());
        
        // Create barracks list widget in the same position as other left-side widgets (structureListWidget, buildingSelectionWidget)
        int sidebarWidth = 180;
        int sidebarX = x - sidebarWidth - 15; // 15px gap from main window (same as structureListWidget)
        int sidebarY = y; // Align exactly with main window top
        int sidebarHeight = screenHeight; // Match main window height exactly
        int sidebarTitleHeight = 20;
        int sidebarPadding = 5; // Padding inside sidebar
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding; // Start below title bar with padding
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarPadding * 2; // Fill remaining space with padding
        int contentWidth = sidebarWidth - sidebarPadding * 2; // Width with padding on both sides
        int widgetXOffset = 22; // Same offset as structureListWidget for alignment
        
        int listX = sidebarX + sidebarPadding + widgetXOffset;
        int listY = sidebarListY;
        int listHeight = sidebarListHeight;
        int listWidth = contentWidth;
        
        // Remove old widget if exists
        if (warbandBarracksListWidget != null) {
            this.remove(warbandBarracksListWidget);
        }
        
        warbandBarracksListWidget = new BuildingListWidget(
            this.client,
            listWidth,
            listHeight,
            listY,
            listY + listHeight,
            40,
            barracksBuildings
        );
        warbandBarracksListWidget.setLeftPos(listX);
        
        // Set selection callback
        warbandBarracksListWidget.setOnSelectionChangedCallback(building -> {
            selectedBarracks = building;
            createWarbandPanels(building);
            // Request NPC sync when barracks selection changes
            if (this.client.player != null && this.client.getNetworkHandler() != null) {
                com.secretasain.settlements.network.RequestWarbandNpcsPacketClient.send(building.getId());
            }
            createHiredNpcListWidget(); // Update hired NPC list when barracks changes
        });
        
        this.addDrawableChild(warbandBarracksListWidget);
        
        // Auto-select first barracks if available
        if (!barracksBuildings.isEmpty() && selectedBarracks == null) {
            if (!warbandBarracksListWidget.children().isEmpty()) {
                @SuppressWarnings("unchecked")
                net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget.Entry<?> firstEntry = 
                    (net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget.Entry<?>) warbandBarracksListWidget.children().get(0);
                warbandBarracksListWidget.setSelected(firstEntry);
                selectedBarracks = barracksBuildings.get(0);
                createWarbandPanels(selectedBarracks);
            }
        } else if (selectedBarracks != null) {
            createWarbandPanels(selectedBarracks);
        }
        
        // Create hired NPC list widget on the right side
        createHiredNpcListWidget();
    }
    
    /**
     * Creates the hired NPC list widget showing all NPCs hired at the selected barracks.
     */
    private void createHiredNpcListWidget() {
        if (selectedBarracks == null || activeTab != TabType.WARBAND) {
            return;
        }
        
        // Remove old widget if exists
        if (hiredNpcListWidget != null) {
            this.remove(hiredNpcListWidget);
        }
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Position on right side of screen
        int sidebarWidth = 180;
        int sidebarX = x + screenWidth + 15; // 15px gap from main window
        int sidebarY = y;
        int sidebarHeight = screenHeight;
        int sidebarTitleHeight = 20;
        int sidebarPadding = 5;
        int sidebarListY = sidebarY + sidebarTitleHeight + sidebarPadding;
        int sidebarListHeight = sidebarHeight - sidebarTitleHeight - sidebarPadding * 2;
        int contentWidth = sidebarWidth - sidebarPadding * 2;
        int widgetXOffset = 22;
        
        int listX = sidebarX + sidebarPadding + widgetXOffset;
        int listY = sidebarListY;
        int listHeight = sidebarListHeight;
        int listWidth = contentWidth;
        
        // Get hired NPCs from cached server data
        List<com.secretasain.settlements.warband.NpcData> hiredNpcs = 
            com.secretasain.settlements.network.SyncWarbandNpcsPacketClient.getNpcsForBarracks(selectedBarracks.getId());
        
        // Request update from server if we don't have data yet
        if (hiredNpcs.isEmpty() && this.client.player != null && this.client.getNetworkHandler() != null) {
            // Request NPC sync from server
            com.secretasain.settlements.network.RequestWarbandNpcsPacketClient.send(selectedBarracks.getId());
        }
        
        hiredNpcListWidget = new HiredNpcListWidget(
            this.client,
            listWidth,
            listHeight,
            listY,
            listY + listHeight,
            50, // Increased item height to accommodate button below text
            hiredNpcs
        );
        hiredNpcListWidget.setLeftPos(listX);
        hiredNpcListWidget.setOnDismissCallback(this::onDismissNpc);
        
        this.addDrawableChild(hiredNpcListWidget);
    }
    
    /**
     * Creates the warband panels (Warrior, Priest, Mage) for the selected barracks.
     */
    private void createWarbandPanels(com.secretasain.settlements.settlement.Building barracks) {
        if (barracks == null || activeTab != TabType.WARBAND) {
            return;
        }
        
        // Remove existing panel buttons - panels will recreate their buttons
        // We don't need to track buttons separately since panels manage them
        
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Create three panels at bottom center (moved down to bottom of screen)
        int panelWidth = 100;
        int panelHeight = 120;
        int panelSpacing = 10;
        int totalWidth = panelWidth * 3 + panelSpacing * 2;
        int panelStartX = x + (screenWidth - totalWidth) / 2;
        // Position panels at bottom of screen with some padding from bottom edge
        int bottomPadding = 10; // Padding from bottom edge
        int panelY = (y + screenHeight) - panelHeight - bottomPadding;
        
        // Warrior panel (implemented)
            warriorPanel = new WarbandPanelWidget(
            this.client,
            panelStartX,
            panelY,
            panelWidth,
            panelHeight,
            com.secretasain.settlements.warband.NpcClass.WARRIOR,
            this::onHireNpc,
            null // Dismiss handled by HiredNpcListWidget
        );
        warriorPanel.initButtons(button -> this.addDrawableChild(button));
        
        // Priest panel (not implemented - grayed out)
        priestPanel = new WarbandPanelWidget(
            this.client,
            panelStartX + panelWidth + panelSpacing,
            panelY,
            panelWidth,
            panelHeight,
            com.secretasain.settlements.warband.NpcClass.PRIEST,
            null,
            null
        );
        priestPanel.setEnabled(false);
        priestPanel.initButtons(button -> this.addDrawableChild(button));
        
        // Mage panel (not implemented - grayed out)
        magePanel = new WarbandPanelWidget(
            this.client,
            panelStartX + (panelWidth + panelSpacing) * 2,
            panelY,
            panelWidth,
            panelHeight,
            com.secretasain.settlements.warband.NpcClass.MAGE,
            null,
            null
        );
        magePanel.setEnabled(false);
        magePanel.initButtons(button -> this.addDrawableChild(button));
    }
    
    /**
     * Called when player clicks to hire an NPC.
     */
    private void onHireNpc(com.secretasain.settlements.warband.NpcClass npcClass, com.secretasain.settlements.warband.ParagonLevel paragonLevel) {
        if (selectedBarracks == null) {
            return;
        }
        // Send HireNpcPacket to server
        com.secretasain.settlements.network.HireNpcPacketClient.send(
            selectedBarracks.getId(),
            settlement.getId(),
            npcClass,
            paragonLevel
        );
    }
    
    /**
     * Called when player clicks to dismiss an NPC.
     */
    private void onDismissNpc(UUID entityId) {
        // Send dismiss packet to server
        com.secretasain.settlements.network.DismissNpcPacketClient.send(entityId);
        // Widget will be updated when server sends updated data
    }
    
    enum TabType {
        OVERVIEW,
        BUILDINGS,
        VILLAGERS,
        WARBAND,
        SETTINGS
    }
}

