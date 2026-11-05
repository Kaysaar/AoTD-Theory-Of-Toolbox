package data.kaysaar.aotd.tot.ui.starsystems.stuctures.map;

import ashlib.data.plugins.ui.models.resizable.ResizableComponent;
import ashlib.data.plugins.ui.models.resizable.map.MapEntityComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class PlanetConstructionIndicatorComponent extends ResizableComponent {
    ChoosePlanetForBuildingMap map;
    float pulse;

    public PlanetConstructionIndicatorComponent(ChoosePlanetForBuildingMap map) {
        this.map = map;
        componentPanel = Global.getSettings().createCustom(1f, 1f, this);
    }

    public float getHalfSize(MapEntityComponent component) {
        return Math.max(26f, Math.min(72f, component.getToken().getRadius() * component.scale + 12f));
    }

    public MapEntityComponent getPlanetAt(float mouseX, float mouseY) {
        MapEntityComponent closest = null;
        float closestDistance = Float.MAX_VALUE;

        // The list is validated when the picker is opened. Only check the
        // chosen planet again, since the market may have changed meanwhile.
        for (MapEntityComponent component : map.eligiblePlanets) {
            PositionAPI pos = component.getComponentPanel().getPosition();
            float x = pos.getCenterX();
            float y = pos.getCenterY();
            float half = getHalfSize(component);
            float dx = mouseX - x;
            float dy = mouseY - y;
            if (Math.abs(dx) > half || Math.abs(dy) > half) continue;

            float distance = dx * dx + dy * dy;
            if (distance >= closestDistance) continue;
            closest = component;
            closestDistance = distance;
        }
        return closest;
    }

    @Override
    public void advance(float amount) {
        pulse = (pulse + amount * 1.5f) % (float) (Math.PI * 2f);
    }

    @Override
    public void render(float alphaMult) {
        if (map.eligiblePlanets.isEmpty()) return;
        MapEntityComponent selected = map.selectedPlanet;
        float brightness = 0.85f + (float) Math.sin(pulse) * 0.1f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_HINT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // All unselected frames in one OpenGL batch, regardless of planet count.
            Color color = Misc.getBasePlayerColor();
            GL11.glLineWidth(1.5f);
            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                    color.getBlue() / 255f, brightness * alphaMult * color.getAlpha() / 255f);
            GL11.glBegin(GL11.GL_LINES);
            for (MapEntityComponent component : map.eligiblePlanets) {
                if (component == selected) continue;
                drawCorners(component);
            }
            GL11.glEnd();

            if (selected != null) {
                color = Misc.getHighlightColor();
                GL11.glLineWidth(2.2f);
                GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                        color.getBlue() / 255f, alphaMult * color.getAlpha() / 255f);
                GL11.glBegin(GL11.GL_LINES);
                drawCorners(selected);
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    protected void drawCorners(MapEntityComponent component) {
        PositionAPI pos = component.getComponentPanel().getPosition();
        float x = pos.getCenterX();
        float y = pos.getCenterY();
        float half = getHalfSize(component);
        float corner = Math.min(16f, half * 0.42f);

        float left = x - half;
        float right = x + half;
        float top = y - half;
        float bottom = y + half;

        GL11.glVertex2f(left, top + corner);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left + corner, top);

        GL11.glVertex2f(right - corner, top);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(right, top + corner);

        GL11.glVertex2f(left, bottom - corner);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(left + corner, bottom);

        GL11.glVertex2f(right - corner, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, bottom - corner);
    }
}
