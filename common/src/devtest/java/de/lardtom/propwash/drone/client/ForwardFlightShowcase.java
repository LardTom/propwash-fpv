package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ForwardFlightShowcase {
    private static final int OFFSET = 48;
    private static final double SIDE = 1.2;
    private static final double LEAD = 1.4;
    private static final double HEIGHT = 1.45;
    private static final float PITCH = 0.3F;
    private static final int FOV = 40;

    private ForwardFlightShowcase() {
    }

    public static Screenshots.Scene scene() {
        return new FlightScene();
    }

    private static final class FlightScene implements Screenshots.Scene {
        private Mode3dSelfTests.@Nullable Script script;
        private int stage;
        private int since;
        private double lastY;
        private double integral;
        private volatile double ground;
        private volatile double lineZ;
        private volatile @Nullable Vec3 returnTo;
        private volatile float returnYaw;
        private volatile float returnPitch;
        private @Nullable Integer fov;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            Mode3dSelfTests.Script s = this.script;
            if (s == null) {
                s = new Mode3dSelfTests.Script(Mode3dSelfTests.Kind.KEYBOARD, List.of());
                this.script = s;
                this.stage = 0;
                this.since = ctx.tick();
                session.clearTestInput();
                Mode3dSelfTests.Script script = s;
                s.pending = ctx.onServer(server -> {
                    ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
                    ServerLevel level = player.level();
                    this.returnTo = player.position();
                    this.returnYaw = player.getYRot();
                    this.returnPitch = player.getXRot();
                    int x = player.getBlockX() + OFFSET;
                    int z = player.getBlockZ() + OFFSET;
                    level.getChunk(x >> 4, z >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    for (int dx = -3; dx <= 5; dx++) {
                        for (int dz = -6; dz <= 8; dz++) {
                            for (int dy = 0; dy <= 6; dy++) {
                                BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                                if (!level.getBlockState(pos).isAir()) {
                                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                                }
                            }
                        }
                    }
                    this.ground = y;
                    this.lineZ = z + 0.5;
                    player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), -90.0F, 0.0F, true);
                    Mode3dSelfTests.spawnAndFly(server, script, false, PilotMode.LINE_OF_SIGHT, new Vec3(x + 0.5 + SIDE, y, z + 0.5 - LEAD), 0.0F);
                });
                return StepResult.pending();
            }
            if (s.pending != null) {
                if (s.pending.isCompletedExceptionally()) {
                    return StepResult.fail("Flug-Szene: " + s.pending.exceptionNow());
                }
                if (!s.pending.isDone()) {
                    return StepResult.pending();
                }
                s.pending = null;
                this.since = ctx.tick();
            }
            int elapsed = ctx.tick() - this.since;
            if (elapsed > 900) {
                return StepResult.fail("Flug-Szene wird nicht fertig, Stufe " + this.stage);
            }
            if (!session.isLineOfSight() || session.drone() == null || session.drone().getId() != s.droneId) {
                return StepResult.pending();
            }
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null) {
                return StepResult.pending();
            }
            switch (this.stage) {
                case 0 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), elapsed % 20 >= 10, StabMode.ANGLE);
                    if (t.armed) {
                        this.lastY = Mode3dSelfTests.comY();
                        this.integral = 0.0;
                        this.stage = 1;
                        this.since = ctx.tick();
                    }
                }
                case 1 -> {
                    this.fly(t, 0.0F);
                    double y = Mode3dSelfTests.comY();
                    if (Math.abs(y - (this.ground + HEIGHT)) < 0.12 && Math.abs(y - this.lastY) < 0.02 && ctx.tick() - this.since > 40) {
                        Minecraft mc = Minecraft.getInstance();
                        this.fov = mc.options.fov().get();
                        mc.options.fov().set(FOV);
                        this.stage = 2;
                        this.since = ctx.tick();
                    }
                }
                case 2 -> {
                    this.fly(t, PITCH);
                    Vector3d com = session.comPosition(new Vector3d());
                    if (com.z >= this.lineZ - 0.45) {
                        ctx.log("Drohne im Flug: Kamera nach +Z, Flug nach +Z mit " + String.format(Locale.ROOT, "%.1f", t.speed) + " m/s");
                        this.stage = 3;
                        return StepResult.pass();
                    }
                }
                default -> {
                }
            }
            return StepResult.pending();
        }

        private void fly(Telemetry t, float pitch) {
            double y = Mode3dSelfTests.comY();
            double vy = (y - this.lastY) * 20.0;
            this.lastY = y;
            double error = this.ground + HEIGHT - y;
            this.integral = Math.clamp(this.integral + error * 0.01, -0.15, 0.15);
            double hover = Double.isFinite(t.hoverThrottleStick) && t.hoverThrottleStick > 0.05 ? t.hoverThrottleStick : 0.35;
            double tilt = Math.max(0.6, Math.cos(Math.toRadians(t.tiltDeg)));
            double throttle = Math.clamp(hover / tilt + this.integral + error * 0.25 - vy * 0.12, 0.05, 0.9);
            PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, pitch, 0.0F, (float) throttle), true, StabMode.ANGLE);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (this.stage == 3) {
                Telemetry t = Mode3dSelfTests.telemetry();
                if (t != null) {
                    this.fly(t, PITCH);
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.fov != null) {
                mc.options.fov().set(this.fov);
                this.fov = null;
            }
            Mode3dSelfTests.Script s = this.script;
            if (s != null) {
                Mode3dSelfTests.cleanup(ctx, s);
            }
            Vec3 back = this.returnTo;
            if (back != null) {
                float yaw = this.returnYaw;
                float pitch = this.returnPitch;
                ctx.onServer(server -> {
                    ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
                    player.teleportTo(player.level(), back.x, back.y, back.z, Set.of(), yaw, pitch, true);
                });
                this.returnTo = null;
            }
            this.script = null;
        }
    }
}
