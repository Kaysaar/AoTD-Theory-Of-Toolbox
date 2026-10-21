package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;

public class AssignAICoreStructure extends BasePopUpDialog {
    StableStructureWidget widget;
    String currentAICoreAssigned;
    CustomPanelAPI headerPanel,listOfAICores,mainSubPanel;
    ButtonAPI unassign;
    float height;
    //for testing
    boolean hasNoAICores=  true;
    public static ArrayList<String>aicoresForTesting = new ArrayList<>();
    static {
        aicoresForTesting.add(Commodities.GAMMA_CORE);
        aicoresForTesting.add(Commodities.BETA_CORE);
        aicoresForTesting.add(Commodities.ALPHA_CORE);
    }
    public AssignAICoreStructure(StableStructureWidget widget) {
        super(null);
        this.widget = widget;
        currentAICoreAssigned = widget.getStructure().getAICoreId();
    }
    public void createUI(CustomPanelAPI panelAPI) {
        this.createHeaader(panelAPI);
        TooltipMakerAPI tooltip = panelAPI.createUIElement(panelAPI.getPosition().getWidth() - 30.0F, panelAPI.getPosition().getHeight() - this.y, true);
        height = panelAPI.getPosition().getHeight() - this.y;
        this.createContentForDialog(tooltip, panelAPI.getPosition().getWidth() - 30.0F);
        this.addTooltip(tooltip);
        panelAPI.addUIElement(tooltip).inTL(this.x, this.y);
        this.createConfirmAndCancelSection(panelAPI);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        mainSubPanel = Global.getSettings().createCustom(width,height,null);
        createCurrentlyAssignedContent(width,230);
        createAICoreListSection(width,height-280);
        mainSubPanel.addComponent(headerPanel).inTL(0,0);
        mainSubPanel.addComponent(listOfAICores).inTL(0,235);
        tooltip.addCustom(mainSubPanel,0f);
    }
    public void createAICoreListSection(float width, float height) {
        listOfAICores = Global.getSettings().createCustom(width,height,null);
        TooltipMakerAPI tlHeader = listOfAICores.createUIElement(width,20,false);
        TooltipMakerAPI tlBody = listOfAICores.createUIElement(width,height-25,true);
        FactionAPI factionAPI = widget.getStructure().currentlyAssignedToken.getFaction();
        tlHeader.addSectionHeading("Select AI core for "+widget.getStructure().getName(),factionAPI.getBaseUIColor(),factionAPI.getDarkUIColor(),Alignment.MID,0f);
        if(hasNoAICores){
            LabelAPI label = tlBody.addPara("No AI cores available",factionAPI.getBaseUIColor(),0f);
            label.setAlignment(Alignment.MID);
            label.getPosition().inTL(5,((height-25)/2)-((label.computeTextHeight(label.getText()))/2));
        }
        else{

        }
        listOfAICores.addUIElement(tlHeader).inTL(0,0);
        listOfAICores.addUIElement(tlBody).inTL(0,25);

    }
    public void createCurrentlyAssignedContent(float width, float height){

        headerPanel = Global.getSettings().createCustom(width,height,null);
        FactionAPI factionAPI = widget.getStructure().currentlyAssignedToken.getFaction();
        if(currentAICoreAssigned!=null){
            TooltipMakerAPI tl = headerPanel.createUIElement(width,140,false);
            tl.setParaFont(Fonts.ORBITRON_20AA);
            headerPanel.addUIElement(tl).inTL(0,0);

        }
        else{
            headerPanel = Global.getSettings().createCustom(width,height,null);
            TooltipMakerAPI tl = headerPanel.createUIElement(width,height-30,false);
            LabelAPI label = tl.addPara("No AI core currently assigned",factionAPI.getBaseUIColor(),0f);
            label.setAlignment(Alignment.MID);
            label.getPosition().inTL(5,(headerPanel.getPosition().getHeight()-30)/2-(label.computeTextHeight(label.getText())/2));


            headerPanel.addUIElement(tl).inTL(0,0);

        }
        TooltipMakerAPI tlbt = headerPanel.createUIElement(width,30,false);
        tlbt.setButtonFontOrbitron20();
        unassign = tlbt.addButton("Unassign AI Core",null,factionAPI.getBaseUIColor(),factionAPI.getDarkUIColor(),Alignment.MID,CutStyle.TL_BR,310,25,0f);
        unassign.setShortcut(Keyboard.KEY_T,false);
        unassign.getPosition().inTL(width-310,0);
        headerPanel.addUIElement(tlbt).inTL(0,height-30);
    }

    public void setBtEnabled(ButtonAPI button,boolean stateToEnfroce){
        if(button.isEnabled()==stateToEnfroce)return;
        button.setEnabled(stateToEnfroce);
    }
    @Override
    public void advance(float amount) {
        super.advance(amount);
        if(unassign!=null){
            setBtEnabled(unassign,AshMisc.isStringValid(currentAICoreAssigned));
        }
    }
}
