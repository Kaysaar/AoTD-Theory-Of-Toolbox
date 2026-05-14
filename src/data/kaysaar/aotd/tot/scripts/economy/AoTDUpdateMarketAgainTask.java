package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.reach.UpdateMarketsAgainTask;

import java.util.ArrayList;
import java.util.List;

public class AoTDUpdateMarketAgainTask extends UpdateMarketsAgainTask {
    private List<MarketAPI> markets = null;
    private int marketIndex = 0;

    public AoTDUpdateMarketAgainTask(Economy var1) {
        super(var1);
        this.markets = new ArrayList(var1.getMarkets());
    }

    public void doNextBatch() {
        if (!this.isDone()) {
            if (this.marketIndex < this.markets.size()) {
                MarketAPI var1 = (MarketAPI) this.markets.get(this.marketIndex);
                var1.reapplyConditions();
                AoTDIndustryData data = AoTDIndustryData.getInstance(var1);
                data.checkForNewIndustries(var1);
                for (Industry industry : var1.getIndustries()) {
                    if (data.isPending(industry.getId())) {
                        industry.getSupplyBonusFromOther().modifyFlat(AoTDIndustryData.source, -getReduction(), "AoTD economy inital stage");
                        industry.getDemandReductionFromOther().modifyFlat(AoTDIndustryData.source, getReduction(), "AoTD economy inital stage");
                        industry.apply();
                        industry.unapply();
                    }
                }
                for (Industry industry : var1.getIndustries()) {
                    if (!data.isPending(industry.getId())) {
                        industry.getSupplyBonusFromOther().unmodifyFlat(AoTDIndustryData.source);
                        industry.getDemandReductionFromOther().unmodifyFlat(AoTDIndustryData.source);
                        industry.unapply();
                        industry.apply();
                    }
                }

//                AoTDEconomy.pruneCommoditiesThatMightAppear((Market) var1);
                ++this.marketIndex;
            }

        }
    }

    public static int getReduction() {
        return 10000;
    }

    public boolean isDone() {
        return this.marketIndex >= this.markets.size();
    }
}
