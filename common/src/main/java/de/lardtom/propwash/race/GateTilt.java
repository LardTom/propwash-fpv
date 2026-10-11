package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GateTilt(float yaw, float pitch, float roll) {
    public static final GateTilt NONE = new GateTilt(0.0F, 0.0F, 0.0F);
    public static final float MAX_PITCH = 90.0F;
    public static final float RESOLUTION = 10.0F;
    public static final Codec<GateTilt> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(GateTilt::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(GateTilt::pitch),
            Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(GateTilt::roll)
    ).apply(i, GateTilt::new));

    public GateTilt {
        yaw = wrap(yaw);
        pitch = quantize(Math.max(-MAX_PITCH, Math.min(MAX_PITCH, finite(pitch))));
        roll = wrap(roll);
    }

    public static GateTilt of(double yaw, double pitch, double roll) {
        return new GateTilt((float) yaw, (float) pitch, (float) roll);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float quantize(float value) {
        float rounded = Math.round(value * RESOLUTION) / RESOLUTION;
        return rounded == 0.0F ? 0.0F : rounded;
    }

    public static float wrap(float degrees) {
        float value = finite(degrees) % 360.0F;
        if (value > 180.0F) {
            value -= 360.0F;
        } else if (value <= -180.0F) {
            value += 360.0F;
        }
        value = quantize(value);
        return value <= -180.0F ? 180.0F : value;
    }

    public boolean none() {
        return this.yaw == 0.0F && this.pitch == 0.0F && this.roll == 0.0F;
    }

    public boolean tilted() {
        return this.pitch != 0.0F || this.roll != 0.0F;
    }

    public GateTilt withYaw(float value) {
        return new GateTilt(value, this.pitch, this.roll);
    }

    public GateTilt turned(float degrees) {
        return new GateTilt(this.yaw + degrees, this.pitch, this.roll);
    }
}
