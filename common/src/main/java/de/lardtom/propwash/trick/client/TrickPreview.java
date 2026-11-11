package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.guide.client.render.SceneFrame;
import de.lardtom.propwash.guide.client.render.SceneRenderState;
import de.lardtom.propwash.guide.client.render.SceneView;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.input.client.StickModes;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Vector2ic;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TrickPreview {
    public static final long HOVER_DELAY_MS = 350L;
    public static final float LOOP_PAUSE_TICKS = 12.0F;
    public static final int MIN_WIDTH = 96;
    public static final int MAX_WIDTH = 168;
    private static final int GAP = 3;
    private static final int TOOLTIP_PADDING = 4;
    private static final int TOOLTIP_LINE = 10;
    private static final int TOOLTIP_TITLE_GAP = 2;

    private final SceneView view = new SceneView();
    private @Nullable TrickType trick;
    private long since;
    private long shownSince = -1L;
    private @Nullable ScreenRectangle box;
    private @Nullable ScreenRectangle tooltip;
    private long frames;
    private float lastTime;

    public @Nullable TrickType trick() {
        return this.trick;
    }

    public @Nullable ScreenRectangle box() {
        return this.box;
    }

    public @Nullable ScreenRectangle tooltip() {
        return this.tooltip;
    }

    public long frames() {
        return this.frames;
    }

    public float lastTime() {
        return this.lastTime;
    }

    public boolean visible() {
        return this.box != null;
    }

    public void clear() {
        this.trick = null;
        this.box = null;
        this.tooltip = null;
        this.shownSince = -1L;
    }

    public void hover(@Nullable TrickType type) {
        if (type != this.trick) {
            this.trick = type;
            this.since = Util.getMillis();
            this.shownSince = -1L;
            this.box = null;
            if (type != null) {
                TrickTutorials.prepare(type);
            }
        }
    }

    public void extract(GuiGraphicsExtractor g, Font font, List<FormattedCharSequence> tip, int mouseX, int mouseY, ScreenRectangle row,
                        ScreenRectangle area) {
        this.box = null;
        this.tooltip = null;
        TrickType type = this.trick;
        if (type == null || Util.getMillis() - this.since < HOVER_DELAY_MS) {
            return;
        }
        TrickTutorials.Prepared prepared = TrickTutorials.ready(type);
        if (prepared == null) {
            return;
        }
        int width = Math.clamp(area.width() * 3 / 10, MIN_WIDTH, MAX_WIDTH);
        int height = width * 5 / 8;
        if (width > area.width() || height > area.height()) {
            return;
        }
        ScreenRectangle tooltip = tooltipBounds(font, tip, mouseX, mouseY, g.guiWidth(), g.guiHeight());
        this.tooltip = tooltip;
        ScreenRectangle box = place(tooltip, row, area, width, height);
        this.box = box;
        if (this.shownSince < 0L) {
            this.shownSince = Util.getMillis();
        }
        Scene scene = prepared.tutorial().scene();
        float loop = scene.duration() + LOOP_PAUSE_TICKS;
        float time = Math.min(scene.duration(), ((Util.getMillis() - this.shownSince) / 50.0F) % loop);
        this.lastTime = time;
        SceneState state = SceneEvaluator.evaluate(scene, time, StickModes.current());
        int x0 = box.left();
        int y0 = box.top();
        int x1 = box.right();
        int y1 = box.bottom();
        g.nextStratum();
        ThemeDraw.framedBox(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.withAlpha(Palette.BACKGROUND, 0xF0), Palette.BORDER_STRONG);
        g.fillGradient(x0, y0, x1, y1, Palette.STAGE_TOP, Palette.STAGE_BOTTOM);
        if (!(g instanceof GuiLayerAccess access)) {
            return;
        }
        float base = SceneProjection.basePixelsPerBlock(width, height, scene.viewRadius());
        SceneProjection projection = prepared.framing().projection(state.camera(), state.time(), base, x0, y0, width, height, 1.0F);
        SceneFrame frame = this.view.build(state, projection);
        access.propwash$renderState().addPicturesInPictureState(new SceneRenderState(frame, x0, y0, x1, y1, projection.pixelsPerBlock(),
                access.propwash$scissor()));
        this.frames++;
    }

    static ScreenRectangle tooltipBounds(Font font, List<FormattedCharSequence> tip, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int width = 0;
        for (FormattedCharSequence line : tip) {
            width = Math.max(width, font.width(line));
        }
        int height = tip.size() * TOOLTIP_LINE + (tip.size() > 1 ? TOOLTIP_TITLE_GAP : 0);
        Vector2ic pos = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height);
        return new ScreenRectangle(pos.x() - TOOLTIP_PADDING, pos.y() - TOOLTIP_PADDING, width + 2 * TOOLTIP_PADDING, height + 2 * TOOLTIP_PADDING);
    }

    static ScreenRectangle place(ScreenRectangle tooltip, ScreenRectangle row, ScreenRectangle area, int width, int height) {
        int[][] candidates = {
                {tooltip.left(), tooltip.bottom() + GAP},
                {tooltip.left(), tooltip.top() - GAP - height},
                {tooltip.right() + GAP, tooltip.top()},
                {tooltip.left() - GAP - width, tooltip.top()},
                {area.right() - width, area.bottom() - height},
                {area.left(), area.bottom() - height},
                {area.right() - width, area.top()},
                {area.left(), area.top()}
        };
        ScreenRectangle best = null;
        long bestOverlap = Long.MAX_VALUE;
        for (int[] candidate : candidates) {
            int x = Math.clamp(candidate[0], area.left(), area.right() - width);
            int y = Math.clamp(candidate[1], area.top(), area.bottom() - height);
            ScreenRectangle box = new ScreenRectangle(x, y, width, height);
            long overlap = overlap(box, tooltip) + overlap(box, row);
            if (overlap == 0L) {
                return box;
            }
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                best = box;
            }
        }
        return best;
    }

    private static long overlap(ScreenRectangle a, ScreenRectangle b) {
        long w = Math.min(a.right(), b.right()) - Math.max(a.left(), b.left());
        long h = Math.min(a.bottom(), b.bottom()) - Math.max(a.top(), b.top());
        return w > 0 && h > 0 ? w * h : 0L;
    }
}
