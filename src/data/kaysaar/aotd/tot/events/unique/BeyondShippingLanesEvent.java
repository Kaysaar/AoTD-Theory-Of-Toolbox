package data.kaysaar.aotd.tot.events.unique;

import ashlib.data.plugins.events.AshlibBaseEventIntel;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.events.BaseTradeEvent;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;

import java.awt.Color;

public class BeyondShippingLanesEvent extends BaseTradeEvent {
    public static final String EVENT_ID = "aotd_beyond_shipping_lanes";
    public static final String EVENT_NAME = "Beyond the Shipping Lanes";
    public static final int FUEL_DEMAND_PERCENT = 30;
    public static final int SUPPLIES_DEMAND_PERCENT = 30;

    public BeyondShippingLanesEvent() {
        super(EVENT_ID);
    }

    @Override
    public void configureEvent() {
        maxAmount = 1;
    }

    @Override
    public boolean isMinorEvent() {
        return false;
    }

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean canEvenFireEvent() {
        if (Global.getSector() == null || AoTDEconomy.runningPrePlayerEconomy) return false;
        return hasSectorSurplus("fuel")
                && hasSectorSurplus("supplies");
    }

    private boolean hasSectorSurplus(String commodityId) {
        float supply = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        float demand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return supply > 0f && supply >= demand * 1.01f;
    }

    @Override
    public void configureEventBeforeFiringUp() {
        possibleTargetOfEvent = new Intel(FUEL_DEMAND_PERCENT, SUPPLIES_DEMAND_PERCENT);
    }

    @Override
    public boolean canFireAfterConfiguration() {
        return possibleTargetOfEvent instanceof Intel && canEvenFireEvent();
    }

    @Override
    public void executeEvent(AshlibBaseEventIntel intel) {
        Intel report = (Intel) intel;
        // TODO: Add fuel and supplies demand AFTER industries at eligible markets throughout the Sector.
        // Track each commodity/source separately. Require >=90% fulfillment of BOTH for the monthly reward.
        // Proposed reward: +5% faction colony income for the next month; refresh, do not stack.
        // Queue permanent demand before monthly allocation; manage earned monthly bonuses separately.
        // Apply across the entire Sector; use the configured values stored on report.
        // Use report.getOccurrenceId() for unique modifier IDs if needed.
        // Apply permanent modifiers without temporary durations; this event fires once per campaign.
    }

    @Override
    public AshlibBaseEventIntel createIntelPlugin() {
        return possibleTargetOfEvent instanceof Intel ? (Intel) possibleTargetOfEvent : null;
    }

    @Override
    public void createTitleForEvent(TooltipMakerAPI tooltip) {
        tooltip.addTitle(EVENT_NAME, Misc.getButtonTextColor());
    }

    @Override
    public void createTooltipForEvent(TooltipMakerAPI tooltip) {
        if (possibleTargetOfEvent instanceof Intel report) report.addDescription(tooltip);
    }

    // Presentation only. Scheduling and confirmation remain in Ashlib's existing manager.
    public static class Intel extends AshlibBaseEventIntel {
        public final int fuelDemandPercent;
        public final int suppliesDemandPercent;

        public Intel(int fuelDemandPercent, int suppliesDemandPercent) {
            super(EVENT_ID, EVENT_NAME, null);
            this.fuelDemandPercent = fuelDemandPercent;
            this.suppliesDemandPercent = suppliesDemandPercent;
        }

        @Override
        public String getIcon() {
            return Global.getSettings().getSpriteName("illustrations", "crew_leaving");
        }

        @Override
        public IntelSortTier getSortTier() {
            return IntelSortTier.TIER_1;
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(EVENT_NAME, Misc.getButtonTextColor(), 0f);
            info.addPara("- %s", 3f, Misc.getButtonTextColor(), "Sector-wide");
            info.addPara("- Additional demand: %s", 3f, Color.ORANGE, "2 commodities");
            info.addPara("- %s", 3f, Misc.getGrayColor(), "Permanent");
        }

        private void addDescription(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Trade Bulletin", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);
            addIntroduction(tooltip);
            addMarketEffects(tooltip);
        }

        private void addIntroduction(TooltipMakerAPI tooltip) {
            tooltip.addPara("Abundant fuel and supplies have brought distant expeditions within reach. Factions across the Sector are fitting out survey fleets, salvage missions, and prospecting ventures, creating a lasting need for expedition cargo.", 10f);
        }

        private void addMarketEffects(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Permanent Market Effects", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 15f);
            tooltip.addPara("Fuel demand increased across the Sector by: %s", 5f, Color.ORANGE, "+" + fuelDemandPercent + "%");
            tooltip.addPara("Supplies demand increased across the Sector by: %s", 5f, Color.ORANGE, "+" + suppliesDemandPercent + "%");
            tooltip.addPara("Required deliveries for both expedition commodities: %s", 5f, Color.ORANGE, "90%");
            tooltip.addPara("Colony income bonus for factions meeting expedition orders: %s", 5f, Color.ORANGE, "+5%");
            tooltip.addPara("Delivery bonuses are awarded for the following month and renewed only while the required orders are supplied.", Misc.getGrayColor(), 8f);
        }

        @Override
        protected void createDescription(TooltipMakerAPI tooltip, float width) {
            tooltip.addSectionHeading("Trade Bulletin", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);
            if (width >= 600f) {
                TooltipMakerAPI overview = tooltip.beginImageWithText(getIcon(), 135f, width, true);
                addIntroduction(overview);
                tooltip.addImageWithText(15f);
            } else {
                float imageWidth = Math.min(width, 216f);
                tooltip.addImage(getIcon(), imageWidth, imageWidth * 300f / 480f, 10f);
                addIntroduction(tooltip);
            }
            addMarketEffects(tooltip);
        }
    }
}
