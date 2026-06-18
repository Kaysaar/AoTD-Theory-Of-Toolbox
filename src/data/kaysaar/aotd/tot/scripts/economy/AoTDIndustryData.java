package data.kaysaar.aotd.tot.scripts.economy;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.econ.impl.Spaceport;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.strings.AoTDIndTags;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
