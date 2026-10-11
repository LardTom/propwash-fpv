package de.lardtom.propwash.video;

public final class FeedModel {
    public static final long DIGITAL_FRAME_NANOS = 1_000_000_000L / 120L;
    public static final long MAX_FREEZE_NANOS = 1_200_000_000L;
    public static final long LOSS_HOLD_NANOS = 300_000_000L;
    public static final long LOSS_HOLD_GENTLE_NANOS = 800_000_000L;
    public static final long DIGITAL_EXTRA_LATENCY_NANOS = 12_000_000L;
    public static final float DIGITAL_LOSS_ENTER = (float) DigitalBreakup.LOSS_ENTER;
    public static final float DIGITAL_LOSS_EXIT = (float) DigitalBreakup.LOSS_EXIT;
    public static final float DIGITAL_SMOOTH_QUALITY = (float) DigitalBreakup.SMOOTH_ABOVE;
    public static final float GENTLE_MAX_ROLL = 0.2F;
    public static final float CAMERA_CLEAN_ABOVE = 0.9F;
    public static final float CAMERA_GENTLE_GLITCH = 0.3F;
    public static final float CAMERA_MAX_GLITCH = 0.6F;
    public static final float GENTLE_MAX_SHIFT = 0.03F;
    public static final double GENTLE_EVENT_SCALE = 0.5;
    public static final double MULTIPATH_DIP_RATE = 1.2;
    public static final double FADING_BASE = 0.03;
    public static final double FADING_MULTIPATH = 0.07;
    private static final double TIME_WRAP_SECONDS = 3600.0;
    private static final double GENTLE_FADE_SECONDS = 0.12;
    private static final long ROLL_GAP_NANOS = 300_000_000L;
    private static final long GENTLE_ROLL_GAP_NANOS = 2_500_000_000L;
    private static final long NO_SLOT = Long.MIN_VALUE;

    private final EffectParams params = new EffectParams();
    private final long seed;
    private long random;
    private boolean started;
    private long lastNanos;
    private double time;
    private boolean lost = true;
    private long lostChangedNanos;
    private float lossMix = 1.0F;
    private boolean burst;
    private long lastSlot = NO_SLOT;
    private long displayedCaptureNanos;
    private long lastArrivalNanos;
    private float roll;
    private float rollSpeed;
    private double rollRemaining;
    private long lastSyncEventNanos;
    private FeedLook lastLook;
    private float link;
    private double glitchLeft;
    private float glitchSeed;
    private float glitchAmount;
    private double shiftLeft;
    private float shiftAmount;
    private float shiftTop;
    private float shiftBottom = 1.0F;
    private double dipLeft;
    private double dipLength;
    private float dipDepth;
    private double colourDropLeft;
    private double bandPhase;
    private int resolutionLevel;

    public FeedModel(long seed) {
        this.seed = seed == 0L ? 0x9E3779B97F4A7C15L : seed;
        this.random = this.seed;
    }

    public EffectParams params() {
        return this.params;
    }

    public boolean lost() {
        return this.lost;
    }

    public float link() {
        return this.link;
    }

    public AnalogBreakup.Stage analogStage() {
        return AnalogBreakup.stage(this.link);
    }

    public DigitalBreakup.Stage digitalStage() {
        return this.lost ? DigitalBreakup.Stage.LOST : DigitalBreakup.stage(this.link);
    }

    public boolean frozen() {
        return !this.params.continuous && !this.params.frameAdvanced;
    }

    public long latencyAt(long nowNanos) {
        if (this.params.continuous) {
            return this.params.latencyNanos;
        }
        return Math.max(this.params.latencyNanos, nowNanos - this.displayedCaptureNanos);
    }

    public long displayedNanos(long frameNanos) {
        return frameNanos - Math.max(0L, this.latencyAt(frameNanos));
    }

    public void forceGlitch(double seconds) {
        this.glitchLeft = Math.max(seconds, 0.0);
        this.glitchSeed = (float) Math.floor(this.nextDouble() * 65536.0);
        this.glitchAmount = 0.9F;
    }

