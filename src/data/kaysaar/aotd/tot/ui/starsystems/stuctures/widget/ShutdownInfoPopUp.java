package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

import java.awt.Color;

public class ShutdownInfoPopUp extends DowngradeInfoPopUp {
    public ShutdownInfoPopUp(StableStructureWidget widget) {
        super(widget);
        confirmButtonText = "Shut down";
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI bt = super.generateConfirmButton(tooltip);
        bt.setEnabled(AoTDStableStructureConstruction.canShutDown(widget.getStructure())
                && !AoTDStableStructureConstruction.canCancelConstruction(widget.getStructure()));
        return bt;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        AoTDStableStructureImpl impl = widget.getStructure();
        tooltip.setParaInsigniaLarge();
        tooltip.addSpacer(0f).getPosition().inTL(0, 0);
        if (!AoTDStableStructureConstruction.canShutDown(impl)) {
            tooltip.addPara("This structure cannot be shut down while it is under construction or upgrading.", 3f);
            return;
        }

        long refund = AoTDStableStructureConstruction.getShutdownRefund(impl);
        String refundPercent = Math.round(AoTDStableStructureConstruction.SHUTDOWN_REFUND_MULT * 100f) + "%";
        if (impl.isUpgrading()) {
            long completedRefund = Math.max(0L, Math.round(impl.getCumulativeCreditCost()
                    * (double) AoTDStableStructureConstruction.SHUTDOWN_REFUND_MULT));
            long upgradeRefund = AoTDStableStructureConstruction.getCancelRefund(impl);
            String upgradePercent = Math.round(AoTDStableStructureConstruction.getCancelRefundMult(impl) * 100f) + "%";
            tooltip.addPara("Shutting down " + impl.getName() + " will refund %s of its completed construction cost (%s), plus %s of the current upgrade cost (%s). Total refund: %s. This takes effect immediately.",
                    3f, Color.ORANGE, refundPercent, Misc.getDGSCredits(completedRefund),
                    upgradePercent, Misc.getDGSCredits(upgradeRefund), Misc.getDGSCredits(refund));
            tooltip.addPara("Any queued upgrades will also be canceled.", 5f);
        } else {
            tooltip.addPara("Shutting down " + impl.getName() + " will refund you %s of the total construction cost, or %s, and will take effect immediately.",
                    3f, Color.ORANGE, refundPercent, Misc.getDGSCredits(refund));
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (confirmButton != null) {
            boolean can = AoTDStableStructureConstruction.canShutDown(widget.getStructure())
                    && !AoTDStableStructureConstruction.canCancelConstruction(widget.getStructure());
            if (confirmButton.isEnabled() != can) confirmButton.setEnabled(can);
        }
    }

    @Override
    public void applyConfirmScript() {
        AoTDStableStructureImpl impl = widget.getStructure();
        if (AoTDStableStructureConstruction.canShutDown(impl)
                && !AoTDStableStructureConstruction.canCancelConstruction(impl)) {
            AoTDStableStructureConstruction.shutDown(impl);
        }
    }
}
