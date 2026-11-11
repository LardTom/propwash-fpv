package de.lardtom.propwash.video.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.testutil.TestPaths;
import de.lardtom.propwash.video.VideoCheck;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoEffectsSettingTest {
    private static VideoConfig load(String json) {
        VideoConfig config = JsonConfig.read(new StringReader(json), VideoConfig.class, VideoConfig::migrate);
        assertNotNull(config);
        config.validate();
        return config;
    }

    @Test
    void threeChoicesInTheOrderOfTheButton() {
        assertEquals(List.of(VideoConfig.EffectRenderer.AUTO, VideoConfig.EffectRenderer.FULL, VideoConfig.EffectRenderer.OVERLAY),
                List.of(VideoConfig.EffectRenderer.values()));
        assertEquals("propwash.video.config.renderer.automatic", VideoConfig.EffectRenderer.AUTO.translationKey());
        assertEquals("propwash.video.config.renderer.full", VideoConfig.EffectRenderer.FULL.translationKey());
        assertEquals("propwash.video.config.renderer.simple", VideoConfig.EffectRenderer.OVERLAY.translationKey());
        assertEquals("propwash.video.config.renderer.simple.tooltip", VideoConfig.EffectRenderer.OVERLAY.tooltipKey());
        assertEquals(VideoConfig.EffectRenderer.AUTO, new VideoConfig().renderer);
    }

    @Test
    void automaticFollowsTheImageCheck() {
        VideoConfig.EffectRenderer auto = VideoConfig.EffectRenderer.AUTO;
        assertEquals(VideoCheck.Level.FULL, auto.level(VideoCheck.Outcome.PENDING));
        assertEquals(VideoCheck.Level.FULL, auto.level(VideoCheck.Outcome.PASS));
        assertEquals(VideoCheck.Level.FULL, auto.level(VideoCheck.Outcome.UNAVAILABLE));
        assertEquals(VideoCheck.Level.SIMPLE, auto.level(VideoCheck.Outcome.FULL_BROKEN));
        assertEquals(VideoCheck.Level.NONE, auto.level(VideoCheck.Outcome.ALL_BROKEN));
    }

    @Test
    void fullStaysFullAndSimpleStaysSimple() {
        for (VideoCheck.Outcome outcome : VideoCheck.Outcome.values()) {
            assertEquals(VideoCheck.Level.FULL, VideoConfig.EffectRenderer.FULL.level(outcome), outcome.name());
            VideoCheck.Level simple = VideoConfig.EffectRenderer.OVERLAY.level(outcome);
            assertEquals(outcome == VideoCheck.Outcome.ALL_BROKEN ? VideoCheck.Level.NONE : VideoCheck.Level.SIMPLE, simple, outcome.name());
        }
    }

    @Test
    void savedChoicesSurviveIncludingTheOldOverlay() {
        assertEquals(VideoConfig.EffectRenderer.OVERLAY, load("{\"renderer\": \"OVERLAY\"}").renderer);
        assertEquals(VideoConfig.EffectRenderer.FULL, load("{\"renderer\": \"FULL\"}").renderer);
        assertEquals(VideoConfig.EffectRenderer.AUTO, load("{\"renderer\": \"AUTO\"}").renderer);
        assertEquals(VideoConfig.EffectRenderer.AUTO, load("{\"renderer\": \"FANCY\"}").renderer);
        assertEquals(VideoConfig.EffectRenderer.AUTO, load("{\"effectStrength\": 0.4}").renderer);
        VideoConfig config = new VideoConfig();
        config.renderer = VideoConfig.EffectRenderer.FULL;
        assertEquals(VideoConfig.EffectRenderer.FULL, config.copy().renderer);
        Gson gson = new Gson();
        assertEquals(VideoConfig.EffectRenderer.FULL, load(gson.toJson(config)).renderer);
    }

    @Test
    void everyChoiceIsTranslated() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject strand = read(TestPaths.lang("fix-intel-video").resolve(language + ".json"));
            JsonObject video = read(TestPaths.lang("video").resolve(language + ".json"));
            assertTrue(video.has("propwash.video.config.renderer"), language);
            for (VideoConfig.EffectRenderer renderer : VideoConfig.EffectRenderer.values()) {
                assertTrue(strand.has(renderer.translationKey()), language + " " + renderer.translationKey());
                assertTrue(strand.has(renderer.tooltipKey()), language + " " + renderer.tooltipKey());
                assertFalse(video.has(renderer.translationKey()), "doppelt in video/: " + renderer.translationKey());
            }
            for (String key : List.of("propwash.video.simplified.chat", "propwash.video.status.simplified", "propwash.video.status.simplified.none")) {
                assertTrue(strand.has(key), language + " " + key);
            }
            assertTrue(strand.get("propwash.video.simplified.chat").getAsString().contains("%s"), language);
        }
        String german = Files.readString(TestPaths.lang("fix-intel-video").resolve("de_de.json"), StandardCharsets.UTF_8);
        assertTrue(german.contains("Videoeffekte auf dieser Grafikkarte vereinfacht"));
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
