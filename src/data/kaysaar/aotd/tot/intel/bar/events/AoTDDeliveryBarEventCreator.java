package data.kaysaar.aotd.tot.intel.bar.events;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEventCreator;

public class AoTDDeliveryBarEventCreator extends DeliveryBarEventCreator {
    @Override
    public PortsideBarEvent createBarEvent() {
        return new AoTDDeliveryBarEvent();
    }
}
