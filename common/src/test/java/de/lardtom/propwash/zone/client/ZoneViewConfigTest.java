package de.lardtom.propwash.zone.client;

import com.google.gson.Gson;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.zone.ZoneView;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ZoneViewConfigTest {
    private static ZoneViewConfig load(String json) {
        ZoneViewConfig config = JsonConfig.read(new StringReader(json), ZoneViewConfig.class, ZoneViewConfig::migrate);
        assertNotNull(config);
        ZoneViewConfig.validate(config);
        return config;
    }

    private static ZoneViewConfig saveAndLoad(ZoneViewConfig config) {
        return load(new Gson().toJson(config));
    }

    @Test
    void defaultIsWithToolOrInFlight() {
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, ZoneView.DEFAULT_SHOW);
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, new ZoneViewConfig().show);
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, load("{}").show);
    }

    @Test
    void choicesRunFromFewToMany() {
        assertEquals(List.of(ZoneView.Show.TOOL, ZoneView.Show.TOOL_OR_FLIGHT, ZoneView.Show.ALWAYS), List.of(ZoneView.Show.values()));
        assertEquals("tool_or_flight", ZoneView.Show.TOOL_OR_FLIGHT.key());
    }

    @Test
    void savedToolFromRc3IsRaisedToTheNewDefault() {
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, load("{\"show\": \"TOOL\"}").show);
    }

    @Test
    void raisingHappensOnlyOnceSoAChosenToolStays() {
        ZoneViewConfig raised = saveAndLoad(load("{\"show\": \"TOOL\"}"));
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, raised.show, "nach dem ersten Speichern bleibt der neue Standard");
        ZoneViewConfig chosen = raised.copy();
        chosen.show = ZoneView.Show.TOOL;
        assertEquals(ZoneView.Show.TOOL, saveAndLoad(chosen).show, "danach gewähltes „Nur mit Werkzeug“ bleibt");
        assertEquals(ZoneView.Show.TOOL, saveAndLoad(saveAndLoad(chosen)).show);
        assertEquals(ZoneView.Show.TOOL, load("{\"version\": 2, \"show\": \"TOOL\"}").show);
    }

    @Test
    void otherSavedValuesStay() {
        assertEquals(ZoneView.Show.ALWAYS, load("{\"show\": \"ALWAYS\"}").show);
        assertEquals(ZoneView.Show.ALWAYS, saveAndLoad(load("{\"show\": \"ALWAYS\"}")).show);
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, load("{\"show\": \"TOOL_OR_FLIGHT\"}").show);
    }

    @Test
    void brokenValuesFallBackToTheDefault() {
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, load("{\"show\": null}").show);
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, load("{\"show\": \"QUATSCH\"}").show);
        assertEquals(ZoneView.Show.TOOL_OR_FLIGHT, load("{\"show\": 7}").show);
        assertEquals(ZoneView.Show.TOOL, load("{\"version\": 3, \"show\": \"TOOL\"}").show);
    }
}
