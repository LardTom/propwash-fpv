package de.lardtom.propwash.audio;

public final class SynthMath {
    public static final int TABLE_BITS = 12;
    public static final int TABLE_SIZE = 1 << TABLE_BITS;

    private static final float[] SINE = new float[TABLE_SIZE + 1];

    static {
        for (int i = 0; i <= TABLE_SIZE; i++) {
            SINE[i] = (float) Math.sin(2.0 * Math.PI * i / TABLE_SIZE);
        }
    }

    private SynthMath() {
    }

    public static float sinCycles(double cycles) {
        double fraction = cycles - (long) cycles;
        if (fraction < 0.0) {
            fraction += 1.0;
        }
        double position = fraction * TABLE_SIZE;
        int index = (int) position;
        if (index >= TABLE_SIZE) {
            index = TABLE_SIZE - 1;
        }
        float t = (float) (position - index);
        float a = SINE[index];
        return a + t * (SINE[index + 1] - a);
    }

    public static float softClip(float x) {
        if (x >= 3.0F) {
            return 1.0F;
        }
        if (x <= -3.0F) {
            return -1.0F;
        }
        float x2 = x * x;
        float y = x * (27.0F + x2) / (27.0F + 9.0F * x2);
        return y > 1.0F ? 1.0F : (y < -1.0F ? -1.0F : y);
    }

    public static float onePoleCoefficient(double cutoffHz, int sampleRate) {
        double hz = Math.max(1.0, Math.min(cutoffHz, sampleRate * 0.45));
        return (float) (1.0 - Math.exp(-2.0 * Math.PI * hz / sampleRate));
    }

    public static float smoothingCoefficient(double seconds, int sampleRate) {
        double s = Math.max(1.0e-4, seconds);
        return (float) (1.0 - Math.exp(-1.0 / (s * sampleRate)));
    }

    public static short toPcm16(float sample) {
        float s = sample != sample ? 0.0F : sample;
        if (s > 1.0F) {
            s = 1.0F;
        } else if (s < -1.0F) {
            s = -1.0F;
        }
        return (short) Math.round(s * 32767.0F);
    }
}
