package de.lardtom.propwash.video.vtx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxHeatTest {
    private static final VtxTable RACE = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
    private static final VtxTable HD = VtxTable.of(true, 700, List.of(25, 100, 400, 700, 1200), 9.0, 23.0);

    private static double secondsUntil(VtxTable table, int index, double airSpeed, double limitC) {
        VtxHeat heat = VtxHeat.of(table, 22.0);
        for (int i = 1; i <= 36000; i++) {
            if (heat.step(0.05, table.heatW(index), airSpeed, 22.0) >= limitC) {
                return i * 0.05;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @Test
    void startsAtAmbientAndCoolsTowardsIt() {
        VtxHeat heat = VtxHeat.of(RACE, 22.0);
        assertEquals(22.0, heat.tempC(), 1.0E-9);
        heat.set(80.0);
        for (int i = 0; i < 60000; i++) {
            heat.step(0.05, 0.0, 0.0, 22.0);
        }
        assertEquals(22.0, heat.tempC(), 0.05);
    }

    @Test
    void highPowerOnTheGroundOverheatsWithinMinutes() {
        double full = secondsUntil(RACE, 4, 0.0, VtxHeat.LIMIT_C);
        assertTrue(full > 45.0 && full < 150.0, "1600 mW ohne Luftstrom: " + full + " s");
        double rated = secondsUntil(RACE, RACE.ratedIndex(), 0.0, VtxHeat.LIMIT_C);
        assertEquals(Double.POSITIVE_INFINITY, rated, "Nennleistung darf bei Raumtemperatur nicht überhitzen");
        double doubled = secondsUntil(RACE, 3, 0.0, VtxHeat.LIMIT_C);
        assertTrue(doubled > 120.0 && doubled < 400.0, "800 mW ohne Luftstrom: " + doubled + " s");
        assertTrue(secondsUntil(RACE, 0, 0.0, VtxHeat.LIMIT_C) == Double.POSITIVE_INFINITY, "25 mW darf nicht überhitzen");
    }

    @Test
    void predictedTimeMatchesTheSimulation() {
        VtxHeat heat = VtxHeat.of(RACE, 22.0);
        double predicted = heat.secondsToLimit(RACE.heatW(4), 0.0, 22.0);
        assertEquals(secondsUntil(RACE, 4, 0.0, VtxHeat.LIMIT_C), predicted, 0.1);
        assertEquals(Double.POSITIVE_INFINITY, heat.secondsToLimit(RACE.heatW(0), 0.0, 22.0));
        assertTrue(heat.secondsToLimit(RACE.heatW(3), 0.0, 40.0) < heat.secondsToLimit(RACE.heatW(3), 0.0, 5.0));
    }

    @Test
    void airflowKeepsEvenFullPowerCool() {
        VtxHeat heat = VtxHeat.of(RACE, 22.0);
        double cruise = VtxHeat.airSpeed(true, 15.0, 0.35);
        assertTrue(heat.steadyC(RACE.heatW(4), cruise, 22.0) < VtxHeat.WARN_C, "Reiseflug mit 1600 mW");
        double hover = VtxHeat.airSpeed(true, 0.0, 0.3);
        assertTrue(heat.steadyC(RACE.heatW(RACE.ratedIndex()), hover, 22.0) < VtxHeat.WARN_C, "Schweben mit 400 mW");
        assertTrue(heat.steadyC(RACE.heatW(4), 0.0, 22.0) > VtxHeat.LIMIT_C, "1600 mW am Boden");
    }

    @Test
    void modulesAreBuiltForTheirRatedPowerAtRoomTemperature() {
        for (VtxTable table : new VtxTable[]{RACE, HD}) {
            VtxHeat heat = VtxHeat.of(table, 22.0);
            assertEquals(VtxHeat.DESIGN_C, heat.steadyC(table.heatW(table.ratedIndex()), 0.0, 22.0), 1.0E-9, table.toString());
            assertTrue(heat.steadyC(table.heatW(table.ratedIndex()), 0.0, 40.0) > VtxHeat.LIMIT_C, "Wüste: " + table);
        }
        VtxHeat hd = VtxHeat.of(HD, 22.0);
        assertTrue(hd.steadyC(HD.heatW(HD.maxIndex()), 0.0, 22.0) < VtxHeat.WARN_C, "Grundlast dominiert digital");
        assertTrue(hd.steadyC(HD.heatW(HD.maxIndex()), 0.0, 32.0) > VtxHeat.LIMIT_C, "warmer Tag");
    }

    @Test
    void hotBiomesHeatFaster() {
        assertEquals(22.0, VtxHeat.ambientFromBiome(0.8F), 1.0E-6);
        assertTrue(VtxHeat.ambientFromBiome(2.0F) > VtxHeat.ambientFromBiome(0.8F));
        assertTrue(VtxHeat.ambientFromBiome(-0.5F) < 0.0);
        assertEquals(VtxHeat.MAX_AMBIENT_C, VtxHeat.ambientFromBiome(5.0F), 1.0E-9);
        assertEquals(VtxHeat.DEFAULT_AMBIENT_C, VtxHeat.ambientFromBiome(Float.NaN), 1.0E-9);
        assertTrue(VtxHeat.of(RACE, 40.0).steadyC(RACE.heatW(3), 0.0, 40.0) > VtxHeat.of(RACE, 5.0).steadyC(RACE.heatW(3), 0.0, 5.0));
    }

    @Test
    void propsMoveAirOnlyWhenArmed() {
        assertEquals(3.0, VtxHeat.airSpeed(false, 3.0, 0.8), 1.0E-9);
        assertTrue(VtxHeat.airSpeed(true, 0.0, 0.3) > 2.5);
        assertEquals(0.0, VtxHeat.airSpeed(false, Double.NaN, Double.NaN), 1.0E-9);
    }
}
