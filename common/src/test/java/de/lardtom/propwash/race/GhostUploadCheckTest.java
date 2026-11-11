package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostUploadCheckTest {
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000L * MS;
    private static final long LAP = 6L * SECOND;
    private static final long T0 = 50L * SECOND;
    private static final long FRAME = SECOND / 60L;
    private static final long SEND = 50L * MS;
    private static final int COURSE = 7;
    private static final String CLASS = "freestyle_5";
    private static final int LAYOUT = 1234;

    private record Course(List<GateGeometry> gates, List<double[]> path, double[] at, double start, double speed, long lap) {
        double[] position(long nanos) {
            double distance = this.start + (nanos - T0) * 1.0E-9 * this.speed;
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

        long first() {
            return T0 - Math.round(this.start / this.speed * 1.0E9);
        }
    }

    private static Course course() {
        return course(24.0, LAP);
    }

    private static Course raceCourse(double speed) {
        return course(speed * 15.0 - 10.0, 30L * SECOND);
    }

    private static Course course(double reach, long lap) {
        GateGeometry a = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM);
        double nx = a.normalX();
        double nz = a.normalZ();
        GateGeometry b = GateGeometry.of(nx * reach, 64.0, nz * reach, 0.0F, GateSize.MEDIUM);
        double sx = nz;
        double sz = -nx;
        double y = a.centerY();
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{-12.0 * nx, y, -12.0 * nz});
        path.add(new double[]{-nx, y, -nz});
        path.add(new double[]{nx, y, nz});
        path.add(new double[]{(reach - 1.0) * nx, y, (reach - 1.0) * nz});
        path.add(new double[]{(reach + 1.0) * nx, y, (reach + 1.0) * nz});
        path.add(new double[]{(reach + 5.0) * nx + 7.0 * sx, y, (reach + 5.0) * nz + 7.0 * sz});
        path.add(new double[]{-5.0 * nx + 7.0 * sx, y, -5.0 * nz + 7.0 * sz});
        path.add(new double[]{-nx, y, -nz});
        path.add(new double[]{nx, y, nz});
        path.add(new double[]{12.0 * nx, y, 12.0 * nz});
        double[] at = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            double[] p = path.get(i - 1);
            double[] q = path.get(i);
            at[i] = at[i - 1] + Math.sqrt((q[0] - p[0]) * (q[0] - p[0]) + (q[1] - p[1]) * (q[1] - p[1]) + (q[2] - p[2]) * (q[2] - p[2]));
        }
        double start = at[1] + 1.0;
        double finish = at[7] + 1.0;
        return new Course(List.of(a, b), path, at, start, (finish - start) / (lap * 1.0E-9), lap);
    }

    private static Quaternionf facing(LongFunction<double[]> pose, long nanos) {
        double[] p = pose.apply(nanos - 20L * MS);
        double[] q = pose.apply(nanos + 20L * MS);
        return new Quaternionf().rotateY((float) Math.atan2(-(q[0] - p[0]), -(q[2] - p[2])));
    }

    private static GhostTrack clientTrack(Course course, LongFunction<double[]> pose, long lapNanos) {
        GhostRecorder recorder = new GhostRecorder();
        long end = T0 + lapNanos;
        boolean started = false;
        for (long t = course.first(); ; t += FRAME) {
            if (!started && t >= T0) {
                recorder.onLapStart(COURSE, T0);
                started = true;
            }
            double[] at = pose.apply(t);
            recorder.sample(t, at[0], at[1], at[2], facing(pose, t));
            if (t >= end) {
                recorder.onLapComplete(lapNanos, end, CLASS, LAYOUT);
                break;
            }
        }
        GhostTrack track = recorder.keptLap(COURSE, lapNanos);
        assertNotNull(track, "Client behält die Runde nicht im feinen Raster");
        return track;
    }

    private record Server(GhostUploadCheck.Reference reference, GhostTrack track) {
    }

    private static Server server(Course course, long seed, double loss) {
        return server(course, seed, loss, 300L * MS, 60L * MS);
    }

    private static Server server(Course course, long seed, double loss, long retransmit, long ping) {
        Random random = new Random(seed);
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(COURSE, course.first() - SECOND);
        List<long[]> crossings = new ArrayList<>();
        double[] last = null;
        long lastArrival = Long.MIN_VALUE;
        long prevArrival = 0L;
        for (long t = course.first(); t <= T0 + course.lap() + 600L * MS; t += SEND) {
            long arrival = t + 120L * MS + Math.round(random.nextDouble() * 80L * MS);
            if (t > T0 + 700L * MS && t < T0 + course.lap() - 700L * MS && random.nextDouble() < loss) {
                arrival += retransmit;
            }
            arrival = Math.max(arrival, lastArrival);
            lastArrival = arrival;
            arrival = Math.ceilDiv(arrival, SEND) * SEND;
            double[] at = course.position(t);
            if (last != null) {
                double hit = course.gates().getFirst().crossing(last[0], last[1], last[2], at[0], at[1], at[2], true);
                if (hit >= 0.0) {
                    long from = arrival > prevArrival ? prevArrival : arrival;
                    crossings.add(new long[]{from + Math.round((arrival - from) * hit)});
                }
            }
            recorder.sample(arrival, at[0], at[1], at[2], facing(course::position, t));
            last = at;
            prevArrival = arrival;
        }
        assertEquals(2, crossings.size(), "Server sieht Start und Ziel");
        long start = crossings.get(0)[0];
        long end = crossings.get(1)[0];
        GhostTrack cut = recorder.cut(start, end, course.lap(), CLASS, LAYOUT, GhostServer.FINE_INTERVAL_NANOS, GhostRecorder.LEAD_NANOS);
        assertNotNull(cut);
        GhostRecorder.Raw raw = recorder.window(start - GhostRecorder.LEAD_NANOS, end + GhostRecorder.LEAD_NANOS);
        return new Server(GhostUploadCheck.Reference.of(raw, start, end, cut, course.gates(), course.lap(), 0L, ping), cut);
    }

    private static GhostUploadCheck.Result check(Course course, Server server, GhostTrack track) {
        return GhostUploadCheck.check(server.reference(), track, course.gates(), false);
    }

    @Test
    void realTrackOfTheLapIsAccepted() {
        Course course = course();
        for (long seed = 1L; seed <= 6L; seed++) {
            Server server = server(course, seed, 0.06);
            GhostTrack track = clientTrack(course, course::position, LAP);
            assertEquals(GhostRecorder.UPLOAD_INTERVAL_NANOS, track.intervalNanos());
            assertEquals(GhostUploadCheck.Result.ACCEPTED, check(course, server, track), "Netz " + seed);
        }
    }

    @Test
    void realTrackAtRaceSpeedWithLongStallsIsAccepted() {
        for (double speed : new double[]{35.0, 40.0, 60.0}) {
            Course course = raceCourse(speed);
            GhostTrack track = clientTrack(course, course::position, course.lap());
            StringBuilder rejected = new StringBuilder();
            for (long seed = 1L; seed <= 24L; seed++) {
                GhostUploadCheck.Result result = check(course, server(course, seed, 0.06, 500L * MS, 120L * MS), track);
                if (!result.accepted()) {
                    rejected.append(" Netz ").append(seed).append(' ').append(result);
                }
            }
            assertEquals("", rejected.toString(), "Tempo " + Math.round(course.speed() * 10.0) / 10.0 + " Blöcke/s, Neusendung 500 ms, Ping 120 ms");
        }
    }

    @Test
    void cheatsAtRaceSpeedWithLongStallsAreStillRejected() {
        Course course = raceCourse(35.0);
        long lap = course.lap();
        LongFunction<double[]> detour = t -> {
            double[] at = course.position(t);
            double f = (t - (T0 + lap / 2L)) / (double) (3_000L * MS);
            if (f > 0.0 && f < 1.0) {
                at[1] += 6.0 * Math.sin(Math.PI * f);
            }
            return at;
        };
        LongFunction<double[]> lifted = t -> {
            double[] at = course.position(t);
            at[1] += 3.0;
            return at;
        };
        LongFunction<double[]> warped = t -> {
            double f = Math.clamp((t - T0) / (double) lap, 0.0, 1.0);
            return course.position(t + Math.round(600L * MS * Math.sin(Math.PI * f)));
        };
        for (long seed = 1L; seed <= 24L; seed++) {
            Server server = server(course, seed, 0.06, 500L * MS, 120L * MS);
            assertEquals(GhostUploadCheck.Result.PATH, check(course, server, clientTrack(course, detour, lap)), "Umweg, Netz " + seed);
            assertNotEquals(GhostUploadCheck.Result.ACCEPTED, check(course, server, clientTrack(course, lifted, lap)), "angehoben, Netz " + seed);
            assertNotEquals(GhostUploadCheck.Result.ACCEPTED, check(course, server, clientTrack(course, warped, lap)), "verschobene Zeit, Netz " + seed);
        }
    }

    @Test
    void cleanNetworkIsAccepted() {
        Course course = course();
        assertEquals(GhostUploadCheck.Result.ACCEPTED, check(course, server(course, 9L, 0.0), clientTrack(course, course::position, LAP)));
    }

    @Test
    void shiftedTimeIsRejected() {
        Course course = course();
        Server server = server(course, 3L, 0.06);
        LongFunction<double[]> warped = t -> {
            double f = Math.clamp((t - T0) / (double) LAP, 0.0, 1.0);
            return course.position(t + Math.round(600L * MS * Math.sin(Math.PI * f)));
        };
        GhostUploadCheck.Result result = check(course, server, clientTrack(course, warped, LAP));
        assertTrue(result == GhostUploadCheck.Result.GATES || result == GhostUploadCheck.Result.PATH, "verschobene Zeit: " + result);
    }

    @Test
    void otherLapTimeIsRejected() {
        Course course = course();
        Server server = server(course, 4L, 0.0);
        LongFunction<double[]> slower = t -> course.position(T0 + Math.round((t - T0) * 0.9));
        long longer = LAP + LAP / 9L;
        GhostRecorder recorder = new GhostRecorder();
        boolean started = false;
        for (long t = course.first(); ; t += FRAME) {
            if (!started && t >= T0) {
                recorder.onLapStart(COURSE, T0);
                started = true;
            }
            double[] at = slower.apply(t);
            recorder.sample(t, at[0], at[1], at[2], facing(slower, t));
            if (t >= T0 + longer) {
                recorder.onLapComplete(longer, T0 + longer, CLASS, LAYOUT);
                break;
            }
        }
        assertEquals(GhostUploadCheck.Result.DURATION, check(course, server, recorder.keptLap(COURSE, longer)));
    }

    @Test
    void teleportedPositionIsRejected() {
        Course course = course();
        Server server = server(course, 5L, 0.06);
        LongFunction<double[]> jumpy = t -> {
            double[] at = course.position(t);
            if (t > T0 + 3_000L * MS && t < T0 + 3_040L * MS) {
                at[1] += 12.0;
            }
            return at;
        };
        assertEquals(GhostUploadCheck.Result.SPEED, check(course, server, clientTrack(course, jumpy, LAP)));
    }

    @Test
    void missingGateIsRejected() {
        Course course = course();
        Server server = server(course, 6L, 0.06);
        GateGeometry b = course.gates().get(1);
        LongFunction<double[]> around = t -> {
            double[] at = course.position(t);
            double dx = at[0] - b.x();
            double dz = at[2] - b.z();
            double near = Math.sqrt(dx * dx + dz * dz);
            if (near < 5.0) {
                double push = 3.0 * (1.0 - near / 5.0) + 1.6;
                at[0] += b.normalZ() * push;
                at[2] -= b.normalX() * push;
            }
            return at;
        };
        assertEquals(GhostUploadCheck.Result.GATES, check(course, server, clientTrack(course, around, LAP)));
    }

    @Test
    void detourBetweenServerSamplesIsRejected() {
        Course course = course();
        Server server = server(course, 7L, 0.0);
        LongFunction<double[]> detour = t -> {
            double[] at = course.position(t);
            double f = (t - (T0 + 4_000L * MS)) / (double) (800L * MS);
            if (f > 0.0 && f < 1.0) {
                at[1] += 6.0 * Math.sin(Math.PI * f);
            }
            return at;
        };
        assertEquals(GhostUploadCheck.Result.PATH, check(course, server, clientTrack(course, detour, LAP)));
    }

    @Test
    void lapFromSomewhereElseIsRejected() {
        Course course = course();
        Server server = server(course, 8L, 0.0);
        LongFunction<double[]> lifted = t -> {
            double[] at = course.position(t);
            at[1] += 3.0;
            return at;
        };
        assertNotEquals(GhostUploadCheck.Result.ACCEPTED, check(course, server, clientTrack(course, lifted, LAP)));
    }

    @Test
    void mergedTrackKeepsServerTimeAndLook() {
        Course course = course();
        Server server = server(course, 2L, 0.06);
        GhostTrack upload = clientTrack(course, course::position, LAP);
        GhostTrack serverTrack = server.track().withLap(LAP, CLASS, LAYOUT).withRules(RaceStandard.DEFAULTS);
        GhostTrack merged = GhostUploads.merge(serverTrack, upload);
        assertEquals(serverTrack.lapNanos(), merged.lapNanos());
        assertEquals(serverTrack.layout(), merged.layout());
        assertEquals(serverTrack.frame(), merged.frame());
        assertEquals(serverTrack.rules(), merged.rules());
        assertEquals(upload.intervalNanos(), merged.intervalNanos());
        assertTrue(merged.leadNanos() <= serverTrack.leadNanos(), "Vorlauf nur so weit, wie der Server ihn gesehen hat");
        assertEquals(upload.samples() - (upload.leadNanos() - merged.leadNanos()) / upload.intervalNanos(), merged.samples());
        assertEquals(upload.durationNanos(), merged.durationNanos());
    }

    @Test
    void leadLongerThanRecordedIsRejectedAndPingIsCapped() {
        Course course = course();
        Server server = server(course, 2L, 0.0);
        GhostTrack upload = clientTrack(course, course::position, LAP);
        int extra = (int) (2L * GhostRecorder.LEAD_NANOS / upload.intervalNanos());
        double[] positions = new double[(upload.samples() + extra) * 3];
        float[] rotations = new float[(upload.samples() + extra) * 4];
        for (int i = 0; i < extra; i++) {
            positions[i * 3] = upload.x(0);
            positions[i * 3 + 1] = upload.y(0);
            positions[i * 3 + 2] = upload.z(0);
            rotations[i * 4 + 3] = 1.0F;
        }
        System.arraycopy(upload.positions(), 0, positions, extra * 3, upload.samples() * 3);
        System.arraycopy(upload.rotations(), 0, rotations, extra * 4, upload.samples() * 4);
        GhostTrack longLead = new GhostTrack(upload.intervalNanos(), positions, rotations, LAP, CLASS, LAYOUT, null, null,
                upload.leadNanos() + extra * upload.intervalNanos());
        assertEquals(GhostUploadCheck.Result.FORMAT, check(course, server, longLead));
        GhostUploadCheck.Reference slow = new GhostUploadCheck.Reference(server.reference().times(), server.reference().positions(), LAP, 0L,
                server.reference().serverLapNanos(), server.reference().splits(), 60_000L * MS);
        assertEquals(GhostUploadCheck.MAX_PING_NANOS, slow.pingNanos());
    }

    @Test
    void keptLapsAreBoundedAndFoundByCourseAndTime() {
        Course course = course();
        GhostRecorder recorder = new GhostRecorder();
        long t = course.first();
        recorder.onLapStart(COURSE, t);
        for (int lap = 1; lap <= GhostRecorder.KEPT_LAPS + 2; lap++) {
            long end = t + SECOND;
            for (; t < end; t += FRAME) {
                double[] at = course.position(t);
                recorder.sample(t, at[0], at[1], at[2], new Quaternionf());
            }
            recorder.onLapComplete(lap * MS + SECOND, end, CLASS, LAYOUT);
        }
        assertEquals(GhostRecorder.KEPT_LAPS, recorder.keptLaps());
        assertEquals(null, recorder.keptLap(COURSE, MS + SECOND));
        assertNotNull(recorder.keptLap(COURSE, (GhostRecorder.KEPT_LAPS + 2) * MS + SECOND));
        assertEquals(null, recorder.keptLap(COURSE + 1, (GhostRecorder.KEPT_LAPS + 2) * MS + SECOND));
        recorder.reset();
        assertEquals(0, recorder.keptLaps());
    }

    private static final long[] RESENDS = {400L * MS, 500L * MS, 600L * MS};

    @Test
    void racingSpeedWithStallsIsAccepted() {
        for (double speed : new double[]{35.0, 40.0}) {
            Course course = raceCourse(speed);
            GhostTrack track = clientTrack(course, course::position, course.lap());
            for (long resend : RESENDS) {
                StringBuilder rejected = new StringBuilder();
                for (long seed = 1L; seed <= 24L; seed++) {
                    GhostUploadCheck.Result result = check(course, server(course, seed, 0.06, resend, 120L * MS), track);
                    if (!result.accepted()) {
                        rejected.append(" Netz ").append(seed).append(' ').append(result);
                    }
                }
                assertEquals("", rejected.toString(), String.format("%.1f B/s, Neusendung %d ms, 6 %% Verlust, Ping 120 ms", course.speed(), resend / MS));
            }
        }
    }

    @Test
    void outlierRunRejectsDetoursAndKeepsHonestLaps() {
        for (double speed : new double[]{35.0, 40.0}) {
            Course course = raceCourse(speed);
            GhostTrack track = clientTrack(course, course::position, course.lap());
            GhostTrack detour = clientTrack(course, t -> {
                double[] at = course.position(t);
                double f = (t - (T0 + 20_000L * MS)) / (double) (2_000L * MS);
                if (f > 0.0 && f < 1.0) {
                    at[1] += 6.0 * Math.sin(Math.PI * f);
                }
                return at;
            }, course.lap());
            for (long resend : RESENDS) {
                int accepted = 0;
                StringBuilder rejected = new StringBuilder();
                StringBuilder passed = new StringBuilder();
                for (long seed = 1L; seed <= 48L; seed++) {
                    Server server = server(course, seed, 0.06, resend, 120L * MS);
                    GhostUploadCheck.Result result = check(course, server, track);
                    if (result.accepted()) {
                        accepted++;
                    } else {
                        rejected.append(" Netz ").append(seed).append(' ').append(result);
                    }
                    if (check(course, server, detour).accepted()) {
                        passed.append(" Netz ").append(seed);
                    }
                }
                String where = String.format("%.1f B/s, Neusendung %d ms", course.speed(), resend / MS);
                assertTrue(accepted >= 47, where + ": " + accepted + " von 48 echten Runden angenommen," + rejected);
                assertEquals("", passed.toString(), where + ": Umweg von 2 s angenommen");
            }
        }
    }

    @Test
    void forgeriesAtRacingSpeedWithStallsAreRejected() {
        for (double speed : new double[]{35.0, 40.0}) {
            Course course = raceCourse(speed);
            long lap = course.lap();
            GateGeometry b = course.gates().get(1);
            LongFunction<double[]> warped = t -> {
                double f = Math.clamp((t - T0) / (double) lap, 0.0, 1.0);
                return course.position(t + Math.round(600L * MS * Math.sin(Math.PI * f)));
            };
            LongFunction<double[]> jumpy = t -> {
                double[] at = course.position(t);
                if (t > T0 + 13_000L * MS && t < T0 + 13_040L * MS) {
                    at[1] += 12.0;
                }
                return at;
            };
            LongFunction<double[]> around = t -> {
                double[] at = course.position(t);
                double dx = at[0] - b.x();
                double dz = at[2] - b.z();
                double near = Math.sqrt(dx * dx + dz * dz);
                if (near < 5.0) {
                    double push = 3.0 * (1.0 - near / 5.0) + 1.6;
                    at[0] += b.normalZ() * push;
                    at[2] -= b.normalX() * push;
                }
                return at;
            };
            LongFunction<double[]> detour = t -> {
                double[] at = course.position(t);
                double f = (t - (T0 + 20_000L * MS)) / (double) (2_000L * MS);
                if (f > 0.0 && f < 1.0) {
                    at[1] += 6.0 * Math.sin(Math.PI * f);
                }
                return at;
            };
            LongFunction<double[]> lifted = t -> {
                double[] at = course.position(t);
                at[1] += 3.0;
                return at;
            };
            for (long resend : RESENDS) {
                int gateSeen = 0;
                for (long seed = 1L; seed <= 6L; seed++) {
                    Server server = server(course, seed, 0.06, resend, 120L * MS);
                    String where = String.format("%.1f B/s, Neusendung %d ms, Netz %d", course.speed(), resend / MS, seed);
                    GhostUploadCheck.Result shifted = check(course, server, clientTrack(course, warped, lap));
                    assertTrue(shifted == GhostUploadCheck.Result.START || shifted == GhostUploadCheck.Result.GATES || shifted == GhostUploadCheck.Result.PATH,
                            "verschobene Zeit bei " + where + ": " + shifted);
                    assertEquals(GhostUploadCheck.Result.SPEED, check(course, server, clientTrack(course, jumpy, lap)), "Teleport bei " + where);
                    if (server.reference().splits()[1] >= 0L) {
                        gateSeen++;
                        assertEquals(GhostUploadCheck.Result.GATES, check(course, server, clientTrack(course, around, lap)), "fehlendes Tor bei " + where);
                    }
                    GhostUploadCheck.Result shortcut = check(course, server, clientTrack(course, detour, lap));
                    assertTrue(shortcut == GhostUploadCheck.Result.GATES || shortcut == GhostUploadCheck.Result.PATH, "Umweg bei " + where + ": " + shortcut);
                    assertNotEquals(GhostUploadCheck.Result.ACCEPTED, check(course, server, clientTrack(course, lifted, lap)), "angehoben bei " + where);
                }
                assertTrue(gateSeen >= 3, "Server sieht das zweite Tor zu selten: " + gateSeen);
            }
        }
    }
}
