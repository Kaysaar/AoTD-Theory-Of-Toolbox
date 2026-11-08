package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.industries.AoTDBaseIndustry;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDIndustryAmounts;

public class StellarMirrorArrayImpl extends AoTDPlanetarySolarArrayImpl {

    @Override
    public boolean isShade() {
        return false;
    }

    protected float getCoreFoodProductionBonus(String coreId) {
        if (Commodities.GAMMA_CORE.equals(coreId)) return 0.05f;
        if (Commodities.BETA_CORE.equals(coreId)) return 0.10f;
        if (Commodities.ALPHA_CORE.equals(coreId)) return 0.15f;
        return 0f;
    }

    protected float getCoreIndustryUpkeepReduction(String coreId) {
        if (Commodities.BETA_CORE.equals(coreId)) return 0.05f;
        if (Commodities.ALPHA_CORE.equals(coreId)) return 0.10f;
        return 0f;
    }

    @Override
    public void apply() {
        if (!isFunctional()) return;
        MarketAPI market = getTargetMarket();
        if (market == null) return;

        float foodProduction = getCoreFoodProductionBonus(getAICoreId());
        float upkeepReduction = getCoreIndustryUpkeepReduction(getAICoreId());
        String modId = getModId();

        if (upkeepReduction > 0f) {
            market.getUpkeepMult().modifyMult(modId, 1f - upkeepReduction, "AI-optimized stellar illumination");
        }
        if (foodProduction <= 0f) return;

        for (Industry industry : market.getIndustries()) {
            if (!industry.isFunctional() || AoTDIndustryAmounts.getProduction(industry, Commodities.FOOD) <= 0) continue;
            if (industry instanceof AoTDBaseIndustry ind && ind.hasExactProduction(Commodities.FOOD)) {
                ind.getExactProductionStat(Commodities.FOOD).modifyMult(modId, 1f + foodProduction, "AI-optimized stellar illumination");
            } else {
                industry.getSupply(Commodities.FOOD).getQuantity().modifyMult(modId, 1f + foodProduction, "AI-optimized stellar illumination");
            }
        }
    }

    @Override
    public void unapply() {
        MarketAPI market = getTargetMarket();
        if (market == null) return;
        String modId = getModId();
        market.getUpkeepMult().unmodifyMult(modId);

        for (Industry industry : market.getIndustries()) {
            if (industry instanceof AoTDBaseIndustry ind) {
                MutableStat stat = ind.getExactProduction().get(Commodities.FOOD);
                if (stat != null) stat.unmodifyMult(modId);
            }
            for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                if (Commodities.FOOD.equals(supply.getCommodityId())) {
                    supply.getQuantity().unmodifyMult(modId);
                }
            }
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
                        + "Automates solar mirror alignment and light distribution. "
                        + "Increases Food production on the linked market by %s.",
                0f, Misc.getHighlightColor(),
                "+5%"
        );
    }

    @Override
    public void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Beta-level AI core", mode)
                        + "Coordinates the array to improve agricultural yields and conserve energy. "
                        + "Increases Food production by %s and reduces industry upkeep by %s on the linked market.",
                0f, Misc.getHighlightColor(),
                "+10%", "5%"
        );
    }

    @Override
    public void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        tooltip.addPara(
                getAICoreDescriptionPrefix("Alpha-level AI core", mode)
                        + "Models planetary illumination patterns to optimize agricultural output and energy consumption. "
                        + "Increases Food production by %s and reduces industry upkeep by %s on the linked market.",
                0f, Misc.getHighlightColor(),
                "+15%", "10%"
        );
    }
}
