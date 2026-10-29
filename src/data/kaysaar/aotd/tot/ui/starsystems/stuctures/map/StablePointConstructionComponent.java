package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.ResizableComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.*;

public class StablePointConstructionComponent extends ResizableComponent {
    ChooseStablePointForBuildingMap map;
    SpriteAPI sprite;
    float spriteWidth,spriteHeight;

    public StablePointConstructionComponent(ChooseStablePointForBuildingMap map, String customEntitySpecId) {
        this.map = map;
        CustomEntitySpecAPI spec = Global.getSettings().getCustomEntitySpec(customEntitySpecId);
        sprite = Global.getSettings().getSprite(spec.getSpriteName());
        spriteWidth = spec.getSpriteWidth();
        spriteHeight = spec.getSpriteHeight();
        componentPanel = Global.getSettings().createCustom(1,1,this);
    }

    @Override
    public void render(float alphaMult) {
        MapEntityComponent point = map.getSelectedPoint();
        if(point==null)return;
        float spriteScale = Math.max(point.scale,24f/Math.max(1f,Math.max(spriteWidth,spriteHeight)));
        sprite.setSize(spriteWidth*spriteScale,spriteHeight*spriteScale);
        sprite.setColor(Color.cyan);
        sprite.setAlphaMult(0.9f*alphaMult);
        sprite.setAngle(point.getToken().getFacing());
        sprite.renderAtCenter(point.getComponentPanel().getPosition().getCenterX(),point.getComponentPanel().getPosition().getCenterY());
    }
}
