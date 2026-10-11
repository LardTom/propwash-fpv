package de.lardtom.propwash.ui.client.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class TooltipCallsTest {
    private static final Pattern DIRECT = Pattern.compile("\\.set(Component)?TooltipForNextFrame\\(");
    private static final String HELPER = "ThemeText.java";

    @Test
    void modCodeDrawsTooltipsOnlyThroughTheNullSafeHelper() throws IOException {
        Path root = root();
        List<String> offenders;
        try (Stream<Path> files = Stream.of("common", "fabric", "neoforge").map(module -> root.resolve(module).resolve("src/main/java"))
                .filter(Files::isDirectory)
                .flatMap(TooltipCallsTest::walk)) {
            offenders = files.filter(path -> path.toString().endsWith(".java") && !path.getFileName().toString().equals(HELPER))
                    .flatMap(path -> lines(root, path))
                    .toList();
        }
        assertEquals(List.of(), offenders, "Tooltips nur über ThemeText.tooltip/tooltipLines/tooltipWrapped/tooltipSequences zeichnen");
    }

    private static Stream<String> lines(Path root, Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String name = root.relativize(file).toString();
            return IntStream.range(0, lines.size())
                    .filter(i -> DIRECT.matcher(lines.get(i)).find())
                    .mapToObj(i -> name + ":" + (i + 1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<Path> walk(Path dir) {
        try {
            return Files.walk(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isRegularFile(candidate.resolve("LICENSE")) && Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        fail("Wurzel des Repositorys nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }
}
