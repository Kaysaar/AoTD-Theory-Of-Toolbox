package data.kaysaar.aotd.tot.stablestructures;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.resizable.map.CustomEntityIconComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Objectives;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.entities.AoTDStableStructureScaffold;
import data.kaysaar.aotd.tot.ids.AoTDTags;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Objects;

public class AoTDStableStructureConstruction {
    public static final String SCAFFOLD = "aotd_scaffold";
    public static final String OVERLAY = "aotd_scaffold_overlay";
    public static final String ORIGINAL_LOCATION = "$originalStableLocation";
    public static final String USES_STABLE_LOCATION = "$aotd_uses_stable_location";
    public static final String WIDGET_ORDER = "$aotd_structure_widget_order";
    public static final float DOWNGRADE_REFUND_MULT = 0.4f;
    public static final float SHUTDOWN_REFUND_MULT = 0.4f;

    public static AoTDStableStructureImpl getBaseStructure(AoTDStableStructureImpl selected) {
        return selected.getConstructionStagesFromBase().getFirst().getImplClassInstance();
    }

    public static boolean isStablePoint(SectorEntityToken token) {
        return token != null && Entities.STABLE_LOCATION.equals(token.getCustomEntityType());
    }

    public static boolean canStart(AoTDStableStructureImpl selected, AoTDStableStructureImpl base,
                                   StarSystemAPI system, SectorEntityToken focus, Vector2f position) {
        if (system == null || position == null || !Float.isFinite(position.x) || !Float.isFinite(position.y)) return false;
        FactionAPI faction = AshMisc.getClaimingFaction(system.getCenter());
        if (faction == null || !faction.isPlayerFaction()) return false;
        if (!AoTDToolboxMisc.hasAvailableSystemStructureSlot(system)) return false;
        if (focus != null && (focus.isExpired() || focus.getContainingLocation() != system
                || !system.getAllEntities().contains(focus))) return false;
        if (isStablePoint(focus) && focus.getMemoryWithoutUpdate().contains(AoTDStableStructureSpecManager.memKeyInEntities)) return false;
        if (focus != null && !isStablePoint(focus) && Vector2f.sub(position, focus.getLocation(), null).length() <= focus.getRadius()) return false;
        return selected.isAvailableToBuild(system, faction) && base.isAvailableToBuild(system, faction)
                && Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= Math.max(0f, base.getBuildCostCredits());
    }

    public static AoTDStableStructureImpl start(AoTDStableStructureImpl selected, StarSystemAPI system,
                                                SectorEntityToken focus, Vector2f position) {
        AoTDStableStructureImpl base = getBaseStructure(selected);
        if (!canStart(selected, base, system, focus, position)) return null;
        CustomCampaignEntityAPI scaffold = system.addCustomEntity(Misc.genUID(),
                base.getSpecEntity().getDefaultName() + " (Under construction)", SCAFFOLD,
                Global.getSector().getPlayerFaction().getId());
        scaffold.getMemoryWithoutUpdate().set(USES_STABLE_LOCATION, isStablePoint(focus));
        if (isStablePoint(focus)) {
            copyPosition(focus, scaffold);
            scaffold.getMemoryWithoutUpdate().set(ORIGINAL_LOCATION, focus);
        } else if (focus != null) {
            float radius = Vector2f.sub(position, focus.getLocation(), null).length();
            scaffold.setCircularOrbitPointingDown(focus, Misc.getAngleInDegrees(focus.getLocation(), position), radius, 120f);
        } else {
            scaffold.setLocation(position.x, position.y);
            scaffold.setFacing(90f);
        }
        scaffold.addTag(AoTDTags.AOTD_OBJECTIVE);
        scaffold.addTag(CustomEntityIconComponent.SHOW_ONLY_ICON_ON_MAP);
        base.setCurrentlyAssignedToken(scaffold);
        ((AoTDStableStructureScaffold) scaffold.getCustomPlugin()).setImplToConstruct(base);
        setPresentation(scaffold, base);
        if (!base.startBuilding(selected.id)) {
            scaffold.getMemoryWithoutUpdate().unset(AoTDStableStructureSpecManager.memKeyInEntities);
            system.removeEntity(scaffold);
            scaffold.setExpired(true);
            return null;
        }
        if (isStablePoint(focus)) {
            system.removeEntity(focus);
            new Objectives().updateOrbitingEntities(system, focus, scaffold);
        }
        return base;
    }

