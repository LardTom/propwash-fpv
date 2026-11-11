package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryRecessFlightTest {
    private static final double MASS_KG = 0.150;
    private static final double RECESS = 58.0;
    private static final double DROP = 1.5;

    private static AccessoryType dispenser(String name, double recess, double drop) {
        return (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(AddonParts.id(name)).basics(PartBasics.of(MASS_KG * 1000.0))
                .slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.0036).recess(recess).drop(drop).build());
    }

    @Test
    void framesWithTheirOwnGeometryMoveTheCenterOfMassWithTheBox() {
        DroneBuild preset = DroneBuild.FREESTYLE;
        FrameType frame = AddonParts.trueXExplicit(preset.frame());
        DroneBuild base = new DroneBuild(frame, preset.stack(), preset.motor(), preset.prop(), preset.video(), preset.battery());
        AirframeSpec bare = AirframeSpec.of(base);
        AirframeSpec flat = AirframeSpec.of(base.withAccessory(dispenser("recess_flat", 0.0, 0.0)));
        AirframeSpec sunk = AirframeSpec.of(base.withAccessory(dispenser("recess_sunk", RECESS, DROP)));
        assertEquals(MASS_KG * RECESS / 1000.0 / sunk.massKg, flat.comZ - sunk.comZ, 1.0E-9, "Schwerpunkt längs");
        assertEquals(MASS_KG * DROP / 1000.0 / sunk.massKg, flat.comHeight - sunk.comHeight, 1.0E-9, "Schwerpunkt Höhe");
        assertTrue(Math.abs(sunk.comZ - bare.comZ) < Math.abs(flat.comZ - bare.comZ), "eingerückt liegt der Schwerpunkt näher an dem ohne Zubehör");
        assertTrue(sunk.inertiaXx < flat.inertiaXx, "weniger Trägheit um die Nickachse: " + sunk.inertiaXx + " gegen " + flat.inertiaXx);
    }
}
