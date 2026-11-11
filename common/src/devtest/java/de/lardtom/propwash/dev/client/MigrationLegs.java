package de.lardtom.propwash.dev.client;

import com.google.gson.JsonObject;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientGateOpenings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class MigrationLegs {
    private static final long MS = 1_000_000L;
    private static final String FUTURE = "future";
    private static final int CLIENT_WAIT = 300;

    private MigrationLegs() {
    }

    public static List<Vec3> path(List<GateGeometry> gates, List<Integer> legs) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            int index = i % gates.size();
            GateGeometry gate = gates.get(index);
            double height = MigrationValues.height(gate, index, legs);
            Vec3 entry = MigrationValues.point(gate, -2.0, 0.0, height);
            if (i > 0) {
                GateGeometry previous = gates.get(i - 1);
                double before = MigrationValues.height(previous, i - 1, legs);
                Vec3 from = MigrationValues.point(previous, 2.0, 0.0, before);
                if (RaceLine.needsDetour(previous, gate) || !clear(gates, index, List.of(from, entry))) {
                    path.addAll(detour(gates, index, previous, before, height, from, entry));
                }
            }
            path.add(entry);
            path.add(MigrationValues.point(gate, 2.0, 0.0, height));
        }
        return path;
    }

    private static List<Vec3> detour(List<GateGeometry> gates, int index, GateGeometry previous, double before, double height, Vec3 from, Vec3 entry) {
        GateGeometry gate = gates.get(index);
        double reach = Math.max(previous.width(), gate.width()) * 0.5 + 2.0;
        List<Vec3> right = around(previous, before, gate, height, reach);
        if (clear(gates, index, chain(from, right, entry))) {
            return right;
        }
        List<Vec3> left = around(previous, before, gate, height, -reach);
        return clear(gates, index, chain(from, left, entry)) ? left : right;
    }

    private static List<Vec3> around(GateGeometry previous, double before, GateGeometry gate, double height, double side) {
        Vec3 exit = MigrationValues.point(previous, 2.0, side, before);
        return List.of(exit, new Vec3(exit.x, height, exit.z), MigrationValues.point(gate, -2.0, side, height));
    }

    private static List<Vec3> chain(Vec3 from, List<Vec3> way, Vec3 entry) {
        List<Vec3> chain = new ArrayList<>();
        chain.add(from);
        chain.addAll(way);
        chain.add(entry);
        return chain;
    }

    private static boolean clear(List<GateGeometry> gates, int target, List<Vec3> chain) {
        for (int p = 1; p < chain.size(); p++) {
            Vec3 a = chain.get(p - 1);
            Vec3 b = chain.get(p);
            for (int g = 0; g < gates.size(); g++) {
                boolean against = g == target;
                if (gates.get(g).crossing(a.x, a.y, a.z, b.x, b.y, b.z, !against, true) >= 0.0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static List<LapTracker.Event> fly(int course, List<GateGeometry> gates, List<Vec3> path, GateOpenings openings) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(course, gates, 0L);
        tracker.setOpenings(openings);
        long now = 1_000L * MS;
        Vec3 at = MigrationValues.point(gates.getFirst(), -4.0, 0.0, path.getFirst().y);
        tracker.update(now, at.x, at.y, at.z);
        List<LapTracker.Event> events = new ArrayList<>();
        for (Vec3 target : path) {
            Vec3 delta = target.subtract(at);
            int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.3));
            Vec3 from = at;
            for (int i = 1; i <= steps; i++) {
                now += 30 * MS;
                at = from.add(delta.scale((double) i / steps));
                tracker.update(now, at.x, at.y, at.z);
                events.addAll(tracker.drainEvents());
            }
        }
        return events;
    }

    public static String describe(List<LapTracker.Event> events) {
        long starts = events.stream().filter(e -> e.type() == LapTracker.EventType.START).count();
        long laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        List<String> wrong = events.stream().filter(e -> e.type() == LapTracker.EventType.MISSED || e.type() == LapTracker.EventType.WRONG_WAY)
                .map(e -> e.type() + "@" + e.gate()).toList();
        return "Start " + starts + ", Gates " + passed + ", Runden " + laps + (wrong.isEmpty() ? "" : ", Fehler " + wrong);
    }

    public static boolean counted(List<LapTracker.Event> events, int gates) {
        long starts = events.stream().filter(e -> e.type() == LapTracker.EventType.START).count();
        long laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        boolean wrong = events.stream().anyMatch(e -> e.type() == LapTracker.EventType.MISSED || e.type() == LapTracker.EventType.WRONG_WAY);
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i < gates; i++) {
            all.add(i);
        }
        return starts == 1 && laps == 1 && !wrong && passed.equals(all);
    }

    public static @Nullable String clientCheck(CourseData data, MigrationValues.Stack stack) {
        List<GateGeometry> gates = ClientCourses.geometry(data);
        for (GateGeometry gate : gates) {
            if (gate.legs()) {
                return "Kursdaten beim Client tragen das Flag legs";
            }
        }
        ClientGateOpenings openings = ClientGateOpenings.of(data.id(), gates);
        List<String> wrongLegs = new ArrayList<>();
        for (int i = 0; i < gates.size(); i++) {
            if (openings.legs(i) != stack.legs().contains(i)) {
                wrongLegs.add(i + "=" + openings.legs(i));
            }
        }
        if (!wrongLegs.isEmpty()) {
            return "Anbieter meldet Beine falsch: " + wrongLegs + " (erwartet unten " + stack.legs() + ")";
        }
        List<Vec3> path = path(gates, stack.legs());
        List<LapTracker.Event> plain = fly(data.id(), gates, path, GateOpenings.NONE);
        if (plain.stream().anyMatch(e -> e.type() == LapTracker.EventType.LAP)) {
            return "Runde unten ohne Beine gezählt: " + describe(plain);
        }
        List<LapTracker.Event> events = fly(data.id(), gates, path, openings);
        if (!counted(events, gates.size())) {
            return "Runde unten durch das Gate auf Beinen falsch gezählt: " + describe(events);
        }
        return null;
    }

    static StepResult step(SelfTest.StepContext ctx) {
        JsonObject expected = MigrationSelfTests.expected();
        if (expected == null) {
            return StepResult.fail("keine Erwartung geladen");
        }
        List<MigrationValues.Stack> stacks = MigrationValues.stacks(expected);
        if (stacks.isEmpty()) {
            return StepResult.fail("Testwelt ohne gestapelte Gates in einem Kurs");
        }
        MigrationValues.Stack stack = stacks.getFirst();
        Map<String, Object> state = ctx.state();
        AtomicReference<String> server = cast(state.computeIfAbsent("server", k -> new AtomicReference<String>()));
        if (ctx.tick() == 0) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt geöffnet");
            }
            state.put(FUTURE, ctx.onServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course course = registry == null ? null : registry.course(stack.course());
                if (course == null) {
                    server.set("Kurs " + stack.course() + " fehlt auf dem Server");
                    return;
                }
                GateGeometry first = course.toData().geometry().getFirst();
                Vec3 stand = MigrationValues.point(first, -6.0, 3.0, first.y());
                player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.of(), player.getYRot(), 0.0F, true);
                String legs = MigrationValues.legsOnServer(player.level(), course, stack);
                server.set(legs == null ? "ok" : legs);
            }));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = server.get();
        if (state.containsKey("lap")) {
            if (result == null || !result.startsWith("ok")) {
                return StepResult.fail(result == null ? "Server ohne Ergebnis" : result);
            }
            ctx.log("Server: " + result.substring(4));
            return StepResult.pass();
        }
        if (!"ok".equals(result)) {
            return StepResult.fail("Server: " + result);
        }
        CourseData data = ClientCourses.get(stack.course());
        String problem = data == null ? "Kurs " + stack.course() + " beim Client fehlt" : clientCheck(data, stack);
        if (problem != null) {
            return ctx.tick() > CLIENT_WAIT ? StepResult.fail(problem) : StepResult.pending();
        }
        ctx.log("Client: Kurs " + stack.course() + " mit " + data.gates().size() + " Gates, unten auf Beinen " + stack.legs()
                + ", Runde unten durch das Gate auf Beinen gezählt, ohne Beine nicht");
        server.set(null);
        state.put("lap", true);
        state.put(FUTURE, ctx.onServer(s -> server.set(MigrationValues.legsLap(s.getPlayerList().getPlayers().getFirst(), stack))));
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
