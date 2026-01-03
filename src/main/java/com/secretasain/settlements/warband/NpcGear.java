package com.secretasain.settlements.warband;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Represents gear equipment for an NPC based on paragon level.
 */
public class NpcGear {
    private Item sword;
    private Item shield;
    private Item helmet;
    private Item chestplate;
    private Item leggings;
    private Item boots;
    
    public NpcGear() {
        // Default empty gear
        this.sword = null;
        this.shield = null;
        this.helmet = null;
        this.chestplate = null;
        this.leggings = null;
        this.boots = null;
    }
    
    public NpcGear(Item sword, Item shield, Item helmet, Item chestplate, Item leggings, Item boots) {
        this.sword = sword;
        this.shield = shield;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
    }
    
    /**
     * Creates gear for a specific paragon level.
     * @param level The paragon level
     * @return Gear appropriate for that level
     */
    public static NpcGear forParagonLevel(ParagonLevel level) {
        return switch (level) {
            case I -> new NpcGear(
                Items.STONE_SWORD,
                Items.SHIELD,
                Items.LEATHER_HELMET,
                Items.LEATHER_CHESTPLATE,
                Items.LEATHER_LEGGINGS,
                Items.LEATHER_BOOTS
            );
            case II -> new NpcGear(
                Items.IRON_SWORD,
                Items.SHIELD,
                Items.IRON_HELMET,
                Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS,
                Items.IRON_BOOTS
            );
            case III -> new NpcGear(
                Items.DIAMOND_SWORD,
                Items.SHIELD,
                Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_BOOTS
            );
            case IV -> new NpcGear(
                Items.NETHERITE_SWORD,
                Items.SHIELD,
                Items.NETHERITE_HELMET,
                Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS
            );
        };
    }
    
    public Item getSword() {
        return sword;
    }
    
    public Item getShield() {
        return shield;
    }
    
    public Item getHelmet() {
        return helmet;
    }
    
    public Item getChestplate() {
        return chestplate;
    }
    
    public Item getLeggings() {
        return leggings;
    }
    
    public Item getBoots() {
        return boots;
    }
    
    /**
     * Writes gear to NBT.
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        if (sword != null) {
            nbt.putString("sword", Registries.ITEM.getId(sword).toString());
        }
        if (shield != null) {
            nbt.putString("shield", Registries.ITEM.getId(shield).toString());
        }
        if (helmet != null) {
            nbt.putString("helmet", Registries.ITEM.getId(helmet).toString());
        }
        if (chestplate != null) {
            nbt.putString("chestplate", Registries.ITEM.getId(chestplate).toString());
        }
        if (leggings != null) {
            nbt.putString("leggings", Registries.ITEM.getId(leggings).toString());
        }
        if (boots != null) {
            nbt.putString("boots", Registries.ITEM.getId(boots).toString());
        }
        return nbt;
    }
    
    /**
     * Reads gear from NBT.
     */
    public static NpcGear fromNbt(NbtCompound nbt) {
        NpcGear gear = new NpcGear();
        if (nbt.contains("sword")) {
            gear.sword = Registries.ITEM.get(Identifier.tryParse(nbt.getString("sword")));
        }
        if (nbt.contains("shield")) {
            gear.shield = Registries.ITEM.get(Identifier.tryParse(nbt.getString("shield")));
        }
        if (nbt.contains("helmet")) {
            gear.helmet = Registries.ITEM.get(Identifier.tryParse(nbt.getString("helmet")));
        }
        if (nbt.contains("chestplate")) {
            gear.chestplate = Registries.ITEM.get(Identifier.tryParse(nbt.getString("chestplate")));
        }
        if (nbt.contains("leggings")) {
            gear.leggings = Registries.ITEM.get(Identifier.tryParse(nbt.getString("leggings")));
        }
        if (nbt.contains("boots")) {
            gear.boots = Registries.ITEM.get(Identifier.tryParse(nbt.getString("boots")));
        }
        return gear;
    }
}

