package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;

import data.kaysaar.aotd.tot.plugins.AoTDBaseDemSupCalc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpec;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.plugins.AoTDExponentialSupDemCalc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AoTDSupplyDemandData {
    public LinkedHashMap<String, MutableStat> demandUnitsFromIndustries = new LinkedHashMap<>();
    public LinkedHashMap<String, MutableStat> supplyUnitsFromIndustries = new LinkedHashMap<>();
    public String commodityID;

    public AoTDSupplyDemandData(String commodityID) {
        this.commodityID = commodityID;
    }

    public transient AoTDCommodityEconSpec ecSpec;
    public int supply, demand, available;
    private transient int batchDemandExceptPending;
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

    private static final ThreadLocal<MarketUpdateScope> ACTIVE_UPDATE = new ThreadLocal<>();

    /** A phase-local snapshot, never retained across frames or published across threads. */
    public static MarketUpdateScope beginMarketUpdateBatch() {
        MarketUpdateScope scope = new MarketUpdateScope(ACTIVE_UPDATE.get());
        ACTIVE_UPDATE.set(scope);
        return scope;
    }

    public static final class MarketUpdateScope implements AutoCloseable {
        private final MarketUpdateScope previous;
        private final Map<MarketAPI, Set<AoTDSupplyDemandData>> refreshed = new IdentityHashMap<>();
        private boolean building;
        private MarketUpdateScope(MarketUpdateScope previous) { this.previous = previous; }

        private boolean refresh(MarketAPI market, AoTDSupplyDemandData data) {
            if (building || !AoTDCommodityEconSpecManager.getListeners().isEmpty()) return false;
            Set<AoTDSupplyDemandData> values = refreshed.get(market);
            if (values == null) {
                building = true;
                try {
                    values = refreshMarket(market, false);
                    refreshed.put(market, values);
                } finally {
                    building = false;
                }
            }
            return values.contains(data);
        }

        private boolean hasSnapshot(MarketAPI market, AoTDSupplyDemandData data) {
            Set<AoTDSupplyDemandData> values = refreshed.get(market);
            return AoTDCommodityEconSpecManager.getListeners().isEmpty()
                    && values != null && values.contains(data);
        }

        @Override
        public void close() {
            if (previous == null) ACTIVE_UPDATE.remove();
            else ACTIVE_UPDATE.set(previous);
        }
    }

    /** Rebuild all AoTD commodity totals by traversing each industry's entries once. */
    public static void updateSupplyDemandDataAcrossMarket(MarketAPI market) {
        refreshMarket(market, true);
        // Explicit refresh invalidates a surrounding phase's memo, if present.
        MarketUpdateScope scope = ACTIVE_UPDATE.get();
        if (scope != null) scope.refreshed.remove(market);
    }

    private static final class IndustryInput {
        final MutableStat quantity;
        IndustryInput(MutableStat quantity) {
            this.quantity = quantity;
        }
    }

    private static final class CommodityInputs {
        final AoTDSupplyDemandData data;
        final AoTDBaseDemSupCalc calculator;
        final Map<String, IndustryInput> supplies = new LinkedHashMap<>();
        final Map<String, IndustryInput> demands = new LinkedHashMap<>();
        CommodityInputs(AoTDSupplyDemandData data, AoTDBaseDemSupCalc calculator) {
            this.data = data;
            this.calculator = calculator;
        }
    }

    private static AoTDSupplyDemandData dataWithoutRefresh(AoTDCommodityOnMarket commodity) {
        // Lazy initialization must not start another industry scan from inside this scan.
        return commodity.getAoTDAvailableStat().getSupplyDemandData(commodity, false);
    }

    private static boolean supportsSparseUpdate(AoTDBaseDemSupCalc calculator) {
        // Missing demand entries can become nonzero through a listener. Unknown custom
        // calculators may also have nonzero-at-zero behavior or side effects.
        return AoTDCommodityEconSpecManager.getListeners().isEmpty()
                && (calculator.getClass() == AoTDBaseDemSupCalc.class
                    || calculator.getClass() == AoTDExponentialSupDemCalc.class);
    }

    private static void addCommodity(CommodityOnMarketAPI commodity,
                                     Map<String, CommodityInputs> inputs,
                                     List<AoTDSupplyDemandData> fallback, boolean includeFallback) {
        if (!(commodity instanceof AoTDCommodityOnMarket aotd)) return;
        AoTDSupplyDemandData existing = aotd.getAoTDAvailableStat().getSupplyDemandDataIfInitialized();
        AoTDCommodityEconSpec spec = existing == null
                ? AoTDCommodityEconSpecManager.getEconSpec(commodity.getId()) : existing.getEconSpec();
        AoTDBaseDemSupCalc calculator = spec.getCalculationScript();
        if (supportsSparseUpdate(calculator)) {
            inputs.put(commodity.getId(), new CommodityInputs(dataWithoutRefresh(aotd), calculator));
        } else if (includeFallback) {
            fallback.add(dataWithoutRefresh(aotd));
        }
    }

    private static Set<AoTDSupplyDemandData> refreshMarket(MarketAPI market, boolean includeFallback) {
        if (!includeFallback && !AoTDCommodityEconSpecManager.getListeners().isEmpty()) {
            return Collections.emptySet();
        }
        Map<String, CommodityInputs> inputs = new LinkedHashMap<>();
        List<AoTDSupplyDemandData> fallback = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        // Include previously present commodities even when no industry lists them now.
        // Their next totals/maps must become empty instead of retaining old production.
        for (CommodityOnMarketAPI commodity : new ArrayList<>(market.getAllCommodities())) {
            known.add(commodity.getId());
            addCommodity(commodity, inputs, fallback, includeFallback);
        }
        Map<String, Industry> firstIndustry = new LinkedHashMap<>();
        for (Industry industry : market.getIndustries()) {
            firstIndustry.putIfAbsent(industry.getId(), industry);
            collectInputs(market, industry, industry.getAllSupply(), true, known, inputs, fallback, includeFallback);
            collectInputs(market, industry, industry.getAllDemand(), false, known, inputs, fallback, includeFallback);
        }
        Set<AoTDSupplyDemandData> updated = Collections.newSetFromMap(new IdentityHashMap<>());
        AoTDIndustryData industryData = AoTDIndustryData.getInstance(market);
        for (CommodityInputs input : inputs.values()) {
            int newSupply = 0;
            int newDemand = 0;
            int nonPendingDemand = 0;
            for (Map.Entry<String, IndustryInput> entry : input.supplies.entrySet()) {
                newSupply += input.calculator.getRawUnitsFromSupply(entry.getValue().quantity,
                        null, input.data.commodityID, firstIndustry.get(entry.getKey()));
            }
            for (Map.Entry<String, IndustryInput> entry : input.demands.entrySet()) {
                int raw = input.calculator.getRawUnitsFromDemand(entry.getValue().quantity,
                        null, input.data.commodityID, firstIndustry.get(entry.getKey()));
                newDemand += raw;
                if (!industryData.isPending(entry.getKey())) nonPendingDemand += raw;
            }
            input.data.supplyUnitsFromIndustries.clear();
            input.data.demandUnitsFromIndustries.clear();
            for (Map.Entry<String, IndustryInput> entry : input.supplies.entrySet()) {
                input.data.supplyUnitsFromIndustries.put(entry.getKey(), entry.getValue().quantity);
            }
            for (Map.Entry<String, IndustryInput> entry : input.demands.entrySet()) {
                input.data.demandUnitsFromIndustries.put(entry.getKey(), entry.getValue().quantity);
            }
            input.data.supply = newSupply;
            input.data.demand = newDemand;
            input.data.batchDemandExceptPending = nonPendingDemand;
            if (newSupply != 0 || newDemand != 0) {
                AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied().add(input.data.commodityID);
            }
            updated.add(input.data);
        }
        if (includeFallback) {
            for (AoTDSupplyDemandData data : fallback) data.updateSupplyDemandDataIndividually(market);
        }
        return updated;
    }

    private static void collectInputs(MarketAPI market, Industry industry,
                                      List<MutableCommodityQuantity> entries, boolean supply,
                                      Set<String> known, Map<String, CommodityInputs> inputs,
                                      List<AoTDSupplyDemandData> fallback, boolean includeFallback) {
        for (MutableCommodityQuantity entry : entries) {
            String id = entry.getCommodityId();
            if (known.add(id)) addCommodity(market.getCommodityData(id), inputs, fallback, includeFallback);
            CommodityInputs input = inputs.get(id);
            if (input == null) continue;
            Map<String, IndustryInput> target = supply ? input.supplies : input.demands;
            target.put(industry.getId(), new IndustryInput(entry.getQuantity()));
        }
    }

    public void updateSupplyDemandData(MarketAPI market) {
        MarketUpdateScope scope = ACTIVE_UPDATE.get();
        if (scope != null && scope.refresh(market, this)) return;
        updateSupplyDemandDataIndividually(market);
    }

    private void updateSupplyDemandDataIndividually(MarketAPI market) {
        // Stage live references before modifying public maps, preserving failure behavior.
        // Arrays avoid two temporary LinkedHashMaps and their per-industry nodes.
        String[] ids;
        MutableStat[] demands;
        MutableStat[] supplies;
        int count = 0;
        try {
            Collection<Industry> industries = market.getIndustries();
            int capacity = industries.size();
            ids = new String[capacity];
            demands = new MutableStat[capacity];
            supplies = new MutableStat[capacity];
            for (Industry industry : industries) {
                if (count == ids.length) {
                    int size = Math.max(4, count * 2);
                    ids = Arrays.copyOf(ids, size);
                    demands = Arrays.copyOf(demands, size);
                    supplies = Arrays.copyOf(supplies, size);
                }
                ids[count] = industry.getId();
                demands[count] = industry.getDemand(commodityID).getQuantity();
                supplies[count] = industry.getSupply(commodityID).getQuantity();
                count++;
            }
        } catch (Exception e) {
            return;
        }
        supply = 0;
        demand = 0;
        replaceOrderedValues(demandUnitsFromIndustries, ids, demands, count);
        replaceOrderedValues(supplyUnitsFromIndustries, ids, supplies, count);
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

    /** Keep existing map entries when industry IDs and their order are unchanged.
     * Values are refreshed every time; no supply/demand result is cached.
     * Collection above still completes before either public map is modified.
     */
    private static void replaceOrderedValues(LinkedHashMap<String, MutableStat> target,
                                             String[] ids, MutableStat[] values, int count) {
        boolean sameOrder = target.size() == count;
        if (sameOrder) {
            int i = 0;
            for (String key : target.keySet()) {
                if (!Objects.equals(key, ids[i++])) {
                    sameOrder = false;
                    break;
                }
            }
        }
        if (!sameOrder) {
            target.clear();
            // put preserves first insertion order and last value for duplicate IDs.
            for (int i = 0; i < count; i++) target.put(ids[i], values[i]);
            return;
        }
        int i = 0;
        for (Map.Entry<String, MutableStat> entry : target.entrySet()) {
            entry.setValue(values[i++]);
        }
    }

    public int getDemandExceptPendingIndustries(MarketAPI market) {
        MarketUpdateScope scope = ACTIVE_UPDATE.get();
        if (scope != null && scope.hasSnapshot(market, this)) return batchDemandExceptPending;
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
        for (Industry industry : AoTDIndustryData.getInstance(market).getActiveIndustriesInOrder(market)) {

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
