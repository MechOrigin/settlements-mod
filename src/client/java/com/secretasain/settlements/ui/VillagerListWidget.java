package com.secretasain.settlements.ui;

import com.secretasain.settlements.settlement.HiringCostCalculator;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.VillagerData;
import com.secretasain.settlements.settlement.GolemData;
import com.secretasain.settlements.settlement.Building;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Widget for displaying a scrollable list of villagers.
 */
@SuppressWarnings("rawtypes")
public class VillagerListWidget extends AlwaysSelectedEntryListWidget {
    private final List<VillagerData> villagers;
    private final List<GolemData> golems;
    private final Settlement settlement; // Settlement for building lookups
    private java.util.function.Consumer<VillagerData> onHireCallback;
    private java.util.function.Consumer<VillagerData> onFireCallback;
    private java.util.function.BiConsumer<VillagerData, UUID> onAssignWorkCallback; // villager, buildingId (null to unassign)
    private java.util.function.BiConsumer<GolemData, UUID> onAssignGolemCallback; // golem, buildingId (null to unassign)
    
    public VillagerListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, List<VillagerData> villagers, List<GolemData> golems, Settlement settlement) {
        super(client, width, height, top, bottom, itemHeight);
        this.villagers = villagers;
        this.golems = golems != null ? golems : new java.util.ArrayList<>();
        this.settlement = settlement;
        this.updateEntries();
    }
    
    /**
     * Sets the callback to be called when a villager's hire button is clicked.
     */
    public void setOnHireCallback(java.util.function.Consumer<VillagerData> callback) {
        this.onHireCallback = callback;
    }
    
    /**
     * Sets the callback to be called when a villager's fire button is clicked.
     */
    public void setOnFireCallback(java.util.function.Consumer<VillagerData> callback) {
        this.onFireCallback = callback;
    }
    
    /**
     * Sets the callback to be called when a villager's assign work button is clicked.
     * @param callback Callback that receives the villager and building ID (null to unassign)
     */
    public void setOnAssignWorkCallback(java.util.function.BiConsumer<VillagerData, UUID> callback) {
        this.onAssignWorkCallback = callback;
    }
    
    /**
     * Sets the callback to be called when a golem's assign button is clicked.
     * @param callback Callback that receives the golem and building ID (null to unassign)
     */
    public void setOnAssignGolemCallback(java.util.function.BiConsumer<GolemData, UUID> callback) {
        this.onAssignGolemCallback = callback;
    }
    
    /**
     * Updates the list entries from the villager and golem data.
     */
    public void updateEntries() {
        this.clearEntries();
        // Add villagers first
        for (VillagerData villager : villagers) {
            this.addEntry(new VillagerEntry(villager));
        }
        // Add golems after villagers
        for (GolemData golem : golems) {
            this.addEntry(new GolemEntry(golem));
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // CRITICAL: Only render if we're on Villagers tab
        // Check if we're actually supposed to be visible
        int x = this.getRowLeft();
        int y = this.top;
        
        // EXTRA SAFETY: If position is on the far left (where empty box appears), don't render
        if (x < 200) {
            return; // Don't render if positioned on far left (empty box area)
        }
        
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Draw FULLY OPAQUE dark background - completely cover any default rendering
        // Use a larger area to ensure complete coverage
        context.fill(x - 10, y - 10, x + width + 10, y + height + 10, 0xFF000000); // Black first
        context.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xFF101010); // Then dark gray
        
        // Draw border matching main window style (0xFF404040)
        context.drawBorder(x - 5, y - 5, width + 10, height + 10, 0xFF404040);
        
        // Render debug title if enabled
        UIDebugRenderer.renderWidgetTitle(context, "VillagerListWidget", x, y, width);
        
        // Enable scissor clipping to constrain rendering within widget bounds
        context.enableScissor(x, y, x + width, y + height);
        
        try {
            // Render entries manually WITHOUT calling super.render() to avoid parent's background
            int scrollAmount = (int)this.getScrollAmount();
            int startIndex = Math.max(0, scrollAmount / this.itemHeight);
            int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
            
            for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
                @SuppressWarnings("unchecked")
                AlwaysSelectedEntryListWidget.Entry<?> entry = (AlwaysSelectedEntryListWidget.Entry<?>) this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                // Only render if entry is within visible bounds
                if (entryY + this.itemHeight >= y && entryY <= y + height) {
                    boolean isSelected = this.getSelectedOrNull() == entry;
                    boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                       mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                    entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
                }
            }
            
            // Render scrollbar if needed (inside scissor area)
            int maxScroll = this.getMaxScroll();
            if (maxScroll > 0) {
                int scrollbarX = x + width - 6;
                int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
                int scrollbarY = y + (int)((scrollAmount / (float)maxScroll) * (height - scrollbarHeight));
                // Clamp scrollbar to widget bounds
                scrollbarY = Math.max(y, Math.min(scrollbarY, y + height - scrollbarHeight));
                context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x80FFFFFF);
            }
        } finally {
            // Always disable scissor to prevent affecting other rendering
            context.disableScissor();
        }
    }
    
    @Override
    protected void renderBackground(DrawContext context) {
        // Override to completely prevent default background rendering (which includes dirt texture)
        // Do NOT call super.renderBackground() - this prevents the dirt texture from showing
        // We draw our own background in render() method instead that matches the main window style
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
                        
                        // Handle villager entry
                        if (entry instanceof VillagerEntry) {
                            VillagerEntry villagerEntry = (VillagerEntry) entry;
                            
                            // Check if hire button was clicked
                            if (villagerEntry.isHireButtonClicked(mouseX, mouseY, x, entryY, this.width, this.itemHeight)) {
                                if (onHireCallback != null) {
                                    onHireCallback.accept(villagerEntry.getVillager());
                                }
                                return true;
                            }
                            
                            // Check if fire button was clicked
                            if (villagerEntry.isFireButtonClicked(mouseX, mouseY, x, entryY, this.width, this.itemHeight)) {
                                if (onFireCallback != null) {
                                    onFireCallback.accept(villagerEntry.getVillager());
                                }
                                return true;
                            }
                            
                            // Check if assign/unassign work button was clicked
                            if (villagerEntry.isAssignWorkButtonClicked(mouseX, mouseY, x, entryY, this.width, this.itemHeight)) {
                                if (onAssignWorkCallback != null) {
                                    VillagerData v = villagerEntry.getVillager();
                                    if (v.isAssigned()) {
                                        // Unassign - pass the current building ID
                                        onAssignWorkCallback.accept(v, v.getAssignedBuildingId());
                                    } else {
                                        // Assign - pass null to indicate "assign to first available"
                                        onAssignWorkCallback.accept(v, null);
                                    }
                                }
                                return true;
                            }
                        }
                        
                        // Handle golem entry
                        if (entry instanceof GolemEntry) {
                            GolemEntry golemEntry = (GolemEntry) entry;
                            
                            // Check if assign button was clicked
                            if (golemEntry.isAssignButtonClicked((int)mouseX, (int)mouseY, x, entryY, this.width, this.itemHeight)) {
                                if (onAssignGolemCallback != null) {
                                    // Pass special UUID to indicate "show selection dialog"
                                    UUID specialUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
                                    onAssignGolemCallback.accept(golemEntry.getGolem(), specialUuid);
                                }
                                return true;
                            }
                            
                            // Check if unassign button was clicked
                            if (golemEntry.isUnassignButtonClicked((int)mouseX, (int)mouseY, x, entryY, this.width, this.itemHeight)) {
                                if (onAssignGolemCallback != null) {
                                    onAssignGolemCallback.accept(golemEntry.getGolem(), golemEntry.getGolem().getAssignedWallStationId());
                                }
                                return true;
                            }
                        }
                        
                        // Otherwise select the entry
                        this.setSelected(entry);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * A single entry in the villager list.
     */
    public class VillagerEntry extends AlwaysSelectedEntryListWidget.Entry<VillagerEntry> {
        private final VillagerData villager;
        
        public VillagerEntry(VillagerData villager) {
            this.villager = villager;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            
            // Draw villager name - better spacing
            // Apply UI formatting rules: use shadow=true for dark backgrounds (improves readability)
            Text nameText = villager.getName() != null && !villager.getName().isEmpty() 
                ? Text.literal(villager.getName())
                : Text.translatable("settlements.ui.villagers.unnamed");
            
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                nameText,
                x + 5,
                y + 3,
                0xFFFFFF,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw profession - better spacing
            Text professionText;
            String professionStr = villager.getProfession();
            if (professionStr != null && !professionStr.isEmpty() && !professionStr.equals("minecraft:none") && !professionStr.equals("none")) {
                // Format profession name (remove namespace if present)
                String profession = professionStr;
                if (profession.contains(":")) {
                    profession = profession.substring(profession.indexOf(':') + 1);
                }
                // Capitalize first letter
                if (!profession.isEmpty()) {
                    profession = profession.substring(0, 1).toUpperCase() + profession.substring(1);
                    professionText = Text.literal(profession);
                } else {
                    professionText = Text.translatable("settlements.ui.villagers.profession.none");
                }
            } else {
                professionText = Text.translatable("settlements.ui.villagers.profession.none");
            }
            
            // Apply UI formatting rules: use shadow=true for dark backgrounds
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                professionText,
                x + 5,
                y + 15,
                0xCCCCCC,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw employment status - better positioning with more spacing
            Text statusText = villager.isEmployed() 
                ? Text.translatable("settlements.ui.villagers.status.employed")
                : Text.translatable("settlements.ui.villagers.status.unemployed");
            int statusColor = villager.isEmployed() ? 0x00FF00 : 0xFFAA00;
            
            // Apply UI formatting rules: use shadow=true for dark backgrounds
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                statusText,
                x + 5,
                y + 27,
                statusColor,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw work assignment status for employed villagers OR hiring cost for unemployed
            if (villager.isEmployed()) {
                int yOffset = 39;
                
                if (villager.isAssigned()) {
                    // Look up building information
                    Building assignedBuilding = null;
                    if (settlement != null && villager.getAssignedBuildingId() != null) {
                        assignedBuilding = settlement.getBuildings().stream()
                            .filter(b -> b.getId().equals(villager.getAssignedBuildingId()))
                            .findFirst()
                            .orElse(null);
                    }
                    
                    if (assignedBuilding != null) {
                        // Show building structure type
                        Identifier structureType = assignedBuilding.getStructureType();
                        String structureName = structureType.getPath();
                        // Format structure name (remove path separators, capitalize)
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
                        
                        // Apply UI formatting rules: use shadow=true for dark backgrounds
                        Text buildingText = Text.translatable("settlements.ui.villagers.assigned_to", structureName);
                        context.drawText(
                            MinecraftClient.getInstance().textRenderer,
                            buildingText,
                            x + 5,
                            y + yOffset,
                            0x00AAFF,
                            true // Use shadow for better visibility on dark background
                        );
                        
                        // Show building position
                        yOffset += 12;
                        net.minecraft.util.math.BlockPos buildingPos = assignedBuilding.getPosition();
                        Text positionText = Text.translatable("settlements.ui.villagers.assignment_location", 
                            buildingPos.getX(), buildingPos.getY(), buildingPos.getZ());
                        context.drawText(
                            MinecraftClient.getInstance().textRenderer,
                            positionText,
                            x + 5,
                            y + yOffset,
                            0xAAAAAA,
                            true // Use shadow for better visibility on dark background
                        );
                    } else {
                        // Building not found - show generic assigned text
                        Text assignmentText = Text.translatable("settlements.ui.villagers.assigned");
                        context.drawText(
                            MinecraftClient.getInstance().textRenderer,
                            assignmentText,
                            x + 5,
                            y + yOffset,
                            0x00AAFF,
                            true // Use shadow for better visibility on dark background
                        );
                    }
                } else {
                    // Not assigned to any building
                    Text assignmentText = Text.translatable("settlements.ui.villagers.unassigned");
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        assignmentText,
                        x + 5,
                        y + yOffset,
                        0xFFAA00,
                        true // Use shadow for better visibility on dark background
                    );
                }
            } else {
                // Draw hiring cost for unemployed villagers
                int cost = HiringCostCalculator.calculateHiringCost(villager);
                Text costText = Text.translatable("settlements.hire.cost", cost);
                
                // Draw emerald icon
                ItemStack emeraldStack = new ItemStack(Items.EMERALD);
                context.drawItem(emeraldStack, x + 5, y + 38);
                
                // Draw cost text next to emerald (item icon is 16x16, so start at x+22)
                // Apply UI formatting rules: use shadow=true for dark backgrounds
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    costText,
                    x + 22,
                    y + 42,
                    0x00FF00,
                    true // Use shadow for better visibility on dark background
                );
            }
            
            // Calculate button positions - position buttons at bottom of entry
            int buttonWidth = 50;
            int buttonHeight = 14;
            int buttonY = y + entryHeight - buttonHeight - 3; // Position at bottom with small margin
            
            // Draw Hire/Fire button
            int buttonX = x + entryWidth - buttonWidth - 5;
            boolean showHireButton = !villager.isEmployed();
            boolean showFireButton = villager.isEmployed();
            
            // Draw Assign/Unassign Work button for employed villagers (to the left of Fire button)
            if (villager.isEmployed()) {
                int assignButtonWidth = 60;
                int assignButtonX = buttonX - assignButtonWidth - 5;
                boolean showAssignButton = !villager.isAssigned();
                boolean showUnassignButton = villager.isAssigned();
                
                if (showAssignButton) {
                    boolean assignButtonHovered = mouseX >= assignButtonX && mouseX <= assignButtonX + assignButtonWidth &&
                                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                    int assignButtonColor = assignButtonHovered ? 0xFF2196F3 : 0xFF1976D2; // Blue
                    context.fill(assignButtonX, buttonY, assignButtonX + assignButtonWidth, buttonY + buttonHeight, assignButtonColor);
                    context.drawBorder(assignButtonX, buttonY, assignButtonWidth, buttonHeight, 0xFF000000);
                    
                    Text assignText = Text.translatable("settlements.ui.villagers.assign_work");
                    int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(assignText);
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        assignText,
                        assignButtonX + (assignButtonWidth - textWidth) / 2,
                        buttonY + (buttonHeight - 8) / 2,
                        0xFFFFFF,
                        false
                    );
                } else if (showUnassignButton) {
                    boolean unassignButtonHovered = mouseX >= assignButtonX && mouseX <= assignButtonX + assignButtonWidth &&
                                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                    int unassignButtonColor = unassignButtonHovered ? 0xFFFF9800 : 0xFFF57C00; // Orange
                    context.fill(assignButtonX, buttonY, assignButtonX + assignButtonWidth, buttonY + buttonHeight, unassignButtonColor);
                    context.drawBorder(assignButtonX, buttonY, assignButtonWidth, buttonHeight, 0xFF000000);
                    
                    Text unassignText = Text.translatable("settlements.ui.villagers.unassign_work");
                    int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(unassignText);
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        unassignText,
                        assignButtonX + (assignButtonWidth - textWidth) / 2,
                        buttonY + (buttonHeight - 8) / 2,
                        0xFFFFFF,
                        false
                    );
                }
            }
            
            if (showHireButton) {
                // Check if mouse is over hire button
                boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                int buttonColor = buttonHovered ? 0xFF4CAF50 : 0xFF2E7D32;
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
                context.drawBorder(buttonX, buttonY, buttonWidth, buttonHeight, 0xFF000000);
                
                Text hireText = Text.translatable("settlements.ui.villagers.hire");
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(hireText);
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    hireText,
                    buttonX + (buttonWidth - textWidth) / 2,
                    buttonY + (buttonHeight - 8) / 2,
                    0xFFFFFF,
                    false
                );
            } else if (showFireButton) {
                // Check if mouse is over fire button
                boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                int buttonColor = buttonHovered ? 0xFFF44336 : 0xFFC62828;
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
                context.drawBorder(buttonX, buttonY, buttonWidth, buttonHeight, 0xFF000000);
                
                Text fireText = Text.translatable("settlements.ui.villagers.fire");
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(fireText);
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    fireText,
                    buttonX + (buttonWidth - textWidth) / 2,
                    buttonY + (buttonHeight - 8) / 2,
                    0xFFFFFF,
                    false
                );
            }
            
            // Draw status text to the left of button
            int statusWidth = MinecraftClient.getInstance().textRenderer.getWidth(statusText);
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                statusText,
                buttonX - statusWidth - 10,
                y + 8,
                statusColor,
                false
            );
        }
        
        /**
         * Checks if the hire button was clicked.
         */
        public boolean isHireButtonClicked(double mouseX, double mouseY, int x, int y, int entryWidth, int entryHeight) {
            if (villager.isEmployed()) return false;
            int buttonWidth = 50;
            int buttonHeight = 14;
            int buttonY = y + entryHeight - buttonHeight - 3; // Match button position in render()
            int buttonX = x + entryWidth - buttonWidth - 5;
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                   mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
        
        /**
         * Checks if the fire button was clicked.
         */
        public boolean isFireButtonClicked(double mouseX, double mouseY, int x, int y, int entryWidth, int entryHeight) {
            if (!villager.isEmployed()) return false;
            int buttonWidth = 50;
            int buttonHeight = 14;
            int buttonY = y + entryHeight - buttonHeight - 3; // Match button position in render()
            int buttonX = x + entryWidth - buttonWidth - 5;
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                   mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
        
        /**
         * Checks if the assign/unassign work button was clicked.
         */
        public boolean isAssignWorkButtonClicked(double mouseX, double mouseY, int x, int y, int entryWidth, int entryHeight) {
            if (!villager.isEmployed()) return false;
            int buttonWidth = 50;
            int assignButtonWidth = 60;
            int buttonHeight = 14;
            int buttonY = y + entryHeight - buttonHeight - 3; // Match button position in render()
            int buttonX = x + entryWidth - buttonWidth - 5;
            int assignButtonX = buttonX - assignButtonWidth - 5;
            return mouseX >= assignButtonX && mouseX <= assignButtonX + assignButtonWidth &&
                   mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
        
        @Override
        public Text getNarration() {
            String name = villager.getName() != null && !villager.getName().isEmpty() 
                ? villager.getName()
                : Text.translatable("settlements.ui.villagers.unnamed").getString();
            String profession = villager.getProfession() != null && !villager.getProfession().isEmpty()
                ? villager.getProfession()
                : Text.translatable("settlements.ui.villagers.profession.none").getString();
            return Text.literal(name + " - " + profession);
        }
        
        public VillagerData getVillager() {
            return villager;
        }
    }
    
    /**
     * A single entry in the list for a golem.
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
            // Apply UI formatting rules: use shadow=true for dark backgrounds (improves readability)
            Text nameText = golem.getName() != null && !golem.getName().isEmpty() 
                ? Text.literal(golem.getName())
                : Text.translatable("settlements.ui.golems.unnamed");
            
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                nameText,
                x + 5,
                y + 3,
                0xFFFFFF,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw "Iron Golem" label
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal("Iron Golem"),
                x + 5,
                y + 15,
                0xCCCCCC,
                true // Use shadow for better visibility on dark background
            );
            
            // Draw assignment status
            int yOffset = 27;
            if (golem.isAssigned()) {
                Building assignedWallStation = null;
                if (settlement != null && golem.getAssignedWallStationId() != null) {
                    assignedWallStation = settlement.getBuildings().stream()
                        .filter(b -> b.getId().equals(golem.getAssignedWallStationId()))
                        .findFirst()
                        .orElse(null);
                }
                
                if (assignedWallStation != null) {
                    Identifier structureType = assignedWallStation.getStructureType();
                    String structureName = structureType.getPath();
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
                        true // Use shadow for better visibility on dark background
                    );
                } else {
                    Text assignmentText = Text.translatable("settlements.ui.golems.assigned");
                    context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        assignmentText,
                        x + 5,
                        y + yOffset,
                        0x00AAFF,
                        true // Use shadow for better visibility on dark background
                    );
                }
            } else {
                Text assignmentText = Text.translatable("settlements.ui.golems.unassigned");
                context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    assignmentText,
                    x + 5,
                    y + yOffset,
                    0xFFAA00,
                    true // Use shadow for better visibility on dark background
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
                int buttonColor = buttonHovered ? 0xFF2196F3 : 0xFF1976D2;
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
                int buttonColor = buttonHovered ? 0xFFFF9800 : 0xFFF57C00;
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
        
        public boolean isAssignButtonClicked(int mouseX, int mouseY, int x, int y, int entryWidth, int entryHeight) {
            if (!golem.isAssigned()) {
                int buttonWidth = 60;
                int buttonHeight = 14;
                int buttonY = y + entryHeight - buttonHeight - 3;
                int buttonX = x + entryWidth - buttonWidth - 5;
                return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                       mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
            }
            return false;
        }
        
        public boolean isUnassignButtonClicked(int mouseX, int mouseY, int x, int y, int entryWidth, int entryHeight) {
            if (golem.isAssigned()) {
                int buttonWidth = 60;
                int buttonHeight = 14;
                int buttonY = y + entryHeight - buttonHeight - 3;
                int buttonX = x + entryWidth - buttonWidth - 5;
                return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                       mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
            }
            return false;
        }
        
        public GolemData getGolem() {
            return golem;
        }
        
        @Override
        public Text getNarration() {
            String name = golem.getName() != null && !golem.getName().isEmpty() 
                ? golem.getName()
                : Text.translatable("settlements.ui.golems.unnamed").getString();
            return Text.literal(name + " - Iron Golem");
        }
    }
}

