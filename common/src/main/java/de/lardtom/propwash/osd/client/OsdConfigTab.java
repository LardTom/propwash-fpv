package de.lardtom.propwash.osd.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.osd.OsdColors;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdFormat;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdPalette;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.LabeledValue;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.TextField;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.client.FpvImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class OsdConfigTab extends ConfigTab {
    private final List<AbstractWidget> ownWidgets = new ArrayList<>();
    private final int[] rowShift = new int[CONTROL_ROWS + 1];
    private final Map<AbstractWidget, Integer> rowLines = new IdentityHashMap<>();
    public static final String ID = "osd";
    static final int LIST_ROW = 12;
    static final int GAP = Metrics.GAP;
    static final int ROW_HEIGHT = Metrics.CONTROL_HEIGHT;
    static final int ROW_GAP = Metrics.ROW_HEIGHT - Metrics.CONTROL_HEIGHT;
    static final int ROW_STEP = ROW_HEIGHT + ROW_GAP;
    static final int CONTROL_ROWS = 4;
    static final int ELEMENT_ROWS = 2;
    static final int LEGEND_HEIGHT = 12;
    private static final String LEGEND_ICON = "⌖";
    static final int MORE_HEIGHT = 12;
    static final int STYLE_PAGE_WIDGETS = 17;
    static final int OPTIONS_PAGE_WIDGETS = 12;
    static final int OPACITY_STEP = 5;
    static final int LADDER_STEP = 5;
    private static final double SCALE_PERCENT_STEP = 100.0 / OsdConfig.STEPS_PER_UNIT;
    private static final int HEX_WIDTH = 56;
    private static final int HEX_GAP = 2;
    private static final int HEX_MIN_BUTTON = 64;
    private static final int CHECK_SIZE = 8;
    private static final int LIST_MARKER_SPACE = 20;
    private static final int LABEL_MARGIN = 3;
    private static final int SELECT_COLOR = Palette.ACCENT;
    private static final int HOVER_COLOR = Palette.HOVER_OUTLINE;
    private static final int GRID_COLOR = Palette.OVERLAY_GRID;
    private static final int HEX_VALID = Palette.TEXT;
    private static final int HEX_INVALID = Palette.ERROR;
    private static final int HINT_COLOR = Palette.TEXT_MUTED;
    private static final List<String> ELEMENT_COLORS = elementColors();
    private static Page lastPage = Page.STYLE;

    private final OsdRenderer renderer = new OsdRenderer();
    private final OsdData sample = OsdData.sample();
    private OsdConfig working;
    private Page page = lastPage;
    private @Nullable OsdPreset pendingPreset;
    private @Nullable OsdElement selected;
    private @Nullable OsdElement dragging;
    private int dragStartCol;
    private int dragStartRow;
    private float grabDx;
    private float grabDy;
    private int listScroll;
    private boolean draggingListBar;
    private boolean syncing;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int imageX;
    private int imageY;
    private int imageW;
    private int imageH;
    private ImageFormat imageFormat = ImageFormat.WIDE;
    private int controlsX;
    private int controlsY;
    private int controlsW;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private @Nullable OsdGrid previewGrid;
    private @Nullable CycleButton<OsdPreset> presetButton;
    private @Nullable Button resetButton;
    private @Nullable Button pageButton;
    private @Nullable Button confirmButton;
    private @Nullable Button cancelButton;
    private @Nullable OsdSlider opacitySlider;
    private @Nullable OsdSlider scaleSlider;
    private @Nullable OsdSlider lineSlider;
    private @Nullable CycleButton<String> colorButton;
    private @Nullable EditBox colorBox;
    private @Nullable CycleButton<String> warningButton;
    private @Nullable EditBox warningBox;
    private @Nullable CycleButton<OsdConfig.Outline> outlineButton;
    private @Nullable CycleButton<OsdConfig.Background> backgroundButton;
    private @Nullable CycleButton<Boolean> alertsButton;
    private @Nullable CycleButton<Boolean> vanillaButton;
    private @Nullable CycleButton<Boolean> breakupButton;
    private @Nullable CycleButton<Boolean> ladderButton;
    private @Nullable OsdSlider ladderStepSlider;
    private @Nullable Button styleResetButton;
    private @Nullable CycleButton<String> elementColorButton;
    private @Nullable OsdSlider elementScaleSlider;

    public OsdConfigTab() {
        this.working = OsdClient.CONFIG.get().copy();
    }

    public enum Page {
        STYLE,
        OPTIONS;

        Page other() {
            return this == STYLE ? OPTIONS : STYLE;
        }

        String translationKey() {
            return "propwash.osd.editor.page." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.osd.tab.title");
    }

    @Override
    public int order() {
        return 510;
    }

    public OsdConfig working() {
        return this.working;
    }

    public @Nullable OsdElement selected() {
        return this.selected;
    }

    public @Nullable OsdGrid previewGrid() {
        return this.previewGrid;
    }

    public OsdRenderer renderer() {
        return this.renderer;
    }

    public Page page() {
        return this.page;
    }

    public @Nullable OsdPreset pendingPreset() {
        return this.pendingPreset;
    }

    @Nullable CycleButton<OsdPreset> presetButton() {
        return this.presetButton;
    }

    @Nullable Button resetButton() {
        return this.resetButton;
    }

    @Nullable Button confirmButton() {
        return this.confirmButton;
    }

    @Nullable Button pageButton() {
        return this.pageButton;
    }

    @Nullable EditBox colorBox() {
        return this.colorBox;
    }

    @Nullable EditBox warningBox() {
        return this.warningBox;
    }

    @Nullable OsdSlider opacitySlider() {
        return this.opacitySlider;
    }

    @Nullable OsdSlider scaleSlider() {
        return this.scaleSlider;
    }

    @Nullable CycleButton<String> elementColorButton() {
        return this.elementColorButton;
    }

    @Override
    protected void build() {
        this.forgetWidgets();
        Arrays.fill(this.rowShift, 0);
        int widestName = 0;
        for (OsdElement element : OsdElement.values()) {
            widestName = Math.max(widestName, this.host.font().width(Component.translatable(element.translationKey())));
        }
        int classicList = Math.clamp(Math.round(this.width * 0.32F), 112, 180);
        int neededList = 4 + CHECK_SIZE + 5 + widestName + LIST_MARKER_SPACE;
        this.listW = Math.max(classicList, Math.min(neededList, Math.max(112, Math.round(this.width * 0.45F))));
        int squeezedList = 4 + CHECK_SIZE + 5 + (int) Math.ceil(widestName * TextFit.MIN_SQUEEZE) + 1 + LIST_MARKER_SPACE;
        this.listW = Math.max(this.listW, Math.min(squeezedList, this.width - 80 - 2 * GAP));
        this.listX = this.x + this.width - this.listW;
        this.listY = this.y;
        int listSpace = Math.max(LIST_ROW * 3 + 4, this.height - ELEMENT_ROWS * ROW_STEP - this.legendHeight());
        if (this.listContentHeight() > listSpace) {
            listSpace = Math.max(LIST_ROW * 3 + 4, listSpace - MORE_HEIGHT);
        }
        this.listH = (listSpace - 4) / LIST_ROW * LIST_ROW + 4;
        int availableW = Math.max(60, this.width - this.listW - GAP * 2);
        int availableH = Math.max(30, this.height - CONTROL_ROWS * ROW_STEP - GAP);
        float aspect = this.screenAspect();
        this.previewW = Math.min(availableW, Math.round(availableH * aspect));
        this.previewH = Math.max(20, Math.round(this.previewW / aspect));
        this.previewX = this.x + (availableW - this.previewW) / 2;
        this.previewY = this.y;
        this.layoutImage(FpvImage.editorFormat());
        this.controlsX = this.x;
        this.controlsW = availableW;
        this.controlsY = this.previewY + this.previewH + GAP;
        this.clampScroll();
        this.buildPresetRow();
        if (this.page == Page.STYLE) {
            this.buildStylePage();
        } else {
            this.buildOptionsPage();
        }
        this.buildElementControls();
        this.refreshWidgets();
        this.reflowControls();
    }

    private void reflowControls() {
        Font font = this.host.font();
        List<List<AbstractWidget>> rows = new ArrayList<>();
        for (int row = 0; row < CONTROL_ROWS; row++) {
            int baseY = this.controlsY + row * ROW_STEP;
            List<AbstractWidget> cells = new ArrayList<>();
            for (AbstractWidget widget : this.ownWidgets) {
                if (widget.getY() == baseY && widget.getX() >= this.controlsX && widget.getX() < this.controlsX + this.controlsW) {
                    cells.add(widget);
                }
            }
            cells.sort(Comparator.comparingInt(AbstractWidget::getX));
            rows.add(cells);
        }
        int shift = 0;
        for (int row = 0; row < CONTROL_ROWS; row++) {
            int baseY = this.controlsY + row * ROW_STEP;
            List<AbstractWidget> cells = rows.get(row);
            this.rowShift[row] = shift;
            if (cells.stream().anyMatch(this.rowLines::containsKey)) {
                int lines = 0;
                for (AbstractWidget cell : cells) {
                    int line = this.rowLines.getOrDefault(cell, 0);
                    cell.setY(baseY + shift + line * ROW_STEP);
                    lines = Math.max(lines, line);
                }
                shift += lines * ROW_STEP;
                continue;
            }
            boolean cramped = false;
            for (AbstractWidget cell : cells) {
                cramped |= neededWidth(font, cell) > cell.getWidth();
            }
            if (!cramped || cells.size() < 2) {
                for (AbstractWidget cell : cells) {
                    cell.setY(baseY + shift);
                }
                continue;
            }
            int line = -1;
            int lastX = Integer.MIN_VALUE;
            for (AbstractWidget cell : cells) {
                if (cell.getX() != lastX) {
                    line++;
                    lastX = cell.getX();
                }
                cell.setRectangle(this.controlsW, cell.getHeight(), this.controlsX, baseY + shift + line * ROW_STEP);
            }
            shift += Math.max(0, line) * ROW_STEP;
        }
        this.rowShift[CONTROL_ROWS] = shift;
    }

    static int neededWidth(Font font, AbstractWidget widget) {
        if (widget instanceof EditBox) {
            return widget.getWidth();
        }
        if (widget instanceof LabeledValue labeled) {
            return font.width(labeled.themeLabel()) + Metrics.LABEL_GAP + 2 + Math.max(Metrics.CONTROL_MIN, font.width(labeled.themeValue()) + 10);
        }
        Component message = widget.getMessage();
        ThemeText.Split split = ThemeText.splitLabel(message);
        int arrows = widget instanceof CycleButton ? 2 * Metrics.ARROW_WIDTH + 8 : 8;
        if (split == null) {
            return font.width(message) + arrows;
        }
        return font.width(split.label()) + Metrics.LABEL_GAP + 2 + Math.max(Metrics.CONTROL_MIN, font.width(split.value()) + arrows);
    }

    private <T extends AbstractWidget> T own(T widget) {
        this.ownWidgets.add(widget);
        return this.host.addWidget(widget);
    }

    private void forgetWidgets() {
        this.ownWidgets.clear();
        this.rowLines.clear();
        this.presetButton = null;
        this.resetButton = null;
        this.pageButton = null;
        this.confirmButton = null;
        this.cancelButton = null;
        this.opacitySlider = null;
        this.scaleSlider = null;
        this.lineSlider = null;
        this.colorButton = null;
        this.colorBox = null;
        this.warningButton = null;
        this.warningBox = null;
        this.outlineButton = null;
        this.backgroundButton = null;
        this.alertsButton = null;
        this.vanillaButton = null;
        this.breakupButton = null;
        this.ladderButton = null;
        this.ladderStepSlider = null;
        this.styleResetButton = null;
        this.elementColorButton = null;
        this.elementScaleSlider = null;
    }

    private int colX(int index, int count) {
        int w = (this.controlsW - GAP * (count - 1)) / count;
        return this.controlsX + index * (w + GAP);
    }

    private int colW(int index, int count) {
        int w = (this.controlsW - GAP * (count - 1)) / count;
        return index == count - 1 ? this.controlsX + this.controlsW - this.colX(index, count) : w;
    }

    private int rowY(int row) {
        return this.controlsY + row * ROW_STEP + this.rowShift[Math.clamp(row, 0, CONTROL_ROWS)];
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    private void buildPresetRow() {
        int y0 = this.rowY(0);
        Component presetTitle = Component.translatable("propwash.osd.editor.preset");
        this.presetButton = this.own(CycleButton.builder(this.labeled(this.colW(0, 3), presetTitle, this::presetName, this::presetBrief), this.working.preset)
                .withValues(List.of(OsdPreset.values()))
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.translatable(value.translationKey() + ".tooltip")))
                .create(this.colX(0, 3), y0, this.colW(0, 3), ROW_HEIGHT, presetTitle,
                        (button, value) -> this.choosePreset(value)));
        this.resetButton = this.own(Button.builder(this.fit(this.colW(1, 3), "propwash.osd.editor.reset"), b -> this.resetLayout())
                .bounds(this.colX(1, 3), y0, this.colW(1, 3), ROW_HEIGHT)
                .tooltip(tip("propwash.osd.editor.reset.tooltip"))
                .build());
        this.pageButton = this.own(Button.builder(this.pageLabel(), b -> this.switchPage())
                .bounds(this.colX(2, 3), y0, this.colW(2, 3), ROW_HEIGHT)
                .tooltip(tip("propwash.osd.editor.page.tooltip"))
                .build());
        this.confirmButton = this.own(Button.builder(Component.translatable("propwash.osd.editor.preset.confirm.short"), b -> this.confirmPreset())
                .bounds(this.colX(1, 3), y0, this.colW(1, 3), ROW_HEIGHT)
                .tooltip(tip("propwash.osd.editor.preset.confirm.tooltip"))
                .build());
        this.cancelButton = this.own(Button.builder(this.fit(this.colW(2, 3), "propwash.osd.editor.preset.cancel"), b -> this.cancelPreset())
                .bounds(this.colX(2, 3), y0, this.colW(2, 3), ROW_HEIGHT)
                .build());
    }

    private void buildStylePage() {
        int y1 = this.rowY(1);
        this.opacitySlider = this.own(new OsdSlider(this.colX(0, 3), y1, this.colW(0, 3), ROW_HEIGHT, 100 / OPACITY_STEP,
                () -> Math.round(this.working.opacity / (float) OPACITY_STEP),
                index -> {
                    this.working.opacity = index * OPACITY_STEP;
                    this.changed();
                },
                index -> this.fit(this.colW(0, 3), "propwash.osd.editor.opacity", Integer.toString(index * OPACITY_STEP)),
                Component.translatable("propwash.osd.editor.opacity.name"), index -> percentValue(index * OPACITY_STEP)));
        this.opacitySlider.setTooltip(tip("propwash.osd.editor.opacity.tooltip"));
        this.opacitySlider.display(OPACITY_STEP, 0.0).percentRange();
        this.scaleSlider = this.own(new OsdSlider(this.colX(1, 3), y1, this.colW(1, 3), ROW_HEIGHT, OsdConfig.SCALE_STEPS,
                () -> OsdConfig.scaleStep(this.working.scale),
                index -> {
                    this.working.scale = OsdConfig.scaleAt(index);
                    this.changed();
                },
                index -> this.fit(this.colW(1, 3), "propwash.osd.editor.scale", percent(OsdConfig.scaleAt(index))),
                Component.translatable("propwash.osd.editor.scale.name"), index -> Component.translatable("propwash.osd.editor.percent", percent(OsdConfig.scaleAt(index)))));
        this.scaleSlider.setTooltip(tip("propwash.osd.editor.scale.tooltip"));
        this.scaleSlider.display(SCALE_PERCENT_STEP, OsdConfig.MIN_SCALE * 100.0).percentRange();
        this.lineSlider = this.own(new OsdSlider(this.colX(2, 3), y1, this.colW(2, 3), ROW_HEIGHT, 100 / OPACITY_STEP,
                () -> Math.round(this.working.lineOpacity / (float) OPACITY_STEP),
                index -> {
                    this.working.lineOpacity = index * OPACITY_STEP;
                    this.changed();
                },
                index -> this.fit(this.colW(2, 3), "propwash.osd.editor.lines", Integer.toString(index * OPACITY_STEP)),
                Component.translatable("propwash.osd.editor.lines.name"), index -> percentValue(index * OPACITY_STEP)));
        this.lineSlider.setTooltip(tip("propwash.osd.editor.lines.tooltip"));
        this.lineSlider.display(OPACITY_STEP, 0.0).percentRange();
        OsdSlider.shareLabelColumn(this.opacitySlider, this.scaleSlider, this.lineSlider);

        int y2 = this.rowY(2);
        int pair = HEX_MIN_BUTTON + HEX_GAP + HEX_WIDTH;
        int columns = this.colW(0, 3) >= pair ? 3 : this.colW(0, 2) >= pair ? 2 : 1;
        boolean below = columns == 1 && this.controlsW < pair;
        int colorX = this.colX(0, columns);
        int warningX = columns == 1 ? colorX : this.colX(1, columns);
        int colorW = below ? this.controlsW : this.colW(0, columns) - HEX_WIDTH - HEX_GAP;
        int warningW = below ? this.controlsW : this.colW(columns == 1 ? 0 : 1, columns) - HEX_WIDTH - HEX_GAP;
        int hexW = Math.min(HEX_WIDTH, this.controlsW);
        Component colorTitle = Component.translatable("propwash.osd.editor.color");
        this.colorButton = this.own(CycleButton.builder(this.swatchLabel(colorW, "propwash.osd.editor.color"), this.working.color)
                .withValues(OsdPalette.hexes(OsdPalette.PRIMARY))
                .displayOnlyValue()
                .withTooltip(value -> tip("propwash.osd.editor.color.tooltip"))
                .create(colorX, y2, colorW, ROW_HEIGHT, colorTitle,
                        (button, value) -> this.setPrimary(value)));
        Component warningTitle = Component.translatable("propwash.osd.editor.warning_color");
        this.warningButton = this.own(CycleButton.builder(this.swatchLabel(warningW, "propwash.osd.editor.warning_color"), this.working.warningColor)
                .withValues(OsdPalette.hexes(OsdPalette.WARNING))
                .displayOnlyValue()
                .withTooltip(value -> tip("propwash.osd.editor.warning_color.tooltip"))
                .create(warningX, y2, warningW, ROW_HEIGHT, warningTitle,
                        (button, value) -> this.setWarning(value)));
        this.colorBox = this.hexBox(below ? colorX : colorX + colorW + HEX_GAP, y2, hexW, this.working.color, this::setPrimary,
                "propwash.osd.editor.hex.color");
        this.warningBox = this.hexBox(below ? warningX : warningX + warningW + HEX_GAP, y2, hexW, this.working.warningColor, this::setWarning,
                "propwash.osd.editor.hex.warning");
        this.rowLines.put(this.colorButton, 0);
        this.rowLines.put(this.colorBox, below ? 1 : 0);
        this.rowLines.put(this.warningButton, columns > 1 ? 0 : below ? 2 : 1);
        this.rowLines.put(this.warningBox, columns > 1 ? 0 : below ? 3 : 1);

        int y3 = this.rowY(3);
        Component outlineTitle = Component.translatable("propwash.osd.editor.outline");
        this.outlineButton = this.own(CycleButton.<OsdConfig.Outline>builder(this.labeled(this.colW(0, 3), outlineTitle,
                        value -> Component.translatable(value.translationKey()), value -> Component.translatable(value.translationKey() + ".short")), this.working.outline)
                .withValues(List.of(OsdConfig.Outline.values()))
                .displayOnlyValue()
                .withTooltip(value -> tip("propwash.osd.editor.outline.tooltip"))
                .create(this.colX(0, 3), y3, this.colW(0, 3), ROW_HEIGHT, outlineTitle,
                        (button, value) -> {
                            this.working.outline = value;
                            this.changed();
                        }));
        Component backgroundTitle = Component.translatable("propwash.osd.editor.background");
        this.backgroundButton = this.own(CycleButton.<OsdConfig.Background>builder(this.labeled(this.colW(1, 3), backgroundTitle,
                        value -> Component.translatable(value.translationKey()), value -> Component.translatable(value.translationKey() + ".short")), this.working.background)
                .withValues(List.of(OsdConfig.Background.values()))
                .displayOnlyValue()
                .withTooltip(value -> tip("propwash.osd.editor.background.tooltip"))
                .create(this.colX(1, 3), y3, this.colW(1, 3), ROW_HEIGHT, backgroundTitle,
                        (button, value) -> {
                            this.working.background = value;
                            this.changed();
                        }));
        this.alertsButton = this.own(this.toggle(this.working.coloredAlerts, this.colW(2, 3), "propwash.osd.editor.alerts")
                .withTooltip(value -> tip("propwash.osd.editor.alerts.tooltip"))
                .create(this.colX(2, 3), y3, this.colW(2, 3), ROW_HEIGHT, Component.translatable("propwash.osd.editor.alerts"),
                        (button, value) -> {
                            this.working.coloredAlerts = value;
                            this.changed();
                        }));
    }

    private void buildOptionsPage() {
        int y1 = this.rowY(1);
        this.vanillaButton = this.own(this.toggle(!this.working.hideVanillaHud, this.colW(0, 2), "propwash.osd.editor.vanilla")
                .withTooltip(value -> tip("propwash.osd.editor.vanilla.tooltip"))
                .create(this.colX(0, 2), y1, this.colW(0, 2), ROW_HEIGHT, Component.translatable("propwash.osd.editor.vanilla"),
                        (button, value) -> {
                            this.working.hideVanillaHud = !value;
                            this.changed();
                        }));
        this.breakupButton = this.own(this.toggle(this.working.analogBreakup, this.colW(1, 2), "propwash.osd.editor.breakup")
                .withTooltip(value -> tip("propwash.osd.editor.breakup.tooltip"))
                .create(this.colX(1, 2), y1, this.colW(1, 2), ROW_HEIGHT, Component.translatable("propwash.osd.editor.breakup"),
                        (button, value) -> {
                            this.working.analogBreakup = value;
                            this.changed();
                        }));

        int y2 = this.rowY(2);
        this.ladderButton = this.own(this.toggle(this.working.pitchLadder, this.colW(0, 3), "propwash.osd.editor.ladder")
                .withTooltip(value -> tip("propwash.osd.editor.ladder.tooltip"))
                .create(this.colX(0, 3), y2, this.colW(0, 3), ROW_HEIGHT, Component.translatable("propwash.osd.editor.ladder"),
                        (button, value) -> {
                            this.working.pitchLadder = value;
                            this.changed();
                        }));
        int ladderSteps = Math.round((OsdConfig.MAX_LADDER_STEP - OsdConfig.MIN_LADDER_STEP) / LADDER_STEP);
        this.ladderStepSlider = this.own(new OsdSlider(this.colX(1, 3), y2, this.colW(1, 3), ROW_HEIGHT, ladderSteps,
                () -> Math.round((this.working.ladderStepDeg - OsdConfig.MIN_LADDER_STEP) / LADDER_STEP),
                index -> {
                    this.working.ladderStepDeg = OsdConfig.MIN_LADDER_STEP + index * LADDER_STEP;
                    this.changed();
                },
                index -> this.fit(this.colW(1, 3), "propwash.osd.editor.ladder_step",
                        Integer.toString(Math.round(OsdConfig.MIN_LADDER_STEP + index * LADDER_STEP))),
                Component.translatable("propwash.osd.editor.ladder_step.name"),
                index -> Component.translatable("propwash.osd.editor.ladder_step.short", Integer.toString(Math.round(OsdConfig.MIN_LADDER_STEP + index * LADDER_STEP)))));
        this.ladderStepSlider.setTooltip(tip("propwash.osd.editor.ladder_step.tooltip"));
        this.ladderStepSlider.display(LADDER_STEP, OsdConfig.MIN_LADDER_STEP);
        this.styleResetButton = this.own(Button.builder(this.fit(this.colW(2, 3), "propwash.osd.editor.style_reset"), b -> this.resetStyle())
                .bounds(this.colX(2, 3), y2, this.colW(2, 3), ROW_HEIGHT)
                .tooltip(tip("propwash.osd.editor.style_reset.tooltip"))
                .build());
    }

    private void buildElementControls() {
        int y0 = this.listBottom() + this.legendHeight() + ROW_GAP;
        Component elementColorTitle = Component.translatable("propwash.osd.editor.element_color");
        this.elementColorButton = this.own(CycleButton.builder(this.labeled(this.listW, elementColorTitle, OsdConfigTab::elementColorName,
                        hex -> CommonComponents.optionNameValue(Component.translatable("propwash.osd.editor.element_color.short"), elementColorName(hex))), this.selectedColor())
                .withValues(ELEMENT_COLORS)
                .displayOnlyValue()
                .withTooltip(value -> tip("propwash.osd.editor.element_color.tooltip"))
                .create(this.listX, y0, this.listW, ROW_HEIGHT, elementColorTitle,
                        (button, value) -> this.setElementColor(value)));
        this.elementScaleSlider = this.own(new OsdSlider(this.listX, y0 + ROW_STEP, this.listW, ROW_HEIGHT, OsdConfig.SCALE_STEPS + 1,
                this::selectedScaleIndex, this::setElementScale, index -> this.fit(this.listW, elementScaleLabel(index), elementScaleBrief(index)),
                Component.translatable("propwash.osd.editor.element_scale.name"), OsdConfigTab::elementScaleValue));
        this.elementScaleSlider.setTooltip(tip("propwash.osd.editor.element_scale.tooltip"));
        this.elementScaleSlider.display(SCALE_PERCENT_STEP, OsdConfig.MIN_SCALE * 100.0 - SCALE_PERCENT_STEP)
                .typedRange(1.0, OsdConfig.SCALE_STEPS + 1.0).percentRange();
    }

    private EditBox hexBox(int boxX, int boxY, int boxW, String value, Consumer<String> apply, String tooltipKey) {
        EditBox box = new TextField(this.host.font(), boxX, boxY, boxW, ROW_HEIGHT, Component.translatable("propwash.osd.editor.hex"));
        box.setMaxLength(7);
        box.setValue(value);
        box.setTextColor(HEX_VALID);
        box.setHint(ThemeText.colored(Component.literal("#RRGGBB"), Palette.TEXT_DISABLED));
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey).append("\n").append(Component.translatable("propwash.osd.editor.hex.tooltip"))));
        box.setResponder(text -> {
            boolean valid = OsdColors.isValid(text);
            box.setTextColor(valid ? HEX_VALID : HEX_INVALID);
            if (valid && !this.syncing) {
                apply.accept(OsdColors.hex(OsdColors.parseRgb(text)));
            }
        });
        return this.own(box);
    }

    private static List<String> elementColors() {
        List<String> values = new ArrayList<>();
        values.add("");
        for (OsdPalette entry : OsdPalette.values()) {
            values.add(entry.hex());
        }
        return List.copyOf(values);
    }

    private static Component percentValue(int percent) {
        return Component.translatable("propwash.osd.editor.percent", Integer.toString(percent));
    }

    private static String percent(float scale) {
        return Integer.toString(Math.round(scale * 100.0F));
    }

    static Component swatchName(String hex) {
        int rgb = OsdColors.rgbOr(hex, 0xFFFFFF);
        OsdPalette entry = OsdPalette.byHex(hex);
        Component name = entry != null ? Component.translatable(entry.translationKey()) : Component.translatable("propwash.osd.color.custom");
        return Component.empty().append(Component.literal("■").withColor(rgb)).append(" ").append(name);
    }

    static Component elementColorName(String hex) {
        return hex.isEmpty() ? Component.translatable("propwash.osd.editor.inherit") : swatchName(hex);
    }

    static Component elementScaleLabel(int index) {
        return Component.translatable("propwash.osd.editor.element_scale", elementScaleValue(index));
    }

    private static Component elementScaleBrief(int index) {
        return Component.translatable("propwash.osd.editor.element_scale.short", elementScaleValue(index));
    }

    private static Component elementScaleValue(int index) {
        return index <= 0 ? Component.translatable("propwash.osd.editor.inherit")
                : Component.translatable("propwash.osd.editor.percent", percent(OsdConfig.scaleAt(index - 1)));
    }

    boolean fits(Component text, int width) {
        return this.host.font().width(text) <= width - 2 * LABEL_MARGIN;
    }

    private Component fit(int width, Component full, Component brief) {
        return this.fits(full, width) ? full : brief;
    }

    private Component fit(int width, String key, Object... args) {
        return this.fit(width, Component.translatable(key, args), Component.translatable(key + ".short", args));
    }

    private <T> Function<T, Component> labeled(int width, Component title, Function<T, Component> value, Function<T, Component> brief) {
        return item -> this.fit(width, CommonComponents.optionNameValue(title, value.apply(item)), brief.apply(item));
    }

    private Function<String, Component> swatchLabel(int width, String key) {
        return this.labeled(width, Component.translatable(key), OsdConfigTab::swatchName, hex -> Component.empty()
                .append(Component.translatable(key + ".short")).append(" ").append(Component.literal("■").withColor(OsdColors.rgbOr(hex, 0xFFFFFF))));
    }

    private CycleButton.Builder<Boolean> toggle(boolean initial, int width, String key) {
        Component title = Component.translatable(key);
        return CycleButton.<Boolean>builder(this.labeled(width, title, ThemedWidgets::onOff,
                        value -> Component.translatable(key + (value ? ".short.on" : ".short.off"))), initial)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .displayOnlyValue();
    }

    private Component presetName(OsdPreset preset) {
        return this.presetMarked(preset, Component.translatable(preset.translationKey()));
    }

    private Component presetBrief(OsdPreset preset) {
        return this.presetMarked(preset, Component.translatable(preset.translationKey() + ".short"));
    }

    private Component presetMarked(OsdPreset preset, MutableComponent name) {
        if (this.pendingPreset == null && preset == this.working.preset && this.working.customized()) {
            return Component.translatable("propwash.osd.editor.preset.customized", name);
        }
        return name;
    }

    private Component pageLabel() {
        String key = this.page.other().translationKey();
        return this.fit(this.colW(2, 3), Component.translatable("propwash.osd.editor.page", Component.translatable(key)),
                Component.translatable("propwash.osd.editor.page", Component.translatable(key + ".short")));
    }

    private void layoutImage(ImageFormat format) {
        ImageArea area = ImageArea.of(format, this.previewW, this.previewH);
        this.imageFormat = format;
        this.imageX = this.previewX + area.x();
        this.imageY = this.previewY + area.y();
        this.imageW = area.width();
        this.imageH = area.height();
        this.previewGrid = new OsdGrid(this.imageX, this.imageY, this.imageW, this.imageH);
    }

    public ImageFormat previewFormat() {
        return this.imageFormat;
    }

    private float screenAspect() {
        try {
            float w = this.host.minecraft().getWindow().getGuiScaledWidth();
            float h = this.host.minecraft().getWindow().getGuiScaledHeight();
            if (w > 0.0F && h > 0.0F) {
                return Math.clamp(w / h, 1.0F, 2.6F);
            }
        } catch (RuntimeException ignored) {
        }
        return 16.0F / 9.0F;
    }

    private String selectedColor() {
        OsdElement element = this.selected;
        return element == null ? "" : this.working.slot(element).color;
    }

    private int selectedScaleIndex() {
        OsdElement element = this.selected;
        if (element == null) {
            return 0;
        }
        OsdConfig.Slot slot = this.working.slot(element);
        return slot.hasScale() ? OsdConfig.scaleStep(slot.scale) + 1 : 0;
    }

    private void setElementColor(String hex) {
        OsdElement element = this.selected;
        if (element == null || this.syncing) {
            return;
        }
        this.working.slot(element).color = hex.isEmpty() ? "" : OsdColors.normalize(hex, "");
        this.changed();
    }

    private void setElementScale(int index) {
        OsdElement element = this.selected;
        if (element == null || this.syncing) {
            return;
        }
        this.working.slot(element).scale = index <= 0 ? 0.0F : OsdConfig.scaleAt(index - 1);
        this.changed();
    }

    private void setPrimary(String hex) {
        if (this.syncing || hex.equals(this.working.color)) {
            return;
        }
        this.working.color = hex;
        this.syncColorWidgets();
        this.changed();
    }

    private void setWarning(String hex) {
        if (this.syncing || hex.equals(this.working.warningColor)) {
            return;
        }
        this.working.warningColor = hex;
        this.syncColorWidgets();
        this.changed();
    }

    private void syncColorWidgets() {
        boolean wasSyncing = this.syncing;
        this.syncing = true;
        try {
            if (this.colorButton != null) {
                this.colorButton.setValue(this.working.color);
            }
            if (this.warningButton != null) {
                this.warningButton.setValue(this.working.warningColor);
            }
            syncBox(this.colorBox, this.working.color);
            syncBox(this.warningBox, this.working.warningColor);
        } finally {
            this.syncing = wasSyncing;
        }
    }

    private static void syncBox(@Nullable EditBox box, String hex) {
        if (box == null) {
            return;
        }
        if (OsdColors.parseRgb(box.getValue()) != OsdColors.parseRgb(hex)) {
            box.setValue(hex);
        }
        box.setTextColor(HEX_VALID);
    }

    public void choosePreset(OsdPreset preset) {
        if (preset == this.working.preset) {
            this.pendingPreset = null;
            this.refreshWidgets();
            return;
        }
        if (this.working.customized()) {
            this.pendingPreset = preset;
            this.refreshWidgets();
            return;
        }
        this.applyPreset(preset);
    }

    public void confirmPreset() {
        OsdPreset preset = this.pendingPreset;
        if (preset == null) {
            return;
        }
        this.pendingPreset = null;
        this.applyPreset(preset);
    }

    public void cancelPreset() {
        this.pendingPreset = null;
        this.refreshWidgets();
    }

    private void applyPreset(OsdPreset preset) {
        this.working.applyPreset(preset);
        this.dragging = null;
        this.edited();
    }

    public void switchPage() {
        this.page = this.page.other();
        lastPage = this.page;
        if (this.host != null) {
            this.host.rebuild();
        }
    }

    private void updateResetButtons() {
        if (this.resetButton != null) {
            this.resetButton.active = this.working.customized() || this.working.hasElementOverrides();
        }
        if (this.styleResetButton != null) {
            this.styleResetButton.active = !this.styleIsDefault();
        }
    }

    private boolean styleIsDefault() {
        OsdConfig fresh = new OsdConfig();
        return this.working.opacity == fresh.opacity && this.working.lineOpacity == fresh.lineOpacity && this.working.scale == fresh.scale
                && fresh.color.equalsIgnoreCase(this.working.color) && fresh.warningColor.equalsIgnoreCase(this.working.warningColor)
                && this.working.outline == fresh.outline && this.working.background == fresh.background;
    }

    private void resetStyle() {
        OsdConfig fresh = new OsdConfig();
        this.working.opacity = fresh.opacity;
        this.working.lineOpacity = fresh.lineOpacity;
        this.working.scale = fresh.scale;
        this.working.color = fresh.color;
        this.working.warningColor = fresh.warningColor;
        this.working.outline = fresh.outline;
        this.working.background = fresh.background;
        this.edited();
    }

    private void edited() {
        this.refreshWidgets();
        this.changed();
    }

    private void changed() {
        if (this.host != null) {
            this.host.changed();
        }
    }

    private static void show(@Nullable AbstractWidget widget, boolean visible) {
        if (widget != null) {
            widget.visible = visible;
        }
    }

    private static void sync(@Nullable OsdSlider slider) {
        if (slider != null) {
            slider.sync();
        }
    }

    private static <T> void sync(@Nullable CycleButton<T> button, T value) {
        if (button != null) {
            button.setValue(value);
        }
    }

    private void refreshWidgets() {
        boolean wasSyncing = this.syncing;
        this.syncing = true;
        try {
            OsdPreset pending = this.pendingPreset;
            sync(this.presetButton, pending != null ? pending : this.working.preset);
            show(this.resetButton, pending == null);
            this.updateResetButtons();
            show(this.pageButton, pending == null);
            show(this.confirmButton, pending != null);
            show(this.cancelButton, pending != null);
            if (this.confirmButton != null && pending != null) {
                this.confirmButton.setMessage(this.fit(this.confirmButton.getWidth(), Component.translatable("propwash.osd.editor.preset.confirm",
                        Component.translatable(pending.translationKey())), Component.translatable("propwash.osd.editor.preset.confirm.short")));
            }
            if (this.pageButton != null) {
                this.pageButton.setMessage(this.pageLabel());
            }
            sync(this.opacitySlider);
            sync(this.scaleSlider);
            sync(this.lineSlider);
            sync(this.outlineButton, this.working.outline);
            sync(this.backgroundButton, this.working.background);
            sync(this.alertsButton, this.working.coloredAlerts);
            sync(this.vanillaButton, !this.working.hideVanillaHud);
            sync(this.breakupButton, this.working.analogBreakup);
            sync(this.ladderButton, this.working.pitchLadder);
            sync(this.ladderStepSlider);
            this.syncColorWidgets();
            this.refreshElementControls();
        } finally {
            this.syncing = wasSyncing;
        }
    }

    private void refreshElementControls() {
        boolean wasSyncing = this.syncing;
        this.syncing = true;
        try {
            boolean has = this.selected != null;
            if (this.elementColorButton != null) {
                this.elementColorButton.active = has;
                this.elementColorButton.visible = has;
                this.elementColorButton.setValue(this.selectedColor());
            }
            if (this.elementScaleSlider != null) {
                this.elementScaleSlider.active = has;
                this.elementScaleSlider.visible = has;
                this.elementScaleSlider.sync();
            }
        } finally {
            this.syncing = wasSyncing;
        }
    }

    private void select(@Nullable OsdElement element) {
        if (element != this.selected) {
            this.selected = element;
            this.refreshElementControls();
        }
    }

    private boolean editingText() {
        return this.colorBox != null && this.colorBox.isFocused() || this.warningBox != null && this.warningBox.isFocused();
    }

    private void clearFocus() {
        if (this.host != null && this.host.screen().getFocused() != null) {
            this.host.screen().setFocused(null);
        }
    }

    public void resetLayout() {
        this.working.resetLayout();
        this.pendingPreset = null;
        this.dragging = null;
        this.edited();
    }

    @Override
    public void resetDefaults() {
        this.working = OsdConfig.defaults();
        this.pendingPreset = null;
        this.dragging = null;
        this.selected = null;
        this.edited();
    }

    @Override
    public void save() {
        OsdClient.CONFIG.set(this.working.copy());
    }

    @Override
    public boolean cancelAction() {
        if (this.dragging != null) {
            this.working.move(this.dragging, this.dragStartCol, this.dragStartRow);
            this.dragging = null;
            this.refreshWidgets();
            return true;
        }
        if (this.pendingPreset != null) {
            this.cancelPreset();
            return true;
        }
        if (this.editingText()) {
            this.clearFocus();
            return true;
        }
        return false;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        OsdGrid grid = this.previewGrid;
        if (grid == null || this.host == null) {
            return;
        }
        Font font = this.host.font();
        ImageFormat format = FpvImage.editorFormat();
        if (format != this.imageFormat) {
            this.layoutImage(format);
            grid = this.previewGrid;
        }
        this.updateResetButtons();
        this.animateSample();
        this.extractScene(graphics, font);
        graphics.outline(this.previewX - 1, this.previewY - 1, this.previewW + 2, this.previewH + 2, Palette.BORDER);
        OsdElement hovered;
        graphics.enableScissor(this.imageX, this.imageY, this.imageX + this.imageW, this.imageY + this.imageH);
        try {
            if (this.dragging != null) {
                this.extractGridLines(graphics, grid);
            }
            this.renderer.setEditorFocus(this.selected);
            this.renderer.setPixelScale(this.host.minecraft().getWindow().getGuiScale());
            this.renderer.render(graphics, font, this.sample, this.working, grid, grid.textScale(this.working.scale), null, true);
            hovered = this.dragging == null && grid.contains(mouseX, mouseY) ? this.renderer.movableAt(mouseX, mouseY) : null;
            if (hovered != null && hovered != this.selected) {
                this.outlineBounds(graphics, hovered, HOVER_COLOR);
            }
            if (this.selected != null && this.renderer.wasDrawn(this.selected)) {
                this.outlineBounds(graphics, this.selected, SELECT_COLOR);
            }
            this.extractPendingBanner(graphics, font);
        } finally {
            graphics.disableScissor();
        }
        this.extractList(graphics, font, mouseX, mouseY);
        if (this.listScroll < this.maxScroll()) {
            ThemeDraw.moreBelow(graphics, font, this.listX + 2, this.listY + this.listH + 2, this.listW - 4);
        }
        int legendY = this.listBottom() + 2;
        graphics.text(font, LEGEND_ICON, this.listX + 1, legendY, HINT_COLOR, false);
        int legendX = this.listX + 1 + font.width(LEGEND_ICON) + 3;
        ThemeText.wrapped(graphics, font, Component.translatable("propwash.osd.editor.fixed_legend"), legendX, legendY,
                this.listX + this.listW - legendX, HINT_COLOR);
        if (this.selected == null) {
            int hintTop = this.listBottom() + this.legendHeight() + ROW_GAP + 2;
            this.reportBottom(hintTop + ThemeText.wrapped(graphics, font, Component.translatable("propwash.osd.editor.element_hint"), this.listX,
                    hintTop, this.listW, HINT_COLOR));
        }
        if (this.page == Page.OPTIONS) {
            int hintTop = this.rowY(3) + (ROW_HEIGHT - 8) / 2;
            this.reportBottom(hintTop + ThemeText.wrapped(graphics, font, Component.translatable("propwash.osd.editor.hint"), this.controlsX,
                    hintTop, this.controlsW, HINT_COLOR));
        }
        if (hovered != null) {
            ThemeText.tooltipLines(graphics, font, List.of(Component.translatable(hovered.translationKey()),
                    ThemeText.muted(Component.translatable("propwash.osd.editor.hint"))), mouseX, mouseY);
        } else if (this.inList(mouseX, mouseY)) {
            OsdElement listed = this.listElementAt(mouseY);
            if (listed != null) {
                ThemeText.tooltipLines(graphics, font, this.listTooltip(listed), mouseX, mouseY);
            }
        }
    }

    private List<Component> listTooltip(OsdElement element) {
        Component name = Component.translatable(element.translationKey());
        if (element.movable()) {
            return List.of(name);
        }
        return List.of(name, ThemeText.muted(Component.translatable("propwash.osd.editor.fixed")));
    }

    private void extractScene(GuiGraphicsExtractor graphics, Font font) {
        if (this.imageW < this.previewW || this.imageH < this.previewH) {
            graphics.fill(this.previewX, this.previewY, this.previewX + this.previewW, this.previewY + this.previewH, 0xFF000000);
            String label = Component.translatable(this.imageFormat.translationKey()).getString();
            int barW = this.imageX - this.previewX;
            if (barW >= ThemeText.smallWidth(font, label) + 4) {
                ThemeText.small(graphics, font, label, this.previewX + (barW - ThemeText.smallWidth(font, label)) / 2,
                        this.previewY + this.previewH - 8, HINT_COLOR);
            }
        }
        OsdPreviewScene.draw(graphics, this.imageX, this.imageY, this.imageW, this.imageH);
    }

    private void extractPendingBanner(GuiGraphicsExtractor graphics, Font font) {
        OsdPreset pending = this.pendingPreset;
        if (pending == null) {
            return;
        }
        int bannerH = 12;
        int y0 = this.imageY + this.imageH - bannerH - 2;
        graphics.fill(this.imageX + 2, y0, this.imageX + this.imageW - 2, y0 + bannerH, Palette.withAlpha(Palette.WARNING_BG, 0xD0));
        String text = Component.translatable("propwash.osd.editor.preset.pending", Component.translatable(pending.translationKey())).getString();
        ThemeText.fitted(graphics, font, text, this.imageX + 6, y0 + 2, this.imageW - 12, SELECT_COLOR);
    }

    private void animateSample() {
        double t = Util.getMillis() / 1000.0;
        this.sample.blinkSeconds = t;
        this.sample.compassDeg = OsdFormat.wrap360(32.0 + t * 6.0);
        this.sample.rollDeg = 14.0 * Math.sin(t * 0.8);
        this.sample.pitchDeg = -6.0 + 4.0 * Math.sin(t * 0.5);
        this.sample.stickRoll = (float) Math.sin(t * 0.8) * 0.4F;
        this.sample.stickPitch = (float) Math.sin(t * 0.5) * 0.3F;
    }

    private void extractGridLines(GuiGraphicsExtractor graphics, OsdGrid grid) {
        for (int col = 1; col < OsdGrid.COLS; col++) {
            int gx = Math.round(grid.cellLeft(col));
            graphics.fill(gx, this.imageY, gx + 1, this.imageY + this.imageH, GRID_COLOR);
        }
        for (int row = 1; row < OsdGrid.ROWS; row++) {
            int gy = Math.round(grid.cellTop(row));
            graphics.fill(this.imageX, gy, this.imageX + this.imageW, gy + 1, GRID_COLOR);
        }
    }

    private void outlineBounds(GuiGraphicsExtractor graphics, OsdElement element, int outlineColor) {
        float[] b = this.renderer.bounds(element);
        int x0 = (int) Math.floor(b[0]) - 1;
        int y0 = (int) Math.floor(b[1]) - 1;
        int x1 = (int) Math.ceil(b[2]) + 1;
        int y1 = (int) Math.ceil(b[3]) + 1;
        graphics.outline(x0, y0, Math.max(2, x1 - x0), Math.max(2, y1 - y0), outlineColor);
    }

    private void extractList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        ThemeDraw.framedBox(graphics, this.listX, this.listY, this.listX + this.listW, this.listY + this.listH, Palette.FIELD, Palette.BORDER);
        graphics.enableScissor(this.listX + 1, this.listY + 1, this.listX + this.listW - 1, this.listY + this.listH - 1);
        try {
            OsdElement[] all = OsdElement.values();
            for (int i = 0; i < all.length; i++) {
                OsdElement element = all[i];
                int rowTop = this.listY + 2 + i * LIST_ROW - this.listScroll;
                if (rowTop + LIST_ROW < this.listY || rowTop > this.listY + this.listH) {
                    continue;
                }
                boolean hover = mouseX >= this.listX && mouseX < this.listX + this.listW && mouseY >= rowTop && mouseY < rowTop + LIST_ROW
                        && mouseY >= this.listY && mouseY < this.listY + this.listH;
                ThemeDraw.selection(graphics, this.listX + 1, rowTop, this.listX + this.listW - 1, rowTop + LIST_ROW, element == this.selected, hover);
                OsdConfig.Slot slot = this.working.slot(element);
                int boxX = this.listX + 4;
                int boxY = rowTop + (LIST_ROW - CHECK_SIZE) / 2;
                ThemeDraw.checkbox(graphics, boxX, boxY, CHECK_SIZE, slot.enabled, new WidgetState(true, hover, false, false));
                int textColor = slot.enabled ? Palette.TEXT : HINT_COLOR;
                int nameX = boxX + CHECK_SIZE + 5;
                ThemeText.fitted(graphics, font, Component.translatable(element.translationKey()), nameX, rowTop + 2,
                        this.listX + this.listW - LIST_MARKER_SPACE - nameX, textColor);
                int markerX = this.listX + this.listW - 12;
                if (slot.hasColor()) {
                    int rgb = OsdColors.rgbOr(slot.color, 0xFFFFFF);
                    graphics.fill(markerX - 7, rowTop + 3, markerX - 2, rowTop + 8, 0xFF000000 | rgb);
                } else if (slot.hasScale()) {
                    graphics.fill(markerX - 6, rowTop + 5, markerX - 3, rowTop + 7, SELECT_COLOR);
                }
                if (!element.movable()) {
                    graphics.text(font, "⌖", markerX, rowTop + 2, HINT_COLOR, false);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        int contentHeight = this.listContentHeight();
        if (contentHeight > this.listH) {
            ThemeDraw.scrollbar(graphics, this.listX + this.listW - 1 - Metrics.SCROLLBAR_WIDTH, this.listY + 1, this.listH - 2, contentHeight,
                    this.listScroll, this.draggingListBar);
        }
    }

    private int maxScroll() {
        return Math.max(0, this.listContentHeight() - this.listH);
    }

    private int listContentHeight() {
        return OsdElement.values().length * LIST_ROW + 4;
    }

    private int legendHeight() {
        Font font = this.host.font();
        int width = Math.max(20, this.listW - 1 - font.width(LEGEND_ICON) - 3);
        int text = ThemeText.wrappedHeight(font, Component.translatable("propwash.osd.editor.fixed_legend"), width);
        return Math.max(LEGEND_HEIGHT, text + 4);
    }

    private int listBottom() {
        return this.listY + this.listH + (this.listContentHeight() > this.listH ? MORE_HEIGHT : 0);
    }

    private void clampScroll() {
        this.listScroll = Math.clamp(this.listScroll, 0, this.maxScroll());
    }

    private boolean inList(double mx, double my) {
        return mx >= this.listX && mx < this.listX + this.listW && my >= this.listY && my < this.listY + this.listH;
    }

    private @Nullable OsdElement listElementAt(double my) {
        int index = (int) Math.floor((my - this.listY - 2 + this.listScroll) / LIST_ROW);
        OsdElement[] all = OsdElement.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || this.previewGrid == null) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (this.previewGrid.contains(mx, my)) {
            this.clearFocus();
            OsdElement hit = this.renderer.movableAt(mx, my);
            this.select(hit);
            if (hit != null) {
                this.beginDrag(hit, mx, my);
            }
            return true;
        }
        if (this.inList(mx, my) && this.maxScroll() > 0 && mx >= this.listX + this.listW - 3 - Metrics.SCROLLBAR_WIDTH) {
            this.clearFocus();
            this.draggingListBar = true;
            this.dragListBar(my);
            return true;
        }
        if (this.inList(mx, my)) {
            this.clearFocus();
            OsdElement element = this.listElementAt(my);
            if (element != null) {
                boolean onCheckbox = mx < this.listX + 4 + CHECK_SIZE + 3;
                if (onCheckbox || doubleClick) {
                    this.toggle(element);
                }
                this.select(element);
            }
            return true;
        }
        return false;
    }

    public void beginDrag(OsdElement element, double mx, double my) {
        OsdGrid grid = this.previewGrid;
        if (grid == null || !element.movable()) {
            return;
        }
        OsdConfig.Slot slot = this.working.slot(element);
        this.dragging = element;
        this.dragStartCol = slot.col;
        this.dragStartRow = slot.row;
        this.grabDx = (float) (mx - grid.anchorX(slot.col, element.align()));
        this.grabDy = (float) (my - grid.cellTop(slot.row));
    }

    public void dragTo(double mx, double my) {
        OsdGrid grid = this.previewGrid;
        OsdElement element = this.dragging;
        if (grid == null || element == null) {
            return;
        }
        double anchorX = mx - this.grabDx;
        double anchorY = my - this.grabDy;
        int col = (int) Math.round((anchorX - grid.left()) / grid.cellWidth());
        if (element.align() == OsdElement.Align.RIGHT) {
            col -= 1;
        }
        int row = (int) Math.round((anchorY - grid.top()) / grid.cellHeight());
        OsdConfig.Slot slot = this.working.slot(element);
        int newCol = OsdGrid.clampCol(col);
        int newRow = OsdGrid.clampRow(row);
        if (slot.col != newCol || slot.row != newRow) {
            this.working.move(element, newCol, newRow);
            this.edited();
        }
    }

    public void endDrag() {
        this.dragging = null;
    }

    public boolean isDragging() {
        return this.dragging != null;
    }

    public void toggle(OsdElement element) {
        OsdConfig.Slot slot = this.working.slot(element);
        slot.enabled = !slot.enabled;
        this.edited();
    }

    private void dragListBar(double my) {
        this.listScroll = (int) Math.round(ScrollMath.dragOffset(my - (this.listY + 1), this.listContentHeight(), this.listH - 2));
        this.clampScroll();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingListBar) {
            this.dragListBar(event.y());
            return true;
        }
        if (this.dragging == null) {
            return false;
        }
        this.dragTo(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingListBar) {
            this.draggingListBar = false;
            return true;
        }
        if (this.dragging == null) {
            return false;
        }
        this.endDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.inList(mouseX, mouseY)) {
            return false;
        }
        int before = this.listScroll;
        this.listScroll -= (int) Math.round(scrollY * LIST_ROW * 2);
        this.clampScroll();
        return this.listScroll != before;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        OsdElement element = this.selected;
        if (element == null || this.editingText()) {
            return false;
        }
        if (event.key() == InputConstants.KEY_SPACE) {
            this.toggle(element);
            return true;
        }
        int dc = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
        int dr = event.isUp() ? -1 : event.isDown() ? 1 : 0;
        if ((dc != 0 || dr != 0) && element.movable()) {
            OsdConfig.Slot slot = this.working.slot(element);
            this.working.move(element, slot.col + dc, slot.row + dr);
            this.edited();
            return true;
        }
        return false;
    }
}
