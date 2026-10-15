package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class AoTDStableStructureSpec {
    //Note here id is also stable structure id as in enitityId
    public String id;
    public int creditCost;
    public int daysToBuild;
    public LinkedHashMap<String,Integer>totalResourcesNeeded;
    public LinkedHashSet<String>downgrades = new LinkedHashSet<>();
    public String pluginClass;

    public LinkedHashSet<String> getDowngrades() {
        return downgrades;
    }

    public String getId() {
        return id;
    }

    public int getCreditCost() {
        return creditCost;
    }

    public int getDaysToBuild() {
        return daysToBuild;
    }

    public void setDaysToBuild(int daysToBuild) {
        this.daysToBuild = daysToBuild;
    }

    public void setCreditCost(int creditCost) {
        this.creditCost = creditCost;
    }

    public LinkedHashMap<String, Integer> getTotalResourcesNeeded() {
        return totalResourcesNeeded;
    }

    public void setPluginClass(String pluginClass) {
        this.pluginClass = pluginClass;
    }
    public AoTDStableStructureImpl getImplClassInstance() {
        try {
            AoTDStableStructureImpl impl = (AoTDStableStructureImpl) Global.getSettings().getScriptClassLoader().loadClass(pluginClass).newInstance();
            impl.init(id);
            return impl;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

}
