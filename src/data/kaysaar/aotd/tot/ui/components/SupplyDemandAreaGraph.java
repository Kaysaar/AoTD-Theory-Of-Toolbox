package data.kaysaar.aotd.tot.ui.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Orange "capacity" bar with:
 *  - GREEN surplus drawn ABOVE orange when supply > demand
 *  - RED shortage drawn INSIDE orange when demand > supply (i.e. within the orange area)
 *
 * Orange cap (flat line):
 *   cap = max(demand) over points where demand <= supply
 *   fallback: if never met, cap = max(demand) so pure shortage is visible
 *
 * IMPORTANT:
 *  - The graph ALWAYS uses full height:
 *      highest point of (cap + max(supply-demand,0)) maps to panel top.
 *    This shrinks orange automatically when surplus is huge.
 *
 * AA:
 *  - Green top AA (feather up)
 *  - Green bottom AA at cap (feather down)
 *  - Red bottom AA (feather down into orange)
 *  - Cap edge AA color is chosen by what is below the cap:
 *      red below -> red AA, orange below -> orange AA, green below -> skip (cap hidden)
 *  - AA is disabled only in a tiny window around diff-crossings (diff=0).
 */
public class SupplyDemandAreaGraph implements ExtendedUIPanelPlugin {

    private final CustomPanelAPI mainPanel;

    // panel-local Y samples [0..height] (callers should already scale into this)
    private final ArrayList<Float> supplyY = new ArrayList<>();
    private final ArrayList<Float> demandY = new ArrayList<>();

    private Color greenFill  = new Color(41, 126, 65, 255);
    private Color orangeFill = new Color(178, 130, 34, 255);
    private Color redFill    = new Color(161, 18, 18, 255);

    private float alphaMult = 1f;

    // overlap to eliminate cracks at sign crossings for filled geometry (screen px)
    private float crossingOverlapPx = 1.25f;

    // AA
    private boolean aaEnabled = true;
    private float aaFeatherPx = 1.25f;

    // disable AA ONLY around sign-crossings (diff=0), in screen px
    private float aaCrossCutPx = 1.5f;

    public SupplyDemandAreaGraph(float width, float height,
                                 List<Float> supplySamplesY,
                                 List<Float> demandSamplesY) {
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        setData(supplySamplesY, demandSamplesY);
        createUI();
    }

    @Override public CustomPanelAPI getMainPanel() { return mainPanel; }
    @Override public void createUI() {}
    @Override public void clearUI() {}
    @Override public void positionChanged(PositionAPI position) {}
    @Override public void renderBelow(float alphaMult) {}
    @Override public void advance(float amount) {}
    @Override public void processInput(List<InputEventAPI> events) {}
    @Override public void buttonPressed(Object buttonId) {}

    // ---------------- settings ----------------

    public void setAlphaMult(float alphaMult) { this.alphaMult = alphaMult; }

    /** Green / Orange / Red */
    public void setColors(Color green, Color orange, Color red) {
        if (green != null) this.greenFill = green;
        if (orange != null) this.orangeFill = orange;
        if (red != null) this.redFill = red;
    }

    /** Overlap used ONLY for fill triangles near diff crossings to avoid 1px cracks. */
    public void setCrossingOverlapPx(float px) { this.crossingOverlapPx = Math.max(0f, px); }

    public void setAAEnabled(boolean enabled) { this.aaEnabled = enabled; }
    public void setAAFeatherPx(float px) { this.aaFeatherPx = Math.max(0f, px); }

    /** Disable AA only in this +/- px window around diff=0 crossing X. */
    public void setAACrossCutPx(float px) { this.aaCrossCutPx = Math.max(0f, px); }

