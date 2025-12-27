package com.secretasain.settlements.mixin;

import com.secretasain.settlements.trader.TraderTradeLoader;
import com.secretasain.settlements.trader.TraderTradeConfig;
import com.secretasain.settlements.trader.TradeOfferConfig;
import com.secretasain.settlements.trader.TraderVillagerManager;
import com.secretasain.settlements.trader.TradeOfferHelper;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to override villager trades for special trader villagers.
 * Replaces default trades with custom trades from JSON config.
 * 
 * In Minecraft 1.20.1, we intercept when trades are restocked/filled
 * and replace them for special traders assigned to trader huts.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    
    /**
     * Intercepts when villager trades are restocked.
     * This method is called periodically to refresh villager trades.
     * We replace the offers for special traders.
     */
    @Inject(method = "restock()V", at = @At("RETURN"))
    private void onRestock(CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        
        // Check if this villager is a special trader
        if (!TraderVillagerManager.isSpecialTrader(villager.getUuid())) {
            return; // Not a special trader, use default trades
        }
        
        // Get custom trade config
        TraderTradeConfig config = TraderTradeLoader.getCached();
        if (config == null || config.getTrades().isEmpty()) {
            return; // No custom trades loaded, use default
        }
        
        // Replace offers with custom trades
        // Access offers through the Merchant interface
        TradeOfferList currentOffers = villager.getOffers();
        if (currentOffers != null) {
            currentOffers.clear();
            
            for (TradeOfferConfig tradeConfig : config.getTrades()) {
                TradeOffer offer = TradeOfferHelper.createTradeOffer(tradeConfig);
                if (offer != null) {
                    currentOffers.add(offer);
                }
            }
        }
    }
}

