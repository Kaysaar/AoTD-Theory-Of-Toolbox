package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.MarketDemand;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;

public class AoTDMarketDemand extends MarketDemand {
    Market market;
    String demandClass;

    public AoTDMarketDemand(Market market, String s) {
        super(market, s);
        this.market = market;
        this.demandClass = s;
        ReflectionUtilis.setPrivateVariableFromSuperclass("demand", this,new MutableStat(0f));


    }
    Object readResolve(){
        ReflectionUtilis.setPrivateVariableFromSuperclass("baseCommodity", this,Global.getSettings().getCommoditySpec(demandClass));
        return this;
    }

    @Override
    public String getDemandClass() {
        return demandClass;
    }

    public Market getMarket() {
        return market;
    }

    @Override
    public float getStockpileUtility(boolean includeTradeImpact) {
        float totalStock = 0f;
        float totalTrade = 0f;

        for (CommodityOnMarket com : AoTdMainWorkTask2.getCommoditiesWithSameDemandClass(demandClass, market)) {
            if(com instanceof AoTDCommodityOnMarket commodity){
                totalStock += commodity.getStocks();
            }
            else{
                totalStock += com.getStockpile();
            }


            if (includeTradeImpact) {
                totalTrade +=
                        com.getTradeMod().getModifiedValue() +
                                com.getTradeModPlus().getModifiedValue() +
                                com.getTradeModMinus().getModifiedValue();
            }
        }

        float demand = getDemand().getModifiedInt();
        float availableAboveDemand = (totalStock + totalTrade);

        return availableAboveDemand;
    }





}
