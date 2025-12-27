package com.secretasain.settlements.settlement;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Statistics for a specific crop type in a farm building.
 * Contains information about crop count, maturity, age distribution, and harvest estimates.
 */
public class CropStatistics {
    public final String cropType;                    // "wheat", "carrots", "potatoes", "beetroot"
    public final Identifier cropItemId;             // Item identifier for the crop (e.g., "minecraft:wheat")
    public final int totalCount;                     // Total crops of this type
    public final int matureCount;                   // Mature crops ready to harvest
    public final int immatureCount;                 // Immature crops
    public final Map<Integer, Integer> ageDistribution; // Age -> count map (e.g., {0: 5, 1: 3, 7: 10})
    public final int averageAge;                    // Average age of all crops
    public final int maxAge;                        // Maximum age for this crop type (7 for wheat/carrots/potatoes, 3 for beetroot)
    public final long estimatedTicksUntilHarvest;  // Average ticks until next harvest (for immature crops)
    public final double expectedItemsPerHarvest;   // Expected items when all mature (matureCount * avgDropsPerCrop)
    public final double avgDropsPerCrop;             // Average drops per crop harvest (typically 1-3, avg 2)
    
    public CropStatistics(String cropType, Identifier cropItemId, int totalCount, int matureCount, 
                         int immatureCount, Map<Integer, Integer> ageDistribution, int averageAge, 
                         int maxAge, long estimatedTicksUntilHarvest, double avgDropsPerCrop) {
        this.cropType = cropType;
        this.cropItemId = cropItemId;
        this.totalCount = totalCount;
        this.matureCount = matureCount;
        this.immatureCount = immatureCount;
        this.ageDistribution = new HashMap<>(ageDistribution);
        this.averageAge = averageAge;
        this.maxAge = maxAge;
        this.estimatedTicksUntilHarvest = estimatedTicksUntilHarvest;
        this.avgDropsPerCrop = avgDropsPerCrop;
        this.expectedItemsPerHarvest = matureCount * avgDropsPerCrop;
    }
    
    /**
     * Gets the maturity percentage (0.0 to 1.0).
     */
    public double getMaturityPercentage() {
        if (totalCount == 0) return 0.0;
        return (double) matureCount / totalCount;
    }
    
    /**
     * Gets the estimated time until harvest in minutes.
     */
    public double getEstimatedMinutesUntilHarvest() {
        return estimatedTicksUntilHarvest / 1200.0; // 1200 ticks = 1 minute (20 ticks/second * 60 seconds)
    }
    
    /**
     * Gets a formatted string describing the age distribution.
     */
    public String getAgeDistributionString() {
        if (ageDistribution.isEmpty()) {
            return "No crops";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : ageDistribution.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("Age ").append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}

