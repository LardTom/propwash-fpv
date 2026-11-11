package de.lardtom.propwash.server.client;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.client.RaceStandardSection;
import de.lardtom.propwash.server.WorldRuleEditC2S;
import de.lardtom.propwash.server.WorldRulePresetC2S;
import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import de.lardtom.propwash.zone.client.ClientZones;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorldRulesTab extends UiTab {
    public static final String ID = "world_rules";
    public static final String OTHER_GROUP = "propwash.server.world_rules.group.other";
    private static final int SLIDER_RANGE_LIMIT = 1000;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int PRESET_TOOLTIP_WIDTH = 250;
    private static final int TIER_TOOLTIP_WIDTH = 260;
    private static final int RACE_STANDARD_VIEW = 72;

    public record Row(Identifier id, GameRule<?> rule, @Nullable RuleSpec spec, int y, @Nullable AbstractWidget widget, Component label,
                      Component tooltip, @Nullable RuleTierSwitch tier, int column) {
    }

    private record Section(Component title, int y, boolean rules, boolean tiers) {
    }

    private final Map<Identifier, String> draft = new HashMap<>();
    private final Map<Identifier, RuleTier> tierDraft = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final RaceStandardSection raceStandard = new RaceStandardSection();
    private int raceStandardY;
    private final Map<RulePreset, FlatButton> presetButtons = new EnumMap<>(RulePreset.class);
    private int introY;
    private int presetStatusY;
    private int hintY;
    private int labelWidth;
    private int builtVersion = -1;
    private boolean builtEditable;
    private boolean builtKnown;
    private int builtZones = -1;
    private int builtTiers = -1;
    private int tierColumn;
    private boolean compactTiers;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.server.world_rules.tab");
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public Storage storage() {
        return Storage.MIXED;
    }

    @Override
    public String mixedStorageKey() {
        return "propwash.server.world_rules.footer";
    }

    @Override
    public boolean readOnly() {
        return !ClientWorldRules.INSTANCE.editable();
    }

    @Override
    public String readOnlyKey() {
        return "propwash.server.world_rules.footer_read_only";
    }

    @Override
    public boolean hasDefaults() {
        return ClientWorldRules.INSTANCE.editable();
    }

    public List<Row> rows() {
        return List.copyOf(this.rows);
    }

    public Map<Identifier, String> draft() {
        return Map.copyOf(this.draft);
    }

    public Map<Identifier, RuleTier> tierDraft() {
        return Map.copyOf(this.tierDraft);
    }

    public Optional<Row> row(Identifier id) {
        for (Row row : this.rows) {
            if (row.id().equals(id)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public RaceStandardSection raceStandard() {
        return this.raceStandard;
    }

    public int raceStandardTop() {
        return this.top() + this.raceStandardY;
    }

    public void revealRaceStandard() {
        ScrollPanel panel = this.scroll;
        for (int i = 0; panel != null && i < 64; i++) {
            int y = this.raceStandardTop();
            if (y >= this.y && y + RACE_STANDARD_VIEW <= this.clipBottom()) {
                return;
            }
            if (!panel.scroll(y < this.y ? 1.0 : -1.0)) {
                return;
            }
            this.scrollOffset = panel.offset();
        }
    }

    public Optional<FlatButton> presetButton(RulePreset preset) {
        return Optional.ofNullable(this.presetButtons.get(preset));
    }

    @Override
    protected void build() {
        ClientWorldRules synced = ClientWorldRules.INSTANCE;
        this.builtVersion = synced.version();
        this.builtEditable = synced.editable();
        this.builtKnown = synced.known();
        this.builtZones = ClientZones.INSTANCE.version();
        this.builtTiers = ClientPersonalRules.INSTANCE.version();
        this.rows.clear();
        this.sections.clear();
        this.presetButtons.clear();
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        Font font = this.font();
        int cy = 0;
        this.introY = cy;
        cy += ThemeText.wrappedHeight(font, this.intro(), w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING + Metrics.GAP;
        Map<String, List<GameRule<?>>> groups = groups();
        int narrow = RuleTierSwitch.column(font, true);
        int full = RuleTierSwitch.column(font, false);
        Map<String, Integer> columns = new HashMap<>();
        int widest = narrow;
        this.compactTiers = false;
        for (Map.Entry<String, List<GameRule<?>>> group : groups.entrySet()) {
            boolean tiers = group.getValue().stream().anyMatch(WorldRulesTab::tierable);
            boolean compact = tiers && this.controlNeed(group.getValue()) + full > w;
            this.compactTiers |= compact;
            int column = tiers && !compact ? full : narrow;
            columns.put(group.getKey(), column);
            widest = Math.max(widest, column);
        }
        this.tierColumn = widest;
        int labelWidth = this.labelWidth(groups, w - widest);
        this.labelWidth = labelWidth;
        boolean editable = synced.editable();
        panel.stop(cy);
        this.sections.add(new Section(Component.translatable("propwash.server.world_rules.presets"), cy, false, false));
        cy += Metrics.SECTION_HEIGHT;
        if (editable) {
            RulePreset[] presets = RulePreset.values();
            int buttonWidth = (w - (presets.length - 1) * Metrics.GAP) / presets.length;
            for (int i = 0; i < presets.length; i++) {
                RulePreset preset = presets[i];
                int bx = this.x + i * (buttonWidth + Metrics.GAP);
                int bw = i == presets.length - 1 ? w - i * (buttonWidth + Metrics.GAP) : buttonWidth;
                FlatButton button = this.add(new FlatButton(bx, this.top() + cy, bw, FIELD, Component.translatable(preset.nameKey()),
                        FlatButton.Style.NORMAL, () -> this.matchingPreset().orElse(null) == preset, () -> this.applyPreset(preset)));
                this.presetButtons.put(preset, button);
            }
            cy += ROW;
        }
        this.presetStatusY = cy;
        cy += Metrics.LINE_HEIGHT + Metrics.SECTION_GAP;
        panel.stop(cy);
        this.raceStandardY = cy;
        this.sections.add(new Section(RaceStandardSection.title(), cy, false, false));
        cy += Metrics.SECTION_HEIGHT;
        cy = this.raceStandard.build(cy, this.x, this.top(), w, editable, font, widget -> this.add(widget), panel::stop);
        for (Map.Entry<String, List<GameRule<?>>> group : groups.entrySet()) {
            panel.stop(cy);
            boolean tiers = group.getValue().stream().anyMatch(WorldRulesTab::tierable);
            int tierColumn = columns.getOrDefault(group.getKey(), narrow);
            boolean compact = tiers && tierColumn == narrow;
            this.sections.add(new Section(Component.translatable(group.getKey()), cy, true, tiers));
            cy += Metrics.SECTION_HEIGHT;
            boolean first = true;
            for (GameRule<?> rule : group.getValue()) {
                Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
                if (id == null) {
                    continue;
                }
                if (!first) {
                    panel.stop(cy - 1);
                }
                first = false;
                RuleSpec spec = WorldRuleText.spec(id).orElse(null);
                Component label = WorldRuleText.name(id);
                Component tooltip = this.tooltip(id, rule, spec, label, editable);
                AbstractWidget widget = null;
                if (editable) {
                    widget = this.widget(id, rule, spec, label, this.x, this.top() + cy, w - tierColumn, labelWidth);
                    widget.setTooltip(ThemeText.tip(tooltip));
                    this.add(widget);
                }
                RuleTierSwitch tier = null;
                if (spec != null && RuleTiers.tierable(spec)) {
                    tier = this.tierSwitch(spec, label, this.x + w - tierColumn + Metrics.GAP, this.top() + cy, tierColumn - Metrics.GAP, editable, compact);
                    this.add(tier);
                }
                this.rows.add(new Row(id, rule, spec, cy, widget, label, tooltip, tier, tierColumn));
                cy += ROW;
            }
            cy += Metrics.SECTION_GAP;
        }
        this.hintY = cy;
        panel.stop(cy);
        cy += ThemeText.wrappedHeight(font, Component.translatable("propwash.server.world_rules.command_hint"), w) + 4;
        this.endScroll(cy);
    }

    private static boolean tierable(GameRule<?> rule) {
        Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
        return id != null && WorldRuleText.spec(id).filter(RuleTiers::tierable).isPresent();
    }

    private RuleTierSwitch tierSwitch(RuleSpec spec, Component label, int x, int y, int width, boolean editable, boolean compact) {
        RuleTierSwitch tier = new RuleTierSwitch(x, y, width, FIELD, Component.translatable(RuleTierSwitch.KEY + "narration", label), editable, compact,
                () -> this.tier(spec), value -> this.setTier(spec, value));
        return SearchTags.hide(tier);
    }

    public static RuleTier synced(RuleSpec spec) {
        RuleTier tier = ClientPersonalRules.INSTANCE.tier(spec);
        if (tier.zones() && !ClientZones.INSTANCE.release().released(spec)) {
            return RuleTier.FIXED;
        }
        return tier;
    }

    public RuleTier tier(RuleSpec spec) {
        RuleTier pending = this.tierDraft.get(Identifier.parse(spec.id()));
        return pending != null ? pending : synced(spec);
    }

    public void setTier(RuleSpec spec, RuleTier tier) {
        if (!ClientWorldRules.INSTANCE.editable() || !RuleTiers.tierable(spec)) {
            return;
        }
        Identifier id = Identifier.parse(spec.id());
        if (tier == synced(spec)) {
            this.tierDraft.remove(id);
        } else {
            this.tierDraft.put(id, tier);
        }
        this.host.changed();
    }

    private static Map<String, List<GameRule<?>>> groups() {
        Map<String, List<GameRule<?>>> groups = new LinkedHashMap<>();
        for (RuleSpec.Group group : RuleSpec.Group.values()) {
            groups.put(group.key(), new ArrayList<>());
        }
        groups.put(OTHER_GROUP, new ArrayList<>());
        for (GameRule<?> rule : WorldRules.propwashRules()) {
            Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
            String key = id == null ? OTHER_GROUP : WorldRuleText.spec(id).map(spec -> spec.group().key()).orElse(OTHER_GROUP);
            groups.get(key).add(rule);
        }
        AddonRuleSections.split(groups);
        groups.values().removeIf(List::isEmpty);
        return groups;
    }

    public boolean compactTiers() {
        return this.compactTiers;
    }

    private int controlNeed(List<GameRule<?>> rules) {
        Font font = this.font();
        int need = 0;
        for (GameRule<?> rule : rules) {
            Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
            if (id == null) {
                continue;
            }
            RuleSpec spec = WorldRuleText.spec(id).orElse(null);
            int control;
            if (rule.gameRuleType() == GameRuleType.BOOL) {
                control = RuleRowLayout.toggleNeed(Math.max(font.width(WorldRuleText.onOff(true)), font.width(WorldRuleText.onOff(false))));
            } else if (spec != null && spec.display() == RuleSpec.Display.LEVELS) {
                int widest = 0;
                for (int v = spec.min(); v <= spec.max(); v++) {
                    widest = Math.max(widest, font.width(WorldRuleText.value(spec, v)));
                }
                control = RuleRowLayout.cycleNeed(widest);
            } else {
                int min = spec != null ? spec.min() : bound(rule.argument(), true);
                int max = spec != null ? spec.max() : bound(rule.argument(), false);
                int widest = Math.max(font.width(this.format(id, spec, min)), font.width(this.format(id, spec, max)));
                control = (long) max - min <= SLIDER_RANGE_LIMIT ? RuleRowLayout.sliderNeed(widest) : RuleRowLayout.spinnerNeed(widest) + Metrics.GAP;
            }
            need = Math.max(need, font.width(WorldRuleText.name(id)) + Metrics.LABEL_GAP + control);
        }
        return need;
    }

    private int labelWidth(Map<String, List<GameRule<?>>> groups, int width) {
        int widest = 0;
        for (List<GameRule<?>> rules : groups.values()) {
            for (GameRule<?> rule : rules) {
                Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
                if (id != null) {
                    widest = Math.max(widest, this.font().width(WorldRuleText.name(id)));
                }
            }
        }
        return RuleRowLayout.column(widest, width);
    }

    public RuleValues displayedValues() {
        Map<String, Integer> out = new HashMap<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            Identifier id = Identifier.parse(spec.id());
            String pending = this.draft.get(id);
            int value = ClientWorldRules.INSTANCE.get(spec);
            if (pending != null) {
                try {
                    value = spec.parse(pending);
                } catch (IllegalArgumentException ignored) {
                    value = ClientWorldRules.INSTANCE.get(spec);
                }
            }
            out.put(spec.name(), value);
        }
        return new RuleValues(out);
    }

    public Optional<RulePreset> matchingPreset() {
        return RulePreset.match(this.displayedValues());
    }

    public void applyPreset(RulePreset preset) {
        if (!ClientWorldRules.INSTANCE.editable()) {
            return;
        }
        ClientNet.sendToServer(new WorldRulePresetC2S(preset));
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            this.draft.remove(Identifier.parse(spec.id()));
        }
        this.flash(Component.translatable("propwash.server.world_rules.preset.applying", Component.translatable(preset.nameKey())),
                Palette.TEXT_MUTED);
    }

    private Component presetStatus() {
        Optional<RulePreset> preset = this.matchingPreset();
        Component status = preset.<Component>map(p -> Component.translatable("propwash.server.world_rules.preset.current",
                        ThemeText.accent(Component.translatable(p.nameKey()))))
                .orElseGet(() -> Component.translatable("propwash.server.world_rules.preset.custom"));
        return this.draft.isEmpty() ? status : Component.translatable("propwash.server.world_rules.preset.unsaved", status);
    }

    public static Component presetTooltip(RulePreset preset) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(Component.translatable(preset.nameKey())));
        tip.append("\n").append(Component.translatable(preset.descriptionKey()));
        tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.server.world_rules.preset.instant")));
        List<RuleSpec> differences = preset.differences();
        if (differences.isEmpty()) {
            tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.server.world_rules.preset.all_default")));
            return tip;
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < differences.size(); i++) {
            RuleSpec spec = differences.get(i);
            if (i > 0) {
                list.append(" · ");
            }
            list.append(Component.translatable("propwash.server.world_rules.preset.line", Component.translatable(spec.translationKey()),
                    WorldRuleText.value(spec, preset.value(spec))));
        }
        tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.server.world_rules.preset.differences", list)));
        return tip;
    }

    private Component intro() {
        ClientWorldRules synced = ClientWorldRules.INSTANCE;
        if (!synced.known()) {
            return Component.translatable("propwash.server.world_rules.unknown");
        }
        return Component.translatable(synced.editable() ? "propwash.rules.tab.intro" : "propwash.server.world_rules.read_only");
    }

    private AbstractWidget widget(Identifier id, GameRule<?> rule, @Nullable RuleSpec spec, Component label, int x, int y, int width,
                                  int column) {
        Font font = this.font();
        int textWidth = font.width(label);
        if (rule.gameRuleType() == GameRuleType.BOOL) {
            int need = RuleRowLayout.toggleNeed(Math.max(font.width(WorldRuleText.onOff(true)), font.width(WorldRuleText.onOff(false))));
            return ChoiceButton.toggle(x, y, width, FIELD, label, RuleRowLayout.labelWidth(column, textWidth, width, need),
                    () -> Boolean.parseBoolean(this.current(id, rule)),
                    v -> this.set(id, rule, Boolean.toString(v)),
                    WorldRuleText::onOff,
                    () -> Boolean.parseBoolean(defaultValue(rule)));
        }
        if (spec != null && spec.display() == RuleSpec.Display.LEVELS) {
            List<Integer> values = new ArrayList<>();
            int widest = 0;
            for (int v = spec.min(); v <= spec.max(); v++) {
                values.add(v);
                widest = Math.max(widest, font.width(WorldRuleText.value(spec, v)));
            }
            int need = RuleRowLayout.cycleNeed(widest);
            return new ChoiceButton<>(x, y, width, FIELD, label, RuleRowLayout.labelWidth(column, textWidth, width, need), values,
                    () -> this.intValue(id, rule, spec.defaultValue()),
                    v -> this.set(id, rule, Integer.toString(v)),
                    v -> WorldRuleText.value(spec, v),
                    spec::defaultValue);
        }
        int min = spec != null ? spec.min() : bound(rule.argument(), true);
        int max = spec != null ? spec.max() : bound(rule.argument(), false);
        int step = spec != null ? spec.step() : 1;
        int fallback = parseInt(defaultValue(rule), 0);
        int widest = Math.max(font.width(this.format(id, spec, min)), font.width(this.format(id, spec, max)));
        if ((long) max - min <= SLIDER_RANGE_LIMIT) {
            int need = RuleRowLayout.sliderNeed(widest);
            return new ValueSlider(x, y, width, FIELD, label, RuleRowLayout.labelWidth(column, textWidth, width, need), min, max, step,
                    () -> this.intValue(id, rule, fallback),
                    v -> this.set(id, rule, Integer.toString((int) Math.max(min, Math.min(max, Math.round(v))))),
                    () -> fallback,
                    v -> this.format(id, spec, (int) Math.round(v))).typed(v -> v, min, max, false);
        }
        int spinnerWidth = RuleRowLayout.spinnerWidth(textWidth, width, RuleRowLayout.spinnerNeed(widest));
        return new NumberSpinner(x + width - spinnerWidth, y, spinnerWidth, FIELD, label,
                () -> this.intValue(id, rule, fallback),
                n -> this.set(id, rule, Integer.toString((int) Math.max(min, Math.min(max, (long) this.intValue(id, rule, fallback) + n)))),
                v -> this.set(id, rule, Integer.toString((int) Math.max(min, Math.min(max, Math.round(v))))),
                () -> fallback,
                v -> this.format(id, spec, (int) Math.round(v)),
                v -> v,
                0.5).range(min, max).integers();
    }

    private Component valueText(Row row) {
        return WorldRuleText.value(row.id(), this.current(row.id(), row.rule()));
    }

    public int screenY(Row row) {
        return this.top() + row.y();
    }

    public int left() {
        return this.x;
    }

    public void reveal(Row row) {
        ScrollPanel panel = this.scroll;
        for (int i = 0; panel != null && i < 64; i++) {
            int y = this.screenY(row);
            if (y >= this.y && y + ROW <= this.clipBottom()) {
                return;
            }
            if (!panel.scroll(y < this.y ? 1.0 : -1.0)) {
                return;
            }
            this.scrollOffset = panel.offset();
        }
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

    private String format(Identifier id, @Nullable RuleSpec spec, int value) {
        return spec != null ? WorldRuleText.value(spec, value).getString() : WorldRuleText.value(id, Integer.toString(value)).getString();
    }

    private Component tooltip(Identifier id, GameRule<?> rule, @Nullable RuleSpec spec, Component label, boolean editable) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(label.copy()));
        String descriptionKey = WorldRuleText.nameKey(id) + ".description";
        tip.append("\n").append(Component.translatable(descriptionKey));
        Component defaultText = spec != null ? WorldRuleText.value(spec, spec.defaultValue()) : WorldRuleText.value(id, defaultValue(rule));
        MutableComponent facts = Component.translatable("propwash.server.world_rules.tooltip.default", defaultText);
        if (spec != null) {
            facts = Component.translatable("propwash.server.world_rules.tooltip.facts", defaultText, WorldRuleText.range(spec));
        }
        tip.append("\n").append(ThemeText.muted(facts));
        tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.server.world_rules.tooltip.command", id.toString())));
        Component racing = RaceStandardSection.ruleNote(id);
        if (racing != null) {
            tip.append("\n").append(ThemeText.muted(racing));
        }
        if (editable) {
            tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.ui.hint.reset")));
        }
        return tip;
    }

    private static int bound(ArgumentType<?> argument, boolean minimum) {
        if (argument instanceof IntegerArgumentType integer) {
            return minimum ? integer.getMinimum() : integer.getMaximum();
        }
        return minimum ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <T> String defaultValue(GameRule<T> rule) {
        return rule.serialize(rule.defaultValue());
    }

    private String synced(Identifier id, GameRule<?> rule) {
        return ClientWorldRules.INSTANCE.raw(id).orElseGet(() -> defaultValue(rule));
    }

    public String current(Identifier id, GameRule<?> rule) {
        String pending = this.draft.get(id);
        return pending != null ? pending : this.synced(id, rule);
    }

    private int intValue(Identifier id, GameRule<?> rule, int fallback) {
        return parseInt(this.current(id, rule), fallback);
    }

    public void set(Identifier id, GameRule<?> rule, String value) {
        if (!ClientWorldRules.INSTANCE.editable() || rule.deserialize(value).result().isEmpty()) {
            return;
        }
        if (value.equals(this.synced(id, rule))) {
            this.draft.remove(id);
        } else {
            this.draft.put(id, value);
        }
        this.host.changed();
    }

    @Override
    public void tick() {
        ClientWorldRules synced = ClientWorldRules.INSTANCE;
        if (ClientZones.INSTANCE.version() != this.builtZones || ClientPersonalRules.INSTANCE.version() != this.builtTiers) {
            this.builtZones = ClientZones.INSTANCE.version();
            this.builtTiers = ClientPersonalRules.INSTANCE.version();
            this.dropSyncedTiers();
        }
        if (synced.editable() != this.builtEditable || synced.known() != this.builtKnown || this.raceStandard.stale()) {
            if (!synced.editable()) {
                this.draft.clear();
                this.tierDraft.clear();
            }
            this.dropSyncedTiers();
            this.draft.entrySet().removeIf(entry -> entry.getValue().equals(synced.raw(entry.getKey()).orElse(null)));
            this.host.rebuild();
        } else if (synced.version() != this.builtVersion) {
            this.builtVersion = synced.version();
            this.draft.entrySet().removeIf(entry -> entry.getValue().equals(synced.raw(entry.getKey()).orElse(null)));
        }
    }

    private void dropSyncedTiers() {
        this.tierDraft.entrySet().removeIf(entry -> WorldRuleText.spec(entry.getKey()).map(spec -> synced(spec) == entry.getValue()).orElse(true));
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font();
        int w = this.innerWidth();
        int top = this.top();
        Component intro = this.intro();
        boolean warn = !ClientWorldRules.INSTANCE.editable();
        int introHeight = ThemeText.wrappedHeight(font, intro, w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING;
        int introTop = top + this.introY;
        ThemeDraw.notice(g, this.x, introTop, this.x + w, introTop + introHeight, warn ? Palette.WARNING : Palette.ACCENT, 1.0F);
        ThemeText.wrapped(g, font, intro, this.x + Metrics.PADDING, introTop + Metrics.PADDING, w - 2 * Metrics.PADDING,
                warn ? Palette.WARNING : Palette.TEXT_MUTED);
        Component column = Component.translatable(RuleTierSwitch.KEY + "column");
        int columnWidth = font.width(column);
        for (Section section : this.sections) {
            int width = w;
            if (section.tiers()) {
                width = w - columnWidth - Metrics.GAP;
                ThemeText.textRight(g, font, column, this.x + w, top + section.y(), Palette.TEXT_FAINT);
            }
            this.section(g, section.title(), this.x, top + section.y(), width);
        }
        ThemeText.line(g, font, this.presetStatus(), this.x, this.x + w, top + this.presetStatusY + 1, Palette.TEXT_MUTED, ThemeText.Align.LEFT,
                false);
        Row hovered = null;
        int clip = Math.min(this.y + this.height, this.clipBottom());
        int valueColumn = this.valueColumn(font, w - this.tierColumn);
        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            int rowY = top + row.y();
            if (i % 2 == 1) {
                g.fill(this.x, rowY - 1, this.x + w, rowY + ROW - 1, Palette.ROW_STRIPE);
            }
            if (row.widget() instanceof NumberSpinner spinner) {
                int labelWidth = spinner.getX() - this.x - Metrics.GAP;
                ThemedWidgets.label(g, font, row.label(), this.x, rowY, FIELD, labelWidth, WidgetState.of(spinner));
            }
            if (row.widget() == null) {
                int rowWidth = w - row.column();
                int textY = Metrics.textY(rowY, FIELD);
                Component value = this.valueText(row);
                int valueX = RuleRowLayout.valueX(valueColumn, font.width(row.label()), font.width(value), rowWidth);
                ThemeText.fitted(g, font, row.label(), this.x + Metrics.LABEL_GAP, textY, valueX - 2 * Metrics.LABEL_GAP, Palette.TEXT_MUTED);
                ThemeText.fitted(g, font, value, this.x + valueX, textY, rowWidth - valueX - Metrics.LABEL_GAP, Palette.TEXT);
                if (mouseX >= this.x && mouseX < this.x + rowWidth && mouseY >= rowY && mouseY < rowY + ROW - 1 && mouseY >= this.y && mouseY < clip) {
                    hovered = row;
                }
            }
        }
        Component raceTip = this.raceStandard.extract(g, font, this.x, top, w, mouseX, mouseY, this.y, clip);
        Row tierHovered = null;
        for (Row row : this.rows) {
            RuleTierSwitch tier = row.tier();
            if (tier != null && row.spec() != null && tier.visible && tier.isHovered() && mouseY >= this.y && mouseY < clip) {
                tierHovered = row;
            }
        }
        if (tierHovered != null && tierHovered.tier() != null && tierHovered.spec() != null) {
            ThemeText.tooltipWrapped(g, font, RuleTierSwitch.tooltip(tierHovered.label(), this.tier(tierHovered.spec()), ClientWorldRules.INSTANCE.editable()),
                    TIER_TOOLTIP_WIDTH, mouseX, mouseY);
        } else if (hovered != null) {
            ThemeText.tooltipWrapped(g, font, hovered.tooltip(), TOOLTIP_WIDTH, mouseX, mouseY);
        } else if (raceTip != null) {
            ThemeText.tooltipWrapped(g, font, raceTip, RaceStandardSection.TOOLTIP_WIDTH, mouseX, mouseY);
        }
        for (Map.Entry<RulePreset, FlatButton> entry : this.presetButtons.entrySet()) {
            FlatButton button = entry.getValue();
            if (button.visible && button.isHovered() && mouseY >= this.y && mouseY < clip) {
                ThemeText.tooltipWrapped(g, font, presetTooltip(entry.getKey()), PRESET_TOOLTIP_WIDTH, mouseX, mouseY);
            }
        }
        ThemeText.wrapped(g, font, Component.translatable("propwash.server.world_rules.command_hint"), this.x, top + this.hintY, w,
                Palette.TEXT_FAINT);
        this.extractScrollbar(g);
    }

    private int valueColumn(Font font, int width) {
        int label = 0;
        int value = 0;
        for (Row row : this.rows) {
            if (row.widget() == null) {
                label = Math.max(label, font.width(row.label()));
                value = Math.max(value, font.width(this.valueText(row)));
            }
        }
        return RuleRowLayout.valueColumn(this.labelWidth, label, value, width);
    }

    @Override
    public void resetDefaults() {
        if (!ClientWorldRules.INSTANCE.editable()) {
            return;
        }
        for (Row row : this.rows) {
            this.set(row.id(), row.rule(), defaultValue(row.rule()));
            if (row.spec() != null && row.tier() != null) {
                this.setTier(row.spec(), RuleTier.DEFAULT);
            }
        }
    }

    @Override
    public void save() {
        if (this.draft.isEmpty() && this.tierDraft.isEmpty()) {
            return;
        }
        if (!ClientWorldRules.INSTANCE.editable()) {
            this.draft.clear();
            this.tierDraft.clear();
            return;
        }
        if (!this.tierDraft.isEmpty()) {
            for (Map.Entry<Identifier, RuleTier> entry : new LinkedHashMap<>(this.tierDraft).entrySet()) {
                WorldRuleText.spec(entry.getKey()).ifPresent(spec -> ClientPersonalRules.INSTANCE.setTier(spec, entry.getValue()));
            }
            this.tierDraft.clear();
        }
        List<WorldRulesS2C.Entry> entries = new ArrayList<>();
        for (Map.Entry<Identifier, String> entry : this.draft.entrySet()) {
            entries.add(new WorldRulesS2C.Entry(entry.getKey(), entry.getValue()));
        }
        for (int from = 0; from < entries.size(); from += WorldRuleEditC2S.MAX_ENTRIES) {
            int to = Math.min(entries.size(), from + WorldRuleEditC2S.MAX_ENTRIES);
            ClientNet.sendToServer(new WorldRuleEditC2S(entries.subList(from, to)));
        }
        this.draft.clear();
    }

    @Override
    public void discard() {
        this.draft.clear();
        this.tierDraft.clear();
    }

    public @Nullable AbstractWidget focusRule(String name) {
        for (Row row : this.rows) {
            if (row.id().getPath().equals(name)) {
                this.reveal(row);
                return row.widget();
            }
        }
        return null;
    }
}
