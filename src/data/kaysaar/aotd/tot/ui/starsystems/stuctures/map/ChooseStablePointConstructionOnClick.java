package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapEntityOnClickHook;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;

public class ChooseStablePointConstructionOnClick implements MapEntityOnClickHook {
    ChooseStablePointForBuildingMap map;

    public ChooseStablePointConstructionOnClick(ChooseStablePointForBuildingMap map) {
        this.map = map;
    }

    @Override
    public void onClick(MapEntityComponent token, InputEventAPI event, CustomPanelAPI anchor, MapMainComponent component) {
        if(!event.isConsumed() && event.isLMBUpEvent() && anchor.getPosition().containsEvent(event)
                && component.getMainPanel().getPosition().containsEvent(event) && map.selectPoint(token)){
            Global.getSoundPlayer().playUISound("ui_button_pressed",1,1);
            event.consume();
        }
    }

    @Override
    public boolean shouldTrigger(InputEventAPI event) {
        return !event.isConsumed() && event.isLMBUpEvent();
    }
}
