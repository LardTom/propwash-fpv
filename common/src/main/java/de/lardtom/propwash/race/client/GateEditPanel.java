package de.lardtom.propwash.race.client;

import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSignalTarget;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

final class GateEditPanel {
    static final int MIN_WIDTH = 212;
    private static final int MAX_WIDTH = 234;
    private static final int MARGIN = 6;
    private static final int HEADER = 20;
    private static final int ROW = 16;
    private static final int STEP_BUTTON = 14;
    private static final int LABEL_WIDTH = 64;
    private static final int BUTTON = 16;
    private static final int BUTTON_GAP = 2;
    private static final int MARKER = 2;
    private static final int OFF_SCREEN = -10000;
    private static final int SAMPLE_COUNT = 99;
    private static final int PAGE_BUTTON_HEIGHT = 12;
    private static final int PAGE_GAP = 4;
    private static final int SCREEN_MARGIN = 2;
    private static final int TIP_INSET = 3;
    private static final int TIP_STRIPE = 2;
    private static final int TIP_PAD = 1;
    private static final int TIP_GAP = 3;
    private static final int TIP_LIFT = 2;

    private final GateEditor editor;
    private final boolean interactive;
    private final int width;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final List<Title> titles = new ArrayList<>();
    private List<FormattedCharSequence> info = List.of();
    private @Nullable FlatButton apply;
    private @Nullable FlatButton unlink;
    private @Nullable Boolean unlinkTool;
    private int left;
    private int top;
    private int bottom;
    private int statusY;
    private int footerY;
    private int laidWidth = -1;
    private int laidStatus = -1;
    private int laidSignal = -1;
    private boolean compact;
    private int laidHeight = -1;
    private int laidPage = -1;
    private int pageWidth;
    private int signalInfoY;
    private int signalInfoLines;
    private int separatorY;
    private int tipY;
    private int tipHeight;
    private int infoY;

    private record Slot(GateEditModel.Row row, int y, AbstractWidget main, @Nullable AbstractWidget minus, @Nullable AbstractWidget plus) {
    }

    private record Title(Component text, int y, boolean first) {
    }

    private record Line(FormattedCharSequence text, int color) {
    }

    GateEditPanel(GateEditor editor, boolean interactive) {
        this.editor = editor;
        this.interactive = interactive;
        this.width = panelWidth(font(), title(editor.gate().kind()));
    }

    static int panelWidth(Font font, Component title) {
        int tip = Math.max(font.width(mouseTip(false)), font.width(mouseTip(true))) + 2 * Metrics.PADDING + TIP_STRIPE + 2 * TIP_INSET;
        return Math.clamp(Math.max(ThemeDraw.brandedTitleX(font, 0) + font.width(title) + Metrics.PADDING, tip), MIN_WIDTH, MAX_WIDTH);
    }

    static Component mouseTip(boolean mouseFree) {
        return Component.translatable(mouseFree ? "propwash.race.edit.mouse.release" : "propwash.race.edit.mouse.hold", GateEditor.cursorKeyName());
    }

    private int tipWidth() {
        return inner() - TIP_STRIPE - 2 * TIP_INSET;
    }

    private int tipLines() {
        Font font = font();
        return Math.max(font.split(mouseTip(false), this.tipWidth()).size(), font.split(mouseTip(true), this.tipWidth()).size());
    }

    int tipTop() {
        return this.tipY;
    }

    int tipBottom() {
        return this.tipY + this.tipHeight;
    }

    boolean compact() {
        return this.compact;
    }

    GateEditor editor() {
        return this.editor;
    }

    List<AbstractWidget> widgets() {
        return this.widgets;
    }

    int left() {
        return this.left;
    }

    int top() {
        return this.top;
    }

