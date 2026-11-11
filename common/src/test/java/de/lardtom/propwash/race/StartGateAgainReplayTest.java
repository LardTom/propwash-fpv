package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartGateAgainReplayTest {
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000L * MS;
    private static final long FRAME = SECOND / 60L;
    private static final long SEND = 50L * MS;
    private static final long LATENCY = 150L * MS;
    private static final long T0 = 100L * SECOND;
    private static final int COURSE = 11;
    private static final String CLASS = "race_5";
    private static final int LAYOUT = 4321;
    private static final double SPEED = 20.0;
    private static final double NEAR = 1.0;

    private record Flight(List<GateGeometry> gates, boolean pointToPoint, int laps, List<double[]> path, double[] at) {
        static Flight of(List<GateGeometry> gates, boolean pointToPoint, int laps, double[][] xz) {
            double y = gates.getFirst().centerY();
            List<double[]> path = new ArrayList<>();
            for (double[] p : xz) {
                path.add(new double[]{p[0], y, p[1]});
            }
            double[] at = new double[path.size()];
            for (int i = 1; i < path.size(); i++) {
                double[] a = path.get(i - 1);
                double[] b = path.get(i);
                at[i] = at[i - 1] + Math.hypot(b[0] - a[0], b[2] - a[2]);
            }
            return new Flight(gates, pointToPoint, laps, path, at);
        }

        long end() {
            return T0 + Math.round(this.at[this.at.length - 1] / SPEED * 1.0E9);
        }

        double[] position(long nanos) {
            double distance = Math.clamp((nanos - T0) * 1.0E-9 * SPEED, 0.0, this.at[this.at.length - 1]);
            int leg = 1;
            while (leg < this.at.length - 1 && this.at[leg] < distance) {
                leg++;
            }
            double[] a = this.path.get(leg - 1);
            double[] b = this.path.get(leg);
            double span = this.at[leg] - this.at[leg - 1];
            double f = span <= 0.0 ? 1.0 : Math.clamp((distance - this.at[leg - 1]) / span, 0.0, 1.0);
            return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
        }

        Quaternionf facing(long nanos) {
            double[] p = this.position(nanos - 20L * MS);
            double[] q = this.position(nanos + 20L * MS);
            return new Quaternionf().rotateY((float) Math.atan2(-(q[0] - p[0]), -(q[2] - p[2])));
        }

        int checkpoints() {
            return CourseCheckpoints.count(CourseCheckpoints.kinds(this.gates));
        }
    }

    private record Lap(int lap, long lapNanos, long startNanos, long endNanos) {
    }

    private record Client(List<Lap> laps, int starts, GhostRecorder recorder) {
    }

    private static Client client(Flight flight) {
        LapTracker tracker = new LapTracker();
        tracker.setCourse(COURSE, flight.gates(), flight.pointToPoint(), T0);
        tracker.setLapTarget(flight.laps());
        GhostRecorder recorder = new GhostRecorder();
        List<Lap> laps = new ArrayList<>();
        int starts = 0;
        for (long t = T0; t <= flight.end(); t += FRAME) {
            double[] at = flight.position(t);
            tracker.update(t, at[0], at[1], at[2]);
            recorder.sample(t, at[0], at[1], at[2], flight.facing(t));
            for (LapTracker.Event event : tracker.drainEvents()) {
                switch (event.type()) {
                    case START, GO -> {
                        starts++;
                        recorder.onLapStart(COURSE, event.nanos());
                    }
                    case LAP -> {
                        recorder.onLapComplete(event.valueNanos(), event.nanos(), CLASS, LAYOUT);
                        laps.add(new Lap(event.lap(), event.valueNanos(), event.nanos() - event.valueNanos(), event.nanos()));
                    }
                    case FINISH, ABORT -> recorder.stop();
                    default -> {
                    }
                }
            }
        }
        return new Client(laps, starts, recorder);
    }

    private record Stored(Lap lap, LapVerifier.VerifiedLap verified, GhostTrack server, GhostUploadCheck.Reference reference) {
    }

    private static List<Stored> server(Flight flight, Client client) {
        List<GateGeometry> plain = new ArrayList<>();
        List<List<CourseOrder.Pass>> groups = new ArrayList<>();
        for (int i = 0; i < flight.gates().size(); i++) {
            GateGeometry base = flight.gates().get(i).withReverse(false);
            int at = plain.indexOf(base);
            if (at < 0) {
                plain.add(base);
                groups.add(new ArrayList<>());
                at = plain.size() - 1;
            }
            groups.get(at).add(new CourseOrder.Pass(i, flight.gates().get(i).reversed()));
        }
        List<CourseElement> kinds = CourseCheckpoints.kinds(flight.gates());
        LapVerifier verifier = new LapVerifier();
        GhostRecorder ghost = new GhostRecorder();
        long[] next = {0L};
        long startedAt = client.laps().getFirst().startNanos() + LATENCY;
        boolean begun = false;
        double[] last = null;
        long lastArrival = 0L;
        for (long t = T0; t <= flight.end(); t += SEND) {
            long arrival = t + LATENCY;
            if (!begun && arrival >= startedAt) {
                ghost.onLapStart(COURSE, startedAt - RaceServer.START_SLACK_NANOS - RaceServer.COUNTDOWN_SLACK_NANOS);
                begun = true;
            }
            double[] at = flight.position(t);
            ghost.sample(arrival, at[0], at[1], at[2], flight.facing(t));
            if (last != null) {
                List<double[]> hits = new ArrayList<>();
                for (int g = 0; g < plain.size(); g++) {
                    CourseCrossings.collect(plain.get(g), groups.get(g), COURSE, (element, stage) -> CourseCheckpoints.index(kinds, element, stage), last[0], last[1],
                            last[2], at[0], at[1], at[2], () -> next[0]++, hits);
                }
                hits.removeIf(hit -> hit[CourseCrossings.CHECKPOINT] < 0.0);
                int loop = flight.pointToPoint() ? CourseCrossings.OPEN : flight.checkpoints();
                CourseCrossings.sort(hits, id -> loop);
                for (double[] hit : hits) {
                    long time = lastArrival + Math.round((arrival - lastArrival) * hit[CourseCrossings.TIME]);
                    verifier.record(COURSE, (int) hit[CourseCrossings.CHECKPOINT], time, (long) hit[CourseCrossings.EVENT]);
                }
            }
            last = at;
            lastArrival = arrival;
        }
        List<Stored> stored = new ArrayList<>();
        long lastEnd = LapVerifier.NO_SEQUENCE;
        for (Lap lap : client.laps()) {
            LapVerifier.VerifiedLap verified = RaceServer.verifiedLap(verifier, COURSE, flight.checkpoints(), flight.pointToPoint(), lastEnd, lap.lap() == 1,
                    startedAt, lap.lapNanos(), 0L);
            assertNotNull(verified, "Server findet Runde " + lap.lap() + " nicht");
            assertTrue(lap.lapNanos() + RaceServer.crossingTolerance(verified.nanos()) >= verified.nanos(), "Server wertet Runde " + lap.lap());
            lastEnd = verified.endSequence();
            GhostTrack cut = ghost.cut(verified.startNanos(), verified.endNanos(), lap.lapNanos(), CLASS, LAYOUT, GhostServer.FINE_INTERVAL_NANOS,
                    GhostRecorder.LEAD_NANOS);
            assertNotNull(cut, "Server schneidet Runde " + lap.lap() + " nicht");
            GhostRecorder.Raw raw = ghost.window(verified.startNanos() - GhostRecorder.LEAD_NANOS, verified.endNanos() + GhostRecorder.LEAD_NANOS);
            stored.add(new Stored(lap, verified, cut, GhostUploadCheck.Reference.of(raw, verified.startNanos(), verified.endNanos(), cut, flight.gates(),
                    lap.lapNanos(), 0L, LATENCY)));
            ghost.trim(verified.endNanos() - SECOND);
        }
        return stored;
    }

    private static String follows(Flight flight, GhostTrack track, long startNanos, long lapNanos, String what) {
        long gap = Math.abs(track.durationNanos() - lapNanos);
        if (gap > 60L * MS) {
            return what + ": Dauer " + track.durationNanos() / MS + " ms statt " + lapNanos / MS + " ms";
        }
        double[] at = new double[3];
        Quaternionf rotation = new Quaternionf();
        for (long t = 0L; t <= track.durationNanos(); t += 250L * MS) {
            if (!track.pose(t, at, rotation)) {
                return what + ": keine Pose bei " + t / MS + " ms";
            }
            double[] want = flight.position(startNanos + t);
            double off = Math.sqrt((at[0] - want[0]) * (at[0] - want[0]) + (at[1] - want[1]) * (at[1] - want[1]) + (at[2] - want[2]) * (at[2] - want[2]));
            if (off > NEAR) {
                return what + ": bei " + t / MS + " ms " + String.format(Locale.ROOT, "%.1f", off) + " Blöcke neben dem Flug ab dem Start der Uhr";
            }
        }
        return null;
    }

    private static void sameInterval(Flight flight, int laps) {
        Client client = client(flight);
        assertEquals(1, client.starts(), "Erneuter Durchflug startet die Uhr nicht neu");
        assertEquals(laps, client.laps().size(), "Runden des Clients");
        List<Stored> stored = server(flight, client);
        for (Stored lap : stored) {
            String label = "Runde " + lap.lap().lap();
            long start = lap.lap().startNanos();
            assertTrue(Math.abs(lap.verified().startNanos() - LATENCY - start) <= 20L * MS,
                    label + ": Server beginnt " + (lap.verified().startNanos() - LATENCY - start) / MS + " ms nach dem Start der Uhr");
            GhostTrack upload = client.recorder().keptLap(COURSE, lap.lap().lapNanos());
            assertNotNull(upload, label + ": Client behält die Runde nicht");
            assertEquals(null, follows(flight, upload, start, lap.lap().lapNanos(), label + ", Spur des Piloten"));
            assertEquals(null, follows(flight, lap.server(), start, lap.lap().lapNanos(), label + ", Spur des Servers"));
            GhostUploadCheck.Result result = GhostUploadCheck.check(lap.reference(), upload, flight.gates(), flight.pointToPoint());
            assertEquals(GhostUploadCheck.Result.ACCEPTED, result, label + ": Upload der richtigen Spur");
            GhostTrack replay = GhostUploads.merge(lap.server(), upload);
            assertEquals(lap.lap().lapNanos(), replay.lapNanos());
            assertEquals(null, follows(flight, replay, start, lap.lap().lapNanos(), label + ", Replay"));
        }
    }

    private static List<GateGeometry> triangle() {
        return List.of(GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM), GateGeometry.of(0.0, 64.0, 30.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(20.0, 64.0, 15.0, 180.0F, GateSize.MEDIUM));
    }

    private static final double[][] RUN_UP = {{0.0, -15.0}, {0.0, 1.0}};
    private static final double[][] BACK_TO_START = {{-10.0, 4.0}, {-10.0, -12.0}, {0.0, -12.0}, {0.0, 1.0}};
    private static final double[][] AFTER_B_BACK = {{0.0, 31.0}, {-10.0, 35.0}, {-10.0, -12.0}, {0.0, -12.0}, {0.0, 1.0}};
    private static final double[][] LAP = {{0.0, 31.0}, {20.0, 35.0}, {20.0, 14.0}, {20.0, -8.0}, {0.0, -8.0}, {0.0, 1.0}};
    private static final double[][] OUT = {{0.0, 10.0}};

    private static double[][] join(double[][]... parts) {
        List<double[]> out = new ArrayList<>();
        for (double[][] part : parts) {
            out.addAll(List.of(part));
        }
        return out.toArray(new double[0][]);
    }

    @Test
    void startGateAgainBeforeTheFirstGate() {
        sameInterval(Flight.of(triangle(), false, 1, join(RUN_UP, BACK_TO_START, LAP, OUT)), 1);
    }

    @Test
    void startGateAgainAfterTheFirstGate() {
        sameInterval(Flight.of(triangle(), false, 1, join(RUN_UP, AFTER_B_BACK, LAP, OUT)), 1);
    }

    @Test
    void startGateAgainInEveryLapOfARace() {
        sameInterval(Flight.of(triangle(), false, 3, join(RUN_UP, AFTER_B_BACK, LAP, BACK_TO_START, LAP, LAP, OUT)), 3);
    }

    @Test
    void startGateAgainOnPointToPoint() {
        List<GateGeometry> gates = List.of(GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM), GateGeometry.of(0.0, 64.0, 30.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 60.0, 0.0F, GateSize.MEDIUM));
        sameInterval(Flight.of(gates, true, 1, join(RUN_UP, AFTER_B_BACK, new double[][]{{0.0, 70.0}})), 1);
    }

    @Test
    void startGateAgainOnALoopCourse() {
        GateGeometry a = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM);
        List<GateGeometry> gates = List.of(a, GateGeometry.of(0.0, 64.0, 30.0, 0.0F, GateSize.MEDIUM), a.withReverse(true));
        double[][] lap = {{0.0, 31.0}, {12.0, 35.0}, {12.0, 6.0}, {0.0, 6.0}, {0.0, -6.0}, {-10.0, -6.0}, {-10.0, -12.0}, {0.0, -12.0}, {0.0, 1.0}};
        sameInterval(Flight.of(gates, false, 2, join(RUN_UP, BACK_TO_START, lap, AFTER_B_BACK, lap, OUT)), 2);
    }

    private static LapVerifier recrossed() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, 10L * SECOND);
        verifier.record(COURSE, 1, 13L * SECOND);
        verifier.record(COURSE, 0, 16L * SECOND);
        verifier.record(COURSE, 1, 19L * SECOND);
        verifier.record(COURSE, 2, 22L * SECOND);
        verifier.record(COURSE, 0, 25L * SECOND);
        return verifier;
    }

    @Test
    void verifierListsEveryStartPassOfTheLap() {
        LapVerifier.VerifiedLap lap = recrossed().find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(lap);
        assertEquals(16L * SECOND, lap.startNanos(), "find bleibt beim letzten Start-Durchflug");
        assertEquals(List.of(10L * SECOND, 16L * SECOND), Arrays.stream(lap.starts()).boxed().toList());
        LapVerifier.VerifiedLap late = recrossed().find(COURSE, 3, LapVerifier.NO_SEQUENCE, 12L * SECOND);
        assertNotNull(late);
        assertEquals(List.of(16L * SECOND), Arrays.stream(late.starts()).boxed().toList(), "Durchflüge vor dem Zeitfenster zählen nicht");
    }

    @Test
    void serverTakesTheStartThatMatchesTheTimer() {
        LapVerifier.VerifiedLap lap = recrossed().find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(lap);
        assertEquals(10L * SECOND, RaceServer.timedStart(lap, 15L * SECOND, Long.MIN_VALUE, Long.MIN_VALUE).startNanos());
        assertEquals(16L * SECOND, RaceServer.timedStart(lap, 9L * SECOND, Long.MIN_VALUE, Long.MIN_VALUE).startNanos());
        assertEquals(16L * SECOND, RaceServer.timedStart(lap, 12L * SECOND, Long.MIN_VALUE, Long.MIN_VALUE).startNanos(),
                "länger als die zweite, kürzer als die erste: wie bisher die zweite");
        assertEquals(16L * SECOND, RaceServer.timedStart(lap, 5L * SECOND, Long.MIN_VALUE, Long.MIN_VALUE).startNanos(),
                "schneller als jede Server-Runde: der letzte Start, der Server lehnt die Zeit ab");
        assertEquals(16L * SECOND, RaceServer.timedStart(lap, 15L * SECOND, 12L * SECOND, Long.MIN_VALUE).startNanos(),
                "Start vor der Startmeldung zählt nicht");
        assertEquals(10L * SECOND, RaceServer.timedStart(lap, 13L * SECOND, Long.MIN_VALUE, 12L * SECOND).startNanos(),
                "Fehlstart: Uhr ab dem Ende des Countdowns");
    }

    @Test
    void secondLapStartsAtTheEndOfTheFirst() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, 0L);
        verifier.record(COURSE, 1, 3L * SECOND);
        verifier.record(COURSE, 2, 6L * SECOND);
        verifier.record(COURSE, 0, 9L * SECOND);
        LapVerifier.VerifiedLap first = RaceServer.verifiedLap(verifier, COURSE, 3, false, LapVerifier.NO_SEQUENCE, true, 100L * MS, 9L * SECOND, 0L);
        assertNotNull(first);
        verifier.record(COURSE, 0, 12L * SECOND);
        verifier.record(COURSE, 1, 15L * SECOND);
        verifier.record(COURSE, 2, 18L * SECOND);
        verifier.record(COURSE, 0, 21L * SECOND);
        LapVerifier.VerifiedLap second = RaceServer.verifiedLap(verifier, COURSE, 3, false, first.endSequence(), false, 100L * MS, 12L * SECOND, 0L);
        assertNotNull(second);
        assertEquals(9L * SECOND, second.startNanos());
        assertEquals(12L * SECOND, second.nanos());
    }
}
