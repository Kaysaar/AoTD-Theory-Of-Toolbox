package data.kaysaar.aotd.tot.scripts.commoditydata;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Price calculator for trade with tunable parameters. The instantaneous price p(s)
 * depends only on the current stockpile s and the demand d.
 * <p>
 * The price function is piecewise‑defined, continuous, strictly decreasing,
 * symmetric and positive for all real s. Three zones are used:
 * <ul>
 *   <li><b>Deficit zone</b> (s ≤ deficitRatio · d) : exponential growth
 *   as stock becomes more negative.</li>
 *   <li><b>Normal zone</b> (deficitRatio·d < s < excessRatio·d) : power law
 *   that passes through equilibrium p(d)=basePrice.</li>
 *   <li><b>Excess zone</b> (s ≥ excessRatio·d) : power law with a smaller
 *   exponent, making the price fall slowly towards zero.</li>
 * </ul>
 * Buying an amount increases the stockpile, selling decreases it.
 * The average unit price for a transaction is the integral of p(s) over the
 * stock change divided by the absolute amount.  Because p(s) is decreasing,
 * buying always yields an average price below the starting price (and below
 * base if starting below demand), selling yields an average above.
 * <p>
 */
public class PriceCalculator {
    private PriceCalculator() {}

    static final float INHERENT_DEMAND = 4f;

    /** Shift added to stock and demand to avoid poles (fraction of demand). */
    static final double SHIFT_FRACTION = 0.002;

    /** Stock/demand ratio below which the deficit zone starts. */
    static final double DEFICIT_RATIO = 0.5;

    /** Stock/demand ratio above which the excess zone starts. */
    static final double EXCESS_RATIO = 1.5;

    /** Exponent for the power law in the normal zone. */
    static final double EXP_NORMAL = 1.0;

    /** Exponent for the power law in the excess zone (must be < EXP_NORMAL
     *  to keep price decreasing). */
    static final double EXP_EXCESS = 0.5;

    /** Steepness of the exponential price increase in the deficit zone. */
    static final double LAMBDA = 0.01;

    static final float PRICE_MULT_FLOOR = 0.1f;
    static final float PRICE_MULT_CEILING = 10.0f;
    static final double MAX_MULT = 1000.0;
    static final double MAX_EXP_ARG = 6.0;

    /**
     * Computes the per‑unit price for a transaction of {@code amount} units. This function is symmetric and directionless.
     *
     * @param type Direction of the trade
     * @param amount Number of units to transact (positive)
     * @param stored Current stockpile (may be negative)
     * @param basePrice Base price when stockpile equals demand
     * @param preferred The demand value.
     * @return the average unit price for this transaction.
     */
    public static final float getUnitPrice(TransactionDirection type, long amount, double stored, float basePrice, float preferred) {
        if (amount < 0l) throw new IllegalArgumentException("Amount cannot be negative: " + amount);

        final float d = Math.max(preferred, INHERENT_DEMAND);

        if (amount == 0l || type == TransactionDirection.NEUTRAL) {
            return (float) Math.max(1.0, basePrice * clampMult(p(stored, d)));
        }

        final double deltaStock = (type == TransactionDirection.ENTITY_BUYING) ? amount : -amount;
        final double newStock = stored + deltaStock;

        final double lower = Math.min(stored, newStock);
        final double upper = Math.max(stored, newStock);

        // integral of the multiplier function over the stock change
        final double integralMult = integrate(lower, upper, d);

        final double avgMult = integralMult / Math.abs(deltaStock);
        final double clampedMult = clampMult(avgMult);

        return (float) Math.max(1f, basePrice * clampedMult);
    }

    private static final double clampMult(double mult) {
        return Math.max(PRICE_MULT_FLOOR, Math.min(PRICE_MULT_CEILING, mult));
    }

    /** Piecewise multiplier function m(s). */
    private static final double p(double stock, float demand) {
        final double deficitBound = DEFICIT_RATIO * demand;
        final double excessBound = EXCESS_RATIO * demand;

        final double raw;
        if (stock <= deficitBound) {
            final double mAtBoundary = normalMultiplier(deficitBound, demand);
            final double arg = -LAMBDA * (stock - deficitBound);

            raw = mAtBoundary * Math.exp(arg > MAX_EXP_ARG ? MAX_EXP_ARG : arg);
        } else if (stock >= excessBound) {
            raw = excessMultiplier(stock, demand);
        } else {
            raw = normalMultiplier(stock, demand);
        }

        return Math.min(raw, MAX_MULT);
    }

