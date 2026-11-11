package de.lardtom.propwash.accessory.hang;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.drone.client.render.DronePhysique;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessoryStandTest {
    private static final double EPS = 1.0E-5;

    private static AccessoryType type(AccessoryDefinition definition) {
        return (AccessoryType) PartIndex.detached(definition);
    }

    static DroneBuild freestyle() {
        AccessoryType dispenser = type(AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("standtest", "dispenser_mount"))
                .basics(PartBasics.of(150.0)).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.0036).build());
        AccessoryType spear = type(AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("standtest", "spear_mount"))
                .basics(PartBasics.of(40.0)).slot(AccessorySlot.BOTTOM).size(24.0, 18.0, 90.0).frontalArea(0.0004).build());
        return DroneBuild.FREESTYLE.withAccessory(dispenser).withAccessory(spear);
    }

    static double lowestCornerBelowCom(DroneBuild build) {
        AirframeSpec p = AirframeSpec.of(build);
        double low = Double.POSITIVE_INFINITY;
        for (AccessoryMount mount : build.accessories().mounts()) {
            AccessoryType type = build.accessories().at(mount).orElseThrow();
            for (double[] c : AccessoryAnchor.of(build.frame(), mount).corners(type)) {
                low = Math.min(low, c[1] / 1000.0 - p.comHeight);
            }
        }
        return low;
    }

    @Test
    void looseDronesAreHeldAsFarBelowTheHitboxAsTheAccessoryReaches() {
        DroneBuild build = freestyle();
        AirframeSpec p = AirframeSpec.of(build);
        assertEquals(8, p.accessoryPointCount());
        assertEquals(p.hitboxFootOffset - lowestCornerBelowCom(build), p.accessoryDepth(new Quaternionf()), EPS, "aufrecht");
        assertEquals(0.0, p.accessoryDepth(new Quaternionf().rotateZ((float) Math.PI)), 0.0, "kopfüber");
        assertEquals(0.0, p.accessoryDepth(new Quaternionf().rotateY(1.0F).rotateZ((float) Math.toRadians(150.0))), 0.0, "fast kopfüber");
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild lit = preset.withoutAccessories().withAccessory(AccessoryType.LED_STRIP);
            assertEquals(0, AirframeSpec.of(lit).accessoryPointCount(), preset.frame().key());
            assertEquals(0.0, AirframeSpec.of(lit).accessoryDepth(new Quaternionf()), 0.0, preset.frame().key());
            assertEquals(0, AirframeSpec.of(preset).accessoryPointCount(), preset.frame().key());
        }
    }

    @Test
    void drawnModelRestsWhereThePhysicsStands() {
        DroneBuild build = freestyle();
        float lowest = DronePhysique.of(build).lowestPoint(new Quaternionf());
        assertEquals(lowestCornerBelowCom(build), lowest, EPS, "tiefster Punkt der Flugmodell-Hülle für das Modell");
    }
}
