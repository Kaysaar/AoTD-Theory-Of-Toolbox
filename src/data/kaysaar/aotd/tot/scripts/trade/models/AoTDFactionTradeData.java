// file: data/kaysaar/aotd/tot/scripts/trade/models/AoTDFactionTradeData.java
package data.kaysaar.aotd.tot.scripts.trade.models;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.history.FactionCycleProductionData;
import data.kaysaar.aotd.tot.scripts.trade.history.FactionProductionData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public void computeInternalTrade() {
        for (AoTDMarketData md : tradeData.values()) {
            md.resetInternalResults();
        }

        LinkedHashMap<String, CommodityBucket> buckets = new LinkedHashMap<>();

        for (AoTDMarketData md : tradeData.values()) {
            for (Map.Entry<String, Integer> e : md.netProductionValues.entrySet()) {
                String commodityId = e.getKey();
                int net = e.getValue();
                if (net == 0) continue;
                MarketAPI market = Global.getSector().getEconomy().getMarket(md.marketId);
                if(market==null)continue;
                if(!market.hasSpaceport()||market.getAccessibilityMod().computeEffective(0f)<=0f)continue;
                CommodityBucket b = buckets.computeIfAbsent(commodityId, k -> new CommodityBucket());
                if (net > 0) {
                    b.exporters.add(new MarketAmount(md, net, md.weight));
                    b.totalSupply += net;
                } else {
                    int need = -net;
                    b.importers.add(new MarketAmount(md, need, md.weight));
                    b.totalNeed += need;
                }
            }
        }

        for (Map.Entry<String, CommodityBucket> entry : buckets.entrySet()) {
            String commodityId = entry.getKey();
            CommodityBucket b = entry.getValue();

            if (b.totalSupply <= 0 || b.totalNeed <= 0) continue;

            b.exporters.sort((a, c) -> Float.compare(c.weight, a.weight));
            b.importers.sort((a, c) -> Float.compare(c.weight, a.weight));

            int i = 0, j = 0;
            while (i < b.exporters.size() && j < b.importers.size()) {
                MarketAmount ex = b.exporters.get(i);
                MarketAmount im = b.importers.get(j);

                if (ex.amount <= 0) { i++; continue; }
                if (im.amount <= 0) { j++; continue; }

                int moved = Math.min(ex.amount, im.amount);

                ex.m.addInternalSent(commodityId, moved);
                im.m.addInternalReceived(commodityId, moved);

                ex.amount -= moved;
                im.amount -= moved;

                if (ex.amount == 0) i++;
                if (im.amount == 0) j++;
            }
        }

        // ---- Hook: internal trade changes remainingNet, so contract predictions must refresh ----
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        if (playerFactionId != null && playerFactionId.equals(this.faction)) {
            AoTDTradeContractManager mgr = AoTDTradeContractManager.getInstance();
            mgr.invalidatePredictions();
            // Optional: precompute now so UI never recomputes on hover
            mgr.ensurePredictionsUpToDate();
        }
    }

    public LinkedHashMap<String, AoTDMarketData> getTradeData() {
        return tradeData;
    }
}