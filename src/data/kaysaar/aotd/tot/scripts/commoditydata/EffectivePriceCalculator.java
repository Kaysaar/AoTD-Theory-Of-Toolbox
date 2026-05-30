package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.PriceVariability;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.PriceCalculator;

import data.kaysaar.aotd.tot.scripts.commoditydata.BasePriceCalculator.TransactionDirection;

public class EffectivePriceCalculator extends PriceCalculator {
    // TODO directionality. getUnitPrice is symmetric otherwise. Adjust as you like.
    private static final float MARKET_SELLING_MULT = 1.15f;
    private static final float MARKET_BUYING_MULT = 0.85f;

    private static final float ILLEGAL_GOODS_MULT = 1.5f; // TODO adjust as you like

    protected float basePrice = 1f;
    protected float demand = 1f;

    // TODO for the future maybe per commodity price variability could be used:
    // com.fs.starfarer.api.campaign.econ.PriceVariability.V4

    public EffectivePriceCalculator(CommodityOnMarketAPI com) {
        basePrice = com.getCommodity().getBasePrice();
        demand = com.getDemandValue();
    }

    @Override
    public void setBasePrice(float price) {
        basePrice = price;
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
     * Not intended to be used by AoTD. However, the following methods from {@link Market} use this:
     * <ul>
     *      <li>{@link Market#getDemandPrice}</li>
     *      <li>{@link Market#getDemandPriceAssumingExistingTransaction}</li>
     *      <li>{@link Market#getDemandPriceAssumingStockpileUtility}</li>
     * </ul>
     */
    @Override
    public float getAddPrice(double stock, double amount) {
        return BasePriceCalculator.getUnitPrice(TransactionDirection.ENTITY_BUYING, (long) amount, stock, basePrice, demand)
            * (float) amount * MARKET_BUYING_MULT;
    }

    /**
     * Not intended to be used by AoTD. However, the following methods from {@link Market} use this:
     * <ul>
     *      <li>{@link Market#getSupplyPrice}</li>
     *      <li>{@link Market#getSupplyPriceAssumingExistingTransaction}</li>
     *      <li>{@link Market#getSupplyPriceAssumingStockpileUtility}</li>
     * </ul>
     */
    @Override
    public float getRemovePrice(double stock, double amount) { 
        return BasePriceCalculator.getUnitPrice(TransactionDirection.ENTITY_SELLING, (long) amount, stock, basePrice, demand)
            * (float) amount * MARKET_SELLING_MULT;
    }

    /**
     * Computes the effective and ready-to-use value of a commodity. Should be used instead of:
     * <ul>
     *      <li>{@link #getPrice}</li>
     *      <li>{@link #getAddPrice}</li>
     *      <li>{@link #getRemovePrice}</li>
     *      <li>{@link Market#getDemandPrice}</li>
     *      <li>{@link Market#getDemandPriceAssumingExistingTransaction}</li>
     *      <li>{@link Market#getDemandPriceAssumingStockpileUtility}</li>
     *      <li>{@link Market#getSupplyPrice}</li>
     *      <li>{@link Market#getSupplyPriceAssumingExistingTransaction}</li>
     *      <li>{@link Market#getSupplyPriceAssumingStockpileUtility}</li>
     * </ul>
     */
    public static final float computeVanillaPrice(double amount, boolean isSellingToMarket, boolean isPlayer,
        AoTDCommodityOnMarket com
    ) {
        final AoTDSupplyDemandData data = com.getSupplyDemandData();
        final float stock = Math.max(0f, data.available - data.demand); 

        return computeVanillaPrice(amount, isSellingToMarket, isPlayer, com, data.demand, stock);
    }

    /**
     * Computes the effective and ready-to-use value of a commodity. Should be used instead of:
     * <ul>
     *      <li>{@link #getPrice}</li>
     *      <li>{@link #getAddPrice}</li>
     *      <li>{@link #getRemovePrice}</li>
     *      <li>{@link Market#getDemandPrice}</li>
     *      <li>{@link Market#getDemandPriceAssumingExistingTransaction}</li>
     *      <li>{@link Market#getDemandPriceAssumingStockpileUtility}</li>
     *      <li>{@link Market#getSupplyPrice}</li>
     *      <li>{@link Market#getSupplyPriceAssumingExistingTransaction}</li>
     *      <li>{@link Market#getSupplyPriceAssumingStockpileUtility}</li>
     * </ul>
     * 
     * @param demand is the effective demand.
     * @param stock is the current stockpiled amount excluding trade modifiers.
     */
    public static final float computeVanillaPrice(double amount, boolean isSellingToMarket, boolean isPlayer,
        CommodityOnMarketAPI com, float demand, double stock
    ) {
        if (amount < 1) return 0f;

        final Market market = (Market) com.getMarket();
        final CommoditySpecAPI spec = com.getCommodity();

        final StatBonus priceMod = isPlayer ? 
            (isSellingToMarket ? com.getPlayerDemandPriceMod() : com.getPlayerSupplyPriceMod()):
            (isSellingToMarket ? market.getDemandPriceMod() : market.getSupplyPriceMod());

        if (spec.getPriceVariability() == PriceVariability.V0) {
            final float value = (float) (spec.getBasePrice() * amount);
            return isPlayer ? priceMod.computeEffective(value) : value;
        }

        final double stored = stock + com.getCombinedTradeModQuantity();

        final TransactionDirection type = isSellingToMarket ? TransactionDirection.ENTITY_BUYING : TransactionDirection.ENTITY_SELLING;
        final float unitPrice = BasePriceCalculator.getUnitPrice(
            type, (long) (amount * com.getUtilityOnMarket()),
            stored, spec.getBasePrice(), demand
        );

        final float directionMult = isSellingToMarket ? MARKET_BUYING_MULT : MARKET_SELLING_MULT;

        final float totalPrice = priceMod.computeEffective(unitPrice) * directionMult
            * (com.isIllegal() ? ILLEGAL_GOODS_MULT : 1f);

        return (float) Math.floor(Math.max(totalPrice * amount, amount));
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