package com.secretasain.settlements.block;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registry for all mod blocks.
 */
public class ModBlocks {
    public static final Block GHOST_BLOCK = new GhostBlock(
        FabricBlockSettings.copyOf(Blocks.BARRIER)
            .nonOpaque()
            .noCollision()
            .strength(-1.0f, 3600000.0f) // Unbreakable like barrier
            .dropsNothing()
    );
    
    public static final BlockEntityType<GhostBlockEntity> GHOST_BLOCK_ENTITY = 
        FabricBlockEntityTypeBuilder.create(GhostBlockEntity::new, GHOST_BLOCK).build();
    
    public static final Block ENDER_CORE = new EnderCoreBlock(
        FabricBlockSettings.copyOf(Blocks.END_STONE)
            .strength(3.0f, 3.0f)
            .luminance(15) // Glowing block
    );
    
    /**
     * Registers all blocks and block entities.
     */
    public static void register() {
        Registry.register(Registries.BLOCK, new Identifier(SettlementsMod.MOD_ID, "ghost_block"), GHOST_BLOCK);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, 
            new Identifier(SettlementsMod.MOD_ID, "ghost_block_entity"), 
            GHOST_BLOCK_ENTITY);
        
        Registry.register(Registries.BLOCK, new Identifier(SettlementsMod.MOD_ID, "ender_core"), ENDER_CORE);
        
        SettlementsMod.LOGGER.info("Registered ghost block, ender core, and block entity");
    }
}

