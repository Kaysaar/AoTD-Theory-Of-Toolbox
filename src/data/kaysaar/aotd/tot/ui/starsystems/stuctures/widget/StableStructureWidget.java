package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ButtonWithImageComponent;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import org.lwjgl.util.vector.Vector2f;


import java.awt.*;
import java.util.List;

import static data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager.memKeyInEntities;

public class StableStructureWidget implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel,contentPanel;
    SectorEntityToken token;
    UILinesRenderer renderer;

    public AoTDStableStructureImpl getStructure(){
        if(token!=null){
            return (AoTDStableStructureImpl) token.getMemoryWithoutUpdate().get(memKeyInEntities);
        }
        return null;
    }
    public static float width= 225;
    public static float height= 150;
    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public StableStructureWidget(SectorEntityToken token) {
        this.token = token;
        this.mainPanel = Global.getSettings().createCustom(width,height,this);
        renderer = new UILinesRenderer(0f);
        createUI();
    }
    @Override
    public void createUI() {
        if(contentPanel!=null){
            mainPanel.removeComponent(contentPanel);
        }
        contentPanel = Global.getSettings().createCustom(mainPanel.getPosition().getWidth(),mainPanel.getPosition().getHeight(),null);
        final StableStructureWidget widget = this;
        ButtonWithImageComponent viewer = new ButtonWithImageComponent(contentPanel.getPosition().getWidth(),125,token.getCustomInteractionDialogImageVisual().getSpriteName()){
            @Override
            public void performActionOnClick(boolean isRightClick) {
                final  ButtonWithImageComponent v = this;
                v.setClickable(false);
                StableStructureWidgetButtons bt = new StableStructureWidgetButtons(widget){
                    @Override
                    public void onExit() {
                        super.onExit();
                        v.setClickable(true);
                    }
                };

                AshMisc.placePopUpUIInTL(bt,this.getComponentPanel(),330,300,new Vector2f(-widget.contentPanel.getPosition().getWidth()-300,0f));

            }
        };
        viewer.setEnableRightClick(false);
        TooltipMakerAPI tooltip = contentPanel.createUIElement(contentPanel.getPosition().getWidth(),contentPanel.getPosition().getHeight(),false);
        Color c = Misc.getGrayColor();
        if(token.getFaction()!=null){
            c= token.getFaction().getBaseUIColor();
        }
        if(token.getMemoryWithoutUpdate().contains(memKeyInEntities)){
            AoTDStableStructureImpl impl = (AoTDStableStructureImpl) token.getMemoryWithoutUpdate().get(memKeyInEntities);
            tooltip.addPara(impl.getName(),c,0f);
            tooltip.addCustom(viewer.getComponentPanel(),2f);
            renderer.setBoxColor(c);
            renderer.setPanel(viewer.getComponentPanel());
            contentPanel.addUIElement(tooltip).inTL(0,0);
            tooltip.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
                @Override
                public boolean isTooltipExpandable(Object tooltipParam) {
                    return false;
                }

                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 450f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    impl.createTooltip(tooltip, AoTDStableStructureImpl.AoTDStableStructureTooltipMode.PANEL);
                }
            },contentPanel, TooltipMakerAPI.TooltipLocation.LEFT,false);
            mainPanel.addComponent(contentPanel).inTL(0,0);
        }
        else{

            tooltip.addPara(token.getName(),c,0f);
            tooltip.addCustom(viewer.getComponentPanel(),2f);
            renderer.setBoxColor(c);
            renderer.setPanel(viewer.getComponentPanel());
            contentPanel.addUIElement(tooltip).inTL(0,0);
            mainPanel.addComponent(contentPanel).inTL(0,0);
        }

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
        renderer.render(alphaMult);
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
