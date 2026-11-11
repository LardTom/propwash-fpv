package de.lardtom.propwash.zone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ZonesOpenDocTest {
    private static final String DOC = "docs/zones-open.md";
    private static final Pattern ATTRIBUTION = Pattern.compile("(?U)des Besitzers|[Bb]esitzer-\\s*(Playtest|Bug|Nachricht|Rückmeldung|Wunsch|Frage|Entscheidung|Idee"
            + "|Zitat)|Besitzer:|des Owners|\\bOwner:");
    private static final Pattern QUOTE_MARK = Pattern.compile("[„“”]");
    private static final int WINDOW = 120;

    private static Path repository() {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            if (Files.isRegularFile(candidate.resolve(DOC))) {
                return candidate;
            }
        }
        fail("Repository nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    static List<String> exportFindings(String text) {
        List<String> findings = new ArrayList<>();
        Matcher match = ATTRIBUTION.matcher(text);
        while (match.find()) {
            int end = match.end();
            int stop = text.offsetByCodePoints(end, Math.min(WINDOW, text.codePointCount(end, text.length())));
            if (QUOTE_MARK.matcher(text.substring(end, stop)).find()) {
                int line = 1;
                for (int i = 0; i < match.start(); i++) {
                    if (text.charAt(i) == '\n') {
                        line++;
                    }
                }
                findings.add(line + ": " + text.substring(match.start(), Math.min(text.length(), end + 60)).replace('\n', ' '));
            }
        }
        return findings;
    }

    @Test
    void ruleRecognisesQuotedOwnerLine() {
        assertEquals(1, exportFindings("je Zone eine Zeile „Besitzer: …“ bzw. „Admin-Zone“").size());
        assertEquals(1, exportFindings("Wunsch des Besitzers: „Zonen für alle“").size());
        assertTrue(exportFindings("je Zone eine Zeile mit ihrem Besitzer bzw. „Admin-Zone“").isEmpty());
    }

    @Test
    void docPassesThePublicExportCheck() throws IOException {
        String text = Files.readString(repository().resolve(DOC), StandardCharsets.UTF_8);
        List<String> findings = exportFindings(text);
        assertTrue(findings.isEmpty(), DOC + " enthält Zeilen, die tools/public_export.py als Zitat meldet: " + findings);
    }
}
