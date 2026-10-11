package de.lardtom.propwash.ui;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RatesType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunePresetsTest {
    private final Map<String, Float> freestyle = TuneDefaults.compute(DroneBuild.FREESTYLE, Map.of());
    private final Map<String, Float> whoop = TuneDefaults.compute(DroneBuild.WHOOP, Map.of());

    private Map<String, Float> ownRates() {
        Map<String, Float> tune = RateTemplate.RACING.apply(Map.of(), this.freestyle);
        tune = TuneEdits.set(tune, this.freestyle, TuneKeys.rateKey(Axis.ROLL, "centerRateDps"), 125.0);
        tune = TuneEdits.set(tune, this.freestyle, TuneKeys.rateKey(Axis.YAW, "actualExpo"), 0.33);
        return TuneEdits.set(tune, this.freestyle, TuneKeys.RC_DEADBAND, 0.04);
    }

    @Test
    void ratesCaptureTheActiveTypeWithoutDeadbands() {
        Map<String, Float> captured = TunePresets.capture(TunePresets.Section.RATES, this.ownRates(), this.freestyle);
        assertEquals(10, captured.size());
        assertEquals((float) RatesType.ACTUAL.ordinal(), captured.get(TuneKeys.RATES_TYPE));
        assertEquals(125.0F, captured.get(TuneKeys.rateKey(Axis.ROLL, "centerRateDps")));
        assertEquals(0.33F, captured.get(TuneKeys.rateKey(Axis.YAW, "actualExpo")), 1e-6F);
        assertFalse(captured.containsKey(TuneKeys.RC_DEADBAND));
        Map<String, Float> classic = TunePresets.capture(TunePresets.Section.RATES, RateTemplate.CLASSIC.apply(Map.of(), this.freestyle), this.freestyle);
        assertTrue(classic.containsKey(TuneKeys.rateKey(Axis.PITCH, "superRate")));
        assertFalse(classic.containsKey(TuneKeys.rateKey(Axis.PITCH, "maxRateDps")));
    }

    @Test
    void ratesPresetGivesTheSameFeelOnAnotherDroneAndKeepsItsDeadband() {
        Map<String, Float> preset = TunePresets.capture(TunePresets.Section.RATES, this.ownRates(), this.freestyle);
        Map<String, Float> other = TuneEdits.set(RateTemplate.CINE.apply(Map.of(), this.whoop), this.whoop, TuneKeys.YAW_DEADBAND, 0.08);
        Map<String, Float> applied = TunePresets.apply(TunePresets.Section.RATES, other, this.whoop, preset);
        for (Map.Entry<String, Float> e : preset.entrySet()) {
            assertEquals(e.getValue(), TuneEdits.value(applied, this.whoop, e.getKey()), 1e-6, e.getKey());
        }
        assertEquals(0.08, TuneEdits.value(applied, this.whoop, TuneKeys.YAW_DEADBAND), 1e-6);
        assertTrue(TunePresets.matches(TunePresets.Section.RATES, applied, this.whoop, preset));
        assertFalse(TunePresets.matches(TunePresets.Section.RATES, other, this.whoop, preset));
    }

    @Test
    void builtInTemplatesAndPresetsApplyAlike() {
        for (RateTemplate template : RateTemplate.values()) {
            Map<String, Float> base = this.ownRates();
            assertEquals(new TreeMap<>(template.apply(base, this.freestyle)),
                    new TreeMap<>(TunePresets.apply(TunePresets.Section.RATES, base, this.freestyle, template.tuneValues())), template.name());
        }
    }

    @Test
    void pidPresetHoldsOnlyChangesAndHandSetGains() {
        Map<String, Float> tune = TuneEdits.set(Map.of(), this.freestyle, TuneKeys.GYRO_LPF, 150.0);
        tune = TuneEdits.set(tune, this.freestyle, TuneKeys.ARM_MAX_ANGLE, 180.0);
        tune = TuneEdits.set(tune, this.freestyle, TuneKeys.rateKey(Axis.ROLL, "maxRateDps"), 900.0);
        Map<String, Float> derived = TunePresets.capture(TunePresets.Section.PID, tune, this.freestyle);
        assertEquals(Map.of(TuneKeys.GYRO_LPF, 150.0F), derived);
        Map<String, Float> manual = TuneEdits.set(tune, this.freestyle, TuneKeys.pidKey(Axis.ROLL, "p"), 61.0);
        Map<String, Float> captured = TunePresets.capture(TunePresets.Section.PID, manual, this.freestyle);
        assertEquals(TuneEdits.PID_MANUAL, captured.get(TuneKeys.PID_SOURCE));
        assertEquals(61.0F, captured.get(TuneKeys.pidKey(Axis.ROLL, "p")));
        assertEquals(TuneKeys.pidTermKeys().size() + 2, captured.size());
        assertFalse(captured.containsKey(TuneKeys.ARM_MAX_ANGLE));
    }

    @Test
    void pidPresetReplacesTheTabButKeepsArmAngleAndRates() {
        Map<String, Float> source = TuneEdits.set(Map.of(), this.freestyle, TuneKeys.GYRO_LPF, 150.0);
        source = TuneEdits.set(source, this.freestyle, TuneKeys.pidKey(Axis.PITCH, "d"), 44.0);
        Map<String, Float> preset = TunePresets.capture(TunePresets.Section.PID, source, this.freestyle);
        Map<String, Float> target = TuneEdits.set(Map.of(), this.whoop, TuneKeys.TPA_RATE, 80.0);
        target = TuneEdits.set(target, this.whoop, TuneKeys.ARM_MAX_ANGLE, 90.0);
        target = TuneEdits.set(target, this.whoop, TuneKeys.rateKey(Axis.ROLL, "maxRateDps"), 900.0);
        Map<String, Float> applied = TunePresets.apply(TunePresets.Section.PID, target, this.whoop, preset);
        assertEquals(150.0, TuneEdits.value(applied, this.whoop, TuneKeys.GYRO_LPF), 1e-6);
        assertEquals(44.0, TuneEdits.value(applied, this.whoop, TuneKeys.pidKey(Axis.PITCH, "d")), 1e-6);
        assertTrue(TuneEdits.manualPid(applied, this.whoop));
        assertFalse(applied.containsKey(TuneKeys.TPA_RATE));
        assertEquals(90.0F, applied.get(TuneKeys.ARM_MAX_ANGLE));
        assertEquals(900.0F, applied.get(TuneKeys.rateKey(Axis.ROLL, "maxRateDps")));
        Map<String, Float> standard = TunePresets.apply(TunePresets.Section.PID, applied, this.whoop, Map.of());
        assertFalse(TuneEdits.manualPid(standard, this.whoop));
        assertFalse(standard.containsKey(TuneKeys.GYRO_LPF));
        assertTrue(TunePresets.matches(TunePresets.Section.PID, standard, this.whoop, Map.of()));
    }

    @Test
    void codesRoundTripAndRejectForeignData() {
        Map<String, Float> preset = TunePresets.capture(TunePresets.Section.RATES, this.ownRates(), this.freestyle);
        String code = TunePresets.encode(TunePresets.Section.RATES, "  Juicy   rates ", preset);
        assertTrue(code.startsWith(TunePresets.CODE_PREFIX));
        assertTrue(code.length() < 260, code);
        TunePresets.Decoded decoded = TunePresets.decode("  pwt1-" + code.substring(TunePresets.CODE_PREFIX.length()) + "\n").orElseThrow();
        assertEquals(TunePresets.Section.RATES, decoded.section());
        assertEquals("Juicy rates", decoded.name());
        assertEquals(preset, decoded.values());
        assertTrue(TunePresets.decode("PW1-ABCDE").isEmpty());
        assertTrue(TunePresets.decode("PWD1-" + code.substring(5)).isEmpty());
        assertTrue(TunePresets.decode("PWT1-!!!").isEmpty());
        assertTrue(TunePresets.decode(null).isEmpty());
        Map<String, Float> foreign = new HashMap<>(preset);
        foreign.put(TuneKeys.GYRO_LPF, 150.0F);
        foreign.put("led.mode", 1.0F);
        foreign.put(TuneKeys.rateKey(Axis.ROLL, "centerRateDps"), 99999.0F);
        TunePresets.Decoded cleaned = TunePresets.decode(TunePresets.encode(TunePresets.Section.RATES, "x", foreign)).orElseThrow();
        assertFalse(cleaned.values().containsKey(TuneKeys.GYRO_LPF));
        assertFalse(cleaned.values().containsKey("led.mode"));
        assertEquals(1998.0F, cleaned.values().get(TuneKeys.rateKey(Axis.ROLL, "centerRateDps")));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("My rates", TunePresets.cleanName("  My \t rates\n"));
        assertEquals(TunePresets.MAX_NAME_LENGTH, TunePresets.cleanName("x".repeat(80)).length());
        assertTrue(TunePresets.sameName("Freestyle", " freestyle "));
        assertEquals("", TunePresets.cleanName(null));
    }

    @Test
    void sectionsOwnTheirTabKeys() {
        assertTrue(TunePresets.Section.RATES.owns(TuneKeys.rateKey(Axis.YAW, "rcRate")));
        assertFalse(TunePresets.Section.RATES.owns(TuneKeys.RC_DEADBAND));
        assertTrue(TunePresets.Section.PID.owns(TuneKeys.HOLD_MIN_SATS));
        assertTrue(TunePresets.Section.PID.owns(TuneKeys.RC_SMOOTHING));
        assertFalse(TunePresets.Section.PID.owns(TuneKeys.ARM_MAX_ANGLE));
        assertFalse(TunePresets.Section.PID.owns("camera.uptiltDeg"));
        assertEquals(List.of("rcRate", "superRate", "rcExpo"), TunePresets.rateFields(RatesType.BETAFLIGHT));
    }
}
