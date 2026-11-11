package de.lardtom.propwash.barrier;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BarrierReleaseTextsTest {
    private static Path doc(String name) {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            Path file = candidate.resolve(name);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        fail(name + " nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static List<String> section(String name, String heading) throws IOException {
        List<String> lines = Files.readAllLines(doc(name), StandardCharsets.UTF_8);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(heading)) {
                start = i + 1;
                break;
            }
        }
        assertTrue(start > 0, name + ": Abschnitt „" + heading + "“ fehlt");
        List<String> out = new ArrayList<>();
        for (int i = start; i < lines.size() && !lines.get(i).startsWith("## "); i++) {
            out.add(lines.get(i));
        }
        return out;
    }

    private static List<String> bullets(List<String> lines) {
        List<String> items = new ArrayList<>();
        boolean open = false;
        for (String line : lines) {
            if (line.startsWith("- ")) {
                items.add(line.substring(2).strip());
                open = true;
            } else if (open && line.startsWith("  ") && !line.isBlank()) {
                items.set(items.size() - 1, items.getLast() + " " + line.strip());
            } else {
                open = false;
            }
        }
        return items;
    }

    private static String barrierBullet(String name, String heading, String marker) throws IOException {
        List<String> found = bullets(section(name, heading)).stream().filter(item -> item.contains(marker)).toList();
        assertEquals(1, found.size(), name + " " + heading + ": genau ein Punkt mit „" + marker + "“ erwartet, gefunden " + found);
        return found.getFirst();
    }

    private static void says(String where, String item, String... words) {
        String lower = item.toLowerCase(Locale.ROOT);
        for (String word : words) {
            assertTrue(lower.contains(word.toLowerCase(Locale.ROOT)), where + ": fehlt „" + word + "“ in „" + item + "“");
        }
    }

    private static void saysNot(String where, String item, String... phrases) {
        String lower = item.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            assertTrue(!lower.contains(phrase.toLowerCase(Locale.ROOT)), where + ": sagt noch „" + phrase + "“ in „" + item + "“");
        }
    }

    @Test
    void changelogSaysCreativeShowsTheOutlineWithoutABarrierInHand() throws IOException {
        String where = "CHANGELOG.md [0.6.0], Punkt zu den Barrieren";
        String item = barrierBullet("CHANGELOG.md", "## [0.6.0]", "*Drone-Passable Barrier*");
        saysNot(where, item, "show up only", "only while you hold", "only when you hold", "visible only");
        says(where, item, "creative mode", "selection outline", "empty hand", "icons", "while you hold", "survival");
    }

    @Test
    void releaseNotesSayCreativeShowsTheOutlineWithoutABarrierInHand() throws IOException {
        String where = "docs/RELEASE-NOTES.md 0.6.0, Punkt „Drohnen-Barrieren“";
        String item = barrierBullet("docs/RELEASE-NOTES.md", "## 0.6.0", "**Drohnen-Barrieren**");
        saysNot(where, item, "zeigen sich nur", "sichtbar nur", "nur sichtbar");
        says(where, item, "Kreativmodus", "Auswahlrahmen", "leerer Hand", "Symbole", "Barriere in der Hand", "Überleben");
    }
}
