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
import data.kaysaar.aotd.tot.stablestructures.StableStructureOptionProvider;
import data.kaysaar.aotd.tot.stablestructures.StableStructureOptionProvider.StructureOptionData;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class StableStructureWidgetButtons extends InstantPopUpUI {

    public static final String AI_CORE = "ai_core";
    public static final String UPGRADE = "upgrade";
    public static final String DOWNGRADE = "downgrade:";
    public static final String CANCEL_UPGRADE = "cancel_upgrade";
    public static final String SHUTDOWN = "shutdown";
    public static final String CUSTOM = "custom:";

    private static final float BUTTON_HEIGHT = 24f;
    private static final float BUTTON_PAD = 1f;
    private static final float PANEL_PAD = 6f;

    StableStructureWidget widget;
    CustomPanelAPI mainPanel;

    LinkedHashMap<String, BetterAreaCheckbox> buttons = new LinkedHashMap<>();
    LinkedHashMap<String, StructureOptionData> customOptions = new LinkedHashMap<>();

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

    public boolean hasOptions() {
        return !getOptions().isEmpty();
    }

    private void addCustomOptions(LinkedHashMap<String, String> options, AoTDStableStructureImpl impl) {
        int index = 0;
        for (StableStructureOptionProvider provider : Global.getSector().getListenerManager().getListeners(StableStructureOptionProvider.class)) {
            List<StructureOptionData> result = provider.getStructureOptions(impl);
            if (result == null) continue;
            for (StructureOptionData data : result) {
                if (data == null || data.text == null || data.text.isEmpty()) continue;
                data.provider = provider;
                String key = CUSTOM + index++;
                customOptions.put(key, data);
                options.put(key, data.text);
            }
        }
    }

    private LinkedHashMap<String, String> getOptions() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        customOptions.clear();

        AoTDStableStructureImpl impl = widget.getStructure();
        if (impl == null || !impl.isCurrentImplementation() || !impl.isPlayerOwned()) return options;

        if (impl.canMountAICores()) {
            options.put(AI_CORE, "Assign AI Core...");
        }

        if (impl.isBuilding() && !impl.isUpgrading()) {
            addCustomOptions(options, impl);
            options.put(SHUTDOWN, "Stop Construction");
            return options;
        }

        if (impl.isUpgrading()) {
            options.put(CANCEL_UPGRADE, "Cancel Upgrade");
            addCustomOptions(options, impl);
            if (AoTDStableStructureConstruction.canShutDown(impl)) options.put(SHUTDOWN, "Shut down...");
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

        addCustomOptions(options, impl);
        if (AoTDStableStructureConstruction.canShutDown(impl)) options.put(SHUTDOWN, "Shut down...");
        return options;
    }

    private void addButtonTooltip(TooltipMakerAPI tooltip, BetterAreaCheckbox button, String id) {
        final StructureOptionData data = customOptions.get(id);
        final String optionId = id;
        tooltip.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 400f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                if (data != null) {
                    data.provider.createTooltip(data, tooltip, 400f);
                    return;
                }

                AoTDStableStructureImpl impl = widget.getStructure();
                if (impl == null) return;

                if (AI_CORE.equals(optionId)) {
                    tooltip.addPara("Assign or manage the AI core installed in this structure.", 0f);
                } else if (UPGRADE.equals(optionId)) {
                    tooltip.addPara("Choose an available upgrade for this structure.", 0f);
                } else if (CANCEL_UPGRADE.equals(optionId)) {
                    tooltip.addPara("Cancel the current upgrade for a refund of %s.", 0f,
                            Misc.getHighlightColor(), Misc.getDGSCredits(AoTDStableStructureConstruction.getCancelRefund(impl)));
                } else if (DOWNGRADE.equals(optionId)) {
                    tooltip.addPara("Downgrade this structure for a refund of %s.", 0f,
                            Misc.getHighlightColor(), Misc.getDGSCredits(AoTDStableStructureConstruction.getDowngradeRefund(impl)));
                } else if (SHUTDOWN.equals(optionId)) {
                    if (AoTDStableStructureConstruction.canCancelConstruction(impl)) {
                        tooltip.addPara("Stop construction for a refund of %s.", 0f,
                                Misc.getHighlightColor(), Misc.getDGSCredits(AoTDStableStructureConstruction.getCancelRefund(impl)));
                    } else {
                        tooltip.addPara("Shut down this structure for a refund of %s. This takes effect immediately.", 0f,
                                Misc.getHighlightColor(), Misc.getDGSCredits(AoTDStableStructureConstruction.getShutdownRefund(impl)));
                    }
                }
            }
        }, button.getMainPanel(), TooltipMakerAPI.TooltipLocation.RIGHT, false);
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

            StructureOptionData data = customOptions.get(id);
            Color buttonColor = data != null && data.color != null ? data.color : Misc.getBasePlayerColor();
            BetterAreaCheckbox button;

            if (keyBind != -1) {
                button = new BetterAreaCheckbox(
                        width - PANEL_PAD * 2f,
                        BUTTON_HEIGHT,
                        id,
                        0f,
                        buttonColor,
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
                        buttonColor,
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        text
                );
            }

            button.createUI();
            if (data != null) {
                button.mainButton.setEnabled(data.enabled);
                button.mainButton.setClickable(data.enabled);
            }
            if (AI_CORE.equals(id)) {
                boolean enabled = AssignAICoreStructure.canAssignAICore(widget.getStructure());
                button.mainButton.setEnabled(enabled);
                button.mainButton.setClickable(enabled);
            }

            tooltip.addCustom(button.getMainPanel(), 0f)
                    .getPosition().inTL(PANEL_PAD, currY);

            buttons.put(id, button);
            addButtonTooltip(tooltip, button, id);

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

            if (AI_CORE.equals(entry.getKey())) {
                boolean enabled = AssignAICoreStructure.canAssignAICore(widget.getStructure());
                button.mainButton.setEnabled(enabled);
                button.mainButton.setClickable(enabled);
                if (!enabled) {
                    button.setChecked(false);
                    continue;
                }
            }

            StructureOptionData data = customOptions.get(entry.getKey());
            if (data != null) {
                button.mainButton.setEnabled(data.enabled);
                button.mainButton.setClickable(data.enabled);
                if (!data.enabled) {
                    button.setChecked(false);
                    continue;
                }
            }

            if (!button.isChecked()) {
                continue;
            }

            String action = entry.getKey();
            AoTDStableStructureImpl impl = widget.getStructure();
            if (impl == null || !impl.isCurrentImplementation() || !impl.isPlayerOwned()) {
                forceDismiss();
                return;
            }
            if (action.startsWith(CUSTOM)) {
                button.setChecked(false);
                forceDismiss();
                if (data != null && data.provider != null && data.enabled) {
                    // Providers may call widget.requestSystemStructuresRefresh() after changing the sector.
                    data.provider.optionSelected(data, widget);
                }
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
                    AshMisc.initPopUpDialog(structure, 700, 205);
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
            button.setChecked(false);

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
