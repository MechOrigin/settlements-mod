package com.secretasain.settlements.ui;

import com.secretasain.settlements.warband.NpcClass;
import com.secretasain.settlements.warband.NpcGear;
import com.secretasain.settlements.warband.ParagonLevel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Widget representing a single NPC class panel in the warband screen.
 * Displays class name, paragon levels, and hire/dismiss buttons.
 */
public class WarbandPanelWidget {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final NpcClass npcClass;
    private final BiConsumer<NpcClass, ParagonLevel> onHire;
    private final Consumer<NpcClass> onDismiss;
    private boolean enabled = true;
    private ParagonLevel selectedParagonLevel = ParagonLevel.I;
    private boolean isHired = false; // TODO: Check actual hired status from player data
    
    private ButtonWidget hireButton;
    private ButtonWidget dismissButton;
    private ButtonWidget paragonUpButton;
    private ButtonWidget paragonDownButton;
    
    // Getters for position and size (needed for button cleanup)
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    // Get all buttons for cleanup
    public java.util.List<ButtonWidget> getButtons() {
        java.util.List<ButtonWidget> buttons = new java.util.ArrayList<>();
        if (hireButton != null) buttons.add(hireButton);
        if (dismissButton != null) buttons.add(dismissButton);
        if (paragonUpButton != null) buttons.add(paragonUpButton);
        if (paragonDownButton != null) buttons.add(paragonDownButton);
        return buttons;
    }
    
