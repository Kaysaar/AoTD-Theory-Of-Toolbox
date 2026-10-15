package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class NavBuoyImpl extends AoTDStableStructureImpl {

    protected float getBurnBonus() {
        return NavBuoyEntityPlugin.NAV_BONUS;
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        int bonus = Math.round(getBurnBonus());
        tooltip.addPara("%s burn level for all same-faction fleets in the star system", 5f,
                Misc.getHighlightColor(), "+" + bonus);
    }
}
