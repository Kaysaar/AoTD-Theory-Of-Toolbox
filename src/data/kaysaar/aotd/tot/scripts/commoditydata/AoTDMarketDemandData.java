package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketDemandAPI;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.MarketDemand;
import com.fs.starfarer.campaign.econ.MarketDemandData;

import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AoTDMarketDemandData extends MarketDemandData {
    private final Map<String, MarketDemand> dem = new HashMap<>();
    private final Market market;
    private transient Map<String, AoTdMainWorkTask2.InactivePriceState> inactivePrices;

    public Map<String, AoTdMainWorkTask2.InactivePriceState> getInactivePrices() {
        if (inactivePrices == null) inactivePrices = new HashMap<>();
        return inactivePrices;
    }

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

    @Override
    public MarketDemand getDemand(final String comId) {
        if (dem.get(comId) instanceof AoTDMarketDemand aotdDemand) {
            return aotdDemand;
        } else {
            final MarketDemand demand = new AoTDMarketDemand(market, comId);
            dem.put(comId, demand);
            return demand;
        }
    }

    public final void replaceWithAoTDMarketDemand(final List<CommoditySpecAPI> specs) {
        for (CommoditySpecAPI spec : specs) {
            final String comId = spec.getId();

            if (!(dem.get(comId) instanceof AoTDMarketDemand)) {
                dem.put(comId, new AoTDMarketDemand(market, comId));
            }
        }
    }
}
