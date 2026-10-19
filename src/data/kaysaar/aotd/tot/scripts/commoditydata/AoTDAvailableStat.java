package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;

public class AoTDAvailableStat extends MutableStatWithTempMods {

    // Excess/deficit temp mods last 30-60 days, so they don't need per-frame updates.
    // About 0.5 s at 1x speed.
    private static final float DATA_ADVANCE_INTERVAL_DAYS = 0.05f;

    AoTDExcDefData data = new AoTDExcDefData();
    private AoTDSupplyDemandData supplyDemandData;

    // Transient: not saved, so saves are unaffected.
    private transient float pendingDataDays;
    private transient float nextDataFlushDays;

    public AoTDAvailableStat(float base) {
        super(base);
    }

    public AoTDSupplyDemandData getSupplyDemandData(CommodityOnMarketAPI commodity) {
        return getSupplyDemandData(commodity, true);
    }

    public AoTDSupplyDemandData getSupplyDemandData(
            CommodityOnMarketAPI commodity,
            boolean initializeValues
    ) {
        if (supplyDemandData == null) {
            supplyDemandData = new AoTDSupplyDemandData(commodity.getId());
            supplyDemandData.getEconSpec();

            if (initializeValues) {
                supplyDemandData.updateSupplyDemandData(
                        commodity.getMarket()
                );
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

        // Called every frame for every commodity on every market, so bank the time and
        // advance the excess/deficit data in batches. The first flush point is randomized
        // so the instances don't all flush on the same frame.
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