package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import ashlib.data.plugins.ui.models.resizable.map.PlanetRenderResizableComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

public class ChooseOrbitForBuildingMap extends MapMainComponent {
    ChooseOrbitConstructionComponent orbitComponent;
    String customEntitySpecId;
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
}
