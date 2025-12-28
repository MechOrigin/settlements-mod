package com.secretasain.settlements.mixin;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.townhall.WanderingTraderSpawnEnhancer;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to enhance wandering trader spawning based on active town halls.
 * Intercepts the spawn attempt and increases spawn chance or spawns near town halls.
 * 
 * Note: The target class may vary by Minecraft version. If this mixin fails to apply,
 * check the actual class name in your Minecraft version.
 */
@Mixin(targets = "net.minecraft.world.spawner.WanderingTraderSpawner")
public abstract class WanderingTraderSpawnerMixin {
    
    /**
     * Intercepts wandering trader spawn attempts.
     * Increases spawn chance based on active town halls and attempts to spawn near town halls.
     */
    @Inject(
        method = "spawn(Lnet/minecraft/server/world/ServerWorld;ZZ)I",
        at = @At("RETURN"),
        cancellable = false
    )
    private void onSpawnAttempt(ServerWorld world, boolean spawnMonsters, boolean spawnAnimals,
                                CallbackInfoReturnable<Integer> cir) {
        SettlementsMod.LOGGER.debug("WanderingTraderSpawnerMixin.onSpawnAttempt called - vanilla result: {}", cir.getReturnValue());
        try {
            // Check if vanilla already spawned a trader
            int vanillaResult = cir.getReturnValue();
            if (vanillaResult > 0) {
                SettlementsMod.LOGGER.debug("Vanilla wandering trader spawn succeeded, skipping enhancement");
                return; // Vanilla already spawned one
            }
            
            // Calculate spawn multiplier based on active town halls
            double multiplier = WanderingTraderSpawnEnhancer.calculateSpawnMultiplier(world);
            
            SettlementsMod.LOGGER.debug("Wandering trader spawn check: multiplier={}, vanillaResult={}", multiplier, vanillaResult);
            
            if (multiplier <= 1.0) {
                SettlementsMod.LOGGER.debug("No active town halls, using vanilla spawning only");
                return; // No enhancement, use vanilla spawning
            }
            
            // Try to spawn near a town hall with enhanced chance
            // Use a reasonable spawn position (near world spawn or player positions)
            BlockPos spawnPos = world.getSpawnPos();
            if (spawnPos == null) {
                spawnPos = new BlockPos(0, 64, 0);
            }
            
            SettlementsMod.LOGGER.info("Attempting enhanced wandering trader spawn near town hall (multiplier: {})", multiplier);
            WanderingTraderEntity trader = WanderingTraderSpawnEnhancer.trySpawnNearTownHall(world, spawnPos);
            if (trader != null) {
                SettlementsMod.LOGGER.info("Successfully spawned enhanced wandering trader {} near town hall at {}", 
                    trader.getUuid(), trader.getBlockPos());
                cir.setReturnValue(1); // Return 1 to indicate a trader was spawned
            } else {
                SettlementsMod.LOGGER.debug("Could not spawn wandering trader near town hall (no valid spawn position found)");
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Error in wandering trader spawn enhancement: {}", e.getMessage(), e);
        }
    }
}

