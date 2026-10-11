package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootprintsTest {
    @Test
    void diameterIsTheWidestHubDistancePlusOneProp() {
        double s = 100.0;
        double[][] hubs = {{-s, 0.0, -s}, {s, 0.0, -s}, {-s, 0.0, s}, {s, 0.0, s}};
        assertEquals(2.0 * Math.sqrt(2.0) * s + 2.0 * 64.0, Footprints.diameterMm(hubs, 64.0), 1e-9);
        assertTrue(Double.isNaN(Footprints.diameterMm(new double[0][], 64.0)));
    }

    @Test
    void presetsGrowWithTheirClassAndMatchTheRegistryEstimate() {
        double whoop = BuildAccessImpl.INSTANCE.footprintMm(Conversions.spec(DroneBuild.WHOOP));
        double freestyle = BuildAccessImpl.INSTANCE.footprintMm(Conversions.spec(DroneBuild.FREESTYLE));
        double longRange = BuildAccessImpl.INSTANCE.footprintMm(Conversions.spec(DroneBuild.LONG_RANGE));
        assertTrue(whoop > 60.0 && whoop < 140.0, "Whoop " + whoop);
        assertTrue(freestyle > 300.0 && freestyle < 500.0, "Freestyle " + freestyle);
        assertTrue(longRange > freestyle, "Long Range " + longRange);
        for (DroneBuild preset : DroneBuild.PRESETS) {
            double rig = BuildAccessImpl.INSTANCE.footprintMm(Conversions.spec(preset));
            double estimate = Footprints.fromRegistry(Conversions.spec(preset));
            assertEquals(rig, estimate, 0.15 * rig, preset.frame().id().toString());
        }
    }

    @Test
    void unknownPartsThrow() {
        Identifier unknown = Identifier.fromNamespaceAndPath("somewhere", "else");
        assertThrows(IllegalArgumentException.class,
                () -> BuildAccessImpl.INSTANCE.footprintMm(Conversions.spec(DroneBuild.FREESTYLE).withPart(PartKind.FRAME, unknown)));
        assertThrows(IllegalArgumentException.class,
                () -> Footprints.fromRegistry(Conversions.spec(DroneBuild.FREESTYLE).withPart(PartKind.FRAME, unknown)));
    }
}