    public void setData(List<Float> supplySamplesY, List<Float> demandSamplesY) {
        supplyY.clear();
        demandY.clear();
        if (supplySamplesY != null) supplyY.addAll(supplySamplesY);
        if (demandSamplesY != null) demandY.addAll(demandSamplesY);

        int n = Math.min(supplyY.size(), demandY.size());
        while (supplyY.size() > n) supplyY.remove(supplyY.size() - 1);
        while (demandY.size() > n) demandY.remove(demandY.size() - 1);
    }

    // ---------------- render ----------------

    @Override
    public void render(float uiAlphaMult) {
        int n = Math.min(supplyY.size(), demandY.size());
        if (n < 2) return;

        PositionAPI pos = mainPanel.getPosition();
        float left   = pos.getX();
        float bottom = pos.getY();
        float w      = pos.getWidth();
        float h      = pos.getHeight();
        float top    = bottom + h;

        float step = w / (n - 1f);
        float aMul = uiAlphaMult * this.alphaMult;

        ArrayList<P> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float x = left + step * i;

            // panel-local inputs (0..h) -> convert to world Y
            float s = bottom + clamp(supplyY.get(i), 0f, h);
            float d = bottom + clamp(demandY.get(i), 0f, h);

            pts.add(new P(x, s, d));
        }

        // cap in world Y
        float capY = computeOrangeCapY(bottom, pts);

        // ---- vertical scale so max visible height hits panel top ----
        float capRel = capY - bottom; // 0..h
        float maxPositiveDiffRel = 0f;

        for (P p : pts) {
            float diffRel = (p.s - bottom) - (p.d - bottom);
            if (diffRel > maxPositiveDiffRel) maxPositiveDiffRel = diffRel;
        }

        float maxVisibleRel = capRel + maxPositiveDiffRel;
        if (maxVisibleRel < 1e-3f) maxVisibleRel = 1f;

        float yScale = h / maxVisibleRel;
        float capYScaled = bottom + capRel * yScale;

