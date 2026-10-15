package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.impl.campaign.SensorArrayEntityPlugin;

public class SensorArrayMakeshiftImpl extends SensorArrayImpl {

    @Override
    protected float getSensorRangeBonus() {
        return SensorArrayEntityPlugin.SENSOR_BONUS_MAKESHIFT;
    }
}
