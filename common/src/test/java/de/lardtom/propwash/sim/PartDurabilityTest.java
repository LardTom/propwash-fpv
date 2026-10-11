package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartDurabilityTest {
    private static final ImpactSample HARD = new ImpactSample(16.0F, 4.0F, 0.0F, 0.0F, 1.0F, 1 | 1 << 1 | 1 << 4, 0, 0.0F);

    private static PropType toughProp(double durability) {
        PropDefinition base = PropType.P51_3B.definition();
        return AddonParts.part(PropDefinition.builder(AddonParts.id("tough_prop"))
                .basics(base.basics()).durability(durability).size(base.diameterMm(), base.pitchInch(), base.blades())
                .coefficients(base.thrustCoefficient(), base.powerCoefficient()).material(PropMaterial.POLYCARBONATE).stiffness(1.0)
                .build());
    }

    private static MotorType toughMotor(double durability) {
        MotorDefinition base = MotorType.M2207_1750.definition();
        return AddonParts.part(MotorDefinition.builder(AddonParts.id("tough_motor"))
                .basics(base.basics().withDurability(durability)).stator(base.statorLabel()).kv(base.kv()).mount(base.mount())
                .maxCurrent(base.maxCurrentAmps()).resistance(base.resistanceOhm()).cells(base.minCells(), base.maxCells())
                .build());
    }

    @Test
    void durabilityDividesTheCrashDose() {
        DroneBuild fs = DroneBuild.FREESTYLE;
        DroneBuild tough = new DroneBuild(fs.frame(), fs.stack(), toughMotor(2.0), toughProp(2.0), fs.video(), fs.battery());
        PartWear normal = CrashDamage.apply(PartWear.PRISTINE, fs, HARD, 1.0, 99L);
        PartWear strong = CrashDamage.apply(PartWear.PRISTINE, tough, HARD, 1.0, 99L);
        boolean anyProp = false;
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            float propLoss = 1.0F - normal.prop(position);
            float toughPropLoss = 1.0F - strong.prop(position);
            assertTrue(toughPropLoss <= propLoss, "Prop " + position + " " + toughPropLoss + " > " + propLoss);
            if (propLoss > 0.01F) {
                anyProp = true;
                assertTrue(toughPropLoss < 0.75F * propLoss, "Prop " + position + " " + toughPropLoss + " / " + propLoss);
            }
            float motorLoss = 1.0F - normal.motor(position);
            float toughMotorLoss = 1.0F - strong.motor(position);
            assertTrue(toughMotorLoss < motorLoss || motorLoss == 0.0F);
            assertEquals(normal.arm(position), strong.arm(position));
        }
        assertTrue(anyProp, "harter Aufprall beschädigt Props: " + normal);
        assertEquals(normal.stack(), strong.stack());
    }

    @Test
    void propMaterialDefaultsSetDurabilityAndStiffness() {
        PropType carbon = AddonParts.part(PropDefinition.builder(AddonParts.id("carbon_prop")).mass(4.0).size(130, 4.5, 3)
                .coefficients(0.12, 0.06).material(PropMaterial.CARBON).build());
        assertEquals(PropMaterial.CARBON.defaultDurability(), carbon.definition().basics().durability());
        assertEquals(PropMaterial.CARBON.defaultStiffness(), carbon.definition().stiffness());
        AirframeSpec p = AirframeSpec.of(new DroneBuild(DroneBuild.FREESTYLE.frame(), DroneBuild.FREESTYLE.stack(), DroneBuild.FREESTYLE.motor(),
                carbon, DroneBuild.FREESTYLE.video(), DroneBuild.FREESTYLE.battery()));
        assertTrue(p.washNoise < AirframeSpec.of(DroneBuild.FREESTYLE).washNoise);
    }

    @Test
    void motorHeatWearIsDividedByDurability() {
        AirframeSpec normal = AirframeSpec.of(DroneBuild.FREESTYLE);
        DroneBuild fs = DroneBuild.FREESTYLE;
        AirframeSpec tough = AirframeSpec.of(new DroneBuild(fs.frame(), fs.stack(), toughMotor(4.0), fs.prop(), fs.video(), fs.battery()));
        assertEquals(normal.motorHeatWearPerSecond / 4.0, tough.motorHeatWearPerSecond, 1e-15);
    }
}
