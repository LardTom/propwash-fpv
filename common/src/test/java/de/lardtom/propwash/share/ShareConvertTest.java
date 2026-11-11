package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareConvertTest {
    static final String FILE = "pw3-conversions.json";
    private static final ShareTables OFFLINE = ShareTables.builder().build();

    static JsonArray compute() {
        JsonArray out = new JsonArray();
        for (String file : List.of("pw1-vectors.json", "pw2-vectors.json")) {
            for (JsonObject vector : ShareTestData.vectors(file)) {
                String code = vector.get("code").getAsString();
                ShareConvert.Conversion conversion = ShareConvert.toPw3(code);
                JsonObject entry = new JsonObject();
                entry.addProperty("name", vector.get("format").getAsString() + "/" + vector.get("name").getAsString());
                entry.addProperty("from_format", vector.get("format").getAsString());
                entry.addProperty("from", code);
                entry.addProperty("status", conversion.status().name());
                entry.add("pw3", conversion.code().<com.google.gson.JsonElement>map(com.google.gson.JsonPrimitive::new).orElse(JsonNull.INSTANCE));
                entry.add("reason", conversion.reason().<com.google.gson.JsonElement>map(com.google.gson.JsonPrimitive::new).orElse(JsonNull.INSTANCE));
                out.add(entry);
            }
        }
        return out;
    }

    static JsonObject file(JsonArray conversions) {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("code_format", Pw3Params.FORMAT);
        root.addProperty("revision", Pw3Params.REVISION);
        root.addProperty("source", "ShareConvert.toPw3 mit eingefrorenen Daten (Tabelle von Propwash, JMP-Tabelle aus dem Verzeichnis-Abzug); Eingaben aus pw1-vectors.json und pw2-vectors.json");
        root.addProperty("count", conversions.size());
        root.add("vectors", conversions);
        return root;
    }

    @Test
    void everyLegacyGoldenConvertsByteIdentically() {
        JsonArray computed = compute();
        if (ShareTestData.regenerate()) {
            ShareTestData.write(FILE, file(computed));
        }
        JsonObject golden = ShareTestData.read(FILE);
        JsonArray expected = golden.getAsJsonArray("vectors");
        assertEquals(expected.size(), computed.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), computed.get(i), expected.get(i).getAsJsonObject().get("name").getAsString());
        }
        assertEquals(306 + 447, expected.size());
    }

    @Test
    void conversionsKeepTheContent() {
        int ok = 0;
        for (JsonObject entry : ShareTestData.vectors(FILE)) {
            if (!entry.get("status").getAsString().equals("OK")) {
                continue;
            }
            ok++;
            String name = entry.get("name").getAsString();
            ShareContent legacy = ShareFormat.decode(entry.get("from").getAsString(), OFFLINE, false).require();
            String pw3 = entry.get("pw3").getAsString();
            assertTrue(pw3.startsWith("PW3-"), name);
            ShareDecoded decoded = ShareFormat.decode(pw3, ShareTables.frozen(), false);
            assertEquals(ShareStatus.OK, decoded.status(), name);
            assertEquals(legacy, decoded.require(), name);
            for (NamespaceReport report : decoded.namespaces()) {
                assertTrue(report.result() == NamespaceReport.Result.MATCH || report.result() == NamespaceReport.Result.MISSING
                        || report.result() == NamespaceReport.Result.NO_TABLE, name + " " + report);
                assertTrue(report.codeName().isEmpty(), name);
            }
            assertEquals(pw3, Pw3Codec.encode(legacy, ShareTables.frozen(), Pw3Codec.Options.NO_INFO, Pw3Carry.EMPTY), name);
        }
        assertTrue(ok > 740, "umgerechnet: " + ok);
    }

    @Test
    void presetsGiveTheSameCodeFromPw1AndPw2() {
        Map<String, String> fromPw1 = new HashMap<>();
        Map<String, String> fromPw2 = new HashMap<>();
        for (JsonObject entry : ShareTestData.vectors(FILE)) {
            String name = entry.get("name").getAsString();
            if (!name.contains("/preset/")) {
                continue;
            }
            String key = name.substring(name.indexOf("/preset/"));
            (entry.get("from_format").getAsString().equals("PW1") ? fromPw1 : fromPw2).put(key, entry.get("pw3").getAsString());
        }
        assertEquals(135, fromPw1.size());
        assertEquals(fromPw1, fromPw2);
    }

    @Test
    void handBuiltUnknownsAreNotConvertible() {
        Map<String, String> reasons = new HashMap<>();
        for (JsonObject entry : ShareTestData.vectors(FILE)) {
            if (entry.get("status").getAsString().equals("NOT_CONVERTIBLE")) {
                reasons.put(entry.get("name").getAsString(), entry.get("reason").getAsString());
            }
        }
        assertEquals("pw2_unknown:frame.200", reasons.get("PW2/PW2_E_tolerant_skips"));
        assertEquals("pw1_unknown:7", reasons.get("PW1/special/pw1_unknown_namespace"));
        assertEquals("pw1_unknown:7", reasons.get("PW2/special/pw2_carries_pw1_unknown_namespace"));
        assertEquals(3, reasons.size(), reasons.toString());
        assertEquals(ShareStatus.CHECKSUM, ShareConvert.toPw3("PW205zwQCrAS4").status());
        assertEquals(ShareStatus.OTHER_CODE, ShareConvert.toPw3("PWT1-abc").status());
        assertEquals(ShareStatus.EMPTY, ShareConvert.toPw3("  ").status());
    }

    @Test
    void pw3InputStaysAsItIs() {
        String code = ShareConvert.toPw3("PW205zwQBrAS4").code().orElseThrow();
        ShareConvert.Conversion again = ShareConvert.toPw3(code.toLowerCase().substring(0, 4) + code.substring(4));
        assertEquals(ShareStatus.OK, again.status());
        assertEquals(code, again.code().orElseThrow());
        assertEquals(code, ShareConvert.toPw3(code.substring(4)).code().orElseThrow());
        assertNotEquals(true, again.converted());
        assertTrue(ShareConvert.toPw3("PW205zwQBrAS4").converted());
    }
}