    public void forceSyncShift(double seconds, float amount, float top, float bottom) {
        this.shiftLeft = Math.max(seconds, 0.0);
        this.shiftAmount = amount;
        this.shiftTop = Math.min(Math.max(Math.min(top, bottom), 0.0F), 1.0F);
        this.shiftBottom = Math.min(Math.max(Math.max(top, bottom), 0.0F), 1.0F);
    }

    public void reset() {
        this.random = this.seed;
        this.started = false;
        this.time = 0.0;
        this.lost = true;
        this.lossMix = 1.0F;
        this.burst = false;
        this.lastSlot = NO_SLOT;
        this.roll = 0.0F;
        this.rollSpeed = 0.0F;
        this.rollRemaining = 0.0;
        this.lastLook = null;
        this.link = 0.0F;
        this.glitchLeft = 0.0;
        this.shiftLeft = 0.0;
        this.dipLeft = 0.0;
        this.colourDropLeft = 0.0;
        this.bandPhase = 0.0;
        this.resolutionLevel = 0;
        this.params.clearEffects();
        this.params.frameAdvanced = true;
        this.params.continuous = true;
        this.params.latencyNanos = 0L;
    }

    public EffectParams update(long nowNanos, FeedInput input) {
        long base = Math.max(0L, input.baseLatencyNanos);
        if (!this.started) {
            this.started = true;
            this.lastNanos = nowNanos;
            this.lostChangedNanos = nowNanos;
            this.lastArrivalNanos = nowNanos;
            this.lastSyncEventNanos = nowNanos;
            this.displayedCaptureNanos = nowNanos - base;
        }
        double dt = Math.min(Math.max((nowNanos - this.lastNanos) * 1.0E-9, 0.0), 0.25);
        this.lastNanos = nowNanos;
        this.time = (this.time + dt) % TIME_WRAP_SECONDS;
        if (input.look != this.lastLook) {
            this.lastLook = input.look;
            this.lastSlot = NO_SLOT;
            this.burst = false;
            this.resolutionLevel = 0;
        }
        EffectParams p = this.params;
        p.clearEffects();
        p.timeSeconds = (float) this.time;
        p.noiseContrast = input.reduceFlicker ? 0.65F : 1.0F;
        p.seed = (float) Math.floor(this.time * (input.reduceFlicker ? 30.0 : 60.0));
        p.blockSeed = (float) Math.floor(this.time * (input.reduceFlicker ? 8.0 : 15.0));
        p.noSignalBlack = input.snowOnLoss ? 0.0F : 1.0F;
        float quality = input.connected ? clamp01(input.quality) : 0.0F;
        float strength = clamp01(input.strength);
        float cosmetic = 0.35F + 0.65F * strength;
        switch (input.look) {
            case ANALOG -> this.analog(nowNanos, dt, input, quality, strength, cosmetic, base);
            case DIGITAL -> this.digital(nowNanos, dt, input, quality, strength, cosmetic, base);
            case CLEAN -> this.clean(nowNanos, input, base);
        }
        float extra = clamp01(input.extraNoise);
        if (extra > 0.0F) {
            p.noise = Math.max(p.noise, extra * 0.9F);
            p.chromaNoise = Math.max(p.chromaNoise, p.noise * 0.3F);
        }
        this.cameraDamage(input, strength);
        return p;
    }

