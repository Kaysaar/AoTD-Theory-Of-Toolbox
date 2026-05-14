package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;

import java.util.Random;

public class AoTDMilitarySubmarketPlugin extends MilitarySubmarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        //		int demand = com.getMaxDemand();
//		int available = com.getAvailable();
//
//		//float limit = BaseIndustry.getSizeMult(available) - BaseIndustry.getSizeMult(Math.max(0, demand - 2));
//		float limit = BaseIndustry.getSizeMult(available);
//		limit *= com.getCommodity().getEconUnit();

        //limit *= com.getMarket().getStockpileMult().getModifiedValue();

        float limit = AoTDOpenMarketPlugin.getStockPileToolbox(com);

        Random random = new Random(market.getId().hashCode() + submarket.getSpecId().hashCode() + Global.getSector().getClock().getMonth() * 170000);
        limit *= 0.9f + 0.2f * random.nextFloat();

        float sm = 1f - market.getStabilityValue() / 10f;
        limit *= (0.25f + 0.75f * sm);

        if (limit < 0) limit = 0;

        return (int) limit;
    }
}
