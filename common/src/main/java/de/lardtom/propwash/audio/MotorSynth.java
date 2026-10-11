package de.lardtom.propwash.audio;

public final class MotorSynth {
    public static final int SAMPLE_RATE = 48_000;
    public static final int MOTORS = MotorControl.MOTORS;
    public static final int MAX_HARMONICS = 8;

    private static final int BLOCK = 32;
    private static final float INV_RATE = 1.0F / SAMPLE_RATE;
    private static final float HARMONIC_CEILING_HZ = 16_500.0F;
    private static final float HARMONIC_FADE_HZ = 4_500.0F;
    private static final float MOTOR_MIX = 0.17F;
    private static final float CHOP_DEPTH = 0.45F;
    private static final float RUMBLE_SCALE = 9.0F;
    private static final float IMPULSE_THUMP_HZ = 85.0F;
    private static final float WANDER_DEPTH = 0.0025F;
    private static final float WIND_GAIN = 0.55F;
    private static final float ROTOR_MODULATION = 0.16F;
    private static final float OUTSIDE_WASH_RUMBLE = 0.25F;
    private static final float OUTPUT_GAIN = 2.0F;
    private static final float ANTI_DENORMAL = 1.0e-18F;
    private static final float HISS_COEFFICIENT = SynthMath.onePoleCoefficient(5_500.0, SAMPLE_RATE);

    private final MotorTimbre timbre;
    private final MotorControl control;
    private final StateVariableFilter air = new StateVariableFilter(SAMPLE_RATE);
    private final StateVariableFilter duct = new StateVariableFilter(SAMPLE_RATE);
    private final StateVariableFilter wind = new StateVariableFilter(SAMPLE_RATE);

    private final double[] rotorPhase = new double[MOTORS];
    private final float[] rpmSmooth = new float[MOTORS];
    private final float[] gainSmooth = new float[MOTORS];
    private final float[] loadSmooth = new float[MOTORS];
    private final float[] detune = new float[MOTORS];
    private final float[] wander = new float[MOTORS];
    private final float[] harmonicAmp = new float[MOTORS * MAX_HARMONICS];
    private final float[] jitter = new float[MOTORS * MAX_HARMONICS];
    private final int[] harmonicCount = new int[MOTORS];
    private final float[] motorGainTarget = new float[MOTORS];
    private final float[] rotorAmp = new float[MOTORS];
    private final float[] whineAmp = new float[MOTORS];
    private final float[] whine2Amp = new float[MOTORS];
    private final float[] commutationAmp = new float[MOTORS];

    private final float[] targetRpm = new float[MOTORS];
    private final float[] targetLoad = new float[MOTORS];
    private float targetWash;
    private float targetAirflow;
    private float targetDoppler = 1.0F;
    private float targetGain;
    private float targetBrightness = 18_000.0F;
    private boolean targetActive;
    private boolean targetOnboard = true;
    private float rpmCoefficient;
    private int seenImpulses;

    private float dopplerSmooth = 1.0F;
    private float washSmooth;
    private float airflowSmooth;
    private float gust;
    private float masterSmooth;
    private float brightnessSmooth = 18_000.0F;
    private float absorbCoefficient = 1.0F;
    private float absorbState;
    private float rumbleCoefficient;
    private float hissState;
    private float rumble1;
    private float rumble2;
    private float dcIn;
    private float dcOut;
    private float impulseEnvelope;
    private float impulseDecay;
    private double impulsePhase;
    private int noiseState;
    private boolean primed;

    private float airLevel;
    private float hissLevel;
    private float ductLevel;
    private float rumbleLevel;
    private float windLevel;
    private float gainSum;
    private float peak;
    private long framesRendered;

    public MotorSynth(MotorTimbre timbre, MotorControl control, long seed) {
        this.timbre = timbre == null ? MotorTimbre.DEFAULT : timbre;
        this.control = control;
        int s = (int) (seed ^ (seed >>> 32)) * 0x9E3779B1;
        this.noiseState = s == 0 ? 0x2545F491 : s;
        for (int m = 0; m < MOTORS; m++) {
            this.detune[m] = 1.0F + this.timbre.detuneSpread() * this.white();
            this.rotorPhase[m] = 0.5 * (this.white() + 1.0F);
        }
        this.rumbleCoefficient = SynthMath.onePoleCoefficient(75.0, SAMPLE_RATE);
        this.impulseDecay = (float) Math.exp(-1.0 / (0.045 * SAMPLE_RATE));
    }

