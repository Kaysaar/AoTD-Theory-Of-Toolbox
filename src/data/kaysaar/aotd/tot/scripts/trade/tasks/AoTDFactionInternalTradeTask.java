package data.kaysaar.aotd.tot.scripts.trade.tasks;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.List;

public class AoTDFactionInternalTradeTask extends MultiFrameTask {
    private List<MarketAPI> markets = null;
    private int marketIndex = 0;

    public AoTDFactionInternalTradeTask(Economy var1) {
        this.markets = new ArrayList(var1.getMarkets());
    }

    public void doNextBatch() {
        if (!this.isDone()) {
            if (this.marketIndex < this.markets.size()) {
                AoTDTradeManager.getInstance().addMarket(this.markets.get(marketIndex));
                ++this.marketIndex;
            }

        }
    }

    public boolean isDone() {
        return this.marketIndex >= this.markets.size();
    }

    @Override
    public String getLoggingIdentifier() {
        return "AoTD-Economy-In-Faction";
    }
}
