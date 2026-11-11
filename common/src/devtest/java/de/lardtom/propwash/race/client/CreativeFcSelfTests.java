package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.CreativeFcJoinCheck;
import de.lardtom.propwash.dev.PersonalRulesDev;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceRulesJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseMenu;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class CreativeFcSelfTests {
    public static final String PREFIX = RaceRulesSelfTests.PREFIX + "Kreativ-FC: ";
    private static final long S = 1_000_000_000L;
    private static final double OFFSET = -40.0;
    private static final int WAIT = 300;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String FRAMES = "frames";
    private static final String LEGAL = "rennfrei mit Kreativ-FC";
    private static final String ASSIST = "mit Flughilfe [CREATIVE_DRONE]";
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicReference<@Nullable Integer> COURSE = new AtomicReference<>();
    private static final AtomicReference<@Nullable Vec3> ORIGIN = new AtomicReference<>();
    private static final List<Integer> ENTITIES = new ArrayList<>();
    private static final String NARROW = "Bestenliste bei GUI 320×240 (Fenster 640x480, Skala 2 und automatisch) und 427×240 mit Abzeichen "
            + "„Serverregeln“: „Kreativ-FC erlaubt“ steht mit in der Kopfzeile, beide innerhalb und ohne Überlappung (bei 427×240 beide in "
            + "voller Länge), Layout ohne Befund (kein gekürzter Text), beide Tooltips erscheinen";
    private static final String NARROW_SHOT = "race_rules_creative_fc_badge_narrow";

    private CreativeFcSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Welt-Regel aus und an × Kurs Welt-Regel, Flughilfe, renntauglich – Runden mit Kreativ-FC am Server gewertet, "
                + "persönliche Regeln bleiben Flughilfe, alte Runden unverändert", 1200, CreativeFcSelfTests::matrixStep);
        SelfTest.clientStep(PREFIX + "Kurs-Einstellungen: Schalter mit drei Stufen, Admin stellt „Renntauglich“ ein, der Server übernimmt es", 900,
                CreativeFcSelfTests::menuStep);
        SelfTest.clientStep(PREFIX + "Bestenliste zeigt „Kreativ-FC erlaubt“ nur, wenn er renntauglich ist; Vorabwarnung nennt ihn nur als Flughilfe",
                1500, CreativeFcSelfTests::boardStep);
        SelfTest.clientStep(PREFIX + NARROW, NarrowBoard.STEP_TICKS, ctx -> NarrowBoard.run(ctx, NARROW_SHOT));
        if (Screenshots.enabled()) {
            shots("");
            SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            shots("_de");
            SelfTest.clientStep(PREFIX + NARROW + " deutsch", NarrowBoard.STEP_TICKS, ctx -> NarrowBoard.run(ctx, NARROW_SHOT + "_de"));
            SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(PREFIX + "Aufräumen", 600, CreativeFcSelfTests::cleanupStep);
    }

    private static void shots(String suffix) {
        String label = suffix.isEmpty() ? "" : " deutsch";
        Screenshots.step(PREFIX + "Bildschirmfoto Kurs-Einstellungen mit Schalter" + label, "race_rules_creative_fc_menu" + suffix, 20, new MenuScene());
        Screenshots.step(PREFIX + "Bildschirmfoto Bestenliste mit „Kreativ-FC erlaubt“" + label, "race_rules_creative_fc_badge" + suffix, 20,
                new BadgeScene(false));
        Screenshots.step(PREFIX + "Bildschirmfoto Tooltip „Kreativ-FC erlaubt“" + label, "race_rules_creative_fc_badge_tooltip" + suffix, 20,
                new BadgeScene(true));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
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
        Vec3 origin = RaceDataJoinCheck.origin(player).add(0.0, 0.0, OFFSET);
        List<GateEntity> spawned = new ArrayList<>();
        CourseRegistry.Course built = RaceDataJoinCheck.build(player, origin, spawned);
        for (GateEntity gate : spawned) {
            ENTITIES.add(gate.getId());
        }
        if (built == null) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        ORIGIN.set(origin);
        COURSE.set(built.id);
        return built;
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

    private static StepResult next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static StepResult waitFor(SelfTest.StepContext ctx, boolean ready, String problem) {
        if (ready) {
            return next(ctx);
        }
        return since(ctx) > WAIT ? StepResult.fail(problem) : StepResult.pending();
    }

    static String rated(Leaderboard board, UUID player, long nanos) {
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (entry.player().equals(player) && entry.nanos() == nanos) {
                if (board.scoring().clean(entry)) {
                    return entry.creativeFc() ? LEGAL : "rennfrei";
                }
                return "mit Flughilfe " + LapAssist.list(entry.assist());
            }
        }
        return "fehlt";
    }

    private static @Nullable String setting(MinecraftServer server, boolean world, CreativeFcRule rule) {
        WorldRules.set(server, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL, world ? 1 : 0);
        registry().setCreativeFc(course(server), rule);
        return null;
    }

    private static boolean synced(boolean world, CreativeFcRule rule) {
        Integer id = COURSE.get();
        CourseData data = id == null ? null : ClientCourses.get(id);
        return data != null && data.creativeFc() == rule && ClientWorldRules.INSTANCE.known() && ClientCreativeFc.world() == world;
    }

    private static @Nullable String matrix(MinecraftServer server) {
        DroneBuild build = CreativeFcJoinCheck.creativeBuild();
        if (!build.isCreative() || !build.problems().isEmpty()) {
            return "Kreativ-Bau taugt nicht für den Test: " + build.problems();
        }
        ServerPlayer player = player(server);
        UUID id = player.getUUID();
        CourseRegistry.Course course = course(server);
        Vec3 origin = ORIGIN.get();
        Leaderboard board = registry().board(course);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Selbsttest");
        PersonalRulesDev.clear(server);
        List<String> wrong = new ArrayList<>();
        long lap = 20 * S;
        for (boolean world : new boolean[]{false, true}) {
            for (CreativeFcRule rule : CreativeFcRule.values()) {
                setting(server, world, rule);
                String label = "Welt-Regel " + (world ? "an" : "aus") + ", Kurs " + rule.key();
                String flown = RaceRulesJoinCheck.fly(course, player, origin, lap, () -> {
                }, build);
                if (flown != null) {
                    return label + ": " + flown;
                }
                String want = rule.legal(world) ? LEGAL : ASSIST;
                String got = rated(board, id, lap);
                if (!want.equals(got)) {
                    wrong.add(label + ": " + got + " statt " + want);
                }
                lap -= S;
            }
        }
        setting(server, false, CreativeFcRule.LEGAL);
        PersonalRulesDev.personal(server, id, Map.of(WorldRuleSpecs.WIND, 0));
        String flown = RaceRulesJoinCheck.fly(course, player, origin, lap, () -> {
        }, build);
        PersonalRulesDev.clear(server);
        if (flown != null) {
            return "renntauglich mit persönlichem Wind 0: " + flown;
        }
        String personal = rated(board, id, lap);
        if (!personal.equals("mit Flughilfe [PERSONAL_RULES]")) {
            wrong.add("renntauglich mit persönlichem Wind 0: " + personal + " statt nur „persönliche Regeln“");
        }
        long kept = 15 * S;
        setting(server, false, CreativeFcRule.ASSIST);
        Leaderboard.Entry best = board.best(id, null);
        if (best == null || best.nanos() != kept || !best.creativeFc() || !LEGAL.equals(rated(board, id, kept))) {
            wrong.add("alte renntaugliche Runde nach Wechsel auf „Flughilfe“ neu eingeordnet: " + rated(board, id, kept));
        }
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    private static StepResult matrixStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            server(ctx, CreativeFcSelfTests::matrix);
            return next(ctx);
        }
        ctx.log("6 Runden mit Kreativ-FC (20–15 s): Welt-Regel aus → nur „renntauglich“ rennfrei, an → „Welt-Regel“ und „renntauglich“ rennfrei, "
                + "„Flughilfe“ immer mit Flughilfe; renntauglich mit persönlichem Wind 0 nur „persönliche Regeln“; alte Runde bleibt rennfrei");
        return StepResult.pass();
    }

    private static StepResult menuStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> setting(server, false, CreativeFcRule.WORLD));
                return next(ctx);
            }
            case 1 -> {
                return waitFor(ctx, synced(false, CreativeFcRule.WORLD), "Kurs mit „Welt-Regel“ kommt nicht beim Client an");
            }
            case 2 -> {
                Integer id = COURSE.get();
                if (id == null) {
                    return StepResult.fail("kein Kurs");
                }
                if (!(mc.gui.screen() instanceof CourseMenuScreen menu) || menu.courseId() != id) {
                    mc.gui.setScreen(new CourseMenuScreen(null, id));
                    return StepResult.pending();
                }
                if (menu.frames() < 3) {
                    return StepResult.pending();
                }
                List<String> wrong = new ArrayList<>();
                if (!menu.rows().contains(CourseMenu.Row.CREATIVE_FC)) {
                    wrong.add("keine Zeile Kreativ-FC: " + menu.rows());
                }
                if (!menu.rights().admin()) {
                    wrong.add("Host gilt nicht als Admin: " + menu.rights());
                }
                for (CreativeFcRule rule : CreativeFcRule.values()) {
                    FlatButton chip = menu.creativeButton(rule);
                    if (chip == null || !chip.active) {
                        wrong.add("Stufe " + rule.key() + (chip == null ? " fehlt" : " nicht aktiv"));
                    }
                }
                if (menu.shownCreativeFc() != CreativeFcRule.WORLD) {
                    wrong.add("zeigt " + menu.shownCreativeFc() + " statt Welt-Regel");
                }
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, menu);
                if (!findings.isEmpty()) {
                    wrong.add(findings.size() + " Befunde im Layout: " + findings.getFirst().describe());
                }
                if (!wrong.isEmpty()) {
                    return StepResult.fail(String.join("; ", wrong));
                }
                if (!menu.setCreativeFc(CreativeFcRule.LEGAL)) {
                    return StepResult.fail("„Renntauglich“ lässt sich nicht wählen");
                }
                return next(ctx);
            }
            case 3 -> {
                return waitFor(ctx, synced(false, CreativeFcRule.LEGAL), "„Renntauglich“ kommt nicht vom Server zurück");
            }
            case 4 -> {
                server(ctx, server -> course(server).creativeFc() == CreativeFcRule.LEGAL && registry().creativeFcLegal(course(server)) ? null
                        : "Server hat „Renntauglich“ nicht gespeichert: " + course(server).creativeFc());
                return next(ctx);
            }
            default -> {
                if (!(mc.gui.screen() instanceof CourseMenuScreen menu) || menu.shownCreativeFc() != CreativeFcRule.LEGAL) {
                    return StepResult.fail("Menü zeigt nicht „Renntauglich“");
                }
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, menu);
                mc.gui.setScreen(null);
                if (!findings.isEmpty()) {
                    return StepResult.fail(findings.size() + " Befunde nach dem Umstellen: " + findings.getFirst().describe());
                }
                ctx.log("drei Stufen aktiv für den Admin, „Renntauglich“ im Menü gestellt, Server und Client übernehmen es, Layout ohne Befund");
                return StepResult.pass();
            }
        }
    }

    private static @Nullable String expectBoard(Minecraft mc, int course, boolean legal) {
        if (!(mc.gui.screen() instanceof LeaderboardScreen screen) || screen.courseId() != course) {
            mc.gui.setScreen(new LeaderboardScreen(null, course));
            return "Bestenliste öffnet";
        }
        if (screen.creativeBadgeLeft() >= 0 != legal) {
            return "Abzeichen " + (legal ? "fehlt" : "steht da, obwohl der Kreativ-FC als Flughilfe zählt");
        }
        AssistPreflight.Result check = AssistPreflight.check(mc, course);
        if (check.reasons().has(LapAssist.CREATIVE_DRONE) == legal) {
            return "Vorabwarnung " + (legal ? "nennt den renntauglichen Kreativ-FC" : "nennt den Kreativ-FC nicht") + ": " + check.reasons();
        }
        return null;
    }

    private static StepResult boardStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        Integer id = COURSE.get();
        if (id == null) {
            return StepResult.fail("kein Kurs");
        }
        boolean[] worlds = {false, false, true, true};
        CreativeFcRule[] rules = {CreativeFcRule.LEGAL, CreativeFcRule.ASSIST, CreativeFcRule.WORLD, CreativeFcRule.ASSIST};
        int phase = phase(ctx);
        if (phase == 0) {
            server(ctx, server -> {
                ItemStack drone = new ItemStack(DroneModule.DRONE_ITEM.get());
                drone.set(CoreComponents.DRONE_BUILD.get(), CreativeFcJoinCheck.creativeBuild());
                player(server).setItemInHand(InteractionHand.MAIN_HAND, drone);
                return null;
            });
            return next(ctx);
        }
        int round = (phase - 1) / 3;
        if (round >= worlds.length) {
            mc.gui.setScreen(null);
            ctx.log("Abzeichen und Vorabwarnung: renntauglich → Abzeichen, kein Kreativ-FC in der Warnung; Flughilfe → kein Abzeichen, Warnung nennt "
                    + "ihn; Welt-Regel an → Abzeichen; Kurs „Flughilfe“ schlägt die Welt-Regel");
            return StepResult.pass();
        }
        boolean world = worlds[round];
        CreativeFcRule rule = rules[round];
        boolean legal = rule.legal(world);
        switch ((phase - 1) % 3) {
            case 0 -> {
                server(ctx, server -> setting(server, world, rule));
                return next(ctx);
            }
            case 1 -> {
                DroneBuild held = mc.player == null ? null : mc.player.getMainHandItem().get(CoreComponents.DRONE_BUILD.get());
                if (synced(world, rule) && held != null && held.isCreative()) {
                    ctx.state().put(FRAMES, -1L);
                    return next(ctx);
                }
                return since(ctx) > WAIT ? StepResult.fail("Stand " + rule.key() + "/" + world + " oder Kreativ-Drohne kommt nicht an")
                        : StepResult.pending();
            }
            default -> {
                String problem = expectBoard(mc, id, legal);
                long frames = mc.gui.screen() instanceof LeaderboardScreen screen ? screen.frames() : 0L;
                long seen = (long) ctx.state().getOrDefault(FRAMES, -1L);
                if (seen < 0L) {
                    ctx.state().put(FRAMES, frames);
                    return StepResult.pending();
                }
                if (problem == null && frames - seen >= 3) {
                    return next(ctx);
                }
                return since(ctx) > WAIT ? StepResult.fail("Kurs " + rule.key() + ", Welt-Regel " + (world ? "an" : "aus") + ": " + problem)
                        : StepResult.pending();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            Minecraft.getInstance().gui.setScreen(null);
            server(ctx, server -> {
                WorldRules.set(server, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL.defaultValue());
                WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, WorldRuleSpecs.SAVE_ASSISTED_LAPS.defaultValue());
                WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
                player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                CourseRegistry registry = CourseRegistry.get();
                Integer id = COURSE.getAndSet(null);
                if (registry != null && id != null) {
                    registry.deleteCourse(id);
                }
                for (int entity : ENTITIES) {
                    Entity gate = server.overworld().getEntity(entity);
                    if (gate != null) {
                        gate.discard();
                    }
                }
                ENTITIES.clear();
                ORIGIN.set(null);
                return null;
            });
            return next(ctx);
        }
        return StepResult.pass();
    }

    private static final class NarrowBoard {
        static final int STEP_TICKS = 2400;
        private static final String STATE = "narrow_board";
        private static final int[][] WINDOWS = {{640, 480, 2, 0}, {640, 480, 0, 0}, {854, 480, 2, 1}};
        private static final int WINDOW_TICKS = 80;
        private static final int TOOLTIP_TICKS = 60;
        private static final int SETTLE_TICKS = 10;
        private static final int GRAVITY = 80;
        private final String shot;
        private final List<String> notes = new ArrayList<>();
        private int stage;
        private int since;
        private int index;
        private int tips;
        private int @Nullable [] saved;
        private @Nullable CompletableFuture<String> capture;

        private NarrowBoard(String shot) {
            this.shot = shot;
        }

        static StepResult run(SelfTest.StepContext ctx, String shot) {
            NarrowBoard run = (NarrowBoard) ctx.state().computeIfAbsent(STATE, key -> new NarrowBoard(shot));
            Minecraft mc = Minecraft.getInstance();
            try {
                StepResult result = run.advance(ctx, mc);
                if (result.status() == StepResult.Status.FAIL) {
                    run.restoreWindow(mc);
                    ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue()));
                }
                return result;
            } catch (RuntimeException e) {
                run.restoreWindow(mc);
                throw e;
            }
        }

        private void restoreWindow(Minecraft mc) {
            int[] window = this.saved;
            if (window != null) {
                mc.gui.setScreen(null);
                Screenshots.parkMouse(mc);
                mc.options.guiScale().set(window[2]);
                mc.getWindow().setWindowed(window[0], window[1]);
                mc.resizeGui();
                this.saved = null;
            }
        }

        private int[] size() {
            return WINDOWS[this.index];
        }

        private String where(Minecraft mc) {
            Window window = mc.getWindow();
            int[] size = this.size();
            return "GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " (Fenster " + size[0] + "x" + size[1] + ", Skala "
                    + (size[2] == 0 ? "automatisch" : String.valueOf(size[2])) + ", " + mc.getLanguageManager().getSelected() + ")";
        }

        private StepResult stage(SelfTest.StepContext ctx, int stage) {
            this.stage = stage;
            this.since = ctx.tick();
            return StepResult.pending();
        }

        private StepResult waiting(SelfTest.StepContext ctx, int limit, String problem) {
            return ctx.tick() - this.since > limit ? StepResult.fail(problem) : StepResult.pending();
        }

        private static void hover(Minecraft mc, int x, int y) {
            Window window = mc.getWindow();
            mc.mouseHandler.onMove(window.handle(), x * (double) window.getScreenWidth() / window.getGuiScaledWidth(),
                    y * (double) window.getScreenHeight() / window.getGuiScaledHeight(), 0.0, 0.0);
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            StepResult pending = waitFuture(ctx);
            if (pending != null) {
                return pending;
            }
            Window window = mc.getWindow();
            Integer id = COURSE.get();
            switch (this.stage) {
                case 0 -> {
                    this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                    mc.gui.setScreen(null);
                    server(ctx, server -> {
                        setting(server, false, CreativeFcRule.LEGAL);
                        WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, GRAVITY);
                        return null;
                    });
                    return this.stage(ctx, 1);
                }
                case 1 -> {
                    if (id == null || !synced(false, CreativeFcRule.LEGAL) || ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) != GRAVITY
                            || ServerRulesText.board().isEmpty()) {
                        return this.waiting(ctx, WAIT, "Kurs mit „Renntauglich“ oder Server-Schwerkraft " + GRAVITY + " % kommt nicht an");
                    }
                    return this.stage(ctx, 2);
                }
                case 2 -> {
                    if (this.index >= WINDOWS.length) {
                        this.restoreWindow(mc);
                        server(ctx, server -> {
                            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
                            return null;
                        });
                        return this.stage(ctx, 10);
                    }
                    mc.gui.setScreen(null);
                    int[] size = this.size();
                    float density = Math.max(1.0F, window.getPixelDensity());
                    window.setWindowed(Math.round(size[0] / density), Math.round(size[1] / density));
                    return this.stage(ctx, 3);
                }
                case 3 -> {
                    int[] size = this.size();
                    if (window.getWidth() != size[0] || window.getHeight() != size[1]) {
                        return this.waiting(ctx, WINDOW_TICKS, "Fenster wird nicht " + size[0] + "x" + size[1] + ": " + window.getWidth() + "x"
                                + window.getHeight());
                    }
                    mc.options.guiScale().set(size[2]);
                    mc.resizeGui();
                    if (id == null) {
                        return StepResult.fail("kein Kurs");
                    }
                    mc.gui.setScreen(new LeaderboardScreen(null, id));
                    return this.stage(ctx, 4);
                }
                case 4 -> {
                    if (!(mc.gui.screen() instanceof LeaderboardScreen screen) || id == null || screen.courseId() != id || screen.frames() < 3) {
                        return this.waiting(ctx, WAIT, "Bestenliste öffnet nicht " + this.where(mc));
                    }
                    String layout = "Titel bis " + (screen.rulesBadgeLeft() - 6) + ", Abzeichen „Serverregeln“ x=" + screen.rulesBadgeLeft() + "–"
                            + screen.rulesBadgeRight() + ", „Kreativ-FC erlaubt“ x=" + screen.creativeBadgeLeft() + "–" + screen.creativeBadgeRight()
                            + ", Kopfzeile bis " + screen.headerRight();
                    List<String> wrong = new ArrayList<>();
                    if (screen.rulesBadgeLeft() < 0) {
                        wrong.add("Abzeichen „Serverregeln“ fehlt");
                    }
                    if (screen.creativeBadgeLeft() < 0) {
                        wrong.add("Abzeichen „Kreativ-FC erlaubt“ fehlt");
                    }
                    if (wrong.isEmpty() && screen.rulesBadgeRight() > screen.creativeBadgeLeft()) {
                        wrong.add("Abzeichen überlappen");
                    }
                    if (wrong.isEmpty() && (screen.creativeBadgeRight() > screen.headerRight() || screen.rulesBadgeRight() > screen.headerRight())) {
                        wrong.add("Abzeichen ragt über die Kopfzeile");
                    }
                    if (wrong.isEmpty() && this.size()[3] == 1 && screen.rulesBadgeRight() - screen.rulesBadgeLeft() != ServerRulesText.width(mc.font)) {
                        wrong.add("„Serverregeln“ gekürzt, obwohl beide Abzeichen ganz passen");
                    }
                    for (LayoutAudit.Finding finding : LayoutProbe.audit(mc, screen)) {
                        wrong.add(finding.describe());
                    }
                    if (!wrong.isEmpty()) {
                        return StepResult.fail(this.where(mc) + ": " + String.join(", ", wrong) + " (" + layout + ")");
                    }
                    this.notes.add(this.where(mc) + ": " + layout + ", Layout ohne Befund");
                    if (Screenshots.enabled() && this.index == 0) {
                        Screenshots.parkMouse(mc);
                        Screenshots.clearOverlays(mc);
                        return this.stage(ctx, 5);
                    }
                    return this.stage(ctx, 7);
                }
                case 5 -> {
                    if (ctx.tick() - this.since < SETTLE_TICKS) {
                        Screenshots.parkMouse(mc);
                        return StepResult.pending();
                    }
                    this.capture = Screenshots.capture(this.shot);
                    return this.stage(ctx, 6);
                }
                case 6 -> {
                    CompletableFuture<String> shot = this.capture;
                    if (shot != null && !shot.isDone()) {
                        return StepResult.pending();
                    }
                    if (shot != null && shot.isCompletedExceptionally()) {
                        return StepResult.fail("Bildschirmfoto " + this.shot + ": " + shot.exceptionNow());
                    }
                    if (shot != null) {
                        this.notes.add("Bildschirmfoto " + Screenshots.dir().resolve(Screenshots.fileName(this.shot) + ".png") + " (" + shot.join() + ")");
                    }
                    return this.stage(ctx, 7);
                }
                case 7 -> {
                    if (!(mc.gui.screen() instanceof LeaderboardScreen screen)) {
                        return StepResult.fail("Bestenliste zu " + this.where(mc));
                    }
                    this.tips = screen.creativeTooltips();
                    hover(mc, screen.creativeBadgeCenterX(), screen.rulesBadgeCenterY());
                    return this.stage(ctx, 8);
                }
                case 8 -> {
                    if (!(mc.gui.screen() instanceof LeaderboardScreen screen) || screen.creativeTooltips() <= this.tips) {
                        if (mc.gui.screen() instanceof LeaderboardScreen open) {
                            hover(mc, open.creativeBadgeCenterX(), open.rulesBadgeCenterY());
                        }
                        return this.waiting(ctx, TOOLTIP_TICKS, "Tooltip „Kreativ-FC erlaubt“ erscheint nicht " + this.where(mc));
                    }
                    this.tips = screen.rulesTooltips();
                    hover(mc, screen.rulesBadgeCenterX(), screen.rulesBadgeCenterY());
                    return this.stage(ctx, 9);
                }
                case 9 -> {
                    if (!(mc.gui.screen() instanceof LeaderboardScreen screen) || screen.rulesTooltips() <= this.tips) {
                        if (mc.gui.screen() instanceof LeaderboardScreen open) {
                            hover(mc, open.rulesBadgeCenterX(), open.rulesBadgeCenterY());
                        }
                        return this.waiting(ctx, TOOLTIP_TICKS, "Tooltip „Serverregeln“ erscheint nicht " + this.where(mc));
                    }
                    Screenshots.parkMouse(mc);
                    this.index++;
                    return this.stage(ctx, 2);
                }
                default -> {
                    if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) != WorldRuleSpecs.GRAVITY_SCALE.defaultValue()) {
                        return this.waiting(ctx, WAIT, "Server-Schwerkraft kommt nicht auf den Standard zurück");
                    }
                    ctx.log(String.join("; ", this.notes));
                    return StepResult.pass();
                }
            }
        }
    }

    private static final class MenuScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (ctx.tick() == 0) {
                ctx.onServer(server -> setting(server, false, CreativeFcRule.LEGAL));
            }
            Integer id = COURSE.get();
            if (id == null || !synced(false, CreativeFcRule.LEGAL)) {
                return ctx.tick() > WAIT ? StepResult.fail("Kurs mit „Renntauglich“ kommt nicht an") : StepResult.pending();
            }
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof CourseMenuScreen menu) || menu.courseId() != id) {
                mc.gui.setScreen(new CourseMenuScreen(null, id));
                return StepResult.pending();
            }
            FlatButton chip = menu.creativeButton(CreativeFcRule.LEGAL);
            if (chip == null || menu.frames() < 3) {
                return ctx.tick() > WAIT ? StepResult.fail("Schalter fehlt") : StepResult.pending();
            }
            AbstractWidget below = menu.control(CourseMenu.Row.RESET);
            menu.setFocused(below == null ? chip : below);
            menu.setFocused(null);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private static final class BadgeScene implements Screenshots.Scene {
        private final boolean tooltip;

        BadgeScene(boolean tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (ctx.tick() == 0) {
                ctx.onServer(server -> setting(server, false, CreativeFcRule.LEGAL));
            }
            Integer id = COURSE.get();
            if (id == null || !synced(false, CreativeFcRule.LEGAL)) {
                return ctx.tick() > WAIT ? StepResult.fail("Kurs mit „Renntauglich“ kommt nicht an") : StepResult.pending();
            }
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof LeaderboardScreen screen) || screen.courseId() != id) {
                mc.gui.setScreen(new LeaderboardScreen(null, id));
                return StepResult.pending();
            }
            return screen.creativeBadgeLeft() >= 0 && screen.rowsDrawn() > 0 ? StepResult.pass()
                    : ctx.tick() > WAIT ? StepResult.fail("Abzeichen fehlt") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.tooltip && mc.gui.screen() instanceof LeaderboardScreen screen && screen.creativeBadgeLeft() >= 0) {
                double x = screen.creativeBadgeCenterX();
                double y = screen.rulesBadgeCenterY();
                mc.mouseHandler.onMove(mc.getWindow().handle(), x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                        y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight(), 0.0, 0.0);
            } else {
                Screenshots.parkMouse(mc);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
