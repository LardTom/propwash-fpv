package de.lardtom.propwash.share.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.share.AddonDirectory;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareOsd;
import de.lardtom.propwash.share.ShareServer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShareScreen extends Screen implements ThemedScreen, ShareLines.Actions {
    public enum Mode {
        EXPORT,
        IMPORT
    }

    public static final List<ShareLayer> EXPORT_TOGGLES = List.of(ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.OSD, ShareLayer.NAME);
    public static final List<ShareLayer> APPLY_TOGGLES = List.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.OSD);
    public static final int SINGLE_COLUMN_WIDTH = 400;
    private static final int MAX_WIDTH = 460;
    private static final int MAX_HEIGHT = 272;
    private static final int PAD = 8;
    private static final int INPUT_HEIGHT = 16;
    private static final int BUSY_TICKS = 20;
    private static final int SHOPPING_REFRESH_TICKS = 10;
    private static final int MAX_INPUT_LENGTH = 8000;
    private static final int MIN_CODE_ROWS = 2;

    private static @Nullable ShareScreen active;

    private final Screen parent;
    private final WorkbenchContext context;
    private final ShareImportState state = new ShareImportState();
    private Mode mode;
    private boolean copyOnOpen;
    private @Nullable EditBox input;
    private @Nullable FlatButton copyButton;
    private @Nullable FlatButton applyButton;
    private @Nullable FlatButton infoToggle;
    private final List<FlatButton> applyToggles = new ArrayList<>();
    private final List<FlatButton> exportToggles = new ArrayList<>();
    private Optional<DroneData> exportData = Optional.empty();
    private String exportCode = "";
    private List<String> exportRows = List.of();
    private List<ShareLines.Line> exportStats = List.of();
    private @Nullable Component exportStatus;
    private int exportStatusColor = Palette.TEXT_MUTED;
    private @Nullable CompletableFuture<ImportResult> pending;
    private @Nullable ImportResult result;
    private List<ShareLines.Line> resultLines = List.of();
    private List<ShareLines.Line> infoLines = List.of();
    private final List<ShareLines.Placed> placed = new ArrayList<>();
    private final ShareLines.Drag infoDrag = new ShareLines.Drag();
    private final ShareLines.Drag statsDrag = new ShareLines.Drag();
    private final ShareLines.Drag codeDrag = new ShareLines.Drag();
    private @Nullable ShoppingList shopping;
    private int shoppingAge;
    private int busyTicks;
    private int scroll;
    private int infoTotal;
    private long frames;
    private boolean single;
    private boolean infoRow;

    private int left;
    private int top;
    private int right;
    private int bottom;
    private int contentTop;
    private int footerTop;
    private int previewRight;
    private int columnX;
    private int columnWidth;
    private int infoTop;
    private int infoBottom;
    private int codeTop;
    private int codeBottom;
    private int codeMaxBottom;
    private int codeScroll;
    private int statsScroll;
    private int statsTotal;
    private int statsTop;
    private int statsHeight;
    private int titleRight;

    public ShareScreen(Screen parent, WorkbenchContext context, Mode mode) {
        super(Component.translatable("propwash.share.title"));
        this.parent = parent;
        this.context = context;
        this.mode = mode;
        this.state.update(ShareSettings.lastInput());
    }

    public static void open(WorkbenchContext context, Mode mode, boolean copyNow) {
        context.openChild(parent -> {
            ShareScreen screen = new ShareScreen(parent, context, mode);
            screen.copyOnOpen = copyNow;
            return screen;
        });
    }

    public static @Nullable ShareScreen active() {
        return active;
    }

    public Mode mode() {
        return this.mode;
    }

    public ShareImportState state() {
        return this.state;
    }

    public String exportCode() {
        return this.exportCode;
    }

    public @Nullable ImportResult result() {
        return this.result;
    }

    public List<ShareLines.Line> resultLines() {
        return this.resultLines;
    }

    public long frames() {
        return this.frames;
    }

    public WorkbenchContext context() {
        return this.context;
    }

    public @Nullable ShoppingList shopping() {
        return this.shopping;
    }

    public List<ShareLines.Line> infoLines() {
        return this.infoLines;
    }

    public List<ShareLines.Line> exportLines() {
        return this.exportStats;
    }

    public List<ShareLines.Placed> placed() {
        return List.copyOf(this.placed);
    }

    public boolean singleColumn() {
        return this.single;
    }

    public @Nullable FlatButton applyButton() {
        return this.applyButton;
    }

    public @Nullable FlatButton copyButton() {
        return this.copyButton;
    }

    public @Nullable Component exportStatus() {
        return this.exportStatus;
    }

    public void switchMode(Mode next) {
        if (this.mode != next) {
            this.mode = next;
            this.scroll = 0;
            this.rebuildWidgets();
        }
    }

    public void setInput(String text) {
        if (this.input != null) {
            this.input.setValue(text);
        }
        this.onInput(text);
    }

    @Override
    protected void init() {
        active = this;
        int w = Math.min(this.width - 16, MAX_WIDTH);
        int h = Math.min(this.height - 12, MAX_HEIGHT);
        this.left = (this.width - w) / 2;
        this.right = this.left + w;
        this.top = (this.height - h) / 2;
        this.bottom = this.top + h;
        this.contentTop = this.top + Metrics.HEADER_HEIGHT + PAD;
        this.footerTop = this.bottom - Metrics.FOOTER_HEIGHT;
        this.single = w < SINGLE_COLUMN_WIDTH;
        this.previewRight = this.single ? this.left : this.left + Math.max(120, w * 2 / 5);
        this.columnX = this.single ? this.left + PAD : this.previewRight + PAD;
        this.columnWidth = this.right - PAD - this.columnX;
        this.applyToggles.clear();
        this.exportToggles.clear();
        this.copyButton = null;
        this.applyButton = null;
        this.infoToggle = null;
        this.input = null;

        Component exportTab = Component.translatable("propwash.share.tab.export");
        Component importTab = Component.translatable("propwash.share.tab.import");
        int tabImportWidth = this.font.width(importTab) + 16;
        int tabExportWidth = this.font.width(exportTab) + 16;
        int tabY = this.top + (Metrics.HEADER_HEIGHT - 1 - Metrics.ROW_HEIGHT) / 2;
        int tabX = this.right - PAD - tabImportWidth;
        this.addRenderableWidget(new FlatButton(tabX, tabY, tabImportWidth, Metrics.ROW_HEIGHT, importTab, FlatButton.Style.TAB,
                () -> this.mode == Mode.IMPORT, () -> this.switchMode(Mode.IMPORT)));
        this.addRenderableWidget(new FlatButton(tabX - Metrics.GAP - tabExportWidth, tabY, tabExportWidth, Metrics.ROW_HEIGHT, exportTab,
                FlatButton.Style.TAB, () -> this.mode == Mode.EXPORT, () -> this.switchMode(Mode.EXPORT)));
        this.titleRight = tabX - 2 * Metrics.GAP - tabExportWidth;

        int buttonY = this.footerTop + (Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT) / 2;
        Component back = Component.translatable("propwash.share.back");
        int backWidth = Math.max(60, this.font.width(back) + 16);
        this.addRenderableWidget(new FlatButton(this.right - PAD - backWidth, buttonY, backWidth, Metrics.CONTROL_HEIGHT, back,
                FlatButton.Style.NORMAL, this::onClose));

        if (this.mode == Mode.EXPORT) {
            this.initExport(buttonY);
        } else {
            this.initImport(buttonY);
        }
        this.updateStates();
    }

    private void initExport(int buttonY) {
        int y = this.contentTop + Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP;
        int count = EXPORT_TOGGLES.size();
        int toggleWidth = (this.columnWidth - (count - 1) * Metrics.GAP) / count;
        for (int i = 0; i < count; i++) {
            ShareLayer layer = EXPORT_TOGGLES.get(i);
            FlatButton toggle = new FlatButton(this.columnX + i * (toggleWidth + Metrics.GAP), y, toggleWidth, Metrics.CONTROL_HEIGHT,
                    Component.translatable(ShareScreen.toggleKey(layer)), FlatButton.Style.NORMAL,
                    () -> ShareSettings.exports(layer) && this.exportable(layer), () -> {
                ShareSettings.toggleExport(layer);
                this.refreshExport();
            });
            toggle.setTooltip(Tooltip.create(Component.translatable("propwash.share.export.toggle." + layer.key())));
            this.exportToggles.add(this.addRenderableWidget(toggle));
        }
        this.exportData = this.context.result();
        this.infoRow = this.infoMatters();
        if (this.infoRow) {
            y += Metrics.ROW_HEIGHT;
            Component info = Component.translatable("propwash.share.export.addon_info");
            int infoWidth = Math.min(this.columnWidth, this.font.width(info) + 16);
            this.infoToggle = this.addRenderableWidget(new FlatButton(this.columnX, y, infoWidth, Metrics.CONTROL_HEIGHT, info, FlatButton.Style.NORMAL,
                    ShareSettings::addonInfo, () -> {
                ShareSettings.addonInfo(!ShareSettings.addonInfo());
                this.refreshExport();
            }));
        }
        this.codeTop = y + Metrics.ROW_HEIGHT + Metrics.GAP + Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP;
        int codeMax = this.footerTop - PAD - 2 * Metrics.LINE_HEIGHT - Metrics.GAP;
        int statsReserve = 3 * Metrics.LINE_HEIGHT + Metrics.GAP;
        boolean roomForStats = codeMax - statsReserve >= this.codeTop + MIN_CODE_ROWS * Metrics.LINE_HEIGHT + 7;
        this.codeMaxBottom = roomForStats ? codeMax - statsReserve : codeMax;
        this.codeBottom = this.codeMaxBottom;
        Component copy = Component.translatable("propwash.share.export.copy");
        int copyWidth = this.font.width(copy) + 20;
        this.copyButton = this.addRenderableWidget(new FlatButton(this.left + PAD, buttonY, copyWidth, Metrics.CONTROL_HEIGHT, copy,
                FlatButton.Style.PRIMARY, this::copy));
        this.refreshExport();
        if (this.copyOnOpen) {
            this.copyOnOpen = false;
            this.copy();
        }
    }

    private void initImport(int buttonY) {
        Component paste = Component.translatable("propwash.share.import.paste");
        int pasteWidth = this.font.width(paste) + 16;
        int inputWidth = this.right - PAD - pasteWidth - Metrics.GAP - (this.left + PAD);
        EditBox box = new TextField(this.font, this.left + PAD, this.contentTop, inputWidth, INPUT_HEIGHT, Component.translatable("propwash.share.import.field"));
        box.setMaxLength(MAX_INPUT_LENGTH);
        box.setHint(ThemeText.colored(Component.translatable("propwash.share.import.hint"), Palette.TEXT_DISABLED));
        box.setValue(this.state.text());
        box.moveCursorToStart(false);
        box.setResponder(this::onInput);
        this.input = this.addRenderableWidget(box);
        FlatButton pasteButton = new FlatButton(this.right - PAD - pasteWidth, this.contentTop + (INPUT_HEIGHT - Metrics.CONTROL_HEIGHT) / 2,
                pasteWidth, Metrics.CONTROL_HEIGHT, paste, FlatButton.Style.NORMAL, this::paste);
        pasteButton.setTooltip(Tooltip.create(Component.translatable("propwash.share.import.paste.tooltip")));
        this.addRenderableWidget(pasteButton);

        int toggleY = this.footerTop - PAD - Metrics.CONTROL_HEIGHT;
        int labelWidth = this.font.width(Component.translatable("propwash.share.import.apply_label")) + Metrics.GAP;
        int count = APPLY_TOGGLES.size();
        int toggleWidth = (this.columnWidth - labelWidth - (count - 1) * Metrics.GAP) / count;
        if (toggleWidth < 36) {
            labelWidth = 0;
            toggleWidth = (this.columnWidth - (count - 1) * Metrics.GAP) / count;
        }
        for (int i = 0; i < count; i++) {
            ShareLayer layer = APPLY_TOGGLES.get(i);
            FlatButton toggle = new FlatButton(this.columnX + labelWidth + i * (toggleWidth + Metrics.GAP), toggleY, toggleWidth,
                    Metrics.CONTROL_HEIGHT, Component.translatable(ShareScreen.toggleKey(layer)), FlatButton.Style.NORMAL,
                    () -> ShareSettings.applies(layer) && this.available(layer), () -> {
                ShareSettings.toggleApply(layer);
                this.updateStates();
            });
            toggle.setTooltip(Tooltip.create(Component.translatable("propwash.share.import.toggle." + layer.key())));
            this.applyToggles.add(this.addRenderableWidget(toggle));
        }
        this.infoTop = this.contentTop + INPUT_HEIGHT + PAD;
        this.infoBottom = toggleY - Metrics.GAP;
        Component apply = Component.translatable("propwash.share.import.apply");
        int applyWidth = this.font.width(apply) + 20;
        this.applyButton = this.addRenderableWidget(new FlatButton(this.left + PAD, buttonY, applyWidth, Metrics.CONTROL_HEIGHT, apply,
                FlatButton.Style.PRIMARY, this::apply));
        this.setInitialFocus(box);
        this.refreshShopping();
    }

    public static String toggleKey(ShareLayer layer) {
        return "propwash.share.toggle." + layer.key();
    }

    public boolean exportable(ShareLayer layer) {
        return layer != ShareLayer.NAME || this.exportData.flatMap(DroneData::name).isPresent();
    }

    public boolean infoMatters() {
        return this.exportData.map(data -> ShareScreen.foreignWithoutDirectory(data.build())).orElse(false);
    }

    static boolean foreignWithoutDirectory(BuildSpec build) {
        AddonDirectory directory = ShareTables.current().directory();
        for (Identifier id : build.allParts()) {
            String namespace = id.getNamespace();
            if (!namespace.equals(ShareTables.PROPWASH) && directory.byNamespace(namespace).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static List<String> withoutTable(BuildSpec build) {
        Set<String> out = new LinkedHashSet<>();
        ShareTables tables = ShareTables.current();
        for (Identifier id : build.allParts()) {
            if (tables.table(id.getNamespace()).isEmpty()) {
                out.add(id.getNamespace());
            }
        }
        return List.copyOf(out);
    }

    private boolean available(ShareLayer layer) {
        Optional<ShareContent> content = this.state.content();
        if (content.isEmpty()) {
            return false;
        }
        return switch (layer) {
            case PARTS -> this.state.partsReady();
            case PAINT -> content.get().paint().isPresent();
            case TUNE -> content.get().tune().isPresent();
            case OSD -> content.get().osd().isPresent();
            default -> false;
        };
    }

    public Set<ShareLayer> chosenLayers() {
        EnumSet<ShareLayer> out = EnumSet.noneOf(ShareLayer.class);
        for (ShareLayer layer : APPLY_TOGGLES) {
            if (ShareSettings.applies(layer) && this.available(layer)) {
                out.add(layer);
            }
        }
        return out;
    }

    private void onInput(String text) {
        ShareSettings.lastInput(text);
        if (this.state.update(text)) {
            this.scroll = 0;
            this.result = null;
            this.resultLines = List.of();
            this.pending = null;
            this.shopping = null;
            this.shoppingAge = SHOPPING_REFRESH_TICKS;
            this.refreshShopping();
            this.updateStates();
        }
    }

    public void paste() {
        String text = ShareConfigTab.compact(this.minecraft.keyboardHandler.getClipboard());
        this.setInput(text.length() > MAX_INPUT_LENGTH ? text.substring(0, MAX_INPUT_LENGTH) : text);
    }

    private void refreshExport() {
        this.exportData = this.context.result();
        this.exportCode = "";
        this.exportStatus = null;
        this.exportStats = List.of();
        this.layoutCode();
        this.updateExportTooltips();
        if (this.exportData.isEmpty()) {
            this.exportStatus = Component.translatable("propwash.share.export.no_result");
            this.exportStatusColor = Palette.WARNING;
            this.updateStates();
            return;
        }
        Set<ShareLayer> layers = ShareSettings.exportLayers();
        try {
            this.exportCode = ShareService.encode(this.exportData.get(), layers, ShareSettings.addonInfo(), osdJson(layers));
        } catch (RuntimeException e) {
            this.exportStatus = Component.translatable("propwash.share.export.not_shareable", ShareCommands.reason(e));
            this.exportStatusColor = Palette.ERROR;
        }
        if (!this.exportCode.isEmpty() && !ShareCommands.fits(this.exportCode)) {
            this.exportStatus = Component.translatable("propwash.share.export.too_long", this.exportCode.length(), Pw3Params.MAX_INPUT_CHARS);
            this.exportStatusColor = Palette.ERROR;
        }
        this.exportStats = exportLines(this.exportData.get());
        this.layoutCode();
        this.updateStates();
    }

    static Optional<String> osdJson(Set<ShareLayer> layers) {
        if (!layers.contains(ShareLayer.OSD)) {
            return Optional.empty();
        }
        OsdLayoutSpec current = PropwashClientApi.osd().current();
        return Optional.of(current.json());
    }

    static List<ShareLines.Line> exportLines(DroneData data) {
        List<ShareLines.Line> stats = new ArrayList<>();
        for (String namespace : withoutTable(data.build())) {
            stats.add(ShareLines.Line.of(Component.translatable("propwash.share.export.no_table", Component.literal(namespace)), Palette.WARNING)
                    .tagged("no_table:" + namespace));
        }
        PropwashApi.builds().analyze(data.build()).ifPresent(analysis -> ShareLines.addStats(stats, analysis));
        return List.copyOf(stats);
    }

    private void layoutCode() {
        this.codeScroll = 0;
        this.exportRows = this.exportCode.isEmpty() ? List.of() : ShareLines.wrapCode(this.font, this.exportCode, this.columnWidth - 8);
        if (this.exportRows.size() > this.codeRowsShown(this.codeMaxBottom)) {
            this.exportRows = ShareLines.wrapCode(this.font, this.exportCode, this.columnWidth - 8 - ShareLines.SCROLLBAR_SPACE);
        }
        int rows = Math.max(MIN_CODE_ROWS, this.exportRows.size());
        this.codeBottom = Math.min(this.codeMaxBottom, this.codeTop + rows * Metrics.LINE_HEIGHT + 7);
    }

    private int codeRowsShown(int bottom) {
        return Math.max(1, (bottom - 2 - (this.codeTop + 4)) / Metrics.LINE_HEIGHT);
    }

    public int codeScroll() {
        return this.codeScroll;
    }

    public int codeRowsShown() {
        return this.codeRowsShown(this.codeBottom);
    }

    public int[] codeCenter() {
        return new int[]{(this.columnX + this.right - PAD) / 2, (this.codeTop + this.codeBottom) / 2};
    }

    public int statsHeight() {
        return this.footerTop - PAD - (this.codeBottom + Metrics.GAP + 2 * Metrics.LINE_HEIGHT + Metrics.GAP);
    }

    public int[] infoArea() {
        return new int[]{this.columnX, this.infoTop, this.columnX + this.columnWidth, this.infoBottom};
    }

    private boolean overCode(double mouseX, double mouseY) {
        return mouseX >= this.columnX && mouseX < this.right - PAD && mouseY >= this.codeTop && mouseY < this.codeBottom;
    }

    private void updateExportTooltips() {
        for (int i = 0; i < this.exportToggles.size() && i < EXPORT_TOGGLES.size(); i++) {
            ShareLayer layer = EXPORT_TOGGLES.get(i);
            String key = "propwash.share.export.toggle." + layer.key() + (this.exportable(layer) ? "" : ".none");
            this.exportToggles.get(i).setTooltip(Tooltip.create(Component.translatable(key)));
        }
        if (this.infoToggle != null) {
            this.infoToggle.setTooltip(Tooltip.create(Component.translatable("propwash.share.export.addon_info.tooltip")));
        }
    }

    public List<String> exportRows() {
        return this.exportRows;
    }

    public boolean copy() {
        if (this.exportCode.isEmpty() || !ShareCommands.fits(this.exportCode)) {
            return false;
        }
        this.minecraft.keyboardHandler.setClipboard(this.exportCode);
        this.exportStatus = Component.translatable("propwash.share.export.copied", this.exportCode.length());
        this.exportStatusColor = Palette.SUCCESS;
        return true;
    }

    public static boolean applyOsd(ShareOsd osd) {
        OsdLayoutSpec layout;
        try {
            layout = new OsdLayoutSpec(osd.version(), osd.json());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return PropwashClientApi.osd().validate(layout) && PropwashClientApi.osd().apply(layout);
    }

    public @Nullable CompletableFuture<ImportResult> apply() {
        Optional<ShareContent> effective = this.state.effective();
        if (effective.isEmpty() || this.pending != null && !this.pending.isDone() || this.busyTicks > 0) {
            return null;
        }
        ShareContent content = effective.get();
        Set<ShareLayer> chosen = this.chosenLayers();
        List<ShareLines.Line> lines = new ArrayList<>();
        if (chosen.contains(ShareLayer.OSD) && content.osd().isPresent()) {
            boolean applied = applyOsd(content.osd().get());
            lines.add(ShareLines.Line.of(Component.translatable(applied ? "propwash.share.osd.applied" : "propwash.share.osd.invalid"),
                    applied ? Palette.SUCCESS : Palette.ERROR));
        }
        EnumSet<ImportLayer> layers = EnumSet.noneOf(ImportLayer.class);
        if (chosen.contains(ShareLayer.PARTS)) {
            layers.add(ImportLayer.PARTS);
            if (content.name().isPresent()) {
                layers.add(ImportLayer.NAME);
            }
        }
        if (chosen.contains(ShareLayer.PAINT)) {
            layers.add(ImportLayer.PAINT);
        }
        if (chosen.contains(ShareLayer.TUNE)) {
            layers.add(ImportLayer.TUNE);
        }
        this.resultLines = lines;
        this.result = null;
        if (layers.isEmpty()) {
            this.pending = null;
            this.rebuildLines();
            return null;
        }
        DroneData data = ShareServer.importData(content, layers, this.context.result().map(DroneData::build).orElseGet(ShareScene::freestyle));
        CompletableFuture<ImportResult> future = this.context.importIntoWorkbench(data, layers, Pw3Params.TUNE_GROUPS);
        this.pending = future;
        List<ShareLines.Line> osdLines = List.copyOf(lines);
        future.thenAccept(done -> {
            if (this.pending != future) {
                return;
            }
            this.result = done;
            List<ShareLines.Line> all = new ArrayList<>(osdLines);
            all.addAll(ShareLines.result(done));
            this.resultLines = all;
            if (done.status() == ImportStatus.BUSY) {
                this.busyTicks = BUSY_TICKS;
            }
            this.shoppingAge = SHOPPING_REFRESH_TICKS;
            this.rebuildLines();
            this.refreshExport();
        });
        this.rebuildLines();
        this.updateStates();
        return future;
    }

    @Override
    public void copyPw3() {
        Optional<String> code = this.state.pw3();
        if (code.isEmpty()) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(code.get());
        this.resultLines = List.of(ShareLines.Line.of(Component.translatable("propwash.share.copy_pw3.done", code.get().length()), Palette.SUCCESS)
                .tagged("copied_pw3"));
        this.rebuildLines();
    }

    @Override
    public void forceParts() {
        this.state.force(true);
        this.rebuildLines();
        this.updateStates();
    }

    @Override
    public void toggleSubstitutes() {
        ShareSettings.substitutes(!ShareSettings.substitutes());
        this.shoppingAge = SHOPPING_REFRESH_TICKS;
        this.refreshShopping();
        this.updateStates();
    }

    @Override
    public void createWithSubstitutes() {
    }

    @Override
    public void openLink(String url) {
        ShareClient.openLink(this, url);
    }

    private void rebuildLines() {
        if (this.mode != Mode.IMPORT) {
            return;
        }
        List<ShareLines.Line> lines = new ArrayList<>(this.resultLines);
        if (this.pending != null && !this.pending.isDone()) {
            lines.addFirst(ShareLines.Line.of(Component.translatable("propwash.share.import.sending"), Palette.ACCENT));
        }
        lines.addAll(ShareLines.head(this.state, this));
        lines.addAll(ShareLines.addons(this.state, this));
        lines.addAll(ShareLines.substitutes(this.state, this, true, false));
        lines.addAll(ShareLines.details(this.state, this.shopping, this.context.creative()));
        if (this.state.content().flatMap(ShareContent::creative).isPresent()) {
            lines.add(ShareLines.Line.of(Component.translatable("propwash.share.import.creative.workbench"), Palette.TEXT_MUTED)
                    .tagged(ShareLines.TAG_CREATIVE_WORKBENCH));
        }
        this.infoLines = List.copyOf(lines);
    }

    private void refreshShopping() {
        this.shopping = null;
        Optional<ShareContent> content = this.state.effective();
        if (content.isPresent() && !this.context.creative() && this.state.decoded().ok()) {
            BuildSpec build = content.get().build();
            List<Identifier> unknown = new ArrayList<>();
            for (Identifier id : build.allParts()) {
                if (this.state.decoded().unknownParts().contains(id) || this.state.decoded().wrongKind().contains(id)) {
                    unknown.add(id);
                }
            }
            if (unknown.isEmpty()) {
                this.shopping = ShoppingList.fromMissing(build, this.context.missingParts(content.get().toDroneData()));
            } else {
                this.shopping = ShoppingList.of(build, this.minecraft == null || this.minecraft.player == null ? null : this.minecraft.player.getInventory(),
                        this.context, unknown);
            }
        }
        this.rebuildLines();
    }

    private void updateStates() {
        if (this.copyButton != null) {
            this.copyButton.active = !this.exportCode.isEmpty() && ShareCommands.fits(this.exportCode);
        }
        if (this.applyButton != null) {
            boolean waiting = this.pending != null && !this.pending.isDone();
            this.applyButton.active = !waiting && this.busyTicks <= 0 && !this.chosenLayers().isEmpty();
        }
        for (int i = 0; i < this.applyToggles.size() && i < APPLY_TOGGLES.size(); i++) {
            this.applyToggles.get(i).active = this.available(APPLY_TOGGLES.get(i));
        }
        for (int i = 0; i < this.exportToggles.size() && i < EXPORT_TOGGLES.size(); i++) {
            this.exportToggles.get(i).active = this.exportable(EXPORT_TOGGLES.get(i));
        }
    }

    public boolean applyVisible() {
        FlatButton button = this.applyButton;
        return button != null && button.getX() >= 0 && button.getY() >= 0 && button.getX() + button.getWidth() <= this.width
                && button.getY() + button.getHeight() <= this.height;
    }

    public boolean addonHintVisible() {
        return ShareLines.hintVisible(this.placed, this.state);
    }

    @Override
    public void tick() {
        if (this.busyTicks > 0) {
            this.busyTicks--;
        }
        if (this.mode == Mode.EXPORT && !this.context.result().equals(this.exportData)) {
            this.refreshExport();
            if (this.infoMatters() != this.infoRow) {
                this.rebuildWidgets();
            }
        }
        if (this.mode == Mode.IMPORT && ++this.shoppingAge >= SHOPPING_REFRESH_TICKS) {
            this.shoppingAge = 0;
            this.refreshShopping();
        }
        this.updateStates();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (this.mode == Mode.IMPORT && (this.infoDrag.press(event.x(), event.y(), this.scroll) || ShareLines.click(this.placed, event.x(), event.y()))) {
                return true;
            }
            if (this.mode == Mode.EXPORT && (this.codeDrag.press(event.x(), event.y(), this.codeScroll * Metrics.LINE_HEIGHT)
                    || this.statsDrag.press(event.x(), event.y(), this.statsScroll))) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.infoDrag.active()) {
            this.scroll = ShareLines.clampScroll(this.infoDrag.offset(event.y()), this.infoTotal, this.infoBottom - this.infoTop);
            return true;
        }
        if (this.codeDrag.active()) {
            int max = Math.max(0, this.exportRows.size() - this.codeRowsShown(this.codeBottom));
            this.codeScroll = Math.max(0, Math.min(max, (int) Math.round(this.codeDrag.offset(event.y()) / (double) Metrics.LINE_HEIGHT)));
            return true;
        }
        if (this.statsDrag.active()) {
            this.statsScroll = ShareLines.clampScroll(this.statsDrag.offset(event.y()), this.statsTotal, this.statsHeight);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean dragging = this.infoDrag.active() || this.codeDrag.active() || this.statsDrag.active();
        this.infoDrag.release();
        this.codeDrag.release();
        this.statsDrag.release();
        return dragging || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.mode == Mode.IMPORT && mouseX >= this.columnX && mouseX < this.right - PAD && mouseY >= this.infoTop && mouseY < this.infoBottom) {
            int height = this.infoBottom - this.infoTop;
            this.scroll = ShareLines.clampScroll(this.scroll - (int) Math.round(scrollY * Metrics.SCROLL_STEP), this.infoTotal, height);
            return true;
        }
        if (this.mode == Mode.EXPORT && this.overCode(mouseX, mouseY) && this.exportRows.size() > this.codeRowsShown(this.codeBottom)) {
            this.codeScroll = Math.max(0, Math.min(this.codeScroll - (int) Math.signum(scrollY), this.exportRows.size() - this.codeRowsShown(this.codeBottom)));
            return true;
        }
        if (this.mode == Mode.EXPORT && mouseX >= this.columnX && mouseX < this.right - PAD && mouseY >= this.statsTop
                && mouseY < this.statsTop + this.statsHeight && this.statsTotal > this.statsHeight) {
            this.statsScroll = ShareLines.clampScroll(this.statsScroll - (int) Math.round(scrollY * Metrics.SCROLL_STEP), this.statsTotal, this.statsHeight);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public int infoScroll() {
        return this.scroll;
    }

    public void scrollInfo(int value) {
        this.scroll = ShareLines.clampScroll(value, this.infoTotal, this.infoBottom - this.infoTop);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.frames++;
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title, this.titleRight);
        if (!this.single) {
            int stageTop = this.mode == Mode.IMPORT ? this.contentTop + INPUT_HEIGHT + PAD : this.contentTop;
            int stageBottom = this.footerTop - PAD;
            ThemeDraw.previewStage(g, this.left + PAD, stageTop, this.previewRight, stageBottom);
            Optional<DroneData> preview = this.mode == Mode.EXPORT ? this.exportData : this.state.preview();
            if (preview.isPresent()) {
                float yaw = (float) ((System.currentTimeMillis() % 14000L) / 14000.0 * 360.0);
                PropwashClientApi.renderPreview(g, preview.get(), this.left + PAD, stageTop, this.previewRight, stageBottom, yaw);
            } else {
                Component hint = this.mode == Mode.EXPORT ? Component.translatable("propwash.share.preview.no_result")
                        : this.state.decoded().ok() ? Component.translatable("propwash.share.preview.unknown")
                        : Component.translatable("propwash.share.preview.empty");
                int hintWidth = this.previewRight - this.left - 2 * PAD - 8;
                int hintHeight = ThemeText.wrappedHeight(this.font, hint, hintWidth);
                ThemeText.wrapped(g, this.font, hint, this.left + PAD + 4 + 4, (stageTop + stageBottom - hintHeight) / 2, hintWidth, Palette.TEXT_FAINT);
            }
            preview.flatMap(DroneData::name).ifPresent(name -> ThemeText.line(g, this.font, Component.literal(name), this.left + PAD + 4,
                    this.previewRight - 4, stageTop + 4, Palette.TEXT, ThemeText.Align.LEFT, true));
        }
        if (this.mode == Mode.EXPORT) {
            this.extractExport(g);
        } else {
            this.extractImport(g, mouseX, mouseY);
        }
        ThemeDraw.footerBar(g, this.left, this.footerTop, this.right, this.bottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
    }

    private void extractExport(GuiGraphicsExtractor g) {
        ThemeDraw.sectionTitle(g, this.font, Component.translatable("propwash.share.export.layers"), this.columnX, this.contentTop, this.columnWidth);
        int titleY = this.codeTop - Metrics.SECTION_HEIGHT - Metrics.SECTION_GAP;
        Component codeTitle = this.exportCode.isEmpty() ? Component.translatable("propwash.share.export.code")
                : Component.translatable("propwash.share.export.code_length", this.exportCode.length());
        ThemeDraw.sectionTitle(g, this.font, codeTitle, this.columnX, titleY, this.columnWidth);
        ThemeDraw.framedBox(g, this.columnX, this.codeTop, this.right - PAD, this.codeBottom, Palette.FIELD, Palette.BORDER);
        int shownRows = this.codeRowsShown(this.codeBottom);
        this.codeScroll = Math.max(0, Math.min(this.codeScroll, this.exportRows.size() - shownRows));
        int rowY = this.codeTop + 4;
        for (int i = this.codeScroll; i < this.exportRows.size() && i < this.codeScroll + shownRows; i++) {
            ThemeText.text(g, this.font, this.exportRows.get(i), this.columnX + 4, rowY, Palette.TEXT);
            rowY += Metrics.LINE_HEIGHT;
        }
        this.codeDrag.track(this.right - PAD - 1 - Metrics.SCROLLBAR_WIDTH, this.codeTop + 1, this.codeBottom - this.codeTop - 2,
                this.exportRows.size() > shownRows ? this.exportRows.size() * Metrics.LINE_HEIGHT : 0);
        if (this.exportRows.size() > shownRows) {
            ThemeDraw.scrollbar(g, this.right - PAD - 1 - Metrics.SCROLLBAR_WIDTH, this.codeTop + 1, this.codeBottom - this.codeTop - 2,
                    this.exportRows.size() * Metrics.LINE_HEIGHT, this.codeScroll * Metrics.LINE_HEIGHT, this.codeDrag.active());
        }
        int y = this.codeBottom + Metrics.GAP;
        if (this.exportData.isPresent() && !this.exportCode.isEmpty()) {
            Component inside = Component.translatable("propwash.share.export.contains",
                    ShareCommands.layerList(ShareContent.of(this.exportData.get(), ShareSettings.exportLayers(), Optional.empty()).layers()));
            ThemeText.line(g, this.font, inside, this.columnX, this.right - PAD, y, Palette.TEXT_MUTED, ThemeText.Align.LEFT, true);
        }
        if (this.exportStatus != null) {
            ThemeText.line(g, this.font, this.exportStatus, this.columnX, this.right - PAD, y + Metrics.LINE_HEIGHT, this.exportStatusColor,
                    ThemeText.Align.LEFT, true);
        }
        this.statsTop = y + 2 * Metrics.LINE_HEIGHT + Metrics.GAP;
        this.statsHeight = this.footerTop - PAD - this.statsTop;
        this.statsTotal = 0;
        if (!this.exportCode.isEmpty() && this.statsHeight > Metrics.LINE_HEIGHT) {
            this.statsTotal = ShareLines.scrollHeight(this.font, this.exportStats, this.columnWidth, this.statsHeight);
            this.statsScroll = ShareLines.clampScroll(this.statsScroll, this.statsTotal, this.statsHeight);
            ShareLines.render(g, this.font, this.exportStats, this.columnX, this.statsTop, this.columnWidth, this.statsHeight, this.statsScroll, -1, -1,
                    null, this.statsDrag);
        } else {
            this.statsDrag.clear();
        }
    }

    private void extractImport(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        this.rebuildLines();
        int height = this.infoBottom - this.infoTop;
        this.infoTotal = ShareLines.scrollHeight(this.font, this.infoLines, this.columnWidth, height);
        this.scroll = ShareLines.clampScroll(this.scroll, this.infoTotal, height);
        ShareLines.render(g, this.font, this.infoLines, this.columnX, this.infoTop, this.columnWidth, height, this.scroll, mouseX, mouseY, this.placed,
                this.infoDrag);
        if (!this.applyToggles.isEmpty() && this.applyToggles.getFirst().getX() > this.columnX) {
            ThemeText.text(g, this.font, Component.translatable("propwash.share.import.apply_label"), this.columnX,
                    Metrics.textY(this.applyToggles.getFirst().getY(), Metrics.CONTROL_HEIGHT), Palette.TEXT_MUTED);
        }
    }
}
