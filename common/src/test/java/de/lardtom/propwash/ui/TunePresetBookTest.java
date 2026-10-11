package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunePresetBookTest {
    private static final TunePresets.Section RATES = TunePresets.Section.RATES;
    private static final Map<String, Float> VALUES = Map.of(TuneKeys.RATES_TYPE, 1.0F, "rates.roll.centerRateDps", 125.0F);

    @Test
    void saveAddsAndSameNameOverwrites() {
        TunePresetBook.Change first = TunePresetBook.save(RATES, List.of(), " Juicy ", VALUES);
        assertEquals(TunePresetBook.Outcome.SAVED, first.outcome());
        assertEquals("Juicy", first.name());
        TunePresetBook.Change again = TunePresetBook.save(RATES, first.presets(), "JUICY", Map.of(TuneKeys.RATES_TYPE, 0.0F));
        assertEquals(TunePresetBook.Outcome.UPDATED, again.outcome());
        assertEquals(1, again.presets().size());
        assertEquals("JUICY", again.presets().getFirst().name());
        assertEquals(TunePresetBook.Outcome.EMPTY_NAME, TunePresetBook.save(RATES, List.of(), "   ", VALUES).outcome());
    }

    @Test
    void savingDropsKeysOfOtherSections() {
        TunePresetBook.Change change = TunePresetBook.save(RATES, List.of(), "x", Map.of(TuneKeys.GYRO_LPF, 100.0F, TuneKeys.RATES_TYPE, 1.0F));
        assertEquals(Map.of(TuneKeys.RATES_TYPE, 1.0F), change.presets().getFirst().values());
    }

    @Test
    void theListIsLimited() {
        List<TunePresets.Preset> presets = new ArrayList<>();
        for (int i = 0; i < TunePresets.MAX_PER_SECTION; i++) {
            presets.add(new TunePresets.Preset("p" + i, VALUES));
        }
        assertEquals(TunePresetBook.Outcome.FULL, TunePresetBook.save(RATES, presets, "new", VALUES).outcome());
        assertEquals(TunePresetBook.Outcome.UPDATED, TunePresetBook.save(RATES, presets, "p3", VALUES).outcome());
    }

    @Test
    void renameChecksNames() {
        List<TunePresets.Preset> presets = List.of(new TunePresets.Preset("A", VALUES), new TunePresets.Preset("B", VALUES));
        assertEquals(TunePresetBook.Outcome.TAKEN, TunePresetBook.rename(presets, "A", "b").outcome());
        assertEquals(TunePresetBook.Outcome.EMPTY_NAME, TunePresetBook.rename(presets, "A", " ").outcome());
        assertEquals(TunePresetBook.Outcome.MISSING, TunePresetBook.rename(presets, "C", "D").outcome());
        TunePresetBook.Change renamed = TunePresetBook.rename(presets, "A", "Alpha");
        assertEquals(TunePresetBook.Outcome.RENAMED, renamed.outcome());
        assertEquals(List.of("Alpha", "B"), renamed.presets().stream().map(TunePresets.Preset::name).toList());
        assertEquals(TunePresetBook.Outcome.RENAMED, TunePresetBook.rename(presets, "A", "a").outcome());
    }

    @Test
    void deleteRemovesOnlyTheNamedPreset() {
        List<TunePresets.Preset> presets = List.of(new TunePresets.Preset("A", VALUES), new TunePresets.Preset("B", VALUES));
        TunePresetBook.Change change = TunePresetBook.delete(presets, "A");
        assertTrue(change.changed());
        assertEquals(List.of("B"), change.presets().stream().map(TunePresets.Preset::name).toList());
        assertEquals(TunePresetBook.Outcome.MISSING, TunePresetBook.delete(presets, "a").outcome());
    }

    @Test
    void importFindsAFreeName() {
        List<TunePresets.Preset> presets = List.of(new TunePresets.Preset("Juicy", VALUES));
        TunePresetBook.Change same = TunePresetBook.importPreset(RATES, presets, "Juicy", "Mine", VALUES);
        assertEquals(TunePresetBook.Outcome.EXISTS, same.outcome());
        assertFalse(same.changed());
        TunePresetBook.Change other = TunePresetBook.importPreset(RATES, presets, "juicy", "Mine", Map.of(TuneKeys.RATES_TYPE, 0.0F));
        assertEquals(TunePresetBook.Outcome.IMPORTED, other.outcome());
        assertEquals("juicy (2)", other.name());
        TunePresetBook.Change unnamed = TunePresetBook.importPreset(RATES, presets, "", "Mine", VALUES);
        assertEquals("Mine", unnamed.name());
        assertEquals(TunePresets.MAX_NAME_LENGTH, TunePresetBook.uniqueName(List.of(new TunePresets.Preset("x".repeat(24), VALUES)), "x".repeat(24)).length());
    }

    @Test
    void validationCleansStoredLists() {
        List<TunePresets.Preset> raw = new ArrayList<>();
        raw.add(new TunePresets.Preset("  A ", Map.of(TuneKeys.RATES_TYPE, 7.0F, TuneKeys.GYRO_LPF, 3.0F)));
        raw.add(new TunePresets.Preset("a", VALUES));
        raw.add(new TunePresets.Preset(" ", VALUES));
        raw.add(null);
        List<TunePresets.Preset> clean = TunePresetBook.validate(RATES, raw);
        assertEquals(1, clean.size());
        assertEquals("A", clean.getFirst().name());
        assertEquals(Map.of(TuneKeys.RATES_TYPE, 1.0F), clean.getFirst().values());
    }
}
