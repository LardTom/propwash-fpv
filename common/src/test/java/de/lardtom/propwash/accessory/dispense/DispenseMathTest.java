package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.api.accessory.DispenseOptions;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispenseMathTest {
    @Test
    void nearestDirectionFollowsForward() {
        assertEquals(Direction.NORTH, DispenseMath.nearest(0.0, 0.0, -1.0));
        assertEquals(Direction.SOUTH, DispenseMath.nearest(0.1, -0.2, 0.9));
        assertEquals(Direction.EAST, DispenseMath.nearest(0.8, 0.5, -0.3));
        assertEquals(Direction.WEST, DispenseMath.nearest(-0.7, 0.1, 0.6));
        assertEquals(Direction.DOWN, DispenseMath.nearest(0.3, -0.9, -0.3));
        assertEquals(Direction.UP, DispenseMath.nearest(0.0, 0.71, -0.70));
        assertEquals(Direction.NORTH, DispenseMath.nearest(0.0, -0.70, -0.71));
        double pitched = Math.toRadians(50.0);
        assertEquals(Direction.DOWN, DispenseMath.nearest(new Vec3(0.0, -Math.sin(pitched), -Math.cos(pitched))));
    }

    @Test
    void recoilComesFromTheTable() {
        assertEquals(0.02 * 22.0, DispenseMath.recoil(DispenseMath.Kind.ARROW, 1.1, 1.0), 1.0E-9);
        assertEquals(0.35 * 27.5, DispenseMath.recoil(DispenseMath.Kind.POTION, 1.375, 1.0), 1.0E-9);
        assertEquals(0.05 * 20.0 + 0.8, DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, 1.0, 1.0), 1.0E-9);
        assertEquals(0.8, DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, 1.0, 0.0), 1.0E-9);
        assertEquals(0.02 * 44.0, DispenseMath.recoil(DispenseMath.Kind.ARROW, 1.1, 2.0), 1.0E-9);
        assertEquals(0.0, DispenseMath.recoil(DispenseMath.Kind.SNOWBALL, Double.NaN, 1.0), 0.0);
        for (DispenseMath.Kind kind : DispenseMath.Kind.values()) {
            assertTrue(kind.massKg > 0.0 && kind.massKg <= 1.5, kind.name());
            assertTrue(DispenseMath.recoil(kind, 1.1, 1.0) > 0.0, kind.name());
        }
        assertTrue(DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, 1.0, 1.0) > 4.0 * DispenseMath.recoil(DispenseMath.Kind.ARROW, 1.1, 1.0));
    }

    @Test
    void tridentWeighsOneAndAHalfKilogramsAndKicksBackWithItsMomentum() {
        assertEquals(1.5, DispenseMath.Kind.TRIDENT.massKg, 0.0);
        assertEquals(1.5 * 22.0, DispenseMath.recoil(DispenseMath.Kind.TRIDENT, 1.1, 1.0), 1.0E-9);
        assertEquals(1.5 * 11.0, DispenseMath.recoil(DispenseMath.Kind.TRIDENT, 1.1, 0.5), 1.0E-9);
        assertTrue(DispenseMath.recoil(DispenseMath.Kind.TRIDENT, 1.1, 1.0) < 50.0, "unter der Grenze von DronePhysics#impulse");
    }

    @Test
    void windChargeBlastOnTheOwnDroneIsAThirdOfTheOldOne() {
        assertEquals(0.8, DispenseMath.WIND_CHARGE_BLAST_NS, 0.0);
        assertEquals(1.8, DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, 1.0, 1.0), 1.0E-9);
        assertTrue(DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, 1.0, 1.0) < 0.6 * (0.05 * 20.0 + 2.5));
    }

    @Test
    void windChargesFromADroneKnockBackHalfAsHardAsVanilla() {
        assertEquals(0.61F, DispenseMath.windKnockback(1.22F, true), 1.0E-6F);
        assertEquals(1.22F, DispenseMath.windKnockback(1.22F, false), 0.0F);
        assertEquals(0.5F, DispenseMath.windKnockback(1.0F, true), 0.0F);
        assertEquals(1.0F, DispenseMath.windKnockback(1.0F, false), 0.0F);
        assertEquals(0.0F, DispenseMath.windKnockback(0.0F, true), 0.0F, "fliegende Spieler im Kreativmodus bleiben ungestoßen");
        assertTrue(Float.isNaN(DispenseMath.windKnockback(Float.NaN, true)));
        assertEquals(0.5F, DispenseMath.DRONE_WIND_KNOCKBACK, 0.0F);
    }

    @Test
    void bucketsAreRefusedUnlessFluidsAreAllowed() {
        assertTrue(DispenseMath.refusesFluid(true, DispenseOptions.defaults().allowFluids()));
        assertFalse(DispenseMath.refusesFluid(true, true));
        assertFalse(DispenseMath.refusesFluid(false, false));
        assertFalse(DispenseOptions.defaults().allowFluids());
        assertTrue(DispenseOptions.defaults().applyRecoil());
        assertTrue(DispenseOptions.defaults().consume());
    }

    @Test
    void consumeFalseKeepsTheStack() {
        assertEquals(7, DispenseMath.remaining(8, 1, true));
        assertEquals(0, DispenseMath.remaining(1, 1, true));
        assertEquals(0, DispenseMath.remaining(1, 3, true));
        assertEquals(8, DispenseMath.remaining(8, 1, false));
        assertEquals(1, DispenseMath.remaining(1, 1, false));
        DispenseOptions free = DispenseOptions.defaults().consume(false);
        assertFalse(free.consume());
        assertEquals(DispenseOptions.defaults().speedScale(), free.speedScale(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> DispenseOptions.defaults().speedScale(-1.0));
    }

    @Test
    void rightsNeedInteractionBuildingAndNoAdventureRestriction() {
        assertFalse(DispenseMath.denied(true, false, true));
        assertTrue(DispenseMath.denied(false, false, true), "Spawnschutz oder Weltgrenze");
        assertTrue(DispenseMath.denied(true, true, false), "Abenteuermodus");
        assertTrue(DispenseMath.denied(true, true, true));
        assertTrue(DispenseMath.denied(true, false, false));
    }

    @Test
    void tntAndItemsInheritTheDroneSpeed() {
        Vec3 drone = new Vec3(12.0, -2.0, -6.0);
        Vec3 tnt = DispenseMath.inherit(drone, new Vec3(-0.01, 0.2, 0.015));
        assertEquals(0.59, tnt.x, 1.0E-9);
        assertEquals(0.1, tnt.y, 1.0E-9);
        assertEquals(-0.285, tnt.z, 1.0E-9);
        Vec3 eject = DispenseMath.itemEject(new Vec3(0.0, 0.0, -2.0), 0.25);
        assertEquals(0.0, eject.x, 1.0E-9);
        assertEquals(DispenseMath.ITEM_EJECT_UP, eject.y, 1.0E-9);
        assertEquals(-0.25, eject.z, 1.0E-9);
        Vec3 down = DispenseMath.itemEject(new Vec3(0.0, -1.0, 0.0), 0.3);
        assertTrue(down.y < 0.0, "nach unten ausgeworfen fällt das Item");
        Vec3 inherited = DispenseMath.inherit(new Vec3(20.0, 0.0, 0.0), eject);
        assertEquals(1.0, inherited.x, 1.0E-9);
        assertEquals(eject.z, inherited.z, 1.0E-9);
        assertEquals(22.0, DispenseMath.muzzleSpeed(1.1, 1.0), 1.0E-9);
        assertEquals(0.0, DispenseMath.muzzleSpeed(1.1, -1.0), 0.0);
    }
}
