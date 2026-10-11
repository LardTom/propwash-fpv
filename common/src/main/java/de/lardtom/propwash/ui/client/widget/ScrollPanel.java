package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class ScrollPanel {
    private static final double PIXELS_PER_NOTCH = 18.0;
    private static final int MORE_HINT = 13;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final ScreenRectangle rect;
    private final List<Entry> entries = new ArrayList<>();
    private final TreeSet<Integer> stops = new TreeSet<>();
    private int contentHeight;
    private double offset;
    private boolean draggingThumb;

    private record Entry(AbstractWidget widget, int baseY) {
    }

    public ScrollPanel(int x, int y, int width, int height, double initialOffset) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rect = new ScreenRectangle(x, y, width, height);
        this.offset = Math.max(0.0, initialOffset);
    }

    public ScreenRectangle rect() {
        return this.rect;
    }

    public int top() {
        return this.y - (int) Math.round(this.offset);
    }

    public int offset() {
        return (int) Math.round(this.offset);
    }

    public int innerWidth() {
        return this.width - Metrics.CONTENT_INSET;
    }

    public <T extends AbstractWidget> T add(T widget) {
        this.entries.add(new Entry(widget, widget.getY() + (int) Math.round(this.offset)));
        if (widget instanceof PanelWidget panel) {
            panel.clip(this.rect);
        }
        return widget;
    }

    public void stop(int contentY) {
        if (contentY > 0) {
            this.stops.add(contentY);
        }
    }

    public boolean snapped() {
        return !this.stops.isEmpty();
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        this.offset = this.snap(this.offset);
        this.layout();
    }

    public int maxOffset() {
        int free = Math.max(0, this.contentHeight - this.height);
        if (free == 0 || this.stops.isEmpty()) {
            return free;
        }
        Integer stop = this.stops.ceiling(free);
        return stop == null ? free : stop;
    }

    private double snap(double raw) {
        double clamped = Math.max(0.0, Math.min(this.maxOffset(), raw));
        if (this.stops.isEmpty()) {
            return clamped;
        }
        int best = 0;
        for (int stop : this.stops) {
            if (stop > this.maxOffset()) {
                break;
            }
            if (Math.abs(stop - clamped) < Math.abs(best - clamped)) {
                best = stop;
            }
        }
        return best;
    }

    public boolean scroll(double notches) {
        if (this.maxOffset() <= 0) {
            return false;
        }
        if (this.stops.isEmpty()) {
            this.offset = Math.max(0.0, Math.min(this.maxOffset(), this.offset - notches * PIXELS_PER_NOTCH));
        } else {
            int current = this.offset();
            int steps = Math.max(1, (int) Math.round(Math.abs(notches)));
            int next = current;
            for (int i = 0; i < steps; i++) {
                Integer stop = notches > 0 ? this.stops.lower(next) : this.stops.higher(next);
                next = stop == null ? (notches > 0 ? 0 : this.maxOffset()) : stop;
            }
            this.offset = Math.max(0, Math.min(this.maxOffset(), next));
        }
        this.layout();
        return true;
    }

    public int clipBottom() {
        int bottom = this.y + this.height;
        if (this.stops.isEmpty()) {
            return bottom;
        }
        int o = this.offset();
        int room = this.contentHeight - o > this.height ? this.height - MORE_HINT : this.height;
        int best = -1;
        for (int stop : this.stops) {
            if (stop <= o) {
                continue;
            }
            if (stop - o > room) {
                break;
            }
            best = stop;
        }
        if (this.contentHeight > o && this.contentHeight - o <= this.height) {
            best = this.contentHeight;
        }
        return best < 0 ? bottom : this.y + best - o;
    }

    public void layout() {
        int o = (int) Math.round(this.offset);
        int bottom = this.clipBottom();
        for (Entry e : this.entries) {
            AbstractWidget w = e.widget();
            w.setY(e.baseY() - o);
            w.visible = w.getY() + w.getHeight() > this.y && w.getY() < bottom;
        }
    }

    public boolean reveal(AbstractWidget widget) {
        for (Entry e : this.entries) {
            if (e.widget() != widget) {
                continue;
            }
            int contentY = e.baseY() - this.y;
            int o = this.offset();
            int bottom = this.clipBottom() - this.y;
            if (contentY >= o && contentY + widget.getHeight() <= o + bottom) {
                return true;
            }
            double target = Math.max(0.0, Math.min(this.maxOffset(), contentY - this.height / 3.0));
            if (!this.stops.isEmpty()) {
                Integer stop = this.stops.floor((int) Math.round(target));
                target = stop == null ? 0 : stop;
                if (contentY < target) {
                    Integer lower = this.stops.floor(contentY);
                    target = lower == null ? 0 : lower;
                }
            }
            this.offset = Math.max(0.0, Math.min(this.maxOffset(), target));
            this.layout();
            return true;
        }
        return false;
    }

    public boolean isVisible(int contentY, int h) {
        int top = this.top() + contentY;
        return top + h > this.y && top < this.clipBottom();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (this.maxOffset() <= 0 || !this.overScrollbar(mouseX, mouseY)) {
            return false;
        }
        this.draggingThumb = true;
        this.dragTo(mouseY);
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (!this.draggingThumb) {
            return false;
        }
        this.dragTo(mouseY);
        return true;
    }

    public void mouseReleased() {
        this.draggingThumb = false;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= this.x + this.width - 5 && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
    }

    private void dragTo(double mouseY) {
        int thumb = this.thumbHeight();
        double t = (mouseY - this.y - thumb / 2.0) / Math.max(1.0, this.height - thumb);
        this.offset = this.snap(Math.max(0.0, Math.min(1.0, t)) * this.maxOffset());
        this.layout();
    }

    private int thumbHeight() {
        if (this.contentHeight <= 0) {
            return this.height;
        }
        return Math.max(12, (int) ((long) this.height * this.height / this.contentHeight));
    }

    public void extractScrollbar(GuiGraphicsExtractor g) {
        if (this.maxOffset() <= 0) {
            return;
        }
        ThemeDraw.scrollbar(g, this.x + this.width - Metrics.SCROLLBAR_WIDTH, this.y, this.height, this.contentHeight, this.offset, this.draggingThumb);
    }
}
