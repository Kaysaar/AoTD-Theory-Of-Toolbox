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

    @Override
    public void reportPlayerColonizedPlanet(PlanetAPI planet) {
        if (planet == null || planet.getStarSystem() == null || planet.getMarket() == null
                || !planet.getMarket().getFaction().isPlayerFaction()) return;

        StarSystemAPI system = planet.getStarSystem();

        // Only claim neutral structures when establishing the first player colony
        // in a system that does not already belong to another faction.
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getStarSystem() != system || market == planet.getMarket()) continue;
            if (!Factions.DERELICT.equals(market.getFactionId())) return;
        }

        for (SectorEntityToken token : system.getAllEntities()) {
            if (token.isExpired() || token.getFaction() == null
                    || !Factions.DERELICT.equals(token.getFaction().getId())
                    || AoTDToolboxMisc.isSystemStableLocation(token)) continue;

            String id = token.getCustomEntityType();
            if (AoTDStableStructureConstruction.SCAFFOLD.equals(id)
                    || AoTDStableStructureConstruction.OVERLAY.equals(id)) continue;

            boolean registered = token.getCustomEntitySpec() != null
                    && AoTDStableStructureSpecManager.getSpec(id) != null;
            if (!AoTDToolboxMisc.isSystemStructure(token) && !registered) continue;

            token.setFaction(Factions.PLAYER);
        }
    }

    @Override
    public void reportPlayerAbandonedColony(MarketAPI colony) {
    }
}
