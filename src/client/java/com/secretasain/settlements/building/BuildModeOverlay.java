package com.secretasain.settlements.building;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Renders the build mode overlay UI.
 */
public class BuildModeOverlay {
    /**
     * Renders the overlay if build mode is active.
     */
    public static void render(DrawContext context, float tickDelta) {
        if (!ClientBuildModeManager.isActive()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        
        StructureData structure = ClientBuildModeManager.getActiveStructure();
        BlockPos placementPos = ClientBuildModeManager.getPlacementPos();
        int rotation = ClientBuildModeManager.getRotation();
        
        if (structure == null) {
            return;
        }
        
        // Draw semi-transparent background (height increased by 5)
        context.fill(10, 10, 250, 155, 0x80000000);
        context.drawBorder(10, 10, 240, 145, 0xFF404040);
        
        int y = 20;
        int lineHeight = 12;
        
        // Title
        context.drawText(
            client.textRenderer,
            Text.literal("Build Mode"),
            20,
            y,
            0xFFFFFF,
            false
        );
        y += lineHeight + 5;
        
        // Structure name
        if (structure.getName() != null) {
            context.drawText(
                client.textRenderer,
                Text.literal("Structure: " + structure.getName()),
                20,
                y,
                0xCCCCCC,
                false
            );
            y += lineHeight;
        }
        
        // Placement position
        if (placementPos != null) {
            context.drawText(
                client.textRenderer,
                Text.literal(String.format("Position: %d, %d, %d", 
                    placementPos.getX(), placementPos.getY(), placementPos.getZ())),
                20,
                y,
                0xCCCCCC,
                false
            );
            y += lineHeight;
        }
        
        // Rotation
        context.drawText(
            client.textRenderer,
            Text.literal("Rotation: " + rotation + "°"),
            20,
            y,
            0xCCCCCC,
            false
        );
        y += lineHeight + 5;
        
        // Controls
        context.drawText(
            client.textRenderer,
            Text.literal("Controls:"),
            20,
            y,
            0xFFFFFF,
            false
        );
        y += lineHeight;
        
        context.drawText(
            client.textRenderer,
            Text.literal("R - Rotate clockwise"),
            20,
            y,
            0xAAAAAA,
            false
        );
        y += lineHeight;
        
        context.drawText(
            client.textRenderer,
            Text.literal("Arrow Keys - Move structure"),
            20,
            y,
            0xAAAAAA,
            false
        );
        y += lineHeight;
        
        context.drawText(
            client.textRenderer,
            Text.literal("Space/X - Move up/down"),
            20,
            y,
            0xAAAAAA,
            false
        );
        y += lineHeight;
        
        context.drawText(
            client.textRenderer,
            Text.literal("ENTER - Confirm placement"),
            20,
            y,
            0xAAAAAA,
            false
        );
        y += lineHeight;
        
        context.drawText(
            client.textRenderer,
            Text.literal("ESC - Exit build mode"),
            20,
            y,
            0xAAAAAA,
            false
        );
    }
}

