package de.lardtom.propwash.drone.operator;

import de.lardtom.propwash.drone.operator.net.OperatorInputC2S;
import de.lardtom.propwash.drone.operator.net.OperatorRoleS2C;
import de.lardtom.propwash.drone.operator.net.OperatorStateS2C;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorChannelTest {
    private static final UUID A = new UUID(1L, 2L);
    private static final UUID B = new UUID(3L, 4L);

    @Test
    void axesAreQuantizedToShortsWithHalfAStepError() {
        for (int i = -1000; i <= 1000; i++) {
            double value = i / 1000.0;
            double back = OperatorChannel.axis(OperatorChannel.quantize(value));
            assertEquals(value, back, 0.5 / OperatorChannel.SCALE + 1.0E-12, "Wert " + value);
        }
        assertEquals(Short.MAX_VALUE, OperatorChannel.quantize(1.0));
        assertEquals(-Short.MAX_VALUE, OperatorChannel.quantize(-1.0));
        assertEquals(Short.MAX_VALUE, OperatorChannel.quantize(3.5));
        assertEquals(-Short.MAX_VALUE, OperatorChannel.quantize(-7.0));
        assertEquals(0, OperatorChannel.quantize(Double.NaN));
        assertEquals(0, OperatorChannel.quantize(Double.POSITIVE_INFINITY));
        assertEquals(0.0, OperatorChannel.axis((short) 0));
        assertEquals(-1.0, OperatorChannel.axis(Short.MIN_VALUE));
        assertEquals(1.0, OperatorChannel.axis(Short.MAX_VALUE));
        assertEquals(0.5, OperatorChannel.axis(OperatorChannel.quantize(0.5)), 1.0E-4);
    }

    @Test
    void fourActionsFitTheBitfieldAndLeaveIsSeparate() {
        int bits = OperatorChannel.actions(true, false, true, false);
        assertEquals(0b0101, bits);
        assertTrue(OperatorChannel.action(bits, 1));
        assertFalse(OperatorChannel.action(bits, 2));
        assertTrue(OperatorChannel.action(bits, 3));
        assertFalse(OperatorChannel.action(bits, 0));
        assertFalse(OperatorChannel.action(bits, 5));
        assertEquals(0b1111, OperatorChannel.actions(true, true, true, true, true, true));
        assertEquals(0b0011, OperatorChannel.sanitize(0b0011 | OperatorChannel.LEAVE | 0x30));
        assertTrue(OperatorChannel.leaves(OperatorChannel.LEAVE | 1));
        assertFalse(OperatorChannel.leaves(0b1111));
    }

    @Test
    void payloadsSurviveTheWire() {
        short[] axes = {OperatorChannel.quantize(-0.25), OperatorChannel.quantize(0.75), Short.MIN_VALUE, Short.MAX_VALUE};
        ByteBuf buffer = Unpooled.buffer();
        OperatorInputC2S.CODEC.encode(buffer, new OperatorInputC2S(1234, axes, 0b1010));
        OperatorInputC2S input = OperatorInputC2S.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(1234, input.entityId());
        assertArrayEquals(axes, input.axes());
        assertEquals(0b1010, input.bits());
        assertEquals(1 + 4 * 2 + 1, encodedSize(new OperatorInputC2S(5, axes, 0)));
        OperatorInputC2S leave = roundTrip(OperatorInputC2S.leave(7));
        assertTrue(OperatorChannel.leaves(leave.bits()));
        assertEquals(0, new OperatorInputC2S(1, axes, 0xFF).bits() & ~(OperatorChannel.ACTION_MASK | OperatorChannel.LEAVE));
        assertArrayEquals(new short[]{3, 0, 0, 0}, new OperatorInputC2S(1, new short[]{3}, 0).axes());
        ByteBuf state = Unpooled.buffer();
        OperatorStateS2C.CODEC.encode(state, new OperatorStateS2C(99, axes, 0xFF));
        OperatorStateS2C decoded = OperatorStateS2C.CODEC.decode(state);
        assertArrayEquals(axes, decoded.axes());
        assertEquals(OperatorChannel.ACTION_MASK, decoded.actions());
        ByteBuf role = Unpooled.buffer();
        OperatorRoleS2C.CODEC.encode(role, new OperatorRoleS2C(5, A, OperatorRoleS2C.Role.PILOT, "Kameramann"));
        OperatorRoleS2C back = OperatorRoleS2C.CODEC.decode(role);
        assertEquals(OperatorRoleS2C.Role.PILOT, back.role());
        assertEquals("Kameramann", back.name());
        assertEquals(A, back.drone());
        assertEquals(OperatorRoleS2C.MAX_NAME, new OperatorRoleS2C(1, A, null, "x".repeat(200)).name().length());
        assertEquals(OperatorRoleS2C.Role.NONE, new OperatorRoleS2C(1, null, null, null).role());
    }

    @Test
    void rateAllowsTwentyHertzAndRefusesMoreThanTwentyFivePerSecond() {
        OperatorChannel.Rate steady = new OperatorChannel.Rate();
        for (int i = 0; i < 20 * 30; i++) {
            assertTrue(steady.accept(i * 50L), "20 Hz, Paket " + i);
        }
        assertEquals(0, steady.refused());
        OperatorChannel.Rate burst = new OperatorChannel.Rate();
        for (int i = 0; i < OperatorChannel.MAX_PER_SECOND; i++) {
            assertTrue(burst.accept(1000L + i));
        }
        assertFalse(burst.accept(1100L));
        assertFalse(burst.accept(1999L));
        assertEquals(2, burst.refused());
        assertTrue(burst.accept(2000L));
        assertFalse(burst.accept(2000L));
        OperatorChannel.Rate fast = new OperatorChannel.Rate();
        int passed = 0;
        for (int i = 0; i < 60 * 10; i++) {
            if (fast.accept(i * 1000L / 60L)) {
                passed++;
            }
        }
        assertTrue(passed <= OperatorChannel.MAX_PER_SECOND * 10 + 1, "60 Hz werden auf 25/s gekappt: " + passed);
        assertTrue(passed >= OperatorChannel.MAX_PER_SECOND * 10 - 1, "Kappung lässt 25/s durch: " + passed);
        OperatorChannel.Rate back = new OperatorChannel.Rate();
        assertTrue(back.accept(5000L));
        assertTrue(back.accept(100L), "Zeitsprung zurück sperrt nicht dauerhaft");
        back.reset();
        assertEquals(0, back.refused());
    }

    @Test
    void roleCheckBindsOnlyAFreeNonPilotInVideoRange() {
        assertEquals(OperatorChannel.Denial.NONE, OperatorChannel.bind(false, false, true, 40.0, 300.0, null, A));
        assertEquals(OperatorChannel.Denial.NONE, OperatorChannel.bind(false, false, true, 40.0, 300.0, A, A));
        assertEquals(OperatorChannel.Denial.PILOT, OperatorChannel.bind(true, false, true, 1.0, 300.0, null, A));
        assertEquals(OperatorChannel.Denial.PILOTING_OTHER, OperatorChannel.bind(false, true, true, 1.0, 300.0, null, A));
        assertEquals(OperatorChannel.Denial.OTHER_LEVEL, OperatorChannel.bind(false, false, false, 1.0, 300.0, null, A));
        assertEquals(OperatorChannel.Denial.OUT_OF_RANGE, OperatorChannel.bind(false, false, true, 301.0, 300.0, null, A));
        assertEquals(OperatorChannel.Denial.TAKEN, OperatorChannel.bind(false, false, true, 1.0, 300.0, B, A));
        assertEquals(OperatorChannel.Denial.NONE, OperatorChannel.bind(false, false, true, 1.0E6, Double.POSITIVE_INFINITY, null, A));
        assertEquals(OperatorChannel.Denial.OUT_OF_RANGE, OperatorChannel.bind(false, false, true, Double.NaN, 300.0, null, A));
        assertEquals(OperatorChannel.Denial.NONE, OperatorChannel.keep(false, false, true, 320.0, 300.0));
        assertEquals(OperatorChannel.Denial.OUT_OF_RANGE, OperatorChannel.keep(false, false, true, 331.0, 300.0));
        assertEquals(OperatorChannel.Denial.PILOT, OperatorChannel.keep(true, false, true, 1.0, 300.0));
        assertEquals(OperatorChannel.Denial.PILOTING_OTHER, OperatorChannel.keep(false, true, true, 1.0, 300.0));
        assertTrue(OperatorChannel.accepts(A, A));
        assertFalse(OperatorChannel.accepts(A, B));
        assertFalse(OperatorChannel.accepts(null, A));
        assertFalse(OperatorChannel.accepts(A, null));
    }

    private static int encodedSize(OperatorInputC2S payload) {
        ByteBuf buffer = Unpooled.buffer();
        OperatorInputC2S.CODEC.encode(buffer, payload);
        return buffer.readableBytes();
    }

    private static OperatorInputC2S roundTrip(OperatorInputC2S payload) {
        ByteBuf buffer = Unpooled.buffer();
        OperatorInputC2S.CODEC.encode(buffer, payload);
        return OperatorInputC2S.CODEC.decode(buffer);
    }
}
