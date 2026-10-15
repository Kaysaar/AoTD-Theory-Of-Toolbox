package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureManager;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class StableStructureDetailedPanel implements ExtendedUIPanelPlugin {

    CustomPanelAPI mainPanel, contentPanel;
    AoTDStableStructureSpec spec;
    StarSystemAPI systemAPI;
    ButtonAPI showUpgradePath;
    private Consumer<AoTDStableStructureSpec> queueHandler;
    private AoTDStableStructureSpec pendingQueueRequest;
    private AoTDStableStructureImpl currentImpl;
    float imageAndTitleSection = 175f;
    float potentialUpgradeButtonHeight = 20f;

    public StableStructureDetailedPanel(AoTDStableStructureSpec spec, StarSystemAPI systemAPI, float width, float height) {
        this.spec = spec;
        this.systemAPI = systemAPI;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }

        contentPanel = Global.getSettings().createCustom(
                mainPanel.getPosition().getWidth(),
                mainPanel.getPosition().getHeight(),
                null
        );
        showUpgradePath = null;

        if (spec != null) {
            if (currentImpl == null || !spec.getId().equals(currentImpl.id)) {
                currentImpl = spec.getImplClassInstance();
            }
            AoTDStableStructureImpl impl = currentImpl;
            float width = mainPanel.getPosition().getWidth();
            float height = mainPanel.getPosition().getHeight();

            CustomPanelAPI panelTitleImage = contentPanel.createCustomPanel(width, imageAndTitleSection, null);
            TooltipMakerAPI titleTooltip = panelTitleImage.createUIElement(width, imageAndTitleSection, false);
            titleTooltip.setParaFont(Fonts.ORBITRON_20AA);
            titleTooltip.addPara(impl.getName(), Misc.getTooltipTitleAndLightHighlightColor(), 0f)
                    .setAlignment(Alignment.MID);

            CustomPanelAPI imageContentPanel = panelTitleImage.createCustomPanel(width, 150f, null);
            CustomPanelAPI viewer = StableStructureUpgradeNode.createImagePanel(
                    240f, 150f, impl.getImageName(), Misc.getBasePlayerColor()
            );
            imageContentPanel.addComponent(viewer).inTL(
                    (width - viewer.getPosition().getWidth()) / 2f,
                    0f
            );
            titleTooltip.addCustom(imageContentPanel, 5f);
            panelTitleImage.addUIElement(titleTooltip).inTL(0f, 0f);
            contentPanel.addComponent(panelTitleImage).inTL(0f, 0f);

            float mainContentHeight = height - imageAndTitleSection - 5f;
            boolean hasPath = AoTDStableStructureManager.hasUpgrades(spec.getId())
                    || !spec.getDowngrades().isEmpty();

            if (hasPath) {
                FactionAPI faction = AshMisc.getClaimingFaction(systemAPI.getCenter());
                mainContentHeight -= potentialUpgradeButtonHeight + 10f;

                TooltipMakerAPI buttonTooltip = contentPanel.createUIElement(
                        width, potentialUpgradeButtonHeight, false
                );
                showUpgradePath = buttonTooltip.addButton(
                        "Show Structure Upgrade Path",
                        null,
                        faction.getBaseUIColor(),
                        faction.getDarkUIColor(),
                        Alignment.MID,
                        CutStyle.ALL,
                        width - 10f,
                        potentialUpgradeButtonHeight,
                        0f
                );
                contentPanel.addUIElement(buttonTooltip).inTL(
                        0f,
                        height - potentialUpgradeButtonHeight
                );
            }

            TooltipMakerAPI mainTooltip = contentPanel.createUIElement(
                    width,
                    Math.max(0f, mainContentHeight),
                    true
            );
            impl.createTooltip(mainTooltip, AoTDStableStructureImpl.AoTDStableStructureTooltipMode.BUILDING_MENU);
            contentPanel.addUIElement(mainTooltip).inTL(0f, imageAndTitleSection + 5f);
        }

        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
    }

    public void setQueueHandler(Consumer<AoTDStableStructureSpec> queueHandler) {
        this.queueHandler = queueHandler;
    }

    public AoTDStableStructureSpec consumeQueueRequest() {
        AoTDStableStructureSpec request = pendingQueueRequest;
        pendingQueueRequest = null;
        return request;
    }

    private void requestQueue(AoTDStableStructureSpec requestedSpec) {
        if (queueHandler != null) {
            queueHandler.accept(requestedSpec);
        } else {
            pendingQueueRequest = requestedSpec;
            setSpec(requestedSpec);
        }
    }

    public void setSpec(AoTDStableStructureSpec spec) {
        if (Objects.equals(this.spec, spec)) return;
        this.spec = spec;
        currentImpl = null;
        createUI();
    }

    @Override
    public void clearUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }
        showUpgradePath = null;
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
        if (showUpgradePath == null || !showUpgradePath.isChecked() || spec == null) return;

        showUpgradePath.setChecked(false);

        StableStructureUpgradePathUI popup = new StableStructureUpgradePathUI(spec, systemAPI, currentImpl, this::requestQueue);
        popup.showLeftOf(showUpgradePath);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }

    @Override
    public void buttonPressed(Object buttonId) {
    }
}
