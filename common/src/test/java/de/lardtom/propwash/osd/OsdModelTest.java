package de.lardtom.propwash.osd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StepResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdModelTest {
    @Test
    void sanitizeFillsMissingElementsAndClampsValues() {
        OsdConfig config = new OsdConfig();
        config.color = "not a colour";
        config.scale = 9.0F;
        config.ladderStepDeg = Float.NaN;
        config.elements.put("speed", new OsdConfig.Slot(false, 99, -4));
        config.elements.put("crosshair", new OsdConfig.Slot(true, 3, 3));
        config.elements.put("unknown_element", new OsdConfig.Slot(true, 1, 1));
        OsdConfig.sanitize(config);
        assertEquals("#FFFFFF", config.color);
        assertEquals(OsdConfig.MAX_SCALE, config.scale);
        assertEquals(10.0F, config.ladderStepDeg);
        assertEquals(OsdElement.values().length, config.elements.size());
        assertFalse(config.elements.containsKey("unknown_element"));
        OsdConfig.Slot speed = config.slot(OsdElement.SPEED);
        assertFalse(speed.enabled);
        assertEquals(OsdGrid.COLS - 1, speed.col);
        assertEquals(0, speed.row);
        OsdConfig.Slot crosshair = config.slot(OsdElement.CROSSHAIR);
        assertEquals(OsdElement.CROSSHAIR.baseCol(), crosshair.col);
        assertEquals(OsdElement.CROSSHAIR.baseRow(), crosshair.row);
        OsdConfig.Slot missing = config.slot(OsdElement.ALTITUDE);
        assertEquals(config.preset.enabled(OsdElement.ALTITUDE), missing.enabled);
        assertEquals(config.preset.col(OsdElement.ALTITUDE), missing.col);
    }

    @Test
    void colorsAndCopies() {
        OsdConfig config = OsdConfig.defaults();
        config.color = "#ff8800";
        OsdConfig.sanitize(config);
        assertEquals("#FF8800", config.color);
        assertEquals(0xFF8800, config.primaryRgb());
        OsdConfig copy = config.copy();
        copy.move(OsdElement.SPEED, 5, 6);
        assertEquals(OsdConfig.DEFAULT_PRESET.col(OsdElement.SPEED), config.slot(OsdElement.SPEED).col);
        assertEquals(5, copy.slot(OsdElement.SPEED).col);
        copy.move(OsdElement.CROSSHAIR, 1, 1);
        assertEquals(OsdElement.CROSSHAIR.baseCol(), copy.slot(OsdElement.CROSSHAIR).col);
        copy.resetLayout();
        assertEquals(OsdConfig.DEFAULT_PRESET.col(OsdElement.SPEED), copy.slot(OsdElement.SPEED).col);
        assertEquals(config.color, copy.color);
    }

    @Test
    void configSurvivesAJsonRoundTrip() {
        Gson gson = new GsonBuilder().create();
        OsdConfig config = OsdConfig.defaults();
        config.move(OsdElement.ALTITUDE, 20, 3);
        config.slot(OsdElement.CLOCK).enabled = true;
        config.coloredAlerts = false;
        OsdConfig loaded = gson.fromJson(gson.toJson(config), OsdConfig.class);
        OsdConfig.sanitize(loaded);
        assertEquals(20, loaded.slot(OsdElement.ALTITUDE).col);
        assertEquals(3, loaded.slot(OsdElement.ALTITUDE).row);
        assertTrue(loaded.enabled(OsdElement.CLOCK));
        assertFalse(loaded.coloredAlerts);
        OsdConfig partial = gson.fromJson("{\"scale\":1.5,\"elements\":{\"speed\":{\"enabled\":false,\"col\":2,\"row\":2}}}", OsdConfig.class);
        OsdConfig.sanitize(partial);
        assertEquals(1.5F, partial.scale);
        assertFalse(partial.enabled(OsdElement.SPEED));
        assertEquals(OsdElement.values().length, partial.elements.size());
    }

    @Test
    void worldEdgeWarnsSteadilyAndCopies() {
        OsdData data = new OsdData();
        data.active = true;
        data.hasSim = true;
        data.armed = true;
        data.radioConnected = true;
        data.linkStrength = 1.0;
        data.worldEdge = true;
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.collect(data, out);
        assertEquals(List.of(OsdWarnings.Warning.WORLD_EDGE), out);
        assertEquals(OsdWarnings.Severity.WARNING, OsdWarnings.Warning.WORLD_EDGE.severity());
        assertFalse(OsdWarnings.Warning.WORLD_EDGE.blinks());
        assertEquals("propwash.osd.warning.world_edge", OsdWarnings.Warning.WORLD_EDGE.translationKey());
        OsdData copy = new OsdData();
        data.copyTo(copy);
        assertTrue(copy.worldEdge);
        copy.reset();
        assertFalse(copy.worldEdge);
    }

    @Test
    void warningsArePrioritisedAndCycleThroughCriticalOnes() {
        OsdData data = new OsdData();
        data.active = true;
        data.hasSim = true;
        data.radioConnected = true;
        data.linkStrength = 1.0;
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.collect(data, out);
        assertTrue(out.isEmpty());
        data.armBlockers = ArmBlocker.THROTTLE_NOT_LOW.bit() | ArmBlocker.TILTED.bit();
        OsdWarnings.collect(data, out);
        assertEquals(List.of(OsdWarnings.Warning.ARM_THROTTLE, OsdWarnings.Warning.ARM_ANGLE), out);
        data.armed = true;
        data.batteryState = BatteryState.LOW;
        data.linkStrength = 0.2;
        OsdWarnings.collect(data, out);
        assertEquals(List.of(OsdWarnings.Warning.BATTERY_LOW, OsdWarnings.Warning.WEAK_SIGNAL), out);
        assertEquals(0, OsdWarnings.visibleIndex(out, 0.0));
        assertEquals(1, OsdWarnings.visibleIndex(out, OsdWarnings.CYCLE_SECONDS * 1.5));
        data.failsafeStage = 1;
        data.radioConnected = false;
        OsdWarnings.collect(data, out);
        assertEquals(OsdWarnings.Warning.FAILSAFE, out.getFirst());
        for (int step = 0; step < 6; step++) {
            int index = OsdWarnings.visibleIndex(out, step * OsdWarnings.CYCLE_SECONDS + 0.1);
            assertEquals(OsdWarnings.Severity.CRITICAL, out.get(index).severity());
        }
        data.active = false;
        OsdWarnings.collect(data, out);
        assertTrue(out.isEmpty());
        assertEquals(-1, OsdWarnings.visibleIndex(out, 1.0));
    }

    @Test
    void flightStatsTrackExtremesOnlyWhileArmed() {
        OsdData data = new OsdData();
        FlightStats stats = new FlightStats();
        data.speed = 30.0;
        stats.accumulate(data, 1.0);
        assertFalse(stats.hasData());
        data.armed = true;
        data.hasPosition = true;
        data.hasHome = true;
        data.x = 30.0;
        data.cellVoltage = 3.9;
        data.mAhUsed = 100.0;
        data.currentA = 40.0;
        data.gLoad = 2.5;
        data.altitude = 12.0;
        stats.accumulate(data, 0.5);
        data.speed = 12.0;
        data.cellVoltage = 3.6;
        data.mAhUsed = 180.0;
        data.currentA = Double.NaN;
        data.x = 10.0;
        stats.accumulate(data, 0.5);
        assertTrue(stats.hasData());
        assertEquals(1.0, stats.flightSeconds, 1.0E-9);
        assertEquals(30.0, stats.maxSpeed, 1.0E-9);
        assertEquals(30.0, stats.maxDistance, 1.0E-9);
        assertEquals(3.6, stats.minCellVoltage, 1.0E-9);
        assertEquals(80.0, stats.mAhUsed, 1.0E-9);
        assertEquals(40.0, stats.maxCurrent, 1.0E-9);
        assertEquals(2.5, stats.maxG, 1.0E-9);
        stats.reset();
        assertFalse(stats.hasData());
        assertTrue(Double.isNaN(stats.minCellVoltage));
    }

    @Test
    void homeGeometry() {
        OsdData data = new OsdData();
        assertTrue(Double.isNaN(data.homeDistance()));
        data.hasPosition = true;
        data.hasHome = true;
        data.x = 0.0;
        data.z = 0.0;
        data.homeX = 30.0;
        data.homeZ = -40.0;
        assertEquals(50.0, data.homeDistance(), 1.0E-9);
        data.compassDeg = 0.0;
        assertEquals(OsdFormat.bearing(30.0, -40.0), data.homeRelativeBearing(), 1.0E-9);
        data.compassDeg = 90.0;
        assertEquals(OsdFormat.wrap180(OsdFormat.bearing(30.0, -40.0) - 90.0), data.homeRelativeBearing(), 1.0E-9);
    }

    @Test
    void telemetryFromARealSimFillsTheOsd() {
        DroneSim sim = new DroneSim(DroneBuild.RACER, new FlightControllerConfig(), new SimTuning(), 3L);
        sim.reset(0.0, 70.0, 0.0, Math.toRadians(90.0));
        PilotInput input = new PilotInput();
        SimEnvironment environment = new SimEnvironment();
        StepResult result = new StepResult();
        for (int i = 0; i < 30; i++) {
            input.armSwitch = i > 5;
            input.throttle = i > 10 ? 0.4 : 0.0;
            sim.step(16_666_667L, input, environment, result);
        }
        OsdData data = new OsdData();
        data.applyTelemetry(sim.telemetry());
        assertTrue(data.hasSim);
        assertTrue(Double.isFinite(data.packVoltage) && data.packVoltage > 10.0);
        assertTrue(Double.isFinite(data.cellVoltage) && data.cellVoltage > 3.0);
        assertTrue(Double.isFinite(data.compassDeg));
        assertEquals(90.0, data.compassDeg, 2.0);
        assertNotNull(data.mode);
        OsdData copy = new OsdData();
        data.copyTo(copy);
        assertEquals(data.packVoltage, copy.packVoltage);
        assertEquals(data.compassDeg, copy.compassDeg);
    }

    @Test
    void sampleDataIsCompleteForThePreview() {
        OsdData sample = OsdData.sample();
        assertTrue(sample.active && sample.racing && sample.hasHome && sample.stats.hasData());
        assertTrue(Double.isFinite(sample.homeDistance()));
        sample.reset();
        assertFalse(sample.active);
        assertFalse(sample.stats.hasData());
    }

    @Test
    void everyTextHasAGermanAndEnglishTranslation() throws IOException {
        Path dir = Files.isDirectory(Path.of("common/src/lang/osd")) ? Path.of("common/src/lang/osd") : Path.of("src/lang/osd");
        JsonObject de = new Gson().fromJson(Files.readString(dir.resolve("de_de.json"), StandardCharsets.UTF_8), JsonObject.class);
        JsonObject en = new Gson().fromJson(Files.readString(dir.resolve("en_us.json"), StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(de.keySet(), en.keySet());
        List<String> keys = new ArrayList<>();
        for (OsdElement element : OsdElement.values()) {
            keys.add(element.translationKey());
        }
        for (OsdWarnings.Warning warning : OsdWarnings.Warning.values()) {
            keys.add(warning.translationKey());
        }
        for (StabMode mode : StabMode.values()) {
            keys.add("propwash.osd.mode." + mode.name().toLowerCase(Locale.ROOT));
        }
        for (String point : List.of("n", "ne", "e", "se", "s", "sw", "w", "nw")) {
            keys.add("propwash.osd.compass." + point);
        }
        for (VideoSystem system : VideoSystem.values()) {
            keys.add("propwash.osd.video." + system.name().toLowerCase(Locale.ROOT));
        }
        for (String key : keys) {
            assertTrue(de.has(key), "de_de fehlt " + key);
            assertTrue(en.has(key), "en_us fehlt " + key);
        }
        for (String key : de.keySet()) {
            assertTrue(key.startsWith("propwash.osd."), key);
            assertFalse(de.get(key).getAsString().isBlank(), key);
            assertFalse(en.get(key).getAsString().isBlank(), key);
        }
    }

    @Test
    void legacyStatusRowsMoveAboveTheActionBar() {
        Gson gson = new GsonBuilder().create();
        OsdConfig legacy = gson.fromJson("{\"version\":1,\"elements\":{\"warnings\":{\"enabled\":true,\"col\":15,\"row\":11},"
                + "\"armed_status\":{\"enabled\":true,\"col\":15,\"row\":10},\"speed\":{\"enabled\":true,\"col\":15,\"row\":11}}}", OsdConfig.class);
        OsdConfig.sanitize(legacy);
        assertEquals(OsdElement.WARNINGS.baseRow(), legacy.slot(OsdElement.WARNINGS).row);
        assertEquals(OsdElement.ARMED_STATUS.baseRow(), legacy.slot(OsdElement.ARMED_STATUS).row);
        assertEquals(11, legacy.slot(OsdElement.SPEED).row);
        OsdConfig moved = gson.fromJson("{\"version\":1,\"elements\":{\"warnings\":{\"enabled\":true,\"col\":4,\"row\":11}}}", OsdConfig.class);
        OsdConfig.sanitize(moved);
        assertEquals(11, moved.slot(OsdElement.WARNINGS).row);
        assertEquals(OsdConfig.VERSION, moved.version);
    }
}
