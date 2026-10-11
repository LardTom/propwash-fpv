package de.lardtom.propwash.drone;

import de.lardtom.propwash.drone.DroneInteraction.Action;
import de.lardtom.propwash.drone.DroneInteraction.HeldKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DroneInteractionTest {
    private static Action resolve(HeldKind held, boolean sneaking, boolean other, boolean self, boolean locked, boolean fits) {
        return DroneInteraction.resolve(held, sneaking, other, self, locked, fits);
    }

    @Test
    void remoteBindsUnlessAnotherPilotFlies() {
        assertEquals(Action.BIND_REMOTE, resolve(HeldKind.REMOTE, false, false, false, false, false));
        assertEquals(Action.OCCUPIED, resolve(HeldKind.REMOTE, false, true, false, true, false));
        assertEquals(Action.BIND_REMOTE, resolve(HeldKind.REMOTE, false, false, true, true, false));
    }

    @Test
    void gogglesBindInAnyState() {
        assertEquals(Action.CHECK_GOGGLES, resolve(HeldKind.GOGGLES, false, false, false, false, false));
        assertEquals(Action.CHECK_GOGGLES, resolve(HeldKind.GOGGLES, false, true, false, true, false));
    }

    @Test
    void batteryWithSneakSwapsOrRejects() {
        assertEquals(Action.QUICK_SWAP, resolve(HeldKind.BATTERY, true, false, false, false, true));
        assertEquals(Action.REJECT_BATTERY, resolve(HeldKind.BATTERY, true, false, false, false, false));
    }

    @Test
    void batteryWithoutSneakGivesHint() {
        assertEquals(Action.HINT_BATTERY, resolve(HeldKind.BATTERY, false, false, false, false, true));
        assertEquals(Action.HINT_BATTERY, resolve(HeldKind.BATTERY, false, true, false, true, false));
    }

    @Test
    void sneakWithOtherItemsOpensMenu() {
        for (HeldKind held : new HeldKind[]{HeldKind.EMPTY, HeldKind.OTHER, HeldKind.REMOTE, HeldKind.GOGGLES, HeldKind.PROP}) {
            assertEquals(Action.OPEN_SERVICE_MENU, resolve(held, true, false, false, false, false), held.name());
        }
    }

    @Test
    void lockBeatsEverySneakAction() {
        for (HeldKind held : HeldKind.values()) {
            assertEquals(Action.LOCKED, resolve(held, true, false, false, true, true), held.name());
            assertEquals(Action.LOCKED, resolve(held, true, true, false, true, false), held.name());
        }
    }

    @Test
    void emptyHandPicksUpOrReportsOccupied() {
        assertEquals(Action.PICK_UP, resolve(HeldKind.EMPTY, false, false, false, false, false));
        assertEquals(Action.OCCUPIED, resolve(HeldKind.EMPTY, false, true, false, true, false));
        assertEquals(Action.PASS, resolve(HeldKind.EMPTY, false, false, true, true, false));
    }

    @Test
    void otherItemsPass() {
        assertEquals(Action.PASS, resolve(HeldKind.OTHER, false, false, false, false, false));
        assertEquals(Action.PASS, resolve(HeldKind.OTHER, false, true, false, true, false));
    }

    @Test
    void propsWithSneakAlwaysOpenTheServiceMenu() {
        assertEquals(Action.OPEN_SERVICE_MENU, resolve(HeldKind.PROP, true, false, false, false, true));
        assertEquals(Action.OPEN_SERVICE_MENU, resolve(HeldKind.PROP, true, false, false, false, false));
        assertEquals(Action.LOCKED, resolve(HeldKind.PROP, true, false, true, true, true));
        assertEquals(Action.HINT_PROPS, resolve(HeldKind.PROP, false, false, false, false, true));
    }
}
