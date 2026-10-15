package data.kaysaar.aotd.tot.events;

import java.util.LinkedHashMap;

/** Saved state of one occurrence, shared with its intel; never the reusable event. */
public class AoTDTradeDemandOrder {
    public enum Status {
        PENDING, ACTIVE, EXPIRED, CANCELLED
    }

    public final AoTDTradeDemandEvent.Target target;
    public Status status = Status.PENDING;
    public int monthsRemaining;
    public String startedOn;
    public long additionalRawDemand;
    // Market ID -> name when orders began. Null means the destinations were not recorded yet.
    public LinkedHashMap<String, String> affectedMarkets;

    public AoTDTradeDemandOrder(AoTDTradeDemandEvent.Target target) {
        this.target = new AoTDTradeDemandEvent.Target(target);
        monthsRemaining = target.durationMonths;
    }
}
