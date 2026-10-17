package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.LineBatch;
import de.lardtom.propwash.video.record.path.PathPreview;
import de.lardtom.propwash.video.record.path.Simplification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class PathPreviewScreen extends Screen implements ThemedScreen {
    private static final String KEY = SimplificationText.PREVIEW;
    private static final String TAB = "propwash.video.record.tab.";
    private static final int MAX_WIDTH = 680;
    private static final int MAX_HEIGHT = 460;
    private static final int GRAPH_HEIGHT = 58;
    private static final int MIN_VIEW = 24;
    private static final int MIN_GRAPH = 26;
    private static final int TEXT_LINES = 4;
    private static final int LEGEND_ROOM = 14;
    private static final int SIDE_PANEL_MIN_WIDTH = 460;
    private static final int SIDE_GRAPH_HEIGHT = 90;
    private static final double ZOOM_STEP = 1.25;

    private final @Nullable Screen parent;
    private final PathPreviewModel model;
    private final BiConsumer<Double, Boolean> onSettings;
    private final @Nullable Function<PathPreview, Component> apply;
    private final LineBatch batch = new LineBatch();
    private PathPreviewDraw.View view = PathPreviewDraw.View.TOP;
    private PathPreviewDraw.@Nullable Projection projection;
    private @Nullable PathPreview projected;
    private PathPreviewDraw.@Nullable View projectedView;
    private PathPreviewDraw.@Nullable Frame frame;
    private boolean autoFit = true;
    private boolean dragging;
    private int mark = -1;
    private @Nullable Component message;
    private int messageColor = Palette.TEXT_MUTED;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int viewTop;
    private int viewBottom;
    private int viewLeft;
    private int viewRight;
    private int graphTop;
    private int graphBottom;
    private int graphLeft;
    private int graphRight;
    private int textTop;
    private int textBottom;
    private int textLeft;
    private int textRight;
    private @Nullable StepSlider timeSlider;

    public PathPreviewScreen(@Nullable Screen parent, PathPreviewModel model, BiConsumer<Double, Boolean> onSettings,
                             @Nullable Function<PathPreview, Component> apply) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.model = model;
        this.onSettings = onSettings;
        this.apply = apply;
    }

    public PathPreviewModel model() {
        return this.model;
    }

    public PathPreviewDraw.View view() {
        return this.view;
    }

    public void view(PathPreviewDraw.View view) {
        this.view = view;
        this.autoFit = true;
        this.rebuildWidgets();
    }

    public void mark(int index) {
        this.mark = index;
    }

    public void zoomOnWorst() {
        PathPreview preview = this.model.result();
        PathPreviewDraw.Projection p = this.projection(preview);
        if (preview == null || p == null) {
            return;
        }
        this.autoFit = false;
        this.frame = PathPreviewDraw.aroundGap(p, this.viewRight - this.viewLeft - 2, this.viewBottom - this.viewTop - 2);
        this.mark = PathPreviewDraw.gapIndex(p);
        if (this.timeSlider != null) {
            this.timeSlider.setCurrent(this.mark / 20.0);
        }
    }

    double graphRingTime() {
        PathPreview preview = this.model.result();
        PathPreviewDraw.Projection p = this.projection(preview);
        return p == null ? -1.0 : PathPreviewDraw.gapTime(p);
    }

    public @Nullable Component message() {
        return this.message;
    }

    String layout() {
        return this.width + "x" + this.height + ", Ansicht " + this.viewLeft + "–" + this.viewRight + " / " + this.viewTop + "–" + this.viewBottom
                + ", Graph " + this.graphLeft + "–" + this.graphRight;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        int h = Math.min(this.height - 16, MAX_HEIGHT);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int left = this.windowLeft + Metrics.PADDING;
        int inner = w - 2 * Metrics.PADDING;
        int rowY = this.windowTop + Metrics.HEADER_HEIGHT + Metrics.PADDING;
        int sliderWidth = this.apply == null ? (inner - Metrics.GAP) * 3 / 5 : inner;
        int limitWidth = inner - sliderWidth - Metrics.GAP;
        int secondY = rowY + Metrics.CONTROL_HEIGHT + 4;
        int worstWidth = this.font.width(Component.translatable(KEY + "worst")) + 16;
        int viewWidth = Math.max(60, (inner - 2 * Metrics.GAP - worstWidth) / 2);
        StepSlider slider = new StepSlider(left, rowY, sliderWidth, Metrics.CONTROL_HEIGHT, 0, 100, 1, Math.round(this.model.slider() * 100.0),
                value -> Component.translatable(TAB + "flashback_simplify").append(": ").append(SimplificationText.value(Simplification.ofSlider(value / 100.0))),
                value -> this.settings(value / 100.0, this.model.limit()));
        slider.untyped();
        slider.setTooltip(Tooltip.create(Component.translatable(TAB + "flashback_simplify.tooltip")));
        this.addRenderableWidget(slider);
        int x = left + sliderWidth + Metrics.GAP;
        if (this.apply == null) {
            CycleButton<Boolean> limit = CycleButton.onOffBuilder(this.model.limit())
                    .withTooltip(value -> Tooltip.create(Component.translatable(TAB + "flashback_limit.tooltip")))
                    .create(x, rowY, limitWidth, Metrics.CONTROL_HEIGHT, Component.translatable(TAB + "flashback_limit"),
                            (button, value) -> this.settings(this.model.slider(), value));
            this.addRenderableWidget(limit);
        }
        x = left;
        CycleButton<PathPreviewDraw.View> viewButton = CycleButton.<PathPreviewDraw.View>builder(
                        value -> Component.translatable(KEY + "view." + value.key()), this.view)
                .withValues(List.of(PathPreviewDraw.View.values()))
                .create(x, secondY, viewWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "view"), (button, value) -> {
                    this.view = value;
                    this.autoFit = true;
                });
        this.addRenderableWidget(viewButton);
        x += viewWidth + Metrics.GAP;
        int timeX = x + worstWidth + Metrics.GAP;
        this.addRenderableWidget(new FlatButton(x, secondY, worstWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "worst"),
                FlatButton.Style.NORMAL, this::zoomOnWorst));
        int footerTop = this.windowBottom - Metrics.FOOTER_HEIGHT;
        int buttonY = footerTop + (Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT) / 2;
        Component done = Component.translatable(KEY + "done");
        int doneWidth = Math.max(56, this.font.width(done) + 16);
        this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - doneWidth, buttonY, doneWidth, Metrics.CONTROL_HEIGHT, done,
                FlatButton.Style.NORMAL, this::onClose));
        if (this.apply != null) {
            Component label = Component.translatable("propwash.video.flashback.rescue.apply");
            int applyWidth = this.font.width(label) + 16;
            FlatButton applyButton = new FlatButton(this.windowRight - Metrics.PADDING - doneWidth - Metrics.GAP - applyWidth, buttonY, applyWidth,
                    Metrics.CONTROL_HEIGHT, label, FlatButton.Style.PRIMARY, this::applyNow);
            applyButton.setTooltip(Tooltip.create(Component.translatable("propwash.video.flashback.rescue.apply.tooltip")));
            this.addRenderableWidget(applyButton);
        }
        int bodyTop = secondY + Metrics.CONTROL_HEIGHT + Metrics.GAP;
        int bodyBottom = footerTop - Metrics.PADDING;
        int right = left + inner;
        this.viewTop = bodyTop;
        this.viewLeft = left;
        if (inner >= SIDE_PANEL_MIN_WIDTH) {
            this.viewRight = left + inner * 3 / 5;
            this.viewBottom = bodyBottom;
            this.graphLeft = this.viewRight + Metrics.GAP;
            this.graphRight = right;
            this.graphTop = bodyTop;
            this.graphBottom = bodyTop + Math.max(MIN_GRAPH, Math.min(SIDE_GRAPH_HEIGHT, (bodyBottom - bodyTop) / 3));
            this.textLeft = this.graphLeft;
            this.textRight = right;
            this.textTop = this.graphBottom + Metrics.GAP;
            this.textBottom = bodyBottom;
        } else {
            this.viewRight = right;
            this.textLeft = left;
            this.textRight = right;
            this.textBottom = bodyBottom;
            this.textTop = bodyBottom - TEXT_LINES * Metrics.LINE_HEIGHT;
            this.graphLeft = left;
            this.graphRight = right;
            this.graphBottom = this.textTop - Metrics.GAP;
            int graphHeight = Math.max(MIN_GRAPH, Math.min(GRAPH_HEIGHT, (this.graphBottom - bodyTop) * 2 / 9));
            this.graphTop = this.graphBottom - graphHeight;
            this.viewBottom = Math.max(bodyTop + MIN_VIEW, this.graphTop - Metrics.GAP);
        }
        double duration = Math.max(0.05, this.durationSeconds());
        StepSlider time = new StepSlider(timeX, secondY, left + inner - timeX, Metrics.CONTROL_HEIGHT, 0.0, duration, 0.05, Math.max(0, this.mark) / 20.0,
                value -> Component.translatable(KEY + "time", SimplificationText.duration(value)), value -> this.mark = (int) Math.round(value * 20.0));
        time.untyped();
        time.setTooltip(Tooltip.create(Component.translatable(KEY + "time.tooltip")));
        this.timeSlider = this.addRenderableWidget(time);
        this.autoFit = this.autoFit || this.frame == null;
    }

    private double durationSeconds() {
        PathPreview preview = this.model.result();
        if (preview != null) {
            return (preview.ticks() - 1) / 20.0;
        }
        return this.model.frames() == null ? 0.0 : Math.max(0, this.model.frames().size() - 1) / 20.0;
    }

    private void settings(double slider, boolean limit) {
        this.model.settings(slider, limit);
        this.onSettings.accept(slider, limit);
    }

    public void applyNow() {
        Function<PathPreview, Component> action = this.apply;
        PathPreview preview = this.model.result();
        if (action == null) {
            return;
        }
        if (!this.model.ready() || preview == null) {
            this.message = Component.translatable(KEY + "computing");
            this.messageColor = Palette.TEXT_MUTED;
            return;
        }
        this.message = action.apply(preview);
        this.messageColor = Palette.ACCENT;
    }

    private PathPreviewDraw.@Nullable Projection projection(@Nullable PathPreview preview) {
        if (preview == null) {
            return null;
        }
        if (this.projection == null || this.projected != preview || this.projectedView != this.view) {
            boolean firstOfFlight = this.projected == null || this.projected.ticks() != preview.ticks();
            this.projection = PathPreviewDraw.Projection.of(preview, this.view);
            this.projected = preview;
            if (this.projectedView != this.view || firstOfFlight) {
                this.autoFit = true;
            }
            this.projectedView = this.view;
            if (firstOfFlight && this.timeSlider != null) {
                this.rebuildWidgets();
            }
        }
        return this.projection;
    }

    @Override
    public void tick() {
        this.model.tick();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.inView(x, y) && this.frame != null && scrollY != 0.0) {
            double cx = (this.viewLeft + this.viewRight) * 0.5;
            double cy = (this.viewTop + this.viewBottom) * 0.5;
            double u = this.frame.u(x, cx);
            double v = this.frame.v(y, cy);
            this.frame = this.frame.zoomed(scrollY > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP, u, v);
            this.autoFit = false;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.inView(event.x(), event.y())) {
            if (doubleClick) {
                this.autoFit = true;
            } else {
                this.dragging = true;
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.inGraph(event.x(), event.y())) {
            this.markFromGraph(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging && this.frame != null) {
            this.frame = this.frame.moved(-dx / this.frame.scale(), -dy / this.frame.scale());
            this.autoFit = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.inGraph(event.x(), event.y())) {
            this.markFromGraph(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    private void markFromGraph(double x) {
        PathPreview preview = this.model.result();
        if (preview == null) {
            return;
        }
        int gx0 = this.graphLeft;
        int gx1 = this.graphRight;
        double t = Math.max(0.0, Math.min(1.0, (x - gx0) / Math.max(1.0, gx1 - gx0)));
        this.mark = (int) Math.round(t * (preview.ticks() - 1));
        if (this.timeSlider != null) {
            this.timeSlider.setCurrent(this.mark / 20.0);
        }
    }

    private boolean inView(double x, double y) {
        return x >= this.viewLeft && x < this.viewRight && y >= this.viewTop && y < this.viewBottom;
    }

    private boolean inGraph(double x, double y) {
        return x >= this.graphLeft && x < this.graphRight && y >= this.graphTop && y < this.graphBottom;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.model.tick();
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, Metrics.HEADER_HEIGHT);
        int textY = Metrics.textY(this.windowTop, Metrics.HEADER_HEIGHT - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + Metrics.HEADER_HEIGHT - 6);
        ThemeText.fitted(g, this.font, this.title, titleX, textY, this.windowRight - titleX - 16, Palette.TEXT);
        int left = this.windowLeft + Metrics.PADDING;
        int right = this.windowRight - Metrics.PADDING;
        ThemeDraw.framedBox(g, this.viewLeft, this.viewTop, this.viewRight, this.viewBottom, Palette.FIELD, Palette.BORDER);
        ThemeDraw.framedBox(g, this.graphLeft, this.graphTop, this.graphRight, this.graphBottom, Palette.FIELD, Palette.BORDER);
        PathPreview preview = this.model.result();
        PathPreviewDraw.Projection p = this.projection(preview);
        if (preview != null && p != null && p.ticks() >= 2) {
            if (this.autoFit || this.frame == null) {
                int legend = this.legendRoom(this.viewRight - this.viewLeft - 10);
                PathPreviewDraw.Frame fitted = PathPreviewDraw.fit(p, 0, p.ticks() - 1, this.viewRight - this.viewLeft - 2,
                        this.viewBottom - this.viewTop - 2 - legend, 1.0);
                this.frame = fitted.moved(0.0, -legend / 2.0 / fitted.scale());
                this.autoFit = false;
            }
            boolean strokes = this.frame.scale() > 6.0;
            PathPreviewDraw.draw(g, this.batch, p, this.frame, this.viewLeft + 1, this.viewTop + 1, this.viewRight - 1, this.viewBottom - 1, 0,
                    p.ticks() - 1, strokes,
                    true, this.mark);
            this.graph(g, preview, p, this.graphLeft + 1, this.graphTop + 1, this.graphRight - 1, this.graphBottom - 1, mouseX, mouseY);
            this.legend(g, this.viewLeft + 4, this.viewTop + 4, this.viewRight - this.viewLeft - 10);
            if (this.inView(mouseX, mouseY) && !this.dragging) {
                Component hint = Component.translatable(KEY + "hint");
                String shown = ThemeText.fit(this.font, hint, Math.max(10, this.viewRight - this.viewLeft - 8));
                int hintY = this.viewBottom - Metrics.LINE_HEIGHT - 2;
                g.fill(this.viewLeft + 1, hintY - 2, this.viewLeft + 6 + this.font.width(shown), this.viewBottom - 1, 0xC0121212);
                ThemeText.text(g, this.font, shown, this.viewLeft + 4, hintY, Palette.TEXT_FAINT);
            }
        } else {
            Component text = Component.translatable(KEY + (this.model.empty() ? "unavailable" : "computing"));
            ThemeText.textCentered(g, this.font, text, (this.viewLeft + this.viewRight) / 2, (this.viewTop + this.viewBottom) / 2 - 4, Palette.TEXT_MUTED);
        }
        int y = this.textTop;
        for (Line line : this.lines(preview)) {
            for (FormattedCharSequence row : this.font.split(line.text(), Math.max(20, this.textRight - this.textLeft))) {
                if (y + Metrics.LINE_HEIGHT > this.textBottom + 1) {
                    break;
                }
                ThemeText.text(g, this.font, row, this.textLeft, y, line.color());
                y += Metrics.LINE_HEIGHT;
            }
        }
        int footerTop = this.windowBottom - Metrics.FOOTER_HEIGHT;
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        int buttonsLeft = this.windowRight;
        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof FlatButton button && button.getY() >= footerTop) {
                buttonsLeft = Math.min(buttonsLeft, button.getX());
            }
        }
        ThemeText.text(g, this.font, ThemeText.fit(this.font, this.model.sourceLine(), Math.max(10, buttonsLeft - left - Metrics.GAP)), left,
                Metrics.textY(footerTop, Metrics.FOOTER_HEIGHT), Palette.TEXT_FAINT);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private record Line(Component text, int color) {
    }

    private List<Line> lines(@Nullable PathPreview preview) {
        List<Line> lines = new ArrayList<>();
        if (this.message != null) {
            lines.add(new Line(this.message, this.messageColor));
        }
        if (preview != null) {
            lines.add(new Line(SimplificationText.metrics(preview), Palette.TEXT));
            Component cap = SimplificationText.cap(preview);
            if (cap != null) {
                lines.add(new Line(cap, preview.built().capped() ? Palette.ACCENT : Palette.WARNING));
            }
            lines.add(new Line(SimplificationText.fits(preview, this.model.limit()), Palette.TEXT_MUTED));
        }
        return lines;
    }

    private void legend(GuiGraphicsExtractor g, int x, int y, int room) {
        Component actual = Component.translatable(KEY + "actual");
        Component saved = Component.translatable(KEY + "saved");
        Component keys = Component.translatable(KEY + "keyframes");
        Component worst = this.worstLegend();
        int first = 13 + this.font.width(actual) + 8 + 13 + this.font.width(saved);
        int second = 10 + this.font.width(keys) + 8 + 11 + this.font.width(worst);
        boolean stacked = first + 8 + second > room;
        int width = stacked ? Math.max(first, second) : first + 8 + second;
        int height = stacked ? 2 * Metrics.LINE_HEIGHT : Metrics.LINE_HEIGHT;
        g.fill(x - 3, y - 3, x + width + 3, y + height + 1, 0xC0121212);
        int lineY = y + Metrics.LINE_HEIGHT / 2 - 1;
        g.fill(x, lineY, x + 10, lineY + 1, PathPreviewDraw.ACTUAL);
        ThemeText.text(g, this.font, actual, x + 13, y, Palette.TEXT_MUTED);
        int x2 = x + 13 + this.font.width(actual) + 8;
        g.fill(x2, lineY, x2 + 10, lineY + 2, PathPreviewDraw.SAVED);
        ThemeText.text(g, this.font, saved, x2 + 13, y, Palette.TEXT_MUTED);
        int x3 = stacked ? x : x + first + 8;
        int y3 = stacked ? y + Metrics.LINE_HEIGHT : y;
        int lineY3 = y3 + Metrics.LINE_HEIGHT / 2 - 1;
        g.fill(x3 + 3, lineY3 - 1, x3 + 6, lineY3 + 2, PathPreviewDraw.KEYFRAME);
        ThemeText.text(g, this.font, keys, x3 + 10, y3, Palette.TEXT_MUTED);
        int x4 = x3 + 10 + this.font.width(keys) + 8;
        this.batch.clear();
        this.batch.ring(x4 + 4, lineY3, 3.5F, 1.0F, PathPreviewDraw.WORST);
        this.batch.submit(g);
        ThemeText.text(g, this.font, worst, x4 + 11, y3, Palette.TEXT_MUTED);
    }

    private int legendRoom(int room) {
        int all = 13 + this.font.width(Component.translatable(KEY + "actual")) + 8 + 13 + this.font.width(Component.translatable(KEY + "saved")) + 8
                + 10 + this.font.width(Component.translatable(KEY + "keyframes")) + 8 + 11 + this.font.width(this.worstLegend());
        return all > room ? LEGEND_ROOM + Metrics.LINE_HEIGHT : LEGEND_ROOM;
    }

    private Component worstLegend() {
        PathPreview preview = this.model.result();
        PathPreview.Gap gap = preview == null ? PathPreview.Gap.NONE : this.view == PathPreviewDraw.View.TOP ? preview.topGap() : preview.sideGap();
        return Component.translatable(KEY + "worst." + this.view.key(), SimplificationText.distance(gap.distance()));
    }

    private void graph(GuiGraphicsExtractor g, PathPreview preview, PathPreviewDraw.Projection p, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        int n = preview.ticks();
        if (n < 2) {
            return;
        }
        Simplification used = preview.built().used();
        double posTop = Math.max(preview.maxSameTime(), used.off() ? 0.01 : used.position() * Simplification.REFINE_FACTOR);
        double angTop = Math.max(preview.maxAngle(), used.off() ? 0.5 : used.angle() * Simplification.REFINE_FACTOR);
        Component title = Component.translatable(KEY + "graph");
        Component position = Component.translatable(KEY + "graph.position").append(" ").append(SimplificationText.distance(preview.maxSameTime()));
        Component angle = Component.translatable(KEY + "graph.angle").append(" ").append(SimplificationText.degrees(preview.maxAngle())).append("°");
        int titleWidth = this.font.width(title) + 10;
        int valuesWidth = this.font.width(position) + 10 + this.font.width(angle);
        boolean twoRows = titleWidth + valuesWidth > x1 - x0 - 6;
        int labelRoom = (twoRows ? 2 : 1) * Metrics.LINE_HEIGHT + 2;
        int top = y0 + labelRoom;
        int height = Math.max(4, y1 - top - 1);
        this.batch.clear();
        if (!used.off()) {
            float tolY = (float) (top + height - used.position() / posTop * height);
            this.batch.line(x0, tolY, x1, tolY, 1.0F, 0x50FFBB00);
        }
        this.series(preview.positionError(), posTop, x0, x1, top, height, PathPreviewDraw.SAVED);
        this.series(preview.angleError(), angTop, x0, x1, top, height, Palette.INFO);
        if (this.mark >= 0 && this.mark < n) {
            float mx = (float) (x0 + this.mark / (double) (n - 1) * (x1 - x0));
            this.batch.line(mx, y0, mx, y1, 1.0F, PathPreviewDraw.MARK);
        }
        double ringTime = PathPreviewDraw.gapTime(p);
        if (ringTime >= 0.0) {
            float[] errors = preview.positionError();
            int i = (int) Math.floor(ringTime);
            int j = Math.min(n - 1, i + 1);
            double error = errors[i] + (errors[j] - errors[i]) * (ringTime - i);
            float wx = (float) (x0 + ringTime / (n - 1) * (x1 - x0));
            this.batch.ring(wx, (float) (top + height - Math.min(1.0, error / posTop) * height), 3.0F, 1.0F, PathPreviewDraw.WORST);
        }
        this.batch.submit(g);
        ThemeText.text(g, this.font, ThemeText.fit(this.font, title, Math.max(10, x1 - x0 - 6)), x0 + 3, y0 + 2, Palette.TEXT_MUTED);
        int x = twoRows ? x0 + 3 : x0 + 3 + titleWidth;
        int valuesY = twoRows ? y0 + 2 + Metrics.LINE_HEIGHT : y0 + 2;
        ThemeText.text(g, this.font, position, x, valuesY, PathPreviewDraw.SAVED);
        x += this.font.width(position) + 10;
        ThemeText.text(g, this.font, angle, x, valuesY, Palette.INFO);
        if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
            int index = (int) Math.round((mouseX - x0) / (double) Math.max(1, x1 - x0) * (n - 1));
            index = Math.max(0, Math.min(n - 1, index));
            Component readout = SimplificationText.duration(index / 20.0).copy().append(" · ")
                    .append(SimplificationText.distance(preview.positionError()[index])).append(" · ")
                    .append(Numbers.of(preview.angleError()[index], 1)).append("°");
            g.fill(mouseX, y0, mouseX + 1, y1, Palette.CROSSHAIR);
            int w = this.font.width(readout) + 6;
            int bx = mouseX + 4 + w > x1 ? mouseX - 4 - w : mouseX + 4;
            ThemeDraw.tooltipFrame(g, bx, y1 - Metrics.LINE_HEIGHT - 4, bx + w, y1 - 1);
            ThemeText.text(g, this.font, readout, bx + 3, y1 - Metrics.LINE_HEIGHT - 2, Palette.TEXT);
        }
    }

    private void series(float[] values, double top, int x0, int x1, int y, int height, int color) {
        int n = values.length;
        int width = Math.max(1, x1 - x0);
        float px = x0;
        float py = (float) (y + height - Math.min(1.0, values[0] / top) * height);
        int columns = Math.min(n, width);
        for (int c = 1; c < columns; c++) {
            int from = (int) ((long) (c - 1) * n / columns);
            int to = (int) ((long) c * n / columns);
            float peak = 0.0F;
            for (int i = from; i < Math.max(from + 1, to); i++) {
                peak = Math.max(peak, values[Math.min(n - 1, i)]);
            }
            float x = x0 + c * (width / (float) Math.max(1, columns - 1));
            float yy = (float) (y + height - Math.min(1.0, peak / top) * height);
            this.batch.line(px, py, x, yy, 1.0F, color);
            px = x;
            py = yy;
        }
    }
}
