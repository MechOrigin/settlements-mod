# Bug Fix Plan

## Bugs to Fix

### 1. Keyboard Input Issues
- [x] Arrow keys don't work moving the NBT structure - FIXED: Removed screen check that was blocking keybinds
- [x] Space/X doesn't work at all - FIXED: Removed screen check that was blocking keybinds
- [x] Shift key doesn't work in game at all (even when UI is closed) - FIXED: Removed shift key interception from SettlementScreen

### 2. Rendering Issues
- [x] Render of place down showing buggy clipping blocks (top/bottom showing through other blocks) - FIXED: Ensured depth test is properly enabled

### 3. Material Management Issues
- [x] Unload materials button does nothing - should load buffer items, delete from internal inventory, transfer to adjacent chest - FIXED: Added better error handling and logging

### 4. Building Ledger Issues
- [x] Building ledger is mostly empty - doesn't load saved data from the build and deposit info in the text book - FIXED: Added better logging and fallback to requiredMaterials if providedMaterials is empty

---

## Fix Details

### Bug 1: Keyboard Input Issues

**Root Cause**: 
- Arrow keys, Space, and X keybinds are registered but only work when build mode is active AND no screen is open
- Shift key is being intercepted by SettlementScreen even when screen is closed
- Keybinds use `wasPressed()` which only fires once per press, not continuously

**Solution**:
1. Remove shift key interception from SettlementScreen when screen is not active
2. Ensure arrow keys work in build mode even when screen might be open
3. Fix Space/X keybinds to work properly
4. Remove any global shift key handling that interferes with game controls

### Bug 2: Rendering Clipping

**Root Cause**:
- Ghost block rendering may have depth testing issues
- Blocks rendering with incorrect depth sorting

**Solution**:
- Fix depth testing in GhostBlockRendererUtility
- Ensure proper depth mask and depth test settings
- Check render order and layer settings

### Bug 3: Unload Materials Button

**Root Cause**:
- Button sends packet correctly
- MaterialManager.returnMaterialsToChests may not be working properly
- Materials might not be getting transferred to chests

**Solution**:
- Verify MaterialManager.returnMaterialsToChests implementation
- Ensure materials are actually being transferred
- Check if chests are being found correctly
- Verify providedMaterials is being cleared after transfer

### Bug 4: Building Ledger Empty

**Root Cause**:
- Book creation reads from `building.getProvidedMaterials()` 
- This might be empty when book is created (cleared too early)
- Materials data might not be saved properly

**Solution**:
- Ensure providedMaterials is read BEFORE clearing
- Verify materials are being saved to building data
- Check if materials are being loaded from NBT correctly
- Add fallback to requiredMaterials if providedMaterials is empty

---

### Bug 5: Material List Widget Showing Twice and Overlapping

**Root Cause**:
- Material list widget is being created in both the render loop AND the selection callback
- This causes duplicate widgets to be added to the screen
- The render loop check `currentSelected != lastSelectedBuilding` was creating widgets every frame when selection changed

**Solution**:
- Remove material list widget creation from render loop
- Rely only on selection callback to create/update material list widget
- Ensure widget is properly removed before creating new one

---

### Bug 6: Building Selection Not Populating Required Materials

**Root Cause**:
- Building selection callback might not be firing properly
- Auto-selection of first building doesn't trigger callback (programmatic selection)
- Buildings might not have required materials set when loaded from NBT

**Solution**:
- Add manual callback trigger after auto-selection
- Add better logging to debug selection issues
- Ensure required materials are loaded from NBT correctly
- Verify buildings have required materials when created

---

### Bug 7: Check Materials Button Not Working

**Root Cause**:
- Check materials requires a building to be selected
- If building selection isn't working, `getSelectedBuilding()` returns null
- This causes the "please select a building first" message

**Solution**:
- Fix building selection to work properly
- Ensure auto-selection works on UI open
- Add better error messages

---

### Bug 8: Check Materials Not Taking Exact Required Amounts

**Root Cause**:
- When calculating how much is still needed, the code only checks what's in settlement storage
- It doesn't account for what's already been taken from chests in the current operation
- This causes it to take more than needed when multiple chests contain the same material
- Example: Need 10, have 5 in storage, 10 in chest 1, 10 in chest 2
  - Chest 1: Takes 5 (correct: 10 - 5 = 5)
  - Chest 2: Takes 5 (WRONG: Should be 0, because we already took 5 from chest 1)

**Solution**:
- Track what's been taken in `foundMaterials` during the operation
- Include `foundMaterials` in the `stillNeeded` calculation: `totalHave = alreadyInStorage + alreadyTaken`
- Only take exactly what's needed: `stillNeeded = required - totalHave`
- Fix stack decrementing to create new stack instead of modifying existing one

---

### Bug 9: Building Selection Not Working When Clicking on Reserved Buildings

**Root Cause**:
- When clicking on a building entry in the list, selection isn't being set properly
- The `mouseClicked` method checks for button clicks but may not be properly handling entry selection
- Clicking on the entry itself (not the buttons) may not trigger selection
- The parent's `mouseClicked` may not be called properly, or selection isn't being set

**Solution**:
- Explicitly set selection when clicking on an entry (not buttons)
- Ensure parent's `mouseClicked` is called to handle selection
- Add comprehensive logging to debug selection issues
- Log all buildings in list and which one is selected when check materials is clicked
- Ensure selection callback fires when selection changes

---

### Bug 10: ConcurrentModificationException When Selecting Building

**Root Cause**:
- When building selection changes, the callback immediately calls `createMaterialListWidget()`
- `createMaterialListWidget()` modifies the children list (removes and adds widgets)
- This happens while `mouseClicked` is still iterating over the children list
- Causes `ConcurrentModificationException` at `ParentElement.mouseClicked`

**Solution**:
- Defer widget creation until after mouse click handling is complete
- Use `client.execute()` to schedule widget creation for the next tick
- This ensures the children list isn't modified during iteration
- Prevents the crash while maintaining functionality

