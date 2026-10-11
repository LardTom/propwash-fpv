package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FormLayout {
    private final int left;
    private final int top;
    private final int available;
    private final List<Step> steps = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private int columns;
    private int columnWidth;
    private int y;
    private int column;
    private boolean fitted;

    private record Section(Component title, int y) {
    }

    private record Step(@Nullable Component section, @Nullable AbstractWidget widget, boolean wide, int space) {
    }

    public FormLayout(int x, int y, int width) {
        this(x, y, width, Metrics.columns(width));
    }

    public FormLayout(int x, int y, int width, int columns) {
        this.left = x;
        this.top = y;
        this.y = y;
        this.available = width;
        this.columns = Math.max(1, columns);
        this.columnWidth = Metrics.columnWidth(width, this.columns);
    }

    public int left() {
        return this.left;
    }

    public int columns() {
        return this.columns;
    }

    public int columnWidth() {
        return this.columnWidth;
    }

    public int width() {
        return this.columns * this.columnWidth + (this.columns - 1) * Metrics.GAP;
    }

    public int columnX(int column) {
        return this.left + column * (this.columnWidth + Metrics.GAP);
    }

    public int cursor() {
        return this.column == 0 ? this.y : this.y + Metrics.ROW_HEIGHT;
    }

    public FormLayout section(Component title) {
        this.steps.add(new Step(title, null, false, 0));
        this.applySection(title);
        return this;
    }

    public <T extends AbstractWidget> T place(T widget) {
        this.steps.add(new Step(null, widget, false, 0));
        this.applyWidget(widget, false);
        return widget;
    }

    public <T extends AbstractWidget> T placeWide(T widget) {
        this.steps.add(new Step(null, widget, true, 0));
        this.applyWidget(widget, true);
        return widget;
    }

    public void endRow() {
        this.steps.add(new Step(null, null, false, 0));
        this.applyEndRow();
    }

    public void space(int pixels) {
        this.steps.add(new Step(null, null, false, pixels));
        this.applyEndRow();
        this.y += pixels;
    }

    public int bottom() {
        this.fit();
        return this.cursor();
    }

    public void fit() {
        if (this.fitted) {
            return;
        }
        this.fitted = true;
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (Step step : this.steps) {
            if (step.widget() != null && !step.wide()) {
                widest = Math.max(widest, needed(font, step.widget()));
            }
        }
        if (widest <= this.columnWidth) {
            return;
        }
        this.columns = 1;
        this.columnWidth = Math.max(1, Math.min(this.available, Math.max(Metrics.COLUMN_MAX, widest)));
        this.replay();
    }

    public static int needed(Font font, AbstractWidget widget) {
        Component message = widget.getMessage();
        ThemeText.Split split = widget instanceof LabeledValue labeled ? new ThemeText.Split(labeled.themeLabel(), labeled.themeValue())
                : ThemeText.splitLabel(message);
        if (split == null) {
            return font.width(message) + 2 * Metrics.PADDING;
        }
        int control = Math.max(Metrics.CONTROL_MIN, font.width(split.value()) + 2 * Metrics.ARROW_WIDTH + 8);
        return font.width(split.label()) + Metrics.LABEL_GAP + 2 + control;
    }

    private void replay() {
        this.sections.clear();
        this.y = this.top;
        this.column = 0;
        for (Step step : this.steps) {
            if (step.section() != null) {
                this.applySection(step.section());
            } else if (step.widget() != null) {
                this.applyWidget(step.widget(), step.wide());
            } else {
                this.applyEndRow();
                this.y += step.space();
            }
        }
    }

    private void applySection(Component title) {
        this.applyEndRow();
        if (this.y > this.top) {
            this.y += Metrics.SECTION_GAP;
        }
        this.sections.add(new Section(title, this.y));
        this.y += Metrics.SECTION_HEIGHT;
    }

    private void applyWidget(AbstractWidget widget, boolean wide) {
        if (wide) {
            this.applyEndRow();
            widget.setRectangle(this.width(), Metrics.CONTROL_HEIGHT, this.left, this.y);
            this.y += Metrics.ROW_HEIGHT;
            return;
        }
        widget.setRectangle(this.columnWidth, Metrics.CONTROL_HEIGHT, this.columnX(this.column), this.y);
        this.column++;
        if (this.column >= this.columns) {
            this.column = 0;
            this.y += Metrics.ROW_HEIGHT;
        }
    }

    private void applyEndRow() {
        if (this.column != 0) {
            this.column = 0;
            this.y += Metrics.ROW_HEIGHT;
        }
    }

    public void extract(GuiGraphicsExtractor g, Font font) {
        this.extract(g, font, 0);
    }

    public void extract(GuiGraphicsExtractor g, Font font, int offsetY) {
        this.fit();
        for (Section section : this.sections) {
            ThemeDraw.sectionTitle(g, font, section.title(), this.left, section.y() + offsetY, this.width());
        }
    }
}
