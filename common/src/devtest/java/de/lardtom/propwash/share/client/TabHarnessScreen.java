package de.lardtom.propwash.share.client;

import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TabHarnessScreen extends Screen implements ThemedScreen, ConfigTabHost {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 250;

    private final ConfigTab tab;
    private long frames;
    private int left;
    private int top;

    public TabHarnessScreen(ConfigTab tab) {
        super(tab.title());
        this.tab = tab;
    }

    public ConfigTab tab() {
        return this.tab;
    }

    public long frames() {
        return this.frames;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, WIDTH);
        int h = Math.min(this.height - 12, HEIGHT);
        this.left = (this.width - w) / 2;
        this.top = (this.height - h) / 2;
        this.tab.attach(this, this.left + Metrics.PADDING, this.top + Metrics.HEADER_HEIGHT + Metrics.PADDING, w - 2 * Metrics.PADDING,
                h - Metrics.HEADER_HEIGHT - 2 * Metrics.PADDING);
    }

    @Override
    public <T extends AbstractWidget> T addWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public Minecraft minecraft() {
        return this.minecraft;
    }

    @Override
    public Screen screen() {
        return this;
    }

    @Override
    public void rebuild() {
        this.rebuildWidgets();
    }

    @Override
    public void changed() {
    }

    @Override
    public void tick() {
        this.tab.tick();
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
        this.frames++;
        int w = Math.min(this.width - 16, WIDTH);
        int h = Math.min(this.height - 12, HEIGHT);
        ThemeDraw.window(g, this.left, this.top, this.left + w, this.top + h);
        ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.left + w, Metrics.HEADER_HEIGHT, Component.translatable(
                "propwash.share.config.title"), this.left + w - 8);
        this.tab.extract(g, mouseX, mouseY, a);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.left, this.top, this.left + w, this.top + h);
    }
}
