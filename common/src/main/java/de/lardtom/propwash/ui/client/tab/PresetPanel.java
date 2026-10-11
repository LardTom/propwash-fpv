package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.ui.RateTemplate;
import de.lardtom.propwash.ui.TunePresetBook;
import de.lardtom.propwash.ui.TunePresets;
import de.lardtom.propwash.ui.client.TuneModel;
import de.lardtom.propwash.ui.client.TunePresetStore;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PresetPanel {
    private static final String KEY = "propwash.ui.preset.";
    private static final long CONFIRM_MILLIS = 3000L;
    private static final String BUILTIN = "builtin:";
    private static final String OWN = "own:";

    public record Choice(String id, Component name, boolean own, Map<String, Float> values, @Nullable RateTemplate template) {
    }

    private final UiTab tab;
    private final TunePresets.Section section;
    private String selectedId = "";
    private String nameText = "";
    private long confirmDeleteUntil;
    private boolean userSelected;
    private List<Choice> choices = List.of();
    private @Nullable FlatButton renameButton;
    private @Nullable FlatButton deleteButton;
    private @Nullable TextField nameBox;

    public PresetPanel(UiTab tab, TunePresets.Section section) {
        this.tab = tab;
        this.section = section;
    }

    public TunePresets.Section section() {
        return this.section;
    }

    public List<Choice> choices() {
        return this.choices;
    }

    public @Nullable Choice selected() {
        for (Choice choice : this.choices) {
            if (choice.id().equals(this.selectedId)) {
                return choice;
            }
        }
        return this.choices.isEmpty() ? null : this.choices.getFirst();
    }

    public String nameText() {
        return this.nameText;
    }

    public void setName(String name) {
        this.nameText = name;
        if (this.nameBox != null) {
            this.nameBox.setValue(name);
        }
    }

    public boolean select(String id) {
        for (Choice choice : this.choices) {
            if (choice.id().equals(id)) {
                this.choose(choice);
                return true;
            }
        }
        return false;
    }

    private List<Choice> buildChoices() {
        List<Choice> out = new ArrayList<>();
        if (this.section == TunePresets.Section.RATES) {
            for (RateTemplate template : RateTemplate.values()) {
                out.add(new Choice(BUILTIN + template.name(), Component.translatable(template.translationKey()), false,
                        template.tuneValues(), template));
            }
        } else {
            out.add(new Choice(BUILTIN + "standard", Component.translatable(KEY + "pid.standard"), false, Map.of(), null));
        }
        for (TunePresets.Preset preset : TunePresetStore.all(this.section)) {
            out.add(new Choice(OWN + preset.name(), Component.translatable(KEY + "own", preset.name()), true, preset.values(), null));
        }
        return out;
    }

    private boolean matches(Choice choice) {
        TuneModel tune = this.tab.tune();
        if (choice.template() != null) {
            return choice.template().matches(tune.working(), tune.defaults());
        }
        return TunePresets.matches(this.section, tune.working(), tune.defaults(), choice.values());
    }

    private void choose(Choice choice) {
        this.selectedId = choice.id();
        this.userSelected = true;
        this.confirmDeleteUntil = 0L;
        if (choice.own()) {
            this.setName(choice.id().substring(OWN.length()));
        }
        this.updateButtons();
    }

    public int build(int left, int contentY, int width, int labelWidth) {
        this.choices = this.buildChoices();
        boolean known = false;
        for (Choice choice : this.choices) {
            known |= choice.id().equals(this.selectedId);
        }
        if (!this.userSelected || !known) {
            String matching = null;
            for (Choice choice : this.choices) {
                if (this.matches(choice) && (matching == null || choice.own())) {
                    matching = choice.id();
                }
            }
            if (matching != null) {
                this.selectedId = matching;
            } else if (!known) {
                this.selectedId = this.section == TunePresets.Section.RATES ? BUILTIN + RateTemplate.FREESTYLE.name() : this.choices.getFirst().id();
            }
        }
        int cy = contentY;
        Component apply = Component.translatable("propwash.ui.rates.apply");
        int applyWidth = this.tab.font().width(apply) + 16;
        List<Choice> values = this.choices;
        int valueNeed = 0;
        for (Choice choice : values) {
            valueNeed = Math.max(valueNeed, this.tab.font().width(choice.name()));
        }
        boolean applyBelow = labelWidth + valueNeed + 2 * Metrics.ARROW_WIDTH + 8 + applyWidth + Metrics.GAP > width;
        ChoiceButton<Choice> presets = this.tab.add(new ChoiceButton<>(left, this.tab.top() + cy, applyBelow ? width : width - applyWidth - Metrics.GAP, UiTab.FIELD,
                Component.translatable("propwash.ui.rates.preset"), labelWidth, values, () -> {
                    Choice current = this.selected();
                    return current != null ? current : values.getFirst();
                }, this::choose, Choice::name, null));
        presets.setTooltip(Tooltip.create(Component.translatable(this.section == TunePresets.Section.RATES ? "propwash.ui.rates.preset.tip" : KEY + "pid.choice.tip")));
        if (applyBelow) {
            cy += UiTab.ROW;
        }
        this.tab.add(new FlatButton(left + width - applyWidth, this.tab.top() + cy, applyWidth, UiTab.FIELD, apply, FlatButton.Style.PRIMARY,
                this::apply)).setTooltip(Tooltip.create(Component.translatable(KEY + "apply.tip")));
        cy += UiTab.ROW;

        Component save = Component.translatable(KEY + "save");
        Component rename = Component.translatable(KEY + "rename");
        Component delete = Component.translatable(KEY + "delete");
        Component confirm = Component.translatable(KEY + "delete.confirm");
        int saveWidth = this.tab.font().width(save) + 12;
        int renameWidth = this.tab.font().width(rename) + 12;
        int deleteWidth = Math.max(this.tab.font().width(delete), this.tab.font().width(confirm)) + 12;
        int buttons = saveWidth + renameWidth + deleteWidth + 3 * Metrics.GAP;
        int hintNeed = this.tab.font().width(Component.translatable(KEY + "name.hint")) + 12;
        boolean stacked = width - buttons < Math.max(60, hintNeed);
        int boxWidth = stacked ? width : width - buttons;
        TextField box = new TextField(this.tab.font(), left, this.tab.top() + cy, boxWidth, UiTab.FIELD, Component.translatable(KEY + "name"));
        box.setMaxLength(TunePresets.MAX_NAME_LENGTH);
        box.setValue(this.nameText);
        box.hint(Component.translatable(KEY + "name.hint"));
        box.setResponder(text -> {
            this.nameText = text;
            this.confirmDeleteUntil = 0L;
            this.updateButtons();
        });
        this.nameBox = this.tab.add(box);
        int bx = left + boxWidth + Metrics.GAP;
        if (stacked) {
            cy += UiTab.ROW;
            bx = left;
        }
        FlatButton saveButton = this.tab.add(new FlatButton(bx, this.tab.top() + cy, saveWidth, UiTab.FIELD, save, FlatButton.Style.NORMAL, this::save));
        saveButton.setTooltip(Tooltip.create(Component.translatable(KEY + this.section.id() + ".save.tip")));
        bx += saveWidth + Metrics.GAP;
        this.renameButton = this.tab.add(new FlatButton(bx, this.tab.top() + cy, renameWidth, UiTab.FIELD, rename, FlatButton.Style.NORMAL, this::rename));
        this.renameButton.setTooltip(Tooltip.create(Component.translatable(KEY + "rename.tip")));
        bx += renameWidth + Metrics.GAP;
        this.deleteButton = this.tab.add(new FlatButton(bx, this.tab.top() + cy, deleteWidth, UiTab.FIELD, delete, FlatButton.Style.DANGER, this::delete));
        this.deleteButton.setTooltip(Tooltip.create(Component.translatable(KEY + "delete.tip")));
        cy += UiTab.ROW;

        int half = (width - Metrics.GAP) / 2;
        this.tab.add(new FlatButton(left, this.tab.top() + cy, half, UiTab.FIELD, Component.translatable(KEY + "copy"), FlatButton.Style.NORMAL,
                this::copyCode)).setTooltip(Tooltip.create(Component.translatable(KEY + "copy.tip")));
        this.tab.add(new FlatButton(left + half + Metrics.GAP, this.tab.top() + cy, width - half - Metrics.GAP, UiTab.FIELD,
                Component.translatable(KEY + "paste"), FlatButton.Style.NORMAL, this::pasteCode))
                .setTooltip(Tooltip.create(Component.translatable(KEY + "paste.tip")));
        cy += UiTab.ROW;
        this.updateButtons();
        return cy;
    }

    public void tick() {
        if (this.confirmDeleteUntil != 0L && Util.getMillis() > this.confirmDeleteUntil) {
            this.confirmDeleteUntil = 0L;
            this.updateButtons();
        }
    }

    private void updateButtons() {
        Choice current = this.selected();
        boolean own = current != null && current.own();
        if (this.renameButton != null) {
            this.renameButton.active = own;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = own;
            this.deleteButton.setMessage(Component.translatable(this.confirmDeleteUntil != 0L ? KEY + "delete.confirm" : KEY + "delete"));
        }
    }

    public void apply() {
        Choice choice = this.selected();
        if (choice == null) {
            return;
        }
        TuneModel tune = this.tab.tune();
        if (choice.template() != null) {
            RateTemplate template = choice.template();
            tune.update(t -> template.apply(t, tune.defaults()), false);
        } else {
            tune.update(t -> TunePresets.apply(this.section, t, tune.defaults(), choice.values()), this.section == TunePresets.Section.PID);
        }
        this.userSelected = true;
        this.tab.flash(Component.translatable("propwash.ui.rates.applied", choice.name()), Palette.SUCCESS);
        this.tab.requestRebuild();
    }

    private List<TunePresets.Preset> stored() {
        return TunePresetStore.all(this.section);
    }

    public void save() {
        TuneModel tune = this.tab.tune();
        String name = TunePresets.cleanName(this.nameText);
        if (name.isEmpty()) {
            name = TunePresetBook.uniqueName(this.stored(), Component.translatable(KEY + this.section.id() + ".default_name").getString());
        }
        TunePresetBook.Change change = TunePresetStore.commit(this.section,
                TunePresetBook.save(this.section, this.stored(), name, TunePresets.capture(this.section, tune.working(), tune.defaults())));
        switch (change.outcome()) {
            case SAVED, UPDATED -> {
                this.selectedId = OWN + change.name();
                this.userSelected = true;
                this.nameText = change.name();
                this.tab.flash(Component.translatable(KEY + (change.outcome() == TunePresetBook.Outcome.SAVED ? "saved" : "updated"), change.name()),
                        Palette.SUCCESS);
            }
            case FULL -> this.tab.flash(Component.translatable(KEY + "full", TunePresets.MAX_PER_SECTION), Palette.ERROR);
            default -> this.tab.flash(Component.translatable(KEY + "name.missing"), Palette.WARNING);
        }
        this.tab.requestRebuild();
    }

    public void rename() {
        Choice current = this.selected();
        if (current == null || !current.own()) {
            return;
        }
        String from = current.id().substring(OWN.length());
        TunePresetBook.Change change = TunePresetStore.commit(this.section, TunePresetBook.rename(this.stored(), from, this.nameText));
        switch (change.outcome()) {
            case RENAMED -> {
                this.selectedId = OWN + change.name();
                this.nameText = change.name();
                this.tab.flash(Component.translatable(KEY + "renamed", from, change.name()), Palette.SUCCESS);
            }
            case TAKEN -> this.tab.flash(Component.translatable(KEY + "taken", change.name()), Palette.ERROR);
            case EMPTY_NAME -> this.tab.flash(Component.translatable(KEY + "name.missing"), Palette.WARNING);
            default -> this.tab.flash(Component.translatable(KEY + "missing"), Palette.ERROR);
        }
        this.tab.requestRebuild();
    }

    public void delete() {
        Choice current = this.selected();
        if (current == null || !current.own()) {
            return;
        }
        if (this.confirmDeleteUntil == 0L) {
            this.confirmDeleteUntil = Util.getMillis() + CONFIRM_MILLIS;
            this.updateButtons();
            return;
        }
        this.confirmDeleteUntil = 0L;
        String name = current.id().substring(OWN.length());
        TunePresetBook.Change change = TunePresetStore.commit(this.section, TunePresetBook.delete(this.stored(), name));
        if (change.outcome() == TunePresetBook.Outcome.DELETED) {
            this.selectedId = "";
            this.userSelected = false;
            this.setName("");
            this.tab.flash(Component.translatable(KEY + "deleted", name), Palette.TEXT_MUTED);
        }
        this.tab.requestRebuild();
    }

    public void copyCode() {
        Choice current = this.selected();
        if (current == null) {
            return;
        }
        String name = current.own() ? current.id().substring(OWN.length()) : current.name().getString();
        Minecraft.getInstance().keyboardHandler.setClipboard(TunePresets.encode(this.section, name, current.values()));
        this.tab.flash(Component.translatable(KEY + "copied", ThemeText.fit(this.tab.font(), name, 120)), Palette.SUCCESS);
    }

    public void pasteCode() {
        this.importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    public void importCode(String code) {
        Optional<TunePresets.Decoded> decoded = TunePresets.decode(code);
        if (decoded.isEmpty()) {
            this.tab.flash(Component.translatable(KEY + "paste.invalid"), Palette.ERROR);
            return;
        }
        TunePresets.Decoded preset = decoded.get();
        String fallback = Component.translatable(KEY + preset.section().id() + ".default_name").getString();
        TunePresetBook.Change change = TunePresetStore.commit(preset.section(), TunePresetBook.importPreset(preset.section(),
                TunePresetStore.all(preset.section()), preset.name(), fallback, preset.values()));
        switch (change.outcome()) {
            case IMPORTED, EXISTS -> {
                if (preset.section() == this.section) {
                    this.selectedId = OWN + change.name();
                    this.userSelected = true;
                    this.nameText = change.name();
                    this.tab.flash(Component.translatable(KEY + "imported", change.name()), Palette.SUCCESS);
                } else {
                    this.tab.flash(Component.translatable(KEY + "imported_other", change.name(),
                            Component.translatable(KEY + preset.section().id() + ".section")), Palette.SUCCESS);
                }
            }
            case FULL -> this.tab.flash(Component.translatable(KEY + "full", TunePresets.MAX_PER_SECTION), Palette.ERROR);
            default -> this.tab.flash(Component.translatable(KEY + "paste.invalid"), Palette.ERROR);
        }
        this.tab.requestRebuild();
    }
}
