package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class AoTDAPCImpl extends AoTDStableStructureImpl {

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        tooltip.addPara(
                "Reduces demand for Ore, Rare Ore, and Volatiles by %s for same-faction refining industries in the star system.",
                5f,
                Misc.getHighlightColor(),
                "25%"
        );
    }
}
