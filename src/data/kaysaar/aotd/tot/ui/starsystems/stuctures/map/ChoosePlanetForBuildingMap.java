package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import ashlib.data.plugins.ui.models.resizable.map.PlanetRenderResizableComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.impl.AoTDPlanetarySolarArrayImpl;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ChoosePlanetForBuildingMap extends MapMainComponent implements StructureLocationPicker {
    AoTDPlanetarySolarArrayImpl structure;
    StarSystemAPI currentStarSystem;
    MapEntityComponent selectedPlanet;
    ArrayList<MapEntityComponent> eligiblePlanets = new ArrayList<>();
    PlanetConstructionIndicatorComponent indicators;
    boolean updateSelectionText = true;

    public ChoosePlanetForBuildingMap(float width, float height, StarSystemAPI system, AoTDPlanetarySolarArrayImpl structure) {
        super(width, height, system);
        this.structure = structure;
        this.currentStarSystem = system;

        // The map is a paused construction dialog. Eligibility only needs to be
        // evaluated once, not every time a planet indicator is rendered.
        for (MapEntityComponent component : getMapZoom().getAllEntitiesComponents()) {
            if (!(component instanceof PlanetRenderResizableComponent)) continue;
            if (!isValidPlanet(component.getToken())) continue;
            eligiblePlanets.add(component);
        }
        if (!eligiblePlanets.isEmpty()) {
            indicators = new PlanetConstructionIndicatorComponent(this);
            getMapZoom().addComponent(indicators, WORLD_X, WORLD_X);
        }
    }

    public boolean isValidPlanet(SectorEntityToken token) {
        return token instanceof PlanetAPI planet && !planet.isStar() && !planet.isExpired()
                && planet.getStarSystem() == currentStarSystem && structure != null
                && structure.canBuildOnPlanet(planet);
    }

    public boolean selectPlanet(MapEntityComponent point) {
        if (!eligiblePlanets.contains(point) || !isValidPlanet(point.getToken())) return false;
        if (selectedPlanet == point) return true;
        selectedPlanet = point;
        updateSelectionText = true;
        return true;
    }

    public void clearSelection() {
        if (selectedPlanet == null) return;
        selectedPlanet = null;
        updateSelectionText = true;
    }

    public MapEntityComponent getSelectedPlanet() {
        // Check only the selected planet for any external state changes.
        if (selectedPlanet != null && !isValidPlanet(selectedPlanet.getToken())) clearSelection();
        return selectedPlanet;
    }

    @Override
    public boolean hasSelection() {
        return getSelectedPlanet() != null;
    }

    @Override
    public SectorEntityToken getSelectedEntity() {
        MapEntityComponent selected = getSelectedPlanet();
        return selected == null ? null : selected.getToken();
    }

    @Override
    public Vector2f getSelectedLocation() {
        SectorEntityToken selected = getSelectedEntity();
        return selected instanceof PlanetAPI planet ? structure.getPlacementLocation(planet) : null;
    }

    @Override
    public boolean consumeSelectionTextUpdate() {
        boolean changed = updateSelectionText;
        updateSelectionText = false;
        return changed;
    }

    @Override
    public void createSelectionText(TooltipMakerAPI tooltip) {
        if (eligiblePlanets.isEmpty()) {
            tooltip.addPara("No eligible player-owned planets in this star system.", Misc.getNegativeHighlightColor(), 0f);
        } else if (hasSelection()) {
            tooltip.addPara("Selected planet: %s. Three structures will be built in orbit. Press %s to clear.",
                    0f, Color.ORANGE, getSelectedEntity().getName(), "D");
        } else {
            tooltip.addPara("Click a highlighted planet frame to construct the complete %s.",
                    0f, Color.ORANGE, structure.getArrayName());
        }
    }

    @Override
    protected void afterFinalize() {
        getPointer().setCanRender(false);
    }

    @Override
    public void onClick(Vector2f inUIWorldCoordinates, InputEventAPI event) {
        if (!event.isLMBUpEvent() || indicators == null) return;
        MapEntityComponent point = indicators.getPlanetAt(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
        if (!selectPlanet(point)) return;
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (!event.isConsumed() && event.isKeyUpEvent() && event.getEventValue() == Keyboard.KEY_D && hasSelection()) {
                clearSelection();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                event.consume();
            }
        }
        super.processInput(events);
    }

    @Override
    public void clearUI() {
        clearSelection();
        indicators = null;
        eligiblePlanets.clear();
        super.clearUI();
    }
}
