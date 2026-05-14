package data.kaysaar.aotd.tot.ui.economy.commoditydata.table;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.GraphPeriodChosenButton;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AoTDCommodityProductionDataTable extends UITableImpl {
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();
    public String factionId;
    public static float seperation =1f;
    float currYPos = 0;
    ButtonAPI lastCheckedState;
    int months = 1;
    public String currCommodityChecked =  null;
    public ButtonAPI buttonCommodity, buttonGraph, buttonSupply, buttonDemand,buttonNet;
    static {
        widthMap.put("commodity", 210);
            widthMap.put("graph", 280);
        widthMap.put("supply", 120);
        widthMap.put("demand", 120);
    }
    public static void resizeToNewWidth(float width) {
        // Minimum width = current total (including the +1 per column already used in getWidth()).
        float minWidthF = getWidth();
        if (width <= minWidthF) {
            return; // can't be smaller than minimum
        }

        // Extra pixels to distribute.
        int extra = (int) Math.floor(width - minWidthF + 1e-6f);
        if (extra <= 0) return;

        // Per-column caps for the "commodity/supply/demand" redistribution stage.
        int capCommodity = 90;
        int capSupply = 30;
        int capDemand = 30;

        int addCommodity = 0;
        int addSupply = 0;
        int addDemand = 0;

        // Water-filling style equal distribution with caps:
        // distribute 1+ pixels at a time evenly among columns that still have cap remaining.
        while (extra > 0) {
            int active = 0;
            if (addCommodity < capCommodity) active++;
            if (addSupply < capSupply) active++;
            if (addDemand < capDemand) active++;

            if (active == 0) break;

            // Try to give an equal chunk; ensure progress even when extra < active.
            int share = extra / active;
            if (share <= 0) share = 1;

            if (extra > 0 && addCommodity < capCommodity) {
                int give = Math.min(share, capCommodity - addCommodity);
                give = Math.min(give, extra);
                addCommodity += give;
                extra -= give;
            }
            if (extra > 0 && addSupply < capSupply) {
                int give = Math.min(share, capSupply - addSupply);
                give = Math.min(give, extra);
                addSupply += give;
                extra -= give;
            }
            if (extra > 0 && addDemand < capDemand) {
                int give = Math.min(share, capDemand - addDemand);
                give = Math.min(give, extra);
                addDemand += give;
                extra -= give;
            }
        }

        // Anything left goes to graph.
        int addGraph = extra;

        // Apply to map (preserve existing widths).
        widthMap.put("commodity", widthMap.get("commodity") + addCommodity);
        widthMap.put("supply",    widthMap.get("supply")    + addSupply);
        widthMap.put("demand",    widthMap.get("demand")    + addDemand);
        widthMap.put("graph",     widthMap.get("graph")     + addGraph);
    }
    public static int getStartingX(String id) {
        int x = 0;
        for (Map.Entry<String, Integer> value : widthMap.entrySet()) {

            if (id.equals(value.getKey())) {
                break;
            }
            x += value.getValue() + 1;
        }
        return x;
    }
    public static float getWidth(){
        float x = 0;
        for (Map.Entry<String, Integer> value : widthMap.entrySet()) {
            x += value.getValue() + 1;

        }
        return x;
    }
    public AoTDCommodityProductionDataTable(float width, float height, boolean doesHaveScroller, float xCord, float yCord,String factionId,int months) {
        super(width, height, doesHaveScroller, xCord, yCord);
        this.factionId = factionId;
        this.months = months;
        List<String>commodities = AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSuppliedSorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                CommoditySpecAPI o1sp = Global.getSettings().getCommoditySpec(o1);
                CommoditySpecAPI o2sp = Global.getSettings().getCommoditySpec(o2);
                return Float.compare(o1sp.getEconomyTier(),o2sp.getEconomyTier());
            }
        });
        if(dropDownButtons.isEmpty()){
            for (String s : commodities) {
                AoTDCommodityProductionDropDownButton bt = new AoTDCommodityProductionDropDownButton(this,width-1,50,0,0,s,factionId,months);
                dropDownButtons.add(bt);
            }
        }
    }
    @Override
    public void createTable() {
        super.createTable();
        tooltipOfImpl.addSpacer(0f).getPosition().inTL(-4,0);
        for (DropDownButton dropDownButton : dropDownButtons) {
            dropDownButton.resetUI();
            dropDownButton.createUI();
            tooltipOfImpl.addCustom(dropDownButton.getPanelOfImpl(),2f);
        }
        for (DropDownButton dropDownButton : dropDownButtons) {
            if(dropDownButton instanceof AoTDCommodityProductionDropDownButton bt){
                if(AshMisc.isStringValid(currCommodityChecked)&&bt.commodityId.equals(currCommodityChecked)){
                    dropDownButton.mainButton.mainButton.highlight();
                }else{
                    dropDownButton.mainButton.mainButton.unhighlight();
                }
            }
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
        buttonCommodity = tooltipOfButtons.addAreaCheckbox("Commodity", SortingState.NON_INITIALIZED, base, bg, bright, widthMap.get("commodity"), 20, 0f);
        if(months==1){
            buttonGraph = tooltipOfButtons.addAreaCheckbox("Graph Data (Last "+GraphPeriodChosenButton.getLabelStringForMonth(months)+")", SortingState.NON_INITIALIZED, base, bg, bright, widthMap.get("graph"), 20, 0f);

        }
        else{
            buttonGraph = tooltipOfButtons.addAreaCheckbox("Graph Data (Last "+ GraphPeriodChosenButton.getNumber(months) +" "+GraphPeriodChosenButton.getLabelStringForMonth(months)+")", SortingState.NON_INITIALIZED, base, bg, bright, widthMap.get("graph"), 20, 0f);

        }
        buttonSupply = tooltipOfButtons.addAreaCheckbox("Production", SortingState.NON_INITIALIZED, base, bg, bright, widthMap.get("supply"), 20, 0f);
        buttonDemand = tooltipOfButtons.addAreaCheckbox("Demand", SortingState.NON_INITIALIZED, base, bg, bright, widthMap.get("demand"), 20, 0f);
        buttonCommodity.getPosition().inTL(seperation, 0);
        buttonGraph.getPosition().rightOfMid(buttonCommodity,seperation);
        buttonSupply.getPosition().rightOfMid(buttonGraph,seperation);
        buttonDemand.getPosition().rightOfMid(buttonSupply,seperation);
        buttonGraph.setClickable(false);
        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
        lastCheckedState = buttonCommodity;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        for (DropDownButton dropDownButton : dropDownButtons) {
            if(dropDownButton instanceof AoTDCommodityProductionDropDownButton bt){
                if(AshMisc.isStringValid(currCommodityChecked)&&bt.commodityId.equals(currCommodityChecked)){
                    dropDownButton.mainButton.mainButton.highlight();
                }else{
                    dropDownButton.mainButton.mainButton.unhighlight();
                }
            }
        }
    }

    @Override
    public void reportButtonPressed(CustomButton buttonPressed) {
        if(buttonPressed instanceof AoTDCommodityProductionButton bt){
            this.currCommodityChecked = bt.getData().commodityId;
        }
    }
}
