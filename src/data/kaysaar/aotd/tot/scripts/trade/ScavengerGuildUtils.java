
package data.kaysaar.aotd.tot.scripts.trade;

import com.fs.starfarer.api.Global;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;

import java.util.HashMap;
import java.util.Map;

public final class ScavengerGuildUtils {

    private static final Map<String, Float> THRESHOLD_BY_COMMODITY = new HashMap<>();
    private static float DEFAULT_THRESHOLD = 0.10f;

    /** Optional global default, if you want to tune the baseline. */
    public static void setDefaultThreshold(float threshold) {
        DEFAULT_THRESHOLD = threshold;
    }

    /** Per-commodity override (e.g. food=0.05, heavy_machinery=0.15). */
    public static void setThreshold(String commodityId, float threshold) {
        THRESHOLD_BY_COMMODITY.put(commodityId, threshold);
    }

    public static float getThreshold(String commodityId) {
        return THRESHOLD_BY_COMMODITY.getOrDefault(commodityId, DEFAULT_THRESHOLD);
    }

    public static boolean isHarvestMode() {
        return settingBoolean("aotd_scavenger_guild_harvest_mode", false);
    }

    public static boolean isOverflowAllowed() {
        return isHarvestMode() && settingBoolean("aotd_scavenger_guild_harvest_allow_overflow", false);
    }

    public static ScavengerHarvestModel.Range getHarvestRange(int demand, int production) {
        boolean overflow = isOverflowAllowed();
        double min = settingFloat("aotd_scavenger_guild_harvest_min_fraction", 0f);
        double max = overflow
                ? settingFloat("aotd_scavenger_guild_harvest_overflow_max_fraction", 1.25f)
                : settingFloat("aotd_scavenger_guild_harvest_max_fraction", 1f);
        return ScavengerHarvestModel.range(demand, production, min, max, overflow);
    }

    /** Eligibility is distinct from yield: an activated guild can have a failed harvest. */
    public static boolean isEligible(String commodityId, int totalDemand, int totalProduction) {
        if (Global.getSettings().getCommoditySpec(commodityId).hasTag(AoTDTradeTags.IGNORE_SCAVENGERS)) return false;
        if (totalDemand <= 0 || totalProduction <= 0) return false;
        return totalDemand > totalProduction * (1.0 + getThreshold(commodityId));
    }

    private static boolean settingBoolean(String id, boolean fallback) {
        try {
            return Global.getSettings().getBoolean(id);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static float settingFloat(String id, float fallback) {
        try {
            float value = Global.getSettings().getFloat(id);
            return Float.isFinite(value) ? value : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    // ----------------------------
    // Core coverage logic
    // ----------------------------

    /** True if scavengers should cover some amount, given totals. */
    public static boolean doesCoverCommodity(String commodityId, int totalDemand, int totalProduction) {
        return getCoveredAmount(commodityId, totalDemand, totalProduction) > 0;
    }

    /** Covered amount using already-known totals. */
    public static int getCoveredAmount(String commodityId, int totalDemand, int totalProduction) {
        if (!isEligible(commodityId, totalDemand, totalProduction)) return 0;
        if (!isHarvestMode()) {
            double allowedDemand = totalProduction * (1.0 + getThreshold(commodityId));
            return (int) Math.ceil(totalDemand - allowedDemand);
        }
        long seed = ScavengerHarvestModel.monthlySeed(Global.getSector().getSeedString(),
                Global.getSector().getClock().getCycle(), Global.getSector().getClock().getMonth(), commodityId);
        return ScavengerHarvestModel.sample(getHarvestRange(totalDemand, totalProduction),
                settingFloat("aotd_scavenger_guild_harvest_failure_chance", 0.02f),
                settingFloat("aotd_scavenger_guild_harvest_maximum_chance", 0.02f), seed);
    }

    /** Covered amount using GLOBAL sector totals (auto fetch). */
    public static int getCoveredAmountFromSector(String commodityId) {
        int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int dem  = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return getCoveredAmount(commodityId, dem, prod);
    }

    /** True if scavengers should cover some amount using GLOBAL sector totals. */
    public static boolean doesCoverCommodityFromSector(String commodityId) {
        return getCoveredAmountFromSector(commodityId) > 0;
    }

    // ----------------------------
    // Percentage / ratio helpers
    // ----------------------------

    /** Returns demand/production ratio (e.g. 1.10 = demand is 10% higher than production). */
    public static float getDemandToProductionRatio(int totalDemand, int totalProduction) {
        if (totalProduction <= 0) return 0f;
        return (float) totalDemand / (float) totalProduction;
    }

    /** Returns percent demand exceeds production: e.g. 0.12 => 12% over. */
    public static float getOverageRatio(int totalDemand, int totalProduction) {
        if (totalProduction <= 0) return 0f;
        return ((float) totalDemand / (float) totalProduction) - 1f;
    }

    /** Returns percent demand exceeds production, as 0..100 (e.g. 12.0f). */
    public static float getOveragePercent(int totalDemand, int totalProduction) {
        return getOverageRatio(totalDemand, totalProduction) * 100f;
    }

    /** Convenience: sector demand/production ratio for a commodity. */
    public static float getSectorDemandToProductionRatio(String commodityId) {
        int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int dem  = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return getDemandToProductionRatio(dem, prod);
    }

    /** Convenience: sector overage percent for a commodity. */
    public static float getSectorOveragePercent(String commodityId) {
        int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int dem  = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return getOveragePercent(dem, prod);
    }
}
