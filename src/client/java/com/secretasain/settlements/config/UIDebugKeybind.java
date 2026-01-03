package com.secretasain.settlements.config;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinding for toggling UI debug titles.
 * Press F9 (default) to toggle debug UI titles on/off.
 */
public class UIDebugKeybind {
    private static KeyBinding toggleDebugTitlesKey;
    
    /**
     * Registers the keybinding for toggling debug UI titles.
     */
    public static void register() {
        toggleDebugTitlesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.settlements.toggle_ui_debug", // Translation key
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F9, // Default: F9
            "category.settlements.debug" // Category key
        ));
        
        // Register tick handler to check for key press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleDebugTitlesKey.wasPressed()) {
                boolean newState = UIDebugConfig.toggleDebugTitles();
                if (client.player != null) {
                    String message = newState 
                        ? "§aUI Debug Titles: §2ENABLED" 
                        : "§cUI Debug Titles: §4DISABLED";
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal(message),
                        false
                    );
                }
            }
        });
    }
}

