package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.UpdateMarketsAgainTask;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AoTDUpdateMarketAgainTask extends UpdateMarketsAgainTask {

    public static final String INITIAL_STAGE_DESC = "AoTD economy initial stage";
    private static final int REDUCTION = 10000;

    private List<MarketAPI> markets;
    private final MarketAPI singleMarket;

    private int marketIndex = 0;
    private boolean done = false;

    private transient boolean profile;
    private transient boolean profileLogged;
    private transient long profileStarted;
    private transient long activeNanos;
    private transient long conditionNanos;
    private transient long tradeDataNanos;
    private transient long industryNanos;
    private transient int marketsProcessed;
    private transient int industriesProcessed;
    private transient int pendingIndustries;

    public AoTDUpdateMarketAgainTask(Economy economy) {
        super(economy);
        this.markets = new ArrayList<>(economy.getMarkets());
        this.singleMarket = null;
        initProfile();
    }

    public AoTDUpdateMarketAgainTask(Economy economy, MarketAPI singleMarket) {
        super(economy);
        this.markets = null;
        this.singleMarket = singleMarket;
        initProfile();
    }

    private void initProfile() {
        profile = AoTDReachEconomy.PROFILE_NEXT_STEP;
        if (profile) profileStarted = System.nanoTime();
    }

    @Override
    public void doNextBatch() {
        if (isDone()) return;
        long batchStarted = profile ? System.nanoTime() : 0L;
        try {
            if (singleMarket != null) {
                processMarket(singleMarket);
                done = true;
                return;
            } else if (markets == null) {
                markets = Global.getSector().getEconomy().getMarketsCopy();
            }

            if (marketIndex >= markets.size()) {
                done = true;
                return;
            }

            processMarket(markets.get(marketIndex));
            marketIndex++;
            if (marketIndex >= markets.size()) done = true;
        } finally {
            if (profile) activeNanos += System.nanoTime() - batchStarted;
            if (profile && done && !profileLogged) {
                profileLogged = true;
                long total = System.nanoTime() - profileStarted;
                Global.getLogger(AoTDUpdateMarketAgainTask.class).info(String.format(Locale.ROOT,
                        "AoTD marketReapply mode=%s markets=%d industries=%d pending=%d " +
                                "active=%.3fms elapsedWall=%.3fms conditions=%.3fms tradeData=%.3fms industries=%.3fms msPerMarket=%.3f",
                        singleMarket == null ? "all" : "single", marketsProcessed, industriesProcessed, pendingIndustries,
                        activeNanos / 1_000_000d, total / 1_000_000d, conditionNanos / 1_000_000d,
                        tradeDataNanos / 1_000_000d, industryNanos / 1_000_000d,
                        marketsProcessed == 0 ? 0d : activeNanos / 1_000_000d / marketsProcessed));
            }
        }
    }

    private void processMarket(MarketAPI market) {
        long started = profile ? System.nanoTime() : 0L;
        market.reapplyConditions();
        if (profile) {
            conditionNanos += System.nanoTime() - started;
            started = System.nanoTime();
        }

        AoTDTradeManager.getInstance().addMarket(market);
        final AoTDIndustryData data = AoTDIndustryData.getInstance(market);
        data.checkForNewIndustries(market);
        if (profile) {
            tradeDataNanos += System.nanoTime() - started;
            started = System.nanoTime();
        }

        for (Industry industry : market.getIndustries()) {
            if (profile) industriesProcessed++;
            if (data.isPending(industry.getId())) {
                applyPendingIndustrySuppression(industry);
                if (profile) pendingIndustries++;
            } else {
                restoreIndustry(industry);
            }
        }
        if (profile) {
            industryNanos += System.nanoTime() - started;
            marketsProcessed++;
        }
    }

    public static void applyPendingIndustrySuppression(Industry industry) {
        industry.getSupplyBonusFromOther().modifyFlat(
                AoTDIndustryData.source, -getReduction(), INITIAL_STAGE_DESC
        );
        industry.getDemandReductionFromOther().modifyFlat(
                AoTDIndustryData.source, getReduction(), INITIAL_STAGE_DESC
        );
        industry.apply();
        industry.unapply();
    }

    public static void restoreIndustry(Industry industry) {
        industry.getSupplyBonusFromOther().unmodifyFlat(AoTDIndustryData.source);
        industry.getDemandReductionFromOther().unmodifyFlat(AoTDIndustryData.source);
        industry.unapply();
        industry.apply();
    }

    public static int getReduction() {
        return REDUCTION;
    }

    @Override
    public boolean isDone() {
        return done;
    }
}
