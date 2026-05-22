package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.CommodityIconCounts;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.PriceCalculator;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask2;
import com.fs.starfarer.campaign.econ.reach.ReachEconomy;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityMarketData;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDMarketDemandData;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class AoTdMainWorkTask2 extends MainWorkTask2 {

    public static boolean ENABLE_MULTITHREADED_VERSION =
            Global.getSettings().getBoolean("aotd_allow_multithreaded_economy_calculaton");

    private List<MarketAPI> aotdMarkets;
    private final MainWorkTask.EconWorkParams aotdParams;

    private ArrayList<MarketAPI> marketsForCurrentMode = new ArrayList<>();
    private ArrayList<String> cachedEconGroups = new ArrayList<>();


    private Set<String> processedMarketDemandClasses = ConcurrentHashMap.newKeySet();

    private List<String> aotdCommodities;
    private int aotdIndex = 0;
    private boolean aotdStarted = false;
    private int aotdMarketIndex = 0;

    public MarketAPI singleMarketToUpdate;

    private boolean runOnce = false;


    private boolean mtMarketPrepDone = false;
    private boolean mtDataCreated = false;
    private boolean mtWorkersSubmitted = false;
    private boolean mtWorkersFinished = false;
    private boolean mtListenersNotified = false;
    private ArrayList<Future<?>> mtFutures = new ArrayList<>();

    private static final String CORE_MOD_ID = "core";
    private static final String AOTD_PRICE_MOD_ID = "aotd_price_state";

    private static final float AOTD_REFERENCE_TRADE_QUANTITY = 500f;

    private static final float[] AOTD_SPREAD_CHECK_QUANTITIES = new float[] {
            500f
    };

    private static final float AOTD_NORMAL_CENTER_MIN = 0.90f;
    private static final float AOTD_NORMAL_CENTER_MAX = 1.12f;

    private static final float AOTD_EXCESS_CENTER_MIN = 0.65f;
    private static final float AOTD_EXCESS_CENTER_MAX = 0.85f;

    private static final float AOTD_DEFICIT_CENTER_MIN = 1.25f;
    private static final float AOTD_DEFICIT_CENTER_MAX = 1.60f;

    private static final float AOTD_MIN_LOCAL_SPREAD = 0.06f;
    private static final float AOTD_GREED_FRACTION = 0.06f;

    private static final float AOTD_MIN_STATE_AMOUNT = 1f;

    private static final float AOTD_MIN_CORRECTION_MULT = 0.05f;
    private static final float AOTD_MAX_CORRECTION_MULT = 20f;

    public AoTdMainWorkTask2(List<MarketAPI> markets, ReachEconomy reachEconomy, MainWorkTask.EconWorkParams econWorkParams) {
        super(markets, reachEconomy, econWorkParams);

        this.aotdMarkets = new ArrayList<>(markets);
        this.aotdParams = econWorkParams;
    }

    public AoTdMainWorkTask2(
            List<MarketAPI> markets,
            ReachEconomy reachEconomy,
            MainWorkTask.EconWorkParams econWorkParams,
            MarketAPI singleMarket
    ) {
        super(markets, reachEconomy, econWorkParams);

        this.singleMarketToUpdate = singleMarket;
        this.aotdMarkets = new ArrayList<>(markets);
        this.aotdParams = econWorkParams;
    }

    @Override
    public void initCommodityList() {
        this.aotdCommodities = new ArrayList<>();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            if (spec != null && !spec.hasTag("nonecon")) {
                this.aotdCommodities.add(spec.getId());
            }
        }

        this.aotdCommodities.sort(Comparator.naturalOrder());
    }

    /*
     * Save compatibility:
     *
     * Old saves can contain an already-serialized instance of this task.
     * XStream may restore fields without running constructors or field initializers,
     * especially for fields added after the save was created.
     *
     * Therefore all mutable runtime containers must be non-final and repaired here.
     * Do not make ArrayList/Set/Future containers final in this task.
     */
    private void ensureRuntimeCollections() {
        if (aotdMarkets == null) {
            aotdMarkets = Global.getSector().getEconomy().getMarketsCopy();
        }

        if (marketsForCurrentMode == null) {
            marketsForCurrentMode = new ArrayList<>();
        }

        if (cachedEconGroups == null) {
            cachedEconGroups = new ArrayList<>();
        }

        if (processedMarketDemandClasses == null) {
            processedMarketDemandClasses = ConcurrentHashMap.newKeySet();
        }

        if (mtFutures == null) {
            mtFutures = new ArrayList<>();
        }

        if (aotdCommodities == null) {
            initCommodityList();
        }
    }

    @Override
    public void doNextBatch() {
        ensureRuntimeCollections();

        if (ENABLE_MULTITHREADED_VERSION) {
            doMultithreadedNextBatch();
            return;
        }

        doSequentialNextBatch();
    }


    private void doSequentialNextBatch() {
        if (!aotdStarted) {
            startTaskState();

            if (singleMarketToUpdate != null) {
                runSequentialSingleMarketNow();
            }

            return;
        }

        if (isDone()) {
            return;
        }

        if (aotdMarketIndex < marketsForCurrentMode.size()) {
            processMarketReapplyStage(marketsForCurrentMode.get(aotdMarketIndex));
            aotdMarketIndex++;
            return;
        }

        String commodityId = aotdCommodities.get(aotdIndex);
        CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(commodityId);
        aotdIndex++;

        createCommodityMarketData(commodityId);

        if (aotdParams != null && aotdParams.withStockpileUpdate && commoditySpec != null) {
            for (MarketAPI market : marketsForCurrentMode) {
                if (market instanceof Market) {
                    updateStockpileAndPriceOnce((Market) market, commoditySpec);
                }
            }
        }

        notifyCommodityUpdated(commodityId);
    }

    private void runSequentialSingleMarketNow() {
        MarketAPI market = singleMarketToUpdate;

        processMarketReapplyStage(market);

        for (String commodityId : aotdCommodities) {
            CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(commodityId);

            createCommodityMarketData(commodityId);

            if (aotdParams != null && aotdParams.withStockpileUpdate && commoditySpec != null && market instanceof Market) {
                updateStockpileAndPriceOnce((Market) market, commoditySpec);
            }

            notifyCommodityUpdated(commodityId);
        }

        runOnce = true;
    }

    private void doMultithreadedNextBatch() {
        if (!aotdStarted) {
            startTaskState();
            return;
        }

        if (isDone()) {
            return;
        }

        /*
         * 1) Main-thread market preparation.
         */
        if (!mtMarketPrepDone) {
            if (aotdMarketIndex < marketsForCurrentMode.size()) {
                processMarketReapplyStage(marketsForCurrentMode.get(aotdMarketIndex));
                aotdMarketIndex++;
                return;
            }

            mtMarketPrepDone = true;
            return;
        }

        /*
         * 2) Main-thread commodity market data creation.
         */
        if (!mtDataCreated) {
            for (String commodityId : aotdCommodities) {
                createCommodityMarketData(commodityId);
            }

            mtDataCreated = true;
            return;
        }

        /*
         * 3) Submit workers, block until they finish, then notify listeners.
         */
        if (!mtWorkersSubmitted) {
            submitMarketPriceWorkers();
            mtWorkersSubmitted = true;

            waitForMarketPriceWorkers();
            mtWorkersFinished = true;

            notifyCommoditiesUpdated(aotdCommodities);

            mtListenersNotified = true;
            runOnce = singleMarketToUpdate != null;
            aotdIndex = aotdCommodities.size();
        }
    }

    private void startTaskState() {
        ensureRuntimeCollections();
        initCommodityList();

        if (aotdMarkets == null) {
            aotdMarkets = Global.getSector().getEconomy().getMarketsCopy();
        }

        marketsForCurrentMode.clear();
        if (singleMarketToUpdate != null) {
            marketsForCurrentMode.add(singleMarketToUpdate);
        } else {
            marketsForCurrentMode.addAll(aotdMarkets);
        }

        rebuildCachedEconGroups();

        processedMarketDemandClasses.clear();

        aotdIndex = 0;
        aotdMarketIndex = 0;

        runOnce = false;

        mtMarketPrepDone = false;
        mtDataCreated = false;
        mtWorkersSubmitted = false;
        mtWorkersFinished = false;
        mtListenersNotified = false;
        mtFutures.clear();

        aotdStarted = true;
    }

    private void rebuildCachedEconGroups() {
        ensureRuntimeCollections();

        LinkedHashSet<String> groups = new LinkedHashSet<>();

        for (MarketAPI market : marketsForCurrentMode) {
            if (market == null) continue;

            String econGroup = market.getEconGroup();
            if (econGroup != null) {
                groups.add(econGroup);
            }
        }

        cachedEconGroups.clear();
        cachedEconGroups.addAll(groups);
    }

    private static void processMarketReapplyStage(MarketAPI market) {
        if (market == null) return;

        market.reapplyConditions();

        AoTDIndustryData data = AoTDIndustryData.getInstance(market);
        for (Industry industry : market.getIndustries()) {
            if (!data.isPending(industry.getId())) {
                industry.reapply();
            }
        }
    }

    private void createCommodityMarketData(String commodityId) {
        new AoTDCommodityMarketData(commodityId, null);

        for (String econGroup : cachedEconGroups) {
            new AoTDCommodityMarketData(commodityId, econGroup);
        }
    }

    private void submitMarketPriceWorkers() {
        ensureRuntimeCollections();

        mtFutures.clear();

        if (aotdParams == null || !aotdParams.withStockpileUpdate) {
            return;
        }

        for (MarketAPI market : marketsForCurrentMode) {
            if (!(market instanceof Market)) {
                continue;
            }

            Market vanillaMarket = (Market) market;

            Future<?> future = AoTDWorkerManager.submit(
                    "AoTD price recalculation: " + market.getId(),
                    () -> runMarketPriceWorker(vanillaMarket)
            );

            mtFutures.add(future);
        }
    }

    private void runMarketPriceWorker(Market market) {

        for (String commodityId : aotdCommodities) {
            AoTDWorkerManager.checkpoint();

            CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(commodityId);
            if (commoditySpec == null || commoditySpec.hasTag("nonecon")) {
                continue;
            }

            try {
                updateStockpileAndPriceOnce(market, commoditySpec);
            } catch (Throwable ex) {
                Global.getLogger(AoTdMainWorkTask2.class).warn(
                        "AoTD price worker failed for commodity " + commodityId +
                                " on market " + market.getId() + ". Skipping.",
                        ex
                );
            }
        }

        AoTDWorkerManager.checkpoint();
    }

    private void waitForMarketPriceWorkers() {
        for (Future<?> future : mtFutures) {
            if (future == null) continue;

            try {
                future.get();
            } catch (Exception ex) {
                Global.getLogger(AoTdMainWorkTask2.class).warn(
                        "AoTD price worker failed.",
                        ex
                );
            }
        }
    }

    private void updateStockpileAndPriceOnce(Market market, CommoditySpecAPI commoditySpec) {
        ensureRuntimeCollections();

        if (market == null || commoditySpec == null) {
            return;
        }

        String demandClass = commoditySpec.getDemandClass();
        if (demandClass == null) {
            return;
        }

        String key = market.getId() + '\u0000' + demandClass;
        if (!processedMarketDemandClasses.add(key)) {
            return;
        }

        aotdUpdateStockpileAndPrice(market, commoditySpec);
    }

    private static void notifyCommodityUpdated(String commodityId) {
        List<EconomyAPI.EconomyUpdateListener> listeners =
                new ArrayList<>(Global.getSector().getEconomy().getUpdateListeners());

        for (EconomyAPI.EconomyUpdateListener listener : listeners) {
            if (listener == null) continue;

            if (listener.isEconomyListenerExpired()) {
                Global.getSector().getEconomy().removeUpdateListener(listener);
            } else {
                listener.commodityUpdated(commodityId);
            }
        }
    }

    private static void notifyCommoditiesUpdated(Collection<String> commodityIds) {
        List<EconomyAPI.EconomyUpdateListener> listeners =
                new ArrayList<>(Global.getSector().getEconomy().getUpdateListeners());

        for (String commodityId : commodityIds) {
            for (EconomyAPI.EconomyUpdateListener listener : listeners) {
                if (listener == null) continue;

                if (listener.isEconomyListenerExpired()) {
                    Global.getSector().getEconomy().removeUpdateListener(listener);
                } else {
                    listener.commodityUpdated(commodityId);
                }
            }
        }
    }

    @Override
    public boolean isDone() {
        if (ENABLE_MULTITHREADED_VERSION) {
            if (singleMarketToUpdate != null) {
                return runOnce;
            }

            return aotdCommodities != null
                    && mtMarketPrepDone
                    && mtDataCreated
                    && mtWorkersSubmitted
                    && mtWorkersFinished
                    && mtListenersNotified;
        }

        if (singleMarketToUpdate != null) {
            return runOnce;
        }

        return aotdCommodities != null && aotdIndex >= aotdCommodities.size();
    }

    @Override
    public String getLoggingIdentifier() {
        if (singleMarketToUpdate != null) {
            return "AoTdMainWorkTask2:" + singleMarketToUpdate.getId();
        }

        return "AoTdMainWorkTask2";
    }

    public static List<CommodityOnMarket> getCommoditiesWithSameDemandClass(String demandClass, Market market) {
        return new ArrayList<>(market.getCommoditiesWithClass(demandClass));
    }

    public static void aotdUpdateStockpileAndPrice(Market market, CommoditySpecAPI commoditySpec) {
        /*
         * Required:
         *
         * Market.getDemandPrice()/getSupplyPrice() read Market.demandData.
         * If this is vanilla MarketDemandData, AoTD stockpile utility is ignored.
         */
        if (!(market.getDemandData() instanceof AoTDMarketDemandData)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("demandData", market, new AoTDMarketDemandData(market));
        }

        List<CommodityOnMarket> sameClassCommodities =
                getCommoditiesWithSameDemandClass(commoditySpec.getDemandClass(), market);

        if (sameClassCommodities.isEmpty()) {
            return;
        }

        boolean hasAoTDCommodity = false;
        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (commodity instanceof AoTDCommodityOnMarket) {
                hasAoTDCommodity = true;
                break;
            }
        }

        if (hasAoTDCommodity) {
            updateAoTDStocks(market, sameClassCommodities);
            applyAoTDNeutralCurveAndCalibratedPriceMods(market, sameClassCommodities);
            return;
        }

        /*
         * Vanilla fallback for non-AoTD commodities/classes.
         */
        Random random = new Random(
                (long) market.getId().hashCode()
                        + commoditySpec.getId().hashCode()
                        + Global.getSector().getClock().getMonth() * 170000L
        );

        float stockpileBonus = 0.0f;
        float stockpileMult = 1.0f;
        float demandForDemandClass = 0.0f;
        float greedFraction = Economy.ECONOMY_GREED_FRACTION;
        boolean noDemandOrSupply = false;

        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (Global.getSettings().getCommoditySpec(commodity.getId()).isPrimary()) {
                continue;
            }

            CommodityIconCounts counts = new CommodityIconCounts(commodity);
            float usableProduction = counts.production - counts.inFactionOnlyExport - counts.canNotExport;
            float targetDemand = Math.max(usableProduction, commodity.getMaxDemand()) + stockpileBonus;

            if (targetDemand < 1.0f) {
                targetDemand = 1.0f;
            }

            noDemandOrSupply = commodity.getMaxDemand() <= 0 && commodity.getMaxSupply() <= 0;

            demandForDemandClass =
                    MainWorkTask2.getStockpileQuantity(commodity, targetDemand) * stockpileMult
                            + Economy.MIN_STOCKPILE_FOR_PRICING * 2.0f;

            demandForDemandClass *= 0.95f + 0.1f * random.nextFloat();

            if (noDemandOrSupply) {
                commodity.getPlayerDemandPriceMod().modifyMult(
                        CORE_MOD_ID,
                        Economy.ECONOMY_NO_DEMAND_PRICE_MULT
                );
            } else {
                commodity.getPlayerDemandPriceMod().unmodifyMult(CORE_MOD_ID);
            }

            commodity.getDemand().getDemand().modifyFlat(
                    CORE_MOD_ID,
                    demandForDemandClass * (1.0f - greedFraction)
            );

            commodity.getGreed().modifyFlat(
                    CORE_MOD_ID,
                    demandForDemandClass * greedFraction
            );

            break;
        }

        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (Global.getSettings().getCommoditySpec(commodity.getId()).isPrimary()) {
                continue;
            }

            if (noDemandOrSupply) {
                commodity.getPlayerDemandPriceMod().modifyMult(
                        CORE_MOD_ID,
                        Economy.ECONOMY_NO_DEMAND_PRICE_MULT
                );
            } else {
                commodity.getPlayerDemandPriceMod().unmodifyMult(CORE_MOD_ID);
            }

            commodity.getGreed().modifyFlat(CORE_MOD_ID, demandForDemandClass * greedFraction);
        }

        for (CommodityOnMarket commodity : sameClassCommodities) {
            applyVanillaPriceBands(commodity, commoditySpec);
        }
    }

    private static void updateAoTDStocks(Market market, List<CommodityOnMarket> sameClassCommodities) {
        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (!(commodity instanceof AoTDCommodityOnMarket aotdCommodity)) {
                continue;
            }

            AoTDSupplyDemandData data = aotdCommodity.getSupplyDemandData();
            data.updateSupplyDemandData(market);

            float rawDemand = Math.max(0f, data.getTotalRawUnitsFromDemand());
            float rawSupply = Math.max(0f, data.getTotalRawUnitsFromSupply());

            aotdCommodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(aotdCommodity);

            float floor = Math.max(1f, PriceCalculator.MIN_STOCKPILE_FOR_PRICING * 2f);

            float rawStocks = Math.max(floor, Math.max(rawDemand, rawSupply));

            aotdCommodity.setStocks(Math.round(rawStocks));
            aotdCommodity.setStockpile(rawStocks);
        }
    }

    private static void applyAoTDNeutralCurveAndCalibratedPriceMods(
            MarketAPI market,
            List<CommodityOnMarket> sameClassCommodities
    ) {
        float classStockpileUtility = getAoTDClassStockpileUtility(sameClassCommodities);

        float neutralDemandCurve =
                classStockpileUtility
                        + PriceCalculator.MIN_STOCKPILE_FOR_PRICING
                        - PriceCalculator.MIN_DEMAND_FOR_PRICING;

        neutralDemandCurve = Math.max(1f, neutralDemandCurve);

        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (!(commodity instanceof AoTDCommodityOnMarket aotdCommodity)) {
                continue;
            }

            boolean noDemandOrSupply = commodity.getMaxDemand() <= 0 && commodity.getMaxSupply() <= 0;

            if (noDemandOrSupply) {
                commodity.getPlayerDemandPriceMod().modifyMult(
                        CORE_MOD_ID,
                        Economy.ECONOMY_NO_DEMAND_PRICE_MULT
                );
            } else {
                commodity.getPlayerDemandPriceMod().unmodifyMult(CORE_MOD_ID);
            }

            setModifiedValueWithFlatMod(commodity.getDemand().getDemand(), CORE_MOD_ID, neutralDemandCurve);
            setModifiedValueWithFlatMod(commodity.getGreed(), CORE_MOD_ID, neutralDemandCurve * AOTD_GREED_FRACTION);

            aotdCommodity.updateCalc();

            aotdResetPriceBands(aotdCommodity.getDemandPrice());
            aotdResetPriceBands(aotdCommodity.getSupplyPrice());

            applyCalibratedAoTDPlayerPriceMods(market, aotdCommodity);
        }
    }

    private static void applyCalibratedAoTDPlayerPriceMods(MarketAPI market, AoTDCommodityOnMarket commodity) {
        AoTDPriceTargets targets = getAoTDPriceTargets(market, commodity);

        commodity.getPlayerDemandPriceMod().unmodifyMult(AOTD_PRICE_MOD_ID);
        commodity.getPlayerSupplyPriceMod().unmodifyMult(AOTD_PRICE_MOD_ID);

        float quantity = AOTD_REFERENCE_TRADE_QUANTITY;
        float basePrice = commodity.getCommoditySpec().getBasePrice();

        float currentSellTotal = market.getDemandPrice(commodity.getId(), quantity, true);
        float currentBuyTotal = market.getSupplyPrice(commodity.getId(), quantity, true);

        float targetSellTotal = basePrice * targets.sellMult * quantity;
        float targetBuyTotal = basePrice * targets.buyMult * quantity;

        float sellCorrection = getCorrectionMult(currentSellTotal, targetSellTotal);
        float buyCorrection = getCorrectionMult(currentBuyTotal, targetBuyTotal);

        float minSpreadPerUnit = basePrice * AOTD_MIN_LOCAL_SPREAD;

        for (float checkQuantity : AOTD_SPREAD_CHECK_QUANTITIES) {

            float sellWithoutAoTD;
            float buyWithoutAoTD;

            sellWithoutAoTD = currentSellTotal;
            buyWithoutAoTD = currentBuyTotal;

            if (sellWithoutAoTD <= 0f || buyWithoutAoTD <= 0f) continue;

            float predictedSell = sellWithoutAoTD * sellCorrection;
            float predictedBuy = buyWithoutAoTD * buyCorrection;

            float requiredBuy = predictedSell + minSpreadPerUnit * checkQuantity;

            if (predictedBuy < requiredBuy) {
                buyCorrection = requiredBuy / buyWithoutAoTD;
                buyCorrection = aotdClamp(buyCorrection, AOTD_MIN_CORRECTION_MULT, AOTD_MAX_CORRECTION_MULT);

                predictedBuy = buyWithoutAoTD * buyCorrection;

                if (predictedBuy < requiredBuy) {
                    float allowedSell = predictedBuy - minSpreadPerUnit * checkQuantity;
                    if (allowedSell > 0f) {
                        sellCorrection = allowedSell / sellWithoutAoTD;
                        sellCorrection = aotdClamp(sellCorrection, AOTD_MIN_CORRECTION_MULT, AOTD_MAX_CORRECTION_MULT);
                    }
                }
            }
        }

        commodity.getPlayerDemandPriceMod().modifyMult(AOTD_PRICE_MOD_ID, sellCorrection);
        commodity.getPlayerSupplyPriceMod().modifyMult(AOTD_PRICE_MOD_ID, buyCorrection);
    }

    private static float getCorrectionMult(float currentTotal, float targetTotal) {
        if (currentTotal <= 0f || targetTotal <= 0f) {
            return 1f;
        }

        return aotdClamp(targetTotal / currentTotal, AOTD_MIN_CORRECTION_MULT, AOTD_MAX_CORRECTION_MULT);
    }

    private static AoTDPriceTargets getAoTDPriceTargets(MarketAPI market, AoTDCommodityOnMarket commodity) {
        AoTDSupplyDemandData data = commodity.getSupplyDemandData();

        float rawDemand = Math.max(0f, data.getTotalRawUnitsFromDemand());
        float rawSupply = Math.max(0f, data.getTotalRawUnitsFromSupply());

        float excess = Math.max(commodity.getExc(), commodity.getExcessQuantity());
        float deficit = Math.max(commodity.getDef(), commodity.getDeficitQuantity());

        excess = Math.max(0f, excess);
        deficit = Math.max(0f, deficit);

        boolean hasExcess = excess >= AOTD_MIN_STATE_AMOUNT && excess >= deficit;
        boolean hasDeficit = deficit >= AOTD_MIN_STATE_AMOUNT && deficit > excess;

        if (hasDeficit) {
            float basis = Math.max(1f, rawDemand);
            float pressure = aotdClamp(deficit / basis, 0f, 1f);

            float center = aotdLerp(AOTD_DEFICIT_CENTER_MIN, AOTD_DEFICIT_CENTER_MAX, pressure);
            return targetsFromCenter(center);
        }

        if (hasExcess) {
            float basis = Math.max(1f, Math.max(rawSupply, rawDemand));
            float pressure = aotdClamp(excess / basis, 0f, 1f);

            float center = aotdLerp(AOTD_EXCESS_CENTER_MAX, AOTD_EXCESS_CENTER_MIN, pressure);
            return targetsFromCenter(center);
        }

        float roll = aotdStablePriceRoll(market, commodity.getId());

        float center = aotdLerp(AOTD_NORMAL_CENTER_MIN, AOTD_NORMAL_CENTER_MAX, roll);
        return targetsFromCenter(center);
    }

    private static AoTDPriceTargets targetsFromCenter(float center) {
        float halfSpread = AOTD_MIN_LOCAL_SPREAD * 0.5f;
        return new AoTDPriceTargets(center - halfSpread, center + halfSpread);
    }

    private static float getAoTDClassStockpileUtility(List<CommodityOnMarket> sameClassCommodities) {
        float total = 0f;

        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                total += Math.max(0f, aotdCommodity.getStocks()) * Math.max(0.0001f, aotdCommodity.getUtilityOnMarket());
            } else {
                total += Math.max(0f, commodity.getStockpile());
            }
        }

        return Math.max(0f, total);
    }

    private static void setModifiedValueWithFlatMod(MutableStat stat, String id, float targetValue) {
        stat.unmodifyFlat(id);
        float existingWithoutThisMod = stat.getModifiedValue();
        stat.modifyFlat(id, targetValue - existingWithoutThisMod);
    }

    private static void applyVanillaPriceBands(CommodityOnMarket commodity, CommoditySpecAPI commoditySpec) {
        commodity.updateCalc();

        CommodityIconCounts counts = new CommodityIconCounts(commodity);
        PriceCalculator demandPrice = commodity.getDemandPrice();
        PriceCalculator supplyPrice = commodity.getSupplyPrice();

        float deficit = counts.deficit;
        float excess = counts.extra;
        float stockpile = commodity.getStockpile();
        float econUnit = commoditySpec.getEconUnit();

        float deficitPriceIncrementPerUnit = Economy.DEFICIT_PRICE_INCR_PER_UNIT;
        float excessPriceDecrementPerUnit = Economy.EXCESS_PRICE_DECR_PER_UNIT;
        float deficitPriceMax = Economy.DEFICIT_PRICE_MULT_MAX;
        float excessPriceMin = Economy.EXCESS_PRICE_MULT_MIN;

        if (deficit > 0.0f) {
            float threshold = stockpile + deficit * econUnit;
            float mult = 1.0f + Math.max(1.0f, deficit) * deficitPriceIncrementPerUnit;

            if (mult > deficitPriceMax) {
                mult = deficitPriceMax;
            }

            demandPrice.setHighPriceThreshold(threshold);
            demandPrice.setHighPriceMult(mult);

            supplyPrice.setHighPriceThreshold(threshold);
            supplyPrice.setHighPriceMult(mult);
        } else {
            demandPrice.setHighPriceThreshold(-1.0f);
            demandPrice.setHighPriceMult(1.0f);

            supplyPrice.setHighPriceThreshold(-1.0f);
            supplyPrice.setHighPriceMult(1.0f);
        }

        float combinedTradeQuantity = commodity.getCombinedTradeModQuantity();
        float tradeValue = commodity.getModValueForQuantity(combinedTradeQuantity);

        if (deficit <= 0.0f && tradeValue > 0.0f) {
            float incomingTrade =
                    commodity.getTradeMod().getModifiedValue()
                            + commodity.getTradeModPlus().getModifiedValue();

            if (incomingTrade > 0.0f) {
                float threshold = Math.max(0.0f, stockpile - excess * econUnit);
                float mult = 1.0f + Math.max(1.0f, 1.0f) * deficitPriceIncrementPerUnit;

                if (mult > deficitPriceMax) {
                    mult = deficitPriceMax;
                }

                supplyPrice.setHighPriceThreshold(threshold);
                supplyPrice.setHighPriceMult(mult);
            }
        }

        if (excess > 0.0f) {
            float threshold = stockpile - excess * econUnit;
            if (threshold < 0.0f) {
                threshold = 0.0f;
            }

            float mult = 1.0f - Math.max(1.0f, excess) * excessPriceDecrementPerUnit;
            if (mult < excessPriceMin) {
                mult = excessPriceMin;
            }

            demandPrice.setLowPriceThreshold(threshold);
            demandPrice.setLowPriceMult(mult);

            supplyPrice.setLowPriceThreshold(threshold);
            supplyPrice.setLowPriceMult(mult);
        } else {
            demandPrice.setLowPriceThreshold(-1.0f);
            demandPrice.setLowPriceMult(1.0f);

            supplyPrice.setLowPriceThreshold(-1.0f);
            supplyPrice.setLowPriceMult(1.0f);
        }
    }

    private static void aotdResetPriceBands(PriceCalculator calculator) {
        calculator.setHighPriceThreshold(-1f);
        calculator.setHighPriceMult(1f);

        calculator.setLowPriceThreshold(-1f);
        calculator.setLowPriceMult(1f);
    }

    private static float aotdStablePriceRoll(MarketAPI market, String commodityId) {
        int seed = 31 * market.getId().hashCode() + commodityId.hashCode();
        return new Random(seed).nextFloat();
    }

    private static float aotdLerp(float from, float to, float t) {
        return from + (to - from) * aotdClamp(t, 0f, 1f);
    }

    private static float aotdClamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class AoTDPriceTargets {
        final float sellMult;
        final float buyMult;

        AoTDPriceTargets(float sellMult, float buyMult) {
            this.sellMult = sellMult;
            this.buyMult = buyMult;
        }
    }
}
