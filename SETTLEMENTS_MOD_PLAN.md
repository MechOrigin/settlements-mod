# Settlements Mod - Development Plan

## Overview
A Minecraft Fabric mod that enhances villages by adding town management features through an interactive lectern block. Players can manage villagers, hire workers, and build structures to expand and improve their settlements.

## Core Concept
- **Lectern Block Enhancement**: Right-clicking a lectern opens a town management UI (furnace-sized window)
- **Village Integration**: Tracks and manages villagers within a configurable radius
- **Building System**: Construct walls, fences, houses, and specialized structures
- **Villager Employment**: Hire villagers to work in their areas of expertise

---

## Phase 1: Foundation & Core Systems

### 1.1 Lectern Block Interaction
**Goal**: Add right-click functionality to lectern blocks to open the settlements UI

**Tasks**:
- [x] Create mixin for `LecternBlock` class
  - [x] Create `LecternBlockMixin.java` in `src/main/java/com/example/mixin/`
  - [x] Add `@Mixin(LecternBlock.class)` annotation
  - [x] Inject into `onUse` method to intercept right-click
  - [x] Check if player is sneaking (shift-click) to allow normal lectern behavior
  - [x] Return early if shift-clicking to preserve vanilla behavior
- [x] Implement settlement detection on lectern interaction
  - [x] Check if lectern position has an associated settlement
  - [x] Create new settlement if none exists (first-time setup)
  - [x] Retrieve existing settlement data if found
- [x] Create basic UI screen class
  - [x] Create `SettlementScreen.java` extending `Screen`
  - [x] Set screen dimensions to 176x166 pixels (furnace size)
  - [x] Implement `init()` method for screen initialization
  - [x] Implement `render()` method for drawing UI
  - [x] Add background texture rendering (basic rectangle for now)
- [x] Implement UI tab system
  - [x] Create tab button widgets (Overview, Buildings, Villagers, Settings)
  - [x] Add tab state management (track active tab)
  - [x] Implement tab switching logic
  - [ ] Add visual feedback for active/inactive tabs (basic implementation done, styling TODO)
- [x] Create basic settlement data structure
  - [x] Create `Settlement.java` class with fields: UUID id, BlockPos lecternPos, int radius, String name
  - [x] Add getter/setter methods for all fields
  - [x] Implement `equals()` and `hashCode()` methods
  - [x] Add constructor for creating new settlements
- [x] Create SettlementManager class
  - [x] Implement singleton pattern per world
  - [x] Add methods for creating and retrieving settlements
  - [x] Add lectern-to-settlement mapping
- [x] Implement network packet system
  - [x] Create OpenSettlementScreenPacket for client communication
  - [x] Create ClientNetworkHandler for client-side packet handling
  - [x] Register network handlers

**Technical Notes**:
- Use Fabric's mixin system: `@Mixin(LecternBlock.class)` with `@Inject` or `@Redirect`
- Store settlement data in `SettlementManager` singleton (world-level storage)
- Use `Screen` class from Minecraft for UI implementation
- Consider using `PersistentState` for world save data persistence
- UI should open on client-side, data operations on server-side

### 1.2 Settlement Data Management
**Goal**: Create the core data structures and persistence system

**Tasks**:
- [x] Design and implement `Settlement` class
  - [x] Create `Settlement.java` with fields: UUID id, BlockPos lecternPos, int radius, String name
  - [x] Add `List<VillagerData> villagers` field (initialize as empty ArrayList)
  - [x] Add `List<Building> buildings` field (initialize as empty ArrayList)
  - [x] Add `Map<String, Integer> materials` field for material storage (using String for ResourceLocation key)
  - [x] Implement `toNbt()` method for serialization
  - [x] Implement `fromNbt(NbtCompound)` static factory method for deserialization
  - [x] Add validation methods (check if position is valid, radius is positive)
- [x] Create `SettlementManager` singleton
  - [x] Create `SettlementManager.java` class
  - [x] Implement `getInstance(ServerWorld)` method to get world-specific manager
  - [x] Add `Map<UUID, Settlement> settlements` to store all settlements
  - [x] Add `Map<BlockPos, UUID> lecternToSettlement` for quick lookup
  - [x] Implement `createSettlement(BlockPos, String, int)` method
  - [x] Implement `getSettlement(UUID)` method
  - [x] Implement `getSettlementByLectern(BlockPos)` method
  - [x] Implement `removeSettlement(UUID)` method
  - [x] Add persistence integration (load/save data automatically)
- [x] Implement settlement data serialization
  - [x] Create `SettlementData.java` extending `PersistentState`
  - [x] Implement `readNbt(NbtCompound)` method (via `fromNbt` static method)
  - [x] Implement `writeNbt(NbtCompound)` method
  - [x] Store all settlements in NBT format
  - [x] Handle version migration if data format changes (version field added)
- [x] Add world save/load hooks
  - [x] Register `SettlementData` with `ServerWorld.getPersistentStateManager()`
  - [x] Use key `"settlements"` for persistence
  - [x] Implement `getOrCreate()` method for lazy initialization
  - [x] Hook into world save events to persist data (automatic via PersistentState)
  - [x] Load data on world load/server start (lazy loading on first access)
- [x] Create settlement detection system
  - [x] Implement `findSettlementAt(BlockPos)` method in SettlementManager
  - [x] Check if BlockPos is within any settlement's radius
  - [x] Return closest settlement if multiple overlap (returns first found - can be enhanced later)
  - [x] Handle edge cases (no settlements, invalid positions)
  - [x] Add method to check if position is within settlement bounds (`isWithinBounds`)

**Data Structure**:
```java
Settlement {
    UUID id
    BlockPos lecternPos
    int radius
    String name
    List<VillagerData> villagers
    List<Building> buildings
    Map<ResourceLocation, Integer> materials
}
```

### 1.3 Villager Tracking System
**Goal**: Track and manage villagers within settlement radius

