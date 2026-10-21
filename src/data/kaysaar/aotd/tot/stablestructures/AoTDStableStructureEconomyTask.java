package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDUpdateMarketAgainTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AoTDStableStructureEconomyTask extends MultiFrameTask {
    private List<StarSystemAPI> systems;
    private List<MarketAPI> markets;
    private int phase = 0;
    private int index = 0;

    public AoTDStableStructureEconomyTask() {
        systems = AoTDStableStructureAdvanceScript.getColonizedSystems();
        markets = Global.getSector().getEconomy().getMarketsCopy();
    }

    @Override
    public void doNextBatch() {
        if (phase < 2) {
            if (index < systems.size()) {
                ArrayList<AoTDStableStructureImpl> structures = AoTDStableStructureAdvanceScript.getStructures(systems.get(index));
                structures.sort(Comparator.comparingInt(AoTDStableStructureImpl::getEffectOrder));
                for (AoTDStableStructureImpl impl : structures) {
                    if (phase == 0) impl.unapply();
                    else if (impl.isFunctional()) impl.apply();
                }
                index++;
                return;
            }
            phase++;
            index = 0;
            return;
        }
        if (phase == 2) {
            if (index < markets.size()) {
                MarketAPI market = markets.get(index++);
                AoTDIndustryData data = AoTDIndustryData.getInstance(market);
                for (Industry industry : market.getIndustries()) {
                    if (data.isPending(industry.getId())) {
                        AoTDUpdateMarketAgainTask.applyPendingIndustrySuppression(industry);
                    } else {
                        AoTDUpdateMarketAgainTask.restoreIndustry(industry);
                    }
                }
                return;
            }
            phase = 3;
        }
    }

    @Override
    public boolean isDone() {
        return phase == 3;
    }

    @Override
    public String getLoggingIdentifier() {
        return "AoTD-Stable-Structure-Effects";
    }
}
