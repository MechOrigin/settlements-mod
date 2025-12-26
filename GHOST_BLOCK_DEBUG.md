# Ghost Block Rendering Debug Guide

## What We Changed

1. **Removed transparency/blend mode** - Ghost blocks now render fully opaque
2. **Set proper shader programs** - Based on block render layer (solid/cutout/etc)
3. **Added extensive logging** - To diagnose issues

## How to Debug

### Step 1: Check if blocks are being added to GhostBlockManager

After placing an NBT wall, check the logs for:
- `GhostBlockSyncHandler: Added ghost block at ... with represented block ...`
- `GhostBlockSyncHandler: Synced X ghost blocks to manager`

If you DON'T see these, blocks aren't being synced to the manager.

### Step 2: Check if render function is being called

Look for:
- `GhostBlockRenderHandler: renderGhostBlocks() called with X blocks`
- `GhostBlockManager: Rendered X blocks, skipped Y blocks`

If you DON'T see these, the render function isn't being called.

### Step 3: Check for rendering errors

Look for:
- `GhostBlockManager: Failed to render ghost block at ...`
- `Failed to render ghost block at ...: [error message]`

These will tell you why blocks aren't rendering.

### Step 4: Verify block entities have represented blocks

Look for:
- `GhostBlockEntity.readNbt: Successfully read represented block ... from NBT`
- `GhostBlockSyncHandler: Cannot add ghost block at ... - represented block is null or air`

If blocks have null/air represented blocks, they won't render.

## Common Issues

1. **Blocks not in manager**: Check if `GhostBlockSyncHandler.syncAllGhostBlocks()` is being called
2. **Render not called**: Check if `GhostBlockRenderHandler.register()` was called during client init
3. **Shader issues**: Check if shader program is being set correctly (should see no errors)
4. **Block entities missing data**: Check if server is sending sync packets correctly

## Next Steps

1. Place an NBT wall
2. Check logs for the messages above
3. Share the relevant log lines so we can diagnose the exact issue

