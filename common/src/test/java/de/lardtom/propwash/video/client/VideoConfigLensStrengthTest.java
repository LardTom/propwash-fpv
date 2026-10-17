package de.lardtom.propwash.video.client;

import com.google.gson.Gson;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.video.FisheyeLens;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VideoConfigLensStrengthTest {
    private static VideoConfig load(String json) {
        VideoConfig config = JsonConfig.read(new StringReader(json), VideoConfig.class, VideoConfig::migrate);
        assertNotNull(config);
        config.validate();
        return config;
    }

    @Test
    void defaultIsTheFisheyeOf052() {
        assertEquals(FisheyeLens.DEFAULT_PERCENT, new VideoConfig().lensStrength);
        assertEquals(80, FisheyeLens.DEFAULT_PERCENT);
        assertEquals(FisheyeLens.DEFAULT_PERCENT, load("{\"effectStrength\": 0.4}").lensStrength);
    }

    @Test
    void oldToggleIsMigrated() {
        assertEquals(0, load("{\"lens\": \"FLAT\"}").lensStrength);
        assertEquals(FisheyeLens.DEFAULT_PERCENT, load("{\"lens\": \"FISHEYE\"}").lensStrength);
        assertEquals(FisheyeLens.DEFAULT_PERCENT, load("{\"lens\": null}").lensStrength);
        assertEquals(35, load("{\"lens\": \"FLAT\", \"lensStrength\": 35}").lensStrength);
    }

    @Test
    void strengthIsClampedAndSurvivesCopyAndSave() {
        assertEquals(100, load("{\"lensStrength\": 250}").lensStrength);
        assertEquals(0, load("{\"lensStrength\": -5}").lensStrength);
        VideoConfig config = new VideoConfig();
        config.lensStrength = 45;
        assertEquals(45, config.copy().lensStrength);
        Gson gson = new Gson();
        assertEquals(45, load(gson.toJson(config)).lensStrength);
    }
}
