package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.StructureLocationPicker;

public class ChooseLocationForBuildingDialog extends BasePopUpDialog {
    AoTDStableStructureImpl structure;
    StructureLocationPicker map;
    CustomPanelAPI panelOfInfoMain;
    CustomPanelAPI contentForInfo;
    StarSystemAPI currentStarSystem;
    float contentHeight;

    public ChooseLocationForBuildingDialog(String customEntityToPlace, String headerTitle, StarSystemAPI currentStarSystem) {
        this(AoTDStableStructureSpecManager.getSpec(customEntityToPlace).getImplClassInstance(),headerTitle,currentStarSystem);
    }

    public ChooseLocationForBuildingDialog(AoTDStableStructureImpl structure, String headerTitle, StarSystemAPI currentStarSystem) {
        super(headerTitle);
        this.structure = structure;
        this.currentStarSystem = currentStarSystem;
    }

    public StructureLocationPicker getPicker(){
        return map;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createHeaader(panelAPI);
        float width = panelAPI.getPosition().getWidth()-30;
        contentHeight = panelAPI.getPosition().getHeight()-y-55;
        TooltipMakerAPI tooltip = panelAPI.createUIElement(width,contentHeight,false);
        createContentForDialog(tooltip,width);
        addTooltip(tooltip);
        panelAPI.addUIElement(tooltip).inTL(x,y);
        createConfirmAndCancelSection(panelAPI);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        if(map!=null)map.clearUI();
        map = structure.createLocationPicker(width-10,contentHeight-90,currentStarSystem);
        panelOfInfoMain = Global.getSettings().createCustom(width-10,80,null);
        contentForInfo = null;
        tooltip.addCustom(map.getMainPanel(),0f);
        tooltip.addCustom(panelOfInfoMain,10f);
        map.consumeSelectionTextUpdate();
        updateInfo();
    }

    public void updateInfo(){
        if(contentForInfo!=null)panelOfInfoMain.removeComponent(contentForInfo);
        contentForInfo = Global.getSettings().createCustom(panelOfInfoMain.getPosition().getWidth(),panelOfInfoMain.getPosition().getHeight(),null);
        TooltipMakerAPI tooltip = contentForInfo.createUIElement(contentForInfo.getPosition().getWidth()-10,contentForInfo.getPosition().getHeight(),true);
        tooltip.setParaFont("graphics/fonts/insignia21LTaa.fnt");
        map.createSelectionText(tooltip);
        contentForInfo.addUIElement(tooltip).inTL(0,0);
        panelOfInfoMain.addComponent(contentForInfo).inTL(0,0);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI button = super.generateConfirmButton(tooltip);
        button.setEnabled(map!=null && map.hasSelection());
        return button;
    }

    @Override
    public void advance(float amount) {
        if(map!=null){
            if(confirmButton!=null)confirmButton.setEnabled(map.hasSelection());
            if(panelOfInfoMain!=null && map.consumeSelectionTextUpdate())updateInfo();
        }
        super.advance(amount);
    }

    @Override
    public void applyConfirmScript() {
        if(map==null || !map.hasSelection())return;
        // TODO: Build structure at map.getSelectedLocation(), using map.getSelectedEntity()
        // as the stable point/orbit focus. Selection and preview do not change the campaign.
    }

    @Override
    public void onExit() {
        if(map!=null)map.clearUI();
        super.onExit();
    }
}
