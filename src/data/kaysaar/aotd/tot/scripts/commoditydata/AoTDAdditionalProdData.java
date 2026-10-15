package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AoTDAdditionalProdData extends MutableStatWithTempMods {

    public LinkedHashMap<String, Boolean> beforeIndustries = new LinkedHashMap<>();
    public transient LinkedHashMap<String, Float> filledRawAmounts = new LinkedHashMap<>();

    public AoTDAdditionalProdData() {
        super(0f);
    }

    public AoTDAdditionalProdData(float base) {
        super(base);
    }

    @Override
    protected Object readResolve() {
        super.readResolve();

        if (beforeIndustries == null) {
            beforeIndustries = new LinkedHashMap<>();
        }
        if (filledRawAmounts == null) {
            filledRawAmounts = new LinkedHashMap<>();
        }

        for (String source : getFlatMods().keySet()) {
            beforeIndustries.putIfAbsent(source, false);
        }

        return this;
    }

    @Override
    public void modifyFlat(String source, float value, String desc) {
        beforeIndustries.putIfAbsent(source, false);
        super.modifyFlat(source, value, desc);
    }

    public void modifyFlat(String source, float value, String desc, boolean beforeIndustries) {
        this.beforeIndustries.put(source, beforeIndustries);
        super.modifyFlat(source, value, desc);
    }

    @Override
    public void addTemporaryModFlat(float durInDays, String source, String desc, float value) {
        beforeIndustries.putIfAbsent(source, false);
        super.addTemporaryModFlat(durInDays, source, desc, value);
    }

    public void addTemporaryModFlat(float durInDays, String source, String desc, float value, boolean beforeIndustries) {
        this.beforeIndustries.put(source, beforeIndustries);
        super.addTemporaryModFlat(durInDays, source, desc, value);
    }

    public void addTemporaryModFlat(float durInDays, String source, float value, boolean beforeIndustries) {
        this.beforeIndustries.put(source, beforeIndustries);
        super.addTemporaryModFlat(durInDays, source, value);
    }

    public void setBeforeIndustries(String source, boolean beforeIndustries) {
        if (!getFlatMods().containsKey(source)) return;
        this.beforeIndustries.put(source, beforeIndustries);
    }

    public boolean isBeforeIndustries(String source) {
        return beforeIndustries.getOrDefault(source, false);
    }

    public float getAmount(String source) {
        MutableStat.StatMod mod = getFlatStatMod(source);
        if (mod == null) return 0f;
        return mod.getValue();
    }

    public float getRequestedAmount(String source) {
        return Math.max(0f, getAmount(source));
    }

    public float getTotalRequestedAmount() {
        float total = 0f;

        for (MutableStat.StatMod mod : getFlatMods().values()) {
            total += Math.max(0f, mod.getValue());
        }

        return total;
    }

    public float getTotalRequestedAmount(boolean beforeIndustries) {
        float total = 0f;

        for (Map.Entry<String, MutableStat.StatMod> entry : getFlatMods().entrySet()) {
            if (isBeforeIndustries(entry.getKey()) != beforeIndustries) continue;
            total += Math.max(0f, entry.getValue().getValue());
        }

        return total;
    }

    public float getFilledRawAmount(String source) {
        if (filledRawAmounts == null) {
            filledRawAmounts = new LinkedHashMap<>();
        }

        return filledRawAmounts.getOrDefault(source, 0f);
    }

    public void setFilledRawAmount(String source, float amount) {
        if (!getFlatMods().containsKey(source)) return;

        if (filledRawAmounts == null) {
            filledRawAmounts = new LinkedHashMap<>();
        }

        filledRawAmounts.put(source, Math.max(0f, amount));
    }

    public void addFilledRawAmount(String source, float amount) {
        setFilledRawAmount(source, getFilledRawAmount(source) + amount);
    }

    public void resetFilledAmounts() {
        if (filledRawAmounts == null) {
            filledRawAmounts = new LinkedHashMap<>();
        } else {
            filledRawAmounts.clear();
        }
    }

    public List<MutableStat.StatMod> getModsBeforeIndustries() {
        return getMods(true);
    }

    public List<MutableStat.StatMod> getModsAfterIndustries() {
        return getMods(false);
    }

    public List<MutableStat.StatMod> getMods(boolean beforeIndustries) {
        List<MutableStat.StatMod> mods = new ArrayList<>();

        for (Map.Entry<String, MutableStat.StatMod> entry : getFlatMods().entrySet()) {
            if (isBeforeIndustries(entry.getKey()) == beforeIndustries) {
                mods.add(entry.getValue());
            }
        }

        return mods;
    }

    public List<String> getSources() {
        return new ArrayList<>(getFlatMods().keySet());
    }

    public List<String> getSources(boolean beforeIndustries) {
        List<String> sources = new ArrayList<>();

        for (String source : getFlatMods().keySet()) {
            if (isBeforeIndustries(source) == beforeIndustries) {
                sources.add(source);
            }
        }

        return sources;
    }

    public boolean hasSource(String source) {
        return getFlatMods().containsKey(source);
    }

    @Override
    public void unmodify(String source) {
        super.unmodify(source);

        beforeIndustries.remove(source);

        if (filledRawAmounts != null) {
            filledRawAmounts.remove(source);
        }
    }

    @Override
    public void unmodifyFlat(String source) {
        super.unmodifyFlat(source);

        beforeIndustries.remove(source);

        if (filledRawAmounts != null) {
            filledRawAmounts.remove(source);
        }
    }

    @Override
    public void unmodify() {
        super.unmodify();

        beforeIndustries.clear();

        if (filledRawAmounts != null) {
            filledRawAmounts.clear();
        }
    }
}