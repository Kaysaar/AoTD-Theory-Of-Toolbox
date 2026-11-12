
package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.IndustryMaxDeficitModifierListener;
import com.fs.starfarer.api.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AoTDEconomyDeficitListener implements IndustryMaxDeficitModifierListener {

    @Override
    public void modifyIndustryMaxDeficit(Industry industry, Pair<String, Integer> result, String... commodityIds) {
        if (Global.CODEX_TOOLTIP_MODE || commodityIds == null) return;

        List<Industry> industries = new ArrayList<>(industry.getMarket().getIndustries());
        industries.sort(Comparator.comparingInt(a -> a.getSpec().getOrder()));

        result.one = null;
        result.two = 0;

        for (String commodityId : commodityIds) {
            int demand = (int) industry.getDemand(commodityId).getQuantity().getModifiedValue();
            CommodityOnMarketAPI commodity = industry.getMarket().getCommodityData(commodityId);
            int available = commodity.getAvailable();

            for (Industry other : industries) {
                if (other.getId().equals(industry.getId())) break;

                int otherDemand = (int) other.getDemand(commodityId).getQuantity().getModifiedValue();
                available -= Math.max(otherDemand, 0);
            }

            int deficit = Math.max(demand - Math.max(available, 0), 0);
            if (deficit > result.two) {
                result.one = commodityId;
                result.two = deficit;
            }
        }
    }
}
