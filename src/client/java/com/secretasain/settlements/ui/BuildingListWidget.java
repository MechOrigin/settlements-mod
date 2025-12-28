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
 */
public class BuildingListWidget extends AlwaysSelectedEntryListWidget<BuildingListWidget.BuildingEntry> {
    private final List<Building> buildings;
    private Consumer<Building> onDeleteCallback; // Callback for when delete button is clicked
    private Consumer<Building> onStartCallback; // Callback for when start button is clicked
    private Consumer<Building> onSelectionChangedCallback; // Callback for when selection changes
    private java.util.Map<String, Integer> availableMaterials; // Settlement materials for calculating availability
    
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
     * Automatically filters out COMPLETED buildings so they don't show in the UI.
     */
    public void updateEntries() {
        this.clearEntries();
        for (Building building : buildings) {
            // Show ALL buildings including COMPLETED ones so user can click on them
            // This allows viewing materials for completed buildings too
            this.addEntry(new BuildingEntry(building, availableMaterials));
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Draw background
        context.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xFF101010);
        context.drawBorder(x - 5, y - 5, width + 10, height + 10, 0xFF404040);
        
        // Render entries
        int scrollAmount = (int)this.getScrollAmount();
        int startIndex = Math.max(0, scrollAmount / this.itemHeight);
        int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
        
        for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
            BuildingEntry entry = this.children().get(i);
            int entryY = y + (i * this.itemHeight) - scrollAmount;
            
            if (entryY + this.itemHeight >= y && entryY <= y + height) {
                boolean isSelected = this.getSelectedOrNull() == entry;
                boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                   mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                // Pass isSelected || isHovered so selected entries get visual feedback
                entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
            }
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
                BuildingEntry entry = this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                // Check if entry is visible (within scroll bounds)
                if (entryY + this.itemHeight >= y && entryY <= y + (this.bottom - this.top)) {
                    // Check if click is within this entry's bounds
                    if (mouseX >= x && mouseX <= x + this.width && 
                        mouseY >= entryY && mouseY <= entryY + this.itemHeight) {
                        
                        clickedEntry = entry; // Remember which entry was clicked
                        break; // Found the entry, stop searching
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
        BuildingEntry entry = this.getSelectedOrNull();
        return entry != null ? entry.getBuilding() : null;
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
            int textY = y + 3;
            
            // Draw structure type name
            String structureName = building.getStructureType().getPath();
            if (structureName.contains("/")) {
                structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
            }
            if (structureName.endsWith(".nbt")) {
                structureName = structureName.substring(0, structureName.length() - 4);
            }
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
            
            context.drawText(
                client.textRenderer,
                Text.literal(formatted.toString()),
                x + 5,
                textY,
                0xFFFFFF,
                false
            );
            textY += 10;
            
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
                x + 5,
                textY,
                statusColor,
                false
            );
            textY += 10;
            
            // Check if this is a town hall and show indicator
            String structurePath = building.getStructureType().getPath();
            boolean isTownHall = structurePath.contains("town_hall") || structurePath.contains("townhall");
            if (isTownHall && building.getStatus() == BuildingStatus.COMPLETED) {
                // Show town hall indicator (simple text for now)
                context.drawText(
                    client.textRenderer,
                    Text.literal("🏛️ Town Hall"),
                    x + 5,
                    textY,
                    0xFFD4AF37, // Gold color
                    false
                );
                textY += 10;
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
            BlockPos pos = building.getPosition();
            String posText = String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
            int posWidth = client.textRenderer.getWidth(posText);
            // Calculate position X - account for start button if visible
            int buttonsWidth = DELETE_BUTTON_SIZE + DELETE_BUTTON_PADDING;
            if (status == BuildingStatus.RESERVED) {
                buttonsWidth += START_BUTTON_SIZE + BUTTON_SPACING; // Add start button width and spacing
            }
            int posX = deleteButtonX - posWidth - buttonsWidth;
            context.drawText(
                client.textRenderer,
                Text.literal(posText),
                posX,
                y + 3,
                0xAAAAAA,
                false
            );
            
            // Draw progress if in progress
            if (status == BuildingStatus.IN_PROGRESS) {
                int progress = building.getProgressPercentage();
                String progressText = progress + "%";
                int progressWidth = client.textRenderer.getWidth(progressText);
                context.drawText(
                    client.textRenderer,
                    Text.literal(progressText),
                    posX,
                    textY,
                    0xAAAAAA,
                    false
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
                    context.drawText(
                        client.textRenderer,
                        Text.literal(materialText),
                        x + 5,
                        textY + 10,
                        materialColor,
                        false
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

