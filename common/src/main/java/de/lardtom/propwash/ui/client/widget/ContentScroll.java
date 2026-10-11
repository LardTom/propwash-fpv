package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ContentScroll {
    public static final int BOTTOM_PADDING = 4;
    private static final int REVEAL_MARGIN = 6;
    private static final int GRAB_WIDTH = 6;

    private final ScreenRectangle view;
    private final Map<AbstractWidget, Integer> bases = new IdentityHashMap<>();
    private int contentHeight;
    private double offset;
    private boolean dragging;
    private boolean resting;

    public ContentScroll(ScreenRectangle view, double offset) {
        this.view = view;
        this.offset = Math.max(0.0, offset);
    }

    public ScreenRectangle view() {
        return this.view;
    }

    public int offset() {
        return (int) Math.round(this.offset);
    }

    public int maxOffset() {
        return ScrollMath.maxOffset(this.contentHeight, this.view.height());
    }

    public boolean scrollable() {
        return this.maxOffset() > 0;
    }

    public boolean above() {
        return this.offset() > 0;
    }

    public boolean below() {
        return this.offset() < this.maxOffset();
    }

    public int contentHeight() {
        return this.contentHeight;
    }

    public void rest() {
        if (this.resting) {
            return;
        }
        this.resting = true;
        this.bases.forEach(AbstractWidget::setY);
    }

    public void adopt(Collection<? extends AbstractWidget> widgets) {
        this.resting = false;
        this.bases.clear();
        for (AbstractWidget widget : widgets) {
            this.bases.put(widget, widget.getY());
        }
        this.place();
    }

    public int widgetBottom() {
        int bottom = this.view.top();
        for (Map.Entry<AbstractWidget, Integer> entry : this.bases.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (widget.visible) {
                bottom = Math.max(bottom, entry.getValue() + widget.getHeight());
            }
        }
        return bottom;
    }

    public void contentBottom(int bottom) {
        this.contentHeight = Math.max(0, bottom + BOTTOM_PADDING - this.view.top());
        if (this.contentHeight <= this.view.height() + BOTTOM_PADDING) {
            this.contentHeight = Math.min(this.contentHeight, this.view.height());
        }
        double clamped = ScrollMath.clamp(this.offset, this.contentHeight, this.view.height());
        if (clamped != this.offset) {
            this.offset = clamped;
            this.place();
        }
    }

    private void place() {
        if (this.resting) {
            return;
        }
        int shift = this.offset();
        this.bases.forEach((widget, base) -> widget.setY(base - shift));
    }

    private boolean moveTo(double target) {
        double clamped = ScrollMath.clamp(target, this.contentHeight, this.view.height());
        if (Math.round(clamped) == Math.round(this.offset)) {
            this.offset = clamped;
            return false;
        }
        this.offset = clamped;
        this.place();
        return true;
    }

    public boolean wheel(double notches) {
        if (!this.scrollable()) {
            return false;
        }
        this.moveTo(ScrollMath.wheel(this.offset, notches, this.contentHeight, this.view.height()));
        return true;
    }

    public boolean page(int direction) {
        return this.scrollable() && this.moveTo(ScrollMath.page(this.offset, direction, this.contentHeight, this.view.height()));
    }

    public boolean edge(boolean end) {
        return this.scrollable() && this.moveTo(end ? this.maxOffset() : 0.0);
    }

    public boolean reveal(AbstractWidget widget) {
        Integer base = this.bases.get(widget);
        if (base == null) {
            return false;
        }
        this.moveTo(ScrollMath.reveal(this.offset, base - this.view.top(), widget.getHeight(), this.contentHeight, this.view.height(), REVEAL_MARGIN));
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!this.scrollable() || mouseX < this.view.right() - GRAB_WIDTH || mouseX >= this.view.right() || mouseY < this.view.top()
                || mouseY >= this.view.bottom()) {
            return false;
        }
        this.dragging = true;
        this.moveTo(ScrollMath.dragOffset(mouseY - this.view.top(), this.contentHeight, this.view.height()));
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (!this.dragging) {
            return false;
        }
        this.moveTo(ScrollMath.dragOffset(mouseY - this.view.top(), this.contentHeight, this.view.height()));
        return true;
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public void extractScrollbar(GuiGraphicsExtractor g) {
        if (!this.scrollable()) {
            return;
        }
        ThemeDraw.scrollbar(g, this.view.right() - Metrics.SCROLLBAR_WIDTH, this.view.top(), this.view.height(), this.contentHeight, this.offset,
                this.dragging);
    }
}
