package de.lardtom.propwash.interact;

import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoApiTest {
    private static AccessoryRegistry older() {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (AccessoryRegistry) Proxy.newProxyInstance(AccessoryRegistry.class.getClassLoader(), new Class<?>[]{AccessoryRegistry.class}, handler);
    }

    @Test
    void cargoMethodsAreAdditiveDefaultsWithNeutralResults() throws NoSuchMethodException {
        Method cargo = AccessoryRegistry.class.getMethod("cargo", Entity.class);
        Method grab = AccessoryRegistry.class.getMethod("grabCargo", Entity.class, ServerPlayer.class);
        Method release = AccessoryRegistry.class.getMethod("releaseCargo", Entity.class, ServerPlayer.class);
        assertTrue(cargo.isDefault() && grab.isDefault() && release.isDefault());
        assertEquals(List.class, cargo.getReturnType());
        assertEquals(boolean.class, grab.getReturnType());
        assertEquals(int.class, release.getReturnType());
        AccessoryRegistry registry = older();
        assertEquals(List.of(), registry.cargo(null));
        assertFalse(registry.grabCargo(null, null));
        assertEquals(0, registry.releaseCargo(null, null));
    }

    @Test
    void propwashImplementsTheCargoMethods() {
        for (String name : List.of("cargo", "grabCargo", "releaseCargo")) {
            boolean declared = false;
            for (Method method : AccessoryBehaviors.class.getDeclaredMethods()) {
                declared |= method.getName().equals(name) && !method.isSynthetic();
            }
            assertTrue(declared, "AccessoryBehaviors reicht " + name + " nicht an den Greifer weiter");
        }
    }

    @Test
    void pilotedDronesPickUpAsBefore() {
        assertTrue(GripperSpec.picksUp(true, true, true, false));
        assertTrue(GripperSpec.picksUp(true, true, true, true));
        assertFalse(GripperSpec.picksUp(true, false, true, false));
        assertFalse(GripperSpec.picksUp(false, true, true, true));
    }

    @Test
    void autopilotPicksUpOnlyAfterAGrab() {
        assertTrue(GripperSpec.picksUp(true, true, false, true), "Autopilot nach grabCargo nimmt nichts auf");
        assertFalse(GripperSpec.picksUp(true, true, false, false), "Autopilot ohne grabCargo nimmt auf (Heimflug)");
        assertFalse(GripperSpec.picksUp(true, false, false, true));
        assertFalse(GripperSpec.picksUp(false, true, false, true));
    }

    @Test
    void aGrabStaysOpenFiveSecondsAndClosesOnRequest() {
        UUID actor = UUID.randomUUID();
        DroneCargo.Hold hold = new DroneCargo.Hold();
        assertFalse(hold.grabbing(0L));
        assertNull(hold.actor());
        hold.openGrab(actor, 1000L);
        assertEquals(actor, hold.actor());
        assertTrue(hold.grabbing(1000L));
        assertTrue(hold.grabbing(1000L + GripperSpec.GRAB_TICKS - 1));
        assertFalse(hold.grabbing(1000L + GripperSpec.GRAB_TICKS));
        hold.openGrab(actor, 2000L);
        hold.closeGrab();
        assertFalse(hold.grabbing(2001L));
        assertEquals(actor, hold.actor());
        assertEquals(100, GripperSpec.GRAB_TICKS);
    }

    @Test
    void itemsMeantForTheGrabbingPlayerMayBeTaken() {
        UUID pilot = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(GripperSpec.mayTake(null, null, null));
        assertTrue(GripperSpec.mayTake(actor, null, actor));
        assertTrue(GripperSpec.mayTake(pilot, pilot, actor));
        assertTrue(GripperSpec.mayTake(actor, pilot, actor));
        assertFalse(GripperSpec.mayTake(other, pilot, actor));
        assertFalse(GripperSpec.mayTake(other, null, null));
        assertFalse(GripperSpec.mayTake(actor, null, null));
    }
}
