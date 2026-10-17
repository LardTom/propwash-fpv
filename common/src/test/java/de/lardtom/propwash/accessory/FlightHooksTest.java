package de.lardtom.propwash.accessory;

import de.lardtom.propwash.api.flight.DroneProfile;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightHooksTest {
    @Test
    void profileNamesThePartsAndAccessories() {
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT);
        DroneProfile profile = FlightHooks.profile(build);
        assertEquals(build.frame().id().toString(), profile.frame());
        assertEquals(Map.of("top", "propwash:action_cam_light"), profile.accessories());
        assertTrue(profile.has("propwash:action_cam_light"));
        assertTrue(profile.has(build.motor().id().toString()));
        assertTrue(profile.massKg() > 0.4);
    }

    @Test
    void registrationRulesAndFirstMatchingAssistWins() {
        FlightHooks hooks = FlightHooks.INSTANCE;
        assertThrows(IllegalArgumentException.class, () -> hooks.registerAssist("ohne-namensraum", profile -> null));
        assertThrows(IllegalArgumentException.class, () -> hooks.registerAssist("propwash:fremd", profile -> null));
        FlightAssist cinema = (state, out) -> out.label("CINE");
        hooks.registerAssist("hooktest:none", profile -> null);
        hooks.registerAssist("hooktest:broken", profile -> {
            throw new IllegalStateException("kaputt");
        });
        hooks.registerAssist("hooktest:cine", profile -> profile.hasAccessory("propwash:action_cam_light") ? cinema : null);
        assertThrows(IllegalArgumentException.class, () -> hooks.registerAssist("hooktest:cine", profile -> null));
        assertTrue(hooks.assistIds().contains("hooktest:cine"));
        assertSame(cinema, hooks.assistFor(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT)));
        assertNull(hooks.assistFor(DroneBuild.FREESTYLE));
        hooks.registerForces("hooktest:lift", profile -> (state, sink) -> sink.addForce(0.0, 1.0, 0.0));
        assertEquals(1, hooks.forcesFor(DroneBuild.FREESTYLE).size());
    }
}
