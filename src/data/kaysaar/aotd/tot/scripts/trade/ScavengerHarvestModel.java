package data.kaysaar.aotd.tot.scripts.trade;

import com.fs.starfarer.api.Global;

import java.util.Random;

/** Pure monthly harvest sampling. Quantities use raw commodity units. */
public final class ScavengerHarvestModel {
    private ScavengerHarvestModel() {}

    public static final class Range {
        public final int min;
        public final int max;

        private Range(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    /** Fractions are relative to unmet sector demand, not total production. */
    public static Range range(int demand, int production, double minFraction,
                              double maxFraction, boolean allowOverflow) {
        long shortage = Math.max(0L, (long) demand - production);
        double a = finiteNonnegative(minFraction, 0d);
        double b = finiteNonnegative(maxFraction, allowOverflow ? 1.25d : 1d);
        if (!allowOverflow) {
            a = Math.min(a, 1d);
            b = Math.min(b, 1d);
        }
        a = Math.min(a, b);
        // Consumers add production + harvest using ints. Keep that total representable.
        int capacity = Integer.MAX_VALUE - Math.max(0, production);
        int min = units(shortage * a, capacity);
        int max = units(shortage * b, capacity);
        return new Range(min, max);
    }

    /** A failed expedition can yield zero even when the ordinary range starts above zero. */
    public static int sample(Range range, double failureChance, double maximumChance, long seed) {
        if (range.max <= 0) return 0;
        double fail = probability(failureChance, 0.02d);
        double maximum = Math.min(probability(maximumChance, 0.02d), 1d - fail);
        Random random = new Random(seed);
        double outcome = random.nextDouble();
        if (outcome < fail) return 0;
        if (outcome >= 1d - maximum) return range.max;

        // Symmetric triangular distribution: density is highest at the midpoint.
        double u = random.nextDouble();
        double fraction = u < 0.5d ? Math.sqrt(u / 2d) : 1d - Math.sqrt((1d - u) / 2d);
        long amount = Math.round(range.min + ((long) range.max - range.min) * fraction);
        return (int) Math.max(range.min, Math.min(range.max, amount));
    }

    public static long monthlySeed(String sectorSeed, int cycle, int month, String commodityId) {
        String key = sectorSeed + "|" + cycle + "|" + month + "|" + commodityId + "|guild-harvest-v1";
        long hash = Global.getSector().getSeedString().hashCode();
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static int units(double amount, int capacity) {
        return (int) Math.min(capacity, Math.floor(amount));
    }

    private static double finiteNonnegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0d ? value : fallback;
    }

    private static double probability(double value, double fallback) {
        return Math.min(1d, finiteNonnegative(value, fallback));
    }
}
