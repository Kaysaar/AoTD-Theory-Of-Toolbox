package data.kaysaar.aotd.tot.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.*;

public class SindrianRefineryMastery extends BaseMarketConditionPlugin {

    float mult = 1.8f;
    @Override
    public void apply(String id) {
        super.apply(id);
        market.getIndustries().forEach(x->x.getSupply(Commodities.FUEL).getQuantity().modifyMult(id,mult));
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getIndustries().forEach(x->x.getSupply(Commodities.FUEL).getQuantity().unmodifyMult(id));

    }



    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        tooltip.addPara("This market has %s multiplier towards fuel production",15f, Color.ORANGE,Float.toString(mult)+"x");
    }

    @Override
    public String getIconName() {
        return Global.getSector().getFaction(Factions.DIKTAT).getCrest();
    }
}
