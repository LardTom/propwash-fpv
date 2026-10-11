package de.lardtom.propwash.content.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetViews;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WorkbenchMoreScreen extends Screen implements ThemedScreen, WidgetViews {
    private static final int WIDTH = 220;
    private static final int ROW = Metrics.CONTROL_HEIGHT + 4;

    private final Screen parent;
    private final DroneWorkbenchMenu menu;
    private final List<WorkbenchHost.Entry> entries;
    private final List<FlatButton> buttons = new ArrayList<>();
    private @Nullable ContentScroll scroll;
    private int left;
    private int top;
    private int right;
    private int bottom;

    WorkbenchMoreScreen(Screen parent, DroneWorkbenchMenu menu, List<WorkbenchHost.Entry> entries) {
        super(Component.translatable("propwash.parts.workbench.more.tooltip"));
        this.parent = parent;
        this.menu = menu;
        this.entries = List.copyOf(entries);
    }

    @Override
    protected void init() {
        double offset = this.scroll == null ? 0.0 : this.scroll.offset();
        this.buttons.clear();
        this.scroll = null;
        int need = ThemeDraw.brandedTitleX(this.font, 0) + this.font.width(this.title) + Metrics.PADDING;
        for (WorkbenchHost.Entry entry : this.entries) {
            need = Math.max(need, this.font.width(entry.label()) + 2 * Metrics.PADDING + Metrics.SCROLLBAR_WIDTH + 14);
        }
        int width = Math.min(Math.max(WIDTH, need), this.width - 8);
        int listHeight = this.entries.size() * ROW;
        int chrome = Metrics.HEADER_HEIGHT + 2 * Metrics.PADDING + ROW;
        int height = Math.min(chrome + listHeight, this.height - 8);
        this.left = (this.width - width) / 2;
        this.right = this.left + width;
        this.top = Math.max(4, (this.height - height) / 2);
        this.bottom = this.top + height;
        int x = this.left + Metrics.PADDING;
        int w = width - 2 * Metrics.PADDING;
        int listTop = this.top + Metrics.HEADER_HEIGHT + Metrics.PADDING;
        int listBottom = this.bottom - Metrics.PADDING - ROW;
        boolean scrolls = listTop + listHeight > listBottom;
        int buttonWidth = scrolls ? w - Metrics.SCROLLBAR_WIDTH - 2 : w;
        int y = listTop;
        for (WorkbenchHost.Entry entry : this.entries) {
            this.buttons.add(this.addWidget(new FlatButton(x, y, buttonWidth, Metrics.CONTROL_HEIGHT, entry.label(), FlatButton.Style.NORMAL,
                    () -> {
                        this.onClose();
                        entry.onPress().run();
                    })));
            y += ROW;
        }
        this.addRenderableWidget(new FlatButton(x, listBottom + 4, w, Metrics.CONTROL_HEIGHT, Component.translatable("gui.back"),
                FlatButton.Style.NORMAL, this::onClose));
        if (scrolls) {
            ContentScroll scroll = new ContentScroll(new ScreenRectangle(this.left, listTop, width - Metrics.PADDING + 2, Math.max(0, listBottom - listTop)),
                    offset);
            scroll.adopt(this.buttons);
            scroll.contentBottom(y - 4);
            this.scroll = scroll;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title, this.right - Metrics.PADDING);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        ContentScroll scroll = this.scroll;
        boolean inside = scroll == null || scroll.view().containsPoint(mouseX, mouseY);
        if (scroll != null) {
            ScreenRectangle view = scroll.view();
            g.enableScissor(view.left(), view.top(), view.right(), view.bottom());
        }
        for (FlatButton button : this.buttons) {
            button.extractRenderState(g, inside ? mouseX : -1, inside ? mouseY : -1, a);
        }
        if (scroll != null) {
            scroll.extractScrollbar(g);
            g.disableScissor();
        }
        for (int i = 0; i < this.buttons.size(); i++) {
            FlatButton button = this.buttons.get(i);
            Component tooltip = this.entries.get(i).tooltip();
            if (inside && button.isHovered() && ThemeText.shows(tooltip)) {
                ThemeText.tooltipLines(g, this.font, List.of(this.entries.get(i).label(), tooltip), mouseX, mouseY);
            }
        }
    }

    @Override
    public @Nullable ScreenRectangle widgetView(AbstractWidget widget) {
        return this.scroll != null && this.buttons.contains(widget) ? this.scroll.view() : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.scroll != null && this.scroll.wheel(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ContentScroll scroll = this.scroll;
        if (scroll != null && scroll.mouseClicked(event.x(), event.y())) {
            return true;
        }
        if (scroll != null && !scroll.view().containsPoint((int) event.x(), (int) event.y())) {
            for (FlatButton button : this.buttons) {
                if (button.isMouseOver(event.x(), event.y())) {
                    return false;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.scroll != null && this.scroll.mouseDragged(event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scroll != null) {
            this.scroll.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        ContentScroll scroll = this.scroll;
        if (scroll == null) {
            return false;
        }
        return switch (event.key()) {
            case InputConstants.KEY_PAGEDOWN -> scroll.page(1);
            case InputConstants.KEY_PAGEUP -> scroll.page(-1);
            case InputConstants.KEY_END -> scroll.edge(true);
            case InputConstants.KEY_HOME -> scroll.edge(false);
            default -> false;
        };
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        if (this.scroll != null && focused instanceof FlatButton button && this.buttons.contains(button)) {
            this.scroll.reveal(button);
        }
    }

    @Override
    public void tick() {
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.containerMenu != this.menu) {
            this.minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
