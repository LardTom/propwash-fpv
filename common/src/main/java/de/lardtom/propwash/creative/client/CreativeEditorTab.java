package de.lardtom.propwash.creative.client;

import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.BuildTexts;
import de.lardtom.propwash.content.DronePresets;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.content.client.WorkbenchReport;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.creative.CreativeCheck;
import de.lardtom.propwash.creative.CreativeDraft;
import de.lardtom.propwash.creative.CreativeEdits;
import de.lardtom.propwash.creative.CreativeLimits;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.creative.net.CreativeEditC2S;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.SessionItems;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.tab.PidTab;
import de.lardtom.propwash.ui.client.tab.RatesTab;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CreativeEditorTab extends UiTab {
    public static final String ID = "creative_editor";
    public static final int ORDER = 290;
    public static final String KEY = "propwash.creative.";
    private static final int SPIN_WIDTH = 64;
    private static final int UNIT_WIDTH = 36;
    private static final int TWO_COLUMNS = 540;
    private static final int TOOLTIP_WIDTH = 220;
    private static final int STAT_COLUMNS = 3;
    private static final int WARNING_LINES = 3;
    private static final String[] ARM_POSITIONS = {"front_left", "front_right", "rear_left", "rear_right"};
    private static final String[] AXES = {"forward", "right", "up"};

    public record Row(CreativeParam param, int y, NumberSpinner spinner) {
    }

    public record TemplateChoice(String id, Component name, CreativeSpec spec, boolean own) {
    }

    private record Section(Component title, int y) {
    }

    private enum LabelKind {
        PARAM,
        UPTILT,
        PLAIN
    }

    private record Label(Component text, int x, int y, int width, @Nullable CreativeParam param, LabelKind kind) {
    }

    private record Unit(String text, int x, int y) {
    }

    private final CreativeDraft draft;
    private final float originalUptilt;
    private float uptilt;
    private final List<Row> rows = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private List<TemplateChoice> templates = List.of();
    private int templateIndex;
    private String templateName = "";
    private int introY;
    private int introHeight;
    private int statsY;
    private int warningsY;
    private int flightY;
    private int armsHeaderY;
    private int armsColumnX;
    private int armsColumnWidth;
    private @Nullable FlatButton deleteButton;
    private @Nullable FlatButton saveTemplateButton;

    private CreativeEditorTab(DroneContext context) {
        this.draft = new CreativeDraft(context.build());
        this.originalUptilt = CameraMount.uptilt(context.build(), tuneOf(context).values());
        this.uptilt = this.originalUptilt;
    }

    public static @Nullable CreativeEditorTab create() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        DroneContext context = DroneContext.resolve(minecraft);
        return context.hasDrone() && context.build().isCreative() ? new CreativeEditorTab(context) : null;
    }

    private static FlightTune tuneOf(DroneContext context) {
        QuadcopterEntity entity = context.entity();
        if (entity != null) {
            return entity.tune();
        }
        return context.stack().getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
    }

    public CreativeDraft draft() {
        return this.draft;
    }

    public float uptilt() {
        return this.uptilt;
    }

    public List<Row> rows() {
        return List.copyOf(this.rows);
    }

    public Optional<Row> row(CreativeParam param) {
        for (Row row : this.rows) {
            if (row.param() == param) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public List<TemplateChoice> templates() {
        return this.templates;
    }

    public int screenY(Row row) {
        return this.top() + row.y();
    }

    public void reveal(Row row, int margin) {
        ScrollPanel panel = this.scroll;
        for (int i = 0; panel != null && i < 128; i++) {
            int y = this.screenY(row);
            int before = panel.offset();
            if (y > this.y + margin) {
                panel.scroll(-1.0);
            } else if (y < this.y) {
                panel.scroll(1.0);
            } else {
                break;
            }
            this.scrollOffset = panel.offset();
            if (panel.offset() == before) {
                break;
            }
        }
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
        return Storage.DRONE;
    }

    @Override
    public boolean readOnly() {
        return !editable();
    }

    @Override
    public String readOnlyKey() {
        return KEY + "footer.read_only";
    }

    @Override
    public boolean hasDefaults() {
        return editable();
    }

    public static boolean editable() {
        Player player = Minecraft.getInstance().player;
        return player != null && CreativeEdits.mayEdit(player);
    }

    @Override
    protected void build() {
        this.rows.clear();
        this.sections.clear();
        this.labels.clear();
        this.units.clear();
        this.templates = this.templateChoices();
        this.templateIndex = Math.max(0, Math.min(this.templateIndex, this.templates.size() - 1));
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        Font font = this.font();
        boolean editable = editable();
        int cy = 0;
        this.introY = cy;
        this.introHeight = ThemeText.wrappedHeight(font, this.intro(), w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING;
        cy += this.introHeight + Metrics.GAP;

        panel.stop(cy);
        this.sections.add(new Section(Component.translatable(KEY + "section.stats"), cy));
        cy += Metrics.SECTION_HEIGHT;
        this.statsY = cy;
        cy += 2 * Metrics.TABLE_ROW + Metrics.GAP;
        this.warningsY = cy;
        cy += WARNING_LINES * Metrics.LINE_HEIGHT + Metrics.SECTION_GAP;

        cy = this.templateSection(panel, cy, w, editable);
        cy = this.baseSection(panel, cy, w, editable);
        for (CreativeParam.Group group : CreativeParam.Group.values()) {
            cy = this.groupSection(panel, group, cy, w, editable);
        }
        panel.stop(cy);
        this.sections.add(new Section(Component.translatable(KEY + "section.flight"), cy));
        cy += Metrics.SECTION_HEIGHT;
        this.flightY = cy;
        cy += ThemeText.wrappedHeight(font, Component.translatable(KEY + "flight.hint"), w) + 4;
        int half = (w - Metrics.GAP) / 2;
        this.add(new FlatButton(this.x, this.top() + cy, half, FIELD, Component.translatable(KEY + "flight.pid"), FlatButton.Style.NORMAL,
                () -> this.open(PidTab.ID)));
        this.add(new FlatButton(this.x + half + Metrics.GAP, this.top() + cy, w - half - Metrics.GAP, FIELD,
                Component.translatable(KEY + "flight.rates"), FlatButton.Style.NORMAL, () -> this.open(RatesTab.ID)));
        cy += ROW + Metrics.GAP;
        panel.stop(cy);
        this.endScroll(cy);
    }

    private void open(String tab) {
        if (this.host instanceof ConfiguratorScreen screen) {
            screen.selectTab(tab);
        }
    }

    private int templateSection(ScrollPanel panel, int cy, int w, boolean editable) {
        panel.stop(cy);
        this.sections.add(new Section(Component.translatable(KEY + "section.template"), cy));
        cy += Metrics.SECTION_HEIGHT;
        int buttonWidth = Math.min(90, w / 4);
        int choiceWidth = w - buttonWidth - Metrics.GAP;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < this.templates.size(); i++) {
            indices.add(i);
        }
        int labelWidth = Math.min(choiceWidth / 2, Math.max(Metrics.LABEL_MIN, this.font().width(Component.translatable(KEY + "template.choice")) + 8));
        ChoiceButton<Integer> choice = this.add(new ChoiceButton<>(this.x, this.top() + cy, choiceWidth, FIELD,
                Component.translatable(KEY + "template.choice"), labelWidth, indices, () -> this.templateIndex, i -> {
                    this.templateIndex = i;
                    this.updateTemplateButtons();
                }, i -> this.templates.get(i).name(), null));
        choice.setTooltip(Tooltip.create(Component.translatable(KEY + "template.choice.tip")));
        FlatButton load = this.add(new FlatButton(this.x + choiceWidth + Metrics.GAP, this.top() + cy, buttonWidth, FIELD,
                Component.translatable(KEY + "template.load"), FlatButton.Style.PRIMARY, this::loadTemplate));
        load.active = editable;
        load.setTooltip(Tooltip.create(Component.translatable(KEY + "template.load.tip")));
        panel.stop(cy);
        cy += ROW;
        int nameWidth = w - 2 * (buttonWidth + Metrics.GAP);
        EditBox name = new TextField(this.font(), this.x, this.top() + cy, nameWidth, FIELD, Component.translatable(KEY + "template.name"));
        name.setMaxLength(CreativeTemplates.MAX_NAME_LENGTH);
        name.setValue(this.templateName);
        name.setHint(ThemeText.colored(Component.translatable(KEY + "template.name.hint"), Palette.TEXT_DISABLED));
        name.setResponder(text -> {
            this.templateName = text;
            this.updateTemplateButtons();
        });
        this.add(name);
        this.saveTemplateButton = this.add(new FlatButton(this.x + nameWidth + Metrics.GAP, this.top() + cy, buttonWidth, FIELD,
                Component.translatable(KEY + "template.save"), FlatButton.Style.NORMAL, this::saveTemplate));
        this.saveTemplateButton.setTooltip(Tooltip.create(Component.translatable(KEY + "template.save.tip")));
        this.deleteButton = this.add(new FlatButton(this.x + nameWidth + buttonWidth + 2 * Metrics.GAP, this.top() + cy, buttonWidth, FIELD,
                Component.translatable(KEY + "template.delete"), FlatButton.Style.DANGER, this::deleteTemplate));
        this.deleteButton.setTooltip(Tooltip.create(Component.translatable(KEY + "template.delete.tip")));
        panel.stop(cy);
        cy += ROW;
        int half = (w - Metrics.GAP) / 2;
        FlatButton copy = this.add(new FlatButton(this.x, this.top() + cy, half, FIELD, Component.translatable(KEY + "code.copy"),
                FlatButton.Style.NORMAL, this::copyCode));
        copy.setTooltip(Tooltip.create(Component.translatable(KEY + "code.copy.tip")));
        FlatButton paste = this.add(new FlatButton(this.x + half + Metrics.GAP, this.top() + cy, w - half - Metrics.GAP, FIELD,
                Component.translatable(KEY + "code.paste"), FlatButton.Style.NORMAL, this::pasteCode));
        paste.active = editable;
        paste.setTooltip(Tooltip.create(Component.translatable(KEY + "code.paste.tip")));
        panel.stop(cy);
        cy += ROW + Metrics.SECTION_GAP;
        this.updateTemplateButtons();
        return cy;
    }

    private void updateTemplateButtons() {
        TemplateChoice selected = this.templates.isEmpty() ? null : this.templates.get(this.templateIndex);
        if (this.deleteButton != null) {
            this.deleteButton.active = selected != null && selected.own();
        }
        if (this.saveTemplateButton != null) {
            this.saveTemplateButton.active = true;
        }
    }

    private int baseSection(ScrollPanel panel, int cy, int w, boolean editable) {
        panel.stop(cy);
        this.sections.add(new Section(Component.translatable(KEY + "section.base"), cy));
        cy += Metrics.SECTION_HEIGHT;
        boolean two = w >= TWO_COLUMNS;
        int colWidth = two ? (w - Metrics.GAP) / 2 : w;
        int labelWidth = Math.min(colWidth / 2, 110);
        int index = 0;
        for (PartKind kind : CreativeSpec.BASE_KINDS) {
            int col = two ? index % 2 : 0;
            int row = two ? index / 2 : index;
            int x = this.x + col * (colWidth + Metrics.GAP);
            int y = this.top() + cy + row * ROW;
            List<String> keys = new ArrayList<>();
            keys.add("");
            for (PartInfo part : PartIndex.all(kind)) {
                if (!part.missing()) {
                    keys.add(part.key());
                }
            }
            ChoiceButton<String> choice = this.add(new ChoiceButton<>(x, y, colWidth, FIELD, Component.translatable(KEY + "base." + kind.key()),
                    labelWidth, keys, () -> this.draft.basePart(kind).orElse(""), key -> {
                        this.draft.basePart(kind, key.isEmpty() ? null : key);
                        this.changed();
                    }, key -> this.partName(kind, key), () -> ""));
            choice.active = editable;
            choice.setTooltip(Tooltip.create(Component.translatable(KEY + "base.tip")));
            index++;
        }
        int rowsUsed = two ? (index + 1) / 2 : index;
        for (int r = 0; r <= rowsUsed; r++) {
            panel.stop(cy + r * ROW);
        }
        return cy + rowsUsed * ROW + Metrics.SECTION_GAP;
    }

    private Component partName(PartKind kind, String key) {
        if (key.isEmpty()) {
            return Component.translatable(KEY + "base.installed", Component.translatable(this.draft.installedPart(kind).translationKey()));
        }
        Optional<? extends PartInfo> part = CreativeLimits.part(kind, key);
        return part.isPresent() ? Component.translatable(part.get().translationKey()) : Component.literal(key);
    }

    private int groupSection(ScrollPanel panel, CreativeParam.Group group, int cy, int w, boolean editable) {
        panel.stop(cy);
        this.sections.add(new Section(Component.translatable(group.translationKey()), cy));
        cy += Metrics.SECTION_HEIGHT;
        List<CreativeParam> params = new ArrayList<>();
        for (CreativeParam param : CreativeParam.grouped().get(group)) {
            if (!CreativeParam.ARMS.contains(param)) {
                params.add(param);
            }
        }
        boolean two = w >= TWO_COLUMNS;
        int colWidth = two ? (w - Metrics.GAP) / 2 : w;
        int index = 0;
        if (group == CreativeParam.Group.CAMERA) {
            int y = cy;
            this.uptiltRow(this.x, y, colWidth, editable);
            index++;
        }
        for (CreativeParam param : params) {
            int col = two ? index % 2 : 0;
            int row = two ? index / 2 : index;
            int x = this.x + col * (colWidth + Metrics.GAP);
            int y = cy + row * ROW;
            this.paramRow(param, x, y, colWidth, editable);
            index++;
        }
        int rowsUsed = two ? (index + 1) / 2 : index;
        for (int r = 0; r <= rowsUsed; r++) {
            panel.stop(cy + r * ROW);
        }
        cy += rowsUsed * ROW;
        if (group == CreativeParam.Group.GEOMETRY) {
            cy = this.armsTable(panel, cy + 4, w, editable);
        }
        return cy + Metrics.SECTION_GAP;
    }

    private void paramRow(CreativeParam param, int x, int y, int width, boolean editable) {
        int spinX = x + width - SPIN_WIDTH - UNIT_WIDTH;
        if (param.toggle()) {
            int labelWidth = Math.max(Metrics.LABEL_MIN, width - SPIN_WIDTH - UNIT_WIDTH - Metrics.GAP);
            ChoiceButton<Boolean> toggle = this.add(ChoiceButton.toggle(x, this.top() + y, width - UNIT_WIDTH, FIELD,
                    Component.translatable(param.translationKey()), labelWidth, () -> this.draft.value(param) >= 0.5, on -> {
                        this.draft.set(param, on ? 1.0 : 0.0);
                        this.changed();
                    }, on -> Component.translatable(on ? "propwash.ui.value.on" : "propwash.ui.value.off"), () -> this.draft.base(param) >= 0.5));
            toggle.active = editable;
            toggle.setTooltip(ThemeText.tip(this.tooltip(param)));
            return;
        }
        NumberSpinner spinner = this.spinner(param, spinX, y, SPIN_WIDTH, editable);
        this.labels.add(new Label(Component.translatable(param.translationKey()), x, y, spinX - x - Metrics.GAP, param, LabelKind.PARAM));
        this.units.add(new Unit(param.unit(), spinX + SPIN_WIDTH + 3, y));
        this.rows.add(new Row(param, y, spinner));
    }

    private NumberSpinner spinner(CreativeParam param, int x, int y, int width, boolean editable) {
        NumberSpinner spinner = this.add(new NumberSpinner(x, this.top() + y, width, FIELD, Component.translatable(param.translationKey()),
                () -> this.draft.value(param),
                steps -> this.apply(this.draft.step(param, steps)),
                value -> this.apply(this.draft.set(param, value)),
                () -> this.draft.base(param),
                value -> Numbers.of(value, param.decimals()).getString(),
                value -> value,
                CreativeLimits.tolerance(param)).range(param.min(), param.max()));
        if (param.integer()) {
            spinner.integers();
        }
        spinner.active = editable;
        spinner.setTooltip(ThemeText.tip(this.tooltip(param)));
        return spinner;
    }

    private void uptiltRow(int x, int y, int width, boolean editable) {
        int spinX = x + width - SPIN_WIDTH - UNIT_WIDTH;
        CameraMount.Range range = CameraMount.range(this.draft.build());
        NumberSpinner spinner = this.add(new NumberSpinner(spinX, this.top() + y, SPIN_WIDTH, FIELD, Component.translatable(KEY + "uptilt"),
                () -> this.uptilt,
                steps -> this.setUptilt(this.uptilt + steps),
                value -> this.setUptilt((float) value),
                () -> CameraMount.defaultUptilt(this.draft.installed().frame()),
                value -> Numbers.of(value, 0).getString(),
                value -> value,
                0.5).range(range.minDeg(), range.maxDeg()).integers());
        spinner.active = editable;
        spinner.setTooltip(Tooltip.create(Component.empty().append(ThemeText.accent(Component.translatable(KEY + "uptilt")))
                .append("\n").append(Component.translatable(KEY + "uptilt.tip"))
                .append("\n").append(ThemeText.muted(Component.translatable(KEY + "tooltip.range", Numbers.of(range.minDeg(), 0),
                        Numbers.of(range.maxDeg(), 0), "°")))
                .append("\n").append(ThemeText.muted(Component.translatable(KEY + "tooltip.reset")))));
        this.labels.add(new Label(Component.translatable(KEY + "uptilt"), x, y, spinX - x - Metrics.GAP, null, LabelKind.UPTILT));
        this.units.add(new Unit("°", spinX + SPIN_WIDTH + 3, y));
    }

    private void setUptilt(float value) {
        CameraMount.Range range = CameraMount.range(this.draft.build());
        float clean = range.clamp(Math.round(value));
        if (clean != Math.round(value)) {
            this.flash(Component.translatable(KEY + "note.clamped_value", Component.translatable(KEY + "uptilt"), Numbers.of(clean, 0)),
                    Palette.WARNING);
        }
        if (clean != this.uptilt) {
            this.uptilt = clean;
            this.host.changed();
        }
    }

    private int armsTable(ScrollPanel panel, int cy, int w, boolean editable) {
        this.armsHeaderY = cy;
        int labelWidth = Math.min(110, w / 3);
        this.armsColumnX = this.x + labelWidth;
        this.armsColumnWidth = (w - labelWidth) / AXES.length;
        cy += Metrics.TABLE_HEADER;
        for (int arm = 0; arm < ARM_POSITIONS.length; arm++) {
            panel.stop(cy);
            this.labels.add(new Label(Component.translatable(KEY + "arm." + ARM_POSITIONS[arm]), this.x, cy, labelWidth - Metrics.GAP, null,
                    LabelKind.PLAIN));
            for (int axis = 0; axis < AXES.length; axis++) {
                CreativeParam param = CreativeParam.ARMS.get(arm * AXES.length + axis);
                int cx = this.armsColumnX + axis * this.armsColumnWidth;
                NumberSpinner spinner = this.spinner(param, cx + 1, cy, this.armsColumnWidth - 4, editable);
                this.rows.add(new Row(param, cy, spinner));
            }
            cy += ROW;
        }
        panel.stop(cy);
        return cy;
    }

    private void apply(Optional<CreativeLimits.Note> note) {
        note.ifPresent(n -> this.flash(Component.translatable(KEY + "note.clamped_value", Component.translatable(n.param() != null
                ? n.param().translationKey() : KEY + "tab"), valueWithUnit(n.param(), n.applied())), Palette.WARNING));
        this.changed();
    }

    private static Component valueWithUnit(@Nullable CreativeParam param, double value) {
        if (param == null) {
            return Numbers.of(value, 3);
        }
        Component number = Numbers.of(value, param.decimals());
        return param.unit().isEmpty() ? number : Component.empty().append(number).append(" " + param.unit());
    }

    private void changed() {
        this.host.changed();
    }

    private Component tooltip(CreativeParam param) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(Component.translatable(param.translationKey())));
        tip.append("\n").append(Component.translatable(param.translationKey() + ".tip"));
        tip.append("\n").append(ThemeText.muted(Component.translatable(KEY + "tooltip.range", Numbers.of(param.min(), param.decimals()),
                Numbers.of(param.max(), param.decimals()), param.unit())));
        tip.append("\n").append(ThemeText.muted(Component.translatable(param.auto() ? KEY + "tooltip.auto" : KEY + "tooltip.base")));
        if (editable()) {
            tip.append("\n").append(ThemeText.muted(Component.translatable(KEY + "tooltip.reset")));
        }
        return tip;
    }

    private Component intro() {
        if (!editable()) {
            return Component.translatable(KEY + "intro.read_only");
        }
        DroneContext context = this.context();
        if (context.piloting() && context.telemetry() != null && context.telemetry().armed) {
            return Component.translatable(KEY + "intro.armed");
        }
        return Component.translatable(KEY + "intro");
    }

    private List<TemplateChoice> templateChoices() {
        List<TemplateChoice> out = new ArrayList<>();
        out.add(new TemplateChoice("installed", Component.translatable(KEY + "template.installed"), CreativeSpec.EMPTY, false));
        for (DroneBuild preset : DroneBuild.PRESETS) {
            out.add(new TemplateChoice("preset:" + DronePresets.key(preset).orElse(preset.frame().key()),
                    Component.translatable(DronePresets.translationKey(preset)), baseOf(preset), false));
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Optional<DroneBuild> build = Conversions.build(preset.data().build());
            if (build.isEmpty()) {
                continue;
            }
            String name = preset.data().name().map(CraftNames::clean).orElse("");
            out.add(new TemplateChoice("addon:" + preset.id(), name.isEmpty() ? Component.translatable(preset.translationKey()) : Component.literal(name),
                    baseOf(build.get()), false));
        }
        for (CreativeTemplates.Entry entry : CreativeTemplates.all()) {
            entry.spec().filter(spec -> SessionItems.unavailableKeys(spec.base()).isEmpty()).ifPresent(spec -> out.add(new TemplateChoice("own:" + entry.name,
                    Component.translatable(KEY + "template.own", entry.name), spec, true)));
        }
        return List.copyOf(out);
    }

    public static CreativeSpec baseOf(DroneBuild preset) {
        Map<PartKind, String> base = new EnumMap<>(PartKind.class);
        base.put(PartKind.FRAME, preset.frame().key());
        base.put(PartKind.STACK, preset.stack().key());
        base.put(PartKind.MOTOR, preset.motor().key());
        base.put(PartKind.PROP, preset.prop().key());
        base.put(PartKind.BATTERY, preset.battery().key());
        return new CreativeSpec(base, Map.of());
    }

    public void selectTemplate(String id) {
        for (int i = 0; i < this.templates.size(); i++) {
            if (this.templates.get(i).id().equals(id)) {
                this.templateIndex = i;
                this.updateTemplateButtons();
                return;
            }
        }
    }

    public void loadTemplate() {
        if (!editable() || this.templates.isEmpty()) {
            return;
        }
        TemplateChoice choice = this.templates.get(this.templateIndex);
        List<CreativeLimits.Note> notes = this.draft.load(choice.spec());
        this.flash(notes.isEmpty() ? Component.translatable(KEY + "template.loaded", choice.name())
                : Component.translatable(KEY + "template.loaded_notes", choice.name(), notes.size()), notes.isEmpty() ? Palette.SUCCESS : Palette.WARNING);
        this.changed();
        this.host.rebuild();
    }

    private void saveTemplate() {
        String name = CreativeTemplates.cleanName(this.templateName);
        if (name.isEmpty()) {
            name = Component.translatable(KEY + "template.default_name", CreativeTemplates.all().size() + 1).getString();
        }
        CreativeTemplates.save(name, this.draft.spec());
        this.templateName = "";
        this.flash(Component.translatable(KEY + "template.saved", name), Palette.SUCCESS);
        String id = "own:" + name;
        this.host.rebuild();
        this.templates = this.templateChoices();
        this.selectTemplate(id);
    }

    private void deleteTemplate() {
        if (this.templates.isEmpty()) {
            return;
        }
        TemplateChoice choice = this.templates.get(this.templateIndex);
        if (!choice.own()) {
            return;
        }
        String name = choice.id().substring("own:".length());
        if (CreativeTemplates.delete(name)) {
            this.flash(Component.translatable(KEY + "template.deleted", name), Palette.TEXT_MUTED);
            this.templateIndex = 0;
            this.host.rebuild();
        }
    }

    public String shareCode() {
        return CreativeShareCode.encode(this.draft.spec());
    }

    private void copyCode() {
        Minecraft.getInstance().keyboardHandler.setClipboard(this.shareCode());
        this.flash(Component.translatable(KEY + "code.copied"), Palette.SUCCESS);
    }

    private void pasteCode() {
        if (!editable()) {
            return;
        }
        this.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    public boolean paste(String code) {
        Optional<CreativeSpec> spec = CreativeShareCode.decode(code);
        if (spec.isEmpty()) {
            this.flash(Component.translatable(KEY + "code.invalid"), Palette.ERROR);
            return false;
        }
        List<String> unavailable = SessionItems.unavailableKeys(spec.get().base());
        if (!unavailable.isEmpty()) {
            this.flash(JoinText.codeParts(unavailable), Palette.ERROR);
            return false;
        }
        List<CreativeLimits.Note> notes = this.draft.load(spec.get());
        this.flash(notes.isEmpty() ? Component.translatable(KEY + "code.pasted") : Component.translatable(KEY + "code.pasted_notes", notes.size()),
                notes.isEmpty() ? Palette.SUCCESS : Palette.WARNING);
        this.changed();
        this.host.rebuild();
        return true;
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font();
        int w = this.innerWidth();
        int top = this.top();
        Component intro = this.intro();
        boolean warn = !editable();
        int introTop = top + this.introY;
        ThemeDraw.notice(g, this.x, introTop, this.x + w, introTop + this.introHeight, warn ? Palette.WARNING : Palette.ACCENT, 1.0F);
        ThemeText.wrapped(g, font, intro, this.x + Metrics.PADDING, introTop + Metrics.PADDING, w - 2 * Metrics.PADDING,
                warn ? Palette.WARNING : Palette.TEXT_MUTED);
        for (Section section : this.sections) {
            this.section(g, section.title(), this.x, top + section.y(), w);
        }
        this.extractStats(g, font, top + this.statsY, w);
        this.extractWarnings(g, font, top + this.warningsY, w, mouseX, mouseY);
        for (Label label : this.labels) {
            boolean overridden = switch (label.kind()) {
                case PARAM -> label.param() != null && this.draft.overridden(label.param());
                case UPTILT -> this.uptilt != CameraMount.defaultUptilt(this.draft.installed().frame());
                case PLAIN -> this.armOverridden(label.y());
            };
            Component text = label.text();
            if (label.param() != null && label.param().auto() && !overridden) {
                text = Component.translatable(KEY + "label.auto", text);
            }
            ThemeText.fitted(g, font, text, label.x(), Metrics.textY(top + label.y(), FIELD), label.width(), overridden ? Palette.TEXT : Palette.TEXT_MUTED);
        }
        for (Unit unit : this.units) {
            ThemeText.text(g, font, unit.text(), unit.x(), Metrics.textY(top + unit.y(), FIELD), Palette.TEXT_FAINT);
        }
        int headerY = top + this.armsHeaderY;
        for (int axis = 0; axis < AXES.length; axis++) {
            int cx = this.armsColumnX + axis * this.armsColumnWidth + (this.armsColumnWidth - 4) / 2 + 1;
            ThemeText.textCentered(g, font, Component.translatable(KEY + "axis." + AXES[axis]).getString() + " (mm)", cx, headerY, Palette.TEXT_MUTED);
        }
        ThemeText.wrapped(g, font, Component.translatable(KEY + "flight.hint"), this.x, top + this.flightY, w, Palette.TEXT_FAINT);
        this.extractScrollbar(g);
    }

    private boolean armOverridden(int y) {
        for (Row row : this.rows) {
            if (row.y() == y && CreativeParam.ARMS.contains(row.param()) && this.draft.overridden(row.param())) {
                return true;
            }
        }
        return false;
    }

    private void extractStats(GuiGraphicsExtractor g, Font font, int y, int w) {
        DroneBuild build = this.draft.build();
        BuildSlots.Check check = new BuildSlots.Check(Optional.of(build), Set.of(), Set.of(), BuildSlots.COUNT);
        List<WorkbenchReport.Row> stats = WorkbenchReport.of(check).rows();
        int columnWidth = w / STAT_COLUMNS;
        for (int i = 0; i < stats.size(); i++) {
            WorkbenchReport.Row row = stats.get(i);
            int cx = this.x + (i % STAT_COLUMNS) * columnWidth;
            int cy = y + (i / STAT_COLUMNS) * Metrics.TABLE_ROW;
            if (cy + Metrics.TABLE_ROW <= this.y || cy >= this.clipBottom()) {
                continue;
            }
            Component value = row.value();
            int valueWidth = font.width(value);
            ThemeText.fitted(g, font, row.label(), cx + Metrics.LABEL_GAP, Metrics.textY(cy, Metrics.TABLE_ROW), columnWidth - valueWidth - 2 * Metrics.LABEL_GAP, Palette.TEXT_MUTED);
            ThemeText.text(g, font, value, cx + columnWidth - valueWidth - Metrics.LABEL_GAP, Metrics.textY(cy, Metrics.TABLE_ROW), row.color());
        }
    }

    public List<Component> warningLines() {
        DroneBuild build = this.draft.build();
        List<Component> lines = new ArrayList<>();
        BuildAnalysis analysis = BuildAnalysis.of(build);
        for (BuildWarning warning : BuildTexts.analysisWarnings(build, analysis)) {
            lines.add(BuildTexts.warning(warning));
        }
        for (CreativeCheck.Warning warning : this.draft.warnings()) {
            lines.add(Component.translatable(warning.translationKey()));
        }
        return lines;
    }

    private void extractWarnings(GuiGraphicsExtractor g, Font font, int y, int w, int mouseX, int mouseY) {
        List<Component> lines = this.warningLines();
        if (lines.isEmpty()) {
            ThemeText.text(g, font, Component.translatable(KEY + "warnings.none"), this.x + Metrics.LABEL_GAP, y, Palette.SUCCESS);
            return;
        }
        int shown = lines.size() > WARNING_LINES ? WARNING_LINES - 1 : lines.size();
        for (int i = 0; i < shown; i++) {
            ThemeText.fitted(g, font, Component.literal("• ").append(lines.get(i)), this.x + Metrics.LABEL_GAP, y + i * Metrics.LINE_HEIGHT, w - Metrics.LABEL_GAP, Palette.WARNING);
        }
        if (shown < lines.size()) {
            int moreY = y + shown * Metrics.LINE_HEIGHT;
            Component more = Component.translatable(KEY + "warnings.more", lines.size() - shown);
            ThemeText.text(g, font, more, this.x + Metrics.LABEL_GAP, moreY, Palette.TEXT_FAINT);
            if (mouseX >= this.x && mouseX < this.x + w && mouseY >= moreY && mouseY < moreY + Metrics.LINE_HEIGHT && mouseY >= this.y
                    && mouseY < this.clipBottom()) {
                MutableComponent all = Component.empty();
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) {
                        all.append("\n");
                    }
                    all.append(Component.literal("• ")).append(lines.get(i));
                }
                ThemeText.tooltipWrapped(g, font, all, TOOLTIP_WIDTH, mouseX, mouseY);
            }
        }
    }

    @Override
    public void resetDefaults() {
        if (!editable()) {
            return;
        }
        this.draft.resetAll();
        this.uptilt = CameraMount.defaultUptilt(this.draft.installed().frame());
        this.changed();
    }

    public boolean dirty() {
        return this.draft.dirty() || this.uptilt != this.originalUptilt;
    }

    @Override
    public void save() {
        if (!this.dirty() || !editable()) {
            return;
        }
        DroneContext context = this.context();
        float uptilt = this.uptilt != this.originalUptilt ? this.uptilt : Float.NaN;
        QuadcopterEntity entity = context.entity();
        if (entity != null) {
            ClientNet.sendToServer(new CreativeEditC2S(entity.getId(), false, this.draft.spec(), uptilt));
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || context.stack().isEmpty()) {
            return;
        }
        boolean offHand = player.getItemInHand(InteractionHand.MAIN_HAND) != context.stack()
                && player.getItemInHand(InteractionHand.OFF_HAND) == context.stack();
        ClientNet.sendToServer(new CreativeEditC2S(CreativeEditC2S.HELD_ITEM, offHand, this.draft.spec(), uptilt));
    }

    @Override
    public void discard() {
        this.draft.revert();
        this.uptilt = this.originalUptilt;
    }

    public static String axisKey(int axis) {
        return AXES[axis].toLowerCase(Locale.ROOT);
    }
}
