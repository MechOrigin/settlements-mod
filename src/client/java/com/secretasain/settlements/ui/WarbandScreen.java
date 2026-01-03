package com.secretasain.settlements.ui;

import com.secretasain.settlements.settlement.Building;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.warband.NpcClass;
import com.secretasain.settlements.warband.ParagonLevel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen for managing warband NPCs at a barracks building.
 */
public class WarbandScreen extends Screen {
    private final Building barracksBuilding;
    private WarbandPanelWidget warriorPanel;
    private WarbandPanelWidget priestPanel;
    private WarbandPanelWidget magePanel;
    
    public WarbandScreen(Building barracksBuilding) {
        super(Text.translatable("settlements.warband.title"));
        this.barracksBuilding = barracksBuilding;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Screen dimensions
        int screenWidth = 400;
        int screenHeight = 280;
        int x = (this.width - screenWidth) / 2;
        int y = (this.height - screenHeight) / 2;
        
        // Create three panels at top center
        int panelWidth = 100;
        int panelHeight = 120;
        int panelSpacing = 10;
        int totalWidth = panelWidth * 3 + panelSpacing * 2;
        int panelStartX = x + (screenWidth - totalWidth) / 2;
        int panelY = y + 20;
        
        // Warrior panel (implemented)
        warriorPanel = new WarbandPanelWidget(
            this.client,
            panelStartX,
            panelY,
            panelWidth,
            panelHeight,
            NpcClass.WARRIOR,
            this::onHireNpc,
            this::onDismissNpc
        );
        warriorPanel.initButtons(button -> this.addDrawableChild(button));
        
        // Priest panel (not implemented - grayed out)
        priestPanel = new WarbandPanelWidget(
            this.client,
            panelStartX + panelWidth + panelSpacing,
            panelY,
            panelWidth,
            panelHeight,
            NpcClass.PRIEST,
            null, // No hire handler - not implemented
            null  // No dismiss handler - not implemented
        );
        priestPanel.setEnabled(false);
        priestPanel.initButtons(button -> this.addDrawableChild(button));
        
        // Mage panel (not implemented - grayed out)
        magePanel = new WarbandPanelWidget(
            this.client,
            panelStartX + (panelWidth + panelSpacing) * 2,
            panelY,
            panelWidth,
            panelHeight,
            NpcClass.MAGE,
            null, // No hire handler - not implemented
            null  // No dismiss handler - not implemented
        );
        magePanel.setEnabled(false);
        magePanel.initButtons(button -> this.addDrawableChild(button));
        
        // Close button
        int closeButtonY = y + screenHeight - 30;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.cancel"),
            button -> this.close()
        ).dimensions(x + screenWidth / 2 - 50, closeButtonY, 100, 20).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background
        this.renderBackground(context);
        
        // Render title
        int titleX = (this.width - this.textRenderer.getWidth(this.title)) / 2;
        context.drawText(this.textRenderer, this.title, titleX, 10, 0xFFFFFF, false);
        
        // Render barracks info
        String barracksInfo = "Barracks: " + barracksBuilding.getPosition().toShortString();
        context.drawText(this.textRenderer, barracksInfo, 
            (this.width - this.textRenderer.getWidth(barracksInfo)) / 2, 30, 0xCCCCCC, false);
        
        // Render panels
        if (warriorPanel != null) {
            warriorPanel.render(context, mouseX, mouseY, delta);
        }
        if (priestPanel != null) {
            priestPanel.render(context, mouseX, mouseY, delta);
        }
        if (magePanel != null) {
            magePanel.render(context, mouseX, mouseY, delta);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    /**
     * Called when player clicks to hire an NPC.
     */
    private void onHireNpc(NpcClass npcClass, ParagonLevel paragonLevel) {
        // TODO: Send HireNpcPacket to server
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Hire NPC: class={}, paragon={}", npcClass, paragonLevel);
    }
    
    /**
     * Called when player clicks to dismiss an NPC.
     */
    private void onDismissNpc(NpcClass npcClass) {
        // TODO: Send DismissNpcPacket to server
        com.secretasain.settlements.SettlementsMod.LOGGER.info("Dismiss NPC: class={}", npcClass);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

