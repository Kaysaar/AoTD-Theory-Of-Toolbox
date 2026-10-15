package data.kaysaar.aotd.tot.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import data.kaysaar.aotd.tot.ui.customprod.components.ProductionCustomButton;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils.getPriceForAmount;

public class AoTDToolboxMisc {
    public static String getDGSStringWithSign(int number) {

        String plus = "";
        if (number > 0) {
            plus = "+";
        }
        return plus + Misc.getWithDGS(number);
    }
    public static void setIndustryOnPlanet(String SystemName, String Planetname, String industryId, String removeIndustry, String potentialSwitch, boolean toImprove, String aiCore, String itemToInsert) {
        if (Global.getSector().getStarSystem(SystemName) == null) return;
        List<PlanetAPI> planets = Global.getSector().getStarSystem(SystemName).getPlanets();
        for (PlanetAPI planet : planets) {
            if (planet.getName().equals(Planetname)) {

                if (planet.getMarket() == null) continue;
                SpecialItemData data = null;
                if (removeIndustry != null && planet.getMarket().getIndustry(removeIndustry) != null) {

                    data = planet.getMarket().getIndustry(removeIndustry).getSpecialItem();
                    planet.getMarket().removeIndustry(removeIndustry, null, false);

                }
                if (industryId != null) {
                    planet.getMarket().addIndustry(industryId);
                    planet.getMarket().getIndustry(industryId).setImproved(toImprove);
                    planet.getMarket().getIndustry(industryId).setAICoreId(aiCore);
                    if (data != null) {
                        planet.getMarket().getIndustry(industryId).setSpecialItem(data);
                    }
                    if (itemToInsert != null) {
                        SpecialItemData daten = new SpecialItemData(itemToInsert, null);
                        planet.getMarket().getIndustry(industryId).setSpecialItem(daten);
                    }
                }


            }
        }
    }
    public static int getAmountOfMarketsGreaterAccThanTargetedMarket(List<MarketAPI>markets, MarketAPI market){
        return markets.stream().filter(x->x.getAccessibilityMod().computeEffective(0f)>market.getAccessibilityMod().computeEffective(0f)).toList().size();
    }
    public static String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static int getAmountOfContractsOfSameType(String typeId) {
        int am = 0;
        for (AoTDTradeContract activeContract : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
            if (activeContract.getContractTypeId().equals(typeId)) {
                am++;
            }
        }
        return am;
    }

    public static int getExpectedMonthlyIncomeForMarket(MarketAPI market) {
        if (market == null) return 0;

        double guaranteed = market.getIndustryIncome();
        double expectedExport = 0.0;

        for (CommodityOnMarketAPI c : market.getAllCommodities()) {
            if (c instanceof AoTDCommodityOnMarket a) {
                expectedExport += getExpectedMonthlyIncomeFromCommodity(a);
            }
        }



        return (int) Math.floor(guaranteed + expectedExport);
    }

    public static boolean isContractMetFully(AoTDTradeContract contract) {
        for (AoTDTradeContract.TradeContractData value : contract.getContractData().values()) {
            int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(value.getCommodityId(), Factions.PLAYER);
            int dem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromFactionTillContract(value.getCommodityId(), Factions.PLAYER, contract.getId());
            if (dem > prod) {
                return false;
            }
        }
        return true;
    }

    public static float getDeficitCountered(AoTDCommodityOnMarket commodity) {
        float countered = 0;
        for (Map.Entry<String, MutableStat.StatMod> entry : commodity.getExcDefData().deficit.getFlatMods().entrySet()) {
            if (entry.getKey().contains("aotd_shortage_counter")) {
                countered += Math.abs(entry.getValue().value);
            }
        }
        return countered;
    }

