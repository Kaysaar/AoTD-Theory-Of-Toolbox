package data.kaysaar.aotd.tot.ui.starsystems;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureConstruction;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaimStarSystemOwnershipDialog extends BasePopUpDialog {
    public static final float RELATIONSHIP_PENALTY = -0.7f;

    StarSystemAPI system;

    public ClaimStarSystemOwnershipDialog(StarSystemAPI system) {
        super("Claim Star System Ownership");
        this.system = system;
        confirmButtonText = "Claim";
    }

    public static Map<String, Integer> getCombinedMarketSizes(StarSystemAPI system) {
        Map<String, Integer> sizes = new HashMap<>();
        if (system == null) return sizes;

        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system)) {
            if (market == null || market.isHidden() || market.getFaction() == null) continue;
            sizes.merge(market.getFactionId(), market.getSize(), Integer::sum);
        }
        return sizes;
    }

    public static int getPlayerCombinedMarketSize(StarSystemAPI system) {
        return getCombinedMarketSizes(system).getOrDefault(Global.getSector().getPlayerFaction().getId(), 0);
    }

    public static int getLargestOtherCombinedMarketSize(StarSystemAPI system) {
        Map<String, Integer> sizes = getCombinedMarketSizes(system);
        String playerId = Global.getSector().getPlayerFaction().getId();
        int max = 0;
        for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
            if (playerId.equals(entry.getKey())) continue;
            max = Math.max(max, entry.getValue());
        }
        return max;
    }

    public static boolean canClaim(StarSystemAPI system) {
        if (system == null || system.getCenter() == null) return false;
        FactionAPI currentOwner = AshMisc.getClaimingFaction(system.getCenter());
        if (currentOwner != null && currentOwner.isPlayerFaction()) return false;

        Map<String, Integer> sizes = getCombinedMarketSizes(system);
        String playerId = Global.getSector().getPlayerFaction().getId();
        int playerSize = sizes.getOrDefault(playerId, 0);
        if (playerSize <= 0) return false;

        for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
            if (playerId.equals(entry.getKey())) continue;
            if (playerSize <= entry.getValue()) return false;
        }
        return true;
    }

    public static void transferStructures(StarSystemAPI system, FactionAPI owner) {
        if (system == null || owner == null) return;

        List<SectorEntityToken> toTransfer = new ArrayList<>();
        List<AoTDStableStructureImpl> affectedImplementations = new ArrayList<>();

        for (SectorEntityToken token : new ArrayList<>(system.getAllEntities())) {
            if (token == null || token.isExpired() || AoTDToolboxMisc.isSystemStableLocation(token)) continue;
            if (token.getFaction() != null && token.getFaction().getId().equals(owner.getId())) continue;

            String id = token.getCustomEntityType();
            boolean overlay = AoTDStableStructureConstruction.OVERLAY.equals(id);
            boolean registered = token.getCustomEntitySpec() != null
                    && AoTDStableStructureSpecManager.getSpec(token.getCustomEntitySpec().getId()) != null;

            if (!overlay && !AoTDToolboxMisc.isSystemStructure(token) && !registered) continue;
            toTransfer.add(token);

            Object data = token.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
            if (data instanceof AoTDStableStructureImpl impl && impl.isCurrentImplementation()) {
                affectedImplementations.add(impl);
            }
        }

        // Remove effects from the previous owner's markets before changing factions.
        for (AoTDStableStructureImpl impl : affectedImplementations) {
            impl.unapply();
        }

        for (SectorEntityToken token : toTransfer) {
            token.setFaction(owner.getId());
        }

        // Reapply ownership-dependent effects to the new owner's eligible markets.
        for (AoTDStableStructureImpl impl : affectedImplementations) {
            if (impl.isCurrentImplementation() && impl.isFunctional()) impl.apply();
        }
    }

    public static boolean claim(StarSystemAPI system) {
        if (!canClaim(system)) return false;

        FactionAPI owner = AshMisc.getClaimingFaction(system.getCenter());
        FactionAPI player = Global.getSector().getPlayerFaction();
        transferStructures(system, player);
        system.getMemoryWithoutUpdate().set(MemFlags.CLAIMING_FACTION, player.getId());

        if (owner != null && !owner.isPlayerFaction() && !Factions.NEUTRAL.equals(owner.getId())) {
            player.adjustRelationship(owner.getId(), RELATIONSHIP_PENALTY);
            Global.getSoundPlayer().playUISound(Sounds.REP_LOSS, 1f, 1f);
        }

        AoTDStableStructureConstruction.requestUIRefresh(system);
        return true;
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI button = super.generateConfirmButton(tooltip);
        button.setEnabled(canClaim(system));
        return button;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        tooltip.setParaInsigniaLarge();
        if (!canClaim(system)) {
            tooltip.addPara("Your faction must have the highest combined market size in the system to claim ownership. Ties do not qualify.", 5f);
            return;
        }

        FactionAPI owner = AshMisc.getClaimingFaction(system.getCenter());
        tooltip.addPara("Claim ownership of %s?", 5f, Misc.getHighlightColor(), system.getName());
        tooltip.addPara("Your combined market size: %s. Largest competing faction: %s.", 10f,
                Misc.getHighlightColor(), String.valueOf(getPlayerCombinedMarketSize(system)),
                String.valueOf(getLargestOtherCombinedMarketSize(system)));

        if (owner != null && !Factions.NEUTRAL.equals(owner.getId()) && !owner.isPlayerFaction()) {
            tooltip.addPara("This will reduce your relationship with %s, the current owner, by %s points.", 10f,
                    new Color[]{Color.ORANGE,Misc.getNegativeHighlightColor()}, owner.getDisplayName(), "70");
        } else {
            tooltip.addPara("There is no recognized faction owner. Claiming ownership will not affect relationships.", 10f);
        }
        tooltip.addPara("Claiming the system transfers all system structures to your faction immediately. Existing colonies remain under their current owners.", 10f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (confirmButton != null) {
            boolean can = canClaim(system);
            if (confirmButton.isEnabled() != can) confirmButton.setEnabled(can);
            if (!can) confirmButton.setChecked(false);
        }
    }

    @Override
    public void applyConfirmScript() {
        claim(system);
    }
}
