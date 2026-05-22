package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.FinishEconomyUpdateTask;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class AoTDFinishEconomyUpdateTask extends FinishEconomyUpdateTask {
    private final Economy economy;

    private boolean done = false;

    private boolean workersSubmitted = false;
    private boolean workersFinished = false;
    private final ArrayList<Future<?>> internalTradeFutures = new ArrayList<>();

    public AoTDFinishEconomyUpdateTask(Economy economy) {
        super(economy);
        this.economy = economy;
    }

    @Override
    public void doNextBatch() {
        if (isDone()) {
            return;
        }

        if (!AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION) {
            doSequential();
            return;
        }

        doMultithreaded();
    }

    private void doSequential() {
        for (AoTDFactionTradeData value : getFactionTradeDataSnapshot()) {
            if (value == null) continue;
            value.computeInternalTrade(true);
        }

        notifyEconomyListeners();
        done = true;
    }

    private void doMultithreaded() {
        if (!workersSubmitted) {
            submitInternalTradeWorkers();
            workersSubmitted = true;
            return;
        }

        if (!workersFinished) {
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

        for (AoTDFactionTradeData value : getFactionTradeDataSnapshot()) {
            if (value == null) continue;

            Future<?> future = AoTDWorkerManager.submit("AoTD internal trade", () -> {
                AoTDWorkerManager.checkpoint();
                value.computeInternalTrade(false);
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
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        AoTDFactionTradeData playerTrade = AoTDTradeManager.getInstance().getFactionTradeData(playerFactionId);

        if (playerTrade != null) {
            playerTrade.refreshContractPredictionsIfPlayerFaction();
        }
    }

    private void notifyEconomyListeners() {
        List<EconomyAPI.EconomyUpdateListener> listeners =
                new ArrayList<>(this.economy.getUpdateListeners());

        for (EconomyAPI.EconomyUpdateListener listener : listeners) {
            if (listener == null) continue;

            if (listener.isEconomyListenerExpired()) {
                Global.getSector().getEconomy().removeUpdateListener(listener);
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
