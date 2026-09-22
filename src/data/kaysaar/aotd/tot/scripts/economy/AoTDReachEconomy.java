package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import com.fs.starfarer.campaign.econ.reach.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AoTDReachEconomy extends ReachEconomy {
    /** Enable temporarily when measuring UI updates. No timers/logging when false. */
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
        final long adminDone = profile ? System.nanoTime() : 0L;

        // Full input scope is retained: an open market still depends on other markets.
        drain(openMarket == null
                ? new AoTdMainWorkTask2(markets, this, params)
                : new AoTdMainWorkTask2(markets, this, params, openMarket));
        final long mainDone = profile ? System.nanoTime() : 0L;

        // The player-only entry point intentionally retains its all-market second pass.
        Economy economy = (Economy) Global.getSector().getEconomy();
        drain(openMarket == null
                ? new AoTDUpdateMarketAgainTask(economy)
                : new AoTDUpdateMarketAgainTask(economy, openMarket));
        final long secondDone = profile ? System.nanoTime() : 0L;

        if (params.withImmigration) {
            drain(new ImmigrationTask(markets, this, !params.forceNonUIStep));
        }
        final long immigrationDone = profile ? System.nanoTime() : 0L;

        AoTDFinishEconomyUpdateTask finish = new AoTDFinishEconomyUpdateTask(
                (Economy) Global.getSector().getEconomy());
        if (playerOnly) finish.doForPlayerOnly();
        else finish.runSynchronously();

        if (profile) {
            long finished = System.nanoTime();
            Global.getLogger(AoTDReachEconomy.class).info(String.format(Locale.ROOT,
                    "AoTD nextStep mode=%s markets=%d total=%.3f ms " +
                    "snapshot/admin=%.3f main=%.3f second=%.3f immigration=%.3f finish=%.3f",
                    playerOnly ? "player" : openMarket == null ? "all" : "open-market",
                    markets.size(), (finished - started) / 1_000_000d,
                    (adminDone - started) / 1_000_000d,
                    (mainDone - adminDone) / 1_000_000d,
                    (secondDone - mainDone) / 1_000_000d,
                    (immigrationDone - secondDone) / 1_000_000d,
                    (finished - immigrationDone) / 1_000_000d));
        }
    }

    private static void refreshAdmin(MarketAPI market) {
        PersonAPI admin = market.getAdmin();
        admin.getStats().refreshCharacterStatsEffects();
        admin.getStats().refreshGovernedOutpostEffects(market);
    }

    private static void drain(MultiFrameTask task) {
        while (!task.isDone()) task.doNextBatch();
    }

    @Override
    public void addMarket(MarketAPI marketAPI) {
        super.addMarket(marketAPI);
        // Here swap all commodities into AoTD commodity data.
    }
}
