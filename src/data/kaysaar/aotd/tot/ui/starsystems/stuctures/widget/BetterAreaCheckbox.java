
package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.ui.models.CustomButton;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
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
    public void createUI() {
        if (this.panel != null) {
            this.mainPanel.removeComponent(this.panel);
        }

        this.panel = Global.getSettings().createCustom(this.width, this.height, (CustomUIPanelPlugin)null);
        CustomPanelAPI panelCheater =Global.getSettings().createCustom(this.width, this.height, (CustomUIPanelPlugin)null);
        TooltipMakerAPI tooltip = panelCheater.createUIElement(this.width, this.height, false);
        TooltipMakerAPI tooltipActualButton = this.panel.createUIElement(this.width, this.height, false);
        this.mainButton = this.createButton(tooltipActualButton);
        this.createButtonContent(tooltip);
        panelCheater.addUIElement(tooltip).inTL(0,0);
        this.panel.addUIElement(tooltipActualButton).inTL(0.0F, 0.0F);
        ((UIPanelAPI) ReflectionUtilis.invokeMethodWithAutoProjection("getPanel",mainButton)).addComponent(panelCheater).inTL(0,0f);
        this.mainPanel.addComponent(this.panel).inTL(0.0F, 0.0F);
        this.isCreated = true;
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
