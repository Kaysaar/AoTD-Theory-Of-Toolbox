package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.MutableStat;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;

public class AoTDSystemStructureCapacityManager {
    public static final String CAPACITY_MOD = "aotd_mobile_structure_capacity";
    public static final String CAPACITY_MOD_ACTIVE = "$aotd_mobile_structure_capacity_active";

    public static void update(StarSystemAPI system) {
        if (system == null) return;

        int bonus = 0;
        for (SectorEntityToken token : system.getAllEntities()) {
            if (token.isExpired() || token.getCustomEntitySpec() == null) continue;
            AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(token.getCustomEntityType());
            if (spec == null) continue;

            Object stored = token.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
            if (stored != null && !(stored instanceof AoTDStableStructureImpl)) continue;
            AoTDStableStructureImpl impl;
            if (stored instanceof AoTDStableStructureImpl) {
                impl = (AoTDStableStructureImpl) stored;
            } else {
                impl = spec.getImplClassInstance();
                impl.setCurrentlyAssignedToken(token);
            }

            int value = Math.max(0, impl.getSystemStructureCapacityBonus());
            if (value == 0) continue;

            // Mobile structures occupy a slot but never replace an existing stable location.
            if (!token.getMemoryWithoutUpdate().contains(AoTDStableStructureConstruction.USES_STABLE_LOCATION)) {
                token.getMemoryWithoutUpdate().set(AoTDStableStructureConstruction.USES_STABLE_LOCATION, false);
            }
            bonus += value;
        }

        if (bonus <= 0 && !system.getMemoryWithoutUpdate().getBoolean(CAPACITY_MOD_ACTIVE)) return;

        MutableStat stat = AoTDToolboxMisc.getSystemStructureCapacityStat(system);
        if (bonus > 0) {
            stat.modifyFlat(CAPACITY_MOD, bonus, "Mobile structures");
            system.getMemoryWithoutUpdate().set(CAPACITY_MOD_ACTIVE, true);
        } else {
            stat.unmodifyFlat(CAPACITY_MOD);
            system.getMemoryWithoutUpdate().unset(CAPACITY_MOD_ACTIVE);
        }
    }
}
