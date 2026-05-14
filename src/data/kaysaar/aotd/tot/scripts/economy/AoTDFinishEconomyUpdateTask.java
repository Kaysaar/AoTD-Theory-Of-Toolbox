package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.FinishEconomyUpdateTask;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AoTDFinishEconomyUpdateTask extends FinishEconomyUpdateTask {
    private Economy economy;
    private boolean done = false;

    public AoTDFinishEconomyUpdateTask(Economy var1) {
        super(var1);
        this.economy = var1;
    }

    public void doNextBatch() {
        if (!this.isDone()) {
            List var1 = this.economy.getUpdateListeners();
            Iterator var3 = (new ArrayList(var1)).iterator();
            for (AoTDFactionTradeData value : AoTDTradeManager.getInstance().getAllFactionTradeData().values()) {
                value.computeInternalTrade();
            }
            while(var3.hasNext()) {
                EconomyAPI.EconomyUpdateListener var2 = (EconomyAPI.EconomyUpdateListener)var3.next();
                if (var2.isEconomyListenerExpired()) {
                    Global.getSector().getEconomy().removeUpdateListener(var2);
                } else {
                    var2.economyUpdated();
                }
            }

            this.done = true;

        }
    }

    public boolean isDone() {
        return this.done;
    }

}
