package data.kaysaar.aotd.tot.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.CivilianSupplyProgram;

public class AoTDToolboxCivContractPopBoost extends BaseMarketConditionPlugin implements MarketImmigrationModifier {
    public int currWeight = 0;

    public void setCurrWeight(int currWeight) {
        if(currWeight==0){
            market.removeCondition("aotd_civ_contract_pop_boost");
            return;
        }
        this.currWeight = currWeight;
    }

    @Override
    public boolean showIcon() {
        return false;
    }
    public void apply(String id) {
        market.addImmigrationModifier(this);
    }

    public void unapply(String id) {
        market.removeImmigrationModifier(this);
    }

    @Override
    public boolean isTransient() {
        return false;
    }
    public static AoTDToolboxCivContractPopBoost getPluginInstance(MarketAPI market){
        if(!market.hasCondition("aotd_civ_contract_pop_boost")){
            market.addCondition("aotd_civ_contract_pop_boost");
        }
        return (AoTDToolboxCivContractPopBoost) market.getCondition("aotd_civ_contract_pop_boost").getPlugin();
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat("aotd_civ_contract",currWeight,"Civilian Supply Program");
    }
}
