package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;

import java.util.*;

public class AoTDLocalResourcesSubmarketPlugin extends LocalResourcesSubmarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        if (com instanceof AoTDCommodityOnMarket commodity) {
            int limit = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
            String cid = com.getId();
            if (stockpilingBonus.containsKey(cid)) {
                limit += AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand((int) stockpilingBonus.get(cid).getModifiedValue(), true, cid);
            }
            //limit *= com.getMarket().getStockpileMult().getModifiedValue();
            limit *= STOCKPILE_MAX_MONTHS;
            int deficitCountered = (int) getDeficitCountered(commodity);

            if (commodity.getExcDefData().getDeficit() - deficitCountered > 0) return 0;
            if (limit < 0) limit = 0;
            return limit;

        } else {
            return super.getStockpileLimit(com);
        }
    }

    @Override
    public int getEstimatedShortageCounteringCostPerMonth() {
        List<CommodityOnMarketAPI> all = new ArrayList<CommodityOnMarketAPI>(market.getAllCommodities());

        float totalCost = 0f;

        CargoAPI cargo = getCargo();

        for (CommodityOnMarketAPI commodity : all) {
            if (commodity instanceof AoTDCommodityOnMarket com) {
                float units = getDeficitCountered(com);
                if (units > 0) {
                    float per = LocalResourcesSubmarketPlugin.getStockpilingUnitPrice(com.getCommoditySpec(), true);
                    totalCost += units * per;
                }
            }
        }
        return (int) totalCost;
    }

    public float getDeficitCountered(AoTDCommodityOnMarket commodity){
        float countered = 0;
        for (Map.Entry<String, MutableStat.StatMod> entry : commodity.getExcDefData().deficit.getFlatMods().entrySet()) {
            if(entry.getKey().contains("aotd_shortage_counter")){
                countered += Math.abs(entry.getValue().value);
            }
        }
        return countered;
    }
    @Override
    protected boolean doShortageCountering(CommodityOnMarketAPI com, float amount, boolean withShortageCountering) {
        if (com instanceof AoTDCommodityOnMarket commodity) {

            float curr = cargo.getCommodityQuantity(commodity.getId());
            float drawAmount = Math.min(curr,commodity.getDeficitQuantity());
            if (drawAmount > 0 && withShortageCountering&&curr>0) {
                float free = left.getCommodityQuantity(com.getId());
                free = Math.min(drawAmount, free);
                left.removeCommodity(com.getId(), free);
                cargo.removeCommodity(com.getId(), drawAmount);
                commodity.getExcDefData().setDeficit((int) -drawAmount,commodity,30,"aotd_shortage_counter_"+Misc.genUID());

                drawAmount -= free;
                if (market.isPlayerOwned() && drawAmount > 0) {
                    MonthlyReport report = SharedData.getData().getCurrentReport();
                    MonthlyReport.FDNode node = report.getCounterShortageNode(market);

                    CargoAPI tooltipCargo = (CargoAPI) node.custom2;
                    float addToTooltipCargo = drawAmount;
                    float q = tooltipCargo.getCommodityQuantity(com.getId()) + addToTooltipCargo;
                    if (q < 1) {
                        addToTooltipCargo = 1f; // add at least 1 unit or it won't do anything
                    }
                    tooltipCargo.addCommodity(com.getId(), addToTooltipCargo);

                    float unitPrice = (int) getStockpilingUnitPrice(commodity.getCommoditySpec(), true);
                    //node.upkeep += unitPrice * addAmount;

                    MonthlyReport.FDNode comNode = report.getNode(node, com.getId());

                    CommoditySpecAPI spec = com.getCommodity();
                    comNode.icon = spec.getIconName();
                    comNode.upkeep += unitPrice * drawAmount;
                    comNode.custom = com;

                    if (comNode.custom2 == null) {
                        comNode.custom2 = 0f;
                    }
                    comNode.custom2 = (Float)comNode.custom2 + drawAmount;

                    float qty = Math.max(1, (Float) comNode.custom2);
                    qty = (float) Math.ceil(qty);
                    comNode.name = spec.getName() + " " + Strings.X + Misc.getWithDGS(qty);
                    comNode.tooltipCreator = report.getMonthlyReportTooltip();
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        List<CommodityOnMarketAPI> all = new ArrayList<CommodityOnMarketAPI>(market.getAllCommodities());

        Collections.sort(all, new Comparator<CommodityOnMarketAPI>() {
            public int compare(CommodityOnMarketAPI o1, CommodityOnMarketAPI o2) {
                int limit1 = getStockpileLimit(o1);
                int limit2 = getStockpileLimit(o2);
                return limit2 - limit1;
            }
        });

        float opad = 10f;

        tooltip.beginGridFlipped(400f, 1, 70f, opad);
        int j = 0;
        for (CommodityOnMarketAPI com : all) {
            if (com.isNonEcon()) continue;
            if (com.getCommodity().isMeta()) continue;

            if (!shouldHaveCommodity(com)) continue;

            int limit = (int) Math.round(getStockpileLimit(com) * getStockpilingAddRateMult(com));
            if (limit <= 0) continue;

            tooltip.addToGrid(0, j++,
                    com.getCommodity().getName(),
                    Misc.getWithDGS(limit));
            //Misc.getWithDGS(curr) + " / " + Misc.getWithDGS(limit));
        }

        tooltip.addPara("A portion of the resources produced by the colony will be made available here. " +
                        "These resources can be extracted from the colony's economy for a cost equal to %s of their base value. " +
                        "This cost will be deducted at the end of the month.", opad,
                Misc.getHighlightColor(), "" + (int) Math.round(STOCKPILE_COST_MULT * 100f) + "%");

        tooltip.addPara("These resources can also be used to counter temporary shortages, for a " +
                        "cost equal to %s of their base value. If additional resources are placed here, they " +
                        "will be used as well, at no cost.", opad,
                Misc.getHighlightColor(), "" + (int) Math.round(STOCKPILE_SHORTAGE_COST_MULT * 100f) + "%");


        tooltip.addSectionHeading("Stockpiled per month", market.getFaction().getBaseUIColor(), market.getFaction().getDarkUIColor(), Alignment.MID, opad);
        if (j > 0) {
            tooltip.addGrid(opad);

            tooltip.addPara("Stockpiles are limited to %s the monthly rate.", opad,
                    Misc.getHighlightColor(), "" + (int) STOCKPILE_MAX_MONTHS + Strings.X);
        } else {
            tooltip.addPara("No stockpiling.", opad);
        }
    }

}
