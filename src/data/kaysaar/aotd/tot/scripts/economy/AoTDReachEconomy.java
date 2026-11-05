package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import com.fs.starfarer.campaign.econ.reach.*;

import java.util.ArrayList;
import java.util.List;

public class AoTDReachEconomy extends ReachEconomy {
    /** Enable when profiling campaign ticks and forced UI economy updates. */
    public static volatile boolean PROFILE_NEXT_STEP = true;

    public void nextStepForPlayer(MainWorkTask.EconWorkParams econWorkParams) {
        runStep(econWorkParams, true);
    }

    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        runStep(econWorkParams, false);
    }

    private void runStep(MainWorkTask.EconWorkParams params, boolean playerOnly) {
        final boolean profile = PROFILE_NEXT_STEP;
        final long started = profile ? System.nanoTime() : 0L;
        final long[] phaseNanos = profile ? new long[5] : null;
        final int[] phaseCalls = profile ? new int[5] : null;
        final List<MarketAPI> markets = new ArrayList<>();
        if (playerOnly) {
            for (MarketAPI market : getMarkets()) {
                if (market.isPlayerOwned() || market.getFaction().isPlayerFaction()) {
                    markets.add(market);
                }
            }
        } else {
            markets.addAll(getMarkets());
        }
        final MarketAPI openMarket = playerOnly ? null : Global.getSector().getCurrentlyOpenMarket();
        if (openMarket != null) {
            refreshAdmin(openMarket);
        } else {
            for (MarketAPI market : markets) refreshAdmin(market);
        }
        long previous = profile ? System.nanoTime() : 0L;
        if (profile) {
            phaseNanos[0] = previous - started;
            phaseCalls[0] = openMarket == null ? markets.size() : 1;
        }

        // Full input scope is retained: an open market still depends on other markets.
        phaseCallsUpdate(profile, phaseCalls, 1, openMarket == null
                ? new AoTdMainWorkTask2(markets, this, params)
                : new AoTdMainWorkTask2(markets, this, params, openMarket));
        if (profile) {
            long finished = System.nanoTime();
            phaseNanos[1] = finished - previous;
            previous = finished;
        }

        // The player-only entry point intentionally retains its all-market second pass.
        Economy economy = (Economy) Global.getSector().getEconomy();
        phaseCallsUpdate(profile, phaseCalls, 2, openMarket == null
                ? new AoTDUpdateMarketAgainTask(economy)
                : new AoTDUpdateMarketAgainTask(economy, openMarket));
        if (profile) {
            long finished = System.nanoTime();
            phaseNanos[2] = finished - previous;
            previous = finished;
        }

        if (params.withImmigration) {
            phaseCallsUpdate(profile, phaseCalls, 3, new ImmigrationTask(markets, this, !params.forceNonUIStep));
        }
        if (profile) {
            long finished = System.nanoTime();
            phaseNanos[3] = finished - previous;
            previous = finished;
        }

        AoTDFinishEconomyUpdateTask finish = new AoTDFinishEconomyUpdateTask(economy);
        if (playerOnly) {
            finish.doForPlayerOnly();
            if (profile) phaseCalls[4] = 1;
        } else {
            finish.runSynchronously();
            if (profile) phaseCalls[4] = 1;
        }
        if (profile) {
            long finished = System.nanoTime();
            phaseNanos[4] = finished - previous;
            AoTDEconomyPerformanceProfiler.logSynchronous(
                    playerOnly ? "player" : openMarket == null ? "all" : "open-market",
                    markets, params, phaseNanos, phaseCalls, openMarket == null ? markets.size() : 1,
                    finished - started);
        }
    }

    private static void refreshAdmin(MarketAPI market) {
        PersonAPI admin = market.getAdmin();
        admin.getStats().refreshCharacterStatsEffects();
        admin.getStats().refreshGovernedOutpostEffects(market);
    }

    private static void phaseCallsUpdate(boolean profile, int[] calls, int phase, MultiFrameTask task) {
        while (!task.isDone()) {
            task.doNextBatch();
            if (profile) calls[phase]++;
        }
    }

    @Override
    public void addMarket(MarketAPI marketAPI) {
        super.addMarket(marketAPI);
        // Here swap all commodities into AoTD commodity data.
    }
}
