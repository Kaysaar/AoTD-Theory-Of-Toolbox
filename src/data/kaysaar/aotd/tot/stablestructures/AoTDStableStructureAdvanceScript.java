package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class AoTDStableStructureAdvanceScript implements EveryFrameScript {
    private IntervalUtil interval = new IntervalUtil(2.5f, 2.5f);
    private AoTDStableStructureAdvanceTask task;

    public static void install() {
        if(!Global.getSector().hasScript(AoTDStableStructureAdvanceScript.class)){
            Global.getSector().addScript(new AoTDStableStructureAdvanceScript());
        }

    }

    @Override
    public void advance(float amount) {
        if (amount <= 0f || Global.getSector().isPaused()) return;
        interval.advance(amount);
        if (task == null && interval.intervalElapsed()) task = new AoTDStableStructureAdvanceTask();
        if (task != null) {
            task.doNextBatch();
            if (task.isDone()) task = null;
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    public static ArrayList<StarSystemAPI> getColonizedSystems() {
        LinkedHashSet<StarSystemAPI> systems = new LinkedHashSet<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getStarSystem() != null) systems.add(market.getStarSystem());
        }
        return new ArrayList<>(systems);
    }

    public static ArrayList<AoTDStableStructureImpl> getStructures(StarSystemAPI system) {
        ArrayList<AoTDStableStructureImpl> structures = new ArrayList<>();
        for (SectorEntityToken token : new ArrayList<>(system.getAllEntities())) {
            Object value = token.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
            if (!(value instanceof AoTDStableStructureImpl)) continue;
            AoTDStableStructureImpl impl = (AoTDStableStructureImpl) value;
            impl.currentlyAssignedToken = token;
            if (token.isExpired()) {
                impl.notifyRemoved();
                continue;
            }
            structures.add(impl);
        }
        return structures;
    }

}
