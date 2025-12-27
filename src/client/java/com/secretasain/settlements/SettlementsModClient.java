package com.secretasain.settlements;

import com.secretasain.settlements.building.BuildModeKeybinds;
import com.secretasain.settlements.building.BuildModeRenderer;
import com.secretasain.settlements.block.GhostBlockRenderer;
import com.secretasain.settlements.block.GhostBlockRenderHandler;
import com.secretasain.settlements.block.GhostBlockSyncHandler;
import com.secretasain.settlements.block.ModBlocks;
import com.secretasain.settlements.network.ClientNetworkHandler;
import com.secretasain.settlements.network.DeactivateBuildModePacket;
import com.secretasain.settlements.network.StructureDataPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class SettlementsModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SettlementsMod.LOGGER.info("Initializing Settlements Mod Client");
		
		// Register client-side network handlers
		ClientNetworkHandler.register();
		StructureDataPacket.register();
		DeactivateBuildModePacket.register();
		
		// Register build mode keybinds
		// NOTE: Keybinds are registered but only active when build mode is active
		// The tick handler returns immediately when build mode is inactive to avoid interfering with normal game controls
		BuildModeKeybinds.register();
		
		// Register build mode renderer
		BuildModeRenderer.register();
		
		// Register ghost block renderer (for block entity)
		BlockEntityRendererRegistry.register(ModBlocks.GHOST_BLOCK_ENTITY, GhostBlockRenderer::new);
		
		// Register general-purpose ghost block rendering system
		GhostBlockRenderHandler.register();
		
		// Register ghost block sync handler (syncs block entities to manager)
		GhostBlockSyncHandler.register();
		
		// Register tick handler to sync ghost blocks when world loads
		// This will automatically sync ghost blocks after a short delay when the world loads
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Only sync once per world load - use a simple flag
			if (client.world != null && !com.secretasain.settlements.block.GhostBlockSyncHandler.hasSyncedThisWorld) {
				// Wait a few ticks for block entities to load
				com.secretasain.settlements.block.GhostBlockSyncHandler.syncTicks++;
				if (com.secretasain.settlements.block.GhostBlockSyncHandler.syncTicks >= 10) {
					GhostBlockSyncHandler.syncAllGhostBlocks();
					com.secretasain.settlements.block.GhostBlockSyncHandler.hasSyncedThisWorld = true;
					com.secretasain.settlements.block.GhostBlockSyncHandler.syncTicks = 0;
				}
			} else if (client.world == null) {
				// Reset flag when world unloads
				com.secretasain.settlements.block.GhostBlockSyncHandler.hasSyncedThisWorld = false;
				com.secretasain.settlements.block.GhostBlockSyncHandler.syncTicks = 0;
			}
		});
		
		// Register build mode overlay
		HudRenderCallback.EVENT.register((context, tickDelta) -> {
			com.secretasain.settlements.building.BuildModeOverlay.render(context, tickDelta);
		});
		
		// Load building output config on client (for UI widget)
		// Try to load once when resource manager becomes available
		// Note: Client ResourceManager may not have access to data files (per cursor rules)
		// This is expected to fail, but we try once anyway to avoid spam
		final boolean[] configLoadAttempted = {false};
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!configLoadAttempted[0] && client.getResourceManager() != null) {
				configLoadAttempted[0] = true;
				// Try to load - will fail gracefully if client can't access data files
				// The load() method now checks if already loaded to prevent repeated attempts
				com.secretasain.settlements.settlement.BuildingOutputConfig.load(client.getResourceManager());
			}
		});
		
		SettlementsMod.LOGGER.info("Settlements Mod Client initialized");
	}
}