package com.secretasain.settlements.ui;

import com.secretasain.settlements.settlement.VillagerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Widget for displaying a scrollable list of villagers.
 */
public class VillagerListWidget extends AlwaysSelectedEntryListWidget<VillagerListWidget.VillagerEntry> {
    private final List<VillagerData> villagers;
    
    public VillagerListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, List<VillagerData> villagers) {
        super(client, width, height, top, bottom, itemHeight);
        this.villagers = villagers;
        this.updateEntries();
    }
    
    /**
     * Updates the list entries from the villager data.
     */
    public void updateEntries() {
        this.clearEntries();
        for (VillagerData villager : villagers) {
            this.addEntry(new VillagerEntry(villager));
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
        
        // Render entries manually WITHOUT calling super.render() to avoid parent's background
        int scrollAmount = (int)this.getScrollAmount();
        int startIndex = Math.max(0, scrollAmount / this.itemHeight);
        int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
        
        for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
            VillagerEntry entry = this.children().get(i);
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
        // Override to completely prevent default background rendering (which includes dirt texture)
        // Do NOT call super.renderBackground() - this prevents the dirt texture from showing
        // We draw our own background in render() method instead that matches the main window style
    }
    
    /**
     * A single entry in the villager list.
     */
    public static class VillagerEntry extends AlwaysSelectedEntryListWidget.Entry<VillagerEntry> {
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
            Text nameText = villager.getName() != null && !villager.getName().isEmpty() 
                ? Text.literal(villager.getName())
                : Text.translatable("settlements.ui.villagers.unnamed");
            
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                nameText,
                x + 5,
                y + 3,
                0xFFFFFF,
                false
            );
            
            // Draw profession - better spacing
            Text professionText;
            if (villager.getProfession() != null && !villager.getProfession().isEmpty()) {
                // Format profession name (remove namespace if present)
                String profession = villager.getProfession();
                if (profession.contains(":")) {
                    profession = profession.substring(profession.indexOf(':') + 1);
                }
                // Capitalize first letter
                profession = profession.substring(0, 1).toUpperCase() + profession.substring(1);
                professionText = Text.literal(profession);
            } else {
                professionText = Text.translatable("settlements.ui.villagers.profession.none");
            }
            
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                professionText,
                x + 5,
                y + 13,
                0xCCCCCC,
                false
            );
            
            // Draw employment status - better positioning
            Text statusText = villager.isEmployed() 
                ? Text.translatable("settlements.ui.villagers.status.employed")
                : Text.translatable("settlements.ui.villagers.status.unemployed");
            int statusColor = villager.isEmployed() ? 0x00FF00 : 0xFFAA00;
            
            // Calculate text width for right alignment
            int statusWidth = MinecraftClient.getInstance().textRenderer.getWidth(statusText);
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                statusText,
                x + entryWidth - statusWidth - 5,
                y + 8,
                statusColor,
                false
            );
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
}

