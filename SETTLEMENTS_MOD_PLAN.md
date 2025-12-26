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
- [ ] Create `BuildMode` enum for state management
  - [ ] Create enum: `INACTIVE`, `SELECTION`, `PLACEMENT`, `ROTATION`, `ADJUSTMENT`, `CONFIRMATION`
  - [ ] Add state transition methods
  - [ ] Add validation for valid state transitions
- [ ] Create `BuildModeHandler` class
  - [ ] Create `BuildModeHandler.java` in `building/` package
  - [ ] Add fields: `BuildMode currentState`, `StructureData selectedStructure`, `BlockPos placementPos`, `int rotation`
  - [ ] Implement `activateBuildMode(StructureData)` method
  - [ ] Implement `deactivateBuildMode()` method
  - [ ] Add `isActive()` method
  - [ ] Store handler per-player (use `Map<UUID, BuildModeHandler>`)
- [ ] Add build mode activation from UI
  - [ ] Add "Build Structure" button in Buildings tab
  - [ ] Create structure selection screen/dropdown
  - [ ] On selection, call `BuildModeHandler.activateBuildMode()`
  - [ ] Close UI screen when entering build mode
  - [ ] Show build mode overlay instead
- [ ] Implement structure preview rendering
  - [ ] Create `BuildModeOverlay.java` extending `InGameHud` or custom renderer
  - [ ] Implement ghost block rendering using `WorldRenderer` or custom renderer
  - [ ] Render blocks as semi-transparent (alpha ~0.5)
  - [ ] Use outline rendering for structure bounds
  - [ ] Update preview position based on player crosshair or placement position
  - [ ] Handle rotation in preview rendering
- [ ] Create player movement/camera restrictions (optional)
  - [ ] Consider if restrictions are needed (may not be necessary)
  - [ ] If implementing: disable normal movement, allow only build mode controls
  - [ ] If implementing: lock camera or allow limited camera movement
  - [ ] Add option to toggle restrictions in settings
- [ ] Add build mode exit/cancel functionality
  - [ ] Register Escape key handler to exit build mode
  - [ ] Call `BuildModeHandler.deactivateBuildMode()` on exit
  - [ ] Clear preview rendering
  - [ ] Return to normal gameplay state
  - [ ] Show confirmation dialog if structure is placed but not confirmed

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
- [ ] Implement ghost block rendering system
  - [ ] Create `GhostBlockRenderer.java` utility class
  - [ ] Use `WorldRenderer.drawBox()` or custom rendering
  - [ ] Render each block in structure as semi-transparent box
  - [ ] Apply rotation transformation to block positions
  - [ ] Use different colors for valid/invalid placement (green/red)
  - [ ] Render at placement position + structure offset
- [ ] Add structure positioning system
  - [ ] Implement raycast from player camera to find target block
  - [ ] Use `World.raycast()` or `BlockHitResult` from player
  - [ ] Calculate placement position (snap to block grid)
  - [ ] Update placement position in `BuildModeHandler`
  - [ ] Optionally add grid-snap mode (snap to 5-block increments)
- [ ] Create rotation controls
  - [ ] Register keybind for rotation: `KeyBinding` for 'R' key
  - [ ] Register keybind for reverse rotation: `KeyBinding` for 'Shift+R'
  - [ ] Implement `rotateStructure()` method (90° increments)
  - [ ] Implement `rotateStructureReverse()` method (-90°)
  - [ ] Update rotation field in `BuildModeHandler` (0, 90, 180, 270)
  - [ ] Apply rotation to preview rendering
  - [ ] Store rotation for final placement
- [ ] Add movement controls
  - [ ] Register keybinds: Arrow keys or WASD for horizontal movement
  - [ ] Register keybinds: Space/Shift for vertical movement
  - [ ] Implement `moveStructure(Direction, int)` method
  - [ ] Update placement position by 1 block per keypress
  - [ ] Add fine movement (1 block) and coarse movement (5 blocks with Shift)
  - [ ] Update preview position immediately on movement
