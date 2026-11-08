package data.kaysaar.aotd.tot.ui.starsystems;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapZoomableComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.StarSystemStructuresUI;

import java.util.List;

public class CurrentStarSystemTab implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel,contentPanel;
    StarSystemAPI currStarSystem;
    MapMainComponent mapMainComponent;
    StarSystemStructuresUI struct;
    ButtonAPI claim;
    long displayedStructureVersion = -1L;
    StarSystemAPI displayedMapSystem;

    public CurrentStarSystemTab(float width,float height){
        this.mainPanel = Global.getSettings().createCustom(width,height,this);
    }

    public void setCurrStarSystem(StarSystemAPI currStarSystem) {
        this.currStarSystem = currStarSystem;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        float previousScale = -1f;
        float previousOffsetX = 0f;
        float previousOffsetY = 0f;
        if (mapMainComponent != null && displayedMapSystem == currStarSystem) {
            MapZoomableComponent previousMap = mapMainComponent.getMapZoom();
            previousScale = previousMap.currScale;
            previousOffsetX = previousMap.getData().getCurrentOffsetX();
            previousOffsetY = previousMap.getData().getCurrentOffsetY();
        }

        displayedStructureVersion = AoTDStableStructureConstruction.getUIRefreshVersion(currStarSystem);
        if(contentPanel!=null){
            mainPanel.removeComponent(contentPanel);
        }
        if(mapMainComponent!=null){
            mapMainComponent.clearUI();
            mapMainComponent = null;
        }
        contentPanel = Global.getSettings().createCustom(mainPanel.getPosition().getWidth(),mainPanel.getPosition().getHeight(),null);
        if(currStarSystem!=null){
            mapMainComponent = new MapMainComponent(contentPanel.getPosition().getWidth()-5,contentPanel.getPosition().getHeight()*0.4f,currStarSystem);
            displayedMapSystem = currStarSystem;
            if (previousScale > 0f) {
                MapZoomableComponent map = mapMainComponent.getMapZoom();
                map.setCurrScale(previousScale);
                map.getData().setCurrentOffsetX(previousOffsetX);
                map.getData().setCurrentOffsetY(previousOffsetY);
            }
            TooltipMakerAPI tooltip = contentPanel.createUIElement(mapMainComponent.getMainPanel().getPosition().getWidth(),mapMainComponent.getMainPanel().getPosition().getHeight()+40,false);
            tooltip.setParaFont(Fonts.ORBITRON_20AA);
            FactionAPI faction = AshMisc.getClaimingFaction(currStarSystem.getCenter());
            tooltip.addPara(currStarSystem.getName(), faction.getBaseUIColor(),0f).setAlignment(Alignment.MID);
            tooltip.addCustom(mapMainComponent.getMainPanel(),3f);
            contentPanel.addUIElement(tooltip).inTL(0,0);
            createStarSystemInfoSection();
        }
        mainPanel.addComponent(contentPanel).inTL(0,0);
    }

    public void createStarSystemInfoSection(){
        float heightLeft = contentPanel.getPosition().getHeight()-mapMainComponent.getMainPanel().getPosition().getHeight()-60;
        float startingY = contentPanel.getPosition().getHeight()-heightLeft-30;
        TooltipMakerAPI tooltipFirst = contentPanel.createUIElement(contentPanel.getPosition().getWidth(),heightLeft,false);
        tooltipFirst.setParaFont(Fonts.ORBITRON_16);
        claim=tooltipFirst.addButton("Claim Star System Ownership",null,Misc.getBasePlayerColor(),Misc.getDarkPlayerColor(),Alignment.MID,CutStyle.TL_BR,contentPanel.getPosition().getWidth()-5,30,5f);
        claim.setEnabled(ClaimStarSystemOwnershipDialog.canClaim(currStarSystem));
        tooltipFirst.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 440;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                FactionAPI owner = AshMisc.getClaimingFaction(currStarSystem.getCenter());
                if (owner != null && owner.isPlayerFaction()) {
                    tooltip.addPara("You already own this star system.", 3f);
                    return;
                }

                tooltip.addPara("Your faction must have the largest combined market size in this star system to claim ownership. Ties do not qualify.", 3f);
                tooltip.addPara("Your combined market size: %s. Largest competing faction: %s.", 5f,
                        Misc.getHighlightColor(),
                        String.valueOf(ClaimStarSystemOwnershipDialog.getPlayerCombinedMarketSize(currStarSystem)),
                        String.valueOf(ClaimStarSystemOwnershipDialog.getLargestOtherCombinedMarketSize(currStarSystem)));

                if (owner != null && !Factions.NEUTRAL.equals(owner.getId())) {
                    tooltip.addPara("Claiming the system reduces your relationship with %s by %s points.", 5f,
                            Misc.getNegativeHighlightColor(), owner.getDisplayName(), "70");
                } else {
                    tooltip.addPara("There is no recognized faction owner, so claiming does not reduce relationships.", 5f);
                }
                if (!ClaimStarSystemOwnershipDialog.canClaim(currStarSystem)) {
                    tooltip.addPara("You do not currently meet the ownership requirement.", Misc.getNegativeHighlightColor(), 5f);
                }
                tooltip.addPara("This mechanic will be fully overhauled / expanded with Seats of Power 4.0 Update",Misc.getGrayColor(),15f);
            }
        },claim, TooltipMakerAPI.TooltipLocation.BELOW,false);

        tooltipFirst.addPara("System Structures", AshMisc.getClaimingFaction(currStarSystem.getCenter()).getBaseUIColor(),17f).setAlignment(Alignment.MID);
        if(struct==null){
            struct = new StarSystemStructuresUI(contentPanel.getPosition().getWidth(),heightLeft,currStarSystem);
        }
        struct.setSystem(currStarSystem);
        tooltipFirst.addCustom(struct.getMainPanel(),5f);
        contentPanel.addUIElement(tooltipFirst).inTL(0,startingY);
    }

    @Override
    public void clearUI() {

    }

    @Override
    public void positionChanged(PositionAPI position) {

    }

    @Override
    public void renderBelow(float alphaMult) {

    }

    @Override
    public void render(float alphaMult) {

    }

    @Override
    public void advance(float amount) {
        if (currStarSystem != null && displayedStructureVersion != AoTDStableStructureConstruction.getUIRefreshVersion(currStarSystem)) {
            createUI();
        }

        if (claim != null && currStarSystem != null) {
            boolean can = ClaimStarSystemOwnershipDialog.canClaim(currStarSystem);
            if (claim.isEnabled() != can) claim.setEnabled(can);
            if (claim.isChecked()) {
                claim.setChecked(false);
                if (can) AshMisc.initPopUpDialog(new ClaimStarSystemOwnershipDialog(currStarSystem), 750, 260);
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}
