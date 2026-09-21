package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.FinishEconomyUpdateTask;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class AoTDFinishEconomyUpdateTask extends FinishEconomyUpdateTask {
    private final Economy economy;
    private ArrayList<Future<?>> internalTradeFutures = new ArrayList<>();

    private boolean done = false;
    private transient boolean synchronousWait;

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
            } catch (java.util.concurrent.ExecutionException ex) {
                throw new IllegalStateException("AoTD internal trade worker failed", ex.getCause());
            }
        }
    }
    private boolean workersSubmitted = false;
    private boolean workersFinished = false;

    public AoTDFinishEconomyUpdateTask(Economy economy) {
        super(economy);
        this.economy = economy;
    }

    public void doForPlayerOnly(){
        AoTDTradeManager.getInstance().getPlayerManager().computeInternalTrade(false);
        refreshPlayerContractPredictionsOnMainThread();
        notifyEconomyListeners();

    }
    @Override
    public void doNextBatch() {
        if (isDone()) return;

        if (!AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION) {
            doSequential();
            return;
        }

        doMultithreaded();
    }

    private void doSequential() {
        final java.util.Map<String, com.fs.starfarer.api.campaign.econ.MarketAPI> marketIndex =
                AoTDFactionTradeData.snapshotMarketsById();
        for (AoTDFactionTradeData value : getFactionTradeDataSnapshot()) {
            if (value == null) continue;
            value.computeInternalTrade(true, marketIndex);
        }

        notifyEconomyListeners();
        done = true;
    }

    private void doMultithreaded() {
        if(internalTradeFutures == null) internalTradeFutures = new ArrayList<>();
        if (!workersSubmitted) {
            submitInternalTradeWorkers();
            workersSubmitted = true;
            return;
        }

        if (!workersFinished) {
            if (synchronousWait) joinInternalTradeWorkers();
            if (!areInternalTradeWorkersDone()) {
                return;
            }

            workersFinished = true;
            refreshPlayerContractPredictionsOnMainThread();
            notifyEconomyListeners();
            done = true;
        }
    }

    private void submitInternalTradeWorkers() {
        internalTradeFutures.clear();
        // Build once on the submitting thread, then share the immutable index.
        final java.util.Map<String, com.fs.starfarer.api.campaign.econ.MarketAPI> marketIndex =
                AoTDFactionTradeData.snapshotMarketsById();

        for (AoTDFactionTradeData value : getFactionTradeDataSnapshot()) {
            if (value == null) continue;

            final Future<?> future = AoTDWorkerManager.submit("AoTD internal trade", () -> {
                AoTDWorkerManager.checkpoint();
                value.computeInternalTrade(false, marketIndex);
                AoTDWorkerManager.checkpoint();
            });

            internalTradeFutures.add(future);
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
            if (future != null && !future.isDone()) {
                return false;
            }
        }

        return true;
    }

    private ArrayList<AoTDFactionTradeData> getFactionTradeDataSnapshot() {
        return new ArrayList<>(AoTDTradeManager.getInstance().getAllFactionTradeData().values());
    }

    private void refreshPlayerContractPredictionsOnMainThread() {
        final AoTDFactionTradeData playerTrade = AoTDTradeManager.getInstance().getFactionTradeData(Factions.PLAYER);

        if (playerTrade != null) playerTrade.refreshContractPredictionsIfPlayerFaction();
    }

    private void notifyEconomyListeners() {
        final List<EconomyAPI.EconomyUpdateListener> listeners =
                new ArrayList<>(economy.getUpdateListeners());
        for (EconomyUpdateListener listener : listeners) {
            if (listener == null || listener.isEconomyListenerExpired()) {
                economy.removeUpdateListener(listener);
            } else {
                listener.economyUpdated();
            }
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }
}