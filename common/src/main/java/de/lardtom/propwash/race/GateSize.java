package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityDimensions;

import java.util.Locale;

public enum GateSize implements StringRepresentable {
    SMALL(3.0F, 3.0F, 0.5F, 0.5F),
    MEDIUM(4.0F, 4.0F, 0.5F, 0.5F),
    LARGE(6.0F, 4.0F, 0.5F, 0.5F);

    public static final Codec<GateSize> CODEC = StringRepresentable.fromEnum(GateSize::values);
    public static final double MIN_OPENING = 1.0;
    public static final double MAX_OPENING = 16.0;
    public static final double OPENING_STEP = 0.5;

    private final float width;
    private final float height;
    private final float frame;
    private final float depth;
    private final EntityDimensions dimensions;

    GateSize(float width, float height, float frame, float depth) {
        this.width = width;
        this.height = height;
        this.frame = frame;
        this.depth = depth;
        this.dimensions = EntityDimensions.fixed(width, height);
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return this.key();
    }

    public float width() {
        return this.width;
    }

    public float height() {
        return this.height;
    }

    public float frame() {
        return this.frame;
    }

    public float depth() {
        return this.depth;
    }

    public EntityDimensions dimensions() {
        return this.dimensions;
    }

    public double openingWidth() {
        return this.width - 2.0 * this.frame;
    }

    public double openingHeight() {
        return this.height - 2.0 * this.frame;
    }

    public double outer(double opening) {
        return snapOpening(opening) + 2.0 * this.frame;
    }

    public boolean standard(double width, double height) {
        return Math.abs(width - this.width) < 1.0E-6 && Math.abs(height - this.height) < 1.0E-6;
    }

    public static double snapOpening(double opening) {
        if (!Double.isFinite(opening)) {
            return MIN_OPENING;
        }
        double snapped = Math.round(opening / OPENING_STEP) * OPENING_STEP;
        return Math.max(MIN_OPENING, Math.min(MAX_OPENING, snapped));
    }

    public static int packOpening(double width, double height) {
        int w = (int) Math.round(snapOpening(width) / OPENING_STEP);
        int h = (int) Math.round(snapOpening(height) / OPENING_STEP);
        return (w << 8) | h;
    }

    public static double unpackOpeningWidth(int packed) {
        return snapOpening(((packed >> 8) & 0xFF) * OPENING_STEP);
    }

    public static double unpackOpeningHeight(int packed) {
        return snapOpening((packed & 0xFF) * OPENING_STEP);
    }

    public static GateSize byOrdinal(int ordinal) {
        GateSize[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MEDIUM;
    }

    public static GateSize byKey(String key) {
        for (GateSize size : values()) {
            if (size.key().equals(key)) {
                return size;
            }
        }
        return MEDIUM;
    }
}
