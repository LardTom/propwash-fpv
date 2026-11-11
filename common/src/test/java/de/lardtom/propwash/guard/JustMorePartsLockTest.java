package de.lardtom.propwash.guard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JustMorePartsLockTest {
    private static final String JMP = "justmoreparts";
    private static final String REASON_DE = "Just More Parts 1.1.x passt nicht zu Propwash 0.6.0 – bitte auf 1.2.0 aktualisieren";

    private static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isRegularFile(candidate.resolve("LICENSE")) && Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        return fail("Wurzel des Repositorys nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
    }

    private static String read(String name) throws IOException {
        return Files.readString(root().resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void fabricBreaksJustMorePartsBelowTheFirstPreReleaseOf120() throws IOException {
        JsonObject mod = JsonParser.parseString(read("fabric/src/main/resources/fabric.mod.json")).getAsJsonObject();
        JsonObject breaks = mod.getAsJsonObject("breaks");
        assertEquals("<1.2.0-", breaks.get(JMP).getAsString(), "1.2.0-dev.N und 1.2.0-rc.N müssen laden");
        assertFalse(mod.getAsJsonObject("depends").has(JMP), "JMP bleibt optional");
    }

    @Test
    void neoforgeMarksJustMoreParts11Incompatible() throws IOException {
        List<Map<String, String>> dependencies = new ArrayList<>();
        Map<String, String> current = null;
        for (String line : read("neoforge/src/main/resources/META-INF/neoforge.mods.toml").lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[[")) {
                current = trimmed.startsWith("[[dependencies.") ? new LinkedHashMap<>() : null;
                if (current != null) {
                    dependencies.add(current);
                }
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (current != null && eq > 0) {
                String value = trimmed.substring(eq + 1).trim();
                current.put(trimmed.substring(0, eq).trim(), value.startsWith("\"") ? value.substring(1, value.length() - 1) : value);
            }
        }
        List<Map<String, String>> jmp = dependencies.stream().filter(d -> JMP.equals(d.get("modId"))).toList();
        assertEquals(1, jmp.size(), "genau ein Eintrag für Just More Parts");
        Map<String, String> entry = jmp.getFirst();
        assertEquals("incompatible", entry.get("type"));
        assertEquals("(,1.2.0-alpha)", entry.get("versionRange"), "1.2.0-dev.N liegt in Maven über 1.2.0-alpha");
        assertEquals("BOTH", entry.get("side"));
        String reason = entry.get("reason");
        assertTrue(reason != null && reason.contains(REASON_DE), "Grund: " + reason);
        assertTrue(reason.contains("please update to Just More Parts 1.2.0"), "Grund: " + reason);
    }

    @Test
    void legacyModeIsGone() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("de.lardtom.propwash.guard.LegacyAddons"));
    }
}
