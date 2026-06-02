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
import data.kaysaar.aotd.tot.scripts.commoditydata.EffectivePriceCalculator;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;

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
    private static final float AOTD_NORMAL_CENTER_MAX = 1.10f;

    /** Player buys from market in blank state: 90% - 100% of base price. */
    private static final float AOTD_NORMAL_BUY_MIN = 0.90f;
    private static final float AOTD_NORMAL_BUY_MAX = 1.00f;
    /** Player sells to market in blank state: 100% - 110% of base price. */
    private static final float AOTD_NORMAL_SELL_MIN = 1.00f;
    private static final float AOTD_NORMAL_SELL_MAX = 1.10f;

    /** Full excess can push prices down to 40% of base price. */
    private static final float AOTD_EXCESS_PRICE_FLOOR = 0.40f;
    private static final float AOTD_EXCESS_SELL_SPREAD = 0.06f;

    /** Legal deficit caps around 160% of base price. */
    private static final float AOTD_DEFICIT_CENTER_MIN = 1.10f;
    private static final float AOTD_DEFICIT_CENTER_MAX = 1.60f;

    /** Illegal deficit caps between 250% and 300% of base price, stable per market/commodity. */
    private static final float AOTD_ILLEGAL_DEFICIT_CENTER_MIN = 2.50f;
    private static final float AOTD_ILLEGAL_DEFICIT_CENTER_MAX = 3.00f;

    private static final float AOTD_MIN_LOCAL_SPREAD = 0.06f;
    private static final float AOTD_GREED_FRACTION = 0.06f;

    private static final float AOTD_PRICE_CURVE_STATE_STRENGTH = 0.65f;

    /** Same-market reverse trades return at most this fraction of what the opposite side charges. */
    private static final float AOTD_MAX_RESELL_RETURN_MULT = 0.85f;

    /** How strongly same-market trade history moves prices during transactions. */
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
            float rawDemand = Math.max(0f, data.getTotalRawUnitsFromDemand());
            float stableSharedSubmarketLimit = getAoTDStableSharedSubmarketLimit(market, aotdCommodity, rawSupply);

            aotdCommodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(aotdCommodity);

            float officialDeficit = Math.max(0f, aotdCommodity.getDeficitQuantity());
            float officialExcess = Math.max(0f, aotdCommodity.getExcessQuantity());

            /*
             * Real stock remains the actual AoTD supply and is used for availability/UI.
             * Pricing stockpile is a stable simulation baseline used by MarketDemand.
             *
             * Important rule:
             * - blank state does NOT mean rawSupply == rawDemand;
             * - blank state means AoTD has no official excess/deficit.
             *
             * Therefore blank markets use stockpile == demand curve later, keeping the
             * neutral curve around base price. Official excess/deficit bends the stockpile
             * baseline only enough to make Market's own assumptions sane; final visible
             * ranges are configured in EffectivePriceCalculator.
             */
            float floor = Math.max(1f, PriceCalculator.MIN_STOCKPILE_FOR_PRICING);
            float realStocks = Math.max(floor, rawSupply);

            float pricingBasis = Math.max(realStocks, stableSharedSubmarketLimit);
            pricingBasis = Math.max(pricingBasis, rawDemand);
            pricingBasis = Math.max(pricingBasis, AOTD_REFERENCE_TRADE_QUANTITY);

            float pricingStockpile = pricingBasis;
            if (officialExcess > officialDeficit && officialExcess >= AOTD_MIN_STATE_AMOUNT) {
                pricingStockpile = pricingBasis + officialExcess;
            } else if (officialDeficit > officialExcess && officialDeficit >= AOTD_MIN_STATE_AMOUNT) {
                pricingStockpile = Math.max(floor, pricingBasis - officialDeficit);
            }

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

            // applyAoTDVanillaPriceBands(aotdCommodity, state);
            applyCalibratedAoTDPlayerPriceMods(market, aotdCommodity, state);
        }
    }

    private static void applyCalibratedAoTDPlayerPriceMods(
            MarketAPI market,
            AoTDCommodityOnMarket commodity,
            AoTDClassPriceState state
    ) {
        /*
         * Important rule for v3:
         *
         * Boundaries are only STARTING bands. They select the initial raw target
         * that the custom calculator starts from. After that, the calculator is
         * allowed to move prices as stockpile/trade impact changes:
         *
         * - buying from the market reduces effective stock, so buy price grows;
         * - selling to the market increases effective stock, so sell price drops;
         * - same-market reverse trades are capped by getCombinedTradeModQuantity().
         *
         * Do NOT re-normalize the visible 500-unit price from the current price.
         * That was the anti-resell killer: after a trade changed the raw price,
         * the correction multiplier changed too and cancelled the movement.
         */
        ensureAoTDPriceCalculators(commodity);

        AoTDPriceTargets finalTargets = getAoTDPriceTargets(market, commodity, state);
        AoTDPriceTargets blankTargets = getAoTDBlankPriceTargets(market, commodity);

        float minSell;
        float maxSell;
        float minBuy;
        float maxBuy;

        if (state.hasDeficit) {
            float deficitMax = getDeficitCenterMax(commodity);

            /*
             * Deficit starts expensive, but after the player sells into the market
             * the sell price must be able to fall. Buying from deficit can still
             * climb above the starting band.
             */
            minSell = AOTD_EXCESS_PRICE_FLOOR;
            maxSell = deficitMax;
            minBuy = AOTD_NORMAL_BUY_MIN;
            maxBuy = Math.max(deficitMax, commodity.isIllegal() ? AOTD_ILLEGAL_DEFICIT_CENTER_MAX : 2.00f);
        } else if (state.hasExcess) {
            /*
             * Excess starts cheap. Buying should climb back toward normal as the
             * excess disappears; selling into excess should collapse further.
             */
            minSell = 0.25f;
            maxSell = AOTD_NORMAL_SELL_MAX;
            minBuy = AOTD_EXCESS_PRICE_FLOOR;
            maxBuy = Math.max(AOTD_NORMAL_BUY_MAX, 1.25f);
        } else {
            /*
             * Blank STARTING visible band:
             * - player buys from market: 0.90 - 1.00
             * - player sells to market: 1.00 - 1.10
             *
             * These are not dynamic clamps. Repeated player trades can move outside
             * the starting band to create diminishing returns.
             */
            minSell = 0.25f;
            maxSell = 1.60f;
            minBuy = AOTD_NORMAL_BUY_MIN;
            maxBuy = 2.50f;
        }

        /*
         * Market.getDemandPrice()/getSupplyPrice() wrap calculator output in
         * market-level demand/supply price mods. Instead of using player price
         * mods to correct the result every time, bake that wrapper into the
         * calculator's raw starting target. That preserves trade movement.
         */
        float demandWrapper = getMarketPriceWrapper(market, true);
        float supplyWrapper = getMarketPriceWrapper(market, false);

        AoTDPriceTargets rawTargets = new AoTDPriceTargets(
                finalTargets.sellMult / demandWrapper,
                finalTargets.buyMult / supplyWrapper
        );

        AoTDPriceTargets rawBlankTargets = new AoTDPriceTargets(
                blankTargets.sellMult / demandWrapper,
                blankTargets.buyMult / supplyWrapper
        );

        configureAoTDCalculator(
                commodity.getDemandPrice(),
                rawTargets,
                rawBlankTargets,
                minSell / demandWrapper,
                maxSell / demandWrapper,
                minBuy / supplyWrapper,
                maxBuy / supplyWrapper,
                state.classStockpileUtility
        );

        configureAoTDCalculator(
                commodity.getSupplyPrice(),
                rawTargets,
                rawBlankTargets,
                minSell / demandWrapper,
                maxSell / demandWrapper,
                minBuy / supplyWrapper,
                maxBuy / supplyWrapper,
                state.classStockpileUtility
        );

        commodity.getPlayerDemandPriceMod().unmodifyMult(AOTD_PRICE_MOD_ID);
        commodity.getPlayerSupplyPriceMod().unmodifyMult(AOTD_PRICE_MOD_ID);

        /*
         * V0 commodities bypass PriceCalculator in MarketAPI, so they cannot use
         * the dynamic curve. Keep only the initial visible band for them.
         */
        if (commodity.getSpec().getPriceVariability() == PriceVariability.V0) {
            commodity.getPlayerDemandPriceMod().modifyMult(AOTD_PRICE_MOD_ID, finalTargets.sellMult);
            commodity.getPlayerSupplyPriceMod().modifyMult(AOTD_PRICE_MOD_ID, finalTargets.buyMult);
        }
    }

    private static float getMarketPriceWrapper(MarketAPI market, boolean playerSellingToMarket) {
        /*
         * This follows vanilla Market's PLAYER-visible path:
         * - demand price/player selling includes market.demandPriceMod first;
         * - supply price/player buying does not include market.supplyPriceMod in
         *   the final player-visible branch of Market#getSupplyPrice(..., true).
         */
        if (!playerSellingToMarket) {
            return 1f;
        }

        if (!(market instanceof Market vanillaMarket)) {
            return 1f;
        }

        float wrapped = vanillaMarket.getDemandPriceMod().computeEffective(1f);

        if (Float.isNaN(wrapped) || Float.isInfinite(wrapped) || wrapped <= 0f) {
            return 1f;
        }

        return wrapped;
    }

    private static void ensureAoTDPriceCalculators(AoTDCommodityOnMarket commodity) {
        if (!(commodity.getDemandPrice() instanceof EffectivePriceCalculator)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass(
                    "demandPrice",
                    commodity,
                    new EffectivePriceCalculator(commodity)
            );
        }

        if (!(commodity.getSupplyPrice() instanceof EffectivePriceCalculator)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass(
                    "supplyPrice",
                    commodity,
                    new EffectivePriceCalculator(commodity)
            );
        }
    }

    private static void configureAoTDCalculator(
            PriceCalculator calculator,
            AoTDPriceTargets targets,
            AoTDPriceTargets blankTargets,
            float minSell,
            float maxSell,
            float minBuy,
            float maxBuy,
            float neutralStockpileUtility
    ) {
        if (calculator instanceof EffectivePriceCalculator aotdCalculator) {
            aotdCalculator.setAoTDPriceModel(
                    targets.sellMult,
                    targets.buyMult,
                    blankTargets.sellMult,
                    blankTargets.buyMult,
                    minSell,
                    maxSell,
                    minBuy,
                    maxBuy,
                    AOTD_REFERENCE_TRADE_QUANTITY,
                    AOTD_CUSTOM_PRICE_RESPONSE,
                    AOTD_CUSTOM_PRICE_STOCKPILE_DENOM_MULT,
                    AOTD_CUSTOM_PRICE_DENOM_MAX_REFERENCE_MULT,
                    AOTD_MAX_RESELL_RETURN_MULT,
                    neutralStockpileUtility
            );
        }
    }

    private static AoTDClassPriceState buildAoTDClassPriceState(List<CommodityOnMarket> sameClassCommodities) {
        AoTDClassPriceState state = new AoTDClassPriceState();

        for (CommodityOnMarket commodity : sameClassCommodities) {
            float utility = Math.max(0.0001f, commodity.getUtilityOnMarket());

            if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                AoTDSupplyDemandData data = aotdCommodity.getSupplyDemandData();

                float stockUtility = Math.max(0f, aotdCommodity.getStockpile()) * utility;
                float rawDemandUtility = Math.max(0f, data.getTotalRawUnitsFromDemand()) * utility;

                state.classStockpileUtility += stockUtility;
                state.classRawDemandUtility += rawDemandUtility;

                /*
                 * Use official AoTD state, not raw supply - demand.
                 *
                 * getDeficitQuantity()/getExcessQuantity() include local trade impact,
                 * so fulfilling a deficit or draining an excess moves price in the
                 * correct direction without turning every raw mismatch into a state.
                 */
                float deficit = Math.max(0f, aotdCommodity.getDeficitQuantity());
                float excess = Math.max(0f, aotdCommodity.getExcessQuantity());

                if (deficit > excess && deficit >= AOTD_MIN_STATE_AMOUNT) {
                    state.deficitUtility += deficit * utility;
                } else if (excess >= deficit && excess >= AOTD_MIN_STATE_AMOUNT) {
                    state.excessUtility += excess * utility;
                }
            } else {
                state.classStockpileUtility += Math.max(0f, commodity.getStockpile()) * utility;
            }
        }

        float pressureDenom = Math.max(
                AOTD_REFERENCE_TRADE_QUANTITY,
                Math.max(1f, state.classRawDemandUtility)
        );

        if (state.deficitUtility > state.excessUtility && state.deficitUtility >= AOTD_MIN_STATE_AMOUNT) {
            state.hasDeficit = true;
            state.classDemandUtility = state.classStockpileUtility + state.deficitUtility;
            state.pressure = aotdClamp(state.deficitUtility / pressureDenom, 0f, 1f);
        } else if (state.excessUtility >= state.deficitUtility && state.excessUtility >= AOTD_MIN_STATE_AMOUNT) {
            state.hasExcess = true;
            state.classDemandUtility = Math.max(1f, state.classStockpileUtility - state.excessUtility);
            state.pressure = aotdClamp(state.excessUtility / pressureDenom, 0f, 1f);
        } else {
            /*
             * Blank market: force demand == stockpile so the neutral curve remains
             * around base price. Stable market-specific variation is applied as
             * explicit buy/sell targets, not by faking deficit/excess.
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

    private static AoTDPriceTargets getAoTDBlankPriceTargets(
            MarketAPI market,
            AoTDCommodityOnMarket commodity
    ) {
        float buyRoll = aotdStablePriceRoll(market, commodity.getId() + "_buy");
        float sellRoll = aotdStablePriceRoll(market, commodity.getId() + "_sell");

        float blankBuy = aotdLerp(AOTD_NORMAL_BUY_MIN, AOTD_NORMAL_BUY_MAX, buyRoll);
        float blankSell = aotdLerp(AOTD_NORMAL_SELL_MIN, AOTD_NORMAL_SELL_MAX, sellRoll);

        return new AoTDPriceTargets(blankSell, blankBuy);
    }

    private static AoTDPriceTargets getAoTDPriceTargets(
            MarketAPI market,
            AoTDCommodityOnMarket commodity,
            AoTDClassPriceState state
    ) {
        float buyRoll = aotdStablePriceRoll(market, commodity.getId() + "_buy");
        float sellRoll = aotdStablePriceRoll(market, commodity.getId() + "_sell");

        float blankBuy = aotdLerp(AOTD_NORMAL_BUY_MIN, AOTD_NORMAL_BUY_MAX, buyRoll);
        float blankSell = aotdLerp(AOTD_NORMAL_SELL_MIN, AOTD_NORMAL_SELL_MAX, sellRoll);

        if (state.hasDeficit) {
            float deficitMax = getDeficitCenterMax(commodity);
            float deficitStart = commodity.isIllegal()
                    ? AOTD_ILLEGAL_DEFICIT_CENTER_MIN
                    : AOTD_DEFICIT_CENTER_MIN;

            float pressure = aotdClamp(state.pressure, 0f, 1f);
            float center = aotdLerp(deficitStart, deficitMax, pressure);

            /*
             * Keep both sides expensive in deficit. Cross-market profit remains possible
             * because excess/blank markets can still be cheap while this market buys high.
             */
            return new AoTDPriceTargets(center, center);
        }

        if (state.hasExcess) {
            float pressure = aotdClamp(state.pressure, 0f, 1f);

            float buy = aotdLerp(blankBuy, AOTD_EXCESS_PRICE_FLOOR, pressure);
            float sell = aotdLerp(blankSell, AOTD_EXCESS_PRICE_FLOOR + AOTD_EXCESS_SELL_SPREAD, pressure);

            return new AoTDPriceTargets(sell, buy);
        }

        return new AoTDPriceTargets(blankSell, blankBuy);
    }

    private static float getDeficitCenterMin(AoTDCommodityOnMarket commodity) {
        if (commodity.isIllegal()) {
            return AOTD_ILLEGAL_DEFICIT_CENTER_MIN;
        }

        return AOTD_DEFICIT_CENTER_MIN;
    }

    private static float getDeficitCenterMax(AoTDCommodityOnMarket commodity) {
        if (commodity.isIllegal()) {
            float roll = aotdStablePriceRoll(commodity.getMarket(), commodity.getId() + "_illegal_deficit_max");
            return aotdLerp(AOTD_ILLEGAL_DEFICIT_CENTER_MIN, AOTD_ILLEGAL_DEFICIT_CENTER_MAX, roll);
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
        float classRawDemandUtility;
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
