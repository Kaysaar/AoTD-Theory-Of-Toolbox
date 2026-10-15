package data.kaysaar.aotd.tot.events;

import ashlib.data.plugins.events.AshlibBaseEvent;
import com.fs.starfarer.api.Global;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

public class BaseTradeEvent extends AshlibBaseEvent {
    public BaseTradeEvent(String uniqueEventTypeID) {
        super(uniqueEventTypeID);
    }

    @Override
    public String getImageName() {
        return Global.getSettings().getSpriteName("illustrations", "crew_leaving");
    }

    @Override
    public float getImageSourceWidth() {
        return 480f;
    }

    @Override
    public float getImageSourceHeight() {
        return 300f;
    }

    @Override
    public float getImageDisplayWidth() {
        return 480f;
    }

    @Override
    public float getImageDisplayHeight() {
        return 150f;
    }

    @Override
    public float getImageCropX() {
        return 0f;
    }

    @Override
    public float getImageCropY() {
        return 75f;
    }

    @Override
    public float getImageCropWidth() {
        return 480f;
    }

    @Override
    public float getImageCropHeight() {
        return 150f;
    }

    protected boolean hasSectorDemand(String commodityId) {
        return AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied().contains(commodityId);
    }
}
