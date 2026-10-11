package de.lardtom.propwash.audio;

import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PropType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorTimbreSizeTest {
    private static DroneBuild withProp(int diameterMm) {
        PropType prop = (PropType) PartIndex.detached(PropDefinition.builder(Identifier.fromNamespaceAndPath("propwash_audiotest", "p" + diameterMm))
                .mass(4.0 + diameterMm / 20.0).size(diameterMm, 4.0, 2).coefficients(0.09, 0.036).material(PropMaterial.POLYCARBONATE).build());
        DroneBuild lr = DroneBuild.LONG_RANGE;
        return new DroneBuild(lr.frame(), lr.stack(), lr.motor(), prop, lr.video(), lr.battery());
    }

    @Test
    void propsAboveSevenInchSoundDeeper() {
        MotorTimbre seven = MotorTimbre.derive(DroneBuild.LONG_RANGE);
        MotorTimbre ten = MotorTimbre.derive(withProp(254));
        MotorTimbre thirteen = MotorTimbre.derive(withProp(330));
        assertEquals(1.0F, seven.sizeClass());
        assertTrue(ten.sizeClass() > 1.0F && thirteen.sizeClass() > ten.sizeClass());
        assertTrue(thirteen.harmonicTilt() > seven.harmonicTilt());
        assertTrue(thirteen.whineGain() < seven.whineGain());
        assertTrue(thirteen.rumbleGain() > seven.rumbleGain());
        assertTrue(thirteen.whineGain() > 0.0F);
    }
}
