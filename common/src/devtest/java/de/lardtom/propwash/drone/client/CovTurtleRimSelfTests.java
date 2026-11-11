package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CovTurtleRimSelfTests {
    private static final String RUN = "run";
    private static final double FALLEN = 1.0;
    private static final int PILLAR_HEIGHT = 3;
    private static final int FALL_TICKS = 60;
    private static final int STILL_TICKS = 40;
    private static final int REST_TICKS = 400;

    private CovTurtleRimSelfTests() {
    }

    public static void register() {
        step(true, 0.70);
        step(true, 0.95);
        step(false, 0.85);
        step(false, 0.95);
    }

    private static void step(boolean gate, double rim) {
        String site = gate ? "einer Gate-Kante" : "einer Blockkante";
        SelfTest.clientStep(String.format(Locale.ROOT, "%skopfüber am Rand der Hitbox auf %s, Schwerpunkt %.0f %% der halben Breite hinter der "
                        + "Kante, Knüppel mittig – kippt herunter, liegt am Boden still", WaterproofTurtleSelfTests.TURTLE, site, rim * 100.0), 2400,
                ctx -> run(ctx, gate, rim));
    }

    private static StepResult run(SelfTest.StepContext ctx, boolean gate, double rim) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(gate, rim));
        try {
            StepResult result = run.tick(ctx);
            if (result.status() != StepResult.Status.PENDING) {
                run.cleanup(ctx);
            }
            return result;
        } catch (RuntimeException exception) {
            run.cleanup(ctx);
            throw exception;
        }
    }

    static final class Run {
        final boolean gate;
        final double rim;
        final double halfWidth = 0.5 * AirframeSpec.of(DroneBuild.FREESTYLE).hitboxWidth;
        final Mode3dSelfTests.Script script = new Mode3dSelfTests.Script(Mode3dSelfTests.Kind.INVERTED, List.of());
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        volatile int gateId = -1;
        volatile double top = Double.NaN;
        volatile double edge = Double.NaN;
        volatile boolean alongX;
        int phase = -2;
        int since;
        int now;
        int stillSince = -1;
        boolean armedOnTop;
        double highest = Double.NEGATIVE_INFINITY;
        double lastTilt = Double.NaN;
        double lastY = Double.NaN;
        @Nullable CompletableFuture<?> pending;
        boolean cleaned;

        Run(boolean gate, double rim) {
            this.gate = gate;
            this.rim = rim;
        }

        private String label() {
            return String.format(Locale.ROOT, "%s %.0f %%", this.gate ? "Gate-Kante" : "Blockkante", this.rim * 100.0);
        }

        StepResult tick(SelfTest.StepContext ctx) {
            this.now = ctx.tick();
            CompletableFuture<?> waiting = this.pending;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return StepResult.fail(this.label() + ": Server-Aktion fehlgeschlagen: " + waiting.exceptionNow().getMessage());
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            PilotSession session = PilotSession.INSTANCE;
            int ticks = this.now - this.since;
            if (this.phase == -2) {
                this.phase = -1;
                this.since = this.now;
                session.clearTestInput();
                session.resetFlightModes();
                session.setTestTurtle(true);
                session.setTestInput(StickPositions.IDLE, false, StabMode.ACRO);
                this.pending = ctx.onServer(this::setUp);
                return StepResult.pending();
            }
            QuadcopterEntity drone = session.drone();
            if (this.phase == -1) {
                if (drone == null || drone.getId() != this.script.droneId || !session.isActive()) {
                    return ticks > 300 ? StepResult.fail(this.label() + ": keine Sitzung mit der Testdrohne") : StepResult.pending();
                }
                double across = this.alongX ? drone.getZ() : drone.getX();
                ctx.log(String.format(Locale.ROOT, "%s: Oberkante %.3f, Kante bei %s %.4f, Drohne bei %s, Überhang %.4f m = %.1f %% der halben "
                                + "Hitbox-Breite %.4f", this.label(), this.top, this.alongX ? "z" : "x", this.edge, drone.position(), across - this.edge,
                        100.0 * (across - this.edge) / (0.5 * drone.getBbWidth()), 0.5 * drone.getBbWidth()));
                this.phase = 0;
                this.since = this.now;
                return StepResult.pending();
            }
            if (!session.isActive() || drone == null) {
                return StepResult.fail(this.label() + ": Sitzung verloren");
            }
            session.setTestInput(StickPositions.IDLE, this.phase > 0 || ticks >= 1, StabMode.ACRO);
            double com = comY();
            this.highest = Math.max(this.highest, com);
            Telemetry t = Mode3dSelfTests.telemetry();
            double turn = t == null || Double.isNaN(this.lastTilt) ? 0.0 : Math.abs(t.tiltDeg - this.lastTilt);
            double drop = Double.isNaN(this.lastY) ? 0.0 : Math.abs(com - this.lastY);
            this.lastTilt = t == null ? Double.NaN : t.tiltDeg;
            this.lastY = com;
            if (this.phase == 0) {
                this.armedOnTop |= t != null && t.armed && t.turtle;
                if (this.highest < this.top - 0.05) {
                    return StepResult.fail(this.label() + ": nie auf der Oberkante, höchster Schwerpunkt " + fmt(this.highest - this.top));
                }
                if (this.top - com > FALLEN) {
                    ctx.log(this.label() + ": über die Kante gekippt und gefallen nach " + ticks + " Ticks, Turtle auf der Kante scharf "
                            + this.armedOnTop + ", " + state());
                    this.phase = 1;
                    this.since = this.now;
                    return StepResult.pending();
                }
                return ticks > FALL_TICKS ? StepResult.fail(this.label() + ": liegt noch oben nach " + ticks + " Ticks, Schwerpunkt "
                        + fmt(com - this.top) + " über der Oberkante, " + state() + ", Drehung " + fmt(turn) + "°/Tick") : StepResult.pending();
            }
            if (this.top - com < FALLEN) {
                return StepResult.fail(this.label() + ": wieder oben: " + fmt(com - this.top));
            }
            boolean still = t != null && t.speed < 0.05 && turn < 1.0 && drop < 0.005;
            if (!still) {
                this.stillSince = -1;
                return ticks > REST_TICKS ? StepResult.fail(this.label() + ": bewegt sich am Boden noch: " + state() + ", Drehung " + fmt(turn)
                        + "°/Tick") : StepResult.pending();
            }
            if (this.stillSince < 0) {
                this.stillSince = this.now;
            }
            if (this.now - this.stillSince < STILL_TICKS) {
                return StepResult.pending();
            }
            ctx.log(this.label() + ": liegt am Boden still nach " + (this.stillSince - this.since) + " Ticks, " + state());
            return StepResult.pass();
        }

        private void setUp(MinecraftServer server) {
            ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
            ServerLevel level = player.level();
            BlockPos feet = player.blockPosition();
            Vec3 at;
            double beyond = this.rim * this.halfWidth;
            if (!this.gate) {
                BlockPos base = feet.offset(10, 0, 14);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy < PILLAR_HEIGHT + 2; dy++) {
                        for (int dz = -1; dz <= 2; dz++) {
                            BlockPos pos = base.offset(dx, dy, dz);
                            BlockState next = dz == 0 && dy < PILLAR_HEIGHT ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                            this.blocks.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                        }
                    }
                }
                this.top = base.getY() + PILLAR_HEIGHT;
                this.alongX = true;
                this.edge = base.getZ() + 1.0;
                at = new Vec3(base.getX() + 0.5, this.top, this.edge + beyond);
            } else {
                Vec3 base = Vec3.atBottomCenterOf(feet.offset(-8, 0, 14));
                GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
                if (gate == null) {
                    throw new IllegalStateException("Gate nicht erzeugt");
                }
                gate.setSize(GateSize.MEDIUM);
                gate.snapTo(base.x, base.y, base.z, 0.0F, 0.0F);
                if (!level.addFreshEntity(gate)) {
                    throw new IllegalStateException("Gate nicht gespawnt");
                }
                this.gateId = gate.getId();
                AABB bar = null;
                for (AABB part : gate.colliders()) {
                    if (bar == null || part.maxY > bar.maxY + 1.0E-6 || part.maxY > bar.maxY - 1.0E-6 && length(part) > length(bar)) {
                        bar = part;
                    }
                }
                if (bar == null) {
                    throw new IllegalStateException("Gate ohne Rahmen");
                }
                this.top = bar.maxY;
                this.alongX = bar.getXsize() >= bar.getZsize();
                this.edge = this.alongX ? bar.maxZ : bar.maxX;
                double cx = (bar.minX + bar.maxX) * 0.5;
                double cz = (bar.minZ + bar.maxZ) * 0.5;
                at = this.alongX ? new Vec3(cx, this.top, this.edge + beyond) : new Vec3(this.edge + beyond, this.top, cz);
            }
            Mode3dSelfTests.spawnAndFly(server, this.script, true, PilotMode.LINE_OF_SIGHT, at, 0.0F);
        }

        void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            PilotSession.INSTANCE.setTestTurtle(null);
            Mode3dSelfTests.cleanup(ctx, this.script);
            Map<BlockPos, BlockState> saved = new LinkedHashMap<>(this.blocks);
            int gate = this.gateId;
            ctx.onServer(server -> {
                ServerLevel level = Mode3dSelfTests.firstPlayer(server).level();
                saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
                Entity entity = gate >= 0 ? level.getEntity(gate) : null;
                if (entity != null) {
                    entity.discard();
                }
            });
        }
    }

    private static double length(AABB box) {
        return Math.max(box.getXsize(), box.getZsize());
    }

    private static String state() {
        Telemetry t = Mode3dSelfTests.telemetry();
        return t == null ? "-" : "Neigung " + fmt(t.tiltDeg) + ", Tempo " + fmt(t.speed) + ", scharf " + t.armed + ", Turtle " + t.turtle;
    }

    private static double comY() {
        return PilotSession.INSTANCE.comPosition(new Vector3d()).y;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
