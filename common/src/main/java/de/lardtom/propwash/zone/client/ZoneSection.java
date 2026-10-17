package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class ZoneSection {
    public static final String KEY = "propwash.zone.section.";
    public static final int ENTRY = 26;
    public static final int FIELD = 14;
    public static final int BUTTON_WIDTH = 74;
    public static final int TOOLTIP_WIDTH = 240;

    private final List<Entry> entries = new ArrayList<>();
    private int builtVersion = -1;
    private boolean builtEditable;
    private int introY;
    private int introHeight;
    private int emptyY = -1;
    private @Nullable FlatButton newButton;

    public record Entry(Zone zone, int y, @Nullable FlatButton edit) {
    }

    public static Component title() {
        return Component.translatable(KEY + "title");
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    public @Nullable FlatButton newButton() {
        return this.newButton;
    }

    public int headingWidth(int width) {
        return this.newButton == null ? width : width - this.newButton.getWidth() - Metrics.GAP;
    }

    public boolean stale() {
        return ClientZones.INSTANCE.version() != this.builtVersion || ClientZones.INSTANCE.editable() != this.builtEditable;
    }

    public int build(int cy, int x, int top, int width, boolean tabEditable, Font font, Consumer<AbstractWidget> add, IntConsumer stop) {
        ClientZones synced = ClientZones.INSTANCE;
        this.builtVersion = synced.version();
        this.builtEditable = synced.editable();
        boolean editable = this.builtEditable && tabEditable;
        this.entries.clear();
        this.newButton = null;
        if (editable) {
            Component label = Component.translatable(KEY + "new");
            int buttonWidth = Math.min(width / 2, font.width(label) + 2 * Metrics.PADDING);
            this.newButton = SearchTags.label(new FlatButton(x + width - buttonWidth, top + cy - Metrics.SECTION_HEIGHT, buttonWidth, FIELD, label,
                    FlatButton.Style.PRIMARY, ZoneSection::createHere), label, title());
            add.accept(this.newButton);
        }
        stop.accept(cy);
        List<Zone> zones = synced.zones();
        this.emptyY = zones.isEmpty() ? cy : -1;
        if (zones.isEmpty()) {
            cy += ThemeText.wrappedHeight(font, Component.translatable(KEY + "empty"), width - Metrics.LABEL_GAP);
            stop.accept(cy);
            cy += Metrics.GAP;
        }
        for (Zone zone : zones) {
            stop.accept(cy);
            FlatButton edit = null;
            if (editable) {
                edit = SearchTags.label(new FlatButton(x + width - BUTTON_WIDTH, top + cy + 1, BUTTON_WIDTH, FIELD - 2,
                        Component.translatable(KEY + "edit"), FlatButton.Style.NORMAL, () -> edit(zone)),
                        Component.translatable(KEY + "edit_search", zone.name()), title());
                add.accept(edit);
            }
            this.entries.add(new Entry(zone, cy, edit));
            cy += ENTRY;
        }
        if (!zones.isEmpty()) {
            stop.accept(cy);
            cy += Metrics.GAP / 2;
        }
        this.introY = cy;
        Component intro = Component.translatable(KEY + "intro");
        this.introHeight = ThemeText.wrappedHeight(font, intro, width);
        cy += this.introHeight;
        return cy + Metrics.SECTION_GAP;
    }

    static void createHere() {
        if (ZoneEditor.startNew()) {
            ZoneEditScreen.openSticky();
        }
    }

    static void edit(Zone zone) {
        if (ZoneEditor.start(zone)) {
            ZoneEditScreen.openSticky();
        }
    }

    public @Nullable Component extract(GuiGraphicsExtractor g, Font font, int x, int top, int width, int mouseX, int mouseY, int clipTop,
                                       int clipBottom) {
        ThemeText.wrapped(g, font, Component.translatable(KEY + "intro"), x, top + this.introY, width, Palette.TEXT_FAINT);
        if (this.emptyY >= 0) {
            ThemeText.wrapped(g, font, Component.translatable(KEY + "empty"), x + Metrics.LABEL_GAP, top + this.emptyY + 1, width - Metrics.LABEL_GAP,
                    Palette.TEXT_MUTED);
        }
        Component hovered = null;
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            Zone zone = entry.zone;
            int y = top + entry.y;
            if (i % 2 == 1) {
                g.fill(x, y - 1, x + width, y + ENTRY - 2, Palette.ROW_STRIPE);
            }
            int color = 0xFF000000 | ZoneRenderer.color(zone);
            ThemeDraw.dot(g, x + 5, y + 5, color);
            int textRight = entry.edit != null ? x + width - BUTTON_WIDTH - Metrics.GAP : x + width;
            Component kind = ZoneText.kind(zone);
            int kindWidth = font.width(kind);
            ThemeText.fitted(g, font, Component.literal(zone.name()), x + 12, y + 2, textRight - x - 12 - kindWidth - Metrics.GAP, Palette.TEXT);
            ThemeText.textRight(g, font, kind, textRight, y + 2, color);
            Component detail = Component.translatable(KEY + "detail", ZoneText.shape(zone), ZoneText.size(zone), ZoneText.position(zone), zone.priority(),
                    ZoneText.overrides(zone));
            ThemeText.fitted(g, font, detail, x + 12, y + 2 + Metrics.LINE_HEIGHT + 1, width - 12, Palette.TEXT_FAINT);
            boolean overButton = entry.edit != null && entry.edit.isHovered();
            if (!overButton && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENTRY - 2 && mouseY >= clipTop && mouseY < clipBottom) {
                hovered = ZoneText.summary(zone).append("\n").append(Component.translatable(ZoneText.KEY + "transition.label",
                        ZoneText.transition(zone.transitionTenths())));
            }
        }
        return hovered;
    }

    public static int tooltipWidth() {
        return TOOLTIP_WIDTH;
    }
}
