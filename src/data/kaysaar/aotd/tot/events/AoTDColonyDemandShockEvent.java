package data.kaysaar.aotd.tot.events;

import ashlib.data.plugins.events.AshlibBaseEvent;
import ashlib.data.plugins.events.AshlibBaseEventIntel;
import ashlib.data.plugins.events.AshlibBaseEventManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Short local buying panics. Ashlib fires them after ToT has assigned this month's cargo. */
public class AoTDColonyDemandShockEvent extends AoTDTradeDemandEvent {
    public static final String TRIGGER_ID = "aotd_after_allocation";
    public static final String EVENT_PREFIX = "aotd_colony_demand_shock_";
    private final String commodityId;
    private Integer reportMonth;
    private AoTDColonyShortageIntel monthlyReport;

    public AoTDColonyDemandShockEvent(String commodityId) {
        super(Scope.MARKET, getEventTypeId(commodityId));
        this.commodityId = commodityId;
    }

    public static String getEventTypeId(String commodityId) {
        return EVENT_PREFIX + commodityId;
    }

    @Override
    public String getMinorEventTrigger() {
        return TRIGGER_ID;
    }

    @Override
    protected boolean isPostAllocationEvent() {
        return true;
    }

    @Override
    protected int getMinimumDurationMonths() {
        return 1;
    }

    @Override
    protected int getMaximumDurationMonths() {
        return 1;
    }

    @Override
    protected int[] getPercentages() {
        return TradeVolatilitySettings.colonyShockPercent;
    }

    @Override
    protected boolean isEligibleCommodity(String id) {
        return TradeVolatilitySettings.colonyShocksEnabled && TradeVolatilitySettings.shortageCommodities.contains(id) && commodityId.equals(id)
                && TradeVolatilitySettings.colonyShockCommodities.contains(id);
    }

    @Override
    protected int getCandidateDemandAmount(AoTDSupplyDemandData data, MarketAPI market, int percent) {
        if (percent <= 0 || !AoTDContrabandDemandEvent.supports(data)) return 0;
        return data.getTotalRawUnitsFromDemand() > 0 || data.getTotalRawUnitsFromSupply() > 0 ? 1 : 0;
    }

    @Override
    protected List<Candidate> getCandidates(int percent) {
        List<Candidate> candidates = super.getCandidates(percent);
        Set<String> affected = new LinkedHashSet<>();
        for (AshlibBaseEvent event : AshlibBaseEventManager.getInstance().getEvents().values()) {
            if (!(event instanceof AoTDColonyDemandShockEvent shock)) continue;
            for (AoTDTradeDemandOrder order : shock.getOrders().values()) {
                affected.add(order.target.marketId);
            }
        }
        // At most one shock per world at a time, including shocks for other commodities.
        for (Candidate candidate : candidates) candidate.markets.keySet().removeAll(affected);
        candidates.removeIf(candidate -> candidate.markets.isEmpty());
        return candidates;
    }

    @Override
    public AshlibBaseEventIntel createIntelPlugin() {
        Target target = createEventData();
        if (target == null) return null;
        int month = Global.getSector().getClock().getCycle() * 12 + Global.getSector().getClock().getMonth();
        if (monthlyReport == null || reportMonth == null || reportMonth != month) {
            monthlyReport = new AoTDColonyShortageIntel(target);
            reportMonth = month;
        }
        return monthlyReport;
    }

    @Override
    public void executeEvent(AshlibBaseEventIntel intel) {
        if (!canFireAfterConfiguration()) return;
        AoTDTradeDemandOrder order = new AoTDTradeDemandOrder(createEventData());
        queueOrder(order);
        if (order.status == AoTDTradeDemandOrder.Status.ACTIVE && intel instanceof AoTDColonyShortageIntel report) {
            report.addOrder(order);
        }
    }



