package de.lardtom.propwash.calibration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class LogData {
    public static final Set<String> WANTED = Set.of("time", "rcCommand[0]", "rcCommand[1]", "rcCommand[2]", "rcCommand[3]", "setpoint[0]",
            "setpoint[1]", "setpoint[2]", "vbatLatest", "amperageLatest", "baroAlt", "gyroADC[0]", "gyroADC[1]", "gyroADC[2]", "accSmooth[0]",
            "accSmooth[1]", "accSmooth[2]", "motor[0]", "motor[1]", "motor[2]", "motor[3]", "motor[4]", "eRPM[0]", "eRPM[1]", "eRPM[2]",
            "eRPM[3]", "imuQuaternion[0]", "imuQuaternion[1]", "imuQuaternion[2]", "GPS_speed", "GPS_numSat", "flightModeFlags");
    static final String MODES = "flightModeFlags";
    static final long SELF_LEVEL_BOXES = (1L << 1) | (1L << 2);

    private final Map<String, double[]> columns = new LinkedHashMap<>();
    private final int rows;

    public LogData(int rows) {
        this.rows = rows;
    }

    public int rows() {
        return this.rows;
    }

    public void put(String name, double[] values) {
        if (values.length != this.rows) {
            throw new IllegalArgumentException(name + ": " + values.length + " statt " + this.rows);
        }
        this.columns.put(name, values);
    }

    public boolean has(String name) {
        return this.columns.containsKey(name);
    }

    public double[] column(String name) {
        double[] v = this.columns.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Spalte fehlt: " + name);
        }
        return v;
    }

    public Set<String> names() {
        return this.columns.keySet();
    }

    static String bareName(String header) {
        return header.replaceAll("\\([^)]*\\)", "").trim();
    }

    public static LogData readCsv(Path csv) throws IOException {
        InputStream raw = Files.newInputStream(csv);
        InputStream source = csv.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(source, StandardCharsets.ISO_8859_1), 1 << 16)) {
            String head = in.readLine();
            if (head == null) {
                return new LogData(0);
            }
            String[] names = head.split(",");
            int[] slot = new int[names.length];
            List<String> kept = new ArrayList<>();
            for (int k = 0; k < names.length; k++) {
                String n = bareName(names[k]);
                slot[k] = WANTED.contains(n) && !kept.contains(n) ? kept.size() : -1;
                if (slot[k] >= 0) {
                    kept.add(n);
                }
            }
            int width = kept.size();
            int modes = kept.indexOf(MODES);
            double[][] buf = new double[width][1 << 16];
            int count = 0;
            String line;
            while ((line = in.readLine()) != null) {
                if (count == buf[0].length) {
                    for (int c = 0; c < width; c++) {
                        buf[c] = Arrays.copyOf(buf[c], count * 2);
                    }
                }
                int col = 0;
                int start = 0;
                int len = line.length();
                boolean complete = true;
                for (int pos = 0; pos <= len; pos++) {
                    if (pos == len || line.charAt(pos) == ',') {
                        if (col < slot.length && slot[col] >= 0) {
                            buf[slot[col]][count] = slot[col] == modes ? selfLevel(line, start, pos) : parse(line, start, pos);
                        }
                        col++;
                        start = pos + 1;
                    }
                }
                if (col < names.length) {
                    complete = false;
                }
                if (complete) {
                    count++;
                }
            }
            LogData out = new LogData(count);
            for (int c = 0; c < width; c++) {
                out.put(kept.get(c), Arrays.copyOf(buf[c], count));
            }
            return out;
        }
    }

    static double selfLevel(String s, int from, int to) {
        double boxes = parse(s, from, to);
        if (Double.isNaN(boxes)) {
            return Double.NaN;
        }
        return (((long) boxes) & SELF_LEVEL_BOXES) != 0 ? 1.0 : 0.0;
    }

    static double parse(String s, int from, int to) {
        while (from < to && s.charAt(from) == ' ') {
            from++;
        }
        while (to > from && s.charAt(to - 1) == ' ') {
            to--;
        }
        if (from == to) {
            return Double.NaN;
        }
        boolean negative = false;
        int i = from;
        if (s.charAt(i) == '-') {
            negative = true;
            i++;
        }
        long whole = 0;
        int digits = 0;
        for (; i < to && Character.isDigit(s.charAt(i)); i++) {
            whole = whole * 10 + (s.charAt(i) - '0');
            digits++;
        }
        double value = whole;
        if (i < to && s.charAt(i) == '.') {
            i++;
            double scale = 0.1;
            for (; i < to && Character.isDigit(s.charAt(i)); i++) {
                value += (s.charAt(i) - '0') * scale;
                scale *= 0.1;
                digits++;
            }
        }
        if (i != to || digits == 0) {
            try {
                return Double.parseDouble(s.substring(from, to));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return negative ? -value : value;
    }
}
