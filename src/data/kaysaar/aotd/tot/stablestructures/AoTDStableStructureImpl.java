package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.ChooseStablePointForBuildingMap;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.map.StructureLocationPicker;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;

public class AoTDStableStructureImpl {
    public String id;
    public String currentAICoreID;
    public String currentItemID;
    public SectorEntityToken currentlyAssignedToken;
    public String upgradeId;
    public String ultimateUpgradeId;
    public boolean isBuilding;
    public boolean isDisrupted;
    protected float buildProgress;
    protected float buildTime = 1f;
    protected float creditsPaid;
    protected boolean constructionCancelled;
    protected String constructionTargetId;
    protected String uniqueIdForContract;
    protected MarketAPI constructionStorage;
    protected LinkedHashMap<String, Integer> constructionResources = new LinkedHashMap<>();
    protected LinkedHashMap<String, Integer> delivered = new LinkedHashMap<>();
    protected float maintenanceDays;
    protected Long lastAdvanceTimestamp;

    public SectorEntityToken getCurrentlyTiedEntity() {
        return currentlyAssignedToken;
    }

    protected Object readResolve() {
        if (constructionResources == null) constructionResources = new LinkedHashMap<>();
        if (delivered == null) delivered = new LinkedHashMap<>();
        return this;
    }

    public void setCurrentlyAssignedToken(SectorEntityToken currentlyAssignedToken) {
        if (currentlyAssignedToken == null) throw new IllegalArgumentException("A structure needs an entity");
        Object existing = currentlyAssignedToken.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
        if (existing != null && existing != this) {
            throw new IllegalStateException("The entity already has a stable structure implementation");
        }
        if (this.currentlyAssignedToken != null && this.currentlyAssignedToken != currentlyAssignedToken) {
            throw new IllegalStateException("A stable structure cannot be moved to another entity");
        }
        this.currentlyAssignedToken = currentlyAssignedToken;
        currentlyAssignedToken.getMemoryWithoutUpdate().set(AoTDStableStructureSpecManager.memKeyInEntities, this);
        if (lastAdvanceTimestamp == null) resetAdvanceTimestamp();
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

    public StructureLocationPicker createLocationPicker(float width, float height, StarSystemAPI systemAPI) {
        return new ChooseStablePointForBuildingMap(width,height,systemAPI,id);
    }

    public void advance(float amount) {
        if (!isCurrentImplementation() || amount <= 0f || Global.getSector().isPaused()) return;
        boolean disrupted = isDisrupted();
        if (isDisrupted != disrupted) {
            isDisrupted = disrupted;
            if (disrupted) notifyDisrupted();
            else disruptionFinished();
            AoTDTradeContractManager.getInstance().invalidatePredictions();
        }
        if (!isPlayerOwned() || disrupted || (!isBuilding && ultimateUpgradeId == null)) return;
        float days = Global.getSector().getClock().convertToDays(amount);
        boolean alreadyBuilding = isBuilding;
        float allowed = getAllowedProgressOnConstruction();
        maintenanceDays -= days;
        if (maintenanceDays <= 0f) {
            maintenanceDays = 1f;
            if (isBuilding) deliverSpecialItemsFromStorage();
            else if (ultimateUpgradeId != null) startNextUpgrade();
        }
        if (!alreadyBuilding || !isBuilding) return;
        buildProgress = Math.min(buildProgress + days, Math.max(buildProgress, buildTime * allowed));
        if (buildProgress >= buildTime && allowed >= 1f) finishBuildingOrUpgrading();
    }

    public boolean isCurrentImplementation() {
        return currentlyAssignedToken != null && !currentlyAssignedToken.isExpired()
                && currentlyAssignedToken.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities) == this;
    }

    public boolean isPlayerOwned() {
        return currentlyAssignedToken != null && currentlyAssignedToken.getFaction().isPlayerFaction();
    }

    protected void requireCurrentImplementation() {
        if (!isCurrentImplementation()) throw new IllegalStateException("Use the implementation stored on the entity");
    }

