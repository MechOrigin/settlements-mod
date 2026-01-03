package com.secretasain.settlements.ui;

import com.secretasain.settlements.warband.NpcData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Widget for displaying a scrollable list of hired NPCs.
 */
@SuppressWarnings("rawtypes")
public class HiredNpcListWidget extends AlwaysSelectedEntryListWidget {
    private final List<NpcData> npcs;
    private Consumer<UUID> onDismissCallback; // UUID is entityId
    
    public HiredNpcListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, List<NpcData> npcs) {
        super(client, width, height, top, bottom, itemHeight);
        this.npcs = npcs;
        this.updateEntries();
    }
    
    /**
     * Sets the callback to be called when a NPC's dismiss button is clicked.
     */
    public void setOnDismissCallback(Consumer<UUID> callback) {
        this.onDismissCallback = callback;
    }
    
    /**
     * Updates the list entries from the NPC data.
     */
    public void updateEntries() {
        this.clearEntries();
        for (NpcData npc : npcs) {
            if (npc.isHired()) {
                this.addEntry(new NpcEntry(npc));
            }
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // CRITICAL: Only render if we're on Warband tab
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
        com.secretasain.settlements.ui.UIDebugRenderer.renderWidgetTitle(context, "HiredNpcListWidget", x, y, width);
        
        // Enable scissor clipping to constrain rendering within widget bounds
        context.enableScissor(x, y, x + width, y + height);
        
        try {
            // Render entries manually WITHOUT calling super.render() to avoid parent's background
            int scrollAmount = (int)this.getScrollAmount();
            int startIndex = Math.max(0, scrollAmount / this.itemHeight);
            int endIndex = Math.min(this.children().size(), startIndex + (height / this.itemHeight) + 2);
            
            for (int i = startIndex; i < endIndex && i < this.children().size(); i++) {
                @SuppressWarnings("unchecked")
                net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget.Entry<?> entry = 
                    (net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget.Entry<?>) this.children().get(i);
                int entryY = y + (i * this.itemHeight) - scrollAmount;
                
                // Only render if entry is within visible bounds
                if (entryY + this.itemHeight >= y && entryY <= y + height) {
                    boolean isSelected = this.getSelectedOrNull() == entry;
                    boolean isHovered = mouseX >= x && mouseX <= x + width && 
                                       mouseY >= entryY && mouseY <= entryY + this.itemHeight;
                    
                    // Store entry position for mouse click handling
                    if (entry instanceof NpcEntry) {
                        NpcEntry npcEntry = (NpcEntry) entry;
                        npcEntry.setPosition(x, entryY, width);
                    }
                    
                    entry.render(context, i, entryY, x, width, this.itemHeight, mouseX, mouseY, isSelected || isHovered, delta);
                }
            }
            
            // Render scrollbar if needed (inside scissor area)
            if (this.getMaxScroll() > 0) {
                int scrollbarX = x + width - 6;
                int scrollbarY = y;
                int scrollbarHeight = height;
                int scrollbarWidth = 4;
                
                // Calculate scrollbar thumb position and size
                int thumbHeight = Math.max(10, (int)(scrollbarHeight * (scrollbarHeight / (double)(this.getMaxScroll() + scrollbarHeight))));
                int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * (this.getScrollAmount() / (double)this.getMaxScroll()));
                
                // Draw scrollbar track
                context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFF404040);
                // Draw scrollbar thumb
                context.fill(scrollbarX + 1, thumbY, scrollbarX + scrollbarWidth - 1, thumbY + thumbHeight, 0xFF808080);
            }
        } finally {
            context.disableScissor();
        }
    }
    
    /**
     * Entry class for displaying an NPC in the list.
     */
    private class NpcEntry extends Entry<NpcEntry> {
        private final NpcData npc;
        private ButtonWidget dismissButton;
        private int entryX = 0;
        private int entryY = 0;
        private int entryWidth = 0;
        
        public NpcEntry(NpcData npc) {
            this.npc = npc;
        }
        
        /**
         * Sets the entry's position for mouse click handling.
         */
        public void setPosition(int x, int y, int width) {
            this.entryX = x;
            this.entryY = y;
            this.entryWidth = width;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw background
            int bgColor = hovered ? 0xFF2A2A2A : 0xFF1E1E1E;
            context.fill(x, y, x + entryWidth, y + entryHeight, bgColor);
            
            // Draw NPC class and paragon level (moved up to avoid button overlap)
            String npcText = npc.getNpcClass().getDisplayName().getString() + " (" + npc.getParagonLevel().getDisplayName().getString() + ")";
            context.drawText(client.textRenderer, npcText, x + 5, y + 5, 0xFFFFFF, false);
            
            // Draw entity ID (for debugging, can be removed later) - moved down
            String entityIdText = "ID: " + npc.getEntityId().toString().substring(0, 8) + "...";
            context.drawText(client.textRenderer, entityIdText, x + 5, y + 17, 0xAAAAAA, false);
            
            // Create dismiss button if it doesn't exist (positioned below text to avoid overlap)
            int buttonY = y + 25; // Position button below text (5px top padding + 12px text + 8px spacing)
            int buttonWidth = 65;
            int buttonX = x + entryWidth - buttonWidth - 5; // Right-aligned with 5px padding
            
            if (dismissButton == null) {
                dismissButton = ButtonWidget.builder(
                    Text.translatable("settlements.warband.dismiss"),
                    button -> {
                        if (onDismissCallback != null) {
                            onDismissCallback.accept(npc.getEntityId());
                        }
                    }
                ).dimensions(buttonX, buttonY, buttonWidth, 20).build();
            } else {
                dismissButton.setX(buttonX);
                dismissButton.setY(buttonY);
            }
            
            // Render dismiss button
            dismissButton.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (dismissButton != null && dismissButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("settlements.warband.npc_entry", 
                npc.getNpcClass().getDisplayName(), 
                npc.getParagonLevel().getDisplayName());
        }
    }
}

