package com.secretasain.settlements.block;

import com.secretasain.settlements.SettlementsMod;
import com.secretasain.settlements.ender.EnderUpgrade;
import com.secretasain.settlements.settlement.Settlement;
import com.secretasain.settlements.settlement.SettlementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Block that provides ender upgrade functionality to settlements.
 * When placed in a settlement, allows villagers to use ender pearls for teleportation.
 */
public class EnderCoreBlock extends Block {
    public EnderCoreBlock(Settings settings) {
        super(settings);
    }
    
    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            // Check if this block is within a settlement
            SettlementManager manager = SettlementManager.getInstance(serverWorld);
            Settlement settlement = manager.findSettlementAt(pos);
            
            if (settlement != null) {
                // Check if settlement already has an ender upgrade
                EnderUpgrade existingUpgrade = settlement.getEnderUpgrade();
                if (existingUpgrade != null && existingUpgrade.isActive()) {
                    // Settlement already has an active upgrade - remove this block
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    SettlementsMod.LOGGER.warn("Settlement {} already has an ender upgrade at {}", 
                        settlement.getId(), existingUpgrade.getUpgradeBlockPos());
                    return;
                }
                
                // Create or activate ender upgrade
                EnderUpgrade upgrade;
                if (existingUpgrade != null) {
                    upgrade = existingUpgrade;
                } else {
                    upgrade = new EnderUpgrade(settlement.getId());
                }
                
                upgrade.setActive(true);
                upgrade.setUpgradeBlockPos(pos);
                settlement.setEnderUpgrade(upgrade);
                manager.markDirty();
                
                SettlementsMod.LOGGER.info("Ender upgrade activated for settlement {} at {}", 
                    settlement.getId(), pos);
            }
        }
    }
    
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            // Deactivate ender upgrade when block is removed
            SettlementManager manager = SettlementManager.getInstance(serverWorld);
            Settlement settlement = manager.findSettlementAt(pos);
            
            if (settlement != null) {
                EnderUpgrade upgrade = settlement.getEnderUpgrade();
                if (upgrade != null && upgrade.getUpgradeBlockPos() != null && 
                    upgrade.getUpgradeBlockPos().equals(pos)) {
                    upgrade.setActive(false);
                    settlement.setEnderUpgrade(null); // Remove upgrade
                    manager.markDirty();
                    
                    SettlementsMod.LOGGER.info("Ender upgrade deactivated for settlement {} at {}", 
                        settlement.getId(), pos);
                }
            }
        }
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, 
                              net.minecraft.util.Hand hand, BlockHitResult hit) {
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            SettlementManager manager = SettlementManager.getInstance(serverWorld);
            Settlement settlement = manager.findSettlementAt(pos);
            
            if (settlement != null) {
                EnderUpgrade upgrade = settlement.getEnderUpgrade();
                if (upgrade != null && upgrade.isActive()) {
                    player.sendMessage(Text.translatable("settlements.ender_upgrade.active", 
                        settlement.getName()), false);
                } else {
                    player.sendMessage(Text.translatable("settlements.ender_upgrade.inactive"), false);
                }
                return ActionResult.SUCCESS;
            }
        }
        
        return ActionResult.PASS;
    }
}

