package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpec;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDSupplyDemandData {
    public LinkedHashMap<String, MutableStat> demandUnitsFromIndustries = new LinkedHashMap<>();
    public LinkedHashMap<String, MutableStat> supplyUnitsFromIndustries = new LinkedHashMap<>();
    public String commodityID;

    public AoTDSupplyDemandData(String commodityID) {
        this.commodityID = commodityID;
    }

    public transient AoTDCommodityEconSpec ecSpec;
    public int supply, demand, available;
    public MutableStatWithTempMods additionalProduction = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods additionalDemand = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods additionalImport = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods additionalExport = new MutableStatWithTempMods(0f);


    public AoTDCommodityEconSpec getEconSpec() {
        if (ecSpec == null) {
            ecSpec = AoTDCommodityEconSpecManager.getEconSpec(commodityID);
        }
        return ecSpec;
    }

    public int getExport(CommodityOnMarketAPI commodity) {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    public int getImportsExcludingDeficits() {
        return -getExportExcludingDeficit();
    }

    public int getExportExcludingDeficit() {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    public boolean doesHaveSupplyOrDemand() {
        int sup = getTotalRawUnitsFromSupply();
        int dem = getTotalRawUnitsFromDemand();
        return dem != 0 || sup != 0;
    }

    public void updateSupplyDemandData(MarketAPI market) {
        LinkedHashMap<String, MutableStat> demandUnitsFromIndustriesCopy = new LinkedHashMap<>();
        LinkedHashMap<String, MutableStat> supplyUnitsFromIndustriesCopy = new LinkedHashMap<>();

        try {
            for (Industry industry : market.getIndustries()) {
                demandUnitsFromIndustriesCopy.put(industry.getId(), industry.getDemand(commodityID).getQuantity());
                supplyUnitsFromIndustriesCopy.put(industry.getId(), industry.getSupply(commodityID).getQuantity());

            }
        } catch (Exception e) {
            return;
        }
        supply = 0;
        demand = 0;
        demandUnitsFromIndustries.clear();
        supplyUnitsFromIndustries.clear();
        demandUnitsFromIndustries.putAll(demandUnitsFromIndustriesCopy);
        supplyUnitsFromIndustries.putAll(supplyUnitsFromIndustriesCopy);
        for (Map.Entry<String, MutableStat> entry : supplyUnitsFromIndustries.entrySet()) {
            supply += getEconSpec().getCalculationScript().getRawUnitsFromSupply(entry.getValue(), null, commodityID, market.getIndustry(entry.getKey()));
        }
        for (Map.Entry<String, MutableStat> entry : demandUnitsFromIndustries.entrySet()) {
            demand += getEconSpec().getCalculationScript().getRawUnitsFromDemand(entry.getValue(), null, commodityID, market.getIndustry(entry.getKey()));
        }
        if (supply != 0 || demand != 0) {
            AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied().add(commodityID);
        }
    }

    public int getDemandExceptPendingIndustries(MarketAPI market) {
        int total = 0;
        for (Industry s : market.getIndustries()) {
            if (!AoTDIndustryData.getInstance(market).isPending(s.getId())) {
                total += getEconSpec().getCalculationScript().getRawUnitsFromDemand(s.getDemand(commodityID).getQuantity(), null, commodityID, s);
            }
        }
        return total;
    }

    public int getRawDemandFromIndustry(Industry industry) {
        return getEconSpec().getCalculationScript().getRawUnitsFromDemand(industry.getDemand(commodityID).getQuantity(), null, commodityID, industry);
    }

    public int getRawSupplyFromIndustry(Industry industry) {
        if (industry.isDisrupted()) return 0;
        return getEconSpec().getCalculationScript().getRawUnitsFromSupply(industry.getSupply(commodityID).getQuantity(), null, commodityID, industry);
    }

    public LinkedHashMap<String, MutableStat> getDemandUnitsFromIndustries() {
        return demandUnitsFromIndustries;
    }

    public LinkedHashMap<String, MutableStat> getSupplyUnitsFromIndustries() {
        return supplyUnitsFromIndustries;
    }

    public int getTotalRawUnitsFromSupply() {
        return supply;
    }

    public int getTotalRawUnitsFromDemand() {
        return demand;
    }

    public int getRawNetExport() {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    public int getTotalExportTowardsOtherSources() {
        return additionalExport.getModifiedInt();
    }

    public int getTotalImportFromOtherSources() {
        return additionalImport.getModifiedInt();
    }

    public MutableStatWithTempMods getAdditionalDemand() {
        return additionalDemand;
    }

    public MutableStatWithTempMods getAdditionalExport() {
        return additionalExport;
    }

    public MutableStatWithTempMods getAdditionalImport() {
        return additionalImport;
    }

    public MutableStatWithTempMods getAdditionalProduction() {
        return additionalProduction;
    }

    public void advance(float days) {
        additionalDemand.advance(days);
        additionalImport.advance(days);
        additionalExport.advance(days);
        additionalProduction.advance(days);
    }

    public int getAvailableOnThisMarket(float cargo, MarketAPI market, String commodityId) {
        int available = 0;
        float remainingCargo = cargo;
        for (Industry industry : market.getIndustries().stream().filter(x -> !AoTDIndustryData.getInstance(x.getMarket()).isPending(x.getId())).sorted(new Comparator<Industry>() {
            @Override
            public int compare(Industry o1, Industry o2) {
                return Integer.compare(o1.getSpec().getOrder(), o2.getSpec().getOrder());
            }
        }).toList()) {

            if (remainingCargo < 1) break;
            float raw = getEconSpec().getCalculationScript().getRawUnitsFromDemand(industry.getDemand(commodityId).getQuantity(), market, commodityId, industry);
            if (raw > remainingCargo) {
                float filled = remainingCargo / raw;
                int rem = Math.round(filled * industry.getDemand(commodityId).getQuantity().getModifiedInt());
                available += rem;
                break;
            } else {
                remainingCargo -= raw;
                available += industry.getDemand(commodityId).getQuantity().getModifiedInt();
            }

        }
        return available;
    }
}
