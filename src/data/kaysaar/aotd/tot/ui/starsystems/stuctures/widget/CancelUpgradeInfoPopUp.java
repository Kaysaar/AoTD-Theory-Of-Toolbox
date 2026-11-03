package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class CancelUpgradeInfoPopUp extends StopConstructionInfoPopUp {
    public CancelUpgradeInfoPopUp(StableStructureWidget widget) {
        super(widget);
        confirmButtonText = "Cancel Upgrade";
    }

    @Override
    public boolean canCancel(AoTDStableStructureImpl impl) {
        return AoTDStableStructureConstruction.canCancelUpgrade(impl);
    }
}
