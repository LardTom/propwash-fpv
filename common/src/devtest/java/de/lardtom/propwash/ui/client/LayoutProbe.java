package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Box;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.TooltipScroll;
import de.lardtom.propwash.ui.client.theme.WidgetViews;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class LayoutProbe {
    private static final int OPAQUE_ALPHA = 0xF0;
    private static final int MAX_SCROLL = 400;
    private static final int MAX_PAGES = 60;
    private static final @Nullable Field TEXT = field("text");
    private static final @Nullable Field TEXT_Y = field("y");
    private static final int LINE_CELL = 9;
    private static final int MAX_TIP_PAGES = 12;
    private static final int SLOT_SIZE = 16;
    private static final int MIN_THUMB = 4;
    private static final @Nullable Field WIDGET_TOOLTIP = field(AbstractWidget.class, "tooltip");
    private static final @Nullable Field LEFT_POS = field(AbstractContainerScreen.class, "leftPos");
    private static final @Nullable Field TOP_POS = field(AbstractContainerScreen.class, "topPos");

    private LayoutProbe() {
    }

    public record Bar(Box box, int color) {
    }

    public record Frame(List<LayoutAudit.Text> texts, List<LayoutAudit.Widget> widgets, Map<String, String> cuts, List<LayoutAudit.Cover> covers,
                        List<LayoutAudit.Text> tooltip, @Nullable Box tooltipBox, List<Bar> bars) {
    }

    public static Frame capture(Minecraft mc, Screen screen) {
        return capture(mc, screen, -1, -1);
    }

    public static Frame capture(Minecraft mc, Screen screen, int mouseX, int mouseY) {
        return capture(mc, g -> screen.extractRenderStateWithTooltipAndSubtitles(g, mouseX, mouseY, 0.0F), widgets(screen), mouseX, mouseY);
    }

    public static Frame capture(Minecraft mc, Consumer<GuiGraphicsExtractor> draw, List<LayoutAudit.Widget> widgets) {
        return capture(mc, draw, widgets, -1, -1);
    }

    private static Frame capture(Minecraft mc, Consumer<GuiGraphicsExtractor> draw, List<LayoutAudit.Widget> widgets, int mouseX, int mouseY) {
        State state = new State();
        Graphics graphics = new Graphics(mc, state, mouseX, mouseY);
        ThemeStats.trackCuts(true);
        ThemeStats.trackTooltip(true);
        Map<String, String> cuts;
        int[] placed;
        try {
            draw.accept(graphics);
        } finally {
            cuts = ThemeStats.takeCuts();
            ThemeStats.trackCuts(false);
            placed = ThemeStats.takeTooltip();
            ThemeStats.trackTooltip(false);
        }
        Box tooltipBox = placed == null ? null : Box.of(placed[0], placed[1], placed[2], placed[3]);
        return new Frame(state.texts, widgets, cuts, state.covers, state.tooltip, tooltipBox, state.bars);
    }

    public record TooltipSpot(int x, int y, Box box, String firstLine) {
        int area() {
            return this.box.width() * this.box.height();
        }
    }

    public record TooltipSweep(List<Finding> findings, int seen) {
    }

    public static @Nullable TooltipSpot largestTooltip(Minecraft mc, Screen screen) {
        Map<String, TooltipSpot> spots = new LinkedHashMap<>();
        sweep(mc, screen, Box.of(0, 0, screen.width, screen.height), capture(mc, screen), new LinkedHashMap<>(), spots);
        capture(mc, screen);
        TooltipSpot largest = null;
        for (TooltipSpot spot : spots.values()) {
            if (largest == null || spot.area() > largest.area()) {
                largest = spot;
            }
        }
        return largest;
    }

    public static void pointMouse(Minecraft mc, int guiX, int guiY) {
        HoverSweepSelfTests.pointMouse(mc, guiX, guiY);
    }

    public static TooltipSweep tooltips(Minecraft mc, Screen screen) {
        Box bounds = Box.of(0, 0, screen.width, screen.height);
        Map<String, Finding> out = new LinkedHashMap<>();
        Map<String, TooltipSpot> spots = new LinkedHashMap<>();
        Frame top = capture(mc, screen);
        sweep(mc, screen, bounds, top, out, spots);
        String digest = digest(top);
        int pages = 0;
        while (pages < MAX_TIP_PAGES) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            Frame next = capture(mc, screen);
            String moved = digest(next);
            if (moved.equals(digest)) {
                break;
            }
            pages++;
            digest = moved;
            sweep(mc, screen, bounds, next, out, spots);
        }
        for (int i = 0; pages > 0 && i < pages + 2; i++) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEUP, 0, 0));
        }
        capture(mc, screen);
        return new TooltipSweep(new ArrayList<>(out.values()), spots.size());
    }

    private static void sweep(Minecraft mc, Screen screen, Box bounds, Frame base, Map<String, Finding> out, Map<String, TooltipSpot> spots) {
        Map<Long, Boolean> points = new LinkedHashMap<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.getWidth() > 0 && widget.getHeight() > 0 && hasTooltip(widget)) {
                Box box = visibleBox(screen, widget);
                if (box != null) {
                    points.put(point(box.centerX(), box.centerY()), Boolean.TRUE);
                }
            }
        }
        for (LayoutAudit.Text text : base.texts()) {
            Box visible = text.visible();
            if (visible != null && bounds.containsPoint(visible.centerX(), visible.centerY())) {
                points.putIfAbsent(point(visible.centerX(), visible.centerY()), Boolean.FALSE);
            }
        }
        for (int[] slot : slots(screen)) {
            if (bounds.containsPoint(slot[0], slot[1])) {
                points.putIfAbsent(point(slot[0], slot[1]), Boolean.FALSE);
            }
        }
        for (Map.Entry<Long, Boolean> entry : points.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int y = (int) (long) entry.getKey();
            int windows = TooltipScroll.windows();
            Frame hovered = capture(mc, screen, x, y);
            if (hovered.tooltipBox() == null && entry.getValue()) {
                pause();
                windows = TooltipScroll.windows();
                hovered = capture(mc, screen, x, y);
            }
            Box box = hovered.tooltipBox();
            if (box == null) {
                continue;
            }
            String first = hovered.tooltip().isEmpty() ? "?" : hovered.tooltip().getFirst().value();
            boolean fresh = spots.putIfAbsent(first + "|" + box.width() + "x" + box.height(), new TooltipSpot(x, y, box, first)) == null;
            Finding finding = LayoutAudit.tooltip(bounds, first, box);
            if (finding != null) {
                out.putIfAbsent(first, new Finding(finding.kind(), finding.subject(), finding.detail() + ", Maus bei " + x + "," + y));
            }
            if (fresh && TooltipScroll.windows() != windows) {
                Finding scrolled = scrollTooltip(mc, screen, bounds, first, x, y);
                if (scrolled != null) {
                    out.putIfAbsent(first + " (Mausrad)", scrolled);
                }
            }
        }
    }

    private static @Nullable Finding scrollTooltip(Minecraft mc, Screen screen, Box bounds, String first, int x, int y) {
        Finding finding = null;
        int steps = 0;
        String lastRow = "";
        while (steps < MAX_SCROLL && TooltipScroll.scroll(-1.0)) {
            steps++;
            Frame frame = capture(mc, screen, x, y);
            Box box = frame.tooltipBox();
            if (box == null) {
                finding = new Finding(LayoutAudit.Kind.TOOLTIP, first, "verschwindet beim Scrollen mit dem Mausrad, Maus bei " + x + "," + y);
                break;
            }
            Finding placed = LayoutAudit.tooltip(bounds, first, box);
            if (placed != null && finding == null) {
                finding = new Finding(placed.kind(), placed.subject(), placed.detail() + " nach " + steps + " Schritten mit dem Mausrad");
            }
            if (frame.tooltip().size() >= 2) {
                lastRow = frame.tooltip().get(frame.tooltip().size() - 2).value();
            }
        }
        if (finding == null && (steps == 0 || TooltipScroll.offset() != TooltipScroll.maxOffset())) {
            finding = new Finding(LayoutAudit.Kind.TOOLTIP, first, "ist höher als der Bildschirm und scrollt nicht bis ans Ende: " + steps
                    + " Schritte, Zeile " + TooltipScroll.offset() + " von " + TooltipScroll.maxOffset() + ", letzte Zeile „" + lastRow + "“");
        }
        for (int i = 0; i < steps + 2; i++) {
            capture(mc, screen, x, y);
            if (!TooltipScroll.scroll(1.0)) {
                break;
            }
        }
        return finding;
    }

    private static long point(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static void pause() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasTooltip(AbstractWidget widget) {
        if (WIDGET_TOOLTIP == null) {
            return true;
        }
        try {
            return WIDGET_TOOLTIP.get(widget) instanceof WidgetTooltipHolder holder && holder.get() != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private static @Nullable Box visibleBox(Screen screen, AbstractWidget widget) {
        Box box = Box.of(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        ScreenRectangle view = screen instanceof WidgetViews views ? views.widgetView(widget) : null;
        return view == null ? box : box.intersection(box(view));
    }

    private static List<int[]> slots(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || LEFT_POS == null || TOP_POS == null) {
            return List.of();
        }
        int left;
        int top;
        try {
            left = LEFT_POS.getInt(container);
            top = TOP_POS.getInt(container);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
        List<int[]> out = new ArrayList<>();
        for (Slot slot : container.getMenu().slots) {
            if (slot.isActive()) {
                out.add(new int[]{left + slot.x + SLOT_SIZE / 2, top + slot.y + SLOT_SIZE / 2});
            }
        }
        return out;
    }

    public static List<Finding> scrollbars(Minecraft mc, Screen screen) {
        Frame start = capture(mc, screen);
        List<Box[]> pairs = scrollbarPairs(start);
        if (pairs.isEmpty()) {
            return List.of();
        }
        List<Finding> out = new ArrayList<>();
        MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
        for (Box[] pair : pairs) {
            Box thumb = pair[0];
            Box track = pair[1];
            double x = thumb.left() + thumb.width() / 2.0;
            double grip = thumb.top() + thumb.height() / 2.0;
            double bottom = track.bottom() - 1.0;
            double top = track.top() + 1.0;
            screen.mouseClicked(new MouseButtonEvent(x, grip, left), false);
            screen.mouseDragged(new MouseButtonEvent(x, bottom, left), 0.0, bottom - grip);
            int atBottom = thumbTop(capture(mc, screen), track);
            screen.mouseDragged(new MouseButtonEvent(x, top, left), 0.0, top - bottom);
            int atTop = thumbTop(capture(mc, screen), track);
            screen.mouseDragged(new MouseButtonEvent(x, grip, left), 0.0, grip - top);
            screen.mouseReleased(new MouseButtonEvent(x, grip, left));
            if (mc.gui.screen() != screen) {
                out.add(LayoutAudit.deadScrollbar(track, thumb, "Klick auf den Griff öffnet "
                        + (mc.gui.screen() == null ? "nichts mehr (Screen geschlossen)" : mc.gui.screen().getClass().getSimpleName())));
                mc.gui.setScreen(screen);
                break;
            }
            if (atBottom == atTop) {
                out.add(LayoutAudit.deadScrollbar(track, thumb, "lässt sich nicht ziehen"));
            }
        }
        capture(mc, screen);
        return out;
    }

    private static List<Box[]> scrollbarPairs(Frame frame) {
        List<Box[]> out = new ArrayList<>();
        for (Bar thumb : frame.bars()) {
            if (thumb.color() != Palette.BORDER_STRONG || thumb.box().height() < MIN_THUMB) {
                continue;
            }
            for (Bar track : frame.bars()) {
                if (track.color() == Palette.FIELD && inTrack(thumb.box(), track.box())) {
                    out.add(new Box[]{thumb.box(), track.box()});
                    break;
                }
            }
        }
        return out;
    }

    private static boolean inTrack(Box thumb, Box track) {
        return thumb.left() == track.left() && thumb.right() == track.right() && thumb.top() >= track.top() && thumb.bottom() <= track.bottom()
                && thumb.height() < track.height();
    }

    private static int thumbTop(Frame frame, Box track) {
        for (Bar bar : frame.bars()) {
            if (bar.color() != Palette.FIELD && inTrack(bar.box(), track)) {
                return bar.box().top();
            }
        }
        return Integer.MIN_VALUE;
    }

    public static List<Finding> audit(Minecraft mc, Screen screen) {
        Box bounds = Box.of(0, 0, screen.width, screen.height);
        Set<Finding> out = new LinkedHashSet<>();
        Frame frame = capture(mc, screen);
        addStatics(out, bounds, frame);
        List<Box> clipped = LayoutAudit.scrollRegions(frame.texts());
        List<Box> blind = new ArrayList<>(LayoutAudit.clipRegions(frame.texts()));
        blind.removeAll(clipped);
        for (Box region : clipped) {
            int x = region.centerX();
            int y = region.centerY();
            Frame top = capture(mc, screen);
            Frame current = top;
            String signature = signature(top, x, y);
            int steps = 0;
            boolean keys = false;
            StringBuilder trail = new StringBuilder();
            while (steps < MAX_SCROLL) {
                if (!keys) {
                    screen.mouseScrolled(x, y, 0.0, -1.0);
                } else {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
                }
                Frame next = capture(mc, screen);
                String moved = signature(next, x, y);
                if (moved.equals(signature)) {
                    if (!keys && steps == 0) {
                        keys = true;
                        continue;
                    }
                    break;
                }
                steps++;
                if (steps <= 3) {
                    trail.append(" [").append(keys ? "Taste" : "Rad").append(state(screen)).append(" ").append(signature).append(" -> ").append(moved).append("]");
                }
                signature = moved;
                current = next;
                addStatics(out, bounds, next);
            }
            if (steps == 0) {
                String why = diagnose(screen);
                for (Finding finding : LayoutAudit.vertical(top.texts(), x, y, true, true)) {
                    out.add(new Finding(finding.kind(), finding.subject(), finding.detail() + " (" + why + ")"));
                }
            } else {
                out.addAll(LayoutAudit.vertical(top.texts(), x, y, true, false));
            }
            if (steps > 0) {
                String state = state(screen);
                for (Finding finding : LayoutAudit.vertical(current.texts(), x, y, false, true)) {
                    out.add(new Finding(finding.kind(), finding.subject(), finding.detail() + " (nach " + steps + " Schritten" + state + trail + ")"));
                }
            }
            for (int i = 0; i < steps + 2; i++) {
                if (keys) {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEUP, 0, 0));
                } else {
                    screen.mouseScrolled(x, y, 0.0, 1.0);
                }
            }
        }
        if (!blind.isEmpty()) {
            pageBlind(mc, screen, bounds, blind, out);
        }
        return new ArrayList<>(out);
    }

    private static void pageBlind(Minecraft mc, Screen screen, Box bounds, List<Box> regions, Set<Finding> out) {
        Frame top = capture(mc, screen);
        Frame current = top;
        String signature = signature(top, regions);
        int steps = 0;
        while (steps < MAX_PAGES) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            Frame next = capture(mc, screen);
            String moved = signature(next, regions);
            if (moved.equals(signature)) {
                if (steps == 0 && !digest(next).equals(digest(top))) {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEUP, 0, 0));
                }
                break;
            }
            steps++;
            signature = moved;
            current = next;
            addStatics(out, bounds, next);
        }
        if (steps == 0) {
            return;
        }
        String state = state(screen);
        for (Box region : regions) {
            for (Finding finding : LayoutAudit.vertical(current.texts(), region.centerX(), region.centerY(), false, true)) {
                out.add(new Finding(finding.kind(), finding.subject(), finding.detail() + " (nach " + steps + " Seiten mit Bild ab" + state + ")"));
            }
        }
        for (int i = 0; i < steps + 2; i++) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEUP, 0, 0));
        }
    }

    private static String signature(Frame frame, List<Box> regions) {
        StringBuilder out = new StringBuilder();
        for (Box region : regions) {
            out.append(signature(frame, region.centerX(), region.centerY())).append('/');
        }
        return out.toString();
    }

    public static List<Finding> auditDrawing(Minecraft mc, Box bounds, Consumer<GuiGraphicsExtractor> draw) {
        Set<Finding> out = new LinkedHashSet<>();
        addStatics(out, bounds, capture(mc, draw, List.of()));
        return List.copyOf(out);
    }

    private static void addStatics(Set<Finding> out, Box bounds, Frame frame) {
        out.addAll(LayoutAudit.statics(bounds, frame.texts(), frame.widgets()));
        out.addAll(LayoutAudit.covered(frame.texts(), frame.covers()));
        frame.cuts().forEach((full, shown) -> out.add(LayoutAudit.cut(full, shown)));
    }

    private static String digest(Frame frame) {
        StringBuilder out = new StringBuilder();
        for (LayoutAudit.Text text : frame.texts()) {
            out.append(text.value()).append('@').append(text.full().left()).append(',').append(text.full().top()).append(';');
        }
        return out.toString();
    }

    private static String signature(Frame frame, int x, int y) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        String first = "";
        String last = "";
        for (LayoutAudit.Text text : frame.texts()) {
            Box clip = text.clip();
            if (clip == null || !clip.containsPoint(x, y)) {
                continue;
            }
            if (text.full().top() < top) {
                top = text.full().top();
                first = text.value();
            }
            if (text.full().bottom() > bottom) {
                bottom = text.full().bottom();
                last = text.value();
            }
        }
        return top + ".." + bottom + ":" + first + "|" + last;
    }

    private static String state(Screen screen) {
        if (!(screen instanceof ConfiguratorScreen configurator)) {
            return "";
        }
        ContentScroll scroll = configurator.contentScroll();
        return scroll == null ? ", Tab scrollt selbst"
                : ", Versatz " + scroll.offset() + " von " + scroll.maxOffset() + ", Inhalt " + scroll.contentHeight() + " in " + scroll.view().height();
    }

    private static String diagnose(Screen screen) {
        if (!(screen instanceof ConfiguratorScreen configurator)) {
            return "scrollt weder mit Mausrad noch mit Bild ab";
        }
        ContentScroll scroll = configurator.contentScroll();
        return scroll == null ? "scrollt nicht, Tab scrollt selbst"
                : "scrollt nicht, Versatz " + scroll.offset() + " von " + scroll.maxOffset() + ", Inhalt " + scroll.contentHeight() + " in " + scroll.view().height();
    }

    public static List<LayoutAudit.Widget> widgets(Screen screen) {
        List<LayoutAudit.Widget> out = new ArrayList<>();
        WidgetViews views = screen instanceof WidgetViews v ? v : null;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible || widget.getWidth() <= 0 || widget.getHeight() <= 0) {
                continue;
            }
            Box box = Box.of(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            ScreenRectangle view = views == null ? null : views.widgetView(widget);
            if (view != null) {
                box = box.intersection(box(view));
                if (box == null) {
                    continue;
                }
            }
            out.add(new LayoutAudit.Widget(widget.getClass().getSimpleName() + " " + widget.getMessage().getString(), box));
        }
        return out;
    }

    static Box box(ScreenRectangle rect) {
        return Box.of(rect.left(), rect.top(), rect.width(), rect.height());
    }

    private static @Nullable Field field(String name) {
        return field(GuiTextRenderState.class, name);
    }

    private static @Nullable Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static String plain(@Nullable FormattedCharSequence sequence) {
        if (sequence == null) {
            return "?";
        }
        StringBuilder out = new StringBuilder();
        sequence.accept((position, style, codepoint) -> {
            out.appendCodePoint(codepoint);
            return true;
        });
        return out.toString();
    }

    private static final class State extends GuiRenderState {
        private final List<LayoutAudit.Text> texts = new ArrayList<>();
        private final List<LayoutAudit.Cover> covers = new ArrayList<>();
        private final List<LayoutAudit.Text> tooltip = new ArrayList<>();
        private final List<Bar> bars = new ArrayList<>();
        private boolean deferred;
        private int layer;
        private @Nullable String pending;
        private @Nullable FormattedCharSequence pendingSequence;

        @Override
        public void nextStratum() {
            this.layer++;
            super.nextStratum();
        }

        @Override
        public void addGuiElement(GuiElementRenderState element) {
            if (!this.deferred && element instanceof ColoredRectangleRenderState rect) {
                ScreenRectangle bounds = rect.bounds();
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    if (opaque(rect.col1()) && opaque(rect.col2())) {
                        this.covers.add(new LayoutAudit.Cover(box(bounds), this.texts.size()));
                    }
                    if (bounds.width() == Metrics.SCROLLBAR_WIDTH && rect.col1() == rect.col2()) {
                        this.bars.add(new Bar(box(bounds), rect.col1()));
                    }
                }
            }
            super.addGuiElement(element);
        }

        private static boolean opaque(int color) {
            return (color >>> 24) >= OPAQUE_ALPHA;
        }

        @Override
        public void addText(GuiTextRenderState text) {
            ScreenRectangle bounds = text.ensurePrepared().bounds();
            if (bounds == null) {
                return;
            }
            FormattedCharSequence sequence = this.pendingSequence != null ? this.pendingSequence : sequence(text);
            if (sequence != null) {
                int advance = Minecraft.getInstance().font.width(sequence) + 1;
                if (advance < bounds.width()) {
                    bounds = new ScreenRectangle(bounds.left(), bounds.top(), advance, bounds.height());
                }
            }
            bounds = lineCell(text, bounds);
            ScreenRectangle full = bounds.transformMaxBounds(text.pose);
            if (full.width() <= 0 || full.height() <= 0) {
                return;
            }
            String value = this.pending != null ? this.pending : sequence != null ? plain(sequence) : "?";
            LayoutAudit.Text entry = new LayoutAudit.Text(value, box(full), text.scissor == null ? null : box(text.scissor), this.layer);
            if (this.deferred) {
                this.tooltip.add(entry);
            } else {
                this.texts.add(entry);
            }
        }

        private static ScreenRectangle lineCell(GuiTextRenderState text, ScreenRectangle bounds) {
            if (TEXT_Y == null) {
                return bounds;
            }
            int y;
            try {
                y = TEXT_Y.getInt(text);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return bounds;
            }
            int top = Math.max(bounds.top(), y);
            int bottom = Math.min(bounds.bottom(), y + LINE_CELL);
            if (bottom <= top || (top == bounds.top() && bottom == bounds.bottom())) {
                return bounds;
            }
            return new ScreenRectangle(bounds.left(), top, bounds.width(), bottom - top);
        }

        private static @Nullable FormattedCharSequence sequence(GuiTextRenderState text) {
            if (TEXT == null) {
                return null;
            }
            try {
                return (FormattedCharSequence) TEXT.get(text);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
    }

    private static final class Graphics extends GuiGraphicsExtractor {
        private final State state;

        private Graphics(Minecraft minecraft, State state, int mouseX, int mouseY) {
            super(minecraft, state, mouseX, mouseY);
            this.state = state;
        }

        @Override
        public void extractDeferredElements(int mouseX, int mouseY, float a) {
            this.state.deferred = true;
            try {
                super.extractDeferredElements(mouseX, mouseY, a);
            } finally {
                this.state.deferred = false;
            }
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
            this.state.pending = plain(text);
            this.state.pendingSequence = text;
            try {
                super.text(font, text, x, y, color, dropShadow);
            } finally {
                this.state.pending = null;
                this.state.pendingSequence = null;
            }
        }
    }
}
