package data.kaysaar.aotd.tot.ui.components;

import ashlib.data.plugins.ui.models.CustomButton;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;

import java.awt.*;

public class ItemExplanationButton extends CustomButton {
    boolean isSpecialItem;
    public ItemExplanationButton(float width, float height, Object buttonData, float indent, Color base, Color bg, Color bright) {
        super(width, height, buttonData, indent, base, bg, bright);
        isSpecialItem = buttonData instanceof SpecialItemSpecAPI;
    }
}
