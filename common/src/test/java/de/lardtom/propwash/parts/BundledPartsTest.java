package de.lardtom.propwash.parts;

import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BuiltinParts;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.network.PartsManifestPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledPartsTest {
    private static final String MOTOR = "{\"mass_g\": 52.0, \"stator\": \"2806.5\", \"kv\": 1300, \"mount\": \"propwash:m28\", \"max_current_a\": 48.0,"
            + " \"resistance_ohm\": 0.062, \"cells\": [6, 8]}";
    private static final String CHEMISTRY = "{\"full_v\": 4.4, \"nominal_v\": 3.85, \"empty_v\": 3.5, \"cutoff_v\": 3.3, \"storage_v\": 3.9,"
            + " \"density_g_cm3\": 2.1, \"ocv_shape\": [0.0, 0.13, 0.2, 0.29, 0.34, 0.39, 0.43, 0.49, 0.57, 0.69, 0.83, 1.0]}";

    private static Map<String, byte[]> files(String... pathAndText) {
        Map<String, byte[]> files = new TreeMap<>();
        for (int i = 0; i < pathAndText.length; i += 2) {
            files.put(pathAndText[i], pathAndText[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }

    @Test
    void addonFilesDecodeInContractOrder() {
        Map<String, byte[]> files = files(
                "data/justmoreparts/propwash_parts/motor/m2806_1300.json", MOTOR,
                "data/justmoreparts/propwash_parts/chemistry/lihv_plus.json", CHEMISTRY,
                "data/justmoreparts/recipe/m2806_1300.json", "{\"type\": \"minecraft:crafting_shaped\"}",
                "data/other/propwash_parts/motor/m1103_11000.json", MOTOR);
        assertEquals(3, BundledParts.load("justmoreparts", files, false));
    }

    @Test
    void propwashSkipsItsOwnBuiltinFiles() {
        Map<String, byte[]> files = files(
                BuiltinParts.resource("motor", "m2207_1750"), "{\"kaputt\": true}",
                "data/propwash_example/propwash_parts/motor/m2806_1300.json", MOTOR);
        assertEquals(1, BundledParts.load("propwash", files, false));
        assertThrows(PartDataException.class, () -> BundledParts.load("justmoreparts", files, false));
    }

    @Test
    void allBrokenFilesAreReportedTogether() {
        Map<String, byte[]> files = files(
                "data/justmoreparts/propwash_parts/motor/m2806_1300.json", "{\"mass_g\": 52.0}",
                "data/justmoreparts/propwash_parts/rotor/wing.json", MOTOR,
                "data/justmoreparts/propwash_parts/motor/extra/deep.json", MOTOR,
                "data/justmoreparts/propwash_parts/battery/Big.json", "{}",
                "data/justmoreparts/propwash_parts/frame/broken.json", "{ nicht json");
        PartDataException error = assertThrows(PartDataException.class, () -> BundledParts.load("justmoreparts", files, false));
        String message = error.getMessage();
        assertTrue(message.contains("motor/m2806_1300.json – kv: Pflichtfeld fehlt"), message);
        assertTrue(message.contains("unbekannte Kategorie \"rotor\""), message);
        assertTrue(message.contains("ohne Unterordner"), message);
        assertTrue(message.contains("ungültiger Dateiname"), message);
        assertTrue(message.contains("frame/broken.json – ungültiges JSON"), message);
        assertTrue(error.problems().size() >= 5, message);
    }

    @Test
    void idRulesProtectTheBuiltinNamespace() {
        assertThrows(IllegalArgumentException.class, () -> PartIndex.validateId(Identifier.fromNamespaceAndPath("propwash", "m9999"), "Bauteil"));
        assertThrows(IllegalArgumentException.class, () -> PartIndex.validateId(Identifier.fromNamespaceAndPath("justmoreparts", "motor/m1"), "Bauteil"));
        PartIndex.validateId(Identifier.fromNamespaceAndPath("justmoreparts", "m2306.5-1950_v2"), "Bauteil");
        FrameDefinition frame = FrameDefinition.builder(Identifier.fromNamespaceAndPath("propwash", "sneaky"))
                .mass(100.0).wheelbase(200).maxProp(127).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30).aero(0.01, 0.9)
                .batteryFit(new de.lardtom.propwash.api.part.BatteryFitRule(4, 6, 250.0, 6, 0)).build();
        int before = PartIndex.all(PartKind.FRAME).size();
        assertThrows(IllegalArgumentException.class, () -> PartIndex.register(frame));
        assertEquals(before, PartIndex.all(PartKind.FRAME).size());
    }

    @Test
    void handshakeFindsEveryKindOfDifference() {
        PartsManifestPayload server = new PartsManifestPayload("aaa", List.of(
                new PartsManifestPayload.Entry("motor", Identifier.fromNamespaceAndPath("jmp", "m1"), "01"),
                new PartsManifestPayload.Entry("motor", Identifier.fromNamespaceAndPath("jmp", "m2"), "02"),
                new PartsManifestPayload.Entry("chemistry", Identifier.fromNamespaceAndPath("jmp", "lihv_plus"), "03")));
        PartsManifestPayload client = new PartsManifestPayload("bbb", List.of(
                new PartsManifestPayload.Entry("motor", Identifier.fromNamespaceAndPath("jmp", "m1"), "01"),
                new PartsManifestPayload.Entry("motor", Identifier.fromNamespaceAndPath("jmp", "m2"), "99"),
                new PartsManifestPayload.Entry("frame", Identifier.fromNamespaceAndPath("jmp", "deadcat"), "04")));
        PartsHandshake.Diff diff = PartsHandshake.compare(server, client);
        assertFalse(diff.matches());
        assertEquals(List.of(Identifier.fromNamespaceAndPath("jmp", "lihv_plus")), diff.missingOnClient());
        assertEquals(List.of(Identifier.fromNamespaceAndPath("jmp", "deadcat")), diff.missingOnServer());
        assertEquals(List.of(Identifier.fromNamespaceAndPath("jmp", "m2")), diff.different());
        assertTrue(PartsHandshake.compare(server, new PartsManifestPayload("aaa", List.of())).matches());
        PartsHandshake.Diff builtins = PartsHandshake.compare(new PartsManifestPayload("aaa", List.of()), new PartsManifestPayload("ccc", List.of()));
        assertTrue(builtins.builtinsDiffer());
        assertFalse(builtins.matches());
        assertFalse(PartsHandshake.message(diff).getString().isBlank());
    }

    @Test
    void manifestMatchesItselfAndSurvivesTheWire() {
        PartsManifestPayload local = PartsHandshake.localManifest();
        assertEquals(PartIndex.fingerprint(), local.fingerprint());
        assertTrue(PartsHandshake.compare(local, local).matches());
        ByteBuf buffer = Unpooled.buffer();
        PartsManifestPayload withEntries = new PartsManifestPayload(local.fingerprint(), List.of(
                new PartsManifestPayload.Entry("motor", Identifier.fromNamespaceAndPath("jmp", "m1"), PartIndex.hashOf("x"))));
        PartsManifestPayload.CODEC.encode(buffer, withEntries);
        assertEquals(withEntries, PartsManifestPayload.CODEC.decode(buffer));
    }
}
