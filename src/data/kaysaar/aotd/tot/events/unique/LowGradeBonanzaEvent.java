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

public class LowGradeBonanzaEvent extends BaseTradeEvent {
    public static final String EVENT_ID = "aotd_low_grade_bonanza";
    public static final String EVENT_NAME = "The Low-Grade Bonanza";
    public static final int[] ORE_PERCENTAGES = {25, 50};

    public LowGradeBonanzaEvent() {
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
        return hasSectorSurplus("ore");
    }

    private boolean hasSectorSurplus(String commodityId) {
        float supply = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        float demand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return supply > 0f && supply >= demand * 1.01f;
    }

    @Override
    public void configureEventBeforeFiringUp() {
        Random random = new Random();
        possibleTargetOfEvent = new Intel(ORE_PERCENTAGES[random.nextInt(ORE_PERCENTAGES.length)]);
    }

    @Override
    public boolean canFireAfterConfiguration() {
        return possibleTargetOfEvent instanceof Intel && canEvenFireEvent();
    }

    @Override
    public void executeEvent(AshlibBaseEventIntel intel) {
        Intel report = (Intel) intel;
        // TODO: Modify ore INPUT REQUIREMENTS of the selected metal-producing industries; preserve nominal metal output.
        // Do not substitute an unrelated before-industry procurement order for the industrial input change.
        // Apply permanent input modifiers before monthly allocation.
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
        public final int oreDemandPercent;

        public Intel(int oreDemandPercent) {
            super(EVENT_ID, EVENT_NAME, null);
            this.oreDemandPercent = oreDemandPercent;
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
            info.addPara("- Additional demand: %s", 3f, Color.ORANGE, "Ore +" + oreDemandPercent + "%");
            info.addPara("- %s", 3f, Misc.getGrayColor(), "Permanent");
        }

        private void addDescription(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Trade Bulletin", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);
            addIntroduction(tooltip);
            addMarketEffects(tooltip);
        }

        private void addIntroduction(TooltipMakerAPI tooltip) {
            tooltip.addPara("Record ore shipments have concealed a decline in quality. Smelters across the Sector must now process larger quantities of low-purity ore to maintain their previous metal output.", 10f);
        }

        private void addMarketEffects(TooltipMakerAPI tooltip) {
            tooltip.addSectionHeading("Permanent Market Effects", Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 15f);
            tooltip.addPara("Ore required by metal-producing industries increased by: %s", 5f, Color.ORANGE, "+" + oreDemandPercent + "%");
            tooltip.addPara("Nominal metal output: %s", 5f, Color.ORANGE, "Unchanged");
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
