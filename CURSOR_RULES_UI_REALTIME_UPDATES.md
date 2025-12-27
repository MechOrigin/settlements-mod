# Cursor Rules for Real-Time UI Updates

## Critical Rule: Always Update UI in Real-Time

### Principle
**When a user action triggers a server-side change, the UI MUST be updated immediately (optimistically) before the server response is received.** This provides instant feedback and prevents the UI from appearing unresponsive.

### Implementation Pattern

#### 1. Optimistic Updates
When a user action occurs (button click, selection, etc.):

1. **Immediately update the local data model** (the `settlement` object or relevant data structure)
2. **Immediately refresh the UI** (call `updateEntries()`, `updateButtons()`, etc.)
3. **Then send the packet to the server** (for server-side validation and persistence)

**Example Pattern:**
```java
button.setOnClick(() -> {
    // 1. Optimistically update local data
    villager.setEmployed(true);
    
    // 2. Update UI immediately
    villagerListWidget.updateEntries();
    
    // 3. Send packet to server
    HireFireVillagerPacketClient.send(villagerId, settlementId, true);
});
```

#### 2. When to Use Optimistic Updates
Use optimistic updates for:
- **Hire/Fire villager** - Update employment status immediately
- **Assign/Unassign work** - Update assignment status immediately
- **Building actions** - Update building status immediately (start, cancel, remove)
- **Any action that changes visible UI state**

#### 3. When NOT to Use Optimistic Updates
Do NOT use optimistic updates for:
- **Actions that require server validation** (e.g., checking if player has enough emeralds)
  - In these cases, wait for server response before updating UI
- **Actions that might fail** (e.g., insufficient materials)
  - Show error message and revert UI if server rejects the action

#### 4. Error Handling
If the server rejects an optimistic update:
- **Revert the local data** to the previous state
- **Update the UI** to reflect the reverted state
- **Show an error message** to the user

**Example:**
```java
// If server rejects, revert the optimistic update
if (serverResponse.isError()) {
    villager.setEmployed(false); // Revert
    villagerListWidget.updateEntries(); // Refresh UI
    showError("Failed to hire villager: " + serverResponse.getError());
}
```

### UI Update Methods

#### For List Widgets
- **`updateEntries()`** - Refreshes the entire list from the current data source
- Call this immediately after updating the underlying data

#### For Button States
- **`button.active = true/false`** - Enable/disable buttons
- **`button.visible = true/false`** - Show/hide buttons
- Update these based on the new state immediately

#### For Text/Display Updates
- **`context.drawText()`** - Text is drawn each frame, so updating the data source is sufficient
- No explicit refresh needed if data is updated

### Best Practices

1. **Always update UI immediately** - Don't wait for server response
2. **Update the data model first** - Then refresh UI components
3. **Send server packet after UI update** - Server validates and persists
4. **Handle errors gracefully** - Revert optimistic updates if server rejects
5. **Test with network lag** - Ensure UI feels responsive even with slow connections

### Examples from Codebase

#### ✅ CORRECT: Optimistic Update Pattern
```java
villagerListWidget.setOnFireCallback(villager -> {
    // 1. Update local data immediately
    villager.setEmployed(false);
    villager.setAssignedBuildingId(null);
    
    // 2. Update UI immediately
    villagerListWidget.updateEntries();
    
    // 3. Send to server
    HireFireVillagerPacketClient.send(villagerId, settlementId, false);
});
```

#### ❌ WRONG: Waiting for Server Response
```java
villagerListWidget.setOnFireCallback(villager -> {
    // Sending packet first - UI won't update until server responds
    HireFireVillagerPacketClient.send(villagerId, settlementId, false);
    
    // This runs immediately but data hasn't changed yet
    villagerListWidget.updateEntries(); // Still shows old state!
});
```

### Related Files
- `SettlementScreen.java` - Main UI screen with villager list
- `VillagerListWidget.java` - Villager list widget
- `BuildingListWidget.java` - Building list widget
- All network packet handlers should follow this pattern

---

**Remember: The UI should ALWAYS feel instant and responsive. Optimistic updates make the UI feel snappy even with network latency.**

