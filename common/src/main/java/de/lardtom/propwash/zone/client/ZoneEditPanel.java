package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.race.client.GateEditor;
import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneShape;
import de.lardtom.propwash.zone.ZoneText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ZoneEditPanel {
    public static final int MIN_WIDTH = 190;
    public static final int MAX_WIDTH = 250;
    public static final int MARGIN = 6;
    public static final int ROW = 11;
    public static final int SECTION = 12;
    public static final int HEADER = 14;
    public static final int NAME = 16;
    public static final int BUTTON = 16;
    public static final int ARROW = 9;
    public static final String KEY = "propwash.zone.edit.";
    private static final int HUD_BACK = 0xD0161616;

    private final ZoneEditor editor;
    private final boolean cursor;
    private int x0;
    private int y0;
    private int width;
    private int height;
    private int bodyTop;
    private int bodyBottom;
    private int offset;
    private final List<Slot> slots = new ArrayList<>();
    private List<FormattedCharSequence> footer = List.of();
    private int footerTop;
    private int buttonsTop;

    record Slot(ZoneDraft.Row row, int y, @Nullable Component section) {
    }

    ZoneEditPanel(ZoneEditor editor, boolean cursor) {
        this.editor = editor;
        this.cursor = cursor;
    }

    ZoneEditor editor() {
        return this.editor;
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    public void layout(int guiWidth, int guiHeight) {
        Font font = font();
        this.width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, guiWidth * 36 / 100));
        this.x0 = MARGIN;
        this.y0 = MARGIN;
        this.height = guiHeight - 2 * MARGIN;
        this.footer = new ArrayList<>();
        this.footer.addAll(font.split(this.status(), this.width - 2 * Metrics.PADDING));
        this.footer.addAll(font.split(Component.translatable(this.cursor ? KEY + "hint.cursor" : KEY + "hint.hud",
                GateEditor.cursorKeyName()), this.width - 2 * Metrics.PADDING));
        int footerHeight = this.footer.size() * Metrics.LINE_HEIGHT + 4;
        int buttons = this.cursor ? BUTTON * 2 + 6 : 0;
        this.bodyTop = this.y0 + HEADER + NAME + 2;
        this.bodyBottom = this.y0 + this.height - footerHeight - buttons - 4;
        this.footerTop = this.bodyBottom + 4;
        this.buttonsTop = this.footerTop + footerHeight;
        this.slots.clear();
        int y = 0;
        ZoneDraft.Kind last = null;
        for (ZoneDraft.Row row : this.editor.model().rows()) {
            Component section = null;
            String group = group(row.kind());
            if (last == null || !group.equals(group(last))) {
                section = Component.translatable(KEY + "section." + group);
                y += SECTION;
            }
            last = row.kind();
            this.slots.add(new Slot(row, y, section));
            y += ROW;
        }
        int content = y;
        int room = Math.max(ROW, this.bodyBottom - this.bodyTop);
        Slot selected = this.slot(this.editor.model().selected());
        if (selected != null) {
            int top = selected.y - (selected.section != null ? SECTION : 0);
            if (top < this.offset) {
                this.offset = top;
            } else if (selected.y + ROW > this.offset + room) {
                this.offset = selected.y + ROW - room;
            }
        }
        this.offset = Math.max(0, Math.min(this.offset, Math.max(0, content - room)));
    }

    private static String group(ZoneDraft.Kind kind) {
        if (kind.geometry()) {
            return "shape";
        }
        return kind == ZoneDraft.Kind.RULE ? "physics" : "zone";
    }

    private @Nullable Slot slot(ZoneDraft.Row row) {
        for (Slot slot : this.slots) {
            if (slot.row.equals(row)) {
                return slot;
            }
        }
        return null;
    }

    public ScreenRectangle nameField() {
        return new ScreenRectangle(this.x0 + Metrics.PADDING, this.y0 + HEADER + 1, this.width - 2 * Metrics.PADDING, NAME - 3);
    }

    public ScreenRectangle button(int index) {
        int half = (this.width - 2 * Metrics.PADDING - Metrics.GAP) / 2;
        int col = index % 2;
        int line = index / 2;
        return new ScreenRectangle(this.x0 + Metrics.PADDING + col * (half + Metrics.GAP), this.buttonsTop + line * (BUTTON + 2), half, BUTTON);
    }

    public int right() {
        return this.x0 + this.width;
    }

    private int rowY(Slot slot) {
        return this.bodyTop + slot.y - this.offset;
    }

    private boolean visible(int y, int h) {
        return y >= this.bodyTop && y + h <= this.bodyBottom;
    }

    public ZoneDraft.@Nullable Row rowAt(double mx, double my) {
        if (mx < this.x0 || mx >= this.x0 + this.width) {
            return null;
        }
        for (Slot slot : this.slots) {
            int y = this.rowY(slot);
            if (this.visible(y, ROW) && my >= y && my < y + ROW) {
                return slot.row;
            }
        }
        return null;
    }

    public int arrowAt(double mx, double my) {
        ZoneDraft.Row row = this.rowAt(mx, my);
        if (row == null) {
            return 0;
        }
        int right = this.x0 + this.width - Metrics.PADDING;
        int valueLeft = this.valueLeft();
        if (mx >= right - ARROW) {
            return 1;
        }
        if (mx >= valueLeft && mx < valueLeft + ARROW) {
            return -1;
        }
        return 0;
    }

    private int valueLeft() {
        return this.x0 + this.width * 52 / 100;
    }

    public Component status() {
        String problem = this.editor.problem();
        if (problem != null) {
            return Component.translatable("propwash.zone.problem." + problem).withColor(Palette.ERROR);
        }
        if (this.editor.deleteArmed()) {
            return Component.translatable(KEY + "status.delete").withColor(Palette.WARNING);
        }
        if (!this.editor.model().changed()) {
            return Component.translatable(KEY + "status.same").withColor(Palette.TEXT_MUTED);
        }
        return Component.translatable(KEY + "status.ready").withColor(Palette.SUCCESS);
    }

    public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font font = font();
        Zone zone = this.editor.draft();
        int x1 = this.x0 + this.width;
        int y1 = this.y0 + this.height;
        if (this.cursor) {
            ThemeDraw.panel(g, this.x0, this.y0, x1, y1);
        } else {
            g.fill(this.x0, this.y0, x1, y1, HUD_BACK);
            ThemeDraw.boxOutline(g, this.x0, this.y0, x1, y1, Palette.BORDER);
        }
        Component title = Component.translatable(this.editor.model().isNew() ? KEY + "title.new" : KEY + "title.edit");
        ThemeDraw.panelTitle(g, font, title, this.x0, this.y0, x1);
        if (!this.cursor) {
            ThemeText.fitted(g, font, Component.literal(zone.name()).withColor(ZoneRenderer.color(zone) | 0xFF000000), this.x0 + Metrics.PADDING,
                    this.y0 + HEADER + 4, this.width - 2 * Metrics.PADDING, Palette.TEXT);
        }
        ZoneDraft.Row selected = this.editor.model().selected();
        int labelWidth = this.valueLeft() - this.x0 - Metrics.PADDING - 2;
        int right = x1 - Metrics.PADDING;
        for (Slot slot : this.slots) {
            int y = this.rowY(slot);
            if (slot.section != null && this.visible(y - SECTION, SECTION)) {
                ThemeDraw.sectionTitle(g, font, slot.section, this.x0 + Metrics.PADDING, y - SECTION + 1, this.width - 2 * Metrics.PADDING);
            }
            if (!this.visible(y, ROW)) {
                continue;
            }
            boolean isSelected = slot.row.equals(selected);
            boolean hovered = this.cursor && mouseX >= this.x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
            if (isSelected) {
                g.fill(this.x0 + 2, y, x1 - 2, y + ROW, Palette.ACCENT_DIM);
            } else if (hovered) {
                g.fill(this.x0 + 2, y, x1 - 2, y + ROW, Palette.BUTTON_HOVER);
            }
            int textY = y + (ROW - Metrics.TEXT_HEIGHT) / 2;
            ThemeText.fitted(g, font, this.label(slot.row, zone), this.x0 + Metrics.PADDING, textY, labelWidth,
                    isSelected ? Palette.TEXT : Palette.TEXT_MUTED);
            boolean inherited = slot.row.kind() == ZoneDraft.Kind.RULE && slot.row.spec() != null && !zone.overrides(slot.row.spec());
            Component value = this.value(slot.row, zone);
            int valueLeft = this.valueLeft();
            if (isSelected || hovered) {
                ThemeText.text(g, font, "‹", valueLeft + 1, textY, Palette.ACCENT);
                ThemeText.textRight(g, font, "›", right, textY, Palette.ACCENT);
            }
            ThemeText.fittedRight(g, font, value, right - ARROW, textY, right - ARROW - valueLeft - ARROW,
                    inherited ? Palette.TEXT_FAINT : Palette.TEXT);
        }
        g.fill(this.x0 + 2, this.footerTop - 2, x1 - 2, this.footerTop - 1, Palette.BORDER);
        int y = this.footerTop;
        for (FormattedCharSequence line : this.footer) {
            ThemeText.text(g, font, line, this.x0 + Metrics.PADDING, y, Palette.TEXT_FAINT);
            y += Metrics.LINE_HEIGHT;
        }
    }

    Component label(ZoneDraft.Row row, Zone zone) {
        if (row.kind() == ZoneDraft.Kind.RULE && row.spec() != null) {
            return Component.translatable(row.spec().translationKey());
        }
        if (row.kind() == ZoneDraft.Kind.WIDTH && zone.shape() == ZoneShape.CYLINDER) {
            return Component.translatable(KEY + "row.diameter");
        }
        return Component.translatable(KEY + "row." + row.key());
    }

    Component value(ZoneDraft.Row row, Zone zone) {
        ZoneBox box = zone.box();
        return switch (row.kind()) {
            case SHAPE -> ZoneText.shape(zone);
            case X -> Component.literal(ZoneText.number(box.centerX()));
            case Y -> Component.literal(ZoneText.number(box.minY()));
            case Z -> Component.literal(ZoneText.number(box.centerZ()));
            case WIDTH -> Component.translatable(KEY + "blocks", ZoneText.number(box.sizeX()));
            case HEIGHT -> Component.translatable(KEY + "blocks", ZoneText.number(box.sizeY()));
            case DEPTH -> Component.translatable(KEY + "blocks", ZoneText.number(box.sizeZ()));
            case PRIORITY -> Component.literal(Integer.toString(zone.priority()));
            case RACE_LEGAL -> WorldRuleText.onOff(zone.raceLegal());
            case TRANSITION -> ZoneText.transition(zone.transitionTenths());
            case RULE -> {
                RuleSpec spec = row.spec();
                if (spec == null) {
                    yield Component.empty();
                }
                if (zone.overrides(spec)) {
                    yield WorldRuleText.value(spec, zone.value(spec, spec.defaultValue()));
                }
                yield Component.translatable(KEY + "inherit", WorldRuleText.value(spec, ClientWorldRules.INSTANCE.get(spec)));
            }
        };
    }

    public @Nullable Component tooltip(ZoneDraft.Row row) {
        if (row.kind() == ZoneDraft.Kind.RULE && row.spec() != null) {
            return Component.translatable(KEY + "tooltip.rule", Component.translatable(row.spec().descriptionKey()));
        }
        return Component.translatable(KEY + "tooltip." + row.key());
    }
}
