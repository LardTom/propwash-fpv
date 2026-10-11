package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class ScrollArea {
    public static final int GUTTER = Metrics.SCROLLBAR_WIDTH + 2;
    private static final int GRAB_WIDTH = 6;
    private static final int REVEAL_MARGIN = 2;

    private ScreenRectangle view = ScreenRectangle.empty();
    private int contentHeight;
    private double offset;
    private boolean dragging;

    public void view(int x0, int y0, int x1, int y1) {
        this.view = new ScreenRectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
        this.offset = ScrollMath.clamp(this.offset, this.contentHeight, this.view.height());
    }

    public ScreenRectangle view() {
        return this.view;
    }

    public int left() {
        return this.view.left();
    }

    public int top() {
        return this.view.top();
    }

    public int innerRight() {
        return this.view.right() - GUTTER;
    }

    public int innerWidth() {
        return Math.max(1, this.view.width() - GUTTER);
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

    public boolean contains(double x, double y) {
        return x >= this.view.left() && x < this.view.right() && y >= this.view.top() && y < this.view.bottom();
    }

    public int contentY(double screenY) {
        return (int) Math.floor(screenY) + this.offset();
    }

    public int hoverY(int mouseX, int mouseY) {
        return this.contains(mouseX, mouseY) ? mouseY + this.offset() : Integer.MIN_VALUE;
    }

    public void begin(GuiGraphicsExtractor g) {
        g.enableScissor(this.view.left(), this.view.top(), this.view.right(), this.view.bottom());
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -this.offset());
    }

    public void end(GuiGraphicsExtractor g, int contentBottom) {
        g.pose().popMatrix();
        this.contentHeight = Math.max(0, contentBottom - this.view.top());
        this.offset = ScrollMath.clamp(this.offset, this.contentHeight, this.view.height());
        if (this.scrollable()) {
            ThemeDraw.scrollbar(g, this.view.right() - Metrics.SCROLLBAR_WIDTH, this.view.top(), this.view.height(), this.contentHeight, this.offset,
                    this.dragging);
        }
        g.disableScissor();
    }

    public void reset() {
        this.offset = 0.0;
    }

    public boolean wheel(double mouseX, double mouseY, double notches) {
        if (!this.scrollable() || !this.contains(mouseX, mouseY)) {
            return false;
        }
        this.offset = ScrollMath.wheel(this.offset, notches, this.contentHeight, this.view.height());
        return true;
    }

    public boolean key(int key) {
        if (!this.scrollable()) {
            return false;
        }
        double before = this.offset;
        if (key == InputConstants.KEY_PAGEDOWN) {
            this.offset = ScrollMath.page(this.offset, 1, this.contentHeight, this.view.height());
        } else if (key == InputConstants.KEY_PAGEUP) {
            this.offset = ScrollMath.page(this.offset, -1, this.contentHeight, this.view.height());
        } else if (key == InputConstants.KEY_END) {
            this.offset = this.maxOffset();
        } else if (key == InputConstants.KEY_HOME) {
            this.offset = 0.0;
        } else {
            return false;
        }
        return this.offset != before;
    }

    public void reveal(int contentTop, int height) {
        this.offset = ScrollMath.reveal(this.offset, contentTop - this.view.top(), height, Math.max(this.contentHeight, contentTop - this.view.top() + height),
                this.view.height(), REVEAL_MARGIN);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!this.scrollable() || mouseX < this.view.right() - GRAB_WIDTH || !this.contains(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;
        this.offset = ScrollMath.dragOffset(mouseY - this.view.top(), this.contentHeight, this.view.height());
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (!this.dragging) {
            return false;
        }
        this.offset = ScrollMath.dragOffset(mouseY - this.view.top(), this.contentHeight, this.view.height());
        return true;
    }

    public void mouseReleased() {
        this.dragging = false;
    }
}
