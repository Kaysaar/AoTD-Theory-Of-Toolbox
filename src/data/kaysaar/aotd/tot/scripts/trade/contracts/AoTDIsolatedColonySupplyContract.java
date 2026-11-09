package data.kaysaar.aotd.tot.scripts.trade.contracts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.scripts.trade.AoTDIsolationUtils;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDIsolatedColonySupplyContract extends AoTDTradeContract {
    public static float MARGIN_OF_COMMODITY_PRICE = 0.1f;
    protected final MarketAPI destination;
    protected LinkedHashMap<String, Integer> deliveredLastMonth = new LinkedHashMap<>();
    protected int deliveryCycle = -1;
    protected int deliveryMonth = -1;

    public static String getContractId(MarketAPI market) {
        return "aotd_isolated_supply_" + market.getId();
    }

    public AoTDIsolatedColonySupplyContract(MarketAPI destination) {
        super(getContractId(destination), null, Factions.PLAYER, 9999);
        this.destination = destination;
        doesContractHavePenalty = false;
        hasCustomEffectSection = true;
    }

    public MarketAPI getDestination() {
        return destination;
    }

    public void refreshRequirements() {
        contractData.clear();
        if (isTerminated()) return;
        AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(destination);
        if (data == null) return;
        for (Map.Entry<String, Integer> entry : data.remainingNet.entrySet()) {
            if (entry.getValue() >= 0 || destination.getCommodityData(entry.getKey()).isNonEcon()) continue;
            addContractData(entry.getKey(), -entry.getValue(), MARGIN_OF_COMMODITY_PRICE);
        }
    }

    public void beginDeliveryMonth() {
        deliveredLastMonth.clear();
        deliveryCycle = Global.getSector().getClock().getCycle();
        deliveryMonth = Global.getSector().getClock().getMonth();
    }

    public int getDelivered(String commodityId) {
        if (isTerminated() || deliveryCycle != Global.getSector().getClock().getCycle()
                || deliveryMonth != Global.getSector().getClock().getMonth()) return 0;
        return deliveredLastMonth.getOrDefault(commodityId, 0);
    }

    @Override
    public void executeMonthEndForCommodity(int delivered, String commodityId) {
        if (delivered <= 0 || isTerminated() || isContractFrozen()) return;
        AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(destination);
        if (data == null) return;
        data.remainingNet.merge(commodityId, delivered, Integer::sum);
        if (data.getRemainingNet(commodityId) == 0) data.remainingNet.remove(commodityId);
        deliveredLastMonth.merge(commodityId, delivered, Integer::sum);
    }

    @Override
    public boolean isTerminated() {
        return destination == null || !destination.isPlayerOwned()
                || Global.getSector().getEconomy().getMarket(destination.getId()) != destination
                || !AoTDIsolationUtils.isIsolated(destination);
    }

    @Override
    public boolean isExpired() {
        return isTerminated();
    }

    @Override
    public boolean canTerminateContract() {
        return isTerminated();
    }

    @Override
    public void terminateContract() {
        if (!canTerminateContract()) return;
        contractData.clear();
        deliveredLastMonth.clear();
    }

    @Override
    public boolean canEditContract() {
        return false;
    }

    @Override
    public boolean canFreezeContract() {
        return false;
    }

    @Override
    public boolean isContractFrozen() {
        return isTerminated() || !destination.hasSpaceport()
                || destination.getAccessibilityMod().computeEffective(0f) <= 0f;
    }

    @Override
    public void decrementMonth() {
    }

    @Override
    public String getContractTypeId() {
        return "aotd_isolated_colony_supply";
    }

    @Override
    public String getNameOfContract() {
        return destination.getName();
    }

    @Override
    public String getSubTypeOfContractString() {
        return "Colony Supply";
    }

    @Override
    public Pair<String, Color> getCurrentContractStatus() {
        if (isTerminated()) return new Pair<>("Ended", Misc.getGrayColor());
        if (isContractFrozen()) return new Pair<>("Not fulfilled", Misc.getHighlightColor());
        if (contractData.isEmpty()) return new Pair<>("Fulfilled", Misc.getPositiveHighlightColor());
        AoTDTradeContractManager manager = AoTDTradeContractManager.getInstance();
        for (TradeContractData line : contractData.values()) {
            if (manager.getPredictedDelivery(getId(), line.getCommodityId()) < line.getReqMonthly()) {
                return new Pair<>("Not fulfilled", Misc.getHighlightColor());
            }
        }
        return new Pair<>("Fulfilled", Misc.getPositiveHighlightColor());
    }

    @Override
    public float getCut(String commodityId) {
        return MARGIN_OF_COMMODITY_PRICE;
    }

    @Override
    public void printCustomSection(TooltipMakerAPI tooltip, float width) {
        tooltip.addPara("Supplies %s from faction production capacity outside isolated systems. " +
                        "Monthly requirements cover demand left after trade within the star system.",
                3f, Misc.getHighlightColor(), destination.getName());
        tooltip.addPara("Transport costs %s of the delivered commodities' base value. Charges apply only to goods actually delivered.",
                3f, Misc.getHighlightColor(), Math.round(MARGIN_OF_COMMODITY_PRICE * 100f) + "%");
        tooltip.addPara("Only delivered goods satisfy colony demand. Any undelivered amount remains a shortage.", 3f);
        tooltip.addPara("This contract remains active while the colony is isolated and ends without penalties.", Misc.getGrayColor(), 3f);
    }
}
