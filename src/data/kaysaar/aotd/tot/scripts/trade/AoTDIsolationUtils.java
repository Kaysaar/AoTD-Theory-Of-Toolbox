package data.kaysaar.aotd.tot.scripts.trade;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.ids.AoTDTags;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDExcDefData;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDIsolatedColonySupplyContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AoTDIsolationUtils {
    private static final String STATE_KEY = "$aotd_isolated_trade_system";

    public static boolean isIsolated(MarketAPI market) {
        return market != null && market.getStarSystem() != null
                && market.getStarSystem().hasTag(AoTDTags.AOTD_ISOLATED_SYSTEM);
    }

    public static boolean canTradeBetween(MarketAPI from, MarketAPI to) {
        if (from == null || to == null) return false;
        if (!isIsolated(from) && !isIsolated(to)) return true;
        return from.getStarSystem() != null && from.getStarSystem() == to.getStarSystem();
    }

    public static boolean canSupplyContract(MarketAPI market) {
        return market != null && !isIsolated(market) && market.hasSpaceport()
                && market.getAccessibilityMod().computeEffective(0f) > 0f;
    }

    // Called on the main thread at economy boundaries, never per commodity or frame.
    public static void prepareMarkets(Iterable<MarketAPI> markets) {
        AoTDTradeContractManager contracts = AoTDTradeContractManager.getInstance();
        for (AoTDTradeContract contract : new ArrayList<>(contracts.getActiveContracts().values())) {
            if (contract instanceof AoTDIsolatedColonySupplyContract supply && supply.isTerminated()) {
                contracts.terminateContract(supply.getId());
            }
        }
        for (MarketAPI market : markets) {
            boolean isolated = isIsolated(market);
            String systemId = isolated ? market.getStarSystem().getId() : null;
            Object previous = market.getMemoryWithoutUpdate().get(STATE_KEY);
            if (!Objects.equals(previous, systemId)) {
                for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                    if (commodity instanceof AoTDCommodityOnMarket com) {
                        com.getExcDefData().clearIsolationTrade();
                        if (isolated) {
                            com.getExcDefData().deficit.removeTemporaryMod(AoTDExcDefData.EXT_TRADE_ID);
                            com.getExcDefData().excess.removeTemporaryMod(AoTDExcDefData.EXT_TRADE_ID);
                        }
                    }
                }
                if (isolated) market.getMemoryWithoutUpdate().set(STATE_KEY, systemId);
                else market.getMemoryWithoutUpdate().unset(STATE_KEY);
                contracts.invalidatePredictions();
            }
            if (isolated && market.isPlayerOwned()) {
                String id = AoTDIsolatedColonySupplyContract.getContractId(market);
                if (!contracts.getActiveContracts().containsKey(id)) {
                    contracts.addContract(new AoTDIsolatedColonySupplyContract(market));
                }
            }
        }
    }

    // Only isolated markets enter these small pools. Local trade also crosses faction borders.
    public static void computeLocalTrade(Map<String, MarketAPI> markets) {
        LinkedHashMap<StarSystemAPI, ArrayList<AoTDMarketData>> systems = null;
        AoTDTradeManager trade = AoTDTradeManager.getInstance();
        for (MarketAPI market : markets.values()) {
            if (!isIsolated(market)) continue;
            AoTDMarketData data = trade.getMarketData(market);
            if (data == null) continue;
            data.resetInternalResults();
            if (!market.hasSpaceport() || market.getAccessibilityMod().computeEffective(0f) <= 0f) continue;
            if (systems == null) systems = new LinkedHashMap<>();
            systems.computeIfAbsent(market.getStarSystem(), key -> new ArrayList<>()).add(data);
        }
        if (systems != null) {
            for (ArrayList<AoTDMarketData> local : systems.values()) {
                AoTDFactionTradeData.allocateInternalTrade(local);
            }
        }
        for (AoTDTradeContract contract : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
            if (contract instanceof AoTDIsolatedColonySupplyContract supply) supply.refreshRequirements();
        }
    }

    public static int getDelivered(MarketAPI market, String commodityId) {
        AoTDTradeContract contract = AoTDTradeContractManager.getInstance().getActiveContracts()
                .get(AoTDIsolatedColonySupplyContract.getContractId(market));
        return contract instanceof AoTDIsolatedColonySupplyContract supply
                ? supply.getDelivered(commodityId) : 0;
    }

    public static void updateAvailability(Iterable<MarketAPI> markets) {
        for (MarketAPI market : markets) {
            if (!isIsolated(market)) continue;
            AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(market);
            if (data == null) continue;
            for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
                if (!(commodity instanceof AoTDCommodityOnMarket com) || com.isNonEcon()) continue;
                int balance = data.getRemainingNet(com.getId()) + getDelivered(market, com.getId());
                com.getExcDefData().applyIsolationTrade(Math.max(0, -balance), Math.max(0, balance), com);
            }
        }
    }
}
