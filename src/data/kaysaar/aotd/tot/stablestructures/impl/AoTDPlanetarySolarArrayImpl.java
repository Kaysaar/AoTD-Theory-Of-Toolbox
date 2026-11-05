package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDLinkedStructureImpl;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.ChoosePlanetForBuildingMap;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.StructureLocationPicker;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;

public abstract class AoTDPlanetarySolarArrayImpl extends AoTDLinkedStructureImpl {
    public static final String PLANET_ARRAY = "$aotd_planetary_solar_array";
    public static final String MARKET_ARRAY = "$aotd_market_solar_array";

    protected PlanetAPI targetPlanet;
    protected MarketAPI targetMarket;
    protected boolean appliedSolarArray;

    public abstract boolean isShade();

    public String getArrayName() {
        return isShade() ? "Stellar Shade Array" : "Stellar Mirror Array";
    }

    @Override
    public String getName() {
        return getArrayName();
    }
    @Override
    public boolean canMountAICores() {
        return true;
    }
    @Override
    public String getImageName() {
        String image = super.getImageName();
        return image == null ? getSpecEntity().getSpriteName() : image;
    }

    public String getArrayEntityId() {
        return isShade() ? Entities.STELLAR_SHADE : Entities.STELLAR_MIRROR;
    }

    public PlanetAPI getTargetPlanet() {
        return targetPlanet;
    }

    public MarketAPI getTargetMarket() {
        MarketAPI market = targetPlanet == null ? null : targetPlanet.getMarket();
        return market == null ? targetMarket : market;
    }

    @Override
    public void setConstructionFocus(SectorEntityToken focus) {
        targetPlanet = focus instanceof PlanetAPI ? (PlanetAPI) focus : null;
        targetMarket = targetPlanet == null ? null : targetPlanet.getMarket();
    }

    public boolean isHotPlanet(PlanetAPI planet) {
        return planet.hasCondition(Conditions.HOT) || planet.hasCondition(Conditions.VERY_HOT);
    }

    public boolean isMirrorPlanet(PlanetAPI planet) {
        return planet.hasCondition(Conditions.POOR_LIGHT)
                || planet.hasCondition(Conditions.FARMLAND_POOR)
                || planet.hasCondition(Conditions.FARMLAND_ADEQUATE)
                || planet.hasCondition(Conditions.FARMLAND_RICH)
                || planet.hasCondition(Conditions.FARMLAND_BOUNTIFUL);
    }

    public boolean marketRequiresShades(PlanetAPI planet) {
        MarketAPI market = planet.getMarket();
        for (PlanetAPI other : planet.getStarSystem().getPlanets()) {
            if (!other.isStar() && !other.isExpired() && other.getMarket() == market && isHotPlanet(other)) return true;
        }
        return false;
    }

    public SectorEntityToken getMarketArrayOwner(PlanetAPI planet) {
        MarketAPI market = planet.getMarket();
        if (market == null) return null;

        Object existing = market.getMemoryWithoutUpdate().get(MARKET_ARRAY);
        if (existing instanceof SectorEntityToken token) {
            if (!token.isExpired()) return token;
            market.getMemoryWithoutUpdate().unset(MARKET_ARRAY);
        }

        // Recover arrays constructed before the market-level reservation was introduced.
        for (PlanetAPI other : planet.getStarSystem().getPlanets()) {
            if (other.getMarket() != market) continue;
            Object owner = other.getMemoryWithoutUpdate().get(PLANET_ARRAY);
            if (!(owner instanceof SectorEntityToken token) || token.isExpired()) continue;
            market.getMemoryWithoutUpdate().set(MARKET_ARRAY, token);
            return token;
        }
        return null;
    }

    public boolean canBuildOnPlanet(PlanetAPI planet) {
        return canBuildOnPlanetIgnoringController(planet) && getMarketArrayOwner(planet) == null;
    }