- [ ] Implement camera angle adjustment (optional)
  - [ ] Consider if needed (may use normal mouse camera)
  - [ ] If implementing: add keybind to toggle camera lock
  - [ ] If implementing: allow limited camera movement for better view
- [ ] Add collision detection
  - [ ] Implement `canPlaceStructure(BlockPos, StructureData, World)` method
  - [ ] Check each block position in structure
  - [ ] Verify block space is air or replaceable
  - [ ] Check structure doesn't overlap with existing buildings
  - [ ] Verify structure is within settlement bounds
  - [ ] Return validation result with error message
  - [ ] Update preview color based on validation (green=valid, red=invalid)
- [ ] Create placement confirmation UI overlay
  - [ ] Render overlay text showing: "Press ENTER to confirm, ESC to cancel"
  - [ ] Show structure name and material requirements
  - [ ] Display current rotation angle
  - [ ] Show placement position coordinates
  - [ ] Add visual confirmation indicator

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
- [ ] Implement material display in lectern UI
  - [ ] Create material list widget in Buildings tab
  - [ ] Display required vs. available materials side-by-side
  - [ ] Use color coding: green (sufficient), yellow (partial), red (missing)
  - [ ] Show item icons and counts
  - [ ] Add tooltips with item names
  - [ ] Update display when materials change
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
- [ ] Design UI layout
  - [ ] Create UI mockup/sketch
  - [ ] Define tab button positions (top of screen)
  - [ ] Define content area dimensions
  - [ ] Plan widget placement (text, buttons, lists)
  - [ ] Design color scheme and styling
- [ ] Create `SettlementScreen` class structure
  - [ ] Create `SettlementScreen.java` extending `Screen`
  - [ ] Implement constructor taking `Settlement` and `BlockPos`
  - [ ] Override `init()` method for widget initialization
  - [ ] Override `render(DrawContext, int, int, float)` method
  - [ ] Override `shouldPause()` method (return false for game pause)
  - [ ] Add background texture rendering
- [ ] Implement tab switching system
  - [ ] Create `TabButton` widget class extending `ButtonWidget`
  - [ ] Create buttons for: Overview, Buildings, Villagers, Settings
  - [ ] Add `activeTab` field to track current tab
  - [ ] Implement `switchTab(TabType)` method
  - [ ] Update button appearance based on active state
  - [ ] Show/hide tab content based on active tab
- [ ] Add settlement information display
  - [ ] Create information panel widget
  - [ ] Display settlement name (editable text field)
  - [ ] Display villager count: "Villagers: X"
  - [ ] Display building count: "Buildings: X"
  - [ ] Display settlement radius: "Radius: X blocks"
  - [ ] Update information when data changes
- [ ] Create UI textures/assets
  - [ ] Create GUI texture file: `textures/gui/settlement_screen.png`
  - [ ] Design background texture (176x166 pixels)
  - [ ] Create tab button textures (normal, hover, active states)
  - [ ] Create button textures for actions
  - [ ] Add texture to `assets/settlements/textures/gui/`
  - [ ] Register textures in resource loading
- [ ] Implement tooltips and help text
  - [ ] Add tooltip system using `Tooltip` class
  - [ ] Create tooltips for buttons explaining their function
  - [ ] Add help text in Overview tab
  - [ ] Implement hover detection for tooltip display
  - [ ] Style tooltips with background and text

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
- [ ] Create building list widget
  - [ ] Create `BuildingListWidget.java` extending `AbstractListWidget`
  - [ ] Implement list entry class `BuildingListEntry`
  - [ ] Add scrollable list functionality
  - [ ] Display buildings in vertical list
  - [ ] Handle empty list state (show "No buildings" message)
- [ ] Display building information in list entries
  - [ ] Show building structure type name
  - [ ] Display building position coordinates
  - [ ] Show building status with color coding
  - [ ] Display material progress (X/Y blocks)
  - [ ] Add building icon/thumbnail (optional)
