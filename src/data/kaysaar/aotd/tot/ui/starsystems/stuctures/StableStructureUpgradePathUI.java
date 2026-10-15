package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.PopUpUI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureImpl;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureManager;
import data.kaysaar.aotd.tot.stablestructures.AoTDStableStructureSpec;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glVertex2f;

public class StableStructureUpgradePathUI extends PopUpUI {

    public float width, height;

    private final StarSystemAPI systemAPI;
    private final String selectedId;
    private final Consumer<AoTDStableStructureSpec> queueHandler;
    private final AoTDStableStructureImpl selectedImpl;
    private final LinkedHashMap<String, Vector2f> coordinates = new LinkedHashMap<>();
    private final LinkedHashMap<String, StableStructureUpgradeNode> nodes = new LinkedHashMap<>();

    private final float imageWidth = 192f;
    private final float imageHeight = 120f;
    private final float nodeWidth = 200f;
    private final float nodeHeight = 178f;
    private final float columnGap = 20f;
    private final float rowGap = 50f;
    private final float padding = 10f;

    private float contentWidth;
    private float contentHeight;
    private CustomPanelAPI mainPanel;

    public StableStructureUpgradePathUI(AoTDStableStructureSpec selected, StarSystemAPI systemAPI) {
        this(selected, systemAPI, null, null);
    }

    public StableStructureUpgradePathUI(AoTDStableStructureSpec selected, StarSystemAPI systemAPI,
                                        Consumer<AoTDStableStructureSpec> queueHandler) {
        this(selected, systemAPI, null, queueHandler);
    }

    public StableStructureUpgradePathUI(AoTDStableStructureSpec selected, StarSystemAPI systemAPI,
                                        AoTDStableStructureImpl selectedImpl,
                                        Consumer<AoTDStableStructureSpec> queueHandler) {
        this.systemAPI = systemAPI;
        this.selectedId = selected.getId();
        this.selectedImpl = selectedImpl;
        this.queueHandler = queueHandler;
        createLayout();
    }

    private void createLayout() {
        LinkedHashSet<String> component = new LinkedHashSet<>();
        LinkedList<String> pending = new LinkedList<>();
        pending.add(selectedId);

        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!component.add(id)) continue;

            AoTDStableStructureSpec spec = AoTDStableStructureManager.getSpec(id);
            if (spec == null) continue;

            for (String parentId : spec.getDowngrades()) {
                if (AoTDStableStructureManager.getSpec(parentId) != null) {
                    pending.add(parentId);
                }
            }

