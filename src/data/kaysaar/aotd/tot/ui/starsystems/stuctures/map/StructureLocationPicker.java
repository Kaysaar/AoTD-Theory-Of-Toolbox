package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.util.vector.Vector2f;

public interface StructureLocationPicker {
    CustomPanelAPI getMainPanel();
    boolean hasSelection();
    // Stable point or orbit focus; a free-space picker may return null.
    SectorEntityToken getSelectedEntity();
    // Campaign coordinates, not map UI coordinates. Null until placement is valid.
    Vector2f getSelectedLocation();
    void createSelectionText(TooltipMakerAPI tooltip);
    boolean consumeSelectionTextUpdate();
    void clearUI();
}
