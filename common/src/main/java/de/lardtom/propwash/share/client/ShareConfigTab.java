package de.lardtom.propwash.share.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareOsd;
import de.lardtom.propwash.share.ShareServer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShareConfigTab extends ConfigTab implements ShareLines.Actions {
    public static final String ID = "propwash:sharecode";
    public static final int ORDER = 1000;
    private static final int INPUT_HEIGHT = 16;
    private static final int BUSY_TICKS = 20;
    private static final int MAX_INPUT_LENGTH = 8000;
    private static final int SUMMARY_LINES = 3;
    private static final int PREVIEW_WIDTH = 120;
    private static final int PREVIEW_MIN_WIDTH = 330;
    private static final int PREVIEW_MAX_HEIGHT = 90;
    private static final int MIN_DETAIL_HEIGHT = 3 * Metrics.LINE_HEIGHT;

    private static @Nullable ShareConfigTab active;

    private final ShareImportState state = new ShareImportState();
    private @Nullable EditBox input;
    private @Nullable FlatButton copyButton;
    private @Nullable FlatButton tuneButton;
    private @Nullable FlatButton paintButton;
    private @Nullable FlatButton osdButton;
    private @Nullable FlatButton buildButton;
    private final List<FlatButton> exportToggles = new ArrayList<>();
    private final List<ShareLines.Placed> placed = new ArrayList<>();
    private boolean heldNamed = true;
    private @Nullable Component exportStatus;
    private int exportColor = Palette.TEXT_MUTED;
    private List<ShareLines.Line> resultLines = List.of();
    private @Nullable CompletableFuture<ImportResult> pending;
    private @Nullable ImportResult lastResult;
    private int busyTicks;
    private long frames;
    private int heldY;
    private int exportStatusY;
    private int importTitleY;
    private int exportTitleY;
    private int summaryY;
    private int resultY;
    private int inputY;
    private int heldRows = 1;
    private int statusRows = 1;
    private int summaryRows = SUMMARY_LINES;
    private boolean refocus;
    private int refocusCursor;
    private boolean creativeBuilt;
    private int summaryAddonY = -1;
    private int summaryAddonRows;

    public ShareConfigTab() {
        this.state.update(ShareSettings.lastInput());
    }

    public static @Nullable ShareConfigTab active() {
        return active;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.share.config.title");
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
        return "propwash.share.config.footer";
    }

    @Override
    public boolean hasDefaults() {
        return false;
    }

    public ShareImportState state() {
        return this.state;
    }

    public long frames() {
        return this.frames;
    }

    public @Nullable ImportResult lastResult() {
        return this.lastResult;
    }

    public List<ShareLines.Line> resultLines() {
        return this.resultLines;
    }

    public @Nullable Component exportStatus() {
        return this.exportStatus;
    }

    public List<ShareLines.Placed> placed() {
        return List.copyOf(this.placed);
    }

    public @Nullable FlatButton buildButton() {
        return this.buildButton;
    }

    public @Nullable FlatButton tuneButton() {
        return this.tuneButton;
    }

    public boolean addonHintVisible() {
        if (!this.state.addonProblem()) {
            return true;
        }
        if (this.summaryAddonY >= 0 && this.host != null) {
            int shift = this.host.contentShift();
            int top = this.y + shift;
            int bottom = Math.min(this.y + this.height, this.clipBottom()) + shift;
            if (this.summaryAddonY >= top && this.summaryAddonY + this.summaryAddonRows * Metrics.LINE_HEIGHT - 1 <= bottom) {
                return true;
            }
        }
        return ShareLines.hintVisible(this.placed, this.state);
    }

    @Override
    protected void build() {
        active = this;
        Font font = this.host.font();
        this.exportToggles.clear();
        this.heldNamed = true;
        this.creativeBuilt = this.creative();
        int y = this.y;
        this.importTitleY = y;
        y += Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP;
        Component paste = Component.translatable("propwash.share.import.paste");
        int pasteWidth = font.width(paste) + 16;
        this.inputY = y;
        EditBox box = new TextField(font, this.x, y, this.width - pasteWidth - Metrics.GAP, INPUT_HEIGHT, Component.translatable("propwash.share.import.field"));
        box.setMaxLength(MAX_INPUT_LENGTH);
        box.setHint(ThemeText.colored(Component.translatable("propwash.share.import.hint"), Palette.TEXT_DISABLED));
        box.setValue(this.state.text());
        box.moveCursorToStart(false);
        box.setResponder(this::onInput);
        this.input = this.host.addWidget(box);
        if (this.refocus) {
            this.refocus = false;
            box.setCursorPosition(Math.min(this.refocusCursor, box.getValue().length()));
            box.setHighlightPos(box.getCursorPosition());
            this.host.screen().setFocused(box);
        }
        FlatButton pasteButton = new FlatButton(this.x + this.width - pasteWidth, y + (INPUT_HEIGHT - Metrics.CONTROL_HEIGHT) / 2, pasteWidth,
                Metrics.CONTROL_HEIGHT, paste, FlatButton.Style.NORMAL, this::paste);
        pasteButton.setTooltip(Tooltip.create(Component.translatable("propwash.share.import.paste.tooltip")));
        this.host.addWidget(pasteButton);
        y += INPUT_HEIGHT + Metrics.GAP;
        this.summaryY = y;
        this.summaryRows = this.summaryRowCount(font);
        y += this.summaryRows * Metrics.LINE_HEIGHT + Metrics.GAP;
        boolean creative = this.creativeBuilt;
        List<Component> labels = List.of(Component.translatable("propwash.share.config.tune"), Component.translatable("propwash.share.config.paint"),
                Component.translatable("propwash.share.config.osd"), Component.translatable(creative ? "propwash.share.config.create"
                        : "propwash.share.config.shopping"));
        int widest = 0;
        for (Component label : labels) {
            widest = Math.max(widest, font.width(label) + 12);
        }
        int columns = (this.width - 3 * Metrics.GAP) / 4 >= widest ? 4 : 2;
        int buttonWidth = (this.width - (columns - 1) * Metrics.GAP) / columns;
        List<FlatButton> buttons = new ArrayList<>();
        List<Runnable> actions = List.of(this::sendTune, this::sendPaint, this::applyOsd, this::buildOrShop);
        List<String> tooltips = List.of("propwash.share.config.tune.tooltip", "propwash.share.config.paint.tooltip", "propwash.share.config.osd.tooltip",
                creative ? "propwash.share.config.create.tooltip" : "propwash.share.config.shopping.tooltip");
        for (int i = 0; i < 4; i++) {
            int column = i % columns;
            int row = i / columns;
            FlatButton button = new FlatButton(this.x + column * (buttonWidth + Metrics.GAP), y + row * Metrics.ROW_HEIGHT, buttonWidth,
                    Metrics.CONTROL_HEIGHT, labels.get(i), i == 3 && creative ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL, actions.get(i));
            button.setTooltip(Tooltip.create(Component.translatable(tooltips.get(i))));
            buttons.add(this.host.addWidget(button));
        }
        this.tuneButton = buttons.get(0);
        this.paintButton = buttons.get(1);
        this.osdButton = buttons.get(2);
        this.buildButton = buttons.get(3);
        y += (4 / columns) * Metrics.ROW_HEIGHT + Metrics.GAP;
        this.heldRows = rows(font, this.heldLine(), this.width);
        this.statusRows = this.statusRowCount(font);
        Component copy = Component.translatable("propwash.share.config.copy");
        int copyWidth = font.width(copy) + 20;
        int count = ShareScreen.EXPORT_TOGGLES.size();
        int toggleWidth = (this.width - copyWidth - count * Metrics.GAP) / count;
        boolean oneRow = toggleWidth >= 44;
        if (!oneRow) {
            toggleWidth = (this.width - (count - 1) * Metrics.GAP) / count;
        }
        this.exportTitleY = y;
        y += Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP;
        this.heldY = y;
        y += this.heldRows * Metrics.LINE_HEIGHT + 2;
        for (int i = 0; i < count; i++) {
            ShareLayer layer = ShareScreen.EXPORT_TOGGLES.get(i);
            FlatButton toggle = new FlatButton(this.x + i * (toggleWidth + Metrics.GAP), y, toggleWidth, Metrics.CONTROL_HEIGHT,
                    Component.translatable(ShareScreen.toggleKey(layer)), FlatButton.Style.NORMAL,
                    () -> ShareSettings.exports(layer) && this.exportable(layer), () -> ShareSettings.toggleExport(layer));
            toggle.setTooltip(Tooltip.create(Component.translatable("propwash.share.export.toggle." + layer.key())));
            this.exportToggles.add(this.host.addWidget(toggle));
        }
        if (!oneRow) {
            y += Metrics.ROW_HEIGHT;
        }
        this.copyButton = this.host.addWidget(new FlatButton(oneRow ? this.x + this.width - copyWidth : this.x, y, copyWidth, Metrics.CONTROL_HEIGHT,
                copy, FlatButton.Style.PRIMARY, this::copyHeld));
        y += Metrics.ROW_HEIGHT;
        this.exportStatusY = y;
        y += this.statusRows * Metrics.LINE_HEIGHT + Metrics.GAP;
        this.resultY = y;
        this.reportBottom(y + MIN_DETAIL_HEIGHT);
        this.updateButtons();
    }

    public boolean exportable(ShareLayer layer) {
        return layer != ShareLayer.NAME || PropwashClientApi.heldDrone().flatMap(DroneData::name).isPresent();
    }

    private boolean creative() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasInfiniteMaterials();
    }

    private void onInput(String text) {
        ShareSettings.lastInput(text);
        if (this.state.update(text)) {
            this.resultLines = List.of();
            this.pending = null;
            this.updateButtons();
        }
    }

    public void setInput(String text) {
        if (this.input != null) {
            this.input.setValue(text);
        }
        this.onInput(text);
    }

    public void paste() {
        String text = compact(this.host.minecraft().keyboardHandler.getClipboard());
        this.setInput(text.length() > MAX_INPUT_LENGTH ? text.substring(0, MAX_INPUT_LENGTH) : text);
    }

    public Optional<String> exportHeld() {
        Optional<DroneData> held = PropwashClientApi.heldDrone();
        if (held.isEmpty()) {
            this.report(Component.translatable("propwash.share.config.no_drone"), Palette.WARNING);
            return Optional.empty();
        }
        EnumSet<ShareLayer> layers = EnumSet.copyOf(ShareSettings.exportLayers());
        Minecraft minecraft = this.host.minecraft();
        Optional<CreativeSpec> creative = minecraft.player == null ? Optional.empty() : ShareServer.creativeOf(minecraft.player.getMainHandItem());
        if (creative.isPresent()) {
            layers.add(ShareLayer.CREATIVE);
        }
        String code;
        try {
            code = ShareService.encode(held.get(), creative, layers, ShareSettings.addonInfo(), ShareScreen.osdJson(layers));
        } catch (RuntimeException e) {
            this.report(Component.translatable("propwash.share.export.not_shareable", ShareCommands.reason(e)), Palette.ERROR);
            return Optional.empty();
        }
        if (!ShareCommands.fits(code)) {
            this.report(Component.translatable("propwash.share.export.too_long", code.length(), Pw3Params.MAX_INPUT_CHARS), Palette.ERROR);
            return Optional.empty();
        }
        return Optional.of(code);
    }

    public boolean copyHeld() {
        Optional<String> code = this.exportHeld();
        if (code.isEmpty()) {
            return false;
        }
        this.host.minecraft().keyboardHandler.setClipboard(code.get());
        this.report(Component.translatable("propwash.share.export.copied", code.get().length()), Palette.SUCCESS);
        return true;
    }

    private void report(Component text, int color) {
        this.exportStatus = text;
        this.exportColor = color;
    }

    public Optional<Map<String, Integer>> paintCost() {
        Optional<PaintSpec> paint = this.state.content().flatMap(ShareContent::paint);
        Minecraft minecraft = this.host.minecraft();
        if (paint.isEmpty() || this.creative() || minecraft.player == null) {
            return Optional.empty();
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!PropwashApi.builds().isDrone(held)) {
            return Optional.empty();
        }
        return Optional.of(PropwashApi.builds().paintCost(held, paint.get()));
    }

    private boolean ready() {
        return this.busyTicks <= 0 && (this.pending == null || this.pending.isDone());
    }

    public @Nullable CompletableFuture<ImportResult> sendTune() {
        Optional<ShareContent> content = this.state.content();
        if (content.isEmpty() || content.get().tune().isEmpty() || !this.ready()) {
            return null;
        }
        EnumSet<ImportLayer> layers = EnumSet.of(ImportLayer.TUNE);
        return this.send(PropwashClientApi.sendImport(new ImportRequest(ShareServer.importData(content.get(), layers, heldBuild()), layers,
                Pw3Params.TUNE_GROUPS, ImportTarget.HELD_DRONE)));
    }

    public @Nullable CompletableFuture<ImportResult> sendPaint() {
        Optional<ShareContent> content = this.state.content();
        if (content.isEmpty() || content.get().paint().isEmpty() || !this.ready()) {
            return null;
        }
        return this.send(PropwashClientApi.sendImport(ImportRequest.paintOnHeldDrone(ShareServer.importData(content.get(), EnumSet.of(ImportLayer.PAINT),
                heldBuild()))));
    }

    private static BuildSpec heldBuild() {
        return PropwashClientApi.heldDrone().map(DroneData::build).orElseGet(ShareScene::freestyle);
    }

    private CompletableFuture<ImportResult> send(CompletableFuture<ImportResult> future) {
        this.pending = future;
        this.resultLines = List.of(ShareLines.Line.of(Component.translatable("propwash.share.import.sending"), Palette.ACCENT));
        future.thenAccept(result -> {
            if (this.pending != future) {
                return;
            }
            this.lastResult = result;
            this.resultLines = ShareLines.result(result);
            if (result.status() == ImportStatus.BUSY) {
                this.busyTicks = BUSY_TICKS;
            }
            this.updateButtons();
        });
        this.updateButtons();
        return future;
    }

    public boolean applyOsd() {
        Optional<ShareOsd> osd = this.state.content().flatMap(ShareContent::osd);
        if (osd.isEmpty()) {
            return false;
        }
        boolean applied = ShareScreen.applyOsd(osd.get());
        this.resultLines = List.of(ShareLines.Line.of(Component.translatable(applied ? "propwash.share.osd.applied" : "propwash.share.osd.invalid"),
                applied ? Palette.SUCCESS : Palette.ERROR));
        return applied;
    }

    public @Nullable CompletableFuture<ImportResult> buildOrShop() {
        ShareDecoded decoded = this.state.decoded();
        if (!decoded.ok()) {
            return null;
        }
        Minecraft minecraft = this.host.minecraft();
        if (this.creative()) {
            Optional<ShareContent> content = this.state.content();
            if (content.isPresent() && this.ready() && this.state.partsReady() && !this.state.usingSubstitutes()) {
                return this.send(ShareCreativeClient.createDrone(content.get()));
            }
            return null;
        }
        Optional<ShareContent> effective = this.state.effective();
        if (effective.isEmpty()) {
            return null;
        }
        List<net.minecraft.resources.Identifier> unknown = new ArrayList<>(decoded.unknownParts());
        unknown.addAll(decoded.wrongKind());
        ShoppingList shopping = ShoppingList.of(effective.get().build(), minecraft.player == null ? null : minecraft.player.getInventory(), null, unknown);
        List<ShareLines.Line> lines = new ArrayList<>();
        ShareLines.addShopping(lines, shopping, this.state);
        lines.add(ShareLines.Line.of(Component.translatable("propwash.share.config.at_workbench"), Palette.TEXT_MUTED));
        this.resultLines = lines;
        return null;
    }

    @Override
    public void copyPw3() {
        Optional<String> code = this.state.pw3();
        if (code.isEmpty()) {
            return;
        }
        this.host.minecraft().keyboardHandler.setClipboard(code.get());
        this.resultLines = List.of(ShareLines.Line.of(Component.translatable("propwash.share.copy_pw3.done", code.get().length()), Palette.SUCCESS)
                .tagged("copied_pw3"));
    }

    @Override
    public void forceParts() {
        this.state.force(true);
        this.updateButtons();
    }

    @Override
    public void toggleSubstitutes() {
        ShareSettings.substitutes(!ShareSettings.substitutes());
        this.updateButtons();
    }

    @Override
    public void createWithSubstitutes() {
        this.createSubstituted();
    }

    public @Nullable CompletableFuture<ImportResult> createSubstituted() {
        Optional<ShareContent> content = this.state.substituted();
        if (content.isEmpty() || !this.ready() || this.state.locked() || !this.creative()) {
            return null;
        }
        if (!this.state.substituteCheck().map(BuildCheck::buildable).orElse(false)) {
            return null;
        }
        return this.send(ShareCreativeClient.createDrone(content.get()));
    }

    @Override
    public void openLink(String url) {
        ShareClient.openLink(this.host.screen(), url);
    }

    public static String compact(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void updateButtons() {
        Optional<ShareContent> content = this.state.content();
        boolean held = PropwashClientApi.heldDrone().isPresent();
        boolean ready = this.ready();
        if (this.copyButton != null) {
            this.copyButton.active = held;
        }
        if (this.tuneButton != null) {
            this.tuneButton.active = ready && held && content.map(c -> c.tune().isPresent()).orElse(false);
        }
        if (this.paintButton != null) {
            this.paintButton.active = ready && held && content.map(c -> c.paint().isPresent()).orElse(false);
        }
        if (this.osdButton != null) {
            this.osdButton.active = content.map(c -> c.osd().isPresent()).orElse(false);
        }
        if (this.buildButton != null) {
            boolean creative = this.creative();
            this.buildButton.active = creative ? ready && this.state.decoded().partsKnown() && this.state.partsReady() : this.state.decoded().ok();
        }
        boolean named = this.exportable(ShareLayer.NAME);
        for (int i = 0; i < this.exportToggles.size() && i < ShareScreen.EXPORT_TOGGLES.size(); i++) {
            ShareLayer layer = ShareScreen.EXPORT_TOGGLES.get(i);
            FlatButton toggle = this.exportToggles.get(i);
            toggle.active = this.exportable(layer);
            if (layer == ShareLayer.NAME && named != this.heldNamed) {
                toggle.setTooltip(Tooltip.create(Component.translatable("propwash.share.export.toggle.name" + (named ? "" : ".none"))));
            }
        }
        this.heldNamed = named;
    }

    @Override
    public void tick() {
        if (this.busyTicks > 0) {
            this.busyTicks--;
        }
        this.updateButtons();
        if (this.host != null && this.width > 0) {
            Font font = this.host.font();
            if (rows(font, this.heldLine(), this.width) != this.heldRows || this.statusRowCount(font) != this.statusRows
                    || this.summaryRowCount(font) != this.summaryRows || this.creative() != this.creativeBuilt) {
                EditBox box = this.input;
                this.refocus = box != null && box.isFocused();
                this.refocusCursor = box == null ? 0 : box.getCursorPosition();
                this.host.rebuild();
            }
        }
    }

    public static int rows(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return 1;
        }
        return Math.max(1, font.split(text, Math.max(8, width)).size());
    }

    private static int flow(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
        if (font.width(text) <= width) {
            ThemeText.text(g, font, text, x, y, color);
            return Metrics.LINE_HEIGHT;
        }
        int row = y;
        for (FormattedCharSequence line : font.split(text, Math.max(8, width))) {
            ThemeText.text(g, font, line, x, row, color);
            row += Metrics.LINE_HEIGHT;
        }
        return Math.max(Metrics.LINE_HEIGHT, row - y);
    }

    private Component heldLine() {
        return PropwashClientApi.heldDrone().map(data -> (Component) Component.translatable("propwash.share.config.held",
                data.name().<Component>map(Component::literal).orElseGet(() -> ShareCommands.partName(data.build().frame()))))
                .orElse(Component.translatable("propwash.share.config.no_drone"));
    }

    private int statusRowCount(Font font) {
        return this.exportStatus == null ? 1 : rows(font, this.exportStatus, this.width);
    }

    private int summaryRowCount(Font font) {
        int total = 0;
        for (ShareLines.Line line : this.summary()) {
            total += rows(font, line.text(), this.width);
        }
        return Math.max(SUMMARY_LINES, total);
    }

    public int summaryRows() {
        return this.summaryRows;
    }

    public int actionsTop() {
        return this.tuneButton == null ? 0 : this.tuneButton.getY();
    }

    public @Nullable String flowProblem() {
        if (this.host == null) {
            return "Tab ist nicht aufgebaut";
        }
        Font font = this.host.font();
        int needed = this.summaryRowCount(font);
        if (needed != this.summaryRows) {
            return "Zusammenfassung braucht " + needed + " Zeilen, Platz ist für " + this.summaryRows;
        }
        for (ShareLines.Line line : this.summary()) {
            for (FormattedCharSequence row : font.split(line.text(), Math.max(8, this.width))) {
                if (rows(font, line.text(), this.width) > 1 && font.width(row) > this.width) {
                    return "Zeile breiter als der Tab: " + font.width(row) + " von " + this.width;
                }
            }
        }
        FlatButton below = this.tuneButton;
        if (below != null && this.input != null
                && below.getY() - this.input.getY() < this.summaryY + this.summaryRows * Metrics.LINE_HEIGHT - this.inputY) {
            return "Knöpfe liegen in der Zusammenfassung: Knopf " + (below.getY() - this.input.getY()) + " unter dem Eingabefeld, Text bis "
                    + (this.summaryY + this.summaryRows * Metrics.LINE_HEIGHT - this.inputY);
        }
        return null;
    }

    public List<ShareLines.Line> detailLines() {
        List<ShareLines.Line> lines = new ArrayList<>(this.resultLines);
        if (!this.state.decoded().ok()) {
            return lines;
        }
        List<ShareLines.Line> head = ShareLines.head(this.state, this);
        lines.addAll(head.subList(1, head.size()));
        lines.addAll(ShareLines.addons(this.state, this));
        lines.addAll(ShareLines.substitutes(this.state, this, false, this.creative()));
        lines.addAll(ShareLines.details(this.state, null, this.creative()));
        this.paintCost().ifPresent(cost -> {
            lines.add(ShareLines.Line.section(Component.translatable("propwash.share.section.paint_cost")));
            ShareLines.addPaintCost(lines, cost);
        });
        return lines;
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.frames++;
        Font font = this.host.font();
        int right = this.x + this.width;
        ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.share.config.import_title"), this.x, this.importTitleY, this.width);
        List<ShareLines.Line> summary = this.summary();
        int y = this.summaryY;
        this.summaryAddonY = -1;
        for (ShareLines.Line line : summary) {
            int rows = flow(g, font, line.text(), this.x, y, this.width, line.color());
            if (line.tag().startsWith(ShareLines.TAG_ADDON)) {
                this.summaryAddonY = y;
                this.summaryAddonRows = rows / Metrics.LINE_HEIGHT;
            }
            y += rows;
        }
        ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.share.config.export_title"), this.x, this.exportTitleY, this.width);
        Optional<DroneData> held = PropwashClientApi.heldDrone();
        flow(g, font, this.heldLine(), this.x, this.heldY, this.width, held.isPresent() ? Palette.TEXT : Palette.TEXT_MUTED);
        if (this.exportStatus != null) {
            flow(g, font, this.exportStatus, this.x, this.exportStatusY, this.width, this.exportColor);
        }
        this.placed.clear();
        int textWidth = this.width;
        Optional<DroneData> preview = this.state.preview();
        List<ShareLines.Line> lines = this.detailLines();
        boolean withPreview = preview.isPresent() && this.width >= PREVIEW_MIN_WIDTH;
        if (withPreview) {
            textWidth = this.width - PREVIEW_WIDTH - Metrics.GAP;
        }
        int total = ShareLines.height(font, lines, textWidth);
        int bottom = this.resultY + Math.max(total, withPreview ? PREVIEW_MAX_HEIGHT : 0);
        if (withPreview) {
            int x0 = right - PREVIEW_WIDTH;
            int y1 = this.resultY + PREVIEW_MAX_HEIGHT;
            ThemeDraw.previewStage(g, x0, this.resultY, right, y1);
            float yaw = (float) ((System.currentTimeMillis() % 14000L) / 14000.0 * 360.0);
            PropwashClientApi.renderPreview(g, preview.get(), x0, this.resultY, right, y1, yaw);
        }
        if (!lines.isEmpty()) {
            ShareLines.render(g, font, lines, this.x, this.resultY, textWidth, total + 1, 0, mouseX, mouseY, this.placed);
        }
        this.reportBottom(bottom);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT && ShareLines.click(this.placed, event.x(), event.y());
    }

    private List<ShareLines.Line> summary() {
        List<ShareLines.Line> lines = new ArrayList<>();
        ShareDecoded decoded = this.state.decoded();
        lines.add(ShareLines.status(decoded));
        if (!decoded.ok()) {
            return lines;
        }
        ShareContent content = decoded.require();
        String language = ShareImportState.language();
        Component name = content.name().<Component>map(Component::literal).orElseGet(() -> ShareLines.partName(content.build().frame(), this.state));
        lines.add(ShareLines.Line.of(Component.translatable("propwash.share.config.summary", name, ShareLines.partName(content.build().motor(), this.state),
                ShareLines.partName(content.build().battery(), this.state)), Palette.TEXT));
        Optional<ShareImportState.Addon> problem = this.state.addons().stream().filter(ShareImportState.Addon::problem).findFirst();
        if (problem.isPresent()) {
            lines.add(ShareLines.Line.of(ShareLines.addonText(problem.get()), ShareLines.addonColor(problem.get()))
                    .tagged(ShareLines.TAG_ADDON + problem.get().key()));
        } else if (!decoded.unknownParts().isEmpty()) {
            lines.add(ShareLines.Line.of(Component.translatable("propwash.share.import.unknown", decoded.unknownParts().size()), Palette.ERROR));
        } else if (!decoded.inactiveParts().isEmpty()) {
            lines.add(ShareLines.Line.of(Component.translatable("propwash.share.import.inactive", decoded.inactiveParts().size()), Palette.WARNING));
        } else if (!decoded.wrongKind().isEmpty()) {
            lines.add(ShareLines.Line.of(Component.translatable("propwash.share.import.wrong_kind", ShareCommands.idList(decoded.wrongKind())),
                    Palette.WARNING));
        } else if (this.state.check().isPresent()) {
            BuildCheck check = this.state.check().get();
            if (!check.problems().isEmpty()) {
                lines.add(ShareLines.Line.of(Component.translatable(BuildCheck.translationKey(check.problems().getFirst())), Palette.ERROR));
            } else {
                Locale locale = ShareCommands.locale(language);
                lines.add(ShareLines.Line.of(this.state.stats().<Component>map(stats -> Component.translatable("propwash.share.config.stats",
                        ShareCommands.number(stats.massGrams(), 0, locale), ShareCommands.number(stats.thrustToWeight(), 1, locale),
                        ShareCommands.number(stats.mixedFlightTimeMin(), 1, locale))).orElse(Component.translatable("propwash.share.config.buildable")),
                        check.warnings().isEmpty() ? Palette.SUCCESS : Palette.WARNING));
            }
        }
        return lines;
    }

    @Override
    public void discard() {
        if (active == this) {
            active = null;
        }
    }

    @Override
    public void save() {
        if (active == this) {
            active = null;
        }
    }
}
