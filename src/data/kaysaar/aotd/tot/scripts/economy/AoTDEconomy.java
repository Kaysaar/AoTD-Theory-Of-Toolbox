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

import java.util.*;

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
    public MarketAPI getMarketThreadSave(String id){
        for (MarketAPI market : getMarkets()) {
            if(market.getId().equals(id)){
                return market;
            }
        }
        return null;
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
        for (MarketAPI market : getMarkets()) ((Market)market).updatePrevStability();

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
    public void doubleStep() {
        super.nextStep();
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
            market.getCondition("aotd_toolbox_food_corrector").getPlugin().apply(null);
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
        super.nextStep();
    }

    public static void pruneCommodities(){
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            pruneCommoditiesThatMightAppear((Market) market);
        }
    }

    public static void pruneCommoditiesThatMightAppear(Market market) {
        List<CommodityOnMarket> commodities = getCommodities(market);

        ensureAoTDDemandData(market);

        /*
         * Preserve already-converted AoTD commodities where possible.
         * Replace vanilla CommodityOnMarket entries and add any missing commodity specs.
         */
        Map<String, AoTDCommodityOnMarket> byId = new HashMap<>();

        for (CommodityOnMarket commodity : new ArrayList<>(commodities)) {
            if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                byId.put(aotdCommodity.getId(), aotdCommodity);
            }
        }

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            AoTDCommodityOnMarket commodity = byId.get(spec.getId());

            if (commodity == null) {
                commodity = new AoTDCommodityOnMarket(market, spec.getId());
                commodity.getSupplyDemandData();
                byId.put(spec.getId(), commodity);
            }
        }

        commodities.clear();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            commodities.add(byId.get(spec.getId()));
        }

        rebuildCommodityLookupMaps(market, commodities);
        market.getAllCommodities();
    }

    public static void initCommodities(Market market) {
        List<CommodityOnMarket> commodities = getCommodities(market);

        commodities.clear();

        ReflectionUtilis.setPrivateVariableFromSuperclass("demandData", market, new AoTDMarketDemandData(market));

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            market.getDemandData().getDemand(spec.getDemandClass());

            AoTDCommodityOnMarket commodity = new AoTDCommodityOnMarket(market, spec.getId());
            commodity.getSupplyDemandData();

            commodities.add(commodity);
        }

        rebuildCommodityLookupMaps(market, commodities);
        market.getAllCommodities();
    }

    private static void ensureAoTDDemandData(Market market) {
        Object demandData = ReflectionUtilis.getPrivateVariableFromSuperClass("demandData", market);

        if (!(demandData instanceof AoTDMarketDemandData)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("demandData", market, new AoTDMarketDemandData(market));
        }
    }

    @SuppressWarnings("unchecked")
    private static void rebuildCommodityLookupMaps(Market market, List<CommodityOnMarket> commodities) {
        Map<String, CommodityOnMarket> commodityMap = (Map<String, CommodityOnMarket>) ReflectionUtilis
            .getPrivateVariableFromSuperClass("commodityMap", market);

        if (commodityMap == null) {
            commodityMap = new HashMap<>();
            ReflectionUtilis.setPrivateVariableFromSuperclass("commodityMap", market, commodityMap);
        }

        var commoditiesByDemandClass = (Map<String, List<CommodityOnMarket>>) ReflectionUtilis.
            getPrivateVariableFromSuperClass("commoditiesByDemandClass", market);

        if (commoditiesByDemandClass == null) {
            commoditiesByDemandClass = new HashMap<>();
            ReflectionUtilis.setPrivateVariableFromSuperclass("commoditiesByDemandClass", market, commoditiesByDemandClass);
        }

        commodityMap.clear();
        commoditiesByDemandClass.clear();

        for (CommodityOnMarket commodity : commodities) {
            if (!(commodity instanceof AoTDCommodityOnMarket)) {
                /*
                 * This should never happen after prune/init, but keep this guard so
                 * getCommoditiesWithClass() cannot return vanilla CommodityOnMarket.
                 */
                continue;
            }

            commodityMap.put(commodity.getId(), commodity);

            String demandClass = ((AoTDCommodityOnMarket) commodity).getSpec().getDemandClass();
            List<CommodityOnMarket> demandClassCommodities = commoditiesByDemandClass.get(demandClass);

            if (demandClassCommodities == null) {
                demandClassCommodities = new ArrayList<>();
                commoditiesByDemandClass.put(demandClass, demandClassCommodities);
            }

            demandClassCommodities.add(commodity);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<CommodityOnMarket> getCommodities(final MarketAPI market) {
        List<CommodityOnMarket> commodities = (List<CommodityOnMarket>) ReflectionUtilis
            .getPrivateVariableFromSuperClass("commodities", market);

        if (commodities == null) {
            commodities = new ArrayList<>();
            ReflectionUtilis.setPrivateVariableFromSuperclass("commodities", market, commodities);
        }

        return commodities;
    }
}