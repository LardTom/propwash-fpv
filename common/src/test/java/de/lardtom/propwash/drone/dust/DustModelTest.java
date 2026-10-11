package de.lardtom.propwash.drone.dust;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.dust.DustModel.Detail;
import de.lardtom.propwash.drone.dust.DustModel.Material;
import de.lardtom.propwash.drone.dust.DustModel.Surface;
import de.lardtom.propwash.drone.dust.DustModel.Wash;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustModelTest {
    private static final double G = 9.81;

    private static double jet(DroneBuild build) {
        return DustModel.jetDiameter(AirframeSpec.of(build).discArea, AirframeSpec.MOTORS);
    }

    private static double hoverThrust(DroneBuild build) {
        return AirframeSpec.of(build).massKg * G;
    }

    private static double rate(DroneBuild build, double height, double thrust, Surface surface, Material material) {
        return DustModel.evaluate(height, 1.0, thrust, jet(build), surface, material, DustModel.DEFAULT_INTENSITY, Detail.ALL).ratePerSecond();
    }

    @Test
    void racerHoverStirsDustOnlyCloseToTheGround() {
        double thrust = hoverThrust(DroneBuild.RACER);
        double reach = DustModel.reach(DustModel.jetSpeed(thrust, jet(DroneBuild.RACER)), jet(DroneBuild.RACER));
        assertTrue(reach > 0.6 && reach < 1.3, "Reichweite im Schwebeflug " + reach);
        assertTrue(rate(DroneBuild.RACER, 0.3, thrust, Surface.SOLID, Material.LOOSE) > 1.0);
        assertEquals(0.0, rate(DroneBuild.RACER, reach + 0.05, thrust, Surface.SOLID, Material.LOOSE));
        assertEquals(0.0, rate(DroneBuild.RACER, 2.5, thrust, Surface.SOLID, Material.LOOSE));
        assertEquals(0.0, rate(DroneBuild.RACER, 12.0, thrust * 6.0, Surface.SOLID, Material.LOOSE));
    }

    @Test
    void reachStaysWithinTheDesignedBand() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE}) {
            AirframeSpec spec = AirframeSpec.of(build);
            double d = jet(build);
            double maxThrust = AirframeSpec.MOTORS * spec.kThrust * spec.ductGain * spec.omegaMax * spec.omegaMax;
            for (double thrust : new double[]{hoverThrust(build), 2.0 * hoverThrust(build), maxThrust}) {
                double reach = DustModel.reach(DustModel.jetSpeed(thrust, d), d);
                assertTrue(reach >= DustModel.MIN_REACH && reach <= DustModel.MAX_REACH, build + " Reichweite " + reach);
            }
        }
        assertEquals(DustModel.MIN_REACH, DustModel.reach(DustModel.jetSpeed(hoverThrust(DroneBuild.WHOOP), jet(DroneBuild.WHOOP)), jet(DroneBuild.WHOOP)), 1.0E-9);
        assertEquals(0.0, DustModel.reach(0.0, 0.25));
        assertEquals(0.0, DustModel.reach(DustModel.PICKUP_SPEED, 0.25));
    }

    @Test
    void biggerPropsReachFurtherAtTheSameLoading() {
        double whoop = DustModel.reach(DustModel.jetSpeed(hoverThrust(DroneBuild.WHOOP), jet(DroneBuild.WHOOP)), jet(DroneBuild.WHOOP));
        double racer = DustModel.reach(DustModel.jetSpeed(hoverThrust(DroneBuild.RACER), jet(DroneBuild.RACER)), jet(DroneBuild.RACER));
        double longRange = DustModel.reach(DustModel.jetSpeed(hoverThrust(DroneBuild.LONG_RANGE), jet(DroneBuild.LONG_RANGE)), jet(DroneBuild.LONG_RANGE));
        assertTrue(whoop < racer && racer < longRange, whoop + " < " + racer + " < " + longRange);
        assertTrue(DustModel.sizeFactor(jet(DroneBuild.LONG_RANGE)) > DustModel.sizeFactor(jet(DroneBuild.WHOOP)));
    }

    @Test
    void rateGrowsWithThrust() {
        double hover = hoverThrust(DroneBuild.FREESTYLE);
        double previous = 0.0;
        for (double factor : new double[]{1.0, 2.0, 4.0, 8.0}) {
            double r = rate(DroneBuild.FREESTYLE, 0.4, hover * factor, Surface.SOLID, Material.SOIL);
            assertTrue(r > previous, "Rate bei " + factor + "× Schwebeschub " + r + " <= " + previous);
            previous = r;
        }
        assertEquals(0.0, rate(DroneBuild.FREESTYLE, 0.0, 0.0, Surface.SOLID, Material.LOOSE));
    }

    @Test
    void rateGrowsWhenCloserToTheGround() {
        double thrust = 3.0 * hoverThrust(DroneBuild.RACER);
        double previous = 0.0;
        for (double height = 1.5; height >= 0.0; height -= 0.1) {
            double r = rate(DroneBuild.RACER, height, thrust, Surface.SOLID, Material.LOOSE);
            assertTrue(r >= previous, "Rate in " + height + " m " + r + " < " + previous);
            previous = r;
        }
        assertTrue(previous > 0.0);
        assertEquals(rate(DroneBuild.RACER, 0.0, thrust, Surface.SOLID, Material.LOOSE), rate(DroneBuild.RACER, -0.2, thrust, Surface.SOLID, Material.LOOSE));
    }

    @Test
    void surfaceDecidesWhatIsStirred() {
        double thrust = 3.0 * hoverThrust(DroneBuild.RACER);
        double loose = rate(DroneBuild.RACER, 0.3, thrust, Surface.SOLID, Material.LOOSE);
        double soil = rate(DroneBuild.RACER, 0.3, thrust, Surface.SOLID, Material.SOIL);
        double covered = rate(DroneBuild.RACER, 0.3, thrust, Surface.SOLID, Material.COVERED);
        double hard = rate(DroneBuild.RACER, 0.3, thrust, Surface.SOLID, Material.HARD);
        double slick = rate(DroneBuild.RACER, 0.3, thrust, Surface.SOLID, Material.SLICK);
        double water = rate(DroneBuild.RACER, 0.3, thrust, Surface.WATER, Material.LOOSE);
        assertTrue(loose > soil && soil > covered && covered > hard && hard > slick && slick > 0.0);
        assertTrue(water > 0.0 && water < loose);
        assertEquals(0.0, rate(DroneBuild.RACER, 0.3, thrust, Surface.NONE, Material.LOOSE));
        assertEquals(0.0, rate(DroneBuild.RACER, 0.3, thrust, null, Material.LOOSE));
    }

    @Test
    void particleSettingAndIntensityScaleTheRate() {
        double thrust = 3.0 * hoverThrust(DroneBuild.RACER);
        double d = jet(DroneBuild.RACER);
        double all = DustModel.evaluate(0.3, 1.0, thrust, d, Surface.SOLID, Material.LOOSE, 1.0, Detail.ALL).ratePerSecond();
        double decreased = DustModel.evaluate(0.3, 1.0, thrust, d, Surface.SOLID, Material.LOOSE, 1.0, Detail.DECREASED).ratePerSecond();
        double minimal = DustModel.evaluate(0.3, 1.0, thrust, d, Surface.SOLID, Material.LOOSE, 1.0, Detail.MINIMAL).ratePerSecond();
        assertTrue(all > decreased && decreased > 0.0);
        assertEquals(0.0, minimal);
        double half = DustModel.evaluate(0.3, 1.0, thrust, d, Surface.SOLID, Material.LOOSE, 0.5, Detail.ALL).ratePerSecond();
        assertEquals(all * Math.pow(0.5, DustModel.AMOUNT_EXPONENT), half, 1.0E-9);
        double standard = DustModel.evaluate(0.3, 1.0, thrust, d, Surface.SOLID, Material.LOOSE, DustModel.DEFAULT_INTENSITY, Detail.ALL).ratePerSecond();
        assertTrue(all > 3.5 * standard, "100 % Staubmenge deutlich mehr als der Standard");
        double tooMuch = DustModel.evaluate(0.3, 1.0, thrust, d, Surface.SOLID, Material.LOOSE, 5.0, Detail.ALL).ratePerSecond();
        assertEquals(all, tooMuch, 1.0E-9);
        assertEquals(DustModel.DEFAULT_INTENSITY, DustModel.clampIntensity(Double.NaN), 1.0E-6);
        assertEquals(DustModel.MIN_INTENSITY, DustModel.clampIntensity(0.0), 1.0E-6);
    }

    @Test
    void defaultIsSubtle() {
        double hover = rate(DroneBuild.RACER, 0.3, hoverThrust(DroneBuild.RACER), Surface.SOLID, Material.COVERED);
        assertTrue(hover > 0.5 && hover < 6.0, "Schwebeflug über Gras " + hover + "/s");
        double punch = DustModel.evaluate(0.0, 1.0, 1.0e4, 2.0, Surface.SOLID, Material.LOOSE, DustModel.DEFAULT_INTENSITY, Detail.ALL).ratePerSecond();
        assertTrue(punch <= DustModel.BASE_RATE * DustModel.MAX_SIZE_FACTOR * DustModel.amount(DustModel.DEFAULT_INTENSITY) + 1.0E-9);
        assertTrue(Detail.ALL.cap <= DustModel.ENGINE_LIMIT);
    }

    @Test
    void tiltMovesTheJetAwayAndFlippedDronesStirNothing() {
        double thrust = 3.0 * hoverThrust(DroneBuild.RACER);
        double level = rate(DroneBuild.RACER, 0.5, thrust, Surface.SOLID, Material.LOOSE);
        double tilted = DustModel.evaluate(0.5, 0.7, thrust, jet(DroneBuild.RACER), Surface.SOLID, Material.LOOSE,
                DustModel.DEFAULT_INTENSITY, Detail.ALL).ratePerSecond();
        assertTrue(tilted > 0.0 && tilted < level);
        assertEquals(0.0, DustModel.evaluate(0.2, 0.1, thrust, jet(DroneBuild.RACER), Surface.SOLID, Material.LOOSE, 1.0, Detail.ALL).ratePerSecond());
        assertEquals(0.0, DustModel.evaluate(0.2, -1.0, thrust, jet(DroneBuild.RACER), Surface.SOLID, Material.LOOSE, 1.0, Detail.ALL).ratePerSecond());
        assertEquals(0.0, DustModel.probeDepth(1.0, -1.0));
        assertEquals(0.7, DustModel.probeDepth(1.0, 0.7), 1.0E-9);
    }

    @Test
    void washReportsWhatHappened() {
        double thrust = 3.0 * hoverThrust(DroneBuild.RACER);
        Wash near = DustModel.evaluate(0.2, 1.0, thrust, jet(DroneBuild.RACER), Surface.SOLID, Material.SOIL, 0.4, Detail.ALL);
        assertTrue(near.active());
        assertTrue(near.groundSpeed() > DustModel.PICKUP_SPEED);
        assertEquals(0.2, near.distance(), 1.0E-9);
        Wash far = DustModel.evaluate(5.0, 1.0, thrust, jet(DroneBuild.RACER), Surface.SOLID, Material.SOIL, 0.4, Detail.ALL);
        assertFalse(far.active());
        assertFalse(DustModel.Wash.NONE.active());
    }

    @Test
    void capLimitsNewParticles() {
        assertEquals(4, DustModel.allowed(4, 0, Detail.ALL));
        assertEquals(2, DustModel.allowed(4, Detail.ALL.cap - 2, Detail.ALL));
        assertEquals(0, DustModel.allowed(4, Detail.ALL.cap, Detail.ALL));
        assertEquals(0, DustModel.allowed(4, Detail.ALL.cap + 10, Detail.ALL));
        assertEquals(0, DustModel.allowed(4, 0, Detail.MINIMAL));
        assertEquals(1, DustModel.allowed(1, Detail.DECREASED.cap - 1, Detail.DECREASED));
        assertEquals(0, DustModel.allowed(0, 0, Detail.ALL));
    }

    @Test
    void particlesFadeInAndOut() {
        float peak = 0.35F;
        assertEquals(0.0F, DustModel.alpha(0.0, peak));
        assertEquals(0.0F, DustModel.alpha(1.0, peak));
        assertEquals(peak, DustModel.alpha(0.3, peak), 1.0E-6F);
        assertTrue(DustModel.alpha(0.05, peak) < peak);
        assertTrue(DustModel.alpha(0.9, peak) < DustModel.alpha(0.6, peak));
        for (double t = 0.0; t <= 1.0; t += 0.01) {
            float a = DustModel.alpha(t, peak);
            assertTrue(a >= 0.0F && a <= peak + 1.0E-6F);
        }
        assertTrue(DustModel.growth(1.0) > DustModel.growth(0.0));
    }

    @Test
    void spritesThinOutOverTheLifetime() {
        assertEquals(6, DustModel.spriteFor(6, 0.0, 8));
        assertEquals(1, DustModel.spriteFor(6, 1.0, 8));
        assertEquals(7, DustModel.spriteFor(12, 0.0, 8));
        assertEquals(1, DustModel.spriteFor(0, 0.0, 8));
        int previous = Integer.MAX_VALUE;
        for (double t = 0.0; t <= 1.0; t += 0.05) {
            int index = DustModel.spriteFor(7, t, 8);
            assertTrue(index <= previous && index >= 1);
            previous = index;
        }
    }

    @Test
    void dustColorFollowsTheGround() {
        int sand = DustModel.color(0xF7E9A3, Material.LOOSE);
        int snow = DustModel.color(0xFFFFFF, Material.LOOSE);
        int dirt = DustModel.color(0x976D4D, Material.SOIL);
        int grass = DustModel.color(0x7FB238, Material.COVERED);
        assertEquals(0xFFFFFF, snow);
        assertTrue(brightness(sand) > brightness(dirt));
        assertTrue((sand >> 16 & 0xFF) > (sand & 0xFF), "Sand bleibt warm");
        assertTrue((grass >> 8 & 0xFF) < 0xB2, "Gras wird mit Erde gemischt");
        assertEquals(DustModel.color(DustModel.NEUTRAL_COLOR, Material.HARD), DustModel.color(0, Material.HARD));
    }

    private static int brightness(int rgb) {
        return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
    }

    @Test
    void maximumDustIsClearlyDenserThanTheDefault() {
        assertEquals(1.0F, DustModel.visualScale(DustModel.DEFAULT_INTENSITY), 1.0E-6);
        assertTrue(DustModel.amount(DustModel.MAX_INTENSITY) >= 3.5 * DustModel.amount(DustModel.DEFAULT_INTENSITY));
        assertTrue(DustModel.visualScale(DustModel.MAX_INTENSITY) > DustModel.visualScale(DustModel.DEFAULT_INTENSITY));
    }
}
