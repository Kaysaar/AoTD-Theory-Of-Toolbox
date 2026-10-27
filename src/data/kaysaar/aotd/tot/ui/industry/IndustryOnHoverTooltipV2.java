package data.kaysaar.aotd.tot.ui.industry;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.kaysaar.aotd.tot.industries.AoTDBaseIndustry;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;

import java.util.List;

public class IndustryOnHoverTooltipV2 implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    Industry ind;
    boolean expanded;
    boolean ignoreDeficits = false;
    Industry.IndustryTooltipMode mode = Industry.IndustryTooltipMode.NORMAL;
    TooltipMakerAPI tl;

    public TooltipMakerAPI getTl() {
        return tl;
    }

    public IndustryOnHoverTooltipV2(float width, Industry ind, boolean expanded) {
        this(width, ind, expanded, Industry.IndustryTooltipMode.NORMAL, false);
    }

    public IndustryOnHoverTooltipV2(float width, Industry ind, boolean expanded,
                                    Industry.IndustryTooltipMode mode) {
        this(width, ind, expanded, mode,
                mode == Industry.IndustryTooltipMode.ADD_INDUSTRY
                        || mode == Industry.IndustryTooltipMode.UPGRADE
                        || mode == Industry.IndustryTooltipMode.DOWNGRADE);
    }

    public IndustryOnHoverTooltipV2(float width, Industry ind, boolean expanded,
                                    boolean ignoreDeficits) {
        this(width, ind, expanded, Industry.IndustryTooltipMode.NORMAL, ignoreDeficits);
    }

    private IndustryOnHoverTooltipV2(float width, Industry ind, boolean expanded,
                                     Industry.IndustryTooltipMode mode, boolean ignoreDeficits) {
        mainPanel = Global.getSettings().createCustom(width, 1f, this);
        this.ind = ind;
        this.expanded = expanded;
        this.mode = mode;
        this.ignoreDeficits = ignoreDeficits;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (tl != null) {
            mainPanel.removeComponent(tl);
            tl = null;
        }

        float width = mainPanel.getPosition().getWidth();
        boolean isAoTD = ind instanceof AoTDBaseIndustry;

        TooltipMakerAPI result = mainPanel.createUIElement(width, 10000f, false);
        TooltipMakerAPI original = mainPanel.createUIElement(width, 100000f, true);

        boolean needToAddIndustry = false;
        boolean hasFreePort = false;

        if (!isAoTD) {
            ind.getMarket().reapplyConditions();
            hasFreePort = ind.getMarket().isFreePort();

            for (Industry industry : ind.getMarket().getIndustries()) {
                if (!AoTDIndustryData.getInstance(industry.getMarket()).isPending(industry.getId())) {
                    industry.reapply();
                }
            }

            needToAddIndustry = !ind.getMarket().hasIndustry(ind.getId());

            if (needToAddIndustry) {
                for (CommodityOnMarketAPI curr : ind.getMarket().getAllCommodities()) {
                    curr.getAvailableStat().setBaseValue(10000);
                }

                ind.apply();
                ind.getMarket().reapplyConditions();
                ind.apply();
            }
        }

        try {
            if (isAoTD && ignoreDeficits) {
                ((AoTDBaseIndustry) ind).createTooltip(mode, original, expanded, true);
            } else {
                ind.createTooltip(mode, original, expanded);
            }

            UIPanelAPI holder = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(original).get(0);
            List<UIComponentAPI> comps = ReflectionUtilis.getChildrenCopy(holder);

            float[] originalTops = new float[comps.size()];
            float[] originalBottoms = new float[comps.size()];

            int productionIndex = -1;
            int demandIndex = -1;

            for (int i = 0; i < comps.size(); i++) {
                UIComponentAPI comp = comps.get(i);
                PositionAPI position = comp.getPosition();

                originalBottoms[i] = Math.abs(position.getY());
                originalTops[i] = originalBottoms[i] - position.getHeight();

                if (!isAoTD && comp instanceof LabelAPI label) {
                    String text = label.getText();

                    if ("Production".equals(text)) {
                        productionIndex = i + 1;
                    }

                    if ("Demand & effects".equals(text)) {
                        demandIndex = i + 1;
                    }
                }
            }

            float offsetY = 0f;
            float totalHeight = 0f;

            for (int i = 0; i < comps.size(); ) {
                boolean replaceProduction = !isAoTD && i == productionIndex;
                boolean replaceDemand = !isAoTD && i == demandIndex;

                if ((replaceProduction || replaceDemand) && i > 0 && i + 1 < comps.size()) {
                    AoTDCommodityShortPanelCombined section =
                            new AoTDCommodityShortPanelCombined(
                                    width,
                                    3,
                                    ind,
                                    replaceDemand,
                                    replaceDemand && ignoreDeficits
                            );

                    float oldHeaderBottom = originalBottoms[i - 1];
                    float oldSectionBottom = Math.max(
                            originalBottoms[i],
                            originalBottoms[i + 1]
                    );

                    float newTop = oldHeaderBottom + offsetY + 5f;
                    float newHeight = section.getMainPanel().getPosition().getHeight();
                    float newBottom = newTop + newHeight;

                    result.addCustom(section.getMainPanel(), 0f)
                            .getPosition().inTL(0f, newTop);

                    totalHeight = Math.max(totalHeight, newBottom);
                    offsetY += newBottom - (oldSectionBottom + offsetY);

                    i += 2;
                    continue;
                }

                UIComponentAPI comp = comps.get(i);

                float top = originalTops[i] + offsetY;
                float bottom = originalBottoms[i] + offsetY;

                result.addCustom(comp, 0f).getPosition().inTL(0f, top);

                totalHeight = Math.max(totalHeight, bottom);
                i++;
            }

            mainPanel.getPosition().setSize(width, Math.max(1f, totalHeight));
            mainPanel.addUIElement(result).inTL(0f, 0f);
            tl = result;
        } finally {
            if (!isAoTD) {
                if (needToAddIndustry) {
                    ind.unapply();

                    for (CommodityOnMarketAPI curr : ind.getMarket().getAllCommodities()) {
                        curr.getAvailableStat().setBaseValue(0);
                    }
                }

                ind.getMarket().setFreePort(hasFreePort);
                ind.getMarket().reapplyConditions();
            }
        }
    }

    @Override
    public void clearUI() {
    }

    @Override
    public void positionChanged(PositionAPI position) {
    }

    @Override
    public void renderBelow(float alphaMult) {
    }

    @Override
    public void render(float alphaMult) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }

    @Override
    public void buttonPressed(Object buttonId) {
    }
}