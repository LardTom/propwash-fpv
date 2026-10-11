package de.lardtom.propwash.api;

import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TuneParam;
import de.lardtom.propwash.video.VideoChannels;
import de.lardtom.propwash.video.VtxSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class DocTablesTest {
    private static final String TUNE_BEGIN = "<!-- tune-keys:begin -->";
    private static final String TUNE_END = "<!-- tune-keys:end -->";
    private static final String WARNINGS_BEGIN = "<!-- build-warnings:begin -->";
    private static final String WARNINGS_END = "<!-- build-warnings:end -->";

    private static Path repository() {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            if (Files.isRegularFile(candidate.resolve("docs/design/parts-api.md"))) {
                return candidate;
            }
        }
        fail("Repository nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static String block(Path file, String begin, String end) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        if (from < 0 || to < from) {
            fail(file + ": Markierungen " + begin + " … " + end + " fehlen");
        }
        return text.substring(from + begin.length(), to).strip();
    }

    private static void compare(Path file, String begin, String end, String expected, String name) throws IOException {
        Path out = repository().resolve("common/build/docs/" + name);
        Files.createDirectories(out.getParent());
        Files.writeString(out, expected + "\n", StandardCharsets.UTF_8);
        assertEquals(expected, block(file, begin, end), file + " weicht ab; erwarteter Block steht in " + out.toAbsolutePath());
    }

    private static String number(double value) {
        return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }

    private static String codes(Enum<?>[] values) {
        List<String> parts = new ArrayList<>();
        for (Enum<?> value : values) {
            parts.add(value.ordinal() + " = `" + value.name().toLowerCase(Locale.ROOT) + "`");
        }
        return String.join(", ", parts);
    }

    private static String row(String key, String kind, String range, String step, String unit, String values) {
        return "| `" + key + "` | `" + TuneGroup.of(key).name() + "` | " + kind + " | " + range + " | " + step + " | " + unit + " | " + values + " |";
    }

    private static String row(TuneParam param) {
        String key = param.key();
        List<String> notes = new ArrayList<>();
        return switch (param.kind()) {
            case ENUM -> row(key, "Auswahl", number(param.min()) + "–" + number(param.max()), "1", "–",
                    codes((Enum<?>[]) param.enumType().getEnumConstants()) + derived(key));
            case BOOL -> row(key, "Schalter", "0–1", "1", "–", "0 = aus, 1 = an" + derived(key));
            case NUMBER -> {
                if (param.allowOff()) {
                    notes.add("0 = aus");
                }
                if (param.displayScale() != 1.0) {
                    notes.add("Anzeige ×" + number(param.displayScale()));
                }
                String unit = param.unit().isEmpty() ? "–" : param.displayScale() != 1.0 ? "Anteil (Anzeige " + param.unit() + ")" : param.unit();
                String values = String.join(", ", notes) + derived(key);
                yield row(key, "Zahl", number(param.min()) + "–" + number(param.max()), number(param.step()), unit,
                        values.isEmpty() ? "–" : values.startsWith("; ") ? values.substring(2) : values);
            }
        };
    }

    private static String derived(String key) {
        if (TuneMapping.ARM_MAX_ANGLE.equals(key)) {
            return "; Standard " + number(FlightControllerConfig.ARM_ANGLE_DEFAULT) + "; " + number(FlightControllerConfig.ARM_ANGLE_MAX)
                    + " = scharf schalten in jeder Lage, auch in der Luft und kopfüber (wie `small_angle` in Betaflight)";
        }
        if (TuneMapping.pidAxisKeys().contains(key)) {
            return "; aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1)";
        }
        return TuneKeys.isDerivedKey(key) ? "; Standard aus dem Bauplan abgeleitet" : "";
    }

    static String tuneKeyTable() {
        Set<String> uiKeys = new HashSet<>(TuneKeys.params().keySet());
        assertEquals(new HashSet<>(TuneMapping.keys()), uiKeys, "TuneKeys und TuneMapping kennen verschiedene Schlüssel");
        List<String> lines = new ArrayList<>();
        lines.add("| Schlüssel | Gruppe | Art | Bereich | Schritt | Einheit | Werte und Hinweise |");
        lines.add("|---|---|---|---|---|---|---|");
        for (TuneParam param : TuneKeys.params().values()) {
            lines.add(row(param));
        }
        lines.add(row(TuneMapping.CAMERA_UPTILT, "Zahl", number(TuneMapping.CAMERA_UPTILT_MIN) + "–" + number(TuneMapping.CAMERA_UPTILT_MAX), "–",
                "°", "Standard aus `camera.tilt_deg` des Rahmens"));
        lines.add(row(LedSettings.MODE, "Auswahl", "0–" + (LedMode.values().length - 1), "1", "–", codes(LedMode.values())));
        lines.add(row(LedSettings.COLOR, "Auswahl", "0–" + (LedColor.values().length - 1), "1", "–", codes(LedColor.values())));
        lines.add(row(LedSettings.BRIGHTNESS, "Zahl", LedSettings.MIN_BRIGHTNESS + "–" + LedSettings.MAX_BRIGHTNESS,
                String.valueOf(LedSettings.BRIGHTNESS_STEP), "%", "–"));
        lines.add(row(LedSettings.HEADLIGHT, "Schalter", "0–1", "1", "–", "0 = aus, 1 = an"));
        lines.add(row(VtxSettings.CHANNEL, "Zahl", "0–" + (VideoChannels.MAX_COUNT - 1), "1", "–",
                "Index in der Kanalliste des Videosystems (analog R1–R8, F1–F8; digital CH1–CH8); fehlt = automatisch"));
        lines.add(row(VtxSettings.MANUAL, "Schalter", "0–1", "1", "–", "1 = Kanal fest eingestellt"));
        return String.join("\n", lines);
    }

    static String warningList() {
        List<String> keys = new ArrayList<>();
        for (String warning : BuildStats.WARNINGS) {
            keys.add("`" + warning + "`");
        }
        return String.join(", ", keys);
    }

    @Test
    void tuneKeyCatalogueInTheContractIsComplete() throws IOException {
        compare(repository().resolve("docs/design/parts-api.md"), TUNE_BEGIN, TUNE_END, tuneKeyTable(), "tune-keys.md");
    }

    @Test
    void everyTuneKeyIsKnownToTheFlightController() {
        for (String key : TuneKeys.params().keySet()) {
            assertEquals(true, TuneMapping.isKnownKey(key), key);
        }
        for (String key : List.of(TuneMapping.CAMERA_UPTILT, LedSettings.MODE, LedSettings.COLOR, LedSettings.BRIGHTNESS, LedSettings.HEADLIGHT,
                VtxSettings.CHANNEL, VtxSettings.MANUAL)) {
            assertEquals(true, TuneMapping.isKnownKey(key), key);
        }
        assertEquals(LedSettings.KEYS.size() + VtxSettings.KEYS.size() + 1 + TuneMapping.keys().size(),
                tuneKeyTable().lines().count() - 2);
    }

    @Test
    void warningKeysAreListedInContractAndGuide() throws IOException {
        compare(repository().resolve("docs/design/parts-api.md"), WARNINGS_BEGIN, WARNINGS_END, warningList(), "build-warnings.md");
        compare(repository().resolve("docs/api/addon-guide.md"), WARNINGS_BEGIN, WARNINGS_END, warningList(), "build-warnings.md");
    }
}
