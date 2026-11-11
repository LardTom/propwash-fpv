package de.lardtom.propwash.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record ZoneOwner(UUID id, String name) {
    public static final int MAX_NAME = 32;
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(ZoneOwner::parse, UUID::toString);
    public static final Codec<ZoneOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("id").forGetter(ZoneOwner::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ZoneOwner::name)
    ).apply(i, ZoneOwner::new));

    public ZoneOwner {
        Objects.requireNonNull(id, "id");
        name = cleanName(name);
    }

    public static DataResult<UUID> parse(String text) {
        try {
            return DataResult.success(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Keine UUID: " + text);
        }
    }

    public static String cleanName(@Nullable String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        name.strip().codePoints().filter(c -> !Character.isISOControl(c) && c != '§').forEach(c -> {
            if (out.codePointCount(0, out.length()) < MAX_NAME) {
                out.appendCodePoint(c);
            }
        });
        return out.toString().strip();
    }

    public boolean is(@Nullable UUID player) {
        return this.id.equals(player);
    }
}
