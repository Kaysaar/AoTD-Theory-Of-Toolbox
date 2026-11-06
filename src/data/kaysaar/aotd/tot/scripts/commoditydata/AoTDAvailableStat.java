package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;

import java.util.LinkedHashMap;

public class AoTDAvailableStat extends MutableStatWithTempMods {

    private static final float DATA_ADVANCE_INTERVAL_DAYS = 0.05f;
    private static final String ADDITIONAL_MODIFIERS_KEY = "$aotd_additional_commodity_modifiers";

    AoTDExcDefData data = new AoTDExcDefData();
    private AoTDSupplyDemandData supplyDemandData;

    private transient float pendingDataDays;
    private transient float nextDataFlushDays;

    public static class SavedAdditionalModifiers {
        public AoTDAdditionalProdData production;
        public AoTDAdditionalProdData demand;

        public SavedAdditionalModifiers() {
        }

        public SavedAdditionalModifiers(AoTDSupplyDemandData data) {
            production = data.additionalProduction;
            demand = data.additionalDemand;
        }
    }

    public AoTDAvailableStat(float base) {
        super(base);
    }

    @SuppressWarnings("unchecked")
    private void linkSavedAdditionalModifiers(CommodityOnMarketAPI commodity) {
        if (commodity == null || commodity.getMarket() == null || supplyDemandData == null) return;

        MarketAPI market = commodity.getMarket();
        Object saved = market.getMemoryWithoutUpdate().get(ADDITIONAL_MODIFIERS_KEY);
        LinkedHashMap<String, SavedAdditionalModifiers> modifiers;

        if (saved instanceof LinkedHashMap) {
            modifiers = (LinkedHashMap<String, SavedAdditionalModifiers>) saved;
        } else {
            modifiers = new LinkedHashMap<>();
            market.getMemoryWithoutUpdate().set(ADDITIONAL_MODIFIERS_KEY, modifiers);
        }

        SavedAdditionalModifiers entry = modifiers.get(commodity.getId());
        if (entry == null) {
            entry = new SavedAdditionalModifiers(supplyDemandData);
            modifiers.put(commodity.getId(), entry);
            return;
        }

        if (entry.production == null) {
            entry.production = supplyDemandData.additionalProduction;
        } else if (supplyDemandData.additionalProduction != entry.production) {
            supplyDemandData.additionalProduction = entry.production;
        }

        if (entry.demand == null) {
            entry.demand = supplyDemandData.additionalDemand;
        } else if (supplyDemandData.additionalDemand != entry.demand) {
            supplyDemandData.additionalDemand = entry.demand;
        }
    }

    public AoTDSupplyDemandData getSupplyDemandData(CommodityOnMarketAPI commodity) {
        return getSupplyDemandData(commodity, true);
    }

    public AoTDSupplyDemandData getSupplyDemandData(
            CommodityOnMarketAPI commodity,
            boolean initializeValues
    ) {
        boolean created = false;

        if (supplyDemandData == null) {
            supplyDemandData = new AoTDSupplyDemandData(commodity.getId());
            created = true;
        }

        // The market's saved memory owns these modifiers. Commodity objects can be
        // reconstructed, but this reference remains persistent for that market.
        linkSavedAdditionalModifiers(commodity);

        if (created) {
            supplyDemandData.getEconSpec();

            if (initializeValues) {
                supplyDemandData.updateSupplyDemandData(commodity.getMarket());
            }
        }

        return supplyDemandData;
    }

    public AoTDSupplyDemandData getSupplyDemandDataIfInitialized() {
        return supplyDemandData;
    }

    public AoTDExcDefData getData() {
        return data;
    }

    @Override
    public void advance(float days) {
        super.advance(days);

        if (nextDataFlushDays <= 0f) {
            nextDataFlushDays = DATA_ADVANCE_INTERVAL_DAYS * (0.25f + 0.75f * (float) Math.random());
        }
        pendingDataDays += days;
        if (pendingDataDays >= nextDataFlushDays) {
            data.advance(pendingDataDays);
            pendingDataDays = 0f;
            nextDataFlushDays = DATA_ADVANCE_INTERVAL_DAYS;
        }

        if (supplyDemandData != null) {
            supplyDemandData.advance(days);
        }
    }
}