    public WarbandPanelWidget(MinecraftClient client, int x, int y, int width, int height,
                             NpcClass npcClass,
                             BiConsumer<NpcClass, ParagonLevel> onHire,
                             Consumer<NpcClass> onDismiss) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.npcClass = npcClass;
        this.onHire = onHire;
        this.onDismiss = onDismiss;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (hireButton != null) {
            hireButton.active = enabled && !isHired;
        }
        if (dismissButton != null) {
            dismissButton.active = enabled && isHired;
        }
        if (paragonUpButton != null) {
            paragonUpButton.active = enabled;
        }
        if (paragonDownButton != null) {
            paragonDownButton.active = enabled;
        }
    }
    
    public void setHired(boolean hired) {
        this.isHired = hired;
        if (hireButton != null) {
            hireButton.active = enabled && !isHired;
            hireButton.visible = !isHired;
        }
        if (dismissButton != null) {
            dismissButton.active = enabled && isHired;
            dismissButton.visible = isHired;
        }
    }
    
    public void initButtons(java.util.function.Consumer<ButtonWidget> addButton) {
        int buttonWidth = this.width - 10;
        int buttonHeight = 20;
        int buttonY = this.y + this.height - 60;
        
        // Paragon level selector buttons
        paragonDownButton = ButtonWidget.builder(
            Text.literal("<"),
            button -> {
                ParagonLevel prev = selectedParagonLevel.getPrevious();
                if (prev != null) {
                    selectedParagonLevel = prev;
                    updateParagonButtons();
                }
            }
        ).dimensions(this.x + 5, buttonY, 20, buttonHeight).build();
        paragonDownButton.active = enabled && selectedParagonLevel.getPrevious() != null;
        addButton.accept(paragonDownButton);
        
        paragonUpButton = ButtonWidget.builder(
            Text.literal(">"),
            button -> {
                ParagonLevel next = selectedParagonLevel.getNext();
                if (next != null) {
                    selectedParagonLevel = next;
                    updateParagonButtons();
                }
            }
        ).dimensions(this.x + this.width - 25, buttonY, 20, buttonHeight).build();
        paragonUpButton.active = enabled && selectedParagonLevel.getNext() != null;
        addButton.accept(paragonUpButton);
        
        // Hire/Dismiss button - make it more obvious
        buttonY += buttonHeight + 5;
        hireButton = ButtonWidget.builder(
            Text.empty(), // Empty text - we'll render emerald icon + cost + "Hire" manually
            button -> {
                if (onHire != null) {
                    onHire.accept(npcClass, selectedParagonLevel);
                }
            }
        ).dimensions(this.x + 5, buttonY, buttonWidth, buttonHeight).build();
        hireButton.active = enabled && !isHired && npcClass.isImplemented();
        hireButton.visible = !isHired;
        addButton.accept(hireButton);
        
        dismissButton = ButtonWidget.builder(
            Text.translatable("settlements.warband.dismiss"),
            button -> {
                if (onDismiss != null) {
                    onDismiss.accept(npcClass);
                }
            }
        ).dimensions(this.x + 5, buttonY, buttonWidth, buttonHeight).build();
        dismissButton.active = enabled && isHired;
        dismissButton.visible = isHired;
        addButton.accept(dismissButton);
    }
    
    private void updateParagonButtons() {
        if (paragonDownButton != null) {
            paragonDownButton.active = enabled && selectedParagonLevel.getPrevious() != null;
        }
        if (paragonUpButton != null) {
            paragonUpButton.active = enabled && selectedParagonLevel.getNext() != null;
        }
    }
    
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render panel background
        int bgColor = enabled ? 0xFF2C2C2C : 0xFF1A1A1A;
        context.fill(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);
        
        // Render border
        int borderColor = enabled ? (isHired ? 0xFF00FF00 : 0xFFFFFFFF) : 0xFF666666;
        context.drawBorder(this.x, this.y, this.width, this.height, borderColor);
        
        // Render class name
        Text className = npcClass.getDisplayName();
        int textX = this.x + (this.width - client.textRenderer.getWidth(className)) / 2;
        int textColor = enabled ? 0xFFFFFF : 0x888888;
        context.drawText(client.textRenderer, className, textX, this.y + 5, textColor, false);
        
        // Render paragon level
        Text paragonText = selectedParagonLevel.getDisplayName();
        textX = this.x + (this.width - client.textRenderer.getWidth(paragonText)) / 2;
        context.drawText(client.textRenderer, paragonText, textX, this.y + 20, textColor, false);
        
        // Render gear preview (simple item icons)
        renderGearPreview(context, this.x + 10, this.y + 35);
        
        // Render level requirement
        String levelReq = "Lvl " + selectedParagonLevel.getRequiredPlayerLevel();
        textX = this.x + (this.width - client.textRenderer.getWidth(levelReq)) / 2;
        context.drawText(client.textRenderer, levelReq, textX, this.y + 80, textColor, false);
        
        // Render emerald cost on hire button (if not hired and enabled)
        // Position: [Emerald Icon] [Cost] [Hire] - all on the button
        if (!isHired && enabled && npcClass.isImplemented() && hireButton != null && hireButton.visible) {
            int cost = calculateHireCost(selectedParagonLevel);
            int buttonX = hireButton.getX();
            int buttonY = hireButton.getY();
            int buttonHeight = hireButton.getHeight();
            
            // Calculate positions: emerald icon and cost on left, "Hire" text after
            int emeraldX = buttonX + 5; // Small padding from left edge
            int emeraldY = buttonY + (buttonHeight - 16) / 2; // Center vertically in button
            
            // Draw emerald icon
            ItemStack emeraldStack = new ItemStack(Items.EMERALD);
            context.drawItem(emeraldStack, emeraldX, emeraldY);
            context.drawItemInSlot(client.textRenderer, emeraldStack, emeraldX, emeraldY);
            
            // Draw cost text next to emerald (16px icon + 4px spacing)
            Text costText = Text.literal(String.valueOf(cost));
            int costX = emeraldX + 16 + 4;
            int costY = buttonY + (buttonHeight - client.textRenderer.fontHeight) / 2;
            context.drawText(client.textRenderer, costText, costX, costY, 0x00FF00, true);
            
            // Draw "Hire" text after the cost (cost text width + 4px spacing)
            Text hireText = Text.translatable("settlements.warband.hire");
            int hireX = costX + client.textRenderer.getWidth(costText) + 4;
            int hireY = buttonY + (buttonHeight - client.textRenderer.fontHeight) / 2;
            int hireTextColor = hireButton.active ? 0xFFFFFF : 0xA0A0A0;
            context.drawText(client.textRenderer, hireText, hireX, hireY, hireTextColor, true);
        }
        
        // Render tooltip if disabled
        if (!enabled && mouseX >= this.x && mouseX < this.x + this.width &&
            mouseY >= this.y && mouseY < this.y + this.height) {
            if (!npcClass.isImplemented()) {
                context.drawTooltip(client.textRenderer, 
                    java.util.List.of(Text.translatable("settlements.warband.not_implemented")),
                    mouseX, mouseY);
            }
        }
    }
    
    /**
     * Calculates the hiring cost based on paragon level.
     * Higher paragon levels cost more emeralds.
     */
    private int calculateHireCost(ParagonLevel level) {
        return switch (level) {
            case I -> 10;   // 10 emeralds for Paragon I
            case II -> 25;  // 25 emeralds for Paragon II
            case III -> 50; // 50 emeralds for Paragon III
            case IV -> 100; // 100 emeralds for Paragon IV
        };
    }
    
    private void renderGearPreview(DrawContext context, int x, int y) {
        NpcGear gear = NpcGear.forParagonLevel(selectedParagonLevel);
        
        // Render sword icon
        if (gear.getSword() != null) {
            context.drawItem(new ItemStack(gear.getSword()), x, y);
        }
        
        // Render shield icon
        if (gear.getShield() != null) {
            context.drawItem(new ItemStack(gear.getShield()), x + 18, y);
        }
        
        // Render helmet icon
        if (gear.getHelmet() != null) {
            context.drawItem(new ItemStack(gear.getHelmet()), x + 36, y);
        }
    }
}
