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
import java.util.List;

public class AoTDEconomy extends Economy {
    private static final String AOTD_FOOD_CORRECTOR_COND_ID = "aotd_toolbox_food_corrector";

    public static boolean runningPrePlayerEconomy = false;
    public static boolean mustPruneCommodities = true;

    public static AoTDEconomy getInstance(){
        if(Global.getSector().getEconomy() instanceof AoTDEconomy econ){
            return econ;
        }
        return null;
    }

    public void doEconomyStepOnNewGameLoad(){
        ((AoTDEconomyReachStepper) getStepper()).doEconomyTick();

        for (MarketAPI market : getMarkets()) {
            AoTDIndustryData.getInstance(market).applyEndOfMonthChange(market);

            for (CommodityOnMarketAPI allCommodity : market.getAllCommodities()) {
                if(allCommodity instanceof AoTDCommodityOnMarket commodity){
                    commodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(commodity);
                }
            }
        }
    }

    /** TODO I made this actually thread safe by setting it as synchronized, but was this intended? */
    public synchronized MarketAPI getMarketThreadSave(String id){
        for (MarketAPI market : getMarkets()) {
            if(market.getId().equals(id)){
                return market;
            }
        }
        return null;
    }

    public AoTDEconomy(boolean isSimMode, Economy econToReplace) {
        super(isSimMode);

        final AoTDReachEconomy reach = new AoTDReachEconomy();
        setEcon(reach);
        ReflectionUtilis.setPrivateVariableFromSuperclass("stepper", this, new AoTDEconomyReachStepper(reach));

        getMarkets().addAll(econToReplace.getMarkets());
        getUpdateListeners().addAll(econToReplace.getUpdateListeners());
        econToReplace.getMarkets().clear();
        econToReplace.getUpdateListeners().clear();

        for (MarketAPI market : getMarkets()) {
            market.clearCommodities();
            initCommodities((Market) market);
        }
    }

    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        for (MarketAPI market : getMarkets()) {
            ((Market) market).updatePrevStability();
        }

        final MainWorkTask.EconWorkParams workParams;
        if (econWorkParams == null) {
            workParams = new MainWorkTask.EconWorkParams();
            workParams.withIncomeAndUpkeep = false;
            workParams.withStockpileUpdate = true;
            workParams.withImmigration = true;
        } else {
            workParams = econWorkParams;
        }
        
        final long time = System.nanoTime();
        getEconomy().nextStep(workParams);
        final long diff = (System.nanoTime() - time) / 1_000_000l;
        Global.getLogger(getClass()).error(diff + " ms");
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
        final Market market = (Market) marketAPI;

        market.clearCommodities();
        initCommodities(market);

        if(!market.hasCondition(AOTD_FOOD_CORRECTOR_COND_ID)){
            market.addCondition(AOTD_FOOD_CORRECTOR_COND_ID); // TODO applied automatically when added
        }
    }

    public void runMarketAdjustmentAfterEconomyCreation(){
        for (MarketAPI market : getMarkets()) {
            market.clearCommodities();
            initCommodities((Market) market);

            if(!market.hasCondition(AOTD_FOOD_CORRECTOR_COND_ID)){
                market.addCondition(AOTD_FOOD_CORRECTOR_COND_ID);
            }
        }
    }

    @Override
    public void tripleStep() {
        super.nextStep();
    }

    @Override
    public void updatePriceMult(MarketAPI marketAPI) {
        super.updatePriceMult(marketAPI);
    }

    // FIXME this is unused
    // private static void pruneCommodities() {
    //     for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
    //         pruneCommoditiesThatMightAppear((Market) market);
    //     }
    // }

    @SuppressWarnings("unchecked")
    public static void pruneCommoditiesThatMightAppear(Market market){
        final ArrayList<String> newCommoditiesToAdd = new ArrayList<>();
        final List<CommodityOnMarket> commodities = market.getCommodities();
        final var commodityMap = (HashMap<String, CommodityOnMarket>) ReflectionUtilis
            .getPrivateVariableFromSuperClass("commodityMap", market);

        for (CommoditySpecAPI allCommoditySpec : Global.getSettings().getAllCommoditySpecs()) {
            if(commodities.stream().noneMatch(x->x.getId().equals(allCommoditySpec.getId()))) {
                newCommoditiesToAdd.add(allCommoditySpec.getId());
            }
        }

        final Iterator<CommodityOnMarket> comIter = commodities.iterator();
        while (comIter.hasNext()) {
            final CommodityOnMarket data = comIter.next();
            if(!(data instanceof AoTDCommodityOnMarket)){
                newCommoditiesToAdd.add(data.getId());
                comIter.remove();
            }
        }

        for (String s : newCommoditiesToAdd) {
            final AoTDCommodityOnMarket data = new AoTDCommodityOnMarket(market,s);
            commodityMap.put(s,data);
            commodities.add(data);
        }
    }

    @SuppressWarnings("unchecked")
    public void initCommodities(Market market) {
        final List<CommodityOnMarket> commodities = market.getCommodities();
        final var commodityMap = (HashMap<String, CommodityOnMarket>) ReflectionUtilis
            .getPrivateVariableFromSuperClass("commodityMap", market);

        final AoTDMarketDemandData demandData = new AoTDMarketDemandData(market);
        ReflectionUtilis.setPrivateVariableFromSuperclass("demandData", market, demandData);

        demandData.replaceWithAoTDMarketDemand(Global.getSettings().getAllCommoditySpecs());

        for (CommoditySpecAPI comSpec : Global.getSettings().getAllCommoditySpecs()) {
            final AoTDCommodityOnMarket data = new AoTDCommodityOnMarket(market,comSpec.getId());

            commodityMap.put(comSpec.getId(),data);
            commodities.add(data);
            ReflectionUtilis.invokeMethodWithAutoProjection("addToDemandClassList", market, (CommodityOnMarket)data);
        }

        commodities.removeIf(c -> !(c instanceof AoTDCommodityOnMarket));
        for (CommodityOnMarket com : commodities) {
            commodityMap.put(com.getId(), com);
        }

        market.getAllCommodities(); // FIXME is this needed?
    }
}