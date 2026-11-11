package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.dev.example.client.ExampleConfigTab;
import de.lardtom.propwash.share.client.ShareConfigTab;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ShowcaseTourShotNameTest {
    private static final String INVALID = "<>:\"\\|?*";
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul",
            "com0", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "com¹", "com²", "com³",
            "lpt0", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "lpt¹", "lpt²", "lpt³");

    @Test
    void namespacedTabIdsBecomeFlatFileNamesValidOnWindows() {
        assertEquals("configurator_propwash_sharecode", ShowcaseTour.configuratorShot(ShareConfigTab.ID));
        assertEquals("configurator_propwash_example_api_demo", ShowcaseTour.configuratorShot(ExampleConfigTab.ID));
        assertEquals("configurator_justmoreparts_sharecode", ShowcaseTour.configuratorShot("justmoreparts:sharecode"));
        assertEquals("configurator_addon_tabs_extra", ShowcaseTour.configuratorShot("addon:tabs/extra"));
        for (String id : List.of(ShareConfigTab.ID, ExampleConfigTab.ID, "justmoreparts:sharecode", "a<b>c\"d\\e|f?g*h", "x:y/z")) {
            String shot = ShowcaseTour.configuratorShot(id);
            assertFalse(shot.contains("/"), id + " ergibt einen Unterordner: " + shot);
            assertValidOnWindows(shot);
        }
        assertEquals("propwash:sharecode", ShareConfigTab.ID, "die Tab-Id selbst bleibt unverändert");
    }

    @Test
    void plainTabIdsKeepTheirFileNames() {
        for (String id : List.of("overview", "server", "world_rules", "video_channel", "drone_render", "action_cam")) {
            assertEquals("configurator_" + id, ShowcaseTour.configuratorShot(id));
        }
    }

    @Test
    void captureCleansNamesCentrallyAndKeepsSubfolders() {
        assertEquals("configurator_propwash_sharecode", Screenshots.fileName("configurator_propwash:sharecode"));
        assertEquals("share/sharecode_import_de", Screenshots.fileName("share/sharecode_import_de"));
        assertEquals("share/a_b_c_d_e_f_g_h", Screenshots.fileName("share/a<b>c:d\"e|f?g*h"));
        assertEquals("before/x_y", Screenshots.fileName("before/x\\y"));
        assertEquals("tab_one_", Screenshots.fileName("tab\tone\u0001"));
        assertEquals("dir_/shot", Screenshots.fileName("dir./shot"));
        assertEquals("dir_/shot", Screenshots.fileName("dir /shot"));
        assertEquals("con_/nul_", Screenshots.fileName("con/nul"));
        assertEquals("COM1_.x/LPT9_", Screenshots.fileName("COM1.x/LPT9"));
        assertEquals("../docs/shot", Screenshots.fileName("../docs/shot"));
        assertEquals("console/null", Screenshots.fileName("console/null"));
        for (String name : List.of("configurator_propwash:sharecode", "a<b>c\"d|e?f*g", "x\\y", "con", "aux.tar", "dir. /x", "q\u0000r")) {
            assertValidOnWindows(Screenshots.fileName(name));
        }
    }

    @Test
    void validNamesStayUnchanged() {
        for (String name : List.of("configurator_server", "share/sharecode_import_de", "fisheye_RACE_wide", "workbench_freestyle_gui2",
                "osd_flashback_budget_LOW_de", "configurator_osd_4_3")) {
            assertEquals(name, Screenshots.fileName(name));
        }
    }

    @Test
    void noFileUnderDocsHasANameInvalidOnWindows() throws IOException {
        Path docs = root().resolve("docs");
        try (Stream<Path> files = Files.walk(docs)) {
            List<String> invalid = files.map(path -> docs.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> !name.isEmpty() && !Screenshots.fileName(name).equals(name))
                    .sorted()
                    .toList();
            assertTrue(invalid.isEmpty(), "Namen unter docs/, die unter Windows ungültig sind: " + invalid);
        }
    }

    private static void assertValidOnWindows(String name) {
        for (String segment : name.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                continue;
            }
            for (char c : segment.toCharArray()) {
                assertFalse(c < 32 || INVALID.indexOf(c) >= 0, "ungültiges Zeichen " + (int) c + " in " + name);
            }
            assertFalse(segment.endsWith(".") || segment.endsWith(" "), "Punkt oder Leerzeichen am Ende in " + name);
            int dot = segment.indexOf('.');
            String base = (dot < 0 ? segment : segment.substring(0, dot)).toLowerCase(Locale.ROOT);
            assertFalse(RESERVED.contains(base), "reservierter Name in " + name);
        }
    }

    private static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle")) && Files.isDirectory(candidate.resolve("docs"))) {
                return candidate;
            }
        }
        fail("Wurzel des Repositorys nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }
}
