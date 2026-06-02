package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.PriceVariability;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
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
            float neutralStockpileUtility
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
         * Market#getDemandPrice()/getSupplyPrice() passes stockpile utility from
         * MarketDemand. AoTDMarketDemand should include getCombinedTradeModQuantity().
         * Therefore stock - neutralStockpileUtility is the real same-market player
         * trade displacement in utility units:
         *   < 0: player previously bought from this market
         *   > 0: player previously sold to this market
         * This is safer than relying only on getCombinedTradeModQuantity() sign, because
         * several submarket paths can update plus/minus/net mods differently.
         */
        float existingTradeUtility = getSameMarketTradeDisplacementUtility(stock);
        float netUtilityAtThisUnit = existingTradeUtility + (float) transactionProgressUtility;

        float mult = getDirectionalBaseMult(playerSellingToMarket, netUtilityAtThisUnit, denom);

        /*
         * Same-market anti-reselling must not depend on detecting the exact direction
         * of the previous trade. In practice several submarket paths update net/plus/minus
         * trade mods differently, and that is what caused the universal
         * "buy for 7k, sell for 7.7k" bug.
         *
         * After ANY local player trade history exists for this market+commodity, lock
         * the local spread into the safe direction:
         *
         *     player buy price > player sell price
         *
         * This is only active for this exact market+commodity after trade impact exists,
         * so cross-market route profit is not affected.
         */
        if (hasSameMarketTradeHistory(existingTradeUtility)) {
            float sellRawNow = getDirectionalBaseMult(true, netUtilityAtThisUnit, denom);
            float buyRawNow = getDirectionalBaseMult(false, netUtilityAtThisUnit, denom);

            float sellWrapper = getFinalWrapperMult(true);
            float buyWrapper = getFinalWrapperMult(false);

            float sellFinalNow = sellRawNow * sellWrapper;
            float buyFinalNow = buyRawNow * buyWrapper;

            /*
             * Use the normal blank-state reference, not the current state target.
             * In excess state targetSellMult/targetBuyMult are intentionally cheap;
             * using them here allowed the dump exploit to come back:
             * sell huge amount -> create excess -> buy back from the collapsed target.
             */
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
        }

        return Math.max(0.01f, mult);
    }


    private boolean hasSameMarketTradeHistory(float existingTradeUtility) {
        if (Math.abs(existingTradeUtility) > 0.0001f) {
            rememberSameMarketTrade(existingTradeUtility);
            return true;
        }

        if (commodity == null) {
            return false;
        }

        if (hasRememberedSameMarketTrade()) {
            return true;
        }

        float combined = commodity.getCombinedTradeModQuantity();
        if (Math.abs(combined) > 0.0001f) {
            rememberSameMarketTrade(combined);
            return true;
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
                rememberSameMarketTrade(trade);
                return true;
            }
        } catch (Throwable ignored) {
            /* API/decompiled variants may differ. getCombinedTradeModQuantity() is still the main path. */
        }

        return false;
    }

    private boolean hasRememberedSameMarketTrade() {
        if (commodity == null || commodity.getMarket() == null || commodity.getMarket().getMemoryWithoutUpdate() == null) {
            return false;
        }

        try {
            String id = commodity.getId();
            return commodity.getMarket().getMemoryWithoutUpdate().contains(LOCAL_PLAYER_TRADE_MEMORY_PREFIX + id)
                    || commodity.getMarket().getMemoryWithoutUpdate().contains(LEGACY_LOCAL_PLAYER_DUMP_MEMORY_PREFIX + id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void rememberSameMarketTrade(float observedTrade) {
        if (commodity == null || commodity.getMarket() == null || commodity.getMarket().getMemoryWithoutUpdate() == null) {
            return;
        }

        try {
            commodity.getMarket().getMemoryWithoutUpdate().set(
                    LOCAL_PLAYER_TRADE_MEMORY_PREFIX + commodity.getId(),
                    observedTrade,
                    31f
            );
        } catch (Throwable ignored) {
            /* Memory is best-effort. Live trade mods still handle the normal case. */
        }
    }

    private float getDirectionalBaseMult(boolean playerSellingToMarket, float netTradeUtility, float denom) {
        float base = playerSellingToMarket ? targetSellMult : targetBuyMult;
        float min = playerSellingToMarket ? minSellMult : minBuyMult;
        float max = playerSellingToMarket ? maxSellMult : maxBuyMult;

        /*
         * netTradeUtility > 0 means the market has received goods from player trades.
         * netTradeUtility < 0 means the market has lost goods to player trades.
         * Prices are intentionally monotonic:
         * - as stock falls from buying, buy price rises;
         * - as stock rises from selling, sell price falls.
         */
        float signedPressure = netTradeUtility / (denom + Math.abs(netTradeUtility));
        float mult = base - response * signedPressure;

        return clamp(mult, min, max);
    }

    private float getResponseDenom(double stock) {
        float safeStock = (float) Math.max(0d, stock);
        float byStock = safeStock * stockpileDenomMult;
        float min = referenceTradeQuantity * 3.0f;
        float max = referenceTradeQuantity * denomMaxReferenceMult;

        return clamp(Math.max(min, byStock), min, max);
    }

    private float getSameMarketTradeDisplacementUtility(double stock) {
        if (neutralStockpileUtility >= 0f) {
            return (float) (stock - neutralStockpileUtility);
        }

        if (commodity == null) return 0f;
        return commodity.getCombinedTradeModQuantity() * getUtilityOnMarketSafe();
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


    public static final float computeVanillaPrice(double amount, boolean isSellingToMarket, boolean isPlayer,
                                                  AoTDCommodityOnMarket com
    ) {
        /*
         * Use the same pricing stockpile/demand that AoTdMainWorkTask2 writes
         * into CommodityOnMarket. Raw available-demand is not the price state.
         */
        final float stock = Math.max(0f, com.getStockpile());
        final float demand = Math.max(1f, com.getDemand().getDemandValue());

        return computeVanillaPrice(amount, isSellingToMarket, isPlayer, com, demand, stock);
    }

    /**
     * Computes the effective and ready-to-use value of a commodity.
     *
     * @param demand is the effective demand.
     * @param stock is the current stockpiled amount excluding trade modifiers.
     */
    public static final float computeVanillaPrice(double amount, boolean isSellingToMarket, boolean isPlayer,
                                                  CommodityOnMarket com, float demand, double stock
    ) {
        if (amount < 1) return 0f;

        final Market market = (Market) com.getMarket();
        final CommoditySpecAPI spec = com.getCommodity();

        if (spec.getPriceVariability() == PriceVariability.V0) {
            float perUnit = spec.getBasePrice();
            perUnit = applyVanillaVisibleWrappers(perUnit, isSellingToMarket, isPlayer, market, com);
            return (float) Math.floor(Math.max(perUnit * amount, amount));
        }

        final float utility = Math.max(0.0001f, com.getUtilityOnMarket());
        final double amountUtility = amount * utility;
        final PriceCalculator calculator = isSellingToMarket ? com.getDemandPrice() : com.getSupplyPrice();

        if (calculator instanceof EffectivePriceCalculator) {
            ((EffectivePriceCalculator) calculator).setDemand(demand);
        }

        final float rawTotal = isSellingToMarket
                ? calculator.getAddPrice(stock, amountUtility)
                : calculator.getRemovePrice(stock, amountUtility);

        float perUnit = rawTotal / (float) Math.max(1d, amount);
        perUnit = applyVanillaVisibleWrappers(perUnit, isSellingToMarket, isPlayer, market, com);

        return (float) Math.floor(Math.max(perUnit * amount, amount));
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
