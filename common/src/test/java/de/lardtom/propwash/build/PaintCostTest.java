package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintCostTest {
    @Test
    void unchangedPaintIsFree() {
        DronePaint paint = PaintPreset.NEON.paint();
        assertSame(PaintCost.FREE, PaintCost.of(paint, paint));
        assertTrue(PaintCost.of(DronePaint.DEFAULT, DronePaint.DEFAULT).free());
    }

    @Test
    void oneDyePerChangedPart() {
        DronePaint target = DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.RED.rgb()).with(PaintSlot.MOTORS, PaintSwatch.RED.rgb())
                .with(PaintSlot.CAMERA, PaintSwatch.BLUE.rgb());
        PaintCost cost = PaintCost.of(DronePaint.DEFAULT, target);
        assertEquals(Map.of("red", 2, "blue", 1), cost.dyes());
        assertEquals(3, cost.total());
        assertEquals(3, cost.changes());
    }

    @Test
    void finishesCostTheirDye() {
        PaintCost cost = PaintCost.of(DronePaint.DEFAULT, DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.CARBON.rgb())
                .with(PaintSlot.MOTORS, PaintSwatch.GOLD.rgb()));
        assertEquals(Map.of("black", 1, "yellow", 1), cost.dyes());
    }

    @Test
    void customColoursCostAnyDye() {
        PaintCost cost = PaintCost.of(DronePaint.DEFAULT, DronePaint.DEFAULT.with(PaintSlot.FRAME, 0x123456));
        assertTrue(cost.dyes().isEmpty());
        assertEquals(List.of(0x123456), cost.custom());
        assertEquals(1, cost.total());
    }

    @Test
    void goingBackToStockIsFreeButCountsAsChange() {
        DronePaint painted = DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.RED.rgb());
        PaintCost cost = PaintCost.of(painted, DronePaint.DEFAULT);
        assertTrue(cost.free());
        assertEquals(1, cost.changes());
    }

    @Test
    void onlyChangedPartsAreCharged() {
        DronePaint before = PaintPreset.RACE_TEAM.paint();
        DronePaint after = before.with(PaintSlot.PROP_FRONT_LEFT, PaintSwatch.LIME.rgb());
        assertEquals(Map.of("lime", 1), PaintCost.of(before, after).dyes());
    }

    @Test
    void planUsesMatchingDyesAndTheClosestForCustomColours() {
        DronePaint target = DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.RED.rgb()).with(PaintSlot.MOTORS, 0xFF1010);
        PaintCost cost = PaintCost.of(DronePaint.DEFAULT, target);
        assertEquals(Map.of("red", 2), cost.plan(Map.of("red", 5, "blue", 5)).orElseThrow());
        assertEquals(Map.of("red", 1, "blue", 1), cost.plan(Map.of("red", 1, "blue", 5)).orElseThrow());
        assertFalse(cost.plan(Map.of("blue", 5)).isPresent());
        assertFalse(cost.plan(Map.of("red", 1)).isPresent());
    }

    @Test
    void fullPresetCostsTwelveDyes() {
        PaintCost cost = PaintCost.of(DronePaint.DEFAULT, PaintPreset.CARBON_CLASSIC.paint());
        assertEquals(PaintSlot.COUNT, cost.total());
        assertTrue(cost.custom().isEmpty());
    }
}
