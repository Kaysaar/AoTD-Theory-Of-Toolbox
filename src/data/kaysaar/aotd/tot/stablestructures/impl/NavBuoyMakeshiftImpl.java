package data.kaysaar.aotd.tot.stablestructures.impl;

import com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin;

public class NavBuoyMakeshiftImpl extends NavBuoyImpl {

    @Override
    protected float getBurnBonus() {
        return NavBuoyEntityPlugin.NAV_BONUS_MAKESHIFT;
    }
}
