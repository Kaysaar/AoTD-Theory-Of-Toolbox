package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.MarketDemandAPI;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.MarketDemand;
import com.fs.starfarer.campaign.econ.MarketDemandData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AoTDMarketDemandData extends MarketDemandData {
    private Market market;
    private Map<String, MarketDemand> dem = new HashMap();
    public AoTDMarketDemandData(Market market) {
        super(market);
        this.market = market;
    }

    @Override
    public List<MarketDemandAPI> getDemandList() {
        return new ArrayList<>(dem.values());
    }

    @Override
    public Map<String, MarketDemand> getDemands() {
        return dem;
    }

    public MarketDemand getDemand(String var1) {
        MarketDemand var2 = (MarketDemand)this.dem.get(var1);
        if ((var2 instanceof AoTDMarketDemand)) {
            return var2;
        } else {
            var2 = new AoTDMarketDemand(this.market, var1);
            this.dem.put(var1, var2);
            return var2;
        }
    }

}
