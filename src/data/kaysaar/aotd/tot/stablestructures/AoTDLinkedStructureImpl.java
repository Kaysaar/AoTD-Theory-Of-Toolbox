package data.kaysaar.aotd.tot.stablestructures;

import ashlib.data.plugins.ui.models.resizable.map.CustomEntityIconComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.entities.AoTDStableStructureScaffold;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;

public abstract class AoTDLinkedStructureImpl extends AoTDStableStructureImpl {
    public static final String LINKED_ENTITY = "aotd_linked_structure_entity";

    protected ArrayList<SectorEntityToken> linkedEntities = new ArrayList<>();

    public static class LinkedEntityData {
        public String entityId;
        public String name;
        public float offset;

        public LinkedEntityData(String entityId, String name, float offset) {
            this.entityId = entityId;
            this.name = name;
            this.offset = offset;
        }
    }

    public abstract boolean canPlaceAt(StarSystemAPI system, SectorEntityToken focus, Vector2f position);

    public abstract void setConstructionFocus(SectorEntityToken focus);

    public abstract ArrayList<LinkedEntityData> getLinkedEntityData();

    public abstract void placeLinkedEntity(SectorEntityToken token, LinkedEntityData data);

    public void onConstructionStarted() {
        createLinkedEntities(true);
    }

    protected void createLinkedEntities(boolean scaffolds) {
        removeLinkedEntities();
        for (LinkedEntityData data : getLinkedEntityData()) {
            CustomCampaignEntityAPI entity = currentlyAssignedToken.getContainingLocation().addCustomEntity(
                    Misc.genUID(), scaffolds ? null : data.name,
                    scaffolds ? AoTDStableStructureConstruction.OVERLAY : data.entityId,
                    currentlyAssignedToken.getFaction().getId());
            entity.clearTags();
            if (scaffolds) {
                entity.addTag(CustomEntityIconComponent.SHOW_ONLY_ICON_ON_MAP);
                ((AoTDStableStructureScaffold) entity.getCustomPlugin()).setIndependentConstruction(this, data.entityId);
                CustomEntitySpecAPI spec = Global.getSettings().getCustomEntitySpec(data.entityId);
                entity.setRadius(Math.max(spec.getDefaultRadius(),
                        (float) Math.hypot(spec.getSpriteWidth(), spec.getSpriteHeight()) * 0.6f));
            } else {
                entity.addTag(LINKED_ENTITY);
            }
            placeLinkedEntity(entity, data);
            linkedEntities.add(entity);
        }
    }

    public void removeLinkedEntities() {
        if (linkedEntities == null) linkedEntities = new ArrayList<>();
        for (SectorEntityToken token : linkedEntities) {
            if (token == null || token.isExpired()) continue;
            if (token.getContainingLocation() != null) token.getContainingLocation().removeEntity(token);
            token.setExpired(true);
        }
        linkedEntities.clear();
    }

    @Override
    protected void buildingFinished() {
        createLinkedEntities(false);
    }

    @Override
    public void cancelBuilding() {
        removeLinkedEntities();
        super.cancelBuilding();
    }

    @Override
    public void notifyRemoved() {
        removeLinkedEntities();
        super.notifyRemoved();
    }
}
