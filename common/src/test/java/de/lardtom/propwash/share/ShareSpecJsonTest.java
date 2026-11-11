package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareSpecJsonTest {
    @Test
    void specDescribesTheFrozenParameters() {
        JsonObject spec = ShareSpecJson.spec();
        assertEquals("PW3-", spec.get("prefix").getAsString());
        assertEquals(40, spec.get("id_alphabet").getAsString().length());
        assertEquals(57, spec.getAsJsonArray("tune_keys").size());
        assertEquals(51, spec.getAsJsonArray("colors").size());
        CRC32 crc = new CRC32();
        crc.update(spec.get("osd_dictionary").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0xb87eac81L, crc.getValue());
        assertEquals("propwash", spec.getAsJsonObject("propwash_table").get("namespace").getAsString());
        assertEquals(2, spec.getAsJsonObject("directory").getAsJsonArray("addons").size());
        assertEquals(44, spec.getAsJsonObject("pw2").getAsJsonObject("parts").getAsJsonArray("prop").size());
        assertEquals(3, spec.getAsJsonObject("pw1").getAsJsonArray("namespaces").size());
        assertEquals(ShareStatus.values().length, spec.getAsJsonArray("statuses").size());
    }

    @Test
    void presetVectorsDecodeToTheirContent() {
        List<DronePreset> presets = PropwashApi.builds().presets();
        JsonArray vectors = ShareSpecJson.vectors(presets, ShareTables.builtin());
        assertEquals(presets.size() * ShareSpecJson.VARIANTS.size(), vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            JsonObject vector = vectors.get(i).getAsJsonObject();
            assertEquals("OK", vector.get("status").getAsString(), vector.toString());
            assertTrue(vector.get("code").getAsString().startsWith("PW3-"));
            assertEquals("MATCH", vector.getAsJsonArray("namespaces").get(0).getAsJsonObject().get("result").getAsString());
        }
        assertEquals(ShareTestData.content(ShareFormat.decode(vectors.get(0).getAsJsonObject().get("code").getAsString(), ShareTables.builtin()).require()),
                vectors.get(0).getAsJsonObject().getAsJsonObject("decoded"));
    }
}
