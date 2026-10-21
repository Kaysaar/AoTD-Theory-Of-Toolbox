
package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.ui.models.CustomButton;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class BetterAreaCheckbox extends CustomButton {

    int keyBind;
    boolean doesKeyBind = false;
    String buttonText;
    Color c;
    public BetterAreaCheckbox(float width, float height, Object buttonData, float indent, Color base, Color bg, Color bright, String buttonText) {
        super(width, height, buttonData, indent, base, bg, bright);
        this.c =base;
        this.buttonText = buttonText;
    }

    public BetterAreaCheckbox(float width, float height, Object buttonData, float indent, Color base, Color bg, Color bright, String buttonText, int keyBind) {
        super(width, height, buttonData, indent, base, bg, bright);
        this.buttonText = buttonText;
        this.doesKeyBind = true;
        this.c =base;
        this.keyBind = keyBind;
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        if (mainButton != null && doesKeyBind) {
            mainButton.setQuickMode(false);
            mainButton.setShortcut(keyBind, false);
        }

        if (buttonText == null || buttonText.isEmpty()) {
            return;
        }

        LabelAPI label;

        if (doesKeyBind) {
            String keyName = Keyboard.getKeyName(keyBind);

            label = tooltip.addPara(
                    buttonText + " [%s]",
                    0f,c,
                    Color.ORANGE,
                    keyName
            );
        } else {
            label = tooltip.addPara(buttonText,c, 0f);
        }

        label.getPosition().inTL(
                indent + 8f,
                (height - label.getPosition().getHeight()) / 2f
        );

        super.createButtonContent(tooltip);
    }
}
