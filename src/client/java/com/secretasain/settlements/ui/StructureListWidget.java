package com.secretasain.settlements.ui;

import com.secretasain.settlements.building.StructureCategory;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Widget for displaying a list of available structures.
 * Compact sidebar that appears when Buildings tab is active.
 */
public class StructureListWidget extends AlwaysSelectedEntryListWidget<StructureListWidget.StructureEntry> {
    private final List<String> structureNames;
    private boolean visible = false;
    private boolean allowRendering = false; // Extra flag to prevent rendering
    private boolean forceDisable = false; // Force disable rendering completely
    
    public StructureListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
        super(client, width, height, top, bottom, itemHeight);
        this.structureNames = new ArrayList<>();
        this.updateEntries();
    }
    
    /**
     * Sets the visibility of the widget.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        this.allowRendering = visible; // Only allow rendering when explicitly set to visible
        if (!visible) {
            this.forceDisable = true; // Force disable when set to not visible
        }
    }
    
    public void setAllowRendering(boolean allow) {
        this.allowRendering = allow;
    }
    
    public void setForceDisable(boolean disable) {
        this.forceDisable = disable;
        if (disable) {
            this.visible = false;
            this.allowRendering = false;
        }
    }
    
    /**
     * Checks if the widget is visible.
     */
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * Sets the list of available structure names.
     * @param names List of structure names/identifiers
     */
    public void setStructures(List<String> names) {
        this.structureNames.clear();
        this.structureNames.addAll(names);
        this.updateEntries();
        
        // Auto-select first entry if available
        if (!this.children().isEmpty()) {
            this.setSelected(this.children().get(0));
            com.secretasain.settlements.SettlementsMod.LOGGER.info("Auto-selected first structure in setStructures: {}", 
                this.getSelectedStructure());
        }
    }
    
    /**
     * Updates the list entries, grouped by category.
     * Note: Category headers are displayed visually but not added as separate entries.
     * Structures are sorted by category and rendered with category headers.
     */
    public void updateEntries() {
        this.clearEntries();
        
        // Group structures by category
        Map<StructureCategory, List<String>> structuresByCategory = structureNames.stream()
            .collect(Collectors.groupingBy(StructureCategory::fromStructureName));
        
        // Sort categories by display order
        List<StructureCategory> sortedCategories = Arrays.asList(
            StructureCategory.DEFENSIVE,
            StructureCategory.RESIDENTIAL,
            StructureCategory.COMMERCIAL,
            StructureCategory.INDUSTRIAL,
            StructureCategory.DECORATIVE,
            StructureCategory.MISC
        );
        
        // Add structures grouped by category (category headers rendered separately)
        for (StructureCategory category : sortedCategories) {
            List<String> structures = structuresByCategory.get(category);
            if (structures != null && !structures.isEmpty()) {
                // Add structures in this category
                for (String name : structures) {
                    this.addEntry(new StructureEntry(name, category));
                }
            }
        }
    }
    
    /**
     * Gets the category for a structure at a given index.
     * Used for rendering category headers.
     */
    private StructureCategory getCategoryForIndex(int index) {
        if (index < 0 || index >= this.children().size()) {
            return StructureCategory.MISC;
        }
        StructureEntry entry = this.children().get(index);
        return entry != null ? entry.getCategory() : StructureCategory.MISC;
    }
    
    /**
     * Checks if a category header should be shown before the entry at the given index.
     */
    private boolean shouldShowCategoryHeader(int index) {
        if (index <= 0) {
            return true; // Always show header for first entry
        }
        StructureCategory currentCategory = getCategoryForIndex(index);
        StructureCategory previousCategory = getCategoryForIndex(index - 1);
        return !currentCategory.equals(previousCategory);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // CRITICAL: If force disabled, visible false, or rendering not allowed, do NOTHING
        // This prevents the parent class from rendering the default dirt texture background
        // MULTIPLE CHECKS: Return immediately - this is the PRIMARY guard
        if (forceDisable || !visible || !allowRendering) {
            return; // Completely skip ALL rendering
        }
        
        // TRIPLE CHECK: If we somehow got here but shouldn't render, return
        // Check if we're actually supposed to be rendering
        if (this.width <= 0 || this.height <= 0) {
            return; // Don't render if dimensions are invalid
        }
        
        // Get position and dimensions
        // Use getRowLeft() which should match setLeftPos(), but verify alignment
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Adjust X position if needed to align with button
        // getRowLeft() might have a small offset, so we use it directly
        // The widget should align perfectly with the button below
        
        // EXTRA SAFETY: If position is off-screen or invalid, don't render
        if (x < -1000 || y < -1000 || x > 10000 || y > 10000) {
            return; // Don't render if positioned off-screen
        }
        
        // FINAL CHECK: If dimensions are zero or negative, don't render
        if (width <= 0 || height <= 0) {
            return;
        }
        
        // Draw background for the list area ONLY - exact bounds, no extra padding
        // The sidebar background is drawn by renderStructureSidebar, we just draw the list area
        // Use exact bounds - no padding or offsets to ensure perfect alignment with button
        context.fill(x, y, x + width, y + height, 0xFF101010); // Dark gray background for list area
        
        // NO border here - the sidebar already has a border, we don't need another one
        // NO extra padding - we want exact alignment with the button below
        
        // Render entries manually WITHOUT calling super.render() to avoid parent's background
        int scrollAmount = (int)this.getScrollAmount();
        int startIndex = Math.max(0, scrollAmount / this.itemHeight);
        int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
        
        for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
            StructureEntry entry = this.children().get(i);
            int entryY = y + (i * this.itemHeight) - scrollAmount;
            
            if (entryY + this.itemHeight >= y && entryY <= y + height) {
                // Render category header if needed
                if (shouldShowCategoryHeader(i)) {
                    StructureCategory category = entry.getCategory();
                    CategoryHeaderEntry header = new CategoryHeaderEntry(category);
                    int headerY = entryY - this.itemHeight;
                    if (headerY >= y - this.itemHeight) {
                        header.render(context, i, headerY, x, width, this.itemHeight, mouseX, mouseY, false, delta);
                    }
                }
                
                boolean isSelected = this.getSelectedOrNull() == entry;
                boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                   mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
            }
        }
        
        // Render scrollbar if needed - position it inside the widget bounds
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarWidth = 4;
            int scrollbarPadding = 2; // Padding from right edge
            int scrollbarX = x + width - scrollbarWidth - scrollbarPadding; // Position inside widget
            int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
            int scrollbarY = y + (int)((scrollAmount / (float)maxScroll) * (height - scrollbarHeight));
            // Make sure scrollbar stays within bounds
            scrollbarY = Math.max(y, Math.min(scrollbarY, y + height - scrollbarHeight));
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
    }
    
    @Override
    protected void renderBackground(DrawContext context) {
        // Override to completely prevent default background rendering (which includes dirt texture)
        // Do NOT call super.renderBackground() - this prevents the dirt texture from showing
        // We draw our own background in render() method instead that matches the main window style
    }
    
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible || !allowRendering || forceDisable) {
            return false; // Don't respond to mouse when not visible
        }
        // Don't intercept mouse over below the list area (where the button is)
        // Check if mouse is below the widget's top + height
        double widgetBottom = this.top + this.height;
        if (mouseY >= widgetBottom) {
            return false; // Let mouse over below the list go through to the button
        }
        // Manually check bounds since we're using custom rendering
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible) {
            return false;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !allowRendering || forceDisable) {
            return false;
        }
        // Don't intercept clicks below the list area (where the button is)
        // The button is positioned at the bottom of the sidebar
        // Check if mouse is below the widget's top + height
        double widgetBottom = this.top + this.height;
        if (mouseY >= widgetBottom) {
            return false; // Let clicks below the list go through to the button
        }
        
        // Since we're using custom rendering, we need to manually detect which entry was clicked
        int x = this.getRowLeft();
        int y = this.top;
        int width = this.width;
        int height = this.bottom - this.top;
        
        // Check if click is within widget bounds
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        
        // Calculate which entry was clicked based on mouse position
        int scrollAmount = (int)this.getScrollAmount();
        int relativeY = (int)(mouseY - y + scrollAmount);
        int entryIndex = relativeY / this.itemHeight;
        
        if (entryIndex >= 0 && entryIndex < this.children().size()) {
            StructureEntry clickedEntry = this.children().get(entryIndex);
            // Verify the click is actually within the entry bounds
            int entryY = y + (entryIndex * this.itemHeight) - scrollAmount;
            if (mouseY >= entryY && mouseY <= entryY + this.itemHeight) {
                this.setSelected(clickedEntry);
                com.secretasain.settlements.SettlementsMod.LOGGER.info("Clicked structure entry: {}", clickedEntry.getName());
                return true;
            }
        }
        
        // Fallback to parent implementation for scrolling, etc.
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible) {
            return false;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    /**
     * Gets the selected structure name.
     * @return Selected structure name, or null if none selected
     */
    public String getSelectedStructure() {
        StructureEntry selected = this.getSelectedOrNull();
        return selected != null ? selected.getName() : null;
    }
    
    /**
     * Gets the selected structure entry (for category access).
     * @return Selected StructureEntry, or null if none selected
     */
    public StructureEntry getSelectedStructureEntry() {
        return this.getSelectedOrNull();
    }
    
    /**
     * A category header entry in the structure list.
     */
    public static class CategoryHeaderEntry extends AlwaysSelectedEntryListWidget.Entry<CategoryHeaderEntry> {
        private final StructureCategory category;
        
        public CategoryHeaderEntry(StructureCategory category) {
            this.category = category;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, 
                          int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw category header with background
            context.fill(x, y, x + entryWidth, y + entryHeight, 0xFF404040);
            context.drawBorder(x, y, entryWidth, entryHeight, 0xFF606060);
            
            // Draw category name
            Text categoryText = category.getDisplayName();
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                categoryText,
                x + 5,
                y + (entryHeight - 8) / 2,
                0xFFFFFF,
                false
            );
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("settlements.ui.buildings.category", category.getDisplayName());
        }
        
        public StructureCategory getCategory() {
            return category;
        }
    }
    
    /**
     * A single entry in the structure list.
     */
    public static class StructureEntry extends AlwaysSelectedEntryListWidget.Entry<StructureEntry> {
        private final String name;
        private final StructureCategory category;
        
        public StructureEntry(String name) {
            this.name = name;
            this.category = StructureCategory.fromStructureName(name);
        }
        
        public StructureEntry(String name, StructureCategory category) {
            this.name = name;
            this.category = category;
        }
        
        public String getName() {
            return name;
        }
        
        public StructureCategory getCategory() {
            return category;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover (more subtle)
            if (hovered) {
                context.fill(x + 2, y + 1, x + entryWidth - 2, y + entryHeight - 1, 0x40FFFFFF);
            }
            
            // Determine block icon based on structure name
            Block iconBlock = getBlockForStructure(name);
            ItemStack iconStack = new ItemStack(iconBlock);
            
            // Draw compact block icon (12x12) - smaller and more elegant
            int iconX = x + 4;
            int iconY = y + 2;
            context.drawItem(iconStack, iconX, iconY);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, iconStack, iconX, iconY);
            
            // Format name for display (remove .nbt, replace underscores with spaces, capitalize)
            String displayName = name;
            if (displayName.endsWith(".nbt")) {
                displayName = displayName.substring(0, displayName.length() - 4);
            }
            displayName = displayName.replace("_", " ");
            // Capitalize first letter of each word
            String[] words = displayName.split(" ");
            StringBuilder formatted = new StringBuilder();
            for (String word : words) {
                if (formatted.length() > 0) formatted.append(" ");
                if (!word.isEmpty()) {
                    formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
                }
            }
            
            // Truncate long names
            String finalName = formatted.toString();
            int maxWidth = entryWidth - 24; // Leave room for icon and padding
            if (MinecraftClient.getInstance().textRenderer.getWidth(finalName) > maxWidth) {
                finalName = MinecraftClient.getInstance().textRenderer.trimToWidth(finalName, maxWidth - 3) + "...";
            }
            
            // Draw text next to icon - compact layout
            int textX = x + 20; // Start after icon (12px) + padding (8px)
            int textY = y + (entryHeight - 9) / 2; // Center vertically (9 is text height)
            
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                finalName,
                textX,
                textY,
                hovered ? 0xFFFFFF : 0xCCCCCC,
                false
            );
        }
        
        /**
         * Determines which block icon to show based on structure name.
         */
        private Block getBlockForStructure(String structureName) {
            String lowerName = structureName.toLowerCase();
            
            // Check for structure types first
            if (lowerName.contains("cartographer")) {
                return Blocks.CARTOGRAPHY_TABLE;
            } else if (lowerName.contains("farm")) {
                return Blocks.FARMLAND;
            } else if (lowerName.contains("smith") || lowerName.contains("forge") || lowerName.contains("smithing")) {
                return Blocks.SMITHING_TABLE;
            } else if (lowerName.contains("fence")) {
                return Blocks.OAK_FENCE;
            } else if (lowerName.contains("gate")) {
                return Blocks.OAK_FENCE_GATE;
            } else if (lowerName.contains("house") || lowerName.contains("home")) {
                return Blocks.OAK_PLANKS; // Houses use oak planks icon
            }
            
            // Check for material types
            if (lowerName.contains("oak")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return Blocks.OAK_PLANKS;
                }
                return Blocks.OAK_LOG;
            } else if (lowerName.contains("spruce")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return Blocks.SPRUCE_PLANKS;
                }
                return Blocks.SPRUCE_LOG;
            } else if (lowerName.contains("birch")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return Blocks.BIRCH_PLANKS;
                }
                return Blocks.BIRCH_LOG;
            } else if (lowerName.contains("jungle")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return Blocks.JUNGLE_PLANKS;
                }
                return Blocks.JUNGLE_LOG;
            } else if (lowerName.contains("acacia")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return Blocks.ACACIA_PLANKS;
                }
                return Blocks.ACACIA_LOG;
            } else if (lowerName.contains("dark_oak") || lowerName.contains("darkoak")) {
                if (lowerName.contains("plank") || lowerName.contains("wall")) {
                    return Blocks.DARK_OAK_PLANKS;
                }
                return Blocks.DARK_OAK_LOG;
            } else if (lowerName.contains("stone") || lowerName.contains("cobble")) {
                return Blocks.COBBLESTONE;
            } else if (lowerName.contains("brick")) {
                return Blocks.BRICKS;
            } else if (lowerName.contains("wood") || lowerName.contains("log")) {
                return Blocks.OAK_LOG;
            }
            
            // Default to oak planks for unknown structures
            return Blocks.OAK_PLANKS;
        }
        
        @Override
        public Text getNarration() {
            return Text.literal("Structure: " + name);
        }
    }
}

