package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDReachEconomy;
import data.kaysaar.aotd.tot.scripts.economy.AoTDUpdateMarketAgainTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AoTDStableStructureEconomyTask extends MultiFrameTask {
    private List<StarSystemAPI> systems;
    private List<MarketAPI> markets;
    private int phase = 0;
    private int index = 0;

    private transient boolean profile;
    private transient boolean profileLogged;
    private transient long started;
    private transient long[] phaseNanos;
    private transient int structures;
    private transient int applied;
    private transient int industries;
    private transient int pending;

    public AoTDStableStructureEconomyTask() {
        systems = AoTDStableStructureAdvanceScript.getColonizedSystems();
        markets = Global.getSector().getEconomy().getMarketsCopy();
        profile = AoTDReachEconomy.PROFILE_NEXT_STEP;
        if (profile) {
            started = System.nanoTime();
            phaseNanos = new long[3];
        }
    }

    @Override
    public void doNextBatch() {
        if (isDone()) return;
        int currentPhase = phase;
        long startedPhase = profile ? System.nanoTime() : 0L;
        try {
            if (phase < 2) {
                if (index < systems.size()) {
                    ArrayList<AoTDStableStructureImpl> values = AoTDStableStructureAdvanceScript.getStructures(systems.get(index));
                    values.sort(Comparator.comparingInt(AoTDStableStructureImpl::getEffectOrder));
                    for (AoTDStableStructureImpl impl : values) {
                        if (phase == 0) {
                            impl.unapply();
                            if (profile) structures++;
                        } else if (impl.isFunctional()) {
                            impl.apply();
                            if (profile) applied++;
                        }
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
                        if (profile) industries++;
                        if (data.isPending(industry.getId())) {
                            AoTDUpdateMarketAgainTask.applyPendingIndustrySuppression(industry);
                            if (profile) pending++;
                        } else {
                            AoTDUpdateMarketAgainTask.restoreIndustry(industry);
                        }
                    }
                    return;
                }
                phase = 3;
            }
        } finally {
            if (profile && phaseNanos != null) {
                phaseNanos[currentPhase] += System.nanoTime() - startedPhase;
                if (phase == 3 && !profileLogged) {
                    profileLogged = true;
                    Global.getLogger(AoTDStableStructureEconomyTask.class).info(String.format(Locale.ROOT,
                            "AoTD structureEffects systems=%d structures=%d applied=%d markets=%d " +
                                    "industries=%d pending=%d active=%.3fms elapsedWall=%.3fms " +
                                    "unapply=%.3fms apply=%.3fms industryRestore=%.3fms",
                            systems.size(), structures, applied, markets.size(), industries, pending,
                            (phaseNanos[0] + phaseNanos[1] + phaseNanos[2]) / 1_000_000d,
                            (System.nanoTime() - started) / 1_000_000d,
                            phaseNanos[0] / 1_000_000d, phaseNanos[1] / 1_000_000d,
                            phaseNanos[2] / 1_000_000d));
                }
            }
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
