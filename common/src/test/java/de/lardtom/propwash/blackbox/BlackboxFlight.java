package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FcListener;
import de.lardtom.propwash.sim.FcSample;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;

import java.util.ArrayList;
import java.util.List;

final class BlackboxFlight {
    static final long FRAME_NANOS = 1_000_000_000L / 120L;

    record Truth(long substep, int divider, boolean armed, double[] p, double[] i, double[] d, double[] f, double[] stick, double[] setpoint,
                 double throttle, double[] gyro, double[] gyroRaw, double[] duty, double[] rpm, double voltage, double current, double baro,
                 boolean gpsFix, int sats, double gpsX, double gpsY, double gpsZ, double gpsVx, double gpsVz, StabMode mode, boolean airmode) {
        static Truth of(FcSample s) {
            return new Truth(s.substep, s.loopDivider, s.armed, s.pTerm.clone(), s.iTerm.clone(), s.dTerm.clone(), s.fTerm.clone(), s.stick.clone(),
                    s.setpoint.clone(), s.mixerThrottle, s.gyro.clone(), s.gyroRaw.clone(), s.motorDuty.clone(), s.motorRpm.clone(), s.voltage,
                    s.current, s.baroAltitude, s.gpsFix, s.gpsSatellites, s.gpsX, s.gpsY, s.gpsZ, s.gpsVx, s.gpsVz, s.mode, s.airmode);
        }
    }

    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();
    final MoveReport report = new MoveReport();
    final List<List<Truth>> logs = new ArrayList<>();
    private List<Truth> current;
    private boolean armedBefore;
    double seconds;

    BlackboxFlight(DroneBuild build, PidLoopRate loop, FcListener recorder) {
        FlightControllerConfig config = new FlightControllerConfig();
        config.filters.pidLoop = loop;
        this.sim = new DroneSim(build, config, new SimTuning(), 4242L);
        this.sim.reset(0.0, 60.0, 0.0, 0.0);
        this.sim.setFcListener((sim, s) -> {
            if (s.armed) {
                if (!this.armedBefore) {
                    this.current = new ArrayList<>();
                    this.logs.add(this.current);
                }
                this.current.add(Truth.of(s));
            }
            this.armedBefore = s.armed;
            recorder.onFcSample(sim, s);
        });
    }

    void frame() {
        this.sim.step(FRAME_NANOS, this.input, this.env, this.result);
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
        this.seconds += FRAME_NANOS * 1e-9;
    }

    void arm() {
        this.input.throttle = 0.0;
        this.input.armSwitch = false;
        this.frame();
        this.input.armSwitch = true;
        this.frame();
    }

    void disarm() {
        this.input.armSwitch = false;
        this.frame();
        this.frame();
    }

    void fly(double seconds) {
        double end = this.seconds + seconds;
        while (this.seconds < end) {
            double t = this.seconds;
            double phase = t % 4.0;
            this.input.mode = StabMode.ACRO;
            this.input.throttle = 0.42 + 0.12 * Math.sin(t * 1.3);
            this.input.roll = phase < 0.4 ? 0.8 : (phase > 2.0 && phase < 2.3 ? -0.6 : 0.15 * Math.sin(t * 7.0));
            this.input.pitch = 0.2 * Math.sin(t * 2.1) + (phase > 1.0 && phase < 1.25 ? 0.7 : 0.0);
            this.input.yaw = 0.3 * Math.sin(t * 0.7);
            this.frame();
        }
    }
}
