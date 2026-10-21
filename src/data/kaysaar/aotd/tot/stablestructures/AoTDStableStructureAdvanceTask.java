package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;

import java.util.List;

public class AoTDStableStructureAdvanceTask extends MultiFrameTask {
    private List<StarSystemAPI> systems;
    private int systemIndex = 0;

    public AoTDStableStructureAdvanceTask() {
        systems = AoTDStableStructureAdvanceScript.getColonizedSystems();
    }

    @Override
    public void doNextBatch() {
        if (isDone()) return;
        for (AoTDStableStructureImpl impl : AoTDStableStructureAdvanceScript.getStructures(systems.get(systemIndex))) {
            if (impl.lastAdvanceTimestamp == null) impl.resetAdvanceTimestamp();
            float days = Math.max(0f, Global.getSector().getClock().getElapsedDaysSince(impl.lastAdvanceTimestamp));
            impl.resetAdvanceTimestamp();
            if (days > 0f) impl.advance(Global.getSector().getClock().convertToSeconds(days));
        }
        systemIndex++;
    }

    @Override
    public boolean isDone() {
        return systemIndex >= systems.size();
    }

    @Override
    public String getLoggingIdentifier() {
        return "AoTD-Stable-Structures";
    }
}
