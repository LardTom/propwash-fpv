package de.lardtom.propwash.ui.client.search;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public final class SearchBar {
    public static final int ROW = 13;
    public static final int LIMIT = SettingsSearch.DEFAULT_LIMIT;
    private static final int MAX_LENGTH = 48;
    private static final int MIN_DROPDOWN = 230;

    private final ConfiguratorScreen screen;
    private final Field field;
    private final Consumer<SettingsSearch.Hit> onPick;
    private @Nullable SettingsIndex index;
    private List<SettingsSearch.Hit> hits = List.of();
    private int selected;
    private boolean open;
    private int bottomLimit;

    public SearchBar(ConfiguratorScreen screen, Font font, int x, int y, int width, int height, String text, int bottomLimit,
                     Consumer<SettingsSearch.Hit> onPick) {
        this.screen = screen;
        this.onPick = onPick;
        this.bottomLimit = bottomLimit;
        this.field = new Field(this, font, x, y, width, height, Component.translatable("propwash.ui.search.narration"));
        this.field.setMaxLength(MAX_LENGTH);
        this.field.hint(Component.translatable("propwash.ui.search.hint", shortcutName()));
        this.field.setValue(text);
        this.field.setResponder(this::update);
    }

    public static Component shortcutName() {
        return Component.translatable(InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? "propwash.ui.search.shortcut.mac" : "propwash.ui.search.shortcut");
    }

    public static boolean isShortcut(KeyEvent event) {
        boolean modifier = event.hasControlDownWithQuirk() || event.hasControlDown();
        return modifier && !event.hasShiftDown() && !event.hasAltDown() && (event.shortcutKey() == 'f' || event.key() == InputConstants.KEY_F);
    }

    public TextField field() {
        return this.field;
    }

    public String text() {
        return this.field.getValue();
    }

    public boolean focused() {
        return this.field.isFocused();
    }

    public boolean open() {
        return this.open && this.field.isFocused() && !this.text().isBlank();
    }

    public List<SettingsSearch.Hit> hits() {
        return this.hits;
    }

    public int selected() {
        return this.selected;
    }

    public @Nullable SettingsIndex index() {
        return this.index;
    }

    public void select(int index) {
        if (!this.hits.isEmpty()) {
            this.selected = Math.floorMod(index, this.hits.size());
        }
    }

    public void focus() {
        this.screen.setFocused(this.field);
        this.field.moveCursorToEnd(false);
        this.field.setHighlightPos(0);
    }

    public void type(String text) {
        this.field.setValue(text);
    }

    private void focusChanged(boolean focused) {
        if (focused) {
            this.index = SettingsIndex.build(this.screen);
            this.open = true;
            this.update(this.text());
        } else {
            this.open = false;
        }
    }

    private void update(String query) {
        SettingsIndex current = this.index;
        if (current == null && this.field.isFocused()) {
            current = this.index = SettingsIndex.build(this.screen);
        }
        this.hits = current == null ? List.of() : current.search(query, LIMIT);
        this.selected = 0;
        this.open = this.field.isFocused();
    }

    public boolean keyPressed(KeyEvent event) {
        if (!this.field.isFocused()) {
            return false;
        }
        if (event.isDown()) {
            this.select(this.selected + 1);
            return true;
        }
        if (event.isUp()) {
            this.select(this.selected - 1);
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            this.select(event.key() == InputConstants.KEY_PAGEDOWN ? this.hits.size() - 1 : 0);
            return true;
        }
        if (event.isConfirmation()) {
            this.pick(this.selected);
            return true;
        }
        return false;
    }

    public boolean escape() {
        if (!this.field.isFocused()) {
            return false;
        }
        if (!this.text().isEmpty()) {
            this.field.setValue("");
            return true;
        }
        this.screen.setFocused(null);
        return true;
    }

    public void pick(int index) {
        if (!this.open() || index < 0 || index >= this.hits.size()) {
            return;
        }
        SettingsSearch.Hit hit = this.hits.get(index);
        this.open = false;
        this.onPick.accept(hit);
    }

    private int dropdownWidth() {
        return Math.max(this.field.getWidth() + 14, MIN_DROPDOWN);
    }

    private int dropdownRight() {
        return this.field.getX() + this.field.getWidth();
    }

    private int dropdownLeft() {
        return this.dropdownRight() - this.dropdownWidth();
    }

    private int dropdownTop() {
        return this.field.getY() + this.field.getHeight() + 2;
    }

    private int visibleRows() {
        int rows = Math.max(1, this.hits.size());
        int room = Math.max(1, (this.bottomLimit - this.dropdownTop() - 4) / ROW);
        return Math.min(rows, room);
    }

    private int dropdownBottom() {
        return this.dropdownTop() + 2 + this.visibleRows() * ROW + 2;
    }

    public boolean overDropdown(double mouseX, double mouseY) {
        return this.open() && mouseX >= this.dropdownLeft() && mouseX < this.dropdownRight() && mouseY >= this.dropdownTop()
                && mouseY < this.dropdownBottom();
    }

    private int rowAt(double mouseY) {
        int row = (int) Math.floor((mouseY - this.dropdownTop() - 2) / ROW);
        return row >= 0 && row < Math.min(this.hits.size(), this.visibleRows()) ? row : -1;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!this.overDropdown(mouseX, mouseY)) {
            return false;
        }
        int row = this.rowAt(mouseY);
        if (row >= 0) {
            this.pick(row);
        }
        return true;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        if (this.overDropdown(mouseX, mouseY)) {
            int row = this.rowAt(mouseY);
            if (row >= 0) {
                this.selected = row;
            }
        }
    }

    public void extractIcon(GuiGraphicsExtractor g) {
        int x = this.field.getX() - 11;
        int y = this.field.getY() + (this.field.getHeight() - 9) / 2;
        int color = this.field.isFocused() ? Palette.ACCENT : Palette.TEXT_FAINT;
        g.fill(x + 1, y, x + 5, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 5, color);
        g.fill(x + 5, y + 1, x + 6, y + 5, color);
        g.fill(x + 1, y + 5, x + 5, y + 6, color);
        g.fill(x + 5, y + 6, x + 7, y + 7, color);
        g.fill(x + 6, y + 7, x + 8, y + 8, color);
        g.fill(x + 7, y + 8, x + 9, y + 9, color);
    }

    public void extractDropdown(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (!this.open()) {
            return;
        }
        g.nextStratum();
        int x0 = this.dropdownLeft();
        int x1 = this.dropdownRight();
        int y0 = this.dropdownTop();
        int y1 = this.dropdownBottom();
        ThemeDraw.tooltipFrame(g, x0, y0, x1, y1);
        if (this.hits.isEmpty()) {
            ThemeText.fitted(g, font, Component.translatable("propwash.ui.search.none"), x0 + 6, y0 + 4, x1 - x0 - 12, Palette.TEXT_FAINT);
            return;
        }
        int rows = this.visibleRows();
        for (int i = 0; i < rows; i++) {
            SettingsSearch.Hit hit = this.hits.get(i);
            int ry = y0 + 2 + i * ROW;
            boolean active = i == this.selected;
            if (active) {
                g.fill(x0 + 1, ry, x1 - 1, ry + ROW, Palette.SELECTION);
                g.fill(x0 + 1, ry, x0 + 3, ry + ROW, Palette.ACCENT);
            }
            Component right = this.rightText(hit);
            int rightWidth = Math.min((x1 - x0) / 2, font.width(right));
            String rightShown = ThemeText.fit(font, right, rightWidth + 1);
            ThemeText.textRight(g, font, rightShown, x1 - 6, ry + 3, active ? Palette.TEXT_MUTED : Palette.TEXT_FAINT);
            int labelRoom = x1 - x0 - 12 - rightWidth - 8;
            this.extractLabel(g, font, hit, x0 + 7, ry + 3, labelRoom, active);
        }
    }

    private Component rightText(SettingsSearch.Hit hit) {
        SettingsSearch.Entry entry = hit.entry();
        if (entry.kind() == SettingsSearch.Kind.TAB) {
            return Component.translatable("propwash.ui.search.kind.tab");
        }
        return Component.literal(entry.tabTitle());
    }

    private void extractLabel(GuiGraphicsExtractor g, Font font, SettingsSearch.Hit hit, int x, int y, int room, boolean active) {
        String label = hit.entry().label();
        String shown = ThemeText.fit(font, label, room);
        int base = active ? Palette.TEXT : Palette.TEXT_MUTED;
        int start = hit.matchStart();
        int end = hit.matchEnd();
        boolean truncated = !shown.equals(label);
        if (start < 0 || end <= start || (truncated && end > shown.length() - 1)) {
            ThemeText.text(g, font, shown, x, y, base);
            return;
        }
        String before = shown.substring(0, Math.min(start, shown.length()));
        String match = shown.substring(Math.min(start, shown.length()), Math.min(end, shown.length()));
        String after = shown.substring(Math.min(end, shown.length()));
        ThemeText.text(g, font, before, x, y, base);
        int mx = x + font.width(before);
        ThemeText.text(g, font, match, mx, y, Palette.ACCENT);
        ThemeText.text(g, font, after, mx + font.width(match), y, base);
    }

    private static final class Field extends TextField {
        private final SearchBar owner;

        private Field(SearchBar owner, Font font, int x, int y, int width, int height, Component narration) {
            super(font, x, y, width, height, narration);
            this.owner = owner;
        }

        @Override
        public void setFocused(boolean focused) {
            boolean was = this.isFocused();
            super.setFocused(focused);
            if (was != focused) {
                this.owner.focusChanged(focused);
            }
        }
    }
}
