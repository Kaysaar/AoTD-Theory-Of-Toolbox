package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.econ.impl.Spaceport;

import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.strings.AoTDIndTags;

import ashlib.data.plugins.misc.AshMisc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class AoTDIndustryData {
    public static enum AoTDIndustryState{
        PENDING,
        ALREADY_WORKING
    }
    public LinkedHashMap<String,String>industriesToIgnoreDueToUpgrade = new LinkedHashMap<>();

    public LinkedHashMap<String, String> getIndustriesToIgnoreDueToUpgrade() {
        if(industriesToIgnoreDueToUpgrade == null)industriesToIgnoreDueToUpgrade = new LinkedHashMap<>();
        return industriesToIgnoreDueToUpgrade;
    }

    private transient volatile IndustryOrder cachedIndustryOrder;

    private static final class IndustryOrder {
        final Industry[] input;
        final int[] priority;
        final boolean[] pending;
        final List<Industry> active;
        IndustryOrder(Industry[] input, int[] priority, boolean[] pending,
                      List<Industry> active) {
            this.input = input; this.priority = priority; this.pending = pending;
            this.active = Collections.unmodifiableList(active);
        }
    }

    public List<Industry> getActiveIndustriesInOrder(MarketAPI market) {
        List<Industry> industries = market.getIndustries();
        IndustryOrder cached = cachedIndustryOrder;
        boolean valid = cached != null && cached.input.length == industries.size();
        if (valid) {
            for (int i = 0; i < industries.size(); i++) {
                Industry industry = industries.get(i);
                if (cached.input[i] != industry || cached.priority[i] != industry.getSpec().getOrder()
                        || cached.pending[i] != isPending(industry.getId())) {
                    valid = false;
                    break;
                }
            }
        }
        if (valid) return cached.active;
        Industry[] input = industries.toArray(new Industry[0]);
        int[] priority = new int[input.length];
        boolean[] pending = new boolean[input.length];
        ArrayList<Industry> active = new ArrayList<>(input.length);
        for (int i = 0; i < input.length; i++) {
            priority[i] = input[i].getSpec().getOrder();
            pending[i] = isPending(input[i].getId());
            if (!pending[i]) active.add(input[i]);
        }
        active.sort(Comparator.comparingInt(i -> i.getSpec().getOrder()));
        IndustryOrder built = new IndustryOrder(input, priority, pending, active);
        // Concurrent builders may do duplicate work, but never expose partial arrays.
        // No monitor is held while calling industry/game methods.
        cachedIndustryOrder = built;
        return built.active;
    }

    public static String source ="aotd_economy_correction";
    public static String memKey = "$aotd_industry_data";
    public LinkedHashMap<String,AoTDIndustryState>statesOnMarket = new LinkedHashMap<>();

    public static AoTDIndustryData getInstance(MarketAPI market){
        if(!market.getMemoryWithoutUpdate().contains(memKey)){
            AoTDIndustryData data = new AoTDIndustryData();
            for (Industry industry : market.getIndustries()) {
                data.statesOnMarket.put(industry.getId(), AoTDIndustryState.ALREADY_WORKING);
            }
            market.getMemoryWithoutUpdate().set(memKey,data);
        }
        return (AoTDIndustryData) market.getMemoryWithoutUpdate().get(memKey);

    }
    public void checkForNewIndustries(MarketAPI market){
        for (Industry industry : market.getIndustries()) {
            if(!statesOnMarket.containsKey(industry.getId())){
                statesOnMarket.put(industry.getId(), AoTDIndustryState.PENDING);
                if(industry.getAllDemand().isEmpty()||industry.getSpec().hasTag(AoTDIndTags.ALWAYS_ACTIVE_NON_PENDING)){
                    statesOnMarket.put(industry.getId(),AoTDIndustryState.ALREADY_WORKING);
                }
                if(AoTDEconomy.runningPrePlayerEconomy){
                    statesOnMarket.put(industry.getId(),AoTDIndustryState.ALREADY_WORKING);
                }
                if(industry instanceof PopulationAndInfrastructure || industry instanceof Spaceport){
                    statesOnMarket.put(industry.getId(),AoTDIndustryState.ALREADY_WORKING);
                }
            }
            if(industry.isUpgrading()){
                String id = (String) ReflectionUtilis.getPrivateVariable("upgradeId",industry);
                if(AshMisc.isStringValid(id)&&!getIndustriesToIgnoreDueToUpgrade().containsKey(id)){
                    getIndustriesToIgnoreDueToUpgrade().put(industry.getId(),id);
                }
            }
        }
        LinkedHashSet<String>toRemove = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : getIndustriesToIgnoreDueToUpgrade().entrySet()) {
            if(!market.hasIndustry(entry.getKey())){
                if(market.getIndustry(entry.getValue())!=null){
                    statesOnMarket.put(entry.getValue(),AoTDIndustryState.ALREADY_WORKING);
                    statesOnMarket.remove(entry.getKey());
                }
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(x->getIndustriesToIgnoreDueToUpgrade().remove(x));
    }
    public boolean isPending(String id){

        return statesOnMarket.get(id) == AoTDIndustryState.PENDING;
    }
    public void applyEndOfMonthChange(MarketAPI market){
        statesOnMarket.clear();
        for (Industry industry : market.getIndustries()) {
            if(!(industry.isBuilding()&&!industry.isUpgrading())||industry.getSpec().hasTag(AoTDIndTags.ALWAYS_ACTIVE_NON_PENDING)||industry instanceof PopulationAndInfrastructure || industry instanceof Spaceport){
                statesOnMarket.put(industry.getId(), AoTDIndustryState.ALREADY_WORKING);
            }

        }
    }

}
