package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class RaceLineSelfTests {
    private static final String PHASE = "phase";
    private static final String FUTURE = "future";
    private static final String INITIAL = "initial";
    private static final String REMOVED = "removed";
    private static final String RESTORED = "restored";
    private static final long MS = 1_000_000L;

    private RaceLineSelfTests() {
    }

    static StepResult markingStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return StepResult.pending();
        }
        Map<String, Object> state = ctx.state();
        String phase = (String) state.getOrDefault(PHASE, INITIAL);
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future != null) {
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow().getMessage());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
        }
        int count = RaceSelfTests.gateCount();
        GateEntity[] gates = new GateEntity[count];
        for (int i = 0; i < count; i++) {
            if (!(minecraft.level.getEntity(RaceSelfTests.gateId(i)) instanceof GateEntity gate)) {
                return StepResult.fail("Gate " + i + " fehlt auf dem Client");
            }
            gates[i] = gate;
        }
        String problem;
        switch (phase) {
            case INITIAL -> {
                problem = expect(minecraft, gates, new int[]{0, 1, 2});
                if (problem == null) {
                    state.put(FUTURE, ctx.onServer(RaceLineSelfTests::removeMiddle));
                    state.put(PHASE, REMOVED);
                    ctx.log("Start-/Ziel-Gate kariert mit Banner, Gates 2 und 3 nummeriert");
                    return StepResult.pending();
                }
            }
            case REMOVED -> {
                problem = expect(minecraft, gates, new int[]{0, -1, 1});
                if (problem == null) {
                    state.put(FUTURE, ctx.onServer(RaceLineSelfTests::restoreOrder));
                    state.put(PHASE, RESTORED);
                    ctx.log("Nach dem Entfernen ist Gate 2 unmarkiert, das letzte trägt jetzt die 2");
                    return StepResult.pending();
                }
            }
            default -> {
                problem = expect(minecraft, gates, new int[]{0, 1, 2});
                CourseData course = ClientCourses.get(RaceSelfTests.courseId());
                if (problem == null && (course == null || course.gates().size() != count)) {
                    problem = "Kurs nach dem Wiederherstellen nicht synchronisiert";
                }
                for (int i = 0; problem == null && i < count; i++) {
                    CourseData.Gate gate = course.gates().get(i);
                    if (gate.index() != i || Math.abs(gate.z() - gates[i].getZ()) > 1.0E-6) {
                        problem = "Kursreihenfolge nach dem Wiederherstellen falsch bei " + i;
                    }
                }
                if (problem == null) {
                    return StepResult.pass();
                }
            }
        }
        return ctx.tick() > 280 ? StepResult.fail(phase + ": " + problem) : StepResult.pending();
    }

    private static @Nullable String expect(Minecraft minecraft, GateEntity[] gates, int[] indices) {
        for (int i = 0; i < gates.length; i++) {
            GateEntity gate = gates[i];
            int index = indices[i];
            GateRenderer.Marking expected = index < 0 ? GateRenderer.Marking.NONE : index == 0 ? GateRenderer.Marking.START
                    : GateRenderer.Marking.NUMBER;
            if (gate.gateIndex() != index || GateRenderer.marking(gate) != expected) {
                return "Gate " + i + ": index=" + gate.gateIndex() + " markierung=" + GateRenderer.marking(gate) + " erwartet " + expected;
            }
            EntityRenderer<? super GateEntity, ?> renderer = minecraft.getEntityRenderDispatcher().getRenderer(gate);
            if (!(renderer instanceof GateRenderer gateRenderer)) {
                return "kein GateRenderer für Gate " + i;
            }
            GateRenderState state = gateRenderer.createRenderState();
            gateRenderer.extractRenderState(gate, state, 0.0F);
            if (state.marking != expected) {
                return "Render-State Gate " + i + ": " + state.marking + " statt " + expected;
            }
            if (expected == GateRenderer.Marking.NONE && state.label != null) {
                return "Gate " + i + " ohne Kurs trägt eine Beschriftung";
            }
            if (expected != GateRenderer.Marking.NONE && (state.label == null || state.labelWidth <= 0)) {
                return "Gate " + i + " ohne Nummer/Banner";
            }
            if (expected == GateRenderer.Marking.NUMBER && state.number != index + 1) {
                return "Gate " + i + " zeigt " + state.number + " statt " + (index + 1);
            }
            if (expected == GateRenderer.Marking.START && (!state.startModelled || state.startFrame.isEmpty() || state.banner.isEmpty())) {
                return "Start-/Ziel-Gate ohne karierten Rahmen oder Banner";
            }
            if (expected != GateRenderer.Marking.START && state.startModelled) {
                return "Gate " + i + " sieht aus wie das Start-/Ziel-Gate";
            }
        }
        return null;
    }

    private static GateEntity serverGate(ServerPlayer player, int index) {
        Entity entity = player.level().getEntity(RaceSelfTests.gateId(index));
        if (!(entity instanceof GateEntity gate)) {
            throw new IllegalStateException("Gate " + index + " fehlt auf dem Server");
        }
        return gate;
    }

    private static void removeMiddle(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        GateEntity middle = serverGate(player, 1);
        if (!registry.removeGate(middle.getUUID())) {
            throw new IllegalStateException("Gate 2 nicht im Kurs");
        }
        middle.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
    }

    private static void restoreOrder(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        GateEntity middle = serverGate(player, 1);
        GateEntity last = serverGate(player, 2);
        if (!registry.removeGate(last.getUUID())) {
            throw new IllegalStateException("letztes Gate nicht im Kurs");
        }
        last.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        int course = RaceSelfTests.courseId();
        if (registry.appendGate(course, middle) != 1 || registry.appendGate(course, last) != 2) {
            throw new IllegalStateException("Reihenfolge nicht wiederhergestellt");
        }
    }

    static StepResult raceLineStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        CourseData course = ClientCourses.get(RaceSelfTests.courseId());
        if (course == null || minecraft.level == null) {
            return StepResult.fail("Kurs fehlt auf dem Client");
        }
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean report = config.reportResults;
        boolean line = config.raceLine;
        int ahead = config.raceLineGates;
        config.reportResults = false;
        config.raceLine = true;
        config.raceLineGates = 2;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        try {
            long now = tracker.now();
            tracker.tracker().abort(now, LapTracker.AbortReason.RESTART);
            List<GateGeometry> gates = ClientCourses.geometry(course);
            GateGeometry first = gates.getFirst();
            if (RaceLineRenderer.active() || !leadsTo(RaceLineRenderer.currentPath(0.0F), first)) {
                return StepResult.fail("Ideallinie ohne Rennen führt nicht zum Start-Gate");
            }
            Vec3 before = new Vec3(first.x(), first.centerY(), first.z() - 3.0);
            Vec3 after = new Vec3(first.x(), first.centerY(), first.z() + 1.5);
            tracker.selectCourseAt(minecraft, before);
            if (tracker.courseId() != course.id()) {
                return StepResult.fail("Kurs nicht gewählt: " + tracker.courseId());
            }
            tracker.tracker().resetPosition();
            tracker.feed(now, before);
            if (!tracker.tracker().beginCountdown(now, 3L * LapTracker.SECOND)) {
                return StepResult.fail("Countdown startet nicht");
            }
            if (RaceLineRenderer.active() || !leadsTo(RaceLineRenderer.currentPath(0.0F), first)) {
                return StepResult.fail("Ideallinie im Countdown führt nicht zum Start-Gate");
            }
            tracker.tracker().abort(now, LapTracker.AbortReason.RESTART);
            tracker.tracker().resetPosition();
            tracker.feed(now + 10L * MS, before);
            tracker.feed(now + 200L * MS, after);
            if (!tracker.tracker().racing()) {
                return StepResult.fail("Rennen startet nicht: " + tracker.tracker().phase());
            }
            RaceLine.Path path = RaceLineRenderer.currentPath(0.0F);
            if (!RaceLineRenderer.active() || path.isEmpty()) {
                return StepResult.fail("Ideallinie im Rennen nicht sichtbar");
            }
            RaceLine.Sample start = path.ahead().getFirst();
            if (!PilotSession.INSTANCE.isActive() && new Vec3(start.x(), start.y(), start.z()).distanceTo(after) > 1.0E-6) {
                return StepResult.fail("Linie beginnt nicht an der Drohne: " + start);
            }
            GateGeometry next = gates.get(1);
            boolean throughNext = false;
            double furthest = 0.0;
            for (RaceLine.Sample sample : path.ahead()) {
                if (Math.abs(sample.gate()) < 1.0E-9) {
                    throughNext = Math.abs(sample.x() - next.x()) < 1.0E-6 && Math.abs(sample.z() - next.z()) < 1.0E-6
                            && Math.abs(sample.y() - next.centerY()) < 1.0E-6;
                }
                furthest = Math.max(furthest, sample.gate());
            }
            if (!throughNext) {
                return StepResult.fail("Linie läuft nicht durch die Mitte von Gate 2");
            }
            if (furthest > 1.0 + RaceLine.TAIL + 1.0E-9) {
                return StepResult.fail("Linie reicht weiter als 2 Gates: " + furthest);
            }
            config.raceLine = false;
            boolean hiddenBySetting = !RaceLineRenderer.active() && RaceLineRenderer.currentPath(0.0F).isEmpty();
            config.raceLine = true;
            if (!hiddenBySetting) {
                return StepResult.fail("Schalter „Ideallinie“ blendet die Linie nicht aus");
            }
            tracker.tracker().abort(now + 300L * MS, LapTracker.AbortReason.RESTART);
            if (RaceLineRenderer.active() || !leadsTo(RaceLineRenderer.currentPath(0.0F), first)) {
                return StepResult.fail("Ideallinie nach dem Abbruch zeigt nicht zurück zum Start-Gate");
            }
            ctx.log("Linie nur im Rennen: " + path.ahead().size() + " Punkte bis Gate " + String.format("%.2f", furthest));
            return StepResult.pass();
        } finally {
            tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
            tracker.tracker().drainEvents();
            config.reportResults = report;
            config.raceLine = line;
            config.raceLineGates = ahead;
        }
    }

    private static boolean leadsTo(RaceLine.Path path, GateGeometry gate) {
        if (path.isEmpty()) {
            return true;
        }
        for (RaceLine.Sample sample : path.ahead()) {
            if (Math.abs(sample.gate()) < 1.0E-9) {
                return Math.abs(sample.x() - gate.x()) < 1.0E-6 && Math.abs(sample.z() - gate.z()) < 1.0E-6
                        && Math.abs(sample.y() - gate.centerY()) < 1.0E-6;
            }
        }
        return false;
    }
}
