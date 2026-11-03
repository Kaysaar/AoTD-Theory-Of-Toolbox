package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.InstantPopUpUI;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public class StableStructureWidgetButtons extends InstantPopUpUI {

    public static final String AI_CORE = "ai_core";
    public static final String UPGRADE = "upgrade";
    public static final String DOWNGRADE = "downgrade:";
    public static final String CANCEL_UPGRADE = "cancel_upgrade";
    public static final String SHUTDOWN = "shutdown";

    private static final float BUTTON_HEIGHT = 24f;
    private static final float BUTTON_PAD = 1f;
    private static final float PANEL_PAD = 6f;

    StableStructureWidget widget;
    CustomPanelAPI mainPanel;

    LinkedHashMap<String, BetterAreaCheckbox> buttons = new LinkedHashMap<>();

    public StableStructureWidgetButtons(StableStructureWidget widget) {
        this.widget = widget;
        this.rendererBorder = new UILinesRenderer(0f) {
            @Override
            public void render(float alphaMult) {

            }
        };
    }

    @Override
    public Color getColorBG() {
        return Color.BLACK;
    }

    @Override
    public void renderBelow(float alphaMult) {
        super.renderBelow(alphaMult);
    }

    private LinkedHashMap<String, String> getOptions() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();

        AoTDStableStructureImpl impl = widget.getStructure();

        if (impl == null || !impl.isCurrentImplementation() || !impl.isPlayerOwned()) {
            return options;
        }

        if (impl.canMountAICores()) {
            options.put(AI_CORE, "Assign AI Core...");
        }

        if (impl.isBuilding() && !impl.isUpgrading()) {
            options.put(SHUTDOWN, "Stop Construction");
            return options;
        }

        if (impl.isUpgrading()) {
            options.put(CANCEL_UPGRADE, "Cancel Upgrade");
            options.put(SHUTDOWN, "Shut down...");
            return options;
        }

        if (AoTDStableStructureSpecManager.hasUpgrades(impl.id)) {
            options.put(UPGRADE, "Choose Upgrade...");
        }

        AoTDStableStructureSpec spec = impl.getSpec();

        if (spec != null && AoTDStableStructureConstruction.canDowngrade(impl)) {
            String parentId = spec.getDowngrade();
            String name = Global.getSettings().getCustomEntitySpec(parentId) != null
                    ? Global.getSettings().getCustomEntitySpec(parentId).getDefaultName()
                    : parentId;

            options.put(DOWNGRADE, "Downgrade to " + name + "...");
        }

        if (AoTDStableStructureConstruction.canShutDown(impl)) options.put(SHUTDOWN, "Shut down...");

        return options;
    }

    @Override
    public float createUIMockup(CustomPanelAPI panelAPI) {
        int count = getOptions().size();

        return PANEL_PAD * 2f
                + count * BUTTON_HEIGHT
                + Math.max(0, count - 1) * BUTTON_PAD - 10f;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        buttons.clear();

        float width = panelAPI.getPosition().getWidth();
        float height = panelAPI.getPosition().getHeight();

        mainPanel = panelAPI.createCustomPanel(width, height, null);

        TooltipMakerAPI tooltip = mainPanel.createUIElement(
                width,
                height,
                false
        );

        LinkedHashMap<String, String> options = getOptions();

        int downgradeCount = 0;

        for (String id : options.keySet()) {
            if (id.startsWith(DOWNGRADE)) {
                downgradeCount++;
            }
        }

        float currY = 1f;

        for (Map.Entry<String, String> entry : options.entrySet()) {
            String id = entry.getKey();
            String text = entry.getValue();

            int keyBind = -1;

            if (AI_CORE.equals(id) && AssignAICoreStructure.canAssignAICore(widget.getStructure())) {
                keyBind = Keyboard.KEY_G;
            } else if (UPGRADE.equals(id)) {
                keyBind = Keyboard.KEY_U;
            } else if (SHUTDOWN.equals(id)) {
                keyBind = Keyboard.KEY_W;
            } else if (id.startsWith(DOWNGRADE) && downgradeCount == 1) {
                keyBind = Keyboard.KEY_D;
            }

            BetterAreaCheckbox button;

            if (keyBind != -1) {
                button = new BetterAreaCheckbox(
                        width - PANEL_PAD * 2f,
                        BUTTON_HEIGHT,
                        id,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        text,
                        keyBind
                );
            } else {
                button = new BetterAreaCheckbox(
                        width - PANEL_PAD * 2f,
                        BUTTON_HEIGHT,
                        id,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        text
                );
            }

            button.createUI();
            if (AI_CORE.equals(id)) {
                boolean enabled = AssignAICoreStructure.canAssignAICore(widget.getStructure());
                button.mainButton.setEnabled(enabled);
            }

            tooltip.addCustom(button.getMainPanel(), 0f)
                    .getPosition().inTL(PANEL_PAD, currY);

            buttons.put(id, button);

            currY += BUTTON_HEIGHT + BUTTON_PAD;
        }

        mainPanel.addUIElement(tooltip).inTL(0f, 0f);
        panelAPI.addComponent(mainPanel).inTL(0f, 0f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!reachedMaxHeight) {
            return;
        }

        for (Map.Entry<String, BetterAreaCheckbox> entry : buttons.entrySet()) {
            BetterAreaCheckbox button = entry.getValue();
            if (!button.isChecked()) {
                continue;
            }
            button.setChecked(false);
            String action = entry.getKey();
            AoTDStableStructureImpl impl = widget.getStructure();
            if (impl == null || !impl.isCurrentImplementation() || !impl.isPlayerOwned()) {
                forceDismiss();
                return;
            }
            if (action.equals(AI_CORE)) {
                if (AssignAICoreStructure.canAssignAICore(impl)) {
                    AssignAICoreStructure structure = new AssignAICoreStructure(widget);
                    AshMisc.initPopUpDialog(structure, 520, 530);
                }
                forceDismiss();
                return;
            }
            if (action.equals(CANCEL_UPGRADE)) {
                if (AoTDStableStructureConstruction.canCancelUpgrade(impl)) {
                    CancelUpgradeInfoPopUp structure = new CancelUpgradeInfoPopUp(widget);
                    AshMisc.initPopUpDialog(structure, 700, 205);
                }
                forceDismiss();
                return;
            }
            if (action.equals(SHUTDOWN)) {
                if (AoTDStableStructureConstruction.canCancelConstruction(impl)) {
                    StopConstructionInfoPopUp structure = new StopConstructionInfoPopUp(widget);
                    AshMisc.initPopUpDialog(structure, 600, 170);
                } else if (AoTDStableStructureConstruction.canShutDown(impl)) {
                    ShutdownInfoPopUp structure = new ShutdownInfoPopUp(widget);
                    AshMisc.initPopUpDialog(structure, 600, 170);
                }
                forceDismiss();
                return;
            }
            if (action.equals(DOWNGRADE)) {
                if (AoTDStableStructureConstruction.canDowngrade(widget.getStructure())) {
                    DowngradeInfoPopUp structure = new DowngradeInfoPopUp(widget);
                    AshMisc.initPopUpDialog(structure, 600, 170);
                }
                this.forceDismiss();
                return;
            }


            return;
        }
    }

    @Override
    public void onExit() {
        buttons.clear();
        mainPanel = null;
        widget.createUI();
        super.onExit();
    }
}
