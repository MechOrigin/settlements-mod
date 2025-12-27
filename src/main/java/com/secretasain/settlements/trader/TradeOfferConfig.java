package com.secretasain.settlements.trader;

import net.minecraft.util.Identifier;

/**
 * Configuration for a single trade offer in the trader hut trade table.
 */
public class TradeOfferConfig {
    private Identifier inputItem;
    private int inputCount;
    private Identifier outputItem;
    private int outputCount;
    private int maxUses;
    
    public TradeOfferConfig() {
        // Default constructor for JSON deserialization
    }
    
    public TradeOfferConfig(Identifier inputItem, int inputCount, Identifier outputItem, int outputCount, int maxUses) {
        this.inputItem = inputItem;
        this.inputCount = inputCount;
        this.outputItem = outputItem;
        this.outputCount = outputCount;
        this.maxUses = maxUses;
    }
    
    public Identifier getInputItem() {
        return inputItem;
    }
    
    public void setInputItem(Identifier inputItem) {
        this.inputItem = inputItem;
    }
    
    public int getInputCount() {
        return inputCount;
    }
    
    public void setInputCount(int inputCount) {
        this.inputCount = inputCount;
    }
    
    public Identifier getOutputItem() {
        return outputItem;
    }
    
    public void setOutputItem(Identifier outputItem) {
        this.outputItem = outputItem;
    }
    
    public int getOutputCount() {
        return outputCount;
    }
    
    public void setOutputCount(int outputCount) {
        this.outputCount = outputCount;
    }
    
    public int getMaxUses() {
        return maxUses;
    }
    
    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
    }
}

