package data.kaysaar.aotd.tot.scripts.trade.route;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.*;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetAssignmentAI.CargoQuantityData;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetAssignmentAI.EconomyRouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.events.PiracyRespiteScript;
import com.fs.starfarer.api.impl.campaign.rulecmd.KantaCMD;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.kaysaar.aotd.tot.intel.AoTDTradeFleetDepartureIntel;
import data.kaysaar.aotd.tot.plugins.AoTDBaseDemSupCalc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;
import data.kaysaar.aotd.tot.scripts.trade.route.AoTDEconomyFleetAssigmentAI.AoTDCargoQuantityData;
import data.kaysaar.aotd.tot.scripts.trade.route.AoTDEconomyFleetAssigmentAI.AoTDEconomyRouteData;

import java.util.*;

public class AoTDEconomyRouteManager extends EconomyFleetRouteManager {
    @Override
    public MarketAPI pickSourceMarket() {
        WeightedRandomPicker<MarketAPI> markets = new WeightedRandomPicker<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!canTrade(market)) continue;
            if (recentlySentTradeFleet.contains(market.getId())) continue;
            markets.add(market, market.getSize());
        }
        return markets.pick();
    }

    private static boolean canTrade(MarketAPI market) {
        return market != null && !market.isHidden() && market.hasSpaceport()
                && market.getPrimaryEntity() != null
                && market.getAccessibilityMod().computeEffective(0f) > 0f
                && !SharedData.getData().getMarketsWithoutTradeFleetSpawn().contains(market.getId());
    }

    private static Set<String> getCommodityIds(MarketAPI from, MarketAPI to) {
        Set<String> ids = new LinkedHashSet<>();
        for (CommodityOnMarketAPI com : from.getAllCommodities()) {
            if (!com.isNonEcon()) ids.add(com.getId());
        }
        for (CommodityOnMarketAPI com : to.getAllCommodities()) {
            if (!com.isNonEcon()) ids.add(com.getId());
        }
        return ids;
    }

    @Override
    public MarketAPI pickDestMarket(MarketAPI from) {
        if (!canTrade(from)) return null;
        WeightedRandomPicker<MarketAPI> markets = new WeightedRandomPicker<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(from.getEconGroup())) {
            if (market == from || !canTrade(market)) continue;
            float weight = 0f;
            for (String id : getCommodityIds(from, market)) {
                int deliver = getTransferAmount(from, market, id);
                int returning = getTransferAmount(market, from, id);
                weight += getCargoTier(from, market, id, deliver);
                weight += getCargoTier(market, from, id, returning);
            }
            if (from.getFaction().isHostileTo(market.getFaction())) weight *= 0.25f;
            if (weight > 0f) markets.add(market, weight);
        }
        return markets.pick();
    }

    private static int getTransferAmount(MarketAPI source, MarketAPI destination, String id) {
        AoTDCommodityOnMarket from = AoTDCommodityOnMarket.getComMarketInstanceSave(source, id);
        AoTDCommodityOnMarket to = AoTDCommodityOnMarket.getComMarketInstanceSave(destination, id);
        if (from == null || to == null || from.isNonEcon() || to.isNonEcon()) return 0;
        AoTDSupplyDemandData supply = from.getSupplyDemandData();
        AoTDSupplyDemandData demand = to.getSupplyDemandData();

        // Imports already assigned by the economy must not erase the need for a trade fleet.
        int exported = Math.max(0, supply.getTotalRawUnitsFromSupply() - supply.getTotalRawUnitsFromDemand());
        int imported = Math.max(0, demand.getTotalRawUnitsFromDemand() - demand.getTotalRawUnitsFromSupply());
        return Math.min(exported, imported);
    }

    static void refreshCargo(List<CargoQuantityData> list, MarketAPI source, MarketAPI destination) {
        if (!canTrade(source) || !canTrade(destination)) {
            list.clear();
            return;
        }
        Iterator<CargoQuantityData> iterator = list.iterator();
        while (iterator.hasNext()) {
            CargoQuantityData cargo = iterator.next();
            cargo.units = Math.min(cargo.units, getTransferAmount(source, destination, cargo.cargo));
            if (cargo.units <= 0) iterator.remove();
        }
    }

    private static float getCargoTier(MarketAPI source, MarketAPI destination, String id, int amount) {
        if (amount <= 0) return 0f;
        AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(source, id);
        AoTDSupplyDemandData data = com.getSupplyDemandData();
        AoTDBaseDemSupCalc calc = data.getEconSpec().getCalculationScript();
        int supply = Math.max(1, calc.convertRawUnitsToSupply(data.getTotalRawUnitsFromSupply(), source, id));
        int demand = Math.max(1, calc.convertRawUnitsToDemand(amount, destination, id));
        boolean sameFaction = source.getFaction() == destination.getFaction();
        int shipping = Math.min(Misc.getShippingCapacity(source, sameFaction), Misc.getShippingCapacity(destination, sameFaction));
        return Math.max(0, Math.min(shipping, Math.min(supply, demand)));
    }

    static float getCargoTier(CargoQuantityData cargo, MarketAPI source, MarketAPI destination) {
        if (cargo instanceof AoTDCargoQuantityData) return ((AoTDCargoQuantityData) cargo).tier;
        return getCargoTier(source, destination, cargo.cargo, cargo.units);
    }

    private static boolean isIllegal(MarketAPI from, MarketAPI to, String id) {
        CommodityOnMarketAPI com = from.getCommodityData(id);
        return from.getFaction().isHostileTo(to.getFaction())
                || com.getCommodityMarketData().getMarketShareData(from).isSourceIsIllegal()
                || com.getCommodityMarketData().getMarketShareData(to).isSourceIsIllegal();
    }

    public static AoTDEconomyRouteData createData(MarketAPI from, MarketAPI to) {
        if (from == to || !canTrade(from) || !canTrade(to)) return null;
        AoTDEconomyRouteData legal = new AoTDEconomyRouteData();
        legal.from = from;
        legal.to = to;
        AoTDEconomyRouteData smuggling = new AoTDEconomyRouteData();
        smuggling.from = from;
        smuggling.to = to;
        smuggling.smuggling = true;
        float legalTotal = 0f;
        float smugglingTotal = 0f;

        for (String id : getCommodityIds(from, to)) {
            int deliver = getTransferAmount(from, to, id);
            int returning = getTransferAmount(to, from, id);
            float deliverTier = getCargoTier(from, to, id, deliver);
            float returnTier = getCargoTier(to, from, id, returning);
            if (deliverTier <= 0f && returnTier <= 0f) continue;
            boolean illegal = isIllegal(from, to, id);
            AoTDEconomyRouteData data = illegal ? smuggling : legal;
            if (deliverTier > 0f) data.addDeliver(id, capGeneratedCargoAmount(deliver), deliverTier);
            if (returnTier > 0f) data.addReturn(id, capGeneratedCargoAmount(returning), returnTier);
            if (illegal) smugglingTotal += deliverTier + returnTier;
            else legalTotal += deliverTier + returnTier;
        }

        if (legalTotal <= 0f && smugglingTotal <= 0f) return null;
        AoTDEconomyRouteData data = Math.random() * (legalTotal + smugglingTotal) < smugglingTotal ? smuggling : legal;
        Comparator<CargoQuantityData> comp = (first, second) -> {
            if (first.getCommodity().isPersonnel() != second.getCommodity().isPersonnel()) {
                return first.getCommodity().isPersonnel() ? 1 : -1;
            }
            return Float.compare(((AoTDCargoQuantityData) second).tier, ((AoTDCargoQuantityData) first).tier);
        };
        data.cargoDeliver.sort(comp);
        data.cargoReturn.sort(comp);
        while (data.cargoDeliver.size() > 4) data.cargoDeliver.remove(4);
        while (data.cargoReturn.size() > 4) data.cargoReturn.remove(4);
        for (CargoQuantityData cargo : data.cargoDeliver) data.size = Math.max(data.size, getCargoTier(cargo, from, to));
        for (CargoQuantityData cargo : data.cargoReturn) data.size = Math.max(data.size, getCargoTier(cargo, to, from));
        return data;
    }

    private static int capGeneratedCargoAmount(int amount) {
        if (amount <= 1500) return amount;
        return Math.min(amount, 1000 + Misc.random.nextInt(1001));
    }

    @Override
    protected void addRouteFleetIfPossible() {
        MarketAPI from = pickSourceMarket();
        MarketAPI to = pickDestMarket(from);
        if (from == null || to == null) return;
        AoTDEconomyRouteData data = createData(from, to);
        if (data == null) return;
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(from);
        String factionId = from.getFactionId();
        if (!from.getFaction().isHostileTo(Factions.INDEPENDENT) && !to.getFaction().isHostileTo(Factions.INDEPENDENT)) {
            if (Math.random() * 10f > from.getStabilityValue() + data.size) factionId = Factions.INDEPENDENT;
        }
        if (data.smuggling) factionId = Factions.INDEPENDENT;
        extra.factionId = factionId;

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(getRouteSourceId(), from, Misc.genRandomSeed(), extra, this);
        route.setCustom(data);
        StarSystemAPI sysFrom = from.getStarSystem();
        StarSystemAPI sysTo = to.getStarSystem();
        WarSimScript.LocationDanger danger;
        try {
            if (PiracyRespiteScript.playerHasPiracyRespite()
                    && (from.getFaction().isPlayerFaction() || to.getFaction().isPlayerFaction())) {
                ENEMY_STRENGTH_CHECK_EXCLUDE_PIRATES = true;
            }
            WarSimScript.LocationDanger dFrom = WarSimScript.getDangerFor(factionId, sysFrom);
            WarSimScript.LocationDanger dTo = WarSimScript.getDangerFor(factionId, sysTo);
            danger = dFrom.ordinal() > dTo.ordinal() ? dFrom : dTo;
        } finally {
            ENEMY_STRENGTH_CHECK_EXCLUDE_PIRATES = false;
        }
        if (sysFrom != null && sysFrom.isCurrentLocation()) danger = WarSimScript.LocationDanger.NONE;
        float pLoss = DANGER_LOSS_PROB.get(danger);
        if (data.smuggling) pLoss *= 0.5f;
        if (Math.random() < pLoss) {
            // No fleet was generated, so there is no capacity-limited cargo to lose yet.
            RouteManager.getInstance().removeRoute(route);
            return;
        }

        float orbitDays = data.size * (0.75f + (float) Math.random() * 0.5f);
        route.addSegment(new RouteManager.RouteSegment(ROUTE_SRC_LOAD, orbitDays, from.getPrimaryEntity()));
        route.addSegment(new RouteManager.RouteSegment(ROUTE_TRAVEL_DST, from.getPrimaryEntity(), to.getPrimaryEntity()));
        route.addSegment(new RouteManager.RouteSegment(ROUTE_DST_UNLOAD, orbitDays * 0.5f, to.getPrimaryEntity()));
        route.addSegment(new RouteManager.RouteSegment(ROUTE_DST_LOAD, orbitDays * 0.5f, to.getPrimaryEntity()));
        route.addSegment(new RouteManager.RouteSegment(ROUTE_TRAVEL_SRC, to.getPrimaryEntity(), from.getPrimaryEntity()));
        route.addSegment(new RouteManager.RouteSegment(ROUTE_SRC_UNLOAD, orbitDays, from.getPrimaryEntity()));
        setDelayAndSendMessage(route, factionId);
        recentlySentTradeFleet.add(from.getId(), Global.getSettings().getFloat("minEconSpawnIntervalPerMarket"));
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
        Random random = route.getSeed() == null ? new Random() : new Random(route.getSeed());
        CampaignFleetAPI fleet = createTradeRouteFleet(route, random);
        if (fleet == null) return null;
        EconomyRouteData data = (EconomyRouteData) route.getCustom();
        if (KantaCMD.playerHasProtection() && (data.from.getFaction().isPlayerFaction() || data.to.getFaction().isPlayerFaction())) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_FACTION, Factions.PIRATES);
        }
        fleet.addEventListener(this);
        fleet.addScript(new AoTDEconomyFleetAssigmentAI(fleet, route));
        return fleet;
    }

    public static CampaignFleetAPI createTradeRouteFleet(RouteManager.RouteData route, Random random) {
        EconomyRouteData data = (EconomyRouteData) route.getCustom();
        float fuel = 0f;
        float cargo = 0f;
        float personnel = 0f;
        float tier = data.size;
        for (int i = 0; i < 2; i++) {
            MarketAPI source = i == 0 ? data.from : data.to;
            MarketAPI destination = i == 0 ? data.to : data.from;
            List<CargoQuantityData> list = i == 0 ? data.cargoDeliver : data.cargoReturn;
            for (CargoQuantityData curr : list) {
                float units = getCargoTier(curr, source, destination);
                tier = Math.max(tier, units);
                CommoditySpecAPI spec = curr.getCommodity();
                if (spec.isMeta()) continue;
                if (spec.hasTag(Commodities.TAG_PERSONNEL)) personnel += units;
                else if (Commodities.FUEL.equals(curr.cargo)) fuel += units;
                else cargo += units;
            }
        }
        data.size = Math.max(1f, tier);
        tier = data.smuggling ? Math.min(data.size, 4f) : data.size;
        float total = Math.max(1f, fuel + cargo + personnel);
        float stabilityFactor = 1f + data.from.getStabilityValue() / 20f;
        float combat = Math.max(1f, tier * stabilityFactor * 0.5f) * 10f;
        float freighter = tier * 6f * cargo / total;
        float tanker = tier * 6f * fuel / total;
        float transport = tier * 6f * personnel / total;
        if (data.smuggling) {
            freighter *= 0.5f;
            tanker *= 0.5f;
            transport *= 0.5f;
        }

        FleetParamsV3 params = new FleetParamsV3(data.from, null, route.getFactionId(), route.getQualityOverride(),
                getFleetTypeIdForTier(tier, data.smuggling), combat, freighter, tanker, transport, 0f, 0f, 0f);
        params.timestamp = route.getTimestamp();
        params.onlyApplyFleetSizeToCombatShips = true;
        params.maxShipSize = 3;
        params.officerLevelBonus = -2;
        params.officerNumberMult = 0.5f;
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;
        if (Misc.isPirateFaction(fleet.getFaction())) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FORCE_TRANSPONDER_OFF, true);
        }
        if (data.smuggling) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SMUGGLER, true);
            Misc.makeLowRepImpact(fleet, "smuggler");
        } else {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
        }

        // Tier controls the ships. Raw cargo is limited separately, for each leg.
        AoTDEconomyFleetAssigmentAI.limitCargoToCapacity(data.cargoDeliver, fleet.getCargo());
        AoTDEconomyFleetAssigmentAI.limitCargoToCapacity(data.cargoReturn, fleet.getCargo());
        data.cargoCap = fleet.getCargo().getMaxCapacity();
        data.fuelCap = fleet.getCargo().getMaxFuel();
        data.personnelCap = fleet.getCargo().getMaxPersonnel();
        return fleet;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        RouteManager.RouteData route = RouteManager.getInstance().getRoute(getRouteSourceId(), fleet);
        if (route == null || route.isExpired() || !(route.getCustom() instanceof EconomyRouteData)) return;
        EconomyRouteData data = (EconomyRouteData) route.getCustom();
        boolean returning = route.getCurrent() != null && route.getCurrentSegmentId() >= ROUTE_DST_LOAD;
        boolean cargo = data.cargoCap * 0.34f > fleet.getCargo().getMaxCapacity();
        boolean fuel = data.fuelCap * 0.34f > fleet.getCargo().getMaxFuel();
        boolean personnel = data.personnelCap * 0.34f > fleet.getCargo().getMaxPersonnel();
        if (!cargo && !fuel && !personnel) return;
        if (cargo) data.cargoCap = 0f;
        if (fuel) data.fuelCap = 0f;
        if (personnel) data.personnelCap = 0f;
        applyLostShipping(data, returning, cargo, fuel, personnel);
    }

    public static void applyLostShipping(EconomyRouteData data, boolean returning, boolean cargo, boolean fuel, boolean personnel) {
        if (!cargo && !fuel && !personnel) return;
        String id = Misc.genUID() + "_aotd_lost_trade";
        if (!returning) applyLostCargo(data.to, data.cargoDeliver, cargo, fuel, personnel, false, id);
        applyLostCargo(data.from, data.cargoReturn, cargo, fuel, personnel, false, id);
        if (!returning) applyLostCargo(data.to, data.cargoReturn, cargo, fuel, personnel, true, id);
    }

    private static void applyLostCargo(MarketAPI market, List<CargoQuantityData> list, boolean cargo, boolean fuel,
                                      boolean personnel, boolean excess, String id) {
        for (CargoQuantityData curr : list) {
            CommoditySpecAPI spec = curr.getCommodity();
            if (Commodities.FUEL.equals(curr.cargo)) {
                if (!fuel) continue;
            } else if (spec.isPersonnel()) {
                if (!personnel) continue;
            } else if (!cargo) continue;
            AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(market, curr.cargo);
            if (excess) com.getExcDefData().excess.addTemporaryModFlat(60f, id, "Recent Shipment Lost", curr.units);
            else com.getExcDefData().deficit.addTemporaryModFlat(60f, id, "Recent Shipment Lost", curr.units);
            AoTdMainWorkTask2.aotdUpdateStockpileAndPrice(com.getMarket(), com.getSpec());
        }
    }

    @Override
    protected void setDelayAndSendMessage(RouteManager.RouteData route) {
        setDelayAndSendMessage(route, route.getFactionId());
    }

    protected void setDelayAndSendMessage(RouteManager.RouteData route, String factionId) {
        EconomyRouteData data = (EconomyRouteData) route.getCustom();
        float delay = data.size <= 4f ? 5f : data.size <= 6f ? 10f : 15f;
        route.setDelay((int) (delay * (0.75f + (float) Math.random() * 0.5f)));
        if (!Factions.PLAYER.equals(factionId)) new AoTDTradeFleetDepartureIntel(route);
    }
}
