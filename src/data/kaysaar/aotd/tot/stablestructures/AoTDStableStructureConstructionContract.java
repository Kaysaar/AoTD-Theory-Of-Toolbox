package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import ashlib.data.plugins.ui.models.ProgressBarComponentV2;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;

import java.util.Map;
import java.awt.Color;

public class AoTDStableStructureConstructionContract extends AoTDTradeContract {
    public static float MARGIN_OF_COMMODITY_PRICE = 0.00f;
    protected final AoTDStableStructureImpl site;
    protected final String targetId;

    public AoTDStableStructureConstructionContract(AoTDStableStructureImpl site) {
        super(site.getUniqueIdForContract(), null, Factions.PLAYER, 9999);
        this.site = site;
        this.targetId = site.getConstructionTargetId();
        doesContractHavePenalty = false;
        hasCustomEffectSection = true;
        refreshRequirements();
    }

    @Override
    public boolean isExpired() {
        return !site.isCurrentImplementation() || !site.isBuilding()
                || !getId().equals(site.getUniqueIdForContract())
                || !targetId.equals(site.getConstructionTargetId());
    }

    @Override
    public boolean isContractFrozen() {
        return !site.isPlayerOwned() || site.isDisrupted() || super.isContractFrozen();
    }

    @Override
    public Pair<String, Color> getCurrentContractStatus() {
        if (!site.isPlayerOwned()) return new Pair<>("Paused", Misc.getGrayColor());
        if (site.isDisrupted()) return new Pair<>("Disrupted", Misc.getNegativeHighlightColor());
        return super.getCurrentContractStatus();
    }

    protected void refreshRequirements() {
        contractData.clear();
        AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(targetId);
        int months = Math.max(1, (int) Math.ceil(spec.getDaysToBuild() / 30f));
        for (Map.Entry<String, Integer> entry : site.getConstructionResources().entrySet()) {
            if (Global.getSettings().getCommoditySpec(entry.getKey()) == null) continue;
            int remaining = site.getRemainingResourceAmount(entry.getKey());
            if (remaining <= 0) continue;
            int monthly = (int) Math.ceil(entry.getValue() / (double) months);
            addContractData(entry.getKey(), Math.min(remaining, monthly), MARGIN_OF_COMMODITY_PRICE);
        }
    }

    @Override
    public void executeMonthEndForCommodity(int delivered, String commodityId) {
        if (!isExpired() && !isContractFrozen()) site.addResourcesToConstruction(commodityId, delivered);
    }

    @Override
    public void executeMonthEnd(float percentageOfEntireContractMet) {
        refreshRequirements();
    }

    @Override
    public void decrementMonth() {
    }

    @Override
    public boolean canEditContract() {
        return false;
    }

    @Override
    public boolean canTerminateContract() {
        return false;
    }

    @Override
    public boolean canFreezeContract() {
        return false;
    }

    @Override
    public String getContractTypeId() {
        return "stable_structure_construction";
    }

    @Override
    public String getContractType() {
        return "Stable Structure Construction";
    }

    @Override
    public String getSubTypeOfContractString() {
        return Global.getSettings().getCustomEntitySpec(targetId).getDefaultName();
    }

    @Override
    public void printCustomSection(TooltipMakerAPI tooltip, float width) {
        float contentWidth = width - 15f;
        Color color = Misc.getBasePlayerColor();
        Color dark = Misc.getDarkPlayerColor();
        float progress = site.getBuildOrUpgradeProgress();

        CustomPanelAPI header = Global.getSettings().createCustom(contentWidth, 83f, null);
        String icon = Global.getSettings().getCustomEntitySpec(targetId).getIconName();
        float textX = 5f;
        if (icon != null && !icon.isEmpty()) {
            ImageViewer viewer = new ImageViewer(55f, 55f, icon);
            header.addComponent(viewer.getComponentPanel()).inTL(0f, 2f);
            textX = 65f;
        }

        TooltipMakerAPI text = header.createUIElement(contentWidth - textX, 53f, false);
        text.setParaFont(Fonts.ORBITRON_20AA);
        text.addPara(getSubTypeOfContractString(), color, 0f);
        text.setParaFont(Fonts.DEFAULT_SMALL);
        text.addPara(site.getBuildOrUpgradeProgressText(), 3f);
        header.addUIElement(text).inTL(textX, 0f);

        ProgressBarComponentV2 progressBar = new ProgressBarComponentV2(contentWidth - textX, 16f,
                Math.round(progress * 100f) + "%", Fonts.VICTOR_10, color, dark, progress);
        header.addComponent(progressBar.getMainPanel()).inTL(textX, 59f);
        tooltip.addCustom(header, 5f);
        tooltip.addSectionHeading("Supply & logistics", Alignment.MID, 8f);
        tooltip.addPara("Construction materials are supplied from %s each month.", 3f,
                Color.ORANGE, "faction production capacity");

        MarketAPI storage = site.getConstructionStorage();
        if (storage != null) {
            tooltip.addPara("Special items are withdrawn directly from storage at %s.", 3f,
                    Color.ORANGE, storage.getName());
        } else {
            tooltip.addPara("Special items are withdrawn directly from the selected construction storage.", 3f);
        }

        if (site.isDisrupted()) {
            tooltip.addPara("Construction and deliveries are currently suspended due to disruption.",
                    Misc.getNegativeHighlightColor(), 6f);
        } else {
            tooltip.addPara("*Construction and deliveries are paused while the structure is disrupted.*",
                    Misc.getGrayColor(), 6f);
        }
    }
}