    @Override
    protected void queueOrder(AoTDTradeDemandOrder order) {
        String source = getSourceId(order.target);
        if (!applyOrder(source, order)) {
            order.status = AoTDTradeDemandOrder.Status.CANCELLED;
            return;
        }
        order.status = AoTDTradeDemandOrder.Status.ACTIVE;
        order.startedOn = Global.getSector().getClock().getDateString();
        getOrders().put(source, order);
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!order.affectedMarkets.containsKey(market.getId())) continue;
            AoTDCommodityOnMarket commodity = (AoTDCommodityOnMarket) market.getCommodityData(commodityId);
            // Keep the allocation baseline intact. The existing shortage calculation sees the new orders.
            commodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(commodity);
        }
        AoTDTradeContractManager.getInstance().invalidatePredictions();
    }

    static LinkedHashMap<MarketAPI, Integer> calculateOrders(Target target) {
        LinkedHashMap<MarketAPI, Integer> amounts = new LinkedHashMap<>();
        if (!TradeVolatilitySettings.colonyShocksEnabled || target.marketId == null || target.percent <= 0
                || !TradeVolatilitySettings.shortageCommodities.contains(target.commodityId)
                || !TradeVolatilitySettings.colonyShockCommodities.contains(target.commodityId)) return amounts;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!target.marketId.equals(market.getId()) || !market.getFactionId().equals(target.factionId)
                    || !isEligibleMarket(market, getEventTypeId(target))) continue;
            if (!(market.getCommodityData(target.commodityId) instanceof AoTDCommodityOnMarket commodity)) continue;
            AoTDSupplyDemandData data = getData(commodity);
            if (!AoTDContrabandDemandEvent.supports(data)) continue;
            double volume = Math.max(data.getTotalRawUnitsFromSupply(), data.getTotalRawUnitsFromDemand());
            if (volume <= 0d) continue;
            // Absorb this world's unallocated surplus, then request a clean percentage more.
            // Player deliveries can still satisfy the resulting shortage normally.
            double raw = Math.max(0, commodity.getExcDefData().getEffectiveExcess(commodity))
                    + volume * target.percent / 100d;
            int units = AoTDContrabandDemandEvent.toDemandUnits(data, market, raw);
            if (units > 0) amounts.put(market, units);
        }
        return amounts;
    }

    /** Used by the early-campaign demand safeguard to preserve intentional shortages. */
    public static int getUnallocatedDemand(AoTDCommodityOnMarket commodity) {
        AoTDSupplyDemandData data = getData(commodity);
        if (data == null) return 0;
        long demand = 0;
        for (String source : data.additionalDemand.getSources()) {
            if (source.startsWith(EVENT_PREFIX)) demand += data.getRawAdditionalDemand(source, commodity.getMarket());
        }
        return (int) Math.min(Integer.MAX_VALUE, demand);
    }

    static String getShockName(String id) {
        switch (id) {
            case "food": return "Panic at the Food Markets";
            case "drugs": return "Underground Buying Rush";
            case "luxury_goods": return "Luxury Buying Spree";
            case "domestic_goods": return "Household Stockpiling";
            default: return "Local Buying Rush";
        }
    }

    static String getShockNarrative(String id) {
        switch (id) {
            case "food": return "Rumors of unreliable deliveries have sent households and retailers rushing to secure food. This month's shipments were already assigned when the buying began, leaving local brokers searching for fresh cargoes.";
            case "drugs": return "Underground distributors have placed a sudden wave of orders after the regular shipments were assigned. Dealers are competing for the remaining stock, and discreet buyers are looking for independent deliveries.";
            case "luxury_goods": return "A sudden fashion has swept through the colony's wealthy districts. Importers are bidding for luxury goods that their regular delivery schedules cannot provide on such short notice.";
            case "domestic_goods": return "Households are buying ahead, and retailers are rushing to replenish their shelves. The surge arrived after the monthly shipments were assigned, opening a brief opportunity for independent haulers.";
            default: return "A sudden buying rush has outpaced the colony's assigned deliveries.";
        }
    }
}
