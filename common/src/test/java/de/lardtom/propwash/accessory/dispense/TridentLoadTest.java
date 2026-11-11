package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.drone.Knockback;
import de.lardtom.propwash.interact.GripperSpec;
import de.lardtom.propwash.interact.ItemWeights;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TridentLoadTest {
    private static final Identifier MOUNT = Identifier.fromNamespaceAndPath("tridenttest", "dispenser");

    private static AccessoryType dispenser() {
        return (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(MOUNT).basics(PartBasics.of(42.0)).slot(AccessorySlot.FRONT)
                .size(62.0, 58.0, 64.0).frontalArea(0.0036).build());
    }

    @Test
    void aTridentWeighsOneAndAHalfKilograms() {
        assertEquals(1500.0, ItemWeights.TRIDENT_GRAMS, 0.0);
        assertEquals(1500.0, ItemWeights.gramsEach(1, false, true, 0.0), 0.0);
        assertEquals(ItemWeights.SINGLE_GRAMS, ItemWeights.gramsEach(1, false, false, 0.0), 0.0, "andere einzelne Items bleiben bei 150 g");
        assertEquals(1500.0, ItemWeights.gramsEach(1, false, true, 6.0), 0.0);
        assertEquals(ItemWeights.TRIDENT_GRAMS, DispenseMath.Kind.TRIDENT.massKg * 1000.0, 1.0E-9, "Rückstoß und Beladung rechnen mit derselben Masse");
    }

    @Test
    void tridentsInTheDispenserWeighOnTheFlightModel() {
        AccessoryType type = dispenser();
        double grams = 2.0 * ItemWeights.gramsEach(1, false, true, 0.0);
        assertEquals(3000.0, AccessoryPayload.flightGrams(type, grams), 0.0);
        AccessoryType laden = type.laden(AccessoryPayload.massStep(type, grams));
        assertEquals(42.0 + 3000.0, laden.massGrams(), 1.0E-9);
        DroneBuild empty = DroneBuild.FREESTYLE.withAccessory(type);
        DroneBuild loaded = DroneBuild.FREESTYLE.withAccessory(laden);
        DroneBuild before = DroneBuild.FREESTYLE.withAccessory(type.laden(AccessoryPayload.massStep(type, 2.0 * ItemWeights.SINGLE_GRAMS)));
        assertEquals(AirframeSpec.of(empty).massKg + 3.0, AirframeSpec.of(loaded).massKg, 1.0E-9);
        assertEquals(BuildAnalysis.of(empty).massGrams() + 3000.0, BuildAnalysis.of(loaded).massGrams(), 1.0E-6);
        double ratio = BuildAnalysis.of(loaded).thrustToWeight();
        assertTrue(ratio < 0.5 * BuildAnalysis.of(before).thrustToWeight(), ratio + " gegen " + BuildAnalysis.of(before).thrustToWeight());
        assertEquals(AirframeSpec.of(empty).massKg + 3.0, Knockback.massKg(AirframeSpec.of(empty).massKg, 3000.0, 0.0), 1.0E-9);
    }

    @Test
    void theRecoilOfATridentIsItsMomentumAtTheMuzzle() {
        double recoil = DispenseMath.recoil(DispenseMath.Kind.TRIDENT, 1.1F, 1.0);
        assertEquals(1.5 * 22.0, recoil, 1.0E-4);
        double drone = AirframeSpec.of(DroneBuild.FREESTYLE.withAccessory(dispenser().laden(AccessoryPayload.massStep(dispenser(), 1500.0)))).massKg;
        assertTrue(recoil / drone > 10.0, "ein 5-Zöller mit einem Dreizack fliegt beim Wurf deutlich zurück: " + recoil / drone + " m/s");
    }

    @Test
    void aGripperCannotLiftATrident() {
        assertTrue(ItemWeights.TRIDENT_GRAMS > GripperSpec.MAX_PAYLOAD_GRAMS);
        assertTrue(ItemWeights.TRIDENT_GRAMS > GripperSpec.capacityGrams(500.0, 4.0 * 1500.0, 1.0));
    }
}
