package data.kaysaar.aotd.tot.entities;

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
import java.util.ArrayList;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class AoTDStableStructureScaffold extends BaseCampaignObjectivePlugin {
    public static final String INDEPENDENT_SCAFFOLD = "aotd_independent_scaffold";
    public static final String SCAFFOLD_ENTITY_SPEC = "$aotd_scaffold_entity_spec";
    public static final String CONSTRUCTION_CONTROLLER = "$aotd_scaffold_controller";
    AoTDStableStructureImpl implToConstruct;
    CampaignEngineLayers renderLayer = CampaignEngineLayers.STATIONS;
    transient SpriteAPI scaffoldSprite, movingSprite, entityToRenderSprite;
    transient String spriteName;
    float movement;
    IntervalUtil sparksInterval = new IntervalUtil(0.25f, 0.7f);
    transient ArrayList<float[]> sparks;
    public static float movingAspect = 40f / 256f;

    public static Color hologramColor = new Color(40, 225, 255);
    public static float scaffoldScale = 1.25f;

    public void setImplToConstruct(AoTDStableStructureImpl implToConstruct) {
        this.implToConstruct = implToConstruct;
    }

    public void setIndependentConstruction(AoTDStableStructureImpl impl, String entitySpecId) {
        setImplToConstruct(impl);
        entity.addTag(INDEPENDENT_SCAFFOLD);
        entity.getMemoryWithoutUpdate().set(SCAFFOLD_ENTITY_SPEC, entitySpecId);
        entity.getMemoryWithoutUpdate().set(CONSTRUCTION_CONTROLLER, impl.currentlyAssignedToken);
    }

    public boolean isIndependent() {
        return entity != null && entity.hasTag(INDEPENDENT_SCAFFOLD);
    }

    public AoTDStableStructureImpl getImplToConstruct() {
        SectorEntityToken token = entity == null ? null
                : entity.getMemoryWithoutUpdate().getEntity(CONSTRUCTION_CONTROLLER);
        if (token == null) token = implToConstruct == null ? entity : implToConstruct.currentlyAssignedToken;
        if (token != null) {
            Object current = token.getMemoryWithoutUpdate().get(AoTDStableStructureSpecManager.memKeyInEntities);
            if (current instanceof AoTDStableStructureImpl impl) implToConstruct = impl;
        }
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
        if (amount <= 0f || Global.getSector().isPaused()) return;
        AoTDStableStructureImpl impl = getImplToConstruct();
        if (impl == null || !impl.isCurrentImplementation() || !impl.isBuilding()) return;
        // Only regular upgrade scaffolds follow the original entity.
        if (entity != impl.currentlyAssignedToken && !isIndependent()) {
            entity.setLocation(impl.currentlyAssignedToken.getLocation().x, impl.currentlyAssignedToken.getLocation().y);
            entity.setFacing(impl.currentlyAssignedToken.getFacing());
        }
        if (sparks == null) sparks = new ArrayList<>();
        for (int i = sparks.size()-1; i >= 0; i--) {
            float[] spark = sparks.get(i);
            spark[4] -= amount;
            if (spark[4] <= 0f) sparks.remove(i);
            else {
                spark[0] += spark[2] * amount;
                spark[1] += spark[3] * amount;
            }
        }
        if (impl.isDisrupted()) return;
        movement = (movement + amount / 8f) % 1f;
        if (!entity.isInCurrentLocation()) {
            sparks.clear();
            return;
        }
        if (sparksInterval == null) sparksInterval = new IntervalUtil(0.25f, 0.7f);
        sparksInterval.advance(amount);
        if (sparksInterval.intervalElapsed() && sparks.size() < 24) {
            CustomEntitySpecAPI spec = getConstructionSpec(impl);
            float width = getFrameWidth(spec);
            float height = getFrameHeight(spec);
            float x = ((float)Math.random() - 0.5f) * width * 0.75f;
            float y = getGantryY(width, height);
            for (int i = 0; i < 3; i++) {
                float life = 0.2f + (float)Math.random() * 0.25f;
                sparks.add(new float[]{x, y, ((float)Math.random()-0.5f)*width*0.3f,
                        ((float)Math.random()-0.5f)*height*0.3f, life, life});
            }
        }
    }

    protected CustomEntitySpecAPI getConstructionSpec(AoTDStableStructureImpl impl) {
        String specId = entity.getMemoryWithoutUpdate().getString(SCAFFOLD_ENTITY_SPEC);
        if (specId != null && !specId.isEmpty()) return Global.getSettings().getCustomEntitySpec(specId);
        return impl.getConstructionTargetId() == null ? impl.getSpecEntity()
                : Global.getSettings().getCustomEntitySpec(impl.getConstructionTargetId());
    }

    protected float getFrameWidth(CustomEntitySpecAPI spec) {
        return Math.max(64f, spec.getSpriteWidth() * scaffoldScale);
    }

    protected float getFrameHeight(CustomEntitySpecAPI spec) {
        return Math.max(64f, Math.max(spec.getSpriteHeight() * scaffoldScale, getFrameWidth(spec) * movingAspect * 2.5f));
    }

    protected float getGantryY(float width, float height) {
        float travel = Math.max(0f, (height - width * movingAspect) / 2f - height * 0.06f);
        return (float)Math.sin(movement * Math.PI * 2f) * travel;
    }

    @Override
    public float getRenderRange() {
        AoTDStableStructureImpl impl = getImplToConstruct();
        if (impl == null) return super.getRenderRange();
        CustomEntitySpecAPI spec = getConstructionSpec(impl);
        return Math.max(super.getRenderRange(),
                (float) Math.hypot(getFrameWidth(spec), getFrameHeight(spec)) * 0.6f);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (layer != getRenderLayer() || entity == null) return;
        AoTDStableStructureImpl impl = getImplToConstruct();
        if (impl == null || !impl.isCurrentImplementation()) return;

        CustomEntitySpecAPI spec = getConstructionSpec(impl);
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
            scaffoldSprite = Global.getSettings().getSprite("aotd_entities", "aotd_scaffold_base");
        }

        if (building && movingSprite == null) {
            movingSprite = Global.getSettings().getSprite("aotd_entities", "aotd_scaffold_movable");
        }
        float progress = building && (!impl.isUpgrading() || isIndependent())
                ? impl.getBuildOrUpgradeProgress() : 1f;
        progress = Math.max(0f, Math.min(1f, progress));
        float alpha = viewport.getAlphaMult() * entity.getSensorFaderBrightness();

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

            // The real entity already draws its complete sprite during upgrades.
            if (entity == impl.currentlyAssignedToken || isIndependent()) {
                renderPart(entityToRenderSprite, width, height, 0f, progress, Color.WHITE, alpha);
                if (progress < 1f) {
                    renderPart(entityToRenderSprite, width, height, progress, 1f, hologramColor, alpha * 0.55f);
                }
            }
            if (building) {
                float frameWidth = getFrameWidth(spec);
                float frameHeight = getFrameHeight(spec);
                renderPart(scaffoldSprite, frameWidth, frameHeight, 0f, 1f, Color.WHITE, alpha);
                GL11.glPushMatrix();
                GL11.glTranslatef(0f, getGantryY(frameWidth, frameHeight), 0f);
                renderPart(movingSprite, frameWidth, frameWidth * movingAspect, 0f, 1f, Color.WHITE, alpha);
                GL11.glPopMatrix();
                renderSparks(alpha);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    protected void renderSparks(float alpha) {
        if (sparks == null || sparks.isEmpty()) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glBegin(GL11.GL_LINES);
        for (float[] spark : sparks) {
            GL11.glColor4f(1f, 0.8f, 0.35f, alpha * spark[4] / spark[5]);
            GL11.glVertex2f(spark[0], spark[1]);
            GL11.glVertex2f(spark[0] - spark[2] * 0.05f, spark[1] - spark[3] * 0.05f);
        }
        GL11.glEnd();
    }

    @Override
    public boolean hasCustomMapTooltip() {
        return true;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        AoTDStableStructureImpl impl = getImplToConstruct();
        if (impl != null) impl.createTooltip(tooltip, AoTDStableStructureImpl.AoTDStableStructureTooltipMode.PANEL);
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
