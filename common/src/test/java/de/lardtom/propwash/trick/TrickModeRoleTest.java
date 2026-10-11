package de.lardtom.propwash.trick;

import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickModeRoleTest {
    private static FrameType frame(FrameRole role) {
        return (FrameType) PartIndex.detached(FrameDefinition.builder(Identifier.fromNamespaceAndPath("propwash_tricktest", role.key()))
                .mass(100.0).role(role).wheelbase(200).maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30)
                .accessoryMount(AccessorySlot.TOP).aero(0.01, 0.9).batteryFit(new BatteryFitRule(4, 6, 250.0, 6, 0)).build());
    }

    @Test
    void builtinFramesKeepTheirAutomaticTricks() {
        assertTrue(TrickMode.freestyleFrame(FrameType.FREESTYLE_5));
        assertTrue(TrickMode.freestyleFrame(FrameType.WHOOP_65));
        assertTrue(TrickMode.freestyleFrame(FrameType.CINEWHOOP_3));
        assertFalse(TrickMode.freestyleFrame(FrameType.RACE_5));
        assertFalse(TrickMode.freestyleFrame(FrameType.LONG_RANGE_7));
        assertFalse(TrickMode.freestyleFrame(null));
        assertTrue(TrickMode.AUTO.enabledFor(DroneBuild.FREESTYLE));
        assertFalse(TrickMode.AUTO.enabledFor(DroneBuild.RACER));
    }

    @Test
    void addonFramesFollowTheirRole() {
        for (FrameRole role : FrameRole.values()) {
            assertEquals(role.tricksByDefault(), TrickMode.freestyleFrame(frame(role)), role.key());
        }
        assertFalse(TrickMode.freestyleFrame(frame(FrameRole.X_CLASS)));
        assertTrue(TrickMode.freestyleFrame(frame(FrameRole.TOOTHPICK)));
    }
}
