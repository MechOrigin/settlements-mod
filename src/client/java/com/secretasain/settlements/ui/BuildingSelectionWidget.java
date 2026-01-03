package com.secretasain.settlements.ui;

import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.VillagerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Widget for selecting a building to assign a villager to.
 * Similar to StructureListWidget but for buildings.
 */
@SuppressWarnings("rawtypes")
public class BuildingSelectionWidget extends AlwaysSelectedEntryListWidget {
    private final List<Building> availableBuildings;
    private final VillagerData villager;
    private final com.secretasain.settlements.settlement.Settlement settlement;
    private BiConsumer<VillagerData, UUID> onBuildingSelected;
    Consumer<Building> onSelectionChanged; // Callback for when selection changes (for output widget) - package private for access
    // Track which groups are expanded for accordion functionality
    private java.util.Map<String, Boolean> expandedGroups = new java.util.HashMap<>();
    
    public VillagerData getVillager() {
        return villager;
    }
    
    public BuildingSelectionWidget(MinecraftClient client, int width, int height, int top, int bottom, 
                                  int itemHeight, List<Building> availableBuildings, VillagerData villager,
                                  com.secretasain.settlements.settlement.Settlement settlement) {
        super(client, width, height, top, bottom, itemHeight);
        this.availableBuildings = availableBuildings;
        this.villager = villager;
        this.settlement = settlement;
        this.updateEntries();
    }
    
    /**
     * Sets the callback to be called when a building is selected (for assignment).
     */
    public void setOnBuildingSelected(BiConsumer<VillagerData, UUID> callback) {
        this.onBuildingSelected = callback;
    }
    
    /**
     * Sets the callback to be called when selection changes (for output widget display).
     */
    public void setOnSelectionChanged(Consumer<Building> callback) {
        this.onSelectionChanged = callback;
    }
    
    /**
     * Gets the currently selected building.
     * @return The selected building, or null if none selected
     */
    public Building getSelectedBuilding() {
        @SuppressWarnings("unchecked")
        AlwaysSelectedEntryListWidget.Entry<?> selected = (AlwaysSelectedEntryListWidget.Entry<?>) this.getSelectedOrNull();
        if (selected instanceof BuildingEntry) {
            return ((BuildingEntry) selected).getBuilding();
        }
        return null; // Group entries don't represent a single building
    }
    
    /**
     * Updates the list entries from the available buildings.
     * Groups similar buildings into accordion sections to reduce clutter.
     * Buildings with 2+ of the same type are grouped, single buildings are shown individually.
     */
    public void updateEntries() {
        this.clearEntries();
        
        // Group buildings by structure type (normalized name)
        java.util.Map<String, java.util.List<Building>> buildingGroups = new java.util.HashMap<>();
        
        for (Building building : availableBuildings) {
            // Normalize structure name for grouping
            String structureName = building.getStructureType().getPath();
            if (structureName.contains("/")) {
                structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
            }
            if (structureName.endsWith(".nbt")) {
                structureName = structureName.substring(0, structureName.length() - 4);
            }
            
            // Group by normalized structure name
            buildingGroups.computeIfAbsent(structureName, k -> new java.util.ArrayList<>()).add(building);
        }
        
        // Create entries: accordion groups for buildings with 2+ of same type, flat entries for singles
        for (java.util.Map.Entry<String, java.util.List<Building>> group : buildingGroups.entrySet()) {
            String groupName = group.getKey();
            java.util.List<Building> groupBuildings = group.getValue();
            
            if (groupBuildings.size() > 1) {
                // Multiple buildings of same type - create accordion entry
                boolean isExpanded = expandedGroups.getOrDefault(groupName, false);
                this.addEntry(new BuildingGroupEntry(groupName, groupBuildings, isExpanded));
                
                // If expanded, add individual building entries
                if (isExpanded) {
                    for (Building building : groupBuildings) {
                        this.addEntry(new BuildingEntry(building, settlement));
                    }
                }
            } else {
                // Single building - add as regular entry (no accordion needed)
                this.addEntry(new BuildingEntry(groupBuildings.get(0), settlement));
            }
        }
    }
    
    /**
     * Toggles the expansion state of a building group.
     * @param groupName The normalized structure name of the group
     */
    private void toggleGroupExpansion(String groupName) {
        boolean currentState = expandedGroups.getOrDefault(groupName, false);
        expandedGroups.put(groupName, !currentState);
        // Refresh entries to reflect the new expansion state
        updateEntries();
    }
    
    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw custom background matching the dialog style (no dirt texture)
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Draw background for the list area - matches dialog style
        context.fill(x, y, x + width, y + height, 0xFF101010); // Dark gray background
        
