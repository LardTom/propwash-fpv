package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.zone.ZoneView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ZonesTab extends UiTab implements ZoneSection.Listener {
    public static final String ID = "zones";
    public static final int ORDER = 790;
    public static final String KEY = "propwash.zones_open.menu.";
    public static final long FOCUS_NANOS = 3_000_000_000L;
    private static int pendingFocus;

    private final ZoneSection zones = new ZoneSection();
    private ZoneView.Show showDraft = ZoneViewConfig.show();
    private @Nullable ChoiceButton<ZoneView.Show> showButton;
    private int introY;
    private int zonesY;
    private int viewY;
    private int aboutY;
    private int focused;
    private long focusUntil;

    public ZonesTab() {
        this.zones.listener(this);
    }

    public static boolean open(int focus) {
        pendingFocus = focus;
        boolean opened = ConfiguratorScreen.openTab(null, ID);
        if (!opened) {
            pendingFocus = 0;
        }
        return opened;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable(KEY + "tab");
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Storage storage() {
        return Storage.MIXED;
    }

    @Override
    public String mixedStorageKey() {
        return KEY + "footer";
    }

    @Override
    public boolean readOnly() {
        return !ClientZones.INSTANCE.creatable();
    }

    @Override
    public String readOnlyKey() {
        return KEY + "footer_read_only";
    }

    @Override
    public boolean hasDefaults() {
        return false;
    }

    public ZoneSection zones() {
        return this.zones;
    }

    public int focused() {
        return this.focusUntil > Util.getNanos() ? this.focused : 0;
    }

    public int zonesTop() {
        return this.top() + this.zonesY;
    }

    public int screenY(ZoneSection.Entry entry) {
        return this.top() + entry.y();
    }

    public int viewTop() {
        return this.y;
    }

    public boolean atTop() {
        return this.scroll == null || this.scroll.offset() <= 0;
    }

    public int rowWidth() {
        return this.innerWidth();
    }

    public int left() {
        return this.x;
    }

    public void reveal(ZoneSection.Entry entry) {
        ScrollPanel panel = this.scroll;
        for (int i = 0; panel != null && i < 64; i++) {
            ZoneSection.Entry current = this.zones.entry(entry.zone().id());
            if (current == null) {
                return;
            }
            int y = this.screenY(current);
            if (y >= this.y && y + current.height() <= this.clipBottom()) {
                return;
            }
            if (!panel.scroll(y < this.y ? 1.0 : -1.0)) {
                return;
            }
            this.scrollOffset = panel.offset();
        }
    }

    private Component intro() {
        ClientZones synced = ClientZones.INSTANCE;
        if (!synced.known()) {
            return Component.translatable(KEY + "intro.unknown");
        }
        if (synced.editable()) {
            return Component.translatable(KEY + "intro.admin");
        }
        return Component.translatable(synced.creatable() ? KEY + "intro.player" : KEY + "intro.locked");
    }

    private boolean warn() {
        ClientZones synced = ClientZones.INSTANCE;
        return !synced.known() || !synced.editable() && !synced.creatable();
    }

    private static Component about() {
        return Component.translatable(KEY + "about");
    }

    @Override
    protected void build() {
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        Font font = this.font();
        int cy = 0;
        this.introY = cy;
        cy += ThemeText.wrappedHeight(font, this.intro(), w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING + Metrics.GAP;
        panel.stop(cy);
        this.zonesY = cy;
        cy += Metrics.SECTION_HEIGHT;
        cy = this.zones.build(cy, this.x, this.top(), w, font, widget -> this.add(widget), panel::stop);
        panel.stop(cy);
        cy += Metrics.SECTION_GAP;
        this.viewY = cy;
        cy += Metrics.SECTION_HEIGHT;
        Component label = Component.translatable(KEY + "view.show");
        ChoiceButton<ZoneView.Show> show = new ChoiceButton<>(this.x, this.top() + cy, w, FIELD, label, Math.min(w / 2, font.width(label) + 12),
                List.of(ZoneView.Show.values()), () -> this.showDraft, value -> {
                    this.showDraft = value;
                    this.host.changed();
                }, value -> Component.translatable(KEY + "view.show." + value.key()), () -> ZoneView.DEFAULT_SHOW);
        show.setTooltip(Tooltip.create(Component.translatable(KEY + "view.show.tooltip")));
        this.showButton = this.add(show);
        cy += ROW;
        panel.stop(cy);
        this.aboutY = cy;
        cy += ThemeText.wrappedHeight(font, about(), w) + 4;
        this.endScroll(cy);
        if (pendingFocus != 0) {
            int id = pendingFocus;
            pendingFocus = 0;
            ZoneSection.Entry entry = this.zones.entry(id);
            if (entry != null) {
                this.focused = id;
                this.focusUntil = Util.getNanos() + FOCUS_NANOS;
                this.reveal(entry);
            }
        }
    }

    public ZoneView.Show showDraft() {
        return this.showDraft;
    }

    public @Nullable ChoiceButton<ZoneView.Show> showButton() {
        return this.showButton;
    }

    @Override
    public void save() {
        if (this.showDraft != ZoneViewConfig.show()) {
            ZoneViewConfig.set(this.showDraft);
        }
    }

    @Override
    public void discard() {
        this.showDraft = ZoneViewConfig.show();
    }

    @Override
    public void tick() {
        if (this.zones.stale()) {
            this.host.rebuild();
        }
    }

    @Override
    public void leave() {
        if (this.host != null && this.host.screen() instanceof ConfiguratorScreen screen) {
            if (screen.hasUnsavedChanges()) {
                screen.saveAndClose();
            } else {
                screen.discardAndClose();
            }
        }
    }

    @Override
    public void flash(Component message, int color) {
        super.flash(message, color);
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font();
        int w = this.innerWidth();
        int top = this.top();
        Component intro = this.intro();
        boolean warn = this.warn();
        int introHeight = ThemeText.wrappedHeight(font, intro, w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING;
        int introTop = top + this.introY;
        ThemeDraw.notice(g, this.x, introTop, this.x + w, introTop + introHeight, warn ? Palette.WARNING : Palette.ACCENT, 1.0F);
        ThemeText.wrapped(g, font, intro, this.x + Metrics.PADDING, introTop + Metrics.PADDING, w - 2 * Metrics.PADDING,
                warn ? Palette.WARNING : Palette.TEXT_MUTED);
        this.section(g, ZoneSection.heading(this.zones.admin()), this.x, top + this.zonesY, this.zones.headingWidth(w));
        this.section(g, Component.translatable(KEY + "view.section"), this.x, top + this.viewY, w);
        int clip = Math.min(this.y + this.height, this.clipBottom());
        Component tip = this.zones.extract(g, font, this.x, top, w, mouseX, mouseY, this.y, clip, this.focused());
        ThemeText.wrapped(g, font, about(), this.x, top + this.aboutY, w, Palette.TEXT_FAINT);
        if (tip != null) {
            ThemeText.tooltipWrapped(g, font, tip, ZoneSection.TOOLTIP_WIDTH, mouseX, mouseY);
        }
        this.extractScrollbar(g);
    }
}
