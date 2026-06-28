package data.kaysaar.aotd.tot.ui.starsystems;

import ashlib.data.plugins.coreui.CommandTabTracker;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class HoldingsUIPanel extends CommandUIPlugin {

    UILinesRenderer renderer;
    StarSystemHoldingsUI starSystemAndPlanetUI;
    Object original;
    public static boolean sentSignalForUpdate = false;

    public HoldingsUIPanel(float width, float height) {
        super(width, height);
    }

    @Override
    public boolean doesPlayCustomSound() {
        return false;
    }

    @Override
    public boolean doesPlayCustomSoundWhenEnteredEntireTab() {
        return true;
    }

    @Override
    public String getTabStateId() {
        return "holdings";
    }

    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public void init(String panelToShowcase, Object data) {
        renderer = new UILinesRenderer(0f);
        original = ReflectionUtilis.invokeMethodWithAutoProjection("getColoniesPanel",data);
        this.panelForPlugins = mainPanel.createCustomPanel(mainPanel.getPosition().getWidth(), mainPanel.getPosition().getHeight() - 45, null);
        if (!AshMisc.isStringValid(panelToShowcase)) {
            panelToShowcase = "star systems & colonies";
        }
        createButtonsAndMainPanels();
        for (Map.Entry<ButtonAPI, CustomPanelAPI> buttons : panelMap.entrySet()) {
            if (buttons.getKey().getText().toLowerCase().contains(panelToShowcase)) {
                currentlyChosen = buttons.getKey();
                break;
            }
        }


        if (currentlyChosen != null) {
            panelForPlugins.addComponent(panelMap.get(currentlyChosen)).inTL(0, 0);
        }

        this.mainPanel.addComponent(panelForPlugins).inTL(0, 35);
        renderer.setPanel(panelForPlugins);
    }

    public void clearUI(boolean clearMusic) {
        ;
        panelMap.clear();
        mainPanel.removeComponent(panelForPlugins);
        if (clearMusic) {
            pauseSound();
        }

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


    public void resetCurrentPlugin(ButtonAPI newButton) {
        if (currentlyChosen != null) {
            this.panelForPlugins.removeComponent(panelMap.get(currentlyChosen));
        }
        currentlyChosen = newButton;
        this.panelForPlugins.addComponent(panelMap.get(currentlyChosen)).inTL(0, 0);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (CommandTabTracker.lockedState) {
                if (event.isConsumed()) continue;
                if (event.getEventValue() == Keyboard.KEY_ESCAPE && !event.isMouseEvent()) {
                    event.consume();
                }
            }

        }
    }

    @Override
    public void buttonPressed(Object buttonId) {

    }

    @Override
    public void playSound(Object data) {
    }

    public void pauseSound() {
    }

    public void createButtonsAndMainPanels() {
        ButtonAPI research, megastructures, customProd, sp;
        this.buttonPanel = this.mainPanel.createCustomPanel(mainPanel.getPosition().getWidth(), 25, null);
        UILinesRenderer renderer = new UILinesRenderer(0f);
        CustomPanelAPI panelHelper = this.buttonPanel.createCustomPanel(490, 0.5f, renderer);
//        renderer.setPanel(panelHelper);
        TooltipMakerAPI buttonTooltip = buttonPanel.createUIElement(mainPanel.getPosition().getWidth(), 20, false);
        Color base, bg;
        base = Global.getSector().getPlayerFaction().getBaseUIColor();
        bg = Global.getSector().getPlayerFaction().getDarkUIColor();
        customProd = buttonTooltip.addButton("Star Systems & Colonies", null, base, bg, Alignment.MID, CutStyle.TOP, 205, 20, 0f);
        research = buttonTooltip.addButton("Warehouses", null, base, bg, Alignment.MID, CutStyle.TOP, 150, 20, 0f);

        customProd.setShortcut(Keyboard.KEY_R, false);
        customProd.getPosition().inTL(0, 0);

        research.setShortcut(Keyboard.KEY_W, false);
        research.getPosition().inTL(206, 0);

        buttonPanel.addUIElement(buttonTooltip).inTL(0, 0);
        buttonPanel.addComponent(panelHelper).inTL(0, 20);
        mainPanel.addComponent(buttonPanel).inTL(0, 10);
        insertStarSystemPanel(customProd);
    }

    private void insertStarSystemPanel(ButtonAPI tiedButton) {
        if (starSystemAndPlanetUI == null) {
            starSystemAndPlanetUI = new StarSystemHoldingsUI(panelForPlugins.getPosition().getWidth()-5, panelForPlugins.getPosition().getHeight(),original);
        }

        panelMap.put(tiedButton, starSystemAndPlanetUI.getMainPanel());
    }


    public void playSound(ButtonAPI button) {

    }
}
