package de.lardtom.propwash.gpucompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuCompatDecisionTest {
    private static GpuCompatDecision auto(boolean windows, String vendor, String renderer) {
        return GpuCompatDecision.decide(GpuCompatMode.AUTO, GpuCompatDecision.Source.SETTING, windows, vendor, renderer, false);
    }

    @Test
    void automaticIsOnForTheIntelChipsFromTheReports() {
        for (String renderer : new String[]{"Intel(R) UHD Graphics 620", "Intel(R) HD Graphics 520", "Intel(R) UHD Graphics 630",
                "Intel(R) Iris(R) Xe Graphics", "Intel(R) Arc(TM) A770 Graphics"}) {
            GpuCompatDecision decision = auto(true, "Intel", renderer);
            assertTrue(decision.active(), renderer);
            assertEquals(GpuCompatDecision.Reason.AUTO_INTEL_WINDOWS, decision.reason(), renderer);
        }
    }

    @Test
    void automaticIsOffEverywhereElse() {
        assertFalse(auto(true, "NVIDIA Corporation", "NVIDIA GeForce GTX 1650/PCIe/SSE2").active());
        assertEquals(GpuCompatDecision.Reason.AUTO_NOT_INTEL, auto(true, "ATI Technologies Inc.", "AMD Radeon RX 6600").reason());
        assertEquals(GpuCompatDecision.Reason.AUTO_NOT_INTEL, auto(false, "Apple", "Apple M4 Pro").reason());
        GpuCompatDecision mesa = auto(false, "Intel", "Mesa Intel(R) UHD Graphics 620 (KBL GT2)");
        assertFalse(mesa.active());
        assertEquals(GpuCompatDecision.Reason.AUTO_NOT_WINDOWS, mesa.reason());
        GpuCompatDecision mac = auto(false, "Intel Inc.", "Intel(R) Iris(TM) Plus Graphics 655");
        assertFalse(mac.active());
        assertFalse(auto(true, "", "").active());
        assertFalse(GpuCompatDecision.decide(GpuCompatMode.AUTO, GpuCompatDecision.Source.SETTING, true, null, null, false).active());
    }

    @Test
    void intelIsFoundInVendorOrRenderer() {
        assertTrue(GpuCompatDecision.intel("Intel", ""));
        assertTrue(GpuCompatDecision.intel("", "INTEL(R) HD GRAPHICS 4000"));
        assertTrue(GpuCompatDecision.intel("Microsoft Corporation", "D3D12 (Intel(R) UHD Graphics 620)"));
        assertFalse(GpuCompatDecision.intel("NVIDIA Corporation", "GeForce RTX 4070"));
        assertFalse(GpuCompatDecision.intel(null, null));
    }

    @Test
    void onAndOffWinOverTheDetection() {
        GpuCompatDecision on = GpuCompatDecision.decide(GpuCompatMode.ON, GpuCompatDecision.Source.SETTING, false, "Apple", "Apple M4 Pro", false);
        assertTrue(on.active());
        assertEquals(GpuCompatDecision.Reason.SETTING_ON, on.reason());
        GpuCompatDecision off = GpuCompatDecision.decide(GpuCompatMode.OFF, GpuCompatDecision.Source.SETTING, true, "Intel", "Intel(R) UHD Graphics 620",
                false);
        assertFalse(off.active());
        assertEquals(GpuCompatDecision.Reason.SETTING_OFF, off.reason());
    }

    @Test
    void propertyBeatsEnvironmentAndBadValuesAreIgnored() {
        assertEquals(GpuCompatMode.ON, GpuCompatDecision.override("on", "off"));
        assertEquals(GpuCompatMode.OFF, GpuCompatDecision.override(null, "off"));
        assertEquals(GpuCompatMode.OFF, GpuCompatDecision.override("quatsch", "aus"));
        assertEquals(GpuCompatMode.AUTO, GpuCompatDecision.override(" Automatisch ", null));
        assertNull(GpuCompatDecision.override(null, null));
        assertNull(GpuCompatDecision.override("", "  "));
    }

    @Test
    void simulationOnlyForTheExactValue() {
        assertTrue(GpuCompatDecision.simulateIntelWindows("intel-windows"));
        assertTrue(GpuCompatDecision.simulateIntelWindows(" INTEL-WINDOWS "));
        assertFalse(GpuCompatDecision.simulateIntelWindows("intel"));
        assertFalse(GpuCompatDecision.simulateIntelWindows(null));
        GpuCompatDecision simulated = GpuCompatDecision.decide(GpuCompatMode.AUTO, GpuCompatDecision.Source.SETTING, true,
                GpuCompatDecision.SIMULATED_VENDOR, GpuCompatDecision.SIMULATED_RENDERER, true);
        assertTrue(simulated.active());
        assertTrue(simulated.describe().contains("simuliert"), simulated.describe());
    }

    @Test
    void logLineNamesStateReasonAndDevice() {
        GpuCompatDecision decision = auto(true, "Intel", "Intel(R) UHD Graphics 620");
        assertEquals("an – Automatisch, Intel-Grafik unter Windows erkannt (Intel / Intel(R) UHD Graphics 620, Windows)", decision.describe());
        GpuCompatDecision forced = GpuCompatDecision.decide(GpuCompatMode.OFF, GpuCompatDecision.Source.OVERRIDE, false, "Apple", "Apple M4 Pro", false);
        assertTrue(forced.describe().startsWith("aus – Einstellung Aus (vorgegeben über propwash.gpuCompat bzw. PROPWASH_GPU_COMPAT)"),
                forced.describe());
        assertFalse(GpuCompatDecision.undecided().active());
    }
}
