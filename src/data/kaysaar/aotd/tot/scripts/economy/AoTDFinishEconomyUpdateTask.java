package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.FinishEconomyUpdateTask;

import data.kaysaar.aotd.tot.scripts.trade.AoTDIsolationUtils;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class AoTDFinishEconomyUpdateTask extends FinishEconomyUpdateTask {
    private final Economy economy;
    private ArrayList<Future<?>> internalTradeFutures = new ArrayList<>();

    private boolean done = false;
    private transient boolean synchronousWait;
    private transient boolean profile;
    private transient boolean profileLogged;
    private transient long profileStarted;
    private transient long activeNanos;
    private transient long submitNanos;
    private transient long predictionsNanos;
    private transient long listenersNanos;
    private transient int factions;
    private transient int workers;
    private transient AtomicLong workerNanos;
    private transient AtomicLong maxWorkerNanos;

    /** UI caller: join workers instead of repeatedly polling isDone in a tight loop. */
    public void runSynchronously() {
        synchronousWait = true;
        try {
            while (!isDone()) doNextBatch();
        } finally {
            synchronousWait = false;
        }
    }

    private void joinInternalTradeWorkers() {
        for (Future<?> future : internalTradeFutures) {
            if (future == null) continue;
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("AoTD UI economy update interrupted", ex);
            } catch (ExecutionException ex) {
                throw new IllegalStateException("AoTD internal trade worker failed", ex.getCause());
            }
        }
    }

    private boolean workersSubmitted = false;
    private boolean workersFinished = false;

    public AoTDFinishEconomyUpdateTask(Economy economy) {
        super(economy);
        this.economy = economy;
        profile = AoTDReachEconomy.PROFILE_NEXT_STEP;
        if (profile) {
            profileStarted = System.nanoTime();
            workerNanos = new AtomicLong();
            maxWorkerNanos = new AtomicLong();
        }
    }

    public void doForPlayerOnly(){
        long started = profile ? System.nanoTime() : 0L;
        AoTDTradeManager.getInstance().getPlayerManager().computeInternalTrade(false);
        if (profile) {
            workerNanos.addAndGet(System.nanoTime() - started);
            factions = 1;
        }
        refreshPlayerContractPredictionsOnMainThread();
        notifyEconomyListeners();
        if (profile) {
            activeNanos += System.nanoTime() - started;
            logTaskProfile("player");
        }
    }

    @Override
    public void doNextBatch() {
        if (isDone()) return;
        long started = profile ? System.nanoTime() : 0L;
        try {
            if (!AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION) {
                doSequential();
            } else {
                doMultithreaded();
            }
        } finally {
            if (profile) {
                activeNanos += System.nanoTime() - started;
                if (done) logTaskProfile(AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION ? "workers" : "sequential");
            }
        }
    }

    private void doSequential() {
        final Map<String, MarketAPI> marketIndex = AoTDFactionTradeData.snapshotMarketsById();
        for (AoTDFactionTradeData value : getFactionTradeDataSnapshot()) {
            if (value == null) continue;
            if (profile) factions++;
            long started = profile ? System.nanoTime() : 0L;
            value.computeInternalTrade(false, marketIndex);
            if (profile) workerNanos.addAndGet(System.nanoTime() - started);
        }
        refreshPlayerContractPredictionsOnMainThread();
        notifyEconomyListeners();
        done = true;
    }

    private void doMultithreaded() {
        if(internalTradeFutures == null) internalTradeFutures = new ArrayList<>();
        if (!workersSubmitted) {
            long started = profile ? System.nanoTime() : 0L;
            submitInternalTradeWorkers();
            if (profile) submitNanos += System.nanoTime() - started;
            workersSubmitted = true;
            return;
        }

        if (!workersFinished) {
            if (synchronousWait) joinInternalTradeWorkers();
            if (!areInternalTradeWorkersDone()) return;

            workersFinished = true;
            refreshPlayerContractPredictionsOnMainThread();
            notifyEconomyListeners();
            done = true;
        }
    }

    private void submitInternalTradeWorkers() {
        internalTradeFutures.clear();
        final Map<String, MarketAPI> marketIndex = AoTDFactionTradeData.snapshotMarketsById();

        for (AoTDFactionTradeData value : getFactionTradeDataSnapshot()) {
            if (value == null) continue;
            if (profile) factions++;
            final Future<?> future = AoTDWorkerManager.submit("AoTD internal trade", () -> {
                AoTDWorkerManager.checkpoint();
                long started = profile ? System.nanoTime() : 0L;
                try {
                    value.computeInternalTrade(false, marketIndex);
                } finally {
                    if (profile && workerNanos != null) {
                        long duration = System.nanoTime() - started;
                        workerNanos.addAndGet(duration);
                        maxWorkerNanos.accumulateAndGet(duration, Math::max);
                    }
                }
                AoTDWorkerManager.checkpoint();
            });
            internalTradeFutures.add(future);
            if (profile) workers++;
        }

        if (internalTradeFutures.isEmpty()) {
            workersFinished = true;
            refreshPlayerContractPredictionsOnMainThread();
            notifyEconomyListeners();
            done = true;
        }
    }

    private boolean areInternalTradeWorkersDone() {
        for (Future<?> future : internalTradeFutures) {
            if (future != null && !future.isDone()) return false;
        }
        return true;
    }

    private ArrayList<AoTDFactionTradeData> getFactionTradeDataSnapshot() {
        return new ArrayList<>(AoTDTradeManager.getInstance().getAllFactionTradeData().values());
    }

    private void refreshPlayerContractPredictionsOnMainThread() {
        long started = profile ? System.nanoTime() : 0L;
        final Map<String, MarketAPI> markets = AoTDFactionTradeData.snapshotMarketsById();
        AoTDIsolationUtils.prepareMarkets(markets.values());
        AoTDIsolationUtils.computeLocalTrade(markets);
        AoTDIsolationUtils.updateAvailability(markets.values());
        final AoTDFactionTradeData playerTrade = AoTDTradeManager.getInstance().getFactionTradeData(Factions.PLAYER);
        if (playerTrade != null) playerTrade.refreshContractPredictionsIfPlayerFaction();
        if (profile) predictionsNanos += System.nanoTime() - started;
    }

    private void notifyEconomyListeners() {
        long started = profile ? System.nanoTime() : 0L;
        final List<EconomyAPI.EconomyUpdateListener> listeners = new ArrayList<>(economy.getUpdateListeners());
        for (EconomyUpdateListener listener : listeners) {
            if (listener == null || listener.isEconomyListenerExpired()) {
                economy.removeUpdateListener(listener);
            } else {
                listener.economyUpdated();
            }
        }
        if (profile) listenersNanos += System.nanoTime() - started;
    }

    private void logTaskProfile(String mode) {
        if (!profile || profileLogged) return;
        profileLogged = true;
        Global.getLogger(AoTDFinishEconomyUpdateTask.class).info(String.format(Locale.ROOT,
                "AoTD finishTask mode=%s factions=%d workers=%d multithread=%s " +
                        "active=%.3fms elapsedWall=%.3fms submit=%.3fms workerCpuSum=%.3fms " +
                        "slowestWorker=%.3fms predictions=%.3fms listeners=%.3fms",
                mode, factions, workers, AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION,
                activeNanos / 1_000_000d, (System.nanoTime() - profileStarted) / 1_000_000d,
                submitNanos / 1_000_000d, workerNanos.get() / 1_000_000d,
                maxWorkerNanos.get() / 1_000_000d, predictionsNanos / 1_000_000d,
                listenersNanos / 1_000_000d));
    }

    @Override
    public boolean isDone() {
        return done;
    }
}
