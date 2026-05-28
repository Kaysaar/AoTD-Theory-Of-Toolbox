package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.PriceCalculator;

public class AoTDPriceCalculator extends PriceCalculator {
    private final AoTDCommodityOnMarket commodity;

    private float targetSellMult = 1.05f;
    private float targetBuyMult = 0.95f;

    private float minSellMult = 0.35f;
    private float maxSellMult = 1.10f;
    private float minBuyMult = 0.90f;
    private float maxBuyMult = 2.50f;

    private float referenceQuantity = 500f;
    private float response = 0.18f;
    private float stockpileDenomMult = 0.75f;
    private float denomMaxReferenceMult = 8.00f;
    private float maxResellReturnMult = 0.94f;

    public AoTDPriceCalculator(AoTDCommodityOnMarket commodity) {
        this.commodity = commodity;
    }

    public void setAoTDPriceModel(
            float targetSellMult,
            float targetBuyMult,
            float minSellMult,
            float maxSellMult,
            float minBuyMult,
            float maxBuyMult,
            float referenceQuantity,
            float response,
            float stockpileDenomMult,
            float denomMaxReferenceMult,
            float maxResellReturnMult
    ) {
        this.targetSellMult = Math.max(0.01f, targetSellMult);
        this.targetBuyMult = Math.max(0.01f, targetBuyMult);

        this.minSellMult = Math.max(0.01f, minSellMult);
        this.maxSellMult = Math.max(this.minSellMult, maxSellMult);

        this.minBuyMult = Math.max(0.01f, minBuyMult);
        this.maxBuyMult = Math.max(this.minBuyMult, maxBuyMult);

        this.referenceQuantity = Math.max(1f, referenceQuantity);
        this.response = Math.max(0f, response);
        this.stockpileDenomMult = Math.max(0.01f, stockpileDenomMult);
        this.denomMaxReferenceMult = Math.max(1f, denomMaxReferenceMult);
        this.maxResellReturnMult = Math.max(0.01f, Math.min(1f, maxResellReturnMult));
    }

    @Override
    public float getAddPrice(double stockpileUtility, double amountUtility) {

        if (amountUtility <= 0.0 || commodity == null) {
            return 0f;
        }

        float basePrice = commodity.getCommoditySpec().getBasePrice();
        double utility = Math.max(0.0001, commodity.getUtilityOnMarket());

        double amount = Math.max(0.0, amountUtility / utility);
        double prior = getPriorTradeUnits(stockpileUtility, utility);

        double priorSold = Math.max(0.0, prior);
        double priorBought = Math.max(0.0, -prior);
        double denom = getReactionDenominator();

        double finalMult =
                targetSellMult
                        - response * ((priorSold + amount * 0.5) / denom);


        if (priorBought > 0.0) {
            finalMult = Math.min(finalMult, targetBuyMult * maxResellReturnMult);
        }

        double min = priorBought > 0.0 ? 0.01 : minSellMult;
        finalMult = clamp(finalMult, min, maxSellMult);

        return (float) Math.floor(basePrice * amount * finalMult);
    }

    @Override
    public float getRemovePrice(double stockpileUtility, double amountUtility) {

        if (amountUtility <= 0.0 || commodity == null) {
            return 0f;
        }

        float basePrice = commodity.getCommoditySpec().getBasePrice();
        double utility = Math.max(0.0001, commodity.getUtilityOnMarket());

        double amount = Math.max(0.0, amountUtility / utility);
        double prior = getPriorTradeUnits(stockpileUtility, utility);

        double priorSold = Math.max(0.0, prior);
        double priorBought = Math.max(0.0, -prior);
        double denom = getReactionDenominator();

        double finalMult =
                targetBuyMult
                        + response * ((priorBought + amount * 0.5) / denom);

        /*
         * If player sold to this market first, buying back must be more expensive
         * than the original sell value.
         */
        if (priorSold > 0.0) {
            finalMult = Math.max(finalMult, targetSellMult / maxResellReturnMult);
        }

        double max = priorSold > 0.0
                ? Math.max(maxBuyMult, targetSellMult / maxResellReturnMult)
                : maxBuyMult;

        finalMult = clamp(finalMult, minBuyMult, max);

        return (float) Math.floor(basePrice * amount * finalMult);
    }

    private double getPriorTradeUnits(double stockpileUtility, double utility) {
        /*
         * MarketAPI passes demand-class stockpile utility, not this commodity's
         * personal stockpile. Existing-transaction previews are represented here.
         */
        double baselineClassUtility = getBaselineClassUtility();
        double fromClassUtility = (stockpileUtility - baselineClassUtility) / Math.max(0.0001, utility);

        double fromMods = getEffectiveRawTradeImpact();

        if (Math.abs(fromClassUtility) <= 0.01) {
            return fromMods;
        }

        if (Math.abs(fromMods) <= 0.01) {
            return fromClassUtility;
        }

        if (Math.signum(fromClassUtility) == Math.signum(fromMods)) {
            return Math.abs(fromClassUtility) >= Math.abs(fromMods) ? fromClassUtility : fromMods;
        }


        return fromMods;
    }

    private double getBaselineClassUtility() {
        if (commodity == null) {
            return getBaselineStock() * Math.max(0.0001, 1.0);
        }

        double total = 0.0;

        for (CommodityOnMarket other : commodity.getMarket().getCommoditiesWithClass(commodity.getDemandClass())) {
            if (other == null) continue;

            double stockpile = Math.max(0.0, other.getStockpile());
            double utility = Math.max(0.0001, other.getUtilityOnMarket());

            total += stockpile * utility;
        }

        if (total <= 0.0) {
            total = getBaselineStock() * Math.max(0.0001, commodity.getUtilityOnMarket());
        }

        return total;
    }

    private double getEffectiveRawTradeImpact() {
        float combined = commodity.getCombinedTradeModQuantity();

        float rawSum =
                commodity.getTradeMod().getModifiedValue()
                        + commodity.getTradeModPlus().getModifiedValue()
                        + commodity.getTradeModMinus().getModifiedValue();

        if (Math.abs(rawSum) <= 0.01f) {
            return combined;
        }

        if (Math.abs(combined) <= 0.01f) {
            return rawSum;
        }

        if (Math.signum(rawSum) == Math.signum(combined)) {
            return Math.abs(rawSum) >= Math.abs(combined) ? rawSum : combined;
        }

        return combined;
    }

    private double getReactionDenominator() {
        double stockBased = Math.max(1.0, getBaselineStock()) * stockpileDenomMult;
        double cap = referenceQuantity * denomMaxReferenceMult;

        return Math.max(referenceQuantity, Math.min(stockBased, cap));
    }

    private double getBaselineStock() {
        if (commodity == null) {
            return referenceQuantity;
        }

        return Math.max(1.0, commodity.getStockpile());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
