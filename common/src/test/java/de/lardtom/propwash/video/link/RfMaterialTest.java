package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfMaterialTest {
    private static final double F = RadioBand.FREQUENCY_GHZ;

    private static double p2040Dielectric(double a, double c, double d) {
        double conductivity = c * Math.pow(F, d);
        return 1636.0 * conductivity / Math.sqrt(a);
    }

    @Test
    void wavelengthAndFresnelRadiusAt5800MHz() {
        assertEquals(0.05169, RadioBand.WAVELENGTH_M, 1.0E-5);
        assertEquals(Math.sqrt(RadioBand.WAVELENGTH_M * 25.0 * 25.0 / 50.0), RadioBand.firstFresnelRadius(25.0, 25.0), 1.0E-12);
        assertEquals(0.804, RadioBand.firstFresnelRadius(25.0, 25.0), 1.0E-3);
        assertEquals(Math.sqrt(2.0), RadioBand.nu(RadioBand.firstFresnelRadius(10.0, 90.0), 10.0, 90.0), 1.0E-12);
    }

    @Test
    void buildingMaterialsFollowItuP2040Table3() {
        assertEquals(p2040Dielectric(5.24, 0.0462, 0.7822), RfMaterial.Wall.CONCRETE.attenuationDbPerMeter(F), 0.5);
        assertEquals(p2040Dielectric(3.91, 0.0238, 0.16), RfMaterial.Wall.BRICK.attenuationDbPerMeter(F), 0.1);
        assertEquals(p2040Dielectric(1.99, 0.0047, 1.0718), RfMaterial.Wall.WOOD.attenuationDbPerMeter(F), 0.1);
        assertEquals(p2040Dielectric(6.31, 0.0036, 1.3394), RfMaterial.Wall.GLASS.attenuationDbPerMeter(F), 0.1);
        assertEquals(130.45, RfMaterial.Wall.CONCRETE.attenuationDbPerMeter(F), 0.1);
        assertEquals(26.09, RfMaterial.Wall.BRICK.attenuationDbPerMeter(F), 0.05);
        assertEquals(35.87, RfMaterial.Wall.WOOD.attenuationDbPerMeter(F), 0.05);
        assertEquals(24.70, RfMaterial.Wall.GLASS.attenuationDbPerMeter(F), 0.05);
        assertEquals(282.7, RfMaterial.Wall.MEDIUM_DRY_GROUND.attenuationDbPerMeter(F), 0.5);
        assertEquals(11.89, RfMaterial.Wall.VERY_DRY_GROUND.attenuationDbPerMeter(F), 0.05);
    }

    @Test
    void waterIceAndSnowFollowItuP527() {
        double water = RfMaterial.Wall.WATER.attenuationDbPerMeter(F);
        assertTrue(water > 1400.0 && water < 1850.0, "water " + water);
        assertEquals(1834.0, Dielectric.pureWater(F, 10.0).attenuationDbPerMeter(F), 2.0);
        assertEquals(1405.1, Dielectric.pureWater(F, 20.0).attenuationDbPerMeter(F), 2.0);
        assertEquals(72.25, Dielectric.pureWater(F, 20.0).real(), 0.05);
        assertEquals(0.163, RfMaterial.Wall.ICE.attenuationDbPerMeter(F), 0.005);
        assertEquals(3.184, RfMaterial.Wall.ICE.real(), 0.001);
        assertEquals(0.0454, RfMaterial.Wall.SNOW.attenuationDbPerMeter(F), 0.002);
        assertEquals(1.57, RfMaterial.Wall.SNOW.real(), 1.0E-9);
    }

    @Test
    void wallsMatchMeasuredSingleWallLossesAt585GHz() {
        assertEquals(10.2, RfMaterial.MASONRY.straightBlockDb(), 0.6);
        double timber = RfMaterial.TIMBER.straightBlockDb();
        assertTrue(timber > 3.6 && timber < 6.0, "wooden wall " + timber);
        double concrete = RfMaterial.CONCRETE.straightBlockDb();
        assertTrue(concrete > 25.0 && concrete < 40.0, "concrete wall " + concrete);
    }

    @Test
    void windowPaneIsAFewDecibels() {
        double pane = RfMaterial.GLASS.dbPerCrossing();
        assertTrue(pane > 2.0 && pane < 4.0, "6 mm pane " + pane);
        assertEquals(0.0, RfMaterial.GLASS.dbPerMeter(), 0.0);
        assertEquals(0.0, Dielectric.p2040(1.0, 0.0, 0.0, 0.0, F).thinSlabLossDb(0.01, F), 1.0E-12);
    }

    @Test
    void interfacesReflectMoreForDenserMaterials() {
        assertEquals(0.99, RfMaterial.Wall.BRICK.interfaceLossDb(), 0.02);
        assertTrue(RfMaterial.Wall.CONCRETE.interfaceLossDb() > RfMaterial.Wall.BRICK.interfaceLossDb());
        assertTrue(RfMaterial.Wall.BRICK.interfaceLossDb() > RfMaterial.Wall.WOOD.interfaceLossDb());
        assertEquals(0.0, Dielectric.p2040(1.0, 0.0, 0.0, 0.0, F).interfaceLossDb(), 1.0E-12);
    }

    @Test
    void oneBlockOfEarthWaterOrMetalIsOpaque() {
        for (RfMaterial opaque : new RfMaterial[]{RfMaterial.EARTH, RfMaterial.WATER, RfMaterial.METAL, RfMaterial.TRUNK}) {
            assertTrue(opaque.straightBlockDb() >= 35.0, opaque + " " + opaque.straightBlockDb());
        }
    }

    @Test
    void lightMaterialsCostLittle() {
        assertTrue(RfMaterial.SNOW.straightBlockDb() < 0.5);
        assertTrue(RfMaterial.ICE.straightBlockDb() < 1.5);
        assertEquals(FoliageLoss.db(1.0), RfMaterial.FOLIAGE.straightBlockDb(), 1.0E-12);
        assertEquals(FoliageLoss.db(RfMaterial.Wall.PLANT_DENSITY), RfMaterial.PLANT.straightBlockDb(), 1.0E-12);
        assertEquals(0.0, RfMaterial.AIR.straightBlockDb(), 0.0);
    }

    @Test
    void orderingFromLightToHeavy() {
        RfMaterial[] order = {RfMaterial.AIR, RfMaterial.SNOW, RfMaterial.PLANT, RfMaterial.ICE, RfMaterial.FOLIAGE, RfMaterial.GLASS,
                RfMaterial.TIMBER, RfMaterial.MASONRY, RfMaterial.CONCRETE, RfMaterial.EARTH, RfMaterial.WATER, RfMaterial.METAL};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i].straightBlockDb() > order[i - 1].straightBlockDb(), order[i - 1] + " < " + order[i]);
        }
    }
}
