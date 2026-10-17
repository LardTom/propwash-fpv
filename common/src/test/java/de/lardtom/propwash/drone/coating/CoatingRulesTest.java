package de.lardtom.propwash.drone.coating;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoatingRulesTest {
    @Test
    void oneCanCoatsTenDrones() {
        assertEquals(10, CoatingRules.usesLeft(0));
        assertEquals(9, CoatingRules.usesLeft(10));
        assertEquals(1, CoatingRules.usesLeft(90));
        assertEquals(0, CoatingRules.usesLeft(100));
        int damage = 0;
        int coats = 0;
        while (!CoatingRules.usedUp(damage)) {
            damage = CoatingRules.damageAfterUse(damage);
            coats++;
        }
        assertEquals(10, coats);
    }

    @Test
    void propsAndBatteryKeepTheCoating() {
        DroneBuild build = DroneBuild.FREESTYLE;
        int coating = CoatingRules.signature(build);
        assertTrue(CoatingRules.matches(coating, build.withProp(PropType.P51_3B)));
        assertTrue(CoatingRules.matches(coating, build.withBattery(BatteryType.B6S_1300)));
        assertTrue(CoatingRules.matches(coating, build.withProp(PropType.P3_3B).withBattery(BatteryType.B4S_650)));
    }

    @Test
    void newElectronicsNeedANewCoat() {
        DroneBuild build = DroneBuild.FREESTYLE;
        int coating = CoatingRules.signature(build);
        assertFalse(CoatingRules.matches(coating, DroneBuild.RACER));
        assertFalse(CoatingRules.matches(coating, build.withAccessory(AccessoryType.LED_STRIP)));
        assertFalse(CoatingRules.matches(coating, new DroneBuild(build.frame(), build.stack(), build.motor(), build.prop(),
                VideoType.ANALOG_RACE, build.battery())));
        assertFalse(CoatingRules.matches(null, build));
        assertNotEquals(CoatingRules.signature(DroneBuild.WHOOP), CoatingRules.signature(DroneBuild.CINEWHOOP));
    }

    @Test
    void accessoryOrderDoesNotMatter() {
        DroneBuild a = DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP).withAccessory(AccessoryType.ACTION_CAM_LIGHT);
        DroneBuild b = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT).withAccessory(AccessoryType.LED_STRIP);
        assertEquals(CoatingRules.signature(a), CoatingRules.signature(b));
    }
}
