package de.lardtom.propwash.sim;

import de.lardtom.propwash.testutil.TestPaths;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

final class GoldenFile {
    static final String UPDATE = "propwash.goldenUpdate";

    private GoldenFile() {
    }

    enum Arch {
        AARCH64, X86_64, OTHER;

        static Arch of(String osArch) {
            return switch (osArch == null ? "" : osArch.strip().toLowerCase(Locale.ROOT)) {
                case "aarch64", "arm64" -> AARCH64;
                case "x86_64", "amd64" -> X86_64;
                default -> OTHER;
            };
        }

        static Arch current() {
            return of(System.getProperty("os.arch"));
        }
    }

    record Entry(String reference, String x86) {
        String expected(Arch arch) {
            return arch == Arch.X86_64 && this.x86 != null ? this.x86 : this.reference;
        }
    }

    static String hash(CharSequence text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void bits(StringBuilder out, double value) {
        out.append(Long.toHexString(Double.doubleToRawLongBits(value))).append(',');
    }

    static Map<String, Entry> parse(List<String> lines) {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.strip().split(" +");
            if (f.length < 2 || f.length > 3) {
                throw new IllegalStateException("Golden-Zeile braucht Schlüssel, Wert und höchstens einen Wert für x86_64: " + line);
            }
            out.put(f[0], new Entry(f[1], f.length == 3 ? f[2] : null));
        }
        return out;
    }

    static String format(Map<String, Entry> entries) {
        StringBuilder text = new StringBuilder();
        entries.forEach((key, entry) -> {
            text.append(key).append(' ').append(entry.reference());
            if (entry.x86() != null) {
                text.append(' ').append(entry.x86());
            }
            text.append('\n');
        });
        return text.toString();
    }

    static List<String> changed(Map<String, Entry> expected, Map<String, String> actual, Arch arch) {
        List<String> changed = new ArrayList<>();
        expected.forEach((key, entry) -> {
            if (!entry.expected(arch).equals(actual.get(key))) {
                changed.add(key);
            }
        });
        return changed;
    }

    static Map<String, Entry> updated(Map<String, Entry> expected, Map<String, String> actual, Arch arch) {
        Map<String, Entry> out = new LinkedHashMap<>();
        actual.forEach((key, hash) -> {
            Entry old = expected.get(key);
            if (arch == Arch.X86_64) {
                out.put(key, new Entry(old.reference(), hash.equals(old.reference()) ? null : hash));
            } else {
                out.put(key, new Entry(hash, old != null && old.reference().equals(hash) ? old.x86() : null));
            }
        });
        return out;
    }

    static void verify(String resource, Map<String, String> actual, String message) {
        Arch arch = Arch.current();
        String where = " (os.arch " + System.getProperty("os.arch") + ")";
        Optional<List<String>> lines = TestPaths.resourceLines(resource);
        if (lines.isEmpty()) {
            Map<String, Entry> fresh = new LinkedHashMap<>();
            actual.forEach((key, hash) -> fresh.put(key, new Entry(hash, null)));
            fail(arch == Arch.AARCH64 ? "Golden-Datei fehlte. " + TestPaths.writeGolden(resource, format(fresh))
                    : "Golden-Datei " + resource + " fehlt; die erste Spalte entsteht nur auf aarch64" + where);
        }
        Map<String, Entry> expected = parse(lines.get());
        if (arch == Arch.OTHER) {
            assertEquals(expected.keySet(), actual.keySet());
            abort("Bit-Vergleich von " + resource + " nur auf aarch64 und x86_64" + where);
        }
        List<String> changed = changed(expected, actual, arch);
        if (!changed.isEmpty() && Boolean.getBoolean(UPDATE)) {
            if (arch == Arch.X86_64 && !expected.keySet().equals(actual.keySet())) {
                fail("Schlüssel von " + resource + " weichen ab; erst auf aarch64 neu schreiben, dann die Spalte für x86_64");
            }
            fail(changed.size() + " Werte für " + arch + " geändert. " + TestPaths.writeGolden(resource, format(updated(expected, actual, arch))));
        }
        assertEquals(List.of(), changed, message + where + "; neu schreiben mit -D" + UPDATE + "=true");
        assertEquals(expected.keySet(), actual.keySet());
    }
}
