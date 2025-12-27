package com.secretasain.settlements.trader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.secretasain.settlements.SettlementsMod;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading trader trade table configurations from JSON files.
 * Loads from server-side resources (per cursor rules).
 */
public class TraderTradeLoader {
    private static final Identifier TRADE_CONFIG_ID = new Identifier("settlements", "trades/trader_hut_trades.json");
    private static TraderTradeConfig cachedConfig = null;
    
    /**
     * Loads the trader trade configuration from resources.
     * @param resourceManager Resource manager (server-side)
     * @return TraderTradeConfig if loaded successfully, null otherwise
     */
    public static TraderTradeConfig load(ResourceManager resourceManager) {
        // Return cached config if available
        if (cachedConfig != null) {
            return cachedConfig;
        }
        
        try {
            SettlementsMod.LOGGER.info("Loading trader trade config from: {}", TRADE_CONFIG_ID);
            
            // Load resource (server-side only, per cursor rules)
            List<Resource> resources = resourceManager.getAllResources(TRADE_CONFIG_ID);
            
            if (resources.isEmpty()) {
                SettlementsMod.LOGGER.warn("No trade config found at: {}", TRADE_CONFIG_ID);
                return null;
            }
            
            // Use the first resource (highest priority)
            Resource resource = resources.get(0);
            
            try (InputStream inputStream = resource.getInputStream();
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                
                // Parse JSON
                Gson gson = new GsonBuilder().create();
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                
                // Parse trade offers
                List<TradeOfferConfig> trades = new ArrayList<>();
                if (json.has("trades") && json.get("trades").isJsonArray()) {
                    JsonArray tradesArray = json.getAsJsonArray("trades");
                    for (JsonElement element : tradesArray) {
                        if (element.isJsonObject()) {
                            JsonObject tradeObj = element.getAsJsonObject();
                            TradeOfferConfig trade = parseTradeOffer(tradeObj);
                            if (trade != null) {
                                trades.add(trade);
                            }
                        }
                    }
                }
                
                // Parse optional fields
                int maxUses = json.has("maxUses") ? json.get("maxUses").getAsInt() : 12;
                int experienceReward = json.has("experienceReward") ? json.get("experienceReward").getAsInt() : 1;
                
                TraderTradeConfig config = new TraderTradeConfig(trades, maxUses, experienceReward);
                
                // Cache the config
                cachedConfig = config;
                
                SettlementsMod.LOGGER.info("Loaded {} trade offers from trader trade config", trades.size());
                return config;
            }
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Failed to load trader trade config: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Parses a single trade offer from JSON.
     * @param json JSON object containing trade offer data
     * @return TradeOfferConfig or null if parsing failed
     */
    private static TradeOfferConfig parseTradeOffer(JsonObject json) {
        try {
            String inputItemStr = json.get("inputItem").getAsString();
            int inputCount = json.get("inputCount").getAsInt();
            String outputItemStr = json.get("outputItem").getAsString();
            int outputCount = json.get("outputCount").getAsInt();
            int maxUses = json.has("maxUses") ? json.get("maxUses").getAsInt() : 12;
            
            Identifier inputItem = Identifier.tryParse(inputItemStr);
            Identifier outputItem = Identifier.tryParse(outputItemStr);
            
            if (inputItem == null || outputItem == null) {
                SettlementsMod.LOGGER.warn("Invalid item identifier in trade config: input={}, output={}", 
                    inputItemStr, outputItemStr);
                return null;
            }
            
            return new TradeOfferConfig(inputItem, inputCount, outputItem, outputCount, maxUses);
        } catch (Exception e) {
            SettlementsMod.LOGGER.error("Failed to parse trade offer: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Clears the cached trade config.
     * Useful for reloading during development.
     */
    public static void clearCache() {
        cachedConfig = null;
        SettlementsMod.LOGGER.info("Cleared trader trade config cache");
    }
    
    /**
     * Gets the cached trade config without loading.
     * @return Cached TraderTradeConfig or null if not loaded
     */
    public static TraderTradeConfig getCached() {
        return cachedConfig;
    }
}

