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

public class SeasonOfConstructionEvent extends BaseTradeEvent {
    public static final String EVENT_ID = "aotd_season_of_construction";
    public static final String EVENT_NAME = "A Season of Construction";
    public static final int METALS_DEMAND_PERCENT = 40;
    public static final int RARE_METALS_DEMAND_PERCENT = 20;
    public static final int HEAVY_MACHINERY_DEMAND_PERCENT = 25;

    public SeasonOfConstructionEvent() {
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
        return hasSectorSurplus("metals")
                && hasSectorSurplus("rare_metals")
                && hasSectorSurplus("heavy_machinery");
    }

    private boolean hasSectorSurplus(String commodityId) {
        float supply = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        float demand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return supply > 0f && supply >= demand * 1.01f;
    }

    @Override
    public void configureEventBeforeFiringUp() {
        possibleTargetOfEvent = new Intel(METALS_DEMAND_PERCENT, RARE_METALS_DEMAND_PERCENT, HEAVY_MACHINERY_DEMAND_PERCENT);
    }

    @Override
    public boolean canFireAfterConfiguration() {
        return possibleTargetOfEvent instanceof Intel && canEvenFireEvent();
    }

    @Override
    public void executeEvent(AshlibBaseEventIntel intel) {
        Intel report = (Intel) intel;
        // TODO: Add metals +40%, rare metals +20%, and heavy machinery +25% demand AFTER industries.
        // Require >=90% fulfillment for EACH commodity at a colony before granting its reward.
        // Proposed reward: +5% accessibility for the following month; refresh, do not stack.
        // Queue permanent demand before allocation; manage earned monthly bonuses separately.
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
        public final int metalsDemandPercent;
        public final int rareMetalsDemandPercent;
        public final int heavyMachineryDemandPercent;

        public Intel(int metalsDemandPercent, int rareMetalsDemandPercent, int heavyMachineryDemandPercent) {
            super(EVENT_ID, EVENT_NAME, null);
            this.metalsDemandPercent = metalsDemandPercent;
            this.rareMetalsDemandPercent = rareMetalsDemandPercent;
            this.heavyMachineryDemandPercent = heavyMachineryDemandPercent;
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
            info.addPara("- Additional demand: %s", 3f, Color.ORANGE, "3 commodities");
            info.addPara("- %s", 3f, Misc.getGrayColor(), "Permanent");
        }

        private void addDescription(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Trade Bulletin", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);
            addIntroduction(tooltip);
            addMarketEffects(tooltip);
        }

        private void addIntroduction(TooltipMakerAPI tooltip) {
            tooltip.addPara("Abundant metals and machinery have made long-delayed developments affordable. New roads, utilities, and commercial districts are being commissioned across the Sector, establishing a lasting demand for construction materials.", 10f);
        }

        private void addMarketEffects(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Permanent Market Effects", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 15f);
            tooltip.addPara("Metals demand increased on all worlds by: %s", 5f, Color.ORANGE, "+" + metalsDemandPercent + "%");
            tooltip.addPara("Transplutonic metals demand increased on all worlds by: %s", 5f, Color.ORANGE, "+" + rareMetalsDemandPercent + "%");
            tooltip.addPara("Heavy machinery demand increased on all worlds by: %s", 5f, Color.ORANGE, "+" + heavyMachineryDemandPercent + "%");
            tooltip.addPara("Required deliveries for each construction commodity: %s", 5f, Color.ORANGE, "90%");
            tooltip.addPara("Accessibility bonus on worlds meeting construction orders: %s", 5f, Color.ORANGE, "+5%");
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
