package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class CommRelayImpl extends AoTDStableStructureImpl {

    protected float getStabilityBonus() {
        return CommRelayCondition.COMM_RELAY_BONUS;
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        int bonus = Math.abs(Math.round(getStabilityBonus()));
        tooltip.addPara("%s stability for same-faction colonies in the star system", 5f,
                Misc.getHighlightColor(), "+" + bonus);
    }
}
