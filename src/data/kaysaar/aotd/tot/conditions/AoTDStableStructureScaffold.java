package data.kaysaar.aotd.tot.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpecManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class AoTDStableStructureScaffold extends BaseCampaignObjectivePlugin {
    AoTDStableStructureImpl implToConstruct;
    CampaignEngineLayers renderLayer = CampaignEngineLayers.STATIONS;
    transient SpriteAPI scaffoldSprite, entityToRenderSprite;
    transient String spriteName;
    float scanlineOffset;

    public static Color hologramColor = new Color(40, 225, 255);
    public static float scaffoldScale = 1.15f;

    public void setImplToConstruct(AoTDStableStructureImpl implToConstruct) {
        this.implToConstruct = implToConstruct;
    }

    public AoTDStableStructureImpl getImplToConstruct() {
        return implToConstruct;
    }

    public void setRenderLayer(CampaignEngineLayers renderLayer) {
        this.renderLayer = renderLayer;
    }

    public CampaignEngineLayers getRenderLayer() {
        return renderLayer == null ? CampaignEngineLayers.STATIONS : renderLayer;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (amount > 0f && !Global.getSector().isPaused()) {
            scanlineOffset = (scanlineOffset + amount * 0.5f) % 1f;
        }
    }

    @Override
    public float getRenderRange() {
        AoTDStableStructureImpl impl = getImplToConstruct();
        if (impl == null) return super.getRenderRange();
        CustomEntitySpecAPI spec = impl.getSpecEntity();
        return Math.max(super.getRenderRange(),
                (float) Math.hypot(spec.getSpriteWidth(), spec.getSpriteHeight()) * scaffoldScale);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (layer != getRenderLayer() || entity == null) return;
        AoTDStableStructureImpl impl = getImplToConstruct();
        if (impl == null) return;

        CustomEntitySpecAPI spec = impl.getSpecEntity();
        String sprite = spec.getSpriteName();
        float width = spec.getSpriteWidth();
        float height = spec.getSpriteHeight();
        if (sprite == null || sprite.isEmpty() || width <= 0f || height <= 0f) return;

        if (entityToRenderSprite == null || !sprite.equals(spriteName)) {
            entityToRenderSprite = Global.getSettings().getSprite(sprite);
            spriteName = sprite;
        }
        boolean building = impl.isBuilding();
        if (building && scaffoldSprite == null) {
            scaffoldSprite = Global.getSettings().getSprite("entity_fake", "aotd_scaffold");
        }

        float progress = building && !impl.isUpgrading() ? impl.getBuildOrUpgradeProgress() : 1f;
        progress = Math.max(0f, Math.min(1f, progress));
        float alpha = viewport.getAlphaMult();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TRANSFORM_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(entity.getLocation().x, entity.getLocation().y, 0f);
            GL11.glRotatef(entity.getFacing() - 90f, 0f, 0f, 1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

            // Local bottom-to-top slices: construction rotates together with the entity.
            renderPart(entityToRenderSprite, width, height, 0f, progress, Color.WHITE, alpha);
            if (progress < 1f) {
                renderPart(entityToRenderSprite, width, height, progress, 1f, hologramColor, alpha * 0.45f);
            }

            if (building) {
                float scale = Math.max(width / scaffoldSprite.getWidth(), height / scaffoldSprite.getHeight()) * scaffoldScale;
                renderPart(scaffoldSprite, scaffoldSprite.getWidth() * scale, scaffoldSprite.getHeight() * scale,
                        0f, 1f, Color.WHITE, alpha);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    protected void renderPart(SpriteAPI sprite, float width, float height, float bottom, float top, Color color, float alpha) {
        if (top <= bottom) return;
        sprite.bindTexture();
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alpha * color.getAlpha() / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        addPart(sprite, width, height, bottom, top);
        GL11.glEnd();
    }

    protected void addPart(SpriteAPI sprite, float width, float height, float bottom, float top) {
        float left = -width / 2f;
        float right = width / 2f;
        float lower = (bottom - 0.5f) * height;
        float upper = (top - 0.5f) * height;
        float textureWidth = sprite.getTextureWidth();
        float textureHeight = sprite.getTextureHeight();

        GL11.glTexCoord2f(0f, bottom * textureHeight);
        GL11.glVertex2f(left, lower);
        GL11.glTexCoord2f(textureWidth, bottom * textureHeight);
        GL11.glVertex2f(right, lower);
        GL11.glTexCoord2f(textureWidth, top * textureHeight);
        GL11.glVertex2f(right, upper);
        GL11.glTexCoord2f(0f, top * textureHeight);
        GL11.glVertex2f(left, upper);
    }
}
