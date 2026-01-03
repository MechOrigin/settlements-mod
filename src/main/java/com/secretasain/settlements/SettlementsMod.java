package com.secretasain.settlements;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

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
		LOGGER.info("Town Hall systems registered - villager spawning and wandering trader enhancement enabled");
		
		// Register blocks
		com.secretasain.settlements.block.ModBlocks.register();
		
		// Register custom entities
		com.secretasain.settlements.warband.ModEntities.register();
		
		// Register villager scanning system
		com.secretasain.settlements.settlement.VillagerScanningSystem.register();
		
		// Register golem scanning system
		com.secretasain.settlements.settlement.GolemScanningSystem.register();
		
		// Register wandering trader attraction system
		com.secretasain.settlements.trader.WanderingTraderAttractionSystem.register();
		
		// Register town hall villager spawner system
		com.secretasain.settlements.townhall.TownHallVillagerSpawner.register();
		
		// Register wandering trader despawn handler
		com.secretasain.settlements.townhall.WanderingTraderDespawnHandler.register();
		
		// Register wandering trader spawn system (more reliable than mixin-only approach)
		com.secretasain.settlements.townhall.WanderingTraderSpawnSystem.register();
		
		// Register town hall villager attraction system (spawns villagers for town halls without librarians)
		com.secretasain.settlements.townhall.TownHallVillagerAttractionSystem.register();
		
		// Register town hall villager despawn handler (handles 50/50 stay/leave decision)
		com.secretasain.settlements.townhall.TownHallVillagerDespawnHandler.register();
		
		// Register villager pathfinding system
		com.secretasain.settlements.settlement.VillagerPathfindingSystem.register();
		
		// Register golem pathfinding system
		com.secretasain.settlements.settlement.GolemPathfindingSystem.register();
		
		// Register road placement system
		com.secretasain.settlements.road.RoadPlacementTickSystem.register();
		
		// Register ender teleport system
		com.secretasain.settlements.ender.VillagerEnderTeleportSystem.register();
		
		// Register farm composter system
		com.secretasain.settlements.farm.FarmComposterSystem.register();
		
		// Register lumberyard item collector system
		com.secretasain.settlements.settlement.LumberyardItemCollectorSystem.register();
		
		// Register task execution system
		com.secretasain.settlements.settlement.TaskExecutionSystem.register();
		
		// Register villager sleep system
		com.secretasain.settlements.settlement.VillagerSleepSystem.register();
		
		// Register farm maintenance system (farmland repair and seed planting)
		com.secretasain.settlements.settlement.FarmMaintenanceSystem.register();
		
		// Register villager deposit system
		com.secretasain.settlements.settlement.VillagerDepositSystem.register();
		
		// Register villager death/despawn event handlers
		com.secretasain.settlements.settlement.VillagerEventHandlers.register();
		
		// Register warband NPC death/despawn event handlers
		com.secretasain.settlements.warband.WarbandNpcEventHandlers.register();
		
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
		com.secretasain.settlements.network.HireNpcPacket.register();
		com.secretasain.settlements.network.DismissNpcPacket.register();
		com.secretasain.settlements.network.NpcCommandPacket.register();
		com.secretasain.settlements.network.RequestWarbandNpcsPacket.register();
		com.secretasain.settlements.network.AssignWorkPacket.register();
		com.secretasain.settlements.network.AssignGolemPacket.register();
		com.secretasain.settlements.network.BuildingOutputDataPacket.register();
		
		// Load building output config when server starts
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			com.secretasain.settlements.settlement.BuildingOutputConfig.load(server.getResourceManager());
			// Load trader trade config
			com.secretasain.settlements.trader.TraderTradeLoader.load(server.getResourceManager());
			// Register modded farming blocks (in case mods load after this mod)
			com.secretasain.settlements.trader.FruitVegetableBlockRegistry.registerModdedBlocks();
		});
	}
}