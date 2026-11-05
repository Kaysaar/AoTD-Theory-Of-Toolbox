package data.kaysaar.aotd.tot.stablestructures;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.widget.StableStructureWidget;

import java.awt.Color;
import java.util.List;

public interface StableStructureOptionProvider {
    //Yes this is very much copy of IndustryOptionProvider
     class StructureOptionData {
        public String text;
        public Color color;
        public boolean enabled = true;
        public Object id;
        public AoTDStableStructureImpl structure;
        public StableStructureOptionProvider provider;

        public StructureOptionData(String text, Object id, AoTDStableStructureImpl structure, StableStructureOptionProvider provider) {
            this.text = text;
            this.id = id;
            this.structure = structure;
            this.provider = provider;
            FactionAPI faction = structure != null && structure.getCurrentlyTiedEntity() != null
                    ? structure.getCurrentlyTiedEntity().getFaction() : null;
            if (faction != null) color = faction.getBaseUIColor();
        }
    }

    List<StructureOptionData> getStructureOptions(AoTDStableStructureImpl structure);

    void optionSelected(StructureOptionData opt, StableStructureWidget widget);

    void createTooltip(StructureOptionData opt, TooltipMakerAPI tooltip, float width);
}
