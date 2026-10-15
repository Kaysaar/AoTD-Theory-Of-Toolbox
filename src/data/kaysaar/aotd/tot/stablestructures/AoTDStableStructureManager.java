
package data.kaysaar.aotd.tot.stablestructures;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;

public class AoTDStableStructureManager {

    public static String filePath = "data/campaign/aotd_stable_structures.csv";

    public static LinkedHashMap<String, AoTDStableStructureSpec> specsList = new LinkedHashMap<>();

    public static LinkedHashMap<String, LinkedList<AoTDStableStructureSpec>> structureUpgrades = new LinkedHashMap<>();

    private static LinkedHashMap<String, LinkedHashSet<String>> directUpgrades = new LinkedHashMap<>();
    public static String memKeyInEntities = "$aotd_impl_field";

    private static boolean cacheBuilt = false;

    public static void init() {
        specsList.clear();
        invalidateUpgradeCache();

        try {
            JSONArray array = Global.getSettings().getMergedSpreadsheetDataForMod(
                    "id", filePath, "aotd_theory_of_toolbox"
            );

            for (int i = 0; i < array.length(); i++) {
                JSONObject jsonObject = array.getJSONObject(i);

                String id = jsonObject.getString("id");
                if (!AshMisc.isStringValid(id.trim())) continue;

                int creditCost = jsonObject.getInt("creditCost");
                int daysToBuild = jsonObject.getInt("daysToBuild");
                String pluginClass = jsonObject.getString("pluginClass");
                if(!AshMisc.isStringValid(pluginClass)){
                    pluginClass = AoTDStableStructureImpl.class.getName();
                }

                String resources = jsonObject.optString("totalResourcesNeeded", "");
                String downgrades = jsonObject.optString("downgrades", "");

                AoTDStableStructureSpec spec = new AoTDStableStructureSpec();

                spec.id = id;
                spec.setCreditCost(creditCost);
                spec.setDaysToBuild(daysToBuild);
                spec.setPluginClass(pluginClass);

                spec.totalResourcesNeeded = loadResourceCosts(resources);

                for (String downgrade : AshMisc.loadEntries(downgrades, ",")) {
                    downgrade = downgrade.trim();

                    if (AshMisc.isStringValid(downgrade) && !downgrade.equals(id)) {
                        spec.downgrades.add(downgrade);
                    }
                }

                specsList.put(id, spec);
            }

            for (AoTDStableStructureSpec spec : specsList.values()) {
                for (String downgradeId : spec.getDowngrades()) {
                    if (!specsList.containsKey(downgradeId)) {
                        throw new RuntimeException(
                                "Stable structure " + spec.getId()
                                        + " references missing downgrade "
                                        + downgradeId
                                        + " in " + filePath
                        );
                    }
                }
            }

            rebuildUpgradeCache();

        } catch (JSONException | IOException e) {
            throw new RuntimeException("Failed to load stable structure specifications", e);
        }
    }

    private static LinkedHashMap<String, Integer> loadResourceCosts(String raw) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();

        for (String entry : AshMisc.loadEntries(raw, ",")) {
            String[] parts = entry.split(":", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid resource cost entry: " + entry);
            }

            String id = parts[0].trim();
            int amount = Integer.parseInt(parts[1].trim());

            if (AshMisc.isStringValid(id)) {
                result.put(id, amount);
            }
        }

        return result;
    }

    public static void addSpec(AoTDStableStructureSpec spec) {
        if (spec == null || spec.getId() == null) return;

        specsList.put(spec.getId(), spec);
        invalidateUpgradeCache();
    }

    public static AoTDStableStructureSpec getSpec(String id) {
        return specsList.get(id);
    }

    public static void invalidateUpgradeCache() {
        structureUpgrades.clear();
        directUpgrades.clear();
        cacheBuilt = false;
    }

    public static void rebuildUpgradeCache() {
        structureUpgrades.clear();
        directUpgrades.clear();

        for (AoTDStableStructureSpec spec : specsList.values()) {
            directUpgrades.put(spec.getId(), new LinkedHashSet<>());
        }

        for (AoTDStableStructureSpec spec : specsList.values()) {
            for (String downgradeId : spec.getDowngrades()) {
                if (downgradeId.equals(spec.getId())) continue;

                LinkedHashSet<String> upgrades = directUpgrades.get(downgradeId);

                if (upgrades != null) {
                    upgrades.add(spec.getId());
                }
            }
        }

        for (String structureId : specsList.keySet()) {
            LinkedList<AoTDStableStructureSpec> upgrades = new LinkedList<>();
            LinkedList<String> pending = new LinkedList<>();
            LinkedHashSet<String> visited = new LinkedHashSet<>();

            visited.add(structureId);
            pending.addAll(directUpgrades.get(structureId));

            while (!pending.isEmpty()) {
                String upgradeId = pending.removeFirst();

                if (!visited.add(upgradeId)) continue;

                AoTDStableStructureSpec upgrade = specsList.get(upgradeId);
                if (upgrade == null) continue;

                upgrades.add(upgrade);

                LinkedHashSet<String> children = directUpgrades.get(upgradeId);

                if (children != null) {
                    pending.addAll(children);
                }
            }

            structureUpgrades.put(structureId, upgrades);
        }

        cacheBuilt = true;
    }

    private static void ensureCacheBuilt() {
        if (!cacheBuilt) {
            rebuildUpgradeCache();
        }
    }

    public static LinkedList<AoTDStableStructureSpec> getStructureTree(String structureId) {
        ensureCacheBuilt();

        LinkedList<AoTDStableStructureSpec> upgrades = structureUpgrades.get(structureId);

        return upgrades == null ? new LinkedList<>() : new LinkedList<>(upgrades);
    }

    public static LinkedList<AoTDStableStructureSpec> getUpgradeChildren(String structureId) {
        ensureCacheBuilt();

        LinkedList<AoTDStableStructureSpec> result = new LinkedList<>();
        LinkedHashSet<String> children = directUpgrades.get(structureId);

        if (children == null) return result;

        for (String childId : children) {
            AoTDStableStructureSpec spec = specsList.get(childId);

            if (spec != null) {
                result.add(spec);
            }
        }

        return result;
    }

    public static boolean hasUpgrades(String structureId) {
        ensureCacheBuilt();

        LinkedList<AoTDStableStructureSpec> upgrades = structureUpgrades.get(structureId);

        return upgrades != null && !upgrades.isEmpty();
    }

    public static boolean isUpgradeOf(String structureId, String upgradeId) {
        for (AoTDStableStructureSpec spec : getStructureTree(structureId)) {
            if (spec.getId().equals(upgradeId)) {
                return true;
            }
        }

        return false;
    }

    public static LinkedList<AoTDStableStructureSpec> getAllBaseStructures() {
        LinkedList<AoTDStableStructureSpec> result = new LinkedList<>();

        for (AoTDStableStructureSpec spec : specsList.values()) {
            if (spec.getDowngrades().isEmpty()) {
                result.add(spec);
            }
        }

        return result;
    }
}
