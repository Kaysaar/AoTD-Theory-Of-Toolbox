package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.UpdateMarketsAgainTask;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.List;

public class AoTDUpdateMarketAgainTask extends UpdateMarketsAgainTask {

    public static final String INITIAL_STAGE_DESC = "AoTD economy initial stage";
    private static final int REDUCTION = 10000;

    private  List<MarketAPI> markets;
    private final MarketAPI singleMarket;

    private int marketIndex = 0;
    private boolean done = false;

    public AoTDUpdateMarketAgainTask(Economy economy) {
        super(economy);
        this.markets = new ArrayList<>(economy.getMarkets());
        this.singleMarket = null;
    }

    public AoTDUpdateMarketAgainTask(Economy economy, MarketAPI singleMarket) {
        super(economy);
        this.markets = null;
        this.singleMarket = singleMarket;
    }

    @Override
    public void doNextBatch() {
        if (isDone()) {
            return;
        }

        /*
         * Single-market mode.
         *
         * The old version processed the selected market, then continued into the
         * normal all-market branch because it did not return and never set runOnce.
         */
        if (singleMarket != null) {
            processMarket(singleMarket);
            done = true;
            return;
        } else if (markets==null) {
            markets = Global.getSector().getEconomy().getMarketsCopy();
        }

        if (marketIndex >= markets.size()) {
            done = true;
            return;
        }

        processMarket(markets.get(marketIndex));
        marketIndex++;

        if (marketIndex >= markets.size()) {
            done = true;
        }
    }

    private static void processMarket(MarketAPI market) {
        market.reapplyConditions();
        AoTDTradeManager.getInstance().addMarket(market);
        AoTDIndustryData data = AoTDIndustryData.getInstance(market);
        data.checkForNewIndustries(market);

        for (Industry industry : market.getIndustries()) {
            if (data.isPending(industry.getId())) {
                applyPendingIndustrySuppression(industry);
            } else {
                restoreIndustry(industry);
            }
        }

//        AoTDEconomy.pruneCommoditiesThatMightAppear((Market) market);
    }

    public static void applyPendingIndustrySuppression(Industry industry) {
        industry.getSupplyBonusFromOther().modifyFlat(
                AoTDIndustryData.source,
                -getReduction(),
                INITIAL_STAGE_DESC
        );

        industry.getDemandReductionFromOther().modifyFlat(
                AoTDIndustryData.source,
                getReduction(),
                INITIAL_STAGE_DESC
        );

        /*
         * Keep the original order for pending industries.
         */
        industry.apply();
        industry.unapply();
    }

    private static void restoreIndustry(Industry industry) {
        industry.getSupplyBonusFromOther().unmodifyFlat(AoTDIndustryData.source);
        industry.getDemandReductionFromOther().unmodifyFlat(AoTDIndustryData.source);

        /*
         * Keep the original order for active industries.
         */
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