        // Render debug title if enabled
        UIDebugRenderer.renderWidgetTitle(context, "BuildingSelectionWidget", x, y, width);
        
        // Render entries manually WITHOUT calling super.render() to avoid parent's background
        int scrollAmount = (int)this.getScrollAmount();
        int startIndex = Math.max(0, scrollAmount / this.itemHeight);
        int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
        
        for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
            @SuppressWarnings("unchecked")
            AlwaysSelectedEntryListWidget.Entry<?> entry = (AlwaysSelectedEntryListWidget.Entry<?>) this.children().get(i);
            int entryY = y + (i * this.itemHeight) - scrollAmount;
            
            if (entryY + this.itemHeight >= y && entryY <= y + height) {
                boolean isSelected = this.getSelectedOrNull() == entry;
                boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                   mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
            }
        }
        
        // Render scrollbar if needed
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarWidth = 4;
            int scrollbarPadding = 2;
            int scrollbarX = x + width - scrollbarWidth - scrollbarPadding;
            int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
            int scrollbarY = y + (int)((scrollAmount / (float)maxScroll) * (height - scrollbarHeight));
            scrollbarY = Math.max(y, Math.min(scrollbarY, y + height - scrollbarHeight));
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
    }
    
    @Override
    protected void renderBackground(net.minecraft.client.gui.DrawContext context) {
        // Override to completely prevent default background rendering (which includes dirt texture)
        // Do NOT call super.renderBackground() - this prevents the dirt texture from showing
        // We draw our own background in render() method instead that matches the dialog style
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int x = this.getRowLeft();
            int y = this.top;
            int scrollAmount = (int)this.getScrollAmount();
            
            // Find which entry was clicked
            for (int i = 0; i < this.children().size(); i++) {
                @SuppressWarnings("unchecked")
                AlwaysSelectedEntryListWidget.Entry<?> entry = (AlwaysSelectedEntryListWidget.Entry<?>) this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                if (entryY + this.itemHeight >= y && entryY <= y + (this.bottom - this.top)) {
                    if (mouseX >= x && mouseX <= x + this.width && 
                        mouseY >= entryY && mouseY <= entryY + this.itemHeight) {
                        
                        // Check if this is a group entry being clicked (for accordion expansion)
                        if (entry instanceof BuildingGroupEntry) {
                            BuildingGroupEntry groupEntry = (BuildingGroupEntry) entry;
                            // Toggle expansion when group header is clicked
                            toggleGroupExpansion(groupEntry.getGroupName());
                            return true; // Handled the click
                        }
                        
                        // Check if this is a building entry
                        if (entry instanceof BuildingEntry) {
                            BuildingEntry buildingEntry = (BuildingEntry) entry;
                            
                            // Select the entry
                            @SuppressWarnings("unchecked")
                            AlwaysSelectedEntryListWidget.Entry<?> previouslySelected = (AlwaysSelectedEntryListWidget.Entry<?>) this.getSelectedOrNull();
                            boolean selectionChanged = (previouslySelected != entry);
                            this.setSelected(entry);
                            
                            // Trigger assignment callback if villager is set (for work assignment)
                            if (onBuildingSelected != null && villager != null) {
                                // Always trigger on click, not just on change
                                onBuildingSelected.accept(villager, buildingEntry.getBuilding().getId());
                            }
                            
                            // Always trigger selection changed callback on click
                            // This ensures widget updates immediately on first click
                            if (onSelectionChanged != null) {
                                onSelectionChanged.accept(buildingEntry.getBuilding());
                            }
                            
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * An accordion entry that groups multiple buildings of the same type.
     * Clicking the header toggles expansion to show/hide individual buildings.
     */
    public static class BuildingGroupEntry extends AlwaysSelectedEntryListWidget.Entry<BuildingGroupEntry> {
        private final String groupName;
        private final java.util.List<Building> buildings;
        private final boolean expanded;
        
        public BuildingGroupEntry(String groupName, java.util.List<Building> buildings, boolean expanded) {
            this.groupName = groupName;
            this.buildings = buildings;
            this.expanded = expanded;
        }
        
        public String getGroupName() {
            return groupName;
        }
        
        /**
         * Gets the display name for the group (formatted).
         */
        private String getFormattedGroupName() {
            String formatted = groupName.replace('_', ' ');
            String[] words = formatted.split(" ");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (result.length() > 0) result.append(" ");
                if (!word.isEmpty()) {
                    result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
                }
            }
            return result.toString();
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, 
                          int mouseX, int mouseY, boolean hovered, float tickDelta) {
            MinecraftClient client = MinecraftClient.getInstance();
            int padding = 4;
            
            // Draw accordion header background
            int headerColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
            context.fill(x, y, x + entryWidth, y + entryHeight, headerColor);
            
            // Draw expand/collapse indicator (arrow)
            String indicator = expanded ? "▼" : "▶";
            int indicatorX = x + padding;
            int indicatorY = y + (entryHeight - client.textRenderer.fontHeight) / 2;
            context.drawText(
                client.textRenderer,
                Text.literal(indicator),
                indicatorX,
                indicatorY,
                0xFFFFFF,
                false
            );
            
            // Draw structure type icon FIRST (before text) - apply UI formatting rules
            // Use first building in group to determine icon
            if (!buildings.isEmpty()) {
                Building firstBuilding = buildings.get(0);
                String structureName = firstBuilding.getStructureType().getPath();
                if (structureName.contains("/")) {
                    structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
                }
                if (structureName.endsWith(".nbt")) {
                    structureName = structureName.substring(0, structureName.length() - 4);
                }
                
                // Get icon block using same logic as BuildingEntry
                net.minecraft.block.Block iconBlock = BuildingEntry.getBlockForStructure(structureName);
                net.minecraft.item.ItemStack iconStack = new net.minecraft.item.ItemStack(iconBlock);
                
                // Apply UI formatting rules: icon first, then text with spacing
                int iconSize = 16;
                int iconSpacing = 4;
                int iconX = indicatorX + 12; // Space after arrow
                int iconY = y + (entryHeight - iconSize) / 2; // Center vertically
                context.drawItem(iconStack, iconX, iconY);
                context.drawItemInSlot(client.textRenderer, iconStack, iconX, iconY);
                
                // Draw group name with count AFTER icon with proper spacing
                String displayName = getFormattedGroupName();
                String countText = String.format(" (%d)", buildings.size());
                String headerText = displayName + countText;
                int textX = iconX + iconSize + iconSpacing; // After icon + spacing
                context.drawText(
                    client.textRenderer,
                    Text.literal(headerText),
                    textX,
                    indicatorY,
                    0xFFFFFF,
                    true // Use shadow for better visibility on dark background
                );
            } else {
                // Fallback: no buildings in group (shouldn't happen, but safety check)
                String displayName = getFormattedGroupName();
                String countText = String.format(" (%d)", buildings.size());
                String headerText = displayName + countText;
                int textX = indicatorX + 12; // Space after arrow
                context.drawText(
                    client.textRenderer,
                    Text.literal(headerText),
                    textX,
                    indicatorY,
                    0xFFFFFF,
                    true // Use shadow for better visibility on dark background
                );
            }
        }
        
        @Override
        public Text getNarration() {
            return Text.literal(groupName + " group (" + buildings.size() + " buildings)");
        }
    }
    
    /**
     * A single entry in the building selection list.
     */
    public static class BuildingEntry extends AlwaysSelectedEntryListWidget.Entry<BuildingEntry> {
        private final Building building;
        private final com.secretasain.settlements.settlement.Settlement settlement;
        
        public BuildingEntry(Building building, com.secretasain.settlements.settlement.Settlement settlement) {
            this.building = building;
            this.settlement = settlement;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, 
                          int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            int padding = 4;
            int iconSize = 16;
            int iconSpacing = 4;
            
            // Get structure name for formatting and icon lookup
            Identifier structureType = building.getStructureType();
            String structureName = structureType.getPath();
            if (structureName.contains("/")) {
                structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
            }
            if (structureName.endsWith(".nbt")) {
                structureName = structureName.substring(0, structureName.length() - 4);
            }
            
            // Determine block icon based on structure name
            net.minecraft.block.Block iconBlock = getBlockForStructure(structureName);
            net.minecraft.item.ItemStack iconStack = new net.minecraft.item.ItemStack(iconBlock);
            
            // Draw icon FIRST (before text) - apply UI formatting rules
            int iconX = x + padding; // Position relative to entry x
            int iconY = y + (entryHeight - iconSize) / 2; // Center icon vertically in entry
            context.drawItem(iconStack, iconX, iconY);
            context.drawItemInSlot(client.textRenderer, iconStack, iconX, iconY);
            
            // Format building name from structure type
            structureName = structureName.replace("_", " ");
            // Capitalize first letter of each word
            String[] words = structureName.split(" ");
            StringBuilder formatted = new StringBuilder();
            for (String word : words) {
                if (formatted.length() > 0) formatted.append(" ");
                if (!word.isEmpty()) {
                    formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
                }
            }
            
            // Draw building name AFTER icon with proper spacing (relative to x, not absolute)
            int textX = iconX + iconSize + iconSpacing; // After icon + spacing, relative to x
            int textY = y + padding;
            context.drawText(
                client.textRenderer,
                Text.literal(formatted.toString()),
                textX,
                textY,
                0xFFFFFF,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw building position
            String posText = String.format("(%d, %d, %d)", 
                building.getPosition().getX(),
                building.getPosition().getY(),
                building.getPosition().getZ());
            context.drawText(
                client.textRenderer,
                Text.literal(posText),
                textX, // Use same textX to align with building name
                textY + client.textRenderer.fontHeight + 2, // Below building name
                0xCCCCCC,
                true // Use shadow for better visibility
            );
            
            // Draw capacity info if settlement is available
            if (settlement != null) {
                int capacity = com.secretasain.settlements.settlement.BuildingCapacity.getCapacity(building.getStructureType());
                int assigned = com.secretasain.settlements.settlement.WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, building.getId()).size();
                int available = capacity - assigned;
                
                String capacityText = String.format("%d/%d", assigned, capacity);
                int capacityColor = available > 0 ? 0x00FF00 : 0xFF0000; // Green if has space, red if full
                context.drawText(
                    client.textRenderer,
                    Text.literal(capacityText),
                    x + entryWidth - 40, // Position relative to entry x
                    textY + client.textRenderer.fontHeight + 2,
                    capacityColor,
                    true // Use shadow for better visibility
                );
            }
        }
        
        /**
         * Determines which block icon to show based on structure name.
         * Uses same logic as BuildingListWidget for consistency.
         * Made static so it can be called from BuildingGroupEntry.
         */
        private static net.minecraft.block.Block getBlockForStructure(String structureName) {
            String lowerName = structureName.toLowerCase();
            
            // Check for structure types first
            if (lowerName.contains("cartographer")) {
                return net.minecraft.block.Blocks.CARTOGRAPHY_TABLE;
            } else if (lowerName.contains("farm")) {
                return net.minecraft.block.Blocks.FARMLAND;
            } else if (lowerName.contains("smith") || lowerName.contains("forge") || lowerName.contains("smithing")) {
                return net.minecraft.block.Blocks.SMITHING_TABLE;
            } else if (lowerName.contains("fence")) {
                return net.minecraft.block.Blocks.OAK_FENCE;
            } else if (lowerName.contains("gate")) {
                return net.minecraft.block.Blocks.OAK_FENCE_GATE;
            } else if (lowerName.contains("house") || lowerName.contains("home")) {
                return net.minecraft.block.Blocks.OAK_PLANKS; // Houses use oak planks icon
            } else if (lowerName.contains("town_hall") || lowerName.contains("townhall")) {
                return net.minecraft.block.Blocks.LECTERN; // Town hall uses lectern icon
            } else if (lowerName.contains("trader") || lowerName.contains("hut")) {
                return net.minecraft.block.Blocks.BARREL; // Trader hut uses barrel icon
            }
            
            // Check for material types
            if (lowerName.contains("oak")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return net.minecraft.block.Blocks.OAK_PLANKS;
                }
                return net.minecraft.block.Blocks.OAK_LOG;
            } else if (lowerName.contains("spruce")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return net.minecraft.block.Blocks.SPRUCE_PLANKS;
                }
                return net.minecraft.block.Blocks.SPRUCE_LOG;
            } else if (lowerName.contains("birch")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return net.minecraft.block.Blocks.BIRCH_PLANKS;
                }
                return net.minecraft.block.Blocks.BIRCH_LOG;
            } else if (lowerName.contains("jungle")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return net.minecraft.block.Blocks.JUNGLE_PLANKS;
                }
                return net.minecraft.block.Blocks.JUNGLE_LOG;
            } else if (lowerName.contains("acacia")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return net.minecraft.block.Blocks.ACACIA_PLANKS;
                }
                return net.minecraft.block.Blocks.ACACIA_LOG;
            } else if (lowerName.contains("dark_oak") || lowerName.contains("darkoak")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return net.minecraft.block.Blocks.DARK_OAK_PLANKS;
                }
                return net.minecraft.block.Blocks.DARK_OAK_LOG;
            } else if (lowerName.contains("stone") || lowerName.contains("cobble")) {
                return net.minecraft.block.Blocks.COBBLESTONE;
            } else if (lowerName.contains("brick")) {
                return net.minecraft.block.Blocks.BRICKS;
            } else if (lowerName.contains("wood") || lowerName.contains("log")) {
                return net.minecraft.block.Blocks.OAK_LOG;
            }
            
            // Default to oak planks for unknown structures
            return net.minecraft.block.Blocks.OAK_PLANKS;
        }
        
        @Override
        public Text getNarration() {
            Identifier structureType = building.getStructureType();
            String name = structureType.getPath();
            return Text.literal("Building: " + name);
        }
        
        public Building getBuilding() {
            return building;
        }
    }
}