    public boolean isBuilding() {
        return isBuilding;
    }

    public boolean isUpgrading() {
        return isBuilding && constructionTargetId != null && !id.equals(constructionTargetId);
    }

    public boolean isFunctional() {
        return isCurrentImplementation() && !constructionCancelled && !isDisrupted()
                && (!isBuilding || isUpgrading());
    }

    public float getBuildTime() {
        return Math.max(0f, getSpec().getDaysToBuild());
    }

    public float getBuildOrUpgradeProgress() {
        if (!isBuilding) return 0f;
        return buildTime <= 0f ? getAllowedProgressOnConstruction() : Math.min(1f, buildProgress / buildTime);
    }

    public String getBuildOrUpgradeDaysText() {
        int days = Math.max(1, (int) Math.ceil(isDisrupted() ? getDisruptedDays() : buildTime - buildProgress));
        return days + (days == 1 ? " day" : " days");
    }

    public String getBuildOrUpgradeProgressText() {
        if (isDisrupted()) return "Disrupted: " + getBuildOrUpgradeDaysText() + " left";
        //        if (getAllowedProgressOnConstruction() < 1f && buildProgress >= buildTime * getAllowedProgressOnConstruction()) {
//            text += " (awaiting resources)";
//        }
        return (isUpgrading() ? "Upgrading: " : "Building: ") + getBuildOrUpgradeDaysText() + " left";
    }

    public boolean startBuilding() {
        return startBuilding(ultimateUpgradeId);
    }

    public boolean startBuilding(String ultimateTargetId) {
        requireCurrentImplementation();
        if (isBuilding) return false;
        String target = ultimateTargetId == null ? id : ultimateTargetId;
        AoTDStableStructureSpecManager.getUpgradePath(id, target);
        if (!startStage(this)) return false;
        ultimateUpgradeId = target.equals(id) ? null : target;
        updateNextUpgradeId();
        return true;
    }

    public boolean startUpgrading() {
        String target = ultimateUpgradeId != null ? ultimateUpgradeId : upgradeId;
        if (target == null) {
            LinkedList<AoTDStableStructureSpec> children = AoTDStableStructureSpecManager.getUpgradeChildren(id);
            if (children.size() != 1) throw new IllegalStateException("Specify an upgrade target for " + id);
            target = children.getFirst().getId();
        }
        return startUpgrading(target);
    }

    public boolean startUpgrading(String ultimateTargetId) {
        requireCurrentImplementation();
        if (ultimateTargetId == null) throw new IllegalArgumentException("An upgrade needs a target");
        String from = isUpgrading() ? constructionTargetId : id;
        AoTDStableStructureSpecManager.getUpgradePath(from, ultimateTargetId);
        if (lastAdvanceTimestamp == null) resetAdvanceTimestamp();
        ultimateUpgradeId = ultimateTargetId.equals(id) ? null : ultimateTargetId;
        updateNextUpgradeId();
        maintenanceDays = 0f;
        return !isBuilding && startNextUpgrade();
    }

    public void clearQueuedUpgrades() {
        requireCurrentImplementation();
        ultimateUpgradeId = isUpgrading() ? constructionTargetId : null;
        updateNextUpgradeId();
    }

    protected void updateNextUpgradeId() {
        if (isUpgrading()) {
            upgradeId = constructionTargetId;
        } else if (ultimateUpgradeId != null && !ultimateUpgradeId.equals(id)) {
            upgradeId = AoTDStableStructureSpecManager.getUpgradePath(id, ultimateUpgradeId).getFirst();
        } else {
            ultimateUpgradeId = null;
            upgradeId = null;
        }
    }

    protected boolean startNextUpgrade() {
        if (isBuilding || constructionCancelled || ultimateUpgradeId == null || isDisrupted() || !isPlayerOwned()) return false;
        updateNextUpgradeId();
        if (upgradeId == null) return false;
        AoTDStableStructureImpl next = AoTDStableStructureSpecManager.getSpec(upgradeId).getImplClassInstance();
        next.currentAICoreID = currentAICoreID;
        next.currentItemID = currentItemID;
        return startStage(next);
    }

