package data.kaysaar.aotd.tot.ui.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;


import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class CommodityGraphComponent implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel,contentPanel;
    int months;
    String commodityId;
    String factionId = Factions.HEGEMONY;
    boolean showAll = false;
    public CommodityGraphComponent(float width, float height,String commodityId,String factionId, int months) {
        mainPanel = Global.getSettings().createCustom(width,height,this);
        this.commodityId = commodityId;
        this.factionId = factionId;
        this.months = months;
        createUI();
    }
    private void addFromEnd(ArrayList<Integer> target, ArrayList<Integer> source) {
        int offset = target.size() - source.size();

        for (int i = 0; i < source.size(); i++) {
            int targetIndex = offset + i;
            target.set(targetIndex, target.get(targetIndex) + source.get(i));
        }
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
        ArrayList<Integer> prodData = new ArrayList<>();
        ArrayList<Integer> demData = new ArrayList<>();
        if (factionId.equals(Factions.NEUTRAL)) {
            String factionWithIdHighestProd = null;
            String factionWithIdHighestDem = null;

            for (Map.Entry<String, AoTDFactionTradeData> tradeDataEntry :
                    AoTDTradeManager.getInstance().getAllFactionTradeData().entrySet()) {

                ArrayList<Integer> prod = AoTDTradeManager.getInstance()
                        .getFactionTradeData(tradeDataEntry.getKey())
                        .getProductionFromMonths(months, commodityId);

                ArrayList<Integer> dem = AoTDTradeManager.getInstance()
                        .getFactionTradeData(tradeDataEntry.getKey())
                        .getDemandFromMonths(months, commodityId);

                if (prod.size() > prodData.size()) {
                    prodData = prod;
                    factionWithIdHighestProd = tradeDataEntry.getKey();
                }

                if (dem.size() > demData.size()) {
                    demData = dem;
                    factionWithIdHighestDem = tradeDataEntry.getKey();
                }
            }

            for (Map.Entry<String, AoTDFactionTradeData> tradeDataEntry :
                    AoTDTradeManager.getInstance().getAllFactionTradeData().entrySet()) {

                String id = tradeDataEntry.getKey();

                if (!id.equals(factionWithIdHighestProd)) {
                    ArrayList<Integer> prod = AoTDTradeManager.getInstance()
                            .getFactionTradeData(id)
                            .getProductionFromMonths(months, commodityId);

                    addFromEnd(prodData, prod);
                }

                if (!id.equals(factionWithIdHighestDem)) {
                    ArrayList<Integer> dem = AoTDTradeManager.getInstance()
                            .getFactionTradeData(id)
                            .getDemandFromMonths(months, commodityId);

                    addFromEnd(demData, dem);
                }
            }
        }
        else{
             prodData =
                    AoTDTradeManager.getInstance().getFactionTradeData(factionId)
                            .getProductionFromMonths(months,commodityId);

            demData =
                    AoTDTradeManager.getInstance().getFactionTradeData(factionId)
                            .getDemandFromMonths(months,commodityId);

        }
        prodData.add(AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(commodityId,factionId));
        demData.add(AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(commodityId,factionId));


        float highest = 0f;
        for (Integer v : demData) if (v != null && v > highest) highest = v;
        for (Integer v : prodData) if (v != null && v > highest) highest = v;

        float w = contentPanel.getPosition().getWidth();
        float h = contentPanel.getPosition().getHeight();

        ArrayList<Float> supplyYs = SupplyDemandAreaGraph.createSeriesForGraph(h, prodData, highest);
        ArrayList<Float> demandYs = SupplyDemandAreaGraph.createSeriesForGraph(h, demData, highest);

        SupplyDemandAreaGraph graph = new SupplyDemandAreaGraph(w, h, supplyYs, demandYs);
        graph.setColors(Misc.getPositiveHighlightColor().darker(),new Color(220, 155, 33), Misc.getNegativeHighlightColor().darker());
        graph.setAACrossCutPx(0f);      // creates visible green->orange->red wedge
        graph.setCrossingOverlapPx(0f);
        graph.setAAFeatherPx(1.25f);



        contentPanel.addComponent(graph.getMainPanel()).inTL(0, 0);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
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
