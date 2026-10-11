package de.lardtom.propwash.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynthMathTest {
    @Test
    void sineTableMatchesMathSin() {
        for (int i = 0; i < 10_000; i++) {
            double cycles = i * 0.000731 + (i % 7) * 3.0;
            assertEquals(Math.sin(2.0 * Math.PI * cycles), SynthMath.sinCycles(cycles), 2.0e-5);
        }
        assertEquals(Math.sin(2.0 * Math.PI * -0.25), SynthMath.sinCycles(-0.25), 2.0e-5);
    }

    @Test
    void softClipIsBoundedMonotonicAndTransparentNearZero() {
        float last = -2.0F;
        for (int i = -4000; i <= 4000; i++) {
            float x = i / 500.0F;
            float y = SynthMath.softClip(x);
            assertTrue(y >= -1.0F && y <= 1.0F, "softClip(" + x + ") = " + y);
            assertTrue(y >= last - 1.0e-6F, "not monotonic at " + x);
            last = y;
        }
        assertEquals(0.1F, SynthMath.softClip(0.1F), 0.002F);
    }

    @Test
    void pcmConversionClampsAndHandlesNaN() {
        assertEquals(32767, SynthMath.toPcm16(5.0F));
        assertEquals(-32767, SynthMath.toPcm16(-5.0F));
        assertEquals(0, SynthMath.toPcm16(Float.NaN));
    }

    @Test
    void controlSanitizesInput() {
        MotorControl control = new MotorControl();
        control.setMotor(0, Double.NaN, Double.POSITIVE_INFINITY);
        control.setMotor(1, -100.0, -1.0);
        control.setMotor(2, 1.0e9, 2.0);
        control.setMotor(7, 5_000.0, 0.5);
        control.setDoppler(Double.NaN);
        control.setGain(Double.NEGATIVE_INFINITY);
        control.setWash(3.0);
        control.setAirflow(-1.0);
        control.publish();
        assertEquals(0.0F, control.rpm(0));
        assertEquals(0.0F, control.load(0));
        assertEquals(0.0F, control.rpm(1));
        assertEquals(MotorControl.MAX_RPM, control.rpm(2));
        assertEquals(1.0F, control.load(2));
        assertEquals(1.0F, control.doppler());
        assertEquals(0.0F, control.gain());
        assertEquals(1.0F, control.wash());
        assertEquals(0.0F, control.airflow());
        assertEquals(1, control.version());
    }
}
