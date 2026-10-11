package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.AxisPid;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FilterSettings;
import de.lardtom.propwash.sim.PidProfile;
import de.lardtom.propwash.sim.RateCurve;
import de.lardtom.propwash.sim.RateProfile;
import de.lardtom.propwash.sim.RatesType;
import de.lardtom.propwash.sim.TpaMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlackboxSetup {
    public static final String COMPATIBLE_FIRMWARE = "Betaflight 4.5.1";
    private static volatile String producer = "Propwash";
    public static final int GYRO_LOOPTIME_US = 1_000_000 / DroneSim.PHYSICS_HZ;
    public static final int I_INTERVAL_MS = 32;
    public static final int SLOW_INTERVAL_I_FRAMES = 256;
    static final double CELL_DETECT_MARGIN_V = 0.1;

    private final int pidLoopDivider;
    private final int pDenominator;
    private final int vbatReference;
    private final boolean[] dTerm;
    private final List<String[]> lines;

    private BlackboxSetup(int pidLoopDivider, int pDenominator, int vbatReference, boolean[] dTerm, List<String[]> lines) {
        this.pidLoopDivider = pidLoopDivider;
        this.pDenominator = pDenominator;
        this.vbatReference = vbatReference;
        this.dTerm = dTerm;
        this.lines = lines;
    }

    public static BlackboxSetup of(String craftName, String startedAt, int pidLoopDivider, int pDenominator, double voltage,
                                   PidProfile pid, RateProfile rates, FilterSettings filters, AirframeSpec airframe) {
        int divider = Math.max(1, pidLoopDivider);
        int denominator = Math.max(1, pDenominator);
        int vbatRef = clamp((int) Math.round(voltage * 100.0), 0, 0xFFFF);
        boolean[] d = {pid.roll.d > 0.0, pid.pitch.d > 0.0, pid.yaw.d > 0.0};
        List<String[]> out = new ArrayList<>();
        int iInterval = iInterval(divider);
        line(out, "Firmware type", "Cleanflight");
        line(out, "Firmware revision", firmwareRevision());
        line(out, "Firmware date", firmwareDate(startedAt));
        line(out, "Board information", "PWSH PROPWASH");
        line(out, "Log start datetime", startedAt);
        line(out, "Craft name", craftName(craftName));
        line(out, "I interval", iInterval);
        line(out, "P interval", "1/" + denominator);
        line(out, "P ratio", Math.max(1, iInterval / denominator));
        line(out, "minthrottle", 1070);
        line(out, "maxthrottle", 2000);
        line(out, "gyro_scale", "0x3f800000");
        line(out, "motorOutput", BlackboxSample.DSHOT_MIN + "," + BlackboxSample.DSHOT_MAX);
        line(out, "motor_kv", (int) Math.round(airframe.kv * 60.0 / (2.0 * Math.PI)));
        line(out, "acc_1G", 2048);
        line(out, "vbat_scale", 110);
        line(out, "vbatcellvoltage", centi(airframe.chemistry.cutoffCellVoltage) + "," + centi(airframe.chemistry.emptyCellVoltage) + ","
                + centi(airframe.chemistry.fullCellVoltage + CELL_DETECT_MARGIN_V));
        line(out, "vbatref", vbatRef);
        line(out, "looptime", GYRO_LOOPTIME_US);
        line(out, "gyro_sync_denom", 1);
        line(out, "pid_process_denom", divider);
        line(out, "thr_mid", percent(pid.throttleMid));
        line(out, "thr_expo", percent(pid.throttleExpo));
        line(out, "tpa_mode", pid.tpaMode == TpaMode.PD ? 0 : 1);
        line(out, "tpa_rate", (int) Math.round(pid.tpaRatePercent));
        line(out, "tpa_breakpoint", 1000 + (int) Math.round(pid.tpaBreakpoint * 1000.0));
        line(out, "mixer_type", "LEGACY");
        line(out, "rc_rates", rateTriple(rates, Part.RC_RATE));
        line(out, "rc_expo", rateTriple(rates, Part.EXPO));
        line(out, "rates", rateTriple(rates, Part.RATE));
        line(out, "rate_limits", "1998,1998,1998");
        line(out, "rollPID", pidTriple(pid.roll));
        line(out, "pitchPID", pidTriple(pid.pitch));
        line(out, "yawPID", pidTriple(pid.yaw));
        line(out, "levelPID", (int) Math.round(pid.angleStrength) + "," + (int) Math.round(pid.horizonStrength) + ","
                + (int) Math.round(pid.horizonStickTransitionPercent));
        line(out, "magPID", 0);
        line(out, "dterm_lpf1_type", 0);
        line(out, "dterm_lpf1_static_hz", hz(filters.dtermLpf1Hz));
        line(out, "dterm_lpf1_dyn_hz", "0,0");
        line(out, "dterm_lpf1_dyn_expo", 0);
        line(out, "dterm_lpf2_type", 0);
        line(out, "dterm_lpf2_static_hz", hz(filters.dtermLpf2Hz));
        line(out, "yaw_lowpass_hz", 0);
        line(out, "dterm_notch_hz", 0);
        line(out, "dterm_notch_cutoff", 0);
        line(out, "iterm_windup", 100);
        line(out, "iterm_relax", switch (pid.itermRelax) {
            case OFF -> 0;
            case RP -> 1;
            case RPY -> 2;
        });
        line(out, "iterm_relax_type", 1);
        line(out, "iterm_relax_cutoff", hz(pid.itermRelaxCutoffHz));
        line(out, "pid_at_min_throttle", 1);
        line(out, "anti_gravity_gain", (int) Math.round(pid.antiGravityGain));
        line(out, "anti_gravity_cutoff_hz", 5);
        line(out, "anti_gravity_p_gain", 100);
        line(out, "ff_weight", (int) Math.round(pid.roll.ff) + "," + (int) Math.round(pid.pitch.ff) + "," + (int) Math.round(pid.yaw.ff));
        line(out, "feedforward_transition", 0);
        line(out, "feedforward_averaging", 0);
        line(out, "feedforward_smooth_factor", 0);
        line(out, "feedforward_jitter_factor", 0);
        line(out, "feedforward_boost", 0);
        line(out, "feedforward_max_rate_limit", (int) Math.round(pid.ffMaxRateLimitPercent));
        line(out, "angle_feedforward", (int) Math.round(pid.angleFeedforwardPercent));
        line(out, "angle_feedforward_smoothing_ms", 0);
        line(out, "angle_limit", (int) Math.round(pid.angleLimitDeg));
        line(out, "angle_earth_ref", (int) Math.round(pid.angleEarthRefPercent));
        line(out, "horizon_limit_degrees", (int) Math.round(pid.horizonLimitDeg));
        line(out, "horizon_delay_ms", 0);
        line(out, "acc_limit_yaw", 0);
        line(out, "acc_limit", 0);
        line(out, "pidsum_limit", (int) Math.round(pid.pidSumLimit));
        line(out, "pidsum_limit_yaw", (int) Math.round(pid.pidSumLimitYaw));
        line(out, "deadband", (int) Math.round(rates.rcDeadband * 500.0));
        line(out, "yaw_deadband", (int) Math.round(rates.yawDeadband * 500.0));
        line(out, "gyro_hardware_lpf", 0);
        line(out, "gyro_lpf1_type", 0);
        line(out, "gyro_lpf1_static_hz", hz(filters.gyroLpfHz));
        line(out, "gyro_lpf1_dyn_hz", "0,0");
        line(out, "gyro_lpf1_dyn_expo", 0);
        line(out, "gyro_lpf2_type", 0);
        line(out, "gyro_lpf2_static_hz", 0);
        line(out, "gyro_notch_hz", "0,0");
        line(out, "gyro_notch_cutoff", "0,0");
        line(out, "gyro_to_use", 0);
        line(out, "dyn_notch_max_hz", 0);
        line(out, "dyn_notch_count", 0);
        line(out, "dyn_notch_q", 0);
        line(out, "dyn_notch_min_hz", 0);
        line(out, "dshot_bidir", 1);
        line(out, "motor_poles", BlackboxSample.MOTOR_POLES);
        line(out, "rpm_filter_harmonics", 0);
        line(out, "rpm_filter_weights", "0,0,0");
        line(out, "rpm_filter_q", 0);
        line(out, "rpm_filter_min_hz", 0);
        line(out, "rpm_filter_fade_range_hz", 0);
        line(out, "rpm_filter_lpf_hz", 0);
        line(out, "acc_lpf_hz", 0);
        line(out, "acc_hardware", 0);
        line(out, "baro_hardware", 0);
        line(out, "gyro_cal_on_first_arm", 0);
        line(out, "airmode_activate_throttle", (int) Math.round(pid.airmodeStartThrottlePercent));
        line(out, "serialrx_provider", 9);
        line(out, "use_unsynced_pwm", 0);
        line(out, "motor_pwm_protocol", 7);
        line(out, "motor_pwm_rate", 480);
        line(out, "dshot_idle_value", (int) Math.round(pid.motorIdlePercent * 100.0));
        line(out, "debug_mode", BlackboxSample.DEBUG_MODE_GYRO_SCALED);
        line(out, "features", features(pid));
        line(out, "rc_smoothing", 1);
        line(out, "rc_smoothing_auto_factor", 0);
        line(out, "rc_smoothing_auto_factor_throttle", 0);
        line(out, "rc_smoothing_feedforward_cutoff", hz(pid.feedforwardSmoothingHz));
        line(out, "rc_smoothing_setpoint_cutoff", hz(filters.rcSmoothingHz));
        line(out, "rc_smoothing_throttle_cutoff", hz(filters.rcSmoothingHz));
        line(out, "rc_smoothing_debug_axis", 0);
        line(out, "rc_smoothing_active_cutoffs_ff_sp_thr", hz(pid.feedforwardSmoothingHz) + "," + hz(filters.rcSmoothingHz) + ","
                + hz(filters.rcSmoothingHz));
        line(out, "rc_smoothing_rx_smoothed", 0);
        line(out, "rates_type", rates.type == RatesType.ACTUAL ? 3 : 0);
        line(out, "fields_disabled_mask", 0);
        line(out, "blackbox_high_resolution", 0);
        line(out, "vbat_sag_compensation", (int) Math.round(pid.vbatSagCompensationPercent));
        line(out, "dyn_idle_min_rpm", 0);
        line(out, "motor_output_limit", (int) Math.round(pid.motorOutputLimitPercent));
        line(out, "throttle_limit_type", switch (pid.throttleLimitType) {
            case OFF -> 0;
            case SCALE -> 1;
            case CLIP -> 2;
        });
        line(out, "throttle_limit_percent", (int) Math.round(pid.throttleLimitPercent));
        line(out, "throttle_boost", 0);
        line(out, "throttle_boost_cutoff", 0);
        line(out, "thrust_linear", (int) Math.round(pid.thrustLinearPercent));
        return new BlackboxSetup(divider, denominator, vbatRef, d, List.copyOf(out));
    }

    public static int iInterval(int pidLoopDivider) {
        int pidHz = DroneSim.PHYSICS_HZ / Math.max(1, pidLoopDivider);
        return Math.max(1, I_INTERVAL_MS * pidHz / 1000);
    }

    public int pidLoopDivider() {
        return this.pidLoopDivider;
    }

    public int pDenominator() {
        return this.pDenominator;
    }

    public int iInterval() {
        return iInterval(this.pidLoopDivider);
    }

    public int loopPeriodUs() {
        return GYRO_LOOPTIME_US * this.pidLoopDivider;
    }

    public int vbatReference() {
        return this.vbatReference;
    }

    public boolean dTerm(int axis) {
        return this.dTerm[axis];
    }

    public List<String[]> lines() {
        return this.lines;
    }

    public String value(String name) {
        for (String[] line : this.lines) {
            if (line[0].equals(name)) {
                return line[1];
            }
        }
        return null;
    }

    static String craftName(String name) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            for (int i = 0; i < name.length() && sb.length() < 16; i++) {
                char c = name.charAt(i);
                if (c >= 0x20 && c < 0x7F) {
                    sb.append(c);
                }
            }
        }
        return sb.toString().trim();
    }

    private static int features(PidProfile pid) {
        int features = 1 << 7;
        if (pid.airmode) {
            features |= 1 << 22;
        }
        return features;
    }

    private enum Part {
        RC_RATE,
        EXPO,
        RATE
    }

    private static String rateTriple(RateProfile rates, Part part) {
        return rateValue(rates.type, rates.roll, part) + "," + rateValue(rates.type, rates.pitch, part) + "," + rateValue(rates.type, rates.yaw, part);
    }

    private static int rateValue(RatesType type, RateCurve curve, Part part) {
        if (type == RatesType.ACTUAL) {
            return switch (part) {
                case RC_RATE -> (int) Math.round(curve.centerRateDps / 10.0);
                case EXPO -> percent(curve.actualExpo);
                case RATE -> (int) Math.round(curve.maxRateDps / 10.0);
            };
        }
        return switch (part) {
            case RC_RATE -> percent(curve.rcRate);
            case EXPO -> percent(curve.rcExpo);
            case RATE -> percent(curve.superRate);
        };
    }

    private static String pidTriple(AxisPid axis) {
        return (int) Math.round(axis.p) + "," + (int) Math.round(axis.i) + "," + (int) Math.round(axis.d);
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100.0);
    }

    private static int centi(double volts) {
        return (int) Math.round(volts * 100.0);
    }

    private static int hz(double hz) {
        return Double.isFinite(hz) ? Math.max(0, (int) Math.round(hz)) : 0;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void line(List<String[]> out, String name, int value) {
        out.add(new String[]{name, Integer.toString(value)});
    }

    private static void line(List<String[]> out, String name, String value) {
        out.add(new String[]{name, value.replace('\n', ' ').replace('\r', ' ')});
    }

    public static void producerVersion(String version) {
        producer = version == null || version.isBlank() ? "Propwash" : "Propwash v" + version.strip();
    }

    public static String firmwareRevision() {
        return COMPATIBLE_FIRMWARE + " compatible (" + producer + ")";
    }

    static String firmwareDate(String startedAt) {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        try {
            int year = Integer.parseInt(startedAt.substring(0, 4));
            int month = Integer.parseInt(startedAt.substring(5, 7));
            int day = Integer.parseInt(startedAt.substring(8, 10));
            return String.format(Locale.ROOT, "%s %2d %04d %s", months[month - 1], day, year, startedAt.substring(11, 19));
        } catch (RuntimeException e) {
            return "Jan  1 2026 00:00:00";
        }
    }

    static String formatDateTime(int year, int month, int day, int hour, int minute, int second, int offsetMinutes) {
        int abs = Math.abs(offsetMinutes);
        return String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:%02d.000%s%02d:%02d", year, month, day, hour, minute, second,
                offsetMinutes < 0 ? "-" : "+", abs / 60, abs % 60);
    }
}
