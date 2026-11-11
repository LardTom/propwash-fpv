package de.lardtom.propwash.race.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.RaceRules;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class RaceStandardSection {
    public static final String KEY = RaceStandards.KEY;
    public static final int FIELD = 14;
    public static final int TOOLTIP_WIDTH = 260;
    public static final int MAX_LISTED = 3;
    public static final int LABEL_INSET = 3;
    public static final int STACK_GAP = Metrics.GAP / 2;
    private static final String SEPARATOR = " · ";

    private int builtVersion = -1;
    private int textRules = -1;
    private int textStandard = -1;
    private int builtWidth;
    private @Nullable Font builtFont;
    private boolean builtEditable;
    private int introY;
    private int statusY;
    private int statusHeight;
    private int compareY;
    private int compareHeight;
    private Component statusText = Component.empty();
    private Component compareText = Component.empty();
    private Component compareTip = Component.empty();
    private boolean matches = true;
    private int checkedRules = -1;
    private int checkedStandard = -1;
    private boolean adoptable;
    private @Nullable FlatButton adopt;
    private @Nullable FlatButton reset;

    public static Component title() {
        return Component.translatable(KEY + "section");
    }

    public @Nullable FlatButton adoptButton() {
        return this.adopt;
    }

    public @Nullable FlatButton resetButton() {
        return this.reset;
    }

    public int statusY() {
        return this.statusY;
    }

    public int compareY() {
        return this.compareY;
    }

    public boolean stale() {
        if (ClientRaceStandard.INSTANCE.version() != this.builtVersion) {
            return true;
        }
        Font font = this.builtFont;
        if (font == null || !this.refreshTexts()) {
            return false;
        }
        return Math.max(Metrics.LINE_HEIGHT, ThemeText.wrappedHeight(font, this.statusText, this.builtWidth)) != this.statusHeight
                || Math.max(Metrics.LINE_HEIGHT, ThemeText.wrappedHeight(font, this.compareText, this.builtWidth)) != this.compareHeight;
    }

    private boolean refreshTexts() {
        int rules = ClientWorldRules.INSTANCE.version();
        int version = ClientRaceStandard.INSTANCE.version();
        if (rules == this.textRules && version == this.textStandard) {
            return false;
        }
        this.textRules = rules;
        this.textStandard = version;
        RaceStandard standard = ClientRaceStandard.INSTANCE.standard();
        Map<String, Integer> current = AssistText.worldValues();
        this.statusText = ClientRaceStandard.INSTANCE.known() ? status(standard) : Component.translatable(KEY + "status.unknown");
        this.compareText = compare(standard, current);
        this.compareTip = compareTooltip(standard, current);
        this.matches = standard.differences(current).isEmpty();
        return true;
    }

    public int build(int cy, int x, int top, int width, boolean editable, Font font, Consumer<AbstractWidget> add, IntConsumer stop) {
        this.builtVersion = ClientRaceStandard.INSTANCE.version();
        this.builtEditable = editable;
        this.builtFont = font;
        this.builtWidth = width;
        this.adopt = null;
        this.reset = null;
        this.checkedRules = -1;
        this.textRules = -1;
        this.refreshTexts();
        stop.accept(cy);
        this.introY = cy;
        cy += ThemeText.wrappedHeight(font, Component.translatable(KEY + "intro"), width) + Metrics.GAP / 2;
        this.statusY = cy;
        this.statusHeight = Math.max(Metrics.LINE_HEIGHT, ThemeText.wrappedHeight(font, this.statusText, width));
        cy += this.statusHeight + 2;
        this.compareY = cy;
        this.compareHeight = Math.max(Metrics.LINE_HEIGHT, ThemeText.wrappedHeight(font, this.compareText, width));
        cy += this.compareHeight + Metrics.GAP / 2;
        if (editable) {
            stop.accept(cy);
            Component adoptLabel = Component.translatable(KEY + "adopt");
            Component resetLabel = Component.translatable(KEY + "reset");
            Buttons buttons = buttons(width, font.width(adoptLabel), font.width(resetLabel));
            this.adopt = SearchTags.label(new FlatButton(x, top + cy, buttons.adoptWidth(), FIELD, adoptLabel, FlatButton.Style.PRIMARY,
                    () -> send(RaceStandards.Action.ADOPT)), adoptLabel, title());
            this.adopt.setTooltip(ThemeText.tip(Component.translatable(KEY + "adopt.tooltip")));
            this.reset = SearchTags.label(new FlatButton(x + buttons.resetX(), top + cy + buttons.resetY(), buttons.resetWidth(), FIELD, resetLabel,
                    FlatButton.Style.NORMAL, () -> send(RaceStandards.Action.RESET)), resetLabel, title());
            this.reset.setTooltip(ThemeText.tip(Component.translatable(KEY + "reset.tooltip")));
            add.accept(this.adopt);
            add.accept(this.reset);
            cy += buttons.height() + Metrics.GAP;
        }
        this.refreshButtons();
        return cy + Metrics.SECTION_GAP;
    }

    public record Buttons(int adoptWidth, int resetX, int resetY, int resetWidth, int height) {
        public boolean stacked() {
            return this.resetY > 0;
        }
    }

    public static Buttons buttons(int width, int adoptLabel, int resetLabel) {
        int half = (width - Metrics.GAP) / 2;
        int rest = width - half - Metrics.GAP;
        if (labelFits(adoptLabel, half) && labelFits(resetLabel, rest)) {
            return new Buttons(half, half + Metrics.GAP, 0, rest, FIELD);
        }
        return new Buttons(width, 0, FIELD + STACK_GAP, width, 2 * FIELD + STACK_GAP);
    }

    public static boolean labelFits(int label, int button) {
        return TextFit.mode(label, button - 2 * LABEL_INSET) != TextFit.Mode.CUT;
    }

    static void send(RaceStandards.Action action) {
        if (ClientWorldRules.INSTANCE.editable()) {
            ClientNet.sendToServer(new RaceStandards.EditC2S(action));
        }
    }

    private void refreshButtons() {
        int rules = ClientWorldRules.INSTANCE.version();
        int standard = ClientRaceStandard.INSTANCE.version();
        if (rules != this.checkedRules || standard != this.checkedStandard) {
            this.checkedRules = rules;
            this.checkedStandard = standard;
            this.adoptable = !RaceStandard.adopt(AssistText.worldValues()).equals(ClientRaceStandard.INSTANCE.standard());
        }
        boolean editable = this.builtEditable && ClientWorldRules.INSTANCE.editable();
        if (this.adopt != null) {
            this.adopt.active = editable && this.adoptable;
        }
        if (this.reset != null) {
            this.reset.active = editable && !ClientRaceStandard.INSTANCE.standard().values().isEmpty();
        }
    }

    public static Component status(RaceStandard standard) {
        List<RaceRules.Rule> differences = standard.differences();
        if (differences.isEmpty()) {
            return Component.translatable(KEY + "status.defaults");
        }
        return Component.translatable(KEY + "status.custom", list(differences, rule -> standard.value(rule), MAX_LISTED));
    }

    public static Component compare(RaceStandard standard, Map<String, Integer> current) {
        List<RaceRules.Rule> differences = standard.differences(current);
        if (differences.isEmpty()) {
            return Component.translatable(KEY + "status.matches");
        }
        return Component.translatable(KEY + "status.differs", list(differences, rule -> RaceRules.value(current, rule), MAX_LISTED));
    }

    public static Component compareTooltip(RaceStandard standard, Map<String, Integer> current) {
        List<RaceRules.Rule> differences = standard.differences(current);
        if (differences.isEmpty()) {
            return Component.translatable(KEY + "status.matches");
        }
        MutableComponent tip = Component.empty().append(ThemeText.accent(Component.translatable(KEY + "tooltip.differs_title")));
        for (RaceRules.Rule rule : differences) {
            tip.append("\n").append(AssistWords.rule(rule, RaceRules.value(current, rule), standard.value(rule)));
        }
        return tip;
    }

    interface Value {
        int of(RaceRules.Rule rule);
    }

    static MutableComponent list(List<RaceRules.Rule> rules, Value value, int limit) {
        MutableComponent out = Component.empty();
        int shown = rules.size() > limit ? limit - 1 : rules.size();
        for (int i = 0; i < shown; i++) {
            RaceRules.Rule rule = rules.get(i);
            if (i > 0) {
                out.append(SEPARATOR);
            }
            out.append(Component.translatable(KEY + "value", AssistWords.name(rule), AssistWords.value(rule, value.of(rule))));
        }
        if (shown < rules.size()) {
            return Component.translatable(KEY + "list.more", out, rules.size() - shown);
        }
        return out;
    }

    public static Component standardTooltip(RaceStandard standard) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(Component.translatable(KEY + "tooltip.standard_title")));
        List<RaceRules.Rule> differences = standard.differences();
        if (differences.isEmpty()) {
            tip.append("\n").append(Component.translatable(KEY + "tooltip.standard_defaults"));
        }
        for (RaceRules.Rule rule : differences) {
            tip.append("\n").append(Component.translatable(KEY + "value", AssistWords.name(rule), AssistWords.value(rule, standard.value(rule))));
        }
        return tip;
    }

    public @Nullable Component extract(GuiGraphicsExtractor g, Font font, int x, int top, int width, int mouseX, int mouseY, int clipTop,
                                       int clipBottom) {
        this.refreshButtons();
        this.refreshTexts();
        ThemeText.wrapped(g, font, Component.translatable(KEY + "intro"), x, top + this.introY, width, Palette.TEXT_FAINT);
        ThemeText.wrapped(g, font, this.statusText, x, top + this.statusY, width, Palette.TEXT);
        ThemeText.wrapped(g, font, this.compareText, x, top + this.compareY, width, this.matches ? Palette.SUCCESS : Palette.WARNING);
        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= clipTop && mouseY < clipBottom;
        if (inside && mouseY >= top + this.statusY && mouseY < top + this.statusY + this.statusHeight) {
            return standardTooltip(ClientRaceStandard.INSTANCE.standard());
        }
        if (inside && mouseY >= top + this.compareY && mouseY < top + this.compareY + this.compareHeight) {
            return this.compareTip;
        }
        return null;
    }

    public static @Nullable Component ruleNote(Identifier id) {
        return RaceRules.find(id.toString()).<Component>map(rule -> rule.relevant()
                ? Component.translatable(KEY + "tooltip.relevant", AssistWords.value(rule, ClientRaceStandard.INSTANCE.standard().value(rule)))
                : Component.translatable(KEY + "tooltip.neutral")).orElse(null);
    }

    public static int tooltipWidth() {
        return TOOLTIP_WIDTH;
    }
}
