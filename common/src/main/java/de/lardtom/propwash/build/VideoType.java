package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.VideoDefinition;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VideoType implements PartInfo {
    public static final VideoType ANALOG_NANO = builtin("analog_nano");
    public static final VideoType ANALOG_RACE = builtin("analog_race");
    public static final VideoType DIGITAL_LITE = builtin("digital_lite");
    public static final VideoType DIGITAL_HD = builtin("digital_hd");

    private final VideoDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    public final VideoSystem system;
    private final double massGrams;
    public final int latencyMs;
    public final int rangeBlocks;
    public final double imageQuality;

    VideoType(VideoDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    VideoType(VideoDefinition definition, @Nullable String name, boolean missing) {
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.system = VideoSystem.of(definition.link());
        this.massGrams = definition.basics().massGrams();
        this.latencyMs = definition.latencyMs();
        this.rangeBlocks = definition.rangeBlocks();
        this.imageQuality = definition.imageQuality();
    }

    private static VideoType builtin(String path) {
        VideoType video = new VideoType(BuiltinParts.load(PartKind.VIDEO, path, VideoDefinition.class), null);
        PartIndex.addBuiltin(video);
        return video;
    }

    public static VideoType[] values() {
        return PartIndex.<VideoType>typed(PartKind.VIDEO).toArray(new VideoType[0]);
    }

    public static List<VideoType> all() {
        return PartIndex.typed(PartKind.VIDEO);
    }

    public static Optional<VideoType> byId(Identifier id) {
        return PartIndex.byId(VideoType.class, id);
    }

    public static Optional<VideoType> byKey(String key) {
        return PartIndex.byKey(VideoType.class, key);
    }

    @Override
    public VideoDefinition definition() {
        return this.definition;
    }

    @Override
    public Identifier id() {
        return this.definition.id();
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean missing() {
        return this.missing;
    }

    @Override
    public double massGrams() {
        return this.massGrams;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
