package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.PriceVariability;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.PriceCalculator;

import data.kaysaar.aotd.tot.scripts.commoditydata.BasePriceCalculator.TransactionDirection;

/**
 * Final AoTD player trade price calculator.
 *
 * Starsector naming is confusing here:
 * - Market.getDemandPrice() / PriceCalculator.getAddPrice(): player sells to market, stock goes up.
 * - Market.getSupplyPrice() / PriceCalculator.getRemovePrice(): player buys from market, stock goes down.
 *
 * The AoTD model therefore keeps two separate target prices:
 * - sellMult: what the player gets when selling into the market.
 * - buyMult:  what the player pays when buying from the market.
 *
 * Same-market anti-reselling is intentionally local and uses getCombinedTradeModQuantity().
 * It only punishes reversing previous trades on the same commodity+market, so buying cheap
 * on one market and selling high on another market remains profitable.
 */
public class EffectivePriceCalculator extends PriceCalculator {

    /** Fallback only, used before AoTdMainWorkTask2 configures the final model. */
    private static final float FALLBACK_PLAYER_SELL_MULT = 1.05f;
    /** Fallback only, used before AoTdMainWorkTask2 configures the final model. */
    private static final float FALLBACK_PLAYER_BUY_MULT = 0.95f;

    /** Minimum base AoTD excess/deficit amount that keeps a market anchored to that state. */
    private static final float AOTD_MIN_STATE_AMOUNT = 1f;

    /**
     * Persistent local trade marker used only for same-market anti-resell.
     *
     * Trade mods can be cleared when AoTD converts player-created excess into
     * local resources cargo. If we only look at the live trade mods, the player
     * can dump a huge amount, create excess, then buy it back cheaply after the
     * live trade signal disappears. This marker is written as soon as any local
     * trade displacement is observed.
     */
    public static final String LOCAL_PLAYER_TRADE_MEMORY_PREFIX = "$aotd_local_player_trade_";

    /** Kept for saves that already tested v13. */
    private static final String LEGACY_LOCAL_PLAYER_DUMP_MEMORY_PREFIX = "$aotd_local_player_dump_";

    protected float basePrice = 1f;
    protected float demand = 1f;

    private CommodityOnMarketAPI commodity;

    private boolean useAoTDPriceModel = false;

    /** Player sells to market. Blank normal target: 1.00 - 1.10. */
    private float targetSellMult = 1.05f;
    /** Player buys from market. Blank normal target: 0.90 - 1.00. */
    private float targetBuyMult = 0.95f;

    /** Normal-state reference used by same-market anti-resell even while current state is excess/deficit. */
    private float antiResellReferenceSellMult = 1.05f;
    private float antiResellReferenceBuyMult = 0.95f;

    private float minSellMult = 0.40f;
    private float maxSellMult = 3.00f;
    private float minBuyMult = 0.40f;
    private float maxBuyMult = 3.00f;

    private float referenceTradeQuantity = 500f;
    private float response = 0.06f;
    private float stockpileDenomMult = 1.00f;
    private float denomMaxReferenceMult = 12.00f;
    private float maxResellReturnMult = 0.94f;

    /**
     * Class stockpile utility before player trade impact. Market passes stockpile utility
     * after getCombinedTradeModQuantity() has been included, so comparing the two is
     * more reliable than trusting the sign of getCombinedTradeModQuantity() alone.
     */
    private float neutralStockpileUtility = -1f;

    /**
     * Official monthly AoTD state, excluding the currently previewed transaction.
     * -1 = excess, 0 = blank, 1 = deficit.
     */
    private int officialStateMode = 0;
    private float officialStateUtility = 0f;
    private float officialStatePressureDenom = 1f;

    /** Dynamic state anchors, already divided by vanilla wrappers by AoTdMainWorkTask2. */
    private float stateStartSellMult = 1.05f;
    private float stateStartBuyMult = 0.95f;
    private float stateExtremeSellMult = 1.05f;
    private float stateExtremeBuyMult = 0.95f;

    public EffectivePriceCalculator(CommodityOnMarketAPI com) {
        this.commodity = com;
        basePrice = com.getCommodity().getBasePrice();
        demand = com.getDemandValue();
    }

