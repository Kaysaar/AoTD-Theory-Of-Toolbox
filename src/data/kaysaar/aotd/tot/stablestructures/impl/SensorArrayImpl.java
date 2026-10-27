package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.SensorArrayEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class SensorArrayImpl extends AoTDStableStructureImpl {

    protected float getSensorRangeBonus() {
        return SensorArrayEntityPlugin.SENSOR_BONUS;
    }

    protected float getCoreEffectiveness() {
        return 1f;
    }

    protected float getCoreGroundDefenseBonus(String coreId) {
        if (Commodities.GAMMA_CORE.equals(coreId)) return 10f * getCoreEffectiveness();
        if (Commodities.BETA_CORE.equals(coreId)) return 20f * getCoreEffectiveness();
        if (Commodities.ALPHA_CORE.equals(coreId)) return 35f * getCoreEffectiveness();
        return 0f;
    }

    @Override
    public boolean canMountAICores() {
        return true;
    }

    @Override
    public void apply() {
        if (!isFunctional()) return;

        float defenseBonus = getCoreGroundDefenseBonus(getAICoreId());
        if (defenseBonus <= 0f) return;

        String modId = getModId();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getStarSystem() != currentlyAssignedToken.getStarSystem()) continue;
            if (!market.getFactionId().equals(currentlyAssignedToken.getFaction().getId())) continue;

            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyPercent(
                    modId,
                    defenseBonus,
                    "AI-coordinated sensor intelligence"
            );
        }
    }

    @Override
    public void unapply() {
        if (currentlyAssignedToken == null) return;

        String modId = getModId();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyPercent(modId);
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
                        + "Automates sensor analysis and threat classification, allowing local defenders to respond more efficiently. "
                        + "Increases ground defenses of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreGroundDefenseBonus(Commodities.GAMMA_CORE)) + "%"
        );
    }

    @Override
    public void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Beta-level AI core", mode)
                        + "Correlates sensor data across the system, tracking suspicious movements and improving defensive readiness. "
                        + "Increases ground defenses of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreGroundDefenseBonus(Commodities.BETA_CORE)) + "%"
        );
    }

    @Override
    public void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Alpha-level AI core", mode)
                        + "Processes extensive sensor intelligence to predict hostile activity and coordinate defensive "
                        + "preparations before an attack develops. "
                        + "Increases ground defenses of same-faction colonies in the star system by %s.",
                0f, Misc.getHighlightColor(),
                "+" + Misc.getRoundedValue(getCoreGroundDefenseBonus(Commodities.ALPHA_CORE)) + "%"
        );
    }

    @Override
    public void createTooltipForEffectSection(TooltipMakerAPI tooltip, AoTDStableStructureTooltipMode mode) {
        super.createTooltipForEffectSection(tooltip, mode);

        int bonus = Math.round(getSensorRangeBonus());

        tooltip.addPara(
                "%s sensor range for all same-faction fleets in the star system",
                5f,
                Misc.getHighlightColor(),
                "+" + bonus
        );

    }
}
