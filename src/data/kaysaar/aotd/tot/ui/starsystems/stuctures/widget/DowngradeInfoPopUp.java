package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class DowngradeInfoPopUp extends BasePopUpDialog {
    StableStructureWidget widget;

    public DowngradeInfoPopUp(StableStructureWidget widget) {
        super(null);
        this.widget = widget;
        confirmButtonText = "Downgrade";
    }

    protected boolean canExecuteAction(AoTDStableStructureImpl impl) {
        return AoTDStableStructureConstruction.canDowngrade(impl);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI bt = super.generateConfirmButton(tooltip);
        bt.setButtonPressedSound("ui_downgrade_industry");
        bt.setShortcut(Keyboard.KEY_G, false);
        boolean can = canExecuteAction(widget.getStructure());
        if (bt.isEnabled() != can) bt.setEnabled(can);
        return bt;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        AoTDStableStructureImpl impl = widget.getStructure();
        tooltip.setParaInsigniaLarge();
        tooltip.addSpacer(0f).getPosition().inTL(0, 0);
        if (!AoTDStableStructureConstruction.canDowngrade(impl)) {
            tooltip.addPara("This structure cannot be downgraded while it is under construction or upgrading.", 3f);
            return;
        }

        AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(impl.getSpec().getDowngrade());
        CustomEntitySpecAPI specEntity = Global.getSettings().getCustomEntitySpec(spec.getId());
        int refund = AoTDStableStructureConstruction.getDowngradeRefund(impl);
        String refundPercent = Math.round(AoTDStableStructureConstruction.DOWNGRADE_REFUND_MULT * 100f) + "%";
        tooltip.addPara("Downgrading " + impl.getName() + " to " + specEntity.getDefaultName()
                        + " takes effect immediately and refunds %s of the construction cost or %s and will take effect immediately.",
                3f, Color.ORANGE, refundPercent, Misc.getDGSCredits(refund));
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (confirmButton != null) {
            boolean can = canExecuteAction(widget.getStructure());
            if (confirmButton.isEnabled() != can) confirmButton.setEnabled(can);
        }
    }

    @Override
    public void applyConfirmScript() {
        AoTDStableStructureConstruction.downgrade(widget.getStructure());
    }
}
