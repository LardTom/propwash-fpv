package de.lardtom.propwash.share;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyReadTest {
    private static final ShareTables OFFLINE = ShareTables.builder().build();

    private static Map<String, Integer> check(String file, String format, int count) {
        List<JsonObject> vectors = ShareTestData.vectors(file);
        assertEquals(count, vectors.size(), file);
        Map<String, Integer> groups = new TreeMap<>();
        for (JsonObject vector : vectors) {
            String name = vector.get("name").getAsString();
            ShareDecoded decoded = ShareFormat.decode(vector.get("code").getAsString(), OFFLINE, false);
            assertEquals(vector.get("status").getAsString(), decoded.status().name(), name);
            assertEquals(format, vector.get("format").getAsString(), name);
            groups.merge(vector.get("group").getAsString(), 1, Integer::sum);
            if (!decoded.ok()) {
                continue;
            }
            assertEquals(format, decoded.format(), name);
            assertEquals(ShareTestData.stringList(vector.get("layers")), decoded.layers().stream().map(ShareLayer::key).toList(), name);
            assertEquals(ShareTestData.stringList(vector.get("skipped")), decoded.skipped(), name);
            assertEquals(vector.getAsJsonObject("content"), ShareTestData.content(decoded.require()), name);
            assertEquals(ShareTestData.content(vector.getAsJsonObject("content")), decoded.require(), name);
        }
        return groups;
    }

    @Test
    void everyPw1VectorFromJmp111ReadsTheSame() {
        Map<String, Integer> groups = check("pw1-vectors.json", "PW1", 306);
        assertEquals(7, groups.get("spec"));
        assertEquals(61, groups.get("published-1.0.0"));
        assertEquals(135, groups.get("preset"));
    }

    @Test
    void everyPw2VectorFromJmp111ReadsTheSame() {
        Map<String, Integer> groups = check("pw2-vectors.json", "PW2", 447);
        assertEquals(9, groups.get("spec"));
        assertEquals(135, groups.get("preset"));
        assertEquals(300, groups.get("random"));
    }

    @Test
    void floatValuesAreBitExact() {
        for (String file : List.of("pw1-vectors.json", "pw2-vectors.json")) {
            for (JsonObject vector : ShareTestData.vectors(file)) {
                if (!vector.get("status").getAsString().equals("OK") || vector.getAsJsonObject("content").get("tune").isJsonNull()) {
                    continue;
                }
                ShareContent expected = ShareTestData.content(vector.getAsJsonObject("content"));
                ShareContent actual = ShareFormat.decode(vector.get("code").getAsString(), OFFLINE, false).require();
                for (Map.Entry<String, Float> entry : expected.tune().orElseThrow().values().entrySet()) {
                    assertEquals(Float.floatToRawIntBits(entry.getValue()), Float.floatToRawIntBits(actual.tune().orElseThrow().values().get(entry.getKey())),
                            vector.get("name").getAsString() + " " + entry.getKey());
                }
            }
        }
    }

    @Test
    void legacyCodesKeepTheirFormatAndStatusSet() {
        ShareDecoded pw1 = ShareFormat.decode("PW1-04002-GR11D-K74SB-5EDT7-JV35B-WTG22-3KEHG-P6TTZ-6CR02-2KD68-S30DT-Z64VK-AC010-SR3AC-AZ6DH-022V1-DSGPR-VV7BX"
                + "-S62RV-50446-4DKKB-WRK6C-1G000-H23NQ", OFFLINE, false);
        assertTrue(pw1.ok());
        assertTrue(pw1.legacy());
        assertEquals("PW1", pw1.format());
        assertEquals("PW2", ShareFormat.decode("pw205zwQBrAS4", OFFLINE, false).format());
        assertEquals(ShareStatus.CHECKSUM, ShareFormat.decode("PW205zwQCrAS4", OFFLINE, false).status());
    }
}
