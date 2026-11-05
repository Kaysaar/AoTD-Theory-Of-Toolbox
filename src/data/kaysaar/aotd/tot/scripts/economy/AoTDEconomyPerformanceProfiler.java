package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AoTDEconomyPerformanceProfiler {
    public static int ROLLING_WINDOW = 10;
    public static final String[] STAGES = {"main", "reapply", "immigration", "internalTrade", "finish", "postStep"};

    public static class Workload {
        public int markets;
        public int playerMarkets;
        public int industries;
        public int commodities;
        public int demandClasses;
        public int econGroups;
        public int listeners;

        public Workload(List<MarketAPI> markets) {
            this.markets = markets.size();
            Set<String> groups = new HashSet<>();
            Set<String> classes = new HashSet<>();
            for (MarketAPI market : markets) {
                if (market == null) continue;
                if (market.isPlayerOwned() || market.getFaction().isPlayerFaction()) playerMarkets++;
                industries += market.getIndustries().size();
                if (market.getEconGroup() != null) groups.add(market.getEconGroup());
            }
            for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
                if (spec == null || spec.hasTag("nonecon")) continue;
                commodities++;
                if (spec.getDemandClass() != null) classes.add(spec.getDemandClass());
            }
            econGroups = groups.size();
            demandClasses = classes.size();
            listeners = Global.getSector().getEconomy().getUpdateListeners().size();
        }

        public long getMarketCommodityPairs() {
            return (long) markets * commodities;
        }
    }

    public static class StepProfile {
        private final long started = System.nanoTime();
        private final long[] nanos = new long[STAGES.length];
        private final int[] calls = new int[STAGES.length];
        private final Workload workload;
        private final String mode;
        private final int iteration;
        private final MainWorkTask.EconWorkParams params;
        private final boolean multithreaded;
        private int frames;

        public StepProfile(String mode, int iteration, List<MarketAPI> markets,
                           MainWorkTask.EconWorkParams params) {
            this.mode = mode;
            this.iteration = iteration;
            this.params = params;
            this.workload = new Workload(markets);
            this.multithreaded = AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION;
        }

        public void add(int stage, long duration) {
            nanos[stage] += duration;
            calls[stage]++;
        }

        public void nextFrame() {
            frames++;
        }

        public double getActiveMs() {
            long total = 0L;
            for (long value : nanos) total += value;
            return total / 1_000_000d;
        }

        public double getWallMs() {
            return (System.nanoTime() - started) / 1_000_000d;
        }

        public void log() {
            long pairs = workload.getMarketCommodityPairs();
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < STAGES.length; i++) {
                detail.append(String.format(Locale.ROOT, " %s=%.3fms/%dcalls",
                        STAGES[i], nanos[i] / 1_000_000d, calls[i]));
            }
            Global.getLogger(AoTDEconomyPerformanceProfiler.class).info(String.format(Locale.ROOT,
                    "AoTD reachStep mode=%s iter=%d markets=%d playerMarkets=%d industries=%d " +
                            "commodities=%d demandClasses=%d econGroups=%d listeners=%d " +
                            "marketCommodityPairs=%d multithread=%s workerThreads=%d income=%s stockpiles=%s paramsImmigration=%s scheduledImmigration=true " +
                            "frames=%d active=%.3fms elapsedWall=%.3fms msPer1000Pairs=%.3f%s",
                    mode, iteration, workload.markets, workload.playerMarkets, workload.industries,
                    workload.commodities, workload.demandClasses, workload.econGroups, workload.listeners,
                    pairs, multithreaded, Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1)),
                    params.withIncomeAndUpkeep, params.withStockpileUpdate,
                    params.withImmigration, frames, getActiveMs(), getWallMs(),
                    pairs == 0 ? 0d : getActiveMs() * 1000d / pairs, detail));
        }
    }

    public static class RollingProfile {
        private int samples;
        private double totalActive;
        private double maxActive;
        private double totalPairs;
        private final double[] phaseTotals = new double[STAGES.length];

        public void add(StepProfile profile) {
            samples++;
            totalActive += profile.getActiveMs();
            maxActive = Math.max(maxActive, profile.getActiveMs());
            totalPairs += profile.workload.getMarketCommodityPairs();
            for (int i = 0; i < STAGES.length; i++) {
                phaseTotals[i] += profile.nanos[i] / 1_000_000d;
            }
            if (samples < Math.max(1, ROLLING_WINDOW)) return;
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < STAGES.length; i++) {
                detail.append(String.format(Locale.ROOT, " avg_%s=%.3fms", STAGES[i], phaseTotals[i] / samples));
            }
            Global.getLogger(AoTDEconomyPerformanceProfiler.class).info(String.format(Locale.ROOT,
                    "AoTD reachStep rolling samples=%d avgActive=%.3fms maxActive=%.3fms " +
                            "avgMarketCommodityPairs=%.1f msPer1000Pairs=%.3f%s",
                    samples, totalActive / samples, maxActive, totalPairs / samples,
                    totalPairs == 0d ? 0d : totalActive * 1000d / totalPairs, detail));
            samples = 0;
            totalActive = 0d;
            maxActive = 0d;
            totalPairs = 0d;
            Arrays.fill(phaseTotals, 0d);
        }
    }

    public static void logSynchronous(String mode, List<MarketAPI> markets,
                                      MainWorkTask.EconWorkParams params, long[] nanos, int[] calls,
                                      int updatedMarkets, long wallNanos) {
        Workload workload = new Workload(markets);
        long activeNanos = 0;
        StringBuilder detail = new StringBuilder();
        String[] names = {"admin", "main", "reapply", "immigration", "finish"};
        for (int i = 0; i < names.length; i++) {
            activeNanos += nanos[i];
            detail.append(String.format(Locale.ROOT, " %s=%.3fms/%dcalls", names[i], nanos[i] / 1_000_000d, calls[i]));
        }
        long pairs = workload.getMarketCommodityPairs();
        Global.getLogger(AoTDEconomyPerformanceProfiler.class).info(String.format(Locale.ROOT,
                "AoTD reachStep mode=%s inputMarkets=%d updatedMarkets=%d playerMarkets=%d industries=%d commodities=%d " +
                        "demandClasses=%d econGroups=%d listeners=%d marketCommodityPairs=%d " +
                        "multithread=%s workerThreads=%d income=%s stockpiles=%s immigration=%s forceNonUI=%s " +
                        "active=%.3fms wall=%.3fms msPer1000Pairs=%.3f%s",
                mode, workload.markets, updatedMarkets, workload.playerMarkets, workload.industries, workload.commodities,
                workload.demandClasses, workload.econGroups, workload.listeners, pairs,
                AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION,
                Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1)),
                params.withIncomeAndUpkeep,
                params.withStockpileUpdate, params.withImmigration, params.forceNonUIStep,
                activeNanos / 1_000_000d, wallNanos / 1_000_000d,
                pairs == 0 ? 0d : activeNanos / 1000d / pairs, detail));
    }

    public static void logMonthEnd(int prevMonth, int markets, int eligibleMarkets,
                                   long[] phaseNanos, long totalNanos) {
        String[] names = {"history", "allocation", "contracts", "externalIndex", "externalTrade", "writeback", "events"};
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            detail.append(String.format(Locale.ROOT, " %s=%.3fms", names[i], phaseNanos[i] / 1_000_000d));
        }
        Global.getLogger(AoTDEconomyPerformanceProfiler.class).info(String.format(Locale.ROOT,
                "AoTD monthEnd prevMonth=%d markets=%d eligibleExternalMarkets=%d total=%.3fms " +
                        "msPerMarket=%.3f%s",
                prevMonth, markets, eligibleMarkets, totalNanos / 1_000_000d,
                markets == 0 ? 0d : totalNanos / 1_000_000d / markets, detail));
    }
}
