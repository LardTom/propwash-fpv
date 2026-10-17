package de.lardtom.propwash.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RacePresetTest {
    private static final DroneBuild RACE = DroneBuild.RACER;
    private static final DroneBuild STEEP = DroneBuild.RACER.withProp(PropType.P51_3B);

    private static JsonObject json(String resource) throws IOException {
        try (InputStream in = RacePresetTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void presetIsATrueXRacerWithALowPitchTriBlade() {
        assertSame(FrameType.RACE_5, RACE.frame());
        assertEquals(FrameShape.TRUE_X, RACE.frame().definition().shape());
        assertFalse(RACE.frame().definition().geometry().batteryOnTop(), "Akku hängt unter dem Rahmen");
        PropType prop = RACE.prop();
        assertSame(PropType.P51_29_3B, prop);
        assertEquals(130, prop.diameterMm);
        assertEquals(2.9, prop.pitchInch, 1.0E-9);
        assertEquals(3, prop.blades);
        assertEquals(4.4, prop.massGrams(), 1.0E-9);
        MotorType motor = RACE.motor();
        assertTrue(motor.kv >= 1750 && motor.kv <= 1950, "KV " + motor.kv);
        assertTrue(motor.statorLabel.startsWith("2207") || motor.statorLabel.startsWith("2306"), motor.statorLabel);
        assertEquals(6, RACE.battery().cells);
        assertTrue(RACE.buildable(), RACE.problems().toString());
        assertTrue(RACE.warnings().isEmpty(), RACE.warnings().toString());
    }

    @Test
    void raceFrameIsDrawnAndFlownAsTrueX() throws IOException {
        JsonArray motors = json("/assets/propwash/drone_layout.json").getAsJsonObject("frames").getAsJsonObject("race_5").getAsJsonArray("motors");
        assertEquals(4, motors.size());
        AirframeSpec spec = AirframeSpec.of(RACE);
        for (int i = 0; i < motors.size(); i++) {
            JsonArray motor = motors.get(i).getAsJsonArray();
            double x = Math.abs(motor.get(0).getAsDouble());
            double z = Math.abs(motor.get(2).getAsDouble());
            assertEquals(x, z, 0.02, "Motor " + i + " im Bild");
            assertEquals(RACE.frame().wheelbaseMm, 2.0 * Math.hypot(x, z), 0.1, "Diagonale");
            assertEquals(x, Math.abs(spec.motorX(i)) * 1000.0, 0.5, "Motor " + i + " im Flugmodell, x");
            assertEquals(z, Math.abs(spec.motorZ(i)) * 1000.0, 0.5, "Motor " + i + " im Flugmodell, z");
        }
    }

    @Test
    void staticCoefficientsFollowTheBenchAgainstThe46Pitch() {
        PropType low = PropType.P51_29_3B;
        PropType steep = PropType.P51_3B;
        double thrust = low.thrustCoefficient / steep.thrustCoefficient;
        double power = low.powerCoefficient / steep.powerCoefficient;
        assertTrue(thrust >= 1.005 && thrust <= 1.035, "c_t-Verhältnis " + thrust);
        assertTrue(power >= 1.04 && power <= 1.11, "c_p-Verhältnis " + power);
        double merit = Math.pow(thrust, 1.5) / power;
        assertTrue(merit >= 0.93 && merit <= 0.97, "Gütegrad-Verhältnis " + merit);
        assertEquals(steep.diameterMm, low.diameterMm);
        assertTrue(low.pitchInch < 0.7 * steep.pitchInch);
    }

    @Test
    void lowPitchKeepsStaticThrustAndCostsTopSpeed() {
        BuildAnalysis race = BuildAnalysis.of(RACE);
        BuildAnalysis steep = BuildAnalysis.of(STEEP);
        double thrust = race.maxThrustPerMotorGrams() / steep.maxThrustPerMotorGrams();
        assertTrue(thrust >= 0.975 && thrust <= 0.995, "Standschub " + thrust);
        double current = race.fullThrottleCurrentA() / steep.fullThrottleCurrentA();
        assertTrue(current >= 1.02 && current <= 1.07, "Vollgasstrom " + current);
        assertTrue(race.topSpeedAcroKmh() >= 140.0 && race.topSpeedAcroKmh() <= 160.0, "Topspeed " + race.topSpeedAcroKmh());
        assertTrue(race.topSpeedAcroKmh() < 0.92 * steep.topSpeedAcroKmh(), race.topSpeedAcroKmh() + " gegen " + steep.topSpeedAcroKmh());
        assertTrue(race.topSpeedAcroKmh() > BuildAnalysis.of(DroneBuild.FREESTYLE).topSpeedAcroKmh(), "Racer schneller als Freestyle");
        double hover = race.hoverFlightTimeMin() / steep.hoverFlightTimeMin();
        assertTrue(hover >= 0.94 && hover <= 1.0, "Schwebezeit " + hover);
    }

    @Test
    void presetStaysInTheRaceCorridor() {
        BuildAnalysis race = BuildAnalysis.of(RACE);
        assertTrue(race.massGrams() >= 400.0 && race.massGrams() <= 480.0, "Masse " + race.massGrams());
        assertTrue(race.massGrams() - RACE.battery().massGrams() >= 250.0 && race.massGrams() - RACE.battery().massGrams() <= 300.0, "trocken");
        assertTrue(race.thrustToWeight() >= 11.0 && race.thrustToWeight() <= 15.0, "Schub/Gewicht " + race.thrustToWeight());
        assertTrue(race.hoverThrottlePercent() >= 16.0 && race.hoverThrottlePercent() <= 25.0, "Schwebegas " + race.hoverThrottlePercent());
        assertTrue(race.mixedFlightTimeMin() >= 2.5 && race.mixedFlightTimeMin() <= 3.5, "gemischt " + race.mixedFlightTimeMin());
        assertTrue(race.aggressiveFlightTimeMin() >= 1.7 && race.aggressiveFlightTimeMin() <= 2.5, "aggressiv " + race.aggressiveFlightTimeMin());
        assertTrue(race.batteryLoadPercent() < 100.0, "Akkulast " + race.batteryLoadPercent());
        assertTrue(race.propsInViewPercent() >= 6.0 && race.propsInViewPercent() <= 10.0, "Props im Bild " + race.propsInViewPercent());
        assertTrue(race.warnings().isEmpty(), race.warnings().toString());
    }

    @Test
    void newPropIsAppendedBehindTheOlderOnes() {
        List<PropType> props = PropType.all().stream().filter(PartInfo::builtin).toList();
        assertEquals(List.of(PropType.P31_4B, PropType.P31_3B, PropType.P3_3B, PropType.P51_3B, PropType.P7_2B, PropType.P51_29_3B), props);
        assertEquals("p51_29_3b", PropType.P51_29_3B.key());
        assertEquals("part.propwash.p51_29_3b", PropType.P51_29_3B.translationKey());
    }

    @Test
    void recipeIsTheFiveInchPropWithTheGoldHubOfRaceParts() throws IOException {
        JsonObject race = json("/data/propwash/recipe/p51_29_3b.json");
        JsonObject standard = json("/data/propwash/recipe/p51_3b.json");
        assertEquals("propwash:p51_29_3b", race.getAsJsonObject("result").get("id").getAsString());
        assertEquals(4, race.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(standard.getAsJsonArray("pattern").size(), race.getAsJsonArray("pattern").size());
        assertTrue(race.getAsJsonObject("key").toString().contains("minecraft:gold_nugget"));
        assertTrue(race.getAsJsonObject("key").toString().contains("minecraft:dried_kelp"));
        assertFalse(race.getAsJsonObject("key").toString().contains("minecraft:iron_nugget"));
        assertTrue(json("/data/propwash/recipe/race_5.json").getAsJsonObject("key").toString().contains("minecraft:gold_nugget"));
    }

    @Test
    void bothLanguagesNameAndDescribeTheProp() throws IOException {
        JsonObject english = json("/assets/propwash/lang/en_us.json");
        JsonObject german = json("/assets/propwash/lang/de_de.json");
        assertEquals("5.1″ Propeller (3-blade, 2.9″ pitch, PC)", english.get("part.propwash.p51_29_3b").getAsString());
        assertEquals("5,1-Zoll-Propeller (3 Blätter, 2,9″ Steigung, PC)", german.get("part.propwash.p51_29_3b").getAsString());
        assertTrue(english.get("propwash.help.p51_29_3b.summary").getAsString().startsWith("5.1″ tri-blade, PC – "));
        assertTrue(german.get("propwash.help.p51_29_3b.summary").getAsString().startsWith("5,1″-Dreiblatt, PC – "));
        assertTrue(english.get("propwash.help.race_5.summary").getAsString().contains("true X"));
        assertTrue(german.get("propwash.help.race_5.summary").getAsString().contains("True-X"));
    }
}
