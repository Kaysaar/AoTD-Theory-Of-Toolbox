package data.kaysaar.aotd.tot.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;

import static data.kaysaar.aotd.tot.conditions.AoTDToolboxFoodProd.prodId;

public class AoTDToolboxFoodProdCanceler extends BaseMarketConditionPlugin {
    //This should cover Unknown skies issues due to food prod being only exp scaling
    public boolean showIcon() {
        return false;
    }
    @Override
    public void apply(String id) {
        super.apply(id);
        if(market.getConditions().get(0)!=this.condition){
            ArrayList<MarketConditionAPI> conditionAPIS = new ArrayList<>(market.getConditions());
            market.getConditions().clear();
            market.getConditions().add(this.condition);
            for (MarketConditionAPI conditionAPI : conditionAPIS) {
                if(conditionAPI!=this.condition){
                    market.getConditions().add(conditionAPI);
                }
            }
        }

        for (Industry industry : market.getIndustries()) {
            industry.getSupply(Commodities.FOOD).getQuantity().unmodify(prodId);
            industry.getDemand(Commodities.HEAVY_MACHINERY).getQuantity().unmodify(prodId);
        }
    }
}
