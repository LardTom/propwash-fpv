package de.lardtom.propwash.guide.client.screen;

import de.lardtom.propwash.guide.client.render.SceneAssets;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public final class SceneList {
    public static final int CARD_HEIGHT = 50;
    public static final int CARD_GAP = 4;
    private static final int ICON_BOX = 26;
    private static final int TITLE_Y = 5;
    private static final int META_GAP = 3;
    private static final int HINT_ROW = ThemeDraw.MORE_BELOW_HEIGHT + 4;
    private static final int GRIP_WIDTH = Metrics.SCROLLBAR_WIDTH + 3;

    private final Consumer<Scene> onOpen;
    private List<Scene> scenes = List.of();
    private int x;
    private int y;
    private int width;
    private int height;
    private int firstRow;
    private int columns = 1;
    private int cardHeight = CARD_HEIGHT;
    private int titleLines = 1;
    private int descriptionLines = 2;
    private boolean dragging;

    public SceneList(Consumer<Scene> onOpen) {
        this.onOpen = onOpen;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = List.copyOf(scenes);
        this.firstRow = 0;
        this.dragging = false;
        if (this.width > 0) {
            this.measure();
        }
    }

    public List<Scene> scenes() {
        return this.scenes;
    }

    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.measure();
        this.firstRow = Math.clamp(this.firstRow, 0, this.maxRow());
    }

    private void measure() {
        Font font = Minecraft.getInstance().font;
        int wanted = this.width >= 2 * Metrics.COLUMN_MIN + CARD_GAP ? 2 : 1;
        for (int count = wanted; count >= 1; count--) {
            int textWidth = Math.max(20, textWidth(this.cardWidth(count)));
            int titles = 1;
            int descriptions = 1;
            for (Scene scene : this.scenes) {
                titles = Math.max(titles, font.split(title(scene), textWidth).size());
                descriptions = Math.max(descriptions, font.split(Component.translatable(scene.descriptionKey()), textWidth).size());
            }
            this.columns = count;
            this.titleLines = titles;
            this.descriptionLines = Math.max(2, descriptions);
            if (titles == 1 && descriptions <= 2) {
                break;
            }
        }
        this.cardHeight = cardHeight(this.titleLines, this.descriptionLines, ThemeText.smallLineHeight());
    }

    public static int cardHeight(int titleLines, int descriptionLines, int metaHeight) {
        return Math.max(CARD_HEIGHT, metaY(titleLines, descriptionLines) + metaHeight + 3);
    }

    private int pitch() {
        return this.cardHeight + CARD_GAP;
    }

    public int cardHeight() {
        return this.cardHeight;
    }

    public int heightFor(int rows) {
        return Math.max(0, rows * this.pitch() - CARD_GAP);
    }

    private static Component title(Scene scene) {
        return Component.translatable(scene.titleKey()).withStyle(ChatFormatting.BOLD);
    }

    private static int textWidth(int cardWidth) {
        return cardWidth - 6 - 7 - ICON_BOX - 7;
    }

    public int visibleHeight() {
        return heightFor(this.visibleRows());
    }

    public int footprint() {
        return this.visibleHeight() + (this.maxRow() > 0 ? HINT_ROW : 0);
    }

    public int rows() {
        return (this.scenes.size() + this.columns() - 1) / this.columns();
    }

    private int visibleRows() {
        int all = (this.height + CARD_GAP) / this.pitch();
        if (all >= this.rows()) {
            return Math.max(1, this.rows());
        }
        return Math.max(1, (this.height - HINT_ROW + CARD_GAP) / this.pitch());
    }

    private int maxRow() {
        return Math.max(0, this.rows() - this.visibleRows());
    }

    private int columns() {
        return this.columns;
    }

    private int cardWidth() {
        return this.cardWidth(this.columns);
    }

    private int cardWidth(int count) {
        return (this.width - (count - 1) * CARD_GAP - Metrics.SCROLLBAR_WIDTH - 3) / count;
    }

    private int contentHeight() {
        return heightFor(this.rows());
    }

    private int cardX(int index) {
        return this.x + (index % this.columns()) * (this.cardWidth() + CARD_GAP);
    }

    private int cardY(int index) {
        return this.y + (index / this.columns() - this.firstRow) * this.pitch();
    }

    private boolean rowShown(int index) {
        int row = index / this.columns();
        return row >= this.firstRow && row < this.firstRow + this.visibleRows();
    }

    public @Nullable Scene sceneAt(double mouseX, double mouseY) {
        if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height) {
            return null;
        }
        for (int i = 0; i < this.scenes.size(); i++) {
            if (!this.rowShown(i)) {
                continue;
            }
            int cx = this.cardX(i);
            int cy = this.cardY(i);
            if (mouseX >= cx && mouseX < cx + this.cardWidth() && mouseY >= cy && mouseY < cy + this.cardHeight) {
                return this.scenes.get(i);
            }
        }
        return null;
    }

    public boolean click(double mouseX, double mouseY) {
        Scene scene = this.sceneAt(mouseX, mouseY);
        if (scene == null) {
            return false;
        }
        this.onOpen.accept(scene);
        return true;
    }

    public boolean overScrollbar(double mouseX, double mouseY) {
        int right = this.x + this.width;
        return this.maxRow() > 0 && mouseX >= right - GRIP_WIDTH && mouseX < right && mouseY >= this.y && mouseY < this.y + this.visibleHeight();
    }

    public boolean grab(double mouseX, double mouseY) {
        if (!this.overScrollbar(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;
        this.dragTo(mouseY);
        return true;
    }

    public boolean drag(double mouseY) {
        if (!this.dragging) {
            return false;
        }
        this.dragTo(mouseY);
        return true;
    }

    public boolean release() {
        boolean was = this.dragging;
        this.dragging = false;
        return was;
    }

    public boolean dragging() {
        return this.dragging;
    }

    private void dragTo(double mouseY) {
        this.firstRow = ScrollMath.rowDrag(mouseY - this.y, this.rows(), this.visibleRows(), this.pitch());
    }

    public boolean page(int direction) {
        if (this.maxRow() <= 0) {
            return false;
        }
        this.firstRow = ScrollMath.rowPage(this.firstRow, direction, this.rows(), this.visibleRows());
        return true;
    }

    public boolean edge(boolean end) {
        if (this.maxRow() <= 0) {
            return false;
        }
        this.firstRow = end ? this.maxRow() : 0;
        return true;
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height || this.maxRow() <= 0) {
            return false;
        }
        int steps = ScrollMath.rowSteps(amount, this.rows(), this.visibleRows(), this.pitch());
        this.firstRow = Math.clamp(this.firstRow + (amount > 0 ? -steps : steps), 0, this.maxRow());
        return true;
    }

    public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (this.scenes.isEmpty()) {
            ThemeText.text(g, font, Component.translatable("propwash.guide.index.empty"), this.x, this.y + 4, Palette.TEXT_FAINT);
            return;
        }
        Scene hovered = this.dragging ? null : this.sceneAt(mouseX, mouseY);
        int w = this.cardWidth();
        for (int i = 0; i < this.scenes.size(); i++) {
            if (this.rowShown(i)) {
                this.card(g, font, this.scenes.get(i), this.cardX(i), this.cardY(i), w, this.scenes.get(i) == hovered);
            }
        }
        if (this.maxRow() > 0) {
            ThemeDraw.scrollbar(g, this.x + this.width - Metrics.SCROLLBAR_WIDTH, this.y, this.visibleHeight(), this.contentHeight(),
                    (double) this.firstRow * this.pitch(), this.dragging);
            if (this.firstRow < this.maxRow() && this.visibleHeight() + 4 + ThemeDraw.MORE_BELOW_HEIGHT <= this.height) {
                ThemeDraw.moreBelow(g, font, this.x + 2, this.y + this.visibleHeight() + 4, this.width - 4);
            }
        }
    }

    private void card(GuiGraphicsExtractor g, Font font, Scene scene, int x0, int y0, int width, boolean hovered) {
        int x1 = x0 + width;
        int y1 = y0 + this.cardHeight;
        ThemeDraw.framedBox(g, x0, y0, x1, y1, hovered ? Palette.FIELD_HOVER : Palette.PANEL, hovered ? Palette.ACCENT_DIM : Palette.BORDER);
        g.fill(x0, y0 + 1, x0 + 2, y1 - 1, hovered ? Palette.ACCENT : Palette.ACCENT_DIM);
        int bx = x0 + 7;
        int by = y0 + (this.cardHeight - ICON_BOX) / 2;
        ThemeDraw.iconTile(g, bx, by, bx + ICON_BOX, by + ICON_BOX);
        g.item(SceneAssets.item(scene.icon()), bx + (ICON_BOX - 16) / 2, by + (ICON_BOX - 16) / 2);
        int tx = bx + ICON_BOX + 7;
        int textWidth = x1 - 6 - tx;
        List<FormattedCharSequence> titles = font.split(title(scene), Math.max(20, textWidth));
        for (int i = 0; i < titles.size(); i++) {
            ThemeText.text(g, font, titles.get(i), tx, y0 + TITLE_Y + i * Metrics.LINE_HEIGHT, hovered ? Palette.ACCENT : Palette.TEXT);
        }
        int descriptionY = y0 + TITLE_Y + titles.size() * Metrics.LINE_HEIGHT + 1;
        List<FormattedCharSequence> lines = font.split(Component.translatable(scene.descriptionKey()), Math.max(20, textWidth));
        for (int i = 0; i < lines.size(); i++) {
            ThemeText.text(g, font, lines.get(i), tx, descriptionY + i * Metrics.LINE_HEIGHT, Palette.TEXT_MUTED);
        }
        Component meta = Component.translatable("propwash.guide.index.meta", scene.stepCount(), SceneScreen.clock(scene.duration()));
        ThemeText.small(g, font, meta.getString(), tx, y0 + metaY(titles.size(), lines.size()), Palette.TEXT_FAINT);
    }

    static int metaY(int titleLines, int descriptionLines) {
        return TITLE_Y + Math.max(1, titleLines) * Metrics.LINE_HEIGHT + 1 + Math.max(1, descriptionLines) * Metrics.LINE_HEIGHT + META_GAP;
    }
}
