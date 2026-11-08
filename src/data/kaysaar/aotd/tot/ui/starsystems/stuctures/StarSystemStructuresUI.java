package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.listeners.AoTDSystemStructureColonizationListener;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import data.kaysaar.aotd.tot.stablestructures.AoTDSystemStructureCapacityManager;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget.StableStructureWidget;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import com.fs.starfarer.api.util.IntervalUtil;

public class StarSystemStructuresUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    StarSystemAPI system;
    ButtonAPI manageButton;
    IntervalUtil refresh = new IntervalUtil(0.25f, 0.25f);
    List<SectorEntityToken> displayedEntities = new ArrayList<>();
    int displayedCapacity;
    int displayedUsedSlots;
    boolean refreshRequested;

    public void requestRefresh() {
        refreshRequested = true;
        AoTDStableStructureConstruction.requestUIRefresh(system);
    }

    public List<SectorEntityToken> getStructureEntities() {
        List<SectorEntityToken> entities = AoTDToolboxMisc.getSystemStructureEntities(system);
        for (SectorEntityToken token : system.getAllEntities()) {
            if (token.isExpired() || token.getCustomEntitySpec() == null) continue;
            if (AoTDStableStructureSpecManager.getSpec(token.getCustomEntitySpec().getId()) == null) continue;
            if (!entities.contains(token)) entities.add(token);
        }

        int nextOrder = 0;
        for (SectorEntityToken token : entities) {
            Object value = token.getMemoryWithoutUpdate().get(AoTDStableStructureConstruction.WIDGET_ORDER);
            if (value instanceof Number) {
                nextOrder = Math.max(nextOrder, ((Number) value).intValue() + 1);
            }
        }
        for (SectorEntityToken token : entities) {
            Object value = token.getMemoryWithoutUpdate().get(AoTDStableStructureConstruction.WIDGET_ORDER);
            if (!(value instanceof Number)) {
                token.getMemoryWithoutUpdate().set(AoTDStableStructureConstruction.WIDGET_ORDER, nextOrder++);
            }
        }

        entities.sort((first, second) -> {
            int result = Boolean.compare(isUnderConstruction(first), isUnderConstruction(second));
            if (result != 0) return result;
            result = Integer.compare(getStructureOrder(first), getStructureOrder(second));
            if (result != 0) return result;
            return Integer.compare(getWidgetOrder(first), getWidgetOrder(second));
        });
        return entities;
    }

    public boolean isUnderConstruction(SectorEntityToken token) {
        AoTDStableStructureImpl impl = (AoTDStableStructureImpl) token.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
        if (impl != null) return impl.isBuilding() && !impl.isUpgrading();
        return AoTDStableStructureConstruction.SCAFFOLD.equals(token.getCustomEntityType());
    }

    public int getStructureOrder(SectorEntityToken token) {
        AoTDStableStructureImpl impl = (AoTDStableStructureImpl) token.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
        String id = impl != null ? impl.id : token.getCustomEntityType();
        AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(id);
        return spec == null ? Integer.MAX_VALUE : spec.getOrder();
    }

    public int getWidgetOrder(SectorEntityToken token) {
        Object value = token.getMemoryWithoutUpdate().get(AoTDStableStructureConstruction.WIDGET_ORDER);
        return value instanceof Number ? ((Number) value).intValue() : Integer.MAX_VALUE;
    }

    public StarSystemStructuresUI(float width, float height, StarSystemAPI system) {
        this.system = system;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public void setSystem(StarSystemAPI system) {
        this.system = system;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        // Also fixes unclaimed pre-existing structures when this UI is opened.
        AoTDSystemStructureColonizationListener.claimUnownedStructures(system);
        AoTDSystemStructureCapacityManager.update(system);
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        FactionAPI faction = AshMisc.getClaimingFaction(system.getCenter());

        contentPanel = Global.getSettings().createCustom(
                mainPanel.getPosition().getWidth(),
                mainPanel.getPosition().getHeight(),
                null
        );

        TooltipMakerAPI tooltip = contentPanel.createUIElement(
                contentPanel.getPosition().getWidth(),
                contentPanel.getPosition().getHeight() - 30,
                true
        );
        TooltipMakerAPI tooltipBottom = contentPanel.createUIElement(
                contentPanel.getPosition().getWidth(),
                25,
                false
        );
        float separator = 10;              // fixed
        float rowGap = 20;                 // vertical gap between rows (tweak)
        float availableWidth = contentPanel.getPosition().getWidth();

        float widgetW = StableStructureWidget.width;
        float widgetH = StableStructureWidget.height; // <-- use your widget height constant

        List<SectorEntityToken> objs = getStructureEntities();
        displayedEntities = new ArrayList<>(objs);
        int total = objs.size();
        int structuresUsed = AoTDToolboxMisc.getUsedSystemStructureSlots(system);
        displayedUsedSlots = structuresUsed;

        int perRow = (int) Math.floor((availableWidth + separator) / (widgetW + separator));
        perRow = Math.max(1, perRow);

        int rowsPlaced = (total + perRow - 1) / perRow;

        // Place
        for (int i = 0; i < total; i++) {
            int row = i / perRow;
            int col = i % perRow;


            int rowStartIndex = row * perRow;
            int remaining = total - rowStartIndex;
            int itemsThisRow = Math.min(perRow, remaining);

            float rowW = itemsThisRow * widgetW + (itemsThisRow - 1) * separator;
            float startX = Math.round((availableWidth - rowW) * 0.5f);  // centered

            float x = startX + col * (widgetW + separator);
            float y = row * (widgetH + rowGap);

            SectorEntityToken token = objs.get(i);
            StableStructureWidget widget = new StableStructureWidget(token, this::requestRefresh);

            tooltip.addCustom(widget.getMainPanel(), 0f).getPosition().inTL(x, y);
        }


        float neededHeight = rowsPlaced * widgetH + Math.max(0, rowsPlaced - 1) * rowGap;
        tooltip.setHeightSoFar(neededHeight);
        tooltipBottom.setParaFont(Fonts.INSIGNIA_LARGE);
        displayedCapacity = AoTDToolboxMisc.getSystemStructureCapacityStat(system).getModifiedInt();
        LabelAPI label = tooltipBottom.addPara("System Structures: %s / %s", 0f, Misc.getGrayColor(),
                structuresUsed >= displayedCapacity ? Misc.getNegativeHighlightColor() : Color.ORANGE,
                String.valueOf(structuresUsed), String.valueOf(displayedCapacity));
        label.getPosition().inTL(contentPanel.getPosition().getWidth() - label.computeTextWidth(label.getText()) - 5, 1);
        tooltipBottom.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) { return false; }

            @Override
            public float getTooltipWidth(Object tooltipParam) { return 500f; }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                StableStructureInfoBottom.addSystemStructureCapacityBreakdown(tooltip, system);
            }
        }, TooltipMakerAPI.TooltipLocation.ABOVE, false);
        float x = contentPanel.getPosition().getWidth() - label.computeTextWidth(label.getText()) - 5;
        manageButton = tooltipBottom.addButton("Build new Structure", null, faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, CutStyle.TL_BR, Math.min(x - 20, 300), 25, 0f);
        manageButton.setShortcut(Keyboard.KEY_A, true);
        if (!faction.isPlayerFaction() || structuresUsed >= displayedCapacity) {
            manageButton.setEnabled(false);
        }

        manageButton.getPosition().inTL(5, 0);


        contentPanel.addUIElement(tooltip).inTL(0, 0f);
        contentPanel.addUIElement(tooltipBottom).inTL(0, contentPanel.getPosition().getHeight() - 20);
        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
    }


    @Override
    public void clearUI() {

    }

    @Override
    public void positionChanged(PositionAPI position) {

    }

    @Override
    public void renderBelow(float alphaMult) {

    }

    @Override
    public void render(float alphaMult) {

    }

    @Override
    public void advance(float amount) {
        if (refreshRequested) {
            refreshRequested = false;
            createUI();
        }
        refresh.advance(amount);
        if (refresh.intervalElapsed()) {
            boolean changed = !displayedEntities.equals(getStructureEntities());
            if (changed) AoTDStableStructureConstruction.requestUIRefresh(system);
            if (changed || displayedCapacity != AoTDToolboxMisc.getSystemStructureCapacityStat(system).getModifiedInt()
                    || displayedUsedSlots != AoTDToolboxMisc.getUsedSystemStructureSlots(system)) createUI();
            boolean canManage = AshMisc.getClaimingFaction(system.getCenter()).isPlayerFaction()
                    && AoTDToolboxMisc.getUsedSystemStructureSlots(system) < displayedCapacity;
            if (manageButton != null && manageButton.isEnabled() != canManage) manageButton.setEnabled(canManage);
        }
        if (manageButton != null && manageButton.isChecked()) {
            manageButton.setChecked(false);
            if (!manageButton.isEnabled() || !AoTDToolboxMisc.hasAvailableSystemStructureSlot(system)) return;
            StableStructureConstructionDialog dialog  = new StableStructureConstructionDialog(system);
            AshMisc.initPopUpDialog(dialog,1150,700);

        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {

    }

    @Override
    public void buttonPressed(Object buttonId) {

    }
}
