package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDMarketDemandData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class AoTDEconomy extends Economy {
    public static boolean runningPrePlayerEconomy = false;
    public static boolean mustPruneCommodities = true;
    public static AoTDEconomy getInstance(){
        if(Global.getSector().getEconomy() instanceof AoTDEconomy){
            return (AoTDEconomy)Global.getSector().getEconomy();
        }
        return null;
    }
    public void doEconomyStepOnNewGameLoad(){
        AoTDEconomyReachStepper stepper = (AoTDEconomyReachStepper) getStepper();
        stepper.doEconomyTick();
        for (MarketAPI market : getMarkets()) {
            AoTDIndustryData data = AoTDIndustryData.getInstance(market);
            data.applyEndOfMonthChange(market);
            for (CommodityOnMarketAPI allCommodity : market.getAllCommodities()) {
                if(allCommodity instanceof AoTDCommodityOnMarket commodity){
                    commodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(commodity);
                }
            }
        }

    }
    public AoTDEconomy(boolean b, Economy currentEconomyToReplace) {
        super(b);
        ArrayList<MarketAPI>current = new ArrayList<>(currentEconomyToReplace.getMarkets());
        this.setEcon(new AoTDReachEconomy());
        ReflectionUtilis.setPrivateVariableFromSuperclass("stepper",this,new AoTDEconomyReachStepper(this.getEconomy()));
        this.getMarkets().addAll(current);
        current.clear();


        this.getUpdateListeners().addAll(currentEconomyToReplace.getUpdateListeners());
        currentEconomyToReplace.getUpdateListeners().clear();
        for (MarketAPI market : getMarkets()) {
            market.clearCommodities();
            initCommodities((Market) market);
        }
    }

    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        Iterator var3 = this.getMarkets().iterator();

        while(var3.hasNext()) {
            MarketAPI var2 = (MarketAPI)var3.next();
            ((Market)var2).updatePrevStability();
        }

        MainWorkTask.EconWorkParams var4 = new MainWorkTask.EconWorkParams();
        var4.withIncomeAndUpkeep = false;
        var4.withStockpileUpdate = true;
        var4.withImmigration = true;
        if (econWorkParams != null) {
            var4 = econWorkParams;
        }
        this.getEconomy().nextStep(var4);
    }

    @Override
    public void removeMarket(MarketAPI marketAPI) {
        super.removeMarket(marketAPI);
        for (AoTDFactionTradeData value : AoTDTradeManager.getInstance().getAllFactionTradeData().values()) {
            value.removeMarket(marketAPI);
        }
    }

    @Override
    public void addMarket(MarketAPI marketAPI, boolean addJunk) {
        super.addMarket(marketAPI, addJunk);
        Market market = (Market) marketAPI;
        market.clearCommodities();
        initCommodities(market);
        if(!market.hasCondition("aotd_toolbox_food_corrector")){
            market.addCondition("aotd_toolbox_food_corrector");
        }
    }

    public void runMarketAdjustmentAfterEconomyCreation(){
        for (MarketAPI market : getMarkets()) {
            market.clearCommodities();
            initCommodities((Market) market);
            if(!market.hasCondition("aotd_toolbox_food_corrector")){
                market.addCondition("aotd_toolbox_food_corrector");
            }
        }
    }
    @Override
    public void tripleStep() {
        super.tripleStep();
    }

    @Override
    public void updatePriceMult(MarketAPI marketAPI) {
        super.updatePriceMult(marketAPI);
    }
    public static void pruneCommodities(){
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            pruneCommoditiesThatMightAppear((Market) market);
        }
    }
    public static void pruneCommoditiesThatMightAppear(Market market){
        ArrayList<String>newCommoditiesToAdd = new ArrayList<>();
        ArrayList<CommodityOnMarket>list = (ArrayList<CommodityOnMarket>) ReflectionUtilis.getPrivateVariableFromSuperClass("commodities",market);
        for (CommoditySpecAPI allCommoditySpec : Global.getSettings().getAllCommoditySpecs()) {
            if(list.stream().noneMatch(x->x.getId().equals(allCommoditySpec.getId()))) {
                newCommoditiesToAdd.add(allCommoditySpec.getId());
            }
        }
        HashMap<String,CommodityOnMarket>commodityMap = (HashMap<String, CommodityOnMarket>) ReflectionUtilis.getPrivateVariableFromSuperClass("commodityMap",market);

        Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            CommodityOnMarket data = (CommodityOnMarket) iterator.next();
            if(!(data instanceof AoTDCommodityOnMarket)){
                newCommoditiesToAdd.add(data.getId());
                iterator.remove();
            }
        }
        for (String s : newCommoditiesToAdd) {
            AoTDCommodityOnMarket data = new AoTDCommodityOnMarket(market,s);
            commodityMap.put(s,data);
            list.add(data);
        }

    }

    public void initCommodities(Market market) {
        ArrayList<CommodityOnMarket>list = (ArrayList<CommodityOnMarket>) ReflectionUtilis.getPrivateVariableFromSuperClass("commodities",market);
        HashMap<String,CommodityOnMarket>commodityMap = (HashMap<String, CommodityOnMarket>) ReflectionUtilis.getPrivateVariableFromSuperClass("commodityMap",market);
        ReflectionUtilis.setPrivateVariableFromSuperclass("demandData",market,new AoTDMarketDemandData(market));
        for (CommoditySpecAPI commoditySpecAPI : Global.getSettings().getAllCommoditySpecs()) {
            market.getDemandData().getDemand(commoditySpecAPI.getDemandClass());
            AoTDCommodityOnMarket data = new AoTDCommodityOnMarket(market,commoditySpecAPI.getId());
            data.getSupplyDemandData();
            commodityMap.put(commoditySpecAPI.getId(),data);
            list.add(data);
            ReflectionUtilis.invokeMethodWithAutoProjection("addToDemandClassList",market,(CommodityOnMarket)data);
        }
        Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            CommodityOnMarket data = (CommodityOnMarket) iterator.next();
            if(!(data instanceof AoTDCommodityOnMarket)){
                iterator.remove();
            }
        }
        for (CommodityOnMarket commodityOnMarket : list) {
            commodityMap.put(commodityOnMarket.getId(),commodityOnMarket);
        }


        market.getAllCommodities();

    }
}
