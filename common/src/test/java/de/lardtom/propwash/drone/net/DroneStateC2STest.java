package de.lardtom.propwash.drone.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneStateC2STest {
    private static DroneStateC2S state(boolean armed, boolean physicsModified, boolean ledsOff) {
        return state(armed, physicsModified, ledsOff, false);
    }

    private static DroneStateC2S state(boolean armed, boolean physicsModified, boolean ledsOff, boolean beeper) {
        return new DroneStateC2S(4711, 3, 12.5, 70.25, -340.75, 0.1F, 0.2F, 0.3F, 0.9F, 1.5F, -0.25F, 3.0F, armed, 0.62F,
                StateValidation.packMotors(0.4F, 0.5F, 0.6F, 0.7F), physicsModified, ledsOff, beeper);
    }

    private static DroneStateC2S roundTrip(DroneStateC2S payload) {
        ByteBuf buffer = Unpooled.buffer();
        DroneStateC2S.CODEC.encode(buffer, payload);
        DroneStateC2S decoded = DroneStateC2S.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    @Test
    void ledSwitchTravelsWithTheFlightState() {
        for (boolean armed : new boolean[]{false, true}) {
            for (boolean modified : new boolean[]{false, true}) {
                for (boolean ledsOff : new boolean[]{false, true}) {
                    DroneStateC2S payload = state(armed, modified, ledsOff);
                    DroneStateC2S decoded = roundTrip(payload);
                    assertEquals(payload, decoded);
                    assertEquals(ledsOff, decoded.ledsOff());
                    assertEquals(armed, decoded.armed());
                    assertEquals(modified, decoded.physicsModified());
                }
            }
        }
        assertTrue(roundTrip(state(true, false, true)).ledsOff());
        assertFalse(roundTrip(state(true, true, false)).ledsOff());
    }

    @Test
    void beeperSwitchTravelsWithTheFlightState() {
        assertTrue(roundTrip(state(false, false, false, true)).beeper());
        assertFalse(roundTrip(state(false, false, true, false)).beeper());
        assertTrue(roundTrip(state(true, true, true, true)).ledsOff());
    }
}
