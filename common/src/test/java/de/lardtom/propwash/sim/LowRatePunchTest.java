package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowRatePunchTest {
    @Test
    void liIonPunchesAtThePracticalRateWhenMixedAndUpToTheCellLimitWhenAggressive() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.LONG_RANGE);
        assertTrue(p.usableFraction >= EnergyModel.LOW_RATE_USABLE);
        double practical = EnergyModel.LOW_RATE_PRACTICAL_C * p.capacityMah / 1000.0;
        assertTrue(practical < p.continuousCurrent);
        EnergyModel.Points pts = new EnergyModel.Points(p, 0.0, 0.0);
        double mixed = pts.punch(3.8, 0.5, EnergyModel.Profile.MIXED);
        double aggressive = pts.punch(5.0, 0.5, EnergyModel.Profile.AGGRESSIVE);
        assertTrue(mixed <= practical + 1e-9, "gemischt " + mixed + " A über 6 C (" + practical + " A)");
        assertTrue(mixed > 0.9 * practical, "gemischt " + mixed + " A");
        assertTrue(aggressive > practical && aggressive <= p.continuousCurrent + 1e-9, "aggressiv " + aggressive + " A");
        BuildAnalysis a = BuildAnalysis.of(DroneBuild.LONG_RANGE);
        double ratio = a.hoverFlightTimeMin() / a.mixedFlightTimeMin();
        assertTrue(ratio >= 1.7 && ratio <= 2.3, "gemischt/Schweben " + ratio);
    }

    @Test
    void lipoPunchesAreNotLimitedByTheLowRateRule() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        assertTrue(p.usableFraction < EnergyModel.LOW_RATE_USABLE);
        EnergyModel.Points pts = new EnergyModel.Points(p, 0.0, 0.0);
        assertEquals(pts.punch(5.5, 0.5, EnergyModel.Profile.AGGRESSIVE), pts.punch(5.5, 0.5, EnergyModel.Profile.MIXED), 0.0);
    }
}
