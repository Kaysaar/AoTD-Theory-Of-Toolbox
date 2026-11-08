package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class StellarShadeArrayImpl extends AoTDPlanetarySolarArrayImpl implements MarketImmigrationModifier {

    @Override
    public boolean isShade() {
        return true;
    }

    protected float getCoreHazardReduction(String coreId) {
        if (Commodities.GAMMA_CORE.equals(coreId)) return 0.05f;
        if (Commodities.BETA_CORE.equals(coreId)) return 0.10f;
        if (Commodities.ALPHA_CORE.equals(coreId)) return 0.15f;
        return 0f;
    }

    protected float getCorePopulationGrowthBonus(String coreId) {
        if (Commodities.BETA_CORE.equals(coreId)) return 0.05f;
        if (Commodities.ALPHA_CORE.equals(coreId)) return 0.10f;
        return 0f;
    }

    @Override
    public void apply() {
        if (!isFunctional()) return;
        MarketAPI market = getTargetMarket();
        if (market == null) return;

        float hazardReduction = getCoreHazardReduction(getAICoreId());
        float populationGrowth = getCorePopulationGrowthBonus(getAICoreId());
        String modId = getModId();

        if (hazardReduction > 0f) {
            market.getHazard().modifyFlat(modId, -hazardReduction, "AI-regulated stellar shading");
        }
        if (populationGrowth > 0f && !market.getImmigrationModifiers().contains(this)) {
            market.addImmigrationModifier(this);
        }
    }

    @Override
    public void unapply() {
        MarketAPI market = getTargetMarket();
        if (market == null) return;
        market.getHazard().unmodifyFlat(getModId());
        market.removeImmigrationModifier(this);
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (!isFunctional() || market != getTargetMarket()) return;
        float growth = getCorePopulationGrowthBonus(getAICoreId());
        if (growth > 0f) {
            incoming.getWeight().modifyPercent(getModId(), growth * 100f, "AI-regulated stellar climate");
        }
    }

    @Override
    public void applyEffectsOnChangeOfAICore(String currentAiCore) {
        unapply();
        if (isFunctional()) apply();
    }

    @Override
    protected void buildingFinished() {
        super.buildingFinished();
        if (isFunctional()) apply();
    }

    @Override
    protected void notifyDisrupted() {
        unapply();
        super.notifyDisrupted();
    }

    @Override
    protected void disruptionFinished() {
        super.disruptionFinished();
        if (isFunctional()) apply();
    }

    @Override
    public void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Gamma-level AI core", mode)
                        + "Automates shade alignment and temperature regulation. "
                        + "Reduces the linked market's hazard rating by %s.",
                0f, Misc.getHighlightColor(),
                "5 percentage points"
        );
    }

    @Override
    public void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Beta-level AI core", mode)
                        + "Predicts atmospheric fluctuations and regulates incoming stellar radiation. "
                        + "Reduces the linked market's hazard rating by %s and increases population growth by %s.",
                0f, Misc.getHighlightColor(),
                "10 percentage points", "+5%"
        );
    }

    @Override
    public void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Alpha-level AI core", mode)
                        + "Continuously models planetary climate and coordinates the entire shade array. "
                        + "Reduces the linked market's hazard rating by %s and increases population growth by %s.",
                0f, Misc.getHighlightColor(),
                "15 percentage points", "+10%"
        );
    }
}
