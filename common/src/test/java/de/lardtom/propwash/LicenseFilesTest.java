package de.lardtom.propwash;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LicenseFilesTest {
    private static final String REQUIRED_NOTICE = "Required Notice: Copyright (c) 2026 lardtom";
    private static final String SHIELD_HEADING = "# PolyForm Shield License 1.0.0";
    private static final String SHIELD_SHA256 = "67530f8e9adfcc5d2e9d72b804500cebb7472ff84c34a6729a80a2a9be901ee6";
    private static final String SPDX = "LicenseRef-PolyForm-Shield-1.0.0 AND MIT";
    private static final String API_PATH = "common/src/main/java/de/lardtom/propwash/api/";
    private static final List<String> EXAMPLE_PATHS = List.of(
            "common/src/devtest/java/de/lardtom/propwash/dev/example/",
            "common/src/devtest/resources/assets/propwash_example/",
            "common/src/devtest/resources/data/propwash_example/");

    private static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isRegularFile(candidate.resolve("LICENSE")) && Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        fail("Wurzel des Repositorys nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static String read(String name) throws IOException {
        return Files.readString(root().resolve(name), StandardCharsets.UTF_8);
    }

    private static List<String> mitPaths() throws IOException {
        return read("LICENSE-MIT").lines().filter(line -> line.startsWith("- ")).map(line -> line.substring(2).trim()).toList();
    }

    @Test
    void polyformShieldTextIsTheOfficialPlainText() throws IOException, NoSuchAlgorithmException {
        String license = read("LICENSE");
        int start = license.indexOf(SHIELD_HEADING);
        assertTrue(start > 0, "LICENSE enthält den Text der PolyForm Shield License 1.0.0 nach dem eigenen Kopf");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(license.substring(start).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        assertEquals(SHIELD_SHA256, hex.toString(), "Lizenztext muss byte-gleich mit polyformproject.org/licenses/shield/1.0.0.txt sein");
    }

    @Test
    void requiredNoticeLineInLicenseAndNotice() throws IOException {
        for (String name : List.of("LICENSE", "NOTICE")) {
            assertTrue(read(name).lines().anyMatch(REQUIRED_NOTICE::equals), name + " braucht die Zeile „" + REQUIRED_NOTICE + "“");
        }
    }

    @Test
    void licenseHeadNamesTheSplitTheOldVersionsAndTheExitClause() throws IOException {
        String head = read("LICENSE");
        head = head.substring(0, head.indexOf(SHIELD_HEADING));
        for (String phrase : List.of("LICENSE-MIT", "0.4.0", "All Rights Reserved", "LicenseRef-PolyForm-Shield-1.0.0",
                "Modpacks.", "Exit clause.", "12 consecutive months", "MIT License")) {
            assertTrue(head.contains(phrase), "Kopf von LICENSE nennt „" + phrase + "“");
        }
    }

    @Test
    void mitPathsExistAndCoverApiAndExampleAddon() throws IOException {
        List<String> paths = mitPaths();
        assertTrue(paths.contains(API_PATH), "LICENSE-MIT nennt die API " + API_PATH);
        assertTrue(paths.containsAll(EXAMPLE_PATHS), "LICENSE-MIT nennt das Beispiel-Addon " + EXAMPLE_PATHS);
        assertTrue(paths.contains("docs/"), "LICENSE-MIT nennt die Doku");
        for (String path : paths) {
            Path resolved = root().resolve(path);
            if (path.endsWith("/")) {
                assertTrue(Files.isDirectory(resolved), "Ordner aus LICENSE-MIT fehlt: " + path);
            } else {
                assertTrue(Files.isRegularFile(resolved), "Datei aus LICENSE-MIT fehlt: " + path);
            }
        }
        assertTrue(Files.isRegularFile(root().resolve(API_PATH).resolve("PropwashApi.java")), "API-Pfad zeigt auf das API-Paket");
    }

    @Test
    void metadataUsesTheSpdxExpression() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(root().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals(SPDX, properties.getProperty("license"), "gradle.properties: license");
        assertTrue(read("fabric/src/main/resources/fabric.mod.json").contains("\"license\": \"${license}\""), "fabric.mod.json übernimmt license");
        assertTrue(read("neoforge/src/main/resources/META-INF/neoforge.mods.toml").contains("license = \"${license}\""),
                "neoforge.mods.toml übernimmt license");
    }

    @Test
    void jarsCarryAllLicenseFiles() throws IOException {
        String build = read("build-logic/src/main/groovy/multiloader-common.gradle");
        assertTrue(build.contains("rootProject.files('LICENSE', 'LICENSE-MIT', 'NOTICE')"), "jedes Jar bekommt LICENSE, LICENSE-MIT und NOTICE");
    }

    @Test
    void contributionFilesAndReadmeSummary() throws IOException {
        for (String name : List.of("CONTRIBUTING.md", "CLA.md", ".github/pull_request_template.md")) {
            assertTrue(Files.isRegularFile(root().resolve(name)), name + " fehlt");
        }
        assertTrue(read(".github/pull_request_template.md").contains("CLA.md"), "Pull-Request-Vorlage fragt nach dem CLA");
        String readme = read("README.md");
        String license = readme.substring(readme.indexOf("## License"));
        for (String phrase : List.of("PolyForm Shield License 1.0.0", "LICENSE-MIT", "modpacks", "12 months", "0.4.0 and older")) {
            assertTrue(license.contains(phrase), "README-Abschnitt License nennt „" + phrase + "“");
        }
    }
}