    public static float getStockpileCost(MarketAPI market) {
        List<CommodityOnMarketAPI> all = new ArrayList<CommodityOnMarketAPI>(market.getAllCommodities());

        float totalCost = 0f;

        for (CommodityOnMarketAPI commodity : all) {
            if (commodity instanceof AoTDCommodityOnMarket com) {
                float units = getDeficitCountered(com);
                if (units > 0) {
                    float per = LocalResourcesSubmarketPlugin.getStockpilingUnitPrice(com.getSpec(), true);
                    totalCost += units * per;
                }
            }
        }
        return (int) totalCost;
    }

    public static float getExpectedMonthlyNetIncomeFromMarket(MarketAPI market) {
        if (market == null) return 0f;

        double totalExportIncome = 0.0;


        for (CommodityOnMarketAPI c : market.getAllCommodities()) {
            if (c instanceof AoTDCommodityOnMarket a) {
                totalExportIncome += getExpectedMonthlyIncomeFromCommodity(a);
            }
        }

        double industryIncome = market.getIndustryIncome();
        double upkeep = market.getIndustryUpkeep();

        double shortageCost = 0.0;
        shortageCost = market.getShortageCounteringCost();


        double immigrationCost = 0.0;
        if (market.isImmigrationIncentivesOn()) {
            immigrationCost = market.getImmigrationIncentivesCost();
        }

        double net =
                industryIncome
                        + totalExportIncome
                        - upkeep
                        - shortageCost
                        - immigrationCost;

        return (float) net;
    }

