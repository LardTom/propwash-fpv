package de.lardtom.propwash.server.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.server.AddonWorldRules;
import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AddonWorldRulesSelfTests {
    public static final String GROUP = "addon-api-17: B5 – ";
    private static final String GERMAN = "de_de";
    private static final String INDEX = "awr_index";
    private static final String SINCE = "awr_since";
    private static final String FUTURE = "awr_future";
    private static final int WAIT_TICKS = 160;
    private static final int WINDOW_TICKS = 80;
    private static final int[][] SHOT_SIZES = {{854, 480, 2}, {320, 240, 3}};
    private static final String NARROW = "awr_narrow";
    private static final int NARROW_WIDTH = 320;
    private static final int NARROW_HEIGHT = 240;
    private static final int NARROW_SCALE = 2;
    private static int @Nullable [] savedWindow;

    private AddonWorldRulesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Beispiel-Regeln als Gamerules im eigenen Namensraum und in Propwashs Kategorie, get(Level) auf Server und Client,"
                + " /gamerule kommt am Client an", 900, AddonWorldRulesSelfTests::registeredStep);
        SelfTest.clientStep(GROUP + "eigener Abschnitt des Beispiel-Add-ons im Tab Welt-Regeln hinter den Propwash-Regeln, Änderung im Tab"
                + " über den Server", 900, AddonWorldRulesSelfTests::sectionStep);
        SelfTest.clientStep(GROUP + "Voreinstellung Training setzt den Wert aus presetValue, ohne Eintrag bleibt der Standard,"
                + " „Voreinstellung aktiv“ zählt Add-on-Werte", 1600, AddonWorldRulesSelfTests::presetStep);
        String narrowBack = GROUP + "GUI 320×240 – Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "GUI 320×240 – Sprache Deutsch", narrowBack, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        SelfTest.clientStep(GROUP + "Tab Welt-Regeln bei GUI 320×240 deutsch: kein Regelname und kein Wert gekürzt, mit Recht und gesperrt,"
                + " auch mit den Abschnitten weiterer Add-ons", 1600, AddonWorldRulesSelfTests::narrowStep);
        SelfTest.clientStep(narrowBack, 2400, Screenshots::restoreLanguage);
        if (Screenshots.enabled()) {
            registerScreenshots();
        }
    }

    private static void registerScreenshots() {
        for (int[] size : SHOT_SIZES) {
            Screenshots.step(GROUP + "Bildschirmfoto Tab Welt-Regeln mit Abschnitt des Beispiel-Add-ons " + size[0] + "x" + size[1],
                    "world_rules_addon_en_" + size[0] + "x" + size[1], 8, new SectionScene(size[0], size[1], size[2]));
        }
        String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        for (int[] size : SHOT_SIZES) {
            Screenshots.step(GROUP + "Bildschirmfoto Tab Welt-Regeln mit Abschnitt des Beispiel-Add-ons deutsch " + size[0] + "x" + size[1],
                    "world_rules_addon_de_" + size[0] + "x" + size[1], 8, new SectionScene(size[0], size[1], size[2]));
        }
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
    }

    private interface Phase {
        @Nullable StepResult run(SelfTest.StepContext ctx, int waited);
    }

    private static Phase server(String what, Consumer<MinecraftServer> action) {
        return (ctx, waited) -> {
            Map<String, Object> state = ctx.state();
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future == null) {
                state.put(FUTURE, ctx.onServer(action));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            state.remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                Throwable cause = future.exceptionNow();
                return StepResult.fail(what + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            return null;
        };
    }

    private static Phase await(String what, BooleanSupplier condition) {
        return (ctx, waited) -> {
            if (condition.getAsBoolean()) {
                return null;
            }
            return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what);
        };
    }

    private static Phase check(AtomicReference<String> problem) {
        return (ctx, waited) -> problem.get() == null ? null : StepResult.fail(problem.get());
    }

    private static Phase log(String message) {
        return (ctx, waited) -> {
            ctx.log(message);
            return null;
        };
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases) {
        if (!ExampleRules.registered()) {
            return StepResult.fail("Beispiel-Add-on nicht geladen (" + ExampleAddon.NAMESPACE + ", PROPWASH_DEV_EXAMPLE)");
        }
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (!state.containsKey(SINCE)) {
            state.put(SINCE, ctx.tick());
        }
        while (index < phases.size()) {
            int waited = ctx.tick() - (int) state.get(SINCE);
            StepResult result;
            try {
                result = phases.get(index).run(ctx, waited);
            } catch (RuntimeException e) {
                result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (result == null) {
                index++;
                state.put(INDEX, index);
                state.put(SINCE, ctx.tick());
                continue;
            }
            if (result.status() == StepResult.Status.FAIL) {
                ctx.onServer(AddonWorldRulesSelfTests::reset);
                Minecraft.getInstance().execute(() -> closeScreen(false));
            }
            return result;
        }
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static <T> AtomicReference<T> keep(SelfTest.StepContext ctx, String key) {
        return (AtomicReference<T>) ctx.state().computeIfAbsent(key, k -> new AtomicReference<T>());
    }

    static void reset(MinecraftServer server) {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            WorldRules.set(server, spec, spec.defaultValue());
        }
        resetAddon(server);
    }

    private static void resetAddon(MinecraftServer server) {
        for (WorldRule<?> rule : List.of(ExampleRules.cooldown(), ExampleRules.refill())) {
            set(server, rule, AddonWorldRules.serialize(rule, rule.defaultValue()));
        }
    }

    private static void set(MinecraftServer server, WorldRule<?> rule, String value) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule " + rule.id() + " " + value);
    }

    private static @Nullable Level clientLevel() {
        return Minecraft.getInstance().level;
    }

    private static boolean clientHas(int cooldown, boolean refill) {
        Level level = clientLevel();
        return level != null && ExampleRules.cooldown().get(level) == cooldown && ExampleRules.refill().get(level) == refill
                && Integer.toString(cooldown).equals(ClientWorldRules.INSTANCE.raw(ExampleRules.LAUNCHER_COOLDOWN).orElse(null));
    }

    private static Phase serverHas(int cooldown, boolean refill) {
        return (ctx, waited) -> {
            Map<String, Object> state = ctx.state();
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            AtomicReference<String> seen = keep(ctx, "server_seen");
            if (future == null) {
                state.put(FUTURE, ctx.onServer(server -> {
                    int ticks = ExampleRules.cooldown().get(server.overworld());
                    boolean free = ExampleRules.refill().get(server.overworld());
                    seen.set(ticks == cooldown && free == refill ? null : "Abklingzeit " + ticks + " und Nachladen " + free);
                }));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            state.remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Server-Werte: " + future.exceptionNow());
            }
            if (seen.get() == null) {
                return null;
            }
            return waited < WAIT_TICKS ? StepResult.pending()
                    : StepResult.fail("Server hat " + seen.get() + " statt " + cooldown + " und " + refill);
        };
    }

    private static StepResult registeredStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "problem");
        return script(ctx, List.of(
                await("Welt-Regeln kamen nicht am Client an", ClientWorldRules.INSTANCE::known),
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                server("Registrierung", server -> {
                    List<String> problems = new ArrayList<>();
                    if (!AddonWorldRules.pending().isEmpty()) {
                        problems.add("nicht ins Register übernommen: " + AddonWorldRules.pending());
                    }
                    List<GameRule<?>> propwash = WorldRules.propwashRules();
                    for (WorldRule<?> rule : List.of(ExampleRules.cooldown(), ExampleRules.refill())) {
                        Optional<GameRule<?>> game = AddonWorldRules.gameRule(rule.id());
                        if (game.isEmpty()) {
                            problems.add(rule.id() + " ohne Gamerule");
                            continue;
                        }
                        Identifier key = BuiltInRegistries.GAME_RULE.getKey(game.get());
                        if (!rule.id().equals(key) || !WorldRules.CATEGORY.equals(game.get().category()) || !WorldRules.isPropwash(game.get())) {
                            problems.add(rule.id() + " mit Id " + key + " oder fremder Kategorie");
                        }
                        if (!rule.nameKey().equals(game.get().getDescriptionId())) {
                            problems.add(rule.id() + " Sprachschlüssel " + game.get().getDescriptionId() + " statt " + rule.nameKey());
                        }
                        if (!propwash.contains(game.get())) {
                            problems.add(rule.id() + " fehlt in den Regeln des Tabs");
                        }
                        if (!rule.defaultValue().equals(rule.get(server.overworld()))) {
                            problems.add(rule.id() + " liefert am Server " + rule.get(server.overworld()) + " statt " + rule.defaultValue());
                        }
                    }
                    if (!problems.isEmpty()) {
                        problem.set(String.join("; ", problems));
                        return;
                    }
                    set(server, ExampleRules.cooldown(), "37");
                    set(server, ExampleRules.refill(), "true");
                }),
                check(problem),
                serverHas(37, true),
                await("/gamerule mit 37 und true kam nicht am Client an", () -> clientHas(37, true)),
                server("Wert außerhalb des Bereichs", server -> {
                    set(server, ExampleRules.cooldown(), Integer.toString(ExampleRules.COOLDOWN_MAX + 1));
                    if (ExampleRules.cooldown().get(server.overworld()) != 37) {
                        problem.set("/gamerule nimmt " + (ExampleRules.COOLDOWN_MAX + 1) + " an: " + ExampleRules.cooldown().get(server.overworld()));
                    }
                }),
                check(problem),
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_DEFAULT, false)),
                log("Beispiel-Regeln " + ExampleRules.LAUNCHER_COOLDOWN + " und " + ExampleRules.LAUNCHER_REFILL
                        + " als Gamerules registriert, get(Level) liefert am Server und Client den Wert aus /gamerule")));
    }

    private static @Nullable WorldRulesTab openTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, WorldRulesTab.ID);
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !WorldRulesTab.ID.equals(active.id())) {
            screen.selectTab(WorldRulesTab.ID);
            return null;
        }
        return active instanceof WorldRulesTab tab && !tab.rows().isEmpty() ? tab : null;
    }

    private static void closeScreen(boolean save) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            if (save) {
                screen.saveAndClose();
            } else {
                screen.discardAndClose();
            }
        }
    }

    private static Phase tab(String what, Function<WorldRulesTab, @Nullable String> body) {
        return (ctx, waited) -> {
            WorldRulesTab tab = openTab();
            if (tab == null || tab.readOnly()) {
                return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht bearbeitbar: " + what);
            }
            String failure = body.apply(tab);
            return failure == null ? null : StepResult.fail(what + ": " + failure);
        };
    }

    static @Nullable String sectionProblem(WorldRulesTab tab) {
        List<WorldRulesTab.Row> rows = tab.rows();
        int first = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(ExampleRules.LAUNCHER_COOLDOWN)) {
                first = i;
                break;
            }
        }
        if (first < 0 || first + 1 >= rows.size() || !rows.get(first + 1).id().equals(ExampleRules.LAUNCHER_REFILL)) {
            return "Zeilen der Beispiel-Regeln fehlen oder stehen nicht in der Reihenfolge der Anmeldung";
        }
        for (int i = 0; i < rows.size(); i++) {
            boolean addon = AddonWorldRules.isAddonRule(rows.get(i).id());
            if (addon && i < first || !addon && i > first && rows.get(i).spec() != null) {
                return "Propwash-Regel " + rows.get(i).id() + " steht hinter dem Abschnitt des Add-ons";
            }
        }
        if (first == 0 || rows.get(first).spec() != null || rows.get(first).y() <= rows.get(first - 1).y() + Metrics.ROW_HEIGHT) {
            return "Beispiel-Regel ohne eigene Überschrift direkt hinter " + rows.get(first - 1).id();
        }
        Map<String, List<GameRule<?>>> groups = new LinkedHashMap<>();
        List<GameRule<?>> other = new ArrayList<>();
        other.add(AddonWorldRules.gameRule(ExampleRules.LAUNCHER_COOLDOWN).orElseThrow());
        other.add(AddonWorldRules.gameRule(ExampleRules.LAUNCHER_REFILL).orElseThrow());
        groups.put(WorldRulesTab.OTHER_GROUP, other);
        AddonRuleSections.split(groups);
        String title = AddonRuleSections.modName(ExampleAddon.NAMESPACE);
        if (!List.of(WorldRulesTab.OTHER_GROUP, title).equals(List.copyOf(groups.keySet())) || !groups.get(WorldRulesTab.OTHER_GROUP).isEmpty()
                || groups.get(title).size() != 2) {
            return "Abschnitte " + groups.keySet() + " statt „" + title + "“";
        }
        if (!rows.get(first).label().getString().equals(I18n.get(ExampleRules.cooldown().nameKey()))
                || I18n.get(ExampleRules.cooldown().nameKey()).equals(ExampleRules.cooldown().nameKey())) {
            return "Name der Beispiel-Regel ohne Übersetzung: " + rows.get(first).label().getString();
        }
        if (!(rows.get(first).widget() instanceof ValueSlider) || !(rows.get(first + 1).widget() instanceof ChoiceButton<?>)) {
            return "Bedienelemente " + rows.get(first).widget() + " und " + rows.get(first + 1).widget();
        }
        return null;
    }

    private static StepResult sectionStep(SelfTest.StepContext ctx) {
        return script(ctx, List.of(
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_DEFAULT, false)),
                tab("Abschnitt", tab -> {
                    String section = sectionProblem(tab);
                    if (section != null) {
                        return section;
                    }
                    WorldRulesTab.Row cooldown = tab.row(ExampleRules.LAUNCHER_COOLDOWN).orElseThrow();
                    WorldRulesTab.Row refill = tab.row(ExampleRules.LAUNCHER_REFILL).orElseThrow();
                    tab.set(cooldown.id(), cooldown.rule(), "12");
                    tab.set(refill.id(), refill.rule(), "true");
                    return tab.draft().size() == 2 ? null : "Entwurf " + tab.draft();
                }),
                (c, waited) -> {
                    closeScreen(true);
                    return null;
                },
                serverHas(12, true),
                await("Änderung im Tab kam nicht zurück an den Client", () -> clientHas(12, true)),
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_DEFAULT, false)),
                log("Abschnitt „" + AddonRuleSections.modName(ExampleAddon.NAMESPACE) + "“ hinter den Propwash-Regeln, Regler und Schalter"
                        + " ändern die Beispiel-Regeln über den Server")));
    }

    private static Phase close() {
        return (ctx, waited) -> {
            closeScreen(false);
            return null;
        };
    }

    private static Phase press(RulePreset preset) {
        return tab("Voreinstellung " + preset.id(), tab -> {
            FlatButton button = tab.presetButton(preset).orElse(null);
            if (button == null || !button.active || !button.visible) {
                return "Knopf fehlt oder ist gesperrt";
            }
            button.press();
            return null;
        });
    }

    private static Phase showsPreset(@Nullable RulePreset preset) {
        return (ctx, waited) -> {
            WorldRulesTab tab = openTab();
            RulePreset shown = tab == null ? null : tab.matchingPreset().orElse(null);
            if (tab != null && shown == preset) {
                return null;
            }
            return waited < WAIT_TICKS ? StepResult.pending()
                    : StepResult.fail("Tab zeigt als aktiv " + (shown == null ? "keine Voreinstellung" : shown.id()) + " statt "
                    + (preset == null ? "keine" : preset.id()));
        };
    }

    private static StepResult presetStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "problem");
        AtomicReference<Integer> changed = keep(ctx, "changed");
        return script(ctx, List.of(
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_DEFAULT, false)),
                showsPreset(RulePreset.REALISTIC),
                press(RulePreset.TRAINING),
                await("Training kam nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_TRAINING, false)
                        && RulePreset.TRAINING.matches(ClientWorldRules.INSTANCE.values())),
                serverHas(ExampleRules.COOLDOWN_TRAINING, false),
                showsPreset(RulePreset.TRAINING),
                close(),
                server("Add-on-Regel nach Training ändern", server -> {
                    set(server, ExampleRules.cooldown(), Integer.toString(ExampleRules.COOLDOWN_TRAINING + 1));
                    if (WorldRules.matchingPreset(server).isPresent()) {
                        problem.set("Server zählt Training trotz geänderter Add-on-Regel: " + WorldRules.matchingPreset(server));
                    }
                }),
                check(problem),
                await("geänderte Add-on-Regel kam nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_TRAINING + 1, false)),
                showsPreset(null),
                press(RulePreset.ARCADE),
                await("Arcade kam nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_ARCADE, true)),
                serverHas(ExampleRules.COOLDOWN_ARCADE, true),
                showsPreset(RulePreset.ARCADE),
                press(RulePreset.REALISTIC),
                await("Realistisch kam nicht am Client an", () -> RulePreset.REALISTIC.matches(ClientWorldRules.INSTANCE.values())
                        && clientHas(ExampleRules.COOLDOWN_ARCADE, true)),
                serverHas(ExampleRules.COOLDOWN_ARCADE, true),
                showsPreset(RulePreset.REALISTIC),
                close(),
                server("Voreinstellung am Server", server -> {
                    int count = WorldRules.applyPreset(server, RulePreset.TRAINING);
                    changed.set(count);
                    int expected = RulePreset.TRAINING.differences().size() + 1;
                    if (count != expected || ExampleRules.cooldown().get(server.overworld()) != ExampleRules.COOLDOWN_TRAINING
                            || !ExampleRules.refill().get(server.overworld())) {
                        problem.set("Training am Server ändert " + count + " statt " + expected + " Regeln, Abklingzeit "
                                + ExampleRules.cooldown().get(server.overworld()) + ", Nachladen " + ExampleRules.refill().get(server.overworld()));
                    }
                    if (WorldRules.matchingPreset(server).orElse(null) != RulePreset.TRAINING) {
                        problem.set("Server zeigt nach Training " + WorldRules.matchingPreset(server));
                    }
                }),
                check(problem),
                await("Training vom Server kam nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_TRAINING, true)),
                showsPreset(RulePreset.TRAINING),
                close(),
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> clientHas(ExampleRules.COOLDOWN_DEFAULT, false)),
                (c, waited) -> {
                    c.log("Voreinstellungen: Training setzt die Abklingzeit auf " + ExampleRules.COOLDOWN_TRAINING + ", Nachladen bleibt aus;"
                            + " geänderte Add-on-Regel hebt Training auf; Arcade " + ExampleRules.COOLDOWN_ARCADE + " und an; Realistisch ohne"
                            + " Eintrag lässt beide; Training am Server ändert " + changed.get() + " Regeln");
                    return null;
                }));
    }

    private static StepResult narrowStep(SelfTest.StepContext ctx) {
        Narrow run = (Narrow) ctx.state().computeIfAbsent(NARROW, key -> new Narrow());
        StepResult result;
        try {
            result = run.advance(ctx);
        } catch (RuntimeException e) {
            result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
        }
        if (result.status() != StepResult.Status.PENDING) {
            run.finish();
        }
        return result;
    }

    private static void lockRules(boolean locked) {
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        List<WorldRulesS2C.Entry> entries = new ArrayList<>();
        rules.raw().forEach((id, value) -> entries.add(new WorldRulesS2C.Entry(id, value)));
        rules.accept(new WorldRulesS2C(!locked, entries));
    }

    private static final class Narrow {
        private int stage;
        private int since;
        private boolean locked;
        private final Map<String, String> cuts = new LinkedHashMap<>();
        private final List<String> seen = new ArrayList<>();

        StepResult advance(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            int frameWidth = NARROW_WIDTH * NARROW_SCALE;
            int frameHeight = NARROW_HEIGHT * NARROW_SCALE;
            if (this.stage == 0) {
                if (!ClientWorldRules.INSTANCE.known() || !ClientWorldRules.INSTANCE.editable()) {
                    return ctx.tick() < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Welt-Regeln am Client nicht bearbeitbar");
                }
                if (savedWindow == null) {
                    savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                }
                closeScreen(false);
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(frameWidth / density), Math.round(frameHeight / density));
                this.stage = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.stage == 1) {
                boolean sized = window.getWidth() == frameWidth && window.getHeight() == frameHeight;
                if (!sized && ctx.tick() - this.since < WINDOW_TICKS) {
                    return StepResult.pending();
                }
                mc.options.guiScale().set(NARROW_SCALE);
                mc.resizeGui();
                if (window.getGuiScaledWidth() != NARROW_WIDTH || window.getGuiScaledHeight() != NARROW_HEIGHT) {
                    return StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt " + NARROW_WIDTH + "x"
                            + NARROW_HEIGHT);
                }
                this.stage = 2;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.stage == 2) {
                WorldRulesTab tab = openTab();
                if (tab == null || tab.readOnly()) {
                    return ctx.tick() - this.since < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht bearbeitbar");
                }
                this.audit(mc, tab, "mit Recht");
                lockRules(true);
                this.locked = true;
                this.stage = 3;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            WorldRulesTab tab = openTab();
            if (tab == null || !tab.readOnly() || tab.rows().stream().anyMatch(row -> row.widget() != null)) {
                return ctx.tick() - this.since < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln wird nicht gesperrt");
            }
            this.audit(mc, tab, "gesperrt");
            ctx.log("GUI " + NARROW_WIDTH + "x" + NARROW_HEIGHT + " deutsch, " + String.join("; ", this.seen));
            if (this.cuts.isEmpty()) {
                return StepResult.pass();
            }
            List<String> lines = new ArrayList<>();
            this.cuts.forEach((full, shown) -> lines.add(full + " → „" + shown + "“"));
            return StepResult.fail(this.cuts.size() + " Regelnamen oder Werte gekürzt: " + String.join(", ", lines));
        }

        private void audit(Minecraft mc, WorldRulesTab tab, String mode) {
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return;
            }
            Map<String, String> found = new LinkedHashMap<>(LayoutProbe.capture(mc, screen).cuts());
            for (WorldRulesTab.Row row : tab.rows()) {
                tab.reveal(row);
                found.putAll(LayoutProbe.capture(mc, screen).cuts());
            }
            List<String> addons = new ArrayList<>();
            Set<String> texts = new HashSet<>();
            for (WorldRulesTab.Row row : tab.rows()) {
                String namespace = row.id().getNamespace();
                if (AddonWorldRules.isAddonRule(row.id()) && !addons.contains(namespace)) {
                    addons.add(namespace);
                }
                texts.add(row.label().getString());
                texts.add(WorldRuleText.value(row.id(), tab.current(row.id(), row.rule())).getString());
            }
            List<String> elsewhere = new ArrayList<>();
            int rowCuts = 0;
            for (Map.Entry<String, String> cut : found.entrySet()) {
                String text = "„" + cut.getKey() + "“ (" + mc.font.width(cut.getKey()) + " px, " + mode + ")";
                if (texts.contains(cut.getKey())) {
                    this.cuts.putIfAbsent(text, cut.getValue());
                    rowCuts++;
                } else {
                    elsewhere.add(text + " → „" + cut.getValue() + "“");
                }
            }
            this.seen.add(mode + ": " + tab.rows().size() + " Regeln, Add-ons " + addons + ", Zeilenbreite " + tab.rowWidth() + ", "
                    + rowCuts + " Regelzeilen gekürzt" + (elsewhere.isEmpty() ? "" : ", außerhalb der Regelzeilen gekürzt: " + String.join(", ", elsewhere)));
        }

        void finish() {
            Minecraft mc = Minecraft.getInstance();
            if (this.locked) {
                lockRules(false);
                this.locked = false;
            }
            closeScreen(false);
            int[] saved = savedWindow;
            if (saved != null) {
                mc.options.guiScale().set(saved[2]);
                mc.getWindow().setWindowed(saved[0], saved[1]);
                mc.resizeGui();
                savedWindow = null;
            }
            this.stage = 0;
        }
    }

    private static final class SectionScene implements Screenshots.Scene {
        private final int width;
        private final int height;
        private final int scale;
        private int stage;
        private int since;

        SectionScene(int width, int height, int scale) {
            this.width = width;
            this.height = height;
            this.scale = scale;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (!ExampleRules.registered()) {
                return StepResult.fail("Beispiel-Add-on nicht geladen");
            }
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            int frameWidth = this.width * this.scale;
            int frameHeight = this.height * this.scale;
            if (this.stage == 0) {
                if (savedWindow == null) {
                    savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                }
                closeScreen(false);
                ctx.onServer(AddonWorldRulesSelfTests::reset);
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(frameWidth / density), Math.round(frameHeight / density));
                this.stage = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.stage == 1) {
                boolean sized = window.getWidth() == frameWidth && window.getHeight() == frameHeight;
                if (!sized && ctx.tick() - this.since < WINDOW_TICKS || !clientHas(ExampleRules.COOLDOWN_DEFAULT, false)) {
                    return ctx.tick() - this.since < 2 * WAIT_TICKS ? StepResult.pending() : StepResult.fail("Standardwerte kamen nicht an");
                }
                mc.options.guiScale().set(this.scale);
                mc.resizeGui();
                ctx.log("Fenster " + window.getWidth() + "x" + window.getHeight() + ", GUI " + window.getGuiScaledWidth() + "x"
                        + window.getGuiScaledHeight());
                if (window.getGuiScaledWidth() != this.width || window.getGuiScaledHeight() != this.height) {
                    return StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt " + this.width + "x"
                            + this.height);
                }
                this.stage = 2;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            WorldRulesTab tab = openTab();
            if (tab == null) {
                return ctx.tick() - this.since < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
            }
            String section = sectionProblem(tab);
            if (section != null) {
                return StepResult.fail(section);
            }
            tab.reveal(tab.row(ExampleRules.LAUNCHER_REFILL).orElseThrow());
            Screenshots.parkMouse(mc);
            Screenshots.hideMarker(true);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            closeScreen(false);
            Screenshots.hideMarker(false);
            int[] saved = savedWindow;
            if (saved != null) {
                mc.options.guiScale().set(saved[2]);
                mc.getWindow().setWindowed(saved[0], saved[1]);
                mc.resizeGui();
                savedWindow = null;
            }
            this.stage = 0;
        }
    }
}
