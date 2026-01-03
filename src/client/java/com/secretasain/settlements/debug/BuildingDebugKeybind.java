package com.secretasain.settlements.debug;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind for toggling building debug outline rendering.
 */
public class BuildingDebugKeybind {
    private static KeyBinding toggleDebugOutline;
    
    /**
     * Registers the debug outline toggle keybind.
     * Should be called during client initialization.
     */
    public static void register() {
        toggleDebugOutline = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.settlements.toggle_building_debug",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "key.categories.settlements"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleDebugOutline.wasPressed()) {
                BuildingDebugOutlineRenderer.toggle();
                if (client.player != null) {
                    boolean enabled = BuildingDebugOutlineRenderer.isEnabled();
                    String message = enabled 
                        ? "Building debug outline: ON (showing all buildings)"
                        : "Building debug outline: OFF";
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal(message),
                        false
                    );
                }
            }
        });
    }
}

