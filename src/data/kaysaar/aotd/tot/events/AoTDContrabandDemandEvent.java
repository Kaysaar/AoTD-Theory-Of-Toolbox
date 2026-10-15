package data.kaysaar.aotd.tot.events;

import ashlib.data.plugins.events.AshlibBaseEvent;
import ashlib.data.plugins.events.AshlibBaseEventManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import data.kaysaar.aotd.tot.plugins.AoTDBaseDemSupCalc;
import data.kaysaar.aotd.tot.plugins.AoTDExponentialSupDemCalc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;

import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDContrabandDemandEvent extends AoTDTradeDemandEvent {
    public static final String BUYING_FRENZY = "aotd_contraband_buying_frenzy";
    public static final String CONSUMPTION_BOOM = "aotd_underground_consumption_boom";
    public static final String STOCKPILING = "aotd_syndicate_stockpiling";

    public AoTDContrabandDemandEvent(Scope scope) {
        super(scope, getEventTypeId(scope));
    }

    public static String getEventTypeId(Scope scope) {
        switch (scope) {
            case SECTOR: return BUYING_FRENZY;
            case FACTION: return CONSUMPTION_BOOM;
            default: return STOCKPILING;
        }
    }

    @Override
    public void configureEvent() {
        maxAmount = -1;
        switch (getScope()) {
            case SECTOR: minimumDaysBetweenFirings = TradeVolatilitySettings.contrabandSectorCooldownDays; break;
            case FACTION: minimumDaysBetweenFirings = TradeVolatilitySettings.contrabandFactionCooldownDays; break;
            default: minimumDaysBetweenFirings = TradeVolatilitySettings.contrabandMarketCooldownDays;
        }
    }

    @Override
    protected boolean isContrabandEvent() {
        return true;
    }

    @Override
    protected int getMinimumDurationMonths() {
        return TradeVolatilitySettings.contrabandMinimumDurationMonths;
    }

    @Override
    protected int getMaximumDurationMonths() {
        return TradeVolatilitySettings.contrabandMaximumDurationMonths;
    }

    @Override
    protected int[] getPercentages() {
        return TradeVolatilitySettings.contrabandReservePercent;
    }

    @Override
    protected boolean isEligibleCommodity(String commodityId) {
        if (!TradeVolatilitySettings.shortageCommodities.contains(commodityId)
                || !TradeVolatilitySettings.contrabandEventsEnabled
                || !TradeVolatilitySettings.contrabandCommodities.contains(commodityId)) return false;

        // One contraband campaign per commodity, including pending orders, across all scopes.
        for (AshlibBaseEvent event : AshlibBaseEventManager.getInstance().getEvents().values()) {
            if (!(event instanceof AoTDContrabandDemandEvent contraband)) continue;
            for (AoTDTradeDemandOrder order : contraband.getOrders().values()) {
                if (order.target.commodityId.equals(commodityId)) return false;
            }
        }
        return true;
    }

    @Override
    protected int getCandidateDemandAmount(AoTDSupplyDemandData data, MarketAPI market, int percent) {
        if (percent <= 0 || !supports(data)) return 0;
        return data.getTotalRawUnitsFromDemand() > 0 || data.getTotalRawUnitsFromSupply() > 0 ? 1 : 0;
    }

    static LinkedHashMap<MarketAPI, Integer> calculateOrders(Target target) {
        LinkedHashMap<MarketAPI, Integer> amounts = new LinkedHashMap<>();
        if (!TradeVolatilitySettings.shortageCommodities.contains(target.commodityId)
                || !TradeVolatilitySettings.contrabandEventsEnabled
                || !TradeVolatilitySettings.contrabandCommodities.contains(target.commodityId)
                || target.percent <= 0) return amounts;

        LinkedHashMap<MarketAPI, Double> demandWeights = new LinkedHashMap<>();
        LinkedHashMap<MarketAPI, Double> supplyWeights = new LinkedHashMap<>();
        double supply = 0d;
        double demand = 0d;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!isEligibleMarket(market, getEventTypeId(target))) continue;
            if (target.marketId != null && !target.marketId.equals(market.getId())) continue;
            if (target.factionId != null && !target.factionId.equals(market.getFactionId())) continue;
            for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                if (!commodity.getId().equals(target.commodityId)) continue;
                AoTDSupplyDemandData data = getData(commodity);
                if (!supports(data)) continue;
                double marketSupply = Math.max(0, data.getTotalRawUnitsFromSupply());
                double marketDemand = Math.max(0, data.getTotalRawUnitsFromDemand());
                supply += marketSupply;
                demand += marketDemand;
                if (marketDemand > 0d) demandWeights.put(market, marketDemand);
                if (marketSupply > 0d) supplyWeights.put(market, marketSupply);
            }
        }

        // Absorb the NET surplus of the whole scope once, not each producer's surplus.
        double requested = Math.max(0d, supply - demand) + Math.max(supply, demand) * target.percent / 100d;
        LinkedHashMap<MarketAPI, Double> weights = demand > 0d ? demandWeights : supplyWeights;
        double totalWeight = demand > 0d ? demand : supply;
        if (totalWeight <= 0d) return amounts;
        for (Map.Entry<MarketAPI, Double> entry : weights.entrySet()) {
            MarketAPI market = entry.getKey();
            AoTDSupplyDemandData data = getData(market.getCommodityData(target.commodityId));
            double rawAmount = requested * entry.getValue() / totalWeight;
            int units = toDemandUnits(data, market, rawAmount);
            if (units > 0) amounts.put(market, units);
        }
        return amounts;
    }

    static boolean supports(AoTDSupplyDemandData data) {
        if (data == null) return false;
        AoTDBaseDemSupCalc calculator = data.getEconSpec().getCalculationScript();
        float multiplier = data.getEconSpec().getDemandMult();
        return multiplier > 0f && Float.isFinite(multiplier)
                && (calculator.getClass() == AoTDBaseDemSupCalc.class
                || calculator.getClass() == AoTDExponentialSupDemCalc.class);
    }

    static int toDemandUnits(AoTDSupplyDemandData data, MarketAPI market, double rawAmount) {
        AoTDBaseDemSupCalc calculator = data.getEconSpec().getCalculationScript();
        float roundedRaw = Math.nextUp((float) Math.ceil(rawAmount));
        int units = calculator.convertRawUnitsToDemand(roundedRaw, market, data.commodityID);
        // The inverse rounds down. Round the final demand unit up so tiny orders still exist.
        int represented = calculator.getRawUnitsFromDemand(new MutableStat(units), market, data.commodityID, null);
        if (represented < Math.ceil(rawAmount) && units < Integer.MAX_VALUE) units++;
        return units;
    }
}
