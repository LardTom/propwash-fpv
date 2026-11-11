package de.lardtom.propwash.barrier;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BarrierNamesTest {
    private static final List<String> OLD = List.of("Drone-Only Barrier", "Drohnen-Durchlass");
    private static final List<String> FILES = List.of("CHANGELOG.md", "README.md", "docs/RELEASE-NOTES.md", "docs/barriers.md", "docs/testing.md",
            "docs/ARCHITECTURE.md", "docs/FOUNDATION.md", "tools/assets/world/barriers.py");

    private static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isRegularFile(candidate.resolve("CHANGELOG.md")) && Files.isDirectory(candidate.resolve("common/src/lang"))) {
                return candidate;
            }
        }
        fail("Projektwurzel nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static List<Path> files() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String name : FILES) {
            out.add(root().resolve(name));
        }
        try (Stream<Path> lang = Files.walk(root().resolve("common/src/lang"))) {
            out.addAll(lang.filter(file -> file.toString().endsWith(".json")).sorted().toList());
        }
        return out;
    }

    private static String text(String name) throws IOException {
        return Files.readString(root().resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void theDronePassableBarrierKeepsNoOldNameInTextsAndDocs() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : files()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String old : OLD) {
                if (text.contains(old)) {
                    found.add(root().relativize(file) + ": „" + old + "“");
                }
            }
        }
        assertTrue(found.isEmpty(), "alter Name: " + found);
    }

    @Test
    void docsUseTheNewNamesInBothLanguages() throws IOException {
        assertTrue(text("CHANGELOG.md").contains("*Drone-Passable Barrier*"), "CHANGELOG.md");
        assertTrue(text("docs/RELEASE-NOTES.md").contains("Drohnen-durchlässige Barriere"), "docs/RELEASE-NOTES.md");
        String doc = text("docs/barriers.md");
        assertTrue(doc.contains("Drohnen-durchlässige Barriere") && doc.contains("Drone-Passable Barrier"), "docs/barriers.md");
    }
}
