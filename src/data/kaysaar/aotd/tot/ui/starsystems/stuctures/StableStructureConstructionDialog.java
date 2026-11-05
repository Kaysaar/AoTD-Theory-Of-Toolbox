package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.table.StableStructureTable;
import org.lwjgl.input.Keyboard;

import java.util.LinkedHashSet;

public class StableStructureConstructionDialog extends BasePopUpDialog {
    StarSystemAPI currentStarSystem;
    StableStructureTable table;
    StableStructureDetailedPanel detailedPanel;
    StableStructureInfoBottom bottom;

    public StableStructureConstructionDialog(StarSystemAPI currentStarSystem) {
        super(null);
        this.currentStarSystem = currentStarSystem;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createHeaader(panelAPI);

        float width = panelAPI.getPosition().getWidth() - 30;
        float height = panelAPI.getPosition().getHeight() - y;

        TooltipMakerAPI tooltip = panelAPI.createUIElement(width, height, false);
        createContentForDialog(tooltip, width, height - 70);

        panelAPI.addUIElement(tooltip).inTL(x, y);
        addTooltip(tooltip);

        createConfirmAndCancelSection(panelAPI);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI button = tooltip.addButton("Build", "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 160, 25, 0f);
        button.setShortcut(Keyboard.KEY_G, true);
        confirmButton = button;
        button.setEnabled(false);
        return button;
    }

    @Override
    public ButtonAPI generateCancelButton(TooltipMakerAPI tooltip) {
        ButtonAPI button = tooltip.addButton("Dismiss", "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, buttonConfirmWidth, 25, 0f);
        button.setShortcut(Keyboard.KEY_A, true);
        cancelButton = button;
        return button;
    }

    public void createContentForDialog(TooltipMakerAPI tooltip, float width, float height) {
        if (table == null) {
            table = new StableStructureTable(StableStructureTable.getWidth() + 16, height - 30, true, 0, 0, currentStarSystem);
            table.createSections();
            table.createTable();
            detailedPanel = new StableStructureDetailedPanel(null, currentStarSystem, 450, height);
            detailedPanel.setQueueHandler(spec -> {
                if (spec == null || !canShowStructure(spec.getImplClassInstance())) return;
                table.currentlyChosen = null;
                detailedPanel.setSpec(spec);
            });
        } else {
            table.recreateTable();
        }

        float bottomWidth = Math.max(100, width - buttonConfirmWidth * 2 - 45);

        if (bottom == null) {
            bottom = new StableStructureInfoBottom(currentStarSystem, bottomWidth, 30);
        } else {
            bottom.createUI();
        }

        tooltip.addCustom(table.mainPanel, 5f).getPosition().inTL(0, 30);
        tooltip.addCustom(detailedPanel.getMainPanel(), 5f).getPosition().inTL(width - detailedPanel.getMainPanel().getPosition().getWidth() - 5, 0);
        tooltip.addCustom(bottom.getMainPanel(), 5f).getPosition().inTL(5, height + 33);
    }

    public AoTDStableStructureSpec getFirstStageSpec(AoTDStableStructureSpec spec) {
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        while (spec != null && spec.getDowngrade() != null) {
            if (!visited.add(spec.getId())) {
                throw new IllegalStateException("Circular stable structure upgrade path: " + spec.getId());
            }
            spec = AoTDStableStructureSpecManager.getSpec(spec.getDowngrade());
        }
        return spec;
    }

    public boolean canShowStructure(AoTDStableStructureImpl impl) {
        if (impl == null) return false;
        return impl.isAvailableToBuild(currentStarSystem, Global.getSector().getPlayerFaction())
                || impl.showWhenUnavailable(currentStarSystem, Global.getSector().getPlayerFaction());
    }

    public boolean canStartConstruction() {
        if (detailedPanel == null || detailedPanel.spec == null) return false;

        AoTDStableStructureImpl impl = detailedPanel.getCurrentImpl();
        if (impl == null || !AoTDToolboxMisc.hasAvailableSystemStructureSlot(currentStarSystem)) return false;
        FactionAPI claiming = AshMisc.getClaimingFaction(currentStarSystem.getCenter());
        if (claiming == null || !claiming.isPlayerFaction()) return false;
        if (!impl.isAvailableToBuild(currentStarSystem, Global.getSector().getPlayerFaction())) return false;

        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        return credits >= Math.max(0f, detailedPanel.getInitialStageCost());
    }

    @Override
    public void applyConfirmScript() {
        if (!canStartConstruction()) return;

        AoTDStableStructureImpl impl = detailedPanel.getCurrentImpl();
        CustomEntitySpecAPI spec = Global.getSettings().getCustomEntitySpec(detailedPanel.spec.getId());
        ChooseLocationForBuildingDialog dialog = new ChooseLocationForBuildingDialog(impl, "Choose location for " + spec.getDefaultName(), currentStarSystem);
        AshMisc.initPopUpDialog(dialog, 1000, 600);
    }

    public void setButtonStage(ButtonAPI bt, boolean stage) {
        if (stage != bt.isEnabled()) {
            bt.setEnabled(stage);
        }
    }

    @Override
    public void advance(float amount) {
        if (table != null && table.currentlyChosen != null) {
            AoTDStableStructureSpec spec = table.currentlyChosen.spec;
            table.currentlyChosen = null;
            if (spec != null && canShowStructure(spec.getImplClassInstance())) detailedPanel.setSpec(spec);
        }

        if (confirmButton != null) {
            setButtonStage(confirmButton, canStartConstruction());
        }

        super.advance(amount);
    }
}
