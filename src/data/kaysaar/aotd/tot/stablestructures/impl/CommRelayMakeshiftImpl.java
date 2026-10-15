package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;

public class CommRelayMakeshiftImpl extends CommRelayImpl {

    @Override
    protected float getStabilityBonus() {
        return CommRelayCondition.MAKESHIFT_COMM_RELAY_BONUS;
    }
}
