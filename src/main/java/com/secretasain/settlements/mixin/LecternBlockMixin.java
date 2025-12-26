package com.secretasain.settlements.mixin;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.LecternBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept right-click events on lectern blocks.
 * Opens the settlements UI instead of the default lectern interface.
 */
@Mixin(LecternBlock.class)
public class LecternBlockMixin {
    
    @Inject(
        at = @At("HEAD"),
        method = "onUse",
        cancellable = true
    )
    private void onLecternUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        SettlementsMod.LOGGER.info("LecternBlockMixin: onUse called at {}", pos);
        
        // Only handle on server side
        if (!(world instanceof ServerWorld)) {
            SettlementsMod.LOGGER.debug("Not server world, skipping");
            return;
        }
        ServerWorld serverWorld = (ServerWorld) world;
        
        // Only handle for players
        if (!(player instanceof ServerPlayerEntity)) {
            SettlementsMod.LOGGER.debug("Not server player, skipping");
            return;
        }
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        
        // Allow normal lectern behavior if player is sneaking (shift-click)
        if (player.isSneaking()) {
            SettlementsMod.LOGGER.debug("Player is sneaking, allowing vanilla behavior");
            return; // Let vanilla behavior proceed
        }
        
        SettlementsMod.LOGGER.info("Processing settlement interaction for lectern at {}", pos);
        
        try {
            // Get or create settlement for this lectern
            SettlementManager manager = SettlementManager.getInstance(serverWorld);
            Settlement settlement = manager.getSettlementByLectern(pos);
            
            if (settlement == null) {
                SettlementsMod.LOGGER.info("Creating new settlement at {}", pos);
                // Create new settlement on first interaction
                settlement = manager.createSettlement(pos, "New Settlement", 64);
            } else {
                SettlementsMod.LOGGER.info("Found existing settlement: {}", settlement.getName());
            }
            
            // Open the settlements UI on the client via network packet
            SettlementsMod.LOGGER.info("Sending open screen packet to player");
            com.secretasain.settlements.network.OpenSettlementScreenPacket.send(serverPlayer, settlement);
            
            // Cancel the default lectern interaction
            cir.setReturnValue(ActionResult.SUCCESS);
            SettlementsMod.LOGGER.info("Lectern interaction handled successfully");
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Error handling lectern interaction", e);
        }
    }
}

