package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class DowngradeInfoPopUp extends BasePopUpDialog {
    StableStructureWidget widget;
    public static int daysToDeconstructPerCredit = 5000;
    public DowngradeInfoPopUp(StableStructureWidget widget) {
        super(null);
        this.widget = widget;
        confirmButtonText = "Downgrade";
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI bt = super.generateConfirmButton(tooltip);
        bt.setButtonPressedSound("ui_downgrade_industry");
        bt.setShortcut(Keyboard.KEY_G,false);
        return bt;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(widget.getStructure().getSpec().getDowngrade());
        CustomEntitySpecAPI specEntity = Global.getSettings().getCustomEntitySpec(spec.getId());
        tooltip.setParaInsigniaLarge();
        tooltip.addSpacer(0f).getPosition().inTL(0,0);
        tooltip.addPara("Downgrading "+widget.getStructure().getName()+" to "+specEntity.getDefaultName()+" will refund you %s of the construction cost, or %s, and will take effect within %s",3f, Color.ORANGE,"40%",
                Misc.getDGSCredits(spec.getCreditCost()),
                AshMisc.convertDaysToString(spec.getCreditCost()/daysToDeconstructPerCredit));
    }
}