    private void cameraDamage(FeedInput input, float strength) {
        float condition = Float.isFinite(input.cameraCondition) ? clamp01(input.cameraCondition) : 1.0F;
        if (condition >= CAMERA_CLEAN_ABOVE) {
            return;
        }
        EffectParams p = this.params;
        if (condition <= 0.0F) {
            p.noSignal = 1.0F;
            p.noSignalBlack = 1.0F;
            return;
        }
        float damage = clamp01((CAMERA_CLEAN_ABOVE - condition) / CAMERA_CLEAN_ABOVE);
        float scale = 0.5F + 0.5F * strength;
        float glitch = 0.5F + 0.5F * valueNoise(this.time * (input.reduceFlicker ? 1.2 : 3.0), 91L);
        float burst = glitch > 1.0F - 0.55F * damage ? (glitch - (1.0F - 0.55F * damage)) / (0.55F * damage + 1.0E-3F) : 0.0F;
        p.noise = Math.max(p.noise, (0.06F + 0.3F * damage * damage) * scale);
        p.chromaNoise = Math.max(p.chromaNoise, p.noise * 0.35F);
        p.desaturation = Math.max(p.desaturation, 0.35F * damage * scale);
        p.vignette = Math.max(p.vignette, 0.12F + 0.3F * damage);
        p.blurPx = Math.max(p.blurPx, 1.6F * damage * scale);
        p.sparkle = Math.max(p.sparkle, 0.25F * damage * scale);
        if (input.look == FeedLook.DIGITAL) {
            p.blockiness = Math.max(p.blockiness, burst * 0.35F * scale);
            p.blockPx = Math.max(p.blockPx, 12.0F);
        } else {
            p.lineJitter = Math.max(p.lineJitter, (float) (burst * AnalogBreakup.MAX_LINE_JITTER * scale));
            float streaks = burst * (input.reduceFlicker ? CAMERA_GENTLE_GLITCH : CAMERA_MAX_GLITCH) * scale;
            if (streaks > p.glitch) {
                p.glitch = streaks;
                p.glitchSeed = this.glitchSeed;
            }
        }
        p.brightness = Math.min(p.brightness, 1.0F - 0.12F * damage);
    }

    private void analog(long now, double dt, FeedInput input, float quality, float strength, float cosmetic, long base) {
        EffectParams p = this.params;
        p.mode = FeedLook.ANALOG.shaderMode();
        boolean gentle = input.reduceFlicker;
        double q = input.connected ? this.analogLink(dt, quality, clamp01(input.multipath), gentle) : 0.0;
        this.link = (float) q;
        p.link = this.link;
        float imageLoss = clamp01(1.0F - input.imageQuality);
        this.analogLook(p, q, imageLoss, cosmetic);
        this.analogGlitches(p, dt, q, gentle, cosmetic);
        this.analogColour(p, dt, q, gentle, imageLoss, cosmetic);
        this.analogSnow(p, q, imageLoss, strength, cosmetic);
        this.analogSync(p, now, dt, q, gentle, cosmetic);
        this.analogStatic(p, q, input.connected);
        this.continuous(now, base);
    }

    private double analogLink(double dt, float quality, float multipath, boolean gentle) {
        if (quality <= 0.0F) {
            this.dipLeft = 0.0;
            return 0.0;
        }
        double slow = 0.55 * valueNoise(this.time * 0.37, 17L)
                + 0.30 * valueNoise(this.time * 1.13, 23L)
                + 0.15 * valueNoise(this.time * 2.71, 29L);
        double margin = Math.min(1.0, (1.0 - quality) * 6.0);
        double amplitude = Math.min((FADING_BASE + FADING_MULTIPATH * multipath) * margin, quality * 0.5);
        double dip = this.multipathDip(dt, quality, multipath, gentle);
        return clamp01((float) (quality + amplitude * slow - dip));
    }

    private double multipathDip(double dt, float quality, float multipath, boolean gentle) {
        if (this.dipLeft > 0.0) {
            this.dipLeft = Math.max(0.0, this.dipLeft - dt);
            double phase = 1.0 - this.dipLeft / this.dipLength;
            double s = Math.sin(Math.PI * phase);
            return this.dipDepth * s * s;
        }
        double rate = multipath * MULTIPATH_DIP_RATE * (gentle ? GENTLE_EVENT_SCALE : 1.0);
        if (rate > 0.0 && this.nextDouble() < rate * dt) {
            this.dipLength = gentle ? 0.25 + this.nextDouble() * 0.3 : 0.08 + this.nextDouble() * 0.27;
            this.dipLeft = this.dipLength;
            double depth = (0.25 + 0.45 * this.nextDouble()) * (0.5 + 0.5 * multipath) * (0.67 + 0.33 * (1.0 - quality));
            this.dipDepth = (float) (gentle ? depth * 0.7 : depth);
        }
        return 0.0;
    }

    private void analogLook(EffectParams p, double q, float imageLoss, float cosmetic) {
        double soft = AnalogBreakup.softness(q);
        p.bleedPx = (float) ((0.8 + imageLoss * 3.0 + soft * 2.5) * cosmetic);
        p.vignette = (float) ((0.15 + soft * 0.1) * cosmetic);
        p.scanlines = 0.05F * cosmetic;
    }

