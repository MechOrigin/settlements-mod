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
        
        // Draw background
        context.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xFF101010);
        context.drawBorder(x - 5, y - 5, width + 10, height + 10, 0xFF404040);
        
        // Draw title
        String titleText = requiredMaterials != null && !requiredMaterials.isEmpty() 
            ? "Required Materials (" + requiredMaterials.size() + ")" 
            : "Required Materials";
        int titleWidth = this.client.textRenderer.getWidth(titleText);
        context.drawText(
            this.client.textRenderer,
            Text.literal(titleText),
            x + (width - titleWidth) / 2, // Center the title
            y - 12,
            0xFFFFFF,
            false
        );
        
        // Show message if no materials
        if (requiredMaterials == null || requiredMaterials.isEmpty()) {
            String noMaterialsText = "No materials required";
            int textWidth = this.client.textRenderer.getWidth(noMaterialsText);
            context.drawText(
                this.client.textRenderer,
                Text.literal(noMaterialsText),
                x + (width - textWidth) / 2,
                y + 10,
                0xAAAAAA,
                false
            );
            return;
        }
        
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
            
            // Handle custom message (e.g., "No materials required")
            if (customMessage != null) {
                context.drawText(
                    client.textRenderer,
                    Text.literal(customMessage),
                    x + 5,
                    y + 3,
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
            
            // Draw material name and count
            String materialText = String.format("%s: %d/%d", formatted.toString(), available, required);
            context.drawText(
                client.textRenderer,
                Text.literal(materialText),
                x + 5,
                y + 3,
                color,
                false
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

