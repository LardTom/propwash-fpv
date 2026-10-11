package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PropType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisApiFieldsTest {
    private static PropType stiffProp(PropType base, double stiffness) {
        PropDefinition d = base.definition();
        return AddonParts.part(PropDefinition.builder(AddonParts.id("stiff_prop")).basics(d.basics()).size(d.diameterMm(), d.pitchInch(), d.blades())
                .coefficients(d.thrustCoefficient(), d.powerCoefficient()).material(PropMaterial.POLYCARBONATE).stiffness(stiffness).build());
    }

    private static DroneBuild withProp(DroneBuild build, PropType prop) {
        return new DroneBuild(build.frame(), build.stack(), build.motor(), prop, build.video(), build.battery(), build.accessories());
    }

    @Test
    void builtinPropsHaveTheNeutralPropwashNoise() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertEquals(1.0, AirframeSpec.of(preset).propwashNoiseFactor(), preset.frame().key());
        }
    }

    @Test
    void propwashNoiseFactorIsTheRatioOfTheDerivedWashNoise() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            AirframeSpec base = AirframeSpec.of(preset);
            for (double stiffness : new double[]{0.6, 1.2, 1.4, 2.2, 4.0}) {
                AirframeSpec stiff = AirframeSpec.of(withProp(preset, stiffProp(preset.prop(), stiffness)));
                assertEquals(stiff.washNoise / base.washNoise, stiff.propwashNoiseFactor(), 1.0E-12, preset.frame().key() + " " + stiffness);
            }
        }
        assertTrue(AirframeSpec.of(withProp(DroneBuild.FREESTYLE, stiffProp(PropType.P51_3B, 2.2))).propwashNoiseFactor() < 1.0);
    }
}
