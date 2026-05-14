package data.kaysaar.aotd.tot.scripts.trade.manager;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.listeners.AoTDCoreUIListener;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;

import java.util.*;

public class AoTDTradeManager {
    public static String memkey = "$aotd_trade_manager";
    public static boolean endOfMonth = false;
    LinkedHashMap<String, AoTDFactionTradeData>factionsTradeData = new LinkedHashMap<>();
    LinkedHashSet<String>possibleCommoditiesDemanded = new LinkedHashSet<>();
    public static float multFromSellingExcess = 0.01f;
    public LinkedHashSet<String> getPossibleCommoditiesDemandedOrSupplied() {
        return possibleCommoditiesDemanded;
    }
    public List<String> getPossibleCommoditiesDemandedOrSuppliedSorted(Comparator<String> comparator) {
        return possibleCommoditiesDemanded.stream().sorted(comparator).toList();
    }
    public static int getExportIncome(CommodityOnMarketAPI comOnMarket) {
        if(comOnMarket instanceof AoTDCommodityOnMarket){
            if(AoTDCoreUIListener.isInCore){
                return AoTDToolboxMisc.getExpectedMonthlyIncomeFromCommodity((AoTDCommodityOnMarket) comOnMarket);

            }
            else{
                return AoTDToolboxMisc.getIncomeFromSelling((AoTDCommodityOnMarket) comOnMarket);

            }

        }
        return comOnMarket.getExportIncome();
    }



    public static AoTDTradeManager getInstance(){
        if(!Global.getSector().getPersistentData().containsKey(memkey)){
            Global.getSector().getPersistentData().put(memkey, new AoTDTradeManager());
        }
        return (AoTDTradeManager) Global.getSector().getPersistentData().get(memkey);
    }
    public void addMarket(MarketAPI market){
        if(!factionsTradeData.containsKey(market.getFaction().getId())){
            factionsTradeData.put(market.getFactionId(),new AoTDFactionTradeData(market.getFactionId()));
        }
        factionsTradeData.get(market.getFactionId()).addMarket(market);
    }
    public AoTDFactionTradeData getPlayerManager(){
        return factionsTradeData.get(Global.getSector().getPlayerFaction().getId());
    }
    public AoTDFactionTradeData getFactionTradeData(String factionId){
        if(!factionsTradeData.containsKey(factionId)){
            factionsTradeData.put(factionId, new AoTDFactionTradeData(factionId));
        }
        return factionsTradeData.get(factionId);
    }
    public AoTDMarketData getMarketData(MarketAPI market){
        try {
            return getFactionTradeData(market.getFactionId()).getTradeData().get(market.getId());
        }
        catch(Exception e){
            return null;
        }

    }
    public LinkedHashMap<String,AoTDFactionTradeData> getAllFactionTradeData(){
        return factionsTradeData;
    }



}