    private void analogGlitches(EffectParams p, double dt, double q, boolean gentle, float cosmetic) {
        if (this.glitchLeft > 0.0) {
            this.glitchLeft -= dt;
        } else {
            double rate = AnalogBreakup.glitchRate(q) * (gentle ? GENTLE_EVENT_SCALE : 1.0);
            if (rate > 0.0 && this.nextDouble() < rate * dt) {
                this.glitchLeft = 0.02 + this.nextDouble() * 0.05;
                this.glitchSeed = (float) Math.floor(this.nextDouble() * 65536.0);
                this.glitchAmount = (float) (0.35 + 0.65 * this.nextDouble());
            }
        }
        boolean active = this.glitchLeft > 0.0;
        p.glitch = active ? this.glitchAmount * cosmetic : 0.0F;
        p.glitchSeed = this.glitchSeed;
        double bursts = 0.5 + 0.5 * valueNoise(this.time * 2.3, 31L);
        p.sparkle = (float) (AnalogBreakup.sparkle(q) * (0.35 + 0.65 * bursts) * cosmetic);
        p.lineJitter = (float) (AnalogBreakup.lineJitter(q) * cosmetic * (active ? 1.0 : 0.5));
    }

    private void analogColour(EffectParams p, double dt, double q, boolean gentle, float imageLoss, float cosmetic) {
        double colour = AnalogBreakup.colour(q);
        if (this.colourDropLeft > 0.0) {
            this.colourDropLeft -= dt;
        } else if (!gentle) {
            double rate = AnalogBreakup.colourFlickerRate(q);
            if (rate > 0.0 && this.nextDouble() < rate * dt) {
                this.colourDropLeft = 0.04 + this.nextDouble() * 0.12;
            }
        }
        if (this.colourDropLeft > 0.0) {
            colour = 0.0;
        }
        double drift = 0.08 + 0.25 * (0.5 + 0.5 * valueNoise(this.time * 0.7, 37L));
        this.bandPhase = (this.bandPhase + dt * drift) % 1000.0;
        double baseline = 1.0 - imageLoss * 0.25 * cosmetic;
        p.desaturation = (float) (1.0 - colour * baseline);
        p.chromaBands = (float) AnalogBreakup.chromaBands(q);
        p.bandPhase = (float) this.bandPhase;
        p.chromaNoise = (float) (AnalogBreakup.chromaNoise(q) * colour * cosmetic);
    }

    private void analogSnow(EffectParams p, double q, float imageLoss, float strength, float cosmetic) {
        p.noise = (float) Math.max(imageLoss * 0.05 * cosmetic, AnalogBreakup.snow(q) * (0.6 + 0.4 * strength));
    }

    private void analogSync(EffectParams p, long now, double dt, double q, boolean gentle, float cosmetic) {
        double sync = AnalogBreakup.sync(q);
        if (this.shiftLeft > 0.0) {
            this.shiftLeft -= dt;
        } else {
            double rate = AnalogBreakup.shiftRate(q) * (gentle ? GENTLE_EVENT_SCALE : 1.0);
            if (rate > 0.0 && this.nextDouble() < rate * dt) {
                this.shiftLeft = 0.04 + this.nextDouble() * 0.2;
                double direction = this.nextDouble() < 0.5 ? -1.0 : 1.0;
                this.shiftAmount = (float) (direction * (0.015 + 0.09 * this.nextDouble()) * (0.4 + 0.6 * sync));
                if (this.nextDouble() < 0.3) {
                    this.shiftTop = 0.0F;
                    this.shiftBottom = 1.0F;
                } else {
                    float top = (float) this.nextDouble();
                    this.shiftTop = top;
                    this.shiftBottom = Math.min(1.0F, top + 0.05F + 0.5F * (float) this.nextDouble());
                }
            }
        }
        float shift = this.shiftLeft > 0.0 ? this.shiftAmount * cosmetic : 0.0F;
        if (gentle) {
            shift = Math.min(Math.max(shift, -GENTLE_MAX_SHIFT), GENTLE_MAX_SHIFT);
        }
        p.syncShift = shift;
        p.shiftTop = this.shiftTop;
        p.shiftBottom = this.shiftBottom;
        p.lineJitter += (float) (sync * 0.002 * cosmetic);
        this.updateRoll(now, dt, AnalogBreakup.rollRate(q), gentle);
        p.roll = this.roll;
        p.brightness = (float) (1.0 - 0.1 * sync);
        if (!gentle && sync > 0.0) {
            p.brightness += (float) (0.06 * sync * valueNoise(this.time * 7.3, 43L));
        }
        float wobble = gentle ? 0.0F : (float) (sync * 0.0015 * valueNoise(this.time * 17.0, 47L));
        p.osdShift = shift * (this.shiftBottom - this.shiftTop) + wobble;
    }

