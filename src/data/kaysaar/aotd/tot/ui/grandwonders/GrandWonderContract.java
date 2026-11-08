package data.kaysaar.aotd.tot.ui.grandwonders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;

import java.awt.Color;
import java.util.Map;

public class GrandWonderContract extends AoTDTradeContract {
    AoTDConstructionSite site;

    public static float MARGIN_OF_COMMODITY_PRICE = 0.00f;

    public GrandWonderContract(AoTDConstructionSite site) {
        super(site.getUniqueIdForContract(), null, Factions.PLAYER, 9999);
        this.site = site;
        doesContractHavePenalty = false;
        hasCustomEffectSection = true;
        refreshRequirements();
    }

    protected void refreshRequirements() {
        contractData.clear();
        if (isExpired()) return;

        for (Map.Entry<String, Integer> entry : site.getMonthlyResNeeded().entrySet()) {
            if (Global.getSettings().getCommoditySpec(entry.getKey()) == null) continue;
            if (entry.getValue() <= 0) continue;
            addContractData(entry.getKey(), entry.getValue(), MARGIN_OF_COMMODITY_PRICE);
        }
    }

    @Override
    public boolean isExpired() {
        if (site == null || site.getMarket() == null || !site.isUpgrading()) return true;

        for (Industry industry : site.getMarket().getIndustries()) {
            if (industry == site) return false;
        }
        return true;
    }

    @Override
    public boolean isContractFrozen() {
        return isExpired()
                || !site.getMarket().getFaction().isPlayerFaction()
                || site.isDisrupted()
                || super.isContractFrozen();
    }

    @Override
    public Pair<String, Color> getCurrentContractStatus() {
        if (!isExpired()) {
            if (!site.getMarket().getFaction().isPlayerFaction()) {
                return new Pair<>("Paused", Misc.getGrayColor());
            }
            if (site.isDisrupted()) {
                return new Pair<>("Disrupted", Misc.getNegativeHighlightColor());
            }
        }
        return super.getCurrentContractStatus();
    }

    @Override
    public void executeMonthEndForCommodity(int delivered, String commodityId) {
        if (delivered > 0 && !isExpired() && !isContractFrozen()) {
            site.addResourcesToBeSpentOnRestoration(commodityId, delivered);
        }
    }

    @Override
    public void executeMonthEnd(float percentageOfEntireContractMet) {
        if (!isExpired() && !isContractFrozen()) refreshRequirements();
    }

    @Override
    public void decrementMonth() {
    }

    @Override
    public String getSubTypeOfContractString() {
        if (site != null && site.getWonderAPI() != null) {
            return site.getWonderAPI().getCurrentName();
        }
        if (site != null && site.getAssignedWonder() != null
                && Global.getSettings().getIndustrySpec(site.getAssignedWonder()) != null) {
            return Global.getSettings().getIndustrySpec(site.getAssignedWonder()).getName();
        }
        return "Grand Wonder";
    }

    @Override
    public String getContractTypeId() {
        return "grand_wonder_construction";
    }

    @Override
    public String getContractType() {
        return "Grand Wonder Construction";
    }

    @Override
    public void printCustomSection(TooltipMakerAPI tooltip, float width) {
        tooltip.addPara(
                "Construction materials are drawn from %s every month. " +
                        "The same monthly installment is requested until the remaining materials " +
                        "are fully supplied.",
                3f, Color.ORANGE, "faction production capacity"
        );
        if (!isExpired() && site.isDisrupted()) {
            tooltip.addPara("Construction and deliveries are paused while the site is disrupted.",
                    Misc.getNegativeHighlightColor(), 5f);
        } else if (!isExpired() && !site.getMarket().getFaction().isPlayerFaction()) {
            tooltip.addPara("Construction and deliveries are paused while the site is not player-owned.",
                    Misc.getGrayColor(), 5f);
        }
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
}
