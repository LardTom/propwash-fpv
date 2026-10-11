package de.lardtom.propwash.ui;

import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RatesType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class TunePresets {
    public static final String CODE_PREFIX = "PWT1-";
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_PER_SECTION = 32;
    public static final int MAX_CODE_LENGTH = 2048;
    private static final int MAX_TEXT_BYTES = 8192;
    private static final String SECTION_LINE = "section";
    private static final String NAME_LINE = "name";

    public enum Section {
        RATES("rates"),
        PID("pid");

        private final String id;

        Section(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Optional<Section> byId(String id) {
            for (Section section : values()) {
                if (section.id.equals(id)) {
                    return Optional.of(section);
                }
            }
            return Optional.empty();
        }

        public boolean owns(String key) {
            return switch (this) {
                case RATES -> key.startsWith("rates.") && !key.equals(TuneKeys.RC_DEADBAND) && !key.equals(TuneKeys.YAW_DEADBAND);
                case PID -> key.startsWith("pid.") && !key.equals(TuneKeys.ARM_MAX_ANGLE) || key.startsWith("filters.") || key.startsWith("hold.");
            };
        }
    }

    public record Preset(String name, Map<String, Float> values) {
        public Preset {
            values = Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }

    public record Decoded(Section section, String name, Map<String, Float> values) {
    }

    private TunePresets() {
    }

    public static String cleanName(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder();
        clean.codePoints().filter(c -> !Character.isISOControl(c)).forEach(out::appendCodePoint);
        String result = out.toString();
        return result.length() > MAX_NAME_LENGTH ? result.substring(0, MAX_NAME_LENGTH).strip() : result;
    }

    public static boolean sameName(String a, String b) {
        return cleanName(a).toLowerCase(Locale.ROOT).equals(cleanName(b).toLowerCase(Locale.ROOT));
    }

    public static List<String> rateFields(RatesType type) {
        return type == RatesType.BETAFLIGHT ? List.of("rcRate", "superRate", "rcExpo") : List.of("centerRateDps", "maxRateDps", "actualExpo");
    }

    public static TreeMap<String, Float> capture(Section section, Map<String, Float> tune, Map<String, Float> defaults) {
        TreeMap<String, Float> out = new TreeMap<>();
        if (section == Section.RATES) {
            RatesType type = RateGraph.type(tune, defaults);
            out.put(TuneKeys.RATES_TYPE, (float) type.ordinal());
            for (Axis axis : Axis.values()) {
                for (String field : rateFields(type)) {
                    String key = TuneKeys.rateKey(axis, field);
                    out.put(key, (float) TuneEdits.value(tune, defaults, key));
                }
            }
            return out;
        }
        boolean manual = TuneEdits.manualPid(tune, defaults);
        for (Map.Entry<String, Float> e : tune.entrySet()) {
            String key = e.getKey();
            TuneParam param = TuneKeys.param(key);
            if (param == null || !section.owns(key) || e.getValue() == null || !Float.isFinite(e.getValue())) {
                continue;
            }
            boolean pidKey = TuneKeys.isPidTermKey(key) || key.equals(TuneKeys.PID_SOURCE);
            if (pidKey && !manual) {
                continue;
            }
            if (!pidKey && !TuneEdits.isModified(tune, defaults, key)) {
                continue;
            }
            out.put(key, (float) param.sanitize(e.getValue()));
        }
        if (manual) {
            out.put(TuneKeys.PID_SOURCE, TuneEdits.PID_MANUAL);
            for (String key : TuneKeys.pidTermKeys()) {
                out.put(key, (float) TuneEdits.value(tune, defaults, key));
            }
        }
        return out;
    }

    public static Map<String, Float> apply(Section section, Map<String, Float> tune, Map<String, Float> defaults, Map<String, Float> values) {
        Map<String, Float> out = TuneEdits.resetWhere(tune, section::owns);
        Map<String, Float> clean = clean(section, values);
        List<String> late = new ArrayList<>();
        List<String> order = new ArrayList<>();
        if (clean.containsKey(TuneKeys.RATES_TYPE)) {
            order.add(TuneKeys.RATES_TYPE);
        }
        if (clean.containsKey(TuneKeys.PID_SOURCE)) {
            order.add(TuneKeys.PID_SOURCE);
        }
        for (String key : clean.keySet()) {
            if (order.contains(key)) {
                continue;
            }
            if (key.endsWith(".maxRateDps")) {
                late.add(key);
            } else {
                order.add(key);
            }
        }
        order.addAll(late);
        for (String key : order) {
            out = TuneEdits.set(out, defaults, key, clean.get(key));
        }
        return out;
    }

    public static boolean matches(Section section, Map<String, Float> tune, Map<String, Float> defaults, Map<String, Float> values) {
        Map<String, Float> applied = apply(section, tune, defaults, values);
        for (String key : TuneKeys.params().keySet()) {
            if (!section.owns(key)) {
                continue;
            }
            TuneParam param = TuneKeys.param(key);
            double a = TuneEdits.value(applied, defaults, key);
            double b = TuneEdits.value(tune, defaults, key);
            if (param == null || !param.sameValue(a, b)) {
                return false;
            }
        }
        return true;
    }

    public static TreeMap<String, Float> clean(Section section, Map<String, Float> values) {
        TreeMap<String, Float> out = new TreeMap<>();
        if (values == null) {
            return out;
        }
        for (Map.Entry<String, Float> e : values.entrySet()) {
            String key = e.getKey();
            Float v = e.getValue();
            TuneParam param = key == null ? null : TuneKeys.param(key);
            if (param == null || v == null || !Float.isFinite(v) || !section.owns(key)) {
                continue;
            }
            out.put(key, (float) param.sanitize(v));
        }
        return out;
    }

    public static String encode(Section section, String name, Map<String, Float> values) {
        StringBuilder text = new StringBuilder();
        text.append(SECTION_LINE).append('=').append(section.id()).append('\n');
        text.append(NAME_LINE).append('=').append(cleanName(name).replace('\n', ' ').replace('=', ' ')).append('\n');
        for (Map.Entry<String, Float> e : clean(section, values).entrySet()) {
            text.append(e.getKey()).append('=').append(number(e.getValue())).append('\n');
        }
        byte[] raw = text.toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    public static Optional<Decoded> decode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String trimmed = code.strip();
        if (trimmed.length() > MAX_CODE_LENGTH || !trimmed.regionMatches(true, 0, CODE_PREFIX, 0, CODE_PREFIX.length())) {
            return Optional.empty();
        }
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(trimmed.substring(CODE_PREFIX.length()).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Optional<String> text = inflate(packed);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Section section = null;
        String name = "";
        Map<String, Float> values = new LinkedHashMap<>();
        for (String line : text.get().split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (key.equals(SECTION_LINE)) {
                section = Section.byId(value).orElse(null);
            } else if (key.equals(NAME_LINE)) {
                name = cleanName(value);
            } else {
                try {
                    values.put(key, Float.parseFloat(value));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        if (section == null) {
            return Optional.empty();
        }
        return Optional.of(new Decoded(section, name, clean(section, values)));
    }

    private static Optional<String> inflate(byte[] packed) {
        Inflater inflater = new Inflater(true);
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_TEXT_BYTES) {
                    return Optional.empty();
                }
            }
            if (!inflater.finished()) {
                return Optional.empty();
            }
        } catch (DataFormatException e) {
            return Optional.empty();
        } finally {
            inflater.end();
        }
        return Optional.of(out.toString(StandardCharsets.UTF_8));
    }

    private static String number(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E7F) {
            return Long.toString((long) value);
        }
        String text = String.format(Locale.ROOT, "%.5f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
