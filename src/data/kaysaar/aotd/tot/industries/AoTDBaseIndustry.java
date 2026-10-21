package data.kaysaar.aotd.tot.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.SharedUnlockData;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.StatModValueGetter;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDIndustryAmounts;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanel;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AoTDBaseIndustry extends BaseIndustry {
    public static final String EXACT_MODIFIER = "aotd_exact_industry_modifier";
    protected LinkedHashMap<String, MutableStat> exactDemand = new LinkedHashMap<>();
    protected LinkedHashMap<String, MutableStat> exactProduction = new LinkedHashMap<>();
    protected transient Set<String> declaredExactDemand = new LinkedHashSet<>();
    protected transient Set<String> declaredExactProduction = new LinkedHashSet<>();
    protected transient boolean tooltipPreview;
    protected transient boolean ignorePreviewDeficits;
    protected transient float exactDemandReduction;
    protected transient float exactSupplyBonus;

    @Override
    protected Object readResolve() {
        super.readResolve();
        if(exactDemand == null)exactDemand = new LinkedHashMap<>();
        if(exactProduction == null)exactProduction = new LinkedHashMap<>();
        declaredExactDemand = new LinkedHashSet<>();
        declaredExactProduction = new LinkedHashSet<>();
        tooltipPreview = false;
        ignorePreviewDeficits = false;
        return this;
    }

    @Override
    public final void apply() {
        super.apply(true);
        updateIndustryAmounts();
        if(!isFunctional())supply.clear();
        if(!isPending() && isFunctional())applyIndustryEffects();
    }

    protected abstract void applyIndustry();

    protected void applyIndustryEffects() {
    }

    protected void unapplyIndustryEffects() {
    }

    @Override
    public void unapply() {
        unapplyIndustryEffects();
        super.unapply();
    }

    protected void updateIndustryAmounts() {
        if(declaredExactDemand == null)declaredExactDemand = new LinkedHashSet<>();
        if(declaredExactProduction == null)declaredExactProduction = new LinkedHashSet<>();
        declaredExactDemand.clear();
        declaredExactProduction.clear();
        MutableStat reduction = demandReduction.createCopy();
        MutableStat bonus = supplyBonus.createCopy();
        reduction.unmodify(AoTDIndustryData.source);
        bonus.unmodify(AoTDIndustryData.source);
        exactDemandReduction = reduction.getModifiedValue();
        exactSupplyBonus = bonus.getModifiedValue();
        applyIndustry();
        exactDemand.keySet().retainAll(declaredExactDemand);
        exactProduction.keySet().retainAll(declaredExactProduction);
    }

    public boolean isPending() {
        return !tooltipPreview && AoTDIndustryData.getInstance(market).isPending(getId());
    }

    public boolean isTooltipPreview() {
        return tooltipPreview;
    }

    public List<MutableCommodityQuantity> getVanillaDemandEntries() {
        List<MutableCommodityQuantity> entries = new ArrayList<>();
        for (MutableCommodityQuantity entry : demand.values()) {
            if(!entry.getQuantity().isUnmodified() || entry.getQuantity().getBaseValue() != 0f)entries.add(entry);
        }
        return entries;
    }

    public Map<String, MutableStat> getExactDemand() {
        return exactDemand;
    }

    public Map<String, MutableStat> getExactProduction() {
        return exactProduction;
    }

    public boolean hasExactDemand(String commodityId) {
        return exactDemand.containsKey(commodityId);
    }

    public boolean hasExactProduction(String commodityId) {
        return exactProduction.containsKey(commodityId);
    }

    public MutableStat getExactDemandStat(String commodityId) {
        return exactDemand.computeIfAbsent(commodityId,x->new MutableStat(0f));
    }

    public MutableStat getExactProductionStat(String commodityId) {
        return exactProduction.computeIfAbsent(commodityId,x->new MutableStat(0f));
    }

    public void demandExact(String commodityId,int quantity) {
        demandExact(0,commodityId,quantity,BASE_VALUE_TEXT);
    }

    public void demandExact(int index,String commodityId,int quantity,String desc) {
        demandExact(getModId(index),commodityId,quantity,desc);
    }

    public void demandExact(String source,String commodityId,int quantity,String desc) {
        declaredExactDemand.add(commodityId);
        demand.remove(commodityId);
        MutableStat stat = getExactDemandStat(commodityId);
        stat.modifyFlat(source,quantity,desc);
        stat.modifyFlat(EXACT_MODIFIER,-getExactDemandReduction(commodityId),"Industry demand modifiers");
    }

    public void productionExact(String commodityId,int quantity) {
        productionExact(0,commodityId,quantity,BASE_VALUE_TEXT);
    }

    public void productionExact(int index,String commodityId,int quantity,String desc) {
        productionExact(getModId(index),commodityId,quantity,desc);
    }

    public void productionExact(String source,String commodityId,int quantity,String desc) {
        declaredExactProduction.add(commodityId);
        supply.remove(commodityId);
        MutableStat stat = getExactProductionStat(commodityId);
        stat.modifyFlat(source,quantity,desc);
        stat.modifyFlat(EXACT_MODIFIER,getExactSupplyBonus(commodityId),"Industry production modifiers");
    }

    protected int getExactDemandReduction(String commodityId) {
        return convertIndustryModifier(exactDemandReduction,commodityId,true);
    }

    protected int getExactSupplyBonus(String commodityId) {
        return convertIndustryModifier(exactSupplyBonus,commodityId,false);
    }

    protected int convertIndustryModifier(float value,String commodityId,boolean demand) {
        if(value == 0f)return 0;
        int converted = AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(Math.round(Math.abs(value)),demand,commodityId);
        return value < 0 ? -converted : converted;
    }

    @Override
    public void demand(String source,String commodityId,int quantity,String desc) {
        exactDemand.remove(commodityId);
        super.demand(source,commodityId,quantity,desc);
    }

    @Override
    public void supply(String source,String commodityId,int quantity,String desc) {
        exactProduction.remove(commodityId);
        super.supply(source,commodityId,quantity,desc);
    }

    public int getDemandExact(String commodityId) {
        MutableStat stat = exactDemand.get(commodityId);
        if(stat == null)return 0;
        return Math.max(0,stat.getModifiedInt());
    }

    public int getProductionExact(String commodityId) {
        MutableStat stat = exactProduction.get(commodityId);
        if(stat == null)return 0;
        if(!tooltipPreview && (isPending() || !isFunctional()))return 0;
        return Math.max(0,stat.getModifiedInt());
    }

    public int getRawDemand(String commodityId) {
        return AoTDIndustryAmounts.getDemand(this,commodityId);
    }

    public int getRawProduction(String commodityId) {
        return AoTDIndustryAmounts.getProduction(this,commodityId);
    }

    protected AoTDSupplyDemandData getAllocationData(String commodityId) {
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
        if(!(commodity instanceof AoTDCommodityOnMarket com))return null;
        return com.getAoTDAvailableStat().getSupplyDemandDataIfInitialized();
    }

    public int getDeficitExact(String commodityId) {
        if(Global.CODEX_TOOLTIP_MODE || ignorePreviewDeficits || isPending())return 0;
        AoTDSupplyDemandData data = getAllocationData(commodityId);
        if(data == null || !data.hasIndustryAllocation())return 0;
        return data.getRawDeficitFromIndustry(this);
    }

    public Pair<String,Integer> getMaxDeficitExact(String... commodities) {
        Pair<String,Integer> result = new Pair<>(null,0);
        for (String commodity : commodities) {
            int deficit = getDeficitExact(commodity);
            if(deficit > result.two){
                result.one = commodity;
                result.two = deficit;
            }
        }
        return result;
    }

    public float getDemandFulfillment(String... commodities) {
        float fulfilled = 1f;
        for (String commodity : commodities) {
            int required = getRawDemand(commodity);
            if(required <= 0)continue;
            fulfilled = Math.min(fulfilled,1f - (float)getDeficitExact(commodity) / required);
        }
        return Math.max(0f,fulfilled);
    }

    public void applyDeficitToProductionExact(String source,String commodityId,String... inputs) {
        getExactProductionStat(commodityId).modifyMult(source,getDemandFulfillment(inputs),"Input shortages");
    }


    @Override
    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        createTooltip(mode, tooltip, expanded, false);
    }

    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded, boolean ignoreDeficits) {
        boolean wasTooltipPreview = tooltipPreview;
        boolean wasIgnorePreviewDeficits = ignorePreviewDeficits;
        boolean needToAddIndustry = !market.hasIndustry(getId());
        boolean pending = AoTDIndustryData.getInstance(market).isPending(getId());

        tooltipPreview = mode != IndustryTooltipMode.NORMAL;
        ignorePreviewDeficits = ignoreDeficits || mode != IndustryTooltipMode.NORMAL || pending;

        try {
            createIndustryTooltip(mode, tooltip, expanded, pending);
        } finally {
            tooltipPreview = wasTooltipPreview;
            ignorePreviewDeficits = wasIgnorePreviewDeficits;
            if (!needToAddIndustry) {
                reapply();
            }
            market.reapplyConditions();
        }
    }

    protected void createIndustryTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded, boolean pending) {

        if (getSpec() != null && getSpec().hasTag(Tags.CODEX_UNLOCKABLE)) {
            SharedUnlockData.get().reportPlayerAwareOfIndustry(getSpec().getId(), true);
        }
        tooltip.setCodexEntryId(CodexDataV2.getIndustryEntryId(getSpec().getId()));

        currTooltipMode = mode;

        float pad = 3f;
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();
        Color grid = faction.getGridUIColor();
        Color bright = faction.getBrightUIColor();

        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        MarketAPI copy = market.clone();
        copy.setSuppressedConditions(market.getSuppressedConditions());
        copy.setRetainSuppressedConditionsSetWhenEmpty(true);
        market.setRetainSuppressedConditionsSetWhenEmpty(true);
        MarketAPI orig = market;

        boolean needToAddIndustry = !copy.hasIndustry(getId());
        try {
            market = copy;
            if (needToAddIndustry) market.getIndustries().add(this);

            if (mode != IndustryTooltipMode.NORMAL) {
                market.clearCommodities();
                for (CommodityOnMarketAPI curr : market.getAllCommodities()) {
                    curr.getAvailableStat().setBaseValue(100);
                }
            }

            market.reapplyConditions();
            reapply();

            String type = "";
            if (isIndustry()) type = " - Industry";
            if (isStructure()) type = " - Structure";

            tooltip.addTitle(getCurrentName() + type, color);

            String desc = spec.getDesc();
            String override = getDescriptionOverride();
            if (override != null) {
                desc = override;
            }
            desc = Global.getSector().getRules().performTokenReplacement(null, desc, market.getPrimaryEntity(), null);

            tooltip.addPara(desc, opad);

            if (isIndustry() && (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                    mode == IndustryTooltipMode.UPGRADE ||
                    mode == IndustryTooltipMode.DOWNGRADE)
            ) {

                int num = Misc.getNumIndustries(market);
                int max = Misc.getMaxIndustries(market);

                if (isIndustry()) {
                    if (mode == IndustryTooltipMode.UPGRADE) {
                        for (Industry curr : market.getIndustries()) {
                            if (getSpec().getId().equals(curr.getSpec().getUpgrade())) {
                                if (curr.isIndustry()) {
                                    num--;
                                }
                                break;
                            }
                        }
                    } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                        for (Industry curr : market.getIndustries()) {
                            if (getSpec().getId().equals(curr.getSpec().getDowngrade())) {
                                if (curr.isIndustry()) {
                                    num--;
                                }
                                break;
                            }
                        }
                    }
                }

                Color c = gray;
                c = Misc.getTextColor();
                Color h1 = highlight;
                Color h2 = highlight;
                if (num > max) {
                    h1 = bad;
                    num--;

                    tooltip.addPara("Maximum number of industries reached", bad, opad);
                }
            }

            addRightAfterDescriptionSection(tooltip, mode);

            if (isDisrupted()) {
                int left = (int) getDisruptedDays();
                if (left < 1) left = 1;
                String days = "days";
                if (left == 1) days = "day";

                tooltip.addPara("Operations disrupted! %s " + days + " until return to normal function.",
                        opad, Misc.getNegativeHighlightColor(), highlight, "" + left);
            }

            if (DebugFlags.COLONY_DEBUG || market.isPlayerOwned()) {
                if (mode == IndustryTooltipMode.NORMAL) {
                    if (getSpec().getUpgrade() != null && !isBuilding()) {
                        tooltip.addPara("Click to manage or upgrade", Misc.getPositiveHighlightColor(), opad);
                    } else {
                        tooltip.addPara("Click to manage", Misc.getPositiveHighlightColor(), opad);
                    }
                }
            }

            if (mode == IndustryTooltipMode.QUEUED) {
                tooltip.addPara("Click to remove or adjust position in queue", Misc.getPositiveHighlightColor(), opad);
                tooltip.addPara("Currently queued for construction. Does not have any impact on the colony.", opad);

                int left = (int) (getSpec().getBuildTime());
                if (left < 1) left = 1;
                String days = "days";
                if (left == 1) days = "day";
                tooltip.addPara("Requires %s " + days + " to build.", opad, highlight, "" + left);

            } else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL && !isDisrupted()) {
                tooltip.addPara("Currently under construction and not producing anything or providing other benefits.", opad);

                int left = (int) (buildTime - buildProgress);
                if (left < 1) left = 1;
                String days = "days";
                if (left == 1) days = "day";
                tooltip.addPara("Requires %s more " + days + " to finish building.", opad, highlight, "" + left);
            }

            if (!isAvailableToBuild() &&
                    (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                            mode == IndustryTooltipMode.UPGRADE ||
                            mode == IndustryTooltipMode.DOWNGRADE)) {
                String reason = getUnavailableReason();
                if (reason != null) {
                    tooltip.addPara(reason, bad, opad);
                }
            }

            boolean category = getSpec().hasTag(Industries.TAG_PARENT);

            if (!category) {
                int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
                String creditsStr = Misc.getDGSCredits(credits);
                if (mode == IndustryTooltipMode.UPGRADE || mode == IndustryTooltipMode.ADD_INDUSTRY) {
                    int cost = (int) getBuildCost();
                    String costStr = Misc.getDGSCredits(cost);

                    int days = (int) getBuildTime();
                    String daysStr = "days";
                    if (days == 1) daysStr = "day";

                    LabelAPI label = null;
                    if (mode == IndustryTooltipMode.UPGRADE) {
                        label = tooltip.addPara("%s and %s " + daysStr + " to upgrade. You have %s.", opad,
                                highlight, costStr, "" + days, creditsStr);
                    } else {
                        label = tooltip.addPara("%s and %s " + daysStr + " to build. You have %s.", opad,
                                highlight, costStr, "" + days, creditsStr);
                    }
                    label.setHighlight(costStr, "" + days, creditsStr);
                    if (credits >= cost) {
                        label.setHighlightColors(highlight, highlight, highlight);
                    } else {
                        label.setHighlightColors(bad, highlight, highlight);
                    }
                } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                    if (getSpec().getUpgrade() != null) {
                        float refundFraction = Global.getSettings().getFloat("industryRefundFraction");

                        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(getSpec().getUpgrade());
                        int cost = (int) (spec.getCost() * refundFraction);
                        String refundStr = Misc.getDGSCredits(cost);

                        tooltip.addPara("%s refunded for downgrade.", opad, highlight, refundStr);
                    }
                }

                addPostDescriptionSection(tooltip, mode);

                if (!getIncome().isUnmodified()) {
                    int income = getIncome().getModifiedInt();
                    tooltip.addPara("Monthly income: %s", opad, highlight, Misc.getDGSCredits(income));
                    tooltip.addStatModGrid(300, 65, 10, pad, getIncome(), true, new StatModValueGetter() {
                        public String getPercentValue(StatMod mod) {return null;}
                        public String getMultValue(StatMod mod) {return null;}
                        public Color getModColor(StatMod mod) {return null;}
                        public String getFlatValue(StatMod mod) {
                            return Misc.getWithDGS(mod.value) + Strings.C;
                        }
                    });
                }

                if (!getUpkeep().isUnmodified()) {
                    int upkeep = getUpkeep().getModifiedInt();
                    tooltip.addPara("Monthly upkeep: %s", opad, highlight, Misc.getDGSCredits(upkeep));
                    tooltip.addStatModGrid(300, 65, 10, pad, getUpkeep(), true, new StatModValueGetter() {
                        public String getPercentValue(StatMod mod) {return null;}
                        public String getMultValue(StatMod mod) {return null;}
                        public Color getModColor(StatMod mod) {return null;}
                        public String getFlatValue(StatMod mod) {
                            return Misc.getWithDGS(mod.value) + Strings.C;
                        }
                    });
                }

                addPostUpkeepSection(tooltip, mode);

                boolean hasSupply = false;
                for (MutableCommodityQuantity curr : supply.values()) {
                    int qty = curr.getQuantity().getModifiedInt();
                    if (qty <= 0) continue;
                    hasSupply = true;
                    break;
                }
                if (!hasSupply) {
                    for (String commodityId : exactProduction.keySet()) {
                        if (getProductionExact(commodityId) <= 0) continue;
                        hasSupply = true;
                        break;
                    }
                }
                boolean hasDemand = false;
                for (MutableCommodityQuantity curr : demand.values()) {
                    int qty = curr.getQuantity().getModifiedInt();
                    if (qty <= 0) continue;
                    hasDemand = true;
                    break;
                }

                if (!hasDemand && (!pending || mode != IndustryTooltipMode.NORMAL)) {
                    for (String commodityId : exactDemand.keySet()) {
                        if (getDemandExact(commodityId) <= 0) continue;
                        hasDemand = true;
                        break;
                    }
                }
                if (hasSupply) {
                    tooltip.addSectionHeading("Production", color, dark, Alignment.MID, opad);
                    tooltip.addCustom(new AoTDCommodityShortPanelCombined(getTooltipWidth(), 3, this, false, true).getMainPanel(), opad);
                }

                addPostSupplySection(tooltip, hasSupply, mode);

                if (hasDemand || hasPostDemandSection(hasDemand, mode)) {
                    tooltip.addSectionHeading("Demand & effects", color, dark, Alignment.MID, opad);
                }
                if (hasDemand) {
                    tooltip.addCustom(new AoTDCommodityShortPanelCombined(getTooltipWidth(), 3, this, true, ignorePreviewDeficits).getMainPanel(), opad);
                }

                addPostDemandSection(tooltip, hasDemand, mode);

                if (!needToAddIndustry) {
                    addInstalledItemsSection(mode, tooltip, expanded);
                    addImprovedSection(mode, tooltip, expanded);

                    if (pending && mode == IndustryTooltipMode.NORMAL) {
                        addPendingIndustryTooltip(tooltip, getTooltipWidth());
                    }
                    ListenerUtil.addToIndustryTooltip(this, mode, tooltip, getTooltipWidth(), expanded);
                }

                tooltip.addPara("*Shown production and demand values are already adjusted based on current market size and local conditions.", gray, opad);
            }
        } finally {
            try {
                if (needToAddIndustry) {
                    unapply();
                    copy.getIndustries().remove(this);
                }
            } finally {
                market = orig;
                market.setRetainSuppressedConditionsSetWhenEmpty(null);
            }
        }
    }

    protected void addPendingIndustryTooltip(TooltipMakerAPI tooltip, float width) {
        Color color = market.getFaction().getBaseUIColor();
        Color dark = market.getFaction().getDarkUIColor();

        tooltip.addSectionHeading("Preparations", color, dark, Alignment.MID, 5f);
        tooltip.addPara("This industry is currently in-effective, as it needs resources to be allocated, it will be activated in next month!",
                Misc.getTooltipTitleAndLightHighlightColor(), 5f).setAlignment(Alignment.MID);

        LinkedHashMap<String, Integer> required = new LinkedHashMap<>();
        for (String commodityId : AoTDIndustryAmounts.getCommodityIds(this, true)) {
            int quantity = getRawDemand(commodityId);
            if (quantity <= 0) continue;
            required.put(commodityId, -quantity);
        }
        LinkedHashMap<String, Integer> expected = new LinkedHashMap<>();
        MutableStat bonus = supplyBonus.createCopy();
        bonus.unmodify(AoTDIndustryData.source);

        for (MutableCommodityQuantity entry : supply.values()) {
            String commodityId = entry.getCommodityId();
            if (hasExactProduction(commodityId)) continue;

            MutableStat quantity = entry.getQuantity().createCopy();
            if (quantity.getFlatStatMod("ind_sb") != null) {
                quantity.unmodifyFlat("ind_sb");
                quantity.modifyFlat("ind_sb", bonus.getModifiedInt());
            }

            int raw = AoTDCommodityEconSpecManager.getEconSpec(commodityId).getCalculationScript()
                    .getRawUnitsFromSupply(quantity, market, commodityId, this);
            if (raw <= 0) continue;
            expected.put(commodityId, raw);
        }

        for (Map.Entry<String, MutableStat> entry : exactProduction.entrySet()) {
            int quantity = entry.getValue().getModifiedInt();
            if (quantity <= 0) continue;
            expected.put(entry.getKey(), quantity);
        }

        if (!expected.isEmpty()) {
            tooltip.addSectionHeading("Estimated production in next month", color, dark, Alignment.MID, 5f);
            addPendingCommodityRows(tooltip, width, expected, Misc.getPositiveHighlightColor());
        }
        if (!required.isEmpty()) {
            tooltip.addSectionHeading("Demand Necessary", color, dark, Alignment.MID, 5f);
            addPendingCommodityRows(tooltip, width, required, Misc.getNegativeHighlightColor());
        }


    }

    protected void addPendingCommodityRows(TooltipMakerAPI tooltip, float width, LinkedHashMap<String, Integer> commodities, Color color) {
        int maxInRow = 3;
        float effectiveWidth = (width - 6) / maxInRow;
        ArrayList<CustomPanelAPI> toPlace = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : commodities.entrySet()) {
            if (toPlace.size() == maxInRow) {
                CustomPanelAPI row = Global.getSettings().createCustom(width, AoTDCommodityShortPanel.height, null);
                float currX = 0;
                for (CustomPanelAPI customPanelAPI : toPlace) {
                    row.addComponent(customPanelAPI).inTL(currX, 0);
                    currX += effectiveWidth + 2;
                }
                tooltip.addCustom(row, 3f);
                toPlace.clear();
            }
            toPlace.add(new AoTDCommodityShortPanel(entry.getKey(), entry.getValue(), color, effectiveWidth).getMainPanel());
        }

        if (!toPlace.isEmpty()) {
            CustomPanelAPI row = Global.getSettings().createCustom(width, AoTDCommodityShortPanel.height, null);
            int size = toPlace.size();
            float effectiveWidthTaken = size * effectiveWidth + ((size - 1) * 2);
            float currX = (width / 2) - (effectiveWidthTaken / 2);
            for (CustomPanelAPI customPanelAPI : toPlace) {
                row.addComponent(customPanelAPI).inTL(currX, 0);
                currX += effectiveWidth + 2;
            }
            tooltip.addCustom(row, 3f);
            toPlace.clear();
        }
    }
}
