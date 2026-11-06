package data.kaysaar.aotd.tot.scripts.trade.route;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.*;

public class AoTDEconomyFleetAssigmentAI extends EconomyFleetAssignmentAI {
    public static class AoTDCargoQuantityData extends CargoQuantityData {
        public float tier;

        public AoTDCargoQuantityData(String cargo, int units, float tier) {
            super(cargo, units);
            this.tier = tier;
        }
    }

    public static class AoTDEconomyRouteData extends EconomyRouteData {
        public boolean deliverLoaded;
        public boolean returnLoaded;

        public void addDeliver(String id, int qty, float tier) {
            cargoDeliver.add(new AoTDCargoQuantityData(id, qty, tier));
        }

        public void addReturn(String id, int qty, float tier) {
            cargoReturn.add(new AoTDCargoQuantityData(id, qty, tier));
        }
    }

    public AoTDEconomyFleetAssigmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
        super(fleet, route);
    }

    public static String getCargoListDeliver(RouteManager.RouteData route) {
        return EconomyRouteData.getCargoList(((EconomyRouteData) route.getCustom()).cargoDeliver);
    }

    public static String getCargoListReturn(RouteManager.RouteData route) {
        return EconomyRouteData.getCargoList(((EconomyRouteData) route.getCustom()).cargoReturn);
    }

    public static void limitCargoToCapacity(List<CargoQuantityData> list, CargoAPI cargo) {
        float space = 0f;
        float personnel = 0f;
        float fuel = 0f;
        for (CargoQuantityData curr : list) {
            CommoditySpecAPI spec = curr.getCommodity();
            if (spec.isMeta()) continue;
            if (Commodities.FUEL.equals(curr.cargo)) fuel += curr.units;
            else if (spec.isPersonnel()) personnel += curr.units;
            else space += curr.units * spec.getCargoSpace();
        }

        float cargoMult = space > 0f ? Math.min(1f, Math.max(0f, cargo.getMaxCapacity()) / space) : 1f;
        float personnelMult = personnel > 0f ? Math.min(1f, Math.max(0f, cargo.getMaxPersonnel()) / personnel) : 1f;
        float fuelMult = fuel > 0f ? Math.min(1f, Math.max(0f, cargo.getMaxFuel()) / fuel) : 1f;
        Iterator<CargoQuantityData> iterator = list.iterator();
        while (iterator.hasNext()) {
            CargoQuantityData curr = iterator.next();
            CommoditySpecAPI spec = curr.getCommodity();
            if (!spec.isMeta()) {
                float mult = cargoMult;
                if (Commodities.FUEL.equals(curr.cargo)) mult = fuelMult;
                else if (spec.isPersonnel()) mult = personnelMult;
                curr.units = (int) Math.floor(curr.units * mult);
            }
            if (curr.units <= 0) iterator.remove();
        }
    }

    @Override
    protected void updateCargo(RouteManager.RouteSegment segment) {
        int id = segment.getId();
        CargoAPI cargo = fleet.getCargo();
        cargo.clear();
        syncMothballedShips(0f, null);
        if (route.isExpired() || id == AoTDEconomyRouteManager.ROUTE_SRC_LOAD
                || id == AoTDEconomyRouteManager.ROUTE_DST_LOAD) return;

        EconomyRouteData data = getData();
        boolean returning = id > AoTDEconomyRouteManager.ROUTE_DST_LOAD;
        MarketAPI source = returning ? data.to : data.from;
        List<CargoQuantityData> list = returning ? data.cargoReturn : data.cargoDeliver;
        if (data instanceof AoTDEconomyRouteData) {
            AoTDEconomyRouteData current = (AoTDEconomyRouteData) data;
            if (returning ? !current.returnLoaded : !current.deliverLoaded) {
                AoTDEconomyRouteManager.refreshCargo(list, source, returning ? data.from : data.to);
                if (returning) current.returnLoaded = true;
                else current.deliverLoaded = true;
            }
        }
        limitCargoToCapacity(list, cargo);
        for (CargoQuantityData curr : list) {
            if (Commodities.SHIPS.equals(curr.cargo)) {
                super.syncMothballedShips(AoTDEconomyRouteManager.getCargoTier(curr, source,
                        returning ? data.from : data.to), source);
            } else if (!curr.getCommodity().isMeta()) {
                cargo.addCommodity(curr.cargo, curr.units);
            }
        }
    }
}
