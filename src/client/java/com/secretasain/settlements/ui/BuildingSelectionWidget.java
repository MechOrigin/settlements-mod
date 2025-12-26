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

/**
 * Widget for selecting a building to assign a villager to.
 * Similar to StructureListWidget but for buildings.
 */
public class BuildingSelectionWidget extends AlwaysSelectedEntryListWidget<BuildingSelectionWidget.BuildingEntry> {
    private final List<Building> availableBuildings;
    private final VillagerData villager;
    private BiConsumer<VillagerData, UUID> onBuildingSelected;
    
    public VillagerData getVillager() {
        return villager;
    }
    
    public BuildingSelectionWidget(MinecraftClient client, int width, int height, int top, int bottom, 
                                  int itemHeight, List<Building> availableBuildings, VillagerData villager) {
        super(client, width, height, top, bottom, itemHeight);
        this.availableBuildings = availableBuildings;
        this.villager = villager;
        this.updateEntries();
    }
    
    /**
     * Sets the callback to be called when a building is selected.
     */
    public void setOnBuildingSelected(BiConsumer<VillagerData, UUID> callback) {
        this.onBuildingSelected = callback;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            BuildingEntry selected = this.getSelectedOrNull();
            if (selected != null && onBuildingSelected != null) {
                onBuildingSelected.accept(villager, selected.getBuilding().getId());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * A single entry in the building selection list.
     */
    public static class BuildingEntry extends AlwaysSelectedEntryListWidget.Entry<BuildingEntry> {
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