    protected boolean startStage(AoTDStableStructureImpl target) {
        if (!isPlayerOwned() || isDisrupted()
                || !target.isAvailableToBuild(currentlyAssignedToken.getStarSystem(), currentlyAssignedToken.getFaction())) return false;
        float cost = Math.max(0f, target.getBuildCostCredits());
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) return false;
        LinkedHashMap<String, Integer> resources = new LinkedHashMap<>();
        if (target.getBuildCostResources() != null) {
            for (Map.Entry<String, Integer> entry : target.getBuildCostResources().entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) continue;
                if (Global.getSettings().getCommoditySpec(entry.getKey()) == null
                        && Global.getSettings().getSpecialItemSpec(entry.getKey()) == null) {
                    throw new IllegalArgumentException("Unknown construction resource: " + entry.getKey());
                }
                resources.put(entry.getKey(), entry.getValue());
            }
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        if (lastAdvanceTimestamp == null) resetAdvanceTimestamp();
        creditsPaid = cost;
        constructionResources = resources;
        delivered.clear();
        constructionTargetId = target.id;
        buildTime = target.getBuildTime();
        buildProgress = 0f;
        constructionCancelled = false;
        isBuilding = true;
        uniqueIdForContract = "stable_structure_construction_" + Misc.genUID();
        resetAdvanceTimestamp();
        if (!getMonthlyResNeeded().isEmpty()) {
            AoTDTradeContractManager.getInstance().addContract(new AoTDStableStructureConstructionContract(this));
        }
        deliverSpecialItemsFromStorage();
        return true;
    }

    public void finishBuildingOrUpgrading() {
        requireCurrentImplementation();
        if (!isBuilding) return;
        boolean upgrading = isUpgrading();
        AoTDStableStructureImpl next = upgrading
                ? AoTDStableStructureSpecManager.getSpec(constructionTargetId).getImplClassInstance() : null;
        removeConstructionContract();
        isBuilding = false;
        creditsPaid = 0f;
        buildProgress = 0f;
        buildTime = 1f;
        constructionTargetId = null;
        delivered.clear();
        constructionResources.clear();
        if (upgrading) {
            unapply();
            next.currentAICoreID = currentAICoreID;
            next.currentItemID = currentItemID;
            next.constructionStorage = constructionStorage;
            next.ultimateUpgradeId = ultimateUpgradeId;
            next.isDisrupted = isDisrupted();
            currentlyAssignedToken.getMemoryWithoutUpdate().set(AoTDStableStructureSpecManager.memKeyInEntities, next);
            next.setCurrentlyAssignedToken(currentlyAssignedToken);
            next.updateEntityPresentation(this);
            next.upgradeFinished(this);
            ultimateUpgradeId = null;
            upgradeId = null;
            next.updateNextUpgradeId();
            next.startNextUpgrade();
        } else {
            buildingFinished();
            updateNextUpgradeId();
            startNextUpgrade();
        }
    }

    protected void updateEntityPresentation(AoTDStableStructureImpl previous) {
        CustomEntitySpecAPI spec = getSpecEntity();
        if (currentlyAssignedToken.getName() == null
                || currentlyAssignedToken.getName().equals(previous.getSpecEntity().getDefaultName())) {
            currentlyAssignedToken.setName(spec.getDefaultName());
        }
        currentlyAssignedToken.setCustomDescriptionId(spec.getCustomDescriptionId());
        if (spec.getInteractionImage() != null) {
            currentlyAssignedToken.setCustomInteractionDialogImageVisual(
                    new InteractionDialogImageVisual(spec.getInteractionImage(), 480f, 300f));
        }
    }

    public void cancelUpgrade() {
        requireCurrentImplementation();
        if (isUpgrading()) cancelCurrentStage(false);
        else clearQueuedUpgrades();
    }

    public void cancelBuilding() {
        requireCurrentImplementation();
        if (isBuilding && !isUpgrading()) cancelCurrentStage(true);
    }

    protected void cancelCurrentStage(boolean cancelledBase) {
        float refund = creditsPaid * (1f - getBuildOrUpgradeProgress());
        removeConstructionContract();
        isBuilding = false;
        constructionCancelled = cancelledBase;
        ultimateUpgradeId = null;
        upgradeId = null;
        constructionTargetId = null;
        buildProgress = 0f;
        buildTime = 1f;
        creditsPaid = 0f;
        constructionResources.clear();
        delivered.clear();
        if (refund > 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
    }

    public String getUniqueIdForContract() {
        return uniqueIdForContract;
    }

    public String getConstructionTargetId() {
        return constructionTargetId;
    }

    public AoTDStableStructureConstructionContract getContract() {
        if (uniqueIdForContract == null) return null;
        Object contract = AoTDTradeContractManager.getInstance().getActiveContracts().get(uniqueIdForContract);
        return contract instanceof AoTDStableStructureConstructionContract
                ? (AoTDStableStructureConstructionContract) contract : null;
    }

    public void deliverSpecialItemsFromStorage() {
        if (!isBuilding || !isCurrentImplementation() || !isPlayerOwned() || isDisrupted()) return;
        boolean needsItems = false;
        for (String resource : constructionResources.keySet()) {
            if (getRemainingResourceAmount(resource) > 0
                    && Global.getSettings().getCommoditySpec(resource) == null) {
                needsItems = true;
                break;
            }
        }
        if (!needsItems) return;
        MarketAPI storage = getConstructionStorage();
        if (storage == null || !storage.getFaction().isPlayerFaction()
                || !storage.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) return;
        CargoAPI cargo = storage.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (!stack.isSpecialStack()) continue;
            String resource = stack.getSpecialDataIfSpecial().getId();
            if (Global.getSettings().getCommoditySpec(resource) != null) continue;
            int amount = Math.min((int) stack.getSize(), getRemainingResourceAmount(resource));
            if (amount > 0 && cargo.removeItems(stack.getType(), stack.getData(), amount)) {
                addResourcesToConstruction(resource, amount);
            }
        }
    }

    protected void removeConstructionContract() {
        if (uniqueIdForContract != null) AoTDTradeContractManager.getInstance().removeContract(uniqueIdForContract);
        uniqueIdForContract = null;
    }

    public void addResourcesToConstruction(String resourceId, int amount) {
        if (!isBuilding || !isCurrentImplementation() || amount <= 0) return;
        int needed = getRemainingResourceAmount(resourceId);
        if (needed > 0) delivered.put(resourceId, delivered.getOrDefault(resourceId, 0) + Math.min(needed, amount));
    }

    public int getRemainingResourceAmount(String resourceId) {
        return Math.max(0, constructionResources.getOrDefault(resourceId, 0) - delivered.getOrDefault(resourceId, 0));
    }

    public LinkedHashMap<String, Integer> getConstructionResources() {
        return new LinkedHashMap<>(constructionResources);
    }

    public LinkedHashMap<String, Integer> getDeliveredResources() {
        return new LinkedHashMap<>(delivered);
    }

    public float getAllowedProgressOnConstruction() {
        float allowed = 1f;
        for (Map.Entry<String, Integer> entry : constructionResources.entrySet()) {
            if (entry.getValue() <= 0) continue;
            allowed = Math.min(allowed, delivered.getOrDefault(entry.getKey(), 0) / (float) entry.getValue());
        }
        return Math.max(0f, Math.min(1f, allowed));
    }

    public LinkedHashMap<String, Integer> getMonthlyResNeeded() {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        int months = Math.max(1, (int) Math.ceil(Math.max(0f, buildTime - buildProgress) / 30f));
        for (String resource : constructionResources.keySet()) {
            if (Global.getSettings().getCommoditySpec(resource) == null) continue;
            int remaining = getRemainingResourceAmount(resource);
            if (remaining > 0) result.put(resource, (int) Math.ceil(remaining / (double) months));
        }
        return result;
    }

    public MarketAPI getConstructionStorage() {
        if (constructionStorage != null) return constructionStorage;
        FactionProductionAPI production = Global.getSector().getPlayerFaction().getProduction();
        return production == null ? null : production.getGatheringPoint();
    }

    public void setConstructionStorage(MarketAPI market) {
        constructionStorage = market;
    }

    public String getDisruptedKey() {
        return "$aotd_stable_structure_disrupted";
    }

    public boolean canBeDisrupted() {
        return true;
    }

    public boolean isDisrupted() {
        return currentlyAssignedToken != null && currentlyAssignedToken.getMemoryWithoutUpdate().getBoolean(getDisruptedKey());
    }

    public float getDisruptedDays() {
        return isDisrupted() ? Math.max(0f, currentlyAssignedToken.getMemoryWithoutUpdate().getExpire(getDisruptedKey())) : 0f;
    }

    public void setDisrupted(float days) {
        setDisrupted(days, false);
    }

    public void setDisrupted(float days, boolean useMax) {
        requireCurrentImplementation();
        if (!canBeDisrupted() && days > 0f) return;
        if (!Float.isFinite(days)) throw new IllegalArgumentException("Invalid disruption duration");
        if (lastAdvanceTimestamp == null) resetAdvanceTimestamp();
        if (useMax) days = Math.max(days, getDisruptedDays());
        MemoryAPI memory = currentlyAssignedToken.getMemoryWithoutUpdate();
        boolean wasDisrupted = isDisrupted();
        if (days <= 0f) memory.unset(getDisruptedKey());
        else memory.set(getDisruptedKey(), true, days);
        isDisrupted = isDisrupted();
        if (wasDisrupted != isDisrupted) {
            if (isDisrupted) notifyDisrupted();
            else disruptionFinished();
        }
        AoTDTradeContractManager.getInstance().invalidatePredictions();
    }

    public void notifyRemoved() {
        removeConstructionContract();
        isBuilding = false;
        constructionCancelled = true;
        ultimateUpgradeId = null;
        upgradeId = null;
        unapply();
        if (currentlyAssignedToken != null
                && currentlyAssignedToken.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities) == this) {
            currentlyAssignedToken.getMemoryWithoutUpdate().unset(AoTDStableStructureSpecManager.memKeyInEntities);
        }
    }

    public void apply() {
    }

    public void unapply() {
    }

    protected void resetAdvanceTimestamp() {
        lastAdvanceTimestamp = Global.getSector().getClock().getTimestamp();
    }

    public int getEffectOrder() {
        return 0;
    }

    public String getModId() {
        return "aotd_stable_structure_" + (currentlyAssignedToken == null ? id : currentlyAssignedToken.getId());
    }

    protected void buildingFinished() {
    }

    protected void upgradeFinished(AoTDStableStructureImpl previous) {
    }

    protected void notifyDisrupted() {
    }

    protected void disruptionFinished() {
    }

    public AoTDStableStructureSpec getSpec() {
        return AoTDStableStructureSpecManager.getSpec(id);
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

    /** Total credits for base construction and all upgrades leading to this tier. */
    public long getCumulativeCreditCost() {
        long total = 0L;
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        AoTDStableStructureSpec current = getSpec();

        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Circular stable structure upgrade path: " + current.getId());
            }
            total += Math.max(0, current.getCreditCost());
            String downgrade = current.getDowngrade();
            if (downgrade == null) return total;
            current = AoTDStableStructureSpecManager.getSpec(downgrade);
            if (current == null) {
                throw new IllegalStateException("Unknown stable structure downgrade: " + downgrade);
            }
        }
        return total;
    }

    /** Total build time for base construction and all upgrades leading to this tier. */
    public int getCumulativeBuildDays() {
        int total = 0;
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        AoTDStableStructureSpec current = getSpec();

        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Circular stable structure upgrade path: " + current.getId());
            }
            total += Math.max(0, current.getDaysToBuild());
            String downgrade = current.getDowngrade();
            if (downgrade == null) return total;
            current = AoTDStableStructureSpecManager.getSpec(downgrade);
            if (current == null) {
                throw new IllegalStateException("Unknown stable structure downgrade: " + downgrade);
            }
        }
        return total;
    }

    public LinkedHashMap<String, Integer> getBuildCostResources() {
        return getSpec().getTotalResourcesNeeded();
    }

    /** Returns the complete construction chain, starting with the base structure. */
    protected LinkedList<AoTDStableStructureSpec> getConstructionStagesFromBase() {
        LinkedList<AoTDStableStructureSpec> result = new LinkedList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        AoTDStableStructureSpec current = getSpec();

        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Circular stable structure upgrade path: " + current.getId());
            }
            result.addFirst(current);

            String downgrade = current.getDowngrade();
            if (downgrade == null) break;
            current = AoTDStableStructureSpecManager.getSpec(downgrade);
            if (current == null) {
                throw new IllegalStateException("Unknown stable structure downgrade: " + downgrade);
            }
        }
        return result;
    }

    protected void addBaseConstructionCostsToTooltip(TooltipMakerAPI tooltip, Color color, Color dark) {
        Color highlight = Misc.getHighlightColor();
        int days = getSpec().getDaysToBuild();
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
                    "Construction materials are drawn from your faction's production capacity each month through trade contracts.",
                    Misc.getGrayColor(), 3f
            );
        }
        if (!immediateItems.isEmpty()) {
            tooltip.addSectionHeading("Other construction costs", color, dark, Alignment.MID, 10f);
            tooltip.addPara(
                    "Unlike regular construction materials, these items are drawn directly from construction storage, through trade contract.",
                    Misc.getGrayColor(), 5f
            );
            CustomPanelAPI immediatePanel = AoTDToolboxMisc.createCostSection(
                    tooltip.getWidthSoFar(), 0f, immediateItems, 0f,
                    30f, 9f, 5f, 10f, false, true
            );
            tooltip.addCustom(immediatePanel, 5f);
        }
    }

    protected void addConstructionStagesToTooltip(TooltipMakerAPI tooltip, Color color, Color dark) {
        Color highlight = Misc.getHighlightColor();
        tooltip.addSectionHeading("Construction materials by stage", color, dark, Alignment.MID, 10f);
        tooltip.addPara(
                "Construction materials are drawn from your faction's production capacity each month through trade contracts. ",
                Misc.getGrayColor(), 5f
        );

        for (AoTDStableStructureSpec stage : getConstructionStagesFromBase()) {
            AoTDStableStructureImpl stageImpl = stage.getId().equals(id) ? this : stage.getImplClassInstance();
            int days = Math.max(0, stage.getDaysToBuild());
            int months = Math.max(1, (int) Math.ceil(days / 30f));
            String installments = months == 1 ? "installment" : "installments";

            tooltip.addPara("%s: %s days, %s monthly " + installments + ".", 8f, highlight,
                    stageImpl.getName(), String.valueOf(days), String.valueOf(months));
            tooltip.addPara("Credits charged at the start of this stage: %s.", 3f, highlight,
                    Misc.getDGSCredits(stageImpl.getBuildCostCredits()));

            LinkedHashMap<String, Integer> monthlyResources = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> immediateItems = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> resources = stageImpl.getBuildCostResources();
            if (resources != null) {
                for (Map.Entry<String, Integer> entry : resources.entrySet()) {
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
            }

            if (!monthlyResources.isEmpty()) {
                tooltip.addPara("Materials per monthly installment:", Misc.getGrayColor(), 3f);
                CustomPanelAPI monthlyPanel = AoTDToolboxMisc.createCostSection(
                        tooltip.getWidthSoFar(), 0f, monthlyResources, 0f,
                        30f, 9f, 5f, 10f, false, false
                );
                tooltip.addCustom(monthlyPanel, 5f);
            }

            if (!immediateItems.isEmpty()) {
                tooltip.addPara("One-time items for this stage:", Misc.getGrayColor(), 3f);
                CustomPanelAPI immediatePanel = AoTDToolboxMisc.createCostSection(
                        tooltip.getWidthSoFar(), 0f, immediateItems, 0f,
                        30f, 9f, 5f, 10f, false, true
                );
                tooltip.addCustom(immediatePanel, 5f);
            }

            if (monthlyResources.isEmpty() && immediateItems.isEmpty()) {
                tooltip.addPara("No construction materials required for this stage.", Misc.getGrayColor(), 3f);
            }
        }

        tooltip.addPara(
                "Unlike monthly construction materials, special items are drawn directly from construction storage " +
                        "when their respective stage begins.",
                Misc.getGrayColor(), 5f
        );
    }


    public void applyEffectsOnChangeOfAICore(String currentAiCore) {
    }

    public void applyEffectsOnChangeOfItem(String currentItem) {
    }

    public boolean canInstallAICore(String coreId) {
        return canMountAICores() && coreId != null
                && Global.getSettings().getCommoditySpec(coreId).hasTag(Commodities.TAG_AI_CORE);
    }

    public void addAICoreSection(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addAICoreSection(tooltip, getAICoreId(), mode);
    }

    public void addAICoreSection(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        if (coreId == null) {
            if (mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
                tooltip.addPara("No AI core currently assigned. Click to assign an AI core from your cargo.", 0f);
            }
            return;
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            tooltip.addSectionHeading("Items",currentlyAssignedToken.getFaction().getBaseUIColor(),currentlyAssignedToken.getFaction().getDarkUIColor(),Alignment.MID,5f);
            TooltipMakerAPI text = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(coreId).getIconName(), 48f);
            addAICoreDescription(text, coreId, mode);
            tooltip.addImageWithText(10f);
        } else {
            addAICoreDescription(tooltip, coreId, mode);
        }
    }

    public void addAICoreDescription(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        if (coreId == null) return;

        switch (coreId) {
            case Commodities.GAMMA_CORE:
                addGammaCoreDescription(tooltip, mode);
                break;
            case Commodities.BETA_CORE:
                addBetaCoreDescription(tooltip, mode);
                break;
            case Commodities.ALPHA_CORE:
                addAlphaCoreDescription(tooltip, mode);
                break;
            default:
                addUnknownCoreDescription(tooltip, coreId, mode);
                break;
        }
    }

    public String getAICoreDescriptionPrefix(String name, AICoreDescriptionMode mode) {
        boolean installed = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_INSTALLED
                || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP;
        return name + (installed ? " currently assigned. " : ". ");
    }

    public void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Gamma-level AI core", mode)
                        + "Reduces upkeep cost by %s.",
                0f, Misc.getHighlightColor(), "25%"
        );
    }

    public void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Beta-level AI core", mode)
                        + "Reduces upkeep cost by %s. Reduces demand by %s unit.",
                0f, Misc.getHighlightColor(), "25%", "1"
        );
    }

    public void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Alpha-level AI core", mode)
                        + "Reduces upkeep cost by %s. Reduces demand by %s unit. "
                        + "Increases production by %s unit.",
                0f, Misc.getHighlightColor(), "25%", "1", "1"
        );
    }

    public void addUnknownCoreDescription(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        String name = Global.getSettings().getCommoditySpec(coreId).getName();
        tooltip.addPara(getAICoreDescriptionPrefix(name, mode).trim(), 0f);
    }

    public void setAICoreId(String coreId) {
        currentAICoreID = coreId;
        applyEffectsOnChangeOfAICore(coreId);
    }

    public String getAICoreId() {
        return currentAICoreID;
    }

    public void setItemId(String itemId) {
        currentItemID = itemId;
        applyEffectsOnChangeOfItem(itemId);
    }

    public String getItemId() {
        return currentItemID;
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

        if (mode == AoTDStableStructureTooltipMode.PANEL) {
            if (isBuilding || isDisrupted() || ultimateUpgradeId != null) {
                tooltip.addSectionHeading("Status", color, dark, Alignment.MID, 10f);
                tooltip.addPara(getBuildOrUpgradeProgressText(), isDisrupted() ? bad : highlight, 5f);
            }
            if (isBuilding) {
                tooltip.addPara("Under construction: %s", 5f, highlight,
                        Global.getSettings().getCustomEntitySpec(constructionTargetId).getDefaultName());
                if (!isPlayerOwned()) tooltip.addPara("Construction is paused while the structure is not player-owned.", bad, 5f);
                if (!constructionResources.isEmpty()) {
                    tooltip.addSectionHeading("Construction resources delivered", color, dark, Alignment.MID, 10f);
                    for (Map.Entry<String, Integer> entry : constructionResources.entrySet()) {
                        String resource = entry.getKey();
                        String name = Global.getSettings().getCommoditySpec(resource) != null
                                ? Global.getSettings().getCommoditySpec(resource).getName()
                                : Global.getSettings().getSpecialItemSpec(resource).getName();
                        int received = delivered.getOrDefault(resource, 0);
                        tooltip.addPara(name + ": %s / %s", 3f,
                                received < entry.getValue() ? bad : highlight,
                                String.valueOf(received), String.valueOf(entry.getValue()));
                    }
                }
            }
            if (ultimateUpgradeId != null && !ultimateUpgradeId.equals(constructionTargetId)) {
                tooltip.addPara("Queued upgrade target: %s", 5f, highlight,
                        Global.getSettings().getCustomEntitySpec(ultimateUpgradeId).getDefaultName());
            }
            if (constructionCancelled) tooltip.addPara("Construction cancelled. This structure is not functional.", bad, 5f);
        }

        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        String creditsStr = Misc.getDGSCredits(credits);

        // Base structures show their regular construction costs. Upgrades show the
        // cumulative cost and time for all stages from the base structure.
        boolean isUpgrade = getSpec().getDowngrade() != null;
        boolean cumulativePreview = isUpgrade && (mode == AoTDStableStructureTooltipMode.UPGRADE_MENU
                || mode == AoTDStableStructureTooltipMode.BUILDING_MENU);
        long displayCost = cumulativePreview ? getCumulativeCreditCost() : (long) getBuildCostCredits();
        int displayDays = cumulativePreview ? getCumulativeBuildDays() : getSpec().getDaysToBuild();
        String costStr = Misc.getDGSCredits(displayCost);
        String daysStr = displayDays == 1 ? "day" : "days";

        LabelAPI label = null;
        if (mode == AoTDStableStructureTooltipMode.UPGRADE_MENU
                || mode == AoTDStableStructureTooltipMode.BUILDING_MENU) {
            label = tooltip.addPara("%s and %s " + daysStr + (isUpgrade ? " to upgrade. You have %s." : " to build. You have %s."),
                    10f, highlight, costStr, String.valueOf(displayDays), creditsStr);
        }

        if (label != null) {
            label.setHighlight(costStr, String.valueOf(displayDays), creditsStr);
            label.setHighlightColors(
                    credits >= displayCost ? highlight : bad,
                    highlight,
                    highlight
            );
        }

        if (mode != AoTDStableStructureTooltipMode.PANEL) {
            if (isUpgrade) {
                addConstructionStagesToTooltip(tooltip, color, dark);
            } else {
                addBaseConstructionCostsToTooltip(tooltip, color, dark);
            }
        }
        tooltip.addSectionHeading("Effects after construction", color, dark, Alignment.MID, 10f);
        createTooltipForEffectSection(tooltip,mode);
        if (getAICoreId() != null) {
            addAICoreSection(tooltip, AICoreDescriptionMode.INDUSTRY_TOOLTIP);
        }
    }
}
