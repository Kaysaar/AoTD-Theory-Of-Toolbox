package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.CommodityIconCounts;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
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
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDPriceCalculator;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

    private static final float AOTD_NORMAL_CENTER_MIN = 0.90f;
    private static final float AOTD_NORMAL_CENTER_MAX = 1.12f;

    private static final float AOTD_NORMAL_BUY_MIN = 0.90f;
    private static final float AOTD_NORMAL_BUY_MAX = 1.00f;
    private static final float AOTD_NORMAL_SELL_MIN = 1.00f;
    private static final float AOTD_NORMAL_SELL_MAX = 1.10f;
    private static final float AOTD_ILLEGAL_NORMAL_SELL_MAX = 1.20f;

    private static final float AOTD_EXCESS_CENTER_MIN = 0.65f;
    private static final float AOTD_EXCESS_CENTER_MAX = 0.85f;

    private static final float AOTD_DEFICIT_CENTER_MIN = 1.25f;
    private static final float AOTD_DEFICIT_CENTER_MAX = 1.60f;


    private static final float AOTD_ILLEGAL_DEFICIT_CENTER_MAX = 2.50f;

    private static final float AOTD_MIN_LOCAL_SPREAD = 0.06f;
    private static final float AOTD_GREED_FRACTION = 0.06f;

    private static final float AOTD_PRICE_CURVE_STATE_STRENGTH = 0.65f;


    private static final float AOTD_MAX_RESELL_RETURN_MULT = 0.94f;


    private static final float AOTD_CUSTOM_PRICE_RESPONSE = 0.18f;
    private static final float AOTD_CUSTOM_PRICE_STOCKPILE_DENOM_MULT = 0.75f;
    private static final float AOTD_CUSTOM_PRICE_DENOM_MAX_REFERENCE_MULT = 8.00f;

    private static final float AOTD_MIN_STATE_AMOUNT = 1f;


    private static final float AOTD_PRICING_STOCKPILE_RESERVE_FRACTION = 0.75f;
    private static final float AOTD_PRICING_STOCKPILE_RESERVE_MIN_MULT = 0.50f;
    private static final float AOTD_PRICING_STOCKPILE_SHARED_LIMIT_MULT = 1.50f;

    private static final float AOTD_MIN_CORRECTION_MULT = 0.001f;
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
        if(!hasAoTDCommodity){
            AoTDEconomy.pruneCommoditiesThatMightAppear(market);
            hasAoTDCommodity = true;
        }

        updateAoTDStocks(market, sameClassCommodities);
        applyAoTDNeutralCurveAndCalibratedPriceMods(market, sameClassCommodities);
        return;
    }

    private static void updateAoTDStocks(Market market, List<CommodityOnMarket> sameClassCommodities) {
        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (!(commodity instanceof AoTDCommodityOnMarket aotdCommodity)) {
                continue;
            }

            AoTDSupplyDemandData data = aotdCommodity.getSupplyDemandData();
            data.updateSupplyDemandData(market);

            float rawSupply = Math.max(0f, data.getTotalRawUnitsFromSupply());
            float stableSharedSubmarketLimit = getAoTDStableSharedSubmarketLimit(market, aotdCommodity, rawSupply);

            aotdCommodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(aotdCommodity);

            /*
             * Real stock remains raw AoTD supply.
             *
             * Pricing stockpile uses stable submarket limits, not live cargo.
             * Live cargo changes when the player buys/sells, which makes the
             * baseline jump and causes non-linear price cliffs.
             */
            float floor = Math.max(1f, PriceCalculator.MIN_STOCKPILE_FOR_PRICING);
            float realStocks = Math.max(floor, rawSupply);

            float pricingBasis = Math.max(realStocks, stableSharedSubmarketLimit);
            pricingBasis = Math.max(pricingBasis, AOTD_REFERENCE_TRADE_QUANTITY * 0.50f);

            float pricingStockpile = Math.max(
                    realStocks,
                    pricingBasis * AOTD_PRICING_STOCKPILE_SHARED_LIMIT_MULT
                            + getAoTDPricingStockpileReserve(pricingBasis)
            );

            aotdCommodity.setStocks(Math.round(realStocks));
            aotdCommodity.setStockpile(pricingStockpile);
        }
    }

    private static float getAoTDStableSharedSubmarketLimit(
            Market market,
            CommodityOnMarket commodity,
            float rawSupply
    ) {
        if (market == null || commodity == null) {
            return Math.max(0f, rawSupply);
        }

        float total = 0f;

        for (com.fs.starfarer.api.campaign.econ.SubmarketAPI submarket : market.getSubmarketsCopy()) {
            if (submarket == null) {
                continue;
            }

            /*
             * Only count economy-participating submarkets when possible.
             * This avoids player storage/abandoned storage inflating pricing stock.
             */
            if (submarket.getPlugin() != null && !submarket.getPlugin().isParticipatesInEconomy()) {
                continue;
            }

            /*
             * Use the plugin stockpile limit, not live cargo quantity.
             * The live cargo quantity changes from the player's transaction and
             * should not alter the baseline used for price response.
             */
            if (submarket.getPlugin() instanceof BaseSubmarketPlugin plugin) {
                total += Math.max(0f, plugin.getStockpileLimit(commodity));
            }
        }

        if (total <= 0f) {
            total = Math.max(0f, rawSupply);
        }

        return total;
    }

    private static float getAoTDPricingStockpileReserve(float pricingBasis) {
        float stock = Math.max(0f, pricingBasis);

        return Math.max(
                AOTD_REFERENCE_TRADE_QUANTITY * AOTD_PRICING_STOCKPILE_RESERVE_MIN_MULT,
                stock * AOTD_PRICING_STOCKPILE_RESERVE_FRACTION
        );
    }

    private static void applyAoTDNeutralCurveAndCalibratedPriceMods(
            MarketAPI market,
            List<CommodityOnMarket> sameClassCommodities
    ) {
        AoTDClassPriceState state = buildAoTDClassPriceState(sameClassCommodities);

        /*
         * PriceCalculator base relation:
         *
         * - stockpile utility comes from AoTDMarketDemand
         * - demand value comes from commodity.updateCalc()
         *
         * For normal markets with no official deficit/excess:
         *   demand curve == stockpile utility
         *   => price stays around base
         *
         * For deficit/excess:
         *   demand curve bends toward official AoTD state
         *   => repeated transactions naturally diminish through vanilla math
         */
        float curveTargetUtility =
                state.classStockpileUtility +
                        (state.classDemandUtility - state.classStockpileUtility) * AOTD_PRICE_CURVE_STATE_STRENGTH;

        float demandCurve =
                curveTargetUtility
                        + PriceCalculator.MIN_STOCKPILE_FOR_PRICING
                        - PriceCalculator.MIN_DEMAND_FOR_PRICING;

        demandCurve = Math.max(1f, demandCurve);

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

            setModifiedValueWithFlatMod(commodity.getDemand().getDemand(), CORE_MOD_ID, demandCurve);
            setModifiedValueWithFlatMod(commodity.getGreed(), CORE_MOD_ID, Math.max(1f, demandCurve * AOTD_GREED_FRACTION));

            ensureAoTDPriceCalculators(aotdCommodity);

            aotdCommodity.updateCalc();

            applyAoTDVanillaPriceBands(aotdCommodity, state);
            applyCalibratedAoTDPlayerPriceMods(market, aotdCommodity, state);
        }
    }

    private static void applyCalibratedAoTDPlayerPriceMods(
            MarketAPI market,
            AoTDCommodityOnMarket commodity,
            AoTDClassPriceState state
    ) {
        /*
         * Simple custom AoTDPriceCalculator path.
         *
         * Do not calibrate by current price anymore. That was the root cause of
         * several bugs: every transaction changed the raw price, then the correction
         * changed too and either cancelled movement or overcorrected it.
         *
         * The calculator itself now returns the final intended price:
         * - selling more lowers sell price smoothly
         * - buying more raises buy price smoothly
         * - same-market resell is capped after trade impact appears
         */
        ensureAoTDPriceCalculators(commodity);

        AoTDPriceTargets targets = getAoTDPriceTargets(market, commodity, state);

        float minSell;
        float maxSell;
        float minBuy;
        float maxBuy;

        if (state.hasDeficit) {
            minSell = getDeficitCenterMin(commodity);
            maxSell = getDeficitCenterMax(commodity);
            minBuy = minSell;
            maxBuy = maxSell;
        } else if (state.hasExcess) {
            minSell = AOTD_EXCESS_CENTER_MIN;
            maxSell = AOTD_EXCESS_CENTER_MAX;
            minBuy = AOTD_EXCESS_CENTER_MIN;
            maxBuy = AOTD_NORMAL_BUY_MAX;
        } else {
            /*
             * Normal no-trade visible band:
             * buy from market = 0.90 - 1.00
             * sell to market  = 1.00 - 1.10
             *
             * After player trade, the custom calculator is allowed to move outside
             * these bounds so repeated same-market trades diminish correctly.
             */
            minSell = 0.35f;
            maxSell = AOTD_NORMAL_SELL_MAX;
            minBuy = AOTD_NORMAL_BUY_MIN;
            maxBuy = 2.50f;
        }

        configureAoTDCalculator(
                commodity.getDemandPrice(),
                targets,
                minSell,
                maxSell,
                minBuy,
                maxBuy
        );

        configureAoTDCalculator(
                commodity.getSupplyPrice(),
                targets,
                minSell,
                maxSell,
                minBuy,
                maxBuy
        );

        /*
         * Non-V0 commodities use AoTDPriceCalculator final prices.
         * V0 bypasses PriceCalculator in MarketAPI, so keep a simple multiplier
         * fallback only for V0.
         */
        commodity.getPlayerDemandPriceMod().unmodifyMult(AOTD_PRICE_MOD_ID);
        commodity.getPlayerSupplyPriceMod().unmodifyMult(AOTD_PRICE_MOD_ID);

        if (commodity.getCommoditySpec().getPriceVariability().v <= 0.0001f) {
            commodity.getPlayerDemandPriceMod().modifyMult(AOTD_PRICE_MOD_ID, targets.sellMult);
            commodity.getPlayerSupplyPriceMod().modifyMult(AOTD_PRICE_MOD_ID, targets.buyMult);
        }
    }

    private static void ensureAoTDPriceCalculators(AoTDCommodityOnMarket commodity) {
        if (!(commodity.getDemandPrice() instanceof AoTDPriceCalculator)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass(
                    "demandPrice",
                    commodity,
                    new AoTDPriceCalculator(commodity)
            );
        }

        if (!(commodity.getSupplyPrice() instanceof AoTDPriceCalculator)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass(
                    "supplyPrice",
                    commodity,
                    new AoTDPriceCalculator(commodity)
            );
        }
    }

    private static void configureAoTDCalculator(
            PriceCalculator calculator,
            AoTDPriceTargets targets,
            float minSell,
            float maxSell,
            float minBuy,
            float maxBuy
    ) {
        if (calculator instanceof AoTDPriceCalculator aotdCalculator) {
            aotdCalculator.setAoTDPriceModel(
                    targets.sellMult,
                    targets.buyMult,
                    minSell,
                    maxSell,
                    minBuy,
                    maxBuy,
                    AOTD_REFERENCE_TRADE_QUANTITY,
                    AOTD_CUSTOM_PRICE_RESPONSE,
                    AOTD_CUSTOM_PRICE_STOCKPILE_DENOM_MULT,
                    AOTD_CUSTOM_PRICE_DENOM_MAX_REFERENCE_MULT,
                    AOTD_MAX_RESELL_RETURN_MULT
            );
        }
    }

    private static AoTDClassPriceState buildAoTDClassPriceState(List<CommodityOnMarket> sameClassCommodities) {
        AoTDClassPriceState state = new AoTDClassPriceState();

        for (CommodityOnMarket commodity : sameClassCommodities) {
            float utility = Math.max(0.0001f, commodity.getUtilityOnMarket());

            if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                float stockUtility = Math.max(0f, aotdCommodity.getStockpile()) * utility;

                state.classStockpileUtility += stockUtility;

                float deficit = Math.max(aotdCommodity.getDef(), aotdCommodity.getDeficitQuantity());
                float excess = Math.max(aotdCommodity.getExc(), aotdCommodity.getExcessQuantity());

                deficit = Math.max(0f, deficit);
                excess = Math.max(0f, excess);

                /*
                 * Use official AoTD excess/deficit state.
                 *
                 * Do not infer deficit from rawDemand - rawSupply here, because some
                 * normal markets can have large raw demand/supply internals without
                 * being officially deficit/excess. That was the cause of normal
                 * markets showing ~500 credits for a base-price 100 commodity.
                 */
                if (deficit > excess && deficit >= AOTD_MIN_STATE_AMOUNT) {
                    state.deficitUtility += deficit * utility;
                } else if (excess >= deficit && excess >= AOTD_MIN_STATE_AMOUNT) {
                    state.excessUtility += excess * utility;
                }
            } else {
                state.classStockpileUtility += Math.max(0f, commodity.getStockpile()) * utility;
            }
        }

        if (state.deficitUtility > state.excessUtility && state.deficitUtility >= AOTD_MIN_STATE_AMOUNT) {
            state.hasDeficit = true;
            state.classDemandUtility = state.classStockpileUtility + state.deficitUtility;
            state.pressure = aotdClamp(state.deficitUtility / Math.max(1f, state.classDemandUtility), 0f, 1f);
        } else if (state.excessUtility >= state.deficitUtility && state.excessUtility >= AOTD_MIN_STATE_AMOUNT) {
            state.hasExcess = true;
            state.classDemandUtility = Math.max(1f, state.classStockpileUtility - state.excessUtility);
            state.pressure = aotdClamp(state.excessUtility / Math.max(1f, state.classStockpileUtility), 0f, 1f);
        } else {
            /*
             * Normal market:
             * force demand == stockpile so vanilla curve stays around base.
             */
            state.classDemandUtility = Math.max(1f, state.classStockpileUtility);
            state.pressure = 0f;
        }

        return state;
    }

    private static void applyAoTDVanillaPriceBands(AoTDCommodityOnMarket commodity, AoTDClassPriceState state) {

        aotdResetPriceBands(commodity.getDemandPrice());
        aotdResetPriceBands(commodity.getSupplyPrice());
    }

    private static AoTDPriceTargets getAoTDPriceTargets(
            MarketAPI market,
            AoTDCommodityOnMarket commodity,
            AoTDClassPriceState state
    ) {
        if (state.hasDeficit) {
            float center = aotdLerp(AOTD_DEFICIT_CENTER_MIN, getDeficitCenterMax(commodity), state.pressure);
            return targetsFromNoImmediateProfitCenter(center);
        }

        if (state.hasExcess) {
            float center = aotdLerp(AOTD_EXCESS_CENTER_MAX, AOTD_EXCESS_CENTER_MIN, state.pressure);
            return targetsFromNoImmediateProfitCenter(center);
        }


        float buyRoll = aotdStablePriceRoll(market, commodity.getId() + "_buy");
        float sellRoll = aotdStablePriceRoll(market, commodity.getId() + "_sell");

        float buy = aotdLerp(AOTD_NORMAL_BUY_MIN, AOTD_NORMAL_BUY_MAX, buyRoll);


        float normalSellMax = commodity.isIllegal()
                ? AOTD_ILLEGAL_NORMAL_SELL_MAX
                : AOTD_NORMAL_SELL_MAX;

        float sell = aotdLerp(AOTD_NORMAL_SELL_MIN, normalSellMax, sellRoll);

        return new AoTDPriceTargets(sell, buy);
    }

    private static float getDeficitCenterMin(AoTDCommodityOnMarket commodity) {

        return AOTD_DEFICIT_CENTER_MIN;
    }

    private static float getDeficitCenterMax(AoTDCommodityOnMarket commodity) {
        CommoditySpecAPI spec = commodity.getCommoditySpec();


        if (commodity.isIllegal()) {
            return AOTD_ILLEGAL_DEFICIT_CENTER_MAX;
        }

        return AOTD_DEFICIT_CENTER_MAX;
    }

    private static float getCorrectionMult(float currentTotal, float targetTotal) {
        if (currentTotal <= 0f || targetTotal <= 0f) {
            return 1f;
        }

        return aotdClamp(targetTotal / currentTotal, AOTD_MIN_CORRECTION_MULT, AOTD_MAX_CORRECTION_MULT);
    }

    private static AoTDPriceTargets targetsFromCenter(float center) {
        return targetsFromNoImmediateProfitCenter(center);
    }

    private static AoTDPriceTargets targetsFromNoImmediateProfitCenter(float center) {
        float halfSpread = AOTD_MIN_LOCAL_SPREAD * 0.5f;


        return new AoTDPriceTargets(center - halfSpread, center + halfSpread);
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
        String seedString =
                String.valueOf(market.getId()) + "|"
                        + String.valueOf(market.getName()) + "|"
                        + String.valueOf(market.getFactionId()) + "|"
                        + commodityId;

        int seed = seedString.hashCode();
        seed ^= (seed << 13);
        seed ^= (seed >>> 17);
        seed ^= (seed << 5);

        return new Random(seed).nextFloat();
    }

    private static float aotdLerp(float from, float to, float t) {
        return from + (to - from) * aotdClamp(t, 0f, 1f);
    }

    private static float aotdClamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class AoTDClassPriceState {
        float classDemandUtility;
        float classStockpileUtility;
        float deficitUtility;
        float excessUtility;
        float pressure;
        boolean hasDeficit;
        boolean hasExcess;
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
