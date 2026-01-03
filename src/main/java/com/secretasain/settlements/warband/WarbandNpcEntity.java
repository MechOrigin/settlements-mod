package com.secretasain.settlements.warband;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.secretasain.settlements.warband.ai.FollowOwnerGoal;
import com.secretasain.settlements.warband.ai.StayAtPositionGoal;
import com.secretasain.settlements.warband.ai.DefensiveCombatGoal;
import com.secretasain.settlements.warband.ai.NpcAttackGoal;

import java.util.UUID;

/**
 * Custom NPC entity for warband members.
 * Can follow players, defend settlements, and fight enemies.
 */
public class WarbandNpcEntity extends PathAwareEntity {
    private UUID playerId; // Player who hired this NPC
    private NpcClass npcClass;
    private ParagonLevel paragonLevel;
    private UUID barracksBuildingId;
    private NpcBehaviorState behaviorState = NpcBehaviorState.FOLLOW;
    private BlockPos stayPosition; // Position to stay at when in STAY mode
    private boolean isAggressive = false; // Aggressive mode (attack hostile mobs)
    
    public WarbandNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setCustomNameVisible(true);
        // Make NPCs persist across world saves (like players)
        // This ensures NPCs are saved and loaded automatically by Minecraft
        this.setPersistent();
    }
    
    /**
     * Creates default attributes for the NPC.
     */
    public static DefaultAttributeContainer.Builder createWarbandNpcAttributes() {
        return createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }
    
    @Override
    protected void initGoals() {
        // Basic goals
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new FollowOwnerGoal(this));
        this.goalSelector.add(3, new StayAtPositionGoal(this));
        this.goalSelector.add(4, new NpcAttackGoal(this, 1.0, false));
        this.goalSelector.add(5, new WanderAroundPointOfInterestGoal(this, 0.6, false));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        
        // Target goals - attack hostile mobs (only in aggressive mode)
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new DefensiveCombatGoal(this));
    }
    
    /**
     * Handles right-click interaction with the NPC.
     * Made public to ensure it can be called from interaction handlers.
     */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // Only allow owner to interact
        if (playerId == null || !player.getUuid().equals(playerId)) {
            return ActionResult.PASS;
        }
        
        // Send packet to client to open interaction screen (server-side only)
        if (!player.getWorld().isClient && player instanceof net.minecraft.server.network.ServerPlayerEntity) {
            com.secretasain.settlements.network.OpenNpcInteractionScreenPacket.send(
                (net.minecraft.server.network.ServerPlayerEntity) player, 
                this.getUuid()
            );
            return ActionResult.SUCCESS;
        }
        
        // Client-side: return success to prevent default interaction
        return ActionResult.SUCCESS;
    }
    
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
    }
    
    /**
     * Prevents NPCs from dropping their equipment when they die.
     */
    @Override
    protected void dropInventory() {
        // Don't drop anything - NPCs keep their gear when they die
        // This prevents players from losing expensive gear when NPCs die
    }
    
    /**
     * Prevents NPCs from dropping items.
     */
    @Override
    public net.minecraft.entity.ItemEntity dropStack(net.minecraft.item.ItemStack stack) {
        // Don't drop any items - return null to prevent dropping
        return null;
    }
    
    /**
     * Prevents NPCs from dropping items with velocity.
     */
    @Override
    public net.minecraft.entity.ItemEntity dropStack(net.minecraft.item.ItemStack stack, float yOffset) {
        // Don't drop any items - return null to prevent dropping
        return null;
    }
    
    /**
     * Prevents NPCs from dropping experience when they die.
     */
    @Override
    public int getXpToDrop() {
        return 0; // NPCs don't drop XP
    }
    
    /**
     * Sets the NPC's data (class, level, player, barracks).
     */
    public void setNpcData(UUID playerId, NpcClass npcClass, ParagonLevel paragonLevel, UUID barracksBuildingId) {
        this.playerId = playerId;
        this.npcClass = npcClass;
        this.paragonLevel = paragonLevel;
        this.barracksBuildingId = barracksBuildingId;
        
        // Set custom name
        String name = npcClass.getDisplayName().getString() + " (" + paragonLevel.getDisplayName().getString() + ")";
        this.setCustomName(Text.literal(name));
        
        // Equip gear based on paragon level
        equipGear();
    }
    
    /**
     * Equips the NPC with gear based on paragon level.
     */
    public void equipGear() {
        NpcGear gear = NpcGear.forParagonLevel(paragonLevel);
        
        // Equip main hand (sword)
        if (gear.getSword() != null) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(gear.getSword()));
        }
        
        // Equip offhand (shield)
        if (gear.getShield() != null) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(gear.getShield()));
        }
        
        // Equip armor
        if (gear.getHelmet() != null) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(gear.getHelmet()));
        }
        if (gear.getChestplate() != null) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(gear.getChestplate()));
        }
        if (gear.getLeggings() != null) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(gear.getLeggings()));
        }
        if (gear.getBoots() != null) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new ItemStack(gear.getBoots()));
        }
    }
    
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (playerId != null) {
            nbt.putUuid("playerId", playerId);
        }
        if (npcClass != null) {
            nbt.putString("npcClass", npcClass.name());
        }
        if (paragonLevel != null) {
            nbt.putString("paragonLevel", paragonLevel.name());
        }
        if (barracksBuildingId != null) {
            nbt.putUuid("barracksBuildingId", barracksBuildingId);
        }
        nbt.putString("behaviorState", behaviorState.name());
        nbt.putBoolean("isAggressive", isAggressive);
        if (stayPosition != null) {
            nbt.putLong("stayPos", stayPosition.asLong());
        }
    }
    
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("playerId")) {
            this.playerId = nbt.getUuid("playerId");
        }
        if (nbt.contains("npcClass")) {
            this.npcClass = NpcClass.valueOf(nbt.getString("npcClass"));
        }
        if (nbt.contains("paragonLevel")) {
            this.paragonLevel = ParagonLevel.valueOf(nbt.getString("paragonLevel"));
            equipGear(); // Re-equip gear when loading from NBT
        }
        if (nbt.containsUuid("barracksBuildingId")) {
            this.barracksBuildingId = nbt.getUuid("barracksBuildingId");
        }
        if (nbt.contains("behaviorState")) {
            this.behaviorState = NpcBehaviorState.valueOf(nbt.getString("behaviorState"));
        }
        if (nbt.contains("isAggressive")) {
            this.isAggressive = nbt.getBoolean("isAggressive");
        }
        if (nbt.contains("stayPos")) {
            this.stayPosition = BlockPos.fromLong(nbt.getLong("stayPos"));
        }
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public NpcClass getNpcClass() {
        return npcClass;
    }
    
    public ParagonLevel getParagonLevel() {
        return paragonLevel;
    }
    
    public UUID getBarracksBuildingId() {
        return barracksBuildingId;
    }
    
    public NpcBehaviorState getBehaviorState() {
        return behaviorState;
    }
    
    public void setBehaviorState(NpcBehaviorState state) {
        this.behaviorState = state;
        if (state == NpcBehaviorState.STAY && stayPosition == null) {
            stayPosition = this.getBlockPos();
        }
    }
    
    public BlockPos getStayPosition() {
        return stayPosition;
    }
    
    public void setStayPosition(BlockPos pos) {
        this.stayPosition = pos;
    }
    
    public boolean isAggressive() {
        return isAggressive;
    }
    
    public void setAggressive(boolean aggressive) {
        boolean wasAggressive = this.isAggressive;
        this.isAggressive = aggressive;
        
        // Force re-evaluation of AI goals when aggressive mode changes
        if (!this.getWorld().isClient) {
            if (wasAggressive && !aggressive) {
                // Turned off - clear targets and stop attacking immediately
                this.setTarget(null);
                // Stop all targeting goals
                this.targetSelector.getRunningGoals().forEach(goal -> {
                    if (goal.getGoal() instanceof net.minecraft.entity.ai.goal.TrackTargetGoal) {
                        goal.stop();
                    }
                });
                // Stop attack goal
                this.goalSelector.getRunningGoals().forEach(goal -> {
                    if (goal.getGoal() instanceof NpcAttackGoal) {
                        goal.stop();
                    }
                });
            } else if (!wasAggressive && aggressive) {
                // Turned on - force target selector to re-evaluate immediately
                // Clear current target first to force re-evaluation
                this.setTarget(null);
                // Tick target selector to find new targets
                this.targetSelector.tick();
                // Also tick goal selector to start attack goal if target found
                this.goalSelector.tick();
            }
        }
    }
}