**Tasks**:
- [x] Create `VillagerData` class
  - [x] Create `VillagerData.java` with fields: UUID entityId, BlockPos lastKnownPos, String profession, boolean isEmployed
  - [x] Add `String name` field (from villager's custom name or generated)
  - [x] Add `long lastSeen` timestamp field
  - [x] Implement `toNbt()` and `fromNbt()` methods
  - [x] Add `equals()` and `hashCode()` based on entityId
- [x] Implement radius-based villager detection
  - [x] Create `VillagerTracker.java` class
  - [x] Implement `scanForVillagers(Settlement, ServerWorld)` method
  - [x] Use `world.getEntitiesByType(EntityType.VILLAGER, ...)` with AABB bounding box
  - [x] Filter villagers by distance from settlement center (lectern position)
  - [x] Calculate distance using `BlockPos.getSquaredDistance()`
  - [x] Return list of villagers within radius
- [x] Add periodic scanning system
  - [x] Create server tick event handler (`VillagerScanningSystem`)
  - [x] Implement scanning every 5-10 seconds (100 ticks)
  - [x] Use tick counter to avoid scanning every tick
  - [x] Scan all settlements in rotation (one per 10 ticks to spread load)
  - [x] Update settlement's villager list with scan results
- [x] Implement villager data caching
  - [x] Store villager data in Settlement's villagers list
  - [x] Update lastSeen timestamp on each scan
  - [x] Remove villagers not seen for extended period (60 seconds)
  - [x] Handle villager position updates
- [x] Handle villager death/despawn events
  - [x] Register `ServerLivingEntityEvents.AFTER_DEATH` event handler
  - [x] Register `ServerEntityEvents.ENTITY_UNLOAD` event handler
  - [x] Check if dead/removed entity is a villager
  - [x] Find which settlement(s) contain this villager
  - [x] Remove villager from settlement's villager list
  - [x] Handle despawn events similarly
- [x] Display villager list in UI
  - [x] Create `VillagerListWidget.java` extending `AlwaysSelectedEntryListWidget`
  - [x] Display villager name, profession, employment status
  - [x] Add scrollable list if many villagers
  - [x] Update list when villager data changes (via updateEntries method)

**Technical Notes**:
- Use `World.getEntitiesByType(EntityType.VILLAGER, boundingBox, predicate)` for efficient scanning
- Cache villager data in Settlement object to avoid repeated lookups
- Update cache on villager spawn/despawn events using Fabric's entity events
- Consider using `Box` (AABB) for efficient spatial queries
- Performance: Scan in background, don't block main thread

---

## Phase 2: Building System - Wall Construction (Pilot Feature)

### 2.1 NBT Structure System
**Goal**: Create system for loading and managing NBT structure files

**Tasks**:
- [x] Design NBT structure file format
  - [x] Define structure file schema (palette, blocks, size, entities) - Uses Minecraft standard format
  - [x] Document required NBT tags and their purposes - Standard Minecraft structure format
  - [ ] Create example structure file for testing (TODO: Create actual .nbt file)
  - [x] Define metadata format (materials list, rotation points, build order) - Build order sorted by Y
- [x] Create `StructureLoader` utility class
  - [x] Create `StructureLoader.java` in `building/` package
  - [x] Implement `loadStructure(ResourceLocation)` static method
  - [x] Use `MinecraftServer.getResourceManager()` to access resources
  - [x] Read NBT file using `NbtIo.readCompressed()`
  - [x] Handle file not found exceptions gracefully
  - [x] Add structure caching to avoid reloading same file
- [x] Implement NBT file loading from resources
  - [x] Create resource location: `new Identifier("settlements", "structures/wall_basic.nbt")`
  - [x] Place structure files in `src/main/resources/data/settlements/structures/`
  - [x] Implement resource path resolution
  - [x] Add error handling for malformed NBT files
  - [x] Log errors for debugging
- [x] Create `StructureData` class
  - [x] Create `StructureData.java` class
  - [x] Add fields: `List<StructureBlock> blocks`, `Vec3i size`, `Map<BlockState, Integer> materialCount`
  - [x] Add `List<BlockPos> buildOrder` field (sorted by Y coordinate)
  - [x] Implement constructor from NBT compound
  - [x] Add `getBlockAt(BlockPos)` method
  - [x] Add `getMaterialRequirements()` method returning material map
  - [x] Add `getDimensions()` method returning size
- [x] Create `StructureBlock` inner class or separate class
  - [x] Store: BlockPos (relative), BlockState, NbtCompound (block entity data)
  - [x] Add methods for block state access
- [x] Add structure validation system
  - [x] Validate NBT structure has required tags (size, blocks, palette)
  - [x] Check structure dimensions are reasonable (max 64x64x64)
  - [x] Validate all block positions are within structure bounds
  - [x] Check palette contains valid block states
  - [x] Return validation result with error messages
  - [x] Log warnings for suspicious structures

**Structure File Format**:
- Store in `src/main/resources/data/settlements/structures/`
- Include: block palette, block positions, required materials list
- Metadata: dimensions, rotation points, build order

### 2.2 Build Mode System
**Goal**: Implement interactive build mode for placing structures

**Tasks**:
- [x] Create `BuildMode` enum for state management
  - [x] Create enum: `INACTIVE`, `SELECTION`, `PLACEMENT`, `ROTATION`, `ADJUSTMENT`, `CONFIRMATION`
  - [x] Add state transition methods (via setCurrentState)
  - [ ] Add validation for valid state transitions (TODO: Optional enhancement)
- [x] Create `BuildModeHandler` class
  - [x] Create `BuildModeHandler.java` in `building/` package
  - [x] Add fields: `BuildMode currentState`, `StructureData selectedStructure`, `BlockPos placementPos`, `int rotation`
  - [x] Implement `activateBuildMode(StructureData)` method
  - [x] Implement `deactivateBuildMode()` method
  - [x] Add `isActive()` method
  - [x] Store handler per-player (use `Map<UUID, BuildModeHandler>` via BuildModeManager)
- [x] Add build mode activation from UI
  - [x] Add "Build Structure" button in Buildings tab
  - [x] Create structure selection screen/dropdown (StructureListWidget)
  - [x] On selection, call `BuildModeHandler.activateBuildMode()` (via ActivateBuildModePacket)
  - [x] Close UI screen when entering build mode
  - [x] Show build mode overlay instead (BuildModeOverlay)
- [x] Implement structure preview rendering
  - [x] Create `BuildModeOverlay.java` extending `InGameHud` or custom renderer
  - [x] Implement ghost block rendering using `WorldRenderer` or custom renderer (BuildModePreviewRenderer)
  - [x] Render blocks as semi-transparent (alpha ~0.5)
  - [x] Use outline rendering for structure bounds
  - [x] Update preview position based on player crosshair or placement position
  - [x] Handle rotation in preview rendering
- [x] Create player movement/camera restrictions (optional)
  - [x] Consider if restrictions are needed (may not be necessary) - DECIDED: Not needed, normal movement works fine
  - [ ] If implementing: disable normal movement, allow only build mode controls - NOT IMPLEMENTED (not needed)
  - [ ] If implementing: lock camera or allow limited camera movement - NOT IMPLEMENTED (not needed)
  - [ ] Add option to toggle restrictions in settings - NOT IMPLEMENTED (not needed)
- [x] Add build mode exit/cancel functionality
  - [x] Register Escape key handler to exit build mode (BuildModeKeybinds)
  - [x] Call `BuildModeHandler.deactivateBuildMode()` on exit
  - [x] Clear preview rendering
  - [x] Return to normal gameplay state
  - [ ] Show confirmation dialog if structure is placed but not confirmed (TODO: Optional enhancement)

**Build Mode States**:
1. **Selection**: Choose structure type (wall, fence, etc.)
2. **Placement**: Position structure in world
3. **Rotation**: Rotate structure (0°, 90°, 180°, 270°)
4. **Adjustment**: Move structure (x, y, z offsets)
5. **Camera**: Adjust camera angle for better view
6. **Confirmation**: Confirm or cancel placement

### 2.3 Structure Placement & Preview
**Goal**: Allow players to position, rotate, and preview structures before placement

**Tasks**:
- [x] Implement ghost block rendering system
  - [x] Create `GhostBlockRenderer.java` utility class (GhostBlockRendererUtility)
  - [x] Use `WorldRenderer.drawBox()` or custom rendering (uses BlockRenderManager.renderBlockAsEntity)
  - [x] Render each block in structure as semi-transparent box
  - [x] Apply rotation transformation to block positions
  - [x] Use different colors for valid/invalid placement (green/red)
  - [x] Render at placement position + structure offset
- [x] Add structure positioning system
  - [ ] Implement raycast from player camera to find target block (TODO: Optional enhancement - currently uses manual movement)
  - [ ] Use `World.raycast()` or `BlockHitResult` from player (TODO: Optional enhancement)
  - [x] Calculate placement position (snap to block grid)
  - [x] Update placement position in `BuildModeHandler` (via ClientBuildModeManager)
  - [ ] Optionally add grid-snap mode (snap to 5-block increments) (TODO: Optional enhancement)
- [x] Create rotation controls
  - [x] Register keybind for rotation: `KeyBinding` for 'R' key (BuildModeKeybinds)
  - [ ] Register keybind for reverse rotation: `KeyBinding` for 'Shift+R' (TODO: Only clockwise rotation implemented)
  - [x] Implement `rotateStructure()` method (90° increments)
  - [x] Implement `rotateStructureReverse()` method (-90°) (rotateCounterClockwise exists but not bound)
  - [x] Update rotation field in `BuildModeHandler` (0, 90, 180, 270)
  - [x] Apply rotation to preview rendering
  - [x] Store rotation for final placement
- [x] Add movement controls
  - [x] Register keybinds: Arrow keys or WASD for horizontal movement (Arrow keys implemented)
  - [x] Register keybinds: Space/Shift for vertical movement (Space/X implemented)
  - [x] Implement `moveStructure(Direction, int)` method (via setPlacementPos)
  - [x] Update placement position by 1 block per keypress
  - [ ] Add fine movement (1 block) and coarse movement (5 blocks with Shift) (TODO: Only 1-block movement implemented)
  - [x] Update preview position immediately on movement
- [x] Implement camera angle adjustment (optional)
  - [x] Consider if needed (may use normal mouse camera) - DECIDED: Normal mouse camera works fine
  - [ ] If implementing: add keybind to toggle camera lock - NOT IMPLEMENTED (not needed)
  - [ ] If implementing: allow limited camera movement for better view - NOT IMPLEMENTED (not needed)
- [x] Add collision detection
  - [x] Implement `canPlaceStructure(BlockPos, StructureData, World)` method
  - [x] Check each block position in structure
  - [x] Verify block space is air or replaceable
  - [x] Check structure doesn't overlap with existing buildings (Enhanced validation in ConfirmPlacementPacket)
  - [x] Verify structure is within settlement bounds (Enhanced validation checks all blocks)
  - [x] Return validation result with error message (Enhanced validation returns error messages)
  - [x] Update preview color based on validation (green=valid, red=invalid)
- [x] Create placement confirmation UI overlay
  - [x] Render overlay text showing: "Press ENTER to confirm, ESC to cancel"
  - [x] Show structure name and material requirements
  - [x] Display current rotation angle
  - [x] Show placement position coordinates
  - [x] Add visual confirmation indicator (overlay shows all info)

**Controls**:
- `R` / `Shift+R`: Rotate structure
- `Arrow Keys` / `WASD`: Move structure horizontally
- `Space` / `Shift`: Move structure vertically
- `Mouse`: Adjust camera angle (optional)
- `Enter`: Confirm placement
- `Escape`: Cancel build mode

### 2.4 Building Reservation System
**Goal**: Reserve building locations and track construction progress

**Tasks**:
- [x] Create `Building` class
  - [x] Create `Building.java` in `settlement/` package
  - [x] Add fields: `UUID id`, `BlockPos position`, `ResourceLocation structureType`, `BuildingStatus status`
  - [x] Add `Map<ResourceLocation, Integer> requiredMaterials` field
  - [x] Add `Map<ResourceLocation, Integer> providedMaterials` field
  - [x] Add `float progress` field (0.0 to 1.0)
  - [x] Add `List<BlockPos> barrierPositions` field
  - [x] Implement `toNbt()` and `fromNbt()` methods
  - [x] Add `getProgressPercentage()` method
  - [x] Add `hasAllMaterials()` method
- [x] Create `BuildingStatus` enum
  - [x] Create enum: `RESERVED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
  - [x] Add status transition validation
- [x] Implement barrier block placement
  - [x] Create `placeBarriers(Building, World)` method
  - [x] Calculate structure bounding box
  - [x] Place barrier blocks at structure corners (basic implementation)
  - [x] Use `Blocks.BARRIER` for barrier blocks
  - [x] Store barrier positions in Building object
  - [ ] Handle barrier placement in unloaded chunks (queue for later) - TODO: Enhanced implementation
- [x] Add building status tracking
  - [x] Implement status update methods in Building class
  - [x] Add validation for status transitions (e.g., can't go from COMPLETED to IN_PROGRESS)
  - [ ] Update status when materials are provided (RESERVED → IN_PROGRESS) - TODO: Will be implemented in Phase 2.5 Material Management
  - [ ] Update status when construction completes (IN_PROGRESS → COMPLETED) - TODO: Will be implemented in Phase 2.6 Sequential Block Placement
  - [x] Persist status changes to settlement data
- [x] Create building list in lectern UI
  - [x] Add building list widget to Buildings tab
  - [x] Display building status with color coding
  - [x] Show building position and structure type
  - [x] Display material progress (X/Y blocks provided)
  - [x] Add "Cancel Building" button for RESERVED/IN_PROGRESS buildings
  - [x] Add "Remove" button for COMPLETED buildings
- [x] Implement building removal/cancellation
  - [x] Create `cancelBuilding(Building, World)` method
  - [x] Remove barrier blocks
  - [ ] Return materials to settlement storage (if applicable) - TODO: Will be implemented in Phase 2.5 Material Management
  - [x] Remove building from settlement's building list
  - [x] Update settlement data
  - [ ] Handle cancellation of in-progress buildings (stop block placement) - TODO: Will be implemented in Phase 2.6 Sequential Block Placement

**Building States**:
- `RESERVED`: Location marked, waiting for materials
- `IN_PROGRESS`: Materials being consumed, blocks being placed
- `COMPLETED`: Fully built
- `CANCELLED`: Player cancelled construction

### 2.5 Material Management System
**Goal**: Track required materials and consume them during construction

**Tasks**:
- [x] Create material requirement calculation
  - [x] Implement `calculateMaterials(StructureData)` method in `MaterialManager`
  - [x] Iterate through all blocks in structure
  - [x] Count each unique BlockState (or Item equivalent)
  - [x] Group by item type (e.g., all oak planks together)
  - [x] Return `Map<Identifier, Integer>` of required materials (using Identifier instead of Item)
  - [ ] Handle block states with NBT data (e.g., chests with items) - TODO: Enhanced implementation
- [x] Create `MaterialManager` class
  - [x] Create `MaterialManager.java` in `building/` package
  - [x] Add methods: `getRequiredMaterials(StructureData)`, `getAvailableMaterials(Settlement)`
  - [x] Add `canAfford(Settlement, Map<Identifier, Integer>)` method
  - [x] Add `consumeMaterials(Settlement, Map<Identifier, Integer>)` method
  - [x] Handle material conversion (blocks to items, items to blocks)
  - [x] Add `consumeMaterialsForBuilding(Building, Settlement)` method
  - [x] Add `returnMaterials(Building, Settlement)` method for cancellation
- [x] Implement material display in lectern UI
  - [x] Create material list widget in Buildings tab (MaterialListWidget)
  - [x] Display required vs. available materials side-by-side
  - [x] Use color coding: green (sufficient), yellow (partial), red (missing)
  - [x] Show item icons and counts
  - [ ] Add tooltips with item names (TODO: Optional enhancement)
  - [x] Update display when materials change
- [x] Add material input system
  - [x] Create `DepositMaterialsPacket` for depositing items from player inventory
  - [x] Allow players to deposit items into settlement storage
  - [x] Store materials in settlement's materials map
  - [ ] Create custom inventory for lectern (or use existing container) - TODO: Enhanced UI implementation
  - [ ] Implement `LecternBlockEntity` extension or custom block entity - TODO: Enhanced implementation
  - [ ] Add inventory slots for material input - TODO: Enhanced UI implementation
  - [ ] Create `SettlementContainer` extending `ScreenHandler` - TODO: Enhanced UI implementation
  - [ ] Auto-sort items into material storage - TODO: Enhanced implementation
  - [ ] Display current material counts in UI - TODO: Enhanced UI implementation
- [x] Create material consumption logic
  - [x] Implement `consumeMaterialsForBuilding(Building, Settlement)` method
  - [x] Check if all required materials are available
  - [x] Remove materials from settlement storage
  - [x] Update building's `providedMaterials` map
  - [ ] Handle partial consumption (if not all materials available) - TODO: Enhanced implementation
  - [x] Return success/failure result
- [x] Implement sequential block placement trigger
  - [x] Create `StartBuildingPacket` to trigger construction start
  - [x] Transition building status to IN_PROGRESS when materials are consumed
  - [ ] Initialize block placement queue - TODO: Will be implemented in Phase 2.6
- [ ] Add progress tracking
  - [ ] Update building progress as blocks are placed
  - [ ] Calculate progress: `placedBlocks / totalBlocks`
  - [ ] Store progress in Building object
  - [ ] Persist progress to settlement data
- [ ] Add visual feedback
  - [ ] Show progress bar in UI
  - [ ] Display "Building in progress..." message
  - [ ] Add particle effects at construction site
  - [ ] Play block placement sounds

**Material System**:
- Parse NBT structure to count block types
- Store materials in lectern's inventory (or custom storage)
- Display required vs. available materials
- Consume materials as blocks are placed
- Place blocks layer by layer (y=0 to y=max)

### Material Management Rules and Bug Prevention

**CRITICAL RULES**:

1. **Material Consumption Flow**:
   - When building starts: Materials are consumed from `settlement.materials` (settlement storage)
   - Materials are tracked in `building.providedMaterials` (internal inventory/buffer)
   - When building completes: `providedMaterials` is cleared (materials already consumed from storage)
   - When building cancels: `providedMaterials` is returned to chests/settlement storage

2. **Item Duplication Prevention**:
   - **NEVER add materials back to settlement storage if they were already deposited in chests**
   - In `returnMaterialsToChests()`: Only add remainder (materials that couldn't fit in chests) to settlement storage
   - **DO NOT** call `addMaterials(settlement, provided)` after depositing in chests - this causes duplication
   - Materials are consumed from settlement storage when building starts, so returning them should only happen on cancellation

3. **Building Completion**:
   - When building completes: Materials in `providedMaterials` are already consumed (don't return them)
   - Clear `providedMaterials` after creating completion book (book needs the data first)
   - Create written book in chest next to lectern with:
     - Page 1: Building name and location
     - Page 2: Materials used (space-separated list)
     - Page 3: Completion signature with settlement name
   - Remove building from UI automatically (filter out COMPLETED status in `BuildingListWidget.updateEntries()`)

4. **Material Storage**:
   - Settlement storage (`settlement.materials`): Virtual storage for material tracking
   - Chest storage: Physical storage adjacent to lectern
   - When returning materials: Try chests first, then settlement storage for remainder
   - When consuming materials: Remove from settlement storage, track in `providedMaterials`

5. **UI Updates**:
   - Building list should automatically filter out COMPLETED buildings
   - Client should refresh building list when settlement data changes
   - No need for explicit refresh packet - client will refresh on next UI open

### 2.6 Sequential Block Placement
**Goal**: Place structure blocks progressively from bottom to top

**Tasks**:
- [x] Create block placement queue system
  - [x] Create `BlockPlacementQueue.java` class
  - [x] Store queue of `QueuedBlock` objects (position, block state, block entity data)
  - [x] Implement `addBlock(BlockPos, BlockState, NbtCompound)` method
  - [x] Implement `getNextBlock()` method (returns and removes next block)
  - [x] Implement `isEmpty()` and `size()` methods
  - [x] Store queue per-building (in Building object or separate manager)
- [x] Create placement order algorithm
  - [x] Implement `sortBlocksByY(List<StructureBlock>)` method
  - [x] Sort blocks by Y coordinate (lowest first)
  - [x] For same Y, sort by X then Z (consistent ordering)
  - [x] Populate queue with sorted blocks
  - [x] Handle rotation in sorting (apply rotation before sorting)
- [x] Add placement timing/rate limiting
  - [x] Create `BlockPlacementScheduler.java` class
  - [x] Implement server tick handler for block placement
  - [x] Place 1-5 blocks per tick (configurable)
  - [x] Use tick counter to spread placement across multiple ticks
  - [x] Add delay between batches if needed (every N ticks)
  - [ ] Make rate configurable in mod config (TODO: Future enhancement)
- [x] Implement block placement validation
  - [x] Create `canPlaceBlock(BlockPos, BlockState, World)` method
  - [x] Check if target position is air or replaceable
  - [x] Verify block can be placed at position (not in unloaded chunk)
  - [ ] Check for entity collisions (TODO: Enhanced validation)
  - [x] Validate block state is valid for position
  - [x] Return validation result with error message
- [x] Implement actual block placement
  - [x] Create `placeBlock(BlockPos, BlockState, NbtCompound, World)` method
  - [x] Use `World.setBlockState(BlockPos, BlockState, int flags)`
  - [x] Set block entity data if NbtCompound is provided
  - [x] Use appropriate flags (NOTIFY_NEIGHBORS, BLOCK_UPDATE)
  - [x] Handle block entity creation (chests, furnaces, etc.)
  - [x] Update building progress after each placement
- [x] Add visual feedback
  - [x] Spawn block break particles at placement location
  - [x] Play block placement sound (`SoundCategory.BLOCKS`)
  - [x] Use appropriate sound for block type
  - [ ] Add progress indicator particles (optional) - TODO: Future enhancement
- [x] Handle block placement failures
  - [x] Catch exceptions during block placement
  - [x] Log errors for debugging
  - [x] Skip failed blocks and continue with queue
  - [ ] Mark building as having errors if too many failures (TODO: Enhanced error handling)
  - [ ] Allow manual retry or cancellation (TODO: Enhanced UI)
- [x] Complete building construction
  - [x] Detect when all blocks are placed (queue is empty)
  - [x] Update building status to COMPLETED
  - [x] Remove barrier blocks
  - [x] Trigger completion event/callback
  - [x] Update settlement data
  - [x] Show completion message to player

**Placement Logic**:
1. Sort blocks by Y coordinate (lowest first)
2. Place blocks in batches (e.g., 1-5 blocks per tick)
3. Check for obstacles before placing
4. Update building progress
5. Remove barrier blocks when complete

---

## Side Quest: General-Purpose Ghost Block Rendering System

### Goal
Create a reusable, general-purpose system for rendering any block as a ghost block (semi-transparent preview). This system can be used for structure previews, block placement previews, or any other visualization needs throughout the mod.

### Tasks
- [x] Create `GhostBlockRendererUtility` class
  - [x] Create `GhostBlockRendererUtility.java` in `block/` package
  - [x] Implement `renderGhostBlock(BlockPos, BlockState, MatrixStack, VertexConsumerProvider, float, int)` method
  - [x] Add overloaded methods with default parameters (alpha, light level)
  - [x] Implement `renderGhostBlockTinted()` method for color tinting (valid/invalid states)
  - [x] Use `BlockRenderManager.renderBlockAsEntity()` for actual block model rendering
  - [x] Apply transparency via `RenderSystem.setShaderColor()` with alpha blending
  - [x] Handle blend modes and depth testing correctly
  - [x] Add proper error handling and logging
- [x] Create `GhostBlockManager` class
  - [x] Create `GhostBlockManager.java` in `block/` package
  - [x] Implement singleton pattern for global access
  - [x] Create `GhostBlockEntry` inner class to store block data (position, state, alpha, tint colors)
  - [x] Use thread-safe `ConcurrentHashMap` for storing ghost blocks
  - [x] Implement `addGhostBlock()` methods with various parameter combinations
  - [x] Add `addGhostBlockTinted()` method for color-tinted blocks
  - [x] Implement `removeGhostBlock(BlockPos)` method
  - [x] Implement `clearAll()` method to remove all ghost blocks
  - [x] Implement `removeGhostBlocks(Collection<BlockPos>)` for batch removal
  - [x] Add query methods: `hasGhostBlock()`, `getGhostBlock()`, `getAllPositions()`, `size()`, `isEmpty()`
  - [x] Implement `renderAll()` method that renders all tracked ghost blocks
  - [x] Add distance-based culling (skip blocks >64 blocks away)
- [x] Create `GhostBlockRenderHandler` class
  - [x] Create `GhostBlockRenderHandler.java` in `block/` package
  - [x] Implement `register()` method to hook into Fabric's rendering pipeline
  - [x] Register with `WorldRenderEvents.AFTER_TRANSLUCENT` for proper rendering order
  - [x] Implement `renderGhostBlocks()` method that calls manager's renderAll()
  - [x] Handle vertex consumer provider and buffer drawing
- [x] Integrate with client initialization
  - [x] Register `GhostBlockRenderHandler` in `SettlementsModClient.onInitializeClient()`
  - [x] Ensure rendering happens after translucent blocks for proper layering
- [ ] Create usage examples and documentation
  - [ ] Document API usage in class JavaDoc
  - [ ] Add example code comments showing common use cases
  - [ ] Document performance considerations (distance culling, batch operations)

**Technical Notes**:
- **Rendering Approach**: Uses `BlockRenderManager.renderBlockAsEntity()` to render actual block models with transparency
- **Transparency**: Applied via `RenderSystem.setShaderColor()` with alpha blending (default 0.5 = 50% transparent)
- **Performance**: Distance-based culling prevents rendering blocks >64 blocks away
- **Thread Safety**: Uses `ConcurrentHashMap` for thread-safe access from multiple threads
- **Integration**: Hooks into Fabric's `WorldRenderEvents.AFTER_TRANSLUCENT` for proper rendering order
- **Color Tinting**: Supports RGB tinting for visual feedback (e.g., green for valid, red for invalid placement)

**Usage Example**:
```java
// Get the manager instance
GhostBlockManager manager = GhostBlockManager.getInstance();

// Add a simple ghost block
manager.addGhostBlock(new BlockPos(100, 64, 200), Blocks.STONE.getDefaultState());

// Add a ghost block with custom alpha
manager.addGhostBlock(new BlockPos(101, 64, 200), Blocks.OAK_PLANKS.getDefaultState(), 0.3f);

// Add a tinted ghost block (red tint for invalid placement)
manager.addGhostBlockTinted(new BlockPos(102, 64, 200), Blocks.GLASS.getDefaultState(), 
                           0.5f, 1.0f, 0.7f, 0.7f); // Red tint

// Remove a ghost block
manager.removeGhostBlock(new BlockPos(100, 64, 200));

// Clear all ghost blocks
manager.clearAll();
```

**Code Statistics**:
- **GhostBlockRendererUtility**: ~200 lines (utility methods for rendering)
- **GhostBlockManager**: ~250 lines (manager with entry class and rendering logic)
- **GhostBlockRenderHandler**: ~40 lines (Fabric integration)
- **Total**: ~490 lines of code

**Complexity Assessment**:
- **Difficulty**: Moderate (requires understanding of Minecraft rendering pipeline)
- **Time Investment**: 1-2 days for complete implementation
- **Dependencies**: Fabric API (WorldRenderEvents), Minecraft rendering system

---

## Phase 3: UI Implementation

### 3.1 Main Settlement UI
**Goal**: Create the main lectern interaction screen

**Tasks**:
- [x] Design UI layout
  - [x] Create UI mockup/sketch (implemented in SettlementScreen)
  - [x] Define tab button positions (top of screen)
  - [x] Define content area dimensions (400x280 screen)
  - [x] Plan widget placement (text, buttons, lists)
  - [x] Design color scheme and styling (basic implementation)
- [x] Create `SettlementScreen` class structure
  - [x] Create `SettlementScreen.java` extending `Screen`
  - [x] Implement constructor taking `Settlement` and `BlockPos` (takes Settlement)
  - [x] Override `init()` method for widget initialization
  - [x] Override `render(DrawContext, int, int, float)` method
  - [x] Override `shouldPause()` method (return false for game pause)
  - [x] Add background texture rendering (basic rectangle background)
- [x] Implement tab switching system
  - [x] Create `TabButton` widget class extending `ButtonWidget` (using ButtonWidget directly)
  - [x] Create buttons for: Overview, Buildings, Villagers, Settings
  - [x] Add `activeTab` field to track current tab
  - [x] Implement `switchTab(TabType)` method
  - [x] Update button appearance based on active state (updateTabButtons method)
  - [x] Show/hide tab content based on active tab
- [x] Add settlement information display
  - [x] Create information panel widget (basic text rendering)
  - [x] Display settlement name (shown in welcome message)
  - [x] Display villager count: "Villagers: X" (TODO: Add to Overview tab)
  - [x] Display building count: "Buildings: X" (TODO: Add to Overview tab)
  - [x] Display settlement radius: "Radius: X blocks" (TODO: Add to Overview tab)
  - [x] Update information when data changes (TODO: Refresh on tab switch)
- [ ] Create UI textures/assets
  - [ ] Create GUI texture file: `textures/gui/settlement_screen.png` (TODO: Optional enhancement)
  - [ ] Design background texture (176x166 pixels) (TODO: Optional enhancement)
  - [ ] Create tab button textures (normal, hover, active states) (TODO: Optional enhancement)
  - [ ] Create button textures for actions (TODO: Optional enhancement)
  - [ ] Add texture to `assets/settlements/textures/gui/` (TODO: Optional enhancement)
  - [ ] Register textures in resource loading (TODO: Optional enhancement)
- [ ] Implement tooltips and help text
  - [ ] Add tooltip system using `Tooltip` class (TODO: Optional enhancement)
  - [ ] Create tooltips for buttons explaining their function (TODO: Optional enhancement)
  - [x] Add help text in Overview tab (description text shown)
  - [ ] Implement hover detection for tooltip display (TODO: Optional enhancement)
  - [ ] Style tooltips with background and text (TODO: Optional enhancement)

**UI Layout**:
```
┌─────────────────────────────────┐
│  [Overview] [Buildings] [Villagers] │
│                                   │
│  Settlement: [Name]               │
│  Villagers: 5                     │
│  Buildings: 2                     │
│                                   │
│  [Build New Structure]            │
│  [Manage Villagers]               │
└─────────────────────────────────┘
```

### 3.2 Buildings Tab
**Goal**: Display and manage settlement buildings

**Tasks**:
- [x] Create building list widget
  - [x] Create `BuildingListWidget.java` extending `AbstractListWidget` (AlwaysSelectedEntryListWidget)
  - [x] Implement list entry class `BuildingListEntry` (BuildingEntry)
  - [x] Add scrollable list functionality
  - [x] Display buildings in vertical list
  - [x] Handle empty list state (show "No buildings" message)
- [x] Display building information in list entries
  - [x] Show building structure type name
  - [x] Display building position coordinates
  - [x] Show building status with color coding
  - [x] Display material progress (X/Y blocks)
  - [ ] Add building icon/thumbnail (optional - TODO: Future enhancement)
- [x] Implement building status display
  - [x] Color code: RESERVED (yellow), IN_PROGRESS (blue), COMPLETED (green), CANCELLED (red)
  - [x] Show status text label
  - [ ] Add status icon indicator (TODO: Optional enhancement)
- [x] Show material requirements
  - [x] Create expandable material list per building (MaterialListWidget)
  - [x] Display required materials with item icons
  - [x] Show available vs. required counts
  - [x] Use color coding for material sufficiency
  - [x] Add "View Materials" button to expand/collapse (Check Materials button)
- [x] Add "Start Building" button
  - [x] Create button widget for reserved buildings
  - [x] Show button for RESERVED buildings
  - [x] On click: consume materials and start construction
  - [x] Update building status to IN_PROGRESS
  - [ ] Check if all materials are available (client-side check) - TODO: Requires material sync to client
  - [ ] Enable/disable button based on material availability - TODO: Requires material sync to client
  - [ ] Show confirmation dialog if materials insufficient - TODO: Enhanced UI
- [x] Implement building cancellation
  - [x] Add "Cancel" button for RESERVED/IN_PROGRESS buildings
  - [ ] Show confirmation dialog before cancellation (TODO: Optional enhancement)
  - [x] Call `cancelBuilding()` method
  - [x] Remove building from list (filtered out automatically)
  - [x] Return materials if applicable
- [ ] Add building progress bars
  - [ ] Create progress bar widget (TODO: Visual progress bar)
  - [x] Display progress for IN_PROGRESS buildings (shown as text percentage)
  - [x] Show percentage: "45% Complete" (displayed in building entry)
  - [ ] Visual progress bar (filled rectangle) (TODO: Optional enhancement)
  - [x] Update progress in real-time (updates when building list refreshes)
- [x] Add "Build New Structure" button
  - [x] Create button in Buildings tab header (Build Structure button)
  - [x] Open structure selection screen (StructureListWidget)
  - [x] On selection, enter build mode

### 3.3 Villagers Tab
**Goal**: Display and manage settlement villagers

**Tasks**:
- [x] Create villager list widget
  - [x] Create `VillagerListWidget.java` extending `AbstractListWidget` (AlwaysSelectedEntryListWidget)
  - [x] Implement list entry class `VillagerListEntry` (VillagerEntry)
  - [x] Add scrollable list functionality
  - [x] Display villagers in vertical list
  - [x] Handle empty list state (show "No villagers found" message)
- [x] Display villager information
  - [x] Show villager name (or "Unnamed Villager")
  - [x] Display profession icon and name
  - [x] Show employment status (Employed/Unemployed)
  - [ ] Display last known position (optional, for debugging) (TODO: Optional enhancement)
  - [ ] Add villager entity render (small 3D preview, optional) (TODO: Optional enhancement)
- [x] Add villager hire/fire functionality
  - [x] Create "Hire" button for unemployed villagers
  - [x] Create "Fire" button for employed villagers
  - [x] Implement `hireVillager(VillagerData)` method (via HireFireVillagerPacket)
  - [x] Implement `fireVillager(VillagerData)` method (via HireFireVillagerPacket)
  - [x] Update villager employment status
  - [ ] Show confirmation dialog for fire action (TODO: Optional enhancement)
  - [x] Update UI immediately after hire/fire
- [x] Show villager work assignments
  - [x] Display assigned building/workstation for employed villagers
  - [x] Show "No assignment" for employed but unassigned villagers
  - [x] Add "Assign Work" button
  - [x] Display work assignment status
  - [x] Create building selection dialog for work assignment
  - [x] Allow players to choose which building to assign villagers to
- [ ] Implement villager search/filter
  - [ ] Add search text field at top of list (TODO: Optional enhancement)
  - [ ] Filter by name (case-insensitive) (TODO: Optional enhancement)
  - [ ] Add filter dropdown: All, Employed, Unemployed, by Profession (TODO: Optional enhancement)
  - [ ] Update list in real-time as user types (TODO: Optional enhancement)
  - [ ] Show "No results" message when filter returns empty (TODO: Optional enhancement)
- [x] Add villager refresh functionality
  - [x] Add "Refresh List" button
  - [x] Trigger villager scan immediately (refreshes UI list)
  - [x] Update list with latest villager data
  - [ ] Show loading indicator during scan (TODO: Optional enhancement)
- [x] Create building output display widget (QoL Enhancement)
  - [x] Create `BuildingOutputWidget.java` similar to `MaterialListWidget`
  - [x] Position widget on right side of Villagers tab (same size/location as materials widget in Buildings tab)
  - [x] Display when building is selected in `BuildingSelectionWidget`
  - [x] Load building outputs from `building_outputs.json` based on building type
  - [x] Display item outputs with item icons and counts
  - [x] Calculate and display items per minute based on task execution interval
  - [x] Load `BuildingOutputConfig` on client side for UI access
  - [x] **FIX: Proper Server-Side Data Loading and Enhanced Statistics**
    - [x] **Problem**: Current implementation tries to load NBT structure files on client, which fails per cursor rules (client ResourceManager can't access data files)
    - [x] **Solution**: Always load all data on server and send to client via packets
    - [x] **Implementation Steps**:
      - [x] Enhance `BuildingOutputDataPacket` to send comprehensive crop statistics for farm buildings
        - [x] Create `CropStatistics` data class to hold crop data (type, count, age distribution, maturity status)
        - [x] Server-side: Scan actual world for crops in farm building area (not just NBT structure)
        - [x] Calculate crop ages using block state properties (AGE_7, AGE_3, etc.)
        - [x] Determine crop maturity status (mature/immature) and count each
        - [x] Calculate growth time remaining for immature crops (based on crop type and current age)
        - [x] Estimate harvest time (when next crops will be ready)
        - [x] Calculate expected items per harvest cycle (mature crops × average drops per crop)
        - [x] Send all crop statistics to client via packet
      - [x] Remove client-side NBT loading from `BuildingOutputWidget` (follow cursor rules)
      - [x] Update `BuildingOutputWidget.updateWithServerData()` to accept crop statistics
      - [x] Display crop statistics in widget with detailed breakdown:
        - [x] Total farmland plots count
        - [x] Crop type distribution (wheat: 10, carrots: 5, etc.)
        - [x] Maturity status: "X mature, Y immature"
        - [x] Age distribution: "Age 0-2: X crops, Age 3-5: Y crops, Age 6-7 (mature): Z crops"
        - [x] Estimated time until next harvest (for immature crops)
        - [x] Expected items per harvest cycle (when all mature)
        - [x] Items per minute calculation (based on task interval and harvest cycle)
      - [x] For non-farm buildings: Enhance output display with detailed statistics
        - [x] Show weighted probability percentage for each output
        - [x] Calculate and display expected average items per task execution
        - [x] Display items per minute with calculation breakdown
        - [x] Show drop rate (weight / total weight) as percentage
      - [x] Add comprehensive tooltip system:
        - [x] Tooltip rendering implemented in `OutputEntry.renderTooltip()`
        - [x] For each output item, show tooltip on hover with:
          - [x] Item name and full identifier (e.g., "minecraft:wheat")
          - [x] Drop weight and probability percentage
          - [x] Min/max count per drop
          - [x] Expected average count per drop: `(min + max) / 2`
          - [x] Expected items per task: `avg_count × probability`
          - [x] Items per minute: `items_per_task × tasks_per_minute`
          - [x] Calculation breakdown: "Based on 200-tick (10s) task interval = 6 tasks/min"
        - [x] For farm crops, show additional tooltip information:
          - [x] Crop type and identifier
          - [x] Current age and max age (e.g., "Age 3/7")
          - [x] Maturity status: "Mature" or "Immature (X ticks remaining)"
          - [x] Growth time: "~25 minutes average" or "X minutes remaining"
          - [x] Average drops per harvest: "1-3 items (avg 2)"
          - [x] Expected harvest time: "Next harvest in ~X minutes" or "Ready now"
      - [x] Implement tooltip rendering in `OutputEntry.render()`:
        - [x] Detect mouse hover over entry
        - [x] Render tooltip using `DrawContext.drawTooltip()` with proper formatting
        - [x] Position tooltip to avoid screen edges (with static lock to prevent overlap)
      - [x] Add visual indicators for crop maturity:
        - [x] Color-code entries: Green for mature crops, Yellow for near-mature, Gray for immature
        - [x] Show progress bar for crop growth progress
        - [x] Display time remaining until maturity (e.g., "5 min remaining")
      - [x] Fix widget cleanup and overlap issues:
        - [x] Implement aggressive widget removal when switching buildings
        - [x] Add cleanup before auto-selection
        - [x] Add cleanup in render method to catch stale widgets
        - [x] Implement static tooltip lock to prevent tooltip overlap
        - [x] Implement explicit widget tracking using HashSet to ensure all widgets are found and removed
        - [x] Create centralized `removeAllBuildingOutputWidgets()` method for consistent cleanup
        - [x] Replace all cleanup code with calls to centralized method
        - [x] Add widgets to tracking set when created
        - [x] Update render method to use tracking set for efficient widget lookup
      - [ ] Performance optimizations:
        - [ ] Cache crop statistics on server (update every 10-20 seconds, not every packet request)
        - [ ] Only scan world when building is selected (lazy loading)
        - [ ] Limit crop scanning to building's structure bounds (use StructureData to get area)
        - [ ] Batch crop age calculations efficiently
    - [ ] **Technical Notes**:
      - **Data Loading**: Always use server-side loading per cursor rules (client ResourceManager limitation)
      - **Crop Scanning**: Use `FarmCropHarvester` logic as reference, but scan without harvesting
      - **Crop Age Detection**: Use block state properties (Properties.AGE_7 for wheat/carrots/potatoes, Properties.AGE_3 for beetroot)
      - **Growth Time Calculation**: Most crops mature in ~25 minutes (30000 ticks) under optimal conditions
      - **Task Interval**: 200 ticks = 10 seconds = 6 tasks per minute
      - **Tooltip Rendering**: Use `DrawContext.drawTooltip()` or custom rendering with `fill()` and `drawText()`
      - **Packet Size**: Consider packet size limits when sending crop statistics (may need to limit to top N crops by count)

---

## Building Output Widget - Implementation Architecture

### Current Issues and Root Causes

**Problem 1: Client-Side Data Loading Failure**
- **Issue**: `BuildingOutputWidget` tries to load NBT structure files on client using `client.getResourceManager()`
- **Root Cause**: Per cursor rules, client ResourceManager cannot reliably access data files (`src/main/resources/data/`)
- **Impact**: Farm building statistics show "Loading..." or fail to display crop data
- **Solution**: Always load data on server and send to client via network packets

**Problem 2: Incomplete Farm Statistics**
- **Issue**: Widget only shows farmland count from NBT structure, not actual crop statistics
- **Root Cause**: No world scanning for actual crops, only NBT structure parsing
- **Impact**: Missing crop type distribution, maturity status, growth times, harvest estimates
- **Solution**: Server-side world scanning to detect actual crops and calculate statistics

**Problem 3: Missing Tooltips and Verbose Data**
- **Issue**: No detailed tooltips showing calculation breakdowns, drop rates, crop maturity details
- **Root Cause**: Tooltip system not implemented
- **Impact**: Users can't see detailed information about outputs and calculations
- **Solution**: Implement comprehensive tooltip system with multi-line formatted text

### Proper Implementation Architecture

**Data Flow Pattern (Server → Client)**:
```
1. Client: User clicks building in BuildingSelectionWidget
2. Client: Calls requestBuildingOutputData(building) → sends BuildingOutputDataPacket
3. Server: Receives packet, loads structure NBT, scans world for crops
4. Server: Calculates all statistics (crop ages, maturity, harvest times, output rates)
5. Server: Sends comprehensive data back to client via packet
6. Client: Receives data, updates BuildingOutputWidget, displays with tooltips
```

**Server-Side Responsibilities**:
- Load structure NBT files (server ResourceManager has access)
- Scan actual world for crops in building area (not just NBT structure)
- Calculate crop statistics (types, ages, maturity, growth times)
- Calculate output rates and probabilities for non-farm buildings
- Send all data to client via network packet

**Client-Side Responsibilities**:
- Request data from server when building is selected
- Display received data in widget with proper formatting
- Render tooltips on hover with detailed information
- Handle loading states and error messages
- Never attempt to load data files directly

### Implementation Best Practices

**1. Server-Side Crop Scanning**:
- Use `StructureData` to get building bounds (min/max BlockPos)
- Iterate through all blocks in structure area
- Check if block is a crop block (wheat, carrots, potatoes, beetroot, modded crops)
- Read crop age from block state properties:
  - `Properties.AGE_7` for wheat, carrots, potatoes (0-7)
  - `Properties.AGE_3` for beetroot (0-3)
  - Generic age property detection for modded crops
- Determine maturity: crop is mature when age == max_age
- Calculate growth time remaining: `(max_age - current_age) × avg_growth_time_per_stage`

**2. Crop Statistics Calculation**:
- Group crops by type (wheat, carrots, etc.)
- Count mature vs immature crops per type
- Calculate age distribution (bins: 0-2, 3-5, 6-7 for AGE_7 crops)
- Estimate harvest time: average growth time for immature crops
- Calculate expected items per harvest: `mature_crops × avg_drops_per_crop`
- Calculate items per minute: `(expected_items_per_harvest / harvest_cycle_time) × 60`

**3. Tooltip System Design**:
- Create `TooltipRenderer` utility class for consistent tooltip rendering
- Use `DrawContext.fill()` for tooltip background (semi-transparent dark)
- Use `DrawContext.drawBorder()` for tooltip border
- Render multi-line text with proper spacing
- Position tooltip to avoid screen edges (adjust X/Y based on mouse position)
- Show different tooltip content based on entry type (output item vs crop statistic)

**4. Performance Considerations**:
- Cache crop statistics on server (don't scan every packet request)
- Update cache every 10-20 seconds or when crops change
- Limit crop scanning to building's structure bounds (don't scan entire settlement)
- Batch crop age calculations efficiently
- Consider packet size limits (may need to limit crop statistics to top N by count)

**5. Error Handling**:
- Handle missing structure NBT files gracefully
- Handle unloaded chunks (skip crops in unloaded areas)
- Handle invalid crop blocks (non-crop blocks in farmland)
- Show appropriate error messages in widget ("Unable to load data", "No crops detected", etc.)

### Data Structures

**CropStatistics Class** (server-side):
```java
public class CropStatistics {
    public final String cropType;           // "wheat", "carrots", etc.
    public final int totalCount;            // Total crops of this type
    public final int matureCount;           // Mature crops ready to harvest
    public final int immatureCount;          // Immature crops
    public final Map<Integer, Integer> ageDistribution; // Age -> count map
    public final int averageAge;            // Average age of all crops
    public final int estimatedTicksUntilHarvest; // For immature crops
    public final double expectedItemsPerHarvest; // When all mature
}
```

**BuildingOutputData Class** (packet data):
```java
public class BuildingOutputData {
    public final UUID buildingId;
    public final boolean isFarm;
    public final List<OutputEntry> outputs;        // For non-farm buildings
    public final List<CropStatistics> cropStats;   // For farm buildings
    public final int farmlandCount;                // Total farmland plots
    public final double estimatedItemsPerMinute;   // Overall production rate
}
```

### Testing Checklist

- [ ] Widget displays correctly when building is selected
- [ ] Widget hides when no building is selected
- [ ] Server loads structure NBT files correctly
- [ ] Server scans world for crops correctly
- [ ] Crop statistics are calculated accurately
- [ ] Packet sends all data to client
- [ ] Client displays crop statistics correctly
- [ ] Tooltips show on hover with correct information
- [ ] Tooltips position correctly (don't go off-screen)
- [ ] Items per minute calculations are accurate
- [ ] Farm buildings show crop data, non-farm buildings show output config
- [ ] Error handling works (missing files, unloaded chunks, etc.)
- [ ] Performance is acceptable (no lag when selecting buildings)

---

## Phase 4: Advanced Features (Future)

### 4.1 Villager Hiring System
**Goal**: Implement villager employment with costs and constraints

**Tasks**:
- [x] Design hiring cost system (emeralds, items, or reputation)
  - [x] Create `HiringCostCalculator` class with profession-based costs
  - [x] Base cost: 5 emeralds, specialized professions: 10 emeralds
  - [x] Implement emerald payment system in `HireFireVillagerPacket`
  - [x] Add cost validation before hiring
- [ ] Create employment contract data structure (TODO: Future enhancement)
- [ ] Implement villager skill level system (TODO: Future enhancement)
- [x] Add profession-specific hiring requirements
  - [x] Different costs for different professions (cartographer, smith, etc.)
- [x] Create hiring UI with cost display
  - [x] Display emerald cost in villager list entry
  - [x] Show emerald icon and cost text for unemployed villagers
- [ ] Implement employment benefits system (TODO: Phase 4.3 feature)

### 4.2 Additional Structures
**Goal**: Add more structure types beyond walls

**Tasks**:
- [x] Create fence structure NBT files (lvl1_oak_fence.nbt created)
- [x] Create gate structure NBT files (lvl1_oak_gate.nbt created)
- [x] Create house structure templates (lvl1_oak_house.nbt, lvl2_oak_house.nbt, lvl3_oak_house.nbt created)
- [ ] Implement villager housing assignment system (TODO: Phase 4.3 feature)
- [x] Create specialized building types (cartographer, farm, smithing created)
- [x] Add structure category system (defensive, residential, commercial)
  - [x] Create `StructureCategory` enum with categories
  - [x] Implement category detection from structure names
  - [x] Add category headers in structure list UI
  - [x] Group structures by category in UI

### 4.3 Villager Work System
**Goal**: Assign villagers to workstations and automate tasks

**Tasks**:
- [x] Create work assignment system
  - [x] Add `assignedBuildingId` field to `VillagerData`
  - [x] Create `WorkAssignmentManager` class
  - [x] Implement `assignVillagerToBuilding()` method
  - [x] Implement `unassignVillager()` method
  - [x] Add assignment tracking in NBT serialization
  - [x] Create `AssignWorkPacket` for network communication
  - [x] Display work assignment status in villager list UI
- [x] Add "Assign Work" button/dialog in UI
  - [x] Add "Assign Work" button for unassigned employed villagers
  - [x] Add "Unassign" button for assigned villagers
  - [x] Implement assignment to first available building
  - [x] Wire up network packet communication
  - [x] Add visual feedback and error messages
- [ ] Implement villager pathfinding to workstations (TODO: Phase 4.3 advanced feature)
- [x] Add automated task execution (smithing, farming, trading) ✅
- [x] Create work schedule system (day/night cycles) ✅
- [ ] Implement productivity tracking (TODO: Phase 4.3 advanced feature)
- [x] Add work output collection system (NEW APPROACH: Villager inventory accumulation + lectern deposit) ✅
  - [x] Create JSON config system for building outputs (configurable per building type)
  - [x] Villagers accumulate items in inventory (up to 32 items)
  - [x] When inventory reaches 32 items, villager walks to lectern
  - [x] Temporarily disable auto-rally during deposit trips
  - [x] Deposit items into nearby chests automatically
  - [x] Re-enable auto-rally after deposit
  - [x] Wall assignments produce: flowers (40%), bones (20%), feathers (30%), seeds (10%)
- [x] Implement active farm crop harvesting system (QoL Enhancement)
  - [x] Change farm building outputs from passive generation to active gathering
  - [x] Load structure data for farm buildings to determine crop area
  - [x] Scan farmland blocks within building NBT structure area
  - [x] Check if crops on farmland are mature (age property check)
  - [x] Support right-click harvest mod detection (if present, use that first) - implemented using FabricLoader, simulates mod behavior
  - [x] Implement vanilla crop harvesting (break and replant)
  - [x] Collect all crop drops and add to villager accumulated items
  - [x] Support modded crops (tomatoes, cabbage, onions, etc.) - generic age property detection
  - [x] Add crop maturity detection for modded crops - uses AGE_7 and AGE_3 properties
  - [x] Handle different crop types (wheat, carrots, potatoes, beetroot, modded)
  - [x] Ensure crops are only harvested when mature (not prematurely)

### 4.4 Settlement Expansion
**Goal**: Add progression and expansion mechanics

**Tasks**:
- [x] Design settlement level/tier system
  - [x] Create `SettlementLevel` enum with 5 levels (Hamlet, Village, Town, City, Metropolis)
  - [x] Add level field to `Settlement` class
  - [x] Implement level calculation based on stats
  - [x] Add `updateLevel()` method to recalculate level
  - [x] Add `canLevelUp()` method to check requirements
- [x] Add expansion requirements (villager count, building count)
  - [x] Define requirements for each level
  - [x] Track villager count, completed building count, and employed villager count
  - [x] Validate requirements before leveling up
- [x] Create progression UI showing current level and requirements
  - [x] Display current level in Overview tab
  - [x] Show progress to next level with requirements
  - [x] Color-code requirements (green = met, gray = not met)
  - [x] Display "Max Level Reached" for level 5
- [x] Implement automatic level recalculation
  - [x] Update level when buildings are completed
  - [x] Update level when buildings are removed/cancelled
  - [x] Update level when villagers are added
  - [x] Update level when villagers are removed (death, despawn, timeout)
  - [x] Update level when villagers are hired (employed count changes)
  - [x] Add level-up/down notifications to players
- [ ] Create unlock system for structures and features (TODO: Future enhancement)
- [ ] Implement settlement reputation system (TODO: Future enhancement)
- [ ] Add rewards for leveling up settlements (TODO: Future enhancement)

### 4.5 Trader Hut Building System
**Goal**: Add trader hut building that attracts wandering traders and allows custom trading with assigned villagers

**Tasks**:
- [ ] Create trader hut NBT structure file
  - [ ] Design trader hut structure (small building with trading area)
  - [ ] Create `lvl1_trader_hut.nbt` structure file
  - [ ] Place structure file in `src/main/resources/data/settlements/structures/`
  - [ ] Ensure structure includes appropriate blocks (workstation, trading area)
  - [ ] Test structure loads correctly via `StructureLoader`
- [ ] Add trader hut to building tab structure list
  - [ ] Verify structure appears in `StructureListWidget` when scanning structures directory
  - [ ] Ensure structure is categorized correctly (commercial category)
  - [ ] Test structure can be selected and placed via build mode
- [ ] Implement wandering trader attraction system
  - [ ] Create `WanderingTraderAttractionSystem.java` class
  - [ ] Add server tick handler to scan for wandering traders near trader huts
  - [ ] Scan for `EntityType.WANDERING_TRADER` within 64 blocks of each trader hut building
  - [ ] Calculate distance from trader hut position to wandering trader
  - [ ] Use AABB bounding box for efficient entity queries
  - [ ] Store attracted traders in building data (optional: track for UI display)
  - [ ] Implement attraction logic (move traders toward hut, or just detect presence)
  - [ ] Add periodic scanning (every 5-10 seconds to avoid performance issues)
- [ ] Create special trader villager system
  - [ ] Design approach for converting assigned villager to special trader
    - [ ] Option A: Extend `VillagerEntity` class (may require mixin)
    - [ ] Option B: Create custom `TraderVillagerEntity` class extending `VillagerEntity`
    - [ ] Option C: Use data component/NBT to mark villager as special trader (recommended)
  - [ ] Implement villager assignment detection for trader huts
    - [ ] Check if assigned building is trader hut type
    - [ ] Trigger conversion when villager is assigned to trader hut
  - [ ] Create `TraderVillagerData` class to store trader-specific data
    - [ ] Add field: `UUID buildingId` (which trader hut they're assigned to)
    - [ ] Add field: `List<TradeOffer>` customTradeOffers
    - [ ] Add field: `boolean isSpecialTrader` flag
    - [ ] Implement `toNbt()` and `fromNbt()` methods
  - [ ] Implement villager conversion logic
    - [ ] When villager assigned to trader hut, mark as special trader
    - [ ] Load custom trade table from JSON config
    - [ ] Replace villager's default trades with custom trades
    - [ ] Store original profession data (for unassignment)
  - [ ] Handle villager unassignment
    - [ ] When villager unassigned from trader hut, restore original profession
    - [ ] Clear custom trade offers
    - [ ] Reset to normal villager behavior
- [ ] Create custom trade table JSON configuration system
  - [ ] Design JSON format for trade tables
    - [ ] Structure: List of trade offers with input/output items
    - [ ] Include: Input item (fruit/vegetable blocks), output item, quantity, max uses
    - [ ] Support multiple trade levels/tiers
  - [ ] Create `TraderTradeConfig.java` data class
    - [ ] Add fields: `List<TradeOfferConfig> trades`, `int maxUses`, `int experienceReward`
    - [ ] Implement JSON deserialization (Gson or similar)
  - [ ] Create `TradeOfferConfig.java` data class
    - [ ] Add fields: `Identifier inputItem`, `int inputCount`, `Identifier outputItem`, `int outputCount`, `int maxUses`
    - [ ] Support for fruit/vegetable block inputs (9 items = 1 block conversion)
  - [ ] Create trade table JSON file: `trader_hut_trades.json`
    - [ ] Place in `src/main/resources/data/settlements/trades/`
    - [ ] Define trades accepting fruit/vegetable blocks
    - [ ] Include vanilla blocks: watermelon blocks, pumpkin blocks
    - [ ] Support modded blocks: apple blocks, strawberry blocks, cabbage blocks, onion blocks, etc.
    - [ ] Define output items for each trade
  - [ ] Implement trade table loading system
    - [ ] Create `TraderTradeLoader.java` utility class
    - [ ] Load JSON file from resources (server-side only, per cursor rules)
    - [ ] Parse JSON into `TraderTradeConfig` objects
    - [ ] Cache loaded trade configs to avoid reloading
    - [ ] Handle missing/invalid JSON files gracefully
- [ ] Implement fruit/vegetable block acceptance system
  - [ ] Create `FruitVegetableBlockRegistry.java` utility class
    - [ ] Maintain list of accepted fruit/vegetable block identifiers
    - [ ] Support both vanilla and modded blocks
    - [ ] Add method: `isFruitVegetableBlock(Identifier)` to check if block is accepted
    - [ ] Add method: `getItemEquivalent(Block)` to convert block to item (9 items = 1 block)
  - [ ] Implement block-to-item conversion logic
    - [ ] When player offers fruit/vegetable block, convert to 9 items
    - [ ] Check if block is in accepted list
    - [ ] Create item stack with count = 9 × block count
    - [ ] Use converted items for trade validation
  - [ ] Add support for condensed blocks (9x9 = 1 block)
    - [ ] Create `BlockCompactor.java` utility class
    - [ ] Implement `compactItemsToBlock(List<ItemStack>)` method
      - [ ] Check if 9 items of same type can be compacted
      - [ ] Return block state if compactable, null otherwise
    - [ ] Implement `expandBlockToItems(BlockState, int)` method
      - [ ] Convert 1 block to 9 items
      - [ ] Return list of item stacks
  - [ ] Integrate block acceptance into trade system
    - [ ] Modify trade offer validation to accept fruit/vegetable blocks
    - [ ] Convert blocks to items before trade processing
    - [ ] Update trade UI to show block icons when applicable
- [ ] Create trader hut building data structure
  - [ ] Add `TraderHutData` class extending or containing building data
    - [ ] Add field: `List<UUID> attractedTraders` (wandering trader entity IDs)
    - [ ] Add field: `UUID assignedVillagerId` (special trader villager)
    - [ ] Add field: `boolean hasSpecialTrader` flag
    - [ ] Implement `toNbt()` and `fromNbt()` methods
  - [ ] Store trader hut data in `Building` class
    - [ ] Add optional `NbtCompound customData` field to Building class
    - [ ] Store trader hut-specific data in customData
    - [ ] Load/save customData in Building serialization
- [ ] Implement UI integration for trader huts
  - [ ] Display trader hut status in building list
    - [ ] Show "Has Trader" or "No Trader" status
    - [ ] Show "X Wandering Traders Attracted" count
    - [ ] Show assigned villager name if assigned
  - [ ] Add trader hut assignment UI
    - [ ] When trader hut is selected, show "Assign Villager" button
    - [ ] Open villager selection dialog (similar to work assignment)
    - [ ] Filter villagers to show only available/unassigned villagers
    - [ ] On assignment, trigger villager conversion
  - [ ] Display custom trade offers in UI (optional)
    - [ ] Show available trades when trader hut is selected
    - [ ] Display input/output items with icons
    - [ ] Show trade availability (uses remaining)
- [ ] Add network packet support
  - [ ] Create `AssignTraderVillagerPacket.java` for assigning villager to trader hut
    - [ ] Send: Building ID, Villager ID
    - [ ] Server validates assignment and converts villager
    - [ ] Send response packet with success/failure
  - [ ] Create `UnassignTraderVillagerPacket.java` for unassigning villager
    - [ ] Send: Building ID, Villager ID
    - [ ] Server restores villager to original profession
  - [ ] Register packets in network initialization
- [ ] Implement trade interaction system
  - [ ] Override villager trade interaction for special traders
    - [ ] Check if villager is special trader (has TraderVillagerData)
    - [ ] Load custom trade offers from config
    - [ ] Display custom trades instead of default trades
    - [ ] Handle fruit/vegetable block input conversion
  - [ ] Create trade offer validation
    - [ ] Check if input is fruit/vegetable block
    - [ ] Convert block to items (9 items per block)
    - [ ] Validate trade offer matches converted items
    - [ ] Execute trade if valid
  - [ ] Handle trade completion
    - [ ] Remove items/blocks from player inventory
    - [ ] Give output items to player
    - [ ] Update trade uses remaining
    - [ ] Grant experience if configured

**Technical Notes**:
- **Wandering Trader Attraction**: Use `World.getEntitiesByType(EntityType.WANDERING_TRADER, boundingBox, predicate)` for efficient scanning
- **Villager Conversion**: Recommended approach is using NBT/data components to mark villagers as special traders rather than extending class (simpler, less invasive)
- **Trade Table Loading**: Always load JSON on server-side per cursor rules (client ResourceManager limitation)
- **Block-to-Item Conversion**: 9 items = 1 block (standard Minecraft compacting ratio)
- **Fruit/Vegetable Blocks**: Support both vanilla (watermelon, pumpkin) and modded blocks (apple, strawberry, cabbage, onion, etc.)
- **Performance**: Scan for wandering traders every 5-10 seconds, not every tick
- **Trade System**: Use Minecraft's existing `Merchant` and `TradeOffer` APIs where possible

**Data Structures**:
```java
TraderHutData {
    UUID buildingId
    List<UUID> attractedTraders  // Wandering trader entity IDs
    UUID assignedVillagerId      // Special trader villager
    boolean hasSpecialTrader
}

TraderVillagerData {
    UUID villagerId
    UUID buildingId
    VillagerProfession originalProfession
    List<TradeOffer> customTrades
    boolean isSpecialTrader
}

TradeOfferConfig {
    Identifier inputItem      // e.g., "minecraft:melon_block"
    int inputCount            // Usually 1 (block) = 9 items
    Identifier outputItem     // What player receives
    int outputCount
    int maxUses
}
```

**JSON Trade Table Format** (`trader_hut_trades.json`):
```json
{
  "trades": [
    {
      "inputItem": "minecraft:melon_block",
      "inputCount": 1,
      "outputItem": "minecraft:emerald",
      "outputCount": 2,
      "maxUses": 12
    },
    {
      "inputItem": "minecraft:pumpkin",
      "inputCount": 1,
      "outputItem": "minecraft:emerald",
      "outputCount": 1,
      "maxUses": 12
    }
  ]
}
```

**Future Enhancement: Building Tier-Based Leveling System**
- [ ] Design building tier/level system for structures
  - [ ] Assign tier levels to structures (e.g., lvl1 = tier 1, lvl2 = tier 2, lvl3 = tier 3)
  - [ ] Parse structure names to determine tier (e.g., "lvl3_oak_house" = tier 3)
  - [ ] Create tier mapping system for structure types
- [ ] Implement 66% rule for level calculation
  - [ ] Calculate percentage of buildings at each tier level
  - [ ] Settlement level = highest tier where ≥66% of buildings are at that tier
  - [ ] Example: To be level 3, at least 66% of buildings must be tier 3 (lvl3_*)
  - [ ] Make level calculation dynamic and automatic
  - [ ] Recalculate level when buildings are added/removed/completed
- [ ] Add level loss mechanics
  - [ ] Settlement can lose levels if building distribution changes
  - [ ] Example: If tier 3 buildings are destroyed, level may drop if <66% remain
  - [ ] Show level changes in UI/logs
- [ ] Design level-based perks/benefits
  - [ ] Define perks for each settlement level (e.g., faster production, better trading, unlock features)
  - [ ] Apply perks when level is reached
  - [ ] Remove perks when level is lost
- [ ] Add non-functional village detection
  - [ ] High level but poor functionality (e.g., all tier 3 walls, no houses/farms)
  - [ ] Consider adding "functionality score" based on building types
  - [ ] Display warnings/suggestions for non-functional settlements
- [ ] Update level calculation system
  - [ ] Replace or enhance current simple level system with tier-based system
  - [ ] Keep backward compatibility with existing settlements
  - [ ] Add migration logic for existing settlements

**Technical Notes for Tier-Based Leveling**:
- Level calculation: Count buildings by tier, find highest tier where count/total ≥ 0.66
- Only count COMPLETED buildings in calculations
- Automatic recalculation on: building completion, building removal, building cancellation
- Consider minimum building count (e.g., need at least 3 buildings before tier-based leveling applies)
- Perks system: Store active perks in Settlement object, update when level changes

---

## Technical Architecture

### Key Classes

**Core**:
- `Settlement` - Main settlement data class
- `SettlementManager` - Manages all settlements in a world
- `SettlementData` - Data persistence handler

**Building System**:
- `StructureLoader` - Loads NBT structure files
- `StructureData` - Holds structure information
- `BuildModeHandler` - Manages build mode state
- `Building` - Represents a building in progress/completed
- `MaterialManager` - Handles material tracking and consumption

**UI**:
- `SettlementScreen` - Main UI screen
- `BuildModeOverlay` - Build mode UI overlay
- `BuildingListWidget` - Building list display
- `VillagerListWidget` - Villager list display

**Integration**:
- `LecternBlockMixin` - Adds right-click functionality to lecterns
- `SettlementBlockEntity` (optional) - Store settlement data per lectern

### Data Storage

**World Save Data**:
- Store in `WorldSaveHandler` or custom save system
- File: `settlements.dat` in world save directory
- Format: NBT or JSON

**Structure Files**:
- Location: `src/main/resources/data/settlements/structures/`
- Format: NBT structure files
- Naming: `wall_basic.nbt`, `fence_wooden.nbt`, etc.

---

## Implementation Priority

### MVP (Minimum Viable Product)
**Status**: ✅ **MVP COMPLETE!** All core features have been implemented.

- [x] Lectern right-click opens UI (Phase 1.1) ✅
- [x] Basic settlement data management (Phase 1.2) ✅
- [x] Villager tracking within radius (Phase 1.3) ✅
- [x] Build mode system (selection, placement, rotation) (Phase 2.2, 2.3) ✅
- [x] NBT structure loading (Phase 2.1) ✅
- [x] Building reservation with barriers (Phase 2.4) ✅
- [x] Material tracking and display (Phase 2.5) ✅
- [x] Sequential block placement (Phase 2.6) ✅
- [x] Complete UI system (Phase 3) ✅

### Post-MVP Enhancements
- Villager hiring system
- Additional structure types
- Villager work assignments
- Settlement progression system

---

## Development Notes

### Fabric-Specific Considerations
- Use Fabric API for block interactions
- Leverage Fabric's structure API if available
- Use Fabric's networking for multiplayer sync
- Consider Fabric's rendering API for preview blocks

### Performance Considerations
- Cache villager data (update periodically, not every tick)
- Limit structure preview rendering distance
- Batch block placement operations
- Optimize NBT structure loading (cache loaded structures)

### Multiplayer Considerations
- Sync settlement data to clients
- Handle build mode conflicts (only one player in build mode per settlement)
- Sync building progress to all players
- Network material consumption events

---

## File Structure

```
src/main/
├── java/com/secretasain/settlements/
│   ├── Settlement.java
│   ├── SettlementManager.java
│   ├── building/
│   │   ├── StructureLoader.java
│   │   ├── StructureData.java
│   │   ├── BuildModeHandler.java
│   │   ├── Building.java
│   │   └── MaterialManager.java
│   ├── villager/
│   │   ├── VillagerData.java
│   │   └── VillagerTracker.java
│   ├── ui/
│   │   ├── SettlementScreen.java
│   │   ├── BuildModeOverlay.java
│   │   └── widgets/
│   ├── mixin/
│   │   └── LecternBlockMixin.java
│   └── SettlementsMod.java
└── resources/
    ├── assets/settlements/
    │   ├── textures/gui/
    │   └── lang/en_us.json
    └── data/settlements/
        └── structures/
            └── wall_basic.nbt
```

---

## Next Steps (Implementation Order)

### Completed Core Features ✅
- [x] Project structure and mod initialization
- [x] Lectern block interaction and UI system
- [x] Settlement data management and persistence
- [x] Villager tracking and scanning
- [x] NBT structure loading
- [x] Build mode foundation and controls
- [x] Structure preview rendering (ghost blocks)
- [x] Building reservation system
- [x] Material management and display
- [x] Sequential block placement
- [x] Building output widget with server-side data loading
- [x] Widget cleanup and overlap fixes

### Recommended Next Steps

#### Priority 1: Performance Optimizations (Building Output Widget)
1. [ ] **Cache crop statistics on server**
   - [ ] Create `CropStatisticsCache` class
   - [ ] Update cache every 10-20 seconds instead of on every packet request
   - [ ] Invalidate cache when crops are harvested/planted
   - [ ] Return cached data when available

2. [ ] **Implement lazy loading for crop scanning**
   - [ ] Only scan world when building is selected
   - [ ] Cancel scan if building selection changes before scan completes
   - [ ] Show "Scanning..." indicator during scan

3. [ ] **Optimize crop scanning bounds**
   - [ ] Use StructureData to get exact building bounds
   - [ ] Limit scanning to building's structure area only
   - [ ] Skip chunks that are outside building bounds

#### Priority 2: UI Enhancements
1. [ ] **Visual progress bars for buildings**
   - [ ] Create custom progress bar widget
   - [ ] Replace text percentage with visual bar
   - [ ] Add color coding (red/yellow/green)

2. [ ] **Villager search/filter functionality**
   - [ ] Add search text field at top of villager list
   - [ ] Filter by name, profession, employment status
   - [ ] Real-time filtering as user types

3. [ ] **Material availability indicators**
   - [ ] Sync material counts to client
   - [ ] Enable/disable "Start Building" button based on materials
   - [ ] Show tooltips for missing materials

#### Priority 3: Optional Enhancements
1. [ ] **Enhanced build mode features**
   - [ ] Grid-snap mode (5-block increments)
   - [ ] Raycast-based block targeting
   - [ ] Reverse rotation keybind (Shift+R)

2. [ ] **Confirmation dialogs**
   - [ ] Building cancellation confirmation
   - [ ] Fire villager confirmation
   - [ ] Structure placement confirmation

3. [ ] **Visual feedback improvements**
   - [ ] Progress indicator particles during construction
   - [ ] Loading indicators for async operations
   - [ ] Better tab button styling

---

## Questions & Considerations

1. **Settlement Radius**: What should be the default radius? (Suggested: 64-128 blocks)
2. **Build Mode Controls**: Should we use keybinds or on-screen buttons?
3. **Structure Rotation**: 90° increments or free rotation?
4. **Material Storage**: Store in lectern inventory or separate storage block?
5. **Multi-Settlement**: Can multiple settlements exist? How to handle overlaps?
6. **Barrier Blocks**: Use actual barrier blocks or custom invisible blocks?

---

*This plan is a living document and should be updated as development progresses.*