    public MotorTimbre timbre() {
        return this.timbre;
    }

    public float peak() {
        return this.peak;
    }

    public long framesRendered() {
        return this.framesRendered;
    }

    public void render(short[] out, int frames) {
        this.pullTargets();
        int done = 0;
        while (done < frames) {
            int n = Math.min(BLOCK, frames - done);
            this.updateBlock(n);
            for (int i = 0; i < n; i++) {
                out[done + i] = SynthMath.toPcm16(this.nextSample());
            }
            done += n;
        }
        this.framesRendered += frames;
    }

    public void render(float[] out, int frames) {
        this.pullTargets();
        int done = 0;
        while (done < frames) {
            int n = Math.min(BLOCK, frames - done);
            this.updateBlock(n);
            for (int i = 0; i < n; i++) {
                out[done + i] = this.nextSample();
            }
            done += n;
        }
        this.framesRendered += frames;
    }

    private void pullTargets() {
        MotorControl c = this.control;
        c.version();
        for (int m = 0; m < MOTORS; m++) {
            this.targetRpm[m] = c.rpm(m);
            this.targetLoad[m] = c.load(m);
        }
        this.targetWash = c.wash();
        this.targetAirflow = c.airflow();
        this.targetDoppler = c.doppler();
        this.targetGain = c.gain();
        this.targetBrightness = c.brightnessHz();
        this.targetActive = c.active();
        this.targetOnboard = c.onboard();
        this.rpmCoefficient = SynthMath.smoothingCoefficient(c.smoothingSeconds(), SAMPLE_RATE);
        int impulses = c.impulseCount();
        if (impulses != this.seenImpulses) {
            this.seenImpulses = impulses;
            if (this.primed) {
                this.impulseEnvelope = Math.max(this.impulseEnvelope, c.impulseStrength());
            }
        }
        if (!this.primed) {
            this.primed = true;
            for (int m = 0; m < MOTORS; m++) {
                this.rpmSmooth[m] = this.targetRpm[m];
                this.loadSmooth[m] = this.targetLoad[m];
            }
            this.dopplerSmooth = this.targetDoppler;
            this.washSmooth = this.targetWash;
            this.airflowSmooth = this.targetAirflow;
            this.brightnessSmooth = this.targetBrightness;
        }
    }

