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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

public class AoTdMainWorkTask2 extends MainWorkTask2 {

    private  List<MarketAPI> aotdMarkets;
    private  final MainWorkTask.EconWorkParams aotdParams;

    private List<String> aotdCommodities;
    private int aotdIndex = 0;
    private boolean aotdStarted = false;
    private int aotdMarketIndex = 0;
    public MarketAPI singleMarketToUpdate;

    private static final String CORE_MOD_ID = "core";
    private static final String AOTD_PRICE_MOD_ID = "aotd_price_state";

    /*
     * The tooltip price table must use the same reference quantity as this.
     *
     * Current tooltip is back to "Price / 500", so this is 500.
     * PriceCalculator is nonlinear, so calibration should match the displayed
     * trade quantity.
     */
    private static final float AOTD_REFERENCE_TRADE_QUANTITY = 500f;

    /*
     * Extra quantities where we enforce:
     *
     * same-market sell price <= same-market buy price
     *
     * This protects against the curve producing valid prices at 100 units but
     * inverted prices at another common batch size.
     */
    private static final float[] AOTD_SPREAD_CHECK_QUANTITIES = new float[] {
            100f,
            500f
    };

    /*
     * Player perspective:
     *
     * demandPrice = player SELLS to market.
     * supplyPrice = player BUYS from market.
     *
     * Anti-resell invariant:
     *
     * On the SAME market, sell price must never be higher than buy price.
     * Otherwise the player can buy and immediately resell for profit.
     *
     * Important:
     * The market has a center price, then local spread is applied around it:
     *
     * sell = center - spread / 2
     * buy  = center + spread / 2
     *
     * This means different markets can still create trade opportunities:
     * a high-price market may buy from the player above base, while a low-price
     * market may sell to the player below base.
     */

    /*
     * Neutral center range.
     *
     * With spread applied, neutral markets usually land around:
     * - sell: 87%-109%
     * - buy:  93%-115%
     *
     * This creates a larger cross-market gap while same-market reselling
     * remains blocked by the local spread.
     *
     * Same market remains non-exploitable, but cross-market trade still exists.
     */
    private static final float AOTD_NORMAL_CENTER_MIN = 0.90f;
    private static final float AOTD_NORMAL_CENTER_MAX = 1.12f;

    /*
     * Excess center range.
     *
     * Oversupplied markets are cheap.
     *
     * With spread applied, excess markets usually land around:
     * - sell: 62%-82%
     * - buy:  68%-88%
     */
    private static final float AOTD_EXCESS_CENTER_MIN = 0.65f;
    private static final float AOTD_EXCESS_CENTER_MAX = 0.85f;

    /*
     * Deficit center range.
     *
     * Undersupplied markets are expensive.
     *
     * With spread applied, deficit markets usually land around:
     * - sell: 122%-157%
     * - buy:  128%-163%
     */
    private static final float AOTD_DEFICIT_CENTER_MIN = 1.25f;
    private static final float AOTD_DEFICIT_CENTER_MAX = 1.60f;

    /*
     * Minimum local spread between same-market buy and sell.
     *
     * buy price must be at least sell price + this value.
     */
    private static final float AOTD_MIN_LOCAL_SPREAD = 0.06f;

    /*
     * A small underlying greed curve so vanilla supplyPrice starts above demandPrice
     * even before final calibration.
     */
    private static final float AOTD_GREED_FRACTION = 0.06f;

    private static final float AOTD_MIN_STATE_AMOUNT = 1f;

    /*
     * Safety clamp for the final correction multiplier.
     *
     * This is not a design price range. It only prevents insane modifiers if the
     * underlying vanilla curve returns something pathological.
     */
    private static final float AOTD_MIN_CORRECTION_MULT = 0.05f;
    private static final float AOTD_MAX_CORRECTION_MULT = 20f;

    public AoTdMainWorkTask2(List<MarketAPI> markets, ReachEconomy reachEconomy, MainWorkTask.EconWorkParams econWorkParams) {
        super(markets, reachEconomy, econWorkParams);

        this.aotdMarkets = new ArrayList<>(markets);
        this.aotdParams = econWorkParams;
    }
    boolean runOnce=  false;
    public AoTdMainWorkTask2(List<MarketAPI> markets, ReachEconomy reachEconomy, MainWorkTask.EconWorkParams econWorkParams,MarketAPI singleMarket) {
        super(markets, reachEconomy, econWorkParams);
        this.singleMarketToUpdate = singleMarket;
        this.aotdMarkets = new ArrayList<>(markets);
        this.aotdParams = econWorkParams;
    }
    @Override
    public void initCommodityList() {
        this.aotdCommodities = new ArrayList<>();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            if (!spec.hasTag("nonecon")) {
                this.aotdCommodities.add(spec.getId());
            }
        }

