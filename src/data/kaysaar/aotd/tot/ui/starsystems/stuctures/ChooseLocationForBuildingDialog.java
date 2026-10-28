package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.ChooseOrbitForBuildingMap;
import java.awt.*;

public class ChooseLocationForBuildingDialog extends BasePopUpDialog {
    String customEntitySpecId;
    ChooseOrbitForBuildingMap map;
    CustomPanelAPI panelOfInfoMain;
    CustomPanelAPI contentForInfo;
    SectorEntityToken orbitFocus = null;
    StarSystemAPI currentStarSystem;
    public ChooseLocationForBuildingDialog(String customEntityToPlace, String headerTitle, StarSystemAPI currentStarSystem) {
        super(headerTitle);
        this.customEntitySpecId = customEntityToPlace;
        this.currentStarSystem = currentStarSystem;
    }
    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        map = new ChooseOrbitForBuildingMap(width-10,500,currentStarSystem,customEntitySpecId);
        panelOfInfoMain = Global.getSettings().createCustom(width-10,100,null);
        contentForInfo = Global.getSettings().createCustom(width-10,100,null);
        panelOfInfoMain.addComponent(contentForInfo).inTL(0,0);
        tooltip.addCustom(map.getMainPanel(),5f);
        tooltip.addCustom(panelOfInfoMain,10f);
        tooltip.setHeightSoFar(0f);

        super.createContentForDialog(tooltip,width);
    }
    public void updateInfo(){
        panelOfInfoMain.removeComponent(contentForInfo);
        contentForInfo = Global.getSettings().createCustom(panelOfInfoMain.getPosition().getWidth(),panelOfInfoMain.getPosition().getHeight(),null);
        TooltipMakerAPI tooltip = contentForInfo.createUIElement(contentForInfo.getPosition().getWidth(),contentForInfo.getPosition().getHeight(),false);
        String font = "graphics/fonts/insignia21LTaa.fnt";
        tooltip.setParaFont( font);
        Float rad = map.getOrbitComponent().getSavedRadiusWorld();
        int r = 0;
        if(rad!=null){
            r = Math.round(rad);
        }
        tooltip.addPara("Orbit Focus : %s",0f, Color.ORANGE,orbitFocus.getName());
        contentForInfo.addUIElement(tooltip);
        panelOfInfoMain.addComponent(contentForInfo);

    }
    @Override
    public void advance(float amount) {
        super.advance(amount);
        if(map!=null && map.getOrbitComponent()!=null && map.getOrbitComponent().isLocked() && map.getOrbitComponent().getCurrentlyCenteredAround()!=null){
            if(!map.getOrbitComponent().getCurrentlyCenteredAround().getToken().equals(orbitFocus)){
                orbitFocus = map.getOrbitComponent().getCurrentlyCenteredAround().getToken();
                updateInfo();
            }
        }
    }
    @Override
    public void onExit() {
        super.onExit();
        if(map.getOrbitComponent()!=null && map.getOrbitComponent().isLocked()){
            /// TODO accept
        }
        else{
            /// TODO decline
        }
    }
}
