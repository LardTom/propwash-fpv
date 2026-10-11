package de.lardtom.propwash.blackbox.client;

import com.mojang.blaze3d.Blaze3D;
import de.lardtom.propwash.blackbox.BlackboxConfig;
import de.lardtom.propwash.blackbox.BlackboxLibrary;
import de.lardtom.propwash.blackbox.BlackboxRate;
import de.lardtom.propwash.blackbox.BlackboxRecorder;
import de.lardtom.propwash.blackbox.BlackboxWriter;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FilterSettings;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.video.record.ClipInfo;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlackboxTab extends UiTab {
    public static final String ID = "blackbox";
    static final String KEY = "propwash.blackbox.tab.";
    static final int LIST_ROWS = 8;
    static final int LIST_LINES_MIN = 4;
    static final int REFRESH_TICKS = 20;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.ROOT);

    private BlackboxConfig working = BlackboxClient.CONFIG.get().copy();
    private boolean edited;
    private int column;
    private int introTop;
    private int logsTop;
    private int listTop;
    private int listRows;
    private int listLines = LIST_ROWS + 1;
    private boolean stacked;
    private int statusTop;
    private int contentHeight;
    private int refreshTicks;
    private List<BlackboxLibrary.LogFile> logs = List.of();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    public int order() {
        return 320;
    }

    @Override
    public boolean hasDefaults() {
        return true;
    }

    @Override
    protected void build() {
        this.logs = this.library().list();
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        this.column = Math.min(w, Metrics.COLUMN_MAX * 2);
        int left = this.x;
        int field = Math.min(w, Metrics.COLUMN_MAX);
        int labelWidth = Math.min(field / 2, Math.max(Metrics.LABEL_MIN, this.widestLabel() + Metrics.PADDING));
        int cy = Metrics.SECTION_HEIGHT;
        this.introTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), Component.translatable(KEY + "intro"), this.column) + 6;

        ChoiceButton<Boolean> enabled = this.add(ChoiceButton.toggle(left, this.top() + cy, field, FIELD,
                Component.translatable(KEY + "enabled"), labelWidth, () -> this.working.enabled,
                v -> {
                    this.working.enabled = v;
                    this.markEdited();
                }, ThemedWidgets::onOff, () -> false));
        enabled.setTooltip(Tooltip.create(Component.translatable(KEY + "enabled.tip")));
        cy += ROW;

        ChoiceButton<BlackboxRate> rate = this.add(new ChoiceButton<>(left, this.top() + cy, field, FIELD,
                Component.translatable(KEY + "rate"), labelWidth, List.of(BlackboxRate.values()), () -> this.working.rate,
                v -> {
                    this.working.rate = v;
                    this.markEdited();
                }, v -> rateLabel(v, pidLoopHz()), () -> BlackboxRate.FULL));
        rate.setTooltip(Tooltip.create(Component.translatable(KEY + "rate.tip")));
        cy += ROW;

        ChoiceButton<Integer> storage = this.add(new ChoiceButton<>(left, this.top() + cy, field, FIELD,
                Component.translatable(KEY + "storage"), labelWidth, BlackboxConfig.STORAGE_MEGABYTES, () -> this.working.storageMegabytes,
                v -> {
                    this.working.storageMegabytes = v;
                    this.markEdited();
                }, v -> Component.translatable(KEY + "storage.value", megabytes(v)), () -> BlackboxConfig.DEFAULT_STORAGE_MEGABYTES));
        storage.setTooltip(Tooltip.create(Component.translatable(KEY + "storage.tip")));
        cy += ROW + 6;

        this.logsTop = cy;
        cy += Metrics.SECTION_HEIGHT;
        int buttonWidth = Math.min(field, Math.max(120, this.font().width(Component.translatable(KEY + "open_folder")) + 24));
        Button folder = Button.builder(Component.translatable(KEY + "open_folder"), button -> this.openFolder())
                .bounds(left, this.top() + cy, buttonWidth, Metrics.CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(KEY + "open_folder.tip")))
                .build();
        this.add(folder);
        cy += ROW + 2;
        this.listTop = cy;
        this.listLines = listLines(this.height - cy - 8 - Metrics.SECTION_HEIGHT - this.statusHeight() - 4);
        this.listRows = this.displayedRows();
        this.stacked = this.needsStack();
        cy += this.listHeight() + 8;
        this.statusTop = cy;
        cy += Metrics.SECTION_HEIGHT + this.statusHeight();
        this.contentHeight = cy + 4;
        this.endScroll(this.contentHeight);
    }

    private int widestLabel() {
        int widest = 0;
        for (String key : new String[]{"enabled", "rate", "storage"}) {
            widest = Math.max(widest, this.font().width(Component.translatable(KEY + key)));
        }
        return widest;
    }

    static int listLines(int space) {
        return Math.clamp(space / Metrics.LINE_HEIGHT, LIST_LINES_MIN, LIST_ROWS + 1);
    }

    static int shownLogs(int count, int lines) {
        return count <= Math.min(lines, LIST_ROWS) ? count : lines - 1;
    }

    private int displayedRows() {
        int n = this.logs.size();
        if (n == 0) {
            return 1;
        }
        int shown = shownLogs(n, this.listLines);
        return shown + (n > shown ? 1 : 0);
    }

    @Override
    public void tick() {
        if (++this.refreshTicks >= REFRESH_TICKS) {
            this.refreshTicks = 0;
            List<BlackboxLibrary.LogFile> fresh = this.library().list();
            boolean rowsChanged = fresh.size() != this.logs.size();
            this.logs = fresh;
            if (rowsChanged && this.displayedRows() != this.listRows || this.needsStack() != this.stacked) {
                this.host.rebuild();
            }
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font();
        int left = this.x;
        int top = this.top();
        this.section(g, Component.translatable(KEY + "section.recording"), left, top, this.column);
        ThemeText.wrapped(g, font, Component.translatable(KEY + "intro"), left, top + this.introTop, this.column, Palette.TEXT_MUTED);
        this.section(g, Component.translatable(KEY + "section.logs"), left, top + this.logsTop, this.column);
        int ly = top + this.listTop;
        if (this.logs.isEmpty()) {
            Component empty = Component.translatable(KEY + "list.empty");
            int lines = font.split(empty, Math.max(40, this.column)).size();
            if (lines > 1 && lines <= this.listLines * (this.stacked ? 2 : 1)) {
                ThemeText.wrapped(g, font, empty, left, ly, Math.max(40, this.column), Palette.TEXT_FAINT);
            } else {
                ThemeText.fitted(g, font, empty, left, ly, this.column, Palette.TEXT_FAINT);
            }
        }
        String current = this.currentFile();
        int shown = shownLogs(this.logs.size(), this.listLines);
        for (int i = 0; i < shown; i++) {
            BlackboxLibrary.LogFile log = this.logs.get(i);
            boolean live = log.name().equals(current);
            Component details = this.details(log, live);
            if (this.stacked) {
                ThemeText.fitted(g, font, log.name(), left, ly, this.column, live ? Palette.ACCENT : Palette.TEXT);
                ly += Metrics.LINE_HEIGHT;
                ThemeText.fitted(g, font, details, left, ly, this.column, Palette.TEXT_MUTED);
            } else {
                int detailsWidth = Math.min(font.width(details), this.column / 2);
                ThemeText.fitted(g, font, log.name(), left, ly, this.column - detailsWidth - 8, live ? Palette.ACCENT : Palette.TEXT);
                ThemeText.fittedRight(g, font, details, left + this.column, ly, detailsWidth, Palette.TEXT_MUTED);
            }
            ly += Metrics.LINE_HEIGHT;
        }
        if (this.logs.size() > shown) {
            ThemeText.text(g, font, Component.translatable(KEY + "list.more", this.logs.size() - shown), left, ly, Palette.TEXT_FAINT);
        }
        int sy = top + this.statusTop;
        this.section(g, Component.translatable(KEY + "section.status"), left, sy, this.column);
        sy += Metrics.SECTION_HEIGHT;
        for (Line line : this.statusLines()) {
            sy += ThemeText.wrapped(g, font, line.text(), left, sy, this.column, line.color()) + 2;
        }
        int used = sy - top + 4;
        if (this.scroll != null && used != this.contentHeight) {
            this.contentHeight = used;
            this.scroll.setContentHeight(used);
            this.scrollOffset = this.scroll.offset();
        }
        this.extractScrollbar(g);
    }

    private int statusHeight() {
        int h = 0;
        for (Line line : this.statusLines()) {
            h += ThemeText.wrappedHeight(this.font(), line.text(), this.column) + 2;
        }
        return h;
    }

    record Line(Component text, int color) {
    }

    List<Line> statusLines() {
        List<Line> lines = new ArrayList<>();
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
        BlackboxRecorder.State state = recorder == null ? (this.working.enabled ? BlackboxRecorder.State.READY : BlackboxRecorder.State.OFF)
                : recorder.state();
        if (!this.working.enabled && state != BlackboxRecorder.State.LOGGING) {
            state = BlackboxRecorder.State.OFF;
        }
        Component stateText = switch (state) {
            case OFF -> Component.translatable(KEY + "state.off");
            case READY -> Component.translatable(KEY + "state.ready", rateLabel(this.working.rate, pidLoopHz()));
            case WAITING -> Component.translatable(KEY + "state.waiting", Component.translatable(SwitchFunction.BLACKBOX.translationKey()));
            case LOGGING -> Component.translatable(KEY + "state.logging", String.valueOf(this.currentFile()), size(this.liveBytes(null)));
            case PAUSED -> Component.translatable(KEY + "state.paused");
            case FULL -> Component.translatable(KEY + "state.full");
        };
        int stateColor = switch (state) {
            case OFF -> Palette.TEXT_MUTED;
            case READY, WAITING -> Palette.TEXT;
            case LOGGING -> Palette.SUCCESS;
            case PAUSED, FULL -> Palette.WARNING;
        };
        lines.add(new Line(stateText, stateColor));
        long total = 0L;
        for (BlackboxLibrary.LogFile log : this.logs) {
            total += log.bytes();
        }
        lines.add(new Line(Component.translatable(KEY + "storage.used", this.logs.size(), size(total), megabytes(this.working.storageMegabytes)),
                Palette.TEXT_MUTED));
        if (recorder != null) {
            BlackboxWriter writer = recorder.writer();
            BlackboxWriter.Saved saved = writer.lastSaved();
            if (saved != null && state != BlackboxRecorder.State.LOGGING) {
                lines.add(new Line(Component.translatable(saved.full() ? KEY + "last.full" : KEY + "last", saved.path().getFileName().toString(),
                        size(saved.bytes())), saved.full() ? Palette.WARNING : Palette.TEXT_MUTED));
            }
            if (writer.pruned() > 0) {
                lines.add(new Line(Component.translatable(KEY + "pruned", writer.pruned()), Palette.TEXT_MUTED));
            }
            if (recorder.dropped() > 0) {
                lines.add(new Line(Component.translatable(KEY + "dropped", recorder.dropped()), Palette.WARNING));
            }
            String error = writer.lastError() != null ? writer.lastError() : recorder.failure();
            if (error != null && !error.isEmpty()) {
                lines.add(new Line(Component.translatable(KEY + "error", error), Palette.ERROR));
            }
        }
        KeyMapping key = FunctionKeys.mapping(SwitchFunction.BLACKBOX);
        Component function = Component.translatable(SwitchFunction.BLACKBOX.translationKey());
        lines.add(new Line(key == null || key.isUnbound() ? Component.translatable(KEY + "hint.switch.unbound", function)
                : Component.translatable(KEY + "hint.switch", function, FunctionKeys.keyLabel(SwitchFunction.BLACKBOX)), Palette.TEXT_FAINT));
        lines.add(new Line(Component.translatable(KEY + "hint.tools"), Palette.TEXT_FAINT));
        return lines;
    }

    private Component details(BlackboxLibrary.LogFile log, boolean live) {
        return live ? Component.translatable(KEY + "list.live", size(this.liveBytes(log)))
                : Component.translatable(KEY + "list.details", when(log.modifiedMillis()), ClipInfo.duration(log.durationMillis() / 1000L),
                size(log.bytes()));
    }

    private boolean needsStack() {
        Font font = this.font();
        String current = this.currentFile();
        int shown = shownLogs(this.logs.size(), this.listLines);
        for (int i = 0; i < shown; i++) {
            BlackboxLibrary.LogFile log = this.logs.get(i);
            int detailsWidth = Math.min(font.width(this.details(log, log.name().equals(current))), this.column / 2);
            if (TextFit.mode(font.width(log.name()), this.column - detailsWidth - 8) == TextFit.Mode.CUT) {
                return true;
            }
        }
        return false;
    }

    private int listHeight() {
        int extra = this.stacked ? shownLogs(this.logs.size(), this.listLines) : 0;
        return (this.listRows + extra) * Metrics.LINE_HEIGHT;
    }

    private String currentFile() {
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
        return recorder == null ? null : recorder.writer().currentFile();
    }

    private long liveBytes(BlackboxLibrary.LogFile fallback) {
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
        if (recorder != null && recorder.writer().currentFile() != null) {
            return recorder.writer().currentBytes();
        }
        return fallback == null ? 0L : fallback.bytes();
    }

    private BlackboxLibrary library() {
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
        return recorder != null ? recorder.library() : new BlackboxLibrary(BlackboxSession.directory(Minecraft.getInstance()));
    }

    static int pidLoopHz() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        FilterSettings filters = sim != null ? sim.activeFilters() : new FilterSettings();
        return (int) Math.round(filters.pidLoop.hz());
    }

    static Component rateLabel(BlackboxRate rate, int pidLoopHz) {
        return Component.translatable(KEY + "rate.value", "1/" + rate.denominator(), hz(rate.hz(pidLoopHz)));
    }

    static Component hz(int hz) {
        return hz >= 1000 ? Component.translatable(KEY + "khz", Numbers.of(hz / 1000.0, 1)) : Component.translatable(KEY + "hz", hz);
    }

    static Component megabytes(int mb) {
        return mb >= 1024 ? Component.translatable(KEY + "gb", Numbers.of(mb / 1024.0, 1)) : Component.translatable(KEY + "mb", mb);
    }

    static Component size(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return Component.translatable(KEY + "gb", Numbers.of(mb / 1024.0, 1));
        }
        if (mb >= 10.0) {
            return Component.translatable(KEY + "mb", Numbers.of(mb, 0));
        }
        return Component.translatable(KEY + "mb", Numbers.of(Math.max(0.1, mb), 1));
    }

    static String when(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private void openFolder() {
        Path dir = BlackboxSession.directory(Minecraft.getInstance());
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        Blaze3D.openPath(dir);
    }

    @Override
    public void resetDefaults() {
        this.working = new BlackboxConfig();
        this.markEdited();
        this.host.rebuild();
    }

    @Override
    public void save() {
        if (!this.edited) {
            return;
        }
        BlackboxConfig saved = this.working.copy();
        BlackboxConfig.sanitize(saved);
        BlackboxClient.CONFIG.set(saved);
        this.edited = false;
    }

    @Override
    public void discard() {
        this.working = BlackboxClient.CONFIG.get().copy();
        this.edited = false;
    }

    BlackboxConfig working() {
        return this.working;
    }

    private void markEdited() {
        this.edited = true;
        this.host.changed();
    }
}
