package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.platform.ModMeta;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CovPw3ShareTest {
    private static final String JMP = "justmoreparts";
    private static final String ADDON_OSD = "mobdefense:dispenser";
    private static final Identifier NEW_MOTOR = Identifier.fromNamespaceAndPath(JMP, "m0802_27200");
    private static final Identifier FALLBACK_MOTOR = Identifier.parse("propwash:m0802_22000");
    private static final List<String[]> NEW_MOTORS = List.of(
            new String[]{"m0702_30000", "0702 Motor, 30000 KV", "0702-Motor, 30000 KV"},
            new String[]{"m0702_35000", "0702 Motor, 35000 KV", "0702-Motor, 35000 KV"},
            new String[]{"m0802_27200", "0802 Motor, 27200 KV", "0802-Motor, 27200 KV"});

    private static BuildSpec freestyle() {
        return Pw3VectorFiles.legacy("preset/propwash:freestyle/parts").build();
    }

    private static String vector(String name) {
        for (JsonObject vector : ShareTestData.vectors("pw3-vectors.json")) {
            if (vector.get("name").getAsString().equals(name)) {
                return vector.get("code").getAsString();
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static String osdCode(OsdConfig sender) {
        String json = OsdLayoutCodec.encode(sender);
        ShareContent content = new ShareContent(freestyle(), Optional.empty(), Optional.empty(), Optional.of(new ShareOsd(OsdLayoutCodec.VERSION, json)),
                Optional.empty());
        return Pw3Codec.encode(content, ShareTables.frozen());
    }

    private static OsdConfig withAddonSlot(boolean enabled, int col, int row) {
        OsdConfig config = OsdConfig.defaults();
        config.elements.put(ADDON_OSD, new OsdConfig.Slot(enabled, col, row));
        OsdConfig.sanitize(config);
        return config;
    }

    @Test
    void addonOsdEntryDoesNotBreakTheImportAndStaysAtTheReceiver() {
        OsdConfig sender = withAddonSlot(true, 1, 12);
        sender.move(de.lardtom.propwash.osd.OsdElement.SPEED, 3, 9);
        ShareDecoded decoded = ShareFormat.decode(osdCode(sender), ShareTables.frozen());
        assertTrue(decoded.ok(), decoded.status().toString());
        String json = decoded.require().osd().orElseThrow().json();
        OsdConfig receiver = withAddonSlot(true, 1, 12);
        OsdConfig applied = OsdLayoutCodec.decode(json, receiver);
        OsdConfig.Slot slot = applied.elements.get(ADDON_OSD);
        assertNotNull(slot, "Add-on-Eintrag beim Empfänger verloren");
        assertTrue(slot.enabled);
        assertEquals(1, slot.col);
        assertEquals(12, slot.row);
        assertEquals(3, applied.slot(de.lardtom.propwash.osd.OsdElement.SPEED).col);
        assertEquals(9, applied.slot(de.lardtom.propwash.osd.OsdElement.SPEED).row);
    }

    @Test
    void addonOsdEntryTravelsInTheSharecode() {
        OsdConfig sender = withAddonSlot(true, 1, 12);
        ShareDecoded decoded = ShareFormat.decode(osdCode(sender), ShareTables.frozen());
        assertTrue(decoded.ok(), decoded.status().toString());
        String json = decoded.require().osd().orElseThrow().json();
        assertTrue(json.contains(ADDON_OSD), "OSD-Ebene ohne " + ADDON_OSD + ": " + json);
        OsdConfig fresh = OsdLayoutCodec.decode(json, OsdConfig.defaults());
        OsdConfig.Slot slot = fresh.elements.get(ADDON_OSD);
        assertNotNull(slot, "Schlüssel " + ADDON_OSD + " fehlt nach dem Anwenden ohne angemeldetes Add-on");
        assertTrue(slot.enabled);
        assertEquals(1, slot.col);
        assertEquals(12, slot.row);
        OsdConfig moved = OsdLayoutCodec.decode(json, withAddonSlot(false, 20, 3));
        assertTrue(moved.elements.get(ADDON_OSD).enabled);
        assertEquals(1, moved.elements.get(ADDON_OSD).col);
        assertEquals(12, moved.elements.get(ADDON_OSD).row);
    }

    @Test
    void cameraAngleTravelsWithTheTuneLayer() {
        ShareDecoded p3 = ShareFormat.decode(vector("P3_all_layers"), ShareTables.frozen());
        assertTrue(p3.ok(), p3.status().toString());
        TuneSpec deadcat = p3.require().tune().orElseThrow();
        assertEquals(35.0F, deadcat.values().get(TuneSpec.CAMERA_UPTILT), 1.0E-4F);
        assertEquals(35.0F, CameraMount.uptilt(DroneBuild.FREESTYLE, TuneSpec.EMPTY.overlay(deadcat, Pw3Params.TUNE_GROUPS).values()), 1.0E-4F);
        BuildSpec own = Conversions.spec(DroneBuild.FREESTYLE);
        DroneData mine = new DroneData(own, PaintSpec.DEFAULT, new TuneSpec(Map.of(TuneSpec.CAMERA_UPTILT, 42.0F, "rates.roll.rcRate", 1.3F)),
                Optional.empty());
        String with = Pw3Codec.encode(ShareContent.of(mine, EnumSet.of(ShareLayer.PARTS, ShareLayer.TUNE), Optional.empty()), ShareTables.frozen());
        String without = Pw3Codec.encode(ShareContent.of(mine, EnumSet.of(ShareLayer.PARTS), Optional.empty()), ShareTables.frozen());
        ShareContent a = ShareFormat.decode(with, ShareTables.frozen()).require();
        ShareContent b = ShareFormat.decode(without, ShareTables.frozen()).require();
        TuneSpec imported = TuneSpec.EMPTY.overlay(a.tune().orElseThrow(), Pw3Params.TUNE_GROUPS);
        assertEquals(42.0F, CameraMount.uptilt(DroneBuild.FREESTYLE, imported.values()), 1.0E-4F);
        assertTrue(b.tune().isEmpty(), b.toString());
        float standard = CameraMount.defaultUptilt(DroneBuild.FREESTYLE.frame());
        assertEquals(standard, CameraMount.uptilt(DroneBuild.FREESTYLE, Map.of()), 1.0E-4F);
        assertNotEquals(42.0F, standard);
    }

    @Test
    void newerJmpMotorWithJmp111AgainstTheCurrentSnapshot() {
        AddonDirectory snapshot = AddonDirectory.builtin();
        assumeFalse(snapshotKnows(snapshot), "Verzeichnis-Abzug kennt " + NEW_MOTOR + " schon: Fall nach dem Abgleich (R) gilt");
        String code = newMotorCode(updatedDirectory());
        ShareTables jmp111 = jmp111(snapshot);
        ShareDecoded decoded = ShareFormat.decode(code, jmp111);
        assertTrue(decoded.ok(), decoded.status().toString());
        NamespaceReport report = jmpReport(decoded);
        assertEquals(NamespaceReport.Result.NEWER, report.result());
        assertEquals(Optional.of("1.2.0"), report.minVersion());
        assertEquals(Optional.of("1.1.1"), report.installedVersion());
        MissingAddons.Line line = jmpLine(decoded, jmp111, "de_de");
        assertEquals(MissingAddons.Kind.OLDER, line.kind());
        assertEquals(Optional.of("1.2.0"), line.requiredVersion());
        assertEquals(Optional.of("1.1.1"), line.installedVersion());
        assertEquals("Propwash: Just More Parts", line.name());
        assertEquals(1, decoded.unknownParts().size(), decoded.unknownParts().toString());
        Identifier unknown = decoded.unknownParts().getFirst();
        assertTrue(Pw3Codec.unknownMarker(unknown), unknown.toString());
        assertEquals(PartKind.MOTOR, line.parts().getFirst().kind());
        assertEquals(37, line.parts().getFirst().number());
    }

    @Test
    void newerJmpMotorAfterTheSnapshotUpdateNamesThePartAndOffersThePropwashMotor() {
        AddonDirectory updated = updatedDirectory();
        assertEquals(List.of(), updated.problems());
        ShareTables jmp111 = jmp111(updated);
        ShareDecoded decoded = ShareFormat.decode(newMotorCode(updated), jmp111);
        assertTrue(decoded.ok(), decoded.status().toString());
        assertEquals(List.of(NEW_MOTOR), decoded.unknownParts());
        MissingAddons.Part de = jmpLine(decoded, jmp111, "de_de").parts().stream().filter(p -> p.kind() == PartKind.MOTOR).findFirst().orElseThrow();
        assertEquals(NEW_MOTOR, de.id());
        assertEquals(Optional.of("0802-Motor, 27200 KV"), de.name());
        assertEquals(Optional.of(FALLBACK_MOTOR), de.fallback());
        MissingAddons.Part en = jmpLine(decoded, jmp111, "en_us").parts().stream().filter(p -> p.kind() == PartKind.MOTOR).findFirst().orElseThrow();
        assertEquals(Optional.of("0802 Motor, 27200 KV"), en.name());
        Substitutes.Result substitutes = Substitutes.find(decoded.require().build(), jmp111);
        assertTrue(substitutes.found(), substitutes.toString());
        assertEquals(FALLBACK_MOTOR, substitutes.build().orElseThrow().motor());
        assertTrue(substitutes.swaps().stream().anyMatch(s -> s.from().equals(NEW_MOTOR) && s.to().equals(Optional.of(FALLBACK_MOTOR))),
                substitutes.swaps().toString());
        String older = Pw3Codec.encode(ShareContent.of(Pw3VectorFiles.legacy("preset/justmoreparts:whoop75_1s/parts").build()), tables120(updated));
        ShareDecoded old = ShareFormat.decode(older, jmp111);
        assertEquals(NamespaceReport.Result.MATCH, jmpReport(old).result());
        assertEquals(Optional.of("1.0.0"), jmpReport(old).minVersion());
        assertEquals(List.of(), old.unknownParts());
    }

    @Test
    @Disabled("cov-pw3-2")
    void newerJmpMotorAfterTheSnapshotUpdateStillNamesBothVersions() {
        AddonDirectory updated = updatedDirectory();
        ShareTables jmp111 = jmp111(updated);
        ShareDecoded decoded = ShareFormat.decode(newMotorCode(updated), jmp111);
        assertTrue(decoded.ok(), decoded.status().toString());
        assertEquals(NamespaceReport.Result.NEWER, jmpReport(decoded).result());
        MissingAddons.Line line = jmpLine(decoded, jmp111, "de_de");
        assertEquals(MissingAddons.Kind.OLDER, line.kind());
        assertEquals(Optional.of("1.2.0"), line.requiredVersion());
        assertEquals(Optional.of("1.1.1"), line.installedVersion());
    }

    private static boolean snapshotKnows(AddonDirectory directory) {
        return directory.byNamespace(JMP).flatMap(AddonDirectory.Addon::table)
                .map(table -> table.entries(PartKind.MOTOR).stream().anyMatch(e -> e.id().equals(NEW_MOTOR.getPath()))).orElse(false);
    }

    private static NamespaceReport jmpReport(ShareDecoded decoded) {
        return decoded.namespaces().stream().filter(r -> r.namespace().equals(Optional.of(JMP))).findFirst()
                .orElseThrow(() -> new AssertionError("kein Bericht für " + JMP + ": " + decoded.namespaces()));
    }

    private static MissingAddons.Line jmpLine(ShareDecoded decoded, ShareTables tables, String language) {
        return MissingAddons.of(decoded, tables, language).stream().filter(l -> l.namespace().equals(Optional.of(JMP))).findFirst()
                .orElseThrow(() -> new AssertionError("keine Zeile für " + JMP + ": " + MissingAddons.of(decoded, tables, language)));
    }

    private static String newMotorCode(AddonDirectory updated) {
        BuildSpec whoop = Pw3VectorFiles.legacy("preset/propwash:whoop/parts").build().withPart(PartKind.MOTOR, NEW_MOTOR);
        return Pw3Codec.encode(ShareContent.of(whoop), tables120(updated));
    }

    private static ShareTables tables120(AddonDirectory updated) {
        ShareTable table = updated.byNamespace(JMP).flatMap(AddonDirectory.Addon::table).orElseThrow();
        Map<Identifier, PartKind> kinds = builtinKinds();
        for (PartKind kind : Pw3Params.KINDS) {
            for (ShareTable.Entry entry : table.entries(kind)) {
                kinds.put(Identifier.fromNamespaceAndPath(JMP, entry.id()), kind);
            }
        }
        return ShareTables.builder().installed(ShareTables.propwashTable()).installed(table).version(JMP, "1.2.0").directory(updated)
                .parts(id -> Optional.ofNullable(kinds.get(id))).build();
    }

    private static ShareTables jmp111(AddonDirectory directory) {
        ShareTable released = AddonDirectory.builtin().byNamespace(JMP).flatMap(AddonDirectory.Addon::table).orElseThrow();
        Map<Identifier, PartKind> kinds = builtinKinds();
        for (PartKind kind : Pw3Params.KINDS) {
            for (ShareTable.Entry entry : released.entries(kind)) {
                if ("1.0.0".equals(entry.since())) {
                    kinds.put(Identifier.fromNamespaceAndPath(JMP, entry.id()), kind);
                }
            }
        }
        ShareTables.Loader loader = new ShareTables.Loader() {
            @Override
            public Map<String, byte[]> tables(String modId) {
                return Map.of();
            }

            @Override
            public Optional<String> version(String modId) {
                return Optional.of(modId.equals(JMP) ? "1.1.1" : "0.6.0");
            }

            @Override
            public Optional<ModMeta> meta(String modId) {
                return Optional.empty();
            }

            @Override
            public AddonDirectory directory() {
                return directory;
            }
        };
        return ShareTables.load(List.of(ShareTables.PROPWASH, JMP), loader, id -> Optional.ofNullable(kinds.get(id)), new ArrayList<>(kinds.keySet()));
    }

    private static Map<Identifier, PartKind> builtinKinds() {
        Map<Identifier, PartKind> kinds = new HashMap<>();
        for (PartInfo part : PartIndex.registered()) {
            if (part.builtin()) {
                kinds.put(part.id(), part.definition().kind());
            }
        }
        return kinds;
    }

    private static AddonDirectory updatedDirectory() {
        JsonObject root;
        try (InputStream in = AddonDirectory.class.getResourceAsStream(AddonDirectory.RESOURCE)) {
            assertNotNull(in, AddonDirectory.RESOURCE);
            root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        for (JsonElement element : root.getAsJsonArray("addons")) {
            JsonObject addon = element.getAsJsonObject();
            if (!addon.get("namespace").getAsString().equals(JMP)) {
                continue;
            }
            addon.addProperty("version", "1.2.0");
            JsonArray motors = addon.getAsJsonObject("table").getAsJsonObject("parts").getAsJsonArray("motor");
            for (String[] motor : NEW_MOTORS) {
                boolean present = false;
                for (JsonElement entry : motors) {
                    present |= entry.getAsJsonObject().get("id").getAsString().equals(motor[0]);
                }
                if (present) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("id", motor[0]);
                entry.addProperty("since", "1.2.0");
                entry.addProperty("fallback", FALLBACK_MOTOR.toString());
                JsonObject name = new JsonObject();
                name.addProperty("en_us", motor[1]);
                name.addProperty("de_de", motor[2]);
                entry.add("name", name);
                motors.add(entry);
            }
        }
        return AddonDirectory.parse(root.toString());
    }
}