    public static int getGuaranteedExportIncome(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;
        if (!com.isDemandLegal() || !com.isSupplyLegal()) return 0;

        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(com.getMarket());
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();



        int internalUnits = Math.max(0, md.getInternalExported(com.getId()));
        int supply = com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        int demand = com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        int suppliedLocally = Math.min(supply, demand);
        double localIncome = 0.0;
        if (suppliedLocally > 0) {
            localIncome = suppliedLocally * basePrice * AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), true);
        }
        double income = internalUnits * basePrice * AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), true);
        return (int) Math.floor(income + localIncome);
    }

    public static int getIncomeFromSelling(AoTDCommodityOnMarket com) {
        int guaranteed = getGuaranteedExportIncome(com);
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(com.getMarket());
        if (md == null) return guaranteed;
        int exported = md.getSoldOutside(com.getSpec().getId());
        int extraExported = md.getExtraSoldOutside(com.getSpec().getId());
        float cut = AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), false);

        int extraIncome = Math.round(getPriceForAmount(com.getId(), extraExported) * AoTDTradeManager.multFromSellingExcess);

        int income = Math.round((exported * cut * com.getSpec().getBasePrice()));
        return income + guaranteed+extraIncome;

    }

    public static int getSpeculatedExportIncome(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;
        if (!com.isSupplyLegal() || !com.isDemandLegal()) return 0;

        MarketAPI market = com.getMarket();
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();


        int export = com.getSupplyDemandData().getExportExcludingDeficit();
        if (export <= 0) return 0;


        int internalUnits = Math.max(0, md.getInternalExported(com.getId()));



        AoTDTradeContractManager.getInstance().ensurePredictionsUpToDate();

        int predictedContractUnits = 0;
        Map<String, AoTDTradeContract> active = AoTDTradeContractManager.getInstance().getActiveContracts();
        if (active != null && !active.isEmpty()) {
            for (AoTDTradeContract c : active.values()) {
                if (c == null) continue;
                if (c.isExpired() || c.isTerminated()) continue;

                if (!c.getContractData().containsKey(com.getId())) continue;

                int units = md.getPredictedContractExported(c.getId(), com.getId());
                if (units > 0) predictedContractUnits += units;
            }
        }


        int externalPool = export - internalUnits - predictedContractUnits;
        if (externalPool < 0) externalPool = 0;
        int producitonOutside = AoTDSectorProductionDemandDataUtils.getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts(com.getSpec().getId(), market.getFactionId());
        externalPool = Math.min(externalPool, producitonOutside);

        boolean hasOutsideDemand = producitonOutside>0;

        double externalIncome = 0.0;
        if (hasOutsideDemand && externalPool > 0) {
            externalIncome = externalPool * basePrice * AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), false);
        }

        double total = externalIncome;
        return (int) Math.floor(total);
    }
    public static int getSpeculatedExportIncomeFromContractsForUI(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;

        MarketAPI market = com.getMarket();
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();

        AoTDTradeContractManager.getInstance().ensurePredictionsUpToDate();

        Map<String, AoTDTradeContract> active = AoTDTradeContractManager.getInstance().getActiveContracts();
        if (active == null || active.isEmpty()) return 0;

        double predicted = 0.0;

        for (AoTDTradeContract c : active.values()) {
            if (c == null) continue;
            if (c.isExpired() || c.isTerminated()) continue;
            AoTDTradeContract.TradeContractData line = c.getContractData().get(com.getId());
            if (line == null) continue;

            int units = md.getPredictedContractExported(c.getId(), com.getId());
            if (units <= 0) continue;

            float cut = Math.max(0f, line.getCutFromBasePrice());
            predicted += units * basePrice * (cut);
        }
        return (int) Math.floor(predicted);
    }

    public static int getSpeculatedExportIncomeFromContracts(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;

        MarketAPI market = com.getMarket();
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();

        AoTDTradeContractManager.getInstance().ensurePredictionsUpToDate();

        Map<String, AoTDTradeContract> active = AoTDTradeContractManager.getInstance().getActiveContracts();
        if (active == null || active.isEmpty()) return 0;

        double predicted = 0.0;

        for (AoTDTradeContract c : active.values()) {
            if (c == null) continue;
            if (c.isExpired() || c.isTerminated()) continue;
            if (!c.isContractEarningIncome()) continue;
            AoTDTradeContract.TradeContractData line = c.getContractData().get(com.getId());
            if (line == null) continue;

            int units = md.getPredictedContractExported(c.getId(), com.getId());
            if (units <= 0) continue;

            float cut = Math.max(0f, line.getCutFromBasePrice());
            predicted += units * basePrice * (cut);
        }
        return (int) Math.floor(predicted);
    }

    public static int getExpectedMonthlyIncomeFromCommodity(AoTDCommodityOnMarket commodity) {
        if (commodity == null) return 0;
        return getGuaranteedExportIncome(commodity) + getSpeculatedExportIncome(commodity) + getSpeculatedExportIncomeFromContracts(commodity);
    }

    public static LinkedHashSet<FactionAPI> getFactionsInEconomy() {
        LinkedHashSet<FactionAPI> factions = new LinkedHashSet<>();
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            factions.add(marketAPI.getFaction());
        }
        return factions;
    }

    public static int getMaxShipped(MarketAPI marketFrom, MarketAPI towards, String commodityId) {
        AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketFrom,commodityId);
        if (marketFrom.getFaction().equals(towards.getFaction())) {
            AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(marketFrom);
            int exportedToFaction = 0;
            if (data != null) {
                data.getInternalExported(commodityId);
            }
            return Math.max(exportedToFaction, com.getSupplyDemandData().getExportExcludingDeficit() - exportedToFaction);
        } else {
            AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(marketFrom);
            int exportedToFaction = 0;
            if (data != null) {
                data.getInternalExported(commodityId);
            }
            return Math.max(0, com.getSupplyDemandData().getExportExcludingDeficit() - exportedToFaction);
        }
    }

    public static int getMaxImported(MarketAPI marketFrom, MarketAPI towards, String commodityId) {
        AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketFrom.getCommodityData(commodityId);
        return com.getSupplyDemandData().getTotalRawUnitsFromDemand() - com.getSupplyDemandData().getTotalRawUnitsFromSupply();
    }

    public static CustomPanelAPI createCostSection(float width, float height, Map<String, Integer> resources, float credits) {
        return createCostSection(width, height, resources, credits, 22f, 6f, 3f, 8f, true, true);
    }

    public static CustomPanelAPI createCostSection(float width, float height, Map<String, Integer> resources, float credits,
                                                   float iconSize, float separator, float rowGap, float padding,
                                                   boolean showCredits, boolean colorSpecialItems) {
        return createCostSection(width, height, resources, credits,
                iconSize, separator, rowGap, padding, 4f, 4f,
                Fonts.DEFAULT_SMALL, Color.ORANGE, Color.CYAN,
                Color.ORANGE, Misc.getNegativeHighlightColor(), showCredits, colorSpecialItems);
    }

    public static CustomPanelAPI createCostSection(float width, float height, Map<String, Integer> resources, float credits,
                                                   float iconSize, float separator, float rowGap, float padding,
                                                   float creditsGap, float iconTextGap, String font,
                                                   Color resourceColor, Color specialItemColor,
                                                   Color affordableColor, Color insufficientColor,
                                                   boolean showCredits, boolean colorSpecialItems) {
        float panelHeight = Math.max(1f, height);
        CustomPanelAPI mainPanel = Global.getSettings().createCustom(width, panelHeight, null);
        LinkedHashMap<String, Integer> orderedResources = ProductionCustomButton.getOrderedResourceMap(resources);
        ArrayList<CustomPanelAPI> panels = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : orderedResources.entrySet()) {
            if (Global.getSettings().getCommoditySpec(entry.getKey()) != null) {
                panels.add(createResourcePanel(iconSize, entry.getKey(), entry.getValue(),
                        iconTextGap, font, resourceColor, specialItemColor, colorSpecialItems));
            }
        }

        for (Map.Entry<String, Integer> entry : orderedResources.entrySet()) {
            if (Global.getSettings().getCommoditySpec(entry.getKey()) == null) {
                panels.add(createResourcePanel(iconSize, entry.getKey(), entry.getValue(),
                        iconTextGap, font, resourceColor, specialItemColor, colorSpecialItems));
            }
        }

        LabelAPI creditsLabel = null;
        float creditsWidth = 0f;
        float creditsHeight = 0f;

        if (showCredits) {
            creditsLabel = Global.getSettings().createLabel(Misc.getDGSCredits(credits), font);
            float playerCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
            creditsLabel.setColor(credits <= playerCredits ? affordableColor : insufficientColor);
            creditsWidth = creditsLabel.computeTextWidth(creditsLabel.getText());
            creditsHeight = creditsLabel.computeTextHeight(creditsLabel.getText());
            creditsLabel.getPosition().setSize(creditsWidth, creditsHeight);
        }

        if (panels.isEmpty()) {
            if (height <= 0f) {
                panelHeight = Math.max(1f, creditsHeight);
                mainPanel.getPosition().setSize(width, panelHeight);
            }
            if (creditsLabel != null) {
                mainPanel.addComponent((UIComponentAPI) creditsLabel).inTL(
                        (width - creditsWidth) / 2f, (panelHeight - creditsHeight) / 2f);
            }
            return mainPanel;
        }

        ArrayList<ArrayList<CustomPanelAPI>> rows = new ArrayList<>();
        ArrayList<Float> rowWidths = new ArrayList<>();
        ArrayList<Float> rowHeights = new ArrayList<>();
        ArrayList<CustomPanelAPI> currentRow = new ArrayList<>();
        float currentWidth = 0f;
        float currentHeight = 0f;
        float availableWidth = Math.max(0f, width - padding * 2f);

        for (int i = 0; i < panels.size(); i++) {
            CustomPanelAPI panel = panels.get(i);
            float resourceWidth = panel.getPosition().getWidth();
            float resourceHeight = panel.getPosition().getHeight();

            if (!currentRow.isEmpty() && currentWidth + separator + resourceWidth > availableWidth) {
                rows.add(currentRow);
                rowWidths.add(currentWidth);
                rowHeights.add(currentHeight);
                currentRow = new ArrayList<>();
                currentWidth = 0f;
                currentHeight = 0f;
            }

            if (!currentRow.isEmpty()) currentWidth += separator;
            currentRow.add(panel);
            currentWidth += resourceWidth;
            currentHeight = Math.max(currentHeight, resourceHeight);
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
            rowWidths.add(currentWidth);
            rowHeights.add(currentHeight);
        }

        float totalResourcesHeight = Math.max(0, rows.size() - 1) * rowGap;
        for (float rowHeight : rowHeights) {
            totalResourcesHeight += rowHeight;
        }

        float totalHeight = totalResourcesHeight + (creditsLabel == null ? 0f : creditsGap + creditsHeight);
        if (height <= 0f) {
            panelHeight = Math.max(1f, totalHeight);
            mainPanel.getPosition().setSize(width, panelHeight);
        }
        float currentY = Math.max(0f, (panelHeight - totalHeight) / 2f);
        float startY = currentY;

        for (int i = 0; i < rows.size(); i++) {
            ArrayList<CustomPanelAPI> row = rows.get(i);
            float rowHeight = rowHeights.get(i);
            float currentX = (width - rowWidths.get(i)) / 2f;

            for (CustomPanelAPI panel : row) {
                mainPanel.addComponent(panel).inTL(
                        currentX,
                        currentY + (rowHeight - panel.getPosition().getHeight()) / 2f
                );
                currentX += panel.getPosition().getWidth() + separator;
            }

            currentY += rowHeight + rowGap;
        }

        if (creditsLabel != null) {
            mainPanel.addComponent((UIComponentAPI) creditsLabel).inTL(
                    (width - creditsWidth) / 2f, startY + totalResourcesHeight + creditsGap);
        }

        return mainPanel;
    }

    public static CustomPanelAPI createResourcePanel(float iconSize, String resourceId, int amount) {
        return createResourcePanel(iconSize, resourceId, amount, true);
    }

    public static CustomPanelAPI createResourcePanel(float iconSize, String resourceId, int amount, boolean colorSpecialItems) {
        return createResourcePanel(iconSize, resourceId, amount, 4f, Fonts.DEFAULT_SMALL,
                Color.ORANGE, Color.CYAN, colorSpecialItems);
    }

    public static CustomPanelAPI createResourcePanel(float iconSize, String resourceId, int amount,
                                                     float iconTextGap, String font,
                                                     Color resourceColor, Color specialItemColor,
                                                     boolean colorSpecialItems) {
        String iconName;
        boolean isCommodity = Global.getSettings().getCommoditySpec(resourceId) != null;

        if (isCommodity) {
            iconName = Global.getSettings().getCommoditySpec(resourceId).getIconName();
        } else if (Global.getSettings().getSpecialItemSpec(resourceId) != null) {
            iconName = Global.getSettings().getSpecialItemSpec(resourceId).getIconName();
        } else {
            throw new IllegalArgumentException("Unknown resource ID: " + resourceId);
        }

        ImageViewer viewer = new ImageViewer(iconSize, iconSize, iconName);
        LabelAPI label = Global.getSettings().createLabel(Misc.getWithDGS(amount), font);
        label.setColor(!isCommodity && colorSpecialItems ? specialItemColor : resourceColor);

        float labelWidth = label.computeTextWidth(label.getText());
        float labelHeight = label.computeTextHeight(label.getText());
        label.getPosition().setSize(labelWidth, labelHeight);

        CustomPanelAPI mainPanel = Global.getSettings().createCustom(
                iconSize + iconTextGap + labelWidth,
                Math.max(iconSize, labelHeight),
                null
        );

        mainPanel.addComponent(viewer.getComponentPanel()).inTL(
                0f, (mainPanel.getPosition().getHeight() - iconSize) / 2f);
        mainPanel.addComponent((UIComponentAPI) label).inTL(
                iconSize + iconTextGap,
                (mainPanel.getPosition().getHeight() - labelHeight) / 2f);

        return mainPanel;
    }
}
