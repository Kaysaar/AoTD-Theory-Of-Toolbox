package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class NavBuoyImpl extends AoTDStableStructureImpl {

    protected float getBurnBonus() {
        return NavBuoyEntityPlugin.NAV_BONUS;
    }

    protected float getCoreEffectiveness() {
        return 1f;
    }

    protected float getCoreAccessibilityBonus(String coreId) {
        if (Commodities.GAMMA_CORE.equals(coreId)) return 0.02f * getCoreEffectiveness();
        if (Commodities.BETA_CORE.equals(coreId)) return 0.04f * getCoreEffectiveness();
        if (Commodities.ALPHA_CORE.equals(coreId)) return 0.08f * getCoreEffectiveness();
        return 0f;
    }

    @Override
    public boolean canMountAICores() {
        return true;
    }

    @Override
    public void apply() {
        if (!isFunctional()) return;

        float accessibility = getCoreAccessibilityBonus(getAICoreId());
        if (accessibility <= 0f) return;

        String modId = getModId();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getStarSystem() != currentlyAssignedToken.getStarSystem()) continue;
            if (!market.getFactionId().equals(currentlyAssignedToken.getFaction().getId())) continue;

            market.getAccessibilityMod().modifyFlat(
                    modId,
                    accessibility,
                    "AI-optimized navigation routes"
            );
        }
    }

    @Override
    public void unapply() {
        if (currentlyAssignedToken == null) return;

        String modId = getModId();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            market.getAccessibilityMod().unmodifyFlat(modId);
        }
    }

    @Override
    public void applyEffectsOnChangeOfAICore(String currentAiCore) {
        unapply();
        if (isFunctional()) apply();
    }

    @Override
    public void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Gamma-level AI core", mode)
                        + "Automates navigation beacon calibration and routine traffic guidance. "
                        + "Increases accessibility of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreAccessibilityBonus(Commodities.GAMMA_CORE) * 100f) + "%"
        );
    }

    @Override
    public void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Beta-level AI core", mode)
                        + "Coordinates commercial traffic and optimizes approach corridors around local navigation hazards. "
                        + "Increases accessibility of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreAccessibilityBonus(Commodities.BETA_CORE) * 100f) + "%"
        );
    }

    @Override
    public void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Alpha-level AI core", mode)
                        + "Predicts system-wide traffic patterns and calculates optimal navigation routes, "
                        + "substantially improving freight coordination. "
                        + "Increases accessibility of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreAccessibilityBonus(Commodities.ALPHA_CORE) * 100f) + "%"
        );
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        int bonus = Math.round(getBurnBonus());

        tooltip.addPara(
                "%s burn level for all same-faction fleets in the star system",
                5f,
                Misc.getHighlightColor(),
                "+" + bonus
        );


    }
}
