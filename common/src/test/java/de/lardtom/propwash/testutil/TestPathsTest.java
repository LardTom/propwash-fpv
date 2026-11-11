package de.lardtom.propwash.testutil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPathsTest {
    private static final String GOLDEN = "flashback/catmull_rom_golden.txt";
    private static final String MISSING = "de/lardtom/propwash/testutil/missing.txt";

    private static Path fakeRoot(Path dir) throws IOException {
        Files.createDirectories(dir.resolve(TestPaths.COMMON_SOURCES));
        Files.writeString(dir.resolve(TestPaths.MARKER), "rootProject.name = 'propwash'\n", StandardCharsets.UTF_8);
        return dir.toAbsolutePath().normalize();
    }

    @Test
    void rootAndModuleFoldersAreAbsoluteAndComplete() {
        Path root = TestPaths.root();
        assertTrue(root.isAbsolute(), root.toString());
        assertTrue(TestPaths.isRoot(root), root.toString());
        assertTrue(Files.isRegularFile(TestPaths.common().resolve("build.gradle")));
        assertTrue(Files.isRegularFile(TestPaths.lang("guide").resolve("en_us.json")));
        assertTrue(Files.isDirectory(TestPaths.mainResources().resolve("data/propwash")));
        assertTrue(Files.isRegularFile(TestPaths.testResources().resolve(GOLDEN)));
        String configured = System.getProperty(TestPaths.ROOT_DIR);
        if (configured != null) {
            assertEquals(Path.of(configured).toAbsolutePath().normalize(), root);
        }
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> TestPaths.lang("no_such_module"));
        assertTrue(missing.getMessage().contains("no_such_module"), missing.getMessage());
    }

    @Test
    void searchClimbsFromAnyFolderBelowTheRoot(@TempDir Path dir) throws IOException {
        Path root = fakeRoot(dir.resolve("repo"));
        Path deep = Files.createDirectories(root.resolve("common/build/classes/java/test/de/lardtom"));
        assertEquals(Optional.of(root), TestPaths.search(deep));
        assertEquals(Optional.of(root), TestPaths.search(root));
        Path nowhere = dir.toAbsolutePath().getRoot();
        assertEquals(root, TestPaths.root(Optional.empty(), List.of(nowhere, deep)));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TestPaths.root(Optional.empty(), List.of(nowhere)));
        assertTrue(e.getMessage().contains(TestPaths.ROOT_DIR), e.getMessage());
    }

    @Test
    void otherGradleProjectsAreNotTakenForTheRoot(@TempDir Path dir) throws IOException {
        Path other = dir.resolve("other").toAbsolutePath().normalize();
        Path sources = Files.createDirectories(other.resolve("common/src/main/java/de/lardtom/justmoreparts"));
        Files.writeString(other.resolve(TestPaths.MARKER), "rootProject.name = 'other'\n", StandardCharsets.UTF_8);
        assertFalse(TestPaths.isRoot(other));
        assertFalse(TestPaths.search(sources).filter(other::equals).isPresent());
        Path nested = fakeRoot(other.resolve("inner"));
        assertEquals(Optional.of(nested), TestPaths.search(nested.resolve("common")));
    }

    @Test
    void configuredRootWinsAndIsChecked(@TempDir Path dir) throws IOException {
        Path root = fakeRoot(dir.resolve("repo"));
        Path elsewhere = fakeRoot(dir.resolve("elsewhere"));
        assertEquals(root, TestPaths.root(Optional.of(root), List.of(elsewhere)));
        Path wrong = dir.toAbsolutePath().normalize();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TestPaths.root(Optional.of(wrong), List.of(elsewhere)));
        assertTrue(e.getMessage().contains(TestPaths.ROOT_DIR) && e.getMessage().contains(wrong.toString()), e.getMessage());
    }

    @Test
    void resourcesAreReadFromTheClassPathOrTheTestResourceFolder() throws IOException {
        List<String> lines = TestPaths.requireLines(GOLDEN);
        assertEquals(Files.readAllLines(TestPaths.testResources().resolve(GOLDEN), StandardCharsets.UTF_8), lines);
        assertTrue(lines.size() >= 100, "Zeilen " + lines.size());
        assertEquals(Optional.empty(), TestPaths.readResource(MISSING));
        assertEquals(Optional.empty(), TestPaths.resourceLines(MISSING));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TestPaths.requireLines(MISSING));
        assertTrue(e.getMessage().contains(MISSING), e.getMessage());
    }

    @Test
    void goldenFilesAreOnlyWrittenIntoConfiguredTestResources(@TempDir Path dir) throws IOException {
        String refused = TestPaths.writeGolden(Optional.empty(), "golden/new.txt", "a 1\n");
        assertTrue(refused.contains(TestPaths.TEST_RESOURCES) && refused.contains("golden/new.txt"), refused);
        assertFalse(Files.exists(dir.resolve("golden")));
        String written = TestPaths.writeGolden(Optional.of(dir), "golden/new.txt", "a 1\n");
        assertTrue(written.contains("neu geschrieben") && written.contains(dir.resolve("golden/new.txt").toString()), written);
        assertEquals("a 1\n", Files.readString(dir.resolve("golden/new.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void searchStartsAtTheWorkingDirectoryAndThenAtTheTestClasses() {
        List<Path> starts = TestPaths.starts();
        assertEquals(Path.of("").toAbsolutePath(), starts.get(0));
        assertTrue(starts.size() <= 2, starts.toString());
        assertTrue(starts.stream().allMatch(Path::isAbsolute), starts.toString());
        TestPaths.codeLocation().ifPresent(location -> assertTrue(starts.contains(location), starts.toString()));
    }
}