    public void setAoTDPriceModel(
            float targetSellMult,
            float targetBuyMult,
            float antiResellReferenceSellMult,
            float antiResellReferenceBuyMult,
            float minSellMult,
            float maxSellMult,
            float minBuyMult,
            float maxBuyMult,
            float referenceTradeQuantity,
            float response,
            float stockpileDenomMult,
            float denomMaxReferenceMult,
            float maxResellReturnMult,
            float neutralStockpileUtility,
            int officialStateMode,
            float officialStateUtility,
            float officialStatePressureDenom,
            float stateStartSellMult,
            float stateStartBuyMult,
            float stateExtremeSellMult,
            float stateExtremeBuyMult
    ) {
        this.useAoTDPriceModel = true;

        this.targetSellMult = sanitizeMult(targetSellMult, 1.05f);
        this.targetBuyMult = sanitizeMult(targetBuyMult, 0.95f);
        this.antiResellReferenceSellMult = sanitizeMult(antiResellReferenceSellMult, this.targetSellMult);
        this.antiResellReferenceBuyMult = sanitizeMult(antiResellReferenceBuyMult, this.targetBuyMult);

        this.minSellMult = Math.max(0.01f, Math.min(minSellMult, maxSellMult));
        this.maxSellMult = Math.max(this.minSellMult, maxSellMult);
        this.minBuyMult = Math.max(0.01f, Math.min(minBuyMult, maxBuyMult));
        this.maxBuyMult = Math.max(this.minBuyMult, maxBuyMult);

        this.referenceTradeQuantity = Math.max(1f, referenceTradeQuantity);
        this.response = Math.max(0f, response);
        this.stockpileDenomMult = Math.max(0.01f, stockpileDenomMult);
        this.denomMaxReferenceMult = Math.max(1f, denomMaxReferenceMult);
        this.maxResellReturnMult = clamp(maxResellReturnMult, 0.01f, 0.999f);
        this.neutralStockpileUtility = neutralStockpileUtility;

        this.officialStateMode = officialStateMode < 0 ? -1 : (officialStateMode > 0 ? 1 : 0);
        this.officialStateUtility = Math.max(0f, officialStateUtility);
        this.officialStatePressureDenom = Math.max(1f, officialStatePressureDenom);
        this.stateStartSellMult = sanitizeMult(stateStartSellMult, this.antiResellReferenceSellMult);
        this.stateStartBuyMult = sanitizeMult(stateStartBuyMult, this.antiResellReferenceBuyMult);
        this.stateExtremeSellMult = sanitizeMult(stateExtremeSellMult, this.targetSellMult);
        this.stateExtremeBuyMult = sanitizeMult(stateExtremeBuyMult, this.targetBuyMult);
    }

    public void clearAoTDPriceModel() {
        this.useAoTDPriceModel = false;
    }

    @Override
    public void setBasePrice(float price) {
        basePrice = Math.max(1f, price);
    }

    @Override
    public void setDemand(float value) {
        demand = value;
    }

    @Override
    public double getD() {
        return Math.max(BasePriceCalculator.INHERENT_DEMAND, demand);
    }

    @Override
    public float getPrice(double stock) {
        return getRemovePrice(stock, 1d);
    }

    /**
     * Market demand price: player sells to market, so stock increases.
     */

    @Override
    public float getAddPrice(double stock, double amount) {
        if (amount <= 0d) return 0f;
        if(commodity.isNonEcon()){
            CommoditySpecAPI specAPI = Global.getSettings().getCommoditySpec(commodity.getId());
            int trueAmount = (int) (amount/commodity.getUtilityOnMarket());
            return (float) (specAPI.getBasePrice()*trueAmount);
        }
        if (useAoTDPriceModel) {
            return getAoTDCustomTotalPrice(true, stock, amount);
        }


        return BasePriceCalculator.getUnitPrice(
                TransactionDirection.ENTITY_BUYING,
                (long) Math.ceil(amount),
                stock,
                basePrice,
                demand
        ) * (float) amount * FALLBACK_PLAYER_SELL_MULT;
    }

    /**
     * Market supply price: player buys from market, so stock decreases.
     */
    @Override
    public float getRemovePrice(double stock, double amount) {
        if (amount <= 0d) return 0f;
        if(commodity.isNonEcon()){
            CommoditySpecAPI specAPI = Global.getSettings().getCommoditySpec(commodity.getId());
            int trueAmount = (int) (amount/commodity.getUtilityOnMarket());
            return (float) (specAPI.getBasePrice()*trueAmount);
        }
        if (useAoTDPriceModel) {
            return getAoTDCustomTotalPrice(false, stock, amount);
        }

        return BasePriceCalculator.getUnitPrice(
                TransactionDirection.ENTITY_SELLING,
                (long) Math.ceil(amount),
                stock,
                basePrice,
                demand
        ) * (float) amount * FALLBACK_PLAYER_BUY_MULT;
    }

    private float getAoTDCustomTotalPrice(boolean playerSellingToMarket, double stock, double amount) {
        float safeBase = Math.max(1f, basePrice);
        double safeAmount = Math.max(0d, amount);
        if (safeAmount <= 0d) return 0f;

        int steps = getIntegrationSteps(safeAmount);
        double step = safeAmount / (double) steps;

        double total = 0d;
        for (int i = 0; i < steps; i++) {
            double progress = (i + 0.5d) * step;
            if (!playerSellingToMarket) {
                progress = -progress;
            }

            float mult = getAoTDUnitMult(playerSellingToMarket, stock, progress);
            total += safeBase * step * mult;
        }

        return (float) Math.max(safeAmount, total);
    }

