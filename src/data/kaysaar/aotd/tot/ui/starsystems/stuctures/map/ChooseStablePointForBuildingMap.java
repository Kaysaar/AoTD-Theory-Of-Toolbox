package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import ashlib.data.plugins.ui.models.resizable.map.StableLocationComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class ChooseStablePointForBuildingMap extends MapMainComponent implements StructureLocationPicker {
    StarSystemAPI currentStarSystem;
    MapEntityComponent selectedPoint;
    ArrayList<MapEntityComponent> stablePoints = new ArrayList<>();
    StablePointConstructionComponent constructionComponent;
    boolean updateSelectionText = false;
    int lastAvailablePoints = -1;

    public ChooseStablePointForBuildingMap(float width, float height, StarSystemAPI system, String customEntitySpecId) {
        super(width,height,system);
        this.currentStarSystem = system;
        constructionComponent = new StablePointConstructionComponent(this,customEntitySpecId);
        getMapZoom().addComponent(constructionComponent,WORLD_X,WORLD_X);
        for (MapEntityComponent component : getMapZoom().getAllEntitiesComponents()) {
            if(component instanceof StableLocationComponent && isValidStablePoint(component.getToken())){
                stablePoints.add(component);
                component.addHook(new ChooseStablePointConstructionOnClick(this),"construction");
            }
        }
    }

    public boolean isValidStablePoint(SectorEntityToken token){
        return token!=null && !token.isExpired() && token.getStarSystem()==currentStarSystem
                && token.getCustomEntitySpec()!=null
                && Entities.STABLE_LOCATION.equals(token.getCustomEntitySpec().getId())
                && !token.getMemoryWithoutUpdate().contains(AoTDStableStructureSpecManager.memKeyInEntities);
    }

    public boolean selectPoint(MapEntityComponent point){
        if(!stablePoints.contains(point) || !isValidStablePoint(point.getToken()))return false;
        if(selectedPoint==point)return true;
        clearSelection();
        selectedPoint = point;
        selectedPoint.setForceHighlight(true);
        requestSelectionTextUpdate();
        return true;
    }

    public void clearSelection(){
        if(selectedPoint==null)return;
        selectedPoint.setForceHighlight(false);
        selectedPoint = null;
        requestSelectionTextUpdate();
    }

    public MapEntityComponent getSelectedPoint(){
        if(selectedPoint!=null && !isValidStablePoint(selectedPoint.getToken()))clearSelection();
        return selectedPoint;
    }

    @Override
    public boolean hasSelection() {
        return getSelectedPoint()!=null;
    }

    @Override
    public SectorEntityToken getSelectedEntity() {
        return hasSelection()?selectedPoint.getToken():null;
    }

    @Override
    public Vector2f getSelectedLocation() {
        SectorEntityToken token = getSelectedEntity();
        return token==null?null:new Vector2f(token.getLocation());
    }

    public void requestSelectionTextUpdate(){
        updateSelectionText = true;
    }

    @Override
    public boolean consumeSelectionTextUpdate() {
        getSelectedPoint();
        int availablePoints = (int)stablePoints.stream().filter(point -> isValidStablePoint(point.getToken())).count();
        if(availablePoints!=lastAvailablePoints){
            lastAvailablePoints = availablePoints;
            requestSelectionTextUpdate();
        }
        boolean update = updateSelectionText;
        updateSelectionText = false;
        return update;
    }

    @Override
    public void createSelectionText(TooltipMakerAPI tooltip) {
        if(stablePoints.stream().noneMatch(point -> isValidStablePoint(point.getToken()))){
            tooltip.addPara("No available stable points on this map.",Misc.getNegativeHighlightColor(),0f);
            return;
        }
        if(hasSelection()){
            tooltip.addPara("Stable point selected. Click another stable point to move the preview, or press %s to clear.",0f,Color.ORANGE,"D");
        }
        else{
            tooltip.addPara("Click a stable point to place the structure preview.",0f);
        }
    }

    @Override
    protected void afterFinalize() {
        getPointer().setCanRender(false);
    }

    @Override
    public void onClick(Vector2f inUIWorldCoordinates, InputEventAPI event) {
        // Empty space and other entities are not placement targets for this picker.
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if(!event.isConsumed() && event.isKeyUpEvent() && event.getEventValue()==Keyboard.KEY_D && hasSelection()){
                clearSelection();
                Global.getSoundPlayer().playUISound("ui_button_pressed",1,1);
                event.consume();
            }
        }
        super.processInput(events);
    }

    @Override
    public void clearUI() {
        clearSelection();
        for (MapEntityComponent point : stablePoints) {
            point.getHooks().remove("construction");
        }
        stablePoints.clear();
        super.clearUI();
    }
}
