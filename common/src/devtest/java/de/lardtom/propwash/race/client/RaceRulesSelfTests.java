package de.lardtom.propwash.race.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.race.RaceStandardInfo;
import de.lardtom.propwash.dev.PersonalRulesDev;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceRulesJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceRules;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.client.ClientZones;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class RaceRulesSelfTests {
    public static final String PREFIX = "race-rules: ";
    private static final long S = 1_000_000_000L;
    private static final long MS = 1_000_000L;
    private static final int SITE_OFFSET = 48;
    private static final int SITE_LIFT = 30;
    private static final int PLATFORM = 7;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final List<Integer> ENTITIES = new ArrayList<>();
    private static final AtomicReference<@Nullable Integer> COURSE = new AtomicReference<>();
    private static final AtomicReference<@Nullable BlockPos> SITE = new AtomicReference<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static final AtomicReference<@Nullable Integer> BLOCK_COURSE = new AtomicReference<>();
    private static final AtomicReference<@Nullable GateGeometry> IN_BLOCK = new AtomicReference<>();

    private RaceRulesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Runde mit „Disarm on crash“ an und aus zählt rennfrei, die Flughilfe-Prüfung nennt die Regel nicht", 1200,
                RaceRulesSelfTests::disarmStep);
        SelfTest.clientStep(PREFIX + "Rennstandard = Serverregeln: Server-Wind 0 rennfrei, persönlicher Wind 0 mit Flughilfe, gleicher Wert wie der Server "
                + "zählt als Serverwert", 1200, RaceRulesSelfTests::standardStep);
        SelfTest.clientStep(PREFIX + "Freigabestufe Fest sperrt Zonen und persönliche Werte, erneute Freigabe wirkt wieder, Befehl für die Konsole", 1200,
                RaceRulesSelfTests::tierStep);
        SelfTest.clientStep(PREFIX + "Tab Welt-Regeln: Abschnitt „Rennstandard“, Übernehmen und Zurücksetzen über die Knöpfe", 1200,
                RaceRulesSelfTests::tabStep);
        SelfTest.clientStep(PREFIX + "Gate in Gate nur mit Welt-Regel „Kurs-Elemente frei platzieren“", 1200, RaceRulesSelfTests::overlapStep);
        SelfTest.clientStep(PREFIX + "Gate und Fahne in Blöcken nur mit Welt-Regel „Kurs-Elemente frei platzieren“, Runde durch das Gate im Block zählt",
                1200, RaceRulesSelfTests::inBlockStep);
        if (Screenshots.enabled()) {
            Screenshots.step(PREFIX + "Bildschirmfoto Abschnitt Rennstandard", "race_rules_standard", 20, new TabScene());
            SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            Screenshots.step(PREFIX + "Bildschirmfoto Abschnitt Rennstandard deutsch", "race_rules_standard_de", 20, new TabScene());
            SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
        PersonalRulesUiSelfTests.register();
        PersonalRulesFlightSelfTests.register();
        CreativeFcSelfTests.register();
        SelfTest.clientStep(PREFIX + "ui-scale-Fall „" + PersonalRulesUiScaleCase.NAME + "“ stellt beim Schließen Werte, Liste „Spieler“, "
                + "Freigabestufen und Zonen-Freigabe wieder her", 600, PersonalRulesUiScaleCase::restoreStep);
        SelfTest.clientStep(PREFIX + "Aufräumen", 600, RaceRulesSelfTests::cleanupStep);
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

    private static CourseRegistry registry() {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        return registry;
    }

    static CourseRegistry.Course course(MinecraftServer server) {
        Integer id = COURSE.get();
        CourseRegistry.Course course = id == null ? null : registry().course(id);
        if (course != null) {
            return course;
        }
        ServerPlayer player = player(server);
        List<GateEntity> spawned = new ArrayList<>();
        CourseRegistry.Course built = RaceDataJoinCheck.build(player, RaceDataJoinCheck.origin(player), spawned);
        for (GateEntity gate : spawned) {
            ENTITIES.add(gate.getId());
        }
        if (built == null) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        COURSE.set(built.id);
        return built;
    }

    static @Nullable String fly(MinecraftServer server, long lapNanos) {
        ServerPlayer player = player(server);
        CourseRegistry.Course course = course(server);
        return RaceRulesJoinCheck.fly(course, player, RaceDataJoinCheck.origin(player), lapNanos);
    }

    private static Leaderboard board(MinecraftServer server) {
        return registry().board(course(server));
    }

    private static long best(MinecraftServer server) {
        Leaderboard.Entry entry = board(server).best(player(server).getUUID(), null);
        return entry == null ? 0L : entry.nanos();
    }

    private static long bestAssisted(MinecraftServer server) {
        Leaderboard.Entry entry = board(server).bestAssisted(player(server).getUUID(), null);
        return entry == null ? 0L : entry.nanos();
    }

    private static String rated(MinecraftServer server, long nanos) {
        Leaderboard board = board(server);
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (entry.player().equals(player(server).getUUID()) && entry.nanos() == nanos) {
                return board.scoring().clean(entry) ? "rennfrei" : entry.hidden() ? "mit Flughilfe (intern)" : "mit Flughilfe";
            }
        }
        return "fehlt";
    }

    private static String seconds(long nanos) {
        return nanos == 0L ? "-" : String.format(Locale.ROOT, "%.1f s", nanos / 1.0e9);
    }

    private static StepResult disarmStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 0);
                    WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 0);
                    RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Selbsttest");
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (ClientWorldRules.INSTANCE.on(WorldRuleSpecs.CRASH_DISARM)) {
                    return since(ctx) > 200 ? StepResult.fail("„Disarm on crash“ aus kommt nicht beim Client an") : StepResult.pending();
                }
                AssistReasons reasons = AssistText.preflight(null, null, HoldMode.OFF);
                if (reasons.has(LapAssist.WORLD_PHYSICS)) {
                    return StepResult.fail("Flughilfe-Prüfung nennt „Disarm on crash“: " + reasons);
                }
                Component note = RaceStandardSection.ruleNote(Identifier.parse(WorldRuleSpecs.CRASH_DISARM.id()));
                if (!(note != null && note.getContents() instanceof TranslatableContents text && text.getKey().endsWith("tooltip.neutral"))) {
                    return StepResult.fail("Tooltip von „Disarm on crash“ nennt die Regel nicht neutral: " + note);
                }
                server(ctx, server -> {
                    String flown = fly(server, 9 * S);
                    if (flown != null) {
                        return "Runde mit „Disarm on crash“ aus: " + flown;
                    }
                    if (best(server) != 9 * S || bestAssisted(server) != 0L) {
                        return "Runde mit „Disarm on crash“ aus nicht rennfrei: ohne " + seconds(best(server)) + ", stored " + board(server).stored();
                    }
                    WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 1);
                    flown = fly(server, 8 * S);
                    if (flown != null) {
                        return "Runde mit „Disarm on crash“ an: " + flown;
                    }
                    if (best(server) != 8 * S) {
                        return "Runde mit „Disarm on crash“ an nicht rennfrei: " + seconds(best(server));
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                ctx.log("„Disarm on crash“ aus 9,0 s und an 8,0 s, beide ohne Flughilfe; Prüfung und Tooltip nennen die Regel neutral");
                return StepResult.pass();
            }
        }
    }

    private static StepResult standardStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
                    RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Selbsttest");
                    PersonalRulesDev.clear(server);
                    WorldRules.set(server, WorldRuleSpecs.WIND, 0);
                    if (best(server) != 8 * S || !rated(server, 8 * S).equals("rennfrei")) {
                        return "neue Zeit bei Wind 1 nach dem Wechsel der Serverregeln neu eingeordnet: 8,0 s " + rated(server, 8 * S);
                    }
                    String flown = fly(server, 15 * S / 2);
                    if (flown != null) {
                        return "Runde bei Server-Wind 0: " + flown;
                    }
                    Leaderboard.Entry calm = board(server).best(player(server).getUUID(), null);
                    if (calm == null || calm.nanos() != 15 * S / 2 || calm.server() == null || calm.server().values().get("propwash:wind") == null) {
                        return "Runde bei Server-Wind 0 nicht rennfrei mit Regelsatz: " + calm;
                    }
                    RaceStandardInfo info = PropwashApi.race().serverRules(server);
                    if (info.value(WorldRuleSpecs.WIND.id()).orElse(-1) != 0 || info.defaults()) {
                        return "API nennt die Serverregeln nicht: " + info;
                    }
                    WorldRules.set(server, WorldRuleSpecs.WIND, 1);
                    PersonalRulesDev.personal(server, player(server).getUUID(), Map.of(WorldRuleSpecs.WIND, 0));
                    flown = fly(server, 7 * S);
                    if (flown != null) {
                        return "Runde mit persönlichem Wind 0: " + flown;
                    }
                    Leaderboard.Entry personal = board(server).bestAssisted(player(server).getUUID(), null);
                    if (personal == null || personal.nanos() != 7 * S || best(server) != 15 * S / 2
                            || !personal.details().contains(new AssistDetail(LapAssist.PERSONAL_RULES, "wind", 0, 1))
                            || LapAssist.WORLD_PHYSICS.in(personal.assist())) {
                        return "Runde mit persönlichem Wind 0 nicht mit Flughilfe „persönliche Regeln“: " + personal + ", ohne " + seconds(best(server));
                    }
                    WorldRules.set(server, WorldRuleSpecs.WIND, 0);
                    flown = fly(server, 13 * S / 2);
                    if (flown != null) {
                        return "Runde mit persönlichem Wind 0 bei Server-Wind 0: " + flown;
                    }
                    if (best(server) != 13 * S / 2 || bestAssisted(server) != 7 * S) {
                        return "gleicher Wert wie der Server zählt nicht als Serverwert: ohne " + seconds(best(server)) + ", mit " + seconds(bestAssisted(server));
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) != 0 || !ClientPersonalRules.INSTANCE.own().has(WorldRuleSpecs.WIND)
                        || ClientPersonalRules.INSTANCE.tier(WorldRuleSpecs.WIND) != RuleTier.PLAYERS) {
                    return since(ctx) > 200 ? StepResult.fail("Wind 0 und persönlicher Wind kommen nicht beim Client an") : StepResult.pending();
                }
                AssistReasons same = AssistText.preflight(null, null, HoldMode.OFF);
                if (same.has(LapAssist.PERSONAL_RULES) || same.has(LapAssist.WORLD_PHYSICS)) {
                    return StepResult.fail("Flughilfe-Prüfung meldet persönlichen Wind 0 bei Server-Wind 0: " + same);
                }
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.WIND, 1);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) != 1) {
                    return since(ctx) > 200 ? StepResult.fail("Wind 1 kommt nicht beim Client an") : StepResult.pending();
                }
                AssistReasons reasons = AssistText.preflight(null, null, HoldMode.OFF);
                if (!reasons.details(LapAssist.PERSONAL_RULES).equals(List.of(new AssistDetail(LapAssist.PERSONAL_RULES, "wind", 0, 1)))
                        || reasons.has(LapAssist.WORLD_PHYSICS)) {
                    return StepResult.fail("Flughilfe-Prüfung nennt den persönlichen Wind nicht: " + reasons);
                }
                server(ctx, server -> {
                    PersonalRulesDev.clear(server);
                    WorldRules.set(server, WorldRuleSpecs.WIND, WorldRuleSpecs.WIND.defaultValue());
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (!ClientPersonalRules.INSTANCE.own().isEmpty() || AssistText.preflight(null, null, HoldMode.OFF).any()) {
                    return since(ctx) > 200 ? StepResult.fail("nach dem Zurücksetzen noch Flughilfe: " + AssistText.preflight(null, null, HoldMode.OFF))
                            : StepResult.pending();
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                ctx.log("Rennstandard = Serverregeln: 8,0 s bleibt nach dem Wechsel rennfrei, Server-Wind 0 7,5 s rennfrei mit Regelsatz, persönlicher "
                        + "Wind 0 bei Server-Wind 1 7,0 s mit Flughilfe „persönliche Regeln“, persönlicher Wind 0 bei Server-Wind 0 6,5 s rennfrei");
                return StepResult.pass();
            }
        }
    }

    private static StepResult tierStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        RuleSpec gravity = WorldRuleSpecs.GRAVITY_SCALE;
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    UUID id = player(server).getUUID();
                    PersonalRulesDev.clear(server);
                    PersonalRulesDev.personal(server, id, Map.of(gravity, 50));
                    if (PersonalRulesServer.base(server, id).personal().gravityPercent() != 50) {
                        return "persönliche Schwerkraft 50 % auf Stufe Spieler wirkt nicht";
                    }
                    if (!PersonalRulesServer.setTier(server, gravity, RuleTier.FIXED, null, "Selbsttest")) {
                        return "Stufe Fest nicht gesetzt";
                    }
                    if (ZoneData.get(server).release().released(gravity) || PersonalRulesServer.base(server, id).personal().gravityPercent() != 100
                            || !PersonalRulesServer.own(server, id).has(gravity)) {
                        return "Fest: Zonen nicht gesperrt oder Abweichung wirkt noch bzw. ist verloren: " + PersonalRulesServer.own(server, id);
                    }
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
                if (rules.tier(gravity) != RuleTier.FIXED || ClientZones.INSTANCE.release().released(gravity)) {
                    return since(ctx) > 200 ? StepResult.fail("Stufe Fest kommt nicht beim Client an: " + rules.tiers()) : StepResult.pending();
                }
                if (rules.effective(gravity) != 100 || !rules.own().has(gravity) || !rules.locked().equals(List.of(gravity))) {
                    return StepResult.fail("Fest beim Client: wirksam " + rules.effective(gravity) + ", gespeichert " + rules.own());
                }
                server(ctx, server -> {
                    PersonalRulesServer.setTier(server, gravity, RuleTier.PLAYERS, null, "Selbsttest");
                    return PersonalRulesServer.base(server, player(server).getUUID()).personal().gravityPercent() == 50 ? null
                            : "erneute Freigabe: gespeicherte Abweichung wirkt nicht wieder";
                });
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
                if (rules.tier(gravity) != RuleTier.PLAYERS || rules.effective(gravity) != 50) {
                    return since(ctx) > 200 ? StepResult.fail("erneute Freigabe kommt nicht beim Client an: " + rules.tiers()) : StepResult.pending();
                }
                server(ctx, server -> {
                    CommandSourceStack console = server.createCommandSourceStack();
                    server.getCommands().performPrefixedCommand(console, "propwash rules tier wind fixed");
                    if (PersonalRulesServer.tiers(server).tier(WorldRuleSpecs.WIND) != RuleTier.FIXED) {
                        return "Befehl /propwash rules tier wind fixed wirkt nicht";
                    }
                    server.getCommands().performPrefixedCommand(console, "propwash rules tier wind zones");
                    server.getCommands().performPrefixedCommand(console, "propwash rules tier gravity_scale zones");
                    if (PersonalRulesServer.tiers(server).tier(WorldRuleSpecs.WIND) != RuleTier.ZONES
                            || PersonalRulesServer.tiers(server).tier(gravity) != RuleTier.ZONES
                            || PersonalRulesServer.base(server, player(server).getUUID()).personal().gravityPercent() != 100) {
                        return "Befehl zurück auf Zonen wirkt nicht: " + PersonalRulesServer.tiers(server);
                    }
                    PersonalRulesDev.clear(server);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                ctx.log("Stufe Spieler: Schwerkraft 50 % wirkt; Fest: für Zonen gesperrt, Abweichung gespeichert und wirkungslos (Server und Client); "
                        + "wieder Spieler: Abweichung wirkt wieder; /propwash rules tier wind fixed und zurück auf zones");
                return StepResult.pass();
            }
        }
    }

    static @Nullable WorldRulesTab tab() {
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
        return active instanceof WorldRulesTab tab ? tab : null;
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        StepResult result = tabPhase(ctx);
        if (result.status() == StepResult.Status.FAIL && Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        return result;
    }

    private static StepResult tabPhase(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, 80);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) != 80) {
                    return since(ctx) > 200 ? StepResult.fail("Schwerkraft 80 % kommt nicht an") : StepResult.pending();
                }
                WorldRulesTab tab = tab();
                if (tab == null || tab.rows().isEmpty()) {
                    return since(ctx) > 300 ? StepResult.fail("Tab Welt-Regeln öffnet nicht") : StepResult.pending();
                }
                tab.revealRaceStandard();
                FlatButton adopt = tab.raceStandard().adoptButton();
                FlatButton reset = tab.raceStandard().resetButton();
                if (adopt == null || reset == null) {
                    return StepResult.fail("Knöpfe „Übernehmen“ und „Propwash-Standard“ fehlen");
                }
                if (!adopt.active || reset.active) {
                    return since(ctx) > 100 ? StepResult.fail("Knöpfe falsch aktiv: Übernehmen " + adopt.active + ", Standard " + reset.active)
                            : StepResult.pending();
                }
                Component compare = RaceStandardSection.compare(ClientRaceStandard.INSTANCE.standard(), AssistText.worldValues());
                if (!compare.getString().contains("80")) {
                    return StepResult.fail("Vergleich nennt die Schwerkraft nicht: " + compare.getString());
                }
                Component wind = RaceStandardSection.ruleNote(Identifier.parse(WorldRuleSpecs.WIND.id()));
                if (!(wind != null && wind.getContents() instanceof TranslatableContents text && text.getKey().endsWith("tooltip.relevant"))) {
                    return StepResult.fail("Tooltip von Wind nennt die Regel nicht rennrelevant: " + wind);
                }
                ctx.log("Abschnitt bei y=" + tab.raceStandardTop() + ": " + RaceStandardSection.status(ClientRaceStandard.INSTANCE.standard()).getString() + " / "
                        + compare.getString());
                adopt.press();
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                RaceStandard standard = ClientRaceStandard.INSTANCE.standard();
                if (standard.value(RaceRules.find(WorldRuleSpecs.GRAVITY_SCALE.id()).orElseThrow()) != 80) {
                    return since(ctx) > 200 ? StepResult.fail("„Übernehmen“ setzt den Rennstandard nicht: " + standard) : StepResult.pending();
                }
                server(ctx, server -> RaceStandards.of(server).values().equals(Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), 80)) ? null
                        : "Server-Standard nach „Übernehmen“: " + RaceStandards.of(server));
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                WorldRulesTab tab = tab();
                if (tab == null) {
                    return since(ctx) > 200 ? StepResult.fail("Tab nach „Übernehmen“ weg") : StepResult.pending();
                }
                FlatButton adopt = tab.raceStandard().adoptButton();
                FlatButton reset = tab.raceStandard().resetButton();
                if (adopt == null || reset == null || adopt.active || !reset.active) {
                    return since(ctx) > 100 ? StepResult.fail("Knöpfe nach „Übernehmen“: Übernehmen " + (adopt != null && adopt.active) + ", Standard "
                            + (reset != null && reset.active)) : StepResult.pending();
                }
                reset.press();
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                if (!ClientRaceStandard.INSTANCE.standard().isDefault()) {
                    return since(ctx) > 200 ? StepResult.fail("„Propwash-Standard“ setzt nicht zurück") : StepResult.pending();
                }
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
                    return RaceStandards.of(server).isDefault() ? null : "Server-Standard nach Zurücksetzen: " + RaceStandards.of(server);
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static BlockPos site(ServerPlayer player) {
        BlockPos known = SITE.get();
        if (known != null) {
            return known;
        }
        ServerLevel level = player.level();
        int x = player.getBlockX() + SITE_OFFSET;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + SITE_LIFT;
        BlockPos center = new BlockPos(x, y, z);
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        SITE.set(center);
        return center;
    }

    private static List<GateEntity> gatesAt(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(GateEntity.class, new AABB(center).inflate(PLATFORM + 4.0), gate -> !gate.isRemoved());
    }

    private static InteractionResult place(ServerPlayer player, Item item, BlockPos center) {
        List<GateEntity> before = gatesAt(player.level(), center);
        InteractionResult result = CourseElementSelfTests.use(player, new ItemStack(item), center, Direction.UP, 0.0F, 0);
        for (GateEntity gate : gatesAt(player.level(), center)) {
            if (!before.contains(gate)) {
                ENTITIES.add(gate.getId());
            }
        }
        return result;
    }

    private static @Nullable String overlap(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 0);
        BlockPos center = site(player);
        Item large = RaceModule.gateItem(GateSize.LARGE).orElseThrow();
        Item medium = RaceModule.gateItem(GateSize.MEDIUM).orElseThrow();
        Item flag = RaceModule.item(CourseElement.FLAG, GateSize.MEDIUM).orElseThrow();
        if (!place(player, large, center).consumesAction()) {
            return "großes Gate auf der Plattform nicht gesetzt";
        }
        GateEntity outer = gatesAt(level, center).getFirst();
        GateGeometry inner = GateGeometry.of(outer.geometry().x(), outer.geometry().y(), outer.geometry().z(), outer.geometry().yawDegrees(),
                GateSize.MEDIUM);
        if (GateItem.free(level, inner, GateStacking.placedBase(inner), false, null)) {
            return "Gate in Gate ohne Regel frei";
        }
        Component blocked = GateItem.blocked(level, inner, GateStacking.placedBase(inner), false);
        if (!(blocked.getContents() instanceof TranslatableContents text && text.getKey().equals("propwash.race_rules.overlap"))) {
            return "Meldung ohne Regel nennt die Regel nicht: " + blocked;
        }
        if (place(player, medium, center).consumesAction() || gatesAt(level, center).size() != 1) {
            return "Gate in Gate ohne Regel gesetzt";
        }
        if (place(player, flag, center).consumesAction() || gatesAt(level, center).size() != 1) {
            return "Fahne im Gate ohne Regel gesetzt";
        }
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 1);
        if (!GateItem.free(level, inner, GateStacking.placedBase(inner), false, null)) {
            return "Gate in Gate mit Regel nicht frei";
        }
        if (!place(player, medium, center).consumesAction() || gatesAt(level, center).size() != 2) {
            return "Gate in Gate mit Regel nicht gesetzt: " + gatesAt(level, center).size() + " Gates";
        }
        if (!place(player, flag, center).consumesAction() || gatesAt(level, center).size() != 3) {
            return "Fahne im Gate mit Regel nicht gesetzt: " + gatesAt(level, center).size() + " Elemente";
        }
        return null;
    }

    private static StepResult overlapStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, RaceRulesSelfTests::overlap);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!ClientWorldRules.INSTANCE.on(WorldRuleSpecs.FREE_COURSE_PLACEMENT)) {
                    return since(ctx) > 200 ? StepResult.fail("Regel „Kurs-Elemente frei platzieren“ kommt nicht beim Client an") : StepResult.pending();
                }
                Minecraft mc = Minecraft.getInstance();
                BlockPos center = SITE.get();
                if (mc.level == null || center == null) {
                    return StepResult.fail("keine Plattform");
                }
                List<GateEntity> seen = mc.level.getEntitiesOfClass(GateEntity.class, new AABB(center).inflate(PLATFORM + 4.0), gate -> !gate.isRemoved());
                if (seen.size() != 3) {
                    return since(ctx) > 200 ? StepResult.fail("Client sieht " + seen.size() + " statt 3 Elemente") : StepResult.pending();
                }
                GateEntity any = seen.getFirst();
                GateGeometry same = any.geometry();
                if (!GateItem.free(mc.level, same, GateStacking.placedBase(same), false, null)) {
                    return StepResult.fail("Vorschau beim Client zeigt überlappendes Gate als blockiert");
                }
                ctx.log("großes Gate, Gate und Fahne ineinander nur mit Regel gesetzt");
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static void stone(ServerLevel level, BlockPos pos) {
        BLOCKS.add(pos.immutable());
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    private static @Nullable GateEntity placeOn(ServerPlayer player, Item item, BlockPos clicked) {
        ServerLevel level = player.level();
        AABB area = new AABB(clicked).inflate(6.0);
        List<GateEntity> before = level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved());
        InteractionResult result = CourseElementSelfTests.use(player, new ItemStack(item), clicked, Direction.UP, 0.0F, 0);
        GateEntity found = null;
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved())) {
            if (!before.contains(gate)) {
                ENTITIES.add(gate.getId());
                found = gate;
            }
        }
        return result.consumesAction() ? found : null;
    }

    private static boolean inBlocks(ServerLevel level, GateGeometry geometry) {
        return !level.noCollision(null, GateItem.bounds(geometry, GateStacking.placedBase(geometry)).deflate(0.01));
    }

    private static boolean blockMessage(ServerLevel level, GateGeometry geometry) {
        Component blocked = GateItem.blocked(level, geometry, GateStacking.placedBase(geometry), false);
        return blocked.getContents() instanceof TranslatableContents text && text.getKey().equals("propwash.race.gate.blocked");
    }

    private static @Nullable String inBlock(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        WorldRules.set(server, WorldRuleSpecs.WIND, WorldRuleSpecs.WIND.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, WorldRuleSpecs.CRASH_DISARM.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
        RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Selbsttest");
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 0);
        BlockPos center = site(player);
        BlockPos spot = center.offset(-4, 0, -5);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                stone(level, spot.offset(dx, 1, dz));
            }
        }
        Item flag = RaceModule.item(CourseElement.FLAG, GateSize.MEDIUM).orElseThrow();
        if (placeOn(player, flag, spot) != null) {
            return "Fahne im Block ohne Regel gesetzt";
        }
        Vec3 origin = new Vec3(player.getBlockX() - RaceDataJoinCheck.AWAY + 0.5,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX() - RaceDataJoinCheck.AWAY, player.getBlockZ()) + RaceDataJoinCheck.LIFT,
                player.getBlockZ() + 0.5);
        BlockPos base = BlockPos.containing(origin);
        BlockPos support = base.offset(0, -1, 12);
        stone(level, support);
        for (int dy = 0; dy < 4; dy++) {
            stone(level, base.offset(-2, dy, 12));
        }
        GateEntity start = RaceDataJoinCheck.gate(level, origin);
        ENTITIES.add(start.getId());
        Item medium = RaceModule.gateItem(GateSize.MEDIUM).orElseThrow();
        if (placeOn(player, medium, support) != null) {
            return "Gate im Block ohne Regel gesetzt";
        }
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 1);
        GateEntity flagged = placeOn(player, flag, spot);
        if (flagged == null) {
            return "Fahne im Block mit Regel nicht gesetzt";
        }
        if (!inBlocks(level, flagged.geometry())) {
            return "Fahne steckt nicht im Block: " + flagged.geometry();
        }
        GateEntity gate = placeOn(player, medium, support);
        if (gate == null) {
            return "Gate im Block mit Regel nicht gesetzt";
        }
        GateGeometry placed = gate.geometry();
        if (!inBlocks(level, placed)) {
            return "Gate steckt nicht im Block: " + placed;
        }
        if (Math.abs(placed.x() - origin.x) > 1.0E-6 || Math.abs(placed.z() - origin.z - 12.0) > 1.0E-6 || placed.yawDegrees() != start.geometry().yawDegrees()) {
            return "Gate im Block an falscher Stelle: " + placed + " statt bei " + origin.add(0.0, 0.0, 12.0);
        }
        GateGeometry turned = GateGeometry.of(spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 45.0F, GateSize.SMALL);
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 0);
        boolean freeWithout = GateItem.free(level, placed, GateStacking.placedBase(placed), false, gate)
                || GateItem.free(level, turned, GateStacking.placedBase(turned), false, null);
        boolean messageWithout = blockMessage(level, placed) && blockMessage(level, flagged.geometry());
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, 1);
        if (freeWithout || !messageWithout) {
            return "ohne Regel nicht wie bisher: frei " + freeWithout + ", Meldung für Blöcke " + messageWithout;
        }
        if (!GateItem.free(level, placed, GateStacking.placedBase(placed), false, gate)) {
            return "Gate im Block mit Regel nicht frei";
        }
        if (!GateItem.free(level, turned, GateStacking.placedBase(turned), false, null)) {
            return "schräg gedrehtes Gate im Block mit Regel nicht frei";
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setYHeadRot(0.0F);
        CourseToolItem.useOnGate(tool, player, start);
        CourseToolItem.useOnGate(tool, player, gate);
        CourseRegistry.Course course = registry().course(CourseToolItem.courseOf(tool));
        if (course == null || course.gates.size() != 2) {
            return "Kurs mit dem Gate im Block nicht angelegt";
        }
        BLOCK_COURSE.set(course.id);
        registry().setLaps(course, 1);
        String flown = RaceRulesJoinCheck.fly(course, player, origin, 9 * S);
        if (flown != null) {
            return "Runde durch das Gate im Block: " + flown;
        }
        Leaderboard board = registry().board(course);
        Leaderboard.Entry entry = board.best(player.getUUID(), null);
        if (entry == null || !board.scoring().clean(entry.assist(), entry.rules())) {
            return "Runde durch das Gate im Block nicht rennfrei gewertet: " + (entry == null ? "keine Zeit" : entry.assist() + " " + entry.rules());
        }
        IN_BLOCK.set(placed);
        return null;
    }

    private static StepResult inBlockStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, RaceRulesSelfTests::inBlock);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                GateGeometry placed = IN_BLOCK.get();
                Minecraft mc = Minecraft.getInstance();
                if (placed == null || mc.level == null) {
                    return StepResult.fail("kein Gate im Block");
                }
                AABB bounds = GateItem.bounds(placed, GateStacking.placedBase(placed)).deflate(0.01);
                if (!ClientWorldRules.INSTANCE.on(WorldRuleSpecs.FREE_COURSE_PLACEMENT) || mc.level.noCollision(null, bounds)) {
                    return since(ctx) > 200 ? StepResult.fail("Regel oder Block kommt nicht beim Client an") : StepResult.pending();
                }
                if (!GateItem.free(mc.level, placed, GateStacking.placedBase(placed), false, null)) {
                    return StepResult.fail("Vorschau beim Client zeigt das Gate im Block als blockiert");
                }
                ctx.log("Fahne und Gate in Blöcken nur mit Regel gesetzt, ohne Regel Meldung wie bisher, Runde durch das Gate im Block rennfrei");
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
            server(ctx, server -> {
                teardown(server);
                return null;
            });
            next(ctx);
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    static void teardown(MinecraftServer server) {
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, WorldRuleSpecs.SAVE_ASSISTED_LAPS.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, WorldRuleSpecs.CRASH_DISARM.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.WIND, WorldRuleSpecs.WIND.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.FREE_COURSE_PLACEMENT, WorldRuleSpecs.FREE_COURSE_PLACEMENT.defaultValue());
        RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Selbsttest");
        PersonalRulesDev.clear(server);
        ServerLevel level = player(server).level();
        for (int id : ENTITIES) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        ENTITIES.clear();
        Integer course = COURSE.getAndSet(null);
        if (course != null) {
            registry().deleteCourse(course);
        }
        Integer blockCourse = BLOCK_COURSE.getAndSet(null);
        if (blockCourse != null) {
            registry().deleteCourse(blockCourse);
        }
        for (BlockPos pos : BLOCKS) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        BLOCKS.clear();
        IN_BLOCK.set(null);
        BlockPos center = SITE.getAndSet(null);
        if (center != null) {
            for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
                for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                    level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (ctx.tick() == 0) {
                ctx.onServer(server -> {
                    WorldRules.set(server, WorldRuleSpecs.WIND, 0);
                    RaceStandards.set(server, RaceStandard.DEFAULTS.with("gravity_scale", 90), null, "Selbsttest");
                });
            }
            if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) != 0 || ClientRaceStandard.INSTANCE.standard().isDefault()) {
                return ctx.tick() > 300 ? StepResult.fail("Werte kommen nicht an") : StepResult.pending();
            }
            WorldRulesTab tab = tab();
            if (tab == null || tab.rows().isEmpty()) {
                return ctx.tick() > 300 ? StepResult.fail("Tab öffnet nicht") : StepResult.pending();
            }
            tab.revealRaceStandard();
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            ctx.onServer(server -> {
                WorldRules.set(server, WorldRuleSpecs.WIND, WorldRuleSpecs.WIND.defaultValue());
                RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Selbsttest");
            });
        }
    }
}
