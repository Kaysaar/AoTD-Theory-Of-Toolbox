package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.PriceCalculator;
import com.fs.starfarer.campaign.econ.reach.CommodityMarketData;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;

public class AoTDCommodityOnMarket extends CommodityOnMarket {

    public static float excessMult = 0.2f;

    public AoTDCommodityOnMarket(Market market, String commodityId) {
        super(market, commodityId);
        ReflectionUtilis.setPrivateVariableFromSuperclass("available", this, new AoTDAvailableStat(0f));

        getSupplyDemandData();
    }

    public int stocks;

    public void setStocks(int stocks) {
        this.stocks = stocks;
    }

    public int getStocks() {
        return stocks;
    }


    public void setDef(int def, float days, String deficitId, String desc) {
        this.getExcDefData().setDeficit(def, this, days, deficitId);
    }

    public void setExc(int exc, float days, String excessId, String desc) {
        this.getExcDefData().setExcess(exc, this, days, excessId);
    }


    @Override
    public void updateCalc() {
        CommoditySpecAPI spec = getCommoditySpec();
        getDemandPrice().setBasePrice(spec.getBasePrice());
        getDemandPrice().setVariability(spec.getPriceVariability());
        getDemandPrice().setDemand(getDemand().getDemandValue());
        getSupplyPrice().setBasePrice(spec.getBasePrice());
        getSupplyPrice().setVariability(spec.getPriceVariability());
        getSupplyPrice().setDemand(getDemand().getDemandValue() + getGreed().getModifiedInt());
    }

    @Override
    public void reapplyEventMod() {

    }


    public int getDef() {
        return Math.min(getExcDefData().getEffectiveDeficit(this), getAoTDAvailableStat().getSupplyDemandData(this).getTotalRawUnitsFromDemand());
    }

    public AoTDExcDefData getExcDefData() {
        return ((AoTDAvailableStat) getAvailableStat()).getData();
    }

    public void resetExcessOrDeficit() {
        getExcDefData().reset();
    }

    public int getExc() {
        return getExcDefData().getEffectiveExcess(this);
    }

    public int supply, demand;

    @Override
    public int getMaxDemand() {
        return Math.min(1, demand);
    }

    @Override
    public int getMaxSupply() {
        return supply;
    }

    @Override
    public void setMaxDemand(int i) {
        this.demand = i;
    }

    @Override
    public void setMaxSupply(int i) {
        this.supply = i;
    }


    public AoTDSupplyDemandData getSupplyDemandData() {
        return getAoTDAvailableStat().getSupplyDemandData(this);
    }

    public boolean doesImport() {
        return getSupplyDemandData().getExport(this) < 0;
    }

    public int getDefQuantity() {
        return getDef();
    }


    @Override
    public CommodityMarketData getCommodityMarketData() {
        Object com = ReflectionUtilis.getPrivateVariableFromSuperClass("commodityMarketData", this);
        if (com == null) {
            return new AoTDCommodityMarketData(this.getId(), this.getMarket().getEconGroup());
        } else if (!(com instanceof AoTDCommodityMarketData)) {
            this.setCommodityMarketData(new AoTDCommodityMarketData(this.getId(), this.getMarket().getEconGroup()));
        }
        return super.getCommodityMarketData();
    }


    Object readResolve() {

        if (this.getAvailableStat() == null) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("available", this, new AoTDAvailableStat(0f));

        }

        if (this.getTradeMod() == null) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("tradeMod", this, new MutableStatWithTempMods(0f));


        }

        if (this.getTradeModPlus() == null) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("tradeModPlus", this, new MutableStatWithTempMods(0f));

        }

        if (this.getTradeModMinus() == null) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("tradeModMinus", this, new MutableStatWithTempMods(0f));

        }
        ReflectionUtilis.invokeMethodWithAutoProjection("setCommodity", this, Global.getSettings().getCommoditySpec(this.getId()));
        if (this.getPlayerDemandPriceMod() == null) {
            ReflectionUtilis.invokeMethodWithAutoProjection("playerDemandMod", this, new StatBonus());
        }
        this.getSupplyDemandData().getEconSpec();


        if (this.getPlayerSupplyPriceMod() == null) {
            ReflectionUtilis.invokeMethodWithAutoProjection("playerSupplyMod", this, new StatBonus());
        }
        ReflectionUtilis.setPrivateVariableFromSuperclass("supplyPrice", this, new PriceCalculator());
        ReflectionUtilis.setPrivateVariableFromSuperclass("demandPrice", this, new PriceCalculator());
        PriceCalculator supply = (PriceCalculator) this.getSupplyPrice();
        PriceCalculator demand = (PriceCalculator) this.getDemandPrice();

        CommoditySpecAPI spec = getCommoditySpec();
        supply.setBasePrice(spec.getBasePrice());
        demand.setBasePrice(spec.getBasePrice());

        supply.setVariability(spec.getPriceVariability());
        demand.setVariability(spec.getPriceVariability());

