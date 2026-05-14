// file: data/kaysaar/aotd/tot/scripts/trade/models/AoTDMarketData.java
package data.kaysaar.aotd.tot.scripts.trade.models;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;

import java.util.LinkedHashMap;

public class AoTDMarketData {
    public String marketId;
    public Object readResolve(){
        if(soldOutside==null){
            soldOutside = new LinkedHashMap<>();
        }
        if(extraSold==null){
            extraSold = new LinkedHashMap<>();
        }
        return this;
    }

    /** Net export (positive) / net import demand (negative). Snapshot at build time. */
    public  LinkedHashMap<String, Integer> netProductionValues = new LinkedHashMap<>();
    public LinkedHashMap<String,Integer>extraSold = new LinkedHashMap<>();
    /** Internal trade results. */
    public  LinkedHashMap<String, Integer> internalSent = new LinkedHashMap<>();
    public  LinkedHashMap<String, Integer> internalReceived = new LinkedHashMap<>();

    public  LinkedHashMap<String,Integer>soldOutside = new LinkedHashMap<>();
    /** Remaining net after internal trade / contracts / external trade bookkeeping. */
    public  LinkedHashMap<String, Integer> remainingNet = new LinkedHashMap<>();

    /**
     * Amount of export removed by the surplus-cap AFTER matching.
     * Used for producer bonuses.
     */
    public  LinkedHashMap<String, Integer> externalExcessExported = new LinkedHashMap<>();

    /** Actual: contractId -> (commodityId -> amount shipped for that contract THIS MONTH) */
    public  LinkedHashMap<String, LinkedHashMap<String, Integer>> exportedByContract = new LinkedHashMap<>();

    /** Predicted: contractId -> (commodityId -> amount that WOULD be shipped this month) */
    public  LinkedHashMap<String, LinkedHashMap<String, Integer>> predictedExportedByContract = new LinkedHashMap<>();

    /** Base weights (per-market). */
    public float weight;
    public float outsideWeight;

    /** Normal constructor from a real market. */
    public AoTDMarketData(MarketAPI market) {
        this.marketId = market.getId();

        for (CommodityOnMarketAPI allCommodity : market.getAllCommodities()) {
            if (allCommodity instanceof AoTDCommodityOnMarket com) {
                int net = com.getSupplyDemandData().getRawNetExport(); // +excess, -deficit
                if (net != 0) {
                    netProductionValues.put(com.getId(), net);
                    remainingNet.put(com.getId(), net);
                }
            }
        }

        this.weight = market.getAccessibilityMod().computeEffective(0f) * 100f;
        this.outsideWeight = Math.max(market.getAccessibilityMod().computeEffective(0f) * 100f,20);
    }

    /** Dummy constructor for synthetic offers (e.g., scavengers). */
    private AoTDMarketData(String id) {
        this.marketId = id;
        this.weight = 0f;
        this.outsideWeight = 0f;
    }

    public static AoTDMarketData createScavengerDummy() {
        return new AoTDMarketData("SCAVENGER_GUILD");
    }

    /** Reset internal results + remainingNet back to original snapshot. */
    public void resetInternalResults() {
        internalSent.clear();
        internalReceived.clear();
        remainingNet.clear();
        remainingNet.putAll(netProductionValues);
    }

    /** Reset per-month external accounting (excess-exported tracking). */
    public void resetExternalResults() {
        externalExcessExported.clear();
        soldOutside.clear();
        extraSold.clear();

    }

    /** Call once per month before contracts. (Actual shipments) */
    public void resetContractResults() {
        exportedByContract.clear();
    }

    /** Called by contract execution (actual shipments). */
    public void recordContractExport(String contractId, String commodityId, int amount) {
        if (amount <= 0) return;
        if (contractId == null || commodityId == null) return;

        exportedByContract
                .computeIfAbsent(contractId, k -> new LinkedHashMap<>())
                .merge(commodityId, amount, Integer::sum);
    }

    public int getContractExported(String contractId, String commodityId) {
        LinkedHashMap<String, Integer> m = exportedByContract.get(contractId);
        if (m == null) return 0;
        return m.getOrDefault(commodityId, 0);
    }

    // ------------------- PREDICTIONS -------------------

    /** Call when prediction manager rebuilds. */
    public void resetContractPredictions() {
        predictedExportedByContract.clear();
    }

    /** Called by prediction dry-run. */
    public void recordPredictedContractExport(String contractId, String commodityId, int amount) {
        if (amount <= 0) return;
        if (contractId == null || commodityId == null) return;

        predictedExportedByContract
                .computeIfAbsent(contractId, k -> new LinkedHashMap<>())
                .merge(commodityId, amount, Integer::sum);
    }

    public int getPredictedContractExported(String contractId, String commodityId) {
        LinkedHashMap<String, Integer> m = predictedExportedByContract.get(contractId);
        if (m == null) return 0;
        return m.getOrDefault(commodityId, 0);
    }

    // ------------------- READ HELPERS -------------------

    public int getOriginalNet(String commodityId) {
        return netProductionValues.getOrDefault(commodityId, 0);
    }

    public int getInternalExported(String commodityId) {
        return internalSent.getOrDefault(commodityId, 0);
    }

    public int getInternalImported(String commodityId) {
        return internalReceived.getOrDefault(commodityId, 0);
    }

    public int getRemainingNet(String commodityId) {
        return remainingNet.getOrDefault(commodityId, 0);
    }
    public int getExtraSoldOutside(String commodityId){return extraSold.getOrDefault(commodityId, 0);}
    public int getSoldOutside(String commodityId){return soldOutside.getOrDefault(commodityId, 0);}
    public int getExternalExcessExported(String commodityId) {
        return externalExcessExported.getOrDefault(commodityId, 0);
    }
    public void addExtraSold(String commodityId, int amount) {
        int curr = getExtraSoldOutside(commodityId);
        extraSold.put(commodityId, curr + amount);
    }

    // ------------------- INTERNAL APPLICATION -------------------
    public void addSoldOutside(String commodityId, int amount) {
        int curr = getSoldOutside(commodityId);
        soldOutside.put(commodityId, curr + amount);
    }
    public void addInternalSent(String commodityId, int amount) {
        if (amount <= 0) return;

        int avail = remainingNet.getOrDefault(commodityId, 0);
        if (avail <= 0) return;

        int moved = Math.min(amount, avail);
        internalSent.merge(commodityId, moved, Integer::sum);

        int newVal = avail - moved;
        if (newVal == 0) remainingNet.remove(commodityId);
        else remainingNet.put(commodityId, newVal);
    }

    public void addInternalReceived(String commodityId, int amount) {
        if (amount <= 0) return;

        int needSigned = remainingNet.getOrDefault(commodityId, 0);
        if (needSigned >= 0) return;

        int need = -needSigned;
        int moved = Math.min(amount, need);
        internalReceived.merge(commodityId, moved, Integer::sum);

        int newNeed = need - moved;
        if (newNeed == 0) remainingNet.remove(commodityId);
        else remainingNet.put(commodityId, -newNeed);
    }
}