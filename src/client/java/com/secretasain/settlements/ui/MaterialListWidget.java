package com.secretasain.settlements.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Widget for displaying required materials for a building.
 * Shows required vs available materials with color coding.
 */
public class MaterialListWidget extends AlwaysSelectedEntryListWidget<MaterialListWidget.MaterialEntry> {
    private final Map<Identifier, Integer> requiredMaterials;
    private Map<String, Integer> availableMaterials; // Settlement uses String keys (mutable for updates)
    
    public MaterialListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight,
                             Map<Identifier, Integer> requiredMaterials, Map<String, Integer> availableMaterials) {
        super(client, width, height, top, bottom, itemHeight);
        this.requiredMaterials = requiredMaterials;
        this.availableMaterials = availableMaterials;
        this.updateEntries();
    }
    
    /**
     * Updates the available materials map (called when materials are synced from server).
     * @param newAvailableMaterials The updated available materials map
     */
    public void updateAvailableMaterials(Map<String, Integer> newAvailableMaterials) {
        this.availableMaterials = newAvailableMaterials;
    }
    
    /**
     * Updates the list entries from the material data.
     */
    public void updateEntries() {
        this.clearEntries();
        
        if (requiredMaterials == null || requiredMaterials.isEmpty()) {
            // Add a message entry if no materials required
            this.addEntry(new MaterialEntry(null, 0, 0, "No materials required"));
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("MaterialListWidget: No materials required, showing message");
            return;
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.debug("MaterialListWidget: Adding {} material entries", requiredMaterials.size());
        for (Map.Entry<Identifier, Integer> entry : requiredMaterials.entrySet()) {
            Identifier materialId = entry.getKey();
            int required = entry.getValue();
            
            // Get available count from settlement materials (convert Identifier to String key)
            String materialKey = materialId.toString();
            int available = availableMaterials.getOrDefault(materialKey, 0);
            
            com.secretasain.settlements.SettlementsMod.LOGGER.debug("MaterialListWidget: Adding entry - {}: {}/{}", materialId, available, required);
            this.addEntry(new MaterialEntry(materialId, required, available));
        }
        
        com.secretasain.settlements.SettlementsMod.LOGGER.debug("MaterialListWidget: Total entries after update: {}", this.children().size());
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
        UIDebugRenderer.renderWidgetTitle(context, "MaterialListWidget", x, y, width);
        
        // Draw title
        // Apply UI formatting rules: proper title spacing (4-8 pixels margin from screen edges)
        String titleText = requiredMaterials != null && !requiredMaterials.isEmpty() 
            ? "Required Materials (" + requiredMaterials.size() + ")" 
            : "Required Materials";
        int titleWidth = this.client.textRenderer.getWidth(titleText);
        int titleY = y - this.client.textRenderer.fontHeight - 4; // 4px spacing from widget top
        context.drawText(
            this.client.textRenderer,
            Text.literal(titleText),
            x + (width - titleWidth) / 2, // Center the title
            titleY,
            0xFFFFFF,
            false
        );
        
        // Show message if no materials
        if (requiredMaterials == null || requiredMaterials.isEmpty()) {
            String noMaterialsText = "No materials required";
            int textWidth = this.client.textRenderer.getWidth(noMaterialsText);
            int padding = 4; // Apply UI formatting rules: widget padding
            context.drawText(
                this.client.textRenderer,
                Text.literal(noMaterialsText),
                x + (width - textWidth) / 2,
                y + padding + this.client.textRenderer.fontHeight,
                0xAAAAAA,
                false
            );
            return;
        }
        
        // Apply UI formatting rules: use scissor clipping to prevent list overflow
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(x, y, width, height);
        
        // Render entries
        int scrollAmount = (int)this.getScrollAmount();
        int startIndex = Math.max(0, scrollAmount / this.itemHeight);
        int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
        
        for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
            MaterialEntry entry = this.children().get(i);
            int entryY = y + (i * this.itemHeight) - scrollAmount;
            
            if (entryY + this.itemHeight >= y && entryY <= y + height) {
                boolean isSelected = this.getSelectedOrNull() == entry;
                boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                   mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
            }
        }
        
        // Disable scissor after rendering entries
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        
        // Render scrollbar if needed (outside scissor area)
        // Apply UI formatting rules: reserve 6-8 pixels width for scrollbar
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarWidth = 6;
            int scrollbarX = x + width - scrollbarWidth;
            int scrollbarHeight = Math.max(4, (int)((height / (float)(maxScroll + height)) * height));
            int scrollbarY = y + (int)((scrollAmount / (float)maxScroll) * (height - scrollbarHeight));
            context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
    }
    
    @Override
    protected void renderBackground(DrawContext context) {
        // Override to prevent default background rendering
    }
    
    /**
     * A single entry in the material list.
     */
    public static class MaterialEntry extends AlwaysSelectedEntryListWidget.Entry<MaterialEntry> {
        private final Identifier materialId;
        private final int required;
        private final int available;
        private final String customMessage; // For special messages like "No materials required"
        
        public MaterialEntry(Identifier materialId, int required, int available) {
            this.materialId = materialId;
            this.required = required;
            this.available = available;
            this.customMessage = null;
        }
        
        public MaterialEntry(Identifier materialId, int required, int available, String customMessage) {
            this.materialId = materialId;
            this.required = required;
            this.available = available;
            this.customMessage = customMessage;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background on hover
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x33FFFFFF);
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            // Apply UI formatting rules: proper padding (4 pixels)
            int padding = 4;
            
            // Handle custom message (e.g., "No materials required")
            if (customMessage != null) {
                context.drawText(
                    client.textRenderer,
                    Text.literal(customMessage),
                    x + padding,
                    y + padding,
                    0xAAAAAA, // Gray color for informational message
                    false
                );
                return;
            }
            
            // Get item name from identifier
            String itemName = materialId != null ? materialId.getPath() : "unknown";
            if (itemName.contains("/")) {
                itemName = itemName.substring(itemName.lastIndexOf('/') + 1);
            }
            // Format name: replace underscores with spaces and capitalize
            itemName = itemName.replace('_', ' ');
            String[] words = itemName.split(" ");
            StringBuilder formatted = new StringBuilder();
            for (String word : words) {
                if (formatted.length() > 0) formatted.append(" ");
                if (!word.isEmpty()) {
                    formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
                }
            }
            
            // Determine color based on availability
            int color;
            if (available >= required) {
                color = 0x00FF00; // Green - sufficient
            } else if (available > 0) {
                color = 0xFFFF00; // Yellow - partial
            } else {
                color = 0xFF0000; // Red - missing
            }
            
            // Apply UI formatting rules: icon and text spacing
            int iconSize = 16; // Standard icon size
            int iconSpacing = 4; // Minimum 4px spacing between icon and text (UI formatting rules)
            
            // Get item from identifier and draw icon
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(materialId);
            if (item != null) {
                net.minecraft.item.ItemStack itemStack = new net.minecraft.item.ItemStack(item);
                
                // Draw icon FIRST (before text) - apply UI formatting rules
                int iconX = x + padding;
                int iconY = y + padding; // Align with text baseline
                context.drawItem(itemStack, iconX, iconY);
                context.drawItemInSlot(client.textRenderer, itemStack, iconX, iconY);
            }
            
            // Draw material name and count AFTER icon with proper spacing
            // Apply UI formatting rules: proper padding and spacing
            String materialText = String.format("%s: %d/%d", formatted.toString(), available, required);
            int textX = x + padding + (item != null ? iconSize + iconSpacing : 0); // After icon + spacing, or just padding if no icon
            // Truncate text if too long to prevent overlap
            int maxTextWidth = entryWidth - textX - padding; // Leave room for icon, spacing, and padding
            if (client.textRenderer.getWidth(materialText) > maxTextWidth) {
                materialText = client.textRenderer.trimToWidth(materialText, maxTextWidth - 3) + "...";
            }
            
            context.drawText(
                client.textRenderer,
                Text.literal(materialText),
                textX,
                y + padding,
                color,
                true // Use shadow for better visibility on dark background
            );
        }
        
        @Override
        public Text getNarration() {
            return Text.literal(materialId.toString() + " - " + available + "/" + required);
        }
        
        public Identifier getMaterialId() {
            return materialId;
        }
        
        public int getRequired() {
            return required;
        }
        
        public int getAvailable() {
            return available;
        }
    }
}