    private void analogStatic(EffectParams p, double q, boolean connected) {
        float loss = connected ? (float) AnalogBreakup.staticMix(q) : 1.0F;
        this.lost = loss >= 0.999F;
        this.lossMix = loss;
        p.noSignal = loss;
    }

    private void digital(long now, double dt, FeedInput input, float q, float strength, float cosmetic, long base) {
        EffectParams p = this.params;
        p.mode = FeedLook.DIGITAL.shaderMode();
        this.link = q;
        p.link = q;
        this.digitalPicture(p, q, clamp01(1.0F - input.imageQuality), strength, cosmetic);
        long latency = base + (long) ((1.0F - q) * DIGITAL_EXTRA_LATENCY_NANOS);
        this.digitalLink(p, now, dt, input, q, latency);
    }

    private void digitalPicture(EffectParams p, float q, float imageLoss, float strength, float cosmetic) {
        double blocks = DigitalBreakup.blockiness(q);
        p.blurPx = (float) ((imageLoss * 1.5 + DigitalBreakup.blur(q) * 3.5) * cosmetic);
        p.blockiness = (float) (blocks * 0.4 * (0.4 + 0.6 * strength));
        p.blockPx = (float) DigitalBreakup.blockPx(q);
        p.posterize = (float) DigitalBreakup.posterize(q);
        this.resolutionLevel = DigitalBreakup.resolutionLevel(q, this.resolutionLevel);
        p.resolution = (float) DigitalBreakup.resolutionScale(this.resolutionLevel);
    }

    private void digitalLink(EffectParams p, long now, double dt, FeedInput input, float q, long latency) {
        boolean frozenTooLong = !this.lost && now - this.lastArrivalNanos > MAX_FREEZE_NANOS;
        boolean wantLoss = !input.connected || q < DIGITAL_LOSS_ENTER || frozenTooLong;
        boolean wantRecover = input.connected && q > DIGITAL_LOSS_EXIT;
        long hold = input.reduceFlicker ? LOSS_HOLD_GENTLE_NANOS : LOSS_HOLD_NANOS;
        if (!this.lost && wantLoss) {
            this.lost = true;
            this.lostChangedNanos = now;
        } else if (this.lost && wantRecover && now - this.lostChangedNanos >= hold) {
            this.lost = false;
            this.lostChangedNanos = now;
            this.burst = false;
            this.lastSlot = NO_SLOT;
            this.lastArrivalNanos = now;
        }
        if (this.lost || (q >= DIGITAL_SMOOTH_QUALITY && !this.burst)) {
            this.continuous(now, latency);
        } else {
            this.advanceClock(now, q, latency);
        }
        float target = this.lost ? 1.0F : 0.0F;
        if (input.reduceFlicker) {
            float step = (float) (dt / GENTLE_FADE_SECONDS);
            this.lossMix = this.lossMix < target ? Math.min(target, this.lossMix + step) : Math.max(target, this.lossMix - step);
        } else {
            this.lossMix = target;
        }
        p.noSignal = this.lossMix;
    }

    private void clean(long now, FeedInput input, long base) {
        EffectParams p = this.params;
        p.mode = FeedLook.CLEAN.shaderMode();
        this.lost = !input.connected;
        this.link = this.lost ? 0.0F : 1.0F;
        p.link = this.link;
        this.lossMix = this.lost ? 1.0F : 0.0F;
        p.noSignal = this.lossMix;
        this.continuous(now, base);
    }

