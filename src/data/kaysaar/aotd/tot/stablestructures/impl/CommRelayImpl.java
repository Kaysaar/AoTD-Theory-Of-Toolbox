package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class CommRelayImpl extends AoTDStableStructureImpl {

    protected float getStabilityBonus() {
        return CommRelayCondition.COMM_RELAY_BONUS;
    }

    protected float getCoreEffectiveness() {
        return 1f;
    }

    protected float getCoreStabilityBonus(String coreId) {
        if (Commodities.GAMMA_CORE.equals(coreId)) return 0.5f * getCoreEffectiveness();
        if (Commodities.BETA_CORE.equals(coreId)) return 1f * getCoreEffectiveness();
        if (Commodities.ALPHA_CORE.equals(coreId)) return 2f * getCoreEffectiveness();
        return 0f;
    }

    protected float getCoreAccessibilityBonus(String coreId) {
        if (Commodities.BETA_CORE.equals(coreId)) return 0.02f * getCoreEffectiveness();
        if (Commodities.ALPHA_CORE.equals(coreId)) return 0.05f * getCoreEffectiveness();
        return 0f;
    }

    @Override
    public boolean canMountAICores() {
        return true;
    }

    @Override
    public boolean canMountItems() {
        return true;
    }

    @Override
    public void apply() {
        if (!isFunctional()) return;

        float stability = getCoreStabilityBonus(getAICoreId());
        float accessibility = getCoreAccessibilityBonus(getAICoreId());
        String modId = getModId();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getStarSystem() != currentlyAssignedToken.getStarSystem()) continue;
            if (!market.getFactionId().equals(currentlyAssignedToken.getFaction().getId())) continue;

            if (stability > 0f) {
                market.getStability().modifyFlat(modId, stability, "AI-coordinated comm relay");
            }

            if (accessibility > 0f) {
                market.getAccessibilityMod().modifyFlat(modId, accessibility, "AI-coordinated comm relay");
            }
        }
    }

    @Override
    public void unapply() {
        if (currentlyAssignedToken == null) return;

        String modId = getModId();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            market.getStability().unmodifyFlat(modId);
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
                        + "Automates communication routing and signal prioritization. "
                        + "Increases stability of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreStabilityBonus(Commodities.GAMMA_CORE))
        );
    }

    @Override
    public void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Beta-level AI core", mode)
                        + "Coordinates interplanetary communications, civilian traffic, and administrative networks. "
                        + "Increases stability by %s and accessibility by %s for same-faction colonies in the star system.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreStabilityBonus(Commodities.BETA_CORE)),
                "+" + Misc.getRoundedValue(getCoreAccessibilityBonus(Commodities.BETA_CORE) * 100f) + "%"
        );
    }

    @Override
    public void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Alpha-level AI core", mode)
                        + "Oversees the system-wide communications network, optimizing information flow "
                        + "and administrative coordination. Increases stability by %s and accessibility by %s "
                        + "for same-faction colonies in the star system.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreStabilityBonus(Commodities.ALPHA_CORE)),
                "+" + Misc.getRoundedValue(getCoreAccessibilityBonus(Commodities.ALPHA_CORE) * 100f) + "%"
        );
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        int bonus = Math.abs(Math.round(getStabilityBonus()));

        tooltip.addPara(
                "%s stability for same-faction colonies in the star system",
                5f,
                Misc.getHighlightColor(),
                "+" + bonus
        );


    }
}
