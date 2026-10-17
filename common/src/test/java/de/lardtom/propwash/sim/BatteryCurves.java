package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.ArrayList;
import java.util.List;

final class BatteryCurves {
    static final double BENCH_STEP = 0.01;
    static final long FRAME = 10_000_000L;

    private BatteryCurves() {
    }

    record Curve(double[] used, double[] cell, double capacity) {
        double at(double x) {
            double target = x * this.capacity;
            int i = 1;
            while (i < this.used.length - 1 && this.used[i] < target) {
                i++;
            }
            double span = this.used[i] - this.used[i - 1];
            double t = span > 0.0 ? Math.clamp((target - this.used[i - 1]) / span, 0.0, 1.0) : 1.0;
            return this.cell[i - 1] + (this.cell[i] - this.cell[i - 1]) * t;
        }

        double slope(double from, double to) {
            return (this.at(from) - this.at(to)) / (to - from);
        }
    }

    static Curve discharge(AirframeSpec p, double cRate, double cutoffCell) {
        PowerSystem power = new PowerSystem();
        power.configure(p, BENCH_STEP);
        power.setStateOfCharge(1.0);
        double amps = cRate * p.capacityMah / 1000.0;
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{0.0, power.emf / p.cells});
        while (true) {
            power.forceBatteryCurrent(amps, BENCH_STEP, true, false);
            double used = power.mAhUsed / p.capacityMah;
            double cell = power.busVoltage / p.cells;
            points.add(new double[]{used, cell});
            if (cell <= cutoffCell || used > 1.25) {
                break;
            }
        }
        double[] used = new double[points.size()];
        double[] cell = new double[points.size()];
        for (int k = 0; k < used.length; k++) {
            used[k] = points.get(k)[0];
            cell[k] = points.get(k)[1];
        }
        double last = cell[cell.length - 1];
        double capacity = used[used.length - 1];
        if (last < cutoffCell && cell.length > 1) {
            double prev = cell[cell.length - 2];
            double t = (prev - cutoffCell) / Math.max(prev - last, 1e-12);
            capacity = used[used.length - 2] + (used[used.length - 1] - used[used.length - 2]) * t;
        }
        return new Curve(used, cell, capacity);
    }

    record Trace(double[] seconds, double[] pack, double before, boolean armed) {
        double at(double t) {
            int i = (int) Math.round(t / (FRAME / 1.0e9));
            return this.pack[Math.clamp(i, 0, this.pack.length - 1)];
        }

        double lowest(double from, double to) {
            double low = Double.POSITIVE_INFINITY;
            for (int k = 0; k < this.seconds.length; k++) {
                if (this.seconds[k] >= from - 1e-9 && this.seconds[k] <= to + 1e-9) {
                    low = Math.min(low, this.pack[k]);
                }
            }
            return low;
        }
    }

    static Trace punch(DroneBuild build, double charge, double hoverStick, double punchSeconds, double afterSeconds) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 7L);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        sim.reset(0.0, 200.0, 0.0, 0.0);
        sim.setBatteryCharge(charge);
        sim.step(0L, input, env, result);
        input.armSwitch = true;
        sim.step(0L, input, env, result);
        input.throttle = hoverStick;
        for (int k = 0; k < 300; k++) {
            sim.step(FRAME, input, env, result);
        }
        double before = sim.telemetry().batteryVoltage;
        int punch = (int) Math.round(punchSeconds * 1.0e9 / FRAME);
        int after = (int) Math.round(afterSeconds * 1.0e9 / FRAME);
        double[] seconds = new double[punch + after + 1];
        double[] pack = new double[punch + after + 1];
        pack[0] = before;
        boolean armed = true;
        for (int k = 1; k < pack.length; k++) {
            input.throttle = k <= punch ? 1.0 : hoverStick;
            sim.step(FRAME, input, env, result);
            seconds[k] = k * FRAME / 1.0e9;
            pack[k] = sim.telemetry().batteryVoltage;
            armed &= sim.telemetry().armed;
        }
        return new Trace(seconds, pack, before, armed);
    }
}
