
package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.industries.AoTDBaseIndustry;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AoTDToobloxIndustryListener implements IndustryOptionProvider {
    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        return List.of();
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {

    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {

    }

    @Override
    @SuppressWarnings("unchecked")
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        if(ind instanceof AoTDBaseIndustry)return;

        MarketAPI market = ind.getMarket();
        if(!AoTDIndustryData.getInstance(market).isPending(ind.getId()))return;

        Color color = market.getFaction().getBaseUIColor();
        Color dark = market.getFaction().getDarkUIColor();

        tooltip.addSectionHeading("Preparations",color,dark,Alignment.MID,5f);
        tooltip.addPara("This industry is currently in-effective, as it needs resources to be allocated, it will be activated in next month!",
                Misc.getTooltipTitleAndLightHighlightColor(),5f).setAlignment(Alignment.MID);

        LinkedHashMap<String,Integer> expected = new LinkedHashMap<>();
        Map<String,MutableCommodityQuantity> supply = (Map<String,MutableCommodityQuantity>) ReflectionUtilis.getPrivateVariable("supply",ind);

        MutableStat supplyBonus = (MutableStat) ReflectionUtilis.getPrivateVariable("supplyBonus",ind);
        if(supplyBonus!=null){
            supplyBonus = supplyBonus.createCopy();
            supplyBonus.unmodify(AoTDIndustryData.source);
        }

        if(supply!=null){
            for (MutableCommodityQuantity entry : supply.values()) {
                if(entry==null)continue;

                String commodityId = entry.getCommodityId();
                MutableStat quantity = entry.getQuantity().createCopy();

                if(quantity.getFlatStatMod("ind_sb")!=null){
                    quantity.unmodifyFlat("ind_sb");
                    if(supplyBonus!=null){
                        quantity.modifyFlat("ind_sb",supplyBonus.getModifiedInt());
                    }
                }

                int raw = AoTDCommodityEconSpecManager.getEconSpec(commodityId).getCalculationScript()
                        .getRawUnitsFromSupply(quantity,market,commodityId,ind);

                if(raw<=0)continue;
                expected.put(commodityId,raw);
            }
        }

        if(!expected.isEmpty()){
            tooltip.addSectionHeading("Estimated production in next month",color,dark,Alignment.MID,5f);
            addPendingCommodityRows(tooltip,width,expected,Misc.getPositiveHighlightColor());
        }

        LinkedHashMap<String,Integer> required = new LinkedHashMap<>();
        Map<String,MutableCommodityQuantity> demand = (Map<String,MutableCommodityQuantity>) ReflectionUtilis.getPrivateVariable("demand",ind);

        MutableStat demandReduction = (MutableStat) ReflectionUtilis.getPrivateVariable("demandReduction",ind);
        if(demandReduction!=null){
            demandReduction = demandReduction.createCopy();
            demandReduction.unmodify(AoTDIndustryData.source);
        }

        if(demand!=null){
            for (MutableCommodityQuantity entry : demand.values()) {
                if(entry==null)continue;

                String commodityId = entry.getCommodityId();
                MutableStat quantity = entry.getQuantity().createCopy();

                if(quantity.getFlatStatMod("ind_dr")!=null){
                    quantity.unmodifyFlat("ind_dr");
                    if(demandReduction!=null){
                        quantity.modifyFlat("ind_dr",-demandReduction.getModifiedInt());
                    }
                }

                int raw = AoTDCommodityEconSpecManager.getEconSpec(commodityId).getCalculationScript()
                        .getRawUnitsFromDemand(quantity,market,commodityId,ind);

                if(raw<=0)continue;
                required.put(commodityId,-raw);
            }
        }

        if(!required.isEmpty()){
            tooltip.addSectionHeading("Demand Necessary",color,dark,Alignment.MID,5f);
            addPendingCommodityRows(tooltip,width,required,Misc.getNegativeHighlightColor());
        }
    }

    private static void addPendingCommodityRows(TooltipMakerAPI tooltip, float width, LinkedHashMap<String,Integer> commodities, Color color) {
        int maxInRow = 3;
        float effectiveWidth = (width-6)/maxInRow;
        ArrayList<CustomPanelAPI> toPlace = new ArrayList<>();

        for (Map.Entry<String,Integer> entry : commodities.entrySet()) {
            if(toPlace.size()==maxInRow){
                CustomPanelAPI row = Global.getSettings().createCustom(width,AoTDCommodityShortPanel.height,null);
                float currX = 0;
                for (CustomPanelAPI customPanelAPI : toPlace) {
                    row.addComponent(customPanelAPI).inTL(currX,0);
                    currX += effectiveWidth+2;
                }
                tooltip.addCustom(row,3f);
                toPlace.clear();
            }

            toPlace.add(new AoTDCommodityShortPanel(entry.getKey(),entry.getValue(),color,effectiveWidth).getMainPanel());
        }

        if(!toPlace.isEmpty()){
            CustomPanelAPI row = Global.getSettings().createCustom(width,AoTDCommodityShortPanel.height,null);
            int size = toPlace.size();
            float effectiveWidthTaken = size*effectiveWidth+((size-1)*2);
            float currX = (width/2)-(effectiveWidthTaken/2);

            for (CustomPanelAPI customPanelAPI : toPlace) {
                row.addComponent(customPanelAPI).inTL(currX,0);
                currX += effectiveWidth+2;
            }

            tooltip.addCustom(row,3f);
            toPlace.clear();
        }
    }
}
