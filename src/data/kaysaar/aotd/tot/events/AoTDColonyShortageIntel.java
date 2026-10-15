package data.kaysaar.aotd.tot.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One saved commodity bulletin per month, containing the actual local orders. */
public class AoTDColonyShortageIntel extends AoTDTradeDemandIntel {
    private final LinkedHashMap<String, AoTDTradeDemandOrder> orders = new LinkedHashMap<>();
    private boolean published;

    public AoTDColonyShortageIntel(AoTDTradeDemandEvent.Target target) {
        super(target);
    }

    public void addOrder(AoTDTradeDemandOrder order) {
        if (!order.target.commodityId.equals(getEventData().commodityId)) return;
        orders.putIfAbsent(order.target.marketId, order);
    }

    public Map<String, AoTDTradeDemandOrder> getOrders() {
        return Collections.unmodifiableMap(orders);
    }

    @Override
    public AoTDTradeDemandOrder getOrder() {
        return orders.isEmpty() ? super.getOrder() : orders.values().iterator().next();
    }

    public boolean shouldPublish() {
        if (published || orders.isEmpty()) return false;
        published = true;
        return true;
    }

    @Override
    protected Color getEventColor() {
        return Misc.getButtonTextColor();
    }

    @Override
    protected Color getEventBackgroundColor() {
        return Misc.getDarkPlayerColor();
    }

    @Override
    protected String getEventHeading() {
        return "Colony Shortage Bulletin";
    }

    private boolean hasActiveOrders() {
        for (AoTDTradeDemandOrder order : orders.values()) {
            if (order.status == AoTDTradeDemandOrder.Status.ACTIVE) return true;
        }
        return false;
    }

    private String getDemandSummary() {
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (AoTDTradeDemandOrder order : orders.values()) {
            min = Math.min(min, order.target.percent);
            max = Math.max(max, order.target.percent);
        }
        if (orders.isEmpty()) return "No orders";
        return "surplus + " + (min == max ? min + "%" : min + "–" + max + "%");
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getEventColor(), 0f);
        info.addPara("- Affected colonies: %s", 3f, getEventColor(), String.valueOf(orders.size()));
        info.addPara("- Additional demand: %s", 3f, Color.ORANGE, getDemandSummary());
        info.addPara(hasActiveOrders() ? "- Until next allocation" : "- Buying rush ended", Misc.getGrayColor(), 3f);
    }

    @Override
    protected void createDescription(TooltipMakerAPI tooltip, float width) {
        String illustration = Global.getSettings().getSpriteName("illustrations", "crew_leaving");
        if (width >= 600f) {
            TooltipMakerAPI overview = tooltip.beginImageWithText(illustration, 135f, width, true);
            overview.addPara(AoTDTradeDemandEvent.getNarrative(getEventData()), 0f);
            tooltip.addImageWithText(15f);
        } else {
            float imageWidth = Math.min(width, 480f);
            tooltip.addImage(illustration, imageWidth, imageWidth * 300f / 480f, 15f);
            tooltip.addPara(AoTDTradeDemandEvent.getNarrative(getEventData()), 10f);
        }
        tooltip.addSectionHeading("Unscheduled buying rush", getEventColor(), getEventBackgroundColor(), Alignment.MID, 15f);
        tooltip.addPara("These local orders arrived after monthly cargo had been assigned. Buyers absorb each colony's remaining allocated surplus and seek an additional reserve. Independent deliveries can ease the shortage.", 10f);
        tooltip.addPara("Reserve percentages use the larger of each colony's monthly supply or demand. Orders take priority over local industry and end before the next allocation.", Misc.getGrayColor(), 8f);
        tooltip.addSectionHeading("Affected Colonies", getEventColor(), getEventBackgroundColor(), Alignment.MID, 15f);
        List<AoTDTradeDemandOrder> sorted = new ArrayList<>(orders.values());
        sorted.sort(Comparator.comparing(order -> AoTDTradeDemandEvent.getScopeName(order.target), String.CASE_INSENSITIVE_ORDER));
        for (AoTDTradeDemandOrder order : sorted) {
            tooltip.setParaFontOrbitron();
            tooltip.addPara(AoTDTradeDemandEvent.getScopeName(order.target), getEventColor(), 12f);
            tooltip.setParaFontDefault();
            tooltip.addPara("Additional demand: %s", 5f, Color.ORANGE,
                    "surplus + " + order.target.percent + "% (" + String.format(Locale.US, "%,d", order.additionalRawDemand) + " units)");
            tooltip.addPara("Began: %s", 3f, Misc.getGrayColor(), order.startedOn);
            tooltip.addPara(order.status == AoTDTradeDemandOrder.Status.ACTIVE
                    ? "Until next allocation" : "Buying rush ended", Misc.getGrayColor(), 3f);
        }
        tooltip.addPara("This bulletin records the selected colonies and original order volumes. Later reports for this commodity are recorded separately each month.", Misc.getGrayColor(), 12f);
    }
}
