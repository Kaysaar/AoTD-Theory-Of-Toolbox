package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.MarketDemand;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;

public class AoTDMarketDemand extends MarketDemand {
    Market market;
    String demandClass;

    private static final float AOTD_MIN_TRADE_IMPACT = 0.01f;

    public AoTDMarketDemand(Market market, String s) {
        super(market, s);
        this.market = market;
        this.demandClass = s;
        ReflectionUtilis.setPrivateVariableFromSuperclass("demand", this, new MutableStat(0f));
    }

    Object readResolve() {
        ReflectionUtilis.setPrivateVariableFromSuperclass("baseCommodity", this, Global.getSettings().getCommoditySpec(demandClass));

        if (market != null && demandClass != null) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("demand", this, new MutableStat(0f));
        }

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
    public float getStockpileUtility() {
        return getStockpileUtility(true);
    }

    @Override
    public float getStockpileUtility(boolean includeTradeImpact) {
        float totalUtility = 0f;

        for (CommodityOnMarket commodity : market.getCommoditiesWithClass(demandClass)) {
            if (commodity == null) continue;

            float utility = Math.max(0.0001f, commodity.getUtilityOnMarket());
            float stockpile = Math.max(0f, commodity.getStockpile());

            if (includeTradeImpact) {
                stockpile += getEffectiveRawTradeImpact(commodity);
            }

            if (stockpile < 0f) {
                stockpile = 0f;
            }

            totalUtility += stockpile * utility;
        }

        return Math.max(0f, totalUtility);
    }

    private static float getEffectiveRawTradeImpact(CommodityOnMarket commodity) {
        float combined = commodity.getCombinedTradeModQuantity();

        float rawSum =
                commodity.getTradeMod().getModifiedValue()
                        + commodity.getTradeModPlus().getModifiedValue()
                        + commodity.getTradeModMinus().getModifiedValue();

        if (Math.abs(rawSum) <= AOTD_MIN_TRADE_IMPACT) {
            return combined;
        }

        if (Math.abs(combined) <= AOTD_MIN_TRADE_IMPACT) {
            return rawSum;
        }

        if (Math.signum(rawSum) == Math.signum(combined)) {
            return Math.abs(rawSum) >= Math.abs(combined) ? rawSum : combined;
        }

        return combined;
    }
}
