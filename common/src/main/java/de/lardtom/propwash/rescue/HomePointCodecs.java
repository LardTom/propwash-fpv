package de.lardtom.propwash.rescue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class HomePointCodecs {
    public static final String KEY = "HomePoint";
    public static final Codec<HomePoint.Source> SOURCE = Codec.STRING.comapFlatMap(HomePointCodecs::source, HomePoint.Source::id);
    private static final Codec<String> DIMENSION = Identifier.CODEC.xmap(Identifier::toString, Identifier::parse);
    public static final Codec<HomePoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("x").forGetter(HomePoint::x),
            Codec.DOUBLE.fieldOf("y").forGetter(HomePoint::y),
            Codec.DOUBLE.fieldOf("z").forGetter(HomePoint::z),
            DIMENSION.optionalFieldOf("dimension").forGetter(p -> p.dimension().isEmpty() ? Optional.empty() : Optional.of(p.dimension())),
            Codec.LONG.optionalFieldOf("game_time").forGetter(p -> p.gameTime() < 0L ? Optional.empty() : Optional.of(p.gameTime())),
            SOURCE.optionalFieldOf("source").forGetter(p -> Optional.of(p.source()))
    ).apply(i, (x, y, z, dimension, time, source) -> new HomePoint(x, y, z, dimension.orElse(""),
            time.orElse(HomePoint.UNKNOWN_TIME), source.orElse(HomePoint.Source.COMMAND))));

    private HomePointCodecs() {
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    private static DataResult<HomePoint.Source> source(String id) {
        HomePoint.Source source = HomePoint.Source.byId(id);
        return source != null ? DataResult.success(source) : DataResult.error(() -> "Unbekannte Herkunft des Homepoints: " + id);
    }
}
