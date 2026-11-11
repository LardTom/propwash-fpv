package de.lardtom.propwash.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ReleasingApiVersionTest {
    private static final String SECTION = "## 2. API-Version";
    private static final Pattern CURRENT = Pattern.compile("`PropwashApi\\.API_VERSION` \\(heute \\*\\*([0-9]+\\.[0-9]+\\.[0-9]+)\\*\\*");

    private static Path releasing() {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            Path file = candidate.resolve("docs/RELEASING.md");
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        fail("docs/RELEASING.md nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    @Test
    void releasingNamesCurrentApiVersion() throws IOException {
        String text = Files.readString(releasing(), StandardCharsets.UTF_8);
        int from = text.indexOf(SECTION);
        assertTrue(from >= 0, "docs/RELEASING.md: Abschnitt „" + SECTION + "“ fehlt");
        int to = text.indexOf("\n## ", from + SECTION.length());
        String section = text.substring(from, to < 0 ? text.length() : to);
        Matcher matcher = CURRENT.matcher(section);
        assertTrue(matcher.find(), "docs/RELEASING.md §2: Angabe „`PropwashApi.API_VERSION` (heute **X.Y.Z**“ fehlt");
        assertEquals(PropwashApi.API_VERSION, matcher.group(1), "docs/RELEASING.md §2 nennt eine andere API-Version als PropwashApi.API_VERSION");
    }
}
