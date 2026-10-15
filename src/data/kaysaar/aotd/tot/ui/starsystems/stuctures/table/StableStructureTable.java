
package data.kaysaar.aotd.tot.ui.starsystems.stuctures.table;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.HoldingsUtilis;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureManager;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.ui.starsystems.components.StarSystemHoldingDropDown;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.StableStructureConstructionDialog;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class StableStructureTable extends UITableImpl {

    public ButtonAPI buttonName, buttonBuildingTime, buttonBuildCost;
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();
    StarSystemAPI starSystemAPI;

    float currYPos = 0f;
   public StableStructureButton currentlyChosen;
    static {
        widthMap.put("name", 290);
        widthMap.put("buildingTime", 80);
        widthMap.put("buildCost", 261);
    }

    public static void reDestributeAdditionalWidth(float additionalWidth) {
        if (additionalWidth <= 0) {
            return;
        }

        int remaining = (int) additionalWidth;
        widthMap.put("buildCost", widthMap.get("buildCost") + remaining);
    }

    public static int getWidth() {
        int width = 0;

        for (Integer value : widthMap.values()) {
            width += value + 1;
        }

        return width - 1;
    }

    public static int getStartingX(String id) {
        int x = 0;

        for (Map.Entry<String, Integer> entry : widthMap.entrySet()) {
            if (id.equals(entry.getKey())) {
                break;
            }

            x += entry.getValue() + 1;
        }

        return x;
    }

    public StableStructureTable(float width, float height, boolean doesHaveScroller, float xCord, float yCord,StarSystemAPI systemAPI) {
        super(width, height, doesHaveScroller, xCord, yCord);
        this.starSystemAPI = systemAPI;
        if (dropDownButtons.isEmpty()) {

            for (AoTDStableStructureSpec spec : AoTDStableStructureManager.getAllBaseStructures()) {
                StableStructureDropDownButton button = new StableStructureDropDownButton(this, width-16, 63, 0, 0, spec,starSystemAPI);
                dropDownButtons.add(button);
            }

        }
    }
    @Override
    public void createTable() {
        super.createTable();
        for (DropDownButton dropDownButton : dropDownButtons) {
            StableStructureDropDownButton button = (StableStructureDropDownButton) dropDownButton;

            button.resetUI();
            button.createUI();
            tooltipOfImpl.addCustom(dropDownButton.getPanelOfImpl(), 2f);
        }

        panelToWorkWith.addUIElement(tooltipOfImpl).inTL(0, 0);
        if (tooltipOfImpl.getExternalScroller() != null) {
            if (currYPos + panelToWorkWith.getPosition().getHeight() - 2 >= tooltipOfImpl.getHeightSoFar()) {
                currYPos = tooltipOfImpl.getHeightSoFar() - panelToWorkWith.getPosition().getHeight() + 2;
            }
            if (currYPos <= 0) {
                currYPos = 0;
            }
            tooltipOfImpl.getExternalScroller().setYOffset(currYPos);
        }

        mainPanel.addComponent(panelToWorkWith).inTL(0, 22);
    }

    @Override
    public void createSections() {
        Color base = Misc.getBasePlayerColor();
        Color bg = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();

        buttonName = tooltipOfButtons.addAreaCheckbox(
                "Name", "name", base, bg, bright,
                widthMap.get("name"), 20, 0f
        );

        buttonBuildingTime = tooltipOfButtons.addAreaCheckbox(
                "Build time", "buildingTime", base, bg, bright,
                widthMap.get("buildingTime"), 20, 0f
        );

        buttonBuildCost = tooltipOfButtons.addAreaCheckbox(
                "Build Cost", "buildCost", base, bg, bright,
                widthMap.get("buildCost"), 20, 0f
        );

        buttonName.getPosition().inTL(10, 0);
        buttonBuildingTime.getPosition().inTL(
                buttonName.getPosition().getWidth() + 11, 0
        );

        float x = buttonName.getPosition().getWidth()
                + 11
                + buttonBuildingTime.getPosition().getWidth()
                + 1;

        buttonBuildCost.getPosition().inTL(x, 0);

        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
    }


    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (tooltipOfImpl != null && tooltipOfImpl.getExternalScroller() != null) {
            currYPos = tooltipOfImpl.getExternalScroller().getYOffset();
        }

        if (buttonName != null && buttonName.isChecked()) {
            buttonName.setChecked(false);
            // TODO: Sort structures by name
        }

        if (buttonBuildingTime != null && buttonBuildingTime.isChecked()) {
            buttonBuildingTime.setChecked(false);
            // TODO: Sort structures by building time
        }

        if (buttonBuildCost != null && buttonBuildCost.isChecked()) {
            buttonBuildCost.setChecked(false);
            // TODO: Sort structures by build cost
        }
    }

    @Override
    public void reportButtonPressed(CustomButton buttonPressed) {
        if(buttonPressed instanceof StableStructureButton bt){
            currentlyChosen = bt;
        }
    }
}
