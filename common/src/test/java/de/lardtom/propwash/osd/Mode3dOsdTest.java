package de.lardtom.propwash.osd;

import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.Telemetry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mode3dOsdTest {
    @Test
    void throttleShowsMinusHundredToPlusHundredIn3d() {
        assertEquals(-100, OsdFormat.signedPercent(-1.4));
        assertEquals(-31, OsdFormat.signedPercent(-0.31));
        assertEquals(0, OsdFormat.signedPercent(Double.NaN));
        assertEquals(100, OsdFormat.signedPercent(1.0));
        assertEquals("-31", OsdFormat.signedPercentText(-31));
        assertEquals("0", OsdFormat.signedPercentText(0));
        assertEquals("+45", OsdFormat.signedPercentText(45));
        assertEquals(0, OsdFormat.percent(-0.31), "normales Gas bleibt 0–100");
    }

    @Test
    void telemetryCarries3dStateAndSignedThrottle() {
        Telemetry t = new Telemetry();
        t.mode3d = true;
        t.throttleOut = -0.42;
        t.armBlockers = ArmBlocker.THROTTLE_NOT_CENTERED.bit();
        OsdData d = new OsdData();
        d.active = true;
        d.radioConnected = true;
        d.linkStrength = 1.0;
        d.applyTelemetry(t);
        assertTrue(d.mode3d);
        assertEquals(-0.42, d.throttle, 1e-12);
        OsdData copy = new OsdData();
        d.copyTo(copy);
        assertTrue(copy.mode3d);
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(d, warnings);
        assertTrue(warnings.contains(OsdWarnings.Warning.ARM_THROTTLE_3D));
        assertFalse(warnings.contains(OsdWarnings.Warning.ARM_THROTTLE));
        Telemetry copied = new Telemetry();
        t.copyTo(copied);
        assertTrue(copied.mode3d);
    }
}
