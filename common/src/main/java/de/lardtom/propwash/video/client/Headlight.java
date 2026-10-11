package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.drone.led.LedAnimator;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.video.HeadlightMath;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class Headlight {
    public static final Headlight INSTANCE = new Headlight();

    private @Nullable QuadcopterEntity drone;
    private boolean equipped;
    private LedAnimator.Light light = LedAnimator.Light.OFF;
    private float[] tint = HeadlightMath.tint(1.0F, 1.0F, 1.0F);
    private int rawLight = 15;
    private double darkness = Double.NaN;
    private float gain;
    private long lastNanos;
    private @Nullable Float forcedDarkness;

    private Headlight() {
    }

    public boolean active() {
        return this.gain > 0.0F;
    }

    public boolean equipped() {
        return this.equipped;
    }

    public float gain() {
        return this.gain;
    }

    public float red() {
        return this.tint[0];
    }

    public float green() {
        return this.tint[1];
    }

    public float blue() {
        return this.tint[2];
    }

    public float intensity() {
        return this.light.intensity();
    }

    public float darkness() {
        return Double.isFinite(this.darkness) ? (float) this.darkness : 0.0F;
    }

    public int rawLight() {
        return this.rawLight;
    }

    public boolean usesShaderPass() {
        return this.active() && !HeadlightPass.INSTANCE.fallbackNeeded();
    }

    public boolean usesOverlay() {
        return this.active() && HeadlightPass.INSTANCE.fallbackNeeded();
    }

    void forceDarkness(@Nullable Float value) {
        this.forcedDarkness = value;
    }

    void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        long now = Util.getNanos();
        double dt = this.lastNanos == 0L ? 0.0 : Math.min(Math.max((now - this.lastNanos) * 1.0E-9, 0.0), 0.25);
        this.lastNanos = now;
        VideoState video = VideoState.INSTANCE;
        QuadcopterEntity source = video.active() ? video.source() : null;
        ClientLevel level = minecraft.level;
        if (source == null || level == null || source.isRemoved()) {
            this.reset();
            return;
        }
        if (source != this.drone) {
            this.drone = source;
            this.darkness = Double.NaN;
        }
        Optional<AccessoryType> strip = DroneLeds.strip(source);
        LedSettings settings = DroneLeds.settings(source);
        this.equipped = strip.isPresent();
        if (strip.isEmpty() || !settings.headlight() || !VideoSettings.get().ledLighting) {
            this.light = LedAnimator.Light.OFF;
            this.gain = 0.0F;
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        double seconds = (source.tickCount + partial) / 20.0;
        this.light = LedAnimator.light(settings, strip.get().ledCount, DroneLeds.input(source, seconds));
        this.tint = HeadlightMath.tint(this.light.red(), this.light.green(), this.light.blue());
        BlockPos pos = BlockPos.containing(source.getX(), source.getY() + source.getBbHeight() * 0.5, source.getZ());
        this.rawLight = level.getMaxLocalRawBrightness(pos);
        double gamma = minecraft.options.gamma().get();
        double target = this.forcedDarkness != null ? this.forcedDarkness : HeadlightMath.darkness(this.rawLight, gamma);
        this.darkness = HeadlightMath.smooth(this.darkness, target, dt);
        this.gain = (float) HeadlightMath.gain(this.light.intensity(), this.darkness);
    }

    void reset() {
        this.drone = null;
        this.equipped = false;
        this.light = LedAnimator.Light.OFF;
        this.darkness = Double.NaN;
        this.gain = 0.0F;
        this.rawLight = 15;
    }
}
