# Cursor Rules for Minecraft UI Text & Element Formatting

## Overview
This document contains best practices for rendering text, icons, widgets, and UI elements in Minecraft Fabric mods. These rules help prevent overlap, ensure proper spacing, and create clean, readable interfaces.

---

## Text Rendering Best Practices

### TextRenderer Usage
**Always use `TextRenderer` for text rendering, not direct `font` calls in screen rendering.**

```java
// ✅ CORRECT: Use textRenderer from Screen
@Override
public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    TextRenderer textRenderer = this.textRenderer;
    context.drawText(textRenderer, text, x, y, color, shadow);
}

// ❌ WRONG: Don't use font directly
// context.drawText(this.client.textRenderer, text, x, y, color, shadow);
```

### Text Positioning & Spacing

#### Vertical Spacing
- **Line Height**: Use at least 10 pixels between lines of text
- **Multi-line Text**: Use `textRenderer.getWrappedLinesHeight()` for accurate height calculation
- **Label Spacing**: Leave at least 2-4 pixels between label and value

```java
// ✅ CORRECT: Proper line spacing
int lineHeight = textRenderer.fontHeight + 2; // 2px padding between lines
int y = startY;
context.drawText(textRenderer, line1, x, y, color, false);
y += lineHeight;
context.drawText(textRenderer, line2, x, y, color, false);

// ❌ WRONG: Overlapping text
context.drawText(textRenderer, line1, x, y, color, false);
context.drawText(textRenderer, line2, x, y, color, false); // Overlaps!
```

#### Horizontal Spacing
- **Text to Icon**: Minimum 4 pixels between text and icon
- **Text Padding**: Add 2-4 pixels padding on sides of text
- **Column Spacing**: Minimum 8 pixels between columns

```java
// ✅ CORRECT: Proper horizontal spacing
int iconWidth = 16;
int padding = 4;
int textX = iconX + iconWidth + padding;
context.drawText(textRenderer, text, textX, y, color, false);
```

### Text Wrapping
**Always wrap text that might exceed widget bounds.**

```java
// ✅ CORRECT: Wrapped text
int maxWidth = widgetWidth - padding * 2;
OrderedText wrapped = TextRenderer.wrapLines(text, maxWidth);
int lineHeight = textRenderer.fontHeight + 2;
for (int i = 0; i < wrapped.size() && i < maxLines; i++) {
    int yPos = startY + (i * lineHeight);
    context.drawText(textRenderer, wrapped.get(i), x, yPos, color, false);
}
```

### Text Shadow
- **Dark Backgrounds**: Use shadow = true (improves readability)
- **Light Backgrounds**: Use shadow = false or lighter text color
- **Consistency**: Use same shadow setting for related text elements

---

## Widget Layout & Spacing

### Widget Positioning

#### Screen Coordinates
- **Origin**: Top-left corner is (0, 0)
- **X-axis**: Increases rightward
- **Y-axis**: Increases downward
- **Always validate**: Ensure widgets don't exceed screen bounds

```java
// ✅ CORRECT: Bounded widget positioning
int widgetX = Math.max(0, Math.min(screenWidth - widgetWidth, x));
int widgetY = Math.max(0, Math.min(screenHeight - widgetHeight, y));
```

#### Widget Spacing Rules
- **Between Widgets**: Minimum 4 pixels spacing
- **Widget Padding**: 4-8 pixels internal padding
- **Screen Edge**: 4-8 pixels margin from screen edges
- **Grouped Widgets**: 2-4 pixels between grouped items, 8+ pixels between groups

### Widget Bounds Checking
**Always check widget bounds before rendering to prevent overflow.**

```java
// ✅ CORRECT: Bounds checking
public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    if (this.x < 0 || this.y < 0 || 
        this.x + this.width > screenWidth || 
        this.y + this.height > screenHeight) {
        // Adjust or skip rendering
        return;
    }
    // Render widget...
}
```

### List Widgets

#### List Entry Spacing
- **Entry Height**: Calculate based on content + padding
- **Entry Padding**: 2-4 pixels vertical padding per entry
- **Scrollbar**: Reserve 6-8 pixels width for scrollbar

```java
// ✅ CORRECT: List entry spacing
public static final int ENTRY_HEIGHT = 20; // Base height
public static final int ENTRY_PADDING = 2; // Top/bottom padding
public int getItemHeight() {
    return ENTRY_HEIGHT + (ENTRY_PADDING * 2);
}
```

#### List Bounds
- **Clip Rendering**: Use scissor or clipping to prevent overflow
- **Scroll Calculation**: Ensure scroll doesn't go negative or exceed content

```java
// ✅ CORRECT: Clipped list rendering
RenderSystem.enableScissor(x, y, width, height);
// Render list entries...
RenderSystem.disableScissor();
```

