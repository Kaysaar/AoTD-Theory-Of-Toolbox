package data.kaysaar.aotd.tot.scripts.submarket.nex;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;
import exerelin.campaign.submarkets.Nex_OpenMarketPlugin;

import java.util.Random;

public class AoTDxNexOpenMarketPlugin extends Nex_OpenMarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
//		int demand = com.getMaxDemand();
//		int available = com.getAvailable();
//
//		float limit = BaseIndustry.getSizeMult(available) - BaseIndustry.getSizeMult(Math.max(0, demand - 2));
//		limit *= com.getCommodity().getEconUnit();

        //limit *= com.getMarket().getStockpileMult().getModifiedValue();
        if(com instanceof AoTDCommodityOnMarket commodity){
            if(commodity.getDef()>0){
                return 0;
            }
            else{
                float limit = AoTDOpenMarketPlugin.getStockPileToolbox(commodity);
                if(limit<=0)return 0;
                Random random = new Random(market.getId().hashCode() + submarket.getSpecId().hashCode() + Global.getSector().getClock().getMonth() * 170000);
                limit *= 0.9f + 0.2f * random.nextFloat();

                float sm = market.getStabilityValue() / 10f;
                limit *= (0.25f + 0.75f * sm);

                if (limit < 0) limit = 0;

                return (int) limit;
            }
        }
        else{
            float limit = OpenMarketPlugin.getBaseStockpileLimit(com);

            Random random = new Random(market.getId().hashCode() + submarket.getSpecId().hashCode() + Global.getSector().getClock().getMonth() * 170000);
            limit *= 0.9f + 0.2f * random.nextFloat();

            float sm = market.getStabilityValue() / 10f;
            limit *= (0.25f + 0.75f * sm);

            if (limit < 0) limit = 0;

            return (int) limit;
        }


    }

}
