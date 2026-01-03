package com.secretasain.settlements.warband;

import com.secretasain.settlements.SettlementsMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registry for custom entities in the Settlements mod.
 */
public class ModEntities {
    public static final EntityType<WarbandNpcEntity> WARBAND_NPC = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier(SettlementsMod.MOD_ID, "warband_npc"),
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, WarbandNpcEntity::new)
            .dimensions(EntityDimensions.fixed(0.6f, 1.95f)) // Same size as player/villager
            .build()
    );
    
    /**
     * Registers all custom entities and their attributes.
     */
    public static void register() {
        SettlementsMod.LOGGER.info("Registering custom entities for Settlements mod");
        
        // Register warband NPC attributes
        FabricDefaultAttributeRegistry.register(WARBAND_NPC, WarbandNpcEntity.createWarbandNpcAttributes());
    }
}

