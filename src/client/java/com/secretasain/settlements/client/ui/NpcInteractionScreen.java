package com.secretasain.settlements.client.ui;

import com.secretasain.settlements.network.NpcCommandPacketClient;
import com.secretasain.settlements.warband.NpcBehaviorState;
import com.secretasain.settlements.warband.WarbandNpcEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen for interacting with NPCs (right-click menu).
 */
public class NpcInteractionScreen extends Screen {
    private final WarbandNpcEntity npc;
    private final boolean initialAggressiveState;
    private final NpcBehaviorState initialBehaviorState;
    private ButtonWidget followButton;
    private ButtonWidget stayButton;
    private ButtonWidget aggressiveButton;
    private ButtonWidget dismissButton;
    
    public NpcInteractionScreen(WarbandNpcEntity npc, boolean aggressiveState, NpcBehaviorState behaviorState) {
        super(Text.translatable("settlements.warband.command.title"));
        this.npc = npc;
        this.initialAggressiveState = aggressiveState;
        this.initialBehaviorState = behaviorState;
    }
    
    // Legacy constructor for backwards compatibility
    public NpcInteractionScreen(WarbandNpcEntity npc) {
        this(npc, npc != null ? npc.isAggressive() : false, 
             npc != null ? npc.getBehaviorState() : NpcBehaviorState.FOLLOW);
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Safety check - if NPC is null or removed, close screen
        if (npc == null || npc.isRemoved() || !npc.isAlive()) {
            this.close();
            return;
        }
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonWidth = 150;
        int buttonHeight = 20;
        
        // Follow button
        followButton = ButtonWidget.builder(
            Text.translatable("settlements.warband.command.follow"),
            button -> {
                if (npc != null && !npc.isRemoved()) {
                    NpcCommandPacketClient.send(npc.getUuid(), NpcBehaviorState.FOLLOW, false);
                }
                this.close();
            }
        ).dimensions(centerX - buttonWidth / 2, centerY - 60, buttonWidth, buttonHeight).build();
        this.addDrawableChild(followButton);
        
        // Stay button
        stayButton = ButtonWidget.builder(
            Text.translatable("settlements.warband.command.stay"),
            button -> {
                if (npc != null && !npc.isRemoved()) {
                    NpcCommandPacketClient.send(npc.getUuid(), NpcBehaviorState.STAY, false);
                }
                this.close();
            }
        ).dimensions(centerX - buttonWidth / 2, centerY - 30, buttonWidth, buttonHeight).build();
        this.addDrawableChild(stayButton);
        
        // Aggressive mode toggle
        // Use initial state from server (accurate) instead of reading from client entity (may be stale)
        boolean currentAggressive = initialAggressiveState;
        String aggressiveText = currentAggressive
            ? Text.translatable("settlements.warband.command.aggressive_on").getString()
            : Text.translatable("settlements.warband.command.aggressive_off").getString();
        aggressiveButton = ButtonWidget.builder(
            Text.literal(aggressiveText),
            button -> {
                if (npc != null && !npc.isRemoved()) {
                    // Toggle aggressive mode - send current behavior state and flipped aggressive flag
                    boolean newAggressive = !currentAggressive;
                    NpcBehaviorState currentBehavior = initialBehaviorState != null ? initialBehaviorState : NpcBehaviorState.FOLLOW;
                    NpcCommandPacketClient.send(npc.getUuid(), currentBehavior, newAggressive);
                }
                this.close();
            }
        ).dimensions(centerX - buttonWidth / 2, centerY, buttonWidth, buttonHeight).build();
        this.addDrawableChild(aggressiveButton);
        
        // Dismiss button
        dismissButton = ButtonWidget.builder(
            Text.translatable("settlements.warband.dismiss"),
            button -> {
                if (npc != null && !npc.isRemoved()) {
                    com.secretasain.settlements.network.DismissNpcPacketClient.send(npc.getUuid(), npc.getBarracksBuildingId());
                }
                this.close();
            }
        ).dimensions(centerX - buttonWidth / 2, centerY + 30, buttonWidth, buttonHeight).build();
        this.addDrawableChild(dismissButton);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Safety check
        if (npc == null || npc.isRemoved()) {
            this.close();
            return;
        }
        
        // Update aggressive button text based on initial state from server (accurate)
        if (aggressiveButton != null) {
            String aggressiveText = initialAggressiveState
                ? Text.translatable("settlements.warband.command.aggressive_on").getString()
                : Text.translatable("settlements.warband.command.aggressive_off").getString();
            aggressiveButton.setMessage(Text.literal(aggressiveText));
        }
        
        // Draw title
        String npcClassName = npc.getNpcClass() != null ? npc.getNpcClass().getDisplayName().getString() : "NPC";
        String title = npcClassName + " - " + Text.translatable("settlements.warband.command.title").getString();
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, 40, 0xFFFFFF);
        
        // Draw current state (use initial state from server)
        if (initialBehaviorState != null) {
            String stateKey = "settlements.warband.command." + initialBehaviorState.name().toLowerCase();
            String stateText = Text.translatable(stateKey).getString();
            context.drawCenteredTextWithShadow(this.textRenderer, stateText, this.width / 2, 60, 0xAAAAAA);
        }
        
        // Draw aggressive mode status (use initial state from server)
        String aggressiveStatus = initialAggressiveState
            ? Text.translatable("settlements.warband.command.aggressive_on").getString()
            : Text.translatable("settlements.warband.command.aggressive_off").getString();
        context.drawCenteredTextWithShadow(this.textRenderer, aggressiveStatus, this.width / 2, 75, 0xAAAAAA);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

