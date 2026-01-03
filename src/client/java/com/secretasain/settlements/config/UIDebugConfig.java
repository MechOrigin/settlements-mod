package com.secretasain.settlements.config;

/**
 * Configuration for UI debug features.
 * Allows enabling/disabling debug UI titles to help identify widgets during development.
 */
public class UIDebugConfig {
    /**
     * Whether to show debug titles on UI widgets.
     * Set to true to see widget names rendered on screen for debugging.
     * Default: false (disabled)
     */
    public static boolean SHOW_UI_DEBUG_TITLES = false;
    
    /**
     * Toggles the debug UI titles feature.
     * @return The new state after toggling
     */
    public static boolean toggleDebugTitles() {
        SHOW_UI_DEBUG_TITLES = !SHOW_UI_DEBUG_TITLES;
        return SHOW_UI_DEBUG_TITLES;
    }
    
    /**
     * Sets whether debug UI titles should be shown.
     * @param enabled True to enable, false to disable
     */
    public static void setDebugTitlesEnabled(boolean enabled) {
        SHOW_UI_DEBUG_TITLES = enabled;
    }
    
    /**
     * Checks if debug UI titles are enabled.
     * @return True if debug titles should be shown
     */
    public static boolean isDebugTitlesEnabled() {
        return SHOW_UI_DEBUG_TITLES;
    }
}

