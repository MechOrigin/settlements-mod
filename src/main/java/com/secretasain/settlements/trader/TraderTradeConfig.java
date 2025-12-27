package com.secretasain.settlements.trader;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for trader hut trade table.
 * Contains list of trade offers loaded from JSON.
 */
public class TraderTradeConfig {
    private List<TradeOfferConfig> trades;
    private int maxUses;
    private int experienceReward;
    
    public TraderTradeConfig() {
        this.trades = new ArrayList<>();
        this.maxUses = 12;
        this.experienceReward = 1;
    }
    
    public TraderTradeConfig(List<TradeOfferConfig> trades, int maxUses, int experienceReward) {
        this.trades = trades != null ? trades : new ArrayList<>();
        this.maxUses = maxUses;
        this.experienceReward = experienceReward;
    }
    
    public List<TradeOfferConfig> getTrades() {
        return trades;
    }
    
    public void setTrades(List<TradeOfferConfig> trades) {
        this.trades = trades != null ? trades : new ArrayList<>();
    }
    
    public int getMaxUses() {
        return maxUses;
    }
    
    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
    }
    
    public int getExperienceReward() {
        return experienceReward;
    }
    
    public void setExperienceReward(int experienceReward) {
        this.experienceReward = experienceReward;
    }
}

