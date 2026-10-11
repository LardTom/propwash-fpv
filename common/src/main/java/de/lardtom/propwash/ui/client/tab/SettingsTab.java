package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.client.CraftNameAccess;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.ui.TuneEdits;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.client.TuneModel;
import de.lardtom.propwash.ui.client.UiConfig;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public final class SettingsTab extends UiTab {
    public static final String ID = "configuration";

    private SpeedUnit speedUnit = UiConfig.get().speedUnit;
    private boolean applyTemplate = UiConfig.get().applyTemplateToNewDrones;
    private final boolean nameAvailable = CraftNameAccess.INSTANCE.available();
    private final String storedName = CraftNameAccess.INSTANCE.current();
    private String craftName = this.storedName;
    private int droneTop;
    private int nameLabelTop;
    private int nameHintTop;
    private int nameHintWidth;
    private int armTop;
    private int armHintTop;
    private int armHintWidth;
    private int displayTop;
    private int templateTop;
    private int templateTextTop;
    private int templateTextWidth;
    private FlatButton saveTemplate;
    private FlatButton applyTemplateButton;
    private FlatButton clearTemplate;
    private ChoiceButton<Boolean> applyToggle;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.ui.tab.configuration");
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public Storage storage() {
        return Storage.MIXED;
    }

    @Override
    protected void build() {
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        int left = this.x;
        int labelWidth = Math.min(140, w / 2);
        int cy = 0;

        this.droneTop = cy;
        cy += 14;
        this.nameLabelTop = cy;
        int boxWidth = Math.max(60, Math.min(w, Metrics.COLUMN_MAX) - labelWidth);
        EditBox name = new TextField(this.font(), left + labelWidth, this.top() + cy, boxWidth, FIELD,
                Component.translatable("propwash.ui.settings.craft_name"));
        name.setMaxLength(CraftNames.MAX_LENGTH);
        name.setValue(this.craftName);
        name.setEditable(this.nameAvailable);
        name.active = this.nameAvailable;
        name.setHint(ThemeText.colored(Component.translatable(this.nameAvailable ? "propwash.ui.settings.craft_name.empty"
                : "propwash.ui.settings.craft_name.none"), Palette.TEXT_DISABLED));
        name.setTooltip(Tooltip.create(Component.translatable("propwash.ui.settings.craft_name.tip")));
        name.setResponder(text -> {
            if (!text.equals(this.craftName)) {
                this.craftName = text;
                this.host.changed();
            }
        });
        this.add(name);
        cy += ROW + 2;
        this.nameHintTop = cy;
        this.nameHintWidth = w;
        cy += ThemeText.wrappedHeight(this.font(), this.nameHint(), w) + 8;

        this.armTop = cy;
        cy += 14;
        this.slider(TuneKeys.ARM_MAX_ANGLE, left, this.top() + cy, Math.min(w, Metrics.COLUMN_MAX), labelWidth);
        cy += ROW + 2;
        this.armHintTop = cy;
        this.armHintWidth = w;
        cy += this.armHintHeight(w) + 8;

        this.displayTop = cy;
        cy += 14;
        ChoiceButton<SpeedUnit> unit = this.add(new ChoiceButton<>(left, this.top() + cy, Math.min(w, Metrics.COLUMN_MAX), FIELD,
                Component.translatable("propwash.ui.settings.speed_unit"), labelWidth, Arrays.asList(SpeedUnit.values()),
                () -> this.speedUnit, v -> {
                    this.speedUnit = v;
                    this.host.changed();
                }, v -> Component.translatable(v.translationKey()), () -> SpeedUnit.KMH));
        unit.setTooltip(Tooltip.create(Component.translatable("propwash.ui.settings.speed_unit.tip")));
        cy += ROW + 8;

        this.templateTop = cy;
        cy += 14;
        ChoiceButton<Boolean> auto = this.add(ChoiceButton.toggle(left, this.top() + cy, Math.min(w, Metrics.COLUMN_MAX), FIELD,
                Component.translatable("propwash.ui.settings.apply_template"), labelWidth, () -> this.applyTemplate && hasTemplate(), v -> {
                    this.applyTemplate = v;
                    this.host.changed();
                }, v -> Component.translatable(!hasTemplate() ? "propwash.ui.settings.template_none"
                        : v ? "propwash.ui.value.on" : "propwash.ui.value.off"), () -> hasTemplate() ? Boolean.TRUE : Boolean.FALSE));
        auto.setTooltip(Tooltip.create(Component.translatable("propwash.ui.settings.apply_template.tip")));
        auto.active = hasTemplate();
        this.applyToggle = auto;
        cy += ROW + 2;

        this.templateTextTop = cy;
        this.templateTextWidth = w;
        cy += ThemeText.wrappedHeight(this.font(), this.templateText(), w) + 6;

        int buttonWidth = Math.max(80, (w - 12) / 3);
        boolean stacked = buttonWidth < 100;
        if (stacked) {
            buttonWidth = Math.min(w, 220);
        }
        int bx = left;
        this.saveTemplate = this.add(new FlatButton(bx, this.top() + cy, buttonWidth, FIELD,
                Component.translatable("propwash.ui.settings.save_template"), FlatButton.Style.NORMAL, this::saveTemplate));
        this.saveTemplate.setTooltip(Tooltip.create(Component.translatable("propwash.ui.settings.save_template.tip")));
        if (stacked) {
            cy += ROW + 2;
        } else {
            bx += buttonWidth + 6;
        }
        this.applyTemplateButton = this.add(new FlatButton(bx, this.top() + cy, buttonWidth, FIELD,
                Component.translatable("propwash.ui.settings.use_template"), FlatButton.Style.NORMAL, this::useTemplate));
        this.applyTemplateButton.setTooltip(Tooltip.create(Component.translatable("propwash.ui.settings.use_template.tip")));
        if (stacked) {
            cy += ROW + 2;
        } else {
            bx += buttonWidth + 6;
        }
        this.clearTemplate = this.add(new FlatButton(bx, this.top() + cy, buttonWidth, FIELD,
                Component.translatable("propwash.ui.settings.clear_template"), FlatButton.Style.DANGER, this::clearTemplate));
        cy += ROW + 8;
        this.endScroll(cy);
    }

    private Component armHint() {
        long angle = Math.round(this.tune().value(TuneKeys.ARM_MAX_ANGLE));
        if (angle >= Math.round(FlightControllerConfig.ARM_ANGLE_MAX)) {
            return Component.translatable("propwash.ui.settings.arm_angle.any");
        }
        if (angle <= Math.round(FlightControllerConfig.ARM_ANGLE_MIN)) {
            return Component.translatable("propwash.ui.settings.arm_angle.level");
        }
        return Component.translatable("propwash.ui.settings.arm_angle.limit", angle);
    }

    private int armHintHeight(int width) {
        int height = 0;
        for (String key : new String[]{"propwash.ui.settings.arm_angle.any", "propwash.ui.settings.arm_angle.level"}) {
            height = Math.max(height, ThemeText.wrappedHeight(this.font(), Component.translatable(key), width));
        }
        return Math.max(height, ThemeText.wrappedHeight(this.font(), Component.translatable("propwash.ui.settings.arm_angle.limit", 179), width));
    }

    private static boolean hasTemplate() {
        return !UiConfig.get().defaultTune.isEmpty();
    }

    private Component templateText() {
        TuneModel tune = this.tune();
        int entries = UiConfig.get().defaultTune.size();
        Component count = entries == 0
                ? Component.translatable(tune.live() ? "propwash.ui.settings.template_empty" : "propwash.ui.settings.template_empty_editing")
                : Component.translatable("propwash.ui.settings.template_count", entries);
        String key = tune.live() ? "propwash.ui.settings.template_live" : "propwash.ui.settings.template_editing";
        return Component.translatable(key).append(" ").append(count);
    }

    private void saveTemplate() {
        UiConfig.get().defaultTune = TuneModel.templateSafe(this.tune().working());
        UiConfig.CONFIG.save();
        this.flash(Component.translatable("propwash.ui.settings.template_saved"), Palette.SUCCESS);
    }

    private void useTemplate() {
        TuneModel tune = this.tune();
        tune.update(t -> {
            Map<String, Float> next = new TreeMap<>(UiConfig.get().defaultTune);
            for (String key : TuneKeys.pidTermKeys()) {
                if (t.containsKey(key)) {
                    next.put(key, t.get(key));
                }
            }
            if (t.containsKey(TuneKeys.PID_SOURCE)) {
                next.put(TuneKeys.PID_SOURCE, t.get(TuneKeys.PID_SOURCE));
            }
            return TuneEdits.clean(next, tune.defaults());
        }, true);
        this.flash(Component.translatable("propwash.ui.settings.template_used"), Palette.SUCCESS);
    }

    private void clearTemplate() {
        UiConfig.get().defaultTune = new TreeMap<>();
        UiConfig.CONFIG.save();
        if (!this.tune().live()) {
            this.tune().replaceAll(new TreeMap<>());
        }
        this.flash(Component.translatable("propwash.ui.settings.template_cleared"), Palette.TEXT_MUTED);
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x;
        int top = this.top();
        int w = this.innerWidth();
        boolean live = this.tune().live();
        boolean hasTemplate = hasTemplate();
        if (this.saveTemplate != null) {
            this.saveTemplate.active = live;
            this.applyTemplateButton.active = live && hasTemplate;
            this.clearTemplate.active = hasTemplate;
            this.applyToggle.active = hasTemplate;
        }
        this.section(g, Component.translatable("propwash.ui.settings.section.drone"), left, top + this.droneTop, w);
        ThemeText.text(g, this.font(), Component.translatable("propwash.ui.settings.craft_name"), left, Metrics.textY(top + this.nameLabelTop, FIELD),
                this.nameAvailable ? Palette.TEXT : Palette.TEXT_DISABLED);
        ThemeText.wrapped(g, this.font(), this.nameHint(), left, top + this.nameHintTop, this.nameHintWidth, Palette.TEXT_FAINT);
        this.section(g, Component.translatable("propwash.ui.settings.section.arming"), left, top + this.armTop, w);
        ThemeText.wrapped(g, this.font(), this.armHint(), left, top + this.armHintTop, this.armHintWidth, Palette.TEXT_FAINT);
        this.section(g, Component.translatable("propwash.ui.settings.section.display"), left, top + this.displayTop, w);
        this.section(g, Component.translatable("propwash.ui.settings.section.template"), left, top + this.templateTop, w);
        ThemeText.wrapped(g, this.font(), this.templateText(), left, top + this.templateTextTop, this.templateTextWidth, Palette.TEXT_MUTED);
        this.extractScrollbar(g);
    }

    @Override
    public void resetDefaults() {
        this.tune().reset(TuneKeys.ARM_MAX_ANGLE);
        this.speedUnit = SpeedUnit.KMH;
        this.applyTemplate = true;
        this.host.changed();
    }

    private Component nameHint() {
        return Component.translatable(this.nameAvailable ? "propwash.ui.settings.craft_name.hint" : "propwash.ui.settings.craft_name.hint_none");
    }

    @Override
    public void save() {
        if (this.nameAvailable && !CraftNames.clean(this.craftName).equals(CraftNames.clean(this.storedName))) {
            CraftNameAccess.INSTANCE.apply(this.craftName);
        }
        UiConfig config = UiConfig.get();
        config.speedUnit = this.speedUnit;
        config.applyTemplateToNewDrones = this.applyTemplate;
        UiConfig.CONFIG.save();
    }
}
