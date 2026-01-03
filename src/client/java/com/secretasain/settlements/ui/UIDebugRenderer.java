package com.secretasain.settlements.ui;

import com.secretasain.settlements.config.UIDebugConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Utility class for rendering debug UI titles on widgets.
 * Helps identify which widget is which during development and debugging.
 */
public class UIDebugRenderer {
    /**
     * Renders a debug title for a widget at the specified position.
     * Only renders if debug titles are enabled in the config.
     * 
     * @param context The DrawContext to render with
     * @param widgetName The name of the widget to display
     * @param x The X position (top-left corner of widget)
     * @param y The Y position (top of widget)
     * @param width The width of the widget (for centering)
     */
    public static void renderWidgetTitle(DrawContext context, String widgetName, int x, int y, int width) {
        if (!UIDebugConfig.isDebugTitlesEnabled()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }
        
        // Render title with background for visibility
        String title = "[" + widgetName + "]";
        int titleWidth = client.textRenderer.getWidth(title);
        int titleX = x + (width - titleWidth) / 2; // Center horizontally
        int titleY = y - client.textRenderer.fontHeight - 2; // Above widget with 2px spacing
        
        // Draw semi-transparent background behind text
        int padding = 2;
        context.fill(
            titleX - padding, 
            titleY - padding, 
            titleX + titleWidth + padding, 
            titleY + client.textRenderer.fontHeight + padding, 
            0x80000000 // Black with 50% opacity
        );
        
        // Draw the title text
        context.drawText(
            client.textRenderer,
            Text.literal(title),
            titleX,
            titleY,
            0xFFFF00, // Yellow color for visibility
            true // Use shadow for better visibility
        );
    }
    
    /**
     * Renders a debug title for a widget at the specified position with custom color.
     * Only renders if debug titles are enabled in the config.
     * 
     * @param context The DrawContext to render with
     * @param widgetName The name of the widget to display
     * @param x The X position (top-left corner of widget)
     * @param y The Y position (top of widget)
     * @param width The width of the widget (for centering)
     * @param color The color to use for the text (0xRRGGBB format)
     */
    public static void renderWidgetTitle(DrawContext context, String widgetName, int x, int y, int width, int color) {
        if (!UIDebugConfig.isDebugTitlesEnabled()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }
        
        // Render title with background for visibility
        String title = "[" + widgetName + "]";
        int titleWidth = client.textRenderer.getWidth(title);
        int titleX = x + (width - titleWidth) / 2; // Center horizontally
        int titleY = y - client.textRenderer.fontHeight - 2; // Above widget with 2px spacing
        
        // Draw semi-transparent background behind text
        int padding = 2;
        context.fill(
            titleX - padding, 
            titleY - padding, 
            titleX + titleWidth + padding, 
            titleY + client.textRenderer.fontHeight + padding, 
            0x80000000 // Black with 50% opacity
        );
        
        // Draw the title text
        context.drawText(
            client.textRenderer,
            Text.literal(title),
            titleX,
            titleY,
            color,
            true // Use shadow for better visibility
        );
    }
}

