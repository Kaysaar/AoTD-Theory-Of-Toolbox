package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.ui.models.ImagePanel;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;

import java.awt.Color;

public class StableStructureUpgradeNode {

    private final AoTDStableStructureSpec spec;
    private final AoTDStableStructureImpl impl;
    private final CustomPanelAPI mainPanel;
    private final CustomPanelAPI imagePanel;
    private final ButtonAPI queueButton;

    public StableStructureUpgradeNode(AoTDStableStructureSpec spec, AoTDStableStructureImpl impl,
                                      float nodeWidth, float nodeHeight, float imageWidth, float imageHeight) {
        this.spec = spec;
        this.impl = impl;

        mainPanel = Global.getSettings().createCustom(nodeWidth, nodeHeight, null);
        TooltipMakerAPI tooltip = mainPanel.createUIElement(nodeWidth, nodeHeight, false);
        tooltip.setParaFont(Fonts.ORBITRON_12);
        tooltip.addPara(impl.getName(), Misc.getTooltipTitleAndLightHighlightColor(), 0f)
                .getPosition().inTL(0f, 0f);

        imagePanel = createImagePanel(imageWidth, imageHeight, impl.getImageName(), Misc.getBasePlayerColor());
        tooltip.addCustom(imagePanel, 0f).getPosition().inTL(0f, 23f);

        if (!spec.getDowngrades().isEmpty()) {
            queueButton = tooltip.addButton("Queue", spec.getId(), 70f, 20f, 0f);
            queueButton.getPosition().inTL(0f, 23f + imageHeight + 5f);
        } else {
            queueButton = null;
        }

        mainPanel.addUIElement(tooltip).inTL(0f, 0f);
    }

    public static CustomPanelAPI createImagePanel(float width, float height, String imageName, Color borderColor) {
        UILinesRenderer lines = new UILinesRenderer(0f);
        lines.setBoxColor(borderColor);

        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, lines);
        ImagePanel imageRenderer = new ImagePanel();
        CustomPanelAPI image = panel.createCustomPanel(width, height, imageRenderer);
        imageRenderer.init(image, Global.getSettings().getSprite(imageName));

        panel.addComponent(image).inTL(0f, 0f);
        lines.setPanel(image);
        return panel;
    }

    public AoTDStableStructureSpec getSpec() {
        return spec;
    }

    public AoTDStableStructureImpl getImpl() {
        return impl;
    }

    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public CustomPanelAPI getImagePanel() {
        return imagePanel;
    }

    public ButtonAPI getQueueButton() {
        return queueButton;
    }
}
