package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.ForceProvider;
import de.lardtom.propwash.api.flight.ForceSink;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.CompassTestOverride;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class CompassSelfTests {
    public static final String GROUP = "addon-api-17: Kompass – ";
    public static final String PROBE_ID = "propwash_example:compass_probe";
    private static final double FLIGHT_HEIGHT = 3.0;
    private static final double[] FORCED = {3.0, 40.0};
    private static final double WANDER = 6.5;
    private static final double SAMPLE_TOLERANCE = 0.5;
    private static final int MIN_SAMPLES = 40;
    private static final int ARM_TICKS = 20;
    private static final int TIMEOUT_TICKS = 400;
    private static final String PHASE = "compass_phase";
    private static final String SINCE = "compass_since";
    private static final String FUTURE = "compass_future";
    private static final String RESULT = "compass_result";
    private static final String CASE = "compass_case";
    private static final String PREVIOUS = "compass_previous";
    private static final String MARK = "compass_mark";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final Probe PROBE = new Probe();
    private static volatile boolean active;
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private CompassSelfTests() {
    }

    public static void register() {
        PropwashApi.flight().registerForces(PROBE_ID, profile -> active ? PROBE : null);
        SelfTest.clientStep(GROUP + "Kräfte im FPV-Flug sehen Kurs und Fehler des Kompasses, per Test-Eigenschaft 3° gesund und 40° gestört",
                2400, CompassSelfTests::flightStep);
    }

    private static final class Probe implements ForceProvider {
        volatile int samples;
        volatile double heading = Double.NaN;
        volatile double compass = Double.NaN;
        volatile double error = Double.NaN;
        volatile boolean healthy;

        @Override
        public void apply(FlightState state, ForceSink sink) {
            if (!Minecraft.getInstance().isSameThread()) {
                return;
            }
            this.heading = state.headingDeg();
            this.compass = state.compassHeadingDeg();
            this.error = state.compassErrorDeg();
            this.healthy = state.compassHealthy();
            this.samples++;
        }

        void clear() {
            this.samples = 0;
            this.heading = Double.NaN;
            this.compass = Double.NaN;
            this.error = Double.NaN;
            this.healthy = false;
        }

        String describe() {
            return String.format(Locale.ROOT, "Kurs %.1f°, Kompass %.1f°, Fehler %+.1f°, %s, %d Proben", this.heading, this.compass, this.error,
                    this.healthy ? "gesund" : "gestört", this.samples);
        }
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !flying()) {
            return cleanup(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        switch (phase) {
            case "spawn" -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                PROBE.clear();
                active = true;
                force(state, FORCED[(int) state.getOrDefault(CASE, 0)]);
                state.put(FUTURE, ctx.onServer(CompassSelfTests::spawnAndFly));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                }
                if (!future.isDone() || !flying()) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                }
                restore(state);
                return next(ctx, "arm");
            }
            case "arm" -> {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
                DroneSim sim = PilotSession.INSTANCE.sim();
                if (since >= ARM_TICKS && sim != null && sim.telemetry().armed) {
                    return next(ctx, "climb");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("nicht scharf: " + (sim == null ? "kein Flugmodell" : sim.telemetry().armBlockers)))
                        : StepResult.pending();
            }
            case "climb" -> {
                hover();
                if (height() > FLIGHT_HEIGHT - 0.8) {
                    state.put(MARK, PROBE.samples);
                    return next(ctx, "measure");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht: Höhe %.2f m", height())))
                        : StepResult.pending();
            }
            case "measure" -> {
                hover();
                if (PROBE.samples - (int) state.get(MARK) < MIN_SAMPLES) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("Kräfte nicht aufgerufen: " + PROBE.describe())) : StepResult.pending();
                }
                int index = (int) state.getOrDefault(CASE, 0);
                double expected = FORCED[index];
                String label = String.format(Locale.ROOT, "Test-Eigenschaft %.0f°", expected);
                String problem = consistency(ctx, expected);
                boolean healthy = Math.abs(expected) < FlightState.COMPASS_DISTURBED_DEG;
                if (problem == null && PROBE.healthy != healthy) {
                    problem = healthy ? "gilt als gestört" : "gilt als gesund";
                }
                if (problem != null) {
                    return cleanup(ctx, StepResult.fail(label + ": " + problem));
                }
                ctx.log(label + ": " + PROBE.describe());
                if (index + 1 < FORCED.length) {
                    state.put(CASE, index + 1);
                    return next(ctx, "spawn");
                }
                return cleanup(ctx, StepResult.pass());
            }
            case "cleanup" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future != null && !future.isDone() || PilotSession.INSTANCE.isActive()) {
                    return since > 200 ? StepResult.fail("Aufräumen hängt: FPV " + PilotSession.INSTANCE.isActive()) : StepResult.pending();
                }
                return (StepResult) state.get(RESULT);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static @Nullable String consistency(SelfTest.StepContext ctx, double expected) {
        DroneSim sim = PilotSession.INSTANCE.sim();
        if (sim == null) {
            return "kein Flugmodell";
        }
        Telemetry t = sim.telemetry();
        double error = PROBE.error;
        double heading = PROBE.heading;
        double compass = PROBE.compass;
        double internal = -t.headingErrorDeg;
        ctx.log(String.format(Locale.ROOT, "Flugmodell: Kompassfehler %+.2f°, Kräfte: %+.2f°", internal, error));
        if (!Double.isFinite(error) || Math.abs(error - internal) > SAMPLE_TOLERANCE) {
            return String.format(Locale.ROOT, "Fehler %+.2f° statt %+.2f° (Flugmodell)", error, internal);
        }
        if (Math.abs(wrap180(compass - heading - error)) > 1.0E-6) {
            return "Kompasskurs passt nicht zu Kurs und Fehler: " + PROBE.describe();
        }
        if (compass < 0.0 || compass >= 360.0) {
            return "Kompasskurs außerhalb 0–360°: " + PROBE.describe();
        }
        if (PROBE.healthy != Math.abs(error) < FlightState.COMPASS_DISTURBED_DEG) {
            return "Güte passt nicht zur Schwelle: " + PROBE.describe();
        }
        if (Double.isFinite(expected) && Math.abs(error - expected) > WANDER) {
            return String.format(Locale.ROOT, "Fehler %+.2f° statt etwa %+.1f°", error, expected);
        }
        return null;
    }

    private static void force(Map<String, Object> state, double degrees) {
        if (!state.containsKey(PREVIOUS)) {
            state.put(PREVIOUS, Optional.ofNullable(System.getProperty(CompassTestOverride.PROPERTY)));
        }
        System.setProperty(CompassTestOverride.PROPERTY, Double.toString(degrees));
    }

    private static void restore(Map<String, Object> state) {
        Object previous = state.remove(PREVIOUS);
        if (previous instanceof Optional<?> value) {
            if (value.isPresent()) {
                System.setProperty(CompassTestOverride.PROPERTY, (String) value.get());
            } else {
                System.clearProperty(CompassTestOverride.PROPERTY);
            }
        }
    }

    private static double wrap180(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            return d - 360.0;
        }
        return d < -180.0 ? d + 360.0 : d;
    }

    private static String phase(SelfTest.StepContext ctx, String initial) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(PHASE, initial);
            state.put(SINCE, ctx.tick());
        }
        return (String) state.get(PHASE);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().get(SINCE);
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (state.containsKey(RESULT)) {
            return StepResult.pending();
        }
        state.put(RESULT, result);
        restore(state);
        active = false;
        PilotSession.INSTANCE.clearTestInput();
        state.put(FUTURE, ctx.onServer(CompassSelfTests::release));
        return next(ctx, "cleanup");
    }

    private static boolean flying() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isFpv() && session.sim() != null && drone != null && drone.getId() == DRONE.get();
    }

    private static double height() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    private static void hover() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        Telemetry t = sim == null ? null : sim.telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (FLIGHT_HEIGHT - height()), -3.0, 3.0);
        float throttle = (float) Math.clamp(hover + 0.06 * (wanted - vs), 0.05, 0.9);
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
    }

    private static void spawnAndFly(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discard(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        Vec3 feet = player.position();
        groundY = feet.y;
        Vec3 pos = new Vec3(feet.x - 2.0, feet.y + 0.1, feet.z - 4.0);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        drone.setCustomName(Component.literal("Kompass"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.resetTo(pos, new Quaternionf());
        DRONE.set(drone.getId());
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        if (previousHand == null) {
            previousHand = player.getMainHandItem().copy();
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new)
                .orElseThrow(() -> new IllegalStateException("Kreativ-Brille nicht registriert"));
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void release(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        discard(server);
        previousHead = null;
        previousHand = null;
    }

    private static void discard(MinecraftServer server) {
        int id = DRONE.get();
        if (id >= 0) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
        }
        DRONE.set(-1);
    }
}
