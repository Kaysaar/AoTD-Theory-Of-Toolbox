package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.StructureLocationPicker;

public class ChooseLocationForBuildingDialog extends BasePopUpDialog {
    AoTDStableStructureImpl structure;
    AoTDStableStructureImpl baseStructure;
    StructureLocationPicker map;
    CustomPanelAPI panelOfInfoMain;
    CustomPanelAPI contentForInfo;
    StarSystemAPI currentStarSystem;
    float contentHeight;
    IntervalUtil refresh = new IntervalUtil(0.25f, 0.25f);

    public ChooseLocationForBuildingDialog(String customEntityToPlace, String headerTitle, StarSystemAPI currentStarSystem) {
        this(AoTDStableStructureSpecManager.getSpec(customEntityToPlace).getImplClassInstance(), headerTitle, currentStarSystem);
    }

    public ChooseLocationForBuildingDialog(AoTDStableStructureImpl structure, String headerTitle, StarSystemAPI currentStarSystem) {
        super(headerTitle);
        this.structure = structure;
        this.baseStructure = AoTDStableStructureConstruction.getBaseStructure(structure);
        this.currentStarSystem = currentStarSystem;
    }

    public StructureLocationPicker getPicker() {
        return map;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createHeaader(panelAPI);
        float width = panelAPI.getPosition().getWidth() - 30;
        contentHeight = panelAPI.getPosition().getHeight() - y - 55;
        TooltipMakerAPI tooltip = panelAPI.createUIElement(width, contentHeight, false);
        createContentForDialog(tooltip, width);
        addTooltip(tooltip);
        panelAPI.addUIElement(tooltip).inTL(x, y);
        createConfirmAndCancelSection(panelAPI);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        if (map != null) map.clearUI();
        map = structure.createLocationPicker(width - 10, contentHeight - 90, currentStarSystem);
        panelOfInfoMain = Global.getSettings().createCustom(width - 10, 80, null);
        contentForInfo = null;
        tooltip.addCustom(map.getMainPanel(), 0f);
        tooltip.addCustom(panelOfInfoMain, 10f);
        map.consumeSelectionTextUpdate();
        updateInfo();
    }

    public void updateInfo() {
        if (contentForInfo != null) panelOfInfoMain.removeComponent(contentForInfo);
        contentForInfo = Global.getSettings().createCustom(panelOfInfoMain.getPosition().getWidth(), panelOfInfoMain.getPosition().getHeight(), null);
        TooltipMakerAPI tooltip = contentForInfo.createUIElement(contentForInfo.getPosition().getWidth() - 10, contentForInfo.getPosition().getHeight(), true);
        tooltip.setParaFont("graphics/fonts/insignia21LTaa.fnt");
        map.createSelectionText(tooltip);
        contentForInfo.addUIElement(tooltip).inTL(0, 0);
        panelOfInfoMain.addComponent(contentForInfo).inTL(0, 0);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI button = super.generateConfirmButton(tooltip);
        button.setEnabled(canConfirm());
        button.setButtonPressedSound("ui_build_industry");
        return button;
    }

    @Override
    public void advance(float amount) {
        refresh.advance(amount);
        boolean update = refresh.intervalElapsed();
        if (map != null) {
            boolean selectionChanged = map.consumeSelectionTextUpdate();
            if (confirmButton != null && (update || selectionChanged)) setButtonStage(confirmButton, canConfirm());
            if (panelOfInfoMain != null && selectionChanged) updateInfo();
        }
        super.advance(amount);
    }

    public void setButtonStage(ButtonAPI bt, boolean stage) {
        if (stage != bt.isEnabled()) {
            bt.setEnabled(stage);
        }
    }

    public boolean canConfirm() {
        return map != null && map.hasSelection() && AoTDStableStructureConstruction.canStart(
                structure, baseStructure, currentStarSystem, map.getSelectedEntity(), map.getSelectedLocation());
    }

    @Override
    public void applyConfirmScript() {
        if (!canConfirm()) return;
        AoTDStableStructureConstruction.start(structure, currentStarSystem, map.getSelectedEntity(), map.getSelectedLocation());
    }

    @Override
    public void onExit() {
        if (map != null) map.clearUI();
        super.onExit();
    }
}