    private void continuous(long now, long latency) {
        EffectParams p = this.params;
        this.displayedCaptureNanos = now - latency;
        this.lastArrivalNanos = now;
        this.lastSlot = NO_SLOT;
        p.continuous = true;
        p.frameAdvanced = true;
        p.latencyNanos = latency;
        p.displayedCaptureNanos = this.displayedCaptureNanos;
    }

    private void advanceClock(long now, float q, long latency) {
        EffectParams p = this.params;
        long slot = Math.floorDiv(now - latency, DIGITAL_FRAME_NANOS);
        boolean advanced = false;
        if (this.lastSlot == NO_SLOT) {
            advanced = true;
            this.displayedCaptureNanos = slot * DIGITAL_FRAME_NANOS;
            this.lastSlot = slot;
        } else if (slot > this.lastSlot) {
            long count = Math.min(slot - this.lastSlot, 64L);
            double goodToBad = DigitalBreakup.burstStart(q);
            double badToGood = DigitalBreakup.burstEnd(q);
            double arrive = DigitalBreakup.frameArrival(q);
            long arrived = NO_SLOT;
            for (long i = slot - count + 1; i <= slot; i++) {
                if (this.burst) {
                    if (this.nextDouble() < badToGood) {
                        this.burst = false;
                    }
                } else if (this.nextDouble() < goodToBad) {
                    this.burst = true;
                }
                if (!this.burst && this.nextDouble() < arrive) {
                    arrived = i;
                }
            }
            if (arrived != NO_SLOT) {
                advanced = true;
                this.displayedCaptureNanos = arrived * DIGITAL_FRAME_NANOS;
            }
            this.lastSlot = slot;
        }
        if (advanced) {
            this.lastArrivalNanos = now;
        }
        p.continuous = false;
        p.frameAdvanced = advanced;
        p.displayedCaptureNanos = this.displayedCaptureNanos;
        p.latencyNanos = Math.max(latency, now - this.displayedCaptureNanos);
    }

    private void updateRoll(long now, double dt, double rate, boolean gentle) {
        if (this.rollRemaining > 0.0) {
            this.roll += (float) (this.rollSpeed * dt);
            this.rollRemaining -= dt;
            if (this.rollRemaining <= 0.0) {
                this.rollRemaining = 0.0;
                if (!gentle) {
                    this.roll = 0.0F;
                }
            }
        } else {
            this.roll *= (float) Math.exp(-dt * 5.0);
            if (Math.abs(this.roll) < 1.0E-3F) {
                this.roll = 0.0F;
            }
            long gap = gentle ? GENTLE_ROLL_GAP_NANOS : ROLL_GAP_NANOS;
            if (rate > 0.0 && now - this.lastSyncEventNanos >= gap && this.nextDouble() < rate * dt) {
                this.lastSyncEventNanos = now;
                double direction = this.nextDouble() < 0.5 ? -1.0 : 1.0;
                this.rollSpeed = (float) (direction * (gentle ? 0.6 : 1.5 + this.nextDouble() * 2.5));
                this.rollRemaining = gentle ? 0.3 : 0.12 + this.nextDouble() * 0.3;
            }
        }
        if (gentle) {
            this.roll = Math.min(Math.max(this.roll, -GENTLE_MAX_ROLL), GENTLE_MAX_ROLL);
        } else {
            this.roll %= 1.0F;
        }
    }

    private double nextDouble() {
        long x = this.random;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        this.random = x;
        return (x >>> 11) * 0x1.0p-53;
    }

    static float valueNoise(double x, long salt) {
        double floor = Math.floor(x);
        long cell = (long) floor;
        double f = x - floor;
        double a = hashUnit(cell, salt);
        double b = hashUnit(cell + 1L, salt);
        double u = f * f * (3.0 - 2.0 * f);
        return (float) ((a + (b - a) * u) * 2.0 - 1.0);
    }

    private static double hashUnit(long cell, long salt) {
        long h = cell * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return Math.min(Math.max(value, 0.0F), 1.0F);
    }
}
