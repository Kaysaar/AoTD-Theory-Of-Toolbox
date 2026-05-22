package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import com.fs.starfarer.campaign.econ.reach.*;
import data.kaysaar.aotd.tot.scripts.trade.tasks.AoTDFactionInternalTradeTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AoTDReachEconomy extends ReachEconomy {


    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        List<MarketAPI> markets = new ArrayList<>(this.getMarkets());


        if(Global.getSector().getCurrentlyOpenMarket()!=null){
            MarketAPI market = Global.getSector().getCurrentlyOpenMarket();
            PersonAPI var4 = market.getAdmin();
            var4.getStats().refreshCharacterStatsEffects();
            var4.getStats().refreshGovernedOutpostEffects(market);

            MainWorkTask2 var5 = new AoTdMainWorkTask2(markets, this, econWorkParams,market);

            while(!((MultiFrameTask)var5).isDone()) {
                ((MultiFrameTask)var5).doNextBatch();
            }
            AoTDUpdateMarketAgainTask var6 = new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy(),market);

            while(!((MultiFrameTask)var6).isDone()) {
                ((MultiFrameTask)var6).doNextBatch();
            }
        }
        else{
            Iterator var3 = (new ArrayList(markets)).iterator();
            while(var3.hasNext()) {
                MarketAPI var2 = (MarketAPI)var3.next();
                PersonAPI var4 = var2.getAdmin();
                var4.getStats().refreshCharacterStatsEffects();
                var4.getStats().refreshGovernedOutpostEffects(var2);
            }
            MainWorkTask2 var5 = new AoTdMainWorkTask2(markets, this, econWorkParams);

            while(!((MultiFrameTask)var5).isDone()) {
                ((MultiFrameTask)var5).doNextBatch();
            }
            AoTDUpdateMarketAgainTask var6 = new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy());

            while(!((MultiFrameTask)var6).isDone()) {
                ((MultiFrameTask)var6).doNextBatch();
            }

        }


        if (econWorkParams.withImmigration) {
            ImmigrationTask var7 = new ImmigrationTask(markets, this, !econWorkParams.forceNonUIStep);

            while(!((MultiFrameTask)var7).isDone()) {
                ((MultiFrameTask)var7).doNextBatch();
            }
        }

        FinishEconomyUpdateTask var8 = new AoTDFinishEconomyUpdateTask((Economy)Global.getSector().getEconomy());

        while(!((MultiFrameTask)var8).isDone()) {
            ((MultiFrameTask)var8).doNextBatch();
        }
    }

    @Override
    public void addMarket(MarketAPI marketAPI) {
        super.addMarket(marketAPI);
        //Here swap of all commodities into AoTDcommoidtyData
    }
}
