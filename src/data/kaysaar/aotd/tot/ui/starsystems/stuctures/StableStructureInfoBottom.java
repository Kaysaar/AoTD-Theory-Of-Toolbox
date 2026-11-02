package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.IntervalUtil;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;

import java.awt.Color;
import java.util.List;

public class StableStructureInfoBottom implements CustomUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    StarSystemAPI system;
    IntervalUtil refresh = new IntervalUtil(0.25f, 0.25f);
    int displayedUsed, displayedCapacity;
    float displayedCredits;

    public StableStructureInfoBottom(StarSystemAPI system, float width, float height) {
        this.system = system;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public void createUI() {
        if (contentPanel != null) mainPanel.removeComponent(contentPanel);
        float width = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();
        contentPanel = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI tooltip = contentPanel.createUIElement(width, height, false);
        tooltip.setParaInsigniaLarge();

        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        displayedCredits = credits;
        LabelAPI creditsLabel = tooltip.addPara("Credits: %s", 0f, Misc.getGrayColor(), Color.ORANGE, Misc.getWithDGS(credits));
        creditsLabel.getPosition().setSize(320, 30);
        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 200f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Credits you currently have available for construction.", 5f);
            }
        }, TooltipMakerAPI.TooltipLocation.ABOVE, false);

        int used = AoTDToolboxMisc.getUsedSystemStructureSlots(system);
        int maximum = AoTDToolboxMisc.getSystemStructureCapacityStat(system).getModifiedInt();
        displayedUsed = used;
        displayedCapacity = maximum;
        Color col = used >= maximum ? Misc.getNegativeHighlightColor() : Color.ORANGE;
        LabelAPI label = tooltip.addPara("System Structures: %s / %s", 5f, Misc.getGrayColor(),
                col, String.valueOf(used), String.valueOf(maximum));
        label.getPosition().inTL(330, 0);
        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 500f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                addSystemStructureCapacityBreakdown(tooltip, system);
            }
        }, TooltipMakerAPI.TooltipLocation.ABOVE, false);

        contentPanel.addUIElement(tooltip).inTL(0, 0);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public static void addSystemStructureCapacityBreakdown(TooltipMakerAPI tooltip, StarSystemAPI system) {
        float paddingSmall = 3f;
        float paddingMedium = 5f;
        float paddingLarge = 10f;
        float width = 500f;

        MutableStat capacityStat = AoTDToolboxMisc.getSystemStructureCapacityStat(system);
        AoTDToolboxMisc.NaturalSystemStructureCapacity natural =
                AoTDToolboxMisc.calculateNaturalSystemStructureCapacity(system);
        int used = AoTDToolboxMisc.getUsedSystemStructureSlots(system);
        int capacity = capacityStat.getModifiedInt();

        tooltip.addTitle("System Structure Capacity", Misc.getBasePlayerColor());
        tooltip.addPara(
                "Each star system can support a limited number of major structures, determined by its natural infrastructure potential.",
                paddingMedium
        );
        tooltip.addPara("System Structures: %s / %s", paddingLarge,
                used >= capacity ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor(),
                String.valueOf(used), String.valueOf(capacity));

        // Show only the two natural contributions; preserve all other modifiers.
        MutableStat breakdown = capacityStat.createCopy();
        breakdown.unmodify(AoTDToolboxMisc.NATURAL_SYSTEM_STRUCTURE_CAPACITY_MOD);
        int stableContribution = Math.min(natural.stableLocations, natural.naturalCapacity);
        int remainingNaturalCapacity = natural.naturalCapacity - stableContribution;
        breakdown.modifyFlatAlways("aotd_capacity_stable_locations", stableContribution, "Stable Locations");
        breakdown.modifyFlatAlways("aotd_capacity_natural", remainingNaturalCapacity, "Natural Capacity");

        tooltip.addStatModGrid(
                width,
                50f,
                paddingLarge,
                paddingSmall,
                breakdown,
                new TooltipMakerAPI.StatModValueGetter() {
                    @Override
                    public String getPercentValue(MutableStat.StatMod mod) {
                        String sign = mod.getValue() > 0f ? "+" : "";
                        return sign + Misc.getRoundedValue(mod.getValue()) + "%";
                    }

                    @Override
                    public String getMultValue(MutableStat.StatMod mod) {
                        return "×" + Misc.getRoundedValue(mod.getValue());
                    }

                    @Override
                    public String getFlatValue(MutableStat.StatMod mod) {
                        String sign = mod.getValue() > 0f ? "+" : "";
                        return sign + (int) mod.getValue();
                    }

                    @Override
                    public Color getModColor(MutableStat.StatMod mod) {
                        return mod.getValue() < 0f ? Misc.getNegativeHighlightColor() : null;
                    }
                }
        );
        tooltip.addPara("Available Stable Locations: %s / %s", paddingLarge,
                natural.availableStableLocations <= 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor(),
                String.valueOf(natural.availableStableLocations), String.valueOf(natural.stableLocations));
    }

    @Override public void positionChanged(PositionAPI position) {}
    @Override public void renderBelow(float alphaMult) {}
    @Override public void render(float alphaMult) {}
    @Override
    public void advance(float amount) {
        refresh.advance(amount);
        if (!refresh.intervalElapsed()) return;
        if (displayedUsed != AoTDToolboxMisc.getUsedSystemStructureSlots(system)
                || displayedCapacity != AoTDToolboxMisc.getSystemStructureCapacityStat(system).getModifiedInt()
                || displayedCredits != Global.getSector().getPlayerFleet().getCargo().getCredits().get()) createUI();
    }
    @Override public void processInput(List<InputEventAPI> events) {}
    @Override public void buttonPressed(Object buttonId) {}
}
