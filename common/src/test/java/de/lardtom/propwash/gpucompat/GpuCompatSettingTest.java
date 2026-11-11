package de.lardtom.propwash.gpucompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuCompatSettingTest {
    private static GpuCompatConfig load(String json) {
        GpuCompatConfig config = JsonConfig.read(new StringReader(json), GpuCompatConfig.class, null);
        assertNotNull(config);
        config.validate();
        return config;
    }

    @Test
    void threeChoicesInTheOrderOfTheButton() {
        assertEquals(List.of(GpuCompatMode.AUTO, GpuCompatMode.ON, GpuCompatMode.OFF), List.of(GpuCompatMode.values()));
        assertEquals(GpuCompatMode.AUTO, new GpuCompatConfig().mode);
        assertEquals("propwash.gpucompat.mode.auto", GpuCompatMode.AUTO.translationKey());
        assertEquals("propwash.gpucompat.mode.on.tooltip", GpuCompatMode.ON.tooltipKey());
    }

    @Test
    void storedChoicesSurviveAndUnknownValuesFallBackToAutomatic() {
        assertEquals(GpuCompatMode.ON, load("{\"mode\": \"ON\"}").mode);
        assertEquals(GpuCompatMode.OFF, load("{\"mode\": \"OFF\"}").mode);
        assertEquals(GpuCompatMode.AUTO, load("{\"mode\": \"AUTO\"}").mode);
        assertEquals(GpuCompatMode.AUTO, load("{\"mode\": \"INTEL\"}").mode);
        assertEquals(GpuCompatMode.AUTO, load("{\"mode\": null}").mode);
        assertEquals(GpuCompatMode.AUTO, load("{}").mode);
        GpuCompatConfig config = new GpuCompatConfig();
        config.mode = GpuCompatMode.OFF;
        assertEquals(GpuCompatMode.OFF, config.copy().mode);
        assertEquals(GpuCompatMode.OFF, load(new Gson().toJson(config)).mode);
    }

    @Test
    void textValuesForPropertyAndEnvironment() {
        assertEquals(GpuCompatMode.AUTO, GpuCompatMode.parse("auto"));
        assertEquals(GpuCompatMode.ON, GpuCompatMode.parse("AN"));
        assertEquals(GpuCompatMode.ON, GpuCompatMode.parse("true"));
        assertEquals(GpuCompatMode.OFF, GpuCompatMode.parse(" off "));
        assertEquals(GpuCompatMode.OFF, GpuCompatMode.parse("0"));
        assertNull(GpuCompatMode.parse("vielleicht"));
        assertNull(GpuCompatMode.parse(null));
    }

    @Test
    void everyTextExistsInBothLanguages() throws IOException {
        List<String> keys = new ArrayList<>(List.of("propwash.gpucompat.section", "propwash.gpucompat.setting", "propwash.gpucompat.setting.tip",
                "propwash.gpucompat.state.on", "propwash.gpucompat.state.off", "propwash.gpucompat.state.override", "propwash.gpucompat.state.restart",
                "propwash.gpucompat.state.paths_broken", "propwash.gpucompat.state.paths_none", "propwash.gpucompat.read.broken.chat",
                "propwash.gpucompat.photo.failed"));
        for (GpuCompatMode mode : GpuCompatMode.values()) {
            keys.add(mode.translationKey());
            keys.add(mode.tooltipKey());
        }
        for (GpuCompatDecision.Reason reason : GpuCompatDecision.Reason.values()) {
            keys.add(reason.key());
        }
        for (String language : List.of("en_us", "de_de")) {
            JsonObject strand = read(TestPaths.lang("intel-compat").resolve(language + ".json"));
            for (String key : keys) {
                assertTrue(strand.has(key), language + " " + key);
            }
            assertEquals(keys.size(), strand.size(), language + ": unbenutzte Schlüssel");
            assertEquals(2, count(strand.get("propwash.gpucompat.read.broken.chat").getAsString(), "%s"), language);
            assertEquals(1, count(strand.get("propwash.gpucompat.state.on").getAsString(), "%s"), language);
        }
        String german = Files.readString(TestPaths.lang("intel-compat").resolve("de_de.json"), StandardCharsets.UTF_8);
        assertTrue(german.contains("Grafik-Kompatibilität"));
        assertTrue(german.contains("Rücklesen"));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
