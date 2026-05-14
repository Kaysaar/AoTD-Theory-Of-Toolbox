package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDLocalResourcesSubmarketPlugin;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;

import java.util.LinkedHashSet;
import java.util.Map;

public class AoTDExcDefData {


    public MutableStatWithTempMods excess = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods deficit = new MutableStatWithTempMods(0f);
    public float recordedDemandFromNonPendingThisMonth = 0f;
    public int deficitConsequtiveMonths = 0;
    public void recordDemandForThisMonth(AoTDCommodityOnMarket commodity){
        this.recordedDemandFromNonPendingThisMonth = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
    }

    public int stockPileRecordedWhenEventHappened = 0;

    public int getDeficitConsequtiveMonths() {
        return deficitConsequtiveMonths;
    }

    public int getExcess() {
        return excess.getModifiedInt();
    }
    public static final String EXT_TRADE_ID = "aotd_ext_trade";
    public static final String DEF_FROM_NEW_IND = "aotd_new_ind_demand";
    public void clearExternalTrade() {
        if(deficit.getModifiedValue()>0){
            deficitConsequtiveMonths++;
        }
        else{
            deficitConsequtiveMonths = 0;
        }
        deficit.removeTemporaryMod(EXT_TRADE_ID);
        LinkedHashSet<String>toRemove = new LinkedHashSet<>();
        for (Map.Entry<String, MutableStat.StatMod> flatMod : deficit.getFlatMods().entrySet()) {
            if(flatMod.getKey().contains("aotd_shortage_counter")){
                toRemove.add(flatMod.getKey());
            }
        }
        toRemove.forEach(x->deficit.removeTemporaryMod(x));
        deficit.removeTemporaryMod(DEF_FROM_NEW_IND);
        excess.removeTemporaryMod(EXT_TRADE_ID);
    }public void applyExternalTrade(int deficitAmt, int excessAmt, float days, AoTDCommodityOnMarket com) {
        if (deficitAmt > 0) deficit.addTemporaryModFlat(days, EXT_TRADE_ID,"Unable to import due to Global Deficit.", deficitAmt);
        else deficit.removeTemporaryMod(EXT_TRADE_ID);

        if (excessAmt > 0) {
            if(com.getCommoditySpec().hasTag(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS)){
                return;
            }
            else{
                if(com.getCommoditySpec().hasTag(AoTDTradeTags.AOTD_NO_ONE_BUYS_OUTSIDE)){
                    excess.addTemporaryModFlat(days, EXT_TRADE_ID,"Unable to export due to Global Excess.", excessAmt);
                }
                else{
                    int soldMax = excessAmt/2;
                    AoTDTradeManager.getInstance().getMarketData(com.getMarket()).addExtraSold(com.getCommoditySpec().getId(),soldMax);
                    excess.addTemporaryModFlat(days, EXT_TRADE_ID,"Unable to export due to Global Excess.", soldMax);

                }


            }


        }

        else excess.removeTemporaryMod(EXT_TRADE_ID);
    }
    public void applyDeficitDueToSuddenChangeOfDemand(AoTDCommodityOnMarket commodity){

        int currDemand = commodity.getSupplyDemandData().getDemandExceptPendingIndustries(commodity.getMarket());

        int recoreded = (int) recordedDemandFromNonPendingThisMonth;
        if(AoTDEconomy.runningPrePlayerEconomy){
            this.recordedDemandFromNonPendingThisMonth = currDemand;
            return;
        }
        //This is to prevent early stage deficits due to placement of industries via other mods
        if(!commodity.getMarket().isPlayerOwned()&& Global.getSector().getClock().getMonth()<=3&&Global.getSector().getClock().getCycle()<=206){
            this.recordedDemandFromNonPendingThisMonth = currDemand;
            return;
        }
        int diff =currDemand - recoreded;
        if(commodity.getCommoditySpec().getId().equals(Commodities.SUPPLIES)){
            String hehe = "ege";
        }
        if(diff>0){
            deficit.addTemporaryModFlat(31,DEF_FROM_NEW_IND,"Sudden surge of demand", diff);
        }
        else{
            deficit.removeTemporaryMod(DEF_FROM_NEW_IND);
        }

    }



    /** Adds/refreshes a temporary excess mod. */
    public void setExcess(int excessAmount, AoTDCommodityOnMarket commodity, float days, String id) {
        this.excess.addTemporaryModFlat(days, id,"Unable to export due to Global Excess.", excessAmount);
    }

    public int getDeficit() {
        return deficit.getModifiedInt();
    }

    /** Adds/refreshes a temporary deficit mod. */
    public void setDeficit(int deficitAmount, AoTDCommodityOnMarket commodity, float days, String id) {
        this.deficit.addTemporaryModFlat(days, id,"Unable to import due to Global Deficit.", deficitAmount);
    }

    public int setstockPileRecordedWhenEventHappened(int stockPile) {
        return stockPileRecordedWhenEventHappened;
    }

    /** Clears ALL mods (use carefully). */
    public void reset() {
        deficit.unmodifyFlat(EXT_TRADE_ID);
        excess.unmodify(EXT_TRADE_ID);
    }

    public void resetDeficit() {
        deficit.unmodify();
    }

    public void resetExcess() {
        excess.unmodify();
    }



    public int getStockPileRecordedWhenEventHappened() {
        return stockPileRecordedWhenEventHappened;
    }

    public int getEffectiveDeficit(AoTDCommodityOnMarket commodity) {
        return (int) Math.max(0, deficit.getModifiedInt()-excess.getModifiedInt());
    }

    public int getEffectiveExcess(AoTDCommodityOnMarket commodity) {
        return (int) Math.max(0, excess.getModifiedInt()-deficit.getModifiedInt());
    }

    public void advance(float days) {
        excess.advance(days);
        deficit.advance(days);
    }
}
