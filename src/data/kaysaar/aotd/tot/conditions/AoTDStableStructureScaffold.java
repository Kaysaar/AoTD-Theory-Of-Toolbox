package data.kaysaar.aotd.tot.conditions;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;

public class AoTDStableStructureScaffold extends BaseCampaignObjectivePlugin {
    AoTDStableStructureImpl implToConstruct;

    public void setImplToConstruct(AoTDStableStructureImpl implToConstruct) {
        this.implToConstruct = implToConstruct;
    }

    public AoTDStableStructureImpl getImplToConstruct() {
        return implToConstruct;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);
    }
}
