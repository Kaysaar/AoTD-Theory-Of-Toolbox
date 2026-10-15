package data.kaysaar.aotd.tot.ui.starsystems.stuctures.table;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.CustomButton;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;

import java.awt.Color;

public class StableStructureButton extends CustomButton {

    public AoTDStableStructureSpec spec;
    public StarSystemAPI systemAPI;

    public StableStructureButton(float width, float height, Object buttonData, float indent, Color base, Color bg, Color bright, StarSystemAPI systemAPI) {
        super(width, height, buttonData, indent, base, bg, bright);
        this.systemAPI = systemAPI;
    }

    @Override
    public void initializeUI() {
        TooltipMakerAPI tooltip = panel.createUIElement(width, height, false);
        TooltipMakerAPI tooltipActualButton = panel.createUIElement(width, height, false);
        mainButton = createButton(tooltipActualButton);
        spec = (AoTDStableStructureSpec) buttonData;
        createButtonContent(tooltip);

        panel.addUIElement(tooltipActualButton).inTL(0, 0);
        panel.addUIElement(tooltip).inTL(0, 0);
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI panelMain = Global.getSettings().createCustom(width, height, null);
        spec = (AoTDStableStructureSpec) buttonData;
        AoTDStableStructureImpl impl = spec.getImplClassInstance();

        float startingXName = 110f;
        float costWidth = StableStructureTable.widthMap.get("buildCost");

        TooltipMakerAPI image = panelMain.createUIElement(100, height, false);
        TooltipMakerAPI name = panelMain.createUIElement(StableStructureTable.widthMap.get("name") - startingXName, height, false);
        TooltipMakerAPI days = panelMain.createUIElement(StableStructureTable.widthMap.get("buildingTime"), height, false);

        image.addImage(impl.getImageName(), 96, 60, 0f);
        image.getPrev().getPosition().inTL(7, 1);

        LabelAPI nameLabel;
        if (impl.isAvailableToBuild(systemAPI, AshMisc.getClaimingFaction(systemAPI.getCenter()))) {
            nameLabel = name.addPara(impl.getName(), Misc.getTooltipTitleAndLightHighlightColor(), 0f);
        } else {
            nameLabel = name.addPara(impl.getName(), Misc.getNegativeHighlightColor(), 0f);
        }

        LabelAPI daysLabel = days.addPara("" + spec.getDaysToBuild(), Color.ORANGE, 0f);
        daysLabel.autoSizeToWidth(StableStructureTable.widthMap.get("buildingTime"));
        daysLabel.setAlignment(Alignment.MID);

        nameLabel.getPosition().inTL(0, height / 2f - nameLabel.computeTextHeight(nameLabel.getText()) / 2f);
        daysLabel.getPosition().inTL(5, height / 2f - daysLabel.computeTextHeight(daysLabel.getText()) / 2f);

        CustomPanelAPI cost = AoTDToolboxMisc.createCostSection(
                costWidth,
                height,
                impl.getBuildCostResources(),
                impl.getBuildCostCredits(),
                22f,
                6f,
                3f,
                8f,
                true,
                true
        );

        panelMain.addUIElement(image).inTL(0, 0);
        panelMain.addUIElement(name).inTL(startingXName, 0);
        panelMain.addUIElement(days).inTL(StableStructureTable.getStartingX("buildingTime"), 0);
        panelMain.addComponent(cost).inTL(StableStructureTable.getStartingX("buildCost"), 0);

        tooltip.addCustom(panelMain, 0f).getPosition().inTL(0, 0);
    }
}
