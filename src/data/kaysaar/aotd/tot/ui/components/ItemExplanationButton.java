package data.kaysaar.aotd.tot.ui.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.function.Consumer;

public class ItemExplanationButton extends CustomButton {
    boolean isSpecialItem;
    Consumer<TooltipMakerAPI> description;
    public int quantity;

    float minimumHeight;
    float textHeight;
    float countHeight;
    float footerHeight;
    float countWidth;
    float textX;
    float textWidth;

    public static final float ICON_SIZE = 48f;

    public float padding = 6f;
    public float gap = 6f;
    public float footerGap = 3f;
    public String cargoText = "IN YOUR COLONY CARGO HOLDS";

    public ItemExplanationButton(float width, float height, Object buttonData, float indent,
                                 Color base, Color bg, Color bright) {
        super(width, height, buttonData, indent, base, bg, bright);
        minimumHeight = Math.max(75f, height);
        isSpecialItem = buttonData instanceof SpecialItemSpecAPI;
    }

    public void setDescription(Consumer<TooltipMakerAPI> description) {
        this.description = description;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void createDescription(TooltipMakerAPI tooltip) {
        if (description != null) description.accept(tooltip);
    }

    public void calculateLayout() {
        CustomPanelAPI mockup = Global.getSettings().createCustom(width, 1f, null);
        TooltipMakerAPI count = mockup.createUIElement(10000, 1f, false);

        String quantityText = Misc.getWithDGS(quantity);
        count.setParaInsigniaLarge();
        countWidth = count.addPara(
                quantityText, Misc.getTextColor(), 0f
        ).computeTextWidth(quantityText);
        countHeight = count.getHeightSoFar();

        textX = indent + padding + ICON_SIZE + gap + 12f + gap;
        textWidth = Math.max(1f, width - textX - padding);

        TooltipMakerAPI text = mockup.createUIElement(textWidth, 1f, false);
        createDescription(text);
        textHeight = text.getHeightSoFar();

        TooltipMakerAPI cargo = mockup.createUIElement(textWidth, 1f, false);
        cargo.setParaFont(Fonts.VICTOR_10);
        cargo.addPara(cargoText, Misc.getGrayColor(), 0f);
        footerHeight = cargo.getHeightSoFar();

        float contentHeight = Math.max(
                Math.max(ICON_SIZE, countHeight),
                textHeight + footerGap + footerHeight
        );
        height = Math.max(minimumHeight, contentHeight + padding * 2f);
    }

    @Override
    public void createUI() {
        calculateLayout();
        mainPanel.getPosition().setSize(width, height);
        super.createUI();
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        String icon = isSpecialItem
                ? ((SpecialItemSpecAPI) buttonData).getIconName()
                : ((CommoditySpecAPI) buttonData).getIconName();

        CustomPanelAPI content = Global.getSettings().createCustom(width, height, null);
        if( buttonData instanceof CommoditySpecAPI specAPI){
            mainButton.setButtonPressedSound(specAPI.getSoundIdDrop());
        }
        float iconX = indent + padding;
        float iconY = (height - ICON_SIZE) / 2f;

        ImageViewer image = new ImageViewer(ICON_SIZE, ICON_SIZE, icon);
        content.addComponent(image.getComponentPanel()).inTL(iconX, iconY);

        TooltipMakerAPI count = content.createUIElement(countWidth+10, countHeight, false);
        count.setParaInsigniaLarge();
        count.addPara(Misc.getWithDGS(quantity), Misc.getGrayColor(), 0f);
        content.addUIElement(count).inTL(textX-gap-countWidth-10f, iconY-5);

        float descriptionAreaHeight = height - padding * 2f - footerGap - footerHeight;

        TooltipMakerAPI text = content.createUIElement(textWidth, textHeight, false);
        createDescription(text);
        content.addUIElement(text).inTL(
                textX, padding + (descriptionAreaHeight - textHeight) / 2f
        );

        TooltipMakerAPI cargo = content.createUIElement(textWidth, footerHeight, false);
        cargo.setParaFont(Fonts.VICTOR_10);
        cargo.addPara(cargoText, Misc.getGrayColor(), 0f).setAlignment(Alignment.RMID);
        content.addUIElement(cargo).inTL(textX, height - padding - footerHeight);

        tooltip.addCustom(content, 0f);
    }
}