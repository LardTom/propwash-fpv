package de.lardtom.propwash.rescue;

import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightStateAngleLimitTest {
    private static final String KEY = "pid.angleLimitDeg";
    private static final double DT = 1.0 / 144.0;
    private static final double HOVER_Y = 20.0;
    private static final double TILT_TOLERANCE = 3.0;

    @Test
    void oldImplementationsReportThePropwashDefault() throws NoSuchMethodException {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            Class<?> r = method.getReturnType();
            if (r == boolean.class) {
                return false;
            }
            if (r == int.class) {
                return 0;
            }
            return r == double.class ? 0.0 : null;
        };
        FlightState old = (FlightState) Proxy.newProxyInstance(FlightState.class.getClassLoader(), new Class<?>[]{FlightState.class}, handler);
        assertEquals(60.0, FlightState.DEFAULT_ANGLE_LIMIT_DEG);
        assertEquals(FlightState.DEFAULT_ANGLE_LIMIT_DEG, old.angleLimitDeg());
        assertTrue(FlightState.class.getMethod("angleLimitDeg").isDefault());
        assertEquals(FlightState.DEFAULT_ANGLE_LIMIT_DEG, new FlightControllerConfig().pid.angleLimitDeg);
    }

    @Test
    void clientStateReportsTheLimitFromTheTune() {
        double[][] cases = {{10.0, 10.0}, {30.0, 30.0}, {45.0, 45.0}, {60.0, 60.0}, {85.0, 85.0}, {5.0, 10.0}, {120.0, 85.0}};
        for (double[] c : cases) {
            Flight f = new Flight(tune(c[0]));
            f.hover(0.5);
            assertNotNull(f.state, "Flughilfe nicht aufgerufen");
            assertEquals(c[1], f.state.angleLimitDeg(), 1.0E-9, "Tune " + c[0] + "°");
        }
        Flight standard = new Flight(Map.of());
        standard.hover(0.5);
        assertEquals(FlightState.DEFAULT_ANGLE_LIMIT_DEG, standard.state.angleLimitDeg());
    }

    @Test
    void fullStickInAngleModeTiltsByTheReportedLimit() {
        for (double limit : new double[]{30.0, 45.0, 60.0}) {
            Flight f = new Flight(tune(limit));
            f.hover(2.0);
            double reported = f.state.angleLimitDeg();
            double tilt = f.fullForward(0.6);
            String label = String.format(Locale.ROOT, "Tune %.0f°: gemeldet %.1f°, geflogen %.1f°", limit, reported, tilt);
            assertEquals(limit, reported, 1.0E-9, label);
            assertEquals(reported, tilt, TILT_TOLERANCE, label);
        }
    }

    @Test
    void tuneChangeInFlightIsReportedWithTheNextUpdate() {
        Flight f = new Flight(Map.of());
        f.hover(0.5);
        assertEquals(60.0, f.state.angleLimitDeg());
        f.sim.setConfig(TuneMapping.toConfig(tune(25.0), f.sim.params()));
        f.frame();
        assertEquals(25.0, f.state.angleLimitDeg());
    }

    @Test
    void serverStateReportsTheLimitOfItsFlightModel() {
        ServerFlightState fresh = new ServerFlightState();
        assertEquals(FlightState.DEFAULT_ANGLE_LIMIT_DEG, fresh.angleLimitDeg());
        for (double limit : new double[]{12.0, 35.0, 60.0, 80.0}) {
            Flight f = new Flight(tune(limit));
            f.hover(0.2);
            ServerFlightState server = new ServerFlightState();
            server.read(f.sim, 1.0);
            assertEquals(limit, server.angleLimitDeg(), 1.0E-9);
            assertEquals(f.state.angleLimitDeg(), server.angleLimitDeg(), 1.0E-9);
        }
    }

    @Test
    void droneViewLimitEqualsTheFlightModelOfTheSameTune() {
        List<Map<String, Float>> tunes = List.of(Map.of(), floatTune(10.0F), floatTune(22.5F), floatTune(30.0F), floatTune(45.0F), floatTune(60.0F),
                floatTune(85.0F), floatTune(3.0F), floatTune(200.0F), floatTune(Float.NaN), floatTune(Float.POSITIVE_INFINITY),
                Map.of("pid.angleStrength", 80.0F));
        for (DroneBuild build : DroneBuild.PRESETS) {
            AirframeSpec params = AirframeSpec.of(build);
            for (Map<String, Float> t : tunes) {
                double expected = TuneMapping.toConfig(t, params).pid.angleLimitDeg;
                assertEquals(expected, TuneMapping.angleLimitDeg(t), 1.0E-9, build.frame() + " " + t);
                DroneSim sim = new DroneSim(params, TuneMapping.toConfig(t, params), new SimTuning(), 1L);
                ServerFlightState server = new ServerFlightState();
                server.read(sim, 0.0);
                assertEquals(expected, server.angleLimitDeg(), 1.0E-9, build.frame() + " " + t);
            }
        }
    }

    @Test
    void droneViewCarriesTheLimitAndKeepsItsOldConstructor() throws NoSuchMethodException {
        Vec3 zero = Vec3.ZERO;
        DroneView old = new DroneView(null, Optional.empty(), true, true, false, zero, zero, zero, zero, 0.6, Map.of());
        assertEquals(FlightState.DEFAULT_ANGLE_LIMIT_DEG, old.angleLimitDeg());
        DroneView tuned = new DroneView(null, Optional.empty(), true, true, false, zero, zero, zero, zero, 0.6, Map.of(), 35.0);
        assertEquals(35.0, tuned.angleLimitDeg());
        RecordComponent[] components = DroneView.class.getRecordComponents();
        assertEquals("angleLimitDeg", components[components.length - 1].getName());
        assertEquals(12, components.length);
        assertNotNull(DroneView.class.getConstructor(net.minecraft.world.entity.Entity.class, Optional.class, boolean.class, boolean.class,
                boolean.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class, double.class, Map.class));
    }

    private static Map<String, Float> tune(double limit) {
        return floatTune((float) limit);
    }

    private static Map<String, Float> floatTune(float limit) {
        Map<String, Float> m = new HashMap<>();
        m.put(KEY, limit);
        return m;
    }

    private static final class Flight implements FlightAssist {
        final DroneSim sim;
        final PilotInput input = new PilotInput();
        final SimEnvironment env = new SimEnvironment();
        final StepResult result = new StepResult();
        final MoveReport report = new MoveReport();
        final Vector3d position = new Vector3d();
        final Vector3d velocity = new Vector3d();
        FlightState state;
        Consumer<AssistOutput> command = out -> {
        };
        private double integral;

        Flight(Map<String, Float> tune) {
            AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
            this.sim = new DroneSim(params, TuneMapping.toConfig(tune, params), new SimTuning(), 7L);
            this.env.skyView = 1.0;
            this.sim.reset(0.0, HOVER_Y, 0.0, 0.0);
            this.sim.setAssist(this);
            this.input.mode = StabMode.ANGLE;
            this.input.armSwitch = false;
            this.sim.step(0.0, this.input, this.env, this.result);
            this.input.armSwitch = true;
            this.sim.step(0.0, this.input, this.env, this.result);
        }

        @Override
        public void update(FlightState s, AssistOutput out) {
            this.state = s;
            this.command.accept(out);
        }

        void frame() {
            this.sim.step(DT, this.input, this.env, this.result);
            MoveReport r = this.report;
            r.clear();
            r.requestedX = this.result.dx;
            r.requestedY = this.result.dy;
            r.requestedZ = this.result.dz;
            r.actualX = this.result.dx;
            r.actualY = this.result.dy;
            r.actualZ = this.result.dz;
            r.supportGap = 0.05;
            this.sim.afterMove(r);
            this.sim.getPosition(this.position);
            this.sim.getVelocity(this.velocity);
        }

        void hover(double seconds) {
            for (int i = 0; i < Math.round(seconds * 144.0); i++) {
                this.sim.getPosition(this.position);
                this.sim.getVelocity(this.velocity);
                double err = HOVER_Y - this.position.y;
                double base = this.sim.telemetry().hoverThrottleStick;
                double raw = base + 0.08 * err - 0.06 * this.velocity.y + this.integral;
                if (raw > 0.0 && raw < 1.0) {
                    this.integral = Math.clamp(this.integral + 0.05 * err * DT, -0.3, 0.3);
                }
                this.input.throttle = Math.clamp(raw, 0.0, 1.0);
                this.frame();
            }
        }

        double fullForward(double seconds) {
            double throttle = this.sim.telemetry().hoverThrottleStick;
            this.command = out -> {
                out.sticks(0.0, 1.0, 0.0, throttle);
                out.mode(StabilizationMode.ANGLE);
            };
            double peak = 0.0;
            for (int i = 0; i < Math.round(seconds * 144.0); i++) {
                this.frame();
                if (this.state != null) {
                    peak = Math.max(peak, Math.toDegrees(Math.acos(Math.clamp(this.state.upY(), -1.0, 1.0))));
                }
            }
            return peak;
        }
    }
}