    int bottom() {
        return this.bottom;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    int width() {
        return this.width;
    }

    private int inner() {
        return this.width - 2 * Metrics.PADDING;
    }

    boolean stale(int width, int height) {
        return width != this.laidWidth || height != this.laidHeight || this.editor.model().page() != this.laidPage
                || !this.compact && font().split(this.describe(), inner()).size() != this.info.size()
                || this.compact && this.statusLines().size() != this.laidStatus
                || this.signalLines() != this.laidSignal;
    }

    private int signalLines() {
        return this.editor.model().rows().contains(GateEditModel.Row.SIGNAL) ? font().split(this.signalInfo(), inner()).size() : 0;
    }

    private static int pageBody(List<GateEditModel.Row> rows, int signalLines) {
        int body = bodyHeight(rows);
        return rows.contains(GateEditModel.Row.SIGNAL) && signalLines > 0 ? body + 2 + signalLines * Metrics.LINE_HEIGHT : body;
    }

    private Component pageLabel(int page) {
        return Component.translatable(page == 0 ? "propwash.race.edit.page.signal" : "propwash.race.edit.page.pose");
    }

    static Component title(CourseElement kind) {
        if (kind.freeform()) {
            return Component.translatable("propwash.race.freeform.edit.title");
        }
        return kind.gate() ? Component.translatable("propwash.race.edit.title")
                : Component.translatable("propwash.race.element.edit.title", GateMounting.elementName(kind));
    }

    private Component describe() {
        GateEntity gate = this.editor.gate();
        Component pose = GateMounting.describe(gate.geometry());
        if (gate.inCourse()) {
            pose = Component.translatable(gate.kind().gate() ? "propwash.race.edit.info_course" : "propwash.race.element.edit.info_course", pose,
                    gate.gateIndex() + 1, gate.courseId());
        }
        return Component.translatable("propwash.race.edit.placed", pose);
    }

    private Component sectionTitle(GateEditModel.Section section) {
        GateEntity gate = this.editor.gate();
        CourseElement kind = gate.kind();
        if (section == GateEditModel.Section.SIGNAL) {
            return Component.translatable("propwash.race.edit.redstone");
        }
        if (kind.freeform()) {
            return Component.translatable("propwash.race.freeform.edit.section");
        }
        if (section == GateEditModel.Section.ROTATION) {
            return Component.translatable(kind.gate() ? "propwash.race.edit.rotation"
                    : kind == CourseElement.GATE_POLE ? "propwash.race.pole.edit.rotation" : "propwash.race.element.edit.rotation");
        }
        return Component.translatable(kind.gate() ? "propwash.race.edit.size" : "propwash.race.element.edit.size." + kind.key(),
                blocksText(gate.standardOpeningWidth()), blocksText(gate.standardOpeningHeight()));
    }

    private String rowKey(GateEditModel.Row row) {
        CourseElement kind = this.editor.gate().kind();
        return switch (row) {
            case HEADING -> kind.gate() ? "heading" : "element.heading";
            case PITCH -> "pitch";
            case ROLL -> "roll";
            case VARIANT -> "variant." + kind.key();
            case WIDTH -> kind.gate() ? "width" : "element." + kind.key() + ".width";
            case HEIGHT -> kind.gate() ? "height" : "element." + kind.key() + ".height";
            case SIGNAL -> "signal";
            case PULSE -> "pulse";
            case RACE_ONLY -> "race_only";
        };
    }

    static String pulseText(double ticks) {
        int value = GateSignal.clampPulse((int) Math.round(ticks));
        return Component.translatable("propwash.race.edit.pulse.value", value, GateSignal.secondsText(value)).getString();
    }

    private static Component toggleName(boolean on) {
        return Component.translatable(on ? "propwash.race.edit.toggle.on" : "propwash.race.edit.toggle.off");
    }

    private Component rowTooltip(GateEditModel.Row row) {
        CourseElement kind = this.editor.gate().kind();
        if (row == GateEditModel.Row.VARIANT) {
            return Component.translatable(kind.freeform() ? "propwash.race.freeform.edit.flip.tooltip"
                    : "propwash.race.element.edit.variant." + kind.key() + ".tooltip");
        }
        return Component.translatable("propwash.race.edit." + this.rowKey(row) + ".tooltip");
    }

    private Component variantName(int variant) {
        GateEntity gate = this.editor.gate();
        CourseElement kind = gate.kind();
        if (kind.freeform()) {
            return GateMounting.variantName(gate.geometry().withVariant(variant));
        }
        if (kind == CourseElement.CUBE) {
            return Component.translatable("propwash.race.edit.variant.cube." + kind.variantKey(variant));
        }
        return GateMounting.variantName(kind, variant);
    }

    static String degreesText(double value) {
        return GateMounting.degrees((float) value) + "°";
    }

    static String blocksText(double value) {
        return GateMounting.degrees((float) value);
    }

    private List<Line> statusLines() {
        Font font = font();
        List<Line> lines = new ArrayList<>();
        GateEditor.Status status = this.editor.status();
        boolean signalOnly = status == GateEditor.Status.SAME && this.editor.model().signalChanged();
        int color = switch (status) {
            case SAME -> signalOnly ? Palette.SUCCESS : Palette.TEXT_FAINT;
            case FREE -> Palette.SUCCESS;
            default -> Palette.ERROR;
        };
        Component state = signalOnly ? Component.translatable("propwash.race.edit.status.signal")
                : status == GateEditor.Status.SAME && this.laidPage == 1 ? Component.translatable("propwash.race.edit.status.signal_same")
                : GateEditor.statusText(status);
        for (FormattedCharSequence line : font.split(state, inner())) {
            lines.add(new Line(line, color));
        }
        FreeformFill.Integrity integrity = this.editor.integrity();
        if (this.editor.freeform() && integrity != null) {
            Component text = integrity.intact() ? Component.translatable("propwash.race.freeform.edit.intact")
                    : Component.translatable("propwash.race.freeform.edit.changed", integrity.openEdges(), integrity.blockedCells());
            int tint = integrity.intact() ? Palette.TEXT_MUTED : Palette.WARNING;
            for (FormattedCharSequence line : font.split(text, inner())) {
                lines.add(new Line(line, tint));
            }
        }
        return lines;
    }

    private int statusReserve() {
        Font font = font();
        int lines = 0;
        for (GateEditor.Status status : GateEditor.Status.values()) {
            lines = Math.max(lines, font.split(GateEditor.statusText(status), inner()).size());
        }
        if (this.editor.model().rows().contains(GateEditModel.Row.SIGNAL)) {
            lines = Math.max(lines, font.split(Component.translatable("propwash.race.edit.status.signal"), inner()).size());
            lines = Math.max(lines, font.split(Component.translatable("propwash.race.edit.status.signal_same"), inner()).size());
        }
        if (this.editor.freeform()) {
            lines += Math.max(font.split(Component.translatable("propwash.race.freeform.edit.intact"), inner()).size(),
                    font.split(Component.translatable("propwash.race.freeform.edit.changed", SAMPLE_COUNT, SAMPLE_COUNT), inner()).size());
        }
        return lines;
    }

    private List<Component> hints() {
        List<Component> hints = new ArrayList<>();
        if (this.editor.freeform()) {
            hints.add(Component.translatable("propwash.race.edit.hint.flip", Component.keybind(KeyDefaults.GATE_ROTATE.mappingName())));
        } else {
            hints.add(Component.translatable("propwash.race.edit.hint.value"));
            hints.add(Component.translatable("propwash.race.edit.hint.row", Component.keybind("key.sneak")));
        }
        hints.add(Component.translatable("propwash.race.edit.hint.apply", Component.keybind("key.use")));
        hints.add(Component.translatable("propwash.race.edit.hint.cancel"));
        return hints;
    }

    private int hudFooterLines() {
        Font font = font();
        int lines = 0;
        for (Component hint : this.hints()) {
            lines += font.split(hint, inner()).size();
        }
        return lines;
    }

    private int cursorFooterHeight() {
        return 2 * (BUTTON + BUTTON_GAP);
    }

    void layout(int width, int height) {
        this.widgets.clear();
        this.slots.clear();
        this.titles.clear();
        this.apply = null;
        this.unlink = null;
        this.unlinkTool = null;
        this.laidWidth = width;
        this.laidHeight = height;
        GateEditModel model = this.editor.model();
        int page = model.page();
        int pages = model.pages();
        this.laidPage = page;
        Font font = font();
        int inner = inner();
        this.info = font.split(this.describe(), inner);
        List<GateEditModel.Row> rows = model.rows(page);
        int signal = this.signalLines();
        int body = 0;
        for (int p = 0; p < pages; p++) {
            body = Math.max(body, pageBody(model.rows(p), signal));
        }
        int footer = Math.max(this.hudFooterLines() * Metrics.LINE_HEIGHT, this.cursorFooterHeight());
        int status = this.statusReserve();
        int infoGap = 4;
        int footerGap = 6;
        int room = height - 2 * SCREEN_MARGIN;
        this.tipHeight = this.tipLines() * Metrics.LINE_HEIGHT + 2 * TIP_PAD;
        int tip = this.tipHeight + TIP_GAP;
        int tipLift = 0;
        int total = HEADER + Metrics.PADDING + tip + this.info.size() * Metrics.LINE_HEIGHT + infoGap + body
                + status * Metrics.LINE_HEIGHT + footerGap + footer + Metrics.PADDING;
        this.compact = total > room;
        if (this.compact) {
            this.info = List.of(this.describe().getVisualOrderText());
            status = this.statusLines().size();
            body = pageBody(rows, signal);
            footer = this.interactive ? 2 * (BUTTON + BUTTON_GAP) : this.hints().size() * Metrics.LINE_HEIGHT;
            this.tipHeight = Metrics.LINE_HEIGHT;
            tip = this.tipHeight;
            tipLift = TIP_LIFT;
            total = HEADER + Metrics.PADDING + tip + Metrics.LINE_HEIGHT + infoGap + body + status * Metrics.LINE_HEIGHT + footerGap + footer
                    + Metrics.PADDING;
            int tighter = Math.clamp(total - room, 0, 3);
            footerGap -= tighter;
            total -= tighter;
            tighter = Math.clamp(total - room, 0, 3);
            infoGap -= tighter;
            total -= tighter;
        }
        this.laidStatus = status;
        this.left = width - this.width - MARGIN;
        this.top = Math.max(SCREEN_MARGIN, (height - total) / 2);
        this.bottom = this.top + total;
        int x = this.left + Metrics.PADDING;
        this.tipY = this.top + HEADER + Metrics.PADDING - tipLift;
        this.infoY = this.top + HEADER + Metrics.PADDING + tip;
        int y = this.infoY + this.info.size() * Metrics.LINE_HEIGHT + infoGap;
        GateEditModel.Section last = null;
        this.pageWidth = 0;
        if (pages > 1) {
            Component label = this.pageLabel(page);
            this.pageWidth = font.width(label) + 8;
            if (this.interactive) {
                int target = (page + 1) % pages;
                FlatButton turn = new FlatButton(x + inner - this.pageWidth, y - 2, this.pageWidth, PAGE_BUTTON_HEIGHT, label, FlatButton.Style.NORMAL,
                        () -> this.editor.showPage(target));
                turn.setTooltip(Tooltip.create(Component.translatable("propwash.race.edit.page.tooltip")));
                this.widgets.add(turn);
            }
        }
        int bodyTop = y;
        for (GateEditModel.Row row : rows) {
            GateEditModel.Section section = GateEditModel.section(row);
            if (section != last) {
                if (last != null) {
                    y += 4;
                }
                this.titles.add(new Title(this.sectionTitle(section), y, last == null));
                y += Metrics.SECTION_HEIGHT;
                last = section;
            }
            this.row(row, x, y, inner);
            y += ROW;
        }
        this.signalInfoY = y + 2;
        this.laidSignal = signal;
        this.signalInfoLines = rows.contains(GateEditModel.Row.SIGNAL) ? signal : 0;
        this.statusY = Math.max(y, bodyTop + body - 4) + 4;
        this.footerY = this.bottom - Metrics.PADDING - footer;
        this.separatorY = this.footerY - (footerGap + 2) / 2;
        if (this.interactive) {
            this.buttons(x, this.footerY, inner);
        }
    }

    private static int bodyHeight(List<GateEditModel.Row> rows) {
        int sections = 0;
        GateEditModel.Section last = null;
        for (GateEditModel.Row row : rows) {
            if (GateEditModel.section(row) != last) {
                sections++;
                last = GateEditModel.section(row);
            }
        }
        return sections * (Metrics.SECTION_HEIGHT + 4) + rows.size() * ROW;
    }

    private void row(GateEditModel.Row row, int x, int y, int inner) {
        GateEditModel model = this.editor.model();
        Component label = Component.translatable("propwash.race.edit." + this.rowKey(row));
        if (row.toggle()) {
            boolean fallback = row == GateEditModel.Row.SIGNAL ? GateSignal.DEFAULT.enabled() : GateSignal.DEFAULT.raceOnly();
            ChoiceButton<Boolean> choice = new ChoiceButton<>(x, y, inner, Metrics.CONTROL_HEIGHT, label, LABEL_WIDTH, List.of(true, false),
                    () -> model.value(row) >= 0.5, v -> this.editor.set(row, v ? 1.0 : 0.0), GateEditPanel::toggleName, () -> fallback);
            this.add(choice, row);
            this.slots.add(new Slot(row, y, choice, null, null));
            return;
        }
        if (row == GateEditModel.Row.VARIANT) {
            CourseElement kind = model.kind();
            List<Integer> values = new ArrayList<>();
            if (kind.freeform()) {
                values.add(model.startVariant());
                values.add(kind.opposite(model.startVariant()));
            } else {
                for (int v = 0; v < kind.variants(); v++) {
                    values.add(v);
                }
            }
            ChoiceButton<Integer> choice = new ChoiceButton<>(x, y, inner, Metrics.CONTROL_HEIGHT, label, LABEL_WIDTH, values, model::variant,
                    v -> {
                        model.select(row);
                        this.editor.setVariant(v);
                    }, this::variantName, model::startVariant);
            this.add(choice, row);
            this.slots.add(new Slot(row, y, choice, null, null));
            return;
        }
        double min;
        double max;
        double step;
        float buttonStep;
        DoubleSupplier fallback;
        DoubleFunction<String> format;
        GateEntity gate = this.editor.gate();
        switch (row) {
            case HEADING -> {
                min = 0.0;
                max = 359.0;
                step = 1.0;
                float start = gate.geometry().heading();
                fallback = () -> start;
            }
            case PITCH -> {
                min = -GateTilt.MAX_PITCH;
                max = GateTilt.MAX_PITCH;
                step = 1.0;
                fallback = () -> 0.0;
            }
            case ROLL -> {
                min = -180.0;
                max = 180.0;
                step = 1.0;
                fallback = () -> 0.0;
            }
            case WIDTH -> {
                min = GateSize.MIN_OPENING;
                max = GateSize.MAX_OPENING;
                step = GateSize.OPENING_STEP;
                fallback = gate::standardOpeningWidth;
            }
            case PULSE -> {
                min = GateSignal.MIN_PULSE;
                max = GateSignal.MAX_PULSE;
                step = 1.0;
                fallback = () -> GateSignal.DEFAULT_PULSE;
            }
            default -> {
                min = GateSize.MIN_OPENING;
                max = GateSize.MAX_OPENING;
                step = GateSize.OPENING_STEP;
                fallback = gate::standardOpeningHeight;
            }
        }
        if (row.angle()) {
            buttonStep = GateMountKeys.step();
            format = GateEditPanel::degreesText;
        } else if (row == GateEditModel.Row.PULSE) {
            buttonStep = GateEditModel.PULSE_STEP;
            format = GateEditPanel::pulseText;
        } else {
            buttonStep = (float) GateSize.OPENING_STEP;
            format = GateEditPanel::blocksText;
        }
        int sliderWidth = inner - 2 * (STEP_BUTTON + 2);
        FlatButton minus = new FlatButton(x, y, STEP_BUTTON, Metrics.CONTROL_HEIGHT, Component.literal("−"), FlatButton.Style.NORMAL,
                () -> this.editor.set(row, model.value(row) - buttonStep));
        ValueSlider slider = new ValueSlider(x + STEP_BUTTON + 2, y, sliderWidth, Metrics.CONTROL_HEIGHT, label, LABEL_WIDTH, min, max, step,
                () -> model.value(row), v -> this.editor.set(row, v), fallback, format);
        FlatButton plus = new FlatButton(x + STEP_BUTTON + 4 + sliderWidth, y, STEP_BUTTON, Metrics.CONTROL_HEIGHT, Component.literal("+"),
                FlatButton.Style.NORMAL, () -> this.editor.set(row, model.value(row) + buttonStep));
        if (this.interactive) {
            Component stepHint = Component.translatable("propwash.race.edit.step", format.apply(buttonStep));
            minus.setTooltip(Tooltip.create(stepHint));
            plus.setTooltip(Tooltip.create(stepHint));
        }
        this.widgets.add(minus);
        this.add(slider, row);
        this.widgets.add(plus);
        this.slots.add(new Slot(row, y, slider, minus, plus));
    }

    @Nullable AbstractWidget control(GateEditModel.Row row) {
        for (Slot slot : this.slots) {
            if (slot.row() == row) {
                return slot.main();
            }
        }
        return null;
    }

    @Nullable AbstractWidget plus(GateEditModel.Row row) {
        for (Slot slot : this.slots) {
            if (slot.row() == row) {
                return slot.plus();
            }
        }
        return null;
    }

    @Nullable FlatButton applyButton() {
        return this.apply;
    }

    private void add(AbstractWidget widget, GateEditModel.Row row) {
        if (this.interactive) {
            widget.setTooltip(ThemeText.tip(this.rowTooltip(row)));
        }
        this.widgets.add(widget);
    }

    private void buttons(int x, int y, int inner) {
        int half = (inner - 4) / 2;
        FlatButton first;
        if (this.editor.freeform()) {
            first = new FlatButton(x, y, half, BUTTON, Component.translatable("propwash.race.freeform.edit.rescan"), FlatButton.Style.NORMAL,
                    this.editor::rescan);
            first.setTooltip(Tooltip.create(Component.translatable("propwash.race.freeform.edit.rescan.tooltip")));
        } else {
            boolean signalPage = this.laidPage == 1;
            first = new FlatButton(x, y, half, BUTTON, Component.translatable("propwash.race.edit.reset"), FlatButton.Style.NORMAL,
                    signalPage ? this.editor::resetSignal : this.editor::reset);
            first.setTooltip(Tooltip.create(Component.translatable(signalPage ? "propwash.race.edit.reset.signal.tooltip"
                    : "propwash.race.edit.reset.tooltip")));
        }
        this.widgets.add(first);
        this.apply = new FlatButton(x + half + 4, y, inner - half - 4, BUTTON, Component.translatable("propwash.race.edit.apply"),
                FlatButton.Style.PRIMARY, this.editor::apply);
        this.widgets.add(this.apply);
        int second = y + BUTTON + BUTTON_GAP;
        if (this.editor.gate().inCourse()) {
            this.unlink = new FlatButton(x, second, half, BUTTON, Component.translatable("propwash.race.edit.unlink"), FlatButton.Style.DANGER,
                    this.editor::unlink);
            this.widgets.add(this.unlink);
        }
        this.widgets.add(new FlatButton(x + half + 4, second, inner - half - 4, BUTTON, Component.translatable("gui.cancel"),
                FlatButton.Style.NORMAL, this.editor::cancel));
        this.sync();
    }

    void sync() {
        if (this.apply != null) {
            GateEditor.Status status = this.editor.status();
            this.apply.active = status == GateEditor.Status.FREE || status == GateEditor.Status.SAME && this.editor.model().signalChanged();
        }
        if (this.unlink != null) {
            boolean tool = this.editor.holdingCourseTool();
            if (this.unlinkTool == null || this.unlinkTool != tool) {
                this.unlinkTool = tool;
                this.unlink.active = tool;
                this.unlink.setTooltip(Tooltip.create(Component.translatable(tool ? "propwash.race.edit.unlink.tooltip"
                        : "propwash.race.edit.unlink.need_tool")));
            }
        }
    }

    GateEditModel.@Nullable Row rowAt(double mouseX, double mouseY) {
        if (mouseX < this.left || mouseX >= this.left + this.width) {
            return null;
        }
        for (Slot slot : this.slots) {
            if (mouseY >= slot.y() - 1 && mouseY < slot.y() + ROW - 1) {
                return slot.row();
            }
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.bottom;
    }

    void background(GuiGraphicsExtractor g) {
        Font font = font();
        int right = this.left + this.width;
        ThemeDraw.window(g, this.left, this.top, right, this.bottom);
        ThemeDraw.brandedHeader(g, font, this.left, this.top, right, HEADER, title(this.editor.gate().kind()), right - Metrics.PADDING);
        int x = this.left + Metrics.PADDING;
        this.mouseTip(g, font, x, right - Metrics.PADDING);
        int y = this.infoY;
        if (this.compact) {
            ThemeText.fitted(g, font, this.describe(), x, y, inner(), Palette.TEXT_MUTED);
        } else {
            for (FormattedCharSequence line : this.info) {
                ThemeText.text(g, font, line, x, y, Palette.TEXT_MUTED);
                y += Metrics.LINE_HEIGHT;
            }
        }
        for (Title title : this.titles) {
            boolean marker = title.first() && this.pageWidth > 0;
            ThemeDraw.sectionTitle(g, font, title.text(), x, title.y(), marker ? inner() - this.pageWidth - PAGE_GAP : inner());
            if (marker && !this.interactive) {
                Component label = this.pageLabel(this.laidPage);
                ThemeText.text(g, font, label, x + inner() - font.width(label), title.y(), Palette.TEXT_FAINT);
            }
        }
        GateEditModel.Row selected = this.editor.model().selected();
        for (Slot slot : this.slots) {
            if (slot.row() == selected) {
                g.fill(this.left + 1, slot.y(), this.left + 1 + MARKER, slot.y() + Metrics.CONTROL_HEIGHT, Palette.ACCENT);
            }
        }
        ThemeDraw.separator(g, x, right - Metrics.PADDING, this.separatorY);
    }

    private void mouseTip(GuiGraphicsExtractor g, Font font, int x0, int x1) {
        int y0 = this.tipY;
        int y1 = y0 + this.tipHeight;
        boolean fresh = GateEditor.tipHighlighted();
        float glow = GateEditor.tipGlow();
        ThemeDraw.box(g, x0, y0, x1, y1, Palette.mix(Palette.WARNING_BG, Palette.ACCENT_FOCUS, glow));
        g.fill(x0, y0 + 1, x0 + TIP_STRIPE, y1 - 1, Palette.mix(Palette.ACCENT, Palette.ACCENT_HOVER, glow));
        if (fresh) {
            ThemeDraw.boxOutline(g, x0, y0, x1, y1, Palette.mix(Palette.ACCENT_DIM, Palette.ACCENT_HOVER, glow));
        }
        int color = Palette.mix(Palette.ACCENT, Palette.TEXT, glow);
        int tx = x0 + TIP_STRIPE + TIP_INSET;
        Component tip = mouseTip(this.interactive);
        if (this.compact) {
            ThemeText.fitted(g, font, tip, tx, y0 + 1, this.tipWidth(), color);
            return;
        }
        List<FormattedCharSequence> lines = font.split(tip, this.tipWidth());
        int ty = y0 + (this.tipHeight - lines.size() * Metrics.LINE_HEIGHT) / 2 + 1;
        for (FormattedCharSequence line : lines) {
            ThemeText.text(g, font, line, tx, ty, color);
            ty += Metrics.LINE_HEIGHT;
        }
    }

    private Component signalInfo() {
        GateSignalTarget target = this.editor.signalTarget();
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null || minecraft.level == null) {
            return Component.translatable("propwash.race.edit.signal.none");
        }
        Component block = minecraft.level.getBlockState(new BlockPos(target.x(), target.y(), target.z())).getBlock().getName();
        return Component.translatable("propwash.race.edit.signal.target", block);
    }

    void foreground(GuiGraphicsExtractor g) {
        Font font = font();
        int x = this.left + Metrics.PADDING;
        if (this.signalInfoLines > 0) {
            List<FormattedCharSequence> lines = font.split(this.signalInfo(), inner());
            for (int i = 0; i < lines.size(); i++) {
                ThemeText.text(g, font, lines.get(i), x, this.signalInfoY + i * Metrics.LINE_HEIGHT, Palette.TEXT_MUTED);
            }
        }
        int y = this.statusY;
        for (Line line : this.statusLines()) {
            ThemeText.text(g, font, line.text(), x, y, line.color());
            y += Metrics.LINE_HEIGHT;
        }
        if (this.interactive) {
            return;
        }
        y = this.footerY;
        for (Component hint : this.hints()) {
            if (this.compact) {
                ThemeText.fitted(g, font, hint, x, y, inner(), Palette.TEXT_FAINT);
                y += Metrics.LINE_HEIGHT;
                continue;
            }
            for (FormattedCharSequence line : font.split(hint, inner())) {
                ThemeText.text(g, font, line, x, y, Palette.TEXT_FAINT);
                y += Metrics.LINE_HEIGHT;
            }
        }
    }

    void extractHud(GuiGraphicsExtractor g, float partialTick) {
        if (this.stale(g.guiWidth(), g.guiHeight())) {
            this.layout(g.guiWidth(), g.guiHeight());
        }
        this.background(g);
        GateEditModel.Row selected = this.editor.model().selected();
        for (Slot slot : this.slots) {
            slot.main().setFocused(slot.row() == selected);
        }
        for (AbstractWidget widget : this.widgets) {
            widget.extractRenderState(g, OFF_SCREEN, OFF_SCREEN, partialTick);
        }
        this.foreground(g);
    }
}
