package de.lardtom.propwash.creative;

import de.lardtom.propwash.api.part.PartKind;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class CreativeShareCode {
    public static final String PREFIX = "PWD1-";
    public static final int MAX_CODE_LENGTH = 4096;
    private static final int MAX_TEXT_BYTES = 16384;
    private static final String BASE = "base.";

    private CreativeShareCode() {
    }

    public static String encode(CreativeSpec spec) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<PartKind, String> entry : spec.base().entrySet()) {
            text.append(BASE).append(entry.getKey().key()).append('=').append(entry.getValue()).append('\n');
        }
        for (Map.Entry<String, Double> entry : spec.values().entrySet()) {
            text.append(entry.getKey()).append('=').append(number(entry.getValue())).append('\n');
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
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    public static Optional<CreativeSpec> decode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String trimmed = code.strip();
        if (trimmed.length() > MAX_CODE_LENGTH || !trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Optional<String> text = inflate(packed);
        return text.flatMap(CreativeShareCode::parse);
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
        } catch (DataFormatException e) {
            return Optional.empty();
        } finally {
            inflater.end();
        }
        return Optional.of(out.toString(StandardCharsets.UTF_8));
    }

    private static Optional<CreativeSpec> parse(String text) {
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        TreeMap<String, Double> values = new TreeMap<>();
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                return Optional.empty();
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (key.startsWith(BASE)) {
                Optional<PartKind> kind = PartKind.byKey(key.substring(BASE.length()));
                if (kind.isPresent() && !value.isEmpty()) {
                    base.put(kind.get(), value.toLowerCase(Locale.ROOT));
                }
                continue;
            }
            try {
                double number = Double.parseDouble(value);
                if (Double.isFinite(number)) {
                    values.put(key, number);
                }
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(new CreativeSpec(base, values));
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
