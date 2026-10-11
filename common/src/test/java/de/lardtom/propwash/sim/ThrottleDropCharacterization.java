package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ThrottleDropCharacterization {
    static final double FPS = 144.0;
    static final double ALTITUDE = 3000.0;
    static final double SOC = 0.8;
    static final double DROP_METERS = 10.0;
    static final double STEADY_SECONDS = 30.0;
    static final double[] STEPS = {0.0, 0.2};
    static final double BODY_CD = 1.2;
    static final double BLADE_CD = 1.66;
    static final double AREA_LOW = 0.8;
    static final double AREA_HIGH = 1.25;
    static final double REFERENCE_DT = 0.0005;
    static final double[][] VRS_CURVE = {{0.0, 1.0}, {0.59, 0.92}, {0.91, 0.81}, {1.07, 0.79}, {1.22, 0.84}, {1.36, 0.94}, {1.50, 1.04},
            {1.65, 1.12}, {1.78, 1.18}, {1.94, 1.31}, {2.09, 1.39}, {2.23, 1.48}, {2.36, 1.57}, {2.51, 1.65}, {2.65, 1.74}, {2.80, 1.82},
            {2.96, 1.93}};

    static final String WHOOP75_TOP_AREA = "\"top_area_m2\": 0.011";
    static final String WHOOP75_FRAME = """
            {"format": 1, "mass_g": 11.0, "role": "whoop", "shape": "true_x", "wheelbase_mm": 80, "props_mm": [40, 46],
             "motor_mounts": ["propwash:m08"], "stack_mounts": ["propwash:whoop_aio"], "accessory_mounts": ["bottom"],
             "battery": {"on_top": false, "plate_mm": -3.5}, "electronics_mm": 8.0,
             "camera": {"pos": [0.0, 12.0, -22.0], "tilt_deg": 20.0},
             "aero": {"frontal_area_m2": 0.0048, "drag_coefficient": 1.1, "top_area_m2": 0.011},
             "duct": {"thrust_gain": 1.05, "clearance_mm": 3.5}, "impact_tolerance": 13.0,
             "battery_fit": {"cells": [1, 1], "max_mass_g": 20.0, "preferred_min_cells": 1, "preferred_min_capacity_mah": 0}}""";
    static final String WHOOP75_BATTERY = """
            {"format": 1, "mass_g": 14.1, "cells": 1, "capacity_mah": 580, "cell_resistance_ohm": 0.0092, "c_rating": 95,
             "chemistry": "propwash:lihv", "pack_mm": [70, 15.0, 6.5], "connector": "BT2.0"}""";
    static final String WHOOP75_VIDEO = """
            {"format": 1, "mass_g": 13.6, "link": "digital", "latency_ms": 26, "range_blocks": 300, "image_quality": 0.85,
             "camera_width_mm": 14.0, "camera_fov_deg": 127.0, "power_w": 5.5}""";
    static final double WHOOP75_METHOD_TOP_AREA = 0.0058;

    static final Map<String, double[]> GEOMETRY = Map.of(
            "whoop75", new double[]{0.0034, 0.0014},
            "propwash:whoop_65", new double[]{0.0021, 0.0012},
            "justmoreparts:whoop75_1s", new double[]{0.0025, 0.00126},
            "justmoreparts:whoop85_2s", new double[]{0.0033, 0.0019},
            "propwash:cinewhoop_3", new double[]{0.0109, 0.0036},
            "propwash:freestyle_5", new double[]{0.0141, 0.0085});

    private ThrottleDropCharacterization() {
    }

    record Entry(String name, String geometry, DroneBuild build) {
    }

    static DroneBuild whoop75(double topArea) {
        String json = WHOOP75_FRAME.replace(WHOOP75_TOP_AREA, String.format(Locale.ROOT, "\"top_area_m2\": %.4f", topArea));
        FrameType frame = CalibrationHarness.fixture(PartKind.FRAME, "whoop75_reference_" + Math.round(topArea * 1.0e5), json);
        StackType stack = CalibrationHarness.fixture(PartKind.STACK, "matrix_1s_3in1_hd", WhoopThrustTableTest.STACK);
        MotorType motor = CalibrationHarness.fixture(PartKind.MOTOR, "m1102_21000", WhoopThrustTableTest.MOTOR);
        PropType prop = CalibrationHarness.fixture(PartKind.PROP, "gf1811_3b", WhoopThrustTableTest.PROP);
        VideoType video = CalibrationHarness.fixture(PartKind.VIDEO, "digital_lite_wide", WHOOP75_VIDEO);
        BatteryType battery = CalibrationHarness.fixture(PartKind.BATTERY, "lihv_1s_580", WHOOP75_BATTERY);
        return new DroneBuild(frame, stack, motor, prop, video, battery);
    }

    static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("whoop75 Umriss 0,011", "whoop75", whoop75(0.011)));
        out.add(new Entry("whoop75 Methode 0,0058", "whoop75", whoop75(WHOOP75_METHOD_TOP_AREA)));
        for (CalibrationHarness.Entry e : CalibrationHarness.builtins()) {
            if (GEOMETRY.containsKey(e.name())) {
                out.add(new Entry(e.name(), e.name(), e.build()));
            }
        }
        for (CalibrationHarness.Entry e : CalibrationHarness.addons()) {
            if (GEOMETRY.containsKey(e.name())) {
                out.add(new Entry(e.name(), e.name(), e.build()));
            }
        }
        return out;
    }

    static final class Step {
        double stick;
        double accel100;
        double accel250;
        double v05;
        double v1;
        double t10;
        double steady;
        double thrustPct;
        double dragPct;
        double washWeight;
        double sinkRatio;
    }

    static final class Expected {
        double stick;
        double accel100;
        double accel250;
        double v05;
        double v1;
        double t10;
        double t10Low;
        double t10High;
        double steady;
        double steadyLow;
        double steadyHigh;
        double steadySink;
    }

    static final class Result {
        String name;
        double massG;
        double hoverStick;
        double referenceHoverStick;
        double motorTauMs;
        double methodCdA;
        double simCdA;
        final List<Step> steps = new ArrayList<>();
        final List<Expected> expected = new ArrayList<>();
        double punchAccel;
        double punchV1;
        double punchIdealV1;
        double topKmh;
    }

    static Result measure(Entry e, SimTuning tuning) {
        DroneBuild b = e.build();
        Result r = new Result();
        r.name = e.name();
        AirframeSpec p = AirframeSpec.of(b);
        r.massG = p.massKg * 1000.0;
        r.motorTauMs = p.motorTauPhys * 1000.0;
        r.simCdA = p.dragCoefficient * p.areaY;
        double[] geometry = GEOMETRY.get(e.geometry());
        r.methodCdA = BODY_CD * geometry[0] + BLADE_CD * geometry[1];
        FlatWorld probe = hover(b, tuning);
        r.hoverStick = probe.input.throttle;
        StaticThrust thrust = new StaticThrust(b, tuning);
        r.referenceHoverStick = thrust.hoverStick();
        for (double s : STEPS) {
            r.steps.add(step(b, tuning, s));
            r.expected.add(reference(p, thrust, geometry, s));
        }
        punch(b, tuning, r, thrust);
        r.topKmh = TopSpeedTest.levelTopKmh(b);
        return r;
    }

    static FlatWorld hover(DroneBuild b, SimTuning t) {
        FlatWorld w = FlightCharacterization.hover(b, new FlightControllerConfig(), t, ALTITUDE, SOC, FPS);
        w.input.mode = StabMode.ACRO;
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        return w;
    }

    static final class StaticThrust {
        final double weight;
        final double idle;
        private final DroneBuild build;
        private final SimTuning tuning;

        StaticThrust(DroneBuild build, SimTuning tuning) {
            this.build = build;
            this.tuning = tuning;
            AirframeSpec p = AirframeSpec.of(build);
            this.weight = p.massKg * ModelConstants.G * tuning.gravityScale;
            this.idle = new PidProfile().motorIdlePercent / 100.0;
        }

        double ratio(double stick) {
            TestStand stand = new TestStand(this.build, this.tuning);
            stand.sim.setBatteryCharge(SOC);
            stand.duty(this.idle + (1.0 - this.idle) * stick);
            stand.substeps(3000);
            double sum = 0.0;
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                sum += stand.sim.thrust[i];
            }
            return sum / this.weight;
        }

        double hoverStick() {
            double lo = 0.0;
            double hi = 1.0;
            if (this.ratio(hi) < 1.0) {
                return 1.0;
            }
            for (int k = 0; k < 18; k++) {
                double mid = 0.5 * (lo + hi);
                if (this.ratio(mid) < 1.0) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return 0.5 * (lo + hi);
        }
    }

    static double measuredSinkLimit() {
        return VRS_CURVE[VRS_CURVE.length - 1][0];
    }

    static double vrsFactor(double x) {
        if (x <= 0.0) {
            return 1.0;
        }
        double[][] c = VRS_CURVE;
        for (int k = 1; k < c.length; k++) {
            if (x <= c[k][0]) {
                double f = (x - c[k - 1][0]) / (c[k][0] - c[k - 1][0]);
                return c[k - 1][1] + f * (c[k][1] - c[k - 1][1]);
            }
        }
        return c[c.length - 1][1];
    }

    static Expected reference(AirframeSpec p, StaticThrust thrust, double[] geometry, double stick) {
        Expected nominal = integrate(p, thrust, geometry, stick, 1.0);
        Expected low = integrate(p, thrust, geometry, stick, AREA_LOW);
        Expected high = integrate(p, thrust, geometry, stick, AREA_HIGH);
        nominal.t10Low = Math.min(low.t10, high.t10);
        nominal.t10High = Math.max(low.t10, high.t10);
        nominal.steadyLow = Math.min(low.steady, high.steady);
        nominal.steadyHigh = Math.max(low.steady, high.steady);
        return nominal;
    }

    static Expected integrate(AirframeSpec p, StaticThrust thrust, double[] geometry, double stick, double areaScale) {
        Expected e = new Expected();
        e.stick = stick;
        double m = p.massKg;
        double g = thrust.weight / m;
        double rho = ModelConstants.RHO0;
        double bodyCdA = BODY_CD * geometry[0] * areaScale;
        double bladeCdA = BLADE_CD * geometry[1] * areaScale;
        double target = Math.sqrt(Math.max(0.0, thrust.ratio(stick)));
        double tau = p.motorTauPhys;
        double rotorArea = p.discArea * p.ductGain;
        double r = 1.0;
        double v = 0.0;
        double fall = 0.0;
        double t = 0.0;
        double h = REFERENCE_DT;
        e.t10 = Double.NaN;
        double sum = 0.0;
        double sinkSum = 0.0;
        int n = 0;
        int steps = (int) Math.round(STEADY_SECONDS / h);
        for (int k = 1; k <= steps; k++) {
            r += (target - r) * (1.0 - Math.exp(-h / tau));
            double rotorThrust = thrust.weight * r * r;
            double vh = Math.sqrt(Math.max(rotorThrust, 1e-9) / AirframeSpec.MOTORS / (2.0 * rho * rotorArea));
            double q = 0.5 * rho * v * Math.abs(v);
            double rotor = Math.max(rotorThrust * vrsFactor(v / vh), bladeCdA * q);
            double a = g - (rotor + bodyCdA * q) / m;
            v += a * h;
            fall += v * h;
            t = k * h;
            if (k == Math.round(0.1 / h)) {
                e.accel100 = -v / t;
            }
            if (k == Math.round(0.25 / h)) {
                e.accel250 = -v / t;
            }
            if (k == Math.round(0.5 / h)) {
                e.v05 = v;
            }
            if (k == Math.round(1.0 / h)) {
                e.v1 = v;
            }
            if (Double.isNaN(e.t10) && fall >= DROP_METERS) {
                e.t10 = t;
            }
            if (t > STEADY_SECONDS - 2.0) {
                sum += v;
                sinkSum += v / vh;
                n++;
            }
        }
        e.steady = sum / n;
        e.steadySink = sinkSum / n;
        return e;
    }

    static Step step(DroneBuild b, SimTuning tuning, double stick) {
        Step s = new Step();
        s.stick = stick;
        FlatWorld w = hover(b, tuning);
        DroneSim sim = w.sim;
        RigidBody body = sim.body;
        AirframeSpec p = sim.params();
        double weight = p.massKg * sim.gravity();
        double y0 = body.py;
        double v0 = body.vy;
        double dt = 1.0 / FPS;
        w.input.throttle = stick;
        double t = 0.0;
        s.t10 = Double.NaN;
        int frames = (int) Math.round(STEADY_SECONDS * FPS);
        int window = (int) Math.round(2.0 * FPS);
        double vSum = 0.0;
        double thrustSum = 0.0;
        double dragSum = 0.0;
        double washSum = 0.0;
        double sinkSum = 0.0;
        int n = 0;
        int f100 = (int) Math.round(0.1 * FPS);
        int f250 = (int) Math.round(0.25 * FPS);
        int f05 = (int) Math.round(0.5 * FPS);
        int f1 = (int) Math.round(FPS);
        for (int f = 1; f <= frames; f++) {
            w.frame(dt);
            t = f * dt;
            if (f == f100) {
                s.accel100 = (body.vy - v0) / t;
            }
            if (f == f250) {
                s.accel250 = (body.vy - v0) / t;
            }
            if (f == f05) {
                s.v05 = -body.vy;
            }
            if (f == f1) {
                s.v1 = -body.vy;
            }
            if (Double.isNaN(s.t10) && y0 - body.py >= DROP_METERS) {
                s.t10 = t;
            }
            if (f > frames - window) {
                vSum += -body.vy;
                double thrust = 0.0;
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    thrust += sim.thrust[i];
                }
                thrustSum += thrust * body.r11;
                dragSum += body.r10 * sim.bodyAero.fx + body.r11 * sim.bodyAero.fy + body.r12 * sim.bodyAero.fz;
                washSum += sim.wash.maxOf();
                double vh = Math.sqrt(Math.max(thrust, 1e-6) / AirframeSpec.MOTORS / (2.0 * ModelConstants.RHO0 * p.discArea * p.ductGain));
                sinkSum += -body.vy / vh;
                n++;
            }
        }
        s.steady = vSum / n;
        s.thrustPct = 100.0 * thrustSum / n / weight;
        s.dragPct = 100.0 * dragSum / n / weight;
        s.washWeight = washSum / n;
        s.sinkRatio = sinkSum / n;
        return s;
    }

    static void punch(DroneBuild b, SimTuning tuning, Result r, StaticThrust thrust) {
        FlatWorld w = hover(b, tuning);
        RigidBody body = w.sim.body;
        double dt = 1.0 / FPS;
        double v0 = body.vy;
        w.input.throttle = 1.0;
        int f250 = (int) Math.round(0.25 * FPS);
        for (int f = 1; f <= (int) Math.round(FPS); f++) {
            w.frame(dt);
            if (f == f250) {
                r.punchAccel = (body.vy - v0) / (f * dt);
            }
        }
        r.punchV1 = body.vy;
        r.punchIdealV1 = (thrust.ratio(1.0) - 1.0) * thrust.weight / AirframeSpec.of(b).massKg;
    }

    static String header() {
        return String.format(Locale.ROOT, "%-26s %6s %6s %6s %5s %7s %7s | %-5s %-4s %6s %6s %5s %5s %13s %13s | %6s %6s %4s %5s | %6s %5s %6s %5s%n",
                "Aufbau", "Masse", "Gas%", "Ref%", "tau", "CdA_y", "Methode", "Stick", "", "a0,1s", "a0,25s", "v0,5", "v1", "t10m",
                "v_end", "Schub%", "Rumpf%", "Wash", "Sink", "aPunch", "vP1", "ideal", "km/h");
    }

    static String rows(Result r) {
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < r.steps.size(); k++) {
            Step s = r.steps.get(k);
            Expected e = r.expected.get(k);
            out.append(String.format(Locale.ROOT,
                    "%-26s %6.1f %6.1f %6.1f %5.1f %7.4f %7.4f | %5.0f %-4s %6.2f %6.2f %5.2f %5.2f %13.2f %13.2f | %6.1f %6.1f %4.2f %5.2f | %6.2f %5.2f %6.2f %5.1f%n",
                    r.name, r.massG, 100.0 * r.hoverStick, 100.0 * r.referenceHoverStick, r.motorTauMs, r.simCdA, r.methodCdA,
                    100.0 * s.stick, "Sim", s.accel100, s.accel250, s.v05, s.v1, s.t10, s.steady, s.thrustPct, s.dragPct, s.washWeight,
                    s.sinkRatio, r.punchAccel, r.punchV1, r.punchIdealV1, r.topKmh));
            out.append(String.format(Locale.ROOT,
                    "%-26s %6s %6s %6s %5s %7s %7s | %5.0f %-4s %6.2f %6.2f %5.2f %5.2f %4.2f %3.2f-%3.2f %5.2f %3.1f-%4.1f |%n",
                    "", "", "", "", "", "", "", 100.0 * e.stick, "Ref", e.accel100, e.accel250, e.v05, e.v1, e.t10, e.t10Low,
                    e.t10High, e.steady, e.steadyLow, e.steadyHigh));
        }
        return out.toString();
    }
}
