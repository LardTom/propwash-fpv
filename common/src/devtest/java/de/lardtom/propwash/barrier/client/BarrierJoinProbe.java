package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.barrier.DroneBarrierBlock;
import de.lardtom.propwash.dev.BarrierJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class BarrierJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 200;
    private static final String START = "barriers_start";
    private static final String PROBE = "barriers_probe";
    private static final double SPEED = 6.0;
    private static final int PUSH_DELAY = 10;
    private static final int HOLD_TICKS = 60;
    private static final int WALK_TICKS = 30;
    private static final int REST_TICKS = 15;
    private static final int MARKER_TICKS = 60;

    private BarrierJoinProbe() {
    }

    private static final class Probe {
        int stage;
        int mark;
        int epoch = Integer.MIN_VALUE;
        int resets;
        int originX;
        int originY;
        int originZ;
        boolean passed;
        int contactAt = -1;
        double front = Double.NEGATIVE_INFINITY;
        double floor = Double.NaN;
        String dronePass = "missing";
        String droneBlock = "missing";
        String playerPass = "missing";
        String playerBlock = "missing";
        String markers = "missing";
        long markersBefore;
        String detail = "";
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        Probe p = (Probe) state.computeIfAbsent(PROBE, key -> new Probe());
        if (tick - start > TIMEOUT_TICKS) {
            mc.options.keyUp.setDown(false);
            return report(p, "timeout at stage " + p.stage);
        }
        PilotSession session = PilotSession.INSTANCE;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        int waited = tick - p.mark;
        if (p.stage <= 3) {
            QuadcopterEntity drone = session.drone();
            DroneSim sim = session.sim();
            if (drone == null || sim == null || !session.isFpv()) {
                return null;
            }
            flight(p, tick, waited, drone, sim);
            return null;
        }
        switch (p.stage) {
            case 4 -> {
                input(0.0F, false);
                if (!session.isActive()) {
                    session.clearTestInput();
                    p.stage = 5;
                    p.mark = tick;
                }
            }
            case 5, 7 -> {
                boolean pass = p.stage == 5;
                Vec3 target = new Vec3(p.originX + BarrierJoinCheck.START_DX, p.originY,
                        p.originZ + (pass ? BarrierJoinCheck.WALK_PASS_DZ : BarrierJoinCheck.WALK_BLOCK_DZ) + 0.5);
                if (player.position().distanceTo(target) < 0.3) {
                    player.setYRot(-90.0F);
                    player.setXRot(0.0F);
                    p.stage++;
                    p.mark = tick;
                }
            }
            case 6, 8 -> {
                boolean pass = p.stage == 6;
                player.setYRot(-90.0F);
                mc.options.keyUp.setDown(waited < WALK_TICKS);
                if (waited < WALK_TICKS + REST_TICKS) {
                    return null;
                }
                mc.options.keyUp.setDown(false);
                double wall = p.originX + BarrierJoinCheck.WALK_WALL_X;
                double half = player.getBbWidth() / 2.0;
                double x = player.getX();
                if (pass) {
                    p.playerPass = x + half <= wall + 0.02 && x > p.originX + BarrierJoinCheck.START_DX + 1.0 ? "ok" : "fail";
                } else {
                    p.playerBlock = x - half > wall + 1.0 ? "ok" : "fail";
                }
                p.detail += String.format(Locale.ROOT, " player_%s_x=%+.2f", pass ? "pass" : "block", x - wall);
                p.stage = pass ? 7 : 9;
                p.mark = tick;
            }
            case 9 -> {
                if (player.isCreative() && player.getMainHandItem().getItem() instanceof BlockItem item
                        && item.getBlock() instanceof DroneBarrierBlock) {
                    p.markersBefore = BarrierMarkers.spawned(BarrierKind.DRONE_PASS) + BarrierMarkers.spawned(BarrierKind.DRONE_BLOCK);
                    p.stage = 10;
                    p.mark = tick;
                }
            }
            case 10 -> {
                if (waited < MARKER_TICKS) {
                    return null;
                }
                long markers = BarrierMarkers.spawned(BarrierKind.DRONE_PASS) + BarrierMarkers.spawned(BarrierKind.DRONE_BLOCK) - p.markersBefore;
                BlockPos wall = new BlockPos(p.originX + BarrierJoinCheck.WALK_WALL_X, p.originY, p.originZ + BarrierJoinCheck.WALK_BLOCK_DZ);
                boolean outline = !mc.level.getBlockState(wall).getShape(mc.level, wall, CollisionContext.of(player)).isEmpty();
                p.markers = markers > 0 && outline ? "ok" : "fail";
                p.detail += " markers=" + markers + " outline=" + outline;
                return report(p, "");
            }
            default -> {
            }
        }
        return null;
    }

    private static void flight(Probe p, int tick, int waited, QuadcopterEntity drone, DroneSim sim) {
        Telemetry t = sim.telemetry();
        switch (p.stage) {
            case 0 -> {
                p.floor = drone.getY();
                p.epoch = drone.resetEpoch();
                p.originX = (int) Math.floor(drone.getX() - BarrierJoinCheck.START_DX + 1.0E-3);
                p.originY = (int) Math.floor(drone.getY() + 1.0E-3);
                p.originZ = (int) Math.floor(drone.getZ());
                p.stage = 1;
                p.mark = tick;
            }
            case 1 -> {
                input(0.0F, BarrierFlightSelfTests.armSwitch(waited, t.armed, t.grounded || t.sleeping));
                if (t.armed) {
                    p.stage = 2;
                    p.mark = tick;
                }
            }
            case 2 -> {
                input((float) t.hoverThrottleStick, true);
                if (drone.resetEpoch() != p.epoch && drone.getY() > p.floor + BarrierJoinCheck.HOVER - 0.5) {
                    p.epoch = drone.resetEpoch();
                    p.stage = 3;
                    p.mark = tick;
                }
            }
            default -> {
                input((float) t.hoverThrottleStick, true);
                int epoch = drone.resetEpoch();
                if (epoch != p.epoch) {
                    p.epoch = epoch;
                    p.resets++;
                    p.mark = tick;
                    return;
                }
                double half = drone.getBbWidth() / 2.0;
                double passFar = p.originX + BarrierJoinCheck.PASS_X + BarrierJoinCheck.PASS_THICK;
                double blockNear = p.originX + BarrierJoinCheck.BLOCK_X;
                double front = drone.getX() + half;
                p.front = Math.max(p.front, front);
                if (!p.passed && drone.getX() - half > passFar + 0.3) {
                    p.passed = true;
                }
                if (waited >= PUSH_DELAY && front < blockNear - 3.0) {
                    push(sim, SPEED);
                }
                if (p.contactAt < 0 && front >= blockNear - 0.08) {
                    p.contactAt = tick;
                }
                if (p.contactAt >= 0 && tick - p.contactAt >= HOLD_TICKS) {
                    p.dronePass = p.passed && p.resets == 0 ? "ok" : "fail";
                    p.droneBlock = p.front <= blockNear + 0.05 ? "ok" : "fail";
                    p.detail += String.format(Locale.ROOT, " drone_front=%+.2f resets=%d", p.front - blockNear, p.resets);
                    input(0.0F, false);
                    p.stage = 4;
                    p.mark = tick;
                }
            }
        }
    }

    private static String report(Probe p, String problem) {
        StringBuilder out = new StringBuilder();
        if (!problem.isEmpty()) {
            out.append("barriers ").append(problem).append('\n');
        }
        out.append("barriers drone_pass=").append(p.dronePass).append('\n');
        out.append("barriers drone_block=").append(p.droneBlock).append('\n');
        out.append("barriers player_pass=").append(p.playerPass).append('\n');
        out.append("barriers player_block=").append(p.playerBlock).append('\n');
        out.append("barriers markers=").append(p.markers).append('\n');
        out.append("barriers detail").append(p.detail).append('\n');
        return out.toString();
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, double vx) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }
}
