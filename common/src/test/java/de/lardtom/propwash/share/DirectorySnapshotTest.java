package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.share.ShareTableCheck;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorySnapshotTest {
    private static final AddonDirectory DIRECTORY = AddonDirectory.builtin();

    @Test
    void snapshotIsValidAndShortIdsAreFixed() {
        assertEquals(List.of(), DIRECTORY.problems());
        Set<Integer> ids = new HashSet<>();
        Set<String> namespaces = new HashSet<>();
        for (AddonDirectory.Addon addon : DIRECTORY.addons()) {
            assertTrue(ids.add(addon.shortId()), "Kurzkennung doppelt: " + addon.shortId());
            assertTrue(namespaces.add(addon.namespace()), "Namensraum doppelt: " + addon.namespace());
            assertTrue(addon.name().containsKey("en_us") && addon.name().containsKey("de_de"), addon.namespace());
            assertTrue(addon.modrinth().isPresent() && addon.curseforge().isPresent(), addon.namespace());
        }
        assertEquals("propwash", DIRECTORY.byShortId(1).orElseThrow().namespace());
        assertEquals("justmoreparts", DIRECTORY.byShortId(2).orElseThrow().namespace());
        assertEquals("propwash-just-more-parts", DIRECTORY.byNamespace("justmoreparts").orElseThrow().modrinth().orElseThrow());
        ShareTables tables = ShareTables.builtin();
        assertEquals(List.of(), tables.problems());
        assertEquals(2, tables.shortIdOf("justmoreparts"));
        assertEquals("justmoreparts", tables.namespaceOfShort(2).orElseThrow());
    }

    @Test
    void loadingFindsTablesAndReportsGaps() {
        Map<String, Map<String, byte[]>> jars = new TreeMap<>();
        jars.put("propwash", Map.of());
        jars.put("myaddon", Map.of("data/myaddon/propwash_share/table.json", ShareTestData.EXTRA_V1.replace("extraparts", "myaddon")
                .getBytes(StandardCharsets.UTF_8), "data/propwash/propwash_share/table.json", "{}".getBytes(StandardCharsets.UTF_8)));
        jars.put("justmoreparts", Map.of());
        ShareTables.Loader loader = new ShareTables.Loader() {
            @Override
            public Map<String, byte[]> tables(String modId) {
                return jars.get(modId);
            }

            @Override
            public Optional<String> version(String modId) {
                return Optional.of(modId.equals("justmoreparts") ? "1.1.1" : "1.1.0");
            }

            @Override
            public Optional<de.lardtom.propwash.platform.ModMeta> meta(String modId) {
                return Optional.empty();
            }

            @Override
            public AddonDirectory directory() {
                return DIRECTORY;
            }
        };
        List<Identifier> registered = new ArrayList<>(ShareTables.registeredParts());
        registered.add(Identifier.parse("myaddon:x_frame_1"));
        registered.add(Identifier.parse("myaddon:not_listed"));
        registered.add(Identifier.parse("justmoreparts:deadcat_5"));
        ShareTables tables = ShareTables.load(jars.keySet(), loader, ShareTables.PartLookup.NONE, registered);
        assertTrue(tables.installedTable("myaddon").isPresent());
        assertTrue(tables.installedTable("propwash").isPresent());
        String problems = String.join("\n", tables.problems());
        assertTrue(problems.contains("FEHLER myaddon: Tabelle für den reservierten Namensraum propwash verworfen"), problems);
        assertTrue(problems.contains("myaddon: Teile ohne Eintrag in der Tabelle gehen als Pfad: [not_listed]"), problems);
        assertTrue(problems.contains("myaddon: Einträge ohne angemeldetes Teil und ohne deprecated: [frame/x_frame_2, motor/new_motor]"), problems);
        assertTrue(problems.contains("justmoreparts: Add-on ohne eigene Teile-Tabelle, Sharecodes nutzen die Tabelle aus dem Verzeichnis-Abzug"), problems);
        assertEquals(Optional.of("1.1.1"), tables.version("justmoreparts"));
        assertTrue(tables.installed("justmoreparts"));
    }

    @Test
    void jmpTableIsOnlyAppendedAgainstTheLastRelease() throws IOException {
        ShareTable table = DIRECTORY.byNamespace("justmoreparts").orElseThrow().table().orElseThrow();
        Map<String, List<String>> baseline = ShareTableCheck.readBaseline(ShareTestData.resource("directory-baseline.json")).get("justmoreparts");
        for (PartKind kind : Pw3Params.KINDS) {
            List<String> base = baseline.getOrDefault(kind.key(), List.of());
            List<String> now = table.entries(kind).stream().map(ShareTable.Entry::id).toList();
            assertTrue(now.size() >= base.size(), kind.key());
            assertEquals(base, now.subList(0, base.size()), kind.key());
        }
    }

    @Test
    void jmpTableStartsWithThePw2TableFiltered() {
        ShareTable table = DIRECTORY.byNamespace("justmoreparts").orElseThrow().table().orElseThrow();
        int total = 0;
        for (PartKind kind : Pw3Params.KINDS) {
            List<String> pw2 = new ArrayList<>();
            for (String id : Pw2Tables.parts(kind)) {
                if (id.startsWith("justmoreparts:")) {
                    pw2.add(id.substring("justmoreparts:".length()));
                }
            }
            total += pw2.size();
            List<String> now = table.entries(kind).stream().map(ShareTable.Entry::id).toList();
            assertEquals(pw2, now.subList(0, pw2.size()), kind.key());
            for (int i = 0; i < pw2.size(); i++) {
                assertEquals("1.0.0", table.entries(kind).get(i).since(), now.get(i));
            }
        }
        assertEquals(143, total);
    }

    @Test
    void everyFallbackIsABuiltinPartOfTheSameKind() {
        for (AddonDirectory.Addon addon : DIRECTORY.addons()) {
            if (addon.table().isEmpty()) {
                continue;
            }
            for (PartKind kind : Pw3Params.KINDS) {
                for (ShareTable.Entry entry : addon.table().get().entries(kind)) {
                    Identifier fallback = entry.fallback().orElseThrow(() -> new AssertionError(addon.namespace() + ":" + entry.id() + " ohne fallback"));
                    assertTrue(PartIndex.isBuiltin(fallback), fallback.toString());
                    assertEquals(kind, PartIndex.registeredById(fallback).orElseThrow().definition().kind(), entry.id());
                    assertTrue(entry.names().containsKey("en_us") && entry.names().containsKey("de_de"), entry.id());
                }
            }
        }
    }
}
