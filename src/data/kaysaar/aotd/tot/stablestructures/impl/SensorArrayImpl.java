package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.impl.campaign.SensorArrayEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class SensorArrayImpl extends AoTDStableStructureImpl {

    protected float getSensorRangeBonus() {
        return SensorArrayEntityPlugin.SENSOR_BONUS;
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        int bonus = Math.round(getSensorRangeBonus());
        tooltip.addPara("%s sensor range for all same-faction fleets in the star system", 5f,
                Misc.getHighlightColor(), "+" + bonus);
    }
}
