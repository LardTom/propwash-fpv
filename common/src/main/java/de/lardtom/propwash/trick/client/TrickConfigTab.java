package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.trick.TrickCatalog;
import de.lardtom.propwash.trick.TrickConfig;
import de.lardtom.propwash.trick.TrickFamily;
import de.lardtom.propwash.trick.TrickMode;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class TrickConfigTab extends ConfigTab {
    public static final int ORDER = 610;
    public static final int MAX_COLUMNS = 3;
    public static final int TOOLTIP_WIDTH = 220;
    private static final int ROW = Metrics.ROW_HEIGHT;
    private static final int LINE = Metrics.LINE_HEIGHT;
    private static final int GAP = Metrics.GAP;
    private static final int ALL_FAMILIES = -1;
    private static final Function<String, String> TEXT = key -> Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : "";

    private @Nullable TrickConfig working;
    private @Nullable CycleButton<TrickMode> modeButton;
    private @Nullable TextField searchBox;
    private @Nullable CycleButton<Integer> familyButton;
    private @Nullable ScrollPanel scroll;
    private double scrollOffset;
    private String query = "";
    private int family = ALL_FAMILIES;
    private final List<FormattedCharSequence> help = new ArrayList<>();
    private final List<FormattedCharSequence> rules = new ArrayList<>();
    private List<TrickType> visible = TrickType.REPERTOIRE;
    private List<TrickCatalog.Section> sections = List.of();
    private int helpTop;
    private int buttonTop;
    private int tableTop;
    private int statusTop = -1;
    private int listTop;
    private int rulesTop;
    private int rows;
    private int columns = 1;
    private int columnWidth;
    private int innerWidth;

    public TrickConfigTab() {
    }

    @Override
    public String id() {
        return "tricks";
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.trick.tab.title");
    }

    @Override
    public int order() {
        return ORDER;
    }

    public TrickConfig working() {
        if (this.working == null) {
            this.working = TrickClient.CONFIG.get().copy();
        }
        return this.working;
    }

    public @Nullable CycleButton<TrickMode> modeButton() {
        return this.modeButton;
    }

    public int columns() {
        return this.columns;
    }

    public int rows() {
        return this.rows;
    }

    public @Nullable TextField searchBox() {
        return this.searchBox;
    }

    public @Nullable CycleButton<Integer> familyButton() {
        return this.familyButton;
    }

    public List<TrickType> visible() {
        return this.visible;
    }

    public List<TrickCatalog.Section> sections() {
        return this.sections;
    }

    public void search(String text) {
        if (this.searchBox != null) {
            this.searchBox.setValue(text);
        } else {
            this.query = text;
        }
        this.relayout();
    }

    public void filterFamily(@Nullable TrickFamily value) {
        this.family = value == null ? ALL_FAMILIES : value.ordinal();
        if (this.familyButton != null) {
            this.familyButton.setValue(this.family);
        }
        this.relayout();
    }

    private @Nullable TrickFamily selectedFamily() {
        return this.family >= 0 && this.family < TrickFamily.values().length ? TrickFamily.values()[this.family] : null;
    }

    private static Component familyName(int index) {
        return index >= 0 && index < TrickFamily.values().length ? Component.translatable(TrickFamily.values()[index].translationKey())
                : Component.translatable("propwash.trick.tab.family.all");
    }

    public static int columnsFor(int width) {
        int columns = MAX_COLUMNS;
        while (columns > 1 && width < columns * Metrics.COLUMN_MIN + (columns - 1) * GAP) {
            columns--;
        }
        return columns;
    }

    @Override
    protected void build() {
        TrickConfig config = this.working();
        Font font = this.host.font();
        this.scroll = new ScrollPanel(this.x, this.y, Math.max(40, this.width), Math.max(20, this.height), this.scrollOffset);
        int width = Math.max(40, this.scroll.innerWidth());
        this.innerWidth = width;
        this.columns = columnsFor(width);
        this.columnWidth = Metrics.columnWidth(width, this.columns);
        this.help.clear();
        this.help.addAll(ThemeText.balanced(font, Component.translatable("propwash.trick.tab.help", TrickType.REPERTOIRE.size()), width));
        this.helpTop = Metrics.SECTION_HEIGHT;
        this.buttonTop = this.helpTop + this.help.size() * LINE + (this.help.isEmpty() ? 0 : GAP);
        this.modeButton = CycleButton.<TrickMode>builder(mode -> Component.translatable(mode.translationKey()), config.mode)
                .withValues(List.of(TrickMode.values()))
                .withTooltip(mode -> Tooltip.create(Component.translatable(mode.translationKey() + ".tooltip")))
                .create(this.x, this.y + this.buttonTop, Metrics.columnWidth(width, Metrics.columns(width)), Metrics.CONTROL_HEIGHT,
                        Component.translatable("propwash.trick.tab.mode"),
                        (button, mode) -> {
                            this.working().mode = mode;
                            this.host.changed();
                        });
        this.host.addWidget(this.modeButton);
        this.scroll.add(this.modeButton);
        int statusLines = 0;
        int statusWidth = 0;
        List<Component> states = List.of(Component.translatable("propwash.trick.tab.live", 99999, 99),
                Component.translatable("propwash.trick.tab.state_off"), Component.translatable("propwash.trick.tab.state_idle"));
        for (Component state : states) {
            statusWidth = Math.max(statusWidth, font.width(state));
        }
        this.statusTop = -1;
        if (Metrics.columns(width) < 2 || statusWidth > width - this.modeButton.getWidth() - GAP) {
            for (Component state : states) {
                statusLines = Math.max(statusLines, font.split(state, width).size());
            }
            this.statusTop = this.buttonTop + ROW;
        }
        this.tableTop = this.buttonTop + ROW + statusLines * LINE + Metrics.SECTION_GAP;
        int filterTop = this.tableTop + Metrics.SECTION_HEIGHT;
        boolean sideBySide = Metrics.columns(width) >= 2;
        int fieldWidth = sideBySide ? Metrics.columnWidth(width, 2) : width;
        TextField box = new TextField(font, this.x, this.y + filterTop, fieldWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.trick.tab.search.narration"));
        box.hint(Component.translatable("propwash.trick.tab.search"));
        box.setMaxLength(TrickCatalog.MAX_QUERY);
        box.setValue(this.query);
        box.setResponder(text -> {
            this.query = text;
            this.relayout();
        });
        this.searchBox = this.host.addWidget(box);
        this.scroll.add(box);
        List<Integer> families = new ArrayList<>();
        families.add(ALL_FAMILIES);
        for (TrickFamily f : TrickFamily.values()) {
            families.add(f.ordinal());
        }
        int buttonX = sideBySide ? this.x + fieldWidth + GAP : this.x;
        int buttonY = sideBySide ? filterTop : filterTop + ROW;
        this.familyButton = CycleButton.<Integer>builder(TrickConfigTab::familyName, this.family)
                .withValues(families)
                .withTooltip(value -> Tooltip.create(Component.translatable("propwash.trick.tab.family.tooltip")))
                .create(buttonX, this.y + buttonY, fieldWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.trick.tab.family"),
                        (button, value) -> {
                            this.family = value;
                            this.relayout();
                        });
        this.host.addWidget(this.familyButton);
        this.scroll.add(this.familyButton);
        this.listTop = buttonY + ROW + GAP / 2;
        this.rules.clear();
        this.rules.addAll(ThemeText.balanced(font, Component.translatable("propwash.trick.tab.rules",
                String.valueOf(Math.round(TrickScore.COMBO_WINDOW)), formatMultiplier(TrickScore.COMBO_MAX),
                "+" + Math.round(TrickScore.CHAIN_BONUS * 100.0) + " %"), width));
        this.relayout();
    }

    private void relayout() {
        this.visible = TrickCatalog.filter(this.query, this.selectedFamily(), TEXT);
        this.sections = TrickCatalog.sections(this.visible);
        int lines = 0;
        for (TrickCatalog.Section section : this.sections) {
            lines += 1 + rowsOf(section);
        }
        this.rows = lines;
        this.rulesTop = this.listTop + Math.max(1, lines) * LINE + GAP;
        if (this.scroll != null) {
            this.scroll.setContentHeight(this.rulesTop + this.rules.size() * LINE);
            this.scrollOffset = this.scroll.offset();
        }
    }

    private int rowsOf(TrickCatalog.Section section) {
        return (section.tricks().size() + this.columns - 1) / this.columns;
    }

    private static String formatMultiplier(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private int top() {
        return this.scroll != null ? this.scroll.top() : this.y;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = this.host.font();
        int width = this.innerWidth;
        int top = this.top();
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.trick.tab.header"), this.x, top, width);
        int lineY = top + this.helpTop;
        for (FormattedCharSequence line : this.help) {
            ThemeText.text(graphics, font, line, this.x, lineY, Palette.TEXT_MUTED);
            lineY += LINE;
        }
        this.status(graphics, font);
        int tableY = top + this.tableTop;
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.trick.tab.points"), this.x, tableY, width);
        Component count = Component.translatable("propwash.trick.tab.count", this.visible.size(), TrickType.REPERTOIRE.size());
        ThemeText.textRight(graphics, font, count, this.x + width, tableY + 2, Palette.TEXT_FAINT);
        int listY = top + this.listTop;
        TrickType hovered = null;
        if (this.sections.isEmpty()) {
            ThemeText.fitted(graphics, font, Component.translatable("propwash.trick.tab.none", this.query.strip()), this.x, listY, width, Palette.TEXT_MUTED);
        }
        for (TrickCatalog.Section section : this.sections) {
            List<TrickType> tricks = section.tricks();
            ThemeText.fitted(graphics, font, Component.translatable(section.family().translationKey()), this.x, listY, width, Palette.ACCENT);
            listY += LINE;
            int sectionRows = this.rowsOf(section);
            for (int i = 0; i < tricks.size(); i++) {
                int column = i / sectionRows;
                int row = i % sectionRows;
                int left = this.x + column * (this.columnWidth + GAP);
                int right = left + this.columnWidth;
                int rowY = listY + row * LINE;
                TrickType type = tricks.get(i);
                boolean over = mouseX >= left && mouseX < right && mouseY >= rowY - 1 && mouseY < rowY + LINE - 1
                        && mouseY >= this.y && mouseY < this.clipBottom();
                if (over) {
                    graphics.fill(left, rowY - 1, right, rowY + LINE - 1, Palette.HOVER_TINT);
                    hovered = type;
                } else if (row % 2 == 1) {
                    graphics.fill(left, rowY - 1, right, rowY + LINE - 1, Palette.ROW_STRIPE);
                }
                Component points = type.scaled()
                        ? Component.translatable("propwash.trick.tab.points_from", type.basePoints())
                        : Component.literal(Integer.toString(type.basePoints()));
                int pointsWidth = font.width(points);
                ThemeText.fitted(graphics, font, Component.translatable(type.translationKey()), left + 2, rowY, this.columnWidth - pointsWidth - 12, over ? Palette.ACCENT_HOVER : Palette.TEXT);
                ThemeText.textRight(graphics, font, points, right - 2, rowY, Palette.ACCENT);
            }
            listY += sectionRows * LINE;
        }
        int after = top + this.rulesTop;
        for (FormattedCharSequence line : this.rules) {
            ThemeText.text(graphics, font, line, this.x, after, Palette.TEXT_FAINT);
            after += LINE;
        }
        if (this.scroll != null) {
            this.scroll.extractScrollbar(graphics);
        }
        if (hovered != null) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(Component.translatable(hovered.translationKey()).getVisualOrderText());
            tip.add(ThemeText.colored(Component.translatable("propwash.trick.tab.in_family", Component.translatable(hovered.family().translationKey())),
                    Palette.TEXT_FAINT).getVisualOrderText());
            tip.addAll(font.split(Component.translatable(hovered.descriptionKey()), TOOLTIP_WIDTH));
            String aka = TEXT.apply(hovered.aliasKey());
            if (!aka.isEmpty()) {
                tip.addAll(font.split(ThemeText.colored(Component.translatable("propwash.trick.tab.aka", aka), Palette.TEXT_FAINT), TOOLTIP_WIDTH));
            }
            ThemeText.tooltipSequences(graphics, font, tip, mouseX, mouseY);
        }
    }

    private void status(GuiGraphicsExtractor graphics, Font font) {
        CycleButton<TrickMode> button = this.modeButton;
        if (button == null) {
            return;
        }
        TrickTracker tracker = TrickTracker.INSTANCE;
        Component text;
        int color;
        if (tracker.active()) {
            TrickScore score = tracker.session().score();
            text = Component.translatable("propwash.trick.tab.live", score.flightScore(), score.bestCombo());
            color = Palette.SUCCESS;
        } else {
            text = Component.translatable(this.working().mode == TrickMode.OFF ? "propwash.trick.tab.state_off" : "propwash.trick.tab.state_idle");
            color = Palette.TEXT_FAINT;
        }
        if (this.statusTop >= 0) {
            ThemeText.wrapped(graphics, font, text, this.x, this.top() + this.statusTop, this.innerWidth, color);
            return;
        }
        int left = button.getX() + button.getWidth() + GAP;
        int textY = Metrics.textY(button.getY(), button.getHeight());
        ThemeText.fitted(graphics, font, text, left, textY, this.x + this.innerWidth - left, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.scroll != null && this.scroll.rect().containsPoint((int) mouseX, (int) mouseY) && this.scroll.scroll(scrollY)) {
            this.scrollOffset = this.scroll.offset();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.scroll != null && this.scroll.mouseClicked(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scroll != null && this.scroll.mouseDragged(event.y())) {
            this.scrollOffset = this.scroll.offset();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scroll != null) {
            this.scroll.mouseReleased();
        }
        return false;
    }

    @Override
    public boolean scrollsItself() {
        return true;
    }

    @Override
    public boolean contentAbove() {
        return this.scroll != null && this.scroll.offset() > 0;
    }

    @Override
    public boolean contentBelow() {
        return this.scroll != null && this.scroll.offset() < this.scroll.maxOffset();
    }

    @Override
    public int clipBottom() {
        return this.scroll != null ? this.scroll.clipBottom() : Integer.MAX_VALUE;
    }

    @Override
    public void resetDefaults() {
        this.working = new TrickConfig();
        this.query = "";
        this.family = ALL_FAMILIES;
        this.host.changed();
        this.host.rebuild();
    }

    @Override
    public void save() {
        if (this.working != null) {
            TrickClient.CONFIG.set(this.working.copy());
        }
    }

    @Override
    public void discard() {
        this.working = null;
    }
}