        // GL base state
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);

        // crisp overwrite for fills
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);

        // 1) Orange bar (baseline -> cap)
        drawRect(bottom, capYScaled, pts.get(0).x, pts.get(pts.size() - 1).x, orangeFill, aMul);

        // 2) Red inside orange where diff<0 (band from cap+diff -> cap)
        drawRedInsideOrangeScaled(bottom, top, capYScaled, yScale, pts, redFill, aMul, crossingOverlapPx);

        // 3) Green above orange where diff>0 (band from cap -> cap+diff)
        drawGreenAboveOrangeScaled(bottom, top, capYScaled, yScale, pts, greenFill, aMul, crossingOverlapPx);

        // AA (with AA removed ONLY at crossings)
        if (aaEnabled && aaFeatherPx > 0f) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // sloped AA
            drawGreenTopEdgeAA_NoCrossScaled(bottom, top, capYScaled, yScale, pts, greenFill, aMul, aaFeatherPx, aaCrossCutPx);
            drawRedBottomEdgeAA_NoCrossScaled(bottom, top, capYScaled, yScale, pts, redFill, aMul, aaFeatherPx, aaCrossCutPx);

            // cap seam AA
            drawGreenBottomEdgeAA_NoCrossScaled(bottom, top, capYScaled, yScale, pts, greenFill, aMul, aaFeatherPx, aaCrossCutPx);

            // IMPORTANT FIX: cap edge AA color depends on what's below (red vs orange). No more orange fringe on red.
            drawCapTopEdgeAA_ByBelow_NoCrossScaled(bottom, top, capYScaled, yScale, pts,
                    orangeFill, redFill, aMul, aaFeatherPx, aaCrossCutPx);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    // =========================
    // Data
    // =========================

    private static class P {
        final float x, s, d;
        P(float x, float s, float d) { this.x = x; this.s = s; this.d = d; }
    }

    private static float computeOrangeCapY(float baseline, List<P> pts) {
        float capMet = baseline;
        float maxDemand = baseline;

        for (P p : pts) {
            if (p.d > maxDemand) maxDemand = p.d;
            if (p.d <= p.s && p.d > capMet) capMet = p.d;
        }

        // If nothing was ever met, use maxDemand so shortage shows.
        return (capMet > baseline) ? capMet : maxDemand;
    }

    // =========================
    // Fills (scaled)
    // =========================

    private static void drawRect(float y0, float y1, float x0, float x1, Color c, float alphaMult) {
        setColor(c, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y0);

        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);

        GL11.glEnd();
    }

    private static void drawRedInsideOrangeScaled(float baseline, float top, float capYScaled, float yScale,
                                                  List<P> pts, Color red, float alphaMult, float overlapPx) {
        setColor(red, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < pts.size() - 1; i++) {
            P a = pts.get(i), b = pts.get(i + 1);

            float diff0 = (a.s - a.d) * yScale;
            float diff1 = (b.s - b.d) * yScale;

            boolean on0 = diff0 < 0f;
            boolean on1 = diff1 < 0f;
            if (!on0 && !on1) continue;

            if (on0 && on1) {
                emitRedBand(baseline, top, capYScaled, a.x, diff0, b.x, diff1);
            } else {
                float t = solveT(diff0, diff1, 0f);
                float xm = lerp(a.x, b.x, t);

                float xL = xm - overlapPx;
                float xR = xm + overlapPx;

                if (on0) emitRedBand(baseline, top, capYScaled, a.x, diff0, xR, 0f);
                if (on1) emitRedBand(baseline, top, capYScaled, xL, 0f, b.x, diff1);
            }
        }

        GL11.glEnd();
    }

    private static void emitRedBand(float baseline, float top, float capY, float x0, float diff0, float x1, float diff1) {
        float yB0 = capY + diff0; // diff negative
        float yB1 = capY + diff1;

        yB0 = clamp(yB0, baseline, top);
        yB1 = clamp(yB1, baseline, top);
        float capC = clamp(capY, baseline, top);

        if (yB0 >= capC && yB1 >= capC) return;

        GL11.glVertex2f(x0, yB0);
        GL11.glVertex2f(x0, capC);
        GL11.glVertex2f(x1, yB1);

        GL11.glVertex2f(x1, yB1);
        GL11.glVertex2f(x0, capC);
        GL11.glVertex2f(x1, capC);
    }

    private static void drawGreenAboveOrangeScaled(float baseline, float top, float capYScaled, float yScale,
                                                   List<P> pts, Color green, float alphaMult, float overlapPx) {
        setColor(green, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < pts.size() - 1; i++) {
            P a = pts.get(i), b = pts.get(i + 1);

            float diff0 = (a.s - a.d) * yScale;
            float diff1 = (b.s - b.d) * yScale;

            boolean on0 = diff0 > 0f;
            boolean on1 = diff1 > 0f;
            if (!on0 && !on1) continue;

            if (on0 && on1) {
                emitGreenBand(baseline, top, capYScaled, a.x, diff0, b.x, diff1);
            } else {
                float t = solveT(diff0, diff1, 0f);
                float xm = lerp(a.x, b.x, t);

                float xL = xm - overlapPx;
                float xR = xm + overlapPx;

                if (on0) emitGreenBand(baseline, top, capYScaled, a.x, diff0, xR, 0f);
                if (on1) emitGreenBand(baseline, top, capYScaled, xL, 0f, b.x, diff1);
            }
        }

        GL11.glEnd();
    }

    private static void emitGreenBand(float baseline, float top, float capY, float x0, float diff0, float x1, float diff1) {
        float yT0 = clamp(capY + diff0, baseline, top);
        float yT1 = clamp(capY + diff1, baseline, top);
        float capC = clamp(capY, baseline, top);

        if (yT0 <= capC && yT1 <= capC) return;

        GL11.glVertex2f(x0, capC);
        GL11.glVertex2f(x0, yT0);
        GL11.glVertex2f(x1, capC);

        GL11.glVertex2f(x1, capC);
        GL11.glVertex2f(x0, yT0);
        GL11.glVertex2f(x1, yT1);
    }

    // =========================
    // AA edges (scaled) with AA "hole" only at crossings
    // =========================

    /** Green TOP edge (feather UP). */
    private static void drawGreenTopEdgeAA_NoCrossScaled(float baseline, float top, float capYScaled, float yScale,
                                                         List<P> pts, Color c, float alphaMult,
                                                         float featherPx, float aaCrossCutPx) {
        float r = c.getRed()/255f, g = c.getGreen()/255f, b = c.getBlue()/255f;
        float a = (c.getAlpha()/255f) * alphaMult;

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < pts.size() - 1; i++) {
            P p0 = pts.get(i), p1 = pts.get(i + 1);

            float diff0 = (p0.s - p0.d) * yScale;
            float diff1 = (p1.s - p1.d) * yScale;

            boolean on0 = diff0 > 0f;
            boolean on1 = diff1 > 0f;
            if (!on0 && !on1) continue;

            float y0 = clamp(capYScaled + diff0, baseline, top);
            float y1 = clamp(capYScaled + diff1, baseline, top);

            if (on0 && on1) {
                aaStripVertical(p0.x, y0, p1.x, y1, +featherPx, r, g, b, a);
                continue;
            }

            float t = solveT(diff0, diff1, 0f);
            float xm = lerp(p0.x, p1.x, t);
            float cutL = xm - aaCrossCutPx;
            float cutR = xm + aaCrossCutPx;

            if (on0) {
                float xEnd = Math.min(p1.x, cutL);
                if (xEnd > p0.x + 0.25f) {
                    float tEnd = (xEnd - p0.x) / (p1.x - p0.x);
                    float yEnd = clamp(capYScaled + lerp(diff0, diff1, tEnd), baseline, top);
                    aaStripVertical(p0.x, y0, xEnd, yEnd, +featherPx, r, g, b, a);
                }
            }
            if (on1) {
                float xStart = Math.max(p0.x, cutR);
                if (p1.x > xStart + 0.25f) {
                    float tStart = (xStart - p0.x) / (p1.x - p0.x);
                    float yStart = clamp(capYScaled + lerp(diff0, diff1, tStart), baseline, top);
                    aaStripVertical(xStart, yStart, p1.x, y1, +featherPx, r, g, b, a);
                }
            }
        }

        GL11.glEnd();
    }

    /** Red BOTTOM edge (feather DOWN into orange). */
    private static void drawRedBottomEdgeAA_NoCrossScaled(float baseline, float top, float capYScaled, float yScale,
                                                          List<P> pts, Color c, float alphaMult,
                                                          float featherPx, float aaCrossCutPx) {
        float r = c.getRed()/255f, g = c.getGreen()/255f, b = c.getBlue()/255f;
        float a = (c.getAlpha()/255f) * alphaMult;

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < pts.size() - 1; i++) {
            P p0 = pts.get(i), p1 = pts.get(i + 1);

            float diff0 = (p0.s - p0.d) * yScale;
            float diff1 = (p1.s - p1.d) * yScale;

            boolean on0 = diff0 < 0f;
            boolean on1 = diff1 < 0f;
            if (!on0 && !on1) continue;

            float y0 = clamp(capYScaled + diff0, baseline, top);
            float y1 = clamp(capYScaled + diff1, baseline, top);

            if (on0 && on1) {
                aaStripVertical(p0.x, y0, p1.x, y1, -featherPx, r, g, b, a);
                continue;
            }

            float t = solveT(diff0, diff1, 0f);
            float xm = lerp(p0.x, p1.x, t);
            float cutL = xm - aaCrossCutPx;
            float cutR = xm + aaCrossCutPx;

            if (on0) {
                float xEnd = Math.min(p1.x, cutL);
                if (xEnd > p0.x + 0.25f) {
                    float tEnd = (xEnd - p0.x) / (p1.x - p0.x);
                    float yEnd = clamp(capYScaled + lerp(diff0, diff1, tEnd), baseline, top);
                    aaStripVertical(p0.x, y0, xEnd, yEnd, -featherPx, r, g, b, a);
                }
            }
            if (on1) {
                float xStart = Math.max(p0.x, cutR);
                if (p1.x > xStart + 0.25f) {
                    float tStart = (xStart - p0.x) / (p1.x - p0.x);
                    float yStart = clamp(capYScaled + lerp(diff0, diff1, tStart), baseline, top);
                    aaStripVertical(xStart, yStart, p1.x, y1, -featherPx, r, g, b, a);
                }
            }
        }

        GL11.glEnd();
    }

    /** Green BOTTOM edge at cap (feather DOWN). */
    private static void drawGreenBottomEdgeAA_NoCrossScaled(float baseline, float top, float capYScaled, float yScale,
                                                            List<P> pts, Color c, float alphaMult,
                                                            float featherPx, float aaCrossCutPx) {
        float r = c.getRed()/255f, g = c.getGreen()/255f, b = c.getBlue()/255f;
        float a = (c.getAlpha()/255f) * alphaMult;

        float capC = clamp(capYScaled, baseline, top);

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < pts.size() - 1; i++) {
            P p0 = pts.get(i), p1 = pts.get(i + 1);

            float diff0 = (p0.s - p0.d) * yScale;
            float diff1 = (p1.s - p1.d) * yScale;

            boolean on0 = diff0 > 0f;
            boolean on1 = diff1 > 0f;
            if (!on0 && !on1) continue;

            if (on0 && on1) {
                aaStripVertical(p0.x, capC, p1.x, capC, -featherPx, r, g, b, a);
                continue;
            }

            float t = solveT(diff0, diff1, 0f);
            float xm = lerp(p0.x, p1.x, t);
            float cutL = xm - aaCrossCutPx;
            float cutR = xm + aaCrossCutPx;

            if (on0) {
                float xEnd = Math.min(p1.x, cutL);
                if (xEnd > p0.x + 0.25f) {
                    aaStripVertical(p0.x, capC, xEnd, capC, -featherPx, r, g, b, a);
                }
            }
            if (on1) {
                float xStart = Math.max(p0.x, cutR);
                if (p1.x > xStart + 0.25f) {
                    aaStripVertical(xStart, capC, p1.x, capC, -featherPx, r, g, b, a);
                }
            }
        }

        GL11.glEnd();
    }

    /**
     * CAP TOP edge AA (feather UP), with color chosen by what is directly below cap:
     *  - diff < 0 -> red is below cap => red AA
     *  - diff >= 0 -> orange is below cap (or equal) => orange AA
     *  - diff > 0 -> green covers cap => skip (cap not visible there)
     *
     * Also cuts out a small window near diff=0 crossings.
     */
    private static void drawCapTopEdgeAA_ByBelow_NoCrossScaled(float baseline, float top,
                                                               float capYScaled, float yScale,
                                                               List<P> pts,
                                                               Color orange, Color red,
                                                               float alphaMult,
                                                               float featherPx, float aaCrossCutPx) {

        float capC = clamp(capYScaled, baseline, top);

        // precompute colors
        float or = orange.getRed()/255f, og = orange.getGreen()/255f, ob = orange.getBlue()/255f;
        float oa = (orange.getAlpha()/255f) * alphaMult;

        float rr = red.getRed()/255f, rg = red.getGreen()/255f, rb = red.getBlue()/255f;
        float ra = (red.getAlpha()/255f) * alphaMult;

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < pts.size() - 1; i++) {
            P p0 = pts.get(i), p1 = pts.get(i + 1);

            float diff0 = (p0.s - p0.d) * yScale;
            float diff1 = (p1.s - p1.d) * yScale;

            boolean green0 = diff0 > 0f;
            boolean green1 = diff1 > 0f;

            // If green covers this whole segment, cap isn't visible
            if (green0 && green1) continue;

            // Determine "below cap" color per endpoint (ignoring green case)
            // red below if diff < 0, else orange below
            int below0 = green0 ? 2 : (diff0 < 0f ? 1 : 0); // 2=green(hidden), 1=red, 0=orange
            int below1 = green1 ? 2 : (diff1 < 0f ? 1 : 0);

            // No transition in below-color and neither is green-hidden => draw whole segment in that color
            if (below0 == below1 && below0 != 2) {
                if (below0 == 1) aaStripVertical(p0.x, capC, p1.x, capC, +featherPx, rr, rg, rb, ra);
                else             aaStripVertical(p0.x, capC, p1.x, capC, +featherPx, or, og, ob, oa);
                continue;
            }

            // Otherwise: there is a crossing at diff=0 between visible types and/or green-hidden.
            // We'll split at diff=0 and apply cut window around the join.
            float t = solveT(diff0, diff1, 0f);
            float xm = lerp(p0.x, p1.x, t);
            float cutL = xm - aaCrossCutPx;
            float cutR = xm + aaCrossCutPx;

            // Left visible portion (if left endpoint isn't green-hidden)
            if (below0 != 2) {
                float xEnd = Math.min(p1.x, cutL);
                if (xEnd > p0.x + 0.25f) {
                    if (below0 == 1) aaStripVertical(p0.x, capC, xEnd, capC, +featherPx, rr, rg, rb, ra);
                    else             aaStripVertical(p0.x, capC, xEnd, capC, +featherPx, or, og, ob, oa);
                }
            }

            // Right visible portion (if right endpoint isn't green-hidden)
            if (below1 != 2) {
                float xStart = Math.max(p0.x, cutR);
                if (p1.x > xStart + 0.25f) {
                    if (below1 == 1) aaStripVertical(xStart, capC, p1.x, capC, +featherPx, rr, rg, rb, ra);
                    else             aaStripVertical(xStart, capC, p1.x, capC, +featherPx, or, og, ob, oa);
                }
            }
        }

        GL11.glEnd();
    }

    // =========================
    // AA primitive: vertical feather strip
    // =========================

    private static void aaStripVertical(float x0, float y0, float x1, float y1,
                                        float dy,
                                        float r, float g, float b, float aInner) {
        float x0o = x0, y0o = y0 + dy;
        float x1o = x1, y1o = y1 + dy;

        GL11.glColor4f(r, g, b, aInner);
        GL11.glVertex2f(x0, y0);
        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(x0o, y0o);
        GL11.glColor4f(r, g, b, aInner);
        GL11.glVertex2f(x1, y1);

        GL11.glColor4f(r, g, b, aInner);
        GL11.glVertex2f(x1, y1);
        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(x0o, y0o);
        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(x1o, y1o);
    }

    // =========================
    // Utils
    // =========================

    private static void setColor(Color c, float alphaMult) {
        float r = c.getRed()/255f;
        float g = c.getGreen()/255f;
        float b = c.getBlue()/255f;
        float a = (c.getAlpha()/255f) * alphaMult;
        GL11.glColor4f(r, g, b, a);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float solveT(float v0, float v1, float target) {
        float denom = (v1 - v0);
        if (Math.abs(denom) < 1e-6f) return 0.5f;
        float t = (target - v0) / denom;
        return clamp(t, 0f, 1f);
    }

    /**
     * Convenience: convert integer values to PANEL-LOCAL Y samples [0..height].
     * highest should be max across BOTH series.
     */
    public static ArrayList<Float> createSeriesForGraph(float height, List<Integer> values, float highest) {
        ArrayList<Float> out = new ArrayList<>();
        if (values == null || values.isEmpty()) return out;

        float denom = Math.max(1f, highest);
        for (Integer v : values) {
            float val = (v == null) ? 0f : v;
            float y = (val / denom) * height;
            out.add(clamp(y, 0f, height));
        }
        return out;
    }
}
