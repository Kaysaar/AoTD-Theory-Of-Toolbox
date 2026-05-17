package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.impl.campaign.econ.CommodityIconCounts;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.PriceCalculator;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask2;
import com.fs.starfarer.campaign.econ.reach.ReachEconomy;

import java.util.*;

import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityMarketData;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDMarketDemandData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import static data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy.pruneCommoditiesThatMightAppear;

public class AoTdMainWorkTask2 extends MainWorkTask2 {
    public AoTdMainWorkTask2(List<MarketAPI> list, ReachEconomy reachEconomy, MainWorkTask.EconWorkParams econWorkParams) {
        super(list, reachEconomy, econWorkParams);

    }




    @Override
    public void doNextBatch() {
        boolean started = (boolean) ReflectionUtilis.getPrivateVariableFromSuperClass("started",this);
        if (!started) {
            initCommodityList();
            ReflectionUtilis.setPrivateVariableFromSuperclass("index",this,0);
            started = true;
            ReflectionUtilis.setPrivateVariableFromSuperclass("started",this,started);

            return;
        }

        if (isDone()) return;
        List<MarketAPI> markets = (List<MarketAPI>) ReflectionUtilis.getPrivateVariableFromSuperClass("markets",this);
        int marketIndex = (int) ReflectionUtilis.getPrivateVariableFromSuperClass("marketIndex",this);
        // Step 1: Reapply one market per tick
        if (marketIndex < markets.size()) {

            MarketAPI market = markets.get(marketIndex);
            pruneCommoditiesThatMightAppear((Market) market);
            market.reapplyConditions();
            for (Industry industry : market.getIndustries()) {
                if(!AoTDIndustryData.getInstance(industry.getMarket()).isPending(industry.getId())){
                    industry.reapply();;
                }
            }
            marketIndex++;
            ReflectionUtilis.setPrivateVariableFromSuperclass("marketIndex",this,marketIndex);
            return;
        }
        List<String>commodities = (List<String>) ReflectionUtilis.getPrivateVariableFromSuperClass("commodities",this);
        int index = (int) ReflectionUtilis.getPrivateVariableFromSuperClass("index",this);
        // Step 2: Process next commodity
        String commodityId = (String) commodities.get(index);
        CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(commodityId);
        index++;
        ReflectionUtilis.setPrivateVariableFromSuperclass("index",this,index);

        // Collect unique economy groups
        LinkedHashSet<String> econGroups = new LinkedHashSet<>();
        for (MarketAPI market : markets) {
            String econGroup = market.getEconGroup();
            if (econGroup != null) {
                econGroups.add(econGroup);
            }
        }

        // Build global commodity data (no econ group)
        new AoTDCommodityMarketData(commodityId, null);

        // Build per econ-group market data
        for (String econGroup : econGroups) {
            new AoTDCommodityMarketData(commodityId, econGroup);
        }
        MainWorkTask.EconWorkParams params = (MainWorkTask.EconWorkParams) ReflectionUtilis.getPrivateVariableFromSuperClass("params",this);
        // Update stockpiles + prices
        if (params.withStockpileUpdate) {
            for (MarketAPI market : markets) {
                aotdUpdateStockpileAndPrice((Market) market, commoditySpec);
            }
        }

        // Notify economy listeners
        List<EconomyAPI.EconomyUpdateListener> listeners =
                Global.getSector().getEconomy().getUpdateListeners();

        for (EconomyAPI.EconomyUpdateListener listener : new ArrayList<>(listeners)) {

            if (listener.isEconomyListenerExpired()) {
                Global.getSector().getEconomy().removeUpdateListener(listener);
            } else {
                listener.commodityUpdated(commodityId);
            }
        }
    }

    public static List<CommodityOnMarket> getCommoditiesWithSameDemandClass(String demandClass,MarketAPI market){
        ArrayList<CommodityOnMarket> commodities = new ArrayList<>();
        for (CommodityOnMarketAPI allCommodity : market.getAllCommodities()) {
            if(allCommodity.getDemandClass().equals(demandClass)){
                commodities.add((CommodityOnMarket) allCommodity);
            }
        }
        return commodities;

    }

