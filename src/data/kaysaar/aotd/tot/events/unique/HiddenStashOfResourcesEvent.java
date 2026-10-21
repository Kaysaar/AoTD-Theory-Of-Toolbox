package data.kaysaar.aotd.tot.events.unique;

import ashlib.data.plugins.events.AshlibBaseEvent;
import ashlib.data.plugins.events.AshlibBaseEventIntel;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityMarketData;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;

public class HiddenStashOfResourcesEvent extends AshlibBaseEvent {
    int am = 40;
    public HiddenStashOfResourcesEvent() {
        super("aotd_hidden_stash_event");
    }
    @Override
    public String getEventName() {
        return "Hidden Stash";
    }

    @Override
    public boolean canEvenFireEvent() {
        return !Misc.getPlayerMarkets(false).isEmpty();
    }

    @Override
    public boolean shouldFireImmediately() {
        return true;
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
    public void createTooltipForEvent(TooltipMakerAPI tooltip) {
        tooltip.addPara(
                "During the establishment of our first colony, construction crews uncovered a hidden " +
                        "stockpile of Spaceframe Modules dating back to the Domain era. " +
                        "These supplies should greatly aid the development of our early star system infrastructure.",
                5f
        );
        tooltip.addSectionHeading("Effects of Event", Alignment.MID, 5f);

        tooltip.addPara(
                "The recovered stockpile provides your faction with an additional 40 cargo units " +
                        "of passive Spaceframe Modules production for the next 5 cycles.",
                10f,
                Misc.getPositiveHighlightColor(),
                am+" cargo units",
                "5 years"
        );
    }
    @Override
    public AshlibBaseEventIntel createIntelPlugin() {
        return null;
    }

    @Override
    public float getImmediateEventDelayDays() {
        return 3f;
    }

    @Override
    public float getImageCropY() {
        return 0F;
    }

    @Override
    public String getImageName() {
        return Global.getSettings().getSpriteName("illustrations", "salvor_ruins");
    }

    @Override
    public boolean shouldPublishIntel(AshlibBaseEventIntel intel) {
        return false;
    }

    @Override
    public void executeEvent() {
        AoTDCommodityOnMarket.getComMarketInstanceSave(Misc.getPlayerMarkets(false).get(0),"aotd_spaceframe_modules").getSupplyDemandData().getAdditionalProduction().addTemporaryModFlatExact(1825,"aotd_hidden_stash_event","Hidden Stash event",am);
    }
}
