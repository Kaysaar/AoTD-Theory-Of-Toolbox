package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.MutableStat;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AoTDSystemStructureCapacityManager {
    public static final String CAPACITY_MOD = "aotd_mobile_structure_capacity";
    public static final String CAPACITY_MOD_ACTIVE = "$aotd_mobile_structure_capacity_active";
    public static final String CAPACITY_MOD_IDS = "$aotd_system_structure_capacity_modifier_ids";
    public static final String DEFAULT_MODIFIER_NAME = "Mobile structures";

    public static void update(StarSystemAPI system) {
        if (system == null) return;

        LinkedHashMap<String, Integer> bonuses = new LinkedHashMap<>();
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

            // Structures offering their own capacity must not consume an existing stable point.
            if (!token.getMemoryWithoutUpdate().contains(AoTDStableStructureConstruction.USES_STABLE_LOCATION)) {
                token.getMemoryWithoutUpdate().set(AoTDStableStructureConstruction.USES_STABLE_LOCATION, false);
            }

            String modifierName = impl.getSystemStructureCapacityModifierName();
            if (modifierName == null || modifierName.trim().isEmpty()) modifierName = DEFAULT_MODIFIER_NAME;
            modifierName = modifierName.trim();
            bonuses.put(modifierName, bonuses.getOrDefault(modifierName, 0) + value);
        }

        MutableStat stat = AoTDToolboxMisc.getSystemStructureCapacityStat(system);
        LinkedHashSet<String> oldModifierIds = new LinkedHashSet<>();
        Object storedIds = system.getMemoryWithoutUpdate().get(CAPACITY_MOD_IDS);
        if (storedIds instanceof Set) {
            for (Object oldId : (Set<?>) storedIds) {
                if (oldId instanceof String) oldModifierIds.add((String) oldId);
            }
        }
        // Older saves used one aggregate capacity modifier, without per-name IDs.
        oldModifierIds.add(CAPACITY_MOD);
        for (String oldId : oldModifierIds) stat.unmodifyFlat(oldId);

        LinkedHashSet<String> newModifierIds = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : bonuses.entrySet()) {
            String modifierName = entry.getKey();
            String modifierId = DEFAULT_MODIFIER_NAME.equals(modifierName)
                    ? CAPACITY_MOD : CAPACITY_MOD + ":" + modifierName;
            stat.modifyFlat(modifierId, entry.getValue(), modifierName);
            newModifierIds.add(modifierId);
        }

        if (newModifierIds.isEmpty()) {
            system.getMemoryWithoutUpdate().unset(CAPACITY_MOD_IDS);
            system.getMemoryWithoutUpdate().unset(CAPACITY_MOD_ACTIVE);
        } else {
            system.getMemoryWithoutUpdate().set(CAPACITY_MOD_IDS, newModifierIds);
            system.getMemoryWithoutUpdate().set(CAPACITY_MOD_ACTIVE, true);
        }
    }
}