    public void aotdUpdateStockpileAndPrice(Market var0, CommoditySpecAPI var1) {

        float var2 = 0.0F;
        float var3 = 1.0F;
        if(!(var0.getDemandData() instanceof AoTDMarketDemandData)){
            ReflectionUtilis.setPrivateVariableFromSuperclass("demandData",var0,new AoTDMarketDemandData(var0));
        }
        Random var4 = new Random((long)(var0.getId().hashCode() + var1.getId().hashCode() + Global.getSector().getClock().getMonth() * 170000));
        List var5 = getCommoditiesWithSameDemandClass(var1.getDemandClass(),var0);
        float var6 = 0.0F;
        float var7 = Economy.ECONOMY_GREED_FRACTION;
        boolean var8 = false;
        String var9 = "core";
        Iterator var11 = var5.iterator();

        CommodityOnMarket var10;
        CommodityIconCounts var12;
        float var13;
        float var14;
        while(var11.hasNext()) {
            var10 = (CommodityOnMarket)var11.next();

            if (var1.isPrimary()) {
                if((var10 instanceof AoTDCommodityOnMarket aoTDCommodityOnMarket)){

                    var12 = new CommodityIconCounts(var10);
                    if(aoTDCommodityOnMarket.getMarket().isPlayerOwned()){
                        String he = "he";
                    }
                    aoTDCommodityOnMarket.getSupplyDemandData().updateSupplyDemandData(var0);
                    float dem = aoTDCommodityOnMarket.getSupplyDemandData().getTotalRawUnitsFromDemand();
                    float sup = aoTDCommodityOnMarket.getSupplyDemandData().getTotalRawUnitsFromSupply();
                    aoTDCommodityOnMarket.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(aoTDCommodityOnMarket);
                    aoTDCommodityOnMarket.setStocks((int) Math.max(dem*0.8f,sup));
                    var8 = var10.getMaxDemand() <= 0 && var10.getMaxSupply() <= 0;
                    var6 = dem;
                    if(AoTDTradeManager.getInstance().getMarketData(var0)!=null){
                        var6+=AoTDTradeManager.getInstance().getMarketData(var0).getInternalExported(var1.getId());
                    }
                    if(var6<sup*0.8f){
                        var6 = sup*0.6f;
                    }
                    if (var8) {
                        var10.getPlayerDemandPriceMod().modifyMult(var9, Economy.ECONOMY_NO_DEMAND_PRICE_MULT);
                    } else {
                        var10.getPlayerDemandPriceMod().unmodifyMult(var9);
                    }
                    var6*=0.95F + 0.1F * var4.nextFloat();

                    var10.getDemand().getDemand().modifyFlat(var9, var6*(1-var7));
                    var10.getGreed().modifyFlat(var9, var6 * var7);
                }
                else{
                    var12 = new CommodityIconCounts(var10);
                    var13 = (float)(var12.production - var12.inFactionOnlyExport - var12.canNotExport);
                    var14 = Math.max(var13, (float)var10.getMaxDemand()) + var2;
                    if (var14 < 1.0F) {
                        var14 = 1.0F;
                    }

                    var8 = var10.getMaxDemand() <= 0 && var10.getMaxSupply() <= 0;
                    var6 = getStockpileQuantity(var10, var14) * var3 + (float)Economy.MIN_STOCKPILE_FOR_PRICING * 2.0F;
                    var6 *= 0.95F + 0.1F * var4.nextFloat();
                    if (var8) {
                        var10.getPlayerDemandPriceMod().modifyMult(var9, Economy.ECONOMY_NO_DEMAND_PRICE_MULT);
                    } else {
                        var10.getPlayerDemandPriceMod().unmodifyMult(var9);
                    }

                    var10.getDemand().getDemand().modifyFlat(var9, var6 * (1.0F - var7));
                    var10.getGreed().modifyFlat(var9, var6 * var7);
                }

                break;
            }
        }

        var11 = var5.iterator();

        while(var11.hasNext()) {
            var10 = (CommodityOnMarket)var11.next();
            if (!var1.isPrimary()) {
                if (var8) {
                    var10.getPlayerDemandPriceMod().modifyMult(var9, Economy.ECONOMY_NO_DEMAND_PRICE_MULT);
                } else {
                    var10.getPlayerDemandPriceMod().unmodifyMult(var9);
                }

                var10.getGreed().modifyFlat(var9, var6 * var7);
            }
        }

        var11 = var5.iterator();

        float var17;
        float var18;
        float var19;
        float var20;
        float var21;
        float var22;
//        while(var11.hasNext()) {
//            var10 = (CommodityOnMarket)var11.next();
//            if (var10.getCommodity().isPrimary()) {
//                var12 = new CommodityIconCounts(var10);
//                var13 = (float)var12.deficit;
//                var14 = (float)(var12.inFactionOnlyExport + var12.canNotExport);
//                var13 *= 1.0F;
//                var14 *= 1.0F;
//                int var15 = Math.min(var10.getAvailable(), var10.getMaxSupply());
//                byte var16 = 0;
//                if (var8) {
//                    var16 = 1;
//                }
//
//                var17 = getStockpileQuantity(var10, (float)(var10.getAvailable() + var16)) * var3;
//                var18 = 0.5F;
//                var19 = (float)var15 * 0.025F;
//                var20 = Math.max(3.0F, (float)var10.getCommodityMarketData().getMaxExportGlobal());
//                var21 = 10.0F;
//                var22 = 0.5F * var21 / var20;
//                var18 = var22 * var21;
//                var19 = (float)var15 * var22;
//                if (var19 > var18) {
//                    var19 = var18;
//                }
//
//                var17 += var10.getCommodity().getEconUnit() * var19;
//                var17 *= 0.95F + 0.1F * var4.nextFloat();
//                var10.setStockpile(var17);
//            }
//        }

        var11 = var5.iterator();

        while(var11.hasNext()) {
            var10 = (CommodityOnMarket)var11.next();
            if (var10 instanceof AoTDCommodityOnMarket com) {

                com.updateCalc();

                float demandRaw = Math.max(1f, com.getDemand().getDemandValue());
                float missingRaw = com.getDefQuantity()-com.getCombinedTradeValue();

                PriceCalculator demandPrice = com.getDemandPrice();
                PriceCalculator supplyPrice = com.getSupplyPrice();

                if (missingRaw > 0f&&com.getDef()>0) {
                    float mult = 1f + (missingRaw / com.getCommoditySpec().getEconUnit());
                    mult = Math.min(mult, 2.5f);

                    float missing = com.getDefQuantity()-Math.min(0,com.getCombinedTradeValue());
                    demandPrice.setHighPriceThreshold(missing+com.getStocks());
                    demandPrice.setHighPriceMult(mult);
                    supplyPrice.setHighPriceThreshold(missing+com.getStocks());
                    supplyPrice.setHighPriceMult(mult*1.1f);
                } else {
                    demandPrice.setHighPriceThreshold(-1f);
                    demandPrice.setHighPriceMult(1f);

                    supplyPrice.setHighPriceThreshold(-1f);
                    supplyPrice.setHighPriceMult(1f);
                }

                if (missingRaw <= 0 &&com.getDef()>0 &&com.getCombinedTradeValue() > 0) {
                     var19 = Economy.DEFICIT_PRICE_INCR_PER_UNIT;
                    float mult = 1f + (com.getDefQuantity() / demandRaw);
                    mult = Math.max(mult, 1.6f);
                    supplyPrice.setHighPriceThreshold((com.getCombinedTradeValue())+com.getStocks());
                    supplyPrice.setHighPriceMult(mult);

                }
                float demandThreshold = Math.max(com.getSupplyDemandData().getTotalRawUnitsFromSupply(),com.getSupplyDemandData().getTotalRawUnitsFromDemand());
                float excess = com.getExcessQuantity();
                if (excess > 0 &&com.getExc()>0) {
                    // Want at most 20–30% below base => clamp multiplier to [0.70 .. 1.00]
                    // Ratio of surplus relative to threshold:
                    float r = (excess+demandThreshold)/demandThreshold;
                    float per = r-1f;
                    float discount = Math.max(0.5f,per) ;  // ramps to 30% discount by r>=1
                    float mult = 1.0f - discount;               // 1..0.70

                    // IMPORTANT: lowT is the stock LEVEL where surplus begins:
                    supplyPrice.setLowPriceThreshold(com.getStocks()-excess);
                    supplyPrice.setLowPriceMult(mult);

                    // Optional anti-resell: also reduce SELL price when market is flooded
                    demandPrice.setLowPriceThreshold(-excess+com.getStocks());
                    demandPrice.setLowPriceMult(mult*0.7f);
                } else if (com.getCombinedTradeValue()>0&&com.getExcessQuantity()>0) {
                    excess = com.getExcessQuantity();
                    // Ratio of surplus relative to threshold:
                    float r = (excess+demandThreshold)/demandThreshold;
                    float per = r-1f;
                    float discount = Math.max(0.5f,per) ;  // ramps to 30% discount by r>=1
                    float mult = 1.0f - discount;               // 1..0.70

                    // IMPORTANT: lowT is the stock LEVEL where surplus begins:
                    supplyPrice.setLowPriceThreshold(com.getStocks()-excess);
                    supplyPrice.setLowPriceMult(mult);

                    // Optional anti-resell: also reduce SELL price when market is flooded
                    demandPrice.setLowPriceThreshold(-excess+com.getStocks());
                    demandPrice.setLowPriceMult(mult*0.7f);
                } else  if(com.getCombinedTradeValue()<0){
                        demandPrice.setLowPriceThreshold(com.getStocks()+com.getCombinedTradeValue());
                        float r = (excess+demandThreshold)/demandThreshold;
                        float per = r-1f;
                        float discount = Math.max(0.5f,per) ;  // ramps to 30% discount by r>=1
                        float mult = 1.0f - discount;               // 1..0.70
                        demandPrice.setLowPriceMult(mult);

                }
                else{
                    supplyPrice.setLowPriceThreshold(-1f);
                    supplyPrice.setLowPriceMult(1f);
                    demandPrice.setLowPriceThreshold(-1f);
                    demandPrice.setLowPriceMult(1f);
                }


            }


            else{
                var10.updateCalc();
                var12 = new CommodityIconCounts(var10);
                PriceCalculator var28 = var10.getDemandPrice();
                PriceCalculator var30 = var10.getSupplyPrice();
                float var29 = (float)var12.deficit;
                float var31 = (float)var12.extra;
                var17 = var10.getStockpile();
                var18 = var1.getEconUnit();
                var19 = Economy.DEFICIT_PRICE_INCR_PER_UNIT;
                var20 = Economy.EXCESS_PRICE_DECR_PER_UNIT;
                var21 = Economy.DEFICIT_PRICE_MULT_MAX;
                var22 = Economy.EXCESS_PRICE_MULT_MIN;
                float var23;
                float var24;
                if (var29 > 0.0F) {
                    var23 = var17 + var29 * var18;
                    var24 = 1.0F + Math.max(1.0F, var29) * var19;
                    if (var24 > var21) {
                        var24 = var21;
                    }

                    var28.setHighPriceThreshold(var23);
                    var28.setHighPriceMult(var24);
                    var30.setHighPriceThreshold(var23);
                    var30.setHighPriceMult(var24);
                } else {
                    var28.setHighPriceThreshold(-1.0F);
                    var28.setHighPriceMult(1.0F);
                    var30.setHighPriceThreshold(-1.0F);
                    var30.setHighPriceMult(1.0F);
                }

                var23 = var10.getCombinedTradeModQuantity();
                var24 = var10.getModValueForQuantity(var23);
                float var25;
                float var26;
                if (var29 <= 0.0F && var24 > 0.0F) {
                    var25 = var10.getTradeMod().getModifiedValue() + var10.getTradeModPlus().getModifiedValue();
                    if (var25 > 0.0F) {
                        var26 = Math.max(0.0F, var17 - var31 * var18);
                        float var27 = 1.0F + Math.max(1.0F, 1.0F) * var19;
                        if (var27 > var21) {
                            var27 = var21;
                        }

                        var30.setHighPriceThreshold(var26);
                        var30.setHighPriceMult(var27);
                    }
                }

                if (var31 > 0.0F) {
                    var25 = var17 - var31 * var18;
                    if (var25 < 0.0F) {
                        var25 = 0.0F;
                    }

                    var26 = 1.0F - Math.max(1.0F, var31) * var20;
                    if (var26 < var22) {
                        var26 = var22;
                    }

                    var28.setLowPriceThreshold(var25);
                    var28.setLowPriceMult(var26);
                    var30.setLowPriceThreshold(var25);
                    var30.setLowPriceMult(var26);
                } else {
                    var28.setLowPriceThreshold(-1.0F);
                    var28.setLowPriceMult(1.0F);
                    var30.setLowPriceThreshold(-1.0F);
                    var30.setLowPriceMult(1.0F);
                }
            }

        }


    }






}
