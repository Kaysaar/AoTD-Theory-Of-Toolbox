package data.kaysaar.aotd.tot.listeners;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;
import data.kaysaar.aotd.tot.listeners.ui.AoTDPointerToStarSystem;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AoTDPriceTableRemoval implements ExtendedUIPanelPlugin {
    TooltipMakerAPI originalTooltip;
    CustomPanelAPI mainPanel;
    boolean removed = false;
    float yAdded;
    String commodityId;
    float prevY;

    public AoTDPriceTableRemoval(TooltipMakerAPI tooltipMakerAPI, String commodityId) {
        this.originalTooltip = tooltipMakerAPI;
        this.commodityId = commodityId;
        this.mainPanel = Global.getSettings().createCustom(1, 1, this);
        yAdded = originalTooltip.getHeightSoFar();
        final PositionAPI pos = originalTooltip.getPosition();
        prevY = (int) (pos.getY() + pos.getHeight());
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
        if (removed) return;

        if (originalTooltip.getPrev() instanceof LabelAPI label) {
            if (label.getText().contains("Per unit prices assume")) {
                UIPanelAPI holder = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(originalTooltip).get(0);
                List<UIComponentAPI> comps = ReflectionUtilis.getChildrenCopy((UIPanelAPI) holder);
                int starter = comps.size();
                int toRemove = 6;

                ArrayList<MarketAPI> commsBuy = new ArrayList<>();
                ArrayList<MarketAPI> commsSell = new ArrayList<>();

                for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (marketAPI.isHidden()) continue;

                    AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI, commodityId);

                    int demand = com.getSupplyDemandData().getTotalRawUnitsFromDemand();
                    if (demand > 0) {
                        commsSell.add(marketAPI);
                    }

                    int am = AoTDOpenMarketPlugin.getStockPileToolbox(com);
                    if (am > 100) {
                        commsBuy.add(marketAPI);
                    }
                }

                commsSell.sort(new Comparator<MarketAPI>() {
                    public int compare(MarketAPI var1x, MarketAPI var2x) {
                        int price1 = getSellPricePerUnit(var1x, commodityId);
                        int price2 = getSellPricePerUnit(var2x, commodityId);

                        int priceCompare = Integer.compare(price2, price1);
                        if (priceCompare != 0) {
                            return priceCompare;
                        }

                        AoTDCommodityOnMarket com1 = AoTDCommodityOnMarket.getComMarketInstanceSave(var1x, commodityId);
                        AoTDCommodityOnMarket com2 = AoTDCommodityOnMarket.getComMarketInstanceSave(var2x, commodityId);

                        int demand1 = com1.getSupplyDemandData().getTotalRawUnitsFromDemand();
                        int demand2 = com2.getSupplyDemandData().getTotalRawUnitsFromDemand();

                        return Integer.compare(demand2, demand1);
                    }
                });

                commsBuy.sort(new Comparator<MarketAPI>() {
                    public int compare(MarketAPI var1x, MarketAPI var2x) {
                        int price1 = getBuyPricePerUnit(var1x, commodityId);
                        int price2 = getBuyPricePerUnit(var2x, commodityId);

                        int priceCompare = Integer.compare(price1, price2);
                        if (priceCompare != 0) {
                            return priceCompare;
                        }

                        int available1 = getAvailableForBuy(var1x, commodityId);
                        int available2 = getAvailableForBuy(var2x, commodityId);

                        return Integer.compare(available2, available1);
                    }
                });

                if (commsSell.isEmpty()) {
                    toRemove -= 2;
                }
                if (commsBuy.isEmpty()) {
                    toRemove -= 2;
                }

                toRemove = Math.min(toRemove, comps.size());
                for (int i = 0; i < toRemove; i++) {
                    int index = comps.size() - 1 - i;
                    holder.removeComponent(comps.get(index));
                }

                removed = true;
                originalTooltip.addSpacer(0f).getPosition().inTL(5, yAdded);
                originalTooltip.setHeightSoFar(yAdded);

                if (!commsSell.isEmpty()) {
                    originalTooltip.addPara("Best places to sell:", 10f);
                    originalTooltip.beginTable(
                            Global.getSector().getPlayerFaction(),
                            20f,
                            "Price / 500*", 100,
                            "Demand", 70,
                            "Deficit", 70,
                            "Location", 230,
                            "Star System", 140,
                            "Dist (LY)", 80
                    );

                    for (int i = 0; i < Math.min(5, commsSell.size()); i++) {
                        MarketAPI bestMarket = commsSell.get(i);
                        AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(bestMarket, commodityId);

                        int deficit = com.getDeficitQuantity();
                        int demand = com.getSupplyDemandData().getTotalRawUnitsFromDemand();
                        int price = getSellPricePerUnit(bestMarket, commodityId);

                        String deficitString = "---";
                        Color deficitStrColor = Misc.getGrayColor();
                        if (deficit > 0) {
                            deficitString = Misc.getWithDGS(deficit);
                            deficitStrColor = Misc.getNegativeHighlightColor();
                        }

                        String factionName = AoTDToolboxMisc.capitalizeFirst(bestMarket.getFaction().getDisplayName());
                        String location = "In Hyperspace";
                        Color locationColor = Misc.getGrayColor();

                        if (bestMarket.getStarSystem() != null) {
                            StarSystemAPI system = bestMarket.getStarSystem();
                            location = system.getBaseName();
                            PlanetAPI star = system.getStar();
                            if (star != null) {
                                locationColor = star.getSpec().getIconColor();
                            }
                        }

                        float distanceLY = Misc.getDistanceToPlayerLY(bestMarket.getPrimaryEntity());

                        Object row = originalTooltip.addRow(
                                Color.ORANGE,
                                Misc.getDGSCredits(price),
                                Color.ORANGE,
                                Misc.getWithDGS(demand),
                                deficitStrColor,
                                deficitString,
                                Alignment.LMID,
                                bestMarket.getFaction().getBaseUIColor(),
                                bestMarket.getName() + " - " + factionName,
                                locationColor,
                                location,
                                Color.ORANGE,
                                Misc.getRoundedValueMaxOneAfterDecimal(distanceLY)
                        );

                        Color finalLocationColor = locationColor;
                        ReflectionUtilis.invokeMethodWithAutoProjection("setAfterCreate", row, new Runnable() {
                            @Override
                            public void run() {
                                AoTDPointerToStarSystem pointer = new AoTDPointerToStarSystem(
                                        (Float) ReflectionUtilis.invokeMethod("getHeight", row),
                                        bestMarket.getLocationInHyperspace(),
                                        finalLocationColor
                                );
                                Object columns = ReflectionUtilis.invokeMethodWithAutoProjection("getCol", row, 4);
                                PositionAPI pos = (PositionAPI) ReflectionUtilis.invokeMethodWithAutoProjection(
                                        "addComponent",
                                        columns,
                                        pointer.getMainPanel()
                                );
                                pos.inRMid(5f);
                            }
                        });
                    }

                    originalTooltip.addTable("", 0, 10f);
                }

                if (!commsBuy.isEmpty()) {
                    originalTooltip.addPara("Best places to buy:", 10f);
                    originalTooltip.beginTable(
                            Global.getSector().getPlayerFaction(),
                            20f,
                            "Price / 500*", 100,
                            "Available", 70,
                            "Excess", 70,
                            "Location", 230,
                            "Star System", 140,
                            "Dist (LY)", 80
                    );

                    for (int i = 0; i < Math.min(5, commsBuy.size()); i++) {
                        MarketAPI bestMarket = commsBuy.get(i);
                        AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(bestMarket, commodityId);

                        int excess = com.getExcessQuantity();
                        int demand = AoTDOpenMarketPlugin.getStockPileToolbox(com);
                        int price = getBuyPricePerUnit(bestMarket, commodityId);

                        String deficitString = "---";
                        Color deficitStrColor = Misc.getGrayColor();
                        if (excess > 0) {
                            deficitString = Misc.getWithDGS(excess);
                            deficitStrColor = Misc.getPositiveHighlightColor();
                        }

                        String factionName = AoTDToolboxMisc.capitalizeFirst(bestMarket.getFaction().getDisplayName());
                        String location = "In Hyperspace";
                        Color locationColor = Misc.getGrayColor();

                        if (bestMarket.getStarSystem() != null) {
                            StarSystemAPI system = bestMarket.getStarSystem();
                            location = system.getBaseName();
                            PlanetAPI star = system.getStar();
                            if (star != null) {
                                locationColor = star.getSpec().getIconColor();
                            }
                        }

                        float distanceLY = Misc.getDistanceToPlayerLY(bestMarket.getPrimaryEntity());

                        Object row = originalTooltip.addRow(
                                Color.ORANGE,
                                Misc.getDGSCredits(price),
                                Color.ORANGE,
                                Misc.getWithDGS(demand),
                                deficitStrColor,
                                deficitString,
                                Alignment.LMID,
                                bestMarket.getFaction().getBaseUIColor(),
                                bestMarket.getName() + " - " + factionName,
                                locationColor,
                                location,
                                Color.ORANGE,
                                Misc.getRoundedValueMaxOneAfterDecimal(distanceLY)
                        );

                        Color finalLocationColor = locationColor;
                        ReflectionUtilis.invokeMethodWithAutoProjection("setAfterCreate", row, new Runnable() {
                            @Override
                            public void run() {
                                AoTDPointerToStarSystem pointer = new AoTDPointerToStarSystem(
                                        (Float) ReflectionUtilis.invokeMethod("getHeight", row),
                                        bestMarket.getLocationInHyperspace(),
                                        finalLocationColor
                                );
                                Object columns = ReflectionUtilis.invokeMethodWithAutoProjection("getCol", row, 4);
                                PositionAPI pos = (PositionAPI) ReflectionUtilis.invokeMethodWithAutoProjection(
                                        "addComponent",
                                        columns,
                                        pointer.getMainPanel()
                                );
                                pos.inRMid(5f);
                            }
                        });
                    }

                    originalTooltip.addTable("", 0, 10f);
                }

                if (commsSell.isEmpty() && commsBuy.isEmpty()) {
                    originalTooltip.addPara("No trade data!", Misc.getGrayColor(), 10f);
                } else {
                    float pos = Math.abs(this.originalTooltip.addPara(
                            "*All values approximate. Prices do not include tariffs, which can be avoided through black market trade.",
                            Misc.getGrayColor(),
                            5f
                    ).getPosition().getY());

                    originalTooltip.addPara(
                            "*Per-unit prices assume buying or selling a batch of %s units. Each unit bought costs more as the market’s supply is reduced, and each unit sold brings in less as demand is fulfilled.",
                            5f,
                            Misc.getGrayColor(),
                            Color.ORANGE,
                            "500"
                    );

                    originalTooltip.addPara(
                            "*Deficit and excess values may change next month due to trade events, so they should be considered reliable only for the current month.",
                            Misc.getGrayColor(),
                            5f
                    );
                }

                final PositionAPI posit = originalTooltip.getPosition();
                final int prevX = (int) posit.getX();

                ReflectionUtilis.invokeStaticMethodWithAutoProjection(
                        StandardTooltipV2Expandable.class,
                        "updateSizeAsUIElement",
                        originalTooltip
                );

                posit.inBL(0f, 0f);

                final float currX = posit.getX();
                final float currY = posit.getY() + posit.getHeight();

                posit.inBL(prevX - currX, Math.max(prevY - currY, 30));
            }
        }
    }

    private static int getBuyPricePerUnit(MarketAPI market, String commodityId) {
        return Math.round(market.getSupplyPrice(commodityId, getQuantity(), true) / getQuantity());
    }

    private static int getSellPricePerUnit(MarketAPI market, String commodityId) {
        return Math.round(market.getDemandPrice(commodityId, getQuantity(), true) / getQuantity());
    }

    private static int getAvailableForBuy(MarketAPI market, String commodityId) {
        AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(market, commodityId);
        return AoTDOpenMarketPlugin.getStockPileToolbox(com);
    }

    public static int getQuantity() {
        return 500;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {

    }

    @Override
    public void clearUI() {

    }
}