package com.secretasain.settlements;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettlementsMod implements ModInitializer {
	public static final String MOD_ID = "settlements";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Settlements Mod initialized!");
		
		// Register blocks
		com.secretasain.settlements.block.ModBlocks.register();
		
		// Register villager scanning system
		com.secretasain.settlements.settlement.VillagerScanningSystem.register();
		
		// Register villager death/despawn event handlers
		com.secretasain.settlements.settlement.VillagerEventHandlers.register();
		
		// Register block placement scheduler
		com.secretasain.settlements.building.BlockPlacementScheduler.register();
		
		// Register network packet handlers
		com.secretasain.settlements.network.ActivateBuildModePacket.register();
		com.secretasain.settlements.network.ConfirmPlacementPacket.register();
		com.secretasain.settlements.network.CancelBuildingPacket.register();
		com.secretasain.settlements.network.StartBuildingPacket.register();
		com.secretasain.settlements.network.CheckMaterialsPacket.register();
		com.secretasain.settlements.network.UnloadInventoryPacket.register();
		com.secretasain.settlements.network.HireFireVillagerPacket.register();
		com.secretasain.settlements.network.AssignWorkPacket.register();
	}
}