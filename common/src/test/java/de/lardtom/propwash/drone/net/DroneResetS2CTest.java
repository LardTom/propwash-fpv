package de.lardtom.propwash.drone.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DroneResetS2CTest {
    private static DroneResetS2C roundTrip(DroneResetS2C payload) {
        ByteBuf buffer = Unpooled.buffer();
        DroneResetS2C.CODEC.encode(buffer, payload);
        DroneResetS2C decoded = DroneResetS2C.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    private static byte[] bytes(DroneResetS2C payload) {
        ByteBuf buffer = Unpooled.buffer();
        DroneResetS2C.CODEC.encode(buffer, payload);
        byte[] out = new byte[buffer.readableBytes()];
        buffer.readBytes(out);
        return out;
    }

    @Test
    void everyFlagCombinationTravels() {
        for (boolean home : new boolean[]{false, true}) {
            for (boolean motion : new boolean[]{false, true}) {
                for (boolean position : new boolean[]{false, true}) {
                    DroneResetS2C payload = new DroneResetS2C(4711, 9, 12.5, 70.25, -340.75, 0.1F, 0.2F, 0.3F, 0.9F, home, motion, position);
                    assertEquals(payload, roundTrip(payload));
                }
            }
        }
    }

    @Test
    void plainResetKeepsNothingAndItsWireFormat() {
        for (boolean home : new boolean[]{false, true}) {
            DroneResetS2C payload = new DroneResetS2C(4711, 9, 12.5, 70.25, -340.75, 0.1F, 0.2F, 0.3F, 0.9F, home);
            assertFalse(payload.keepMotion());
            assertFalse(payload.keepPosition());
            byte[] wire = bytes(payload);
            ByteBuf legacy = Unpooled.buffer();
            legacy.writeByte(0x80 | (4711 & 0x7F));
            legacy.writeByte(4711 >>> 7);
            legacy.writeByte(9);
            legacy.writeDouble(12.5);
            legacy.writeDouble(70.25);
            legacy.writeDouble(-340.75);
            legacy.writeFloat(0.1F);
            legacy.writeFloat(0.2F);
            legacy.writeFloat(0.3F);
            legacy.writeFloat(0.9F);
            legacy.writeBoolean(home);
            byte[] expected = new byte[legacy.readableBytes()];
            legacy.readBytes(expected);
            assertEquals(HexFormat.of().formatHex(expected), HexFormat.of().formatHex(wire));
        }
    }
}
