package de.lardtom.propwash.server.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.ImpactC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRuleEditC2S;
import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesRights;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.ImpactSample;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.SliderTypingSelfTests;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WorldRulesSelfTests {
    private static final String INDEX = "rules_index";
    private static final String SINCE = "rules_since";
    private static final String FUTURE = "rules_future";
    private static final int WAIT_TICKS = 100;
    private static final int FRONT = (1 << ImpactSample.HULL_TIP + 1) | (1 << ImpactSample.HULL_TIP + 3) | (1 << ImpactSample.HULL_ELECTRONICS);

    private WorldRulesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("server: Welt-Regeln registriert, im Katalog und am Client", 200, WorldRulesSelfTests::registeredStep);
        SelfTest.clientStep("server: Welt-Regel per /gamerule erreicht den Client", 300, WorldRulesSelfTests::commandStep);
        SelfTest.clientStep("server: Tab Welt-Regeln ändert eine Regel über den Server", 400, WorldRulesSelfTests::tabStep);
        SelfTest.clientStep("server: Welt-Regel Schwerkraft 0–1000 % in 1-%-Schritten, im Tab getippt", 500, WorldRulesSelfTests::gravityRangeStep);
        SelfTest.clientStep("server: Welt-Regel-Paket nimmt nur gültige Propwash-Regeln an", 300, WorldRulesSelfTests::invalidStep);
        SelfTest.clientStep("server: Tab Welt-Regeln ohne Recht nur lesbar", 300, WorldRulesSelfTests::readOnlyStep);
        SelfTest.clientStep("server: Welt-Regeln – Server lehnt Änderungen eines Spielers ohne Recht ab", 200, WorldRulesSelfTests::guestStep);
        SelfTest.clientStep("server: Welt-Regel Videokanäle begrenzt die Sender", 200, WorldRulesSelfTests::channelsStep);
        SelfTest.clientStep("server: Welt-Regel unendliche Reichweite je Videosystem", 300, WorldRulesSelfTests::rangeStep);
        SelfTest.clientStep("server: Welt-Regel Crash-Schaden aus verhindert Verschleiß", 400, WorldRulesSelfTests::crashDamageStep);
        SelfTest.clientStep("server: Welt-Regel-Voreinstellungen im Tab setzen alle Regeln über den Server", 900, WorldRulesSelfTests::presetStep);
        SelfTest.clientStep("server: Welt-Regel-Voreinstellungen per /propwash rules preset", 300, WorldRulesSelfTests::presetCommandStep);
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

    private static Phase client(Runnable action) {
        return (ctx, waited) -> {
            action.run();
            return null;
        };
    }

    private static Phase pause(int ticks) {
        return (ctx, waited) -> waited < ticks ? StepResult.pending() : null;
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases, @Nullable Consumer<MinecraftServer> cleanup) {
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
            if (result.status() == StepResult.Status.FAIL && cleanup != null) {
                ctx.onServer(cleanup);
            }
            return result;
        }
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static <T> T keep(SelfTest.StepContext ctx, String key, Supplier<T> init) {
        return (T) ctx.state().computeIfAbsent(key, k -> init.get());
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void reset(MinecraftServer server) {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            WorldRules.set(server, spec, spec.defaultValue());
        }
    }

    private static StepResult registeredStep(SelfTest.StepContext ctx) {
        AtomicReference<RuleValues> serverValues = keep(ctx, "server_values", AtomicReference::new);
        return script(ctx, List.of(
                await("Welt-Regeln kamen nicht am Client an", ClientWorldRules.INSTANCE::known),
                server("Registrierung", server -> {
                    List<String> problems = new ArrayList<>();
                    for (RuleSpec spec : WorldRuleSpecs.all()) {
                        Optional<GameRule<?>> rule = WorldRules.rule(spec);
                        if (rule.isEmpty()) {
                            problems.add(spec.id() + " nicht registriert");
                            continue;
                        }
                        Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule.get());
                        if (id == null || !id.toString().equals(spec.id()) || !WorldRules.CATEGORY.equals(rule.get().category())) {
                            problems.add(spec.id() + " mit falscher Id oder Kategorie: " + id);
                        }
                        if (!rule.get().getDescriptionId().equals(spec.translationKey())) {
                            problems.add(spec.id() + " Sprachschlüssel " + rule.get().getDescriptionId());
                        }
                    }
                    for (GameRule<?> rule : WorldRules.propwashRules()) {
                        Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
                        if (id == null || WorldRuleText.spec(id).isEmpty()) {
                            problems.add("Welt-Regel " + id + " fehlt im Katalog WorldRuleSpecs (Export für die Website)");
                        }
                    }
                    if (!problems.isEmpty()) {
                        throw new IllegalStateException(String.join("; ", problems));
                    }
                    serverValues.set(WorldRules.values(server));
                }),
                await("Client-Werte weichen vom Server ab", () -> ClientWorldRules.INSTANCE.values().equals(serverValues.get())),
                await("Host im Einzelspieler darf die Welt-Regeln nicht ändern", ClientWorldRules.INSTANCE::editable),
                (c, waited) -> {
                    c.log("Welt-Regeln: " + WorldRuleSpecs.all().size() + " im Katalog, " + ClientWorldRules.INSTANCE.raw().size()
                            + " am Client, " + ClientWorldRules.INSTANCE.values());
                    return null;
                }), null);
    }

    private static StepResult commandStep(SelfTest.StepContext ctx) {
        return script(ctx, List.of(
                server("Befehl", server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "gamerule propwash:wind 3")),
                await("Wind 3 kam nicht am Client an", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) == 3),
                await("Wind 3 wirkt nicht auf die Physik", () -> ClientWorldRules.INSTANCE.physics().toSimTuning().windScale == 3.0
                        && ClientWorldRules.INSTANCE.physics().modified()),
                server("Befehl außerhalb des Bereichs", server -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule propwash:gravity_scale 5000");
                    if (WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != 100) {
                        throw new IllegalStateException("Schwerkraft 5000 % angenommen");
                    }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule propwash:wind 1");
                }),
                await("Wind 1 kam nicht zurück", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) == 1
                        && !ClientWorldRules.INSTANCE.physics().modified())), WorldRulesSelfTests::reset);
    }

    private static @Nullable WorldRulesTab openTab(SelfTest.StepContext ctx) {
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

    private static Identifier id(RuleSpec spec) {
        return Identifier.parse(spec.id());
    }

    private static StepResult gravityRangeStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "problem", AtomicReference::new);
        return script(ctx, List.of(
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
                    }
                    WorldRulesTab.Row row = tab.row(id(WorldRuleSpecs.GRAVITY_SCALE)).orElse(null);
                    if (row == null || !(row.widget() instanceof ValueSlider slider) || !slider.typeable() || !slider.active) {
                        return StepResult.fail("Schwerkraft im Tab kein tippbarer Regler: " + (row == null ? "fehlt" : row.widget()));
                    }
                    if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
                        return StepResult.fail("Konfigurator nicht offen");
                    }
                    screen.setFocused(slider);
                    SliderTypingSelfTests.typeText("347");
                    SliderTypingSelfTests.pressEnter();
                    screen.setFocused(null);
                    if (!"347".equals(tab.draft().get(id(WorldRuleSpecs.GRAVITY_SCALE)))) {
                        return StepResult.fail("getippte 347 % nicht vorgemerkt: " + tab.draft() + ", Regler " + slider.value());
                    }
                    return null;
                },
                client(() -> closeScreen(true)),
                await("Schwerkraft 347 % kam nicht über den Server zurück", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) == 347
                        && ClientWorldRules.INSTANCE.physics().toSimTuning().gravityScale == 3.47 && ClientWorldRules.INSTANCE.physics().modified()),
                server("Grenzen per Befehl", server -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule propwash:gravity_scale 0");
                    if (WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != 0) {
                        problem.set("Schwerkraft 0 % nicht angenommen: " + WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE));
                    }
                }),
                await("Schwerkraft 0 % kam nicht am Client an", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) == 0
                        && ClientWorldRules.INSTANCE.physics().toSimTuning().gravity() == 0.0),
                server("Obergrenze per Befehl", server -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule propwash:gravity_scale 1000");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule propwash:gravity_scale 1001");
                    if (WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != 1000) {
                        problem.set("Schwerkraft 1000 % nicht angenommen oder 1001 % nicht abgelehnt: "
                                + WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE));
                    }
                }),
                await("Schwerkraft 1000 % kam nicht am Client an", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) == 1000
                        && ClientWorldRules.INSTANCE.physics().toSimTuning().gravityScale == 10.0),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get()),
                server("Zurücksetzen", WorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht zurück", () -> !ClientWorldRules.INSTANCE.physics().modified())),
                server -> {
                    reset(server);
                    Minecraft.getInstance().execute(() -> closeScreen(false));
                });
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "problem", AtomicReference::new);
        return script(ctx, List.of(
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
                    }
                    List<String> missing = new ArrayList<>();
                    for (RuleSpec spec : WorldRuleSpecs.all()) {
                        Optional<WorldRulesTab.Row> row = tab.row(id(spec));
                        if (row.isEmpty() || row.get().widget() == null || !row.get().widget().active) {
                            missing.add(spec.name());
                        }
                    }
                    if (!missing.isEmpty() || tab.readOnly()) {
                        return StepResult.fail("Tab zeigt nicht alle Regeln bedienbar: " + missing + " nurLesen=" + tab.readOnly());
                    }
                    WorldRulesTab.Row battery = tab.row(id(WorldRuleSpecs.BATTERY_SCALE)).orElseThrow();
                    tab.set(battery.id(), battery.rule(), "200");
                    WorldRulesTab.Row sag = tab.row(id(WorldRuleSpecs.BATTERY_SAG)).orElseThrow();
                    tab.set(sag.id(), sag.rule(), "false");
                    if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) || !screen.hasUnsavedChanges()
                            || tab.draft().size() != 2) {
                        return StepResult.fail("Änderung im Tab nicht vorgemerkt: " + tab.draft());
                    }
                    if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.BATTERY_SCALE) != 100) {
                        return StepResult.fail("Entwurf wirkt vor dem Speichern");
                    }
                    return null;
                },
                client(() -> closeScreen(true)),
                await("Akku 200 % kam nicht über den Server zurück", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.BATTERY_SCALE) == 200
                        && ClientWorldRules.INSTANCE.physics().toSimTuning().batteryScale == 2.0
                        && !ClientWorldRules.INSTANCE.physics().toSimTuning().batterySag),
                server("Server-Werte", server -> {
                    if (WorldRules.get(server, WorldRuleSpecs.BATTERY_SCALE) != 200 || WorldRules.on(server, WorldRuleSpecs.BATTERY_SAG)) {
                        problem.set("Server hat Akku 200 % und Spannungseinbruch aus nicht übernommen: " + WorldRules.values(server));
                    }
                }),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get()),
                server("Zurücksetzen", WorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht zurück", () -> !ClientWorldRules.INSTANCE.physics().modified())),
                server -> {
                    reset(server);
                    Minecraft.getInstance().execute(() -> closeScreen(false));
                });
    }

    private static StepResult invalidStep(SelfTest.StepContext ctx) {
        AtomicReference<Boolean> pvpBefore = keep(ctx, "pvp", AtomicReference::new);
        return script(ctx, List.of(
                server("PvP lesen", server -> pvpBefore.set(server.getGameRules().get(GameRules.PVP))),
                client(() -> ClientNet.sendToServer(new WorldRuleEditC2S(List.of(
                        new WorldRulesS2C.Entry(Identifier.withDefaultNamespace("pvp"), Boolean.toString(!pvpBefore.get())),
                        new WorldRulesS2C.Entry(id(WorldRuleSpecs.GRAVITY_SCALE), "5000"),
                        new WorldRulesS2C.Entry(id(WorldRuleSpecs.WIND), "stark"),
                        new WorldRulesS2C.Entry(Identifier.fromNamespaceAndPath(RuleSpec.NAMESPACE, "gibt_es_nicht"), "1"))))),
                pause(20),
                server("Prüfen", server -> {
                    if (server.getGameRules().get(GameRules.PVP) != pvpBefore.get()) {
                        server.getGameRules().set(GameRules.PVP, pvpBefore.get(), server);
                        throw new IllegalStateException("fremde Vanilla-Regel pvp über das Propwash-Paket geändert");
                    }
                    if (WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != 100 || WorldRules.get(server, WorldRuleSpecs.WIND) != 1) {
                        throw new IllegalStateException("ungültige Werte angenommen: " + WorldRules.values(server));
                    }
                })), WorldRulesSelfTests::reset);
    }

    private static StepResult guestStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "guest", AtomicReference::new);
        return script(ctx, List.of(
                server("Gast ohne Recht", server -> problem.set(WorldRulesRights.check(server, false))),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get())), WorldRulesSelfTests::reset);
    }

    private static StepResult readOnlyStep(SelfTest.StepContext ctx) {
        return script(ctx, List.of(
                client(() -> ClientWorldRules.INSTANCE.accept(new WorldRulesS2C(false, entriesOf(ClientWorldRules.INSTANCE.raw())))),
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
                    }
                    if (!tab.readOnly() || tab.hasDefaults()) {
                        return StepResult.fail("Tab ohne Recht nicht schreibgeschützt");
                    }
                    if (tab.rows().size() != WorldRules.propwashRules().size()) {
                        return StepResult.fail("Tab ohne Recht zeigt " + tab.rows().size() + " statt " + WorldRules.propwashRules().size() + " Regeln");
                    }
                    for (WorldRulesTab.Row row : tab.rows()) {
                        if (row.widget() != null) {
                            return StepResult.fail("Regel " + row.id() + " ohne Recht bedienbar");
                        }
                    }
                    for (RulePreset preset : RulePreset.values()) {
                        if (tab.presetButton(preset).isPresent()) {
                            return StepResult.fail("Voreinstellung " + preset.id() + " ohne Recht bedienbar");
                        }
                    }
                    tab.applyPreset(RulePreset.ARCADE);
                    if (tab.matchingPreset().orElse(null) == RulePreset.ARCADE) {
                        return StepResult.fail("Voreinstellung ohne Recht übernommen");
                    }
                    WorldRulesTab.Row wind = tab.row(id(WorldRuleSpecs.WIND)).orElseThrow();
                    tab.set(wind.id(), wind.rule(), "3");
                    return tab.draft().isEmpty() ? null : StepResult.fail("Entwurf trotz fehlendem Recht angelegt");
                },
                client(() -> closeScreen(false)),
                server("Regeln neu schicken", server -> {
                    ServerPlayer player = player(server);
                    PropwashNet.sendToPlayer(player, WorldRules.snapshotFor(player));
                }),
                await("Recht kam nicht zurück", ClientWorldRules.INSTANCE::editable)), null);
    }

    private static boolean clientMatches(RulePreset preset) {
        return preset.matches(ClientWorldRules.INSTANCE.values());
    }

    private static Phase serverMatches(RulePreset preset, AtomicReference<String> problem) {
        return server("Server-Werte " + preset.id(), server -> {
            if (!preset.matches(WorldRules.values(server)) || WorldRules.matchingPreset(server).orElse(null) != preset) {
                problem.set("Server hat die Voreinstellung " + preset.id() + " nicht: " + WorldRules.values(server));
            }
        });
    }

    private static Phase press(RulePreset preset) {
        return (c, waited) -> {
            WorldRulesTab tab = openTab(c);
            if (tab == null) {
                return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
            }
            FlatButton button = tab.presetButton(preset).orElse(null);
            if (button == null || !button.active || !button.visible) {
                return StepResult.fail("Knopf für die Voreinstellung " + preset.id() + " fehlt oder ist gesperrt");
            }
            button.press();
            return null;
        };
    }

    private static StepResult presetStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "preset_problem", AtomicReference::new);
        return script(ctx, List.of(
                server("Zurücksetzen", WorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht an", () -> clientMatches(RulePreset.REALISTIC)),
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
                    }
                    for (RulePreset preset : RulePreset.values()) {
                        if (tab.presetButton(preset).isEmpty()) {
                            return StepResult.fail("Knopf für die Voreinstellung " + preset.id() + " fehlt");
                        }
                    }
                    if (tab.matchingPreset().orElse(null) != RulePreset.REALISTIC) {
                        return StepResult.fail("Standardwerte zeigen nicht Realistisch: " + tab.matchingPreset());
                    }
                    WorldRulesTab.Row wind = tab.row(id(WorldRuleSpecs.WIND)).orElseThrow();
                    tab.set(wind.id(), wind.rule(), "3");
                    if (tab.matchingPreset().isPresent()) {
                        return StepResult.fail("Entwurf mit Wind 3 passt noch zu " + tab.matchingPreset());
                    }
                    return null;
                },
                press(RulePreset.TRAINING),
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null) {
                        return StepResult.fail("Tab nach dem Klick zu");
                    }
                    if (!tab.draft().isEmpty()) {
                        return StepResult.fail("Entwurf bleibt nach der Voreinstellung: " + tab.draft());
                    }
                    return null;
                },
                await("Training kam nicht über den Server zurück", () -> clientMatches(RulePreset.TRAINING)
                        && ClientWorldRules.INSTANCE.physics().infiniteBattery() && !ClientWorldRules.INSTANCE.crashDamage()),
                serverMatches(RulePreset.TRAINING, problem),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get()),
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null || tab.matchingPreset().orElse(null) != RulePreset.TRAINING) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab zeigt Training nicht als aktiv");
                    }
                    return null;
                },
                press(RulePreset.ARCADE),
                await("Arcade kam nicht über den Server zurück", () -> clientMatches(RulePreset.ARCADE)
                        && ClientWorldRules.INSTANCE.partCompatibility() == PartCompatibility.LIFTED
                        && ClientWorldRules.INSTANCE.physics().toSimTuning().windScale == 0.0),
                serverMatches(RulePreset.ARCADE, problem),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get()),
                (c, waited) -> {
                    WorldRulesTab tab = openTab(c);
                    if (tab == null) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
                    }
                    WorldRulesTab.Row wind = tab.row(id(WorldRuleSpecs.WIND)).orElseThrow();
                    tab.set(wind.id(), wind.rule(), "2");
                    return tab.matchingPreset().isEmpty() ? null : StepResult.fail("einzelne Regel nach Arcade zählt noch als Arcade");
                },
                client(() -> closeScreen(true)),
                await("einzelne Regel nach der Voreinstellung kam nicht an", () -> ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) == 2
                        && RulePreset.match(ClientWorldRules.INSTANCE.values()).isEmpty()
                        && ClientWorldRules.INSTANCE.partCompatibility() == PartCompatibility.LIFTED),
                press(RulePreset.REALISTIC),
                await("Realistisch kam nicht über den Server zurück", () -> clientMatches(RulePreset.REALISTIC)
                        && !ClientWorldRules.INSTANCE.physics().modified()),
                serverMatches(RulePreset.REALISTIC, problem),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get()),
                client(() -> closeScreen(false)),
                (c, waited) -> {
                    c.log("Voreinstellungen: Training, Arcade, eigene Regel (Wind 2) und Realistisch über Tab und Server");
                    return null;
                }),
                server -> {
                    reset(server);
                    Minecraft.getInstance().execute(() -> closeScreen(false));
                });
    }

    private static StepResult presetCommandStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "preset_command", AtomicReference::new);
        return script(ctx, List.of(
                server("Befehl", server -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "propwash rules preset arcade");
                    if (!RulePreset.ARCADE.matches(WorldRules.values(server))) {
                        problem.set("/propwash rules preset arcade wirkt nicht: " + WorldRules.values(server));
                        return;
                    }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "propwash rules preset quatsch");
                    if (!RulePreset.ARCADE.matches(WorldRules.values(server))) {
                        problem.set("unbekannte Voreinstellung per Befehl verändert die Regeln: " + WorldRules.values(server));
                    }
                }),
                (c, waited) -> problem.get() == null ? null : StepResult.fail(problem.get()),
                await("Arcade per Befehl kam nicht am Client an", () -> clientMatches(RulePreset.ARCADE)),
                server("Zurück", server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "propwash rules preset realistic")),
                await("Realistisch per Befehl kam nicht am Client an", () -> clientMatches(RulePreset.REALISTIC))),
                WorldRulesSelfTests::reset);
    }

    static List<WorldRulesS2C.Entry> entriesOf(Map<Identifier, String> raw) {
        List<WorldRulesS2C.Entry> out = new ArrayList<>();
        raw.forEach((id, value) -> out.add(new WorldRulesS2C.Entry(id, value)));
        return out;
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.setNoGravity(true);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.setAttitude(new Quaternionf());
        return drone;
    }

    private static boolean deniedForChannels(Optional<Component> reason) {
        return reason.isPresent() && reason.get().getContents() instanceof TranslatableContents text
                && "propwash.server.deny.channels_full".equals(text.getKey());
    }

    private static StepResult channelsStep(SelfTest.StepContext ctx) {
        List<QuadcopterEntity> drones = keep(ctx, "drones", ArrayList::new);
        Consumer<MinecraftServer> cleanup = server -> {
            for (QuadcopterEntity drone : drones) {
                SpectateServer.simulateTransmitter(drone, null);
                drone.discard();
            }
            drones.clear();
            reset(server);
        };
        return script(ctx, List.of(server("Videokanäle", server -> {
            ServerPlayer player = player(server);
            Vec3 eye = player.getEyePosition();
            QuadcopterEntity busy = spawn(player.level(), eye.add(3.0, 2.0, 6.0), DroneBuild.RACER);
            QuadcopterEntity wanted = spawn(player.level(), eye.add(-3.0, 2.0, 6.0), DroneBuild.RACER);
            drones.add(busy);
            drones.add(wanted);
            int others = SpectateServer.transmittersExcept(wanted, player.getUUID());
            WorldRules.set(server, WorldRuleSpecs.MAX_VIDEO_CHANNELS, others + 1);
            if (deniedForChannels(ServerRules.pilotDenialReason(player, wanted))) {
                throw new IllegalStateException("gesperrt, obwohl noch ein Kanal frei ist (" + others + " Sender)");
            }
            SpectateServer.simulateTransmitter(busy, "Kanal belegt");
            if (!deniedForChannels(ServerRules.pilotDenialReason(player, wanted))) {
                throw new IllegalStateException("alle " + (others + 1) + " Kanäle belegt, Start trotzdem erlaubt");
            }
            if (DronePiloting.tryStart(player, wanted, PilotMode.LINE_OF_SIGHT) != DronePiloting.StartResult.DENIED || wanted.pilot().isPresent()) {
                throw new IllegalStateException("Start bei vollen Kanälen nicht abgelehnt");
            }
            WorldRules.set(server, WorldRuleSpecs.MAX_VIDEO_CHANNELS, 0);
            if (deniedForChannels(ServerRules.pilotDenialReason(player, wanted))) {
                throw new IllegalStateException("0 = unbegrenzt sperrt trotzdem");
            }
            cleanup.accept(server);
        })), cleanup);
    }

    private static StepResult rangeStep(SelfTest.StepContext ctx) {
        List<QuadcopterEntity> drones = keep(ctx, "drones", ArrayList::new);
        Consumer<MinecraftServer> cleanup = server -> {
            drones.forEach(QuadcopterEntity::discard);
            drones.clear();
            reset(server);
        };
        return script(ctx, List.of(
                server("Reichweite", server -> {
                    ServerPlayer player = player(server);
                    Vec3 eye = player.getEyePosition();
                    QuadcopterEntity analog = spawn(player.level(), eye.add(3.0, 2.0, 6.0), DroneBuild.RACER);
                    QuadcopterEntity digital = spawn(player.level(), eye.add(-3.0, 2.0, 6.0), DroneBuild.FREESTYLE);
                    drones.add(analog);
                    drones.add(digital);
                    if (analog.build().video().system != VideoSystem.ANALOG || digital.build().video().system != VideoSystem.DIGITAL) {
                        throw new IllegalStateException("Testdrohnen haben nicht analog und digital");
                    }
                    GameType mode = player.gameMode();
                    try {
                        player.setGameMode(GameType.SURVIVAL);
                        if (DronePiloting.freeRange(player, analog) || DronePiloting.freeRange(player, digital)) {
                            throw new IllegalStateException("freie Reichweite ohne Regel im Überleben");
                        }
                        WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_ANALOG, 1);
                        if (!DronePiloting.freeRange(player, analog) || DronePiloting.freeRange(player, digital)) {
                            throw new IllegalStateException("Regel analog wirkt falsch: analog=" + DronePiloting.freeRange(player, analog)
                                    + " digital=" + DronePiloting.freeRange(player, digital));
                        }
                    } finally {
                        player.setGameMode(mode);
                    }
                }),
                await("unendliche Reichweite analog kam nicht am Client an", () -> ClientWorldRules.INSTANCE.infiniteRange(VideoSystem.ANALOG)
                        && !ClientWorldRules.INSTANCE.infiniteRange(VideoSystem.DIGITAL)),
                server("digital", server -> {
                    WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_ANALOG, 0);
                    WorldRules.set(server, WorldRuleSpecs.INFINITE_RANGE_DIGITAL, 1);
                }),
                await("unendliche Reichweite digital kam nicht am Client an", () -> ClientWorldRules.INSTANCE.infiniteRange(VideoSystem.DIGITAL)
                        && !ClientWorldRules.INSTANCE.infiniteRange(VideoSystem.ANALOG)),
                server("Aufräumen", cleanup),
                await("Reichweite nicht zurückgesetzt", () -> !ClientWorldRules.INSTANCE.infiniteRange(VideoSystem.DIGITAL))), cleanup);
    }

    private static StepResult crashDamageStep(SelfTest.StepContext ctx) {
        AtomicReference<QuadcopterEntity> drone = keep(ctx, "drone", AtomicReference::new);
        AtomicReference<int[]> ids = keep(ctx, "ids", AtomicReference::new);
        AtomicReference<PartWear> wear = keep(ctx, "wear", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = server -> {
            QuadcopterEntity d = drone.getAndSet(null);
            if (d != null) {
                d.setPilot(null);
                d.discard();
            }
            reset(server);
        };
        ImpactSample hit = new ImpactSample(20.0F, 0.0F, 0.0F, 0.0F, -1.0F, FRONT, 0xF, 1.0F);
        Runnable send = () -> {
            int[] idAndEpoch = ids.get();
            ClientNet.sendToServer(new ImpactC2S(idAndEpoch[0], idAndEpoch[1], 1.0F, List.of(hit, hit, hit)));
        };
        return script(ctx, List.of(
                server("Drohne", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity d = spawn(player.level(), player.getEyePosition().add(0.0, 1.0, 4.0), DroneBuild.RACER);
                    drone.set(d);
                    d.setPilot(player);
                    ids.set(new int[]{d.getId(), d.resetEpoch()});
                    WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE, 0);
                }),
                await("Crash-Schaden aus kam nicht am Client an", () -> !ClientWorldRules.INSTANCE.crashDamage()
                        && ClientWorldRules.INSTANCE.crashDamageStrength() == 0.0F),
                client(send),
                pause(10),
                server("ohne Schaden", server -> {
                    QuadcopterEntity d = drone.get();
                    if (d == null || !d.wear().isNew()) {
                        throw new IllegalStateException("Aufprall hat trotz Regel aus Teile abgenutzt: " + (d == null ? "-" : d.wear()));
                    }
                    WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE, 1);
                    WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE_SCALE, 300);
                }),
                await("Crash-Schaden an kam nicht am Client an", () -> ClientWorldRules.INSTANCE.crashDamage()
                        && ClientWorldRules.INSTANCE.crashDamageStrength() == 3.0F),
                client(send),
                pause(10),
                server("mit Schaden", server -> {
                    QuadcopterEntity d = drone.get();
                    if (d == null || d.wear().isNew()) {
                        throw new IllegalStateException("Aufprall mit Regel an hat nichts abgenutzt");
                    }
                    wear.set(d.wear());
                    cleanup.accept(server);
                }),
                (c, waited) -> {
                    c.log("Crash-Schaden: aus = neu, an mit 300 % = " + wear.get());
                    return null;
                }), cleanup);
    }

    public static Screenshots.Scene tabScene(@Nullable RuleSpec hover, int value) {
        return new TabScene(hover, value, true);
    }

    public static Screenshots.Scene readOnlyScene() {
        return new TabScene(WorldRuleSpecs.WIND, -1, false);
    }

    public static Screenshots.Scene presetScene(RulePreset applied, RulePreset hover) {
        return new PresetScene(applied, hover);
    }

    private static final class PresetScene implements Screenshots.Scene {
        private final RulePreset applied;
        private final RulePreset hover;
        private boolean sent;
        private @Nullable FlatButton button;

        private PresetScene(RulePreset applied, RulePreset hover) {
            this.applied = applied;
            this.hover = hover;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (!this.sent) {
                this.sent = true;
                ctx.onServer(server -> WorldRules.applyPreset(server, this.applied));
            }
            if (!clientMatches(this.applied)) {
                return ctx.tick() > 200 ? StepResult.fail("Voreinstellung " + this.applied.id() + " kam nicht an") : StepResult.pending();
            }
            WorldRulesTab tab = openTab(ctx);
            if (tab == null || tab.readOnly() || tab.matchingPreset().orElse(null) != this.applied) {
                return ctx.tick() > 300 ? StepResult.fail("Tab Welt-Regeln zeigt die Voreinstellung nicht") : StepResult.pending();
            }
            this.button = tab.presetButton(this.hover).orElse(null);
            return this.button == null ? StepResult.fail("Knopf " + this.hover.id() + " fehlt") : StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            FlatButton b = this.button;
            Minecraft mc = Minecraft.getInstance();
            if (b == null || !b.visible) {
                Screenshots.parkMouse(mc);
                return;
            }
            double x = (b.getX() + 0.5 * b.getWidth()) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = (b.getY() + 0.5 * b.getHeight()) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            closeScreen(false);
            this.button = null;
            this.sent = false;
            Screenshots.parkMouse(Minecraft.getInstance());
            ctx.onServer(WorldRulesSelfTests::reset);
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        private final @Nullable RuleSpec hover;
        private final int value;
        private final boolean editable;
        private @Nullable WorldRulesTab tab;
        private WorldRulesTab.@Nullable Row row;

        private TabScene(@Nullable RuleSpec hover, int value, boolean editable) {
            this.hover = hover;
            this.value = value;
            this.editable = editable;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (!this.editable && ClientWorldRules.INSTANCE.editable()) {
                ClientWorldRules.INSTANCE.accept(new WorldRulesS2C(false, entriesOf(ClientWorldRules.INSTANCE.raw())));
            }
            WorldRulesTab tab = openTab(ctx);
            if (tab == null) {
                return ctx.tick() > 200 ? StepResult.fail("Tab Welt-Regeln öffnet nicht") : StepResult.pending();
            }
            if (tab.readOnly() == this.editable) {
                return ctx.tick() > 200 ? StepResult.fail("Tab Welt-Regeln im falschen Modus") : StepResult.pending();
            }
            if (this.hover != null) {
                WorldRulesTab.Row row = tab.row(id(this.hover)).orElse(null);
                if (row == null) {
                    return StepResult.fail("Regel " + this.hover.id() + " fehlt im Tab");
                }
                if (this.value >= 0) {
                    tab.set(row.id(), row.rule(), this.hover.serialize(this.value));
                }
                tab.reveal(row);
                this.tab = tab;
                this.row = row;
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            WorldRulesTab t = this.tab;
            WorldRulesTab.Row r = this.row;
            Minecraft mc = Minecraft.getInstance();
            if (t == null || r == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            AbstractWidget w = r.widget();
            if (w != null && !w.visible) {
                Screenshots.parkMouse(mc);
                return;
            }
            double guiX = w != null ? w.getX() + 0.8 * w.getWidth() : t.left() + 0.6 * t.rowWidth();
            double guiY = w != null ? w.getY() + 0.5 * w.getHeight() : t.screenY(r) + 7.0;
            double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            closeScreen(false);
            this.tab = null;
            this.row = null;
            Screenshots.parkMouse(Minecraft.getInstance());
            if (!this.editable) {
                ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    PropwashNet.sendToPlayer(player, WorldRules.snapshotFor(player));
                });
            }
        }
    }
}