    public static void setPresentation(CustomCampaignEntityAPI scaffold, AoTDStableStructureImpl impl) {
        CustomEntitySpecAPI spec = impl.getSpecEntity();
        scaffold.setRadius(Math.max(spec.getDefaultRadius(),
                (float) Math.hypot(spec.getSpriteWidth(), spec.getSpriteHeight()) * 0.6f));
        if (spec.getInteractionImage() != null) {
            scaffold.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual(spec.getInteractionImage(), 480f, 300f));
        }
    }

    public static SectorEntityToken showUpgradeScaffold(AoTDStableStructureImpl impl) {
        SectorEntityToken current = impl.currentlyAssignedToken;
        CustomCampaignEntityAPI overlay = current.getContainingLocation().addCustomEntity(Misc.genUID(), null,
                OVERLAY, current.getFaction().getId());
        overlay.clearTags();
        overlay.addTag(CustomEntityIconComponent.SHOW_ONLY_ICON_ON_MAP);
        copyPosition(current, overlay);
        setPresentation(overlay, impl);
        ((AoTDStableStructureScaffold) overlay.getCustomPlugin()).setImplToConstruct(impl);
        return overlay;
    }

    public static void removeOverlay(SectorEntityToken overlay) {
        if (overlay == null) return;
        if (overlay.getContainingLocation() != null) overlay.getContainingLocation().removeEntity(overlay);
        overlay.setExpired(true);
    }

    public static SectorEntityToken createFinishedEntity(AoTDStableStructureImpl previous, AoTDStableStructureImpl next) {
        SectorEntityToken current = previous.currentlyAssignedToken;
        LocationAPI location = current.getContainingLocation();
        String name = null;
        if (!SCAFFOLD.equals(current.getCustomEntityType())
                && !Objects.equals(current.getName(), previous.getSpecEntity().getDefaultName())) name = current.getName();
        CustomCampaignEntityAPI built = location.addCustomEntity(Misc.genUID(), name, next.id, current.getFaction().getId());
        copyPosition(current, built);
        built.addTag(AoTDTags.AOTD_OBJECTIVE);
        Object widgetOrder = current.getMemoryWithoutUpdate().get(WIDGET_ORDER);
        if (widgetOrder != null) built.getMemoryWithoutUpdate().set(WIDGET_ORDER, widgetOrder);
        Object original = current.getMemoryWithoutUpdate().get(ORIGINAL_LOCATION);
        if (original != null) built.getMemoryWithoutUpdate().set(ORIGINAL_LOCATION, original);
        if (current.getMemoryWithoutUpdate().contains(USES_STABLE_LOCATION)) {
            built.getMemoryWithoutUpdate().set(USES_STABLE_LOCATION,
                    current.getMemoryWithoutUpdate().getBoolean(USES_STABLE_LOCATION));
        } else if (SCAFFOLD.equals(current.getCustomEntityType())) {
            built.getMemoryWithoutUpdate().set(USES_STABLE_LOCATION, original != null);
        }
        float disruption = previous.getDisruptedDays();
        if (disruption > 0f) built.getMemoryWithoutUpdate().set(next.getDisruptedKey(), true, disruption);
        return built;
    }

    public static void replaceEntity(AoTDStableStructureImpl previous, AoTDStableStructureImpl next, SectorEntityToken built) {
        SectorEntityToken current = previous.currentlyAssignedToken;
        LocationAPI location = current.getContainingLocation();
        current.getMemoryWithoutUpdate().unset(AoTDStableStructureSpecManager.memKeyInEntities);
        // Controlled handoff only: ordinary setCurrentlyAssignedToken still rejects accidental moves.
        next.currentlyAssignedToken = null;
        next.setCurrentlyAssignedToken(built);
        location.removeEntity(current);
        new Objectives().updateOrbitingEntities(location, current, built);
        current.setExpired(true);
    }

    public static boolean canDowngrade(AoTDStableStructureImpl impl) {
        if (impl == null || !impl.isCurrentImplementation() || !impl.isPlayerOwned() || impl.isBuilding()) return false;
        AoTDStableStructureSpec spec = impl.getSpec();
        return spec != null && spec.getDowngrade() != null
                && AoTDStableStructureSpecManager.getSpec(spec.getDowngrade()) != null;
    }

    public static int getDowngradeRefund(AoTDStableStructureImpl impl) {
        if (!canDowngrade(impl)) return 0;
        return Math.max(0, Math.round(impl.getBuildCostCredits() * DOWNGRADE_REFUND_MULT));
    }

    public static boolean downgrade(AoTDStableStructureImpl impl) {
        if (!canDowngrade(impl)) return false;
        AoTDStableStructureSpec downgrade = AoTDStableStructureSpecManager.getSpec(impl.getSpec().getDowngrade());
        AoTDStableStructureImpl next = downgrade.getImplClassInstance();
        next.currentAICoreID = impl.currentAICoreID;
        next.currentItemID = impl.currentItemID;
        next.constructionStorage = impl.constructionStorage;
        next.isDisrupted = impl.isDisrupted();

        int refund = getDowngradeRefund(impl);
        SectorEntityToken built = createFinishedEntity(impl, next);
        impl.unapply();
        replaceEntity(impl, next, built);
        if (next.isFunctional()) next.apply();
        if (refund > 0) Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
        return true;
    }

    public static boolean canCancelConstruction(AoTDStableStructureImpl impl) {
        return impl != null && impl.isCurrentImplementation() && impl.isPlayerOwned()
                && impl.isBuilding() && !impl.isUpgrading();
    }

    public static boolean canCancelUpgrade(AoTDStableStructureImpl impl) {
        return impl != null && impl.isCurrentImplementation() && impl.isPlayerOwned() && impl.isUpgrading();
    }

    public static final float CANCEL_REFUND_MULT = 0.4f;

    public static float getCancelRefundMult(AoTDStableStructureImpl impl) {
        if (!canCancelConstruction(impl) && !canCancelUpgrade(impl)) return 0f;
        return impl.getBuildOrUpgradeProgress() <= 0f ? 1f : CANCEL_REFUND_MULT;
    }

    public static float getCancelStageCost(AoTDStableStructureImpl impl) {
        if (!canCancelConstruction(impl) && !canCancelUpgrade(impl)) return 0f;
        if (impl.isUpgrading()) {
            AoTDStableStructureSpec spec = AoTDStableStructureSpecManager.getSpec(impl.getConstructionTargetId());
            return spec == null ? 0f : Math.max(0f, spec.getImplClassInstance().getBuildCostCredits());
        }
        return Math.max(0f, impl.getBuildCostCredits());
    }

    public static long getCancelRefund(AoTDStableStructureImpl impl) {
        if (!canCancelConstruction(impl) && !canCancelUpgrade(impl)) return 0L;
        return Math.max(0L, Math.round(getCancelStageCost(impl) * (double) getCancelRefundMult(impl)));
    }

    public static boolean cancelConstruction(AoTDStableStructureImpl impl) {
        if (!canCancelConstruction(impl)) return false;
        float originalRefund = getCancelStageCost(impl) * (1f - impl.getBuildOrUpgradeProgress());
        long refund = getCancelRefund(impl);
        impl.cancelBuilding();
        correctCancelRefund(refund - originalRefund);
        return true;
    }

    public static boolean cancelUpgrade(AoTDStableStructureImpl impl) {
        if (!canCancelUpgrade(impl)) return false;
        float originalRefund = getCancelStageCost(impl) * (1f - impl.getBuildOrUpgradeProgress());
        long refund = getCancelRefund(impl);
        impl.cancelUpgrade();
        correctCancelRefund(refund - originalRefund);
        return true;
    }

    private static void correctCancelRefund(float difference) {
        if (difference > 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().add(difference);
        else if (difference < 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(-difference);
    }

    public static boolean canShutDown(AoTDStableStructureImpl impl) {
        return impl != null && impl.isCurrentImplementation() && impl.isPlayerOwned();
    }

    public static long getShutdownRefund(AoTDStableStructureImpl impl) {
        if (!canShutDown(impl)) return 0L;
        if (canCancelConstruction(impl)) return getCancelRefund(impl);
        long refund = Math.max(0L, Math.round(impl.getCumulativeCreditCost() * (double) SHUTDOWN_REFUND_MULT));
        if (canCancelUpgrade(impl)) refund += getCancelRefund(impl);
        return refund;
    }

    public static boolean shutDown(AoTDStableStructureImpl impl) {
        if (!canShutDown(impl)) return false;
        if (canCancelConstruction(impl)) {
            cancelConstruction(impl);
            return true;
        }

        SectorEntityToken current = impl.currentlyAssignedToken;
        LocationAPI location = current.getContainingLocation();
        if (location == null) return false;

        long refund = Math.max(0L, Math.round(impl.getCumulativeCreditCost() * (double) SHUTDOWN_REFUND_MULT));
        SectorEntityToken original = current.getMemoryWithoutUpdate().getEntity(ORIGINAL_LOCATION);

        if (canCancelUpgrade(impl)) cancelUpgrade(impl);

        if (impl.getAICoreId() != null) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            for (MarketAPI market : Global.getSector().getEconomy().getMarkets(location)) {
                if (!market.getFactionId().equals(current.getFaction().getId())) continue;
                if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null) continue;
                cargo = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
                break;
            }
            cargo.addCommodity(impl.getAICoreId(), 1);
        }
        if (impl.getItemId() != null && Global.getSettings().getSpecialItemSpec(impl.getItemId()) != null) {
            Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(impl.getItemId(), null), 1);
        }

        impl.notifyRemoved();
        if (original != null) {
            copyPosition(current, original);
            original.setExpired(false);
            location.addEntity(original);
            new Objectives().updateOrbitingEntities(location, current, original);
        }

        location.removeEntity(current);
        current.setExpired(true);
        if (refund > 0L) Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
        return true;
    }

    public static void cancelScaffold(AoTDStableStructureImpl impl) {
        SectorEntityToken current = impl.currentlyAssignedToken;
        if (!SCAFFOLD.equals(current.getCustomEntityType())) return;
        LocationAPI location = current.getContainingLocation();
        SectorEntityToken original = current.getMemoryWithoutUpdate().getEntity(ORIGINAL_LOCATION);
        if (original != null) {
            copyPosition(current, original);
            original.setExpired(false);
            location.addEntity(original);
            new Objectives().updateOrbitingEntities(location, current, original);
        }
        current.getMemoryWithoutUpdate().unset(AoTDStableStructureSpecManager.memKeyInEntities);
        location.removeEntity(current);
        current.setExpired(true);
    }

    public static void copyPosition(SectorEntityToken from, SectorEntityToken to) {
        if (from.getOrbit() != null) to.setOrbit(from.getOrbit().makeCopy());
        else to.setOrbit(null);
        to.setLocation(from.getLocation().x, from.getLocation().y);
        to.setFacing(from.getFacing());
    }
}
