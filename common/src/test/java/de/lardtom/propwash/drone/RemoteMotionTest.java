package de.lardtom.propwash.drone;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteMotionTest {
    private static final long MS = 1_000_000L;
    private static final long TICK = 50L * MS;
    private static final long FRAME = 1_000_000_000L / 60L;
    private static final long RUN = 10_000L * MS;
    private static final long WARMUP = 1_500L * MS;
    private static final double RADIUS = 10.0;
    private static final Flight CIRCLE = new Flight(14.0, RADIUS);
    private static final Flight WIDE = new Flight(14.0, 30.0);
    private static final Flight RACE = new Flight(35.0, 75.0);

    private record Packet(long arrival, double[] position, Quaternionf rotation, double[] velocity) {
    }

    private record Flight(double speed, double radius) {
        double[] position(long nanos) {
            double angle = nanos * 1.0E-9 * this.speed / this.radius;
            return new double[]{Math.cos(angle) * this.radius, 70.0, Math.sin(angle) * this.radius};
        }

        double[] velocity(long nanos) {
            double angle = nanos * 1.0E-9 * this.speed / this.radius;
            return new double[]{-Math.sin(angle) * this.speed, 0.0, Math.cos(angle) * this.speed};
        }

        Quaternionf rotation(long nanos) {
            double[] v = this.velocity(nanos);
            return new Quaternionf().rotateY((float) Math.atan2(-v[0], -v[2]));
        }
    }

    private static List<Packet> network(long seed, double loss, long retransmit, long pilotJitter, long viewerJitter) {
        return network(CIRCLE, seed, loss, retransmit, pilotJitter, viewerJitter);
    }

    private static List<Packet> network(Flight flight, long seed, double loss, long retransmit, long pilotJitter, long viewerJitter) {
        Random random = new Random(seed);
        int sends = (int) (RUN / TICK) + 1;
        long[] atServer = new long[sends];
        long last = Long.MIN_VALUE;
        for (int i = 0; i < sends; i++) {
            long at = i * TICK + 120L * MS + Math.round(random.nextDouble() * pilotJitter);
            if (i * TICK > WARMUP && random.nextDouble() < loss) {
                at += retransmit;
            }
            at = Math.max(at, last);
            last = at;
            atServer[i] = Math.ceilDiv(at, TICK) * TICK;
        }
        List<Packet> packets = new ArrayList<>();
        int latest = -1;
        int cursor = 0;
        int sentState = -1;
        long lastArrival = Long.MIN_VALUE;
        for (long tick = 0L; tick <= RUN + 1_000L * MS; tick += TICK) {
            while (cursor < sends && atServer[cursor] <= tick) {
                latest = cursor++;
            }
            if (latest >= 0 && latest != sentState) {
                sentState = latest;
                long state = latest * TICK;
                long arrival = Math.max(lastArrival, tick + 40L * MS + Math.round(random.nextDouble() * viewerJitter));
                lastArrival = arrival;
                packets.add(new Packet(arrival, flight.position(state), flight.rotation(state), flight.velocity(state)));
            }
        }
        return packets;
    }

    private record Meter(int freezes, int jumps, int frames) {
    }

    private record Played(Meter position, Meter look, int cuts) {
    }

    private static Meter measure(List<double[]> shown, List<Long> times, double speed) {
        int freezes = 0;
        int jumps = 0;
        long stillSince = -1L;
        boolean counted = false;
        for (int i = 1; i < shown.size(); i++) {
            double[] a = shown.get(i - 1);
            double[] b = shown.get(i);
            long dt = times.get(i) - times.get(i - 1);
            double moved = Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
            double expected = speed * dt * 1.0E-9;
            if (moved < 0.25 * expected) {
                if (stillSince < 0L) {
                    stillSince = times.get(i - 1);
                    counted = false;
                }
                if (!counted && times.get(i) - stillSince >= 50L * MS) {
                    counted = true;
                    freezes++;
                }
            } else {
                stillSince = -1L;
            }
            if (moved > 3.0 * expected + 0.05) {
                jumps++;
            }
        }
        return new Meter(freezes, jumps, shown.size());
    }

    private static Played playBuffered(List<Packet> packets, boolean withVelocity) {
        RemoteMotion motion = new RemoteMotion();
        int cuts = -1;
        List<double[]> positions = new ArrayList<>();
        List<double[]> looks = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        Vector3f forward = new Vector3f();
        int next = 0;
        for (long now = 0L; now <= RUN; now += FRAME) {
            while (next < packets.size() && packets.get(next).arrival() <= now) {
                Packet packet = packets.get(next++);
                if (withVelocity) {
                    motion.motion(packet.arrival(), packet.velocity()[0], packet.velocity()[1], packet.velocity()[2]);
                }
                if (motion.position(packet.arrival(), packet.position()[0], packet.position()[1], packet.position()[2])) {
                    cuts++;
                }
                motion.orientation(packet.arrival(), packet.rotation());
            }
            if (!motion.sample(now, at, rot) || now < WARMUP) {
                continue;
            }
            positions.add(at.clone());
            rot.transform(forward.set(0.0F, 0.0F, -1.0F));
            looks.add(new double[]{forward.x * RADIUS, forward.y * RADIUS, forward.z * RADIUS});
            times.add(now);
            assertTrue(motion.delayNanos() >= RemoteMotion.MIN_DELAY_NANOS && motion.delayNanos() <= RemoteMotion.MAX_DELAY_NANOS);
        }
        return new Played(measure(positions, times, CIRCLE.speed()), measure(looks, times, CIRCLE.speed()), cuts);
    }

    private static Played playTicks(List<Packet> packets, long frameJitter, long seed) {
        return playTicks(CIRCLE, packets, frameJitter, seed);
    }

    private static Played playTicks(Flight flight, List<Packet> packets, long frameJitter, long seed) {
        Random random = new Random(seed);
        RemoteMotion motion = new RemoteMotion();
        int cuts = -1;
        List<double[]> positions = new ArrayList<>();
        List<double[]> looks = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        double[] previous = null;
        double[] current = null;
        Quaternionf previousRot = new Quaternionf();
        Quaternionf currentRot = new Quaternionf();
        Vector3f forward = new Vector3f();
        long clock = Long.MIN_VALUE;
        long nextTick = 0L;
        int next = 0;
        for (long now = 0L; now <= RUN; now += FRAME + Math.round((random.nextDouble() - 0.5) * frameJitter)) {
            while (next < packets.size() && packets.get(next).arrival() <= now) {
                Packet packet = packets.get(next++);
                motion.motion(now, packet.velocity()[0], packet.velocity()[1], packet.velocity()[2]);
                if (motion.position(now, packet.position()[0], packet.position()[1], packet.position()[2])) {
                    cuts++;
                }
                motion.orientation(now, packet.rotation());
            }
            while (nextTick <= now) {
                nextTick += TICK;
                long expected = clock == Long.MIN_VALUE ? now : clock + TICK;
                clock = Math.abs(now - expected) > 250L * MS ? now : expected + (now - expected) / 8L;
                if (motion.sample(clock, at, rot)) {
                    previous = current == null ? at.clone() : current;
                    current = at.clone();
                    previousRot.set(current == null ? rot : currentRot);
                    currentRot.set(rot);
                }
            }
            if (current == null || now < WARMUP) {
                continue;
            }
            double partial = Math.clamp(1.0 - (nextTick - now) / (double) TICK, 0.0, 1.0);
            positions.add(new double[]{previous[0] + (current[0] - previous[0]) * partial, previous[1] + (current[1] - previous[1]) * partial,
                    previous[2] + (current[2] - previous[2]) * partial});
            new Quaternionf(previousRot).slerp(currentRot, (float) partial).transform(forward.set(0.0F, 0.0F, -1.0F));
            looks.add(new double[]{forward.x * flight.radius(), forward.y * flight.radius(), forward.z * flight.radius()});
            times.add(now);
        }
        return new Played(measure(positions, times, flight.speed()), measure(looks, times, flight.speed()), cuts);
    }

    private static Meter playVanilla(List<Packet> packets) {
        double[] pos = null;
        double[] target = null;
        int steps = 0;
        double[] previous = null;
        List<double[]> shown = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        int next = 0;
        long tick = 0L;
        for (long now = 0L; now <= RUN; now += FRAME) {
            while (next < packets.size() && packets.get(next).arrival() <= now) {
                target = packets.get(next++).position().clone();
                steps = 2;
                if (pos == null) {
                    pos = target.clone();
                    previous = target.clone();
                }
            }
            while (tick + TICK <= now && pos != null) {
                tick += TICK;
                previous = pos.clone();
                if (steps > 0) {
                    for (int c = 0; c < 3; c++) {
                        pos[c] += (target[c] - pos[c]) / steps;
                    }
                    steps--;
                }
            }
            if (pos == null || now < WARMUP) {
                continue;
            }
            double partial = (now - tick) / (double) TICK;
            shown.add(new double[]{previous[0] + (pos[0] - previous[0]) * partial, previous[1] + (pos[1] - previous[1]) * partial,
                    previous[2] + (pos[2] - previous[2]) * partial});
            times.add(now);
        }
        return measure(shown, times, CIRCLE.speed());
    }

    @Test
    void badPilotConnectionStaysSmoothForViewers() {
        for (long seed = 1L; seed <= 8L; seed++) {
            List<Packet> packets = network(seed, 0.05, 300L * MS, 80L * MS, 25L * MS);
            Played buffered = playBuffered(packets, true);
            assertEquals(0, buffered.position().freezes(), "Standbilder Außensicht, Netz " + seed);
            assertEquals(0, buffered.position().jumps(), "Sprünge Außensicht, Netz " + seed);
            assertEquals(0, buffered.look().freezes(), "Standbilder Blickrichtung, Netz " + seed);
            assertEquals(0, buffered.look().jumps(), "Sprünge Blickrichtung, Netz " + seed);
        }
    }

    @Test
    void tickedLikeTheGameItStaysSmooth() {
        for (long seed = 1L; seed <= 8L; seed++) {
            Played ticked = playTicks(network(seed, 0.05, 300L * MS, 80L * MS, 25L * MS), 6L * MS, seed);
            assertEquals(0, ticked.position().freezes(), "Standbilder Außensicht im Tick-Takt, Netz " + seed);
            assertEquals(0, ticked.position().jumps(), "Sprünge Außensicht im Tick-Takt, Netz " + seed);
            assertEquals(0, ticked.look().freezes(), "Standbilder Blickrichtung im Tick-Takt, Netz " + seed);
            assertEquals(0, ticked.look().jumps(), "Sprünge Blickrichtung im Tick-Takt, Netz " + seed);
        }
    }

    @Test
    void withoutServerVelocityItStaysSmoothToo() {
        Played buffered = playBuffered(network(11L, 0.05, 300L * MS, 80L * MS, 25L * MS), false);
        assertEquals(0, buffered.position().freezes());
        assertEquals(0, buffered.position().jumps());
    }

    @Test
    void oldTwoTickInterpolationFroze() {
        Meter vanilla = playVanilla(network(1L, 0.05, 300L * MS, 80L * MS, 25L * MS));
        assertTrue(vanilla.freezes() > 0 && vanilla.jumps() > 0, "Vergleich ohne Puffer: " + vanilla);
    }

    @Test
    void delayFollowsMeasuredJitter() {
        RemoteMotion calm = new RemoteMotion();
        RemoteMotion rough = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        Random random = new Random(3L);
        for (int i = 0; i < 200; i++) {
            long t = i * TICK;
            calm.position(t + 40L * MS, i, 0.0, 0.0);
            rough.position(t + 40L * MS + Math.round(random.nextDouble() * 120L * MS), i, 0.0, 0.0);
            calm.sample(t + 45L * MS, at, rot);
            rough.sample(t + 45L * MS, at, rot);
        }
        assertEquals(RemoteMotion.MIN_DELAY_NANOS, calm.delayNanos());
        assertTrue(rough.delayNanos() > calm.delayNanos() + 30L * MS, "Verzögerung " + rough.delayNanos() / MS + " ms");
        assertTrue(rough.delayNanos() <= RemoteMotion.MAX_DELAY_NANOS);
    }

    @Test
    void teleportSnapsInsteadOfGliding() {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        for (int i = 0; i < 10; i++) {
            motion.position(i * TICK, i * 0.5, 0.0, 0.0);
            motion.sample(i * TICK + 1L, at, rot);
        }
        motion.position(10L * TICK, 200.0, 0.0, 0.0);
        motion.sample(10L * TICK + FRAME, at, rot);
        assertEquals(200.0, at[0], 1.0E-9);
    }

    @Test
    void discreteTurnOfStandingDroneDoesNotOvershoot() {
        for (boolean withMotion : new boolean[]{false, true}) {
            RemoteMotion motion = new RemoteMotion();
            double[] at = new double[3];
            Quaternionf shown = new Quaternionf();
            float[] yaws = {0.0F, -90.0F, 45.0F, 135.0F};
            long now = 0L;
            for (int turn = 0; turn < yaws.length; turn++) {
                Quaternionf target = new Quaternionf().rotateY((float) Math.toRadians(yaws[turn]));
                if (withMotion) {
                    motion.motion(now, 0.0, 0.0, 0.0);
                }
                motion.position(now, 5.0, 70.0, 5.0);
                motion.orientation(now, target);
                float last = Float.MAX_VALUE;
                long end = now + 600L * MS;
                for (; now < end; now += TICK) {
                    motion.sample(now, at, shown);
                    float off = (float) Math.toDegrees(shown.angle() == 0.0F ? 0.0 : new Quaternionf(shown).mul(new Quaternionf(target).conjugate()).angle());
                    off = Math.min(off, 360.0F - off);
                    assertTrue(off <= last + 0.01F, "Drehung springt zurück: " + last + " → " + off + " (Wende " + turn + ", Tempo " + withMotion + ")");
                    last = off;
                    assertEquals(5.0, at[0], 1.0E-9);
                }
                assertTrue(last < 0.5F, "Ziel nicht erreicht: " + last);
            }
        }
    }

    @Test
    void liftedStandingDroneKeepsItsTurnAndDoesNotRise() {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf shown = new Quaternionf();
        Quaternionf last = new Quaternionf().rotateY((float) Math.toRadians(45.0));
        long now = 0L;
        motion.position(now, 0.0, 70.0, 0.0);
        motion.orientation(now, new Quaternionf());
        now += 900L * MS;
        motion.position(now, 0.0, 70.0, 0.0);
        motion.orientation(now, new Quaternionf().rotateY((float) Math.toRadians(-90.0)));
        now += 900L * MS;
        motion.orientation(now, last);
        now += 900L * MS;
        motion.position(now, 0.0, 70.25, 0.0);
        double highest = 0.0;
        for (int tick = 0; tick < 30; tick++, now += TICK) {
            if (tick == 4) {
                motion.motion(now, 0.0, -4.0, 0.0);
                motion.position(now, 0.0, 70.17, 0.0);
            } else if (tick == 5) {
                motion.motion(now, 0.0, 0.0, 0.0);
                motion.position(now, 0.0, 70.0, 0.0);
            }
            motion.sample(now, at, shown);
            highest = Math.max(highest, at[1]);
            float off = (float) Math.toDegrees(new Quaternionf(shown).mul(new Quaternionf(last).conjugate()).angle());
            off = Math.min(off, 360.0F - off);
            assertTrue(off < 0.5F, "Lage springt nach dem Anheben: " + off + "° im Tick " + tick);
        }
        assertTrue(highest <= 70.25 + 1.0E-6, "steigt über den Teleport hinaus: " + highest);
    }

    private static int snaps(List<Packet> packets) {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        int snaps = 0;
        int next = 0;
        for (long now = 0L; now <= RUN; now += FRAME) {
            Packet newest = null;
            while (next < packets.size() && packets.get(next).arrival() <= now) {
                newest = packets.get(next++);
                motion.motion(now, newest.velocity()[0], newest.velocity()[1], newest.velocity()[2]);
                motion.position(now, newest.position()[0], newest.position()[1], newest.position()[2]);
                motion.orientation(now, newest.rotation());
            }
            if (motion.sample(now, at, rot) && newest != null && now >= WARMUP) {
                double[] p = newest.position();
                if (Math.abs(at[0] - p[0]) + Math.abs(at[1] - p[1]) + Math.abs(at[2] - p[2]) < 1.0E-9) {
                    snaps++;
                }
            }
        }
        return snaps;
    }

    @Test
    void normalGapsAtRaceSpeedNeverEmptyTheBuffer() {
        for (double speed : new double[]{RACE.speed(), 60.0, 100.0, 165.0}) {
            Flight flight = new Flight(speed, speed * RACE.radius() / RACE.speed());
            for (long retransmit : new long[]{400L * MS, 500L * MS, 600L * MS}) {
                int snaps = 0;
                for (long seed = 1L; seed <= 8L; seed++) {
                    snaps += snaps(network(flight, seed, 0.05, retransmit, 80L * MS, 25L * MS));
                }
                assertEquals(0, snaps, "Puffer geleert bei " + speed + " B/s, Neusendung " + retransmit / MS + " ms");
            }
        }
    }

    @Test
    void raceSpeedStaysAsSmoothAsSlowFlightInTheSameNetwork() {
        int[] slow = new int[4];
        int[] fast = new int[4];
        for (long seed = 1L; seed <= 8L; seed++) {
            Played wide = playTicks(WIDE, network(WIDE, seed, 0.05, 400L * MS, 80L * MS, 25L * MS), 6L * MS, seed);
            Played race = playTicks(RACE, network(RACE, seed, 0.05, 400L * MS, 80L * MS, 25L * MS), 6L * MS, seed);
            Meter[][] views = {{wide.position(), race.position()}, {wide.look(), race.look()}};
            for (int view = 0; view < 2; view++) {
                slow[view * 2] += views[view][0].freezes();
                slow[view * 2 + 1] += views[view][0].jumps();
                fast[view * 2] += views[view][1].freezes();
                fast[view * 2 + 1] += views[view][1].jumps();
            }
        }
        String label = "Neusendung 400 ms, Standbilder/Sprünge Außensicht und Blickrichtung bei " + RACE.speed() + " B/s " + Arrays.toString(fast)
                + ", bei " + WIDE.speed() + " B/s " + Arrays.toString(slow);
        for (int i = 0; i < 4; i++) {
            assertTrue(fast[i] <= slow[i], label);
        }
    }

    @Test
    void fastDroneAfterShortGapGlidesInsteadOfSnapping() {
        double speed = 60.0;
        RemoteMotion motion = new RemoteMotion();
        List<double[]> shown = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        long gapFrom = 2_000L * MS;
        long gapTo = gapFrom + 300L * MS;
        long sent = 0L;
        for (long now = 0L; now <= 4_000L * MS; now += FRAME) {
            while (sent <= now) {
                if (sent <= gapFrom || sent >= gapTo) {
                    motion.motion(sent, speed, 0.0, 0.0);
                    motion.position(sent, sent * 1.0E-9 * speed, 70.0, 0.0);
                }
                sent += TICK;
            }
            if (motion.sample(now, at, rot) && now >= WARMUP) {
                shown.add(at.clone());
                times.add(now);
            }
        }
        Meter meter = measure(shown, times, speed);
        assertEquals(0, meter.jumps(), "Sprünge nach 300 ms Lücke bei " + speed + " B/s: " + meter);
        assertEquals(0, meter.freezes(), "Standbilder nach 300 ms Lücke bei " + speed + " B/s: " + meter);
    }

    @Test
    void teleportAtRaceSpeedStillSnaps() {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        for (int i = 0; i < 20; i++) {
            motion.motion(i * TICK, 35.0, 0.0, 0.0);
            motion.position(i * TICK, i * 1.75, 70.0, 0.0);
            motion.sample(i * TICK + 1L, at, rot);
        }
        motion.motion(20L * TICK, 0.0, 0.0, 0.0);
        motion.position(20L * TICK, 19 * 1.75, 70.0, 20.0);
        motion.sample(20L * TICK + FRAME, at, rot);
        assertEquals(20.0, at[2], 1.0E-9);
        assertEquals(19 * 1.75, at[0], 1.0E-9);
    }

    @Test
    void stoppedDroneDoesNotOvershoot() {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        for (int i = 0; i <= 20; i++) {
            motion.motion(i * TICK, i < 20 ? 10.0 : 0.0, 0.0, 0.0);
            motion.position(i * TICK, Math.min(i, 19) * 0.5, 0.0, 0.0);
        }
        double furthest = 0.0;
        for (long now = 20L * TICK; now < 20L * TICK + 1_000L * MS; now += FRAME) {
            motion.sample(now, at, rot);
            furthest = Math.max(furthest, at[0]);
        }
        assertEquals(9.5, furthest, 1.0E-6);
    }

    private static Flight racing(double speed) {
        return new Flight(speed, speed * RACE.radius() / RACE.speed());
    }

    @Test
    void racingSpeedGapsKeepTheBuffer() {
        long[] resends = {400L * MS, 500L * MS, 600L * MS};
        int[] maxFreezes = {8, 42, 64};
        int[] maxJumps = {0, 12, 62};
        for (double speed : new double[]{35.0, 40.0}) {
            Flight flight = racing(speed);
            for (int r = 0; r < resends.length; r++) {
                int freezes = 0;
                int jumps = 0;
                for (long seed = 1L; seed <= 8L; seed++) {
                    List<Packet> packets = network(flight, seed, 0.06, resends[r], 80L * MS, 25L * MS);
                    Played played = playTicks(flight, packets, 4L * MS, seed);
                    String where = speed + " B/s, Neusendung " + resends[r] / MS + " ms, Netz " + seed;
                    assertEquals(0, played.cuts(), "Puffer geleert bei " + where);
                    assertEquals(0, playBuffered(packets, false).cuts(), "Puffer ohne Tempo vom Server geleert bei " + where);
                    if (r == 0) {
                        assertTrue(played.position().freezes() <= 1, "Standbilder bei " + where + ": " + played.position());
                        assertEquals(0, played.look().jumps(), "Sprünge der Blickrichtung bei " + where);
                    }
                    freezes += played.position().freezes();
                    jumps += played.position().jumps();
                }
                String where = speed + " B/s, Neusendung " + resends[r] / MS + " ms";
                assertTrue(freezes <= maxFreezes[r], "Standbilder bei " + where + ": " + freezes);
                assertTrue(jumps <= maxJumps[r], "Sprünge bei " + where + ": " + jumps);
            }
        }
    }

    private static long flyStraight(RemoteMotion motion, double speed, int packets) {
        long t = 0L;
        for (int i = 0; i < packets; i++) {
            t = i * TICK;
            motion.motion(t, speed, 0.0, 0.0);
            motion.position(t, speed * t * 1.0E-9, 70.0, 0.0);
        }
        return t;
    }

    @Test
    void halfSecondGapAtRacingSpeedFliesOn() {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        long last = flyStraight(motion, 35.0, 20);
        motion.sample(last + 400L * MS, at, rot);
        double before = at[0];
        long t = last + 500L * MS;
        motion.motion(t, 35.0, 0.0, 0.0);
        assertFalse(motion.position(t, 35.0 * t * 1.0E-9, 70.0, 0.0), "Lücke von 500 ms bei 35 B/s gilt als Teleport");
        motion.sample(t + FRAME, at, rot);
        assertTrue(Math.abs(at[0] - before) < 3.0 * 35.0 * (100L * MS + FRAME) * 1.0E-9, "Sprung nach der Lücke: " + before + " → " + at[0]);
    }

    private static void assertSnaps(RemoteMotion motion, long t, double x, double y, double z, String what) {
        double[] at = new double[3];
        assertTrue(motion.position(t, x, y, z), what + ": Puffer nicht geleert");
        motion.sample(t + FRAME, at, new Quaternionf());
        assertEquals(x, at[0], 1.0E-9, what);
        assertEquals(y, at[1], 1.0E-9, what);
        assertEquals(z, at[2], 1.0E-9, what);
    }

    @Test
    void teleportsStillSnapAtRacingSpeed() {
        RemoteMotion sideways = new RemoteMotion();
        long t = flyStraight(sideways, 35.0, 20) + TICK;
        sideways.motion(t, 35.0, 0.0, 0.0);
        assertSnaps(sideways, t, 35.0 * t * 1.0E-9, 70.0, 20.0, "20 Blöcke zur Seite im Takt");

        RemoteMotion far = new RemoteMotion();
        t = flyStraight(far, 35.0, 20) + 500L * MS;
        far.motion(t, 35.0, 0.0, 0.0);
        assertSnaps(far, t, 35.0 * t * 1.0E-9 + 25.0, 70.0, 0.0, "nach 500 ms 25 Blöcke weiter als geflogen");

        RemoteMotion stopped = new RemoteMotion();
        t = flyStraight(stopped, 35.0, 20);
        stopped.motion(t + TICK, 0.0, 0.0, 0.0);
        t += 2_000L * MS;
        assertSnaps(stopped, t, 35.0 * (t - 2_000L * MS) * 1.0E-9 - 20.0, 70.0, 0.0, "/tp nach Halt");

        RemoteMotion landed = new RemoteMotion();
        t = flyStraight(landed, 35.0, 20) + TICK;
        landed.motion(t, 0.0, 0.0, 0.0);
        landed.position(t, 35.0 * t * 1.0E-9 - 1.0, 70.0, 0.0);
        assertSnaps(landed, t + 2_000L * MS, 35.0 * t * 1.0E-9 - 21.0, 70.0, 0.0, "/tp nach Halt, Tempo 0 mit der letzten Position");

        long[][] resyncs = {{0L, 2_000L}, {1_000L, 2_000L}, {3_000L, 3_500L}, {3_000L, 5_000L}};
        for (double speed : new double[]{14.0, 35.0, 60.0}) {
            for (long[] times : resyncs) {
                for (double jump : new double[]{20.0, 60.0}) {
                    RemoteMotion resynced = new RemoteMotion();
                    long last = flyStraight(resynced, speed, 20);
                    double x = speed * last * 1.0E-9;
                    resynced.motion(last + TICK, 0.0, 0.0, 0.0);
                    String what = "/tp um " + jump + " Blöcke " + times[1] + " ms nach Halt aus " + speed + " B/s, "
                            + (times[0] > 0L ? "Position ohne Bewegung nach " + times[0] + " ms" : "ohne weitere Position");
                    if (times[0] > 0L) {
                        assertFalse(resynced.position(last + times[0] * MS, x, 70.0, 0.0), what + ": Position ohne Bewegung leert den Puffer");
                    }
                    assertSnaps(resynced, last + times[1] * MS, x, 70.0, jump, what);
                }
            }
        }

        RemoteMotion resting = new RemoteMotion();
        long last = flyStraight(resting, 35.0, 20);
        double x = 35.0 * last * 1.0E-9;
        resting.motion(last + TICK, 0.0, 0.0, 0.0);
        for (int k = 1; k <= 3; k++) {
            assertFalse(resting.position(last + k * 3_000L * MS, x, 70.0, 0.0), "Position ohne Bewegung " + k + " leert den Puffer");
        }
        assertSnaps(resting, last + 9_500L * MS, x - 20.0, 70.0, 0.0, "/tp nach drei Positionen ohne Bewegung");

        RemoteMotion landedResynced = new RemoteMotion();
        t = flyStraight(landedResynced, 35.0, 20) + TICK;
        landedResynced.motion(t, 0.0, 0.0, 0.0);
        landedResynced.position(t, 35.0 * t * 1.0E-9 - 1.0, 70.0, 0.0);
        assertFalse(landedResynced.position(t + 3_000L * MS, 35.0 * t * 1.0E-9 - 1.0, 70.0, 0.0),
                "Position ohne Bewegung nach der Landung leert den Puffer");
        assertSnaps(landedResynced, t + 3_500L * MS, 35.0 * t * 1.0E-9 - 1.0, 70.0, 20.0,
                "/tp nach Halt, Tempo 0 mit der letzten Position, danach Position ohne Bewegung");

        RemoteMotion hovering = new RemoteMotion();
        hovering.motion(0L, 0.0, 0.0, 0.0);
        hovering.position(0L, 5.0, 70.0, 5.0);
        assertSnaps(hovering, 3_000L * MS, 5.0, 70.0, 25.0, "/tp einer stehenden Drohne");

        RemoteMotion stalled = new RemoteMotion();
        t = flyStraight(stalled, 35.0, 20) + 3_000L * MS;
        stalled.motion(t, 35.0, 0.0, 0.0);
        assertSnaps(stalled, t, 35.0 * (t - 3_000L * MS) * 1.0E-9 + 70.0, 70.0, 0.0, "70 Blöcke nach 3 s Lücke");
    }

    @Test
    void takeoffAfterRestGlidesThroughStalls() {
        for (long resync : new long[]{1_000L * MS, 3_000L * MS}) {
            RemoteMotion motion = new RemoteMotion();
            long last = flyStraight(motion, 35.0, 20);
            double x = 35.0 * last * 1.0E-9;
            motion.motion(last + TICK, 0.0, 0.0, 0.0);
            motion.position(last + resync, x, 70.0, 0.0);
            long t = last + resync + 900L * MS;
            motion.motion(t, 40.0, 0.0, 0.0);
            assertFalse(motion.position(t, x + 30.0, 70.0, 0.0),
                    "Start nach dem Stehen mit 900 ms Stockung gilt als Teleport, Position ohne Bewegung nach " + resync / MS + " ms");
        }
    }

    private record Watch(double lowest, double highest, double biggestStep, double last) {
    }

    private static Watch watchPlacement(double spawn, double settled, boolean zeroMotion) {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        long arrival = 1_000L * MS + TICK;
        motion.seed(arrival - TICK, 5.0, spawn, 5.0, 0.0, 0.0, 0.0);
        motion.position(arrival, 5.0, spawn, 5.0);
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        double biggest = 0.0;
        double previous = spawn;
        for (int tick = 0; tick <= 100; tick++) {
            long now = arrival + tick * TICK;
            if (tick == 1) {
                if (zeroMotion) {
                    motion.motion(now, 0.0, 0.0, 0.0);
                }
                motion.position(now, 5.0, settled, 5.0);
            } else if (tick > 1 && tick % 60 == 1) {
                motion.position(now, 5.0, settled, 5.0);
            }
            motion.sample(now, at, rot);
            lowest = Math.min(lowest, at[1]);
            highest = Math.max(highest, at[1]);
            biggest = Math.max(biggest, Math.abs(at[1] - previous));
            previous = at[1];
        }
        return new Watch(lowest, highest, biggest, previous);
    }

    @Test
    void placedDroneNeitherSinksNorRises() {
        double ground = 64.0;
        double[][] cases = {{0.01, 0.0}, {0.01, 0.106}, {0.01, 0.162}};
        String[] names = {"FPV-Drohne fällt 1 cm auf den Boden", "Freestyle mit Speer steigt auf den Speer", "Kamera-Drohne steigt auf Gimbal und Beine"};
        List<String> problems = new ArrayList<>();
        for (int c = 0; c < cases.length; c++) {
            double spawn = ground + cases[c][0];
            double settled = ground + cases[c][1];
            for (boolean zeroMotion : new boolean[]{false, true}) {
                Watch watch = watchPlacement(spawn, settled, zeroMotion);
                String what = names[c] + (zeroMotion ? " (Tempo 0 aus dem Spawn-Paket und mit der Position)" : " (Tempo 0 aus dem Spawn-Paket)");
                double low = Math.min(spawn, settled);
                double high = Math.max(spawn, settled);
                if (watch.lowest() < low - 0.001) {
                    problems.add(what + ": sinkt " + Math.round((low - watch.lowest()) * 1000.0) + " mm unter die Server-Höhe");
                }
                if (watch.highest() > high + 0.001) {
                    problems.add(what + ": steigt " + Math.round((watch.highest() - high) * 1000.0) + " mm über die Server-Höhe");
                }
                if (watch.biggestStep() > Math.abs(settled - spawn) + 0.001) {
                    problems.add(what + ": springt " + Math.round(watch.biggestStep() * 1000.0) + " mm in einem Tick");
                }
                if (Math.abs(watch.last() - settled) > 0.001) {
                    problems.add(what + ": steht nach 100 Ticks " + Math.round((watch.last() - settled) * 1000.0) + " mm neben der Server-Höhe");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }

    @Test
    void restingDroneNudgedWithoutTempoStaysAtServerHeight() {
        RemoteMotion motion = new RemoteMotion();
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        motion.motion(0L, 0.0, 0.0, 0.0);
        motion.position(0L, 5.0, 70.162, 5.0);
        long[] when = {3_000L * MS, 3_050L * MS, 3_100L * MS, 3_150L * MS};
        double[] heights = {70.162, 70.150, 70.140, 70.140};
        double lowest = Double.MAX_VALUE;
        int next = 0;
        for (long now = 0L; now <= 5_000L * MS; now += TICK) {
            while (next < when.length && when[next] <= now) {
                motion.position(when[next], 5.0, heights[next], 5.0);
                next++;
            }
            motion.sample(now, at, rot);
            lowest = Math.min(lowest, at[1]);
        }
        assertTrue(lowest >= 70.140 - 0.001, "ruhende Drohne sinkt nach kleinen Korrekturen ohne Tempo " + Math.round((70.140 - lowest) * 1000.0)
                + " mm unter die Server-Höhe");
        assertEquals(70.140, at[1], 0.001);
    }

    private static double lowestWhileLanding(long gap, boolean withGround) {
        RemoteMotion motion = new RemoteMotion();
        if (withGround) {
            motion.ground((x, z, from, to) -> Math.max(to, 64.0));
        }
        double[] at = new double[3];
        Quaternionf rot = new Quaternionf();
        long touchdown = 1_000L * MS;
        double lowest = Double.MAX_VALUE;
        int sent = 0;
        List<long[]> held = new ArrayList<>();
        for (long now = 0L; now <= touchdown + gap + 2_000L * MS; now += FRAME) {
            while (sent * TICK <= now) {
                long t = sent * TICK;
                long arrival = t >= touchdown - 100L * MS && t < touchdown + gap ? touchdown + gap : t;
                held.add(new long[]{t, arrival});
                sent++;
            }
            for (Iterator<long[]> it = held.iterator(); it.hasNext(); ) {
                long[] packet = it.next();
                if (packet[1] > now) {
                    continue;
                }
                it.remove();
                long t = packet[0];
                boolean landed = t >= touchdown;
                double y = landed ? 64.0 : 64.0 + 3.0 * (touchdown - t) * 1.0E-9;
                double x = 2.0 * Math.min(t, touchdown) * 1.0E-9;
                motion.motion(now, landed ? 0.0 : 2.0, landed ? 0.0 : -3.0, 0.0);
                motion.position(now, x, y, 0.0);
            }
            if (motion.sample(now, at, rot)) {
                lowest = Math.min(lowest, at[1]);
            }
        }
        return lowest;
    }

    @Test
    void landingDroneDuringAGapStaysAboveGround() {
        List<String> problems = new ArrayList<>();
        for (long gap : new long[]{150L * MS, 300L * MS, 600L * MS}) {
            double free = lowestWhileLanding(gap, false);
            assertTrue(free < 64.0 - 0.1, "Gegenprobe ohne Boden: Lücke " + gap / MS + " ms führt nicht unter den Boden (" + free + ")");
            double kept = lowestWhileLanding(gap, true);
            if (kept < 64.0 - 0.001) {
                problems.add("Lücke " + gap / MS + " ms: sinkt " + Math.round((64.0 - kept) * 1000.0) + " mm unter den Boden");
            }
        }
        assertTrue(problems.isEmpty(), "landende Drohne: " + String.join("; ", problems));
    }
}
