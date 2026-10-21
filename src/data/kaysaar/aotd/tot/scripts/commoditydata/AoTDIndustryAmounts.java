package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.combat.MutableStat;
import data.kaysaar.aotd.tot.industries.AoTDBaseIndustry;
import data.kaysaar.aotd.tot.plugins.AoTDBaseDemSupCalc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class AoTDIndustryAmounts {
    public static boolean hasExactDemand(Industry industry,String commodityId) {
        return industry instanceof AoTDBaseIndustry ind && ind.hasExactDemand(commodityId);
    }

    public static int getDemand(Industry industry,String commodityId) {
        if(industry instanceof AoTDBaseIndustry ind && ind.hasExactDemand(commodityId))return ind.getDemandExact(commodityId);
        return getDemand(industry,commodityId,AoTDCommodityEconSpecManager.getEconSpec(commodityId).getCalculationScript(),industry.getMarket());
    }

    public static int getDemand(Industry industry,String commodityId,AoTDBaseDemSupCalc calculator,MarketAPI market) {
        if(industry instanceof AoTDBaseIndustry ind && ind.hasExactDemand(commodityId))return ind.getDemandExact(commodityId);
        return calculator.getRawUnitsFromDemand(industry.getDemand(commodityId).getQuantity(),market,commodityId,industry);
    }

    public static int getDemand(Industry industry,String commodityId,MutableStat stat,AoTDBaseDemSupCalc calculator,MarketAPI market) {
        if(industry instanceof AoTDBaseIndustry ind && ind.hasExactDemand(commodityId))return ind.getDemandExact(commodityId);
        return calculator.getRawUnitsFromDemand(stat,market,commodityId,industry);
    }

    public static int getProduction(Industry industry,String commodityId) {
        if(industry instanceof AoTDBaseIndustry ind && ind.hasExactProduction(commodityId))return ind.getProductionExact(commodityId);
        return getProduction(industry,commodityId,AoTDCommodityEconSpecManager.getEconSpec(commodityId).getCalculationScript(),industry.getMarket());
    }

    public static int getProduction(Industry industry,String commodityId,AoTDBaseDemSupCalc calculator,MarketAPI market) {
        if(industry instanceof AoTDBaseIndustry ind && ind.hasExactProduction(commodityId))return ind.getProductionExact(commodityId);
        return calculator.getRawUnitsFromSupply(industry.getSupply(commodityId).getQuantity(),market,commodityId,industry);
    }

    public static int getProduction(Industry industry,String commodityId,MutableStat stat,AoTDBaseDemSupCalc calculator,MarketAPI market) {
        if(industry instanceof AoTDBaseIndustry ind && ind.hasExactProduction(commodityId))return ind.getProductionExact(commodityId);
        return calculator.getRawUnitsFromSupply(stat,market,commodityId,industry);
    }

    public static List<String> getCommodityIds(Industry industry,boolean demand) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (MutableCommodityQuantity entry : demand && industry instanceof AoTDBaseIndustry ind ? ind.getVanillaDemandEntries() : demand ? industry.getAllDemand() : industry.getAllSupply()) {
            ids.add(entry.getCommodityId());
        }
        if(industry instanceof AoTDBaseIndustry ind){
            ids.addAll((demand ? ind.getExactDemand() : ind.getExactProduction()).keySet());
        }
        return new ArrayList<>(ids);
    }

    public static boolean hasDemand(Industry industry) {
        if(!industry.getAllDemand().isEmpty())return true;
        if(industry instanceof AoTDBaseIndustry ind){
            for (String id : ind.getExactDemand().keySet()) {
                if(ind.getDemandExact(id) > 0)return true;
            }
        }
        return false;
    }
}
