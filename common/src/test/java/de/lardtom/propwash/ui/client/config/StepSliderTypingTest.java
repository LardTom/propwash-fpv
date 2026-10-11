package de.lardtom.propwash.ui.client.config;

import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.widget.NumberEditor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepSliderTypingTest {
    private static StepSlider slider(double min, double max, double step, double initial, List<Double> applied) {
        return new StepSlider(0, 0, 200, 20, min, max, step, initial, v -> Component.literal("Wert: " + v), applied::add);
    }

    private static void type(StepSlider slider, String text) {
        NumberEditor editor = slider.editor();
        editor.startEmpty();
        text.codePoints().forEach(editor::charTyped);
    }

    @Test
    void everyStepSliderIsTypeableInItsOwnUnits() {
        List<Double> applied = new ArrayList<>();
        StepSlider laps = slider(0.0, 20.0, 1.0, 3.0, applied);
        assertTrue(laps.typeable());
        assertEquals(0.0, laps.editor().min(), 1e-12);
        assertEquals(20.0, laps.editor().max(), 1e-12);
        type(laps, "12");
        assertEquals(NumberEntry.Status.OK, laps.editor().result().status());
        laps.commitPending();
        assertEquals(12.0, laps.current(), 1e-12);
        assertEquals(List.of(12.0), applied);
        assertFalse(laps.isEditing());
    }

    @Test
    void percentSlidersTakePercent() {
        List<Double> applied = new ArrayList<>();
        StepSlider volume = slider(0.0, 1.0, 0.01, 0.8, applied).percent();
        assertEquals(0.0, volume.editor().min(), 1e-12);
        assertEquals(100.0, volume.editor().max(), 1e-9);
        type(volume, "35");
        volume.commitPending();
        assertEquals(0.35, volume.current(), 1e-9);
        assertEquals(1, applied.size());
        StepSlider deadband = slider(0.0F, 0.25F, 0.005F, 0.0F, applied).percent();
        type(deadband, "2.5");
        assertEquals(NumberEntry.Status.OK, deadband.editor().result().status());
        deadband.commitPending();
        assertEquals(25L, Math.round(deadband.current() * 1000.0));
    }

    @Test
    void typedValuesSnapToTheStepAndOutOfRangeIsClamped() {
        List<Double> applied = new ArrayList<>();
        StepSlider distance = slider(16.0, 512.0, 16.0, 128.0, applied);
        type(distance, "100");
        distance.commitPending();
        assertEquals(96.0, distance.current(), 1e-12);
        type(distance, "9000");
        assertEquals(NumberEntry.Status.CLAMPED, distance.editor().result().status());
        distance.commitPending();
        assertEquals(512.0, distance.current(), 1e-12);
        type(distance, "-");
        assertEquals(NumberEntry.Status.EMPTY, distance.editor().result().status());
    }

    @Test
    void integerSlidersRejectTheDecimalSeparatorAndDecimalSlidersKeepIt() {
        List<Double> applied = new ArrayList<>();
        StepSlider seconds = slider(1.0, 10.0, 1.0, 3.0, applied);
        type(seconds, "4.5");
        assertEquals("45", seconds.editText());
        StepSlider descent = slider(0.5, 5.0, 0.1, 1.5, applied);
        type(descent, "2,3");
        descent.commitPending();
        assertEquals(2.3, descent.current(), 1e-9);
    }

    @Test
    void floatSlidersAcceptTheirShownEndpoints() {
        List<Double> applied = new ArrayList<>();
        StepSlider rate = slider(0.05F, 2.0F, 0.05F, 1.0F, applied).display(100.0, 0.0);
        assertEquals(5.0, rate.editor().min(), 0.0);
        assertEquals(200.0, rate.editor().max(), 0.0);
        type(rate, "5");
        assertEquals(NumberEntry.Status.OK, rate.editor().result().status());
        rate.commitPending();
        assertEquals(5L, Math.round(rate.current() * 100.0));
    }

    @Test
    void escapeLeavesTheValueAlone() {
        List<Double> applied = new ArrayList<>();
        StepSlider fov = slider(60.0, 160.0, 1.0, 120.0, applied);
        type(fov, "90");
        fov.cancelEdit();
        assertFalse(fov.isEditing());
        assertEquals(120.0, fov.current(), 1e-12);
        assertTrue(applied.isEmpty());
    }

    @Test
    void affineIndexSlidersAndTypedRange() {
        List<Double> applied = new ArrayList<>();
        StepSlider ladder = slider(0.0, 5.0, 1.0, 1.0, applied).display(5.0, 5.0);
        assertEquals(5.0, ladder.editor().min(), 1e-12);
        assertEquals(30.0, ladder.editor().max(), 1e-12);
        type(ladder, "20");
        ladder.commitPending();
        assertEquals(3.0, ladder.current(), 1e-12);
        StepSlider element = slider(0.0, 16.0, 1.0, 0.0, applied).display(10.0, 40.0).typedRange(1.0, 16.0);
        assertEquals(50.0, element.editor().min(), 1e-9);
        assertEquals(200.0, element.editor().max(), 1e-9);
        type(element, "10");
        element.commitPending();
        assertEquals(1.0, element.current(), 1e-12);
    }

    @Test
    void untypedSlidersStayMouseOnly() {
        StepSlider slider = slider(0.0, 1.0, 0.1, 0.5, new ArrayList<>()).untyped();
        assertFalse(slider.typeable());
        assertFalse(slider.isEditing());
    }
}
