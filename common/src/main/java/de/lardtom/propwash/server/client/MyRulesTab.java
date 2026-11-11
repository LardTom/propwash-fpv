package de.lardtom.propwash.server.client;

import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.rules.PersonalPresets;
import de.lardtom.propwash.server.rules.PersonalRulesDraft;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTiers;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.TextField;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class MyRulesTab extends UiTab {
    public static final String ID = "my_rules";
    public static final String KEY = "propwash.rules.my.";
    private static final String PRESET_KEY = "propwash.ui.preset.";
    private static final int TOOLTIP_WIDTH = 240;
    private static final long CONFIRM_MILLIS = 3000L;
    private static final int MAX_LISTED = 3;
    private static final String RESET = "↺";

    public record Row(RuleSpec spec, int y, Component label, @Nullable AbstractWidget widget, @Nullable FlatButton reset) {
        public boolean editable() {
            return this.widget != null;
        }
    }

    private final PersonalRulesDraft draft = new PersonalRulesDraft(ClientWorldRules.INSTANCE.physics(), ClientPersonalRules.INSTANCE.tiers(),
            ClientPersonalRules.INSTANCE.own());
    private final List<Row> rows = new ArrayList<>();
    private int introY;
    private int presetsY;
    private int statusY;
    private int statusHeight;
    private int rulesY;
    private int hintY;
    private int serverColumn;
    private int resetWidth;
    private boolean stacked;
    private int rowHeight = ROW;
    private int builtMask = -1;
    private boolean builtKnown;
    private String selected = PersonalPresets.RACE_LEGAL_NAME;
    private boolean userSelected;
    private String nameText = "";
    private long confirmDeleteUntil;
    private @Nullable ChoiceButton<PersonalPresets.Preset> presetChoice;
    private @Nullable FlatButton applyButton;
    private @Nullable FlatButton saveButton;
    private @Nullable FlatButton renameButton;
    private @Nullable FlatButton deleteButton;
    private @Nullable TextField nameBox;
    private Component lastFlash = Component.empty();

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
        return 805;
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
    public boolean hasDefaults() {
        return mask() != 0;
    }

    public PersonalRulesDraft draft() {
        return this.draft;
    }

    public List<Row> rows() {
        return List.copyOf(this.rows);
    }

    public Optional<Row> row(RuleSpec spec) {
        for (Row row : this.rows) {
            if (row.spec().equals(spec)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public @Nullable ChoiceButton<PersonalPresets.Preset> presetChoice() {
        return this.presetChoice;
    }

    public @Nullable FlatButton applyButton() {
        return this.applyButton;
    }

    public @Nullable FlatButton saveButton() {
        return this.saveButton;
    }

    public @Nullable FlatButton renameButton() {
        return this.renameButton;
    }

    public @Nullable FlatButton deleteButton() {
        return this.deleteButton;
    }

    public Component lastFlash() {
        return this.lastFlash;
    }

    public int left() {
        return this.x;
    }

    public int screenY(Row row) {
        return this.top() + row.y();
    }

    public void reveal(Row row) {
        ScrollPanel panel = this.scroll;
        for (int i = 0; panel != null && i < 64; i++) {
            int y = this.screenY(row);
            if (y >= this.y && y + this.rowHeight <= this.clipBottom()) {
                return;
            }
            if (!panel.scroll(y < this.y ? 1.0 : -1.0)) {
                return;
            }
            this.scrollOffset = panel.offset();
        }
    }

    public int presetsTop() {
        return this.top() + this.presetsY;
    }

    private static int mask() {
        ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
        if (!rules.known()) {
            return 0;
        }
        int mask = 0;
        for (int i = 0; i < PhysicsRules.SPECS.size(); i++) {
            if (rules.tiers().players(PhysicsRules.SPECS.get(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private void syncDraft() {
        ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
        RuleTiers tiers = rules.known() ? rules.tiers() : RuleTiers.DEFAULTS;
        this.draft.sync(ClientWorldRules.INSTANCE.physics(), tiers, rules.own());
    }

    @Override
    protected void build() {
        this.syncDraft();
        this.builtMask = mask();
        this.builtKnown = ClientPersonalRules.INSTANCE.known();
        this.rows.clear();
        this.presetChoice = null;
        this.applyButton = null;
        this.saveButton = null;
        this.renameButton = null;
        this.deleteButton = null;
        this.nameBox = null;
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        Font font = this.font();
        int cy = 0;
        this.introY = cy;
        cy += ThemeText.wrappedHeight(font, this.intro(), w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING + Metrics.GAP;
        panel.stop(cy);
        this.presetsY = cy;
        cy += Metrics.SECTION_HEIGHT;
        cy = this.buildPresets(cy, w, panel);
        this.statusY = cy;
        this.statusHeight = this.statusHeight(font, w);
        cy += this.statusHeight + Metrics.SECTION_GAP;
        panel.stop(cy);
        this.rulesY = cy;
        cy += Metrics.SECTION_HEIGHT;
        this.resetWidth = Math.max(FIELD, font.width(RESET) + 8);
        int serverWidth = 0;
        int labelWidest = 0;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            serverWidth = Math.max(serverWidth, this.widestServerText(font, spec));
            labelWidest = Math.max(labelWidest, font.width(label(spec)));
        }
        int need = 0;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            need = Math.max(need, font.width(label(spec)) + Metrics.LABEL_GAP + this.controlNeed(font, spec));
        }
        this.stacked = need + serverWidth + this.resetWidth + 2 * Metrics.GAP > w;
        this.rowHeight = this.stacked ? ROW + Metrics.LINE_HEIGHT : ROW;
        this.serverColumn = this.stacked ? w - this.resetWidth - Metrics.GAP : serverWidth;
        int rowWidth = this.stacked ? w - this.resetWidth - Metrics.GAP : w - this.serverColumn - this.resetWidth - 2 * Metrics.GAP;
        int column = RuleRowLayout.column(labelWidest, rowWidth);
        boolean first = true;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (!first) {
                panel.stop(cy - 1);
            }
            first = false;
            Component label = label(spec);
            AbstractWidget widget = null;
            FlatButton reset = null;
            if (ClientPersonalRules.INSTANCE.known() && this.draft.editable(spec)) {
                widget = this.widget(spec, label, this.x, this.top() + cy, rowWidth, column);
                widget.setTooltip(ThemeText.tip(this.rowTooltip(spec, label)));
                this.add(widget);
                reset = SearchTags.hide(new FlatButton(this.x + w - this.resetWidth, this.top() + cy, this.resetWidth, FIELD, Component.literal(RESET),
                        FlatButton.Style.NORMAL, () -> this.reset(spec)));
                reset.setTooltip(ThemeText.tip(Component.translatable(KEY + "reset.tooltip", label)));
                this.add(reset);
            }
            this.rows.add(new Row(spec, cy, label, widget, reset));
            cy += this.rowHeight;
        }
        cy += Metrics.SECTION_GAP;
        this.hintY = cy;
        panel.stop(cy);
        cy += ThemeText.wrappedHeight(font, Component.translatable(KEY + "hint"), w) + 4;
        this.endScroll(cy);
        this.refreshButtons();
    }

    private int buildPresets(int cy, int w, ScrollPanel panel) {
        Font font = this.font();
        List<PersonalPresets.Preset> choices = PersonalPresets.choices(PersonalPresetStore.all());
        if (!this.userSelected || PersonalPresets.find(choices, this.selected).isEmpty()) {
            this.selected = this.matching(choices).map(PersonalPresets.Preset::name).orElse(PersonalPresets.RACE_LEGAL_NAME);
        }
        Component apply = Component.translatable("propwash.ui.rates.apply");
        Component label = Component.translatable("propwash.ui.rates.preset");
        int applyWidth = font.width(apply) + 16;
        int valueNeed = 0;
        for (PersonalPresets.Preset preset : choices) {
            valueNeed = Math.max(valueNeed, font.width(presetName(preset)));
        }
        int labelWidth = Math.min(w / 2, font.width(label) + 8);
        boolean applyBelow = labelWidth + valueNeed + 2 * Metrics.ARROW_WIDTH + 8 + applyWidth + Metrics.GAP > w;
        int choiceWidth = applyBelow ? w : w - applyWidth - Metrics.GAP;
        this.presetChoice = this.add(new ChoiceButton<>(this.x, this.top() + cy, choiceWidth, FIELD, label, labelWidth, choices, this::selected,
                this::choose, MyRulesTab::presetName, null));
        this.presetChoice.setTooltip(ThemeText.tip(Component.translatable(KEY + "preset.tooltip")));
        if (applyBelow) {
            cy += ROW;
            panel.stop(cy);
        }
        this.applyButton = this.add(new FlatButton(this.x + w - applyWidth, this.top() + cy, applyWidth, FIELD, apply, FlatButton.Style.PRIMARY,
                this::applyPreset));
        this.applyButton.setTooltip(ThemeText.tip(Component.translatable(KEY + "preset.apply.tooltip")));
        cy += ROW;
        panel.stop(cy);
        Component save = Component.translatable(KEY + "preset.save");
        Component rename = Component.translatable(PRESET_KEY + "rename");
        Component delete = Component.translatable(PRESET_KEY + "delete");
        Component confirm = Component.translatable(PRESET_KEY + "delete.confirm");
        int saveWidth = font.width(save) + 12;
        int renameWidth = font.width(rename) + 12;
        int deleteWidth = Math.max(font.width(delete), font.width(confirm)) + 12;
        int buttons = saveWidth + renameWidth + deleteWidth + 3 * Metrics.GAP;
        int hintNeed = font.width(Component.translatable(PRESET_KEY + "name.hint")) + 12;
        boolean stacked = w - buttons < Math.max(60, hintNeed);
        int boxWidth = stacked ? w : w - buttons;
        TextField box = new TextField(font, this.x, this.top() + cy, boxWidth, FIELD, Component.translatable(PRESET_KEY + "name"));
        box.setMaxLength(PersonalPresets.MAX_NAME_LENGTH);
        box.setValue(this.nameText);
        box.hint(Component.translatable(PRESET_KEY + "name.hint"));
        box.setResponder(text -> {
            this.nameText = text;
            this.confirmDeleteUntil = 0L;
            this.refreshButtons();
        });
        this.nameBox = this.add(box);
        int bx = this.x + boxWidth + Metrics.GAP;
        if (stacked) {
            cy += ROW;
            panel.stop(cy);
            bx = this.x;
        }
        this.saveButton = this.add(new FlatButton(bx, this.top() + cy, saveWidth, FIELD, save, FlatButton.Style.NORMAL, this::savePreset));
        this.saveButton.setTooltip(ThemeText.tip(Component.translatable(KEY + "preset.save.tooltip")));
        bx += saveWidth + Metrics.GAP;
        this.renameButton = this.add(new FlatButton(bx, this.top() + cy, renameWidth, FIELD, rename, FlatButton.Style.NORMAL, this::renamePreset));
        this.renameButton.setTooltip(ThemeText.tip(Component.translatable(KEY + "preset.rename.tooltip")));
        bx += renameWidth + Metrics.GAP;
        this.deleteButton = this.add(new FlatButton(bx, this.top() + cy, deleteWidth, FIELD, delete, FlatButton.Style.DANGER, this::deletePreset));
        this.deleteButton.setTooltip(ThemeText.tip(Component.translatable(KEY + "preset.delete.tooltip")));
        cy += ROW;
        return cy;
    }

    private Optional<PersonalPresets.Preset> matching(List<PersonalPresets.Preset> choices) {
        PersonalPresets.Preset found = null;
        for (PersonalPresets.Preset preset : choices) {
            if (PersonalPresets.matches(preset, this.draft.desired(), ClientWorldRules.INSTANCE.physics(), this.draft.tiers())
                    && (found == null || !preset.raceLegal())) {
                found = preset;
            }
        }
        return Optional.ofNullable(found);
    }

    public static Component presetName(PersonalPresets.Preset preset) {
        return preset.raceLegal() ? Component.translatable(KEY + "preset.race_legal") : Component.translatable(PRESET_KEY + "own", preset.name());
    }

    private static Component plainName(PersonalPresets.Preset preset) {
        return preset.raceLegal() ? Component.translatable(KEY + "preset.race_legal") : Component.literal(preset.name());
    }

    public PersonalPresets.Preset selected() {
        List<PersonalPresets.Preset> choices = PersonalPresets.choices(PersonalPresetStore.all());
        return PersonalPresets.find(choices, this.selected).orElse(PersonalPresets.RACE_LEGAL);
    }

    public void choose(PersonalPresets.Preset preset) {
        this.selected = preset.name();
        this.userSelected = true;
        this.confirmDeleteUntil = 0L;
        if (!preset.raceLegal()) {
            this.setName(preset.name());
        }
        this.refreshButtons();
    }

    public void setName(String name) {
        this.nameText = name;
        if (this.nameBox != null) {
            this.nameBox.setValue(name);
        }
    }

    private void announce(Component message, int color) {
        this.lastFlash = message;
        this.flash(message, color);
    }

    public PersonalPresets.Applied applyPreset() {
        PersonalPresets.Preset preset = this.selected();
        PersonalPresets.Applied applied = this.draft.apply(preset);
        this.userSelected = true;
        boolean sent = ClientPersonalRules.INSTANCE.apply(applied.values());
        if (!sent) {
            this.announce(Component.translatable(KEY + "not_sent"), Palette.ERROR);
        } else if (applied.skipped().isEmpty()) {
            this.announce(Component.translatable("propwash.ui.rates.applied", plainName(preset)), Palette.SUCCESS);
        } else {
            this.announce(Component.translatable(KEY + "preset.skipped", plainName(preset), names(applied.skipped())), Palette.WARNING);
        }
        this.refreshButtons();
        return applied;
    }

    public PersonalPresets.Change savePreset() {
        List<PersonalPresets.Preset> stored = PersonalPresetStore.all();
        String name = PersonalPresets.cleanName(this.nameText);
        if (name.isEmpty()) {
            name = PersonalPresets.uniqueName(stored, Component.translatable(KEY + "preset.default_name").getString());
        }
        PersonalPresets.Change change = PersonalPresetStore.commit(PersonalPresets.save(stored, name, this.draft.capture()));
        switch (change.outcome()) {
            case SAVED, UPDATED -> {
                this.selected = change.name();
                this.userSelected = true;
                this.nameText = change.name();
                this.announce(Component.translatable(PRESET_KEY + (change.outcome() == PersonalPresets.Outcome.SAVED ? "saved" : "updated"),
                        change.name()), Palette.SUCCESS);
            }
            case RESERVED -> this.announce(Component.translatable(KEY + "preset.reserved", PersonalPresets.RACE_LEGAL_NAME), Palette.ERROR);
            case FULL -> this.announce(Component.translatable(KEY + "preset.full", PersonalPresets.MAX_PRESETS), Palette.ERROR);
            default -> this.announce(Component.translatable(PRESET_KEY + "name.missing"), Palette.WARNING);
        }
        this.requestRebuildLater();
        return change;
    }

    public PersonalPresets.Change renamePreset() {
        PersonalPresets.Preset current = this.selected();
        if (current.raceLegal()) {
            return new PersonalPresets.Change(PersonalPresets.Outcome.MISSING, PersonalPresetStore.all(), current.name());
        }
        String from = current.name();
        PersonalPresets.Change change = PersonalPresetStore.commit(PersonalPresets.rename(PersonalPresetStore.all(), from, this.nameText));
        switch (change.outcome()) {
            case RENAMED -> {
                this.selected = change.name();
                this.nameText = change.name();
                this.announce(Component.translatable(PRESET_KEY + "renamed", from, change.name()), Palette.SUCCESS);
            }
            case TAKEN -> this.announce(Component.translatable(PRESET_KEY + "taken", change.name()), Palette.ERROR);
            case RESERVED -> this.announce(Component.translatable(KEY + "preset.reserved", PersonalPresets.RACE_LEGAL_NAME), Palette.ERROR);
            case EMPTY_NAME -> this.announce(Component.translatable(PRESET_KEY + "name.missing"), Palette.WARNING);
            default -> this.announce(Component.translatable(PRESET_KEY + "missing"), Palette.ERROR);
        }
        this.requestRebuildLater();
        return change;
    }

    public PersonalPresets.Change deletePreset() {
        PersonalPresets.Preset current = this.selected();
        if (current.raceLegal()) {
            return new PersonalPresets.Change(PersonalPresets.Outcome.MISSING, PersonalPresetStore.all(), current.name());
        }
        if (this.confirmDeleteUntil == 0L) {
            this.confirmDeleteUntil = Util.getMillis() + CONFIRM_MILLIS;
            this.refreshButtons();
            return new PersonalPresets.Change(PersonalPresets.Outcome.MISSING, PersonalPresetStore.all(), current.name());
        }
        this.confirmDeleteUntil = 0L;
        PersonalPresets.Change change = PersonalPresetStore.commit(PersonalPresets.delete(PersonalPresetStore.all(), current.name()));
        if (change.outcome() == PersonalPresets.Outcome.DELETED) {
            this.selected = PersonalPresets.RACE_LEGAL_NAME;
            this.userSelected = false;
            this.setName("");
            this.announce(Component.translatable(PRESET_KEY + "deleted", current.name()), Palette.TEXT_MUTED);
        }
        this.requestRebuildLater();
        return change;
    }

    private void requestRebuildLater() {
        this.host.rebuild();
    }

    private void refreshButtons() {
        boolean own = !this.selected().raceLegal();
        if (this.renameButton != null) {
            this.renameButton.active = own;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = own;
            this.deleteButton.setMessage(Component.translatable(this.confirmDeleteUntil != 0L ? PRESET_KEY + "delete.confirm" : PRESET_KEY + "delete"));
        }
        if (this.applyButton != null) {
            this.applyButton.active = ClientPersonalRules.INSTANCE.known();
        }
        for (Row row : this.rows) {
            if (row.reset() != null) {
                row.reset().active = this.draft.deviates(row.spec());
            }
        }
    }

    public void set(RuleSpec spec, int value) {
        if (this.draft.set(spec, value)) {
            this.host.changed();
            this.refreshButtons();
        }
    }

    public void reset(RuleSpec spec) {
        if (this.draft.reset(spec)) {
            this.host.changed();
            this.refreshButtons();
        }
    }

    private static Component label(RuleSpec spec) {
        return WorldRuleText.name(Identifier.parse(spec.id()));
    }

    private int widestServerText(Font font, RuleSpec spec) {
        int widest = 0;
        List<Integer> values = new ArrayList<>(List.of(spec.min(), spec.max(), spec.defaultValue(), ClientWorldRules.INSTANCE.physics().value(spec)));
        if (spec.display() == RuleSpec.Display.LEVELS) {
            for (int v = spec.min(); v <= spec.max(); v++) {
                values.add(v);
            }
        }
        for (int value : values) {
            widest = Math.max(widest, font.width(serverText(spec, value)));
        }
        return Math.max(widest, font.width(Component.translatable(KEY + "locked")));
    }

    public static Component serverText(RuleSpec spec, int value) {
        return Component.translatable(KEY + "server", WorldRuleText.value(spec, value));
    }

    private int controlNeed(Font font, RuleSpec spec) {
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return RuleRowLayout.toggleNeed(Math.max(font.width(WorldRuleText.onOff(true)), font.width(WorldRuleText.onOff(false))));
        }
        int widest = 0;
        if (spec.display() == RuleSpec.Display.LEVELS) {
            for (int v = spec.min(); v <= spec.max(); v++) {
                widest = Math.max(widest, font.width(WorldRuleText.value(spec, v)));
            }
            return RuleRowLayout.cycleNeed(widest);
        }
        widest = Math.max(font.width(WorldRuleText.value(spec, spec.min())), font.width(WorldRuleText.value(spec, spec.max())));
        return RuleRowLayout.sliderNeed(widest);
    }

    public boolean stacked() {
        return this.stacked;
    }

    private AbstractWidget widget(RuleSpec spec, Component label, int x, int y, int width, int column) {
        Font font = this.font();
        int textWidth = font.width(label);
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            int need = RuleRowLayout.toggleNeed(Math.max(font.width(WorldRuleText.onOff(true)), font.width(WorldRuleText.onOff(false))));
            return ChoiceButton.toggle(x, y, width, FIELD, label, RuleRowLayout.labelWidth(column, textWidth, width, need),
                    () -> this.draft.value(spec) != 0, v -> this.set(spec, v ? 1 : 0), WorldRuleText::onOff, () -> this.draft.server(spec) != 0);
        }
        if (spec.display() == RuleSpec.Display.LEVELS) {
            List<Integer> values = new ArrayList<>();
            int widest = 0;
            for (int v = spec.min(); v <= spec.max(); v++) {
                values.add(v);
                widest = Math.max(widest, font.width(WorldRuleText.value(spec, v)));
            }
            int need = RuleRowLayout.cycleNeed(widest);
            return new ChoiceButton<>(x, y, width, FIELD, label, RuleRowLayout.labelWidth(column, textWidth, width, need), values,
                    () -> this.draft.value(spec), v -> this.set(spec, v), v -> WorldRuleText.value(spec, v), () -> this.draft.server(spec));
        }
        int min = spec.min();
        int max = spec.max();
        int widest = Math.max(font.width(WorldRuleText.value(spec, min)), font.width(WorldRuleText.value(spec, max)));
        int need = RuleRowLayout.sliderNeed(widest);
        return new ValueSlider(x, y, width, FIELD, label, RuleRowLayout.labelWidth(column, textWidth, width, need), min, max, spec.step(),
                () -> this.draft.value(spec),
                v -> this.set(spec, (int) Math.max(min, Math.min(max, Math.round(v)))),
                () -> this.draft.server(spec),
                v -> WorldRuleText.value(spec, (int) Math.round(v)).getString()).typed(v -> v, min, max, false);
    }

    private Component rowTooltip(RuleSpec spec, Component label) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(label.copy()));
        tip.append("\n").append(Component.translatable(spec.descriptionKey()));
        tip.append("\n").append(ThemeText.muted(Component.translatable(KEY + "row.only_you")));
        tip.append("\n").append(ThemeText.muted(Component.translatable(spec.raceRelevant() ? KEY + "row.relevant" : KEY + "row.neutral")));
        tip.append("\n").append(ThemeText.muted(Component.translatable(KEY + "row.reset")));
        return tip;
    }

    public Component lockedTooltip(RuleSpec spec) {
        Component label = label(spec);
        MutableComponent tip = Component.empty().append(ThemeText.accent(label.copy()));
        tip.append("\n").append(Component.translatable(KEY + "locked.tooltip", RuleTierSwitch.name(ClientPersonalRules.INSTANCE.tier(spec))));
        OptionalInt stored = this.draft.stored(spec);
        if (stored.isPresent()) {
            tip.append("\n").append(ThemeText.muted(Component.translatable(KEY + "locked.stored", WorldRuleText.value(spec, stored.getAsInt()))));
        }
        return tip;
    }

    private Component intro() {
        ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
        if (!rules.known()) {
            return Component.translatable(KEY + "intro.unknown");
        }
        return Component.translatable(mask() == 0 ? KEY + "intro.none" : KEY + "intro");
    }

    static MutableComponent names(List<RuleSpec> specs) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(label(specs.get(i)));
        }
        return out;
    }

    public Component status() {
        return switch (this.draft.status()) {
            case RACE_LEGAL -> Component.translatable(KEY + "status.legal");
            case NEUTRAL -> Component.translatable(KEY + "status.neutral");
            case ASSISTED -> Component.translatable(KEY + "status.assisted");
        };
    }

    private Component statusLong() {
        Component status = this.status();
        MutableComponent out = Component.empty().append(status);
        List<RuleSpec> assisting = this.draft.assisting();
        if (!assisting.isEmpty()) {
            MutableComponent list = Component.empty();
            int shown = assisting.size() > MAX_LISTED ? MAX_LISTED - 1 : assisting.size();
            for (int i = 0; i < shown; i++) {
                RuleSpec spec = assisting.get(i);
                if (i > 0) {
                    list.append(", ");
                }
                list.append(Component.translatable("propwash.race.assist.rule_value", label(spec), WorldRuleText.value(spec, this.draft.value(spec)),
                        WorldRuleText.value(spec, this.draft.server(spec))));
            }
            if (shown < assisting.size()) {
                list = Component.translatable("propwash.race_rules.list.more", list, assisting.size() - shown);
            }
            out = Component.translatable(KEY + "status.with", status, list);
        }
        return this.draft.changed() ? Component.translatable(KEY + "status.unsaved", out) : out;
    }

    private int statusHeight(Font font, int width) {
        return Math.max(Metrics.LINE_HEIGHT, ThemeText.wrappedHeight(font, this.statusLong(), width));
    }

    @Override
    public void tick() {
        this.syncDraft();
        if (mask() != this.builtMask || ClientPersonalRules.INSTANCE.known() != this.builtKnown
                || this.statusHeight(this.font(), this.innerWidth()) != this.statusHeight) {
            this.host.rebuild();
            return;
        }
        if (this.confirmDeleteUntil != 0L && Util.getMillis() > this.confirmDeleteUntil) {
            this.confirmDeleteUntil = 0L;
        }
        this.refreshButtons();
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font();
        int w = this.innerWidth();
        int top = this.top();
        Component intro = this.intro();
        boolean warn = !ClientPersonalRules.INSTANCE.known() || mask() == 0;
        int introHeight = ThemeText.wrappedHeight(font, intro, w - 2 * Metrics.PADDING) + 2 * Metrics.PADDING;
        int introTop = top + this.introY;
        ThemeDraw.notice(g, this.x, introTop, this.x + w, introTop + introHeight, warn ? Palette.WARNING : Palette.ACCENT, 1.0F);
        ThemeText.wrapped(g, font, intro, this.x + Metrics.PADDING, introTop + Metrics.PADDING, w - 2 * Metrics.PADDING,
                warn ? Palette.WARNING : Palette.TEXT_MUTED);
        this.section(g, Component.translatable(KEY + "presets"), this.x, top + this.presetsY, w);
        int statusColor = this.draft.status() == PersonalRulesDraft.Status.ASSISTED ? Palette.WARNING : Palette.SUCCESS;
        ThemeText.wrapped(g, font, this.statusLong(), this.x, top + this.statusY + 1, w, statusColor);
        this.section(g, Component.translatable(KEY + "rules"), this.x, top + this.rulesY, w);
        int clip = Math.min(this.y + this.height, this.clipBottom());
        int serverLeft = this.stacked ? this.x + Metrics.LABEL_GAP : this.x + w - this.resetWidth - Metrics.GAP - this.serverColumn;
        int lineRight = this.stacked ? this.x + w - this.resetWidth - Metrics.GAP : serverLeft - Metrics.GAP;
        Row hoveredLocked = null;
        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            int rowY = top + row.y();
            if (i % 2 == 1) {
                g.fill(this.x, rowY - 1, this.x + w, rowY + this.rowHeight - 1, Palette.ROW_STRIPE);
            }
            int textY = Metrics.textY(rowY, FIELD);
            int serverY = this.stacked ? rowY + ROW - 2 : textY;
            RuleSpec spec = row.spec();
            int server = this.draft.server(spec);
            if (row.editable()) {
                ThemeText.fitted(g, font, serverText(spec, server), serverLeft, serverY, this.serverColumn,
                        this.draft.deviates(spec) ? Palette.TEXT_MUTED : Palette.TEXT_FAINT);
                continue;
            }
            int labelWidth = font.width(row.label());
            int room = lineRight - this.x - 2 * Metrics.LABEL_GAP - labelWidth;
            Component locked = Component.translatable(KEY + "locked");
            if (font.width(locked) > room) {
                locked = Component.translatable(KEY + "locked.short");
            }
            boolean showLocked = font.width(locked) <= room;
            int labelRoom = showLocked ? labelWidth : lineRight - this.x - 2 * Metrics.LABEL_GAP;
            ThemeText.fitted(g, font, row.label(), this.x + Metrics.LABEL_GAP, textY, labelRoom, Palette.TEXT_FAINT);
            if (showLocked) {
                ThemeText.textRight(g, font, locked, lineRight, textY, Palette.TEXT_DISABLED);
            }
            ThemeText.fitted(g, font, serverText(spec, server), serverLeft, serverY, this.serverColumn, Palette.TEXT_FAINT);
            if (mouseX >= this.x && mouseX < this.x + w && mouseY >= rowY && mouseY < rowY + this.rowHeight - 1 && mouseY >= this.y && mouseY < clip) {
                hoveredLocked = row;
            }
        }
        ThemeText.wrapped(g, font, Component.translatable(KEY + "hint"), this.x, top + this.hintY, w, Palette.TEXT_FAINT);
        if (hoveredLocked != null) {
            ThemeText.tooltipWrapped(g, font, this.lockedTooltip(hoveredLocked.spec()), TOOLTIP_WIDTH, mouseX, mouseY);
        }
        this.extractScrollbar(g);
    }

    @Override
    public void resetDefaults() {
        this.draft.apply(PersonalPresets.RACE_LEGAL);
        this.host.changed();
        this.refreshButtons();
    }

    @Override
    public void save() {
        if (this.draft.changed() && ClientPersonalRules.INSTANCE.known()) {
            ClientPersonalRules.INSTANCE.apply(this.draft.desired());
        }
    }

    @Override
    public void discard() {
        this.draft.discard();
    }
}
