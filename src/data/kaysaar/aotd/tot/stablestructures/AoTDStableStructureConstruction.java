package data.kaysaar.aotd.tot.stablestructures;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.*;
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
