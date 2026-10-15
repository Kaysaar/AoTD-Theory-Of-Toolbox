package data.kaysaar.aotd.tot.events;

import ashlib.data.plugins.events.AshlibBaseEventIntel;
import ashlib.data.plugins.events.AshlibBaseEvent;
import ashlib.data.plugins.events.AshlibBaseEventManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDBaseDemSupCalc;
import data.kaysaar.aotd.tot.plugins.AoTDExponentialSupDemCalc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AoTDTradeDemandEvent extends BaseTradeEvent {
    public static final String SECTOR_EVENT_ID = "aotd_sector_trade_volatility";
    public static final String FACTION_EVENT_ID = "aotd_faction_trade_volatility";
    public static final String MARKET_EVENT_ID = "aotd_market_trade_volatility";

    public enum Scope {
        SECTOR, FACTION, MARKET
    }

    private final boolean sectorWide;
    private final boolean singleMarket;
    private transient Random random;
    private LinkedHashMap<String, AoTDTradeDemandOrder> orders = new LinkedHashMap<>();
    private Integer lastAllocationMonth;

    public AoTDTradeDemandEvent(boolean sectorWide) {
        this(sectorWide ? Scope.SECTOR : Scope.FACTION);
    }

    public AoTDTradeDemandEvent(Scope scope) {
        this(scope, getEventTypeId(scope));
    }

    protected AoTDTradeDemandEvent(Scope scope, String eventId) {
        super(eventId);
        this.sectorWide = scope == Scope.SECTOR;
        this.singleMarket = scope == Scope.MARKET;
    }

    public Scope getScope() {
        return singleMarket ? Scope.MARKET : sectorWide ? Scope.SECTOR : Scope.FACTION;
    }

    public static String getEventTypeId(Scope scope) {
        switch (scope) {
            case SECTOR: return SECTOR_EVENT_ID;
            case FACTION: return FACTION_EVENT_ID;
            case MARKET: return MARKET_EVENT_ID;
            default: throw new IllegalArgumentException("Unknown trade event scope: " + scope);
        }
    }

    public static String getEventTypeId(Target target) {
        return target.postAllocation ? AoTDColonyDemandShockEvent.getEventTypeId(target.commodityId)
                : target.contraband ? AoTDContrabandDemandEvent.getEventTypeId(target.getScope())
                : getEventTypeId(target.getScope());
    }

    protected boolean isPostAllocationEvent() {
        return false;
    }

    protected boolean isContrabandEvent() {
        return false;
    }

    protected int getMinimumDurationMonths() {
        return TradeVolatilitySettings.minimumDurationMonths;
    }

    protected int getMaximumDurationMonths() {
        return TradeVolatilitySettings.maximumDurationMonths;
    }

    protected boolean isEligibleCommodity(String commodityId) {
        return true;
    }

    protected int getCandidateDemandAmount(AoTDSupplyDemandData data, MarketAPI market, int percent) {
        return getAdditionalDemandAmount(data, market, percent);
    }



    @Override
    public boolean isMinorEvent() {
        return !sectorWide;
    }

    @Override
    public Target createEventData() {
        Target target = getTarget();
        return target == null ? null : new Target(target);
    }

    @Override
    public AshlibBaseEventIntel createIntelPlugin() {
        Target data = createEventData();
        return data == null ? null : new AoTDTradeDemandIntel(data);
    }

    @Override
    public void configureEvent() {
        maxAmount = -1;
        minimumDaysBetweenFirings = 0f;
    }

    @Override
    public boolean canEvenFireEvent() {
        if (!TradeVolatilitySettings.enabled || AoTDEconomy.runningPrePlayerEconomy) return false;

        int maximumPercent = 0;
        for (int percent : getPercentages()) {
            maximumPercent = Math.max(maximumPercent, percent);
        }
        return !getCandidates(maximumPercent).isEmpty();
    }

    @Override
    public void configureEventBeforeFiringUp() {
        possibleTargetOfEvent = null;
        int[] percentages = getPercentages();
        if (percentages.length == 0) return;

        int percent = percentages[getRandom().nextInt(percentages.length)];
        List<Candidate> candidates = getCandidates(percent);
        if (candidates.isEmpty()) return;

        float totalWeight = 0f;
        for (Candidate candidate : candidates) {
            totalWeight += candidate.getWeight();
        }
        float roll = getRandom().nextFloat() * totalWeight;
        Candidate chosen = candidates.get(candidates.size() - 1);
        for (Candidate candidate : candidates) {
            roll -= candidate.getWeight();
            if (roll < 0f) {
                chosen = candidate;
                break;
            }
        }

        String factionId = null;
        String marketId = null;
        String marketName = null;
        if (singleMarket) {
            List<MarketAPI> markets = new ArrayList<>(chosen.markets.values());
            MarketAPI market = markets.get(getRandom().nextInt(markets.size()));
            factionId = market.getFactionId();
            marketId = market.getId();
            marketName = market.getName();
        } else if (!sectorWide) {
            List<String> factions = new ArrayList<>(chosen.factions);
            factionId = factions.get(getRandom().nextInt(factions.size()));
        }
        int months = getMinimumDurationMonths() + getRandom().nextInt(
                getMaximumDurationMonths() - getMinimumDurationMonths() + 1
        );
        possibleTargetOfEvent = new Target(chosen.commodityId, factionId, marketId, marketName,
                percent, months, getRandom().nextInt(3), isContrabandEvent(), isPostAllocationEvent());
    }

    @Override
    public boolean canFireAfterConfiguration() {
        Target target = getTarget();
        if (target == null || !TradeVolatilitySettings.enabled || AoTDEconomy.runningPrePlayerEconomy) return false;
        if (target.getScope() != getScope() || target.contraband != isContrabandEvent()
                || target.postAllocation != isPostAllocationEvent()) return false;

        for (Candidate candidate : getCandidates(target.percent)) {
            if (!candidate.commodityId.equals(target.commodityId)) continue;
            if (singleMarket) {
                MarketAPI market = candidate.markets.get(target.marketId);
                return market != null && market.getFactionId().equals(target.factionId);
            }
            return sectorWide || candidate.factions.contains(target.factionId);
        }
        return false;
    }

    @Override
    public void executeEvent() {
        if (!canFireAfterConfiguration()) return;
        queueOrder(new AoTDTradeDemandOrder(getTarget()));
    }

    @Override
    public void executeEvent(AshlibBaseEventIntel intel) {
        if (!canFireAfterConfiguration()) return;
        AoTDTradeDemandOrder order = intel instanceof AoTDTradeDemandIntel tradeIntel
                ? tradeIntel.getOrder() : new AoTDTradeDemandOrder(getTarget());
        queueOrder(order);
    }

    protected void queueOrder(AoTDTradeDemandOrder order) {
        Target target = order.target;
        getOrders().putIfAbsent(getSourceId(target), order);
    }

    protected LinkedHashMap<String, AoTDTradeDemandOrder> getOrders() {
        if (orders == null) orders = new LinkedHashMap<>();
        return orders;
    }

    /** Called by ToT's stepper before rebuilding balances and allocating monthly trade. */
    public static boolean prepareForMonthlyAllocation() {
        if (AoTDEconomy.runningPrePlayerEconomy) return false;
        boolean changed = false;
        for (AshlibBaseEvent event : AshlibBaseEventManager.getInstance().getEvents().values()) {
            if (event instanceof AoTDTradeDemandEvent tradeEvent) {
                changed |= tradeEvent.advanceMonthlyOrders();
            }
        }
        return changed;
    }

    boolean advanceMonthlyOrders() {
        int month = Global.getSector().getClock().getCycle() * 12 + Global.getSector().getClock().getMonth();
        if (lastAllocationMonth != null && lastAllocationMonth == month) return false;
        lastAllocationMonth = month;

        boolean changed = false;
        Iterator<Map.Entry<String, AoTDTradeDemandOrder>> iterator = getOrders().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AoTDTradeDemandOrder> entry = iterator.next();
            AoTDTradeDemandOrder order = entry.getValue();
            if (order.status == AoTDTradeDemandOrder.Status.PENDING) {
                if (applyOrder(entry.getKey(), order)) {
                    order.status = AoTDTradeDemandOrder.Status.ACTIVE;
                    order.startedOn = Global.getSector().getClock().getDateString();
                    changed = true;
                } else {
                    order.status = AoTDTradeDemandOrder.Status.CANCELLED;
                    iterator.remove();
                }
            } else if (--order.monthsRemaining <= 0) {
                removeOrder(entry.getKey());
                order.status = AoTDTradeDemandOrder.Status.EXPIRED;
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    protected boolean applyOrder(String source, AoTDTradeDemandOrder order) {
        Target target = order.target;
        order.affectedMarkets = new LinkedHashMap<>();
        order.additionalRawDemand = 0L;
        for (Map.Entry<MarketAPI, Integer> entry : getOrderAmounts(target).entrySet()) {
            MarketAPI market = entry.getKey();
            AoTDSupplyDemandData data = getData(market.getCommodityData(target.commodityId));
            // Ordinary orders begin at allocation; local shocks begin at their delayed trigger.
            data.additionalDemand.modifyFlat(source, entry.getValue(), getEventName(target), true);
            order.affectedMarkets.put(market.getId(), market.getName());
            order.additionalRawDemand += data.getRawAdditionalDemand(source, market);
        }
        return !order.affectedMarkets.isEmpty();
    }

    static LinkedHashMap<String, String> getExpectedMarkets(Target target) {
        LinkedHashMap<String, String> markets = new LinkedHashMap<>();
        for (MarketAPI market : getOrderAmounts(target).keySet()) {
            markets.put(market.getId(), market.getName());
        }
        return markets;
    }

    private static LinkedHashMap<MarketAPI, Integer> getOrderAmounts(Target target) {
        LinkedHashMap<MarketAPI, Integer> amounts = new LinkedHashMap<>();
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(target.commodityId);
        if (!TradeVolatilitySettings.enabled || spec == null || spec.getEconUnit() <= 0f
                || TradeVolatilitySettings.excludedCommodities.contains(target.commodityId)
                || spec.hasTag(TradeVolatilitySettings.excludedCommodityTag)) return amounts;

        if (target.postAllocation) return AoTDColonyDemandShockEvent.calculateOrders(target);
        if (target.contraband) return AoTDContrabandDemandEvent.calculateOrders(target);

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!isEligibleMarket(market, getEventTypeId(target))) continue;
            if (target.marketId != null && !target.marketId.equals(market.getId())) continue;
            if (target.factionId != null && !target.factionId.equals(market.getFactionId())) continue;

            for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                if (!commodity.getId().equals(target.commodityId)) continue;
                AoTDSupplyDemandData data = getData(commodity);
                int amount = getAdditionalDemandAmount(data, market, target.percent);
                if (amount <= 0) continue;

                amounts.put(market, amount);
            }
        }
        return amounts;
    }

    private void removeOrder(String source) {
        // Include markets that were captured or hidden after the order began.
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                AoTDSupplyDemandData data = getData(commodity);
                if (data != null) data.additionalDemand.unmodify(source);
            }
        }
    }

    @Override
    public String getEventName() {
        return getEventName(getTarget());
    }

    static String getEventName(Target target) {
        if (target != null && target.postAllocation) return AoTDColonyDemandShockEvent.getShockName(target.commodityId);
        if (target != null && target.contraband) {
            switch (target.getScope()) {
                case SECTOR: return "Contraband Buying Frenzy";
                case FACTION: return "Underground Consumption Boom";
                default: return "Syndicate Stockpiling";
            }
        }
        if (target == null || target.narrative == 0) return "A Rush of Orders";
        return target.narrative == 1 ? "Commercial Expansion" : "Rebuilding Reserves";
    }

    @Override
    public void createTitleForEvent(TooltipMakerAPI tooltip) {
        FactionAPI faction = getAffectedFaction(getTarget());
        tooltip.addTitle(getEventName(), faction == null ? Misc.getButtonTextColor() : faction.getBaseUIColor());
    }

    @Override
    public void createTooltipForEvent(TooltipMakerAPI tooltip) {
        createTooltip(tooltip, getTarget());
    }

    static void createTooltip(TooltipMakerAPI tooltip, Target target) {
        if (target == null) return;

        boolean sectorWide = target.getScope() == Scope.SECTOR;
        boolean singleMarket = target.getScope() == Scope.MARKET;
        FactionAPI faction = getAffectedFaction(target);
        Color headingColor = faction == null ? Misc.getButtonTextColor() : faction.getBaseUIColor();
        Color headingBackground = faction == null ? Misc.getDarkPlayerColor() : faction.getDarkUIColor();
        String commodity = Global.getSettings().getCommoditySpec(target.commodityId).getName();
        String factionName = faction == null ? target.factionId : faction.getDisplayName();
        String location = singleMarket ? "at " + getScopeName(target)
                : sectorWide ? "across the Sector" : "in markets controlled by " + factionName;

        tooltip.addSectionHeading(singleMarket ? "Local Trade Bulletin"
                        : sectorWide ? "Sector Trade Bulletin" : "Faction Trade Bulletin",
                headingColor, headingBackground, Alignment.MID, 0f);
        tooltip.addPara("Trade reports point to growing demand for %s %s.", 10f, Color.ORANGE, commodity, location);
        tooltip.addPara(getNarrative(target), 10f);

        tooltip.addSectionHeading("Market Effects", headingColor, headingBackground, Alignment.MID, 15f);
        tooltip.addPara("Commodity: %s", 10f, Color.ORANGE, commodity);
        if (target.postAllocation) {
            tooltip.addPara("Buyers absorb the colony's allocated surplus, then request a further %s of its larger monthly supply or demand.",
                    5f, Color.ORANGE, target.percent + "%");
        } else if (target.contraband) {
            tooltip.addPara("Buyers intend to absorb the available surplus, then seek a further %s buying reserve.",
                    5f, Color.ORANGE, target.percent + "%");
            tooltip.addPara("The reserve is measured against the larger of monthly supply and demand in the affected markets. Outside deliveries may ease the resulting shortage.", 5f);
        } else {
            tooltip.addPara("Additional orders: approximately %s of existing industrial demand.", 5f,
                    Misc.getPositiveHighlightColor(), "+" + target.percent + "%");
        }
        tooltip.addPara("Scope: %s", 5f, headingColor, getScopeLabel(target));
        tooltip.addPara(singleMarket ? "Affected colony: %s" : "Affected markets: %s", 5f, headingColor,
                sectorWide ? "Eligible markets across the Sector" : getScopeName(target));
        if (singleMarket) {
            tooltip.addPara("Faction: %s", 5f, headingColor, factionName);
        }
        tooltip.addPara("Expected duration: %s", 5f, Color.ORANGE,
                target.postAllocation ? "Until the next allocation" : target.durationMonths + " months");
        tooltip.addPara(target.postAllocation
                ? "Buying begins one or two days after cargo has been assigned. These orders take priority over local industry and end before the next monthly allocation."
                : "Ordering begins with the next monthly trade allocation. These orders take priority over local industry and remain in place for the stated number of months.", 10f);
        tooltip.addPara("There may be profit in following the new orders, though established routes and competing suppliers will decide who captures it.", Misc.getGrayColor(), 10f);
    }

    static String getNarrative(Target target) {
        if (target.postAllocation) return AoTDColonyDemandShockEvent.getShockNarrative(target.commodityId);
        if (target.contraband) {
            switch (target.getScope()) {
                case SECTOR: return "Rival syndicates are bidding against one another across the Sector. Brokers who once struggled to move their stock are reporting empty warehouses, while discreet buyers offer long-term contracts to anyone able to keep deliveries flowing.";
                case FACTION: return "An expanding network of underground distributors is drawing in new customers. Local dealers are placing larger orders, and their suppliers are buying ahead to keep their outlets stocked.";
                default: return "A local syndicate has ordered its brokers to rebuild its reserves. Quiet purchases have become a concerted buying campaign, with intermediaries competing to secure whatever stock reaches the docks.";
            }
        }
        String[] descriptions = {
                "Procurement offices are bringing forward their purchases, while wholesalers compete to secure reliable deliveries. Orders that once waited for a favorable price are now being signed without delay.",
                "A wave of commercial expansion has pushed purchasing budgets upward. Traders report fresh inquiries from established buyers and new intermediaries alike, all looking for dependable supplies.",
                "Buyers have begun rebuilding their reserves. What started as a handful of unusually large orders has spread through the trade networks, encouraging others to secure cargoes before their competitors do."
        };
        return descriptions[target.narrative];
    }

    protected List<Candidate> getCandidates(int percent) {
        LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>();
        Set<String> activeSources = new LinkedHashSet<>(getOrders().keySet());

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                AoTDSupplyDemandData data = getData(commodity);
                if (data == null) continue;
                // Include hidden or captured markets when checking for an existing event.
                activeSources.addAll(data.additionalDemand.getFlatMods().keySet());
                if (market.isHidden()) continue;

                CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodity.getId());
                if (spec.getEconUnit() <= 0f
                        || !isEligibleCommodity(commodity.getId())
                        || TradeVolatilitySettings.excludedCommodities.contains(commodity.getId())
                        || spec.hasTag(TradeVolatilitySettings.excludedCommodityTag)) continue;

                Candidate candidate = candidates.computeIfAbsent(commodity.getId(), Candidate::new);
                candidate.demand += Math.max(0, data.getTotalRawUnitsFromDemand());
                candidate.supply += Math.max(0, data.getTotalRawUnitsFromSupply());
                if (isEligibleMarket(market, getUniqueEventTypeID()) && getCandidateDemandAmount(data, market, percent) > 0) {
                    candidate.factions.add(market.getFactionId());
                    candidate.markets.put(market.getId(), market);
                }
            }
        }

        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            if (singleMarket) {
                candidate.markets.keySet().removeIf(id -> activeSources.contains(
                        getSourceIdForEvent(candidate.commodityId, null, id)));
                if (!candidate.markets.isEmpty()) result.add(candidate);
                continue;
            } else if (sectorWide) {
                if (activeSources.contains(getSourceIdForEvent(candidate.commodityId, null, null))) continue;
            } else {
                candidate.factions.removeIf(id -> activeSources.contains(getSourceIdForEvent(candidate.commodityId, id, null)));
            }
            if (!candidate.factions.isEmpty()) result.add(candidate);
        }
        return result;
    }

    protected static int getAdditionalDemandAmount(AoTDSupplyDemandData data, MarketAPI market, int percent) {
        if (data == null) return 0;
        AoTDBaseDemSupCalc calculator = data.getEconSpec().getCalculationScript();
        if (calculator.getClass() != AoTDBaseDemSupCalc.class
                && calculator.getClass() != AoTDExponentialSupDemCalc.class) return 0;

        // Additional demand stores vanilla demand units, not raw cargo units.
        // Use the industry subtotal so overlapping events do not compound.
        float rawAmount = Math.max(0, data.getRawUnitsFromIndustryDemand()) * (percent / 100f);
        // Keep floating-point roundoff from dropping an exact whole demand unit.
        return calculator.convertRawUnitsToDemand(Math.nextUp(rawAmount), market, data.commodityID);
    }

    protected static AoTDSupplyDemandData getData(CommodityOnMarketAPI commodity) {
        if (!(commodity instanceof AoTDCommodityOnMarket aotd)) return null;
        // Eligibility checks must not initialize or recalculate the economy.
        return aotd.getAoTDAvailableStat().getSupplyDemandDataIfInitialized();
    }

    protected static boolean isEligibleMarket(MarketAPI market, String eventId) {
        return !market.isHidden() && !TradeVolatilitySettings.excludedFactions.contains(market.getFactionId())
                && TradeVolatilitySettings.canTargetPlayer(eventId, market);
    }

    public static String getSourceId(String commodityId, String factionId) {
        return getSourceId(commodityId, factionId, null);
    }

    public static String getSourceId(String commodityId, String factionId, String marketId) {
        // Market IDs keep local orders separate, even when ownership changes.
        if (marketId != null) return MARKET_EVENT_ID + ":" + marketId + ":" + commodityId;
        return factionId == null ? SECTOR_EVENT_ID + ":" + commodityId
                : FACTION_EVENT_ID + ":" + factionId + ":" + commodityId;
    }

    public static String getSourceId(Target target) {
        return getEventTypeId(target) + getSourceSuffix(target.commodityId, target.factionId, target.marketId);
    }

    private String getSourceIdForEvent(String commodityId, String factionId, String marketId) {
        return getUniqueEventTypeID() + getSourceSuffix(commodityId, factionId, marketId);
    }

    private static String getSourceSuffix(String commodityId, String factionId, String marketId) {
        String location = marketId != null ? marketId : factionId;
        return (location == null ? "" : ":" + location) + ":" + commodityId;
    }

    protected int[] getPercentages() {
        if (singleMarket) return TradeVolatilitySettings.marketDemandPercent;
        return sectorWide ? TradeVolatilitySettings.sectorDemandPercent : TradeVolatilitySettings.factionDemandPercent;
    }

    static String getScopeLabel(Target target) {
        switch (target.getScope()) {
            case SECTOR: return "Sector-wide";
            case FACTION: return "Faction-wide";
            case MARKET: return "Single colony";
            default: throw new IllegalArgumentException("Unknown trade event scope");
        }
    }

    static String getScopeName(Target target) {
        if (target.marketId != null) return target.marketName == null ? target.marketId : target.marketName;
        if (target.factionId == null) return "Sector-wide";
        FactionAPI faction = getAffectedFaction(target);
        return faction == null ? target.factionId : faction.getDisplayName();
    }

    protected Random getRandom() {
        if (random == null) random = new Random();
        return random;
    }

    private Target getTarget() {
        return possibleTargetOfEvent instanceof Target target ? target : null;
    }

    static FactionAPI getAffectedFaction(Target target) {
        return target == null || target.factionId == null ? null : Global.getSector().getFaction(target.factionId);
    }

    protected static class Candidate {
        final String commodityId;
        double supply;
        double demand;
        final Set<String> factions = new LinkedHashSet<>();
        final LinkedHashMap<String, MarketAPI> markets = new LinkedHashMap<>();

        Candidate(String commodityId) {
            this.commodityId = commodityId;
        }

        float getWeight() {
            return demand > supply ? TradeVolatilitySettings.deficitSelectionWeight : 1f;
        }
    }

    public static class Target {
        public final String commodityId;
        public final String factionId;
        public final String marketId;
        public final String marketName;
        public final int percent;
        public final int durationMonths;
        public final int narrative;
        public final boolean contraband;
        public final boolean postAllocation;

        public Target(Target other) {
            this(other.commodityId, other.factionId, other.marketId, other.marketName,
                    other.percent, other.durationMonths, other.narrative, other.contraband, other.postAllocation);
        }

        public Target(String commodityId, String factionId, int percent, int durationMonths, int narrative) {
            this(commodityId, factionId, null, null, percent, durationMonths, narrative);
        }

        public Target(String commodityId, String factionId, String marketId, String marketName,
                      int percent, int durationMonths, int narrative) {
            this(commodityId, factionId, marketId, marketName, percent, durationMonths, narrative, false);
        }

        public Target(String commodityId, String factionId, String marketId, String marketName,
                      int percent, int durationMonths, int narrative, boolean contraband) {
            this(commodityId, factionId, marketId, marketName, percent, durationMonths, narrative, contraband, false);
        }

        public Target(String commodityId, String factionId, String marketId, String marketName,
                      int percent, int durationMonths, int narrative, boolean contraband, boolean postAllocation) {
            this.postAllocation = postAllocation;
            this.commodityId = commodityId;
            this.factionId = factionId;
            this.marketId = marketId;
            this.marketName = marketName;
            this.percent = percent;
            this.durationMonths = durationMonths;
            this.narrative = narrative;
            this.contraband = contraband;
        }

        public Scope getScope() {
            return marketId != null ? Scope.MARKET : factionId == null ? Scope.SECTOR : Scope.FACTION;
        }
    }
}