        this.aotdCommodities.sort(Comparator.naturalOrder());
    }

    @Override
    public void doNextBatch() {
        if (!aotdStarted) {
            initCommodityList();
            if(singleMarketToUpdate != null) {
                runOnce = true;
                singleMarketToUpdate.reapplyConditions();

                AoTDIndustryData data = AoTDIndustryData.getInstance(singleMarketToUpdate);
                for (Industry industry : singleMarketToUpdate.getIndustries()) {
                    if (!data.isPending(industry.getId())) {
                        industry.reapply();
                    }
                }
                for (String commodityId : aotdCommodities) {
                    CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(commodityId);
                    new AoTDCommodityMarketData(commodityId, null);
                    if (aotdParams.withStockpileUpdate) {
                        for (MarketAPI market : aotdMarkets) {
                            aotdUpdateStockpileAndPrice((Market) market, commoditySpec);
                        }
                    }

                    List<EconomyAPI.EconomyUpdateListener> listeners =
                            Global.getSector().getEconomy().getUpdateListeners();

                    for (EconomyAPI.EconomyUpdateListener listener : new ArrayList<>(listeners)) {
                        if (listener.isEconomyListenerExpired()) {
                            Global.getSector().getEconomy().removeUpdateListener(listener);
                        } else {
                            listener.commodityUpdated(commodityId);
                        }
                    }
                }

                aotdStarted = true;

            }
            else{
                if(aotdMarkets==null){
                    aotdMarkets = Global.getSector().getEconomy().getMarketsCopy();
                }
                aotdIndex = 0;
                aotdStarted = true;
            }

            return;
        }

        if (isDone()) {
            return;
        }

        if (aotdMarketIndex < aotdMarkets.size()) {
            MarketAPI market = aotdMarkets.get(aotdMarketIndex);

            market.reapplyConditions();

            AoTDIndustryData data = AoTDIndustryData.getInstance(market);
            for (Industry industry : market.getIndustries()) {
                if (!data.isPending(industry.getId())) {
                    industry.reapply();
                }
            }

            aotdMarketIndex++;
            return;
        }

        String commodityId = aotdCommodities.get(aotdIndex);
        CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(commodityId);
        aotdIndex++;

        LinkedHashSet<String> econGroups = new LinkedHashSet<>();
        for (MarketAPI market : aotdMarkets) {
            String econGroup = market.getEconGroup();
            if (econGroup != null) {
                econGroups.add(econGroup);
            }
        }

        new AoTDCommodityMarketData(commodityId, null);

        for (String econGroup : econGroups) {
            new AoTDCommodityMarketData(commodityId, econGroup);
        }

        if (aotdParams!=null&&aotdParams.withStockpileUpdate) {
            for (MarketAPI market : aotdMarkets) {
                aotdUpdateStockpileAndPrice((Market) market, commoditySpec);
            }
        }

        List<EconomyAPI.EconomyUpdateListener> listeners =
                Global.getSector().getEconomy().getUpdateListeners();

        for (EconomyAPI.EconomyUpdateListener listener : new ArrayList<>(listeners)) {
            if (listener.isEconomyListenerExpired()) {
                Global.getSector().getEconomy().removeUpdateListener(listener);
            } else {
                listener.commodityUpdated(commodityId);
            }
        }
    }

    @Override
    public boolean isDone() {
        if(singleMarketToUpdate!=null){
            return runOnce;
        }
        return aotdCommodities != null && aotdIndex >= aotdCommodities.size();
    }

    @Override
    public String getLoggingIdentifier() {
        return "AoTdMainWorkTask2";
    }

    public static List<CommodityOnMarket> getCommoditiesWithSameDemandClass(String demandClass, MarketAPI market) {
        ArrayList<CommodityOnMarket> commodities = new ArrayList<>();

        for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
            if (demandClass.equals(commodity.getDemandClass())) {
                commodities.add((CommodityOnMarket) commodity);
            }
        }

        return commodities;
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

        Random random = new Random(
                (long) market.getId().hashCode()
                        + commoditySpec.getId().hashCode()
                        + Global.getSector().getClock().getMonth() * 170000L
        );

        List<CommodityOnMarket> sameClassCommodities =
                getCommoditiesWithSameDemandClass(commoditySpec.getDemandClass(), market);

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
        float stockpileBonus = 0.0f;
        float stockpileMult = 1.0f;
        float demandForDemandClass = 0.0f;
        float greedFraction = Economy.ECONOMY_GREED_FRACTION;
        boolean noDemandOrSupply = false;

        for (CommodityOnMarket commodity : sameClassCommodities) {
            if (!commodity.getCommodity().isPrimary()) {
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
            if (commodity.getCommodity().isPrimary()) {
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

            /*
             * getStocks() is raw market mass.
             *
             * AoTDMarketDemand converts this to utility units by multiplying by
             * getUtilityOnMarket().
             */
            float rawStocks = Math.max(floor, Math.max(rawDemand, rawSupply));

            aotdCommodity.setStocks(Math.round(rawStocks));

            /*
             * Mirror for vanilla systems that look at getStockpile().
             */
            aotdCommodity.setStockpile(rawStocks);
        }
    }

    private static void applyAoTDNeutralCurveAndCalibratedPriceMods(
            MarketAPI market,
            List<CommodityOnMarket> sameClassCommodities
    ) {
        float classStockpileUtility = getAoTDClassStockpileUtility(sameClassCommodities);

        /*
         * Keep the underlying PriceCalculator curve near neutral.
         *
         * The final displayed prices are calibrated after updateCalc().
         */
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

            /*
             * Every AoTD commodity must get the same neutral demand curve.
             *
             * updateCalc() reads each commodity's own demand stat.
             */
            setModifiedValueWithFlatMod(commodity.getDemand().getDemand(), CORE_MOD_ID, neutralDemandCurve);

            /*
             * Greed creates the natural same-market spread:
             *
             * supplyPrice uses demand + greed.
             * demandPrice uses demand only.
             *
             * This means buying from the market starts above selling to the market,
             * before final calibration is applied.
             */
            setModifiedValueWithFlatMod(commodity.getGreed(), CORE_MOD_ID, neutralDemandCurve * AOTD_GREED_FRACTION);

            aotdCommodity.updateCalc();

            /*
             * Prevent old threshold bands from fighting the calibrated price mods.
             */
            aotdResetPriceBands(aotdCommodity.getDemandPrice());
            aotdResetPriceBands(aotdCommodity.getSupplyPrice());

            applyCalibratedAoTDPlayerPriceMods(market, aotdCommodity);
        }
    }

    private static void applyCalibratedAoTDPlayerPriceMods(MarketAPI market, AoTDCommodityOnMarket commodity) {
        AoTDPriceTargets targets = getAoTDPriceTargets(market, commodity);

        /*
         * Remove our previous state first so current price measurement is not polluted.
         */
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

        /*
         * Hard anti-resell guard.
         *
         * We enforce this after target calibration because PriceCalculator is nonlinear.
         * A pair of corrections that is valid for one quantity can still invert for
         * another quantity.
         *
         * Final rule:
         * same-market sell total + minimum spread <= same-market buy total
         */
        float minSpreadPerUnit = basePrice * AOTD_MIN_LOCAL_SPREAD;

        for (float checkQuantity : AOTD_SPREAD_CHECK_QUANTITIES) {
            if (checkQuantity <= 0f) continue;

            float sellWithoutAoTD = market.getDemandPrice(commodity.getId(), checkQuantity, true);
            float buyWithoutAoTD = market.getSupplyPrice(commodity.getId(), checkQuantity, true);

            if (sellWithoutAoTD <= 0f || buyWithoutAoTD <= 0f) continue;

            float predictedSell = sellWithoutAoTD * sellCorrection;
            float predictedBuy = buyWithoutAoTD * buyCorrection;

            float requiredBuy = predictedSell + minSpreadPerUnit * checkQuantity;

            if (predictedBuy < requiredBuy) {
                buyCorrection = requiredBuy / buyWithoutAoTD;
                buyCorrection = aotdClamp(buyCorrection, AOTD_MIN_CORRECTION_MULT, AOTD_MAX_CORRECTION_MULT);

                predictedBuy = buyWithoutAoTD * buyCorrection;

                /*
                 * If buy correction hit the safety clamp and still cannot create a spread,
                 * lower sell correction. Same-market exploit prevention wins over price target.
                 */
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
            /*
             * Deficit should sort above normal markets in the "Best places to sell" list.
             */
            float basis = Math.max(1f, rawDemand);
            float pressure = aotdClamp(deficit / basis, 0f, 1f);

            float center = aotdLerp(AOTD_DEFICIT_CENTER_MIN, AOTD_DEFICIT_CENTER_MAX, pressure);
            return targetsFromCenter(center);
        }

        if (hasExcess) {
            /*
             * Excess should sort low in sell lists and high-value in buy lists.
             */
            float basis = Math.max(1f, Math.max(rawSupply, rawDemand));
            float pressure = aotdClamp(excess / basis, 0f, 1f);

            float center = aotdLerp(AOTD_EXCESS_CENTER_MAX, AOTD_EXCESS_CENTER_MIN, pressure);
            return targetsFromCenter(center);
        }

        /*
         * Neutral:
         * stable per market/commodity roll.
         */
        float roll = aotdStablePriceRoll(market, commodity.getId());

        float center = aotdLerp(AOTD_NORMAL_CENTER_MIN, AOTD_NORMAL_CENTER_MAX, roll);
        return targetsFromCenter(center);
    }

    private static AoTDPriceTargets targetsFromCenter(float center) {
        /*
         * Same-market anti-resell is enforced here for every state:
         *
         * sell = center - spread / 2
         * buy  = center + spread / 2
         */
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
