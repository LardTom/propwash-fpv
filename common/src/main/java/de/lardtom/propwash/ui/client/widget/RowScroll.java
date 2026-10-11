package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class RowScroll {
    public static final int NONE = -1;
    private static final int GRAB_BEFORE = 2;
    private static final int GRAB_AFTER = 1;

    private int barX;
    private int top;
    private int rowHeight = 1;
    private int rows;
    private int visible;
    private boolean dragging;

    public void layout(int barX, int top, int rowHeight, int rows, int visible) {
        this.barX = barX;
        this.top = top;
        this.rowHeight = Math.max(1, rowHeight);
        this.rows = Math.max(0, rows);
        this.visible = Math.max(0, visible);
        if (!this.scrollable()) {
            this.dragging = false;
        }
    }

    public boolean scrollable() {
        return this.visible > 0 && this.rows > this.visible;
    }

    public int max() {
        return ScrollMath.rowMax(this.rows, this.visible);
    }

    public boolean dragging() {
        return this.dragging;
    }

    public ScreenRectangle track() {
        return new ScreenRectangle(this.barX, this.top, Metrics.SCROLLBAR_WIDTH, this.visible * this.rowHeight);
    }

    public boolean overBar(double mouseX, double mouseY) {
        return this.scrollable() && mouseX >= this.barX - GRAB_BEFORE && mouseX < this.barX + Metrics.SCROLLBAR_WIDTH + GRAB_AFTER
                && mouseY >= this.top && mouseY < this.top + this.visible * this.rowHeight;
    }

    public int grab(double mouseX, double mouseY) {
        if (!this.overBar(mouseX, mouseY)) {
            return NONE;
        }
        this.dragging = true;
        return this.rowAt(mouseY);
    }

    public int drag(double mouseY) {
        return this.dragging ? this.rowAt(mouseY) : NONE;
    }

    public void release() {
        this.dragging = false;
    }

    public int key(int key, int first) {
        if (!this.scrollable()) {
            return NONE;
        }
        int next;
        if (key == InputConstants.KEY_PAGEDOWN) {
            next = ScrollMath.rowPage(first, 1, this.rows, this.visible);
        } else if (key == InputConstants.KEY_PAGEUP) {
            next = ScrollMath.rowPage(first, -1, this.rows, this.visible);
        } else if (key == InputConstants.KEY_END) {
            next = this.max();
        } else if (key == InputConstants.KEY_HOME) {
            next = 0;
        } else {
            return NONE;
        }
        return next == first ? NONE : next;
    }

    public void extract(GuiGraphicsExtractor g, int first) {
        if (this.scrollable()) {
            ThemeDraw.scrollbar(g, this.barX, this.top, this.visible * this.rowHeight, this.rows * this.rowHeight, first * (double) this.rowHeight,
                    this.dragging);
        }
    }

    private int rowAt(double mouseY) {
        return ScrollMath.rowDrag(mouseY - this.top, this.rows, this.visible, this.rowHeight);
    }
}
