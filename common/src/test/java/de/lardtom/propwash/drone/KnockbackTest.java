package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockbackTest {
    @Test
    void deltaVelocityBecomesAnImpulseWithTheLoad() {
        double airframe = AirframeSpec.of(DroneBuild.FREESTYLE).massKg;
        double loaded = Knockback.massKg(airframe, 350.0, 150.0);
        assertEquals(airframe + 0.5, loaded, 1.0E-9);
        Vec3 dv = Knockback.metresPerSecond(new Vec3(0.3, 0.1, 0.0));
        assertEquals(6.0, dv.x, 1.0E-9);
        assertEquals(2.0, dv.y, 1.0E-9);
        Vec3 j = Knockback.impulse(dv, loaded, 1.0, false);
        assertEquals(6.0 * loaded, j.x, 1.0E-9);
        assertEquals(2.0 * loaded, j.y, 1.0E-9);
        Vec3 heavier = Knockback.impulse(dv, Knockback.massKg(airframe, 0.0, 0.0), 1.0, false);
        assertTrue(j.length() > heavier.length(), "Beladung erhöht den Stoß");
        Vec3 half = Knockback.impulse(dv, loaded, 0.5, false);
        assertEquals(j.x * 0.5, half.x, 1.0E-9);
        double gripper = AirframeSpec.of(DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER)).massKg;
        assertTrue(gripper > airframe, "Zubehör zählt zur Masse des Baus");
    }

    @Test
    void impulseIsCappedAtFiftyNewtonSeconds() {
        Vec3 j = Knockback.impulse(new Vec3(400.0, 0.0, 300.0), 2.0, 2.0, false);
        assertEquals(Knockback.MAX_IMPULSE, j.length(), 1.0E-9);
        assertEquals(0.8, j.normalize().x, 1.0E-9);
        assertEquals(50.0, Knockback.MAX_IMPULSE, 0.0);
    }

    @Test
    void defaultScaleAndCancelForwardNothing() {
        Vec3 dv = new Vec3(10.0, 0.0, 0.0);
        for (DroneKnockback.Cause cause : DroneKnockback.Cause.values()) {
            assertEquals(Vec3.ZERO, Knockback.impulse(dv, 0.7, 0.0, false), cause.name());
            assertEquals(Vec3.ZERO, Knockback.impulse(dv, 0.7, 1.0, true), cause.name());
            assertTrue(Knockback.impulse(dv, 0.7, 1.0, false).length() > 0.0, cause.name());
        }
        assertEquals(Vec3.ZERO, Knockback.impulse(new Vec3(Double.NaN, 0.0, 0.0), 0.7, 1.0, false));
        assertEquals(Vec3.ZERO, Knockback.impulse(dv, 0.0, 1.0, false));
        assertEquals(Vec3.ZERO, Knockback.impulse(Vec3.ZERO, 0.7, 1.0, false));
    }

    @Test
    void eventStartsWithoutScaleAndClampsIt() {
        assertEquals(DroneKnockback.Cause.EXPLOSION, Knockback.cause(null));
        assertEquals(3, DroneKnockback.Cause.values().length);
        assertEquals(2.0, DroneKnockback.MAX_SCALE, 0.0);
    }
}