// if you use demand in your curve:
        float rawDemand = getDemand().getDemand().getModifiedInt();
        supply.setDemand(rawDemand);
        demand.setDemand(rawDemand);

        this.updateCalc();
        return this;
    }

    public CommoditySpecAPI getCommoditySpec() {
        return Global.getSettings().getCommoditySpec(this.getId());
    }

    public AoTDAvailableStat getAoTDAvailableStat() {
        return (AoTDAvailableStat) this.getAvailableStat();
    }

    @Override
    public void updateMaxSupplyAndDemand() {
        supply = 0;
        demand = 0;
        for (Industry industry : getMarket().getIndustries()) {
            this.setDemandLegal(industry.isDemandLegal(this));
        }
        if (this.getAvailableStat().getBaseValue() != 0) {
            this.getAvailableStat().setBaseValue(0f);
        }
        ;
        this.getSupplyDemandData().updateSupplyDemandData(getMarket());
        supply = getSupplyDemandData().getEconSpec().getCalculationScript().convertRawUnitsToSupply(getSupplyDemandData().getTotalRawUnitsFromSupply(), getMarket(), this.getCommoditySpec().getId());
        demand = getSupplyDemandData().getEconSpec().getCalculationScript().convertRawUnitsToDemand(getSupplyDemandData().getTotalRawUnitsFromDemand(), getMarket(), this.getCommoditySpec().getId());
    }

    @Override
    public int getPlayerTradeNetQuantity() {
        return super.getPlayerTradeNetQuantity();
    }

    @Override
    public float getModValueForQuantity(float v) {
        return super.getModValueForQuantity(v);
    }

    @Override
    public float getUtilityOnMarket() {
        MarketAPI var3 = AoTDEconomy.getInstance().getMarketThreadSave(this.getCommoditySpec().getOrigin());
        if (this.getCommoditySpec().isExotic() && var3 != null) {
            float var1 = Economy.EXOTIC_UTILITY_MULT;
            float var2 = Economy.RANGE_FOR_MAX_EXOTIC_DEMAND;
            float var4 = Misc.getDistanceLY((var3.getLocation()), this.getMarket().getLocation());
            float var5 = 1.0F + var1 * Math.min(var4 / var2, 1.0F);
            return this.getCommoditySpec().getUtility() * var5;
        }
        return 1f;
    }


    @Override
    public MutableStatWithTempMods getTradeModPlus() {
        if (super.getTradeModPlus() == null) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("tradeModPlus", this, new MutableStatWithTempMods(0f));
        }
        return super.getTradeModPlus();
    }

    @Override
    public int getDeficitQuantity() {
        // optionally include net trade bringing supply in
        if (getDef() <= 0) return 0;
        float trade = getTradeMod().getModifiedValue()
                + getTradeModPlus().getModifiedValue()
                + getTradeModMinus().getModifiedValue();
        int deficit = Math.round(getDef() - trade);
        if (deficit > 0) {
            String hehe = "";
        }
        return Math.max(0, deficit);
    }

    @Override
    public int getExcessQuantity() {
        float excess = getExc();
        if (excess <= 0) {
            float trade = getTradeMod().getModifiedValue()
                    + getTradeModPlus().getModifiedValue()
                    + getTradeModMinus().getModifiedValue();
            float effectiveOversurplus = getSupplyDemandData().getTotalRawUnitsFromDemand() * 2;
            return (int) Math.max(0, trade - effectiveOversurplus);
        }
        float trade = getTradeMod().getModifiedValue()
                + getTradeModPlus().getModifiedValue()
                + getTradeModMinus().getModifiedValue();

        return Math.max(0, Math.round(excess + trade));
    }

    public int getCombinedTradeValue() {
        return getTradeModPlus().getModifiedInt() + getTradeMod().getModifiedInt() + getTradeModMinus().getModifiedInt();
    }


    @Override
    public int getAvailable() {
        return super.getAvailable();
    }

}
