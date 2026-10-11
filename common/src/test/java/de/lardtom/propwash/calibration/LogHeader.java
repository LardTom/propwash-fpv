package de.lardtom.propwash.calibration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LogHeader {
    static final int DSHOT_LOW = 48;
    static final int DSHOT_HIGH = 2047;
    static final int PWM_LOW = 1000;
    static final int PWM_HIGH = 2000;
    static final int FIRST_DSHOT_PROTOCOL = 5;
    static final int LAST_DSHOT_PROTOCOL = 8;
    static final double ACC_LPF_SCALED_ABOVE = 500.0;
    static final double DEFAULT_ACC_LPF_HZ = 25.0;
    static final String[] RATE_TYPES = {"BETAFLIGHT", "RACEFLIGHT", "KISS", "ACTUAL", "QUICK"};

    private final Map<String, String> values;

    public LogHeader(Map<String, String> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public static LogHeader readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
        Map<String, String> map = new LinkedHashMap<>();
        for (int k = 1; k < lines.size(); k++) {
            String line = lines.get(k);
            int comma = line.indexOf(',');
            if (comma <= 0) {
                continue;
            }
            String value = line.substring(comma + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(line.substring(0, comma).trim(), value);
        }
        return new LogHeader(map);
    }

    public Map<String, String> values() {
        return this.values;
    }

    public boolean has(String key) {
        return this.values.containsKey(key);
    }

    public String text(String key, String fallback) {
        String v = this.values.get(key);
        return v == null ? fallback : v;
    }

    public int[] ints(String key) {
        String v = this.values.get(key);
        if (v == null || v.isBlank()) {
            return new int[0];
        }
        String[] parts = v.split(",");
        int[] out = new int[parts.length];
        for (int k = 0; k < parts.length; k++) {
            try {
                out[k] = Integer.parseInt(parts[k].trim());
            } catch (NumberFormatException e) {
                return new int[0];
            }
        }
        return out;
    }

    public int integer(String key, int fallback) {
        int[] v = this.ints(key);
        return v.length == 0 ? fallback : v[0];
    }

    public String firmware() {
        return this.text("Firmware revision", "?");
    }

    public String craft() {
        return this.text("Craft name", "");
    }

    public boolean betaflight() {
        return this.firmware().startsWith("Betaflight");
    }

    public int[] version() {
        String[] words = this.firmware().split(" ");
        if (words.length < 2) {
            return new int[]{0, 0, 0};
        }
        String[] parts = words[1].split("[.-]");
        int[] out = new int[3];
        for (int k = 0; k < 3 && k < parts.length; k++) {
            try {
                out[k] = Integer.parseInt(parts[k]);
            } catch (NumberFormatException e) {
                out[k] = 0;
            }
        }
        return out;
    }

    public String versionText() {
        int[] v = this.version();
        return v[0] + "." + v[1] + "." + v[2];
    }

    public boolean dshot() {
        int protocol = this.integer("motor_pwm_protocol", -1);
        if (protocol >= 0) {
            return protocol >= FIRST_DSHOT_PROTOCOL && protocol <= LAST_DSHOT_PROTOCOL;
        }
        int[] out = this.ints("motorOutput");
        return out.length > 0 && out[0] < PWM_LOW / 2;
    }

    public double motorLow() {
        return this.dshot() ? DSHOT_LOW : PWM_LOW;
    }

    public double motorHigh() {
        return this.dshot() ? DSHOT_HIGH : PWM_HIGH;
    }

    public int motorPoles() {
        return this.integer("motor_poles", 14);
    }

    public boolean bidirectional() {
        return this.integer("dshot_bidir", 0) == 1;
    }

    public double acc1G() {
        int v = this.integer("acc_1G", 0);
        return v > 0 ? v : Double.NaN;
    }

    public double accLpfHz() {
        int v = this.integer("acc_lpf_hz", -1);
        if (v <= 0) {
            return DEFAULT_ACC_LPF_HZ;
        }
        return v > ACC_LPF_SCALED_ABOVE ? v / 100.0 : v;
    }

    public double vbatScale() {
        int major = this.version()[0];
        return major >= 4 ? 0.01 : 0.1;
    }

    public double gyroScale() {
        String v = this.values.get("gyro_scale");
        if (v == null) {
            return 1.0;
        }
        try {
            return Float.intBitsToFloat((int) Long.parseLong(v.replace("0x", ""), 16));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public double idlePercent() {
        return this.integer("dshot_idle_value", 0) / 100.0;
    }

    public int motorKv() {
        return this.integer("motor_kv", 0);
    }

    public String ratesType() {
        int t = this.integer("rates_type", 0);
        return t >= 0 && t < RATE_TYPES.length ? RATE_TYPES[t] : "?";
    }

    public int[] rcRates() {
        return this.ints("rc_rates");
    }

    public int[] superRates() {
        return this.ints("rates");
    }

    public int[] rcExpo() {
        return this.ints("rc_expo");
    }

    public String pidText() {
        StringBuilder sb = new StringBuilder();
        for (String axis : new String[]{"rollPID", "pitchPID", "yawPID"}) {
            int[] v = this.ints(axis);
            if (v.length >= 3) {
                if (!sb.isEmpty()) {
                    sb.append(" / ");
                }
                sb.append(v[0]).append('-').append(v[1]).append('-').append(v[2]);
            }
        }
        int[] ff = this.ints("ff_weight");
        if (ff.length >= 2) {
            sb.append(String.format(Locale.ROOT, ", FF %d/%d", ff[0], ff[1]));
        }
        return sb.toString();
    }

    public String rateText() {
        int[] rc = this.rcRates();
        int[] sr = this.superRates();
        int[] ex = this.rcExpo();
        if (rc.length < 1 || sr.length < 1) {
            return "?";
        }
        int e = ex.length > 0 ? ex[0] : 0;
        return String.format(Locale.ROOT, "%s %d/%d/%d", this.ratesType(), rc[0], sr[0], e);
    }

    public double loopHz() {
        int looptime = this.integer("looptime", 0);
        int denom = Math.max(1, this.integer("pid_process_denom", 1));
        return looptime > 0 ? 1e6 / looptime / denom : Double.NaN;
    }
}
