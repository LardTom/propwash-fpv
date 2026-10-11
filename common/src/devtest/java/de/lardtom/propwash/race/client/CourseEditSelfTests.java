package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseAccess;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.CourseNames;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.server.WorldRulesRights;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CourseEditSelfTests {
    private static final long MS = 1_000_000L;
    private static final int WAIT_TICKS = 200;
    private static final int SPACING = 10;
    private static final int BUILDER_LAPS = 2;
    private static final int EDITED_LAPS = 3;
    private static final String NAME = "Waldschleife";
    private static final String GHOST_FRAME = "race_5";
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<ItemStack> HAND = new AtomicReference<>();
    private static final AtomicLong CLOCK = new AtomicLong();
    private static final AtomicLong FIRST_LAP = new AtomicLong(LapTracker.NONE);
    private static final AtomicLong FIRST_RACE = new AtomicLong(LapTracker.NONE);
    private static volatile int savedLaps = CourseLaps.UNSET;

    private CourseEditSelfTests() {
    }

    private record Site(int course, int a, int b, int c, int d, int flag, double z0) {
        int[] entities() {
            return new int[]{this.a, this.b, this.c, this.d, this.flag};
        }

        double z(int gate) {
            return this.z0 - gate * SPACING;
        }
    }

    public static void register() {
        SelfTest.clientStep("race: Kurs bearbeiten – neuer Kurs bekommt die Standard-Rundenzahl des Erbauers", 400, CourseEditSelfTests::createStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Rennen endet erst nach der Rundenzahl des Kurses", 200, CourseEditSelfTests::firstRaceStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Fenster ändert Name und Rundenzahl ohne neue Bestenliste", 500, CourseEditSelfTests::metaStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Reihenfolge ändern archiviert Zeiten und Geister", 500, CourseEditSelfTests::reorderStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Element und Gate anhängen, Start verlegen, Element herausnehmen", 900,
                CourseEditSelfTests::layoutStep);
        SelfTest.clientStep("race: Kurs bearbeiten – neue Zeiten zählen nur für das neue Layout und die neue Rundenzahl", 500,
                CourseEditSelfTests::secondRaceStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Rechte für fremde Kurse, Kurse ohne Ersteller und Abenteuermodus", 200,
                CourseEditSelfTests::rightsStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Löschen lässt die Gates stehen", 500, CourseEditSelfTests::deleteStep);
        SelfTest.clientStep("race: Kurs bearbeiten – Aufräumen", 200, CourseEditSelfTests::cleanupStep);
    }

    public static void registerScreenshots() {
        Screenshots.step("bildschirmfotos: Kurs bearbeiten mit Gates und Elementen", "course_edit", 60, new EditScene("Forest Loop", false));
        Screenshots.step("bildschirmfotos: Kurs bearbeiten in der Bestenliste mit Rundenzahl und bestem Rennen", "leaderboard_course_edit", 60,
                new EditScene("Forest Loop", true));
    }

    public static void registerGermanScreenshots() {
        Screenshots.step("bildschirmfotos: Kurs bearbeiten mit Gates und Elementen deutsch", "course_edit_de", 60, new EditScene(NAME, false));
        Screenshots.step("bildschirmfotos: Kurs bearbeiten in der Bestenliste mit Rundenzahl und bestem Rennen deutsch", "leaderboard_course_edit_de",
                60, new EditScene(NAME, true));
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

    private static Site site() {
        Site site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("kein Kurs aus den vorigen Schritten");
        }
        return site;
    }

    private static @Nullable CourseEditScreen screen() {
        return Minecraft.getInstance().gui.screen() instanceof CourseEditScreen screen ? screen : null;
    }

    private static void once(SelfTest.StepContext ctx, String key, Runnable action) {
        if (ctx.state().putIfAbsent(key, Boolean.TRUE) == null) {
            action.run();
        }
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx, String key, BooleanSupplier ready, Supplier<String> problem) {
        Map<String, Object> state = ctx.state();
        if (state.containsKey(key + ".ok")) {
            return null;
        }
        if (ready.getAsBoolean()) {
            state.put(key + ".ok", Boolean.TRUE);
            return null;
        }
        int since = (int) state.computeIfAbsent(key, k -> ctx.tick());
        return ctx.tick() - since > WAIT_TICKS ? StepResult.fail(problem.get()) : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static @Nullable StepResult server(SelfTest.StepContext ctx, String key, Function<MinecraftServer, @Nullable String> action) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(key)) {
            AtomicReference<String> result = new AtomicReference<>();
            state.put(key + ".result", result);
            state.put(key, ctx.onServer(server -> result.set(action.apply(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(key);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt " + key + ": " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        String result = ((AtomicReference<String>) state.get(key + ".result")).get();
        return result == null ? null : StepResult.fail(key + ": " + result);
    }

    private static @Nullable StepResult opened(SelfTest.StepContext ctx, String key) {
        once(ctx, key, () -> CourseEditScreen.open(null, site().course()));
        return await(ctx, key + ".shown", () -> screen() != null && screen().frames() >= 2, () -> "Fenster Kurs bearbeiten öffnet nicht");
    }

    private static GateEntity place(ServerPlayer player, Item item, int x, int z, int variant) {
        ServerLevel level = player.level();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        AABB area = new AABB(ground).inflate(6.0);
        List<GateEntity> before = level.getEntitiesOfClass(GateEntity.class, area);
        InteractionResult result = CourseElementSelfTests.use(player, new ItemStack(item), ground, Direction.UP, 0.0F, variant);
        if (!result.consumesAction()) {
            throw new IllegalStateException(item + " bei " + x + "/" + z + " nicht gesetzt: " + result);
        }
        List<GateEntity> after = level.getEntitiesOfClass(GateEntity.class, area, gate -> !before.contains(gate) && !gate.isRemoved());
        if (after.size() != 1) {
            throw new IllegalStateException(item + " bei " + x + "/" + z + ": " + after.size() + " neue Gates");
        }
        return after.getFirst();
    }

    private static @Nullable GateEntity gate(ServerLevel level, int id) {
        return level.getEntity(id) instanceof GateEntity gate ? gate : null;
    }

    private static StepResult createStep(SelfTest.StepContext ctx) {
        RaceConfig config = RaceConfig.CONFIG.get();
        once(ctx, "config", () -> {
            savedLaps = config.laps;
            config.laps = BUILDER_LAPS;
        });
        StepResult waiting = await(ctx, "sent", () -> RaceClient.sentDefaultLaps() == BUILDER_LAPS,
                () -> "Standard-Rundenzahl geht nicht an den Server: " + RaceClient.sentDefaultLaps());
        if (waiting != null) {
            return waiting;
        }
        once(ctx, "sentAt", () -> ctx.state().put("sentTick", ctx.tick()));
        if (ctx.tick() - (int) ctx.state().get("sentTick") < 10) {
            return StepResult.pending();
        }
        waiting = server(ctx, "build", CourseEditSelfTests::build);
        if (waiting != null) {
            return waiting;
        }
        Site site = site();
        Minecraft minecraft = Minecraft.getInstance();
        waiting = await(ctx, "synced", () -> {
            CourseData course = ClientCourses.get(site.course());
            return course != null && course.gates().size() == 3;
        }, () -> "Kurs " + site.course() + " kommt nicht beim Client an");
        if (waiting != null) {
            return waiting;
        }
        CourseData course = ClientCourses.get(site.course());
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        if (course.laps() != BUILDER_LAPS || course.named() || self == null || !self.equals(course.owner())) {
            return StepResult.fail("Kursdaten beim Client falsch: runden=" + course.laps() + " name=" + course.name() + " ersteller=" + course.owner());
        }
        config.laps = 5;
        int resolved = CourseLaps.resolve(course.laps(), config.laps);
        config.laps = BUILDER_LAPS;
        if (resolved != BUILDER_LAPS) {
            return StepResult.fail("Rundenzahl des Kurses gewinnt nicht gegen die eigene Einstellung: " + resolved);
        }
        ctx.log("Kurs " + site.course() + " mit " + BUILDER_LAPS + " Runden aus der Einstellung des Erbauers");
        return StepResult.pass();
    }

    private static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = registry();
        int personal = CourseEdits.defaultLaps(player.getUUID());
        if (personal != BUILDER_LAPS) {
            return "Standard-Rundenzahl kam nicht beim Server an: " + personal;
        }
        int x = player.getBlockX() - 20;
        int z0 = player.getBlockZ() - 34;
        for (GateEntity old : level.getEntitiesOfClass(GateEntity.class, new AABB(x - 8, level.getMinY(), z0 - 4 * SPACING, x + 14, level.getMaxY(), z0 + 8))) {
            old.discard();
        }
        GateEntity a = place(player, RaceModule.GATE_MEDIUM.get(), x, z0, 0);
        GateEntity b = place(player, RaceModule.GATE_MEDIUM.get(), x, z0 - SPACING, 0);
        GateEntity c = place(player, RaceModule.GATE_MEDIUM.get(), x, z0 - 2 * SPACING, 0);
        GateEntity d = place(player, RaceModule.GATE_MEDIUM.get(), x, z0 - 3 * SPACING, 0);
        GateEntity flag = place(player, RaceModule.RACE_FLAG.get(), x + 7, z0 - SPACING - 5, CourseElement.LEFT);
        Site site = new Site(GateEntity.NO_COURSE, a.getId(), b.getId(), c.getId(), d.getId(), flag.getId(), a.getZ());
        SITE.set(site);
        if (Math.abs(b.getZ() - site.z(1)) > 0.01 || Math.abs(d.getZ() - site.z(3)) > 0.01) {
            return "Gates stehen nicht im Raster: " + a.getZ() + " " + b.getZ() + " " + c.getZ() + " " + d.getZ();
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, a);
        CourseToolItem.useOnGate(tool, player, b);
        CourseToolItem.useOnGate(tool, player, c);
        int id = CourseToolItem.courseOf(tool);
        SITE.set(new Site(id, site.a(), site.b(), site.c(), site.d(), site.flag(), site.z0()));
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 3) {
            return "Kurs nicht angelegt";
        }
        if (course.laps != BUILDER_LAPS || !course.ownedBy(player.getUUID()) || !course.name.isEmpty() || course.layout != 1) {
            return "Neuer Kurs falsch: runden=" + course.laps + " ersteller=" + course.ownerName + " name=" + course.name + " layout=" + course.layout;
        }
        return null;
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos, long total) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, total);
    }

    private static void passGate(ServerPlayer player, GateGeometry gate, long nanos) {
        Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
        Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
        RaceServer.onDroneMoved(player, center.subtract(normal), center.add(normal), nanos);
    }

    private static long flyLap(ServerPlayer player, List<GateGeometry> gates, long start, long lap) {
        passGate(player, gates.getFirst(), start);
        for (int i = 1; i < gates.size(); i++) {
            passGate(player, gates.get(i), start + lap * i / gates.size());
        }
        passGate(player, gates.getFirst(), start + lap);
        return start + lap;
    }

    private static @Nullable String race(MinecraftServer server, int laps, AtomicLong bestLap, AtomicLong total) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = registry();
        CourseRegistry.Course course = registry.course(site().course());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        if (course.laps != laps) {
            return "Kurs hat " + course.laps + " statt " + laps + " Runden";
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        drone.snapTo(start.x, start.y + 1.5, start.z - 3.0, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            int id = course.id;
            Leaderboard board = registry.board(course);
            List<GateGeometry> gates = course.toData().geometry();
            long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 6_000L * MS);
            long t = Math.max(System.nanoTime() + 60_000L * MS, CLOCK.get() + 10_000L * MS);
            RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            long crossed = flyLap(player, gates, t + 100L * MS, lap + 900L * MS);
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap + 900L * MS, 0L), player, crossed + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde abgelehnt: " + verdict;
            }
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.FINISH, id, 1, 0L, lap + 900L * MS), player, crossed + 150L * MS);
            if (verdict != RaceServer.Verdict.OUT_OF_SEQUENCE || board.raceBest(player.getUUID(), 1) != LapTracker.NONE) {
                return "Ziel nach 1 von " + laps + " Runden nicht abgelehnt: " + verdict;
            }
            t = crossed + 3_000L * MS;
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Zweiter Start abgelehnt: " + verdict;
            }
            crossed = t + 100L * MS;
            long sum = 0L;
            long fastest = Long.MAX_VALUE;
            for (int i = 1; i <= laps; i++) {
                long time = lap - (i - 1) * 300L * MS;
                crossed = flyLap(player, gates, crossed, time);
                verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, i, time, 0L), player, crossed + 100L * MS);
                if (verdict != RaceServer.Verdict.ACCEPTED) {
                    return "Runde " + i + " von " + laps + " abgelehnt: " + verdict;
                }
                sum += time;
                fastest = Math.min(fastest, time);
            }
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.FINISH, id, laps, 0L, sum), player, crossed + 200L * MS);
            CLOCK.set(crossed + 200L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Ziel nach " + laps + " Runden abgelehnt: " + verdict;
            }
            if (board.raceBest(player.getUUID(), laps) != sum) {
                return "Rennen über " + laps + " Runden nicht gewertet: " + board.raceBest(player.getUUID(), laps) + " statt " + sum;
            }
            List<Leaderboard.Entry> ranking = board.top(null, 10);
            if (ranking.size() != 1 || ranking.getFirst().nanos() != fastest) {
                return "Bestenliste nach dem Rennen falsch: " + ranking;
            }
            bestLap.set(fastest);
            total.set(sum);
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    private static StepResult firstRaceStep(SelfTest.StepContext ctx) {
        StepResult waiting = server(ctx, "rennen", server -> {
            String problem = race(server, BUILDER_LAPS, FIRST_LAP, FIRST_RACE);
            if (problem != null) {
                return problem;
            }
            CourseRegistry registry = registry();
            CourseRegistry.Course course = registry.course(site().course());
            registry.ghosts().put(course.id, GHOST_FRAME, course.toData().layoutHash(), player(server).getGameProfile().name(), FIRST_LAP.get(),
                    new byte[]{1, 2, 3});
            return registry.ghosts().get(course.id, GHOST_FRAME) == null ? "Rekord-Geist nicht abgelegt" : null;
        });
        return waiting != null ? waiting : StepResult.pass();
    }

    private static StepResult metaStep(SelfTest.StepContext ctx) {
        Site site = site();
        Minecraft minecraft = Minecraft.getInstance();
        StepResult waiting = opened(ctx, "open");
        if (waiting != null) {
            return waiting;
        }
        CourseEditScreen screen = screen();
        if (screen != null && ctx.state().putIfAbsent("checked", Boolean.TRUE) == null) {
            CourseData course = screen.course();
            if (!(minecraft.gui.screen() instanceof ThemedScreen) || !screen.editable() || course == null
                    || !CourseEditScreen.canDelete(minecraft, course) || screen.shownLaps() != BUILDER_LAPS) {
                return StepResult.fail("Fenster nicht bearbeitbar: theme=" + (minecraft.gui.screen() instanceof ThemedScreen) + " ändern="
                        + screen.editable() + " runden=" + screen.shownLaps());
            }
            screen.rename("  " + NAME + "  ");
            screen.stepLaps(1);
            if (screen.shownLaps() != EDITED_LAPS) {
                return StepResult.fail("Fenster zeigt nach + nicht " + EDITED_LAPS + " Runden: " + screen.shownLaps());
            }
            screen.flush();
        }
        waiting = await(ctx, "synced", () -> {
            CourseData course = ClientCourses.get(site.course());
            return course != null && NAME.equals(course.name()) && course.laps() == EDITED_LAPS;
        }, () -> "Name und Rundenzahl kommen nicht zurück: " + ClientCourses.get(site.course()));
        if (waiting != null) {
            return waiting;
        }
        waiting = server(ctx, "wertung", server -> {
            ServerPlayer player = player(server);
            CourseRegistry registry = registry();
            CourseRegistry.Course course = registry.course(site.course());
            if (course == null || !NAME.equals(course.name) || course.laps != EDITED_LAPS) {
                return "Server hat Name oder Rundenzahl nicht";
            }
            Leaderboard board = registry.board(course);
            if (course.layout != 1 || !registry.records().archive(course.id).isEmpty()) {
                return "Name und Rundenzahl haben die Bestenliste archiviert: layout=" + course.layout;
            }
            List<Leaderboard.Entry> ranking = board.top(null, 10);
            if (ranking.size() != 1 || ranking.getFirst().nanos() != FIRST_LAP.get()) {
                return "Rundenzeit nach dem Umbenennen weg: " + ranking;
            }
            if (board.raceBest(player.getUUID(), BUILDER_LAPS) != FIRST_RACE.get() || board.raceBest(player.getUUID(), EDITED_LAPS) != LapTracker.NONE) {
                return "Rennzeiten nicht je Rundenzahl getrennt";
            }
            RacePayloads.LeaderboardS2C payload = RaceServer.leaderboardPayload(board, course, player.getUUID(), false);
            if (payload.raceLaps() != EDITED_LAPS || payload.hasRaceBest() || payload.archived() != 0 || payload.layout() != 1) {
                return "Bestenliste meldet Rennen falsch: runden=" + payload.raceLaps() + " bestes=" + payload.raceBest() + " archiv="
                        + payload.archived();
            }
            return null;
        });
        if (waiting != null) {
            return waiting;
        }
        minecraft.gui.setScreen(null);
        return StepResult.pass();
    }

    private static boolean order(Site site, int... gates) {
        CourseData course = ClientCourses.get(site.course());
        if (course == null || course.gates().size() != gates.length) {
            return false;
        }
        for (int i = 0; i < gates.length; i++) {
            CourseData.Gate gate = course.gates().get(i);
            boolean flag = gates[i] < 0;
            if (flag != (gate.kind() == CourseElement.FLAG) || !flag && Math.abs(gate.z() - site.z(gates[i])) > 0.01) {
                return false;
            }
        }
        return true;
    }

    private static String shown(Site site) {
        CourseData course = ClientCourses.get(site.course());
        if (course == null) {
            return "kein Kurs";
        }
        List<String> out = new ArrayList<>();
        for (CourseData.Gate gate : course.gates()) {
            out.add(gate.kind() + "@" + Math.round(gate.z()));
        }
        return out.toString();
    }

    private static @Nullable String indices(ServerLevel level, CourseRegistry.Course course, int... entities) {
        for (int i = 0; i < entities.length; i++) {
            GateEntity gate = gate(level, entities[i]);
            if (gate == null || gate.courseId() != course.id || gate.gateIndex() != i || !course.gates.get(i).uuid.equals(gate.getUUID().toString())) {
                return "Gate an Stelle " + (i + 1) + " falsch nummeriert: " + (gate == null ? "fehlt" : gate.courseId() + "/" + gate.gateIndex());
            }
        }
        return course.gates.size() == entities.length ? null : "Kurs hat " + course.gates.size() + " statt " + entities.length + " Gates";
    }

    private static StepResult reorderStep(SelfTest.StepContext ctx) {
        Site site = site();
        StepResult waiting = opened(ctx, "open");
        if (waiting != null) {
            return waiting;
        }
        once(ctx, "move", () -> screen().move(2, 1));
        waiting = await(ctx, "moved", () -> order(site, 0, 2, 1), () -> "Reihenfolge A, C, B kommt nicht an: " + shown(site));
        if (waiting != null) {
            return waiting;
        }
        waiting = server(ctx, "archiv", server -> {
            ServerPlayer player = player(server);
            CourseRegistry registry = registry();
            CourseRegistry.Course course = registry.course(site.course());
            if (course == null) {
                return "Kurs fehlt";
            }
            String numbering = indices(player.level(), course, site.a(), site.c(), site.b());
            if (numbering != null) {
                return numbering;
            }
            Leaderboard board = registry.board(course);
            if (course.layout != 2 || !board.top(null, 10).isEmpty() || board.raceBest(player.getUUID(), BUILDER_LAPS) != LapTracker.NONE) {
                return "Alte Zeiten gelten im neuen Layout weiter: layout=" + course.layout + " zeiten=" + board.top(null, 10).size();
            }
            List<RaceRecords.Archived> archive = registry.records().archive(course.id);
            if (archive.size() != 1 || archive.getFirst().layout() != 1 || archive.getFirst().gates() != 3) {
                return "Archiv falsch: " + archive.size();
            }
            Leaderboard old = archive.getFirst().board();
            if (old.top(null, 10).size() != 1 || old.top(null, 10).getFirst().nanos() != FIRST_LAP.get()
                    || old.raceBest(player.getUUID(), BUILDER_LAPS) != FIRST_RACE.get()) {
                return "Archiv ohne die alten Zeiten";
            }
            if (registry.ghosts().get(course.id, GHOST_FRAME) != null) {
                return "Rekord-Geist des alten Layouts noch da";
            }
            if (!NAME.equals(course.name) || course.laps != EDITED_LAPS) {
                return "Name oder Rundenzahl beim Umsortieren verloren";
            }
            return null;
        });
        if (waiting != null) {
            return waiting;
        }
        Minecraft.getInstance().gui.setScreen(null);
        return StepResult.pass();
    }

    private static StepResult layoutStep(SelfTest.StepContext ctx) {
        Site site = site();
        StepResult waiting = server(ctx, "werkzeug", server -> {
            ServerPlayer player = player(server);
            HAND.set(player.getMainHandItem().copy());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            return null;
        });
        if (waiting != null) {
            return waiting;
        }
        waiting = opened(ctx, "open");
        if (waiting != null) {
            return waiting;
        }
        once(ctx, "bind", () -> screen().bindTool());
        if (ctx.state().putIfAbsent("bindTick", ctx.tick()) == null || ctx.tick() - (int) ctx.state().get("bindTick") < 10) {
            return StepResult.pending();
        }
        waiting = server(ctx, "flagge", server -> {
            ServerPlayer player = player(server);
            ItemStack tool = player.getMainHandItem();
            if (!CourseToolItem.isCourseTool(tool) || CourseToolItem.courseOf(tool) != site.course()) {
                return "Kurs-Werkzeug steht nicht auf dem Kurs: " + CourseToolItem.courseOf(tool);
            }
            GateEntity flag = gate(player.level(), site.flag());
            if (flag == null) {
                return "Flagge fehlt";
            }
            CourseToolItem.useOnGate(tool, player, flag);
            return flag.courseId() == site.course() && flag.gateIndex() == 3 ? null : "Flagge nicht angehängt: " + flag.courseId() + "/"
                    + flag.gateIndex();
        });
        if (waiting != null) {
            return waiting;
        }
        waiting = await(ctx, "flagged", () -> order(site, 0, 2, 1, -1), () -> "Flagge kommt nicht beim Client an: " + shown(site));
        if (waiting != null) {
            return waiting;
        }
        CourseData withFlag = ClientCourses.get(site.course());
        if (withFlag != null && withFlag.gates().size() == 4 && withFlag.gates().get(3).kind() == CourseElement.FLAG
                && (CourseOrder.canStart(withFlag.kinds(), 3) || CourseOrder.canMove(withFlag.kinds(), 3, 0) || !CourseOrder.canStart(withFlag.kinds(), 1))) {
            return StepResult.fail("Fenster würde eine Flagge als Start anbieten");
        }
        waiting = server(ctx, "flaggenstart", server -> {
            ServerPlayer player = player(server);
            CourseRegistry.Course course = registry().course(site.course());
            CourseEdits.Result start = CourseEdits.apply(player, new RacePayloads.CourseEditC2S(site.course(), CourseEdits.Action.START, 3, 0));
            CourseEdits.Result move = CourseEdits.apply(player, new RacePayloads.CourseEditC2S(site.course(), CourseEdits.Action.MOVE, 3, 0));
            if (start != CourseEdits.Result.NOT_STARTABLE || move != CourseEdits.Result.NOT_STARTABLE) {
                return "Server lässt eine Flagge als Start zu: " + start + "/" + move;
            }
            return indices(player.level(), course, site.a(), site.c(), site.b(), site.flag());
        });
        if (waiting != null) {
            return waiting;
        }
        waiting = opened(ctx, "reopen");
        if (waiting != null) {
            return waiting;
        }
        once(ctx, "start", () -> screen().makeStart(1));
        waiting = await(ctx, "started", () -> order(site, 2, 1, -1, 0), () -> "Start bei C kommt nicht an: " + shown(site));
        if (waiting != null) {
            return waiting;
        }
        once(ctx, "remove", () -> screen().remove(2));
        waiting = await(ctx, "removed", () -> order(site, 2, 1, 0), () -> "Flagge nicht herausgenommen: " + shown(site));
        if (waiting != null) {
            return waiting;
        }
        waiting = server(ctx, "gate", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            CourseRegistry.Course course = registry().course(site.course());
            GateEntity flag = gate(level, site.flag());
            if (flag == null || flag.inCourse()) {
                return "Herausgenommene Flagge steht nicht mehr frei in der Welt";
            }
            GateEntity d = gate(level, site.d());
            ItemStack tool = player.getMainHandItem();
            if (d == null || !CourseToolItem.isCourseTool(tool)) {
                return "Gate D oder Kurs-Werkzeug fehlt";
            }
            CourseToolItem.useOnGate(tool, player, d);
            ItemStack previous = HAND.getAndSet(null);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous == null ? ItemStack.EMPTY : previous);
            if (course.layout != 2 || !registry().records().archive(course.id).stream().allMatch(board -> board.layout() == 1)) {
                return "Leere Bestenliste wurde archiviert: layout=" + course.layout;
            }
            return indices(level, course, site.c(), site.b(), site.a(), site.d());
        });
        if (waiting != null) {
            return waiting;
        }
        waiting = await(ctx, "appended", () -> order(site, 2, 1, 0, 3), () -> "Gate D kommt nicht beim Client an: " + shown(site));
        if (waiting != null) {
            return waiting;
        }
        Minecraft.getInstance().gui.setScreen(null);
        return StepResult.pass();
    }

    private static StepResult secondRaceStep(SelfTest.StepContext ctx) {
        Site site = site();
        Minecraft minecraft = Minecraft.getInstance();
        AtomicLong lap = new AtomicLong();
        AtomicLong total = new AtomicLong();
        StepResult waiting = server(ctx, "rennen", server -> {
            String problem = race(server, EDITED_LAPS, lap, total);
            if (problem != null) {
                return problem;
            }
            ServerPlayer player = player(server);
            CourseRegistry registry = registry();
            CourseRegistry.Course course = registry.course(site.course());
            Leaderboard board = registry.board(course);
            if (board.raceBest(player.getUUID(), BUILDER_LAPS) != LapTracker.NONE) {
                return "Rennen über " + BUILDER_LAPS + " Runden aus dem alten Layout zählt im neuen";
            }
            List<RaceRecords.Archived> archive = registry.records().archive(course.id);
            if (archive.size() != 1 || archive.getFirst().board().top(null, 10).getFirst().nanos() != FIRST_LAP.get()) {
                return "Archiv nach dem neuen Rennen verändert";
            }
            RacePayloads.LeaderboardS2C payload = RaceServer.leaderboardPayload(board, course, player.getUUID(), false);
            if (payload.layout() != 2 || payload.archived() != 1 || payload.raceLaps() != EDITED_LAPS || payload.raceBest() != total.get()
                    || payload.rows().size() != 1 || payload.rows().getFirst().nanos() != lap.get() || payload.gates() != 4) {
                return "Bestenliste des neuen Layouts falsch: layout=" + payload.layout() + " archiv=" + payload.archived() + " zeilen="
                        + payload.rows().size();
            }
            return null;
        });
        if (waiting != null) {
            return waiting;
        }
        once(ctx, "board", () -> minecraft.gui.setScreen(new LeaderboardScreen(null, site.course())));
        waiting = await(ctx, "boardShown", () -> {
            LeaderboardScreen board = LeaderboardScreen.active();
            RacePayloads.LeaderboardS2C data = ClientLeaderboards.get(site.course());
            return board != null && board.frames() >= 2 && data != null && data.layout() == 2 && data.hasRaceBest() && board.editButton() != null;
        }, () -> "Bestenliste des neuen Layouts öffnet nicht");
        if (waiting != null) {
            return waiting;
        }
        LeaderboardScreen board = LeaderboardScreen.active();
        if (board != null) {
            if (!board.editButton().active || board.shownRows().size() != 1) {
                return StepResult.fail("Bestenliste ohne Knopf Kurs bearbeiten oder mit alten Zeilen: " + board.shownRows().size());
            }
            board.editCourse();
        }
        waiting = await(ctx, "fromBoard", () -> screen() != null && screen().courseId() == site.course() && screen().frames() >= 2,
                () -> "Kurs bearbeiten öffnet nicht aus der Bestenliste");
        if (waiting != null) {
            return waiting;
        }
        minecraft.gui.setScreen(null);
        return StepResult.pass();
    }

    private static CourseEdits.Result edit(ServerPlayer player, int course, CourseEdits.Action action, int first) {
        return CourseEdits.apply(player, new RacePayloads.CourseEditC2S(course, action, first, 0));
    }

    private static @Nullable String rights(MinecraftServer server) {
        ServerPlayer player = player(server);
        CourseRegistry registry = registry();
        CourseRegistry.Course course = registry.course(site().course());
        if (course == null) {
            return "Kurs fehlt";
        }
        String owner = course.owner;
        String ownerName = course.ownerName;
        CourseRegistry.GateRecord start = course.gates.getFirst();
        ServerPlayer guest = WorldRulesRights.guest(server);
        try {
            guest.setGameMode(GameType.SURVIVAL);
            guest.snapTo(start.x + 2.0, start.y, start.z, 0.0F, 0.0F);
            if (CourseRegistry.isGamemaster(guest)) {
                return "Gast hat Operator-Rechte";
            }
            if (CourseEdits.access(guest, course, CourseEdits.Action.LAPS) != CourseAccess.NOT_OWNER
                    || edit(guest, course.id, CourseEdits.Action.LAPS, 7) != CourseEdits.Result.DENIED
                    || edit(guest, course.id, CourseEdits.Action.MOVE, 1) != CourseEdits.Result.DENIED
                    || CourseEdits.apply(guest, RacePayloads.CourseEditC2S.rename(course.id, "Geklaut")) != CourseEdits.Result.DENIED
                    || edit(guest, course.id, CourseEdits.Action.DELETE, 0) != CourseEdits.Result.DENIED) {
                return "Gast darf einen fremden Kurs ändern";
            }
            if (course.laps != EDITED_LAPS || !NAME.equals(course.name) || registry.course(course.id) == null) {
                return "Abgelehnte Änderung hat den Kurs verändert";
            }
            course.owner = "";
            course.ownerName = "";
            if (CourseEdits.access(guest, course, CourseEdits.Action.LAPS) != CourseAccess.ALLOWED
                    || edit(guest, course.id, CourseEdits.Action.LAPS, 7) != CourseEdits.Result.DONE || course.laps != 7) {
                return "Gast darf einen Kurs ohne Ersteller in dessen Nähe nicht ändern";
            }
            if (!course.owner.isEmpty()) {
                return "Kurs ohne Ersteller wurde durch das Fenster übernommen";
            }
            if (edit(guest, course.id, CourseEdits.Action.DELETE, 0) != CourseEdits.Result.DENIED || registry.course(course.id) == null) {
                return "Gast darf einen Kurs ohne Ersteller löschen";
            }
            guest.snapTo(start.x + 2_000.0, start.y, start.z, 0.0F, 0.0F);
            if (CourseEdits.access(guest, course, CourseEdits.Action.LAPS) != CourseAccess.TOO_FAR
                    || edit(guest, course.id, CourseEdits.Action.LAPS, 9) != CourseEdits.Result.DENIED) {
                return "Gast darf einen Kurs ohne Ersteller aus der Ferne ändern";
            }
            guest.snapTo(start.x + 2.0, start.y, start.z, 0.0F, 0.0F);
            guest.setGameMode(GameType.ADVENTURE);
            if (CourseEdits.access(guest, course, CourseEdits.Action.LAPS) != CourseAccess.NO_BUILD
                    || edit(guest, course.id, CourseEdits.Action.LAPS, 9) != CourseEdits.Result.DENIED) {
                return "Gast darf im Abenteuermodus Kurse ändern";
            }
            guest.setGameMode(GameType.SURVIVAL);
            course.owner = guest.getUUID().toString();
            course.ownerName = guest.getGameProfile().name();
            guest.snapTo(start.x + 2_000.0, start.y, start.z, 0.0F, 0.0F);
            if (edit(guest, course.id, CourseEdits.Action.LAPS, EDITED_LAPS) != CourseEdits.Result.DONE || course.laps != EDITED_LAPS) {
                return "Ersteller darf seinen Kurs aus der Ferne nicht ändern";
            }
            course.owner = new UUID(79L, 1L).toString();
            course.ownerName = "Heron";
            if (edit(guest, course.id, CourseEdits.Action.LAPS, 9) != CourseEdits.Result.DENIED) {
                return "Früherer Ersteller darf den Kurs weiter ändern";
            }
            server.getPlayerList().op(guest.nameAndId());
            ServerPlayer operator = CourseRegistry.isGamemaster(player) ? player : CourseRegistry.isGamemaster(guest) ? guest : null;
            if (operator != null) {
                CourseEdits.Result byOperator = edit(operator, course.id, CourseEdits.Action.LAPS, 500);
                CourseAccess deleteByOperator = CourseEdits.access(operator, course, CourseEdits.Action.DELETE);
                if (byOperator != CourseEdits.Result.DONE || course.laps != CourseLaps.MAX || deleteByOperator != CourseAccess.ALLOWED) {
                    return "Operator darf fremden Kurs nicht ändern oder Rundenzahl nicht begrenzt: " + byOperator + " runden=" + course.laps
                            + " löschen=" + deleteByOperator;
                }
            }
            server.getPlayerList().deop(guest.nameAndId());
            course.owner = owner;
            course.ownerName = ownerName;
            if (edit(player, course.id, CourseEdits.Action.LAPS, CourseLaps.ENDLESS) != CourseEdits.Result.DONE || course.laps != CourseLaps.ENDLESS
                    || RaceServer.leaderboardPayload(registry.board(course), course, player.getUUID(), false).raceLaps() != CourseLaps.ENDLESS) {
                return "Endlos lässt sich nicht einstellen: " + course.laps;
            }
            String raw = "§c  Rot \u0007 und   Blau  ";
            if (CourseEdits.apply(player, RacePayloads.CourseEditC2S.rename(course.id, raw)) != CourseEdits.Result.DONE
                    || !course.name.equals(CourseNames.clean(raw)) || course.name.contains("§") || course.name.contains("  ")) {
                return "Name nicht bereinigt: '" + course.name + "'";
            }
            if (course.layout != 2) {
                return "Name und Rundenzahl haben das Layout hochgezählt: " + course.layout;
            }
            return null;
        } finally {
            server.getPlayerList().deop(guest.nameAndId());
            course.owner = owner;
            course.ownerName = ownerName;
            registry.rename(course, NAME);
            registry.setLaps(course, EDITED_LAPS);
            registry.markDirty();
        }
    }

    private static StepResult rightsStep(SelfTest.StepContext ctx) {
        StepResult waiting = server(ctx, "rechte", CourseEditSelfTests::rights);
        return waiting != null ? waiting : StepResult.pass();
    }

    private static StepResult deleteStep(SelfTest.StepContext ctx) {
        Site site = site();
        StepResult waiting = opened(ctx, "open");
        if (waiting != null) {
            return waiting;
        }
        CourseEditScreen screen = screen();
        if (screen != null && ctx.state().putIfAbsent("armed", Boolean.TRUE) == null) {
            screen.delete();
            if (!screen.deleteArmed() || ClientCourses.get(site.course()) == null) {
                return StepResult.fail("Löschen fragt nicht nach");
            }
            screen.delete();
        }
        waiting = await(ctx, "gone", () -> ClientCourses.get(site.course()) == null && screen() == null, () -> "Kurs nach dem Löschen noch beim Client");
        if (waiting != null) {
            return waiting;
        }
        waiting = server(ctx, "gates", server -> {
            ServerLevel level = player(server).level();
            CourseRegistry registry = registry();
            if (registry.course(site.course()) != null || !registry.records().archive(site.course()).isEmpty()
                    || !registry.records().isEmpty(site.course())) {
                return "Kurs oder seine Zeiten nach dem Löschen noch auf dem Server";
            }
            for (int id : site.entities()) {
                GateEntity gate = gate(level, id);
                if (gate == null || gate.inCourse() || registry.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
                    return "Gate nach dem Löschen des Kurses weg oder noch im Kurs";
                }
            }
            return null;
        });
        return waiting != null ? waiting : StepResult.pass();
    }

    private static void teardown(MinecraftServer server) {
        Site site = SITE.getAndSet(null);
        ServerPlayer player = player(server);
        ItemStack previous = HAND.getAndSet(null);
        if (previous != null) {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        if (site == null) {
            return;
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && site.course() != GateEntity.NO_COURSE) {
            registry.deleteCourse(site.course());
        }
        for (int id : site.entities()) {
            GateEntity gate = gate(player.level(), id);
            if (gate != null) {
                gate.discard();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        once(ctx, "config", () -> {
            if (savedLaps != CourseLaps.UNSET) {
                RaceConfig.CONFIG.get().laps = savedLaps;
                savedLaps = CourseLaps.UNSET;
            }
            Minecraft.getInstance().gui.setScreen(null);
        });
        StepResult waiting = server(ctx, "abbau", server -> {
            teardown(server);
            return null;
        });
        return waiting != null ? waiting : StepResult.pass();
    }

    private static final class EditScene implements Screenshots.Scene {
        private static final long[] TIMES = {9_310L, 9_870L, 10_120L, 11_460L};
        private static final String[] PILOTS = {"Swift", "Heron", "Lark", "Wren"};
        private static final String[] FRAMES = {"race_5", "freestyle_5", "race_5", "whoop_65"};
        private static final int SITE_X = 18;
        private static final int SITE_Z = -30;
        private static final int SITE_WIDTH = 9;
        private static final int SITE_DEPTH = 24;
        private static final int SITE_MARGIN = 8;
        private final String name;
        private final boolean board;
        private final List<Integer> entities = new ArrayList<>();
        private volatile int course = GateEntity.NO_COURSE;
        private @Nullable CompletableFuture<Void> built;
        private @Nullable CompletableFuture<Void> probe;
        private volatile boolean loaded;
        private boolean opened;

        private EditScene(String name, boolean board) {
            this.name = name;
            this.board = board;
        }

        private static boolean siteLoaded(ServerPlayer player) {
            ServerLevel level = player.level();
            int x = player.getBlockX() + SITE_X;
            int z = player.getBlockZ() + SITE_Z;
            for (int chunkX = (x - SITE_MARGIN) >> 4; chunkX <= (x + SITE_WIDTH + SITE_MARGIN) >> 4; chunkX++) {
                for (int chunkZ = (z - SITE_DEPTH - SITE_MARGIN) >> 4; chunkZ <= (z + SITE_MARGIN) >> 4; chunkZ++) {
                    BlockPos probe = new BlockPos((chunkX << 4) + 8, player.getBlockY(), (chunkZ << 4) + 8);
                    if (!level.isLoaded(probe) || !level.isPositionEntityTicking(probe)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private void build(MinecraftServer server) {
            ServerPlayer player = player(server);
            CourseRegistry registry = registry();
            int x = player.getBlockX() + SITE_X;
            int z = player.getBlockZ() + SITE_Z;
            List<GateEntity> placed = new ArrayList<>();
            try {
                placed.add(place(player, RaceModule.GATE_MEDIUM.get(), x, z, 0));
                placed.add(place(player, RaceModule.SLALOM_POLE.get(), x, z - 8, CourseElement.LEFT));
                placed.add(place(player, RaceModule.SLALOM_POLE.get(), x, z - 14, CourseElement.LEFT));
                placed.add(place(player, RaceModule.RACE_FLAG.get(), x, z - 24, CourseElement.LEFT));
                placed.add(place(player, RaceModule.DIVE_CUBE.get(), x + 9, z - 16, CourseElement.EXIT_FRONT));
                placed.add(place(player, RaceModule.GATE_LARGE.get(), x + 9, z, 0));
            } finally {
                for (GateEntity gate : placed) {
                    this.entities.add(gate.getId());
                }
            }
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (GateEntity gate : placed) {
                CourseToolItem.useOnGate(tool, player, gate);
            }
            int id = CourseToolItem.courseOf(tool);
            CourseRegistry.Course data = registry.course(id);
            if (data == null || data.gates.size() != placed.size()) {
                throw new IllegalStateException("Kurs für das Bildschirmfoto nicht angelegt");
            }
            this.course = id;
            registry.rename(data, this.name);
            registry.setLaps(data, 5);
            Leaderboard board = registry.board(data);
            long now = System.currentTimeMillis();
            board.submit(player.getUUID(), player.getGameProfile().name(), FRAMES[2], TIMES[2] * MS, now);
            for (int i = 0; i < TIMES.length; i++) {
                if (i != 2) {
                    board.submit(new UUID(78L, i), PILOTS[i], FRAMES[i], TIMES[i] * MS, now - (i + 1) * 86_400_000L);
                }
            }
            board.submitRace(player.getUUID(), 5, 52_480L * MS);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.built == null) {
                if (!this.loaded) {
                    if (this.probe == null || this.probe.isDone()) {
                        if (ctx.tick() > 300) {
                            return StepResult.fail("Platz für das Bildschirmfoto lädt nicht");
                        }
                        this.probe = ctx.onServer(server -> this.loaded = siteLoaded(player(server)));
                    }
                    return StepResult.pending();
                }
                this.built = ctx.onServer(this::build);
                return StepResult.pending();
            }
            if (this.built.isCompletedExceptionally()) {
                return StepResult.fail("Kurs für das Bildschirmfoto: " + this.built.exceptionNow());
            }
            if (!this.built.isDone()) {
                return StepResult.pending();
            }
            CourseData data = ClientCourses.get(this.course);
            if (data == null || data.gates().size() != this.entities.size() || !this.name.equals(data.name()) || data.laps() != 5) {
                return ctx.tick() > 300 ? StepResult.fail("Kurs für das Bildschirmfoto kommt nicht an") : StepResult.pending();
            }
            if (!this.opened) {
                this.opened = true;
                if (this.board) {
                    int id = this.course;
                    ctx.onServer(server -> RaceServer.sendLeaderboard(player(server), id, true));
                } else {
                    CourseEditScreen.open(null, this.course);
                }
                return StepResult.pending();
            }
            if (this.board) {
                LeaderboardScreen screen = LeaderboardScreen.active();
                if (screen == null || screen.frames() < 2 || screen.shownRows().size() != TIMES.length || screen.editButton() == null) {
                    return ctx.tick() > 300 ? StepResult.fail("Bestenliste mit Knopf Kurs bearbeiten öffnet nicht") : StepResult.pending();
                }
                return StepResult.pass();
            }
            CourseEditScreen screen = screen();
            if (screen == null || screen.frames() < 2 || !screen.editable()) {
                return ctx.tick() > 300 ? StepResult.fail("Fenster Kurs bearbeiten öffnet nicht") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            LeaderboardScreen screen = LeaderboardScreen.active();
            if (!this.board || screen == null || screen.editButton() == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            double guiX = screen.editButton().getX() + screen.editButton().getWidth() * 0.5;
            double guiY = screen.editButton().getY() + screen.editButton().getHeight() * 0.5;
            double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            Screenshots.parkMouse(mc);
            int id = this.course;
            List<Integer> ids = List.copyOf(this.entities);
            this.entities.clear();
            this.course = GateEntity.NO_COURSE;
            this.built = null;
            this.probe = null;
            this.loaded = false;
            this.opened = false;
            ctx.onServer(server -> {
                CourseRegistry registry = CourseRegistry.get();
                if (registry != null && id != GateEntity.NO_COURSE) {
                    registry.deleteCourse(id);
                }
                ServerLevel level = player(server).level();
                for (int entity : ids) {
                    GateEntity gate = gate(level, entity);
                    if (gate != null) {
                        gate.discard();
                    }
                }
            });
        }
    }
}