---

## Icon & Image Rendering

### Icon Positioning
- **Icon Size**: Standard icons are 16x16 pixels
- **Icon Alignment**: Align icons to text baseline or center vertically
- **Icon Spacing**: Minimum 4 pixels between icon and text

```java
// ✅ CORRECT: Icon with text spacing
int iconX = x;
int iconY = y + (textHeight / 2) - (iconSize / 2); // Center vertically
context.drawTexture(ICON_TEXTURE, iconX, iconY, 0, 0, 16, 16, 16, 16);
int textX = iconX + 16 + 4; // 4px spacing
context.drawText(textRenderer, text, textX, y, color, false);
```

### Texture Rendering
- **UV Coordinates**: Always use correct UV coordinates for texture atlas
- **Scaling**: Maintain aspect ratio when scaling textures
- **Tinting**: Use `setShaderColor()` for tinting, reset to white after

```java
// ✅ CORRECT: Texture rendering with tint reset
RenderSystem.setShaderColor(r, g, b, alpha);
context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset to white
```

---

## Tooltip Rendering

### Tooltip Positioning
- **Mouse Offset**: Position tooltip offset from mouse cursor (8-12 pixels)
- **Screen Bounds**: Ensure tooltip doesn't exceed screen bounds
- **Z-Order**: Render tooltips last (on top of other elements)

```java
// ✅ CORRECT: Bounded tooltip positioning
int tooltipX = mouseX + 12;
int tooltipY = mouseY + 12;
// Adjust if tooltip exceeds screen bounds
if (tooltipX + tooltipWidth > screenWidth) {
    tooltipX = mouseX - tooltipWidth - 12; // Show to left of cursor
}
if (tooltipY + tooltipHeight > screenHeight) {
    tooltipY = mouseY - tooltipHeight - 12; // Show above cursor
}
```

### Tooltip Spacing
- **Text Padding**: 4-6 pixels padding inside tooltip
- **Line Spacing**: 2 pixels between tooltip lines
- **Max Width**: Limit tooltip width to prevent overly wide tooltips (suggested: 200-250 pixels)

```java
// ✅ CORRECT: Tooltip rendering with proper spacing
int padding = 4;
int lineHeight = textRenderer.fontHeight + 2;
int maxWidth = 200;
List<OrderedText> wrapped = TextRenderer.wrapLines(tooltipText, maxWidth);
int tooltipHeight = (wrapped.size() * lineHeight) + (padding * 2);
// Render tooltip background...
// Render wrapped text with padding...
```

### Preventing Tooltip Overlap
- **Single Tooltip**: Only show one tooltip at a time
- **Priority System**: Higher priority tooltips override lower priority
- **Delay**: Add small delay before showing tooltip (prevents flicker)

---

## Dynamic UI Refresh

### Refresh Triggers
**UI must refresh when underlying data changes.**

- **Settlement Data Changes**: Refresh buildings list, villager list
- **Building Status Changes**: Refresh building entries immediately
- **Villager Assignment Changes**: Refresh villager list and building assignments
- **Tab Switching**: Refresh all widgets when switching tabs

### Refresh Implementation
```java
// ✅ CORRECT: Refresh method pattern
public void refreshData() {
    // Clear cached data
    this.cachedBuildings = null;
    this.cachedVillagers = null;
    
    // Request fresh data from server
    requestDataFromServer();
    
    // Rebuild widget children
    this.clearChildren();
    this.rebuildWidgets();
}
```

### Preventing Overlay Issues
- **Clear Children**: Always clear widget children before rebuilding
- **Remove Listeners**: Remove old event listeners before adding new ones
- **State Reset**: Reset widget state when switching tabs

```java
// ✅ CORRECT: Clean widget rebuild
public void rebuildWidgets() {
    // Clear existing widgets
    this.clearChildren();
    
    // Remove old listeners
    this.listeners.clear();
    
    // Rebuild widgets from fresh data
    for (Building building : this.buildings) {
        Widget widget = createBuildingWidget(building);
        this.addDrawableChild(widget);
        this.listeners.add(widget);
    }
}
```

---

## Common Patterns & Anti-Patterns

### ✅ GOOD: Proper Spacing Pattern
```java
public void renderWidget(DrawContext context, int x, int y) {
    int padding = 4;
    int spacing = 8;
    
    // Icon
    int iconX = x + padding;
    int iconY = y + padding;
    context.drawTexture(icon, iconX, iconY, 0, 0, 16, 16, 16, 16);
    
    // Text (spaced from icon)
    int textX = iconX + 16 + spacing;
    int textY = y + padding;
    context.drawText(textRenderer, label, textX, textY, color, false);
    
    // Value (spaced from label)
    int valueX = textX + textRenderer.getWidth(label) + spacing;
    context.drawText(textRenderer, value, valueX, textY, color, false);
}
```

