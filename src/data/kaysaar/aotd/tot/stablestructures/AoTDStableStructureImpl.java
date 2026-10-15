package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDStableStructureImpl {
    public String id;
    public String currentAICoreID;
    public String currentItemID;
    public SectorEntityToken currentlyAssignedToken;

    public void setCurrentlyAssignedToken(SectorEntityToken currentlyAssignedToken) {
        this.currentlyAssignedToken = currentlyAssignedToken;
    }

    public enum AoTDStableStructureTooltipMode {
        PANEL,
        BUILDING_MENU,
        UPGRADE_MENU
    }

    public void init(String id) {
        this.id = id;
    }

    public boolean isAvailableToBuild(StarSystemAPI systemAPI, FactionAPI factionAttemptingToBuildIt) {
        return true;
    }

    public void advance(float amount) {
    }

    public AoTDStableStructureSpec getSpec() {
        return AoTDStableStructureManager.getSpec(id);
    }

    public CustomEntitySpecAPI getSpecEntity() {
        return Global.getSettings().getCustomEntitySpec(id);
    }

    public String getName() {
        if (currentlyAssignedToken != null) return currentlyAssignedToken.getName();
        return getSpecEntity().getDefaultName();
    }

    public String getImageName() {
        return getSpecEntity().getInteractionImage();
    }

    public boolean canMountAICores() {
        return false;
    }

    public boolean canMountItems() {
        return false;
    }

    public float getBuildCostCredits() {
        return getSpec().getCreditCost();
    }

    public LinkedHashMap<String, Integer> getBuildCostResources() {
        return getSpec().getTotalResourcesNeeded();
    }

    public void applyEffectsOnChangeOfAICore(String currentAiCore) {
    }

    public void applyEffectsOnChangeOfItem(String currentItem) {
    }
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {

    }

    public void createTooltip(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        if (mode != AoTDStableStructureTooltipMode.BUILDING_MENU) {
            tooltip.addTitle(getName());
        }

        String descriptionId = currentlyAssignedToken != null
                ? currentlyAssignedToken.getCustomDescriptionId()
                : getSpecEntity().getCustomDescriptionId();
        if (descriptionId != null) {
            tooltip.addPara(Global.getSettings().getDescription(
                    descriptionId, Description.Type.CUSTOM).getText1FirstPara(), 10f);
        }

        FactionAPI faction = currentlyAssignedToken != null
                ? currentlyAssignedToken.getFaction()
                : Global.getSector().getPlayerFaction();

        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        String creditsStr = Misc.getDGSCredits(credits);
        String costStr = Misc.getDGSCredits(getBuildCostCredits());
        int days = getSpec().getDaysToBuild();
        String daysStr = days == 1 ? "day" : "days";

        LabelAPI label = null;
        if (mode == AoTDStableStructureTooltipMode.UPGRADE_MENU) {
            label = tooltip.addPara("%s and %s " + daysStr + " to upgrade. You have %s.",
                    10f, highlight, costStr, String.valueOf(days), creditsStr);
        } else if (mode == AoTDStableStructureTooltipMode.BUILDING_MENU) {
            label = tooltip.addPara("%s and %s " + daysStr + " to build. You have %s.",
                    10f, highlight, costStr, String.valueOf(days), creditsStr);
        }

        if (label != null) {
            label.setHighlight(costStr, String.valueOf(days), creditsStr);
            label.setHighlightColors(
                    credits >= getBuildCostCredits() ? highlight : bad,
                    highlight,
                    highlight
            );
        }

        int months = Math.max(1, (int) Math.ceil(days / 30f));
        LinkedHashMap<String, Integer> monthlyResources = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> immediateItems = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : getBuildCostResources().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;

            String resourceId = entry.getKey();
            int amount = entry.getValue();

            if (Global.getSettings().getCommoditySpec(resourceId) != null) {
                monthlyResources.put(resourceId, (int) Math.ceil(amount / (double) months));
            } else if (Global.getSettings().getSpecialItemSpec(resourceId) != null) {
                immediateItems.put(resourceId, amount);
            } else {
                throw new IllegalArgumentException("Unknown construction resource: " + resourceId);
            }
        }

        if(mode!=AoTDStableStructureTooltipMode.PANEL){
            if (!monthlyResources.isEmpty()) {
                tooltip.addSectionHeading("Monthly resources for construction", color, dark, Alignment.MID, 10f);
                tooltip.addPara("Construction takes %s " + (days == 1 ? "day" : "days")
                                + ", requiring resources across %s monthly " + (months == 1 ? "installment" : "installments") + ".",
                        5f, highlight, String.valueOf(days), String.valueOf(months));
                CustomPanelAPI monthlyPanel = AoTDToolboxMisc.createCostSection(
                        tooltip.getWidthSoFar(), 0f, monthlyResources, 0f,
                        30f, 9f, 5f, 10f, false, false
                );
                tooltip.addCustom(monthlyPanel, 5f);
                tooltip.addPara(
                        "Construction materials are drawn from colony production capacity each month, through trade contract.",
                        Misc.getGrayColor(), 3f
                );
            }
            if (!immediateItems.isEmpty()) {
                tooltip.addSectionHeading("Other construction costs", color, dark, Alignment.MID, 10f);
                tooltip.addPara(
                        "Unlike regular construction materials, these items are drawn directly from storage, through trade contract.",
                        Misc.getGrayColor(), 5f
                );
                CustomPanelAPI immediatePanel = AoTDToolboxMisc.createCostSection(
                        tooltip.getWidthSoFar(), 0f, immediateItems, 0f,
                        30f, 9f, 5f, 10f, false, true
                );
                tooltip.addCustom(immediatePanel, 5f);
            }
        }
        tooltip.addSectionHeading("Effects after construction", color, dark, Alignment.MID, 10f);
        createTooltipForEffectSection(tooltip,mode);
    }
}
