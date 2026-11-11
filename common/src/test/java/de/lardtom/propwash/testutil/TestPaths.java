package de.lardtom.propwash.testutil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TestPaths {
    public static final String ROOT_DIR = "propwash.rootDir";
    public static final String TEST_RESOURCES = "propwash.testResources";
    static final String MARKER = "settings.gradle";
    static final String COMMON_SOURCES = "common/src/main/java/de/lardtom/propwash";
    private static final String TEST_RESOURCES_IN_COMMON = "src/test/resources";

    private TestPaths() {
    }

    public static Path root() {
        return root(property(ROOT_DIR), starts());
    }

    static Path root(Optional<Path> configured, List<Path> starts) {
        if (configured.isPresent()) {
            Path dir = configured.get();
            if (!isRoot(dir)) {
                throw new IllegalStateException("Systemeigenschaft " + ROOT_DIR + " zeigt nicht auf die Wurzel des Repositorys: " + dir);
            }
            return dir;
        }
        for (Path start : starts) {
            Optional<Path> found = search(start);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new IllegalStateException("Wurzel des Repositorys nicht gefunden (gesucht ab " + starts + " nach oben bis " + MARKER
                + "); Systemeigenschaft " + ROOT_DIR + " setzen");
    }

    public static Path common() {
        return root().resolve("common");
    }

    public static Path lang(String module) {
        Path dir = common().resolve("src/lang").resolve(module);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Sprachquellen des Moduls " + module + " fehlen: " + dir);
        }
        return dir;
    }

    public static Path mainResources() {
        return common().resolve("src/main/resources");
    }

    public static Path testResources() {
        return property(TEST_RESOURCES).orElseGet(() -> common().resolve(TEST_RESOURCES_IN_COMMON));
    }

    public static Optional<Path> writableTestResources() {
        return property(TEST_RESOURCES);
    }

    public static Optional<String> readResource(String resource) {
        try (InputStream in = TestPaths.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return readFile(testResources().resolve(resource));
    }

    public static Optional<List<String>> resourceLines(String resource) {
        return readResource(resource).map(text -> text.lines().toList());
    }

    public static List<String> requireLines(String resource) {
        return resourceLines(resource).orElseThrow(() -> new IllegalStateException(missing(resource)));
    }

    public static String writeGolden(String resource, String text) {
        return writeGolden(writableTestResources(), resource, text);
    }

    static String writeGolden(Optional<Path> dir, String resource, String text) {
        if (dir.isEmpty()) {
            return "Golden-Datei " + resource + " nicht geschrieben; das geht nur mit der Systemeigenschaft " + TEST_RESOURCES
                    + "=<Ordner der Testressourcen>, die ./gradlew :common:test selbst setzt";
        }
        Path path = dir.get().resolve(resource);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "Golden-Datei neu geschrieben: " + path + " — nur einchecken, wenn die Änderung gewollt ist";
    }

    static String missing(String resource) {
        return "Testressource " + resource + " fehlt (weder im Klassenpfad noch unter " + testResources() + ")";
    }

    static Optional<Path> search(Path start) {
        for (Path dir = start.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (isRoot(dir)) {
                return Optional.of(dir);
            }
        }
        return Optional.empty();
    }

    static boolean isRoot(Path dir) {
        return Files.isRegularFile(dir.resolve(MARKER)) && Files.isDirectory(dir.resolve(COMMON_SOURCES));
    }

    static List<Path> starts() {
        List<Path> out = new ArrayList<>();
        out.add(Path.of("").toAbsolutePath());
        codeLocation().filter(path -> !out.contains(path)).ifPresent(out::add);
        return out;
    }

    static Optional<Path> codeLocation() {
        try {
            CodeSource source = TestPaths.class.getProtectionDomain().getCodeSource();
            URL location = source == null ? null : source.getLocation();
            if (location == null || !"file".equals(location.getProtocol())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(location.toURI()).toAbsolutePath().normalize());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> property(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(value.strip()).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            throw new IllegalStateException("Systemeigenschaft " + name + " ist kein gültiger Pfad: " + value, e);
        }
    }

    private static Optional<String> readFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
