package de.lardtom.propwash.rx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.drone.bind.BindCodecs;
import de.lardtom.propwash.drone.bind.ReceiverBind;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RxSettings(@Nullable UUID owner, @Nullable RxSource source, float low, float high, int range) {
    public static final int FORMAT = 1;
    public static final RxSettings EMPTY = new RxSettings(null, null, -1.0F, 1.0F, RxLink.DEFAULT_RANGE);

    public static final MapCodec<RxSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.lenientOptionalFieldOf("Format").forGetter(settings -> Optional.of(FORMAT)),
            BindCodecs.CODEC.lenientOptionalFieldOf("Bind").forGetter(RxSettings::bind),
            RxSource.CODEC.lenientOptionalFieldOf("Source").forGetter(settings -> Optional.ofNullable(settings.source)),
            Codec.FLOAT.lenientOptionalFieldOf("Low").forGetter(settings -> Optional.of(settings.low)),
            Codec.FLOAT.lenientOptionalFieldOf("High").forGetter(settings -> Optional.of(settings.high)),
            Codec.INT.lenientOptionalFieldOf("Range").forGetter(settings -> Optional.of(settings.range))
    ).apply(i, RxSettings::read));

    public RxSettings {
        low = endpoint(low, -1.0F);
        high = endpoint(high, 1.0F);
        range = RxLink.clampRange(range);
    }

    private static RxSettings read(Optional<Integer> format, Optional<ReceiverBind> bind, Optional<RxSource> source, Optional<Float> low,
                                   Optional<Float> high, Optional<Integer> range) {
        UUID owner = bind.map(ReceiverBind::pilot).orElse(null);
        RxSource kind = source.orElse(null);
        float fallbackLow = kind == null ? -1.0F : kind.defaultLow();
        float fallbackHigh = kind == null ? 1.0F : kind.defaultHigh();
        return new RxSettings(owner, kind, low.orElse(fallbackLow), high.orElse(fallbackHigh), range.orElse(RxLink.DEFAULT_RANGE));
    }

    private Optional<ReceiverBind> bind() {
        return this.owner == null ? Optional.empty() : Optional.of(ReceiverBind.of(this.owner));
    }

    public static float endpoint(float value, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, -1.0F, 1.0F) : fallback;
    }

    public static boolean validEndpoint(float value) {
        return Float.isFinite(value) && value >= -1.0F && value <= 1.0F;
    }

    public boolean bound() {
        return this.owner != null;
    }

    public boolean ownedBy(UUID player) {
        return this.owner != null && this.owner.equals(player);
    }

    public RxSettings withOwner(@Nullable UUID owner) {
        return new RxSettings(owner, this.source, this.low, this.high, this.range);
    }

    public RxSettings withSource(RxSource source) {
        Objects.requireNonNull(source, "source");
        return new RxSettings(this.owner, source, source.defaultLow(), source.defaultHigh(), this.range);
    }

    public RxSettings withLow(float low) {
        return new RxSettings(this.owner, this.source, low, this.high, this.range);
    }

    public RxSettings withHigh(float high) {
        return new RxSettings(this.owner, this.source, this.low, high, this.range);
    }

    public RxSettings withRange(int range) {
        return new RxSettings(this.owner, this.source, this.low, this.high, range);
    }

    public RxSettings unbound() {
        return new RxSettings(null, null, -1.0F, 1.0F, this.range);
    }

    public int strength(float value, int previous) {
        return RxMapping.strength(value, this.low, this.high, previous);
    }
}
