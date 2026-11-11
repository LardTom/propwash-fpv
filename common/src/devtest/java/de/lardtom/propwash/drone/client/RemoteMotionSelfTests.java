package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.BadNetwork;
import de.lardtom.propwash.dev.FreezeMeter;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RemoteMotionSelfTests {
    public static final String STEP = "drone: Fremde Drohne bei schlechter Verbindung des Piloten ohne Standbilder (Außensicht und Zuschauer-FPV)";
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000L * MS;
    private static final long SEND_NANOS = 50L * MS;
    private static final long RUN_NANOS = 8L * SECOND;
    private static final long WARMUP_NANOS = 1_500L * MS;
    private static final int ENTITY_ID = -0x5A11;
    private static final double RADIUS = 10.0;
    private static final double SPEED = 14.0;
    private static final BadNetwork.Settings VIEWER = new BadNetwork.Settings(40L * MS, 25L * MS, 0.0, 0L, 0L, 0x0A6L);
    private static @Nullable Run run;

    private RemoteMotionSelfTests() {
    }

    private record Packet(long arrival, Vec3 position, Quaternionf orientation, Vec3 motion) {
    }

    private static final class Run {
        final QuadcopterEntity drone;
        final long origin;
        final List<Packet> packets;
        final int stalls;
        final FreezeMeter outside = new FreezeMeter(SPEED);
        final FreezeMeter look = new FreezeMeter(SPEED);
        final Quaternionf attitude = new Quaternionf();
        final Vector3f forward = new Vector3f();
        int next;
        boolean done;

        Run(QuadcopterEntity drone, long origin, List<Packet> packets, int stalls) {
            this.drone = drone;
            this.origin = origin;
            this.packets = packets;
            this.stalls = stalls;
        }
    }

    public static void register() {
        SelfTest.clientStep(STEP, 500, RemoteMotionSelfTests::step);
        ClientBus.FRAME_START.register(RemoteMotionSelfTests::frame);
    }

    static Vec3 truePosition(Vec3 center, long nanos) {
        double angle = nanos * 1.0E-9 * SPEED / RADIUS;
        return center.add(Math.cos(angle) * RADIUS, 0.0, Math.sin(angle) * RADIUS);
    }

    static Vec3 trueMotion(long nanos) {
        double angle = nanos * 1.0E-9 * SPEED / RADIUS;
        return new Vec3(-Math.sin(angle) * SPEED, 0.0, Math.cos(angle) * SPEED).scale(SEND_NANOS * 1.0E-9);
    }

    static Quaternionf trueOrientation(long nanos) {
        double angle = nanos * 1.0E-9 * SPEED / RADIUS;
        double tx = -Math.sin(angle);
        double tz = Math.cos(angle);
        return new Quaternionf().rotateY((float) Math.atan2(-tx, -tz));
    }

    static List<Packet> schedule(Vec3 center, long[] stallsOut) {
        int sends = (int) (RUN_NANOS / SEND_NANOS) + 1;
        long[] sent = new long[sends];
        for (int i = 0; i < sends; i++) {
            sent[i] = i * SEND_NANOS;
        }
        long[] atServer = BadNetwork.arrivals(sent, BadNetwork.PILOT, WARMUP_NANOS, RUN_NANOS - 500L * MS);
        stallsOut[0] = BadNetwork.stalls(atServer, SEND_NANOS);
        List<Long> ticks = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        int latest = -1;
        int cursor = 0;
        for (long tick = 0L; tick <= RUN_NANOS + SECOND; tick += SEND_NANOS) {
            while (cursor < sends && atServer[cursor] <= tick) {
                latest = cursor++;
            }
            if (latest >= 0 && (states.isEmpty() || states.getLast() != latest)) {
                ticks.add(tick);
                states.add(latest);
            }
        }
        long[] sentByServer = new long[ticks.size()];
        for (int i = 0; i < sentByServer.length; i++) {
            sentByServer[i] = ticks.get(i);
        }
        long[] atViewer = BadNetwork.arrivals(sentByServer, VIEWER, 0L, 0L);
        List<Packet> packets = new ArrayList<>();
        for (int i = 0; i < sentByServer.length; i++) {
            long state = sent[states.get(i)];
            packets.add(new Packet(atViewer[i], truePosition(center, state), trueOrientation(state), trueMotion(state)));
        }
        return packets;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return StepResult.fail("Keine Welt");
        }
        if (ctx.tick() == 0) {
            Vec3 look = mc.player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
            Vec3 center = mc.player.position().add(flat.scale(RADIUS + 10.0)).add(0.0, 6.0, 0.0);
            long[] stalls = new long[1];
            List<Packet> packets = schedule(center, stalls);
            QuadcopterEntity drone = new QuadcopterEntity(DroneModule.DRONE.get(), mc.level);
            drone.setId(ENTITY_ID);
            Vec3 first = packets.getFirst().position();
            drone.snapTo(first.x, first.y, first.z, 0.0F, 0.0F);
            drone.setNoGravity(true);
            mc.level.addEntity(drone);
            run = new Run(drone, Util.getNanos(), packets, (int) stalls[0]);
            return StepResult.pending();
        }
        Run current = run;
        if (current == null) {
            return StepResult.fail("Lauf fehlt");
        }
        if (Util.getNanos() - current.origin < RUN_NANOS) {
            return StepResult.pending();
        }
        current.done = true;
        run = null;
        mc.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
        FreezeMeter.Result outside = current.outside.result();
        FreezeMeter.Result look = current.look.result();
        ctx.log("Pilot: " + BadNetwork.PILOT.text() + "; Zuschauer: " + VIEWER.text() + "; " + current.stalls + " Stockungen beim Server; Außensicht: "
                + outside.text() + "; Zuschauer-FPV (Blickrichtung): " + look.text());
        if (outside.frames() < 60 || look.frames() < 60) {
            return StepResult.fail("Zu wenige Bilder gemessen: " + outside.frames());
        }
        if (!outside.smooth() || !look.smooth()) {
            return StepResult.fail("Fremde Drohne ruckelt: Außensicht " + outside.text() + ", Zuschauer-FPV " + look.text());
        }
        return StepResult.pass();
    }

    private static void frame(Minecraft mc, DeltaTracker delta) {
        Run current = run;
        if (current == null || current.done || mc.level == null) {
            return;
        }
        long elapsed = Util.getNanos() - current.origin;
        while (current.next < current.packets.size() && current.packets.get(current.next).arrival() <= elapsed) {
            Packet packet = current.packets.get(current.next++);
            current.drone.lerpMotion(packet.motion());
            current.drone.moveOrInterpolateTo(packet.position(), 0.0F, 0.0F);
            current.drone.setAttitude(packet.orientation());
        }
        if (elapsed < WARMUP_NANOS) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        Vec3 at = current.drone.getPosition(partial);
        current.outside.frame(elapsed, at.x, at.y, at.z);
        current.drone.renderAttitude(partial, current.attitude);
        current.attitude.transform(current.forward.set(0.0F, 0.0F, -1.0F));
        current.look.frame(elapsed, current.forward.x * RADIUS, current.forward.y * RADIUS, current.forward.z * RADIUS);
    }
}
