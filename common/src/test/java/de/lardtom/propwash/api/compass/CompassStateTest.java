package de.lardtom.propwash.api.compass;

import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompassStateTest {
    private static final double DT = 1.0 / 144.0;
    private static final double HOVER_Y = 20.0;
    private static final double WANDER = 6.5;
    private static final double EXACT = 1.0E-9;
    private static final String PROPERTY = "propwash.test.compassErrorDeg";

    @Test
    void oldImplementationsReportTheTrueHeadingAsHealthy() throws NoSuchMethodException {
        FlightState old = state(123.25, Double.NaN);
        assertEquals(123.25, old.compassHeadingDeg());
        assertEquals(0.0, old.compassErrorDeg());
        assertTrue(old.compassHealthy());
        assertEquals(15.0, FlightState.COMPASS_DISTURBED_DEG);
        for (String name : new String[]{"compassHeadingDeg", "compassErrorDeg", "compassHealthy"}) {
            assertTrue(FlightState.class.getMethod(name).isDefault(), name + " ist keine default-Methode");
        }
    }

    @Test
    void healthFollowsTheDocumentedThreshold() {
        assertError(14.0, true, 350.0, 4.0);
        assertError(-14.9, true, 10.0, 355.1);
        assertError(15.0, false, 350.0, 5.0);
        assertError(-20.0, false, 10.0, 350.0);
        assertError(42.0, false, 300.0, 342.0);
        assertError(180.0, false, 0.0, 180.0);
        FlightState unknown = state(90.0, Double.NaN, true);
        assertTrue(Double.isNaN(unknown.compassErrorDeg()));
        assertFalse(unknown.compassHealthy());
    }

    @Test
    void clientStateReportsTheHeadingTheFlightControllerAssumes() {
        double[] errors = {4.0, -4.0, 25.0, -42.0};
        double[] headings = {0.3, 2.5};
        for (double error : errors) {
            for (double heading : headings) {
                Flight f = new Flight(21L, heading, error);
                f.hover(12.0);
                FlightState s = f.state;
                assertNotNull(s, "Flughilfe nicht aufgerufen");
                String label = String.format(Locale.ROOT, "Fehler %.0f°, Kurs %.1f rad", error, heading);
                assertEquals(error, s.compassErrorDeg(), WANDER, label);
                assertEquals(-f.sim.telemetry().headingErrorDeg, s.compassErrorDeg(), EXACT, label);
                assertEquals(s.compassErrorDeg(), wrap180(s.compassHeadingDeg() - s.headingDeg()), EXACT, label);
                assertTrue(s.compassHeadingDeg() >= 0.0 && s.compassHeadingDeg() < 360.0, label + ": " + s.compassHeadingDeg());
                assertEquals(Math.abs(error) < FlightState.COMPASS_DISTURBED_DEG, s.compassHealthy(), label);
            }
        }
    }

    @Test
    void positionHoldTiltsAwayFromTheCommandByTheCompassError() {
        for (double error : new double[]{35.0, -35.0, 2.0}) {
            Flight f = new Flight(5L, 1.1, error);
            f.hover(12.0);
            double actual = f.firstTiltBearing(90.0);
            double expected = 90.0 - f.state.compassErrorDeg();
            assertEquals(0.0, wrap180(actual - expected), 10.0,
                    String.format(Locale.ROOT, "Fehler %.1f°: Neigung nach %.1f°, erwartet %.1f°", f.state.compassErrorDeg(), actual, expected));
        }
    }

    @Test
    void disturbedCompassIsTheOneThatCirclesInPositionHold() {
        Flight good = new Flight(9L, 0.4, 4.0);
        good.hover(12.0);
        Flight bad = new Flight(9L, 0.4, 42.0);
        bad.hover(12.0);
        double calm = good.positionHold(40.0);
        double bowl = bad.positionHold(40.0);
        assertTrue(good.state.compassHealthy(), "4° gilt als gestört");
        assertFalse(bad.state.compassHealthy(), "42° gilt als gesund");
        assertTrue(calm < 1.0, String.format(Locale.ROOT, "4° hält nicht: %.2f m/s", calm));
        assertTrue(bowl > 2.0, String.format(Locale.ROOT, "42° kreist nicht: %.2f m/s", bowl));
    }

    @Test
    void aboutOneFlightInTenStartsWithADisturbedCompass() {
        int disturbed = 0;
        int seeds = 200;
        for (long seed = 1; seed <= seeds; seed++) {
            Flight f = new Flight(seed, 0.0);
            f.hover(0.1);
            FlightState s = f.state;
            double error = Math.abs(s.compassErrorDeg());
            assertTrue(error >= 0.0 && error < 60.0, "Seed " + seed + ": " + error);
            assertEquals(error < FlightState.COMPASS_DISTURBED_DEG, s.compassHealthy(), "Seed " + seed);
            if (!s.compassHealthy()) {
                disturbed++;
                assertTrue(error > 15.0, "Seed " + seed + " gestört bei " + error);
            } else {
                assertTrue(error < 14.0, "Seed " + seed + " knapp unter der Schwelle: " + error);
            }
        }
        assertTrue(disturbed >= 8 && disturbed <= 35, disturbed + " von " + seeds + " Flügen gestört");
    }

    @Test
    void testPropertyFixesTheCompassErrorOfEveryNewFlight() {
        long calm = naturalSeed(true);
        long stormy = naturalSeed(false);
        double[][] cases = {{calm, 40.0}, {calm, -25.0}, {stormy, 0.0}, {stormy, 3.0}, {calm, 170.0}};
        for (double[] c : cases) {
            long seed = (long) c[0];
            double error = c[1];
            Flight f = new Flight(seed, 0.8, error);
            f.hover(3.0);
            FlightState s = f.state;
            String label = String.format(Locale.ROOT, "Seed %d, Eigenschaft %.0f°: %+.2f°", seed, error, s.compassErrorDeg());
            assertEquals(0.0, wrap180(s.compassErrorDeg() - error), WANDER, label);
            assertEquals(-f.sim.telemetry().headingErrorDeg, s.compassErrorDeg(), EXACT, label);
            assertEquals(Math.abs(error) < FlightState.COMPASS_DISTURBED_DEG, s.compassHealthy(), label);
        }
    }

    @Test
    void testPropertyLeavesEverythingElseOfTheFlightUnchanged() {
        for (long seed : new long[]{naturalSeed(true), naturalSeed(false)}) {
            Flight natural = new Flight(seed, 1.3);
            Flight forced = new Flight(seed, 1.3, 40.0);
            natural.hover(4.0);
            forced.hover(4.0);
            String label = "Seed " + seed;
            assertEquals(natural.position, forced.position, label);
            assertEquals(natural.velocity, forced.velocity, label);
            assertEquals(natural.sim.telemetry().altitudeEstimate, forced.sim.telemetry().altitudeEstimate, label);
            assertEquals(natural.sim.telemetry().baroAltitude, forced.sim.telemetry().baroAltitude, label);
            assertEquals(natural.sim.telemetry().gpsSatellites, forced.sim.telemetry().gpsSatellites, label);
            assertEquals(natural.sim.telemetry().gpsHdop, forced.sim.telemetry().gpsHdop, label);
            assertEquals(40.0, forced.state.compassErrorDeg(), WANDER, label);
            assertNotEquals(natural.state.compassErrorDeg(), forced.state.compassErrorDeg(), label);
        }
    }

    @Test
    void testPropertyOnlyActsWhenSetAndValidAtTheStartOfAFlight() {
        long seed = naturalSeed(true);
        Flight natural = new Flight(seed, 0.5);
        natural.hover(1.0);
        double expected = natural.sim.telemetry().headingErrorDeg;
        for (String invalid : new String[]{"", " ", "abc", "40°", "NaN", "Infinity", "-Infinity"}) {
            Flight f = new Flight(seed, 0.5, invalid);
            f.hover(1.0);
            assertEquals(expected, f.sim.telemetry().headingErrorDeg, "ungültiger Wert \"" + invalid + "\" wirkt");
        }
        Flight spaced = new Flight(seed, 0.5, " 40 ");
        spaced.hover(1.0);
        assertEquals(40.0, spaced.state.compassErrorDeg(), WANDER, "Wert mit Leerzeichen");
        Flight late = new Flight(seed, 0.5);
        String before = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, "40");
            late.hover(1.0);
        } finally {
            restore(before);
        }
        assertEquals(expected, late.sim.telemetry().headingErrorDeg, "im laufenden Flug gesetzt");
    }

    @Test
    void serverStateFliesWithTheTrueHeading() throws ReflectiveOperationException {
        Flight f = new Flight(3L, 2.0, 40.0);
        f.hover(2.0);
        Class<?> type = Class.forName("de.lardtom.propwash.rescue.ServerFlightState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object server = constructor.newInstance();
        Method read = type.getDeclaredMethod("read", DroneSim.class, double.class);
        read.setAccessible(true);
        read.invoke(server, f.sim, 2.0);
        FlightState s = (FlightState) server;
        assertEquals(40.0, -f.sim.telemetry().headingErrorDeg, WANDER);
        assertEquals(s.headingDeg(), s.compassHeadingDeg());
        assertEquals(0.0, s.compassErrorDeg());
        assertTrue(s.compassHealthy());
        assertEquals(f.state.headingDeg(), s.headingDeg(), 1.0E-6);
    }

    private static long naturalSeed(boolean healthy) {
        for (long seed = 1; seed <= 400; seed++) {
            Flight f = new Flight(seed, 0.0);
            f.hover(0.1);
            if (f.state.compassHealthy() == healthy) {
                return seed;
            }
        }
        throw new AssertionError("kein Seed mit " + (healthy ? "gesundem" : "gestörtem") + " Kompass");
    }

    private static DroneSim sim(long seed, String compassProperty) {
        String before = System.getProperty(PROPERTY);
        try {
            if (compassProperty == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, compassProperty);
            }
            return new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), seed);
        } finally {
            restore(before);
        }
    }

    private static void restore(String before) {
        if (before == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, before);
        }
    }

    private static void assertError(double expected, boolean healthy, double heading, double compass) {
        FlightState s = state(heading, compass, true);
        assertEquals(expected, s.compassErrorDeg(), EXACT, heading + " → " + compass);
        assertEquals(healthy, s.compassHealthy(), heading + " → " + compass);
    }

    private static FlightState state(double heading, double compass) {
        return state(heading, compass, false);
    }

    private static FlightState state(double heading, double compass, boolean overrideCompass) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "headingDeg" -> {
                    return heading;
                }
                case "compassHeadingDeg" -> {
                    if (overrideCompass) {
                        return compass;
                    }
                }
                default -> {
                }
            }
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
            if (r == double.class) {
                return 0.0;
            }
            return null;
        };
        return (FlightState) Proxy.newProxyInstance(FlightState.class.getClassLoader(), new Class<?>[]{FlightState.class}, handler);
    }

    private static double wrap180(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            return d - 360.0;
        }
        return d < -180.0 ? d + 360.0 : d;
    }

    private static double bearing(double x, double z) {
        double b = Math.toDegrees(Math.atan2(x, -z));
        return b < 0.0 ? b + 360.0 : b;
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

        Flight(long seed, double headingRad) {
            this(seed, headingRad, null);
        }

        Flight(long seed, double headingRad, double compassErrorDeg) {
            this(seed, headingRad, Double.toString(compassErrorDeg));
        }

        Flight(long seed, double headingRad, String compassProperty) {
            this.sim = sim(seed, compassProperty);
            this.env.skyView = 1.0;
            this.sim.reset(0.0, HOVER_Y, 0.0, headingRad);
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

        double firstTiltBearing(double compassDeg) {
            double x0 = this.position.x;
            double z0 = this.position.z;
            double vx = 4.0 * Math.sin(Math.toRadians(compassDeg));
            double vz = -4.0 * Math.cos(Math.toRadians(compassDeg));
            this.command = out -> {
                out.holdAltitude(HOVER_Y);
                out.velocity(vx, vz);
                out.maxSpeed(4.0);
            };
            double sumX = 0.0;
            double sumZ = 0.0;
            for (int i = 0; i < 144; i++) {
                this.frame();
                if (i >= 36 && i < 108) {
                    sumX += this.state.upX();
                    sumZ += this.state.upZ();
                }
            }
            assertTrue(Math.hypot(this.position.x - x0, this.position.z - z0) > 0.3, "Drohne bewegt sich nicht");
            return bearing(sumX, sumZ);
        }

        double positionHold(double seconds) {
            this.input.throttle = this.sim.telemetry().hoverThrottleStick;
            this.input.altHold = true;
            this.input.posHold = true;
            double peak = 0.0;
            int frames = (int) Math.round(seconds * 144.0);
            for (int i = 0; i < frames; i++) {
                this.frame();
                if (i >= frames / 2) {
                    peak = Math.max(peak, Math.hypot(this.velocity.x, this.velocity.z));
                }
            }
            return peak;
        }
    }
}
