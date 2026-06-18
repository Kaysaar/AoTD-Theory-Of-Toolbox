package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;

public class AoTDOpenMarketPlugin extends OpenMarketPlugin {


    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return getStockPileToolbox(com);
    }

    public static int getStockPileToolbox(CommodityOnMarketAPI com){
        if(com instanceof AoTDCommodityOnMarket commodity){
            if(commodity.getDeficitQuantity()>0){
                return 0;
            }
            else{
                commodity.getSupplyDemandData().updateSupplyDemandData(commodity.getMarket());
                float limit = getLimit(com, commodity);
                return (int) limit;
            }
        }
        else{
            float limit = OpenMarketPlugin.getBaseStockpileLimit(com);
            return (int) limit;
        }
    }

    private static float getLimit(CommodityOnMarketAPI com, AoTDCommodityOnMarket commodity) {
        float supply = commodity.getSupplyDemandData().getTotalRawUnitsFromSupply();
        float demand = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();

        float imports = demand-supply;
        float limit = 0f;
        if(imports>0&& commodity.getDef()<=0){
            limit+=imports*0.05f;
        }
        limit+=supply*0.25f;
        limit-= commodity.getDeficitQuantity();
        limit+= commodity.getExcessQuantity();
        if(limit<=0)limit=0;
        return limit;
    }
}
