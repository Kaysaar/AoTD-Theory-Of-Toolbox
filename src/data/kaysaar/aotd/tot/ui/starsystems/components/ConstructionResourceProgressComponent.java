package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.ProgressBarComponentV2;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

import java.awt.Color;
import java.util.List;

public class ConstructionResourceProgressComponent implements ExtendedUIPanelPlugin {
    public static final float HEIGHT = 80f;
    CustomPanelAPI mainPanel, contentPanel;
    AoTDStableStructureImpl structure;
    String resourceId;
    Color color, dark;
    IntervalUtil refresh = new IntervalUtil(0.25f, 0.25f);
    int displayedDelivered = -1;
    int displayedRequired = -1;

    public ConstructionResourceProgressComponent(AoTDStableStructureImpl structure, String resourceId,
                                                 float width, Color color, Color dark) {
        this.structure = structure;
        this.resourceId = resourceId;
        this.color = color;
        this.dark = dark;
        mainPanel = Global.getSettings().createCustom(width, HEIGHT, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) mainPanel.removeComponent(contentPanel);

        float width = mainPanel.getPosition().getWidth();
        contentPanel = Global.getSettings().createCustom(width, HEIGHT, null);
        displayedRequired = Math.max(0, structure.getConstructionResources().getOrDefault(resourceId, 0));
        displayedDelivered = Math.max(0, Math.min(displayedRequired,
                structure.getDeliveredResources().getOrDefault(resourceId, 0)));

        CommoditySpecAPI commodity = Global.getSettings().getCommoditySpec(resourceId);
        SpecialItemSpecAPI specialItem = commodity == null
                ? Global.getSettings().getSpecialItemSpec(resourceId) : null;
        String name = commodity != null ? commodity.getName() : specialItem.getName();
        String icon = commodity != null ? commodity.getIconName() : specialItem.getIconName();

        ImageViewer viewer = new ImageViewer(45f, 45f, icon);
        contentPanel.addComponent(viewer.getComponentPanel()).inTL(0f, 0f);

        TooltipMakerAPI title = contentPanel.createUIElement(width - 55f, 45f, false);
        title.setParaFont(Fonts.DEFAULT_SMALL);
        title.addPara(name, color, 0f);
        contentPanel.addUIElement(title).inTL(53f, 12f);

        float progress = displayedRequired <= 0 ? 0f : displayedDelivered / (float) displayedRequired;
        String text = Misc.getWithDGS(displayedDelivered) + " / " + Misc.getWithDGS(displayedRequired);
        ProgressBarComponentV2 bar = new ProgressBarComponentV2(width - 2f, 24f, text,
                Fonts.DEFAULT_SMALL, color, dark, progress);
        contentPanel.addComponent(bar.getMainPanel()).inTL(0f, 51f);

        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
    }

    @Override
    public void clearUI() {
        if (contentPanel != null) mainPanel.removeComponent(contentPanel);
        contentPanel = null;
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
        refresh.advance(amount);
        if (!refresh.intervalElapsed()) return;
        int required = Math.max(0, structure.getConstructionResources().getOrDefault(resourceId, 0));
        int delivered = Math.max(0, Math.min(required,
                structure.getDeliveredResources().getOrDefault(resourceId, 0)));
        if (required != displayedRequired || delivered != displayedDelivered) createUI();
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }

    @Override
    public void buttonPressed(Object buttonId) {
    }
}
