package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.ids.AoTDTags;

import java.awt.*;
import java.util.List;

public class StableStructureInfoBottom implements CustomUIPanelPlugin {
    CustomPanelAPI mainPanel;
    StarSystemAPI system;

    public StableStructureInfoBottom(StarSystemAPI system, float width, float height) {
        this.system = system;
        mainPanel = Global.getSettings().createCustom(width,height,this);
        createUI();
    }

    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public void createUI() {
        float width = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();

        TooltipMakerAPI tooltip = mainPanel.createUIElement(width,height,false);
        tooltip.setParaInsigniaLarge();

        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        LabelAPI creditsLabel = tooltip.addPara("Credits: %s",0f,Misc.getGrayColor(),Color.ORANGE,Misc.getWithDGS(credits));
        creditsLabel.getPosition().setSize(320,30);

        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 200f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Credits you currently have available for construction.",5f);
            }
        }, TooltipMakerAPI.TooltipLocation.ABOVE,false);

        int occupied = system.getEntitiesWithTag(AoTDTags.AOTD_OBJECTIVE).size();
        int available = system.getEntitiesWithTag(AoTDTags.AOTD_STABLE_LOCATION).size();
        int total = occupied+available;

        Color col = Color.ORANGE;
        if(available<=0){
            col = Misc.getNegativeHighlightColor();
        }

        LabelAPI label = tooltip.addPara("System Structures: %s / %s",5f,Misc.getGrayColor(),col,""+occupied,""+total);
        label.getPosition().inTL(330,0);

        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 300f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("There are currently %s structures in this star system, with a maximum capacity of %s.",5f,
                        Misc.getHighlightColor(),""+occupied,""+total);
                tooltip.addPara("%s stable locations remain available for construction.",5f,
                        available>0?Misc.getHighlightColor():Misc.getNegativeHighlightColor(),""+available);
            }
        }, TooltipMakerAPI.TooltipLocation.ABOVE,false);

        mainPanel.addUIElement(tooltip).inTL(0,0);
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

    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}