package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

import java.awt.Color;
import org.lwjgl.input.Keyboard;

public class StopConstructionInfoPopUp extends BasePopUpDialog {
    StableStructureWidget widget;

    public StopConstructionInfoPopUp(StableStructureWidget widget) {
        super(null);
        this.widget = widget;
        confirmButtonText = "Stop Construction";
    }

    public boolean canCancel(AoTDStableStructureImpl impl) {
        return AoTDStableStructureConstruction.canCancelConstruction(impl);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI bt = tooltip.addButton(confirmButtonText, "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, 230f, 25f, 0f);
        bt.setShortcut(Keyboard.KEY_G, true);
        bt.setButtonPressedSound("ui_downgrade_industry");
        bt.setEnabled(canCancel(widget.getStructure()));
        confirmButton = bt;
        return bt;
    }

    @Override
    public void createConfirmAndCancelSection(CustomPanelAPI mainPanel) {
        float totalWidth = 230f + buttonConfirmWidth + 10f;
        TooltipMakerAPI tooltip = mainPanel.createUIElement(totalWidth, 25f, false);
        tooltip.setButtonFontOrbitron20();
        generateConfirmButton(tooltip);
        generateCancelButton(tooltip);
        if (confirmButton != null) confirmButton.getPosition().inTL(0f, 0f);
        if (cancelButton != null) cancelButton.getPosition().inTL(235f, 0f);
        mainPanel.addUIElement(tooltip).inTL(mainPanel.getPosition().getWidth() - totalWidth - 10f, goalSizeY - 40f);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        AoTDStableStructureImpl impl = widget.getStructure();
        tooltip.setParaInsigniaLarge();
        tooltip.addSpacer(0f).getPosition().inTL(0, 0);
        if (!canCancel(impl)) {
            tooltip.addPara("This construction can no longer be canceled.", 3f);
            return;
        }

        String name = impl.getName();
        if (impl.isUpgrading() && impl.getConstructionTargetId() != null
                && Global.getSettings().getCustomEntitySpec(impl.getConstructionTargetId()) != null) {
            name = Global.getSettings().getCustomEntitySpec(impl.getConstructionTargetId()).getDefaultName();
        }
        String refundPercent = Math.round(AoTDStableStructureConstruction.getCancelRefundMult(impl) * 100f) + "%";
        long refund = AoTDStableStructureConstruction.getCancelRefund(impl);
        tooltip.addPara("Canceling the in-progress " + (impl.isUpgrading() ? "upgrade" : "construction") + " of " + name
                        + " will refund you %s of the current stage's construction cost, or %s, and will take effect immediately.",
                3f, Color.ORANGE, refundPercent, Misc.getDGSCredits(refund));
        if (impl.ultimateUpgradeId != null && !impl.ultimateUpgradeId.equals(impl.getConstructionTargetId())) {
            tooltip.addPara("Any queued upgrades will also be canceled. No credits have been charged for future stages.", 5f);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (confirmButton != null) {
            boolean can = canCancel(widget.getStructure());
            if (confirmButton.isEnabled() != can) confirmButton.setEnabled(can);
            if (!can) confirmButton.setChecked(false);
        }
    }

    @Override
    public void applyConfirmScript() {
        AoTDStableStructureImpl impl = widget.getStructure();
        if (!canCancel(impl)) return;
        if (impl.isUpgrading()) AoTDStableStructureConstruction.cancelUpgrade(impl);
        else AoTDStableStructureConstruction.cancelConstruction(impl);
    }
}
