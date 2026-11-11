package de.lardtom.propwash.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovComboMissingAddonsTest {
    private static final String NAMESPACE = "mobdefense";
    private static final String FILE = "cov/cov-combo/mobdefense-codes.json";
    private static final Map<AccessorySlot, String> PARTS = Map.of(AccessorySlot.FRONT, "dispenser_mount", AccessorySlot.TOP, "rocket_booster",
            AccessorySlot.BOTTOM, "spear_mount");

    private static JsonObject fixture() throws IOException {
        for (Path candidate : List.of(Path.of("common/src/test/resources", FILE), Path.of("src/test/resources", FILE))) {
            if (Files.isRegularFile(candidate)) {
                return JsonParser.parseString(Files.readString(candidate, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        throw new IOException("Fixture fehlt: " + FILE);
    }

    private static MissingAddons.Line line(String code, ShareTables tables) {
        ShareDecoded decoded = ShareFormat.decode(code, tables);
        assertTrue(decoded.ok(), "Code ohne Mob Defense nicht lesbar: " + decoded.status());
        BuildSpec build = decoded.content().orElseThrow().build();
        assertEquals(PARTS.keySet(), build.accessories().keySet());
        build.accessories().values().forEach(id -> assertTrue(id.toString().contains(NAMESPACE), "Halterung " + id + " ohne Bezug zu " + NAMESPACE));
        assertEquals(3, decoded.unknownParts().stream().filter(id -> id.toString().contains(NAMESPACE)).count(), "unbekannte Teile " + decoded.unknownParts());
        List<MissingAddons.Line> lines = MissingAddons.of(decoded, tables, "en_us").stream().filter(l -> l.key().equals(NAMESPACE)).toList();
        assertEquals(1, lines.size(), "genau eine Zeile für " + NAMESPACE + ": " + lines);
        MissingAddons.Line line = lines.get(0);
        assertEquals(MissingAddons.Kind.MISSING, line.kind());
        assertEquals(Optional.of(NAMESPACE), line.namespace());
        assertEquals(3, line.parts().size());
        assertTrue(line.parts().stream().allMatch(part -> part.kind() == PartKind.ACCESSORY && part.id().toString().contains(NAMESPACE)), "Teile " + line.parts());
        assertTrue(line.requiredVersion().isPresent(), "Version aus dem Code");
        return line;
    }

    @Test
    void codeWithAddonInfoNamesModVersionAndLinkFromTheCode() throws IOException {
        JsonObject fixture = fixture();
        MissingAddons.Line line = line(fixture.get("withInfo").getAsString(), ShareTables.frozen());
        assertEquals(fixture.get("modName").getAsString(), line.name());
        assertFalse(line.links().isEmpty(), "Link aus dem Code");
        assertTrue(line.links().stream().noneMatch(MissingAddons.Link::official), "Link aus dem Code ist kein offizieller Verzeichnis-Link: " + line.links());
        assertTrue(fixture.get("modVersion").getAsString().startsWith(line.requiredVersion().orElseThrow().split("-")[0]),
                line.requiredVersion() + " gegen " + fixture.get("modVersion").getAsString());
    }

    @Test
    void codeWithoutAddonInfoOnlyNamesTheModId() throws IOException {
        JsonObject fixture = fixture();
        MissingAddons.Line line = line(fixture.get("withoutInfo").getAsString(), ShareTables.frozen());
        assertEquals(NAMESPACE, line.name());
        assertTrue(line.links().isEmpty(), "ohne Add-on-Infos kein Link: " + line.links());
        assertFalse(line.hasClickableLink());
    }

    @Test
    void codeWithoutAddonInfoIsShorter() throws IOException {
        JsonObject fixture = fixture();
        assertTrue(fixture.get("withoutInfo").getAsString().length() < fixture.get("withInfo").getAsString().length());
    }

    @Test
    void stillMissingWithTheBuiltInDirectoryOnly() throws IOException {
        JsonObject fixture = fixture();
        ShareTables builtin = ShareTables.builder().installed(ShareTables.propwashTable()).directory(AddonDirectory.builtin()).build();
        assertEquals(fixture.get("modName").getAsString(), line(fixture.get("withInfo").getAsString(), builtin).name());
        assertEquals(NAMESPACE, line(fixture.get("withoutInfo").getAsString(), builtin).name());
    }
}