            for (AoTDStableStructureSpec child : AoTDStableStructureManager.getUpgradeChildren(id)) {
                pending.add(child.getId());
            }
        }

        LinkedHashMap<String, Integer> indegrees = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> depth = new LinkedHashMap<>();
        LinkedList<String> ready = new LinkedList<>();

        for (String id : component) {
            AoTDStableStructureSpec spec = AoTDStableStructureManager.getSpec(id);
            if (spec == null) continue;

            int parents = 0;
            for (String parentId : spec.getDowngrades()) {
                if (component.contains(parentId)) parents++;
            }

            indegrees.put(id, parents);
            if (parents == 0) {
                depth.put(id, 0);
                ready.add(id);
            }
        }

        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            int nextDepth = depth.get(id) + 1;

            for (AoTDStableStructureSpec child : AoTDStableStructureManager.getUpgradeChildren(id)) {
                String childId = child.getId();
                if (!indegrees.containsKey(childId)) continue;

                depth.put(childId, Math.max(depth.getOrDefault(childId, 0), nextDepth));
                int remaining = indegrees.get(childId) - 1;
                indegrees.put(childId, remaining);
                if (remaining == 0) ready.add(childId);
            }
        }

        int lastDepth = 0;
        for (Integer value : depth.values()) {
            lastDepth = Math.max(lastDepth, value);
        }

        for (String id : component) {
            if (!depth.containsKey(id)) depth.put(id, ++lastDepth);
        }

        LinkedHashMap<Integer, ArrayList<String>> levels = new LinkedHashMap<>();
        int largestLevel = 1;

        for (String id : component) {
            int level = depth.get(id);
            levels.computeIfAbsent(level, key -> new ArrayList<>()).add(id);
        }

        for (ArrayList<String> level : levels.values()) {
            largestLevel = Math.max(largestLevel, level.size());
        }

        float screenWidth = Math.max(nodeWidth + padding * 2f, Global.getSettings().getScreenWidth() - 80f);
        int columnsOnScreen = Math.max(1, (int) ((screenWidth - padding * 2f + columnGap) / (nodeWidth + columnGap)));
        int columns = Math.min(largestLevel, columnsOnScreen);

        contentWidth = columns * nodeWidth + (columns - 1) * columnGap;
        width = Math.min(screenWidth, contentWidth + padding * 2f);

        int visualRow = 0;
        for (int level = 0; level <= lastDepth; level++) {
            ArrayList<String> ids = levels.get(level);
            if (ids == null) continue;

            for (int start = 0; start < ids.size(); start += columns) {
                int size = Math.min(columns, ids.size() - start);
                float rowWidth = size * nodeWidth + (size - 1) * columnGap;
                float startX = (width - rowWidth) / 2f;

                for (int column = 0; column < size; column++) {
                    coordinates.put(ids.get(start + column), new Vector2f(
                            startX + column * (nodeWidth + columnGap),
                            padding + visualRow * (nodeHeight + rowGap)
                    ));
                }
                visualRow++;
            }
        }

        contentHeight = padding * 2f + visualRow * nodeHeight + Math.max(0, visualRow - 1) * rowGap;
        height = Math.min(contentHeight, Math.max(nodeHeight + padding * 2f,
                Global.getSettings().getScreenHeight() - 80f));
    }

    public void showLeftOf(ButtonAPI button) {
        float popupHeight = createUIMockup(Global.getSettings().createCustom(width, height, null));
        CustomPanelAPI panel = Global.getSettings().createCustom(width, popupHeight, this);

        float x = Math.max(0f, button.getPosition().getX() - width);
        float y = button.getPosition().getY() + button.getPosition().getHeight();

        if (y - popupHeight <= 0f) y = popupHeight;
        if (y > Global.getSettings().getScreenHeight()) y = Global.getSettings().getScreenHeight() - 10f;

        init(panel, x, y, false);
    }

    @Override
    public float createUIMockup(CustomPanelAPI panelAPI) {
        return height;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        nodes.clear();

        mainPanel = panelAPI.createCustomPanel(width, height, null);
        TooltipMakerAPI tooltip = mainPanel.createUIElement(width, height, contentHeight > height);

        for (Map.Entry<String, Vector2f> entry : coordinates.entrySet()) {
            String structureId = entry.getKey();
            AoTDStableStructureSpec spec = AoTDStableStructureManager.getSpec(structureId);
            if (spec == null) continue;

            AoTDStableStructureImpl impl = getImplementation(spec);
            StableStructureUpgradeNode node = new StableStructureUpgradeNode(
                    spec, impl, nodeWidth, nodeHeight, imageWidth, imageHeight
            );
            Vector2f position = entry.getValue();

            tooltip.addCustom(node.getMainPanel(), 0f).getPosition().inTL(position.x, position.y);
            tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
                @Override
                public boolean isTooltipExpandable(Object tooltipParam) {
                    return false;
                }

                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 425f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI hoverTooltip, boolean expanded, Object tooltipParam) {
                    impl.createTooltip(hoverTooltip,
                            AoTDStableStructureImpl.AoTDStableStructureTooltipMode.UPGRADE_MENU);
                }
            }, TooltipMakerAPI.TooltipLocation.RIGHT, false);

            nodes.put(structureId, node);
        }

        tooltip.setHeightSoFar(contentHeight);
        mainPanel.addUIElement(tooltip).inTL(0f, 0f);
        panelAPI.addComponent(mainPanel).inTL(0f, 0f);
    }

    private AoTDStableStructureImpl getImplementation(AoTDStableStructureSpec spec) {

        return selectedImpl != null && spec.getId().equals(selectedId)
                ? selectedImpl
                : spec.getImplClassInstance();
    }

    @Override
    public void renderBelow(float alphaMult) {
        super.renderBelow(alphaMult);
        if (mainPanel == null || nodes.isEmpty() || !reachedMaxHeight || pressedConfirmCancel) return;

        Color color = systemAPI != null
                ? AshMisc.getClaimingFaction(systemAPI.getCenter()).getGridUIColor()
                : Misc.getBasePlayerColor();

        AshMisc.startStencil(mainPanel, 1f);
        glPushAttrib(GL_ENABLE_BIT | GL_LINE_BIT | GL_CURRENT_BIT | GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(1f);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f * alphaMult * 0.75f);

        for (StableStructureUpgradeNode node : nodes.values()) {
            for (String parentId : node.getSpec().getDowngrades()) {
                StableStructureUpgradeNode parent = nodes.get(parentId);
                if (parent != null) {
                    drawConnection(parent.getImagePanel().getPosition(), node.getImagePanel().getPosition());
                }
            }
        }

        glPopAttrib();
        AshMisc.endStencil();
    }

    private void drawConnection(PositionAPI parent, PositionAPI child) {
        float fromX = parent.getCenterX();
        float fromY = parent.getCenterY() - parent.getHeight() / 2f;
        float toX = child.getCenterX();
        float toY = child.getCenterY() + child.getHeight() / 2f;
        float midY = (fromY + toY) / 2f;

        glBegin(GL_LINE_STRIP);
        glVertex2f(fromX, fromY);
        glVertex2f(fromX, midY);
        glVertex2f(toX, midY);
        glVertex2f(toX, toY);
        glEnd();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (!reachedMaxHeight || pressedConfirmCancel) return;

        for (StableStructureUpgradeNode node : nodes.values()) {
            ButtonAPI button = node.getQueueButton();
            if (button == null || !button.isChecked()) continue;

            button.setChecked(false);
            if (queueHandler != null) {
                queueHandler.accept(node.getSpec());
            }
            forceDismiss();
            return;
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (!reachedMaxHeight || pressedConfirmCancel) {
            super.processInput(events);
            return;
        }

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (!event.isMouseEvent() && event.isKeyDownEvent()
                    && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                pressedConfirmCancel = true;
                event.consume();
                continue;
            }

            if (event.isMouseDownEvent() && panelToInfluence != null) {
                PositionAPI position = panelToInfluence.getPosition();
                float mouseX = Global.getSettings().getMouseX();
                float mouseY = Global.getSettings().getMouseY();

                if (mouseX < position.getX() || mouseX > position.getX() + position.getWidth()
                        || mouseY < position.getY() || mouseY > position.getY() + position.getHeight()) {
                    pressedConfirmCancel = true;
                    event.consume();
                }
            }
        }
    }

    @Override
    public void onExit() {
        nodes.clear();
        mainPanel = null;
        super.onExit();
    }
}