    private int getIntegrationSteps(double amount) {
        if (amount <= 16d) {
            return Math.max(1, (int) Math.ceil(amount));
        }

        if (amount <= referenceTradeQuantity) {
            return 16;
        }

        return 32;
    }

    private float getAoTDUnitMult(boolean playerSellingToMarket, double stock, double transactionProgressUtility) {
        float denom = getResponseDenom(stock);

        /*
         * Market passes stockpile utility after the current transaction preview has
         * been applied. The neutral utility is the economy-update baseline. Their
         * difference is therefore the local displacement for this market/commodity.
         */
        float existingTradeUtility = getSameMarketTradeDisplacementUtility(stock);

        /*
         * Always include live local trade displacement. AoTdMainWorkTask2 now
         * configures neutralStockpileUtility from the CURRENT remaining state, so
         * after a proper price refresh this value is normally zero.
         *
         * If the market UI has not refreshed the task yet, this still lets the
         * calculator see getExcessQuantity()/trade-mod movement and prevents the
         * old state from staying at full excess forever.
         */
        float netUtilityAtThisUnit = existingTradeUtility + (float) transactionProgressUtility;

        LiveAoTDState liveStateAtThisUnit = getLiveAoTDState();
        float transactionOnlyUtility = netUtilityAtThisUnit - existingTradeUtility;

        float mult = getStateAwareBaseMult(playerSellingToMarket, netUtilityAtThisUnit, denom, existingTradeUtility);

        /*
         * Directional anti-reselling only.
         *
         * v12 fixed the normal round-trip bug by enforcing buy > sell after any
         * local trade history. That was too broad: while buying from an excess
         * market, the next buy quote was treated as a reverse trade and jumped
         * above base price. Continuing in the same direction must be handled by
         * the excess/deficit depletion curve, not by anti-resell.
         *
         * So:
         * - previous local buy  (stock below neutral) only caps selling back;
         * - previous local sell (stock above neutral) only floors buying back.
         */
        float antiResellTradeUtility = getAntiResellTradeUtility(stock, existingTradeUtility);
        boolean hasAnyAntiResellHistory = Math.abs(antiResellTradeUtility) > 0.0001f || hasAnyLiveSameMarketTradeHistory();

        /*
         * Hard directional same-market anti-resell, based on the directional
         * trade mods directly.
         *
         * This is intentionally separate from official excess/deficit state.
         * If the player has bought from THIS market, selling back into THIS market
         * must be capped even if the commodity is demanded/deficit. If the player
         * has sold into THIS market, buying back from THIS market must be floored
         * even if that sale created excess. Continuing in the same direction is not
         * punished here, so buying from excess still climbs normally instead of
         * jumping above base.
         */
        float playerBoughtFromThisMarketUtility = getPlayerBoughtFromThisMarketUtility(existingTradeUtility);
        float playerSoldToThisMarketUtility = getPlayerSoldToThisMarketUtility(existingTradeUtility);

        boolean rememberedSameMarketDump = hasRememberedSameMarketTrade();
        boolean buyingFromRealExcessThatStillExists = !playerSellingToMarket
                && liveStateAtThisUnit.mode < 0
                && liveStateAtThisUnit.currentUtility > 0f
                && liveStateAtThisUnit.currentUtility + transactionOnlyUtility > 0f;

        if (buyingFromRealExcessThatStillExists && !rememberedSameMarketDump) {
            /*
             * Excess invariant: while a real monthly excess still exists, the buy
             * quote may climb toward the blank buy band, but it must not jump above
             * it. This also covers the case where the player sells a few units into
             * an already-excess market: that sale should make selling even less
             * worthwhile, not reset the buy price to 120%+.
             *
             * Player-created dumped excess is different: clearExternalTrade() writes
             * rememberedSameMarketDump, and that path may still floor buyback above
             * blank so the huge dump -> cheap buyback exploit stays blocked.
             */
            mult = Math.min(mult, antiResellReferenceBuyMult);
        }

        boolean sellingIntoExcessAnchoredMarket = playerSellingToMarket
                && liveStateAtThisUnit.mode < 0
                && liveStateAtThisUnit.currentUtility + transactionOnlyUtility > 0f;

        if (sellingIntoExcessAnchoredMarket && !rememberedSameMarketDump) {
            /*
             * Excess-anchor anti-resell.
             *
             * v27 only applied this while current excess was still above 0. That
             * missed the exact roundtrip case:
             *   8k excess -> player buys all excess -> current excess is 0
             *   player sells the same cargo back -> sell price started from blank
             *
             * A market with getExc() > 0 is still excess-anchored for this pricing
             * window. Selling back into it should recreate oversaturation and be
             * paid worse than the buy curve at the same recreated excess pressure.
             * This keeps the desired buy curve growth, but removes the 600k buy /
             * 700k sell-back profit.
             */
            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);
            float currentBuyRaw = getStateAwareBaseMult(false, netUtilityAtThisUnit, denom, existingTradeUtility);
            float currentBuyFinal = currentBuyRaw * buyWrapper;
            float sellFinalCap = currentBuyFinal * maxResellReturnMult;
            float rawSellCap = sellFinalCap / sellWrapper;
            mult = Math.min(mult, Math.max(0.01f, rawSellCap));
        }

