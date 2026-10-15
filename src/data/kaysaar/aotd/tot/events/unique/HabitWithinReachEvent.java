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
import java.util.Random;

public class HabitWithinReachEvent extends BaseTradeEvent {
    public static final String EVENT_ID = "aotd_habit_within_reach";
    public static final String EVENT_NAME = "A Habit Within Reach";
    public static final int[] DRUGS_PERCENTAGES = {40, 60, 80};

    public HabitWithinReachEvent() {
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
        return hasSectorSurplus("drugs");
    }

    private boolean hasSectorSurplus(String commodityId) {
        float supply = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        float demand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return supply > 0f && supply >= demand * 1.01f;
    }

    @Override
    public void configureEventBeforeFiringUp() {
        Random random = new Random();
        possibleTargetOfEvent = new Intel(DRUGS_PERCENTAGES[random.nextInt(DRUGS_PERCENTAGES.length)]);
    }

    @Override
    public boolean canFireAfterConfiguration() {
        return possibleTargetOfEvent instanceof Intel && canEvenFireEvent();
    }

    @Override
    public void executeEvent(AshlibBaseEventIntel intel) {
        Intel report = (Intel) intel;
        // TODO: Add drugs demand BEFORE industries at eligible markets throughout the Sector, using fixed baseline volumes.
        // Queue permanent demand activation before monthly allocation.
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
        public final int drugsDemandPercent;

        public Intel(int drugsDemandPercent) {
            super(EVENT_ID, EVENT_NAME, null);
            this.drugsDemandPercent = drugsDemandPercent;
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
            info.addPara("- Additional demand: %s", 3f, Color.ORANGE, "Drugs +" + drugsDemandPercent + "%");
            info.addPara("- %s", 3f, Misc.getGrayColor(), "Permanent");
        }

        private void addDescription(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Trade Bulletin", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);
            addIntroduction(tooltip);
            addMarketEffects(tooltip);
        }

        private void addIntroduction(TooltipMakerAPI tooltip) {
            tooltip.addPara("Cheap, readily available drugs have reached a much wider market. Occasional customers have become regular buyers, and distributors across the Sector are expanding to meet a lasting rise in consumption.", 10f);
        }

        private void addMarketEffects(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Permanent Market Effects", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 15f);
            tooltip.addPara("Drug demand increased on all worlds by: %s", 5f, Color.ORANGE, "+" + drugsDemandPercent + "%");
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