- [ ] Implement building status display
  - [ ] Color code: RESERVED (yellow), IN_PROGRESS (blue), COMPLETED (green), CANCELLED (red)
  - [ ] Show status text label
  - [ ] Add status icon indicator
- [ ] Show material requirements
  - [ ] Create expandable material list per building
  - [ ] Display required materials with item icons
  - [ ] Show available vs. required counts
  - [ ] Use color coding for material sufficiency
  - [ ] Add "View Materials" button to expand/collapse
- [x] Add "Start Building" button
  - [x] Create button widget for reserved buildings
  - [x] Show button for RESERVED buildings
  - [x] On click: consume materials and start construction
  - [x] Update building status to IN_PROGRESS
  - [ ] Check if all materials are available (client-side check) - TODO: Requires material sync to client
  - [ ] Enable/disable button based on material availability - TODO: Requires material sync to client
  - [ ] Show confirmation dialog if materials insufficient - TODO: Enhanced UI
- [ ] Implement building cancellation
  - [ ] Add "Cancel" button for RESERVED/IN_PROGRESS buildings
  - [ ] Show confirmation dialog before cancellation
  - [ ] Call `cancelBuilding()` method
  - [ ] Remove building from list
  - [ ] Return materials if applicable
- [ ] Add building progress bars
  - [ ] Create progress bar widget
  - [ ] Display progress for IN_PROGRESS buildings
  - [ ] Show percentage: "45% Complete"
  - [ ] Visual progress bar (filled rectangle)
  - [ ] Update progress in real-time
- [ ] Add "Build New Structure" button
  - [ ] Create button in Buildings tab header
  - [ ] Open structure selection screen
  - [ ] On selection, enter build mode

### 3.3 Villagers Tab
**Goal**: Display and manage settlement villagers

**Tasks**:
- [ ] Create villager list widget
  - [ ] Create `VillagerListWidget.java` extending `AbstractListWidget`
  - [ ] Implement list entry class `VillagerListEntry`
  - [ ] Add scrollable list functionality
  - [ ] Display villagers in vertical list
  - [ ] Handle empty list state (show "No villagers found" message)
- [ ] Display villager information
  - [ ] Show villager name (or "Unnamed Villager")
  - [ ] Display profession icon and name
  - [ ] Show employment status (Employed/Unemployed)
  - [ ] Display last known position (optional, for debugging)
  - [ ] Add villager entity render (small 3D preview, optional)
- [ ] Add villager hire/fire functionality
  - [ ] Create "Hire" button for unemployed villagers
  - [ ] Create "Fire" button for employed villagers
  - [ ] Implement `hireVillager(VillagerData)` method
  - [ ] Implement `fireVillager(VillagerData)` method
  - [ ] Update villager employment status
  - [ ] Show confirmation dialog for fire action
  - [ ] Update UI immediately after hire/fire
- [ ] Show villager work assignments
  - [ ] Display assigned building/workstation for employed villagers
  - [ ] Show "No assignment" for employed but unassigned villagers
  - [ ] Add "Assign Work" button (future feature, Phase 4)
  - [ ] Display work assignment status
- [ ] Implement villager search/filter
  - [ ] Add search text field at top of list
  - [ ] Filter by name (case-insensitive)
  - [ ] Add filter dropdown: All, Employed, Unemployed, by Profession
  - [ ] Update list in real-time as user types
  - [ ] Show "No results" message when filter returns empty
- [ ] Add villager refresh functionality
  - [ ] Add "Refresh List" button
  - [ ] Trigger villager scan immediately
  - [ ] Update list with latest villager data
  - [ ] Show loading indicator during scan

---

## Phase 4: Advanced Features (Future)

### 4.1 Villager Hiring System
**Goal**: Implement villager employment with costs and constraints

