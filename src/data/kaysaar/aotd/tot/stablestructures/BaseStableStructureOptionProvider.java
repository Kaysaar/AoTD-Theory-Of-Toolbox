package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget.StableStructureWidget;

import java.util.List;

public class BaseStableStructureOptionProvider implements StableStructureOptionProvider {

    @Override
    public List<StructureOptionData> getStructureOptions(AoTDStableStructureImpl structure) {
        return null;
    }

    @Override
    public void optionSelected(StructureOptionData opt, StableStructureWidget widget) {

    }
    @Override
    public void createTooltip(StructureOptionData opt, TooltipMakerAPI tooltip, float width) {

    }
}
