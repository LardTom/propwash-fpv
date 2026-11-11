package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.PersonalRulesDev;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.RaceRules;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.ServerRulesBadge;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.client.MyRulesTab;
import de.lardtom.propwash.server.client.PersonalPresetStore;
import de.lardtom.propwash.server.client.RuleTierSwitch;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.PersonalPresets;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.PersonalRulesDraft;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.client.ClientZones;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class PersonalRulesUiSelfTests {
    private static final String PREFIX = RaceRulesSelfTests.PREFIX;
    private static final long S = 1_000_000_000L;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String MOON = "Selbsttest Mond";
    private static final String EARTH = "Selbsttest Erde";
    private static final String WINDLESS = "Selbsttest Windstill";
    private static final RuleSpec GRAVITY = WorldRuleSpecs.GRAVITY_SCALE;
    private static final RuleSpec WIND = WorldRuleSpecs.WIND;
    private static final RuleSpec DISARM = WorldRuleSpecs.CRASH_DISARM;
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicReference<@Nullable List<PersonalPresets.Preset>> SAVED_PRESETS = new AtomicReference<>();
    private static final AtomicReference<@Nullable Integer> COURSE = new AtomicReference<>();

    private PersonalRulesUiSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(PREFIX + "Tab Welt-Regeln: Schalter „Freigabe“ Fest, Zonen, Spieler je Physikregel, Entwurf bis Speichern, kommt bei Server "
                + "und Client an", 1200, PersonalRulesUiSelfTests::tierTabStep);
        SelfTest.clientStep(PREFIX + "Tab „Meine Regeln“: freigegebene Regeln mit „Server: X“ änderbar, gesperrte nur lesbar, Zurücksetzen folgt dem "
                + "Server, Statuszeile Race Legal bzw. assisted", 1200, PersonalRulesUiSelfTests::myRulesStep);
        SelfTest.clientStep(PREFIX + "Presets: „Race Legal“ fest, eigenes Preset merken, umbenennen, löschen, nicht freigegebene Regel beim Anwenden "
                + "übersprungen mit Hinweis", 1200, PersonalRulesUiSelfTests::presetStep);
        SelfTest.clientStep(PREFIX + "Vorabwarnung nennt die persönliche Abweichung mit Serverwert, Knopf „Race Legal anwenden“ löscht sie", 1200,
                PersonalRulesUiSelfTests::preflightStep);
        SelfTest.clientStep(PREFIX + "Abzeichen „Serverregeln“ an der Bestenliste und an Zeiten mit anderem Regelsatz", 1600,
                PersonalRulesUiSelfTests::badgeStep);
        if (Screenshots.enabled()) {
            shots("");
            SelfTest.switchStep(PREFIX + "Sprache Deutsch (persönliche Regeln)", PREFIX + "Sprache zurücksetzen (persönliche Regeln)", 1200,
                    ctx -> Screenshots.language(ctx, "de_de"));
            shots("_de");
            SelfTest.clientStep(PREFIX + "Sprache zurücksetzen (persönliche Regeln)", 1200, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(PREFIX + "Aufräumen persönliche Regeln", 600, PersonalRulesUiSelfTests::cleanupStep);
    }

    private static void shots(String suffix) {
        String lang = suffix.isEmpty() ? "" : " deutsch";
        Screenshots.step(PREFIX + "Bildschirmfoto Tab Welt-Regeln mit Schalter „Freigabe“" + lang, "personal_rules_tiers" + suffix, 20,
                new TierScene(false));
        Screenshots.step(PREFIX + "Bildschirmfoto Tooltip des Schalters „Freigabe“" + lang, "personal_rules_tiers_tooltip" + suffix, 20,
                new TierScene(true));
        Screenshots.step(PREFIX + "Bildschirmfoto Tab „Meine Regeln“" + lang, "personal_rules_my_rules" + suffix, 20, new MyRulesScene(false));
        Screenshots.step(PREFIX + "Bildschirmfoto Tab „Meine Regeln“, gesperrte Regel mit Tooltip" + lang, "personal_rules_my_rules_locked" + suffix, 20,
                new MyRulesScene(true));
        Screenshots.step(PREFIX + "Bildschirmfoto Vorabwarnung mit „Race Legal anwenden“" + lang, "personal_rules_preflight" + suffix, 20,
                new PreflightScene());
        Screenshots.step(PREFIX + "Bildschirmfoto Bestenliste mit Abzeichen „Serverregeln“" + lang, "personal_rules_badge" + suffix, 20,
                new BadgeScene(false));
        Screenshots.step(PREFIX + "Bildschirmfoto Tooltip des Abzeichens „Serverregeln“" + lang, "personal_rules_badge_tooltip" + suffix, 20,
                new BadgeScene(true));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(result);
    }

    private static void server(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> action) {
        RESULT.set(null);
        ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static StepResult pendingOr(SelfTest.StepContext ctx, int ticks, String failure) {
        return since(ctx) > ticks ? StepResult.fail(failure) : StepResult.pending();
    }

    private static <T extends ConfigTab> @Nullable T tab(String id, Class<T> type) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, id);
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !id.equals(active.id())) {
            screen.selectTab(id);
            return null;
        }
        return type.isInstance(active) ? type.cast(active) : null;
    }

    private static void close(boolean save) {
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            if (save) {
                screen.saveAndClose();
            } else {
                screen.discardAndClose();
            }
        }
    }

    private static StepResult failClosing(String message) {
        close(false);
        return StepResult.fail(message);
    }

    private static void hover(Minecraft mc, double x, double y) {
        double sx = x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double sy = y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), sx, sy, 0.0, 0.0);
    }

    private static boolean tiersAre(Map<RuleSpec, RuleTier> wanted) {
        for (Map.Entry<RuleSpec, RuleTier> entry : wanted.entrySet()) {
            if (ClientPersonalRules.INSTANCE.tier(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static StepResult tierTabStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    for (RuleSpec spec : List.of(GRAVITY, WIND)) {
                        PersonalRulesServer.setTier(server, spec, RuleTier.ZONES, null, "Selbsttest");
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!tiersAre(Map.of(GRAVITY, RuleTier.ZONES, WIND, RuleTier.ZONES))) {
                    return pendingOr(ctx, 200, "Stufe Zonen kommt nicht beim Client an: " + ClientPersonalRules.INSTANCE.tiers());
                }
                WorldRulesTab tab = tab(WorldRulesTab.ID, WorldRulesTab.class);
                if (tab == null || tab.rows().isEmpty()) {
                    return pendingOr(ctx, 300, "Tab Welt-Regeln öffnet nicht");
                }
                List<String> problems = new ArrayList<>();
                int switches = 0;
                for (WorldRulesTab.Row row : tab.rows()) {
                    boolean tierable = row.spec() != null && RuleTiers.tierable(row.spec());
                    RuleTierSwitch tier = row.tier();
                    if (tierable != (tier != null)) {
                        problems.add(row.id() + (tierable ? " ohne Schalter" : " mit Schalter, obwohl immer Fest"));
                    } else if (tier != null) {
                        switches++;
                        if (!tier.active) {
                            problems.add(row.id() + ": Schalter mit Recht nicht bedienbar");
                        }
                    }
                }
                if (switches != 11) {
                    problems.add(switches + " Schalter statt 11");
                }
                WorldRulesTab.Row gravity = tab.row(Identifier.parse(GRAVITY.id())).orElse(null);
                WorldRulesTab.Row wind = tab.row(Identifier.parse(WIND.id())).orElse(null);
                if (gravity == null || gravity.tier() == null || wind == null || wind.tier() == null) {
                    return failClosing("Schwerkraft oder Wind ohne Schalter: " + problems);
                }
                gravity.tier().cycle(1);
                wind.tier().set(RuleTier.FIXED);
                if (gravity.tier().tier() != RuleTier.PLAYERS || wind.tier().tier() != RuleTier.FIXED || tab.tierDraft().size() != 2) {
                    problems.add("Entwurf nach Klick: Schwerkraft " + gravity.tier().tier() + ", Wind " + wind.tier().tier() + " " + tab.tierDraft());
                }
                if (ClientPersonalRules.INSTANCE.tier(GRAVITY) != RuleTier.ZONES) {
                    problems.add("Stufe gilt schon vor dem Speichern");
                }
                String tip = RuleTierSwitch.tooltip(gravity.label(), RuleTier.PLAYERS, true).getString();
                for (RuleTier level : RuleTier.values()) {
                    if (!tip.contains(Component.translatable(level.translationKey()).getString())) {
                        problems.add("Tooltip ohne Stufe " + level + ": " + tip);
                    }
                }
                if (!problems.isEmpty()) {
                    return failClosing(String.join("; ", problems));
                }
                ctx.log(switches + " Schalter „Freigabe“; Tooltip: " + tip.replace('\n', ' '));
                close(true);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!tiersAre(Map.of(GRAVITY, RuleTier.PLAYERS, WIND, RuleTier.FIXED)) || ClientZones.INSTANCE.release().released(WIND)) {
                    return pendingOr(ctx, 200, "gespeicherte Stufen kommen nicht beim Client an: " + ClientPersonalRules.INSTANCE.tiers());
                }
                server(ctx, server -> {
                    RuleTiers tiers = PersonalRulesServer.tiers(server);
                    if (tiers.tier(GRAVITY) != RuleTier.PLAYERS || tiers.tier(WIND) != RuleTier.FIXED) {
                        return "Server hat die Stufen nicht: " + tiers;
                    }
                    PersonalRulesServer.setTier(server, WIND, RuleTier.ZONES, null, "Selbsttest");
                    PersonalRulesServer.setTier(server, GRAVITY, RuleTier.ZONES, null, "Selbsttest");
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult myRulesStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    WorldRules.set(server, GRAVITY, 100);
                    PersonalRulesServer.setTier(server, GRAVITY, RuleTier.PLAYERS, null, "Selbsttest");
                    PersonalRulesServer.setTier(server, DISARM, RuleTier.PLAYERS, null, "Selbsttest");
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!tiersAre(Map.of(GRAVITY, RuleTier.PLAYERS, DISARM, RuleTier.PLAYERS))) {
                    return pendingOr(ctx, 200, "Freigabe für Spieler kommt nicht beim Client an");
                }
                MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
                if (tab == null || tab.rows().isEmpty()) {
                    return pendingOr(ctx, 300, "Tab „Meine Regeln“ öffnet nicht");
                }
                List<String> problems = new ArrayList<>();
                MyRulesTab.Row gravity = tab.row(GRAVITY).orElse(null);
                MyRulesTab.Row wind = tab.row(WIND).orElse(null);
                if (tab.rows().size() != 11 || gravity == null || wind == null) {
                    return failClosing(tab.rows().size() + " Zeilen statt aller 11 Physikregeln");
                }
                if (!gravity.editable() || gravity.reset() == null || gravity.reset().active) {
                    problems.add("Schwerkraft nicht änderbar oder Zurücksetzen ohne Abweichung aktiv");
                }
                if (wind.editable() || wind.reset() != null) {
                    problems.add("Wind (Stufe Zonen) änderbar");
                }
                if (!MyRulesTab.serverText(GRAVITY, 100).getString().contains("100")) {
                    problems.add("Referenz „Server: X“ fehlt: " + MyRulesTab.serverText(GRAVITY, 100).getString());
                }
                if (tab.draft().status() != PersonalRulesDraft.Status.RACE_LEGAL || !tab.status().getString().contains("✓")) {
                    problems.add("Status ohne Abweichung: " + tab.status().getString());
                }
                tab.set(GRAVITY, 130);
                tab.set(WIND, 0);
                if (tab.draft().status() != PersonalRulesDraft.Status.ASSISTED || tab.draft().value(WIND) != 1) {
                    problems.add("nach Schwerkraft 130 %: " + tab.status().getString() + ", Wind " + tab.draft().value(WIND));
                }
                tab.set(DISARM, 0);
                if (!tab.draft().desired().equals(Map.of(GRAVITY.name(), 130, DISARM.name(), 0))) {
                    problems.add("Entwurf: " + tab.draft().desired());
                }
                if (!problems.isEmpty()) {
                    return failClosing(String.join("; ", problems));
                }
                ctx.log("Status mit Abweichung: " + tab.status().getString());
                close(true);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                PersonalRules own = ClientPersonalRules.INSTANCE.own();
                if (!own.values().equals(Map.of(GRAVITY.name(), 130, DISARM.name(), 0))) {
                    return pendingOr(ctx, 200, "gespeicherte Abweichungen kommen nicht an: " + own);
                }
                server(ctx, server -> {
                    PersonalRules stored = PersonalRulesServer.own(server, player(server).getUUID());
                    return stored.values().equals(Map.of(GRAVITY.name(), 130, DISARM.name(), 0)) ? null : "Server speichert " + stored;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
                if (tab == null) {
                    return pendingOr(ctx, 300, "Tab „Meine Regeln“ öffnet nicht erneut");
                }
                MyRulesTab.Row gravity = tab.row(GRAVITY).orElse(null);
                FlatButton reset = gravity == null ? null : gravity.reset();
                if (reset == null || !reset.active) {
                    return failClosing("Zurücksetzen bei Abweichung nicht aktiv");
                }
                reset.press();
                if (tab.draft().value(GRAVITY) != 100 || tab.draft().deviates(GRAVITY)) {
                    return failClosing("Zurücksetzen setzt nicht auf den Serverwert: " + tab.draft().value(GRAVITY));
                }
                close(true);
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                PersonalRules own = ClientPersonalRules.INSTANCE.own();
                if (!own.values().equals(Map.of(DISARM.name(), 0))) {
                    return pendingOr(ctx, 200, "Zurücksetzen kommt nicht an: " + own);
                }
                server(ctx, server -> {
                    WorldRules.set(server, GRAVITY, 90);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (ClientPersonalRules.INSTANCE.effective(GRAVITY) != 90) {
                    return pendingOr(ctx, 200, "nach dem Zurücksetzen folgt die Schwerkraft nicht dem Server: " + ClientPersonalRules.INSTANCE.effective(GRAVITY));
                }
                MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
                if (tab == null) {
                    return pendingOr(ctx, 300, "Tab „Meine Regeln“ öffnet nicht");
                }
                if (tab.draft().status() != PersonalRulesDraft.Status.NEUTRAL || tab.draft().value(GRAVITY) != 90) {
                    return failClosing("Status nur mit „Disarm on crash“: " + tab.status().getString() + ", Schwerkraft " + tab.draft().value(GRAVITY));
                }
                ctx.log("nur neutrale Abweichung: " + tab.status().getString());
                close(false);
                server(ctx, server -> {
                    WorldRules.set(server, GRAVITY, GRAVITY.defaultValue());
                    PersonalRulesDev.clear(server);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult presetStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                SAVED_PRESETS.compareAndSet(null, PersonalPresetStore.all());
                PersonalPresetStore.replace(List.of(new PersonalPresets.Preset(WINDLESS, Map.of(WIND.name(), 0, GRAVITY.name(), 110))));
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    PersonalRulesServer.setTier(server, GRAVITY, RuleTier.PLAYERS, null, "Selbsttest");
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!tiersAre(Map.of(GRAVITY, RuleTier.PLAYERS, WIND, RuleTier.ZONES))) {
                    return pendingOr(ctx, 200, "Freigabe der Schwerkraft kommt nicht an");
                }
                MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
                if (tab == null || tab.presetChoice() == null) {
                    return pendingOr(ctx, 300, "Tab „Meine Regeln“ ohne Presets");
                }
                List<String> problems = new ArrayList<>();
                tab.choose(PersonalPresets.RACE_LEGAL);
                if (tab.renameButton() == null || tab.renameButton().active || tab.deleteButton() == null || tab.deleteButton().active) {
                    problems.add("„Race Legal“ lässt sich umbenennen oder löschen");
                }
                tab.set(GRAVITY, 120);
                tab.setName(MOON);
                PersonalPresets.Change saved = tab.savePreset();
                if (saved.outcome() != PersonalPresets.Outcome.SAVED || PersonalPresets.find(PersonalPresetStore.all(), MOON).map(p -> p.values()
                        .equals(Map.of(GRAVITY.name(), 120))).orElse(false) != Boolean.TRUE) {
                    problems.add("Preset merken: " + saved.outcome() + " " + PersonalPresetStore.all());
                }
                tab.setName(PersonalPresets.RACE_LEGAL_NAME);
                if (tab.renamePreset().outcome() != PersonalPresets.Outcome.RESERVED) {
                    problems.add("Umbenennen in „Race Legal“ nicht abgelehnt");
                }
                tab.setName(EARTH);
                if (tab.renamePreset().outcome() != PersonalPresets.Outcome.RENAMED || PersonalPresets.find(PersonalPresetStore.all(), EARTH).isEmpty()) {
                    problems.add("Umbenennen: " + PersonalPresetStore.all());
                }
                tab.deletePreset();
                if (PersonalPresets.find(PersonalPresetStore.all(), EARTH).isEmpty()) {
                    problems.add("Löschen ohne zweiten Klick");
                }
                if (tab.deletePreset().outcome() != PersonalPresets.Outcome.DELETED || PersonalPresets.find(PersonalPresetStore.all(), EARTH).isPresent()) {
                    problems.add("Löschen mit zweitem Klick: " + PersonalPresetStore.all());
                }
                PersonalPresets.Preset windless = PersonalPresets.find(PersonalPresetStore.all(), WINDLESS).orElse(null);
                if (windless == null) {
                    return failClosing("Preset „" + WINDLESS + "“ fehlt: " + PersonalPresetStore.all());
                }
                tab.choose(windless);
                PersonalPresets.Applied applied = tab.applyPreset();
                if (!applied.skipped().equals(List.of(WIND)) || !tab.lastFlash().getString().contains(Component.translatable(WIND.translationKey()).getString())) {
                    problems.add("Anwenden überspringt Wind nicht mit Hinweis: " + applied + " / " + tab.lastFlash().getString());
                }
                if (!problems.isEmpty()) {
                    return failClosing(String.join("; ", problems));
                }
                ctx.log("Hinweis beim Anwenden: " + tab.lastFlash().getString());
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!ClientPersonalRules.INSTANCE.own().values().equals(Map.of(GRAVITY.name(), 110))) {
                    return pendingOr(ctx, 200, "Preset kommt nicht an: " + ClientPersonalRules.INSTANCE.own());
                }
                MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
                if (tab == null) {
                    return pendingOr(ctx, 300, "Tab „Meine Regeln“ weg");
                }
                tab.choose(PersonalPresets.RACE_LEGAL);
                PersonalPresets.Applied legal = tab.applyPreset();
                if (!legal.values().isEmpty() || !legal.skipped().isEmpty()) {
                    return failClosing("Race Legal hat Werte: " + legal);
                }
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (!ClientPersonalRules.INSTANCE.own().isEmpty()) {
                    return pendingOr(ctx, 200, "Race Legal löscht die Abweichungen nicht: " + ClientPersonalRules.INSTANCE.own());
                }
                MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
                if (tab == null) {
                    return pendingOr(ctx, 300, "Tab „Meine Regeln“ weg");
                }
                if (tab.draft().status() != PersonalRulesDraft.Status.RACE_LEGAL) {
                    return failClosing("Status nach Race Legal: " + tab.status().getString());
                }
                close(false);
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult preflightStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    PersonalRulesDev.personal(server, player(server).getUUID(), Map.of(GRAVITY, 130));
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (ClientPersonalRules.INSTANCE.effective(GRAVITY) != 130) {
                    return pendingOr(ctx, 200, "persönliche Schwerkraft 130 % kommt nicht an");
                }
                RaceConfigTab tab = tab("race", RaceConfigTab.class);
                if (tab == null) {
                    return pendingOr(ctx, 300, "Tab Rennen öffnet nicht");
                }
                AssistPreflight.Result result = AssistPreflight.check(Minecraft.getInstance());
                AssistReasons reasons = result.reasons();
                List<AssistDetail> details = reasons.details(LapAssist.PERSONAL_RULES);
                if (!details.equals(List.of(AssistDetail.personal(GRAVITY, 130, 100)))) {
                    return failClosing("Vorabwarnung nennt die persönliche Schwerkraft nicht: " + reasons);
                }
                String text = AssistWords.reason(LapAssist.PERSONAL_RULES, details, true).getString();
                String name = Component.translatable(GRAVITY.translationKey()).getString();
                if (!text.contains(name) || !text.contains("130") || !text.contains("100")) {
                    return failClosing("Text der Flughilfe ohne Regel, Wert und Serverwert: " + text);
                }
                FlatButton legal = tab.raceLegalButton();
                if (legal == null || !legal.visible || !legal.active) {
                    return failClosing("Knopf „Race Legal anwenden“ fehlt");
                }
                ctx.log("Vorabwarnung: " + text);
                legal.press();
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!ClientPersonalRules.INSTANCE.own().isEmpty() || AssistText.preflight(null, null, HoldMode.OFF).has(LapAssist.PERSONAL_RULES)) {
                    return pendingOr(ctx, 200, "„Race Legal anwenden“ löscht die Abweichung nicht: " + ClientPersonalRules.INSTANCE.own());
                }
                RaceConfigTab tab = tab("race", RaceConfigTab.class);
                if (tab == null) {
                    return pendingOr(ctx, 300, "Tab Rennen weg");
                }
                tab.tick();
                if (tab.raceLegalButton() != null && tab.raceLegalButton().visible) {
                    return pendingOr(ctx, 100, "Knopf bleibt ohne Abweichung sichtbar");
                }
                close(false);
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult badgeStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    WorldRules.set(server, WIND, WIND.defaultValue());
                    WorldRules.set(server, GRAVITY, GRAVITY.defaultValue());
                    String flown = RaceRulesSelfTests.fly(server, 16 * S);
                    if (flown != null) {
                        return "Runde mit Standardregeln: " + flown;
                    }
                    WorldRules.set(server, GRAVITY, 80);
                    COURSE.set(RaceRulesSelfTests.course(server).id);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (ClientWorldRules.INSTANCE.get(GRAVITY) != 80) {
                    return pendingOr(ctx, 200, "Server-Schwerkraft 80 % kommt nicht an");
                }
                RaceRules.Rule gravity = RaceRules.find(GRAVITY.id()).orElseThrow();
                List<ServerRulesBadge.Line> board = ServerRulesText.board();
                if (!board.equals(List.of(new ServerRulesBadge.Line(gravity, 80, 100)))) {
                    return StepResult.fail("Abzeichen der Bestenliste listet nicht Schwerkraft 80 statt 100: " + board);
                }
                Integer course = COURSE.get();
                if (course == null) {
                    return StepResult.fail("kein Kurs");
                }
                if (!(mc.gui.screen() instanceof LeaderboardScreen)) {
                    mc.gui.setScreen(new LeaderboardScreen(null, course));
                    return StepResult.pending();
                }
                LeaderboardScreen screen = (LeaderboardScreen) mc.gui.screen();
                if (screen.rowsDrawn() < 2 || screen.frames() < 3) {
                    return pendingOr(ctx, 300, "Bestenliste zeigt die Zeiten nicht: " + screen.rowsDrawn());
                }
                if (screen.rulesBadgeLeft() < 0 || screen.rulesBadgesDrawn() < 1) {
                    return pendingOr(ctx, 100, "Abzeichen fehlt: Kopfzeile " + screen.rulesBadgeLeft() + ", Zeilen " + screen.rulesBadgesDrawn());
                }
                List<ServerRulesBadge.Line> entry = ServerRulesText.entry(RaceStandard.DEFAULTS);
                String tip = ServerRulesText.entryTooltip(entry).getString();
                if (entry.size() != 1 || !tip.contains(Component.translatable(GRAVITY.translationKey()).getString())) {
                    return StepResult.fail("Tooltip der Zeit mit anderem Regelsatz: " + tip);
                }
                ctx.log("Kopfzeile mit Abzeichen bei x=" + screen.rulesBadgeLeft() + ", " + screen.rulesBadgesDrawn() + " Zeit(en) mit Abzeichen; "
                        + ServerRulesText.boardTooltip(board).getString().replace('\n', ' '));
                server(ctx, server -> {
                    WorldRules.set(server, GRAVITY, GRAVITY.defaultValue());
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (ClientWorldRules.INSTANCE.get(GRAVITY) != 100 || !(mc.gui.screen() instanceof LeaderboardScreen screen)) {
                    return pendingOr(ctx, 200, "Server-Schwerkraft 100 % kommt nicht an oder Bestenliste zu");
                }
                if (screen.rulesBadgeLeft() >= 0) {
                    return pendingOr(ctx, 100, "Abzeichen der Kopfzeile bleibt bei Standardregeln");
                }
                mc.gui.setScreen(null);
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            List<PersonalPresets.Preset> saved = SAVED_PRESETS.getAndSet(null);
            if (saved != null) {
                PersonalPresetStore.replace(saved);
            }
            close(false);
            server(ctx, server -> {
                PersonalRulesDev.clear(server);
                WorldRules.set(server, WIND, WIND.defaultValue());
                WorldRules.set(server, GRAVITY, GRAVITY.defaultValue());
                return null;
            });
            next(ctx);
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void setupScene(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> action) {
        if (ctx.tick() == 0) {
            server(ctx, action);
        }
    }

    private static final class TierScene implements Screenshots.Scene {
        private final boolean tooltip;

        TierScene(boolean tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            setupScene(ctx, server -> {
                PersonalRulesDev.clear(server);
                PersonalRulesServer.setTier(server, GRAVITY, RuleTier.PLAYERS, null, "Selbsttest");
                PersonalRulesServer.setTier(server, WIND, RuleTier.FIXED, null, "Selbsttest");
                return null;
            });
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (!tiersAre(Map.of(GRAVITY, RuleTier.PLAYERS, WIND, RuleTier.FIXED))) {
                return ctx.tick() > 300 ? StepResult.fail("Stufen kommen nicht an") : StepResult.pending();
            }
            WorldRulesTab tab = tab(WorldRulesTab.ID, WorldRulesTab.class);
            if (tab == null || tab.rows().isEmpty()) {
                return ctx.tick() > 300 ? StepResult.fail("Tab öffnet nicht") : StepResult.pending();
            }
            WorldRulesTab.Row gravity = tab.row(Identifier.parse(GRAVITY.id())).orElse(null);
            WorldRulesTab.Row wind = tab.row(Identifier.parse(WIND.id())).orElse(null);
            if (gravity == null || wind == null) {
                return StepResult.fail("Schwerkraft oder Wind fehlt");
            }
            tab.reveal(gravity);
            tab.reveal(wind);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            WorldRulesTab tab = mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof WorldRulesTab rules ? rules : null;
            WorldRulesTab.Row gravity = tab == null ? null : tab.row(Identifier.parse(GRAVITY.id())).orElse(null);
            if (!this.tooltip || gravity == null || gravity.tier() == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            hover(mc, gravity.tier().getX() + gravity.tier().getWidth() / 2.0, gravity.tier().getY() + gravity.tier().getHeight() / 2.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close(false);
            ctx.onServer(server -> {
                PersonalRulesServer.setTier(server, WIND, RuleTier.ZONES, null, "Selbsttest");
                PersonalRulesDev.clear(server);
            });
        }
    }

    private static final class MyRulesScene implements Screenshots.Scene {
        private final boolean locked;

        MyRulesScene(boolean locked) {
            this.locked = locked;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            setupScene(ctx, server -> {
                PersonalRulesDev.clear(server);
                UUID id = player(server).getUUID();
                PersonalRulesDev.personal(server, id, Map.of(GRAVITY, 130, WIND, 0, DISARM, 0));
                PersonalRulesServer.setTier(server, WIND, RuleTier.FIXED, null, "Selbsttest");
                PersonalRulesServer.setTier(server, WorldRuleSpecs.BATTERY_SCALE, RuleTier.PLAYERS, null, "Selbsttest");
                return null;
            });
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (ctx.tick() == 0) {
                SAVED_PRESETS.compareAndSet(null, PersonalPresetStore.all());
                PersonalPresetStore.replace(List.of(new PersonalPresets.Preset(MOON, Map.of(GRAVITY.name(), 30)),
                        new PersonalPresets.Preset(WINDLESS, Map.of(WIND.name(), 0))));
            }
            if (ClientPersonalRules.INSTANCE.tier(WIND) != RuleTier.FIXED || ClientPersonalRules.INSTANCE.effective(GRAVITY) != 130) {
                return ctx.tick() > 300 ? StepResult.fail("persönliche Werte kommen nicht an") : StepResult.pending();
            }
            MyRulesTab tab = tab(MyRulesTab.ID, MyRulesTab.class);
            if (tab == null || tab.rows().isEmpty()) {
                return ctx.tick() > 300 ? StepResult.fail("Tab öffnet nicht") : StepResult.pending();
            }
            if (this.locked) {
                tab.row(GRAVITY).ifPresent(tab::reveal);
                tab.row(WorldRuleSpecs.BATTERY_SCALE).ifPresent(tab::reveal);
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            MyRulesTab tab = mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof MyRulesTab rules ? rules : null;
            MyRulesTab.Row wind = tab == null ? null : tab.row(WIND).orElse(null);
            if (!this.locked || tab == null || wind == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            hover(mc, tab.left() + 30, tab.screenY(wind) + 6);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close(false);
            List<PersonalPresets.Preset> saved = SAVED_PRESETS.getAndSet(null);
            if (saved != null) {
                PersonalPresetStore.replace(saved);
            }
            ctx.onServer(server -> {
                PersonalRulesServer.setTier(server, WIND, RuleTier.ZONES, null, "Selbsttest");
                PersonalRulesDev.clear(server);
            });
        }
    }

    private static final class PreflightScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            setupScene(ctx, server -> {
                PersonalRulesDev.clear(server);
                PersonalRulesDev.personal(server, player(server).getUUID(), Map.of(GRAVITY, 130, WorldRuleSpecs.BATTERY_SCALE, 0));
                return null;
            });
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (ClientPersonalRules.INSTANCE.effective(WorldRuleSpecs.BATTERY_SCALE) != 0) {
                return ctx.tick() > 300 ? StepResult.fail("persönliche Werte kommen nicht an") : StepResult.pending();
            }
            RaceConfigTab tab = tab("race", RaceConfigTab.class);
            if (tab == null) {
                return ctx.tick() > 300 ? StepResult.fail("Tab Rennen öffnet nicht") : StepResult.pending();
            }
            tab.tick();
            if (tab.raceLegalButton() != null && tab.raceLegalButton().visible) {
                showButton(tab);
                return StepResult.pass();
            }
            return ctx.tick() > 300 ? StepResult.fail("Knopf fehlt") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        private static void showButton(RaceConfigTab tab) {
            AssistCheckSelfTests.scrollToCheck();
            FlatButton button = tab.raceLegalButton();
            if (button != null && Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen && screen.contentScroll() != null) {
                screen.contentScroll().reveal(button);
                screen.contentScroll().wheel(1.0);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close(false);
            ctx.onServer(PersonalRulesDev::clear);
        }
    }

    private static final class BadgeScene implements Screenshots.Scene {
        private final boolean tooltip;

        BadgeScene(boolean tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            setupScene(ctx, server -> {
                WorldRules.set(server, WIND, 0);
                WorldRules.set(server, GRAVITY, 80);
                COURSE.set(RaceRulesSelfTests.course(server).id);
                return null;
            });
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            Integer course = COURSE.get();
            if (course == null || ClientWorldRules.INSTANCE.get(GRAVITY) != 80) {
                return ctx.tick() > 300 ? StepResult.fail("Werte kommen nicht an") : StepResult.pending();
            }
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof LeaderboardScreen screen)) {
                mc.gui.setScreen(new LeaderboardScreen(null, course));
                return StepResult.pending();
            }
            return screen.rulesBadgeLeft() >= 0 && screen.rowsDrawn() > 0 ? StepResult.pass()
                    : ctx.tick() > 400 ? StepResult.fail("Abzeichen fehlt") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.tooltip && mc.gui.screen() instanceof LeaderboardScreen screen && screen.rulesBadgeLeft() >= 0) {
                hover(mc, screen.rulesBadgeLeft() + 10, screen.rulesBadgeCenterY());
            } else {
                Screenshots.parkMouse(mc);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            ctx.onServer(server -> {
                WorldRules.set(server, WIND, WIND.defaultValue());
                WorldRules.set(server, GRAVITY, GRAVITY.defaultValue());
            });
        }
    }
}
