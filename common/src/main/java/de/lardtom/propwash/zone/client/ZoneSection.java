package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneEditC2S;
import de.lardtom.propwash.zone.ZoneJumpC2S;
import de.lardtom.propwash.zone.ZoneMenu;
import de.lardtom.propwash.zone.ZoneRelease;
import de.lardtom.propwash.zone.ZoneText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class ZoneSection {
    public static final String KEY = "propwash.zone.section.";
    public static final String MENU_KEY = "propwash.zones_open.menu.";
    public static final int ENTRY = 26;
    public static final int LINE = Metrics.LINE_HEIGHT + 1;
    public static final int FIELD = 14;
    public static final int BUTTON_GAP = 4;
    public static final int TOOLTIP_WIDTH = 240;
    public static final int NAME_MIN_WIDTH = 96;
    public static final int TEXT_LEFT = 12;
    public static final long DELETE_CONFIRM_NANOS = ZoneEditor.DELETE_CONFIRM_NANOS;
    private static final String SEPARATOR = " · ";

    private final List<Entry> entries = new ArrayList<>();
    private final Map<Integer, Long> deleteArmed = new HashMap<>();
    private int builtVersion = -1;
    private boolean builtEditable;
    private boolean builtCreatable;
    private boolean builtJump;
    private boolean admin;
    private int emptyY = -1;
    private @Nullable FlatButton newButton;
    private @Nullable Listener listener;

    public interface Listener {
        void leave();

        void flash(Component message, int color);
    }

    public record Entry(Zone zone, Zone effective, int y, int height, boolean owner, @Nullable FlatButton show, @Nullable FlatButton jump,
                        @Nullable FlatButton edit, @Nullable FlatButton delete) {
        public List<FlatButton> buttons() {
            List<FlatButton> out = new ArrayList<>();
            for (FlatButton button : new FlatButton[]{this.show, this.jump, this.edit, this.delete}) {
                if (button != null) {
                    out.add(button);
                }
            }
            return out;
        }
    }

    public static Component title() {
        return Component.translatable(KEY + "title");
    }

    public static Component heading(boolean admin) {
        return Component.translatable(MENU_KEY + (admin ? "all" : "own"));
    }

    public void listener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    public @Nullable Entry entry(int id) {
        for (Entry entry : this.entries) {
            if (entry.zone.id() == id) {
                return entry;
            }
        }
        return null;
    }

    public @Nullable FlatButton newButton() {
        return this.newButton;
    }

    public boolean admin() {
        return this.admin;
    }

    public int headingWidth(int width) {
        return this.newButton == null ? width : width - this.newButton.getWidth() - Metrics.GAP;
    }

    public boolean stale() {
        ClientZones synced = ClientZones.INSTANCE;
        return synced.version() != this.builtVersion || synced.editable() != this.builtEditable || synced.creatable() != this.builtCreatable
                || ClientZones.mayJump() != this.builtJump;
    }

    public boolean deleteArmed(int id) {
        Long until = this.deleteArmed.get(id);
        return until != null && until > Util.getNanos();
    }

    public int build(int cy, int x, int top, int width, Font font, Consumer<AbstractWidget> add, IntConsumer stop) {
        ClientZones synced = ClientZones.INSTANCE;
        this.builtVersion = synced.version();
        this.builtEditable = synced.editable();
        this.builtCreatable = synced.creatable();
        this.builtJump = ClientZones.mayJump();
        this.admin = this.builtEditable;
        ZoneRelease release = synced.release();
        this.entries.clear();
        this.newButton = null;
        if (this.builtCreatable) {
            Component label = Component.translatable(KEY + "new");
            int buttonWidth = Math.min(width / 2, font.width(label) + 2 * Metrics.PADDING);
            this.newButton = SearchTags.label(new FlatButton(x + width - buttonWidth, top + cy - Metrics.SECTION_HEIGHT, buttonWidth, FIELD, label,
                    FlatButton.Style.PRIMARY, this::createHere), label, title());
            add.accept(this.newButton);
        }
        stop.accept(cy);
        List<Zone> zones = synced.listed();
        this.emptyY = zones.isEmpty() ? cy : -1;
        if (zones.isEmpty()) {
            cy += ThemeText.wrappedHeight(font, this.emptyText(), width - Metrics.LABEL_GAP);
            stop.accept(cy);
            cy += Metrics.GAP;
        }
        for (Zone zone : zones) {
            stop.accept(cy);
            int textHeight = ENTRY + (this.admin ? LINE : 0);
            List<ZoneMenu.Action> actions = synced.actions(zone);
            List<FlatButton> row = new ArrayList<>();
            FlatButton show = null;
            FlatButton jump = null;
            FlatButton edit = null;
            FlatButton delete = null;
            for (ZoneMenu.Action action : actions) {
                FlatButton button = this.button(action, zone, font);
                row.add(button);
                switch (action) {
                    case SHOW -> show = button;
                    case JUMP -> jump = button;
                    case EDIT -> edit = button;
                    case DELETE -> delete = button;
                }
            }
            int rows = place(row, x + TEXT_LEFT, top + cy + textHeight - 2, width - TEXT_LEFT);
            for (FlatButton button : row) {
                add.accept(button);
            }
            int height = textHeight + rows * (FIELD + 2) + (rows > 0 ? 2 : 0);
            this.entries.add(new Entry(zone, release.effective(zone), cy, height, this.admin, show, jump, edit, delete));
            cy += height;
        }
        if (!zones.isEmpty()) {
            stop.accept(cy);
            cy += Metrics.GAP / 2;
        }
        return cy;
    }

    static int place(List<FlatButton> buttons, int left, int y, int width) {
        if (buttons.isEmpty()) {
            return 0;
        }
        int rows = 1;
        int bx = left;
        int by = y;
        for (FlatButton button : buttons) {
            int w = Math.min(button.getWidth(), width);
            if (bx > left && bx + w > left + width) {
                rows++;
                bx = left;
                by += FIELD + 2;
            }
            button.setX(bx);
            button.setY(by);
            button.setWidth(w);
            bx += w + BUTTON_GAP;
        }
        return rows;
    }

    private FlatButton button(ZoneMenu.Action action, Zone zone, Font font) {
        Component label = label(action);
        int width = font.width(label) + 2 * Metrics.PADDING;
        return switch (action) {
            case SHOW -> SearchTags.hide(new FlatButton(0, 0, width, FIELD, label, FlatButton.Style.NORMAL, () -> this.show(zone)));
            case JUMP -> SearchTags.hide(new FlatButton(0, 0, width, FIELD, label, FlatButton.Style.NORMAL, () -> this.jump(zone)));
            case EDIT -> SearchTags.label(new FlatButton(0, 0, width, FIELD, label, FlatButton.Style.NORMAL, () -> this.edit(zone)),
                    Component.translatable(KEY + "edit_search", zone.name()), title());
            case DELETE -> SearchTags.hide(new FlatButton(0, 0, width, FIELD, label, FlatButton.Style.DANGER, () -> this.deleteArmed(zone.id()),
                    () -> this.delete(zone)));
        };
    }

    public static Component label(ZoneMenu.Action action) {
        return switch (action) {
            case SHOW -> Component.translatable(MENU_KEY + "show");
            case JUMP -> Component.translatable(MENU_KEY + "jump");
            case EDIT -> Component.translatable(KEY + "edit");
            case DELETE -> Component.translatable("propwash.zone.edit.button.delete");
        };
    }

    private Component emptyText() {
        return Component.translatable(this.admin ? KEY + "empty" : MENU_KEY + "empty_own");
    }

    private void leave() {
        if (this.listener != null) {
            this.listener.leave();
        }
    }

    private void flash(Component message, int color) {
        if (this.listener != null) {
            this.listener.flash(message, color);
        }
    }

    private static void toWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }
    }

    void createHere() {
        this.leave();
        if (ZoneEditor.startNew()) {
            ZoneEditScreen.openSticky();
        }
    }

    void edit(Zone zone) {
        this.leave();
        if (ZoneEditor.start(zone)) {
            ZoneEditScreen.openSticky();
        }
    }

    void show(Zone zone) {
        this.leave();
        toWorld();
        ZoneFocus.show(zone);
    }

    void jump(Zone zone) {
        if (!ClientZones.mayJump()) {
            return;
        }
        if (ClientNet.sendToServer(new ZoneJumpC2S(zone.id()))) {
            this.leave();
            toWorld();
            ZoneFocus.show(zone);
        } else {
            this.flash(Component.translatable("propwash.zone.edit.not_sent"), Palette.ERROR);
        }
    }

    void delete(Zone zone) {
        if (!ClientZones.INSTANCE.mayDelete(zone)) {
            return;
        }
        if (!this.deleteArmed(zone.id())) {
            this.deleteArmed.put(zone.id(), Util.getNanos() + DELETE_CONFIRM_NANOS);
            this.flash(Component.translatable("propwash.zone.edit.status.delete"), Palette.WARNING);
            return;
        }
        this.deleteArmed.remove(zone.id());
        boolean sent = ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, zone));
        this.flash(sent ? Component.translatable(MENU_KEY + "deleting", zone.name()) : Component.translatable("propwash.zone.edit.not_sent"),
                sent ? Palette.TEXT_MUTED : Palette.ERROR);
    }

    public @Nullable Component extract(GuiGraphicsExtractor g, Font font, int x, int top, int width, int mouseX, int mouseY, int clipTop,
                                       int clipBottom, int focused) {
        if (this.emptyY >= 0) {
            ThemeText.wrapped(g, font, this.emptyText(), x + Metrics.LABEL_GAP, top + this.emptyY + 1, width - Metrics.LABEL_GAP, Palette.TEXT_MUTED);
        }
        MutableComponent hovered = null;
        ZoneRelease release = ClientZones.INSTANCE.release();
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            Zone zone = entry.zone;
            Zone effective = entry.effective;
            int y = top + entry.y;
            if (zone.id() == focused) {
                g.fill(x, y - 1, x + width, y + entry.height - 2, Palette.SELECTION);
                g.fill(x, y - 1, x + 1, y + entry.height - 2, Palette.ACCENT);
            } else if (i % 2 == 1) {
                g.fill(x, y - 1, x + width, y + entry.height - 2, Palette.ROW_STRIPE);
            }
            int color = 0xFF000000 | ZoneRenderer.color(effective);
            ThemeDraw.dot(g, x + 5, y + 5, color);
            int left = x + TEXT_LEFT;
            int textRight = left + textRoom(width, false);
            int detailY = y + 2 + LINE;
            Component kind = ZoneText.kind(effective);
            int kindWidth = font.width(kind);
            Component name = Component.literal(zone.name());
            Component detail = Component.translatable(KEY + "detail", ZoneText.shape(zone), ZoneText.size(zone), ZoneText.position(zone), zone.priority(),
                    ZoneText.overrides(zone, release));
            if (compact(font, effective, width, false)) {
                ThemeText.fitted(g, font, name, left, y + 2, textRight - left, Palette.TEXT);
                int kindShown = Math.min(kindWidth, width - TEXT_LEFT);
                ThemeText.fitted(g, font, kind, left, detailY, kindShown, color);
                int separatorLeft = left + kindShown;
                int detailLeft = separatorLeft + font.width(SEPARATOR);
                if (detailLeft < x + width) {
                    ThemeText.text(g, font, SEPARATOR, separatorLeft, detailY, Palette.TEXT_FAINT);
                    ThemeText.fitted(g, font, detail, detailLeft, detailY, x + width - detailLeft, Palette.TEXT_FAINT);
                }
            } else {
                ThemeText.fitted(g, font, name, left, y + 2, textRight - left - kindWidth - Metrics.GAP, Palette.TEXT);
                ThemeText.textRight(g, font, kind, textRight, y + 2, color);
                ThemeText.fitted(g, font, detail, left, detailY, width - TEXT_LEFT, Palette.TEXT_FAINT);
            }
            if (entry.owner) {
                ThemeText.fitted(g, font, ZoneText.owner(zone), left, detailY + LINE, width - TEXT_LEFT, Palette.TEXT_MUTED);
            }
            boolean overButton = false;
            for (FlatButton button : entry.buttons()) {
                overButton |= button.isHovered();
            }
            int textBottom = y + ENTRY - 2 + (entry.owner ? LINE : 0);
            if (!overButton && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < textBottom && mouseY >= clipTop && mouseY < clipBottom) {
                hovered = ZoneText.summary(zone, release).append("\n").append(Component.translatable(ZoneText.KEY + "transition.label",
                        ZoneText.transition(zone.transitionTenths())));
                if (!release.blocked(zone).isEmpty()) {
                    hovered.append("\n").append(Component.translatable(ZoneText.OPEN_KEY + "section.blocked"));
                }
            }
        }
        return hovered;
    }

    public static boolean compact(Font font, Zone zone, int width, boolean button) {
        int nameRoom = textRoom(width, button) - font.width(ZoneText.kind(zone)) - Metrics.GAP;
        return font.width(zone.name()) > nameRoom && nameRoom < NAME_MIN_WIDTH;
    }

    public static int nameRoom(Font font, Zone zone, int width, boolean button) {
        int room = textRoom(width, button);
        return compact(font, zone, width, button) ? room : room - font.width(ZoneText.kind(zone)) - Metrics.GAP;
    }

    private static int textRoom(int width, boolean button) {
        return width - TEXT_LEFT - (button ? 74 + Metrics.GAP : 0);
    }

    public static int tooltipWidth() {
        return TOOLTIP_WIDTH;
    }
}
