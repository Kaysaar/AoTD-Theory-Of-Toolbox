package data.kaysaar.aotd.tot.industries;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class AoTDSpaceframeFoundry extends AoTDBaseIndustry {
    @Override
    protected void applyIndustry() {
        int size = market.getSize();
        demandExact(Commodities.METALS,size * 1500);
        demand(Commodities.ORGANICS,size);
        supply("aotd_spaceframe_modules",size - 3);
        getSupply("aotd_spaceframe_modules").getQuantity().modifyMult("aotd_input_shortage",
                getDemandFulfillment(Commodities.METALS,Commodities.ORGANICS),"Input shortages");
    }
}
