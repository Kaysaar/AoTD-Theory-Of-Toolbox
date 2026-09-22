package data.kaysaar.aotd.tot.scripts.trade.models;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.economy.AoTDWorkerManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.history.FactionCycleProductionData;
import data.kaysaar.aotd.tot.scripts.trade.history.FactionProductionData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AoTDFactionTradeData {

    private final LinkedHashMap<String, AoTDMarketData> tradeData = new LinkedHashMap<>();
    private final String faction;

    protected LinkedHashMap<Integer, FactionCycleProductionData> cycleProductionData;
    public int startingCycleOfData = 0;
    public int startingMonthOfCycle = 0;
    public boolean hasSetStartingDate = false;

    public AoTDFactionTradeData(String faction) {
        this.faction = faction;
        this.cycleProductionData = new LinkedHashMap<>();
    }

    public void removeMarket(MarketAPI market) {
        if (tradeData.containsKey(market.getId())) {
            tradeData.remove(market.getId());
            computeInternalTrade(); // rebuild remainingNet
        }
    }

    private boolean isBeforeStartOfHistory(int cycle, int month) {
        if (!hasSetStartingDate) return true;

        if (cycle < startingCycleOfData) return true;
        return cycle == startingCycleOfData && month < startingMonthOfCycle;
    }

    public void doEndOfMonthStuffForHistory(int month) {
        if (!hasSetStartingDate) {
            hasSetStartingDate = true;
            startingCycleOfData = Global.getSector().getClock().getCycle();
            if (month == -1) {
                startingCycleOfData = Global.getSector().getClock().getCycle() - 1;
                month = 12;
                startingMonthOfCycle = 12;
                FactionCycleProductionData productionData = new FactionCycleProductionData(faction);

                this.cycleProductionData.put(startingCycleOfData, productionData);
                productionData.doEndOfMonth(month);
            }
        } else {
            if (!cycleProductionData.containsKey(Global.getSector().getClock().getCycle())) {
                FactionCycleProductionData productionData = new FactionCycleProductionData(faction);
                this.cycleProductionData.put(Global.getSector().getClock().getCycle(), productionData);
            }
            FactionCycleProductionData productionData = cycleProductionData.get(Global.getSector().getClock().getCycle());
            productionData.doEndOfMonth(month);
        }
    }

    public ArrayList<Integer> getProductionFromMonths(String commodityId) {
        return getProductionFromMonths(Global.getSector().getClock().getCycle(), Global.getSector().getClock().getMonth() - 1, commodityId, Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getDemandFromMonths(String commodityId) {
        return getDemandFromMonths(Global.getSector().getClock().getCycle(), Global.getSector().getClock().getMonth() - 1, commodityId, Integer.MAX_VALUE);
    }

    public ArrayList<Integer>  getProductionFromMonths(int months, String commodityId) {
        return getProductionFromMonths(Global.getSector().getClock().getCycle(), Global.getSector().getClock().getMonth() - 1, commodityId, months);
    }

    public ArrayList<Integer> getDemandFromMonths(int months, String commodityId) {
        return getDemandFromMonths(Global.getSector().getClock().getCycle(), Global.getSector().getClock().getMonth() - 1, commodityId, months);
    }

    public ArrayList<Integer> getProductionFromMonths(int startingCycle, int startingMonth, String commodityId) {
        return getProductionFromMonths(startingCycle, startingMonth, commodityId, Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getDemandFromMonths(int startingCycle, int startingMonth, String commodityId) {
        return getDemandFromMonths(startingCycle, startingMonth, commodityId, Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getProductionFromMonths(int startingCycle, int startingMonth, String commodityId, int monthsBack) {
        ArrayList<Integer> result = new ArrayList<>();
        if (monthsBack <= 0) return result;

        if (!hasSetStartingDate) return result;

        int cycle = startingCycle;
        int month = startingMonth;
        if(startingMonth<=0){
            cycle--;
            month=12;
        }
        for (int i = 0; i < monthsBack; i++) {
            if (isBeforeStartOfHistory(cycle, month)) break;

            int val = 0;

            FactionCycleProductionData cycleData = cycleProductionData.get(cycle);
            if (cycleData != null) {
                FactionProductionData monthData = cycleData.getProductionFromMonth(month);
                if (monthData != null) {
                    Integer v = monthData.getProductionValueFromMonth(commodityId);
                    if (v != null) val = v;
                }
            }

            result.add(val);

            month--;
            if (month < 1) {
                month = 12;
                cycle--;
            }
        }

        Collections.reverse(result);
        return result;
    }

    public ArrayList<Integer> getDemandFromMonths(int startingCycle, int startingMonth, String commodityId, int monthsBack) {
        ArrayList<Integer> result = new ArrayList<>();
        if (monthsBack <= 0) return result;
        if (!hasSetStartingDate) return result;

        int cycle = startingCycle;
        int month = startingMonth;
        if(startingMonth<=0){
            cycle--;
            month=12;
        }

        for (int i = 0; i < monthsBack; i++) {
            if (isBeforeStartOfHistory(cycle, month)) break;

            int val = 0;

            FactionCycleProductionData cycleData = cycleProductionData.get(cycle);
            if (cycleData != null) {
                FactionProductionData monthData = cycleData.getProductionFromMonth(month);
                if (monthData != null) {
                    Integer v = monthData.getDemandValueFromMonth(commodityId);
                    if (v != null) val = v;
                }
            }

            result.add(val);

            month--;
            if (month < 1) {
                month = 12;
                cycle--;
            }
        }

        Collections.reverse(result);
        return result;
    }

    public void addMarket(MarketAPI market) {
        tradeData.put(market.getId(), new AoTDMarketData(market));
        // no cached flag anymore; computeInternalTrade is cheap enough and you call it once/month anyway
    }

    public void reset() {
        tradeData.clear();
    }

    public FactionAPI getFaction() {
        return Global.getSector().getFaction(faction);
    }

    public int getFactionEffectiveDemand(String commodityId) {
        int net = 0;
        for (AoTDMarketData md : tradeData.values()) {
            net += md.netProductionValues.getOrDefault(commodityId, 0);
        }
        return Math.max(0, -net);
    }

    public int getFactionDemand(String commodityId) {

        return AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(commodityId,faction);
    }

    public int getFactionSupply(String commodityId) {

        return AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(commodityId,faction);
    }

    // ---------- internal trade solver ----------

    private static final class MarketAmount {
        final AoTDMarketData m;
        int amount;
        final float weight;

        MarketAmount(AoTDMarketData m, int amount, float weight) {
            this.m = m;
            this.amount = amount;
            this.weight = weight;
        }
    }

    private static final class CommodityBucket {
        final ArrayList<MarketAmount> exporters = new ArrayList<>();
        final ArrayList<MarketAmount> importers = new ArrayList<>();
        int totalSupply;
        int totalNeed;
    }

    /**
     * Computes internal trade and updates remainingNet.
     * ALSO: if this is player faction, it invalidates and (optionally) precomputes contract predictions,
     * because remainingNet is what contracts draw from.
     */
    private static final Comparator<MarketAmount> MARKET_AMOUNT_WEIGHT_DESC =
            (a, b) -> Float.compare(b.weight, a.weight);
    public void computeInternalTrade() {
        computeInternalTrade(true);
    }

    private transient volatile List<AllocationRow> cachedInternalAllocation;

    private static boolean eligibleForInternalTrade(AoTDMarketData md, MarketAPI market) {
        return !md.netProductionValues.isEmpty() && market != null && market.hasSpaceport()
                && !(market.getAccessibilityMod().computeEffective(0f) <= 0f);
    }

    private static boolean sameOrderedMap(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.size() != b.size()) return false;
        Iterator<Map.Entry<String, Integer>> ai = a.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> bi = b.entrySet().iterator();
        while (ai.hasNext()) if (!ai.next().equals(bi.next())) return false;
        return true;
    }

    private static final class AllocationRow {
        final String marketId;
        final int weightBits;
        final boolean eligible;
        final LinkedHashMap<String, Integer> net, sent, received, remaining;
        AllocationRow(AoTDMarketData md, MarketAPI market) {
            marketId = md.marketId;
            weightBits = Float.floatToIntBits(md.weight);
            eligible = eligibleForInternalTrade(md, market);
            net = new LinkedHashMap<>(md.netProductionValues);
            sent = new LinkedHashMap<>(md.internalSent);
            received = new LinkedHashMap<>(md.internalReceived);
            remaining = new LinkedHashMap<>(md.remainingNet);
        }
        boolean matches(AoTDMarketData md, MarketAPI market) {
            return Objects.equals(marketId, md.marketId)
                    && weightBits == Float.floatToIntBits(md.weight)
                    && eligible == eligibleForInternalTrade(md, market)
                    && sameOrderedMap(net, md.netProductionValues);
        }
        void restore(AoTDMarketData md) {
            // Restore the POST-INTERNAL baseline, not a previous settlement's
            // contract/external-trade-consumed balances. Leave monthly records alone.
            md.internalSent.clear(); md.internalSent.putAll(sent);
            md.internalReceived.clear(); md.internalReceived.putAll(received);
            md.remainingNet.clear(); md.remainingNet.putAll(remaining);
        }
    }

    private boolean restoreCachedInternalAllocation(Map<String, MarketAPI> marketsById) {
        List<AllocationRow> rows = cachedInternalAllocation;
        if (rows == null || rows.size() != tradeData.size()) return false;
        int i = 0;
        for (AoTDMarketData md : tradeData.values()) {
            if (!rows.get(i++).matches(md, marketsById.get(md.marketId))) return false;
        }
        i = 0;
        for (AoTDMarketData md : tradeData.values()) rows.get(i++).restore(md);
        return true;
    }

    private void rememberInternalAllocation(Map<String, MarketAPI> marketsById) {
        ArrayList<AllocationRow> rows = new ArrayList<>(tradeData.size());
        for (AoTDMarketData md : tradeData.values()) {
            rows.add(new AllocationRow(md, marketsById.get(md.marketId)));
        }
        cachedInternalAllocation = Collections.unmodifiableList(rows);
    }

    public static Map<String, MarketAPI> snapshotMarketsById() {
        Map<String, MarketAPI> result = new HashMap<>();
        for (MarketAPI market : AoTDEconomy.getInstance().getMarkets()) {
            result.putIfAbsent(market.getId(), market);
        }
        return Collections.unmodifiableMap(result);
    }

    public void computeInternalTrade(boolean refreshContractPredictions) {
        if (tradeData.isEmpty()) return;
        computeInternalTrade(refreshContractPredictions, snapshotMarketsById());
    }

    public void computeInternalTrade(boolean refreshContractPredictions,
                                     Map<String, MarketAPI> marketsById) {
        if (tradeData.isEmpty()) {
            cachedInternalAllocation = null;
            return;
        }
        if (restoreCachedInternalAllocation(marketsById)) {
            if (refreshContractPredictions) refreshContractPredictionsIfPlayerFaction();
            return;
        }

        ArrayList<AoTDMarketData> eligibleMarkets = new ArrayList<>(tradeData.size());


        for (AoTDMarketData md : tradeData.values()) {
            md.resetInternalResults();

            if (md.netProductionValues.isEmpty()) continue;

            MarketAPI market = marketsById.get(md.marketId);
            if (market == null) continue;
            if (!market.hasSpaceport()) continue;
            if (market.getAccessibilityMod().computeEffective(0f) <= 0f) continue;

            eligibleMarkets.add(md);
        }

        if (eligibleMarkets.size() <= 1) {
            rememberInternalAllocation(marketsById);
            if (refreshContractPredictions) {
                    refreshContractPredictionsIfPlayerFaction();
                }

            return;
        }

        LinkedHashMap<String, CommodityBucket> buckets = new LinkedHashMap<>();

        // Preserve original first-seen commodity order independently of market priority.
        // Internal result maps expose this order to downstream consumers.
        for (AoTDMarketData md : eligibleMarkets) {
            AoTDWorkerManager.checkpoint();
            for (Map.Entry<String, Integer> entry : md.netProductionValues.entrySet()) {
                if (entry.getValue() != 0) {
                    buckets.computeIfAbsent(entry.getKey(), id -> new CommodityBucket());
                }
            }
        }
        // All commodities use the same weight ordering. Stable sorting preserves
        // original market order on ties (including signed zero/NaN Float semantics).
        // Each bucket is then a sorted subsequence, so per-commodity sorts disappear.
        eligibleMarkets.sort((a, b) -> Float.compare(b.weight, a.weight));

        for (AoTDMarketData md : eligibleMarkets) {
            AoTDWorkerManager.checkpoint();
            for (Map.Entry<String, Integer> entry : md.netProductionValues.entrySet()) {
                int net = entry.getValue();
                if (net == 0) continue;

                CommodityBucket bucket = buckets.get(entry.getKey());

                if (net > 0) {
                    bucket.exporters.add(new MarketAmount(md, net, md.weight));
                    bucket.totalSupply += net;
                } else {
                    int need = -net;
                    bucket.importers.add(new MarketAmount(md, need, md.weight));
                    bucket.totalNeed += need;
                }
            }
        }

        for (Map.Entry<String, CommodityBucket> entry : buckets.entrySet()) {
            AoTDWorkerManager.checkpoint();
            CommodityBucket bucket = entry.getValue();

            if (bucket.totalSupply <= 0 || bucket.totalNeed <= 0) continue;

            ArrayList<MarketAmount> exporters = bucket.exporters;
            ArrayList<MarketAmount> importers = bucket.importers;

            String commodityId = entry.getKey();

            int exporterIndex = 0;
            int importerIndex = 0;

            while (exporterIndex < exporters.size() && importerIndex < importers.size()) {
                MarketAmount exporter = exporters.get(exporterIndex);
                MarketAmount importer = importers.get(importerIndex);

                int moved = Math.min(exporter.amount, importer.amount);
                if (moved <= 0) {
                    if (exporter.amount <= 0) exporterIndex++;
                    if (importer.amount <= 0) importerIndex++;
                    continue;
                }

                exporter.m.addInternalSent(commodityId, moved);
                importer.m.addInternalReceived(commodityId, moved);

                exporter.amount -= moved;
                importer.amount -= moved;

                if (exporter.amount <= 0) exporterIndex++;
                if (importer.amount <= 0) importerIndex++;
            }
        }

        rememberInternalAllocation(marketsById);
        if (refreshContractPredictions) {
            refreshContractPredictionsIfPlayerFaction();
        }
    }

    public void refreshContractPredictionsIfPlayerFaction() {
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        if (!faction.equals(playerFactionId)) return;

        AoTDTradeContractManager mgr = AoTDTradeContractManager.getInstance();
        mgr.invalidatePredictions();
        mgr.ensurePredictionsUpToDate();
    }
    public LinkedHashMap<String, AoTDMarketData> getTradeData() {
        return tradeData;
    }
}
