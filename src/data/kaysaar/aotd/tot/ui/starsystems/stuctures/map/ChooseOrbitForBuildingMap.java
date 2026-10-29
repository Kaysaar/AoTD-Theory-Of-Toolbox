package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import ashlib.data.plugins.ui.models.resizable.map.PlanetRenderResizableComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.Color;
import java.util.Objects;

public class ChooseOrbitForBuildingMap extends MapMainComponent implements StructureLocationPicker {
    ChooseOrbitConstructionComponent orbitComponent;
    String customEntitySpecId;
    boolean updateSelectionText = false;
    MapEntityComponent lastOrbitFocus;
    boolean lastHasSelection;
    int lastRadius;
    String lastFocusName;
    public ChooseOrbitForBuildingMap(float width, float height, StarSystemAPI system, String customEntitySpecId) {
        super(width, height, system);
        this.customEntitySpecId = customEntitySpecId;
        orbitComponent.setCustomEntitySpecId(customEntitySpecId);
    }
    @Override
    protected void afterAddOrbits() {
        orbitComponent = new ChooseOrbitConstructionComponent(0f,this,customEntitySpecId);
        getMapZoom().addComponent(orbitComponent,27000,27000);

    }
    @Override
    protected void afterAddPlanets() {
        for (MapEntityComponent allEntitiesComponent : getMapZoom().getAllEntitiesComponents()) {
            if(allEntitiesComponent instanceof PlanetRenderResizableComponent comp) {
                comp.addHook(new ChooseOrbitConstrucitonOnClick(orbitComponent),"orbit");
            }
        }
    }
    @Override
    public void onClick(Vector2f inUIWorldCoordinates, InputEventAPI event) {
        if(event.isLMBUpEvent()){
            if(orbitComponent.getCurrentlyCenteredAround()!=null){
                if(orbitComponent.isLocked()){
                    orbitComponent.unlockFollow();
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1, 1);

                }
                else{
                    orbitComponent.lockToCurrentFollower();
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1, 1);

                }
            }
        }
    }

    @Override
    protected void afterFinalize() {
        getPointer().setCanRender(false);
    }

    public ChooseOrbitConstructionComponent getOrbitComponent() {
        return orbitComponent;
    }

    @Override
    public boolean hasSelection() {
        return orbitComponent!=null && orbitComponent.isLocked()
                && orbitComponent.getCurrentlyCenteredAround()!=null
                && !orbitComponent.getCurrentlyCenteredAround().getToken().isExpired()
                && orbitComponent.getSavedRadiusWorld()!=null && orbitComponent.getSavedGateAngleRad()!=null;
    }

    @Override
    public SectorEntityToken getSelectedEntity() {
        return hasSelection()?orbitComponent.getCurrentlyCenteredAround().getToken():null;
    }

    @Override
    public Vector2f getSelectedLocation() {
        if(!hasSelection())return null;
        Vector2f center = getSelectedEntity().getLocation();
        float radius = orbitComponent.getSavedRadiusWorld();
        float angle = orbitComponent.getSavedGateAngleRad();
        return new Vector2f(center.x+(float)Math.cos(angle)*radius,center.y+(float)Math.sin(angle)*radius);
    }

    public void requestSelectionTextUpdate(){
        updateSelectionText = true;
    }

    @Override
    public boolean consumeSelectionTextUpdate() {
        MapEntityComponent focus = orbitComponent==null?null:orbitComponent.getCurrentlyCenteredAround();
        boolean selected = hasSelection();
        int radius = selected?Math.round(orbitComponent.getSavedRadiusWorld()):0;
        String focusName = selected?focus.getToken().getName():null;
        // The orbit hook and renderer can change selection/radius directly.
        if(focus!=lastOrbitFocus || selected!=lastHasSelection || radius!=lastRadius || !Objects.equals(focusName,lastFocusName)){
            lastOrbitFocus = focus;
            lastHasSelection = selected;
            lastRadius = radius;
            lastFocusName = focusName;
            requestSelectionTextUpdate();
        }
        boolean update = updateSelectionText;
        updateSelectionText = false;
        return update;
    }

    @Override
    public void createSelectionText(TooltipMakerAPI tooltip) {
        if(!hasSelection()){
            tooltip.addPara("Select an orbit focus, then click on the map to place the structure preview. Press %s to clear.",0f,Color.ORANGE,"D");
        }
        else{
            tooltip.addPara("Orbit focus: %s. Radius: %s.",0f,Color.ORANGE,getSelectedEntity().getName(),""+Math.round(orbitComponent.getSavedRadiusWorld()));
        }
    }
}
