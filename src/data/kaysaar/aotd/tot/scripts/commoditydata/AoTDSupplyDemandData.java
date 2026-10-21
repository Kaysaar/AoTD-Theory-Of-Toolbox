package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.combat.MutableStat;
import data.kaysaar.aotd.tot.industries.AoTDBaseIndustry;
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

    public transient AoTDCommodityEconSpec ecSpec;
    public int supply, demand, available;
    private transient int batchDemandExceptPending;
    private transient LinkedHashMap<String,Float> filledRawDemandFromIndustries;
    private transient boolean industryAllocation;

    public void clearIndustryAllocation() {
        if(filledRawDemandFromIndustries == null)filledRawDemandFromIndustries = new LinkedHashMap<>();
        else filledRawDemandFromIndustries.clear();
        industryAllocation = true;
    }

    public boolean hasIndustryAllocation() {
        return industryAllocation;
    }

    public int getRawFilledDemandFromIndustry(Industry industry) {
        if(AoTDIndustryData.getInstance(industry.getMarket()).isPending(industry.getId()))return 0;
        if(filledRawDemandFromIndustries == null)return 0;
        return Math.min(getRawDemandFromIndustry(industry),(int)Math.floor(filledRawDemandFromIndustries.getOrDefault(industry.getId(),0f)));
    }

    public int getRawDeficitFromIndustry(Industry industry) {
        if(AoTDIndustryData.getInstance(industry.getMarket()).isPending(industry.getId()))return 0;
        return Math.max(0,getRawDemandFromIndustry(industry) - getRawFilledDemandFromIndustry(industry));
    }

    public AoTDAdditionalProdData additionalProduction = new AoTDAdditionalProdData(0f);
    public AoTDAdditionalProdData additionalDemand = new AoTDAdditionalProdData(0f);

    public AoTDSupplyDemandData(String commodityID) {
        this.commodityID = commodityID;
    }

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

    public static MarketUpdateScope beginMarketUpdateBatch() {
        MarketUpdateScope scope = new MarketUpdateScope(ACTIVE_UPDATE.get());
        ACTIVE_UPDATE.set(scope);
        return scope;
    }

    public static final class MarketUpdateScope implements AutoCloseable {
        private final MarketUpdateScope previous;
        private final Map<MarketAPI, Set<AoTDSupplyDemandData>> refreshed = new IdentityHashMap<>();
        private boolean building;

        private MarketUpdateScope(MarketUpdateScope previous) {
            this.previous = previous;
        }

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
                    && values != null
                    && values.contains(data);
        }

        @Override
        public void close() {
            if (previous == null) ACTIVE_UPDATE.remove();
            else ACTIVE_UPDATE.set(previous);
        }
    }

    public static void updateSupplyDemandDataAcrossMarket(MarketAPI market) {
        refreshMarket(market, true);

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
        return commodity.getAoTDAvailableStat().getSupplyDemandData(commodity, false);
    }

    private static boolean supportsSparseUpdate(AoTDBaseDemSupCalc calculator) {
        return AoTDCommodityEconSpecManager.getListeners().isEmpty()
                && (calculator.getClass() == AoTDBaseDemSupCalc.class
                || calculator.getClass() == AoTDExponentialSupDemCalc.class);
    }

    private static void addCommodity(CommodityOnMarketAPI commodity,
                                     Map<String, CommodityInputs> inputs,
                                     List<AoTDSupplyDemandData> fallback,
                                     boolean includeFallback) {
        if (!(commodity instanceof AoTDCommodityOnMarket aotd)) return;

        AoTDSupplyDemandData existing = aotd.getAoTDAvailableStat().getSupplyDemandDataIfInitialized();
        AoTDCommodityEconSpec spec = existing == null
                ? AoTDCommodityEconSpecManager.getEconSpec(commodity.getId())
                : existing.getEconSpec();

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

        for (CommodityOnMarketAPI commodity : new ArrayList<>(market.getAllCommodities())) {
            known.add(commodity.getId());
            addCommodity(commodity, inputs, fallback, includeFallback);
        }

        Map<String, Industry> firstIndustry = new LinkedHashMap<>();

        for (Industry industry : market.getIndustries()) {
            firstIndustry.putIfAbsent(industry.getId(), industry);

            collectInputs(market, industry, industry.getAllSupply(), true, known, inputs, fallback, includeFallback);
            collectInputs(market, industry, industry instanceof AoTDBaseIndustry ind ? ind.getVanillaDemandEntries() : industry.getAllDemand(), false, known, inputs, fallback, includeFallback);
            if(industry instanceof AoTDBaseIndustry ind){
                collectExactInputs(market,ind,ind.getExactProduction(),true,known,inputs,fallback,includeFallback);
                collectExactInputs(market,ind,ind.getExactDemand(),false,known,inputs,fallback,includeFallback);
            }
        }

        Set<AoTDSupplyDemandData> updated = Collections.newSetFromMap(new IdentityHashMap<>());
        AoTDIndustryData industryData = AoTDIndustryData.getInstance(market);

        for (CommodityInputs input : inputs.values()) {
            int newSupply = 0;
            int newDemand = 0;
            int nonPendingDemand = 0;

            for (Map.Entry<String, IndustryInput> entry : input.supplies.entrySet()) {
                newSupply += AoTDIndustryAmounts.getProduction(firstIndustry.get(entry.getKey()),input.data.commodityID,
                        entry.getValue().quantity,input.calculator,null);
            }

            for (Map.Entry<String, IndustryInput> entry : input.demands.entrySet()) {
                int raw = AoTDIndustryAmounts.getDemand(firstIndustry.get(entry.getKey()),input.data.commodityID,
                        entry.getValue().quantity,input.calculator,null);

                newDemand += raw;

                if (!industryData.isPending(entry.getKey())) {
                    nonPendingDemand += raw;
                }
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

            if (input.data.getTotalRawUnitsFromSupply() != 0 || input.data.getTotalRawUnitsFromDemand() != 0) {
                AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied().add(input.data.commodityID);
            }

            updated.add(input.data);
        }

        if (includeFallback) {
            for (AoTDSupplyDemandData data : fallback) {
                data.updateSupplyDemandDataIndividually(market);
            }
        }

        return updated;
    }

    private static void collectInputs(MarketAPI market,
                                      Industry industry,
                                      List<MutableCommodityQuantity> entries,
                                      boolean supply,
                                      Set<String> known,
                                      Map<String, CommodityInputs> inputs,
                                      List<AoTDSupplyDemandData> fallback,
                                      boolean includeFallback) {
        for (MutableCommodityQuantity entry : entries) {
            String id = entry.getCommodityId();

            if (known.add(id)) {
                addCommodity(market.getCommodityData(id), inputs, fallback, includeFallback);
            }

            CommodityInputs input = inputs.get(id);
            if (input == null) continue;

            Map<String, IndustryInput> target = supply ? input.supplies : input.demands;
            target.put(industry.getId(), new IndustryInput(entry.getQuantity()));
        }
    }

    private static void collectExactInputs(MarketAPI market,AoTDBaseIndustry industry,Map<String,MutableStat> entries,
                                           boolean supply,Set<String> known,Map<String,CommodityInputs> inputs,
                                           List<AoTDSupplyDemandData> fallback,boolean includeFallback) {
        for (Map.Entry<String,MutableStat> entry : entries.entrySet()) {
            String id = entry.getKey();
            if(known.add(id))addCommodity(market.getCommodityData(id),inputs,fallback,includeFallback);
            CommodityInputs input = inputs.get(id);
            if(input == null)continue;
            (supply ? input.supplies : input.demands).put(industry.getId(),new IndustryInput(entry.getValue()));
        }
    }

    public void updateSupplyDemandData(MarketAPI market) {
        MarketUpdateScope scope = ACTIVE_UPDATE.get();
        if (scope != null && scope.refresh(market, this)) return;
        updateSupplyDemandDataIndividually(market);
    }

    private void updateSupplyDemandDataIndividually(MarketAPI market) {
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
                demands[count] = industry instanceof AoTDBaseIndustry ind && ind.hasExactDemand(commodityID)
                        ? ind.getExactDemand().get(commodityID) : industry.getDemand(commodityID).getQuantity();
                supplies[count] = industry instanceof AoTDBaseIndustry ind && ind.hasExactProduction(commodityID)
                        ? ind.getExactProduction().get(commodityID) : industry.getSupply(commodityID).getQuantity();
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
            supply += AoTDIndustryAmounts.getProduction(market.getIndustry(entry.getKey()),commodityID,
                    entry.getValue(),getEconSpec().getCalculationScript(),null);
        }

        for (Map.Entry<String, MutableStat> entry : demandUnitsFromIndustries.entrySet()) {
            demand += AoTDIndustryAmounts.getDemand(market.getIndustry(entry.getKey()),commodityID,
                    entry.getValue(),getEconSpec().getCalculationScript(),null);
        }

        if (getTotalRawUnitsFromSupply() != 0 || getTotalRawUnitsFromDemand() != 0) {
            AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied().add(commodityID);
        }
    }

    private static void replaceOrderedValues(LinkedHashMap<String, MutableStat> target,
                                             String[] ids,
                                             MutableStat[] values,
                                             int count) {
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

            for (int i = 0; i < count; i++) {
                target.put(ids[i], values[i]);
            }

            return;
        }

        int i = 0;

        for (Map.Entry<String, MutableStat> entry : target.entrySet()) {
            entry.setValue(values[i++]);
        }
    }

    public int getDemandExceptPendingIndustries(MarketAPI market) {
        MarketUpdateScope scope = ACTIVE_UPDATE.get();
        int additional = getTotalRawAdditionalDemand(market);

        if (scope != null && scope.hasSnapshot(market, this)) {
            return batchDemandExceptPending + additional;
        }

        int total = additional;

        for (Industry industry : market.getIndustries()) {
            if (!AoTDIndustryData.getInstance(market).isPending(industry.getId())) {
                total += getRawDemandFromIndustry(industry);
            }
        }

        return total;
    }

    public int getRawDemandFromIndustry(Industry industry) {
        return AoTDIndustryAmounts.getDemand(industry,commodityID,getEconSpec().getCalculationScript(),null);
    }

    public int getRawSupplyFromIndustry(Industry industry) {
        if(industry.isDisrupted())return 0;
        return AoTDIndustryAmounts.getProduction(industry,commodityID,getEconSpec().getCalculationScript(),null);
    }

    public int getRawAdditionalDemand(MutableStat.StatMod mod, MarketAPI market) {
        if (mod == null || mod.getValue() <= 0f) return 0;
        if (additionalDemand.isExact(mod.getSource())) return Math.max(0, Math.round(mod.getValue()));

        MutableStat stat = new MutableStat(0f);
        stat.modifyFlat(mod.getSource(), mod.getValue(), mod.getDesc());

        return getEconSpec().getCalculationScript().getRawUnitsFromDemand(
                stat,
                market,
                commodityID,
                null
        );
    }

    public int getRawAdditionalDemand(String source, MarketAPI market) {
        return getRawAdditionalDemand(additionalDemand.getFlatStatMod(source), market);
    }

    public int getTotalRawAdditionalDemand(MarketAPI market) {
        int total = 0;

        for (MutableStat.StatMod mod : additionalDemand.getFlatMods().values()) {
            total += getRawAdditionalDemand(mod, market);
        }

        return total;
    }

    public int getRawAdditionalProduction(MutableStat.StatMod mod, MarketAPI market) {
        if (mod == null || mod.getValue() <= 0f) return 0;
        if (additionalProduction.isExact(mod.getSource())) return Math.max(0, Math.round(mod.getValue()));

        MutableStat stat = new MutableStat(0f);
        stat.modifyFlat(mod.getSource(), mod.getValue(), mod.getDesc());

        return getEconSpec().getCalculationScript().getRawUnitsFromSupply(
                stat,
                market,
                commodityID,
                null
        );
    }

    public int getRawAdditionalProduction(String source, MarketAPI market) {
        return getRawAdditionalProduction(additionalProduction.getFlatStatMod(source), market);
    }

    public int getTotalRawAdditionalProduction(MarketAPI market) {
        int total = 0;

        for (MutableStat.StatMod mod : additionalProduction.getFlatMods().values()) {
            total += getRawAdditionalProduction(mod, market);
        }

        return total;
    }

    private float fillAdditionalDemand(float available, MarketAPI market, boolean beforeIndustries) {
        float remaining = Math.max(0f, available);

        for (MutableStat.StatMod mod : additionalDemand.getMods(beforeIndustries)) {
            if (remaining <= 0f) break;

            int required = getRawAdditionalDemand(mod, market);
            if (required <= 0) continue;

            float filled = Math.min(required, remaining);

            additionalDemand.setFilledRawAmount(mod.getSource(), filled);
            remaining -= filled;
        }

        return remaining;
    }

    public LinkedHashMap<String, MutableStat> getDemandUnitsFromIndustries() {
        return demandUnitsFromIndustries;
    }

    public LinkedHashMap<String, MutableStat> getSupplyUnitsFromIndustries() {
        return supplyUnitsFromIndustries;
    }

    public int getRawUnitsFromIndustrySupply() {
        return supply;
    }

    public int getRawUnitsFromIndustryDemand() {
        return demand;
    }

    public int getTotalRawUnitsFromSupply() {
        return supply + getTotalRawAdditionalProduction(null);
    }

    public int getTotalRawUnitsFromDemand() {
        return demand + getTotalRawAdditionalDemand(null);
    }

    public int getRawNetExport() {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    public AoTDAdditionalProdData getAdditionalDemand() {
        return additionalDemand;
    }

    public AoTDAdditionalProdData getAdditionalProduction() {
        return additionalProduction;
    }

    public void advance(float days) {
        additionalDemand.advance(days);
        additionalProduction.advance(days);
    }

    public int getAvailableOnThisMarket(float cargo, MarketAPI market, String commodityId) {
        int available = 0;
        float remainingCargo = Math.max(0f,cargo);
        clearIndustryAllocation();
        additionalDemand.resetFilledAmounts();
        remainingCargo = fillAdditionalDemand(remainingCargo,market,true);

        for (Industry industry : AoTDIndustryData.getInstance(market).getActiveIndustriesInOrder(market)) {
            if(remainingCargo < 1f)break;
            int raw = AoTDIndustryAmounts.getDemand(industry,commodityId,getEconSpec().getCalculationScript(),market);
            if(raw <= 0)continue;
            float filled = Math.min(raw,remainingCargo);
            filledRawDemandFromIndustries.put(industry.getId(),filled);
            remainingCargo -= filled;
            if(!AoTDIndustryAmounts.hasExactDemand(industry,commodityId)){
                int vanillaDemand = Math.max(0,industry.getDemand(commodityId).getQuantity().getModifiedInt());
                available += filled >= raw ? vanillaDemand : Math.round(filled / raw * vanillaDemand);
            }
        }

        fillAdditionalDemand(remainingCargo,market,false);
        return available;
    }
}