    private void updateBlock(int frames) {
        MotorTimbre t = this.timbre;
        float blockFactor = frames * INV_RATE;
        float slow = 1.0F - (float) Math.exp(-blockFactor / 0.06F);
        this.washSmooth += slow * (this.targetWash - this.washSmooth);
        this.airflowSmooth += slow * (this.targetAirflow - this.airflowSmooth);
        this.gust += 0.01F * (this.white() * 1.6F - this.gust);
        this.brightnessSmooth += slow * (this.targetBrightness - this.brightnessSmooth);
        this.absorbCoefficient = SynthMath.onePoleCoefficient(this.brightnessSmooth, SAMPLE_RATE);
        float maxRpm = (float) Math.max(1000.0, t.maxRpm());
        float wash = this.washSmooth;
        float jitterDepth = 0.10F + 0.65F * wash;
        float loadSum = 0.0F;
        float bladePassSum = 0.0F;
        float gainTotal = 0.0F;
        for (int m = 0; m < MOTORS; m++) {
            this.loadSmooth[m] += slow * (this.targetLoad[m] - this.loadSmooth[m]);
            this.wander[m] += 0.03F * (this.white() - this.wander[m]);
            float rpm = this.rpmSmooth[m];
            float fraction = Math.min(1.4F, rpm / maxRpm);
            float load = this.loadSmooth[m];
            float motorGain = fraction <= 0.0F ? 0.0F : (float) Math.pow(fraction, 0.6) * (0.6F + 0.4F * load);
            this.motorGainTarget[m] = motorGain;
            gainTotal += motorGain;
            loadSum += load;
            float rotorHz = rpm / 60.0F * this.dopplerSmooth;
            float bladeHz = rotorHz * t.blades();
            bladePassSum += bladeHz;
            float tilt = Math.max(0.35F, t.harmonicTilt() - 0.45F * load);
            int base = m * MAX_HARMONICS;
            int count = 0;
            float norm = 0.0F;
            for (int h = 1; h <= MAX_HARMONICS; h++) {
                float hz = bladeHz * h;
                if (hz >= HARMONIC_CEILING_HZ) {
                    break;
                }
                float fade = Math.min(1.0F, (HARMONIC_CEILING_HZ - hz) / HARMONIC_FADE_HZ);
                float shape = (float) Math.pow(h, -tilt);
                int idx = base + h - 1;
                this.jitter[idx] += 0.2F * (jitterDepth * this.white() - this.jitter[idx]);
                this.harmonicAmp[idx] = shape * fade * (1.0F + this.jitter[idx]);
                norm += shape;
                count = h;
            }
            if (norm > 0.0F) {
                float inv = 1.0F / norm;
                for (int h = 0; h < count; h++) {
                    this.harmonicAmp[base + h] *= inv;
                }
            }
            this.harmonicCount[m] = count;
            float rotorFade = rotorHz < 2.0F ? 0.0F : 1.0F;
            this.rotorAmp[m] = t.rotorGain() * rotorFade;
            float electricalHz = rotorHz * t.polePairs();
            float whineBase = t.whineGain() * (float) Math.sqrt(Math.min(1.0F, fraction)) * (0.45F + 0.55F * load);
            this.whineAmp[m] = whineBase * ceilingFade(electricalHz);
            this.whine2Amp[m] = 0.35F * whineBase * ceilingFade(electricalHz * 2.0F);
            this.commutationAmp[m] = 0.3F * whineBase * ceilingFade(electricalHz * 6.0F) * (1.0F - 0.6F * load);
        }
        float avgLoad = loadSum / MOTORS;
        float avgGain = gainTotal / MOTORS;
        float avgBladeHz = bladePassSum / MOTORS;
        this.air.setup(clamp(avgBladeHz * 2.2F, 250.0F, 8_000.0F), 0.55);
        if (t.ductGain() > 0.0F) {
            float fraction = Math.min(1.0F, avgBladeHz / Math.max(1.0F, (float) t.bladePassHz(maxRpm)));
            this.duct.setup(t.ductHz() * (0.9F + 0.25F * fraction), 5.0);
        }
        float loudness = (float) Math.sqrt(avgGain);
        this.airLevel = t.noiseGain() * loudness * (0.45F + 0.55F * avgLoad);
        this.hissLevel = 0.5F * t.noiseGain() * loudness * avgLoad;
        this.ductLevel = t.ductGain() * loudness * (0.3F + 0.7F * avgLoad);
        if (this.targetOnboard) {
            this.rumbleLevel = t.rumbleGain() * loudness * (0.12F * avgLoad + 1.1F * wash) * RUMBLE_SCALE;
        } else {
            this.rumbleLevel = t.rumbleGain() * loudness * (OUTSIDE_WASH_RUMBLE * wash) * RUMBLE_SCALE;
        }
        float airflow = this.targetOnboard ? this.airflowSmooth : 0.0F;
        this.windLevel = WIND_GAIN * airflow * airflow * (1.0F + 0.3F * this.gust);
        if (airflow > 0.001F) {
            this.wind.setup(450.0F + 1_100.0F * airflow, 0.45);
        }
        this.dopplerStep(frames);
    }

    private void dopplerStep(int frames) {
        float k = 1.0F - (float) Math.exp(-(frames * INV_RATE) / 0.05F);
        this.dopplerSmooth += k * (this.targetDoppler - this.dopplerSmooth);
    }

    private static float ceilingFade(float hz) {
        if (hz >= HARMONIC_CEILING_HZ) {
            return 0.0F;
        }
        return Math.min(1.0F, (HARMONIC_CEILING_HZ - hz) / HARMONIC_FADE_HZ);
    }