        if (playerSellingToMarket && playerBoughtFromThisMarketUtility > 0.0001f) {
            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);

            float referenceBuyFinal = antiResellReferenceBuyMult * buyWrapper;
            float sellFinalCap = referenceBuyFinal * maxResellReturnMult;
            float rawSellCap = sellFinalCap / sellWrapper;
            mult = Math.min(mult, Math.max(0.01f, rawSellCap));
        } else if (!playerSellingToMarket
                && playerSoldToThisMarketUtility > 0.0001f
                && !(buyingFromRealExcessThatStillExists && !rememberedSameMarketDump)) {
            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);

            /*
             * Remembered dump/player-created excess keeps the blank-state reference
             * so huge dump -> buyback cheap remains impossible. Existing excess is
             * handled on the SELL side above, so it must not raise the later buy price.
             */
            float referenceSellFinal = antiResellReferenceSellMult * sellWrapper;

            float buyFinalFloor = referenceSellFinal / maxResellReturnMult;
            float rawBuyFloor = buyFinalFloor / buyWrapper;
            mult = Math.max(mult, Math.max(0.01f, rawBuyFloor));
        }

        /*
         * Blank-state anti-resell is intentionally broad, like v12. This is the
         * case that fixed the universal "buy for 7k, sell for 7.7k" bug: after
         * any local trade exists, same-market buy must be higher than same-market
         * sell.
         *
         * Excess/deficit state remains directional, because broad enforcement there
         * was what made continuing to buy from excess jump above base.
         */
        if (officialStateMode == 0 && hasAnyAntiResellHistory) {
            float sellRawNow = getStateAwareBaseMult(true, netUtilityAtThisUnit, denom, existingTradeUtility);
            float buyRawNow = getStateAwareBaseMult(false, netUtilityAtThisUnit, denom, existingTradeUtility);

            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);

            float sellFinalNow = sellRawNow * sellWrapper;
            float buyFinalNow = buyRawNow * buyWrapper;

            float blankSellFinal = antiResellReferenceSellMult * sellWrapper;
            float blankBuyFinal = antiResellReferenceBuyMult * buyWrapper;

            if (playerSellingToMarket) {
                float referenceBuyFinal = Math.max(buyFinalNow, blankBuyFinal);
                float sellFinalCap = referenceBuyFinal * maxResellReturnMult;
                float rawSellCap = sellFinalCap / sellWrapper;
                mult = Math.min(mult, Math.max(0.01f, rawSellCap));
            } else {
                float referenceSellFinal = Math.max(sellFinalNow, blankSellFinal);
                float buyFinalFloor = referenceSellFinal / maxResellReturnMult;
                float rawBuyFloor = buyFinalFloor / buyWrapper;
                mult = Math.max(mult, Math.max(0.01f, rawBuyFloor));
            }
        } else if (antiResellTradeUtility < -0.0001f && playerSellingToMarket) {
            float buyRawNow = getStateAwareBaseMult(false, netUtilityAtThisUnit, denom, existingTradeUtility);
            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);

            float buyFinalNow = buyRawNow * buyWrapper;
            float blankBuyFinal = antiResellReferenceBuyMult * buyWrapper;
            float referenceBuyFinal = Math.max(buyFinalNow, blankBuyFinal);

            float sellFinalCap = referenceBuyFinal * maxResellReturnMult;
            float rawSellCap = sellFinalCap / sellWrapper;
            mult = Math.min(mult, Math.max(0.01f, rawSellCap));
        } else if (antiResellTradeUtility > 0.0001f
                && !playerSellingToMarket
                && !(buyingFromRealExcessThatStillExists && !rememberedSameMarketDump)) {
            float sellRawNow = getStateAwareBaseMult(true, netUtilityAtThisUnit, denom, existingTradeUtility);
            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);

            float sellFinalNow = sellRawNow * sellWrapper;
            float blankSellFinal = antiResellReferenceSellMult * sellWrapper;
            float referenceSellFinal = Math.max(sellFinalNow, blankSellFinal);

            float buyFinalFloor = referenceSellFinal / maxResellReturnMult;
            float rawBuyFloor = buyFinalFloor / buyWrapper;
            mult = Math.max(mult, Math.max(0.01f, rawBuyFloor));
        }

        if (buyingFromRealExcessThatStillExists && !rememberedSameMarketDump) {
            /*
             * Apply this after anti-resell floors too. The floor should only make
             * same-market buyback worse than the cheap excess sell price; it should
             * never push an active excess market above the blank buy band.
             */
            mult = Math.min(mult, antiResellReferenceBuyMult);
        }

        return Math.max(0.01f, mult);
    }

    private float getStateAwareBaseMult(boolean playerSellingToMarket, float netTradeUtility, float denom) {
        return getStateAwareBaseMult(playerSellingToMarket, netTradeUtility, denom, 0f);
    }

    private float getStateAwareBaseMult(
            boolean playerSellingToMarket,
            float netTradeUtility,
            float denom,
            float existingTradeUtility
    ) {
        LiveAoTDState liveState = getLiveAoTDState();

        /*
         * If AoTDCommodityOnMarket can report current remaining excess/deficit,
         * use that as the state position and apply only the currently simulated
         * transaction progress on top of it. Do not apply existingTradeUtility
         * twice; getExcessQuantity()/getDeficitQuantity() already include it.
         *
         * This is the key distinction:
         * - getExc()/getDef() or pressureDenom = base amount / how far from normal
         * - getExcessQuantity()/getDeficitQuantity() = current remaining amount
         */
        float movementUtility = liveState.fromCommodityQuantities
                ? (netTradeUtility - existingTradeUtility)
                : netTradeUtility;

        if (liveState.mode < 0) {
            /*
             * Excess anchor, split by direction.
             *
             * BUYING FROM MARKET:
             *   price is based on getExcessQuantity(), i.e. where we are now.
             *   8k excess -> cheap, 4k excess -> halfway toward blank, 0 -> blank.
             *
             * SELLING TO MARKET:
             *   price is based on getExc(), i.e. the monthly excess anchor.
             *   Even if the player bought getExcessQuantity() down to 0, this
             *   market was still oversaturated this month, so selling the same
             *   commodity back must remain weak/cheap instead of using blank sell
             *   pricing. This is the same-market anti-resell pressure for excess
             *   markets, without raising the BUY price.
             */
            if (playerSellingToMarket) {
                float anchoredExcess = Math.max(liveState.currentUtility, liveState.pressureDenom);
                float recreatedExcess = anchoredExcess + Math.max(0f, movementUtility);
                float pressure = clamp(recreatedExcess / liveState.pressureDenom, 0f, 1f);
                float sell = lerp(antiResellReferenceSellMult, stateExtremeSellMult, pressure);
                return clamp(sell, minSellMult, maxSellMult);
            }

            float remainingExcess = liveState.currentUtility + movementUtility;
            if (remainingExcess > 0f) {
                float pressure = clamp(remainingExcess / liveState.pressureDenom, 0f, 1f);
                float buy = lerp(antiResellReferenceBuyMult, stateExtremeBuyMult, pressure);
                return clamp(buy, minBuyMult, maxBuyMult);
            }

            /* Past the excess: buying beyond all excess returns to normal movement. */
            return getDirectionalBaseMultFromBase(
                    false,
                    antiResellReferenceBuyMult,
                    remainingExcess,
                    denom
            );
        }

        if (liveState.mode > 0) {
            /*
             * Deficit anchor, split by direction.
             *
             * SELLING TO MARKET:
             *   price is based on getDeficitQuantity(), i.e. where we are now.
             *   As the deficit is filled, the sell price falls toward blank.
             *
             * BUYING FROM MARKET:
             *   price is based on getDef(), i.e. the monthly deficit anchor.
             *   Even if the player filled the deficit down to 0, buying the same
             *   commodity back from that market must still carry a deficit markup.
             */
            if (!playerSellingToMarket) {
                float anchoredDeficit = Math.max(liveState.currentUtility, liveState.pressureDenom);
                float recreatedDeficit = anchoredDeficit + Math.max(0f, -movementUtility);
                float pressure = clamp(recreatedDeficit / liveState.pressureDenom, 0f, 1f);
                float buy = lerp(stateStartBuyMult, stateExtremeBuyMult, pressure);
                return clamp(buy, minBuyMult, maxBuyMult);
            }

            float remainingDeficit = liveState.currentUtility - movementUtility;
            if (remainingDeficit > 0f) {
                float pressure = clamp(remainingDeficit / liveState.pressureDenom, 0f, 1f);
                float sell = lerp(stateStartSellMult, stateExtremeSellMult, pressure);
                return clamp(sell, minSellMult, maxSellMult);
            }

            /* Past the deficit: extra selling now pushes toward oversupply. */
            float oversupplyUtility = -remainingDeficit;
            return getDirectionalBaseMultFromBase(
                    true,
                    antiResellReferenceSellMult,
                    oversupplyUtility,
                    denom
            );
        }

        return getDirectionalBaseMultFromBase(
                playerSellingToMarket,
                playerSellingToMarket ? targetSellMult : targetBuyMult,
                netTradeUtility,
                denom
        );
    }

    private LiveAoTDState getLiveAoTDState() {
        if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
            float utility = Math.max(0.0001f, aotdCommodity.getUtilityOnMarket());

            float currentDeficit = Math.max(0f, aotdCommodity.getDeficitQuantity()) * utility;
            float currentExcess = Math.max(0f, aotdCommodity.getExcessQuantity()) * utility;

            float baseDeficit = Math.max(0f, aotdCommodity.getDef()) * utility;
            float baseExcess = Math.max(0f, aotdCommodity.getExc()) * utility;

            if (currentDeficit > currentExcess && currentDeficit > 0.0001f) {
                float denom = Math.max(Math.max(baseDeficit, currentDeficit), officialStatePressureDenom);
                return new LiveAoTDState(1, currentDeficit, Math.max(1f, denom), true);
            }

            if (currentExcess >= currentDeficit && currentExcess > 0.0001f) {
                float denom = Math.max(Math.max(baseExcess, currentExcess), officialStatePressureDenom);
                return new LiveAoTDState(-1, currentExcess, Math.max(1f, denom), true);
            }

            /*
             * Keep the monthly state as an anchor even after the current remaining
             * quantity reaches 0.
             *
             * Example: 8k excess -> player buys all of it -> getExcessQuantity() is 0.
             * Selling those supplies back should not use blank sell pricing; it should
             * recreate excess from 0 toward getExc(), producing a low average sell
             * price and preventing same-market resale profit.
             */
            if (baseExcess >= AOTD_MIN_STATE_AMOUNT * utility && baseExcess >= baseDeficit) {
                float denom = Math.max(baseExcess, officialStatePressureDenom);
                return new LiveAoTDState(-1, 0f, Math.max(1f, denom), true);
            }

            if (baseDeficit >= AOTD_MIN_STATE_AMOUNT * utility) {
                float denom = Math.max(baseDeficit, officialStatePressureDenom);
                return new LiveAoTDState(1, 0f, Math.max(1f, denom), true);
            }

            /*
             * If live AoTD quantities are currently zero, do NOT immediately fall
             * back to blank. AoTdMainWorkTask2 may still have configured an
             * anchored monthly state from market memory:
             *   excess existed -> player bought it all -> getExcessQuantity() == 0
             * In that case sell-back must still be priced as excess-anchored.
             */
            if (officialStateMode != 0 && officialStatePressureDenom > 1f) {
                return new LiveAoTDState(
                        officialStateMode,
                        Math.max(0f, officialStateUtility),
                        Math.max(1f, officialStatePressureDenom),
                        false
                );
            }

            return new LiveAoTDState(0, 0f, Math.max(1f, officialStatePressureDenom), true);
        }

        return new LiveAoTDState(
                officialStateMode,
                officialStateUtility,
                Math.max(1f, officialStatePressureDenom),
                false
        );
    }

    private static final class LiveAoTDState {
        final int mode;
        final float currentUtility;
        final float pressureDenom;
        final boolean fromCommodityQuantities;

        LiveAoTDState(int mode, float currentUtility, float pressureDenom, boolean fromCommodityQuantities) {
            this.mode = mode < 0 ? -1 : (mode > 0 ? 1 : 0);
            this.currentUtility = Math.max(0f, currentUtility);
            this.pressureDenom = Math.max(1f, pressureDenom);
            this.fromCommodityQuantities = fromCommodityQuantities;
        }
    }

    private float getDirectionalBaseMultFromBase(boolean playerSellingToMarket, float base, float netTradeUtility, float denom) {
        float min = playerSellingToMarket ? minSellMult : minBuyMult;
        float max = playerSellingToMarket ? maxSellMult : maxBuyMult;

        float signedPressure = netTradeUtility / (denom + Math.abs(netTradeUtility));
        float mult = base - response * signedPressure;

        return clamp(mult, min, max);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t, 0f, 1f);
    }

    private float getPlayerBoughtFromThisMarketUtility(float existingTradeUtility) {
        if (commodity == null) {
            return 0f;
        }

        /*
         * Main source of truth: MarketDemand stockpile utility already includes
         * local trade impact. If it is below the neutral economy-update baseline,
         * the player has bought from this same market.
         *
         * This is more reliable than trusting tradeModPlus/tradeModMinus names,
         * since their sign can vary between vanilla/AoTD paths and UI previews.
         */
        if (existingTradeUtility < -0.0001f) {
            return -existingTradeUtility;
        }

        if (neutralStockpileUtility >= 0f) {
            return 0f;
        }

        /* Fallback for old saves or calculators configured before neutral utility exists. */
        float utility = getUtilityOnMarketSafe();
        float combined = commodity.getCombinedTradeModQuantity();
        if (combined < -0.0001f) {
            return -combined * utility;
        }

        try {
            if (commodity.getTradeModMinus() != null) {
                return Math.abs(commodity.getTradeModMinus().getModifiedValue()) * utility;
            }
        } catch (Throwable ignored) {
        }

        return 0f;
    }

    private float getPlayerSoldToThisMarketUtility(float existingTradeUtility) {
        if (commodity == null) {
            return 0f;
        }

        float remembered = getRememberedSameMarketTradeUtility();
        if (remembered > 0.0001f) {
            return remembered;
        }

        /*
         * Main source of truth: above-neutral stockpile means the player has sold
         * into this same market. This avoids treating a staged/confirmed BUY from
         * an already-excess market as previous selling just because one live trade
         * mod happens to be positive.
         */
        if (existingTradeUtility > 0.0001f) {
            return existingTradeUtility;
        }

        if (neutralStockpileUtility >= 0f) {
            return 0f;
        }

        /* Fallback for old saves or calculators configured before neutral utility exists. */
        float utility = getUtilityOnMarketSafe();
        float combined = commodity.getCombinedTradeModQuantity();
        if (combined > 0.0001f) {
            return combined * utility;
        }

        try {
            if (commodity.getTradeModPlus() != null) {
                return Math.abs(commodity.getTradeModPlus().getModifiedValue()) * utility;
            }
        } catch (Throwable ignored) {
        }

        return 0f;
    }

    private float getAntiResellTradeUtility(double stock, float existingTradeUtility) {
        if (Math.abs(existingTradeUtility) > 0.0001f) {
            return existingTradeUtility;
        }

        if (commodity != null) {
            float utility = getUtilityOnMarketSafe();

            float combined = commodity.getCombinedTradeModQuantity();
            if (Math.abs(combined) > 0.0001f) {
                return combined * utility;
            }

            try {
                float trade = 0f;
                if (commodity.getTradeMod() != null) {
                    trade += commodity.getTradeMod().getModifiedValue();
                }
                if (commodity.getTradeModPlus() != null) {
                    trade += commodity.getTradeModPlus().getModifiedValue();
                }
                if (commodity.getTradeModMinus() != null) {
                    trade += commodity.getTradeModMinus().getModifiedValue();
                }

                if (Math.abs(trade) > 0.0001f) {
                    return trade * utility;
                }
            } catch (Throwable ignored) {
                /* API/decompiled variants may differ. getCombinedTradeModQuantity() is still the main path. */
            }
        }

        return getRememberedSameMarketTradeUtility();
    }

    private boolean hasAnyLiveSameMarketTradeHistory() {
        if (commodity == null) {
            return false;
        }

        if (Math.abs(commodity.getCombinedTradeModQuantity()) > 0.0001f) {
            return true;
        }

        try {
            if (commodity.getTradeMod() != null && Math.abs(commodity.getTradeMod().getModifiedValue()) > 0.0001f) {
                return true;
            }
            if (commodity.getTradeModPlus() != null && Math.abs(commodity.getTradeModPlus().getModifiedValue()) > 0.0001f) {
                return true;
            }
            if (commodity.getTradeModMinus() != null && Math.abs(commodity.getTradeModMinus().getModifiedValue()) > 0.0001f) {
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }

        return false;
    }

    private boolean hasRememberedSameMarketTrade() {
        return getRememberedSameMarketTradeUtility() > 0.0001f;
    }

    private float getRememberedSameMarketTradeUtility() {
        if (commodity == null || commodity.getMarket() == null || commodity.getMarket().getMemoryWithoutUpdate() == null) {
            return 0f;
        }

        try {
            String id = commodity.getId();
            Object value = commodity.getMarket().getMemoryWithoutUpdate().get(LOCAL_PLAYER_TRADE_MEMORY_PREFIX + id);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }

            value = commodity.getMarket().getMemoryWithoutUpdate().get(LEGACY_LOCAL_PLAYER_DUMP_MEMORY_PREFIX + id);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }

            if (commodity.getMarket().getMemoryWithoutUpdate().contains(LOCAL_PLAYER_TRADE_MEMORY_PREFIX + id)
                    || commodity.getMarket().getMemoryWithoutUpdate().contains(LEGACY_LOCAL_PLAYER_DUMP_MEMORY_PREFIX + id)) {
                return 1f;
            }
        } catch (Throwable ignored) {
            return 0f;
        }

        return 0f;
    }

    private float getResponseDenom(double stock) {
        float safeStock = (float) Math.max(0d, stock);
        float byStock = safeStock * stockpileDenomMult;
        float min = referenceTradeQuantity * 3.0f;
        float max = referenceTradeQuantity * denomMaxReferenceMult;

        return clamp(Math.max(min, byStock), min, max);
    }

    private float getSameMarketTradeDisplacementUtility(double stock) {
        /*
         * Price movement must be based on how much of the monthly state was
         * actually eaten/filled by player trade. In some vanilla UI paths the
         * stock argument does not move enough while the trade mods already do;
         * that made an 8k excess stay priced like a full 8k excess even after
         * the player bought most of it.
         *
         * Use the larger signed displacement between:
         * - stockpile utility minus neutral economy-update utility;
         * - live same-market trade mods converted to utility.
         *
         * Do not add them together, because on paths where both are correct they
         * describe the same local trade impact and summing would double-count it.
         */
        float stockDisplacement = 0f;
        boolean hasNeutral = neutralStockpileUtility >= 0f;
        if (hasNeutral) {
            stockDisplacement = (float) (stock - neutralStockpileUtility);
        }

        float tradeModDisplacement = getLiveTradeModDisplacementUtility();

        if (Math.abs(tradeModDisplacement) > Math.abs(stockDisplacement)) {
            return tradeModDisplacement;
        }

        if (hasNeutral) {
            return stockDisplacement;
        }

        return tradeModDisplacement;
    }

    private float getLiveTradeModDisplacementUtility() {
        if (commodity == null) return 0f;

        float utility = getUtilityOnMarketSafe();
        float combined = commodity.getCombinedTradeModQuantity();
        if (Math.abs(combined) > 0.0001f) {
            return combined * utility;
        }

        try {
            float soldToMarket = 0f;
            float boughtFromMarket = 0f;

            if (commodity.getTradeModPlus() != null) {
                soldToMarket += Math.abs(commodity.getTradeModPlus().getModifiedValue());
            }
            if (commodity.getTradeModMinus() != null) {
                boughtFromMarket += Math.abs(commodity.getTradeModMinus().getModifiedValue());
            }

            float directional = soldToMarket - boughtFromMarket;
            if (Math.abs(directional) > 0.0001f) {
                return directional * utility;
            }

            if (commodity.getTradeMod() != null) {
                float trade = commodity.getTradeMod().getModifiedValue();
                if (Math.abs(trade) > 0.0001f) {
                    return trade * utility;
                }
            }
        } catch (Throwable ignored) {
            /* Different decompiled/API paths may not expose all three mods. */
        }

        return 0f;
    }

    private float getUtilityOnMarketSafe() {
        if (commodity == null) return 1f;
        return Math.max(0.0001f, commodity.getUtilityOnMarket());
    }

    private float getFinalWrapperMult(boolean playerSellingToMarket) {
        if (commodity == null) {
            return 1f;
        }

        float wrapped = 1f;

        if (playerSellingToMarket && commodity.getMarket() instanceof Market) {
            Market market = (Market) commodity.getMarket();
            wrapped = market.getDemandPriceMod().computeEffective(wrapped);
            wrapped = commodity.getPlayerDemandPriceMod().computeEffective(wrapped);
        } else if (!playerSellingToMarket) {
            /* Vanilla player-visible supply path uses playerSupplyPriceMod on the
             * raw per-unit value and does not apply market.supplyPriceMod. */
            wrapped = commodity.getPlayerSupplyPriceMod().computeEffective(wrapped);
        }

        if (Float.isNaN(wrapped) || Float.isInfinite(wrapped) || wrapped <= 0f) {
            return 1f;
        }

        return wrapped;
    }

    private static float sanitizeMult(float value, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return fallback;
        return Math.max(0.01f, value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }



    private static float applyVanillaVisibleWrappers(
            float perUnit,
            boolean isSellingToMarket,
            boolean isPlayer,
            Market market,
            CommodityOnMarketAPI com
    ) {
        if (isSellingToMarket) {
            /* Market#getDemandPrice(..., true): raw -> market demand mod -> player demand mod. */
            perUnit = market.getDemandPriceMod().computeEffective(perUnit);
            if (isPlayer) {
                perUnit = com.getPlayerDemandPriceMod().computeEffective(perUnit);
            }
            return perUnit;
        }

        if (isPlayer) {
            /* Market#getSupplyPrice(..., true) applies player supply to the raw per-unit price. */
            return com.getPlayerSupplyPriceMod().computeEffective(perUnit);
        }

        return market.getSupplyPriceMod().computeEffective(perUnit);
    }

    // UNUSED METHODS

    @Override public void setVariability(PriceVariability variability) {}
    @Override public float getLowPriceThreshold() { return 0f; }
    @Override public void setLowPriceThreshold(float threshold) {}
    @Override public float getLowPriceMult() { return 0f; }
    @Override public void setLowPriceMult(float mult) {}
    @Override public float getHighPriceThreshold() { return 0f; }
    @Override public void setHighPriceThreshold(float threshold) {}
    @Override public float getHighPriceMult() { return 0f; }
    @Override public void setHighPriceMult(float mult) {}
}
