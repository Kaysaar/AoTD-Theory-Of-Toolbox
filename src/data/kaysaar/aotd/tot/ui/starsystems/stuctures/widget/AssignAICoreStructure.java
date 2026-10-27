package data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.BasePopUpDialog;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.ui.components.ItemExplanationButton;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AssignAICoreStructure extends BasePopUpDialog {
    StableStructureWidget widget;
    AoTDStableStructureImpl structure;
    String originalAICoreAssigned;
    String currentAICoreAssigned;
    CustomPanelAPI headerPanel, listOfAICores, mainSubPanel, content;
    TooltipMakerAPI body;
    ButtonAPI unassign;
    float height;
    float width;
    float currentOffsetY;

    public AssignAICoreStructure(StableStructureWidget widget) {
        this(widget.getStructure());
        this.widget = widget;
    }

    public AssignAICoreStructure(AoTDStableStructureImpl structure) {
        super(null);
        this.structure = structure;
        originalAICoreAssigned = structure.getAICoreId();
        currentAICoreAssigned = originalAICoreAssigned;
    }

    public List<CargoAPI> getLocalStorages() {
        List<CargoAPI> storages = new ArrayList<>();
        SectorEntityToken entity = structure.getCurrentlyTiedEntity();

        if (entity == null || entity.getContainingLocation() == null) {
            return storages;
        }

        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(entity.getContainingLocation())) {
            if (!Objects.equals(market.getFactionId(), entity.getFaction().getId())) {
                continue;
            }
            if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null) {
                continue;
            }

            CargoAPI cargo = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
            if (cargo != null && !storages.contains(cargo)) {
                storages.add(cargo);
            }
        }

        return storages;
    }

    public int getAvailableCoreCount(String coreId, List<CargoAPI> storages) {
        int quantity = 0;

        for (CargoAPI cargo : storages) {
            quantity += (int) cargo.getCommodityQuantity(coreId);
        }

        return quantity;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createHeaader(panelAPI);
        width = panelAPI.getPosition().getWidth() - 30f;
        height = panelAPI.getPosition().getHeight() - y - 55f;
        content = Global.getSettings().createCustom(width, height, null);
        panelAPI.addComponent(content).inTL(x, y);
        rebuild();
        createConfirmAndCancelSection(panelAPI);
    }

    public void rebuild() {
        if (body != null && body.getExternalScroller() != null) {
            currentOffsetY = body.getExternalScroller().getYOffset();
        }
        if (mainSubPanel != null) content.removeComponent(mainSubPanel);

        mainSubPanel = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI tooltip = mainSubPanel.createUIElement(width, height, false);
        createContentForDialog(tooltip, width);
        mainSubPanel.addUIElement(tooltip).inTL(0f, 0f);
        content.addComponent(mainSubPanel).inTL(0f, 0f);

        if (body != null && body.getExternalScroller() != null) {
            body.getExternalScroller().setYOffset(currentOffsetY);
        }
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        float headerHeight = 230f;
        createCurrentlyAssignedContent(width, headerHeight);
        createAICoreListSection(width, Math.max(120f, height - headerHeight - 5f));
        tooltip.addCustom(headerPanel, 0f);
        tooltip.addCustom(listOfAICores, 5f);
    }

    public void createCurrentlyAssignedContent(float width, float height) {
        headerPanel = Global.getSettings().createCustom(width, height, null);
        FactionAPI faction = structure.currentlyAssignedToken.getFaction();

        if (AshMisc.isStringValid(currentAICoreAssigned)) {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(currentAICoreAssigned);
            TooltipMakerAPI title = headerPanel.createUIElement(width, 25f, false);
            title.setParaFont(Fonts.ORBITRON_24AABOLD);
            title.addPara(spec.getName(), Misc.getHighlightColor(), 0f).setAlignment(Alignment.MID);
            headerPanel.addUIElement(title).inTL(0f, 5f);

            ImageViewer image = new ImageViewer(80f, 80f, spec.getIconName());
            headerPanel.addComponent(image.getComponentPanel()).inTL((width - 80f) / 2f, 40f);

            TooltipMakerAPI text = headerPanel.createUIElement(width - 40f, height - 165f, true);
            structure.addAICoreSection(
                    text, currentAICoreAssigned,
                    AICoreDescriptionMode.MANAGE_CORE_DIALOG_INSTALLED
            );
            headerPanel.addUIElement(text).inTL(20f, 120f);
        } else {
            TooltipMakerAPI text = headerPanel.createUIElement(width, height - 30f, false);
            LabelAPI label = text.addPara(
                    "No AI core currently assigned", faction.getBaseUIColor(), 0f
            );
            label.setAlignment(Alignment.MID);
            headerPanel.addUIElement(text).inTL(
                    0f, (height - 30f - text.getHeightSoFar()) / 2f
            );
        }

        TooltipMakerAPI buttons = headerPanel.createUIElement(width, 30f, false);
        buttons.setButtonFontOrbitron20();
        float buttonWidth = Math.min(310f, width);
        unassign = buttons.addButton(
                "Unassign AI Core", null,
                faction.getBaseUIColor(), faction.getDarkUIColor(),
                Alignment.MID, CutStyle.TL_BR, buttonWidth, 25f, 0f
        );
        unassign.setShortcut(Keyboard.KEY_T, false);
        unassign.setEnabled(AshMisc.isStringValid(currentAICoreAssigned));
        headerPanel.addUIElement(buttons).inTL(width - buttonWidth - 5f, height - 30f);
    }

    @Override
    public void createConfirmSection(CustomPanelAPI mainPanel) {
        super.createConfirmSection(mainPanel);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI bt = super.generateConfirmButton(tooltip);
        bt.setButtonPressedSound("ui_industry_install_any_item");
        bt.setShortcut(Keyboard.KEY_G,false);
        return bt;
    }

    public void createAICoreListSection(float width, float height) {
        listOfAICores = Global.getSettings().createCustom(width, height, null);
        FactionAPI faction = structure.currentlyAssignedToken.getFaction();

        TooltipMakerAPI header = listOfAICores.createUIElement(width, 20f, false);
        header.addAreaCheckbox(
                "Select AI core to assign to " + structure.getName(),null,faction.getBaseUIColor(),faction.getDarkUIColor(),faction.getBrightUIColor(),width,20,0f
        ).setClickable(false);
        listOfAICores.addUIElement(header).inTL(-5f, 0f);

        body = listOfAICores.createUIElement(width, height - 25f, true);
        body.addSpacer(0f).getPosition().inTL(0f, 0f);

        List<CargoAPI> storages = getLocalStorages();
        boolean hasCores = false;
        ArrayList<CommoditySpecAPI >specs = new ArrayList<>(Global.getSettings().getAllCommoditySpecs());
        specs.removeIf(x->!x.hasTag(Commodities.TAG_AI_CORE));
        Collections.sort(specs, new Comparator<CommoditySpecAPI>() {
            @Override
            public int compare(CommoditySpecAPI o1, CommoditySpecAPI o2) {
                return  Float.compare(o1.getOrder(), o2.getOrder());
            }
        });
        for (CommoditySpecAPI spec : specs) {
            if (!spec.hasTag(Commodities.TAG_AI_CORE)) {
                continue;
            }

            String coreId = spec.getId();
            int quantity = getAvailableCoreCount(coreId, storages);

            if (Objects.equals(coreId, originalAICoreAssigned)&&currentAICoreAssigned==null) {
                quantity++;
            }
            if (quantity <= 0) {
                continue;
            }
            if(currentAICoreAssigned!=null&&currentAICoreAssigned.equals(coreId)&&!Objects.equals(coreId, originalAICoreAssigned)){
                quantity--;
            }


            hasCores = true;

            ItemExplanationButton button = new ItemExplanationButton(
                    width - 10f, 75f,
                    spec, 0f,
                    faction.getBaseUIColor(), faction.getDarkUIColor(), faction.getBrightUIColor()
            );
            button.setQuantity(quantity);
            button.setDescription(text -> structure.addAICoreSection(
                    text, coreId, AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST
            ));
            button.createUI();
            button.setListener(() -> {
                currentAICoreAssigned = coreId;
                rebuild();
            });
            body.addCustom(button.getMainPanel(), 2f);
        }



        listOfAICores.addUIElement(body).inTL(0f, 25f);
    }

    public boolean canConfirm() {
        if (!structure.isCurrentImplementation()
                || !structure.isPlayerOwned()
                || !Objects.equals(structure.getAICoreId(), originalAICoreAssigned)) {
            return false;
        }

        if (Objects.equals(currentAICoreAssigned, originalAICoreAssigned)) {
            return true;
        }

        List<CargoAPI> storages = getLocalStorages();

        if (AshMisc.isStringValid(originalAICoreAssigned) && storages.isEmpty()) {
            return false;
        }

        return !AshMisc.isStringValid(currentAICoreAssigned)
                || getAvailableCoreCount(currentAICoreAssigned, storages) > 0;
    }

    @Override
    public void advance(float amount) {
        if (confirmButton != null) {
            confirmButton.setEnabled(canConfirm());
            if (!confirmButton.isEnabled()) confirmButton.setChecked(false);
        }
        if (unassign != null && unassign.isChecked()) {
            unassign.setChecked(false);
            currentAICoreAssigned = null;
            rebuild();
        }
        super.advance(amount);
    }

    public boolean handleAICoreTransfer(String previousCoreId, String selectedCoreId) {
        if (Objects.equals(previousCoreId, selectedCoreId)) {
            return true;
        }

        List<CargoAPI> storages = getLocalStorages();
        CargoAPI source = null;

        if (AshMisc.isStringValid(selectedCoreId)) {
            for (CargoAPI cargo : storages) {
                if (cargo.getCommodityQuantity(selectedCoreId) >= 1f) {
                    source = cargo;
                    break;
                }
            }

            if (source == null) {
                return false;
            }
        }

        if (AshMisc.isStringValid(previousCoreId) && storages.isEmpty()) {
            return false;
        }

        if (source != null) {
            source.removeCommodity(selectedCoreId, 1);
        }

        if (AshMisc.isStringValid(previousCoreId)) {
            CargoAPI destination = source != null ? source : storages.get(0);
            destination.addCommodity(previousCoreId, 1);
        }

        structure.setAICoreId(selectedCoreId);
        return true;
    }

    @Override
    public void applyConfirmScript() {
        if (!canConfirm() || Objects.equals(currentAICoreAssigned, originalAICoreAssigned)) return;
        if (!handleAICoreTransfer(originalAICoreAssigned, currentAICoreAssigned)) return;

        originalAICoreAssigned = currentAICoreAssigned;

        if (widget != null) {
            widget.createUI();
        }
    }
}