    /** Normal zone multiplier: m(s) = ((d+shift)/(s+shift))^EXP_NORMAL */
    private static final double normalMultiplier(double s, float demand) {
        final double shift = SHIFT_FRACTION * demand;
        final double ratio = (demand + shift) / (s + shift);
        return Math.pow(ratio, EXP_NORMAL);
    }

    /** Excess zone multiplier, continuous with normal at excessBound. */
    private static final double excessMultiplier(double s, float demand) {
        final double excessBound = EXCESS_RATIO * demand;
        final double shift = SHIFT_FRACTION * demand;

        final double ratio = (excessBound + shift) / (s + shift);
        return normalMultiplier(excessBound, demand) * Math.pow(ratio, EXP_EXCESS);
    }

    /** Integral of the multiplier function over [a, b]. */
    private static final double integrate(double a, double b, float demand) {
        if (a == b) return 0.0;

        final double deficitBound = DEFICIT_RATIO * demand;
        final double excessBound = EXCESS_RATIO * demand;

        final ArrayList<Double> points = new ArrayList<>(4);
        points.add(a);
        points.add(b);
        if (deficitBound > a && deficitBound < b) points.add(deficitBound);
        if (excessBound  > a && excessBound  < b) points.add(excessBound);
        Collections.sort(points);

        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            final double x0 = points.get(i);
            final double x1 = points.get(i + 1);
            if (x0 == x1) continue;

            final double midpoint = (x0 + x1) * 0.5;
            final IntegralRegion zone = midpoint <= deficitBound ? IntegralRegion.DEFICIT :
                    midpoint >= excessBound ? IntegralRegion.EXCESS : IntegralRegion.NORMAL;

            total += integrateMultiplierBranch(x0, x1, zone, demand);
        }
        return total;
    }

    /** Integral of the multiplier over [a, b] assuming one zone. */
    private static final double integrateMultiplierBranch(double a, double b, IntegralRegion zone, float demand) {
        final double shift = SHIFT_FRACTION * demand;
        switch (zone) {
            case NORMAL: {
                // ∫ (d+shift)^EXP_NORMAL * (s+shift)^{-EXP_NORMAL} ds
                final double K = Math.pow(demand + shift, EXP_NORMAL);
                return powerIntegral(a, b, shift, EXP_NORMAL, K);
            }
            case EXCESS: {
                final double excessBound = EXCESS_RATIO * demand;
                final double mAtBoundary = normalMultiplier(excessBound, demand);
                final double K = mAtBoundary * Math.pow(excessBound + shift, EXP_EXCESS);
                return powerIntegral(a, b, shift, EXP_EXCESS, K);
            }
            case DEFICIT: {
                final double deficitBound = DEFICIT_RATIO * demand;
                final double mDef = normalMultiplier(deficitBound, demand);
                final double satStock = deficitBound - Math.log(MAX_MULT / mDef) / LAMBDA;

                double total = 0.0;

                // Capped region
                if (a < satStock) {
                    double end = Math.min(b, satStock);
                    total += MAX_MULT * (end - a);
                }

                // Exponential region (safe arguments)
                double expStart = Math.max(a, satStock);
                if (expStart < b) {
                    double argStart = -LAMBDA * (expStart - deficitBound);
                    double argEnd = -LAMBDA * (b - deficitBound);
                    total += (mDef / LAMBDA) * (Math.exp(argStart) - Math.exp(argEnd));
                }

                return total;
            }
            default:
                throw new IllegalArgumentException("Unhandled: " + zone.name());
        }
    }

    /** ∫_a^b K * (s + shift)^{-exp} ds for exp != 1. */
    private static final double powerIntegral(double a, double b, double shift, double exp, double K) {
        if (exp == 1.0) {
            return K * Math.log((b + shift) / (a + shift));
        } else {
            final double powA = Math.pow(a + shift, 1.0 - exp);
            final double powB = Math.pow(b + shift, 1.0 - exp);
            return K / (1.0 - exp) * (powB - powA);
        }
    }

    public enum TransactionDirection {
        /** Buying from the player. Stock increases. */
        ENTITY_BUYING,
        /** Selling to the player. Stock decreases. */
        ENTITY_SELLING,
        /** Internal baseline */
        NEUTRAL 
    }

    public enum IntegralRegion {
        DEFICIT,
        EXCESS,
        NORMAL
    }
}