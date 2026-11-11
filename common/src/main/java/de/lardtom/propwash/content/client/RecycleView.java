package de.lardtom.propwash.content.client;

import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.PropRepairs;
import de.lardtom.propwash.content.Recycling;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecycleView {
    public static final String KEY = "propwash.content.workbench.recycle.";
    public static final String TAB = "propwash.content.workbench.tab.";
    public static final String BUTTON = KEY + "button";
    public static final String INPUT = KEY + "input";
    public static final String OUTPUT = KEY + "output";
    public static final String TITLE = KEY + "title";
    public static final String EMPTY = KEY + "empty";
    public static final String CONDITION = KEY + "condition";
    public static final String TIER = KEY + "tier";
    public static final String PERCENT = KEY + "percent";
    public static final String EXPECTED = KEY + "expected";
    public static final String AMOUNT = KEY + "amount";
    public static final String APPROX = KEY + "approx";
    public static final String MORE = KEY + "more";
    public static final String NOT_RECYCLABLE = KEY + "not_recyclable";
    public static final String MISSING = KEY + "missing";
    public static final String NOT_A_PART = KEY + "not_a_part";
    public static final String DRONE = KEY + "drone";
    public static final String ONLY_BUTTON = KEY + "only_button";
    public static final String FULL = KEY + "full";
    public static final String PARTIAL = KEY + "partial";
    public static final String READY = KEY + "ready";
    public static final String TIERS = KEY + "tiers";
    public static final String TIER_FULL = KEY + "tier.full";
    public static final String TIER_HALF = KEY + "tier.half";
    public static final String TIER_QUARTER = KEY + "tier.quarter";
    public static final String TIER_BROKEN = KEY + "tier.broken";
    public static final String INPUT_TITLE = KEY + "input_title";
    public static final String INPUT_HINT = KEY + "input_hint";
    public static final String OUTPUT_HINT = KEY + "output_hint";
    public static final String BUTTON_HINT = KEY + "button_hint";
    public static final String REPAIR_PROPS = "propwash.content.workbench.hint.repair_props";

    private static final int SHADOW = Palette.SHADOW;
    private static final int FIELD = Palette.FIELD;
    private static final int WELL = Palette.BACKGROUND;
    private static final int LIGHT = Palette.BORDER;
    private static final int LINE = 10;
    private static final int PAD = 5;
    private static final int ICON = 16;
    private static final int LEGEND_LINES = 4;
    private static final int BULLET = WorkbenchLayout.ISSUE_BULLET;
    private static final long NOTICE_MILLIS = 4000L;

    private final Font font;
    private final DroneWorkbenchMenu menu;
    private final List<Row> rows = new ArrayList<>();
    private int legendTop;
    private int moreTop = -1;
    private final List<Component> hidden = new ArrayList<>();
    private long droneNoticeUntil;

    public RecycleView(Font font, DroneWorkbenchMenu menu) {
        this.font = font;
        this.menu = menu;
    }

    public static void sunken(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int fill) {
        g.fill(x0, y0, x1, y1, LIGHT);
        g.fill(x0, y0, x1 - 1, y1 - 1, SHADOW);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }

    public void background(GuiGraphicsExtractor g, int x, int y) {
        sunken(g, x + WorkbenchLayout.RECYCLE_X0, y + WorkbenchLayout.RECYCLE_Y0, x + WorkbenchLayout.RECYCLE_X1, y + WorkbenchLayout.RECYCLE_Y1, FIELD);
        g.fill(x + WorkbenchLayout.RECYCLE_X1 + 1, y + WorkbenchLayout.RECYCLE_INFO_Y0, x + WorkbenchLayout.WIDTH - 4,
                y + WorkbenchLayout.RECYCLE_INFO_Y1 + 2, Palette.PANEL);
        sunken(g, x + WorkbenchLayout.RECYCLE_INFO_X0, y + WorkbenchLayout.RECYCLE_INFO_Y0, x + WorkbenchLayout.RECYCLE_INFO_X1,
                y + WorkbenchLayout.RECYCLE_INFO_Y1 + 1, FIELD);
        for (Slot slot : this.menu.slots) {
            if (slot.index >= DroneWorkbenchMenu.RECYCLE_INPUT && slot.index < DroneWorkbenchMenu.RECYCLE_PREVIEW) {
                sunken(g, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, WELL);
            }
        }
        Slot input = this.menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT);
        if (input.hasItem()) {
            ThemeDraw.boxOutline(g, x + input.x - 2, y + input.y - 2, x + input.x + 18, y + input.y + 18, Palette.ACCENT_DIM);
        }
        boolean ready = this.menu.recyclePieces() > 0;
        arrow(g, x + WorkbenchLayout.RECYCLE_ARROW_X, y + WorkbenchLayout.RECYCLE_ARROW_Y, ready ? Palette.ACCENT : Palette.BUTTON);
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 2, y + 6, x + 11, y + 10, color);
        for (int row = 1; row < 15; row++) {
            g.fill(x + 11, y + row, x + 11 + Math.min(row, 15 - row), y + row + 1, color);
        }
    }

    public void labels(GuiGraphicsExtractor g) {
        int labelColor = WorkbenchReport.COLOR_LABEL;
        int inputWidth = WorkbenchLayout.RECYCLE_BUTTON_X1 - WorkbenchLayout.RECYCLE_BUTTON_X0;
        ThemeText.fittedCentered(g, this.font, Component.translatable(INPUT), WorkbenchLayout.RECYCLE_INPUT_X + 8, WorkbenchLayout.RECYCLE_LABEL_Y,
                inputWidth, labelColor);
        int outputWidth = WorkbenchLayout.RECYCLE_X1 - 4 - (WorkbenchLayout.RECYCLE_ARROW_X + 18);
        ThemeText.fittedCentered(g, this.font, Component.translatable(OUTPUT), WorkbenchLayout.RECYCLE_OUTPUT_X + 27, WorkbenchLayout.RECYCLE_LABEL_Y,
                outputWidth, labelColor);
        this.status(g);
        this.info(g);
    }

    private void status(GuiGraphicsExtractor g) {
        Status status = this.statusLine();
        int x0 = WorkbenchLayout.RECYCLE_X0 + PAD;
        int width = WorkbenchLayout.RECYCLE_X1 - PAD - x0;
        int y = WorkbenchLayout.RECYCLE_STATUS_Y;
        for (FormattedCharSequence line : this.font.split(status.text(), width)) {
            if (y + this.font.lineHeight > WorkbenchLayout.RECYCLE_Y1 - 2) {
                break;
            }
            g.text(this.font, line, x0, y, status.color(), false);
            y += 9;
        }
    }

    public void droneNotice(boolean shown) {
        this.droneNoticeUntil = shown ? Util.getMillis() + NOTICE_MILLIS : 0L;
    }

    public Status statusLine() {
        if (Util.getMillis() < this.droneNoticeUntil) {
            return new Status(Component.translatable(DRONE), WorkbenchReport.COLOR_WARN);
        }
        ItemStack input = this.menu.recycleInput();
        if (input.isEmpty()) {
            return new Status(Component.translatable(ONLY_BUTTON), WorkbenchReport.COLOR_LABEL);
        }
        if (this.menu.recycleStatus() != Recycling.Status.READY) {
            return new Status(this.problem(), WorkbenchReport.COLOR_WARN);
        }
        int pieces = this.menu.recyclePieces();
        if (pieces < 1) {
            return new Status(Component.translatable(FULL), WorkbenchReport.COLOR_WARN);
        }
        if (pieces < input.getCount()) {
            return new Status(Component.translatable(PARTIAL, pieces, input.getCount()), WorkbenchReport.COLOR_WARN);
        }
        return new Status(Component.translatable(READY, pieces), WorkbenchReport.COLOR_LABEL);
    }

    private Component problem() {
        return switch (this.menu.recycleStatus()) {
            case MISSING -> Component.translatable(MISSING);
            case NOT_A_PART -> Component.translatable(NOT_A_PART);
            case EMPTY, READY, NOT_RECYCLABLE -> Component.translatable(NOT_RECYCLABLE);
        };
    }

    private void info(GuiGraphicsExtractor g) {
        this.rows.clear();
        this.hidden.clear();
        this.moreTop = -1;
        int x0 = WorkbenchLayout.RECYCLE_INFO_X0 + PAD;
        int x1 = WorkbenchLayout.RECYCLE_INFO_X1 - PAD;
        int width = x1 - x0;
        this.legendTop = WorkbenchLayout.RECYCLE_INFO_Y1 - PAD - LEGEND_LINES * LINE + 1;
        int y = WorkbenchLayout.RECYCLE_INFO_Y0 + PAD;
        ItemStack input = this.menu.recycleInput();
        if (input.isEmpty()) {
            y = this.wrapped(g, Component.translatable(TITLE), x0, y, width, WorkbenchReport.COLOR_VALUE);
            this.wrapped(g, Component.translatable(EMPTY), x0, y + 2, width, WorkbenchReport.COLOR_LABEL);
            this.legend(g, x0, width, 0);
            return;
        }
        MutableComponent name = Component.translatable(AMOUNT, input.getCount(), input.getHoverName());
        y = this.title(g, name, x0, y, width);
        float condition = PartCondition.of(input);
        int tier = Recycling.tier(condition);
        y = this.pair(g, Component.translatable(CONDITION), PartCondition.value(condition), x0, y, width);
        y = this.pair(g, Component.translatable(TIER), Component.translatable(PERCENT, tier).withColor(tierColor(tier) & 0xFFFFFF), x0, y, width);
        Optional<Recycling.Materials> materials = this.menu.recycleMaterials();
        if (materials.isEmpty()) {
            if (this.menu.recycleStatus() != Recycling.Status.EMPTY && this.menu.recycleStatus() != Recycling.Status.READY) {
                this.wrapped(g, this.problem(), x0, y + 3, width, WorkbenchReport.COLOR_WARN);
            }
            this.legend(g, x0, width, tier);
            return;
        }
        ThemeDraw.separator(g, x0, x1, y + 1);
        y += 4;
        y = this.wrapped(g, Component.translatable(EXPECTED), x0, y, width, WorkbenchReport.COLOR_LABEL);
        int bottom = this.legendTop - 3;
        boolean prop = PropRepairs.isProp(input);
        List<Component> repair = prop ? List.of(Component.translatable(REPAIR_PROPS)) : List.of();
        int repairLines = prop ? this.font.split(repair.getFirst(), width).size() : 0;
        Recycling.Materials found = materials.get();
        int count = found.entries().size();
        int room = Math.max(1, (bottom - y - repairLines * 9) / WorkbenchLayout.RECYCLE_ROW);
        int shown = count > room ? room - 1 : count;
        for (int i = 0; i < count; i++) {
            Optional<Item> item = found.item(i);
            if (item.isEmpty()) {
                continue;
            }
            ItemStack icon = new ItemStack(item.get());
            Component amount = amount(found, i, input.getCount(), tier);
            Component line = Component.translatable(AMOUNT, amount, icon.getHoverName());
            if (i >= shown) {
                this.hidden.add(line);
                continue;
            }
            g.fakeItem(icon, x0, y);
            int textX = x0 + ICON + 3;
            int textWidth = x1 - textX;
            Component text = TextFit.mode(this.font.width(line), textWidth) == TextFit.Mode.CUT ? amount : line;
            ThemeText.fitted(g, this.font, text, textX, y + 4, textWidth, WorkbenchReport.COLOR_VALUE);
            this.rows.add(new Row(y, line, found.least(i, input.getCount(), tier), found.most(i, input.getCount(), tier)));
            y += WorkbenchLayout.RECYCLE_ROW;
        }
        if (!this.hidden.isEmpty()) {
            this.moreTop = y;
            ThemeText.fitted(g, this.font, Component.translatable(MORE, this.hidden.size()), x0, y + 4, width, WorkbenchReport.COLOR_LABEL);
            y += WorkbenchLayout.RECYCLE_ROW;
        }
        for (Component text : repair) {
            this.wrapped(g, text, x0, y + 1, width, WorkbenchReport.COLOR_LABEL);
        }
        this.legend(g, x0, width, tier);
    }

    public static Component amount(Recycling.Materials materials, int index, int pieces, int tier) {
        Component value = Numbers.of(materials.expected(index, pieces, tier), 2);
        return materials.exact(index, pieces, tier) ? value : Component.translatable(APPROX, value);
    }

    private void legend(GuiGraphicsExtractor g, int x0, int width, int active) {
        int y = this.legendTop;
        ThemeText.fitted(g, this.font, Component.translatable(TIERS), x0, y, width, WorkbenchReport.COLOR_LABEL);
        int[] tiers = {Recycling.FULL, Recycling.HALF, Recycling.QUARTER};
        String[] keys = {TIER_FULL, TIER_HALF, TIER_QUARTER};
        for (int i = 0; i < tiers.length; i++) {
            y += LINE;
            boolean on = tiers[i] == active;
            int color = on ? tierColor(tiers[i]) : WorkbenchReport.COLOR_LABEL;
            g.fill(x0, y + 2, x0 + BULLET, y + 2 + BULLET, on ? color : Palette.BORDER_STRONG);
            int textX = x0 + BULLET + WorkbenchLayout.ISSUE_BULLET_GAP;
            ThemeText.fitted(g, this.font, Component.translatable(keys[i]), textX, y, x0 + width - textX, color);
        }
    }

    public static int tierColor(int tier) {
        return switch (tier) {
            case Recycling.FULL -> WorkbenchReport.COLOR_GOOD;
            case Recycling.HALF -> WorkbenchReport.COLOR_WARN;
            default -> WorkbenchReport.COLOR_BAD;
        };
    }

    private int pair(GuiGraphicsExtractor g, Component label, Component value, int x, int y, int width) {
        MutableComponent line = label.copy().withColor(WorkbenchReport.COLOR_LABEL & 0xFFFFFF).append(" ").append(value);
        return this.wrapped(g, line, x, y, width, WorkbenchReport.COLOR_LABEL);
    }

    private int title(GuiGraphicsExtractor g, Component text, int x, int y, int width) {
        if (TextFit.mode(this.font.width(text), width) == TextFit.Mode.CUT) {
            return this.wrapped(g, text, x, y, width, WorkbenchReport.COLOR_VALUE);
        }
        ThemeText.fitted(g, this.font, text, x, y, width, WorkbenchReport.COLOR_VALUE);
        return y + LINE;
    }

    private int wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            g.text(this.font, line, x, y, color, false);
            y += LINE;
        }
        return y;
    }

    public List<Component> tooltip(int rx, int ry) {
        int x0 = WorkbenchLayout.RECYCLE_INFO_X0;
        int x1 = WorkbenchLayout.RECYCLE_INFO_X1;
        if (rx < x0 || rx >= x1) {
            return List.of();
        }
        for (Row row : this.rows) {
            if (ry >= row.y() && ry < row.y() + WorkbenchLayout.RECYCLE_ROW) {
                List<Component> lines = new ArrayList<>();
                lines.add(row.text());
                lines.add(Component.translatable(KEY + "range", row.least(), row.most()));
                return lines;
            }
        }
        if (this.moreTop >= 0 && ry >= this.moreTop && ry < this.moreTop + WorkbenchLayout.RECYCLE_ROW) {
            return List.copyOf(this.hidden);
        }
        if (ry >= this.legendTop && ry < this.legendTop + LEGEND_LINES * LINE) {
            return tierLines();
        }
        return List.of();
    }

    public static List<SalvageHint.Line> inputLines(ItemStack carried) {
        List<SalvageHint.Line> lines = new ArrayList<>();
        lines.add(SalvageHint.Line.title(INPUT_TITLE));
        if (!carried.isEmpty() && !Recycling.isPart(carried)) {
            lines.add(SalvageHint.Line.warning(NOT_A_PART));
            if (DroneAssembly.isDrone(carried)) {
                lines.add(SalvageHint.Line.warning(DRONE));
            }
            return lines;
        }
        lines.add(SalvageHint.Line.body(INPUT_HINT));
        for (Component tier : tierLines()) {
            lines.add(new SalvageHint.Line(tier, SalvageHint.Kind.BODY));
        }
        return lines;
    }

    public static List<Component> tierLines() {
        return List.of(Component.translatable(TIERS), Component.translatable(TIER_FULL), Component.translatable(TIER_HALF),
                Component.translatable(TIER_QUARTER), Component.translatable(TIER_BROKEN));
    }

    public record Status(Component text, int color) {
    }

    private record Row(int y, Component text, int least, int most) {
    }
}
