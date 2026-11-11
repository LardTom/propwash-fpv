package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import de.lardtom.propwash.trick.TrickType;

import java.util.Optional;

public final class FeatCodecs {
    public static final int MAX_TRICK_KEY = 48;
    public static final Codec<String> TRICK = Codec.STRING.validate(key -> trick(key).isPresent() ? DataResult.success(key)
            : DataResult.error(() -> "Unbekannter Trick: " + key));

    private FeatCodecs() {
    }

    public static Optional<TrickType> trick(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_TRICK_KEY) {
            return Optional.empty();
        }
        for (TrickType type : TrickType.values()) {
            if (type.key().equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
