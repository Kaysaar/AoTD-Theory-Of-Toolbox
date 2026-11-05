package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ButtonWithImageComponent;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.ProgressBarComponentV2;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import com.fs.starfarer.api.util.IntervalUtil;

import static data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager.memKeyInEntities;

public class StableStructureWidget implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel,contentPanel;
    SectorEntityToken token;
    UILinesRenderer renderer;
    IntervalUtil refresh = new IntervalUtil(0.25f, 0.25f);
    AoTDStableStructureImpl displayedStructure;
    String displayedStatus;
    float displayedProgress;
    Runnable onSystemStructuresRefresh;

    public AoTDStableStructureImpl getStructure(){
        if (token == null || token.isExpired()) return null;

        Object value = token.getMemoryWithoutUpdate().get(memKeyInEntities);
        if (value instanceof AoTDStableStructureImpl) return (AoTDStableStructureImpl) value;
        if (value != null || token.getCustomEntitySpec() == null) return null;

        AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(token.getCustomEntitySpec().getId());
        if (spec == null) return null;

        AoTDStableStructureImpl impl = spec.getImplClassInstance();
        impl.setCurrentlyAssignedToken(token);
        return impl;
    }
    public static float width= 225;
    public static float height= 150;
    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public StableStructureWidget(SectorEntityToken token) {
        this(token, null);
    }

    public StableStructureWidget(SectorEntityToken token, Runnable onSystemStructuresRefresh) {
        this.onSystemStructuresRefresh = onSystemStructuresRefresh;
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
        if (token.isExpired()) return;
        contentPanel = Global.getSettings().createCustom(mainPanel.getPosition().getWidth(),mainPanel.getPosition().getHeight(),null);
        displayedStructure = getStructure();
        displayedStatus = displayedStructure == null ? "" : displayedStructure.getBuildOrUpgradeProgressText();
        displayedProgress = displayedStructure == null ? 0f : displayedStructure.getBuildOrUpgradeProgress();
        final StableStructureWidget widget = this;
        ButtonWithImageComponent viewer = new ButtonWithImageComponent(contentPanel.getPosition().getWidth(),125,getStructure() != null ? getStructure().getImageName() : token.getCustomEntitySpec().getInteractionImage()){
            @Override
            public void performActionOnClick(boolean isRightClick) {
                StableStructureWidgetButtons bt = new StableStructureWidgetButtons(widget);
                if (!bt.hasOptions()) return;
                this.setClickable(false);
                AshMisc.placePopUpUIInTL(bt,this.getComponentPanel(),330,300,new Vector2f(-widget.contentPanel.getPosition().getWidth()-300,0f));
            }
        };
        viewer.setEnableRightClick(false);

        TooltipMakerAPI tooltip = contentPanel.createUIElement(contentPanel.getPosition().getWidth(),contentPanel.getPosition().getHeight(),false);
        Color c = Misc.getGrayColor();
        Color base = null,bg= null;
        if(token.getFaction()!=null){
            c= token.getFaction().getBaseUIColor();
            base = token.getFaction().getBaseUIColor();
            bg = token.getFaction().getDarkUIColor();;
        }
        if(getStructure()!=null){
            AoTDStableStructureImpl impl = getStructure();
            if(AshMisc.isStringValid(impl.getAICoreId())){
                ImageViewer viewer1 = new ImageViewer(40,40,Global.getSettings().getCommoditySpec(impl.getAICoreId()).getIconName());
                viewer.addComponent(viewer1,viewer.getComponentPanel().getPosition().getWidth()-40,2);
            }
            tooltip.addPara(impl.getName(),c,0f);
            tooltip.addCustom(viewer.getComponentPanel(),2f);
            if(getStructure().isBuilding()||getStructure().isUpgrading()||getStructure().isDisrupted()){
                ProgressBarComponentV2 barComponentV2 = new ProgressBarComponentV2(contentPanel.getPosition().getWidth()+18f,12,getStructure().getBuildOrUpgradeProgressText(), Fonts.VICTOR_10,base,bg,getStructure().getBuildOrUpgradeProgress());
                float y = tooltip.getHeightSoFar();
                tooltip.addCustom(barComponentV2.getMainPanel(),1f).getPosition().inTL(-1,y+1);
            }


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

    public void requestSystemStructuresRefresh() {
        if (onSystemStructuresRefresh != null) onSystemStructuresRefresh.run();
        else createUI();
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
        refresh.advance(amount);
        if (!refresh.intervalElapsed()) return;
        AoTDStableStructureImpl impl = getStructure();
        if (impl != displayedStructure || impl != null &&
                (!Objects.equals(displayedStatus, impl.getBuildOrUpgradeProgressText())
                        || Math.abs(displayedProgress - impl.getBuildOrUpgradeProgress()) > 0.001f)) createUI();
    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}
