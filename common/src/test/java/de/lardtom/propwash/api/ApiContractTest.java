package de.lardtom.propwash.api;

import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.ui.TuneKeys;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiContractTest {
    @Test
    void semanticVersionRules() {
        assertTrue(PropwashApi.isCompatible("1.0"));
        assertTrue(PropwashApi.isCompatible("1.0.0"));
        assertTrue(PropwashApi.isCompatible("1.1"));
        assertTrue(PropwashApi.isCompatible("1.2"));
        assertTrue(PropwashApi.isCompatible("1.3"));
        assertTrue(PropwashApi.isCompatible("1.4"));
        assertTrue(PropwashApi.isCompatible("1.5"));
        assertTrue(PropwashApi.isCompatible("1.6"));
        assertFalse(PropwashApi.isCompatible("1.7"));
        assertEquals("1.6.0", PropwashApi.API_VERSION);
        assertFalse(PropwashApi.isCompatible("2.0"));
        assertFalse(PropwashApi.isCompatible("0.9"));
        assertTrue(ApiVersion.parse("1.4.2").satisfies(ApiVersion.parse("1.3")));
        assertFalse(ApiVersion.parse("1.2.9").satisfies(ApiVersion.parse("1.3")));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("eins"));
        assertThrows(IllegalStateException.class, () -> PropwashApi.requireVersion("test", "9.0"));
    }

    @Test
    void everyKnownTuneKeyHasAStableGroup() {
        assertEquals(TuneGroup.RATES, TuneGroup.of("rates.roll.rcRate"));
        assertEquals(TuneGroup.PID, TuneGroup.of("pid.roll.p"));
        assertEquals(TuneGroup.FEEDFORWARD, TuneGroup.of("pid.yaw.ff"));
        assertEquals(TuneGroup.TPA, TuneGroup.of("pid.tpaBreakpoint"));
        assertEquals(TuneGroup.THROTTLE, TuneGroup.of("pid.throttleMid"));
        assertEquals(TuneGroup.THROTTLE, TuneGroup.of("pid.motorIdlePercent"));
        assertEquals(TuneGroup.FILTERS, TuneGroup.of("filters.gyroLpfHz"));
        assertEquals(TuneGroup.HOLD, TuneGroup.of("hold.altP"));
        assertEquals(TuneGroup.CAMERA, TuneGroup.of("camera.uptiltDeg"));
        assertEquals(TuneGroup.LED, TuneGroup.of("led.mode"));
        assertEquals(TuneGroup.VTX, TuneGroup.of("vtx.channel"));
        for (String key : TuneKeys.params().keySet()) {
            assertNotEquals(TuneGroup.OTHER, TuneGroup.of(key), key);
        }
    }

    @Test
    void overlayReplacesOnlyTheChosenGroups() {
        TuneSpec base = new TuneSpec(Map.of("rates.roll.rcRate", 1.0F, "pid.roll.p", 40.0F, "vtx.channel", 3.0F));
        TuneSpec imported = new TuneSpec(Map.of("rates.roll.rcRate", 1.5F, "pid.roll.p", 55.0F, "vtx.channel", 7.0F));
        TuneSpec merged = base.overlay(imported, EnumSet.of(TuneGroup.RATES));
        assertEquals(1.5F, merged.values().get("rates.roll.rcRate"));
        assertEquals(40.0F, merged.values().get("pid.roll.p"));
        assertEquals(3.0F, merged.values().get("vtx.channel"));
        assertEquals(55.0F, base.overlay(imported, TuneGroup.FLIGHT).values().get("pid.roll.p"));
        assertEquals(3.0F, base.overlay(imported, TuneGroup.FLIGHT).values().get("vtx.channel"));
    }

    @Test
    void builtinDefinitionsPassTheirOwnValidation() {
        for (PartKind kind : PartKind.values()) {
            for (PartInfo part : PartIndex.all(kind)) {
                assertEquals(kind, part.definition().kind());
            }
        }
        for (PartInfo part : PartIndex.all(PartKind.FRAME)) {
            FrameDefinition frame = (FrameDefinition) part.definition();
            assertTrue(frame.geometry().arms().isEmpty(), frame.id() + " nutzt das alte True-X-Modell");
        }
    }
}
