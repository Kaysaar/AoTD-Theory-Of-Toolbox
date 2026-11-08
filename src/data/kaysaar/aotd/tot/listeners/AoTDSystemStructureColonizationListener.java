package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;

public class AoTDSystemStructureColonizationListener implements PlayerColonizationListener {

    public static boolean canClaimUnownedStructures(StarSystemAPI system) {
        if (system == null || Global.getSector() == null) return false;

        boolean hasPlayerColony = false;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getStarSystem() != system) continue;

            if (market.getFaction() != null && market.getFaction().isPlayerFaction()) {
                hasPlayerColony = true;
            } else if (!Factions.NEUTRAL.equals(market.getFactionId())
                    && !Factions.DERELICT.equals(market.getFactionId())) {
                return false;
            }
        }
        return hasPlayerColony;
    }

    public static int claimUnownedStructures(StarSystemAPI system) {
        if (!canClaimUnownedStructures(system)) return 0;

        int claimed = 0;
        for (SectorEntityToken token : system.getAllEntities()) {
            if (token.isExpired() || token.getFaction() == null
                    || AoTDToolboxMisc.isSystemStableLocation(token)) continue;

            String factionId = token.getFaction().getId();
            if (!Factions.NEUTRAL.equals(factionId) && !Factions.DERELICT.equals(factionId)) continue;

            String id = token.getCustomEntityType();
            if (AoTDStableStructureConstruction.SCAFFOLD.equals(id)
                    || AoTDStableStructureConstruction.OVERLAY.equals(id)) continue;

            boolean registered = token.getCustomEntitySpec() != null
                    && AoTDStableStructureSpecManager.getSpec(id) != null;
            if (!AoTDToolboxMisc.isSystemStructure(token) && !registered) continue;

            token.setFaction(Factions.PLAYER);
            claimed++;
        }
        return claimed;
    }

    @Override
    public void reportPlayerColonizedPlanet(PlanetAPI planet) {
        if (planet == null || planet.getStarSystem() == null || planet.getMarket() == null
                || planet.getMarket().getFaction() == null
                || !planet.getMarket().getFaction().isPlayerFaction()) return;

        claimUnownedStructures(planet.getStarSystem());
    }

    @Override
    public void reportPlayerAbandonedColony(MarketAPI colony) {
    }
}
