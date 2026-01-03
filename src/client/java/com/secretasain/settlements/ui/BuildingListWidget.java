package com.secretasain.settlements.ui;

import com.secretasain.settlements.building.BuildingStatus;
import com.secretasain.settlements.settlement.Building;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;

/**
 * Widget for displaying a scrollable list of buildings.
 * Supports both individual building entries and grouped entries (accordion).
 */
@SuppressWarnings("rawtypes")
public class BuildingListWidget extends AlwaysSelectedEntryListWidget {
    private final List<Building> buildings;
    private Consumer<Building> onDeleteCallback; // Callback for when delete button is clicked
    private Consumer<Building> onStartCallback; // Callback for when start button is clicked
    private Consumer<Building> onSelectionChangedCallback; // Callback for when selection changes
    private java.util.Map<String, Integer> availableMaterials; // Settlement materials for calculating availability
    // Track which groups are expanded for accordion functionality
    private java.util.Map<String, Boolean> expandedGroups = new java.util.HashMap<>();
    
    public BuildingListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, List<Building> buildings) {
        super(client, width, height, top, bottom, itemHeight);
        this.buildings = buildings;
        this.updateEntries();
    }
    
    /**
     * Sets the callback to be called when a building's delete button is clicked.
     * @param callback Callback that receives the building to delete
     */
    public void setOnDeleteCallback(Consumer<Building> callback) {
        this.onDeleteCallback = callback;
    }
    
    /**
     * Sets the callback to be called when a building's start button is clicked.
     * @param callback Callback that receives the building to start
     */
    public void setOnStartCallback(Consumer<Building> callback) {
        this.onStartCallback = callback;
    }
    
    /**
     * Sets the callback to be called when the selected building changes.
     * @param callback Callback that receives the newly selected building (or null if none)
     */
    public void setOnSelectionChangedCallback(Consumer<Building> callback) {
        this.onSelectionChangedCallback = callback;
    }
    
    /**
     * Manually triggers the selection changed callback (useful for programmatic selection).
     * @param building The building that was selected (or null if none)
     */
    public void triggerSelectionChanged(Building building) {
        if (onSelectionChangedCallback != null) {
            onSelectionChangedCallback.accept(building);
        }
    }
    
    /**
     * Updates the available materials map (for calculating material availability).
     * @param materials The settlement's available materials map
     */
    public void setAvailableMaterials(java.util.Map<String, Integer> materials) {
        this.availableMaterials = materials;
        // Refresh entries to update material counts
        this.updateEntries();
    }
    
    /**
     * Updates the list entries from the building data.
     * Groups similar buildings into accordion sections to reduce clutter.
     * Buildings with 2+ of the same type are grouped, single buildings are shown individually.
     */
    public void updateEntries() {
        this.clearEntries();
        
        // Group buildings by structure type (normalized name)
        java.util.Map<String, java.util.List<Building>> buildingGroups = new java.util.HashMap<>();
        
        for (Building building : buildings) {
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
                this.addEntry(new BuildingGroupEntry(groupName, groupBuildings, availableMaterials, isExpanded));
                
                // If expanded, add individual building entries
                if (isExpanded) {
                    for (Building building : groupBuildings) {
                        this.addEntry(new BuildingEntry(building, availableMaterials));
                    }
                }
            } else {
                // Single building - add as regular entry (no accordion needed)
                this.addEntry(new BuildingEntry(groupBuildings.get(0), availableMaterials));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Apply UI formatting rules: bounds checking
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            return; // Invalid bounds, skip rendering
        }
        
        // Draw background
        context.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xFF101010);
        context.drawBorder(x - 5, y - 5, width + 10, height + 10, 0xFF404040);
        
        // Render debug title if enabled
        UIDebugRenderer.renderWidgetTitle(context, "BuildingListWidget", x, y, width);
        
        // Calculate scroll amount (needed for both entries and scrollbar)
        int currentScrollAmount = (int)this.getScrollAmount();
        
        // Apply UI formatting rules: use scissor clipping to prevent list overflow
        context.enableScissor(x, y, x + width, y + height);
        
        try {
            // Show empty message if no buildings
            if (this.children().isEmpty()) {
                MinecraftClient client = MinecraftClient.getInstance();
                String emptyText = "No buildings";
                int textWidth = client.textRenderer.getWidth(emptyText);
                int textX = x + (width - textWidth) / 2; // Center horizontally
                int textY = y + (height - client.textRenderer.fontHeight) / 2; // Center vertically
                context.drawText(
                    client.textRenderer,
                    net.minecraft.text.Text.literal(emptyText),
                    textX,
                    textY,
                    0xAAAAAA,
                    true // Use shadow for better visibility
                );
            } else {
                // Render entries
                int startIndex = Math.max(0, currentScrollAmount / this.itemHeight);
                int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
                
                for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
                    @SuppressWarnings("unchecked")
                    AlwaysSelectedEntryListWidget.Entry<?> entry = (AlwaysSelectedEntryListWidget.Entry<?>) this.children().get(i);
                    int entryY = y + (i * this.itemHeight) - currentScrollAmount;
                    
                    if (entryY + this.itemHeight >= y && entryY <= y + height) {
                        boolean isSelected = this.getSelectedOrNull() == entry;
                        boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                           mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                        // Pass isSelected || isHovered so selected entries get visual feedback
                        entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
                    }
                }
            }
        } finally {
            // Always disable scissor to prevent affecting other rendering
            context.disableScissor();
        }
        
        // Render scrollbar if needed (outside scissor area)
        // Apply UI formatting rules: reserve 6-8 pixels width for scrollbar
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarWidth = 6;
            int scrollbarX = x + width - scrollbarWidth;
            int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
            int scrollbarY = y + (int)((currentScrollAmount / (float)maxScroll) * (height - scrollbarHeight));
            context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        
        Building previouslySelected = this.getSelectedBuilding();
        boolean buttonWasClicked = false;
        BuildingEntry clickedEntry = null;
        
        if (button == 0) { // Left click
            int x = this.getRowLeft();
            int y = this.top;
            int scrollAmount = (int)this.getScrollAmount();
            
            // Find which entry was clicked using manual detection
            // Check all entries to find which one the mouse is over
            for (int i = 0; i < this.children().size(); i++) {
                @SuppressWarnings("unchecked")
                AlwaysSelectedEntryListWidget.Entry<?> entry = (AlwaysSelectedEntryListWidget.Entry<?>) this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                // Check if entry is visible (within scroll bounds)
                if (entryY + this.itemHeight >= y && entryY <= y + (this.bottom - this.top)) {
                    // Check if click is within this entry's bounds
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
                            clickedEntry = (BuildingEntry) entry;
                            break; // Found the entry, stop searching
                        }
                    }
                }
            }
            
            // If we found an entry, check if a button was clicked
            if (clickedEntry != null) {
                // Find the entry's index to calculate its Y position
                int entryIndex = -1;
                for (int i = 0; i < this.children().size(); i++) {
                    if (this.children().get(i) == clickedEntry) {
                        entryIndex = i;
                        break;
                    }
                }
                
                if (entryIndex >= 0) {
                    int entryY = y + (entryIndex * this.itemHeight) - scrollAmount;
                    
                    // Check start button first (for RESERVED buildings)
                    if (clickedEntry.getBuilding().getStatus() == BuildingStatus.RESERVED && 
                        clickedEntry.isStartButtonClicked(mouseX, mouseY, x, entryY, this.width, this.itemHeight)) {
                        if (onStartCallback != null) {
                            onStartCallback.accept(clickedEntry.getBuilding());
                        }
                        buttonWasClicked = true;
                    }
                    // Check delete button
                    else if (clickedEntry.isDeleteButtonClicked(mouseX, mouseY, x, entryY, this.width, this.itemHeight)) {
                        if (onDeleteCallback != null) {
                            onDeleteCallback.accept(clickedEntry.getBuilding());
                        }
                        buttonWasClicked = true;
                    }
                }
            }
            
            // If an entry was clicked but no button, explicitly select it BEFORE calling parent
            // This ensures selection is set even if parent's mouseClicked doesn't handle it
            if (clickedEntry != null && !buttonWasClicked) {
                this.setSelected(clickedEntry);
                
                // Manually trigger callback immediately after selection is set
                // This ensures the material widget is created even if the normal callback doesn't fire
                Building selectedBuilding = this.getSelectedBuilding();
                if (selectedBuilding != null && onSelectionChangedCallback != null) {
                    onSelectionChangedCallback.accept(selectedBuilding);
                }
            }
        }
        // Right-click functionality removed - debug outlines now show all buildings when F10 is enabled
        // (No action needed for right-click)
        
        // Call parent to handle selection change (this will also set selection if we didn't already)
        // But if we already set it manually above, parent's call won't change it
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        
        // Notify if selection changed OR if same building was clicked again
        Building newlySelected = this.getSelectedBuilding();
        if (previouslySelected != newlySelected) {
            if (onSelectionChangedCallback != null) {
                onSelectionChangedCallback.accept(newlySelected);
            }
        } else if (newlySelected != null && clickedEntry != null && clickedEntry.getBuilding() == newlySelected) {
            // Selection didn't change, but same building was clicked - trigger callback anyway
            // This ensures the material widget is updated/refreshed when clicking the same building
            if (onSelectionChangedCallback != null) {
                onSelectionChangedCallback.accept(newlySelected);
            }
        }
        
        // If a button was clicked, we handled it, so return true
        // Otherwise, return the parent's result (which should be true if an entry was clicked)
        return buttonWasClicked || result;
    }
    
    @Override
    protected void renderBackground(DrawContext context) {
        // Override to prevent default background rendering
    }
    
    /**
     * Gets the selected building, or null if none is selected.
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
     * An accordion entry that groups multiple buildings of the same type.
     * Clicking the header toggles expansion to show/hide individual buildings.
     */
    public static class BuildingGroupEntry extends AlwaysSelectedEntryListWidget.Entry<BuildingGroupEntry> {
        private final String groupName;
        private final java.util.List<Building> buildings;
        private final java.util.Map<String, Integer> availableMaterials;
        private final boolean expanded;
        
        public BuildingGroupEntry(String groupName, java.util.List<Building> buildings,
                                 java.util.Map<String, Integer> availableMaterials, boolean expanded) {
            this.groupName = groupName;
            this.buildings = buildings;
            this.availableMaterials = availableMaterials;
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
     * A single entry in the building list.
     */
    public static class BuildingEntry extends AlwaysSelectedEntryListWidget.Entry<BuildingEntry> {
        private final Building building;
        private final java.util.Map<String, Integer> availableMaterials; // Available materials for this entry
        private static final int DELETE_BUTTON_SIZE = 12;
        private static final int DELETE_BUTTON_PADDING = 5;
        private static final int START_BUTTON_SIZE = 12;
        private static final int START_BUTTON_PADDING = 5;
        private static final int BUTTON_SPACING = 8; // Space between buttons
        
        public BuildingEntry(Building building, java.util.Map<String, Integer> availableMaterials) {
            this.building = building;
            this.availableMaterials = availableMaterials;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover (semi-transparent white)
            // Note: hovered parameter includes both hover AND selection (from parent render method)
            // So selected entries will also get the hover background, which provides visual feedback
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            // Apply UI formatting rules: proper text spacing and icon positioning
            // Use textRenderer.fontHeight + 2 for line spacing (2px padding between lines)
            int lineHeight = client.textRenderer.fontHeight + 2;
            int padding = 4; // Widget internal padding
            int iconSize = 16; // Standard icon size
            int iconSpacing = 4; // Minimum 4px spacing between icon and text (UI formatting rules)
            int textY = y + padding;
            
            // Get structure name for formatting and icon lookup
            String structureName = building.getStructureType().getPath();
            if (structureName.contains("/")) {
                structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
            }
            if (structureName.endsWith(".nbt")) {
                structureName = structureName.substring(0, structureName.length() - 4);
            }
            
            // Determine block icon based on structure name (same logic as StructureListWidget)
            net.minecraft.block.Block iconBlock = BuildingEntry.getBlockForStructure(structureName);
            net.minecraft.item.ItemStack iconStack = new net.minecraft.item.ItemStack(iconBlock);
            
            // Draw icon FIRST (before text) - apply UI formatting rules
            int iconX = x + padding;
            // iconSize already defined above (line 470)
            int iconY = y + (entryHeight - iconSize) / 2; // Center icon vertically in entry
            context.drawItem(iconStack, iconX, iconY);
            context.drawItemInSlot(client.textRenderer, iconStack, iconX, iconY);
            
            // Format name: replace underscores with spaces and capitalize
            structureName = structureName.replace('_', ' ');
            String[] words = structureName.split(" ");
            StringBuilder formatted = new StringBuilder();
            for (String word : words) {
                if (formatted.length() > 0) formatted.append(" ");
                if (!word.isEmpty()) {
                    formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
                }
            }
            
            // Draw text AFTER icon with proper spacing (apply UI formatting rules)
            int textX = iconX + iconSize + iconSpacing + 30; // After icon + spacing + 30px offset
            context.drawText(
                client.textRenderer,
                Text.literal(formatted.toString()),
                textX,
                textY,
                0xFFFFFF,
                true // Use shadow for better visibility on dark background
            );
            textY += lineHeight;
            
            // Draw status with color coding
            BuildingStatus status = building.getStatus();
            String statusText = status.name();
            int statusColor;
            switch (status) {
                case RESERVED:
                    statusColor = 0xFFAA00; // Orange
                    statusText = "Reserved";
                    break;
                case IN_PROGRESS:
                    statusColor = 0x00AAFF; // Blue
                    statusText = "In Progress";
                    break;
                case COMPLETED:
                    statusColor = 0x00FF00; // Green
                    statusText = "Completed";
                    break;
                case CANCELLED:
                    statusColor = 0xFF0000; // Red
                    statusText = "Cancelled";
                    break;
                default:
                    statusColor = 0xCCCCCC; // Gray
            }
            
            context.drawText(
                client.textRenderer,
                Text.literal(statusText),
                textX, // Use same textX as building name so status appears below it
                textY,
                statusColor,
                true // Use shadow for better visibility on dark background
            );
            textY += lineHeight;
            
            // Check if this is a town hall and show indicator
            String structurePath = building.getStructureType().getPath();
            boolean isTownHall = structurePath.contains("town_hall") || structurePath.contains("townhall");
            if (isTownHall && building.getStatus() == BuildingStatus.COMPLETED) {
                // Show town hall indicator (simple text for now)
                context.drawText(
                    client.textRenderer,
                    Text.literal("🏛️ Town Hall"),
                    x + padding,
                    textY,
                    0xFFD4AF37, // Gold color
                    true // Use shadow for better visibility on dark background
                );
                textY += lineHeight;
            }
            
            // Calculate button positions (right side)
            // Start button first (left), then delete button (right)
            int deleteButtonX = x + entryWidth - DELETE_BUTTON_SIZE - DELETE_BUTTON_PADDING;
            int deleteButtonY = y + (entryHeight - DELETE_BUTTON_SIZE) / 2;
            int startButtonX = deleteButtonX - START_BUTTON_SIZE - BUTTON_SPACING; // Left of delete button
            int startButtonY = y + (entryHeight - START_BUTTON_SIZE) / 2;
            
            boolean deleteButtonHovered = mouseX >= deleteButtonX && mouseX <= deleteButtonX + DELETE_BUTTON_SIZE &&
                                         mouseY >= deleteButtonY && mouseY <= deleteButtonY + DELETE_BUTTON_SIZE;
            boolean startButtonHovered = mouseX >= startButtonX && mouseX <= startButtonX + START_BUTTON_SIZE &&
                                        mouseY >= startButtonY && mouseY <= startButtonY + START_BUTTON_SIZE;
            
            // Draw delete button (X)
            int deleteButtonColor = deleteButtonHovered ? 0xFFFF0000 : 0xFFCC0000; // Brighter red when hovered
            context.fill(deleteButtonX, deleteButtonY, deleteButtonX + DELETE_BUTTON_SIZE, deleteButtonY + DELETE_BUTTON_SIZE, deleteButtonColor);
            context.drawBorder(deleteButtonX, deleteButtonY, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE, 0xFF000000);
            
            // Draw X symbol (simple cross)
            int xColor = 0xFFFFFFFF;
            int centerX = deleteButtonX + DELETE_BUTTON_SIZE / 2;
            int centerY = deleteButtonY + DELETE_BUTTON_SIZE / 2;
            int xSize = 6;
            // Draw X as diagonal lines
            for (int i = -xSize/2; i <= xSize/2; i++) {
                // Diagonal from top-left to bottom-right
                if (centerX + i >= deleteButtonX && centerX + i < deleteButtonX + DELETE_BUTTON_SIZE &&
                    centerY + i >= deleteButtonY && centerY + i < deleteButtonY + DELETE_BUTTON_SIZE) {
                    context.fill(centerX + i, centerY + i, centerX + i + 1, centerY + i + 1, xColor);
                }
                // Diagonal from top-right to bottom-left
                if (centerX - i >= deleteButtonX && centerX - i < deleteButtonX + DELETE_BUTTON_SIZE &&
                    centerY + i >= deleteButtonY && centerY + i < deleteButtonY + DELETE_BUTTON_SIZE) {
                    context.fill(centerX - i, centerY + i, centerX - i + 1, centerY + i + 1, xColor);
                }
            }
            
            // Draw start button (checkmark) - only for RESERVED buildings
            if (status == BuildingStatus.RESERVED) {
                int startButtonColor = startButtonHovered ? 0xFF00FF00 : 0xFF00CC00; // Brighter green when hovered
                context.fill(startButtonX, startButtonY, startButtonX + START_BUTTON_SIZE, startButtonY + START_BUTTON_SIZE, startButtonColor);
                context.drawBorder(startButtonX, startButtonY, START_BUTTON_SIZE, START_BUTTON_SIZE, 0xFF000000);
                
                // Draw checkmark symbol
                int checkColor = 0xFFFFFFFF;
                int checkCenterX = startButtonX + START_BUTTON_SIZE / 2;
                int checkCenterY = startButtonY + START_BUTTON_SIZE / 2;
                // Draw checkmark: left vertical line, then diagonal to bottom-right
                for (int i = 0; i < 3; i++) {
                    if (checkCenterY - 2 + i >= startButtonY && checkCenterY - 2 + i < startButtonY + START_BUTTON_SIZE) {
                        context.fill(checkCenterX - 2, checkCenterY - 2 + i, checkCenterX - 1, checkCenterY - 1 + i, checkColor);
                    }
                }
                // Diagonal line
                for (int i = 0; i < 4; i++) {
                    if (checkCenterX + i >= startButtonX && checkCenterX + i < startButtonX + START_BUTTON_SIZE &&
                        checkCenterY + i >= startButtonY && checkCenterY + i < startButtonY + START_BUTTON_SIZE) {
                        context.fill(checkCenterX + i, checkCenterY + i, checkCenterX + i + 1, checkCenterY + i + 1, checkColor);
                    }
                }
                
                // Show tooltip on hover
                if (startButtonHovered) {
                    context.drawTooltip(client.textRenderer, java.util.List.of(Text.literal("Start building")), mouseX, mouseY);
                }
            }
            
            // Show tooltip on hover for delete button
            if (deleteButtonHovered) {
                context.drawTooltip(client.textRenderer, java.util.List.of(Text.literal("Delete reservation")), mouseX, mouseY);
            }
            
            // Draw position on the right (but leave space for buttons)
            // Apply UI formatting rules: proper spacing between elements
            BlockPos pos = building.getPosition();
            String posText = String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
            int posWidth = client.textRenderer.getWidth(posText);
            // Apply UI formatting rules: minimum 4px spacing between text and buttons
            int spacing = 4;
            int posX = deleteButtonX - posWidth - spacing;
            if (status == BuildingStatus.RESERVED) {
                posX -= START_BUTTON_SIZE + BUTTON_SPACING; // Additional spacing for start button
            }
            context.drawText(
                client.textRenderer,
                Text.literal(posText),
                posX,
                y + padding,
                0xAAAAAA,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw progress if in progress (use lineHeight for proper spacing)
            if (status == BuildingStatus.IN_PROGRESS) {
                int progress = building.getProgressPercentage();
                String progressText = progress + "%";
                context.drawText(
                    client.textRenderer,
                    Text.literal(progressText),
                    posX,
                    y + padding + lineHeight, // Use lineHeight for consistent spacing
                    0xAAAAAA,
                    true // Use shadow for better visibility on dark background
                );
            }
            
            // Draw material progress (available/required) if reserved or in progress
            if (status == BuildingStatus.RESERVED || status == BuildingStatus.IN_PROGRESS) {
                int totalRequired = building.getRequiredMaterials().values().stream()
                    .mapToInt(Integer::intValue).sum();
                
                // Calculate available materials from settlement (not provided materials)
                int totalAvailable = 0;
                if (availableMaterials != null) {
                    for (java.util.Map.Entry<net.minecraft.util.Identifier, Integer> requiredEntry : building.getRequiredMaterials().entrySet()) {
                        String materialKey = requiredEntry.getKey().toString();
                        int required = requiredEntry.getValue();
                        int available = availableMaterials.getOrDefault(materialKey, 0);
                        // Count how many we have available (can't exceed required)
                        totalAvailable += Math.min(available, required);
                    }
                }
                
                if (totalRequired > 0) {
                    String materialText = String.format("Materials: %d/%d", totalAvailable, totalRequired);
                    // Color code based on availability
                    int materialColor;
                    if (totalAvailable >= totalRequired) {
                        materialColor = 0x00FF00; // Green - sufficient
                    } else if (totalAvailable > 0) {
                        materialColor = 0xFFFF00; // Yellow - partial
                    } else {
                        materialColor = 0xFF0000; // Red - missing
                    }
                    // Apply UI formatting rules: use lineHeight for spacing
                    context.drawText(
                        client.textRenderer,
                        Text.literal(materialText),
                        x + padding,
                        textY,
                        materialColor,
                        true // Use shadow for better visibility on dark background
                    );
                }
            }
        }
        
        /**
         * Checks if the delete button was clicked.
         * @param mouseX Mouse X position
         * @param mouseY Mouse Y position
         * @param entryX Entry X position
         * @param entryY Entry Y position
         * @param entryWidth Entry width
         * @param entryHeight Entry height
         * @return true if delete button was clicked
         */
        public boolean isDeleteButtonClicked(double mouseX, double mouseY, int entryX, int entryY, int entryWidth, int entryHeight) {
            int deleteButtonX = entryX + entryWidth - DELETE_BUTTON_SIZE - DELETE_BUTTON_PADDING;
            int deleteButtonY = entryY + (entryHeight - DELETE_BUTTON_SIZE) / 2;
            return mouseX >= deleteButtonX && mouseX <= deleteButtonX + DELETE_BUTTON_SIZE &&
                   mouseY >= deleteButtonY && mouseY <= deleteButtonY + DELETE_BUTTON_SIZE;
        }
        
        /**
         * Checks if the start button was clicked.
         * @param mouseX Mouse X position
         * @param mouseY Mouse Y position
         * @param entryX Entry X position
         * @param entryY Entry Y position
         * @param entryWidth Entry width
         * @param entryHeight Entry height
         * @return true if start button was clicked
         */
        public boolean isStartButtonClicked(double mouseX, double mouseY, int entryX, int entryY, int entryWidth, int entryHeight) {
            int deleteButtonX = entryX + entryWidth - DELETE_BUTTON_SIZE - DELETE_BUTTON_PADDING;
            int startButtonX = deleteButtonX - START_BUTTON_SIZE - BUTTON_SPACING;
            int startButtonY = entryY + (entryHeight - START_BUTTON_SIZE) / 2;
            return mouseX >= startButtonX && mouseX <= startButtonX + START_BUTTON_SIZE &&
                   mouseY >= startButtonY && mouseY <= startButtonY + START_BUTTON_SIZE;
        }
        
        /**
         * Determines which block icon to show based on structure name.
         * Uses same logic as StructureListWidget for consistency.
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
            String structureName = building.getStructureType().getPath();
            if (structureName.contains("/")) {
                structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
            }
            return Text.literal(structureName + " - " + building.getStatus().name());
        }
        
        public Building getBuilding() {
            return building;
        }
    }
}