    private float nextSample() {
        MotorTimbre t = this.timbre;
        int blades = t.blades();
        int polePairs = t.polePairs();
        float rpmK = this.rpmCoefficient;
        float gainK = 0.004F;
        float dop = this.dopplerSmooth;
        float motorSum = 0.0F;
        float chop = 0.0F;
        float gains = 0.0F;
        for (int m = 0; m < MOTORS; m++) {
            this.rpmSmooth[m] += rpmK * (this.targetRpm[m] - this.rpmSmooth[m]);
            float g = this.gainSmooth[m] + gainK * (this.motorGainTarget[m] - this.gainSmooth[m]);
            if (g < 1.0e-9F) {
                g = 0.0F;
            }
            this.gainSmooth[m] = g;
            double increment = this.rpmSmooth[m] * dop * (this.detune[m] + WANDER_DEPTH * this.wander[m]) / 60.0 * INV_RATE;
            double phase = this.rotorPhase[m] + increment;
            if (phase >= 1.0) {
                phase -= (long) phase;
            }
            this.rotorPhase[m] = phase;
            if (g <= 1.0e-5F && this.whineAmp[m] <= 1.0e-5F) {
                continue;
            }
            double blade = phase * blades;
            int base = m * MAX_HARMONICS;
            int count = this.harmonicCount[m];
            float first = count > 0 ? SynthMath.sinCycles(blade) : 0.0F;
            float tonal = count > 0 ? this.harmonicAmp[base] * first : 0.0F;
            for (int h = 1; h < count; h++) {
                tonal += this.harmonicAmp[base + h] * SynthMath.sinCycles(blade * (h + 1));
            }
            float rotor = SynthMath.sinCycles(phase);
            tonal = tonal * (1.0F + ROTOR_MODULATION * rotor) + this.rotorAmp[m] * rotor;
            double electrical = phase * polePairs;
            float whine = this.whineAmp[m] * SynthMath.sinCycles(electrical)
                    + this.whine2Amp[m] * SynthMath.sinCycles(electrical * 2.0)
                    + this.commutationAmp[m] * SynthMath.sinCycles(electrical * 6.0);
            motorSum += g * tonal + whine;
            chop += g * first;
            gains += g;
        }
        this.gainSum = gains;
        float noise = this.white();
        this.air.process(noise);
        float chopMod = gains > 1.0e-4F ? 1.0F + CHOP_DEPTH * (chop / gains) : 1.0F;
        this.hissState += HISS_COEFFICIENT * (this.air.high() - this.hissState);
        float airOut = (this.air.band() * this.airLevel + this.hissState * this.hissLevel) * chopMod;
        float ductOut = 0.0F;
        if (this.ductLevel > 0.0F) {
            this.duct.process(noise);
            ductOut = this.duct.band() * this.ductLevel;
        }
        float rumbleNoise = this.white();
        this.rumble1 += this.rumbleCoefficient * (rumbleNoise - this.rumble1);
        this.rumble2 += this.rumbleCoefficient * (this.rumble1 - this.rumble2);
        float rumbleOut = this.rumble2 * this.rumbleLevel;
        float windOut = 0.0F;
        if (this.windLevel > 1.0e-5F) {
            this.wind.process(rumbleNoise);
            windOut = this.wind.band() * this.windLevel;
        }
        float impulseOut = 0.0F;
        if (this.impulseEnvelope > 1.0e-4F) {
            this.impulsePhase += IMPULSE_THUMP_HZ * INV_RATE;
            if (this.impulsePhase >= 1.0) {
                this.impulsePhase -= 1.0;
            }
            impulseOut = this.impulseEnvelope * (0.7F * noise + 0.8F * SynthMath.sinCycles(this.impulsePhase));
            this.impulseEnvelope *= this.impulseDecay;
        } else {
            this.impulseEnvelope = 0.0F;
        }
        float mix = MOTOR_MIX * motorSum + airOut + ductOut + rumbleOut + windOut + impulseOut + ANTI_DENORMAL;
        this.absorbState += this.absorbCoefficient * (mix - this.absorbState);
        float filtered = this.absorbState;
        float dc = filtered - this.dcIn + 0.9975F * this.dcOut;
        this.dcIn = filtered;
        float dcMagnitude = Math.abs(dc);
        this.dcOut = dcMagnitude < 1.0e4F && dcMagnitude > 1.0e-20F ? dc : 0.0F;
        float masterTarget = this.targetActive ? this.targetGain : 0.0F;
        this.masterSmooth += 0.0025F * (masterTarget - this.masterSmooth);
        if (this.masterSmooth < 1.0e-9F) {
            this.masterSmooth = 0.0F;
        }
        float out = SynthMath.softClip(OUTPUT_GAIN * this.dcOut * this.masterSmooth);
        if (out != out) {
            out = 0.0F;
            this.resetFilters();
        }
        float magnitude = Math.abs(out);
        if (magnitude > this.peak) {
            this.peak = magnitude;
        } else {
            this.peak = this.peak > 1.0e-9F ? this.peak * 0.99995F : 0.0F;
        }
        return out;
    }

    private void resetFilters() {
        this.air.reset();
        this.duct.reset();
        this.wind.reset();
        this.absorbState = 0.0F;
        this.hissState = 0.0F;
        this.rumble1 = 0.0F;
        this.rumble2 = 0.0F;
        this.dcIn = 0.0F;
        this.dcOut = 0.0F;
        this.impulseEnvelope = 0.0F;
    }

    private float white() {
        int s = this.noiseState;
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        this.noiseState = s;
        return s * (1.0F / 2_147_483_648.0F);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
