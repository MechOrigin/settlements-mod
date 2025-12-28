package com.secretasain.settlements.ender;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Data class for storing ender upgrade information for a settlement.
 * When active, allows villagers to use ender pearls for teleportation.
 */
public class EnderUpgrade {
    private UUID settlementId;
    private boolean isActive;
    private BlockPos upgradeBlockPos; // Position of the ender core block (if block-based)
    private long enderPearlCooldown; // Ticks between ender pearl uses (default: 100 ticks = 5 seconds)
    private long lastTeleportTime; // Last time a villager used ender pearl (for cooldown tracking)
    
    public EnderUpgrade(UUID settlementId) {
        this.settlementId = settlementId;
        this.isActive = false;
        this.upgradeBlockPos = null;
        this.enderPearlCooldown = 100; // Default: 5 seconds at 20 TPS
        this.lastTeleportTime = 0;
    }
    
    /**
     * Creates an EnderUpgrade from NBT data.
     */
    public static EnderUpgrade fromNbt(NbtCompound nbt, UUID settlementId) {
        EnderUpgrade upgrade = new EnderUpgrade(settlementId);
        upgrade.isActive = nbt.getBoolean("isActive");
        
        if (nbt.contains("upgradeBlockPos", 11)) { // 11 = IntArray
            int[] posArray = nbt.getIntArray("upgradeBlockPos");
            if (posArray.length == 3) {
                upgrade.upgradeBlockPos = new BlockPos(posArray[0], posArray[1], posArray[2]);
            }
        }
        
        upgrade.enderPearlCooldown = nbt.getLong("enderPearlCooldown");
        upgrade.lastTeleportTime = nbt.getLong("lastTeleportTime");
        
        return upgrade;
    }
    
    /**
     * Converts this EnderUpgrade to NBT data.
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("isActive", isActive);
        
        if (upgradeBlockPos != null) {
            nbt.putIntArray("upgradeBlockPos", new int[]{
                upgradeBlockPos.getX(),
                upgradeBlockPos.getY(),
                upgradeBlockPos.getZ()
            });
        }
        
        nbt.putLong("enderPearlCooldown", enderPearlCooldown);
        nbt.putLong("lastTeleportTime", lastTeleportTime);
        
        return nbt;
    }
    
    // Getters and Setters
    public UUID getSettlementId() {
        return settlementId;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public BlockPos getUpgradeBlockPos() {
        return upgradeBlockPos;
    }
    
    public void setUpgradeBlockPos(BlockPos pos) {
        this.upgradeBlockPos = pos;
    }
    
    public long getEnderPearlCooldown() {
        return enderPearlCooldown;
    }
    
    public void setEnderPearlCooldown(long cooldown) {
        this.enderPearlCooldown = cooldown;
    }
    
    public long getLastTeleportTime() {
        return lastTeleportTime;
    }
    
    public void setLastTeleportTime(long time) {
        this.lastTeleportTime = time;
    }
    
    /**
     * Checks if ender pearl teleportation is available (cooldown has passed).
     * @param currentTime Current world time in ticks
     * @return true if teleportation is available
     */
    public boolean canUseEnderPearl(long currentTime) {
        if (!isActive) {
            return false;
        }
        
        return (currentTime - lastTeleportTime) >= enderPearlCooldown;
    }
    
    /**
     * Records that an ender pearl was used.
     * @param currentTime Current world time in ticks
     */
    public void recordTeleport(long currentTime) {
        this.lastTeleportTime = currentTime;
    }
}

