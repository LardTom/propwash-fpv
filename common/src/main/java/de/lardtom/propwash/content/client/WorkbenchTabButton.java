package de.lardtom.propwash.content.client;

import de.lardtom.propwash.content.WorkbenchTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class WorkbenchTabButton extends PanelWidget {
    private static final int OUTLINE = Palette.SHADOW;
    private static final int BEVEL = Palette.BORDER;
    private static final int ACTIVE = Palette.HEADER;
    private static final int IDLE = Palette.BACKGROUND;
    private static final int IDLE_HOVER = Palette.SIDEBAR;

    private final WorkbenchTab tab;
    private final Supplier<WorkbenchTab> current;
    private final Consumer<WorkbenchTab> select;

    public WorkbenchTabButton(int x, int y, int width, int height, Component label, WorkbenchTab tab, Supplier<WorkbenchTab> current,
                              Consumer<WorkbenchTab> select) {
        super(x, y, width, height, label);
        this.tab = tab;
        this.current = current;
        this.select = select;
    }

    public WorkbenchTab tab() {
        return this.tab;
    }

    public boolean selected() {
        return this.current.get() == this.tab;
    }

    public void press() {
        this.select.accept(this.tab);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive() && event.isSelection()) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.press();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        WidgetState state = this.state();
        boolean on = this.selected();
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height + (on ? 2 : 0);
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, OUTLINE);
        g.fill(x0, y0 + 1, x0 + 1, y1, OUTLINE);
        g.fill(x1 - 1, y0 + 1, x1, y1, OUTLINE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, on ? Palette.ACCENT : BEVEL);
        g.fill(x0 + 1, y0 + 2, x0 + 2, y1, BEVEL);
        g.fill(x0 + 2, y0 + 2, x1 - 1, y1, on ? ACTIVE : state.hot() ? IDLE_HOVER : IDLE);
        if (state.focused() && !on) {
            g.fill(x0 + 2, y0 + 2, x1 - 1, y0 + 3, Palette.ACCENT_DIM);
        }
        int color = on ? Palette.ACCENT : state.hot() ? Palette.TEXT : Palette.TEXT_MUTED;
        ThemeText.line(g, this.font(), this.getMessage(), x0 + 4, x1 - 4, Metrics.textY(y0 + 1, this.height - 1), color, ThemeText.Align.CENTER,
                state.hot());
    }
}
