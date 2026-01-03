# UI Elements Reference Guide

This document provides a comprehensive reference for all UI elements, widgets, and classes in the Settlements Mod UI system. Use this guide to quickly locate specific UI components when reporting issues or requesting changes.

**Last Updated**: Based on current codebase structure

---

## Table of Contents

1. [Main Screen: SettlementScreen](#main-screen-settlementscreen)
2. [Widget: BuildingListWidget](#widget-buildinglistwidget)
3. [Widget: MaterialListWidget](#widget-materiallistwidget)
4. [Widget: VillagerListWidget](#widget-villagerlistwidget)
5. [Widget: StructureListWidget](#widget-structurelistwidget)
6. [Widget: BuildingOutputWidget](#widget-buildingoutputwidget)
7. [Widget: BuildingSelectionWidget](#widget-buildingselectionwidget)
8. [Widget: GolemListWidget](#widget-golemlistwidget)

---

## Main Screen: SettlementScreen

**File**: `src/client/java/com/secretasain/settlements/ui/SettlementScreen.java`  
**Total Lines**: 3372  
**Class Declaration**: Line 26

### Class Overview
Main UI screen for settlement management. Opens when a player right-clicks on a lectern block.

### Key Fields (Widget Declarations)

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `overviewTabButton` | `ButtonWidget` | 39 | Tab button for Overview tab |
| `buildingsTabButton` | `ButtonWidget` | 40 | Tab button for Buildings tab |
| `villagersTabButton` | `ButtonWidget` | 41 | Tab button for Villagers tab |
| `settingsTabButton` | `ButtonWidget` | 42 | Tab button for Settings tab |
| `villagerListWidget` | `VillagerListWidget` | 45 | Widget displaying list of villagers |
| `structureListWidget` | `StructureListWidget` | 48 | Widget for selecting structures to build (left sidebar in Buildings tab) |
| `buildStructureButton` | `ButtonWidget` | 49 | Button to activate build mode with selected structure |
| `buildingListWidget` | `BuildingListWidget` | 52 | Widget displaying list of existing buildings |
| `buildingSelectionWidget` | `BuildingSelectionWidget` | 55 | Widget for selecting buildings (used in work assignment dialog) |
| `cancelBuildingButton` | `ButtonWidget` | 59 | Button to cancel/remove a building |
| `removeBuildingButton` | `ButtonWidget` | 60 | Button to remove a completed building |
| `startBuildingButton` | `ButtonWidget` | 61 | Button to start building construction |
| `checkMaterialsButton` | `ButtonWidget` | 62 | Button to check materials for selected building |
| `unloadInventoryButton` | `ButtonWidget` | 63 | Button to unload player inventory into settlement storage |
| `refreshVillagersButton` | `ButtonWidget` | 64 | Button to refresh villager list |
| `materialListWidget` | `MaterialListWidget` | 67 | Widget displaying required/available materials |
| `buildingOutputWidget` | `BuildingOutputWidget` | 71 | Widget displaying building outputs (Villagers tab) |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | 83-86 | Creates new SettlementScreen instance |
| `init()` | 88-315 | Initializes screen, creates all widgets and buttons |
| `render()` | ~546-925 | Main render method, draws screen and widgets |
| `mouseClicked()` | ~547-615 | Handles mouse click events |
| `switchTab()` | ~1230-1385 | Switches between tabs (Overview, Buildings, Villagers, Settings) |
| `createStructureListWidget()` | ~1548-1619 | Creates/updates structure list widget (left sidebar) |
| `createBuildingListWidget()` | ~1737-1857 | Creates/updates building list widget |
| `positionBuildingActionButtons()` | ~1862-1926 | Positions cancel/remove/start buttons |
| `updateBuildingActionButtons()` | ~1928-1976 | Updates button visibility/state based on selected building |
| `createMaterialListWidget()` | ~2826-2939 | Creates/updates material list widget |
| `createBuildingOutputWidget()` | ~2941-3143 | Creates/updates building output widget |
| `refreshCurrentTabWidgets()` | ~3145-3210 | Refreshes all widgets in current tab |
| `closeBuildingWidgets()` | ~3212-3277 | Closes/removes building-related widgets |
| `removeAllBuildingOutputWidgets()` | ~3279-3343 | Removes all building output widget instances |
| `closeVillagerWidgets()` | ~3345-3400 | Closes/removes villager-related widgets |
| `onBuildStructureClicked()` | ~496-544 | Handler for "Build Structure" button click |
| `updateSettlementData()` | ~356-415 | Updates settlement data and refreshes UI |
| `updateMaterials()` | ~417-477 | Updates material data and refreshes material widget |

### Tab Types

**Enum Declaration**: Line 3378

| Tab Type | Description |
|----------|-------------|
| `TabType.OVERVIEW` | Overview tab (default) |
| `TabType.BUILDINGS` | Buildings tab - structure selection, building list, materials |
| `TabType.VILLAGERS` | Villagers tab - villager list, building assignments, building outputs |
| `TabType.SETTINGS` | Settings tab (future use) |

---

## Widget: BuildingListWidget

**File**: `src/client/java/com/secretasain/settlements/ui/BuildingListWidget.java`  
**Total Lines**: 788  
**Class Declaration**: Line 19

### Class Overview
Widget for displaying a scrollable list of buildings. Supports both individual building entries and grouped entries (accordion for similar buildings).

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `buildings` | `List<Building>` | 20 | List of buildings to display |
| `onDeleteCallback` | `Consumer<Building>` | 21 | Callback for delete button clicks |
| `onStartCallback` | `Consumer<Building>` | 22 | Callback for start button clicks |
| `onSelectionChangedCallback` | `Consumer<Building>` | 23 | Callback for selection changes |
| `availableMaterials` | `Map<String, Integer>` | 24 | Available materials for calculating availability |
| `expandedGroups` | `Map<String, Boolean>` | 26 | Tracks which building groups are expanded |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | 28-32 | Creates new BuildingListWidget instance |
| `setOnDeleteCallback()` | 38-40 | Sets callback for delete button |
| `setOnStartCallback()` | 46-48 | Sets callback for start button |
| `setOnSelectionChangedCallback()` | 54-56 | Sets callback for selection changes |
| `setAvailableMaterials()` | 72-76 | Sets available materials and refreshes entries |
| `updateEntries()` | 83-124 | Updates list entries, groups similar buildings |
| `toggleGroupExpansion()` | 130-135 | Toggles expansion state of a building group |
| `render()` | 138-300 | Main render method, draws list with scissor clipping |
| `mouseClicked()` | 191-299 | Handles mouse clicks, button clicks, selection |
| `getSelectedBuilding()` | 309-316 | Gets currently selected building |
| `getBuilding()` (BuildingEntry) | 718-720 | Gets building from entry |

### Inner Classes

#### BuildingGroupEntry
**Declaration**: Line 322  
**Purpose**: Accordion entry that groups multiple buildings of the same type  
**Key Methods**:
- `render()` - Line 357-392: Renders accordion header with expand/collapse indicator
- `getGroupName()` - Line 336-338: Returns group name
- `getFormattedGroupName()` - Line 343-354: Returns formatted display name

#### BuildingEntry
**Declaration**: Line 403  
**Purpose**: Single building entry in the list  
**Key Fields**:
- `building` - Line 404: The building data
- `availableMaterials` - Line 405: Available materials map
- `DELETE_BUTTON_SIZE` - Line 406: Size of delete button (12px)
- `START_BUTTON_SIZE` - Line 408: Size of start button (12px)
- `BUTTON_SPACING` - Line 410: Space between buttons (8px)

**Key Methods**:
- `render()` - Line 418-605: Renders building entry with icon, text, status, buttons
- `getBlockForStructure()` - Line 723-778: Determines block icon based on structure name
- `isDeleteButtonClicked()` - Line 669-674: Checks if delete button was clicked
- `isStartButtonClicked()` - Line 701-707: Checks if start button was clicked
- `getBuilding()` - Line 718-720: Returns the building data

**Rendering Elements** (within `render()` method):
- Structure icon - Line ~449-452: Block icon drawn FIRST (16x16, positioned at x+padding, y+padding)
- Structure name text - Line ~467-474: Text drawn AFTER icon with 4px spacing (iconSize + iconSpacing offset)
- Text shadow: true (dark background - per UI formatting rules)
- Status text - Line ~487-494: Building status (Reserved, In Progress, etc.)
- Delete button (X) - Line ~527-547: Red X button on right side (12x12px, DELETE_BUTTON_SIZE)
- Start button (checkmark) - Line ~550-572: Green checkmark button for RESERVED buildings (12x12px START_BUTTON_SIZE, left of delete button with 8px BUTTON_SPACING)
- **BUG FIX**: Buttons are rendered per-building entry, allowing individual cancel/start actions for multiple queued buildings

---

## Widget: MaterialListWidget

**File**: `src/client/java/com/secretasain/settlements/ui/MaterialListWidget.java`  
**Total Lines**: 275  
**Class Declaration**: Line 15

### Class Overview
Widget for displaying required materials for a building. Shows required vs available materials with color coding (green/yellow/red).

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `requiredMaterials` | `Map<Identifier, Integer>` | 16 | Required materials map |
| `availableMaterials` | `Map<String, Integer>` | 17 | Available materials map (String keys) |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | 19-25 | Creates new MaterialListWidget instance |
| `updateAvailableMaterials()` | 31-33 | Updates available materials and refreshes entries |
| `updateEntries()` | 38-62 | Updates list entries from material data |
| `render()` | 64-145 | Main render method, draws list with title and scissor clipping |

### Inner Classes

#### MaterialEntry
**Declaration**: Line 155  
**Purpose**: Single material entry in the list  
**Key Fields**:
- `materialId` - Line 156: Material identifier
- `required` - Line 157: Required count
- `available` - Line 158: Available count
- `customMessage` - Line 159: Custom message (e.g., "No materials required")

**Key Methods**:
- `render()` - Line 176-273: Renders material entry with icon, name, and count
- `getMaterialId()` - Line 243-245: Returns material identifier
- `getRequired()` - Line 247-249: Returns required count
- `getAvailable()` - Line 251-253: Returns available count

**Rendering Elements** (within `render()` method):
- Item icon - Line ~235-241: Item icon drawn FIRST (16x16, if item exists from Registry)
- Material text - Line ~255-271: Material name and count AFTER icon with 4px spacing (e.g., "Oak Planks: 10/20")
- Text truncation: Text is truncated if too long to prevent overlap
- Text shadow: true (dark background - per UI formatting rules)
- Color coding: Green (sufficient), Yellow (partial), Red (missing)

---

## Widget: VillagerListWidget

**File**: `src/client/java/com/secretasain/settlements/ui/VillagerListWidget.java`  
**Total Lines**: 769  
**Class Declaration**: Line 23

### Class Overview
Widget for displaying a scrollable list of villagers and golems. Shows villager information, employment status, work assignments, and action buttons.

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `villagers` | `List<VillagerData>` | 24 | List of villagers |
| `golems` | `List<GolemData>` | 25 | List of golems |
| `settlement` | `Settlement` | 26 | Settlement for building lookups |
| `onHireCallback` | `Consumer<VillagerData>` | 27 | Callback for hire button |
| `onFireCallback` | `Consumer<VillagerData>` | 28 | Callback for fire button |
| `onAssignWorkCallback` | `BiConsumer<VillagerData, UUID>` | 29 | Callback for work assignment |
| `onAssignGolemCallback` | `BiConsumer<GolemData, UUID>` | 30 | Callback for golem assignment |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | 32-38 | Creates new VillagerListWidget instance |
| `setOnHireCallback()` | 43-45 | Sets callback for hire button |
| `setOnFireCallback()` | 50-52 | Sets callback for fire button |
| `setOnAssignWorkCallback()` | 57-59 | Sets callback for work assignment |
| `setOnAssignGolemCallback()` | 64-66 | Sets callback for golem assignment |
| `updateEntries()` | 73-82 | Updates list entries from villager/golem data |
| `render()` | 86-145 | Main render method, draws list with scissor clipping |
| `mouseClicked()` | 148-235 | Handles mouse clicks, button clicks, selection |

### Inner Classes

#### VillagerEntry
**Declaration**: Line 243  
**Purpose**: Single villager entry in the list  
**Key Fields**:
- `villager` - Line 244: The villager data

**Key Methods**:
- `render()` - Line 251-580: Renders villager entry with name, profession, status, buttons
- `getVillager()` - Line 582-584: Returns the villager data

**Rendering Elements** (within `render()` method):
- Villager name - Line ~263-270: Name text at top (shadow: true - per UI formatting rules)
- Profession text - Line ~291-298: Profession text below name (shadow: true)
- Employment status - Line ~307-314: Employed/Unemployed status (shadow: true)
- Work assignment info - Line ~320-398: Assigned building info (if assigned, shadow: true)
- Hiring cost - Line ~399-417: Emerald icon and cost text (if unemployed, shadow: true)
- Hire/Fire button - Line ~419-517: Action buttons at bottom
- Assign/Unassign button - Line ~419-517: Work assignment buttons
- **BUG FIX**: All text rendering uses shadow=true for visibility on dark background

#### GolemEntry
**Declaration**: Line 587  
**Purpose**: Single golem entry in the list  
**Key Fields**:
- `golem` - Line 588: The golem data

**Key Methods**:
- `render()` - Line 595-741: Renders golem entry with name, type, assignment, buttons
- `getGolem()` - Line 743-745: Returns the golem data

**Rendering Elements** (within `render()` method):
- Golem name - Line ~606-613: Name text at top
- "Iron Golem" label - Line ~616-624: Type label
- Assignment status - Line ~628-687: Wall station assignment info
- Assign/Unassign button - Line ~695-741: Assignment buttons at bottom

---

## Widget: StructureListWidget

**File**: `src/client/java/com/secretasain/settlements/ui/StructureListWidget.java`  
**Total Lines**: 515  
**Class Declaration**: Line 19

### Class Overview
Widget for displaying a list of available structures to build. Compact sidebar that appears on the left when Buildings tab is active.

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `structureNames` | `List<String>` | 20 | List of structure names |
| `visible` | `boolean` | 21 | Visibility flag |
| `allowRendering` | `boolean` | 22 | Rendering permission flag |
| `forceDisable` | `boolean` | 23 | Force disable flag |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | 25-29 | Creates new StructureListWidget instance |
| `setVisible()` | 34-40 | Sets widget visibility |
| `setAllowRendering()` | 42-44 | Sets rendering permission |
| `setForceDisable()` | 46-48 | Sets force disable flag |
| `setStructures()` | 54-76 | Sets structure list and updates entries |
| `getSelectedStructure()` | 82-94 | Gets currently selected structure name |
| `updateEntries()` | 100-322 | Updates list entries, groups by category |
| `render()` | ~125-280 | Main render method, draws list with scissor clipping |

### Inner Classes

#### CategoryHeaderEntry
**Declaration**: Line 327  
**Purpose**: Category header entry (e.g., "Residential", "Commercial", "Defensive")  
**Key Methods**:
- `render()` - Line 336-351: Renders category header text

#### StructureEntry
**Declaration**: Line 366  
**Purpose**: Single structure entry in the list  
**Key Fields**:
- `name` - Line 367: Structure name
- `category` - Line 368: Structure category

**Key Methods**:
- `render()` - Line 389-443: Renders structure entry with icon and name
- `getName()` - Line 380-382: Returns structure name
- `getCategory()` - Line 384-386: Returns structure category
- `getBlockForStructure()` - Line 452-520: Determines block icon based on structure name

**Rendering Elements** (within `render()` method):
- Structure icon - Line ~399-403: Block icon drawn first (16x16, positioned at x+padding, y+padding)
- Structure name text - Line ~439-446: Text drawn after icon with 4px spacing (iconSize + iconSpacing offset)
- Text shadow: false (light text on dark background - per UI formatting rules)

---

## Widget: BuildingOutputWidget

**File**: `src/client/java/com/secretasain/settlements/ui/BuildingOutputWidget.java`  
**Total Lines**: 1012  
**Class Declaration**: Line 19

### Class Overview
Widget for displaying building outputs and statistics. Shows item outputs, crop statistics, and production rates. Used in Villagers tab when a building is selected.

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `tooltipRendering` | `static boolean` | 21 | Static lock to prevent multiple tooltips |
| `selectedBuilding` | `Building` | ~25 | Currently selected building |
| `outputs` | `List<OutputEntry>` | ~30 | List of output entries |
| `isFarm` | `boolean` | ~35 | Whether building is a farm |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | ~40-60 | Creates new BuildingOutputWidget instance |
| `updateWithServerData()` | ~100-200 | Updates widget with server data (crop stats, outputs) |
| `clearTooltipState()` | ~210-215 | Clears tooltip rendering state |
| `render()` | ~220-350 | Main render method, draws list with title and scissor clipping |

### Inner Classes

#### OutputEntry
**Declaration**: Line 698  
**Purpose**: Single output entry (item output or crop statistic)  
**Key Fields**:
- `item` - Line 699: Output item (for non-farm buildings)
- `weight` - Line 700: Drop weight
- `minCount` - Line 701: Minimum count
- `maxCount` - Line 702: Maximum count

**Key Methods**:
- `render()` - Line ~710-850: Renders output entry with icon, text, tooltip
- `renderTooltip()` - Line ~750-850: Renders detailed tooltip on hover

**Rendering Elements** (within `render()` method):
- Item icon - Line ~720-730: Item icon
- Output text - Line ~735-750: Item name, count, probability
- Tooltip - Line ~755-850: Detailed tooltip with calculations

---

## Widget: BuildingSelectionWidget

**File**: `src/client/java/com/secretasain/settlements/ui/BuildingSelectionWidget.java`  
**Total Lines**: 254  
**Class Declaration**: Line 20

### Class Overview
Widget for selecting a building (used in work assignment dialog). Shows list of available buildings for villager assignment.

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `availableBuildings` | `List<Building>` | 21 | List of available buildings |
| `villager` | `VillagerData` | 22 | Villager being assigned |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | ~30-40 | Creates new BuildingSelectionWidget instance |
| `updateEntries()` | ~50-80 | Updates list entries from available buildings |
| `render()` | ~90-150 | Main render method |

### Inner Classes

#### BuildingEntry
**Declaration**: Line 168  
**Purpose**: Single building entry in selection dialog  
**Key Methods**:
- `render()` - Line ~175-250: Renders building entry with name and status

---

## Widget: GolemListWidget

**File**: `src/client/java/com/secretasain/settlements/ui/GolemListWidget.java`  
**Total Lines**: 327  
**Class Declaration**: Line 18

### Class Overview
Widget for displaying a list of golems (separate from VillagerListWidget). Shows golem information and wall station assignments.

### Key Fields

| Field Name | Type | Line | Description |
|------------|------|------|-------------|
| `golems` | `List<GolemData>` | 19 | List of golems |
| `settlement` | `Settlement` | 20 | Settlement for building lookups |

### Key Methods

| Method Name | Line Range | Description |
|-------------|-----------|-------------|
| Constructor | ~25-35 | Creates new GolemListWidget instance |
| `updateEntries()` | ~40-60 | Updates list entries from golem data |
| `render()` | ~70-130 | Main render method |

### Inner Classes

#### GolemEntry
**Declaration**: Line 168  
**Purpose**: Single golem entry in the list  
**Key Methods**:
- `render()` - Line ~175-300: Renders golem entry with name, assignment, buttons

---

## Quick Reference: Common UI Element Locations

### Buttons (in SettlementScreen)

| Button Name | Field Line | Creation Method | Handler Method |
|-------------|-----------|-----------------|----------------|
| Overview Tab | 39 | `init()` ~100-110 | `switchTab()` ~1230 |
| Buildings Tab | 40 | `init()` ~100-110 | `switchTab()` ~1230 |
| Villagers Tab | 41 | `init()` ~100-110 | `switchTab()` ~1230 |
| Settings Tab | 42 | `init()` ~100-110 | `switchTab()` ~1230 |
| Build Structure | 49 | `createStructureListWidget()` ~1580 | `onBuildStructureClicked()` ~496 |
| Cancel Building | 59 | `init()` ~236-239 | `positionBuildingActionButtons()` ~1862 |
| Remove Building | 60 | `init()` ~236-239 | `positionBuildingActionButtons()` ~1862 |
| Start Building | 61 | `init()` ~236-239 | `positionBuildingActionButtons()` ~1862 |
| Check Materials | 62 | `init()` ~245-248 | `positionCheckMaterialsButton()` ~2775 |
| Unload Inventory | 63 | `init()` ~251-254 | `init()` ~251-254 |
| Refresh Villagers | 64 | `init()` ~165-170 | `switchTab()` ~1320 |

### Widget Creation Methods (in SettlementScreen)

| Widget | Creation Method | Line Range |
|--------|----------------|------------|
| StructureListWidget | `createStructureListWidget()` | ~1548-1619 |
| BuildingListWidget | `createBuildingListWidget()` | ~1737-1857 |
| MaterialListWidget | `createMaterialListWidget()` | ~2826-2939 |
| BuildingOutputWidget | `createBuildingOutputWidget()` | ~2941-3143 |
| VillagerListWidget | Created in `init()` and `switchTab()` | ~136-147, ~1299-1370 |
| BuildingSelectionWidget | Created in `showBuildingSelectionDialog()` | ~2400+ |

---

## Notes

- Line numbers are approximate and may shift as code is modified
- Always verify line numbers in your IDE before making changes
- Widget rendering typically occurs in the `render()` method of each widget class
- Entry rendering occurs in the `render()` method of each Entry inner class
- Button click handlers are typically in SettlementScreen or the respective widget class

## Recent Bug Fixes (Applied UI Formatting Rules)

### StructureListWidget.StructureEntry
- **Fixed**: Icon/text overlap - Icon now drawn first with 4px spacing before text
- **Location**: `StructureEntry.render()` Line ~389-446
- **Changes**: Applied UI formatting rules (4px minimum spacing, icon first)

### BuildingListWidget.BuildingEntry
- **Fixed**: Icon/text order - Icon now drawn FIRST, then text (was: text then icon)
- **Location**: `BuildingEntry.render()` Line ~418-605
- **Changes**: Added `getBlockForStructure()` method, icon rendering before text, 4px spacing, text shadows

### MaterialListWidget.MaterialEntry
- **Fixed**: Overlap issues - Added item icon rendering, proper spacing, text truncation
- **Location**: `MaterialEntry.render()` Line ~176-273
- **Changes**: Item icons from Registry, 4px spacing, text truncation to prevent overflow, text shadows

### VillagerListWidget.VillagerEntry & GolemEntry
- **Fixed**: Text not visible on dark background - Added text shadows to all text rendering
- **Location**: `VillagerEntry.render()` Line ~251-580, `GolemEntry.render()` Line ~595-741
- **Changes**: All `drawText()` calls now use `shadow=true` per UI formatting rules for dark backgrounds

### BuildingListWidget Button System
- **Verified**: Cancel/start buttons work per-building (not destroyed)
- **Location**: `BuildingEntry.render()` Line ~513-572, `isDeleteButtonClicked()` Line 669, `isStartButtonClicked()` Line 701
- **Status**: Buttons render correctly with individual callbacks per building entry

---

*This document should be updated when significant UI structure changes are made.*