    public boolean canBuildOnSelectedPlanet() {
        if (targetPlanet == null || !canBuildOnPlanetIgnoringController(targetPlanet)) return false;
        SectorEntityToken owner = getMarketArrayOwner(targetPlanet);
        return owner == null || owner == currentlyAssignedToken;
    }

    public boolean canBuildOnPlanetIgnoringController(PlanetAPI planet) {
        if (planet == null || planet.isStar() || planet.isExpired() || planet.getStarSystem() == null) return false;
        MarketAPI market = planet.getMarket();
        if (market == null || market.getFaction() == null || !market.getFaction().isPlayerFaction()
                || market.hasCondition(Conditions.SOLAR_ARRAY)) return false;
        if (isShade()) return isHotPlanet(planet);
        return isMirrorPlanet(planet) && !marketRequiresShades(planet);
    }

    @Override
    public boolean canPlaceAt(StarSystemAPI system, SectorEntityToken focus, Vector2f position) {
        return focus instanceof PlanetAPI planet && planet.getStarSystem() == system && canBuildOnPlanet(planet);
    }


    @Override
    public boolean canMountItems() {
        return true;
    }

    @Override
    public boolean isAvailableToBuild(StarSystemAPI system, FactionAPI faction) {
        if (system == null || faction == null || !faction.isPlayerFaction()) return false;
        if (targetPlanet != null && targetPlanet.getStarSystem() == system) return canBuildOnSelectedPlanet();
        for (PlanetAPI planet : system.getPlanets()) {
            if (canBuildOnPlanet(planet)) return true;
        }
        return false;
    }

    @Override
    public String getUnavailableReason(StarSystemAPI system, FactionAPI faction) {
        if (faction == null || !faction.isPlayerFaction()) {
            return "This structure can only be built in player-controlled star systems.";
        }
        if (system != null) {
            for (PlanetAPI planet : system.getPlanets()) {
                MarketAPI market = planet.getMarket();
                if (planet.isStar() || planet.isExpired() || market == null || market.getFaction() == null
                        || !market.getFaction().isPlayerFaction()) continue;
                if (isShade() ? !isHotPlanet(planet) : !isMirrorPlanet(planet)) continue;
                if (getMarketArrayOwner(planet) != null || market.hasCondition(Conditions.SOLAR_ARRAY)) {
                    return "A Stellar Mirror or Shade Array is already assigned to an eligible market.";
                }
                if (!isShade() && marketRequiresShades(planet)) {
                    return "Hot markets require Stellar Shade Arrays instead of Stellar Mirrors.";
                }
            }
        }
        if (isShade()) return "Requires a player-owned planet with Hot or Very Hot conditions.";
        return "Requires a player-owned planet with Poor Light or farmland, on a market without Hot or Very Hot conditions.";
    }

    @Override
    public StructureLocationPicker createLocationPicker(float width, float height, StarSystemAPI system) {
        return new ChoosePlanetForBuildingMap(width, height, system, this);
    }

    public float getArrayRadius(PlanetAPI planet) {
        float radius = planet.getRadius() + 320f;
        for (PlanetAPI other : planet.getStarSystem().getPlanets()) {
            if (other.getOrbitFocus() == planet) {
                radius = Math.max(radius, other.getCircularOrbitRadius() + other.getRadius() + 100f);
            }
        }
        return radius;
    }

    public float getArrayAngle(PlanetAPI planet) {
        return planet.getCircularOrbitAngle() + (isShade() ? 180f : 0f);
    }

    public float getArrayPeriod(PlanetAPI planet) {
        return planet.getCircularOrbitPeriod() <= 0f ? 120f : planet.getCircularOrbitPeriod();
    }

    public Vector2f getPlacementLocation(PlanetAPI planet) {
        float radius = getArrayRadius(planet);
        float angle = (float) Math.toRadians(getArrayAngle(planet));
        return new Vector2f(planet.getLocation().x + (float) Math.cos(angle) * radius,
                planet.getLocation().y + (float) Math.sin(angle) * radius);
    }

    public float getCompanionOffset() {
        return isShade() ? 26f : 30f;
    }