**Tasks**:
- [ ] Design hiring cost system (emeralds, items, or reputation)
- [ ] Create employment contract data structure
- [ ] Implement villager skill level system
- [ ] Add profession-specific hiring requirements
- [ ] Create hiring UI with cost display
- [ ] Implement employment benefits system

### 4.2 Additional Structures
**Goal**: Add more structure types beyond walls

**Tasks**:
- [ ] Create fence structure NBT files
- [ ] Create gate structure NBT files
- [ ] Create house structure templates
- [ ] Implement villager housing assignment system
- [ ] Create specialized building types (blacksmith, farm, etc.)
- [ ] Add structure category system (defensive, residential, commercial)

### 4.3 Villager Work System
**Goal**: Assign villagers to workstations and automate tasks

**Tasks**:
- [ ] Create work assignment system
- [ ] Implement villager pathfinding to workstations
- [ ] Add automated task execution (smithing, farming, trading)
- [ ] Create work schedule system (day/night cycles)
- [ ] Implement productivity tracking
- [ ] Add work output collection system

### 4.4 Settlement Expansion
**Goal**: Add progression and expansion mechanics

**Tasks**:
- [ ] Design settlement level/tier system
- [ ] Create unlock system for structures and features
- [ ] Implement settlement reputation system
- [ ] Add expansion requirements (villager count, building count)
- [ ] Create progression UI showing current level and requirements
- [ ] Add rewards for leveling up settlements

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
**Status**: All MVP features are planned but not yet implemented. Check off items as they are completed.

- [ ] Lectern right-click opens UI (Phase 1.1)
- [ ] Basic settlement data management (Phase 1.2)
- [ ] Villager tracking within radius (Phase 1.3)
- [ ] Build mode system (selection, placement, rotation) (Phase 2.2, 2.3)
- [ ] NBT structure loading (Phase 2.1)
- [ ] Building reservation with barriers (Phase 2.4)
- [ ] Material tracking and display (Phase 2.5)
- [ ] Sequential block placement (Phase 2.6)

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

### Immediate Next Steps
1. [ ] Set up project structure and basic mod class
   - [ ] Rename `ExampleMod` to `SettlementsMod`
   - [ ] Update mod ID in `fabric.mod.json`
   - [ ] Create package structure: `com.example.settlements`
   - [ ] Set up basic mod initialization
2. [ ] Implement lectern block interaction (Phase 1.1)
   - [ ] Create `LecternBlockMixin`
   - [ ] Implement right-click interception
   - [ ] Create basic `SettlementScreen` stub
3. [ ] Create settlement data management (Phase 1.2)
   - [ ] Implement `Settlement` class
   - [ ] Create `SettlementManager`
   - [ ] Add persistence system
4. [ ] Implement villager tracking (Phase 1.3)
   - [ ] Create `VillagerData` class
   - [ ] Implement scanning system
   - [ ] Add to UI display
5. [ ] Build NBT structure loading (Phase 2.1)
   - [ ] Create `StructureLoader`
   - [ ] Implement `StructureData` class
   - [ ] Test with sample structure file
6. [ ] Implement build mode foundation (Phase 2.2)
   - [ ] Create `BuildModeHandler`
   - [ ] Add build mode activation
   - [ ] Implement basic state management
7. [ ] Add structure preview rendering (Phase 2.3)
   - [ ] Create ghost block renderer
   - [ ] Implement positioning system
   - [ ] Add rotation controls
8. [ ] Create building reservation system (Phase 2.4)
   - [ ] Implement `Building` class
   - [ ] Add barrier placement
   - [ ] Create building list UI
9. [ ] Implement material management (Phase 2.5)
   - [ ] Create `MaterialManager`
   - [ ] Add material calculation
   - [ ] Implement material input system
10. [ ] Add sequential block placement (Phase 2.6)
    - [ ] Create placement queue
    - [ ] Implement placement algorithm
    - [ ] Add progress tracking

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

