package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;

import java.util.Map;
import java.awt.Color;

public class AoTDStableStructureConstructionContract extends AoTDTradeContract {
    public static float MARGIN_OF_COMMODITY_PRICE = 0.10f;
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
        for (Map.Entry<String, Integer> entry : site.getMonthlyResNeeded().entrySet()) {
            addContractData(entry.getKey(), entry.getValue(), MARGIN_OF_COMMODITY_PRICE);
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
        tooltip.addPara("Construction materials are drawn from colony production capacity each month. "
                + "Special items are drawn directly from the selected construction storage. "
                + "Construction and deliveries pause while the structure is disrupted.", 3f);
        MarketAPI storage = site.getConstructionStorage();
        if (storage != null) tooltip.addPara("Construction storage: " + storage.getName(), 3f);
        tooltip.addPara(site.getBuildOrUpgradeProgressText(), 3f);
    }
}
