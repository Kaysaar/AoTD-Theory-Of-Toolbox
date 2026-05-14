package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class AoTDSectorProductionDemandDataUtils {
    public static int getTotalProductionFromSector(String commodityId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        }
        return prod;
    }

    public static int getTotalProductionFromSectorOutsideOfFaction(String commodityId, String factionId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (marketAPI.getFactionId().equals(factionId)) {
                continue;
            }
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        }
        return prod;
    }

    public static float getPercentageOfSectorProduction(String commodityId, int amount) {
        float total = getTotalProductionFromSector(commodityId);
        if (total <= 0) return 0;
        return amount / total;
    }
    public static float getProductionPercentageShareOfFaction(String commodityId, String factionId) {
        float total = getTotalProductionFromSector(commodityId);
        if (total <= 0) return 0;
        return getTotalProductionFromFaction(commodityId,factionId) / total;
    }

    public static List<MarketAPI> getFactionMarketsProducers(String commodityId, String factionId) {
        LinkedHashSet<MarketAPI> markets = new LinkedHashSet<>();
        if(factionId.equals(Factions.NEUTRAL)){
            for (MarketAPI factionMarket : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) factionMarket.getCommodityData(commodityId);
                if (com.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0&&!factionMarket.isHidden()) {
                    markets.add(factionMarket);
                }
            }
        }
        else{
            for (MarketAPI factionMarket : Misc.getFactionMarkets(factionId)) {
                AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) factionMarket.getCommodityData(commodityId);
                if (com.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0&&!factionMarket.isHidden()) {
                    markets.add(factionMarket);
                }
            }
        }

        return markets.stream().sorted(new Comparator<MarketAPI>() {

            @Override
            public int compare(MarketAPI o1, MarketAPI o2) {
                AoTDCommodityOnMarket com1 = (AoTDCommodityOnMarket) o1.getCommodityData(commodityId);
                AoTDCommodityOnMarket com2 = (AoTDCommodityOnMarket) o2.getCommodityData(commodityId);
                return Integer.compare(com2.getSupplyDemandData().getTotalRawUnitsFromSupply(), com1.getSupplyDemandData().getTotalRawUnitsFromSupply());
            }
        }).toList();
    }

    public static List<MarketAPI> getFactionMarketsConsumers(String commodityId, String factionId) {
        LinkedHashSet<MarketAPI> markets = new LinkedHashSet<>();
        if(factionId.equals(Factions.NEUTRAL)){
            for (MarketAPI factionMarket : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) factionMarket.getCommodityData(commodityId);
                if (com.getSupplyDemandData().getTotalRawUnitsFromDemand() > 0&&!factionMarket.isHidden()) {
                    markets.add(factionMarket);
                }
            }
        }
        else{
            for (MarketAPI factionMarket : Misc.getFactionMarkets(factionId)) {
                AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) factionMarket.getCommodityData(commodityId);
                if (com.getSupplyDemandData().getTotalRawUnitsFromDemand() > 0&&!factionMarket.isHidden()) {
                    markets.add(factionMarket);
                }
            }
        }

        return markets.stream().sorted(new Comparator<MarketAPI>() {

            @Override
            public int compare(MarketAPI o1, MarketAPI o2) {
                AoTDCommodityOnMarket com1 = (AoTDCommodityOnMarket) o1.getCommodityData(commodityId);
                AoTDCommodityOnMarket com2 = (AoTDCommodityOnMarket) o2.getCommodityData(commodityId);
                return Integer.compare(com2.getSupplyDemandData().getTotalRawUnitsFromDemand(), com1.getSupplyDemandData().getTotalRawUnitsFromDemand());
            }
        }).toList();
    }

    public static int getTotalProductionFromFaction(String commodityId, String factionId) {
        int prod = 0;
        if(factionId.equals(Factions.NEUTRAL)){
            return getTotalProductionFromSector(commodityId);
        }
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFaction() == faction).toList()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        }
        return prod;
    }

    public static int getTotalDemandFromFaction(String commodityId, String factionId) {
        int prod = 0;
        if(factionId.equals(Factions.NEUTRAL)){
            return getTotalDemandFromSector(commodityId);
        }
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFaction() == faction).toList()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        if (factionId.equals(Factions.PLAYER)) {
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }
            }
        }
        return prod;
    }

    public static int getTotalDemandFromFactionIgnoreContracts(String commodityId, String factionId) {
        int prod = 0;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFaction() == faction).toList()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        return prod;
    }

    public static int getTotalDemandFromFactionExcludingContracts(String commodityId, String factionId) {
        int prod = 0;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFaction() == faction).toList()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        return prod;
    }

    public static int getTotalDemandFromFactionTillContract(String commodityId, String factionId, String contract) {
        int prod = 0;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFaction() == faction).toList()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        if (factionId.equals(Factions.PLAYER)) {
            boolean initalizeBreakAfter = false;
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                if (initalizeBreakAfter) break;
                if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
                if (value.getId().equals(contract)) {
                    initalizeBreakAfter = true;
                }
                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }
            }
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getCurrentlyGeneratedInBrowser().values()) {
                if (value.getId().equals(contract)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                    continue;
                }
                if(!value.itWasTaken())continue;
                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }

            }



        }



        return prod;
    }

    public static int getTotalDemandFromFactionBeforeContract(String commodityId, String factionId, String contract) {
        int prod = 0;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFaction() == faction).toList()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        if (factionId.equals(Factions.PLAYER)) {
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
                if (value.getId().equals(contract)) {
                    break;
                }
                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }
            }
        }

        return prod;
    }
    public static int getTotalDemandFromSectorExcludeContracts(String commodityId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        return prod;
    }

    public static int getTotalDemandFromSector(String commodityId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
            if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
            if (value.isPrivate() || value.isIssuedByPlayer()) {
                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }
            }

        }
        return prod;
    }

    public static LinkedHashSet<FactionAPI> getFactionsInEconomy() {
        LinkedHashSet<FactionAPI> factionAPIS = new LinkedHashSet<>();
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (marketAPI.isHidden()) continue;
            factionAPIS.add(marketAPI.getFaction());
        }
        return factionAPIS;
    }

    public static int getTotalDemandFromSectorOutsideFromFactionIgnoreContracts(String commodityId, String factionId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (marketAPI.getFaction().getId().equals(factionId)) continue;
            if (marketAPI.getCommodityData(commodityId) instanceof AoTDCommodityOnMarket) {
                AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
                prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
            }
        }
        return prod;
    }

    public static int getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts(String commodityId, String factionId) {
        int effectiveDemand = 0;
        for (FactionAPI factionAPI : getFactionsInEconomy()) {
            if (factionAPI.getId().equals(factionId)) continue;
            int prod = getTotalProductionFromFaction(commodityId, factionAPI.getId());
            int dem = getTotalDemandFromFaction(commodityId, factionAPI.getId());
            int effectiveDem = dem - prod;
            if (effectiveDem > 0) {
                effectiveDemand += effectiveDem;
            }
        }
        return effectiveDemand;
    }

    public static int getTotalDemandFromSectorOutsideFromFaction(String commodityId, String factionId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (marketAPI.getFaction().getId().equals(factionId)) continue;
            if (marketAPI.getCommodityData(commodityId) instanceof AoTDCommodityOnMarket) {
                AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketAPI.getCommodityData(commodityId);
                prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
            }
        }
        if (!factionId.equals(Factions.PLAYER)) {
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
                if (value.isPrivate() || value.isIssuedByPlayer()) {
                    if (value.getContractData().containsKey(commodityId)) {
                        prod += value.getContractData().get(commodityId).getReqMonthly();
                    }
                }

            }
        }


        return prod;
    }

    public static int getPriceForAmount(String commodityId, int amount) {
        return (int) ((int) (Global.getSettings().getCommoditySpec(commodityId).getBasePrice() * amount * AoTDCommodityEconSpecManager.getCutForCommodity(commodityId, false)));
    }

    public static int getPriceForAmount(String commodityId, int amount, boolean internal) {
        return (int) ((int) (Global.getSettings().getCommoditySpec(commodityId).getBasePrice() * amount * AoTDCommodityEconSpecManager.getCutForCommodity(commodityId, internal)));
    }

    public static int getPriceAmountTotalAroundSectorForFaction(String commodityId, int dem, int supply, String factionId) {
        int demByFaction = getTotalDemandFromSectorOutsideFromFaction(commodityId, factionId);
        int outsideDEm = dem - demByFaction;
        int extra = supply - dem;
        int original = getPriceForAmount(commodityId, outsideDEm);
        original += getPriceForAmount(commodityId, demByFaction, true);
        if (extra > 1 && !Global.getSettings().getCommoditySpec(commodityId).hasTag(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS)) {
            extra /= 2;
            original += Math.round(getPriceForAmount(commodityId, extra) * AoTDTradeManager.multFromSellingExcess);
        }
        return original;
    }

    public static int getPriceAmountTotalAroundSector(String commodityId, int dem, int supply) {
        int effective = dem;
        int extra = supply - dem;
        int original = getPriceForAmount(commodityId, effective);
        if (extra > 1 && !Global.getSettings().getCommoditySpec(commodityId).hasTag(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS)) {
            extra /= 2;
            original += Math.round(getPriceForAmount(commodityId, extra) * AoTDTradeManager.multFromSellingExcess);
        }
        return original;
    }

    public static int getPercentageOfDemandFromSector(String commodityId, int amount) {
        int total = getTotalDemandFromSector(commodityId);
        return Math.round(((float) amount / total) * 100f);
    }
}
