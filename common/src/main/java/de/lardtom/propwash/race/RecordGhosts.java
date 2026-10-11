package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RecordGhosts extends SavedData {
    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }, ByteBuffer::wrap);

    public record Stored(int course, String frame, int layout, int revision, String holder, long nanos, byte[] data) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("course").forGetter(Stored::course),
                Codec.STRING.fieldOf("frame").forGetter(Stored::frame),
                Codec.INT.fieldOf("layout").forGetter(Stored::layout),
                Codec.INT.fieldOf("revision").forGetter(Stored::revision),
                Codec.STRING.fieldOf("holder").forGetter(Stored::holder),
                Codec.LONG.fieldOf("nanos").forGetter(Stored::nanos),
                BYTES.fieldOf("data").forGetter(Stored::data)
        ).apply(i, Stored::new));
    }

    private static final Codec<RecordGhosts> CODEC = Stored.CODEC.listOf().xmap(RecordGhosts::of, g -> List.copyOf(g.ghosts.values()));

    public static final SavedDataType<RecordGhosts> TYPE = new SavedDataType<>(Constants.id("race_ghosts"), RecordGhosts::new, CODEC, null);

    private final Map<String, Stored> ghosts = new TreeMap<>();
    private int revision;

    public RecordGhosts() {
    }

    private static RecordGhosts of(List<Stored> list) {
        RecordGhosts ghosts = new RecordGhosts();
        for (Stored stored : list) {
            ghosts.ghosts.put(key(stored.course(), stored.frame()), stored);
            ghosts.revision = Math.max(ghosts.revision, stored.revision());
        }
        return ghosts;
    }

    public static RecordGhosts get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(int course, String frame) {
        return course + "/" + frame;
    }

    public @Nullable Stored get(int course, String frame) {
        return this.ghosts.get(key(course, frame));
    }

    public Stored put(int course, String frame, int layout, String holder, long nanos, byte[] data) {
        Stored stored = new Stored(course, frame, layout, ++this.revision, holder, nanos, data);
        this.ghosts.put(key(course, frame), stored);
        this.setDirty();
        return stored;
    }

    public void removeCourse(int course) {
        if (this.ghosts.values().removeIf(stored -> stored.course() == course)) {
            this.setDirty();
        }
    }
}