### ❌ BAD: Overlapping Elements
```java
// Text and icon overlap
context.drawTexture(icon, x, y, 0, 0, 16, 16, 16, 16);
context.drawText(textRenderer, text, x, y, color, false); // Overlaps icon!

// No spacing between elements
context.drawText(textRenderer, label, x, y, color, false);
context.drawText(textRenderer, value, x, y, color, false); // Overlaps label!
```

### ✅ GOOD: Bounded List Rendering
```java
public void renderList(DrawContext context, int x, int y, int width, int height) {
    int scrollbarWidth = 6;
    int listWidth = width - scrollbarWidth;
    
    // Enable scissor to clip overflow
    RenderSystem.enableScissor(x, y, listWidth, height);
    
    int entryHeight = getItemHeight();
    int startIndex = Math.max(0, scrollOffset / entryHeight);
    int endIndex = Math.min(items.size(), startIndex + (height / entryHeight) + 1);
    
    for (int i = startIndex; i < endIndex; i++) {
        int entryY = y + (i * entryHeight) - scrollOffset;
        renderEntry(context, items.get(i), x, entryY, listWidth, entryHeight);
    }
    
    RenderSystem.disableScissor();
    
    // Render scrollbar
    renderScrollbar(context, x + listWidth, y, scrollbarWidth, height);
}
```

### ❌ BAD: Unbounded Rendering
```java
// Renders all items without clipping - causes overflow
for (int i = 0; i < items.size(); i++) {
    int entryY = y + (i * 20);
    renderEntry(context, items.get(i), x, entryY, width, 20);
    // No bounds checking - items render outside widget!
}
```

---

## Screen & Widget Size Calculations

### Screen Dimensions
```java
// ✅ CORRECT: Use screen dimensions from client
int screenWidth = this.client.getWindow().getScaledWidth();
int screenHeight = this.client.getWindow().getScaledHeight();
```

### Widget Dimensions
- **Fixed Size Widgets**: Define constants for widget dimensions
- **Dynamic Size Widgets**: Calculate size based on content
- **Min/Max Sizes**: Define minimum and maximum sizes for resizable widgets

```java
// ✅ CORRECT: Widget dimension constants
public static final int WIDGET_WIDTH = 200;
public static final int WIDGET_HEIGHT = 100;
public static final int MIN_WIDTH = 150;
public static final int MAX_WIDTH = 300;
```

---

## Accordion/Collapsible Sections

### Implementation Pattern
```java
public class AccordionWidget extends Widget {
    private boolean expanded = true;
    private int headerHeight = 20;
    private int contentHeight = 0; // Calculated based on content
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render header (always visible)
        renderHeader(context, x, y, width, headerHeight);
        
        // Render content (only if expanded)
        if (expanded) {
            int contentY = y + headerHeight;
            renderContent(context, x, contentY, width, contentHeight);
        }
    }
    
    @Override
    public int getHeight() {
        return headerHeight + (expanded ? contentHeight : 0);
    }
}
```

### Spacing in Accordions
- **Header Padding**: 4 pixels padding in header
- **Content Padding**: 4-8 pixels padding around content
- **Between Sections**: 2-4 pixels between accordion sections

---

## Performance Considerations

### Rendering Optimization
- **Scissor Clipping**: Always use scissor for list widgets to prevent off-screen rendering
- **Culling**: Don't render widgets outside screen bounds
- **Cache Calculations**: Cache text width/height calculations when possible
- **Batch Rendering**: Group similar render calls together

### Refresh Optimization
- **Partial Refresh**: Only refresh changed widgets, not entire screen
- **Debouncing**: Debounce rapid refresh requests
- **Lazy Loading**: Load data only when widget is visible

---

## Testing Checklist

Before committing UI changes, verify:
- [ ] No text/icon overlap in any screen state
- [ ] All widgets refresh when data changes
- [ ] No UI artifacts when switching tabs
- [ ] Lists don't overflow their containers
- [ ] Tooltips don't overlap or exceed screen bounds
- [ ] Proper spacing between all UI elements
- [ ] Widgets are properly centered/aligned
- [ ] Accordion sections work correctly
- [ ] Scrollbars appear when content exceeds bounds
- [ ] All text is readable (proper color/shadow)

---

## References

- **Minecraft Source**: Check `net.minecraft.client.gui` package for examples
- **Fabric API**: `net.fabricmc.fabric.api.client.screen.v1` for screen helpers
- **TextRenderer**: `net.minecraft.client.font.TextRenderer` for text rendering
- **DrawContext**: `net.minecraft.client.gui.DrawContext` for rendering in 1.20.1+

---

*Last Updated: Based on Minecraft 1.20.1 / Fabric API patterns*

