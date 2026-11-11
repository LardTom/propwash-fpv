package de.lardtom.propwash.drone.client.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BatteryPlugTextsTest {
    private static final String DREHWELT = "common/src/devtest/java/de/lardtom/propwash/dev/client/DrehweltSelfTests.java";
    private static final String FIXES = "docs/design/0.6.0/fixes-polish.md";
    private static final String WEBSITE = "docs/design/0.6.0/website-jmp.md";
    private static final String VARIANT = "Variante 2 gilt";

    private static Path file(String name) {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            Path path = candidate.resolve(name);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        fail(name + " nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static List<String> lines(String name) throws IOException {
        return Files.readAllLines(file(name), StandardCharsets.UTF_8);
    }

    private static List<String> within(String where, List<String> lines, String start, String... ends) {
        int from = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(start)) {
                from = i;
                break;
            }
        }
        assertTrue(from >= 0, where + ": Abschnitt „" + start + "“ fehlt");
        List<String> out = new ArrayList<>();
        out.add(lines.get(from));
        for (int i = from + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (Arrays.stream(ends).anyMatch(line::startsWith)) {
                break;
            }
            out.add(line);
        }
        return out;
    }

    private static String flat(List<String> lines) {
        return String.join(" ", lines.stream().map(String::strip).toList()).replaceAll("\\s+", " ");
    }

    private static void check(List<String> problems, String where, String text, List<String> wanted, List<String> stale) {
        for (String phrase : wanted) {
            if (!text.contains(phrase)) {
                problems.add(where + ": fehlt „" + phrase + "“");
            }
        }
        for (String phrase : stale) {
            if (text.contains(phrase)) {
                problems.add(where + ": beschreibt noch „" + phrase + "“");
            }
        }
    }

    @Test
    void changelogSaysPlugsAndLeadsAreHidden() throws IOException {
        List<String> problems = new ArrayList<>();
        String text = flat(within("CHANGELOG.md", lines("CHANGELOG.md"), "## [0.6.0]", "## "));
        check(problems, "CHANGELOG.md [0.6.0]", text, List.of("no longer show plugs or cables"),
                List.of("sits in the drone's plug", "lead along the frame"));
        assertEquals(List.of(), problems, "Seit PLUG-1 gilt Variante 2: mit Akku keine Stecker und keine Kabel an der Drohne");
    }

    @Test
    void releaseNotesSayPlugsAndLeadsAreHidden() throws IOException {
        List<String> problems = new ArrayList<>();
        List<String> release = within("docs/RELEASE-NOTES.md", lines("docs/RELEASE-NOTES.md"), "## 0.6.0", "## ");
        String text = flat(within("docs/RELEASE-NOTES.md 0.6.0", release, "### Akku gesteckt", "### ", "## "));
        check(problems, "docs/RELEASE-NOTES.md 0.6.0 „Akku gesteckt“", text, List.of("keine Stecker und Kabel"),
                List.of("steckt jetzt im Stecker der Drohne", "läuft das Kabel"));
        assertEquals(List.of(), problems, "Seit PLUG-1 gilt Variante 2: mit Akku keine Stecker und keine Kabel an der Drohne");
    }

    @Test
    void foundationAndArchitectureSayPlugsAndLeadsAreHidden() throws IOException {
        List<String> problems = new ArrayList<>();
        String foundation = flat(within("docs/FOUNDATION.md", lines("docs/FOUNDATION.md"), "### Akku gesteckt", "#"));
        check(problems, "docs/FOUNDATION.md „Akku gesteckt“", foundation, List.of("keine Stecker und keine Kabel"),
                List.of("Kabelführung", "Steckerpaar"));
        String architecture = flat(within("docs/ARCHITECTURE.md", lines("docs/ARCHITECTURE.md"), "- **Akku gesteckt**", "- ", "#"));
        check(problems, "docs/ARCHITECTURE.md „Akku gesteckt“", architecture, List.of("keine Stecker und keine Kabel"),
                List.of("Kabelführung", "Steckerpaar"));
        assertEquals(List.of(), problems, "Seit PLUG-1 gilt Variante 2: mit Akku keine Stecker und keine Kabel an der Drohne");
    }

    @Test
    void designDocsNoteThatVariant2Applies() throws IOException {
        List<String> problems = new ArrayList<>();
        List<String> fixes = lines(FIXES);
        check(problems, FIXES + " §3.B", flat(within(FIXES, fixes, "### 3.B ", "### ")), List.of(VARIANT), List.of());
        check(problems, FIXES, flat(fixes), List.of("no longer show plugs or cables", "keine Stecker und Kabel mehr"),
                List.of("sits in the drone's plug", "steckt jetzt im Stecker der Drohne"));
        List<String> website = lines(WEBSITE);
        check(problems, WEBSITE + " §3.5.6", flat(within(WEBSITE, website, "#### 3.5.6 ", "#")), List.of(VARIANT), List.of());
        check(problems, WEBSITE + " §3.12.3", flat(within(WEBSITE, website, "#### 3.12.3 ", "#")), List.of(VARIANT), List.of());
        check(problems, WEBSITE + " A.2", flat(within(WEBSITE, website, "**A.2 ", "**A.3 ", "#")), List.of(VARIANT), List.of());
        assertEquals(List.of(), problems, "Design-Dokumente brauchen den Vermerk, dass seit PLUG-1 Variante 2 gilt");
    }

    @Test
    void drehweltNamesTheHiddenPlugs() throws IOException {
        List<String> problems = new ArrayList<>();
        check(problems, DREHWELT, flat(lines(DREHWELT)), List.of("\"Akku gesteckt ohne Stecker und Kabel\""), List.of("XT60"));
        String testing = flat(within("docs/testing.md", lines("docs/testing.md"), "### Drehwelt", "### ", "## "));
        check(problems, "docs/testing.md „Drehwelt“", testing, List.of("Akku gesteckt ohne Stecker und Kabel"), List.of("XT60"));
        assertEquals(List.of(), problems, "Platz R6 der Drehwelt zeigt seit PLUG-1 einen gesteckten Akku ohne Stecker und Kabel");
    }

    @Test
    void storeTextsDoNotPromiseVisiblePlugs() throws IOException {
        List<String> problems = new ArrayList<>();
        check(problems, "docs/modrinth/description.md", flat(lines("docs/modrinth/description.md")),
                List.of("without plugs or leads"), List.of("plugged batteries"));
        check(problems, "docs/modrinth/description_de.md", flat(lines("docs/modrinth/description_de.md")),
                List.of("ohne Stecker und Kabel"), List.of("gesteckte Akkus,"));
        check(problems, "docs/modrinth/listing.md", flat(lines("docs/modrinth/listing.md")),
                List.of("no plugs or leads"), List.of("(plugged battery)"));
        assertEquals(List.of(), problems, "Seit PLUG-1 gilt Variante 2: mit Akku keine Stecker und keine Kabel an der Drohne");
    }
}
