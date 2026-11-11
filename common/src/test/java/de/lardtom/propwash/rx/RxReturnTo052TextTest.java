package de.lardtom.propwash.rx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RxReturnTo052TextTest {
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

    private static List<String> block(String name, String heading, String sub) throws IOException {
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
        boolean inside = sub == null;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("## ")) {
                break;
            }
            if (sub != null && line.startsWith("### ")) {
                inside = line.strip().equals(sub);
                continue;
            }
            if (inside) {
                out.add(line);
            }
        }
        assertTrue(!out.isEmpty(), name + ": Abschnitt „" + heading + (sub == null ? "" : " / " + sub) + "“ ist leer");
        return out;
    }

    private static List<String> bullets(List<String> lines) {
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("- ")) {
                items.add(line.substring(2).strip());
            } else if (!items.isEmpty() && line.startsWith("  ") && !line.isBlank()) {
                items.set(items.size() - 1, items.getLast() + " " + line.strip());
            }
        }
        return items;
    }

    private static Predicate<String> all(String... words) {
        return item -> {
            String lower = item.toLowerCase(Locale.ROOT);
            for (String word : words) {
                if (!lower.contains(word.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
            return true;
        };
    }

    @Test
    void changelogCompatibilitySaysRxBlocksDisappearBackOn052() throws IOException {
        List<String> items = bullets(block("CHANGELOG.md", "## [0.6.0]", "### Compatibility"));
        assertTrue(items.stream().anyMatch(all("RX block", "0.5.2", "disappear")),
                "CHANGELOG.md [0.6.0] Compatibility braucht einen Punkt: zurück auf 0.5.2 verschwinden RX-Blöcke; Punkte: " + items);
    }

    @Test
    void releaseNotesSayRxBlocksTurnToAirBackOn052() throws IOException {
        List<String> items = bullets(block("docs/RELEASE-NOTES.md", "## 0.6.0", "### Kompatibilität und Rückweg"));
        assertTrue(items.stream().anyMatch(all("RX-Blöcke", "0.5.2", "Luft")),
                "docs/RELEASE-NOTES.md 0.6.0 „Kompatibilität und Rückweg“ braucht den Hinweis: in 0.5.2 werden RX-Blöcke Luft; Punkte: " + items);
    }
}
