package com.secretasain.settlements.ui;

import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.GolemData;
import com.secretasain.settlements.settlement.Building;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Widget for displaying a scrollable list of golems.
 */
public class GolemListWidget extends AlwaysSelectedEntryListWidget<GolemListWidget.GolemEntry> {
    private final List<GolemData> golems;
    private final Settlement settlement; // Settlement for building lookups
    private java.util.function.BiConsumer<GolemData, UUID> onAssignCallback; // golem, buildingId (null to unassign)
    
    public GolemListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, List<GolemData> golems, Settlement settlement) {
        super(client, width, height, top, bottom, itemHeight);
        this.golems = golems;
        this.settlement = settlement;
        this.updateEntries();
    }
    
    /**
     * Sets the callback to be called when a golem's assign button is clicked.
     * @param callback Callback that receives the golem and building ID (null to unassign)
     */
    public void setOnAssignCallback(java.util.function.BiConsumer<GolemData, UUID> callback) {
        this.onAssignCallback = callback;
    }
    
    /**
     * Updates the list entries from the golem data.
     */
    public void updateEntries() {
        this.clearEntries();
        for (GolemData golem : golems) {
            this.addEntry(new GolemEntry(golem));
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // CRITICAL: Only render if we're on appropriate tab
        int x = this.getRowLeft();
        int y = this.top;
        
        // EXTRA SAFETY: If position is on the far left (where empty box appears), don't render
        if (x < 200) {
            return; // Don't render if positioned on far left (empty box area)
        }
        
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Draw FULLY OPAQUE dark background - completely cover any default rendering
        context.fill(x - 10, y - 10, x + width + 10, y + height + 10, 0xFF000000); // Black first
        context.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xFF101010); // Then dark gray
        
        // Draw border matching main window style
        context.drawBorder(x - 5, y - 5, width + 10, height + 10, 0xFF404040);
        
        // Render debug title if enabled
        UIDebugRenderer.renderWidgetTitle(context, "GolemListWidget", x, y, width);
        
        // Enable scissor clipping to constrain rendering within widget bounds
        context.enableScissor(x, y, x + width, y + height);
        
        try {
            // Render entries manually
            int scrollAmount = (int)this.getScrollAmount();
            int startIndex = Math.max(0, scrollAmount / this.itemHeight);
            int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
            
            for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
                GolemEntry entry = this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                // Only render if entry is within visible bounds
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
                int scrollbarX = x + width - 6;
                int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
                int scrollbarY = y + (int)((scrollAmount / (float)maxScroll) * (height - scrollbarHeight));
                scrollbarY = Math.max(y, Math.min(scrollbarY, y + height - scrollbarHeight));
                context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x80FFFFFF);
            }
        } finally {
            context.disableScissor();
        }
    }
    
    @Override
    protected void renderBackground(DrawContext context) {
        // Override to prevent default background rendering
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int x = this.getRowLeft();
            int y = this.top;
            int scrollAmount = (int)this.getScrollAmount();
            
            // Find which entry was clicked
            for (int i = 0; i < this.children().size(); i++) {
                GolemEntry entry = this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                if (entryY + this.itemHeight >= y && entryY <= y + (this.bottom - this.top)) {
                    if (mouseX >= x && mouseX <= x + this.width && 
                        mouseY >= entryY && mouseY <= entryY + this.itemHeight) {
                        
                        // Check if button was clicked
                        int buttonWidth = 60;
                        int buttonHeight = 14;
                        int buttonY = entryY + this.itemHeight - buttonHeight - 3;
                        int assignButtonX = x + this.width - buttonWidth - 5;
                        int unassignButtonX = assignButtonX;
                        
                        if (entry.golem.isAssigned()) {
                            // Check unassign button
                            if (mouseX >= unassignButtonX && mouseX <= unassignButtonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                                if (onAssignCallback != null) {
                                    onAssignCallback.accept(entry.golem, null);
                                }
                                return true;
                            }
                        } else {
                            // Check assign button
                            if (mouseX >= assignButtonX && mouseX <= assignButtonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                                // Trigger assignment dialog (handled by parent screen)
                                if (onAssignCallback != null) {
                                    // Pass null buildingId to trigger selection dialog
                                    onAssignCallback.accept(entry.golem, UUID.fromString("00000000-0000-0000-0000-000000000001")); // Special UUID to indicate "show selection"
                                }
                                return true;
                            }
                        }
                        
                        // Select entry
                        this.setSelected(entry);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * List entry for a golem.
     */
    public class GolemEntry extends AlwaysSelectedEntryListWidget.Entry<GolemEntry> {
        private final GolemData golem;
        
        public GolemEntry(GolemData golem) {
            this.golem = golem;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            
            // Draw golem name
            Text nameText = golem.getName() != null && !golem.getName().isEmpty() 
                ? Text.literal(golem.getName())
                : Text.translatable("settlements.ui.golems.unnamed");
            
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                nameText,
                x + 5,
                y + 3,
                0xFFFFFF,
                false
            );
            
            // Draw assignment status
            int yOffset = 15;
            if (golem.isAssigned()) {
                // Look up wall station information
                Building assignedWallStation = null;
                if (settlement != null && golem.getAssignedWallStationId() != null) {
                    assignedWallStation = settlement.getBuildings().stream()
                        .filter(b -> b.getId().equals(golem.getAssignedWallStationId()))
                        .findFirst()
                        .orElse(null);
                }
                
                if (assignedWallStation != null) {
                    // Show wall station structure type
                    Identifier structureType = assignedWallStation.getStructureType();
                    String structureName = structureType.getPath();
                    // Format structure name
                    if (structureName.contains("/")) {
                        structureName = structureName.substring(structureName.lastIndexOf('/') + 1);
                    }
                    if (structureName.contains("_")) {
                        String[] parts = structureName.split("_");
                        StringBuilder formatted = new StringBuilder();
                        for (String part : parts) {
                            if (!part.isEmpty()) {
                                if (formatted.length() > 0) formatted.append(" ");
                                formatted.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
                            }
                        }
                        structureName = formatted.toString();
                    } else if (!structureName.isEmpty()) {
                        structureName = structureName.substring(0, 1).toUpperCase() + structureName.substring(1);
                    }
                    
                    Text wallStationText = Text.translatable("settlements.ui.golems.assigned_to", structureName);
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        wallStationText,
                        x + 5,
                        y + yOffset,
                        0x00AAFF,
                        false
                    );
                    
                    // Show wall station position
                    yOffset += 12;
                    net.minecraft.util.math.BlockPos wallStationPos = assignedWallStation.getPosition();
                    Text positionText = Text.translatable("settlements.ui.golems.assignment_location", 
                        wallStationPos.getX(), wallStationPos.getY(), wallStationPos.getZ());
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        positionText,
                        x + 5,
                        y + yOffset,
                        0xAAAAAA,
                        false
                    );
                } else {
                    // Wall station not found
                    Text assignmentText = Text.translatable("settlements.ui.golems.assigned");
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        assignmentText,
                        x + 5,
                        y + yOffset,
                        0x00AAFF,
                        false
                    );
                }
            } else {
                // Not assigned
                Text assignmentText = Text.translatable("settlements.ui.golems.unassigned");
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    assignmentText,
                    x + 5,
                    y + yOffset,
                    0xFFAA00,
                    false
                );
            }
            
            // Calculate button positions
            int buttonWidth = 60;
            int buttonHeight = 14;
            int buttonY = y + entryHeight - buttonHeight - 3;
            int buttonX = x + entryWidth - buttonWidth - 5;
            
            // Draw Assign/Unassign button
            boolean showAssignButton = !golem.isAssigned();
            boolean showUnassignButton = golem.isAssigned();
            
            if (showAssignButton) {
                boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                int buttonColor = buttonHovered ? 0xFF2196F3 : 0xFF1976D2; // Blue
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
                context.drawBorder(buttonX, buttonY, buttonWidth, buttonHeight, 0xFF000000);
                
                Text assignText = Text.translatable("settlements.ui.golems.assign_wall_station");
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(assignText);
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    assignText,
                    buttonX + (buttonWidth - textWidth) / 2,
                    buttonY + (buttonHeight - 8) / 2,
                    0xFFFFFF,
                    false
                );
            } else if (showUnassignButton) {
                boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                int buttonColor = buttonHovered ? 0xFFFF9800 : 0xFFF57C00; // Orange
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
                context.drawBorder(buttonX, buttonY, buttonWidth, buttonHeight, 0xFF000000);
                
                Text unassignText = Text.translatable("settlements.ui.golems.unassign_wall_station");
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(unassignText);
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    unassignText,
                    buttonX + (buttonWidth - textWidth) / 2,
                    buttonY + (buttonHeight - 8) / 2,
                    0xFFFFFF,
                    false
                );
            }
        }
        
        @Override
        public Text getNarration() {
            return Text.literal(golem.getName() != null ? golem.getName() : "Unnamed Golem");
        }
    }
}