    public void placeAroundPlanet(SectorEntityToken token, float offset) {
        if (targetPlanet == null || token == null) return;
        token.setCircularOrbitPointingDown(targetPlanet, getArrayAngle(targetPlanet) + offset,
                getArrayRadius(targetPlanet), getArrayPeriod(targetPlanet));
    }

    @Override
    public ArrayList<LinkedEntityData> getLinkedEntityData() {
        ArrayList<LinkedEntityData> result = new ArrayList<>();
        String id = getArrayEntityId();
        if (isShade()) {
            result.add(new LinkedEntityData(id, "Stellar Shade Omega", -getCompanionOffset()));
            result.add(new LinkedEntityData(id, "Stellar Shade Chi", getCompanionOffset()));
        } else {
            result.add(new LinkedEntityData(id, "Stellar Mirror Beta", -getCompanionOffset()));
            result.add(new LinkedEntityData(id, "Stellar Mirror Gamma", getCompanionOffset()));
        }
        return result;
    }

    @Override
    public void placeLinkedEntity(SectorEntityToken token, LinkedEntityData data) {
        placeAroundPlanet(token, data.offset);
    }

    protected void reserveMarket() {
        if (targetPlanet == null) return;
        targetPlanet.getMemoryWithoutUpdate().set(PLANET_ARRAY, currentlyAssignedToken);
        MarketAPI market = getTargetMarket();
        if (market != null) market.getMemoryWithoutUpdate().set(MARKET_ARRAY, currentlyAssignedToken);
    }

    @Override
    public void onConstructionStarted() {
        if (targetPlanet == null || !isBuilding() || !isCurrentImplementation()) return;
        reserveMarket();
        currentlyAssignedToken.setName(getArrayName() + " (Under construction)");
        placeAroundPlanet(currentlyAssignedToken, 0f);
        super.onConstructionStarted();
    }

    protected void applySolarArray() {
        MarketAPI market = getTargetMarket();
        if (market == null || market.hasCondition(Conditions.SOLAR_ARRAY)) return;
        market.addCondition(Conditions.SOLAR_ARRAY);
        appliedSolarArray = true;
    }

    protected void removeSolarArray() {
        MarketAPI market = getTargetMarket();
        if (market != null && appliedSolarArray) market.removeCondition(Conditions.SOLAR_ARRAY);
        appliedSolarArray = false;
    }

    protected void releasePlanet() {
        if (targetPlanet == null) return;
        MarketAPI market = getTargetMarket();
        if (market != null) {
            Object existing = market.getMemoryWithoutUpdate().get(MARKET_ARRAY);
            if (existing == currentlyAssignedToken || existing instanceof SectorEntityToken token && token.isExpired()) {
                market.getMemoryWithoutUpdate().unset(MARKET_ARRAY);
            }
        }
        Object existing = targetPlanet.getMemoryWithoutUpdate().get(PLANET_ARRAY);
        if (existing == currentlyAssignedToken || existing instanceof SectorEntityToken token && token.isExpired()) {
            targetPlanet.getMemoryWithoutUpdate().unset(PLANET_ARRAY);
        }
    }

    @Override
    protected void buildingFinished() {
        super.buildingFinished();
        currentlyAssignedToken.setName(getArrayName());
        reserveMarket();
        applySolarArray();
    }

    @Override
    public void cancelBuilding() {
        releasePlanet();
        super.cancelBuilding();
    }

    @Override
    public void notifyRemoved() {
        removeSolarArray();
        releasePlanet();
        super.notifyRemoved();
    }

    @Override
    protected void notifyDisrupted() {
        removeSolarArray();
    }

    @Override
    protected void disruptionFinished() {
        if (!isBuilding() && isCurrentImplementation()) applySolarArray();
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);
        String planet = targetPlanet == null ? "the selected planet" : targetPlanet.getName();
        tooltip.addPara("Establishes a three-part %s around %s, providing the Solar Array condition to its market.",
                3f, Misc.getHighlightColor(), isShade() ? "stellar shade array" : "stellar mirror array", planet);
    }
}
