package de.lardtom.propwash.ui.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public abstract class ConfigTab {
    public enum Storage {
        DRONE,
        GAME,
        MIXED
    }

    protected ConfigTabHost host;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    private int builtBottom = Integer.MIN_VALUE;
    private int drawnBottom = Integer.MIN_VALUE;
    private boolean drawing;

    public abstract String id();

    public abstract Component title();

    public int order() {
        return 1000;
    }

    public final void attach(ConfigTabHost host, int x, int y, int width, int height) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.builtBottom = Integer.MIN_VALUE;
        this.drawnBottom = Integer.MIN_VALUE;
        this.drawing = false;
        this.build();
    }

    protected void reportBottom(int bottomY) {
        if (this.drawing) {
            this.drawnBottom = Math.max(this.drawnBottom, bottomY);
        } else {
            this.builtBottom = Math.max(this.builtBottom, bottomY);
        }
    }

    public int reportedBottom() {
        return Math.max(this.builtBottom, this.drawnBottom);
    }

    public void frame(boolean begin) {
        this.drawing = begin;
        if (begin) {
            this.drawnBottom = Integer.MIN_VALUE;
        }
    }

    public boolean scrollsItself() {
        return false;
    }

    protected abstract void build();

    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    public void tick() {
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean cancelAction() {
        return false;
    }

    public boolean filesDropped(List<Path> files) {
        return false;
    }

    public void resetDefaults() {
    }

    public boolean hasDefaults() {
        return true;
    }

    public boolean readOnly() {
        return false;
    }

    public Storage storage() {
        return Storage.GAME;
    }

    public String mixedStorageKey() {
        return "propwash.ui.footer.mixed";
    }

    public String readOnlyKey() {
        return "propwash.ui.footer.read_only";
    }

    public boolean capturesInput() {
        return false;
    }

    public boolean reveal(AbstractWidget widget) {
        return false;
    }

    public boolean contentAbove() {
        return false;
    }

    public boolean contentBelow() {
        return false;
    }

    public boolean rowSnapped() {
        return false;
    }

    public int clipBottom() {
        return Integer.MAX_VALUE;
    }

    public void save() {
    }

    public void discard() {
    }
}
