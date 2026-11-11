package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProtocolVersionTextsTest {
    private static final Pattern MOD_VERSION = Pattern.compile("(?m)^version=([0-9]+\\.[0-9]+\\.[0-9]+)(\\S*)");
    private static final Pattern RELEASE_HEADING = Pattern.compile("(?m)^## ([0-9]+\\.[0-9]+\\.[0-9]+) — .*?Netzprotokoll (\\d+)");

    private interface Check {
        void run(Repo repo) throws IOException;
    }

    private static final Map<String, Check> CHECKS = Map.of(
            "changelog", ProtocolVersionTextsTest::changelog,
            "releaseNotes", ProtocolVersionTextsTest::releaseNotes,
            "apiChanges", ProtocolVersionTextsTest::apiChanges,
            "releasing", ProtocolVersionTextsTest::releasing,
            "addonApi", ProtocolVersionTextsTest::addonApi,
            "designAndStore", ProtocolVersionTextsTest::designAndStore);

    private record Repo(Path root, int protocol) {
        static Repo real() {
            for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
                if (Files.isRegularFile(candidate.resolve("gradle.properties")) && Files.isRegularFile(candidate.resolve("CHANGELOG.md"))) {
                    return new Repo(candidate, Constants.PROTOCOL_VERSION);
                }
            }
            fail("Projektwurzel mit gradle.properties und CHANGELOG.md nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
            return null;
        }

        boolean exists(String name) {
            return Files.isRegularFile(root.resolve(name));
        }

        String read(String name) throws IOException {
            assertTrue(exists(name), name + " nicht gefunden (" + root.toAbsolutePath() + ")");
            return Files.readString(root.resolve(name), StandardCharsets.UTF_8);
        }

        private Matcher version() throws IOException {
            Matcher matcher = MOD_VERSION.matcher(read("gradle.properties"));
            assertTrue(matcher.find(), "gradle.properties: version=x.y.z fehlt");
            return matcher;
        }

        String release() throws IOException {
            return version().group(1);
        }

        boolean published() throws IOException {
            return version().group(2).isEmpty();
        }
    }

    private static String section(String name, String text, String heading, String end) {
        int from = text.indexOf(heading);
        assertTrue(from >= 0, name + ": Abschnitt „" + heading.strip() + "“ fehlt");
        int to = text.indexOf(end, from + heading.length());
        return text.substring(from, to < 0 ? text.length() : to);
    }

    private static List<Integer> numbers(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        List<Integer> out = new ArrayList<>();
        while (matcher.find()) {
            out.add(Integer.parseInt(matcher.group(1)));
        }
        return out;
    }

    private static List<Integer> current(String where, int protocol, String text, String regex) {
        List<Integer> found = numbers(text, regex);
        for (int number : found) {
            assertEquals(protocol, number, where + " nennt Netzprotokoll " + number + ", Constants.PROTOCOL_VERSION ist " + protocol);
        }
        return found;
    }

    private static void allCurrent(String where, int protocol, String text, String regex) {
        assertFalse(current(where, protocol, text, regex).isEmpty(), where + ": keine Angabe zum Netzprotokoll (Muster " + regex + ")");
    }

    private static boolean present(Repo repo, String where, String text, String heading) throws IOException {
        if (text.contains(heading)) {
            return true;
        }
        assertFalse(repo.published(), where + ": Abschnitt „" + heading.strip() + "“ fehlt; das Release " + repo.release()
                + " braucht ihn (docs/RELEASING.md §4 Schritt 1)");
        return false;
    }

    private static int previousProtocol(Repo repo, String version) throws IOException {
        Matcher matcher = RELEASE_HEADING.matcher(repo.read("docs/api/changes.md"));
        while (matcher.find()) {
            if (!matcher.group(1).equals(version)) {
                return Integer.parseInt(matcher.group(2));
            }
        }
        fail("docs/api/changes.md: kein Abschnitt eines früheren Releases mit Netzprotokoll");
        return -1;
    }

    private static void changelog(Repo repo) throws IOException {
        String version = repo.release();
        String heading = "\n## [" + version + "]";
        String text = repo.read("CHANGELOG.md");
        if (!present(repo, "CHANGELOG.md", text, heading)) {
            return;
        }
        String where = "CHANGELOG.md [" + version + "] Compatibility";
        String block = section("CHANGELOG.md", text, heading, "\n## [");
        String compatibility = section("CHANGELOG.md [" + version + "]", block, "\n### Compatibility", "\n### ");
        current(where, repo.protocol(), compatibility, "[Nn]etwork protocol (\\d+)");
        String sameVersion = "client and server must both run " + version;
        boolean named = compatibility.toLowerCase(Locale.ROOT).contains(sameVersion);
        int previous = previousProtocol(repo, version);
        if (repo.protocol() != previous) {
            assertTrue(named, where + ": Netzprotokoll steigt von " + previous + " auf " + repo.protocol() + ", Hinweis „" + sameVersion
                    + "“ fehlt (docs/RELEASING.md §3)");
        } else {
            assertFalse(named, where + ": Netzprotokoll " + previous + " unverändert, „" + sameVersion
                    + "“ stimmt nicht, bei gleichem Protokoll spielen die Versionen zusammen (docs/RELEASING.md §3, README.md „Servers and commands“)");
        }
    }

    private static void releaseNotes(Repo repo) throws IOException {
        String version = repo.release();
        String heading = "\n## " + version + "\n";
        String text = repo.read("docs/RELEASE-NOTES.md");
        if (!present(repo, "docs/RELEASE-NOTES.md", text, heading)) {
            return;
        }
        String where = "docs/RELEASE-NOTES.md " + version;
        String block = section("docs/RELEASE-NOTES.md", text, heading, "\n## ");
        String intro = section(where, block, heading, "\n### ");
        current(where, repo.protocol(), intro, "Netzprotokoll\\*{0,2} \\(?(\\d+)");
        int previous = previousProtocol(repo, version);
        if (repo.protocol() == previous) {
            return;
        }
        Matcher before = Pattern.compile("\\*\\*Netzprotokoll \\d+\\*\\* \\(([^:()]+): (\\d+)\\)").matcher(intro);
        assertTrue(before.find(), where + ": Netzprotokoll steigt von " + previous + " auf " + repo.protocol()
                + ", „**Netzprotokoll N** (<vorher>: M)“ fehlt");
        assertEquals(previous, Integer.parseInt(before.group(2)), where + ": Netzprotokoll von " + before.group(1)
                + " passt nicht zu docs/api/changes.md");
    }

    private static void apiChanges(Repo repo) throws IOException {
        String version = repo.release();
        int protocol = repo.protocol();
        String text = repo.read("docs/api/changes.md");
        String where = "docs/api/changes.md " + version;
        List<Integer> row = current("docs/api/changes.md Übersicht " + version, protocol, text,
                "(?m)^\\| " + Pattern.quote(version) + "(?: [^|]*)?\\|[^|]*\\| (\\d+) \\|");
        assertTrue(!row.isEmpty() || !repo.published(), "docs/api/changes.md Übersicht: Zeile " + version
                + " mit Netzprotokoll fehlt; das Release braucht sie (docs/RELEASING.md §4 Schritt 1)");
        String heading = "\n## " + version + " — ";
        if (!present(repo, "docs/api/changes.md", text, heading)) {
            return;
        }
        String block = section("docs/api/changes.md", text, heading, "\n## ");
        allCurrent("docs/api/changes.md Überschrift " + version, protocol, block.lines().skip(1).findFirst().orElse(""), "Netzprotokoll (\\d+)");
        assertTrue(numbers(block, "steht für " + Pattern.quote(version) + " auf (\\d+)").stream().allMatch(n -> n == protocol),
                where + ": „steht für " + version + " auf …“ nennt nicht " + protocol);
        assertTrue(numbers(block, "[Pp]rotokoll\\w* bleibt (\\d+)").stream().allMatch(n -> n == protocol),
                where + ": „Protokoll… bleibt N“ mit N ungleich " + protocol);
        int previous = previousProtocol(repo, version);
        List<Integer> subsections = numbers(block, "(?m)^### Netzprotokoll (\\d+)");
        for (int number : subsections) {
            assertTrue(number == protocol || (number > previous && number < protocol), where + ": Unterabschnitt Netzprotokoll " + number
                    + " passt weder zu Constants.PROTOCOL_VERSION " + protocol + " noch zwischen " + previous + " (voriges Release) und " + protocol);
        }
        if (protocol == previous) {
            return;
        }
        assertTrue(subsections.contains(protocol), where + ": Netzprotokoll steigt von " + previous + " auf " + protocol
                + ", Unterabschnitt „### Netzprotokoll " + protocol + "“ mit dem Grund fehlt (docs/RELEASING.md §3)");
        String sub = section(where, block, "\n### Netzprotokoll " + protocol + "\n", "\n### ");
        int list = sub.indexOf("\n- ");
        String lead = list < 0 ? sub : sub.substring(0, list);
        for (int skipped = previous + 1; skipped < protocol; skipped++) {
            assertTrue(subsections.contains(skipped) || Pattern.compile("\\b" + skipped + "\\b").matcher(lead).find(), where
                    + ": Unterabschnitt Netzprotokoll " + protocol + " nennt vor seiner Liste die Zwischennummer " + skipped
                    + " nicht, und es gibt keinen Unterabschnitt Netzprotokoll " + skipped + " (docs/RELEASING.md §3)");
        }
        assertTrue(Pattern.compile("Gr(?:u|ü)nde?\\b").matcher(lead).find(), where + ": Unterabschnitt Netzprotokoll " + protocol
                + " nennt vor seiner Liste keinen Grund der Erhöhung („Grund:“ bzw. „Gründe:“, docs/RELEASING.md §3)");
    }

    private static void releasing(Repo repo) throws IOException {
        String section = section("docs/RELEASING.md", repo.read("docs/RELEASING.md"), "## 3. Netzprotokoll", "\n## ");
        allCurrent("docs/RELEASING.md §3", repo.protocol(), section, "`Constants\\.PROTOCOL_VERSION` \\(heute \\*\\*(\\d+)\\*\\*");
    }

    private static void addonApi(Repo repo) throws IOException {
        String version = repo.release();
        for (String name : List.of("docs/addon-api-1.7.md", "docs/api/addon-guide.md")) {
            Matcher matcher = Pattern.compile("(?s)Propwash ([0-9]+\\.[0-9]+\\.[0-9]+).{0,120}?Netzprotokoll (\\d+)\\)").matcher(repo.read(name));
            boolean any = false;
            while (matcher.find()) {
                any = true;
                if (matcher.group(1).equals(version)) {
                    assertEquals(repo.protocol(), Integer.parseInt(matcher.group(2)), name + ": „" + matcher.group().replace('\n', ' ')
                            + "“ – Constants.PROTOCOL_VERSION ist " + repo.protocol());
                }
            }
            assertTrue(any, name + ": keine Angabe „Propwash x.y.z … Netzprotokoll N)“");
        }
    }

    private static void designAndStore(Repo repo) throws IOException {
        String version = repo.release();
        String design = "docs/design/" + version + "/README.md";
        if (repo.exists(design)) {
            String text = repo.read(design);
            allCurrent(design + " Kopf", repo.protocol(), text, "Propwash FPV " + Pattern.quote(version) + " \\(API [0-9.]+, Netzprotokoll (\\d+)\\)");
            allCurrent(design + " Kurzfassung", repo.protocol(), text, "Netzprotokoll \\*\\*(\\d+)\\*\\*");
            allCurrent(design + " §3.3", repo.protocol(), text, "(?m)^### 3\\.3 Netzprotokoll (\\d+)");
        }
        Matcher listing = Pattern.compile("need ([0-9]+\\.[0-9]+\\.[0-9]+) together \\(network protocol (\\d+)\\)").matcher(repo.read("docs/modrinth/listing.md"));
        assertTrue(listing.find(), "docs/modrinth/listing.md: „need x.y.z together (network protocol N)“ fehlt");
        if (listing.group(1).equals(version)) {
            assertEquals(repo.protocol(), Integer.parseInt(listing.group(2)), "docs/modrinth/listing.md: „" + listing.group()
                    + "“ – Constants.PROTOCOL_VERSION ist " + repo.protocol());
        }
    }

    @Test
    void changelogCompatibilityNamesTheProtocol() throws IOException {
        changelog(Repo.real());
    }

    @Test
    void releaseNotesNameTheProtocol() throws IOException {
        releaseNotes(Repo.real());
    }

    @Test
    void apiChangesNameTheProtocolAndTheReason() throws IOException {
        apiChanges(Repo.real());
    }

    @Test
    void releasingNamesTheProtocol() throws IOException {
        releasing(Repo.real());
    }

    @Test
    void addonApiTextsNameTheProtocolOfTheirRelease() throws IOException {
        addonApi(Repo.real());
    }

    @Test
    void releaseDesignAndStoreTextsNameTheProtocol() throws IOException {
        designAndStore(Repo.real());
    }

    private static final String CHANGELOG = """
            # Changelog

            ## [0.6.0] — Share codes and a bigger race system

            ### New
            - Race standard for operators.

            ### Compatibility
            - Java 25 · network protocol 18: client and server must both run 0.6.0 – 0.5.x can't join a 0.6.0 server.

            ## [0.5.2] — Fisheye lens

            ### Compatibility
            - Java 25 · client and server must both run 0.5.2.
            """;

    private static final String RELEASE_NOTES = """
            # Ausführliche Release-Notizen

            ---

            ## 0.6.0

            Großes Update nach 0.5.2. **Netzprotokoll 18** (0.5.2: 16): neue Pakete – Client und Server brauchen beide 0.6.0.

            ### Rennen
            - Rennstandard.

            ## 0.5.2

            Großes Update nach 0.5.1. **Netzprotokoll 16** (0.5.0/0.5.1: 15): neue Pakete.
            """;

    private static final String CHANGES = """
            # Änderungen für Addon-Autoren

            ## Übersicht

            | Propwash | Parts-API | Netzprotokoll | Für Addons |
            |---|---|---|---|
            | 0.6.0 | 1.7.0 | 18 | Schalter und Achsen |
            | 0.5.2 | 1.6.0 | 16 | Halterung vorne |

            ---

            ## 0.6.0 — Parts-API 1.7.0, Netzprotokoll 18

            Die API steht auf 1.7.0.

            ### Netzprotokoll 18
            `Constants.PROTOCOL_VERSION` steht für 0.6.0 auf 18. Bis `0.6.0-rc.2` galt 17, danach änderte sich das Protokoll
            noch einmal. Grund:

            - Neue Pakete `propwash:personal_rules` und `propwash:rule_tier`.

            - Kurs-Einstellungen: `propwash:race_leaderboard` hat ein Feld mehr, die Protokollversion blieb dabei 17.

            ## 0.5.2 — Parts-API 1.6.0, Netzprotokoll 16

            ### Netzprotokoll 16
            0.5.0 und 0.5.1 (Protokoll 15) trennt der Handshake. Gründe:
            - Payload `propwash:accessory_action`.
            """;

    private static final String RELEASING = """
            # Versionen und Releases

            ## 3. Netzprotokoll

            `Constants.PROTOCOL_VERSION` (heute **18** für 0.6.0) steigt, sobald Client und Server nicht mehr zusammenspielen.

            ## 4. Ablauf eines Releases
            """;

    private static Map<String, String> fixture(String version) {
        Map<String, String> files = new TreeMap<>();
        files.put("gradle.properties", "version=" + version + "\n");
        files.put("CHANGELOG.md", CHANGELOG);
        files.put("docs/RELEASE-NOTES.md", RELEASE_NOTES);
        files.put("docs/api/changes.md", CHANGES);
        files.put("docs/RELEASING.md", RELEASING);
        files.put("docs/addon-api-1.7.md", "# Add-on-API 1.7\n\nAPI 1.7 (Propwash 0.6.0, Netzprotokoll 18)\n");
        files.put("docs/api/addon-guide.md", "# Handbuch\n\nAPI 1.7 kommt mit Propwash 0.6.0 (API 1.7.0, Netzprotokoll 18).\n");
        files.put("docs/modrinth/listing.md", "Client and server need 0.6.0 together (network protocol 18).\n");
        return files;
    }

    private static void edit(Map<String, String> files, String name, String from, String to) {
        String text = files.get(name);
        assertTrue(text.contains(from), "Testaufbau: „" + from.strip() + "“ fehlt in " + name);
        files.put(name, text.replace(from, to));
    }

    private static void patchSections(Map<String, String> files, String version, int protocol, String compatibility) {
        edit(files, "CHANGELOG.md", "## [0.6.0]", "## [" + version + "] — Fixes\n\n### Fixed\n- Laps with disarm on crash count.\n\n"
                + "### Compatibility\n- Java 25 · " + compatibility + "\n\n## [0.6.0]");
        edit(files, "docs/RELEASE-NOTES.md", "## 0.6.0\n", "## " + version + "\n\nFehlerbehebung zu 0.6.0. Netzprotokoll (" + protocol
                + "), Parts-API (1.7.0) und Speicherformate sind unverändert.\n\n### Rennen\n- Disarm on crash.\n\n## 0.6.0\n");
        edit(files, "docs/api/changes.md", "| 0.6.0 |", "| " + version + " | 1.7.0 | " + protocol + " | nichts Neues für Addons |\n| 0.6.0 |");
        edit(files, "docs/api/changes.md", "## 0.6.0 — ", "## " + version + " — Parts-API 1.7.0, Netzprotokoll " + protocol
                + " (unverändert)\n\nFehlerbehebung ohne API- oder Datenformat-Änderung.\n\n---\n\n## 0.6.0 — ");
    }

    private static Map<String, String> failures(Path dir, Map<String, String> files, int protocol) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = dir.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
        Repo repo = new Repo(dir, protocol);
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, Check> check : CHECKS.entrySet()) {
            try {
                check.getValue().run(repo);
            } catch (AssertionError e) {
                out.put(check.getKey(), e.getMessage());
            }
        }
        return out;
    }

    private static void assertFailures(Set<String> expected, Map<String, String> failures) {
        assertEquals(expected, failures.keySet(), "rote Prüfungen: " + failures);
    }

    @Test
    void fixtureOfTheReleaseIsGreen(@TempDir Path dir) throws IOException {
        assertFailures(Set.of(), failures(dir, fixture("0.6.0"), 18));
        assertFailures(Set.of(), failures(dir, fixture("0.6.0-rc.3"), 18));
    }

    @Test
    void nextDevelopmentStateNeedsNoReleaseSections(@TempDir Path dir) throws IOException {
        assertFailures(Set.of(), failures(dir, fixture("0.6.1-dev.1"), 18));
    }

    @Test
    void risingProtocolDuringDevelopmentOnlyNeedsReleasing(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.7.0-dev.1");
        edit(files, "docs/RELEASING.md", "heute **18**", "heute **19**");
        assertFailures(Set.of(), failures(dir, files, 19));
        assertFailures(Set.of("releasing"), failures(dir, fixture("0.7.0-dev.1"), 19));
    }

    @Test
    void patchWithTheSameProtocolNeedsNoProtocolNumberAndNoHint(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.6.1");
        patchSections(files, "0.6.1", 18, "0.6.0 and 0.6.1 play together – clients and servers can mix both versions.");
        assertFailures(Set.of(), failures(dir, files, 18));
    }

    @Test
    void patchWithTheSameProtocolMustNotClaimTheSameVersion(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.6.1");
        patchSections(files, "0.6.1", 18, "client and server must both run 0.6.1.");
        Map<String, String> failures = failures(dir, files, 18);
        assertFailures(Set.of("changelog"), failures);
        assertTrue(failures.get("changelog").contains("unverändert"), failures.get("changelog"));
    }

    @Test
    void risingProtocolNeedsTheHintAndTheReason(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.7.0");
        edit(files, "docs/RELEASING.md", "heute **18**", "heute **19**");
        patchSections(files, "0.7.0", 19, "0.6.0 and 0.7.0 play together.");
        Map<String, String> failures = failures(dir, files, 19);
        assertFailures(Set.of("changelog", "releaseNotes", "apiChanges"), failures);
        assertTrue(failures.get("changelog").contains("client and server must both run 0.7.0"), failures.get("changelog"));
        assertTrue(failures.get("releaseNotes").contains("**Netzprotokoll N** (<vorher>: M)"), failures.get("releaseNotes"));
        assertTrue(failures.get("apiChanges").contains("### Netzprotokoll 19"), failures.get("apiChanges"));
    }

    @Test
    void releaseNeedsItsSections(@TempDir Path dir) throws IOException {
        Map<String, String> failures = failures(dir, fixture("0.6.1"), 18);
        assertFailures(Set.of("changelog", "releaseNotes", "apiChanges"), failures);
        assertTrue(failures.get("changelog").contains("## [0.6.1]"), failures.get("changelog"));
        assertTrue(failures.get("releaseNotes").contains("## 0.6.1"), failures.get("releaseNotes"));
        assertTrue(failures.get("apiChanges").contains("0.6.1"), failures.get("apiChanges"));
    }

    @Test
    void presentSectionsOfAPreReleaseNameTheCurrentProtocol(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.6.0-rc.3");
        edit(files, "CHANGELOG.md", "network protocol 18", "network protocol 17");
        edit(files, "docs/RELEASE-NOTES.md", "**Netzprotokoll 18**", "**Netzprotokoll 17**");
        edit(files, "docs/api/changes.md", "| 0.6.0 | 1.7.0 | 18 |", "| 0.6.0 | 1.7.0 | 17 |");
        assertFailures(Set.of("changelog", "releaseNotes", "apiChanges"), failures(dir, files, 18));
    }

    @Test
    void reasonAndInterimNumberStandBeforeTheList(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.6.0");
        edit(files, "docs/api/changes.md", " Bis `0.6.0-rc.2` galt 17, danach änderte sich das Protokoll\nnoch einmal. Grund:\n\n- Neue Pakete `propwash:personal_rules` und `propwash:rule_tier`.\n", "\n");
        Map<String, String> failures = failures(dir, files, 18);
        assertFailures(Set.of("apiChanges"), failures);
        assertTrue(failures.get("apiChanges").contains("Zwischennummer 17"), failures.get("apiChanges"));
        Map<String, String> withoutReason = fixture("0.6.0");
        edit(withoutReason, "docs/api/changes.md", "noch einmal. Grund:", "noch einmal.");
        failures = failures(dir, withoutReason, 18);
        assertFailures(Set.of("apiChanges"), failures);
        assertTrue(failures.get("apiChanges").contains("keinen Grund"), failures.get("apiChanges"));
    }

    @Test
    void interimNumberMayHaveItsOwnSubsection(@TempDir Path dir) throws IOException {
        Map<String, String> files = fixture("0.6.0");
        edit(files, "docs/api/changes.md", " Bis `0.6.0-rc.2` galt 17, danach änderte sich das Protokoll\nnoch einmal. Grund:",
                " Grund:");
        edit(files, "docs/api/changes.md", "\n## 0.5.2 — ", "\n### Netzprotokoll 17\nGrund: neues Paket `propwash:race_standard`.\n\n## 0.5.2 — ");
        assertFailures(Set.of(), failures(dir, files, 18));
    }
}
