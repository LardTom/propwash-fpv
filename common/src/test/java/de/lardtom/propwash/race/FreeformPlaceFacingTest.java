package de.lardtom.propwash.race;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeformPlaceFacingTest {
    private static final FreeformFill.Probe SLAB = (x, y, z) -> y == 64 && x >= 10 && x <= 12 && z >= 20 && z <= 21 ? FreeformFill.Cell.OPEN
            : y == 64 ? FreeformFill.Cell.SOLID : FreeformFill.Cell.OPEN;

    private record Look(String name, Vec3 look, int clickedX, int clickedZ, int faceX, int faceZ, float yaw, GateSignalTarget output) {
    }

    private static final Look[] LOOKS = {
            new Look("Süden", new Vec3(0.0, -0.8, 0.6), 11, 19, 0, 1, 0.0F, new GateSignalTarget(11, 64, 19, GateSignalTarget.SOUTH)),
            new Look("Westen", new Vec3(-0.6, -0.8, 0.0), 13, 20, -1, 0, 90.0F, new GateSignalTarget(13, 64, 20, GateSignalTarget.WEST)),
            new Look("Norden", new Vec3(0.0, -0.8, -0.6), 11, 22, 0, -1, 180.0F, new GateSignalTarget(11, 64, 22, GateSignalTarget.NORTH)),
            new Look("Osten", new Vec3(0.6, -0.8, 0.0), 9, 21, 1, 0, 270.0F, new GateSignalTarget(9, 64, 21, GateSignalTarget.EAST))};

    private static float facing(Vec3 look) {
        return FreeformFill.facing(look.x, look.z);
    }

    @Test
    void lookSnapsToQuarterTurns() {
        for (Look look : LOOKS) {
            assertEquals(look.yaw(), facing(look.look()), "Blick nach " + look.name());
        }
        assertEquals(90.0F, facing(new Vec3(-0.7, -0.5, 0.4)), "schräg nach Südwest, mehr West");
        assertEquals(180.0F, facing(new Vec3(0.3, -0.9, -0.5)), "schräg nach Nordost, mehr Nord");
        assertEquals(0.0F, facing(new Vec3(0.0, -1.0, 0.0)), "senkrecht nach unten: 0° wie bisher");
        assertEquals(0.0F, facing(new Vec3(0.0, 1.0, 0.0)), "senkrecht nach oben: 0° wie bisher");
    }

    @Test
    void lyingGatePlacedFromTheLookHasItsOutputOnThePlayersSide() {
        for (Look look : LOOKS) {
            String label = "Blick nach " + look.name();
            Vec3 v = look.look();
            FreeformFill.Aim aim = FreeformFill.aim(look.clickedX(), 64, look.clickedZ(), look.faceX(), 0, look.faceZ(), false, v.x, v.y, v.z, false);
            assertEquals(FreeformRegion.AXIS_Y, aim.axis(), label + ": Innenwand beim Blick nach unten ergibt ein liegendes Gate");
            FreeformFill.Result result = FreeformFill.fill(SLAB, aim.axis(), aim.seedX(), aim.seedY(), aim.seedZ(), 20);
            assertTrue(result.ok(), label + ": " + result.status());
            FreeformGateItem.Plan plan = new FreeformGateItem.Plan(aim, result, null, false, facing(v));
            GateGeometry geometry = plan.geometry();
            assertNotNull(geometry, label);
            assertEquals(look.yaw(), geometry.yawDegrees(), label + ": Ausrichtung aus dem Blick");
            GateSignalTarget output = GateSignalTarget.of(geometry);
            assertEquals(look.output(), output, label + ": Ausgang am Rand auf der Seite des Spielers");
            double towardPlayerX = -v.x;
            double towardPlayerZ = -v.z;
            double offX = output.x() + 0.5 - geometry.centerX();
            double offZ = output.z() + 0.5 - geometry.centerZ();
            assertTrue(offX * towardPlayerX + offZ * towardPlayerZ > 0.0, label + ": Ausgang liegt zum Spieler hin (" + offX + ", " + offZ + ")");
            assertTrue(FreeformBlocks.covers(geometry, output.emitterX(), output.emitterY(), output.emitterZ()), label + ": Ausgang zeigt in die Öffnung");
        }
    }
}
