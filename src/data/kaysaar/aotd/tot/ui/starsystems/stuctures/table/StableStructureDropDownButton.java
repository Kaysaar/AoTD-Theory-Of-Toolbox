package data.kaysaar.aotd.tot.ui.starsystems.stuctures.table;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.ui.warehouses.components.WarehouseCustomButton;

import java.util.ArrayList;

public class StableStructureDropDownButton extends DropDownButton {
    AoTDStableStructureSpec spec;
    StarSystemAPI systemAPI;
    public StableStructureDropDownButton(UITableImpl tableOfReference, float width, float height, float maxWidth, float maxHeight, AoTDStableStructureSpec spec, StarSystemAPI systemAPI) {
        super(tableOfReference, width, height, maxWidth, maxHeight, false);
        this.spec = spec;
        this.systemAPI = systemAPI;
    }

    @Override
    public void createUIContent() {
        if (buttons == null) {
            buttons = new ArrayList<>();

        }
        mainButton = new StableStructureButton(width,height,spec,0f, Misc.getBasePlayerColor(),Misc.getDarkPlayerColor(),Misc.getBrightPlayerColor(),systemAPI);
        mainButton.createUI();
        tooltipOfImpl.addCustom(mainButton.getPanel(),5f).getPosition().inTL(0,0);
    }
}
