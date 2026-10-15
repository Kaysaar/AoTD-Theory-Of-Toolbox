package data.kaysaar.aotd.tot.events;

import ashlib.data.plugins.events.AshlibBaseEventIntel;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AoTDTradeDemandIntel extends AshlibBaseEventIntel {
    private final float durationDays;
    private final AoTDTradeDemandOrder order;

    public AoTDTradeDemandIntel(AoTDTradeDemandEvent.Target data) {
        super(AoTDTradeDemandEvent.getEventTypeId(data),
                AoTDTradeDemandEvent.getEventName(data), new AoTDTradeDemandEvent.Target(data));
        durationDays = data.durationMonths * TradeVolatilitySettings.daysPerMonth;
        order = new AoTDTradeDemandOrder(data);
    }

    public AoTDTradeDemandOrder getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return getCommodityName() + " - " + super.getName();
    }

    @Override
    public IntelSortTier getSortTier() {
        switch (getEventData().getScope()) {
            case SECTOR: return IntelSortTier.TIER_1;
            case FACTION: return IntelSortTier.TIER_2;
            default: return IntelSortTier.TIER_3;
        }
    }

    @Override
    public boolean shouldRemoveIntel() {
        return super.shouldRemoveIntel();
    }

    @Override
    public AoTDTradeDemandEvent.Target getEventData() {
        return (AoTDTradeDemandEvent.Target) super.getEventData();
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getCommoditySpec(getEventData().commodityId).getIconName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI faction = AoTDTradeDemandEvent.getAffectedFaction(getEventData());
        return faction == null ? super.getFactionForUIColors() : faction;
    }

    @Override
    protected Color getEventColor() {
        FactionAPI faction = AoTDTradeDemandEvent.getAffectedFaction(getEventData());
        return faction == null ? Misc.getButtonTextColor() : faction.getBaseUIColor();
    }

    @Override
    protected Color getEventBackgroundColor() {
        FactionAPI faction = AoTDTradeDemandEvent.getAffectedFaction(getEventData());
        return faction == null ? Misc.getDarkPlayerColor() : faction.getDarkUIColor();
    }

    @Override
    protected String getEventHeading() {
        switch (getEventData().getScope()) {
            case MARKET: return "Local Trade Bulletin";
            case FACTION: return "Faction Trade Bulletin";
            default: return "Sector Trade Bulletin";
        }
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getEventColor(), 0f);
        AoTDTradeDemandEvent.Target data = getEventData();
        info.addPara("- %s", 3f, getEventColor(), AoTDTradeDemandEvent.getScopeName(data));
        info.addPara("- Additional demand: %s", 3f, Color.ORANGE, getBuyingTarget());
        info.addPara("- %s", 3f, Misc.getGrayColor(), getScheduleSummary());
    }

    @Override
    protected void createDescription(TooltipMakerAPI tooltip, float width) {
        createOverview(tooltip, width);
        createOrderSummary(tooltip, width);
        createAffectedMarkets(tooltip);

        addHeading(tooltip, "Market Outlook", 18f);
        tooltip.addPara(AoTDTradeDemandEvent.getNarrative(getEventData()), 10f);

        addHeading(tooltip, "Procurement Schedule", 18f);
        if (getEventData().postAllocation && isActive()) {
            tooltip.setParaFontOrbitron();
            tooltip.addPara("Unscheduled buying rush", Color.ORANGE, 10f);
            tooltip.setParaFontDefault();
            tooltip.addPara("Buying began: %s", 8f, Color.ORANGE, order.startedOn);
            tooltip.addPara("The rush began after this month's cargo was assigned. These extra orders were not covered by that allocation, creating an immediate need for deliveries.", 8f);
            tooltip.addPara("The orders take priority over local industry and end before the next monthly allocation. Existing stocks and player deliveries can ease the shortage.", 8f);
            tooltip.addPara("The figures above record the original buying rush. Live shortages and prices can change as cargo arrives.", Misc.getGrayColor(), 8f);
        } else if (order != null && order.status == AoTDTradeDemandOrder.Status.PENDING) {
            tooltip.setParaFontOrbitron();
            tooltip.addPara("Orders announced", Color.ORANGE, 10f);
            tooltip.setParaFontDefault();
            tooltip.addPara("Procurement begins with the next monthly trade allocation. Current deliveries and industry requirements remain unchanged until then.", 8f);
        } else if (order != null && order.status == AoTDTradeDemandOrder.Status.CANCELLED) {
            tooltip.setParaFontOrbitron();
            tooltip.addPara("Orders withdrawn", Misc.getGrayColor(), 10f);
            tooltip.setParaFontDefault();
            tooltip.addPara("The orders could not begin because no eligible buyer remained when the monthly allocation was prepared.", Misc.getGrayColor(), 8f);
        } else if (isActive()) {
            tooltip.setParaFontOrbitron();
            tooltip.addPara("Procurement window open", Misc.getPositiveHighlightColor(), 10f);
            tooltip.setParaFontDefault();
            if (order != null) {
                tooltip.addPara("Ordering began: %s", 8f, Color.ORANGE, order.startedOn);
                tooltip.addPara("Ordering periods remaining: %s", 5f, Color.ORANGE,
                        order.monthsRemaining + (order.monthsRemaining == 1 ? " month" : " months"));
                tooltip.addPara("Includes the current allocation period. Orders end before the following month's cargo is assigned.", Misc.getGrayColor(), 5f);
            } else {
                tooltip.addPara("Expected remaining time: %s", 8f, Color.ORANGE, getRemainingDays() + " days");
            }
            tooltip.addPara("These orders receive priority over local industry. Incoming cargo supplies them first; any remainder is available to local producers.", 10f);
            tooltip.addPara("Reliable deliveries may command a premium, but established routes and competing suppliers will determine who secures the business.",
                    Misc.getGrayColor(), 8f);
        } else {
            tooltip.setParaFontOrbitron();
            tooltip.addPara("Ordering period completed", Misc.getGrayColor(), 10f);
            tooltip.setParaFontDefault();
            tooltip.addPara("The projected ordering window has concluded. The figures above describe the original orders and remain here as a record of that period of trade.",
                    Misc.getGrayColor(), 8f);
        }
        tooltip.addSpacer(12f);
    }

    private void createOverview(TooltipMakerAPI tooltip, float width) {
        String illustration = Global.getSettings().getSpriteName("illustrations", "crew_leaving");
        if (width >= 600f) {
            TooltipMakerAPI overview = tooltip.beginImageWithText(illustration, 135f, width, true);
            createOverviewText(overview);
            tooltip.addImageWithText(15f);
        } else {
            // Keep the illustration's 480:300 aspect ratio on smaller intel panels.
            float imageWidth = Math.min(width, 480f);
            tooltip.addImage(illustration, imageWidth, imageWidth * 300f / 480f, 15f);
            tooltip.addSpacer(12f);
            createOverviewText(tooltip);
        }
    }

    private void createOverviewText(TooltipMakerAPI tooltip) {
        AoTDTradeDemandEvent.Target data = getEventData();
        tooltip.setParaFontOrbitron();
        tooltip.addPara("Commodity: " + getCommodityName(), Color.ORANGE, 0f);
        tooltip.setParaFontDefault();
        tooltip.addPara(AoTDTradeDemandEvent.getScopeLabel(data), Misc.getGrayColor(), 5f);
        String scope = data.getScope() == AoTDTradeDemandEvent.Scope.SECTOR ? "Coverage: %s"
                : data.marketId == null ? "Affected faction: %s" : "Affected colony: %s";
        tooltip.addPara(scope,
                10f, getEventColor(), AoTDTradeDemandEvent.getScopeName(data));
        if (data.marketId != null) {
            FactionAPI faction = AoTDTradeDemandEvent.getAffectedFaction(data);
            tooltip.addPara("Faction at time of report: %s", 5f, getEventColor(),
                    faction == null ? data.factionId : faction.getDisplayName());
        }
        tooltip.addPara(data.postAllocation ? "A sudden local buying rush has outpaced the shipments already assigned to this colony."
                : data.contraband ? "Discreet buyers are competing to secure supplies through underground trade networks."
                : "Procurement offices have announced additional orders for suppliers.", Misc.getGrayColor(), 10f);
    }

    private void createOrderSummary(TooltipMakerAPI tooltip, float width) {
        AoTDTradeDemandEvent.Target data = getEventData();
        String change = getBuyingTarget();
        String duration = data.postAllocation ? "Until next allocation"
                : data.durationMonths + (data.durationMonths == 1 ? " month" : " months");
        addHeading(tooltip, "Order Summary", 18f);
        if (width >= 420f) {
            float tableWidth = width-10;
            tooltip.beginTable(getEventColor(), getEventBackgroundColor(), getEventColor(), 32f,
                    (data.contraband || data.postAllocation) ? "Buying target" : "Additional orders (approx.)", tableWidth * 0.56f,
                    "Original duration", tableWidth * 0.44f);
            tooltip.addRow(Misc.getPositiveHighlightColor(), change, Color.ORANGE, duration);
            tooltip.addTable("", 0, 10f);
        } else {
            tooltip.addPara((data.contraband || data.postAllocation) ? "Buying target: %s" : "Additional orders: approximately %s",
                    10f, Misc.getPositiveHighlightColor(), change);
            tooltip.addPara("Original duration: %s", 5f, Color.ORANGE, duration);
        }
        if (data.postAllocation) {
            tooltip.addPara("Buyers absorb the colony's allocated surplus, then request an extra %s of its larger monthly supply or demand. Quantities are fixed when the rush begins.",
                    8f, Color.ORANGE, data.percent + "%");
            if (order != null) {
                tooltip.addPara("Additional orders placed: %s", 8f, Color.ORANGE,
                        String.format(Locale.US, "%,d units", order.additionalRawDemand));
            }
        } else if (data.contraband) {
            tooltip.addPara("Buyers intend to absorb the surplus in the affected markets, then seek a further %s reserve measured against the larger of monthly supply and demand.",
                    8f, Color.ORANGE, data.percent + "%");
            tooltip.addPara("Orders are fixed when buying begins. Incoming deliveries and Scavenger Guild shipments may ease the shortage; the buying target is not a guaranteed market deficit.",
                    Misc.getGrayColor(), 8f);
            if (order != null && order.additionalRawDemand > 0L) {
                tooltip.addPara("Additional monthly orders placed: %s", 8f, Color.ORANGE,
                        String.format(Locale.US, "%,d units", order.additionalRawDemand));
            }
        } else {
            tooltip.addPara("Order volumes are set from industrial demand when procurement begins and remain fixed for the stated period.",
                    Misc.getGrayColor(), 8f);
        }
    }

    private String getBuyingTarget() {
        return getEventData().postAllocation ? "Local surplus + " + getEventData().percent + "%"
                : getEventData().contraband ? "Surplus + " + getEventData().percent + "%"
                : "+" + getEventData().percent + "%";
    }

    private void addHeading(TooltipMakerAPI tooltip, String title, float pad) {
        tooltip.addSectionHeading(title, getEventColor(), getEventBackgroundColor(), Alignment.MID, pad);
    }

    private void createAffectedMarkets(TooltipMakerAPI tooltip) {
        if (getEventData().getScope() != AoTDTradeDemandEvent.Scope.FACTION) return;

        addHeading(tooltip, isPending() ? "Expected Markets" : "Affected Markets", 18f);
        Map<String, String> markets = getAffectedMarkets();
        if (markets == null) {
            tooltip.addPara("Individual destinations were not recorded in this earlier report.", Misc.getGrayColor(), 10f);
            return;
        }
        if (markets.isEmpty()) {
            String text = isPending() ? "No eligible buyers are currently accepting these orders."
                    : order != null && order.status == AoTDTradeDemandOrder.Status.CANCELLED
                    ? "The orders were withdrawn before deliveries began."
                    : "No destinations for this order remain in the current trade records.";
            tooltip.addPara(text, Misc.getGrayColor(), 10f);
            return;
        }

        tooltip.addPara(isPending() ? "%s orders are expected at %s."
                        : "%s orders were placed at %s.",
                10f, Color.ORANGE, getCommodityName(),
                markets.size() + (markets.size() == 1 ? " market" : " markets"));
        List<String> names = new ArrayList<>(markets.values());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            // Paragraphs wrap long colony names within the existing scrollable report.
            tooltip.addPara("- %s", 5f, getEventColor(), name);
        }
        tooltip.addPara(isPending()
                        ? "Destinations will be confirmed when ordering begins. Changes in ownership or local demand may alter this list."
                        : "These orders belong to the listed markets. Later changes in ownership do not redirect them to other colonies.",
                Misc.getGrayColor(), 8f);
    }

    private boolean isPending() {
        return order != null && order.status == AoTDTradeDemandOrder.Status.PENDING;
    }

    private Map<String, String> getAffectedMarkets() {
        if (isPending()) return AoTDTradeDemandEvent.getExpectedMarkets(getEventData());
        if (order == null) return null;
        if (order.affectedMarkets != null) return order.affectedMarkets;
        if (order.status == AoTDTradeDemandOrder.Status.CANCELLED) return new LinkedHashMap<>();
        if (order.status != AoTDTradeDemandOrder.Status.ACTIVE) return null;

        // Older active orders did not save destinations. Their surviving source mods
        // can still identify buyers; never infer an expired report from a later occurrence.
        AoTDTradeDemandEvent.Target data = getEventData();
        String source = AoTDTradeDemandEvent.getSourceId(data);
        LinkedHashMap<String, String> markets = new LinkedHashMap<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                if (!commodity.getId().equals(data.commodityId)
                        || !(commodity instanceof AoTDCommodityOnMarket aotd)) continue;
                AoTDSupplyDemandData supplyDemand = aotd.getAoTDAvailableStat().getSupplyDemandDataIfInitialized();
                if (supplyDemand != null && supplyDemand.additionalDemand.hasSource(source)) {
                    markets.put(market.getId(), market.getName());
                }
            }
        }
        return markets;
    }

    private String getCommodityName() {
        return Global.getSettings().getCommoditySpec(getEventData().commodityId).getName();
    }

    private int getRemainingDays() {
        // Records saved by the previous daily-timer implementation retain their old estimate.
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(getOccurredAt());
        return (int) Math.ceil(Math.max(0f, durationDays - elapsed));
    }

    private boolean isActive() {
        return order == null ? getRemainingDays() > 0 : order.status == AoTDTradeDemandOrder.Status.ACTIVE;
    }

    private String getScheduleSummary() {
        if (order == null) {
            int remaining = getRemainingDays();
            return remaining > 0 ? "Approx. " + remaining + " days remaining" : "Scheduled period elapsed";
        }
        switch (order.status) {
            case PENDING: return "Begins with the next monthly allocation";
            case ACTIVE: if (getEventData().postAllocation) return "Active until next allocation";
                return order.monthsRemaining + (order.monthsRemaining == 1
                    ? " monthly period remaining" : " monthly periods remaining");
            case CANCELLED: return "Orders withdrawn";
            default: return "Ordering period completed";
        }
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Trade");
        if (getEventData().contraband) tags.add("Contraband");
        if (getEventData().postAllocation) tags.add("Local shortage");
        tags.add(AoTDTradeDemandEvent.getScopeLabel(getEventData()));
        return tags;
    }
}
