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
public class BuildingSelectionWidget extends AlwaysSelectedEntryListWidget<BuildingSelectionWidget.BuildingEntry> {
    private final List<Building> availableBuildings;
    private final VillagerData villager;
    private final com.secretasain.settlements.settlement.Settlement settlement;
    private BiConsumer<VillagerData, UUID> onBuildingSelected;
    Consumer<Building> onSelectionChanged; // Callback for when selection changes (for output widget) - package private for access
    
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
        BuildingEntry entry = this.getSelectedOrNull();
        return entry != null ? entry.getBuilding() : null;
    }
    
    /**
     * Updates the list entries from the available buildings.
     */
    public void updateEntries() {
        this.clearEntries();
        for (Building building : availableBuildings) {
            this.addEntry(new BuildingEntry(building));
        }
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
        
        // Render entries manually WITHOUT calling super.render() to avoid parent's background
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
                BuildingEntry entry = this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                if (entryY + this.itemHeight >= y && entryY <= y + (this.bottom - this.top)) {
                    if (mouseX >= x && mouseX <= x + this.width && 
                        mouseY >= entryY && mouseY <= entryY + this.itemHeight) {
                        
                        // Select the entry
                        BuildingEntry previouslySelected = this.getSelectedOrNull();
                        // Always trigger callbacks on click (even if same building)
                        // This ensures widget updates on first click
                        boolean selectionChanged = (previouslySelected != entry);
                        this.setSelected(entry);
                        
                        // Trigger assignment callback if villager is set (for work assignment)
                        if (onBuildingSelected != null && villager != null) {
                            // Always trigger on click, not just on change
                            onBuildingSelected.accept(villager, entry.getBuilding().getId());
                        }
                        
                        // Always trigger selection changed callback on click
                        // This ensures widget updates immediately on first click
                        if (onSelectionChanged != null) {
                            onSelectionChanged.accept(entry.getBuilding());
                        }
                        
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * A single entry in the building selection list.
     */
    public class BuildingEntry extends AlwaysSelectedEntryListWidget.Entry<BuildingEntry> {
        private final Building building;
        
        public BuildingEntry(Building building) {
            this.building = building;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, 
                          int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            
            // Format building name from structure type
            Identifier structureType = building.getStructureType();
            String structureName = structureType.getPath();
            if (structureName.contains("/")) {
                structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
            }
            if (structureName.endsWith(".nbt")) {
                structureName = structureName.substring(0, structureName.length() - 4);
            }
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
            
            // Draw building name
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                formatted.toString(),
                x + 5,
                y + 3,
                0xFFFFFF,
                false
            );
            
            // Draw building position
            String posText = String.format("(%d, %d, %d)", 
                building.getPosition().getX(),
                building.getPosition().getY(),
                building.getPosition().getZ());
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(posText),
                x + 5,
                y + 13,
                0xCCCCCC,
                false
            );
            
            // Draw capacity info if settlement is available
            if (settlement != null) {
                int capacity = com.secretasain.settlements.settlement.BuildingCapacity.getCapacity(building.getStructureType());
                int assigned = com.secretasain.settlements.settlement.WorkAssignmentManager.getVillagersAssignedToBuilding(settlement, building.getId()).size();
                int available = capacity - assigned;
                
                String capacityText = String.format("%d/%d", assigned, capacity);
                int capacityColor = available > 0 ? 0x00FF00 : 0xFF0000; // Green if has space, red if full
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(capacityText),
                    x + entryWidth - 40,
                    y + 13,
                    capacityColor,
                    false
                );
            }
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

