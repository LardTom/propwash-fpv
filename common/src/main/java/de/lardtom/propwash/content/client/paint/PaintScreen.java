package de.lardtom.propwash.content.client.paint;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.paint.PaintC2S;
import de.lardtom.propwash.content.paint.PaintService;
import de.lardtom.propwash.drone.client.render.PaintPicker;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetViews;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Vector2f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class PaintScreen extends Screen implements ThemedScreen, WidgetViews {
    private static final String KEY = "propwash.paint.";
    private static final int MAX_WIDTH = 440;
    private static final int MAX_HEIGHT = 266;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 8;
    private static final int LIST_WIDTH = 126;
    private static final int PALETTE_WIDTH = 112;
    private static final int ROW = 13;
    private static final int CHIP_W = 26;
    private static final int CHIP_H = 16;
    private static final int CHIP_GAP = 2;
    private static final int FINISH_W = 20;
    private static final int PRESET_ROW = 20;
    private static final int PREVIEW_MIN = 110;
    private static final int STACKED_PREVIEW = 96;
    private static final long STATUS_MILLIS = 3500L;

    private final Screen parent;
    private final DroneWorkbenchMenu menu;
    private DronePaint draft;
    private PaintSlot selected = PaintSlot.FRAME;
    private float yaw = 210.0F;
    private long lastFrameMillis;
    private long lastDragMillis;
    private boolean pressedPreview;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private boolean updatingHex;
    private @Nullable EditBox hex;
    private @Nullable FlatButton applyButton;
    private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
    private @Nullable ContentScroll scroll;
    private @Nullable FlatButton propsButton;
    private @Nullable FlatButton resetSlotButton;
    private @Nullable FlatButton pasteButton;
    private Component status = Component.empty();
    private int statusColor = Palette.TEXT_MUTED;
    private long statusUntil;
    private @Nullable DroneBuild cachedBuild;
    private Set<PaintSlot> available = Set.of();
    private Map<PaintSlot, Integer> defaults = Map.of();
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int bodyTop;
    private int bodyBottom;
    private int presetTop;
    private @Nullable FlatButton backButton;
    private int listX;
    private int paletteX;
    private int previewX0;
    private int previewY0;
    private int previewX1;
    private int previewY1;
    private long frames;

    public PaintScreen(Screen parent, DroneWorkbenchMenu menu) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.menu = menu;
        this.draft = menu.paint();
    }

    public DronePaint draft() {
        return this.draft;
    }

    public PaintSlot selected() {
        return this.selected;
    }

    public long frames() {
        return this.frames;
    }

    public DroneWorkbenchMenu menu() {
        return this.menu;
    }

    public Set<PaintSlot> availableSlots() {
        this.refreshBuild();
        return this.available;
    }

    public void select(PaintSlot slot) {
        if (slot == null) {
            return;
        }
        this.selected = slot;
        this.syncHex();
    }

    public void setDraft(DronePaint paint) {
        this.draft = paint == null ? DronePaint.DEFAULT : paint;
        this.syncHex();
    }

    public void paintSelected(int rgb) {
        this.setDraft(this.draft.with(this.selected, rgb));
    }

    public void resetSelected() {
        this.setDraft(this.draft.without(this.selected));
    }

    public void applyToAllProps() {
        OptionalInt color = this.draft.color(this.selected);
        this.setDraft(color.isPresent() ? this.draft.withAllProps(color.getAsInt()) : this.draft.withoutProps());
        this.showStatus(Component.translatable(KEY + "status.all_props"), Palette.ACCENT);
    }

    public void applyPreset(PaintPreset preset) {
        this.setDraft(preset.paint());
        this.showStatus(Component.translatable(KEY + "status.preset", Component.translatable(preset.translationKey())), Palette.ACCENT);
    }

    public PaintCost cost() {
        return PaintCost.of(this.menu.paint(), this.draft);
    }

    public boolean changed() {
        return !this.draft.equals(this.menu.paint());
    }

    public boolean affordable() {
        return this.minecraft != null && this.minecraft.player != null && PaintService.affordable(this.minecraft.player, this.cost());
    }

    public boolean canApply() {
        return this.changed() && !this.menu.frame().isEmpty() && this.affordable();
    }

    public boolean apply() {
        if (!this.canApply()) {
            return false;
        }
        boolean sent = ClientNet.sendToServer(new PaintC2S(this.menu.containerId, this.draft));
        this.showStatus(Component.translatable(sent ? KEY + "status.applied" : KEY + "status.offline"), sent ? Palette.SUCCESS : Palette.ERROR);
        return sent;
    }

    public void copy() {
        if (this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(this.draft.toText());
            this.showStatus(Component.translatable(KEY + "status.copied"), Palette.ACCENT);
        }
    }

    public boolean paste() {
        if (this.minecraft == null) {
            return false;
        }
        Optional<DronePaint> pasted = DronePaint.fromText(this.minecraft.keyboardHandler.getClipboard());
        if (pasted.isEmpty()) {
            this.showStatus(Component.translatable(KEY + "status.paste_invalid"), Palette.WARNING);
            return false;
        }
        this.setDraft(pasted.get());
        this.showStatus(Component.translatable(KEY + "status.pasted"), Palette.ACCENT);
        return true;
    }

    private void showStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
        this.statusUntil = Util.getMillis() + STATUS_MILLIS;
    }

    public DroneBuild previewBuild() {
        Optional<DroneBuild> built = this.menu.check().build();
        if (built.isPresent()) {
            return built.get();
        }
        Optional<PartInfo> part = DroneAssembly.part(this.menu.frame());
        if (part.isPresent() && part.get() instanceof FrameType frame) {
            for (DroneBuild preset : DroneBuild.PRESETS) {
                if (preset.frame() == frame) {
                    return preset;
                }
            }
        }
        return DroneBuild.FREESTYLE;
    }

    private void refreshBuild() {
        DroneBuild build = this.previewBuild();
        if (!build.equals(this.cachedBuild)) {
            this.cachedBuild = build;
            this.available = PaintPicker.available(build);
            this.defaults = PaintPicker.defaults(build);
            if (!this.available.contains(this.selected) && !this.available.isEmpty()) {
                this.selected = this.available.iterator().next();
                this.syncHex();
            }
        }
    }

    public int shownColor(PaintSlot slot) {
        OptionalInt color = this.draft.color(slot);
        if (color.isPresent()) {
            return color.getAsInt();
        }
        return this.defaults.getOrDefault(slot, 0x808080);
    }

    @Override
    protected void init() {
        this.lastFrameMillis = Util.getMillis();
        int w = Math.min(this.width - 12, MAX_WIDTH);
        int h = Math.min(this.height - 12, MAX_HEIGHT);
        this.left = (this.width - w) / 2;
        this.top = (this.height - h) / 2;
        this.right = this.left + w;
        this.bottom = this.top + h;
        this.bodyTop = this.top + HEADER + 6;
        this.bodyBottom = this.top + MAX_HEIGHT - FOOTER - PRESET_ROW - 4;
        this.bodyWidgets.clear();
        this.listX = this.left + PAD;
        this.paletteX = this.right - PAD - PALETTE_WIDTH;
        int buttons = 2 * Metrics.CONTROL_HEIGHT + 4;
        boolean stacked = w - 4 * PAD - LIST_WIDTH - PALETTE_WIDTH < PREVIEW_MIN;
        if (stacked) {
            this.previewX0 = this.listX;
            this.previewX1 = this.right - PAD;
            this.previewY0 = this.bodyBottom + PAD;
            this.previewY1 = this.previewY0 + STACKED_PREVIEW;
            this.presetTop = this.previewY1 + 4 + buttons + 4;
        } else {
            this.previewX0 = this.listX + LIST_WIDTH + PAD;
            this.previewX1 = this.paletteX - PAD;
            this.previewY0 = this.bodyTop;
            this.previewY1 = this.bodyBottom - buttons - 4;
            this.presetTop = this.bodyBottom + 4;
        }

        int bx = this.previewX0;
        int bw = this.previewX1 - this.previewX0;
        int by = this.previewY1 + 4;
        this.propsButton = this.body(new FlatButton(bx, by, bw, Metrics.CONTROL_HEIGHT,
                Component.translatable(KEY + "all_props"), FlatButton.Style.NORMAL, this::applyToAllProps));
        int half = (bw - 4) / 2;
        by += Metrics.CONTROL_HEIGHT + 4;
        this.body(new FlatButton(bx, by, half, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "copy"),
                FlatButton.Style.NORMAL, this::copy));
        this.pasteButton = this.body(new FlatButton(bx + half + 4, by, bw - half - 4, Metrics.CONTROL_HEIGHT,
                Component.translatable(KEY + "paste"), FlatButton.Style.NORMAL, this::paste));

        int customTop = this.customTop();
        EditBox box = new TextField(this.font, this.paletteX, customTop + Metrics.SECTION_HEIGHT, PALETTE_WIDTH - CHIP_H - 4 - 2,
                Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "custom"));
        box.setMaxLength(7);
        box.setHint(Component.literal("#RRGGBB"));
        box.setResponder(this::onHex);
        this.hex = this.body(box);
        this.resetSlotButton = this.body(new FlatButton(this.paletteX, customTop + Metrics.SECTION_HEIGHT + Metrics.CONTROL_HEIGHT + 4,
                PALETTE_WIDTH, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "reset_slot"), FlatButton.Style.NORMAL, this::resetSelected));

        int presetY = this.presetTop + (PRESET_ROW - Metrics.CONTROL_HEIGHT) / 2;
        Component presetsLabel = Component.translatable(KEY + "presets");
        int px = this.left + PAD + this.font.width(presetsLabel) + 6;
        Component resetAll = Component.translatable(KEY + "reset_all");
        int resetWidth = this.font.width(resetAll) + 14;
        int presetSpace = this.right - PAD - resetWidth - 8 - px;
        int count = PaintPreset.values().length;
        int natural = 0;
        for (PaintPreset preset : PaintPreset.values()) {
            natural += this.font.width(Component.translatable(preset.translationKey())) + 12;
        }
        natural += (count - 1) * 4;
        boolean oneRow = natural <= presetSpace;
        int extra = oneRow ? (presetSpace - natural) / count : 0;
        int rowStart = px;
        int rowEnd = this.right - PAD;
        for (PaintPreset preset : PaintPreset.values()) {
            Component label = Component.translatable(preset.translationKey());
            int presetWidth = Math.min(this.font.width(label) + 12 + extra, rowEnd - rowStart);
            if (!oneRow && px > rowStart && px + presetWidth > rowEnd) {
                px = rowStart;
                presetY += Metrics.CONTROL_HEIGHT + 4;
            }
            this.body(new FlatButton(px, presetY, presetWidth, Metrics.CONTROL_HEIGHT, label, FlatButton.Style.NORMAL,
                    () -> this.applyPreset(preset)));
            px += presetWidth + 4;
        }
        if (!oneRow && px + resetWidth > rowEnd) {
            presetY += Metrics.CONTROL_HEIGHT + 4;
        }
        int layoutBottom = Math.max(this.presetTop + PRESET_ROW, presetY + Metrics.CONTROL_HEIGHT + (PRESET_ROW - Metrics.CONTROL_HEIGHT) / 2) + FOOTER;
        this.body(new FlatButton(this.right - PAD - resetWidth, presetY, resetWidth, Metrics.CONTROL_HEIGHT, resetAll,
                FlatButton.Style.DANGER, () -> {
                    this.setDraft(DronePaint.DEFAULT);
                    this.showStatus(Component.translatable(KEY + "status.reset_all"), Palette.ACCENT);
                }));

        int footerY = this.bottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component applyLabel = Component.translatable(KEY + "apply");
        Component backLabel = Component.translatable(KEY + "back");
        int applyWidth = Math.max(80, this.font.width(applyLabel) + 16);
        int backWidth = Math.max(60, this.font.width(backLabel) + 16);
        this.applyButton = this.addRenderableWidget(new FlatButton(this.right - Metrics.PADDING - applyWidth, footerY, applyWidth,
                Metrics.CONTROL_HEIGHT, applyLabel, FlatButton.Style.PRIMARY, this::apply));
        this.backButton = this.addRenderableWidget(new FlatButton(this.right - Metrics.PADDING - applyWidth - Metrics.GAP - backWidth, footerY, backWidth,
                Metrics.CONTROL_HEIGHT, backLabel, FlatButton.Style.NORMAL, this::onClose));
        ContentScroll scroll = new ContentScroll(new ScreenRectangle(this.left, this.top + HEADER, w, Math.max(0, h - HEADER - FOOTER)),
                this.scroll == null ? 0.0 : this.scroll.offset());
        scroll.adopt(this.bodyWidgets);
        scroll.contentBottom(layoutBottom - FOOTER - ContentScroll.BOTTOM_PADDING);
        this.scroll = scroll;
        this.cachedBuild = null;
        this.refreshBuild();
        this.syncHex();
    }

    private <T extends AbstractWidget> T body(T widget) {
        this.addWidget(widget);
        this.bodyWidgets.add(widget);
        return widget;
    }

    private int shift() {
        return this.scroll == null ? 0 : this.scroll.offset();
    }

    @Override
    public @Nullable ScreenRectangle widgetView(AbstractWidget widget) {
        return this.scroll != null && this.bodyWidgets.contains(widget) ? this.scroll.view() : null;
    }

    private boolean inBody(double mx, double my) {
        return this.scroll == null || this.scroll.view().containsPoint((int) mx, (int) my);
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double x, double y) {
        boolean inside = this.inBody(x, y);
        for (GuiEventListener child : this.children()) {
            if (child.isMouseOver(x, y) && (inside || !(child instanceof AbstractWidget widget) || !this.bodyWidgets.contains(widget))) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return this.scroll != null && this.inBody(mouseX, mouseY) && this.scroll.wheel(scrollY);
    }

    private int dyesTop() {
        return this.bodyTop + Metrics.SECTION_HEIGHT;
    }

    private int finishesTop() {
        return this.dyesTop() + 4 * CHIP_H + 3 * CHIP_GAP + 4;
    }

    private int customTop() {
        return this.finishesTop() + Metrics.SECTION_HEIGHT + CHIP_H + 4;
    }

    private void syncHex() {
        if (this.hex == null) {
            return;
        }
        this.updatingHex = true;
        try {
            OptionalInt color = this.draft.color(this.selected);
            this.hex.setValue(color.isPresent() ? DronePaint.hex(color.getAsInt()) : "");
        } finally {
            this.updatingHex = false;
        }
    }

    private void onHex(String value) {
        if (this.updatingHex) {
            return;
        }
        OptionalInt parsed = DronePaint.parseHex(value);
        if (parsed.isPresent() && value.strip().replace("#", "").length() == 6) {
            this.draft = this.draft.with(this.selected, parsed.getAsInt());
        }
    }

    @Override
    public void tick() {
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.containerMenu != this.menu) {
            this.minecraft.gui.setScreen(null);
            return;
        }
        if (this.menu.frame().isEmpty()) {
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = this.hex != null && this.hex.isFocused();
        if (!typing && event.isCopy()) {
            this.copy();
            return true;
        }
        if (!typing && event.isPaste()) {
            this.paste();
            return true;
        }
        if (!typing && (event.isUp() || event.isDown())) {
            this.step(event.isDown() ? 1 : -1);
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (this.scroll == null) {
            return false;
        }
        return switch (event.key()) {
            case InputConstants.KEY_PAGEDOWN -> this.scroll.page(1);
            case InputConstants.KEY_PAGEUP -> this.scroll.page(-1);
            case InputConstants.KEY_END -> !typing && this.scroll.edge(true);
            case InputConstants.KEY_HOME -> !typing && this.scroll.edge(false);
            default -> false;
        };
    }

    private void step(int delta) {
        List<PaintSlot> slots = new ArrayList<>(this.available);
        if (slots.isEmpty()) {
            return;
        }
        int index = Math.max(0, slots.indexOf(this.selected));
        this.select(slots.get(Math.floorMod(index + delta, slots.size())));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.scroll != null && this.scroll.mouseClicked(event.x(), event.y())) {
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !this.inBody(event.x(), event.y())) {
            return false;
        }
        double mx = event.x();
        double my = event.y() + this.shift();
        PaintSlot row = this.rowAt(mx, my);
        if (row != null) {
            if (this.available.contains(row)) {
                this.select(row);
                this.playClick();
            }
            return true;
        }
        PaintSwatch swatch = this.swatchAt(mx, my);
        if (swatch != null) {
            if (this.available.contains(this.selected)) {
                this.paintSelected(swatch.rgb());
                this.playClick();
            }
            return true;
        }
        if (this.inPreview(mx, my)) {
            this.pressedPreview = true;
            this.dragged = false;
            this.pressX = mx;
            this.pressY = my;
            this.lastDragMillis = Util.getMillis();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scroll != null && this.scroll.mouseDragged(event.y())) {
            return true;
        }
        if (this.pressedPreview) {
            if (Math.abs(event.x() - this.pressX) + Math.abs(event.y() + this.shift() - this.pressY) > 3.0) {
                this.dragged = true;
            }
            if (this.dragged) {
                this.yaw = wrap(this.yaw + (float) dx * 2.5F);
            }
            this.lastDragMillis = Util.getMillis();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scroll != null) {
            this.scroll.mouseReleased();
        }
        if (this.pressedPreview) {
            this.pressedPreview = false;
            this.lastDragMillis = Util.getMillis();
            if (!this.dragged) {
                this.pickAt(event.x(), event.y() + this.shift());
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    public Optional<PaintSlot> pickAt(double mx, double my) {
        PaintPicker.View view = this.view();
        if (view == null) {
            return Optional.empty();
        }
        Optional<PaintSlot> picked = PaintPicker.pick(view, mx, my);
        picked.filter(this.available::contains).ifPresent(slot -> {
            this.select(slot);
            this.playClick();
        });
        return picked;
    }

    private void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
        }
    }

    private PaintPicker.@Nullable View view() {
        return PaintPicker.view(this.previewBuild(), PartWear.PRISTINE, this.draft, this.previewX0 + 2, this.previewY0 + 2, this.previewX1 - 2,
                this.previewY1 - 2, this.yaw);
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0 ? wrapped + 360.0F : wrapped;
    }

    private boolean inPreview(double mx, double my) {
        return mx >= this.previewX0 && mx < this.previewX1 && my >= this.previewY0 && my < this.previewY1;
    }

    private int rowTop(int index) {
        return this.bodyTop + Metrics.SECTION_HEIGHT + index * ROW;
    }

    public int[] rowBounds(PaintSlot slot) {
        int y = this.rowTop(slot.ordinal());
        return new int[]{this.listX, y, this.listX + LIST_WIDTH, y + ROW};
    }

    private @Nullable PaintSlot rowAt(double mx, double my) {
        if (mx < this.listX || mx >= this.listX + LIST_WIDTH) {
            return null;
        }
        for (PaintSlot slot : PaintSlot.values()) {
            int y = this.rowTop(slot.ordinal());
            if (my >= y && my < y + ROW) {
                return slot;
            }
        }
        return null;
    }

    public int[] swatchBounds(PaintSwatch swatch) {
        if (swatch.finish()) {
            int index = PaintSwatch.finishes().indexOf(swatch);
            int x = this.paletteX + index * (FINISH_W + (PALETTE_WIDTH - 5 * FINISH_W) / 4);
            int y = this.finishesTop() + Metrics.SECTION_HEIGHT;
            return new int[]{x, y, x + FINISH_W, y + CHIP_H};
        }
        int index = PaintSwatch.dyes().indexOf(swatch);
        int x = this.paletteX + (index % 4) * (CHIP_W + CHIP_GAP);
        int y = this.dyesTop() + (index / 4) * (CHIP_H + CHIP_GAP);
        return new int[]{x, y, x + CHIP_W, y + CHIP_H};
    }

    private @Nullable PaintSwatch swatchAt(double mx, double my) {
        for (PaintSwatch swatch : PaintSwatch.values()) {
            int[] b = this.swatchBounds(swatch);
            if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                return swatch;
            }
        }
        return null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        if (this.minecraft != null) {
            this.minecraft.gui.hud.extractDeferredSubtitles();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        this.refreshBuild();
        this.advanceYaw();
        boolean slotAvailable = this.available.contains(this.selected);
        if (this.applyButton != null) {
            this.applyButton.active = this.canApply();
        }
        if (this.propsButton != null) {
            this.propsButton.active = this.selected.isProp() || this.draft.has(this.selected);
        }
        if (this.resetSlotButton != null) {
            this.resetSlotButton.active = slotAvailable && this.draft.has(this.selected);
        }
        if (this.hex != null) {
            this.hex.active = slotAvailable;
        }

        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        int titleEnd = ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, HEADER, this.title, this.right - 8);
        ThemeDraw.headerSubtitle(g, this.font, titleEnd, this.top, HEADER, this.subtitle(), this.right - 8);

        ContentScroll scroll = this.scroll;
        int shift = this.shift();
        boolean inside = this.inBody(mouseX, mouseY);
        int bodyX = inside ? mouseX : -1;
        int bodyY = inside ? mouseY + shift : -1;
        ScreenRectangle view = scroll == null ? new ScreenRectangle(this.left, this.top, this.right - this.left, this.bottom - this.top) : scroll.view();
        g.enableScissor(view.left(), view.top(), view.right(), view.bottom());
        if (scroll != null) {
            scroll.rest();
        }
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -shift);
        this.drawList(g, bodyX, bodyY);
        this.drawPreview(g);
        this.drawPalette(g, bodyX, bodyY);
        int presetTop = this.presetTop;
        ThemeDraw.separator(g, this.left + PAD, this.right - PAD, presetTop - 2);
        ThemeText.text(g, this.font, Component.translatable(KEY + "presets"), this.left + PAD, Metrics.textY(presetTop, PRESET_ROW), Palette.TEXT_MUTED);
        g.pose().popMatrix();
        if (scroll != null) {
            scroll.adopt(this.bodyWidgets);
        }
        for (AbstractWidget widget : this.bodyWidgets) {
            widget.extractRenderState(g, mouseX, mouseY, a);
        }
        if (scroll != null) {
            scroll.extractScrollbar(g);
        }
        g.disableScissor();

        int footerTop = this.bottom - FOOTER;
        ThemeDraw.footerBar(g, this.left, footerTop, this.right, this.bottom);
        this.drawFooter(g, footerTop);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
        g.nextStratum();
        g.enableScissor(view.left(), view.top(), view.right(), view.bottom());
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -shift);
        if (scroll != null) {
            scroll.rest();
        }
        this.drawMarkers(g);
        List<Component> tooltip = this.tooltip(bodyX, bodyY);
        if (scroll != null) {
            scroll.adopt(this.bodyWidgets);
        }
        g.pose().popMatrix();
        g.disableScissor();
        ThemeText.tooltipLines(g, this.font, tooltip, mouseX, mouseY);
    }

    private Component subtitle() {
        DroneBuild build = this.previewBuild();
        MutableComponent frame = Component.translatable(build.frame().translationKey()).copy();
        if (this.menu.check().build().isEmpty()) {
            return Component.translatable(KEY + "subtitle_frame_only", frame);
        }
        return frame;
    }

    private void advanceYaw() {
        long now = Util.getMillis();
        long delta = Math.max(0L, Math.min(100L, now - this.lastFrameMillis));
        this.lastFrameMillis = now;
        if (!this.pressedPreview && now - this.lastDragMillis > 4000L) {
            this.yaw = wrap(this.yaw + delta * 0.02F);
        }
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ThemeDraw.sectionTitle(g, this.font, Component.translatable(KEY + "parts"), this.listX, this.bodyTop, LIST_WIDTH);
        DronePaint saved = this.menu.paint();
        for (PaintSlot slot : PaintSlot.values()) {
            int y = this.rowTop(slot.ordinal());
            boolean enabled = this.available.contains(slot);
            boolean hovered = enabled && mouseX >= this.listX && mouseX < this.listX + LIST_WIDTH && mouseY >= y && mouseY < y + ROW;
            ThemeDraw.selection(g, this.listX, y, this.listX + LIST_WIDTH, y + ROW, slot == this.selected, hovered);
            int sx = this.listX + 5;
            int sy = y + 2;
            if (enabled) {
                PaintSwatches.chip(g, sx, sy, sx + 9, sy + 9, this.shownColor(slot), false, false, false);
            } else {
                g.fill(sx, sy, sx + 9, sy + 9, Palette.BORDER);
            }
            int textColor = !enabled ? Palette.TEXT_DISABLED : slot == this.selected ? Palette.TEXT : Palette.TEXT_MUTED;
            Component name = Component.translatable(KEY + "slot." + slot.key());
            String state = !enabled ? Component.translatable(KEY + "absent").getString()
                    : this.draft.has(slot) ? "" : Component.translatable(KEY + "standard").getString();
            int stateWidth = state.isEmpty() ? 0 : this.font.width(state) + 4;
            ThemeText.fitted(g, this.font, name, sx + 13, y + 3, LIST_WIDTH - 22 - stateWidth, textColor);
            if (!state.isEmpty()) {
                ThemeText.textRight(g, this.font, state, this.listX + LIST_WIDTH - 4, y + 3, Palette.TEXT_FAINT);
            }
            if (enabled && !this.draft.color(slot).equals(saved.color(slot))) {
                g.fill(this.listX + 1, y + 5, this.listX + 3, y + ROW - 5, Palette.ACCENT);
            }
        }
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        ThemeDraw.previewStage(g, this.previewX0, this.previewY0, this.previewX1, this.previewY1);
        DronePreview.render(g, this.previewBuild(), PartWear.PRISTINE, this.draft, this.previewX0 + 2, this.previewY0 + 2, this.previewX1 - 2,
                this.previewY1 - 2, this.yaw);
        String hint = Component.translatable(KEY + "preview_hint").getString();
        ThemeText.smallWrapped(g, this.font, hint, this.previewX0 + 4, this.previewY1 - 1, this.previewX1 - this.previewX0 - 8, Palette.TEXT_FAINT);
    }

    private void drawMarkers(GuiGraphicsExtractor g) {
        PaintPicker.View view = this.view();
        if (view == null || !this.available.contains(this.selected)) {
            return;
        }
        long phase = (Util.getMillis() / 400L) % 2L;
        int color = phase == 0 ? Palette.ACCENT : Palette.ACCENT_HOVER;
        g.enableScissor(this.previewX0 + 1, this.previewY0 + 1, this.previewX1 - 1, this.previewY1 - 1);
        for (Vector3fc anchor : PaintPicker.anchors(view, this.selected)) {
            Vector2f p = PaintPicker.project(view, anchor);
            int x = Math.round(p.x);
            int y = Math.round(p.y);
            g.fill(x - 1, y - 4, x + 1, y - 2, color);
            g.fill(x - 1, y + 2, x + 1, y + 4, color);
            g.fill(x - 4, y - 1, x - 2, y + 1, color);
            g.fill(x + 2, y - 1, x + 4, y + 1, color);
        }
        g.disableScissor();
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ThemeDraw.sectionTitle(g, this.font, Component.translatable(KEY + "dyes"), this.paletteX, this.bodyTop, PALETTE_WIDTH);
        ThemeDraw.sectionTitle(g, this.font, Component.translatable(KEY + "finishes"), this.paletteX, this.finishesTop(), PALETTE_WIDTH);
        ThemeDraw.sectionTitle(g, this.font, Component.translatable(KEY + "custom"), this.paletteX, this.customTop(), PALETTE_WIDTH);
        boolean enabled = this.available.contains(this.selected);
        OptionalInt current = this.draft.color(this.selected);
        for (PaintSwatch swatch : PaintSwatch.values()) {
            int[] b = this.swatchBounds(swatch);
            boolean hovered = enabled && mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
            boolean chosen = current.isPresent() && current.getAsInt() == swatch.rgb();
            PaintSwatches.chip(g, b[0], b[1], b[2], b[3], swatch.rgb(), swatch.finish(), chosen, hovered);
        }
        if (!enabled) {
            g.fill(this.paletteX - 1, this.dyesTop() - 1, this.paletteX + PALETTE_WIDTH + 1, this.finishesTop() + Metrics.SECTION_HEIGHT + CHIP_H + 1,
                    Palette.withAlpha(Palette.BACKGROUND, 0xB0));
        }
        int customY = this.customTop() + Metrics.SECTION_HEIGHT;
        int cx = this.paletteX + PALETTE_WIDTH - CHIP_H - 4;
        PaintSwatches.chip(g, cx, customY, cx + CHIP_H + 4, customY + Metrics.CONTROL_HEIGHT, this.shownColor(this.selected), false, false, false);
        int infoY = customY + 2 * Metrics.CONTROL_HEIGHT + 8;
        Component info = enabled ? this.slotInfo() : Component.translatable(KEY + "absent_hint");
        List<FormattedCharSequence> lines = ThemeText.balanced(this.font, info, PALETTE_WIDTH);
        int room = Math.max(0, (this.bodyBottom - infoY) / Metrics.LINE_HEIGHT);
        int shown = ThemeText.shownLines(info, lines.size(), room);
        for (int i = 0; i < shown; i++) {
            ThemeText.text(g, this.font, lines.get(i), this.paletteX, infoY + i * Metrics.LINE_HEIGHT, Palette.TEXT_FAINT);
        }
    }

    private Component slotInfo() {
        OptionalInt color = this.draft.color(this.selected);
        if (color.isEmpty()) {
            return Component.translatable(KEY + "info.standard");
        }
        Optional<PaintSwatch> swatch = PaintSwatch.byColor(color.getAsInt());
        if (swatch.isPresent()) {
            return Component.translatable(KEY + "info.swatch", Component.translatable(swatch.get().translationKey()));
        }
        return Component.translatable(KEY + "info.custom", DronePaint.hex(color.getAsInt()));
    }

    private void drawFooter(GuiGraphicsExtractor g, int footerTop) {
        int y = Metrics.textY(footerTop, FOOTER);
        int x = this.left + 8;
        int right = this.backButton != null ? this.backButton.getX() - 6 : this.right - 8;
        boolean status = Util.getMillis() < this.statusUntil;
        Component text = status ? this.status : this.costLine();
        int color = status ? this.statusColor : this.costColor();
        List<FormattedCharSequence> lines = ThemeText.balanced(this.font, text, Math.max(20, right - x));
        if (lines.size() == 2 && 2 * Metrics.LINE_HEIGHT <= FOOTER - 2) {
            int top = footerTop + (FOOTER - 2 * Metrics.LINE_HEIGHT) / 2 + 1;
            ThemeText.text(g, this.font, lines.get(0), x, top, color);
            ThemeText.text(g, this.font, lines.get(1), x, top + Metrics.LINE_HEIGHT, color);
            return;
        }
        ThemeText.fitted(g, this.font, text, x, y, right - x, color);
    }

    public Component costLine() {
        PaintCost cost = this.cost();
        if (!this.changed()) {
            return Component.translatable(KEY + "cost.none");
        }
        if (this.minecraft != null && this.minecraft.player != null && PaintService.free(this.minecraft.player)) {
            return Component.translatable(KEY + "cost.creative");
        }
        if (cost.free()) {
            return Component.translatable(KEY + "cost.free");
        }
        MutableComponent parts = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : cost.dyes().entrySet()) {
            if (!first) {
                parts.append(", ");
            }
            first = false;
            parts.append(Component.translatable(KEY + "cost.count", entry.getValue(), Component.translatable("item.minecraft." + entry.getKey() + "_dye")));
        }
        if (!cost.custom().isEmpty()) {
            if (!first) {
                parts.append(", ");
            }
            parts.append(Component.translatable(KEY + "cost.any", cost.custom().size()));
        }
        return Component.translatable(this.affordable() ? KEY + "cost.line" : KEY + "cost.missing", parts);
    }

    private int costColor() {
        if (!this.changed()) {
            return Palette.TEXT_FAINT;
        }
        return this.affordable() ? Palette.TEXT_MUTED : Palette.ERROR;
    }

    private List<Component> tooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        PaintSwatch swatch = this.swatchAt(mouseX, mouseY);
        if (swatch != null) {
            lines.add(Component.translatable(swatch.translationKey()).withColor(Palette.TEXT & 0xFFFFFF));
            lines.add(Component.literal(DronePaint.hex(swatch.rgb())).withColor(Palette.TEXT_FAINT & 0xFFFFFF));
            lines.add(Component.translatable(KEY + "tooltip.cost", Component.translatable("item.minecraft." + swatch.dye() + "_dye"))
                    .withColor(Palette.TEXT_MUTED & 0xFFFFFF));
            return lines;
        }
        PaintSlot row = this.rowAt(mouseX, mouseY);
        if (row != null) {
            lines.add(Component.translatable(KEY + "slot." + row.key()).withColor(Palette.TEXT & 0xFFFFFF));
            lines.add(Component.translatable(KEY + "slot_hint." + row.key()).withColor(Palette.TEXT_MUTED & 0xFFFFFF));
            if (!this.available.contains(row)) {
                lines.add(Component.translatable(KEY + "absent_hint").withColor(Palette.WARNING & 0xFFFFFF));
            }
            return lines;
        }
        if (this.hex != null && this.hex.isMouseOver(mouseX, mouseY)) {
            lines.add(Component.translatable(KEY + "tooltip.custom").withColor(Palette.TEXT_MUTED & 0xFFFFFF));
            return lines;
        }
        if (this.inPreview(mouseX, mouseY) && !this.pressedPreview) {
            lines.add(Component.translatable(KEY + "tooltip.preview").withColor(Palette.TEXT_MUTED & 0xFFFFFF));
        }
        return lines;
    }
}
