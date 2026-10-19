package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class AoTDSectorProductionDemandDataUtils {
    private AoTDSectorProductionDemandDataUtils() {}

    public static int getTotalProductionFromSector(String commodityId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            final AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
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
            final AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        }
        return prod;
    }

    public static float getPercentageOfSectorProduction(String commodityId, int amount) {
        final float total = getTotalProductionFromSector(commodityId);
        if (total <= 0f) return 0f;
        return amount / total;
    }

    public static float getProductionPercentageShareOfFaction(String commodityId, String factionId) {
        final float total = getTotalProductionFromSector(commodityId);
        if (total <= 0f) return 0f;
        return getTotalProductionFromFaction(commodityId,factionId) / total;
    }

    public static List<MarketAPI> getFactionMarketsProducers(String commodityId, String factionId) {
        final List<MarketAPI> marketsToTraverse = factionId.equals(Factions.NEUTRAL) ?
            Global.getSector().getEconomy().getMarketsCopy() : Misc.getFactionMarkets(factionId);

        final List<MarketAPI> result = new ArrayList<>(marketsToTraverse.size()/2); // optimistic size
        for (MarketAPI market : marketsToTraverse) {
            if (market.isHidden()) continue;
            final AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(market,commodityId);
            if (com != null && com.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0) {
                result.add(market);
            }
        }

        result.sort((m1, m2) -> {
            final AoTDCommodityOnMarket c1 =  AoTDCommodityOnMarket.getComMarketInstanceSave(m1,commodityId);
            final AoTDCommodityOnMarket c2 =AoTDCommodityOnMarket.getComMarketInstanceSave(m2,commodityId);
            final int supply1 = c1.getSupplyDemandData().getTotalRawUnitsFromSupply();
            final int supply2 = c2.getSupplyDemandData().getTotalRawUnitsFromSupply();
            return Integer.compare(supply2, supply1);
        });

        return result;
    }

    public static List<MarketAPI> getFactionMarketsConsumers(String commodityId, String factionId) {
        final List<MarketAPI> marketsToTraverse = factionId.equals(Factions.NEUTRAL) ?
            Global.getSector().getEconomy().getMarketsCopy() : Misc.getFactionMarkets(factionId);

        final List<MarketAPI> result = new ArrayList<>(marketsToTraverse.size()/2); // optimistic size
        for (MarketAPI market : marketsToTraverse) {
            if (market.isHidden()) continue;
            final AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(market,commodityId);
            if (com != null && com.getSupplyDemandData().getTotalRawUnitsFromDemand() > 0) {
                result.add(market);
            }
        }

        result.sort((m1, m2) -> {
            final AoTDCommodityOnMarket c1 = AoTDCommodityOnMarket.getComMarketInstanceSave(m1,commodityId);
            final AoTDCommodityOnMarket c2 = AoTDCommodityOnMarket.getComMarketInstanceSave(m2,commodityId);
            final int demand1 = c1.getSupplyDemandData().getTotalRawUnitsFromDemand();
            final int demand2 = c2.getSupplyDemandData().getTotalRawUnitsFromDemand();
            return Integer.compare(demand2, demand1);
        });

        return result;
    }

    public static int getTotalProductionFromFaction(String commodityId, String factionId) {
        if(factionId.equals(Factions.NEUTRAL)) return getTotalProductionFromSector(commodityId);
        
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFactionId().equals(factionId)).toList()) {
            final AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        }
        return prod;
    }

    public static int getTotalDemandFromFaction(String commodityId, String factionId) {
        if(factionId.equals(Factions.NEUTRAL)) return getTotalDemandFromSector(commodityId);
        
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFactionId().equals(factionId)).toList()) {
            final AoTDCommodityOnMarket com =  AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
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
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFactionId().equals(factionId)).toList()) {
            final AoTDCommodityOnMarket com =  AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        return prod;
    }

    public static int getTotalDemandFromFactionExcludingContracts(String commodityId, String factionId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFactionId().equals(factionId)).toList()) {
            final AoTDCommodityOnMarket com =  AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        return prod;
    }

    public static int getTotalDemandFromFactionTillContract(String commodityId, String factionId, String contract) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFactionId().equals(factionId)).toList()) {
            final AoTDCommodityOnMarket com =  AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }

        if (factionId.equals(Factions.PLAYER)) {
            boolean initalizeBreakAfter = false;
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                if (initalizeBreakAfter) break;
                if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
                if (value.getId().equals(contract)) initalizeBreakAfter = true;

                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }
            }
            for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getCurrentlyGeneratedInBrowser().values()) {
                if (value.getId().equals(contract)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                    continue;
                }
                if(!value.itWasTaken()) continue;
                if (value.getContractData().containsKey(commodityId)) {
                    prod += value.getContractData().get(commodityId).getReqMonthly();
                }
            }
        }

        return prod;
    }

    public static int getTotalDemandFromFactionBeforeContract(String commodityId, String factionId, String contract) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy().stream().filter(x -> x.getFactionId().equals(factionId)).toList()) {
            final AoTDCommodityOnMarket com =  AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
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
            final AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
            prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        }
        return prod;
    }

    public static int getTotalDemandFromSector(String commodityId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            final AoTDCommodityOnMarket com =  AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI,commodityId);
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
        final LinkedHashSet<FactionAPI> factionAPIS = new LinkedHashSet<>();
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!marketAPI.isHidden()) factionAPIS.add(marketAPI.getFaction());
        }
        return factionAPIS;
    }

    public static int getTotalDemandFromSectorOutsideFromFactionIgnoreContracts(String commodityId, String factionId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (marketAPI.getFaction().getId().equals(factionId)) continue;
            if (marketAPI.getCommodityData(commodityId) instanceof AoTDCommodityOnMarket com) {
                prod += com.getSupplyDemandData().getTotalRawUnitsFromDemand();
            }
        }
        return prod;
    }

    public static int getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts(String commodityId, String factionId) {
        // Same result as summing max(0, getTotalDemandFromFaction - getTotalProductionFromFaction)
        // over getFactionsInEconomy() except factionId, but in one pass over the markets. Doing it
        // per faction copied and filtered the whole market list twice per faction, and this runs
        // for every commodity on every player market whenever the command UI shows income.
        final List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();

        // As in getFactionsInEconomy(): factions of non-hidden markets.
        final LinkedHashSet<FactionAPI> factions = new LinkedHashSet<>();
        for (MarketAPI market : markets) {
            if (!market.isHidden()) factions.add(market.getFaction());
        }
        final Set<String> countedFactionIds = new HashSet<>();
        boolean countsSector = false;
        for (FactionAPI faction : factions) {
            if (faction.getId().equals(factionId)) continue;
            // getTotal*FromFaction treat neutral as the whole sector.
            if (faction.getId().equals(Factions.NEUTRAL)) countsSector = true;
            else countedFactionIds.add(faction.getId());
        }

        // Faction id -> {production, demand}, over all of that faction's markets (hidden too).
        final Map<String, int[]> totals = new HashMap<>();
        int sectorProduction = 0;
        int sectorDemand = 0;
        for (MarketAPI market : markets) {
            final boolean counted = countedFactionIds.contains(market.getFactionId());
            if (!counted && !countsSector) continue;
            final AoTDSupplyDemandData data = AoTDCommodityOnMarket.getComMarketInstanceSave(market, commodityId).getSupplyDemandData();
            final int production = data.getTotalRawUnitsFromSupply();
            final int demand = data.getTotalRawUnitsFromDemand();
            if (countsSector) {
                sectorProduction += production;
                sectorDemand += demand;
            }
            if (counted) {
                final int[] factionTotals = totals.computeIfAbsent(market.getFactionId(), k -> new int[2]);
                factionTotals[0] += production;
                factionTotals[1] += demand;
            }
        }

        int effectiveDemand = 0;
        for (FactionAPI faction : factions) {
            final String id = faction.getId();
            if (id.equals(factionId)) continue;
            final int prod;
            final int dem;
            if (id.equals(Factions.NEUTRAL)) {
                prod = sectorProduction;
                dem = sectorDemand + getSectorContractDemand(commodityId);
            } else {
                final int[] factionTotals = totals.get(id);
                prod = factionTotals == null ? 0 : factionTotals[0];
                final int marketDemand = factionTotals == null ? 0 : factionTotals[1];
                dem = id.equals(Factions.PLAYER) ? marketDemand + getPlayerContractDemand(commodityId) : marketDemand;
            }
            final int effectiveDem = dem - prod;
            if (effectiveDem > 0) effectiveDemand += effectiveDem;
        }
        return effectiveDemand;
    }

    /** The contract part of getTotalDemandFromFaction(commodityId, Factions.PLAYER). */
    private static int getPlayerContractDemand(String commodityId) {
        int prod = 0;
        for (AoTDTradeContract value : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
            if (value.isExpired() || value.isTerminated() || value.isContractFrozen()) continue;
            if (value.getContractData().containsKey(commodityId)) {
                prod += value.getContractData().get(commodityId).getReqMonthly();
            }
        }
        return prod;
    }

    /** The contract part of getTotalDemandFromSector(commodityId). */
    private static int getSectorContractDemand(String commodityId) {
        int prod = 0;
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

    public static int getTotalDemandFromSectorOutsideFromFaction(String commodityId, String factionId) {
        int prod = 0;
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            if (marketAPI.getFaction().getId().equals(factionId)) continue;
            if (marketAPI.getCommodityData(commodityId) instanceof AoTDCommodityOnMarket com) {
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
        return (int) (Global.getSettings().getCommoditySpec(commodityId).getBasePrice() * amount * AoTDCommodityEconSpecManager.getCutForCommodity(commodityId, false));
    }

    public static int getPriceForAmount(String commodityId, int amount, boolean internal) {
        return (int) (Global.getSettings().getCommoditySpec(commodityId).getBasePrice() * amount * AoTDCommodityEconSpecManager.getCutForCommodity(commodityId, internal));
    }

    public static int getPriceAmountTotalAroundSectorForFaction(String commodityId, int dem, int supply, String factionId) {
        final int demByFaction = getTotalDemandFromSectorOutsideFromFaction(commodityId, factionId);
        final int outsideDEm = dem - demByFaction;
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
        int extra = supply - dem;
        int original = getPriceForAmount(commodityId, dem);
        if (extra > 1 && !Global.getSettings().getCommoditySpec(commodityId).hasTag(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS)) {
            extra /= 2;
            original += Math.round(getPriceForAmount(commodityId, extra) * AoTDTradeManager.multFromSellingExcess);
        }
        return original;
    }

    public static int getPercentageOfDemandFromSector(String commodityId, int amount) {
        return Math.round(((float) amount / getTotalDemandFromSector(commodityId)) * 100f);
    }
}